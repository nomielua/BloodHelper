package ru.bloodhelper.client.lyrics;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.bloodhelper.client.config.ClientConfig;
import ru.bloodhelper.client.music.MediaSnapshot;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class LyricsServiceTest {
    @Test void blankArtistUsesSearchWithoutInvalidExactRequest() {
        FakeHttp http = new FakeHttp(request -> new Reply(200, """
                [{"trackName":"Example","artistName":"Singer","duration":180,
                  "syncedLyrics":"[00:01]The right line"}]
                """));
        try (LyricsService service = new LyricsService(http)) {
            var settings = new ClientConfig.MusicSettings();
            service.update(new MediaSnapshot("Example", "", 1200, 180_000, true, null, 1), settings);
            assertEquals(LyricsService.Status.READY, service.status());
            assertEquals("The right line", service.currentLine(1200, settings));
            assertEquals(1, http.requests.size());
            assertEquals("/api/search", http.requests.getFirst().uri().getPath());
            assertEquals("q=Example", http.requests.getFirst().uri().getRawQuery());
        }
    }

    @Test void exactMissFallsBackButRejectsWrongArtistsAndWrongDuration() {
        FakeHttp http = new FakeHttp(request -> request.uri().getPath().equals("/api/get")
                ? new Reply(404, "{}") : new Reply(200, """
                [{"trackName":"Example","artistName":"Other","duration":180,"syncedLyrics":"[00:01]Wrong artist"},
                 {"trackName":"Example","artistName":"Singer","duration":240,"syncedLyrics":"[00:01]Wrong version"},
                 {"trackName":"Example","artistName":"Singer","duration":180,"syncedLyrics":"[00:01]Correct"}]
                """));
        try (LyricsService service = new LyricsService(http)) {
            var settings = new ClientConfig.MusicSettings();
            service.update(new MediaSnapshot("Example", "Singer", 1200, 180_000, true, null, 1), settings);
            assertEquals("Correct", service.currentLine(1200, settings));
            assertEquals(List.of("/api/get", "/api/search"), http.requests.stream().map(r -> r.uri().getPath()).toList());
        }
    }

    @Test void ambiguousTitleOnlySearchDoesNotSelectAnArbitraryPerformer() {
        var results = JsonParser.parseString("""
                [{"trackName":"Example","artistName":"First","duration":180,"syncedLyrics":"[00:01]First"},
                 {"trackName":"Example","artistName":"Second","duration":180.5,"syncedLyrics":"[00:01]Second"}]
                """);
        assertTrue(LyricsService.selectSearchResult(results, "Example", "", 180_000).isEmpty());
        assertTrue(LyricsService.selectSearchResult(results, "Example", "", 0).isEmpty());
    }

    @Test void disabledLyricsMakeNoRequestAndMissesAreCachedAcrossTrackChanges() {
        FakeHttp http = new FakeHttp(request -> new Reply(200, "[]"));
        try (LyricsService service = new LyricsService(http)) {
            var settings = new ClientConfig.MusicSettings();
            settings.lyricsMode = "OFF";
            MediaSnapshot song = new MediaSnapshot("Example", "", 0, 180_000, true, null, 1);
            service.update(song, settings);
            assertTrue(http.requests.isEmpty());
            settings.lyricsMode = "ISLAND";
            service.update(song, settings);
            assertEquals(LyricsService.Status.NOT_FOUND, service.status());
            service.update(MediaSnapshot.empty(), settings);
            service.update(song, settings);
            assertEquals(1, http.requests.size());
            assertEquals(LyricsService.Status.NOT_FOUND, service.status());
        }
    }

    private record Reply(int status, String json) {}

    /** Deterministic in-memory transport; no network, executors or Minecraft instance. */
    private static final class FakeHttp extends HttpClient {
        private final Function<HttpRequest, Reply> responder;
        private final List<HttpRequest> requests = new ArrayList<>();
        FakeHttp(Function<HttpRequest, Reply> responder) { this.responder = responder; }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            requests.add(request);
            Reply reply = responder.apply(request);
            HttpHeaders headers = HttpHeaders.of(Map.of(), (key, value) -> true);
            HttpResponse.BodySubscriber<T> subscriber = handler.apply(new HttpResponse.ResponseInfo() {
                @Override public int statusCode() { return reply.status(); }
                @Override public HttpHeaders headers() { return headers; }
                @Override public Version version() { return Version.HTTP_1_1; }
            });
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override public void request(long count) {}
                @Override public void cancel() {}
            });
            subscriber.onNext(List.of(ByteBuffer.wrap(reply.json().getBytes(StandardCharsets.UTF_8))));
            subscriber.onComplete();
            return subscriber.getBody().toCompletableFuture().thenApply(body -> new HttpResponse<>() {
                @Override public int statusCode() { return reply.status(); }
                @Override public HttpRequest request() { return request; }
                @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
                @Override public HttpHeaders headers() { return headers; }
                @Override public T body() { return body; }
                @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
                @Override public URI uri() { return request.uri(); }
                @Override public Version version() { return Version.HTTP_1_1; }
            });
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushHandler) { return sendAsync(request, handler); }
        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) { return sendAsync(request, handler).join(); }
        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { throw new UnsupportedOperationException(); }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public void shutdownNow() {}
    }
}
