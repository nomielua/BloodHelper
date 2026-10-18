package ru.bloodhelper.client.music;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Background Windows media connection. No network or automatic playback commands. */
public final class MediaService implements AutoCloseable {
    private static final long STALE_NANOS = TimeUnit.SECONDS.toNanos(20);
    private final boolean supported = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    private final Object lifecycle = new Object();
    private volatile boolean closed;
    private volatile boolean enabled;
    private volatile Session session;
    private volatile MediaSnapshot snapshot = MediaSnapshot.empty();
    private volatile String status = supported ? "stopped" : "unsupported_os";
    private volatile String diagnostic = "";

    public MediaService() {}
    public void start() { setEnabled(true); }

    /** Idempotent module toggle. Never waits for process startup, shutdown, or a worker join. */
    public void setEnabled(boolean value) {
        Session previous;
        Session next;
        synchronized (lifecycle) {
            if (closed || enabled == value) return;
            enabled = value;
            previous = session;
            next = value && supported ? new Session(snapshot.revision()) : null;
            session = next;
            if (!value) snapshot = emptyAfter(snapshot);
            diagnostic = "";
            status = !supported ? "unsupported_os" : value ? "starting" : "stopped";
        }
        if (previous != null) previous.stop();
        if (next != null) next.worker.start();
    }

    /** Cached immutable observation; about two updates per second and no allocation on this call. */
    public MediaSnapshot snapshot() { return snapshot; }
    public boolean isSupported() { return supported; }
    public boolean isEnabled() { return enabled && !closed; }
    /** stopped, unsupported_os, starting, no_session, playing, paused, or unavailable. */
    public String status() { return status; }
    /** Optional bounded diagnostic. Media titles are never intentionally included. */
    public String diagnostic() { return diagnostic; }
    public void playPause() { offer("toggle"); }
    public void next() { offer("next"); }
    public void previous() { offer("previous"); }

    private void offer(String command) {
        synchronized (lifecycle) {
            Session active = session;
            if (active != null && active.active() && snapshot.available()) {
                Process process = active.process;
                if (process != null && process.isAlive()) {
                    active.commands.offer(new Command(command, System.nanoTime(), process));
                }
            }
        }
    }

    private final class Session {
        private final AtomicBoolean stopped = new AtomicBoolean();
        private final ArrayBlockingQueue<Command> commands = new ArrayBlockingQueue<>(16);
        private final MediaProtocol protocol;
        private final Thread worker;
        private volatile Process process;
        private volatile long lastPacketNanos;

        Session(long initialRevision) {
            protocol = new MediaProtocol(initialRevision);
            worker = daemon("bloodhelper-media", this::run);
        }
        boolean active() { return !stopped.get() && !closed && enabled && session == this; }

        void publish(MediaSnapshot observation) {
            synchronized (lifecycle) {
                if (!active()) return;
                snapshot = observation;
                status = observation.available() ? observation.playing() ? "playing" : "paused" : "no_session";
            }
        }
        void setStatus(String value) {
            synchronized (lifecycle) { if (active()) status = value; }
        }
        void diagnose(String value) {
            synchronized (lifecycle) { if (active()) diagnostic = safeMessage(value); }
        }

