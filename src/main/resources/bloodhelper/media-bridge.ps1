# BLOODHELPER Windows GSMTC bridge, Windows PowerShell 5.1 / Windows 10 1809+.
# This script is bundled with the mod. It never downloads or executes external code.
# -ReadOnlySamples N is a diagnostic mode: emit N observations and accept no controls.
param([ValidateRange(0, 120)][int]$ReadOnlySamples = 0)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
[Console]::InputEncoding = New-Object System.Text.UTF8Encoding($false)
$MaxArtworkBytes = 2 * 1024 * 1024

function Write-Packet($Packet) {
    [Console]::Out.WriteLine(($Packet | ConvertTo-Json -Compress -Depth 4))
    [Console]::Out.Flush()
}

function Write-Diagnostic([string]$Message) {
    $text = ($Message -replace '[\r\n\t]', ' ')
    if ($text.Length -gt 512) { $text = $text.Substring(0, 512) }
    [Console]::Error.WriteLine($text)
}

try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $ManagerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
    $PropertiesType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
    $StreamType = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
    $RandomAccessStreamType = [Windows.Storage.Streams.IRandomAccessStream, Windows.Storage.Streams, ContentType = WindowsRuntime]
    $null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]

    $script:AsTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.IsGenericMethodDefinition -and
        $_.GetGenericArguments().Length -eq 1 -and $_.GetParameters().Length -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    } | Select-Object -First 1
    if ($null -eq $script:AsTask) { throw 'WinRT task adapter is unavailable' }

    function Await-WinRT($Operation, [Type]$ResultType) {
        $task = $script:AsTask.MakeGenericMethod($ResultType).Invoke($null, @($Operation))
        if (-not $task.Wait(3000)) {
            try { $Operation.Cancel() } catch {}
            throw 'Windows media operation timed out'
        }
        # Unary comma prevents PowerShell from enumerating projected WinRT return values.
        return ,$task.Result
    }

    # Console.In.ReadLineAsync can synchronously block in Windows PowerShell. A small .NET
    # background thread reads a bounded command queue, without touching PowerShell runspaces.
    Add-Type -TypeDefinition @'
