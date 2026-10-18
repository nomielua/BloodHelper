package ru.bloodhelper.client.lyrics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.bloodhelper.client.config.ClientConfig;
import ru.bloodhelper.client.music.MediaSnapshot;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

/** Optional LRCLIB integration. Only title/artist/duration are sent, when lyrics are enabled. */
public final class LyricsService implements AutoCloseable {
    public enum Status { DISABLED, LOADING, READY, NOT_FOUND, ERROR }
    public record ActiveLine(int index, LyricLine line, long endMs) {}
    private record Lyrics(List<LyricLine> raw, List<LyricLine> split, Status status) {
        static Lyrics empty(Status status) { return new Lyrics(List.of(), List.of(), status); }
    }
    private static final int MAX_BODY = 1_048_576;
    private final HttpClient http;
    private final Map<String, Lyrics> cache = new LinkedHashMap<>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Lyrics> eldest) { return size() > 64; }
    };
    private volatile Lyrics active = Lyrics.empty(Status.DISABLED);
    private volatile String trackKey = "";
    private volatile boolean closed;
    private long generation;
    private CompletableFuture<?> inFlight;

    public LyricsService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    LyricsService(HttpClient http) { this.http = Objects.requireNonNull(http); }

    /** Call from the client tick. Performs no blocking IO. Changes invalidate older requests. */
    public synchronized void update(MediaSnapshot snapshot, ClientConfig.MusicSettings settings) {
        boolean enabled = !closed && settings.enabled && !"OFF".equals(settings.lyricsMode)
                && snapshot != null && snapshot.available() && !snapshot.title().isBlank();
        if (!enabled) {
            if (!trackKey.isEmpty() || active.status() != Status.DISABLED) reset();
            return;
        }
        String title = metadata(snapshot.title()), artist = metadata(snapshot.artist());
        String key = title.toLowerCase(Locale.ROOT) + "\u0000" + artist.toLowerCase(Locale.ROOT);
        if (key.equals(trackKey)) return;
        reset();
        trackKey = key;
        Lyrics cached = cache.get(key);
        if (cached != null) { active = cached; return; }
        active = Lyrics.empty(Status.LOADING);
        long token = generation;
        fetch(title, artist, snapshot.durationMs(), token).whenComplete((lines, failure) -> {
            synchronized (this) {
                if (closed || token != generation || !key.equals(trackKey)) return;
                Status state = failure != null ? Status.ERROR : lines.isEmpty() ? Status.NOT_FOUND : Status.READY;
                Lyrics found = failure == null ? new Lyrics(lines, LrcParser.splitLongLines(lines), state) : Lyrics.empty(state);
                // Cache misses too; failed requests remain suppressed for this track until
                // the user toggles lyrics or changes track, but are not permanent cache misses.
                if (state != Status.ERROR) cache.put(key, found);
                active = found;
                inFlight = null;
            }
        });
    }

    public String trackKey() { return trackKey; }
    public Status status() { return active.status(); }
    public List<LyricLine> lines(ClientConfig.MusicSettings settings) { return settings.splitLyrics ? active.split() : active.raw(); }

    public ActiveLine current(long playbackMs, ClientConfig.MusicSettings settings) {
        if (!settings.enabled || "OFF".equals(settings.lyricsMode)) return null;
        List<LyricLine> lines = lines(settings);
        long time = Math.max(0, playbackMs + settings.lyricOffsetMs);
        int index = LrcParser.indexAt(lines, time);
        if (index < 0) return null;
        LyricLine line = lines.get(index);
        long end = index + 1 < lines.size() ? lines.get(index + 1).timestampMs() : line.timestampMs() + 6000;
        if (time >= end || line.text().isBlank()) return null;
        return new ActiveLine(index, line, end);
    }

    public String currentLine(long playbackMs, ClientConfig.MusicSettings settings) {
        ActiveLine line = current(playbackMs, settings);
        return line == null ? "" : line.line().text();
    }

    private CompletableFuture<List<LyricLine>> fetch(String title, String artist, long duration, long token) {
        String query = "?track_name=" + encode(title) + "&artist_name=" + encode(artist);
        if (duration > 0) query += "&duration=" + duration / 1000;
        // LRCLIB rejects an empty artist on /get with HTTP 400. Browser sessions
        // can legitimately supply only a title, so use the search route directly.
        CompletableFuture<JsonElement> exactRequest = artist.isBlank()
                ? CompletableFuture.completedFuture(new JsonObject())
                : request("https://lrclib.net/api/get" + query, token);
        return exactRequest.thenCompose(json -> {
            List<LyricLine> exact = extract(json);
            if (!exact.isEmpty()) return CompletableFuture.completedFuture(exact);
            return request("https://lrclib.net/api/search?q=" + encode((title + " " + artist).strip()), token)
                    .thenApply(results -> selectSearchResult(results, title, artist, duration));
        });
    }

    private record Candidate(String artist, double difference, List<LyricLine> lines) {}

    static List<LyricLine> selectSearchResult(JsonElement results, String title, String artist, long duration) {
        if (!results.isJsonArray()) return List.of();
        List<Candidate> candidates = new ArrayList<>();
        for (JsonElement element : results.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (!normalize(string(item, "trackName")).equals(normalize(title))) continue;
            String candidateArtist = normalize(string(item, "artistName"));
            if (!artist.isBlank() && !candidateArtist.equals(normalize(artist))) continue;
            double trackDuration = number(item, "duration");
            if (!Double.isFinite(trackDuration) || trackDuration < 0) continue;
            // Without the artist, known playback duration is the only extra
            // discriminator. Require it to match instead of accepting absent data.
            if (artist.isBlank() && duration > 0 && trackDuration <= 0) continue;
            double difference = duration > 0 && trackDuration > 0 ? Math.abs(trackDuration - duration / 1000.0) : 0;
            if (difference > (artist.isBlank() ? 3 : 10)) continue;
            List<LyricLine> parsed = extract(item);
            if (!parsed.isEmpty()) candidates.add(new Candidate(candidateArtist, difference, parsed));
        }
        if (candidates.isEmpty()) return List.of();
        Candidate best = candidates.stream().min(java.util.Comparator.comparingDouble(Candidate::difference)).orElseThrow();
        if (artist.isBlank()) {
            // A generic title shared by equally plausible performers is ambiguous;
            // showing no synced text is preferable to displaying another song.
            boolean ambiguous = candidates.stream().anyMatch(candidate -> !candidate.artist().equals(best.artist())
                    && candidate.difference() <= best.difference() + 1);
            if (ambiguous) return List.of();
        }
        return best.lines();
    }

    private synchronized CompletableFuture<JsonElement> request(String url, long token) {
        if (closed || token != generation) return CompletableFuture.failedFuture(new CancellationException());
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12))
                .header("User-Agent", "BloodHelper/1.0 (Fabric visual client)")
                .header("Accept", "application/json").GET().build();
        CompletableFuture<HttpResponse<byte[]>> response = http.sendAsync(request, ignored -> new LimitedBody());
        inFlight = response;
        return response.orTimeout(14, TimeUnit.SECONDS).thenApply(result -> {
            if (result.statusCode() == 404) return new JsonObject();
            if (result.statusCode() != 200) throw new IllegalStateException("LRCLIB HTTP " + result.statusCode());
            return JsonParser.parseString(new String(result.body(), StandardCharsets.UTF_8));
        });
    }

    private static List<LyricLine> extract(JsonElement json) {
        return json.isJsonObject() ? LrcParser.parse(string(json.getAsJsonObject(), "syncedLyrics")) : List.of();
    }
    private static String string(JsonObject value, String name) {
        JsonElement element = value.get(name);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() ? element.getAsString() : "";
    }
    private static double number(JsonObject value, String name) {
        try { return value.has(name) && !value.get(name).isJsonNull() ? value.get(name).getAsDouble() : 0; }
        catch (RuntimeException ignored) { return 0; }
    }
    private static String metadata(String value) {
        String text = value.replaceAll("[\\p{Cntrl}]", " ").strip();
        return text.substring(0, Math.min(text.length(), 300));
    }
    private static String normalize(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip(); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private void reset() {
        generation++;
        trackKey = "";
        active = Lyrics.empty(Status.DISABLED);
        if (inFlight != null) { inFlight.cancel(true); inFlight = null; }
    }

    @Override public synchronized void close() {
        closed = true;
        reset();
        cache.clear();
        http.shutdownNow();
    }

    /** Cancels the HTTP subscription before a response can grow beyond the fixed bound. */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long received;
        private boolean ended;
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (ended) return;
            for (ByteBuffer buffer : buffers) received += buffer.remaining();
            if (received > MAX_BODY) {
                ended = true;
                subscription.cancel();
                delegate.onError(new IOException("Lyrics response exceeds limit"));
            } else delegate.onNext(buffers);
        }
        @Override public void onError(Throwable failure) { if (!ended) { ended = true; delegate.onError(failure); } }
        @Override public void onComplete() { if (!ended) { ended = true; delegate.onComplete(); } }
    }
}
