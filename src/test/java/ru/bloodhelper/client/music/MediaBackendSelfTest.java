package ru.bloodhelper.client.music;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

/** Plain JVM regression probe. It does not start a Windows process or control a media player. */
public final class MediaBackendSelfTest {
    public static void main(String[] args) throws Exception {
        MediaProtocol protocol = new MediaProtocol();
        check(!protocol.current().available(), "Initial snapshot must be empty");
        String art = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        MediaSnapshot first = protocol.accept(packet("Track", "Artist", 500, 1000, ",\"artworkBase64\":\"" + art + "\""));
        check(first.available() && first.playing() && first.revision() == 1, "Track metadata must be available");
        check(Arrays.equals(first.artwork(), new byte[]{1, 2, 3}), "Artwork must decode");
        byte[] copy = first.artwork();
        copy[0] = 99;
        check(first.artwork()[0] == 1, "Accessor must not expose mutable artwork");

        MediaSnapshot progress = protocol.accept(packet("Track", "Artist", 750, 1000, ""));
        check(progress.revision() == first.revision(), "Progress must not reload textures");
        check(progress.artwork()[0] == 1, "Omitted artwork must preserve current cover");
        MediaSnapshot changed = protocol.accept(packet("Next track", "Artist", 0, 1000, ""));
        check(changed.artwork() == null && changed.revision() == 2, "Track change must not retain old cover");

        MediaSnapshot clamped = protocol.accept(packet("Next track", "Artist", 9999, 1000, ""));
        check(clamped.positionMs() == 1000, "Position must not exceed duration");
        MediaSnapshot negative = protocol.accept(packet("Next track", "Artist", -10, -100, ""));
        check(negative.positionMs() == 0 && negative.durationMs() == 0, "Negative time must clamp");
        MediaSnapshot unknownDuration = protocol.accept(packet("Next track", "Artist", 550, 0, ""));
        check(unknownDuration.positionMs() == 550, "Unknown duration must preserve elapsed time");

        long beforeClear = protocol.current().revision();
        MediaSnapshot cleared = protocol.accept("{\"kind\":\"snapshot\",\"available\":false}");
        check(!cleared.available() && cleared.artwork() == null && cleared.revision() > beforeClear, "Disconnect must clear state");
        check(protocol.clear().revision() == cleared.revision(), "Repeated absence must not churn revision");
        rejected(() -> protocol.accept("{\"kind\":\"script\"}"));
        rejected(() -> protocol.accept(packet("Track", "Artist", 0, 0, ",\"artworkBase64\":\"not-base64!!\"")));
        String overLimitArt = "A".repeat(((MediaProtocol.MAX_ARTWORK_BYTES + 2) / 3) * 4 + 4);
        rejected(() -> protocol.accept(packet("Track", "Artist", 0, 0, ",\"artworkBase64\":\"" + overLimitArt + "\"")));

        StringReader lines = new StringReader("one\r\ntwo\nthree");
        check("one".equals(MediaProtocol.readLine(lines)), "CRLF must decode");
        check("two".equals(MediaProtocol.readLine(lines)), "LF must decode");
        check("three".equals(MediaProtocol.readLine(lines)), "Final unterminated line must decode");
        check(MediaProtocol.readLine(lines) == null, "EOF must terminate reader");
        try {
            MediaProtocol.readLine(new StringReader("x".repeat(MediaProtocol.MAX_LINE_CHARS + 1)));
            throw new AssertionError("Oversized stream must be rejected before newline");
        } catch (IOException expected) { }

        byte[] supplied = {4, 5};
        MediaSnapshot immutable = new MediaSnapshot("A", "B", 0, 0, false, supplied, 1);
        supplied[0] = 9;
        check(immutable.artwork()[0] == 4, "Constructor must not retain caller array");

        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            try (MediaService service = new MediaService()) {
                service.start();
                check(!service.isSupported() && "unsupported_os".equals(service.status()), "Non-Windows must be graceful");
                check(!service.snapshot().available(), "Unsupported platforms must remain empty");
                service.setEnabled(false);
                check(!service.isEnabled(), "Module off must update desired state");
                MediaSnapshot off = service.snapshot();
                for (int i = 0; i < 100; i++) service.setEnabled(false);
                check(service.snapshot() == off, "Repeated tick updates must be idempotent");
                service.setEnabled(true);
                check(service.isEnabled(), "The same service object must support re-enabling");
                service.close();
                service.setEnabled(true);
                check(!service.isEnabled() && !service.snapshot().available(), "Close must be final");
            }
        }
        MediaProtocol restarted = new MediaProtocol(500);
        check(restarted.accept(packet("Track", "Artist", 0, 0, "")).revision() == 501,
                "Restarted session must continue the service artwork revision");
        System.out.println("MediaBackendSelfTest: all checks passed; no media controls or Windows processes executed.");
    }

    private static String packet(String title, String artist, long position, long duration, String extra) {
        return "{\"kind\":\"snapshot\",\"available\":true,\"playing\":true,\"title\":\"" + title
                + "\",\"artist\":\"" + artist + "\",\"positionMs\":" + position + ",\"durationMs\":" + duration + extra + "}";
    }

    private static void rejected(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Invalid packet must be rejected");
        } catch (IllegalArgumentException expected) { }
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