        void run() {
            Path directory = null;
            try {
                if (!active()) return;
                Path parent = Path.of(System.getProperty("java.io.tmpdir"), "bloodhelper");
                Files.createDirectories(parent);
                directory = Files.createTempDirectory(parent, "media-");
                Path script = directory.resolve("media-bridge.ps1");
                try (InputStream embedded = MediaService.class.getResourceAsStream("/bloodhelper/media-bridge.ps1")) {
                    if (embedded == null) throw new IOException("Bundled media bridge is missing");
                    Files.copy(embedded, script);
                }
                String windows = System.getenv("SystemRoot");
                if (windows == null || windows.isBlank()) throw new IOException("Windows directory is unavailable");
                Path executable = Path.of(windows, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
                int failures = 0;
                while (active()) {
                    long connectedAt = System.nanoTime();
                    Process connection = null;
                    try {
                        setStatus("starting");
                        connection = new ProcessBuilder(executable.toString(), "-NoLogo", "-NoProfile", "-NonInteractive",
                                "-WindowStyle", "Hidden", "-Mta", "-ExecutionPolicy", "Bypass", "-File", script.toString()).start();
                        process = connection;
                        // Disabling during ProcessBuilder.start must never revive a stopped session.
                        if (!active()) break;
                        lastPacketNanos = System.nanoTime();
                        Process bridge = connection;
                        daemon("bloodhelper-media-controls", () -> writeCommands(bridge)).start();
                        daemon("bloodhelper-media-diagnostics", () -> drainErrors(bridge)).start();
                        daemon("bloodhelper-media-watchdog", () -> watch(bridge)).start();
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while (active() && (line = MediaProtocol.readLine(reader)) != null) {
                                if (line.isBlank()) continue;
                                MediaSnapshot observation = protocol.accept(line);
                                lastPacketNanos = System.nanoTime();
                                publish(observation);
                                diagnose("");
                            }
                        }
                    } catch (IOException | RuntimeException failure) {
                        diagnose(failure.getClass().getSimpleName() + ": " + failure.getMessage());
                    } finally {
                        if (connection != null) connection.destroyForcibly();
                        process = null;
                        commands.clear();
                        publish(protocol.clear());
                    }
                    if (active()) {
                        setStatus("unavailable");
                        if (System.nanoTime() - connectedAt > TimeUnit.SECONDS.toNanos(30)) failures = 0;
                        long retryMs = Math.min(30_000, 1_000L << Math.min(5, failures++));
                        Thread.sleep(retryMs);
                    }
                }
            } catch (IOException | RuntimeException failure) {
                diagnose(failure.getClass().getSimpleName() + ": " + failure.getMessage());
                setStatus("unavailable");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                if (directory != null) {
                    try {
                        Files.deleteIfExists(directory.resolve("media-bridge.ps1"));
                        Files.deleteIfExists(directory);
                    } catch (IOException ignored) {
                        // External VM termination can leave a harmless script in the OS temp folder.
                    }
                }
            }
        }

        void writeCommands(Process connection) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8))) {
                while (active() && connection.isAlive() && process == connection) {
                    Command command = commands.poll(250, TimeUnit.MILLISECONDS);
                    if (command == null || command.connection() != connection || !active() || process != connection
                            || System.nanoTime() - command.createdNanos() > TimeUnit.SECONDS.toNanos(2)) continue;
                    writer.write(command.name());
                    writer.newLine();
                    writer.flush();
                }
            } catch (IOException ignored) {
                // The reader reconnects; old commands never cross a connection or module toggle.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        void watch(Process connection) {
            try {
                while (active() && connection.isAlive() && process == connection) {
                    Thread.sleep(1_000);
                    if (System.nanoTime() - lastPacketNanos > STALE_NANOS) {
                        diagnose("Media bridge timed out");
                        connection.destroyForcibly();
                        return;
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        void drainErrors(Process connection) {
            try (InputStreamReader reader = new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8)) {
                char[] buffer = new char[1024];
                int count;
                while ((count = reader.read(buffer)) >= 0) {
                    if (count > 0 && process == connection) diagnose(new String(buffer, 0, count));
                }
            } catch (IOException ignored) { }
        }
        void stop() {
            stopped.set(true);
            commands.clear();
            Process bridge = process;
            if (bridge != null) bridge.destroyForcibly();
            worker.interrupt();
        }
    }

    private static MediaSnapshot emptyAfter(MediaSnapshot previous) {
        return new MediaSnapshot("", "", 0, 0, false, null, previous.revision() + 1);
    }
    private static String safeMessage(String message) {
        String clean = message.replaceAll("[\\p{Cntrl}]", " ");
        return clean.substring(0, Math.min(512, clean.length()));
    }
    private static Thread daemon(String name, Runnable action) {
        Thread thread = new Thread(action, name);
        thread.setDaemon(true);
        return thread;
    }

    /** Final shutdown; subsequent start/setEnabled calls are ignored. */
    @Override
    public void close() {
        Session previous;
        synchronized (lifecycle) {
            if (closed) return;
            closed = true;
            enabled = false;
            previous = session;
            session = null;
            snapshot = emptyAfter(snapshot);
            status = supported ? "stopped" : "unsupported_os";
        }
        if (previous != null) previous.stop();
    }
    private record Command(String name, long createdNanos, Process connection) {}
}