using System;
using System.Collections.Concurrent;
using System.Threading;
public static class BloodHelperMediaInput {
    private static readonly ConcurrentQueue<string> Pending = new ConcurrentQueue<string>();
    public static volatile bool Closed;
    public static void Start() {
        var reader = new Thread(() => {
            try {
                string line;
                while ((line = Console.ReadLine()) != null) {
                    if (Pending.Count < 16 && (line == "toggle" || line == "next" || line == "previous"))
                        Pending.Enqueue(line);
                }
            } catch { }
            finally { Closed = true; }
        });
        reader.IsBackground = true;
        reader.Start();
    }
    public static string Take() {
        string value;
        return Pending.TryDequeue(out value) ? value : null;
    }
}
'@

    $manager = Await-WinRT ($ManagerType::RequestAsync()) $ManagerType
    if ($ReadOnlySamples -eq 0) { [BloodHelperMediaInput]::Start() }
    $lastKey = $null
    $lastArtwork = $null
    $lastArtworkRead = [DateTime]::MinValue
    $observations = 0
    $failures = 0

    function Get-Artwork($Thumbnail) {
        if ($null -eq $Thumbnail) { return $null }
        $stream = $null
        $reader = $null
        $inputStream = $null
        try {
            $stream = Await-WinRT ($Thumbnail.OpenReadAsync()) $StreamType
            # WinRT interface results appear as System.__ComObject in PowerShell 5.1.
            # Invoke the declared interface explicitly; dynamic .Size would silently be null.
            $length = [long]$RandomAccessStreamType.GetProperty('Size').GetValue($stream, $null)
            if ($length -le 0 -or $length -gt $MaxArtworkBytes) { return $null }
            $inputStream = $RandomAccessStreamType.GetMethod('GetInputStreamAt').Invoke($stream, @([uint64]0))
            $reader = [Windows.Storage.Streams.DataReader]::new($inputStream)
            $loaded = Await-WinRT ($reader.LoadAsync([uint32]$length)) ([uint32])
            if ([long]$loaded -ne $length) { return $null }
            $bytes = New-Object byte[] $length
            $reader.ReadBytes($bytes)
            return [Convert]::ToBase64String($bytes)
        } catch {
            # Some players publish a stale or inaccessible thumbnail. Text/progress still work.
            return $null
        } finally {
            if ($null -ne $reader) { try { $reader.Dispose() } catch {} }
            if ($null -ne $inputStream) { try { ([IDisposable]$inputStream).Dispose() } catch {} }
            if ($null -ne $stream) { try { ([IDisposable]$stream).Dispose() } catch {} }
        }
    }

    while ($ReadOnlySamples -gt 0 -or -not [BloodHelperMediaInput]::Closed) {
        $cycle = [Diagnostics.Stopwatch]::StartNew()
        try {
            $session = $manager.GetCurrentSession()
            if ($ReadOnlySamples -eq 0) {
                # Consume at most 16 requests per poll. None are issued until the UI sends one.
                for ($i = 0; $i -lt 16; $i++) {
                    $command = [BloodHelperMediaInput]::Take()
                    if ($null -eq $command) { break }
                    if ($null -eq $session) { continue }
                    try {
                        $controlPlayback = $session.GetPlaybackInfo()
                        $controls = $controlPlayback.Controls
                        switch ($command) {
                            'toggle' {
                                if ($controls.IsPlayPauseToggleEnabled) {
                                    $null = Await-WinRT ($session.TryTogglePlayPauseAsync()) ([bool])
                                } elseif ($controlPlayback.PlaybackStatus.ToString() -eq 'Playing' -and $controls.IsPauseEnabled) {
                                    $null = Await-WinRT ($session.TryPauseAsync()) ([bool])
                                } elseif ($controls.IsPlayEnabled) {
                                    $null = Await-WinRT ($session.TryPlayAsync()) ([bool])
                                }
                            }
                            'next' {
                                if ($controls.IsNextEnabled) {
                                    $null = Await-WinRT ($session.TrySkipNextAsync()) ([bool])
                                }
                            }
                            'previous' {
                                if ($controls.IsPreviousEnabled) {
                                    $null = Await-WinRT ($session.TrySkipPreviousAsync()) ([bool])
                                }
                            }
                        }
                    } catch {
                        Write-Diagnostic 'The selected media application did not accept a control request.'
                    }
                }
            }

            if ($null -eq $session) {
                $lastKey = $null
                $lastArtwork = $null
                Write-Packet @{ kind = 'snapshot'; available = $false }
            } else {
                $properties = Await-WinRT ($session.TryGetMediaPropertiesAsync()) $PropertiesType
                $playback = $session.GetPlaybackInfo()
                $timeline = $session.GetTimelineProperties()
                $playing = $playback.PlaybackStatus.ToString() -eq 'Playing'
                $title = [string]$properties.Title
                $artist = [string]$properties.Artist
                if ($title.Length -gt 1024) { $title = $title.Substring(0, 1024) }
                if ($artist.Length -gt 1024) { $artist = $artist.Substring(0, 1024) }
                $key = ([string]$session.SourceAppUserModelId) + "`n" + $title + "`n" + $artist + "`n" + $properties.AlbumTitle
                $metadataChanged = $key -cne $lastKey
                $packet = @{
                    kind = 'snapshot'
                    available = -not ([string]::IsNullOrWhiteSpace($title) -and [string]::IsNullOrWhiteSpace($artist))
                    title = $title
                    artist = $artist
                    playing = $playing
                }
                $start = $timeline.StartTime.TotalMilliseconds
                $duration = [Math]::Max(0, $timeline.EndTime.TotalMilliseconds - $start)
                $position = [Math]::Max(0, $timeline.Position.TotalMilliseconds - $start)
                if ($playing -and $timeline.LastUpdatedTime.Year -gt 2000) {
                    $rate = 1.0
                    if ($null -ne $playback.PlaybackRate -and [double]$playback.PlaybackRate -gt 0) {
                        $rate = [Math]::Min(16.0, [double]$playback.PlaybackRate)
                    }
                    $elapsed = ([DateTimeOffset]::UtcNow - $timeline.LastUpdatedTime).TotalMilliseconds
                    $position += [Math]::Max(0, $elapsed) * $rate
                }
                if ($duration -gt 0) { $position = [Math]::Min($position, $duration) }
                $packet.positionMs = [long]$position
                $packet.durationMs = [long]$duration

                if ($metadataChanged -or ([DateTime]::UtcNow - $lastArtworkRead).TotalSeconds -ge 5) {
                    $artwork = Get-Artwork $properties.Thumbnail
                    if ($metadataChanged -or $artwork -cne $lastArtwork) {
                        $packet.artworkBase64 = $artwork
                        $lastArtwork = $artwork
                    }
                    $lastArtworkRead = [DateTime]::UtcNow
                }
                $lastKey = $key
                Write-Packet $packet
            }
            $failures = 0
        } catch {
            # No exception text includes the media property values or is copied into stdout.
            Write-Diagnostic ('Windows media read failed: ' + $_.Exception.GetType().Name)
            $lastKey = $null
            $lastArtwork = $null
            Write-Packet @{ kind = 'snapshot'; available = $false }
            $failures++
            if ($failures -ge 10) { throw 'Windows media session repeatedly failed' }
        }
        $observations++
        if ($ReadOnlySamples -gt 0 -and $observations -ge $ReadOnlySamples) { break }
        $delay = [Math]::Max(10, 500 - $cycle.ElapsedMilliseconds)
        Start-Sleep -Milliseconds ([int]$delay)
    }
} catch {
    Write-Diagnostic ('BLOODHELPER media bridge unavailable: ' + $_.Exception.GetType().Name)
    exit 1
}
