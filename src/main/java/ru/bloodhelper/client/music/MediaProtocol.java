package ru.bloodhelper.client.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Base64;

/** Bounded newline-delimited protocol. Used only by the bridge reader thread. */
final class MediaProtocol {
    static final int MAX_ARTWORK_BYTES = 2 * 1024 * 1024;
    static final int MAX_LINE_CHARS = 3 * 1024 * 1024;
    private static final int MAX_TEXT_CHARS = 1024;
    private static final long MAX_TIME_MS = 365L * 24 * 60 * 60 * 1000;

    private MediaSnapshot current;
    private byte[] currentArtwork;

    MediaProtocol() {
        this(0);
    }

    MediaProtocol(long initialRevision) {
        current = new MediaSnapshot("", "", 0, 0, false, null, initialRevision);
    }

    MediaSnapshot current() {
        return current;
    }

    MediaSnapshot clear() {
        if (current.available() || currentArtwork != null) {
            current = new MediaSnapshot("", "", 0, 0, false, null, current.revision() + 1);
            currentArtwork = null;
        }
        return current;
    }

    MediaSnapshot accept(String line) {
        if (line.length() > MAX_LINE_CHARS) throw new IllegalArgumentException("Media packet too large");
        JsonObject packet = JsonParser.parseString(line).getAsJsonObject();
        if (!"snapshot".equals(string(packet, "kind", 32))) {
            throw new IllegalArgumentException("Unknown media packet");
        }
        if (!bool(packet, "available")) return clear();
        String title = string(packet, "title", MAX_TEXT_CHARS);
        String artist = string(packet, "artist", MAX_TEXT_CHARS);
        boolean metadataChanged = !title.equals(current.title()) || !artist.equals(current.artist());
        byte[] artwork = metadataChanged ? null : currentArtwork;
        if (packet.has("artworkBase64")) {
            JsonElement element = packet.get("artworkBase64");
            if (element.isJsonNull()) {
                artwork = null;
            } else {
                String encoded = element.getAsString();
                if (encoded.length() > ((MAX_ARTWORK_BYTES + 2) / 3) * 4) {
                    throw new IllegalArgumentException("Media artwork too large");
                }
                artwork = Base64.getDecoder().decode(encoded);
                if (artwork.length > MAX_ARTWORK_BYTES) throw new IllegalArgumentException("Media artwork too large");
                if (artwork.length == 0) artwork = null;
            }
        }
        long revision = current.revision();
        if (metadataChanged || !Arrays.equals(artwork, currentArtwork)) revision++;
        currentArtwork = artwork;
        current = new MediaSnapshot(title, artist, time(packet, "positionMs"), time(packet, "durationMs"),
                bool(packet, "playing"), artwork, revision);
        return current;
    }

    /** Unlike BufferedReader.readLine(), this stops an unterminated oversized packet. */
    static String readLine(Reader reader) throws IOException {
        StringBuilder line = new StringBuilder(1024);
        int character;
        while ((character = reader.read()) != -1) {
            if (character == '\n') return line.toString();
            if (character != '\r') {
                if (line.length() >= MAX_LINE_CHARS) throw new IOException("Media packet too large");
                line.append((char) character);
            }
        }
        return line.isEmpty() ? null : line.toString();
    }

    private static String string(JsonObject object, String key, int limit) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return "";
        String result = value.getAsString().replaceAll("[\\p{Cntrl}&&[^\\t]]", " ");
        return result.length() > limit ? result.substring(0, limit) : result;
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && !value.isJsonNull() && value.getAsBoolean();
    }

    private static long time(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return 0;
        double numeric = value.getAsDouble();
        if (!Double.isFinite(numeric)) return 0;
        return (long) Math.max(0, Math.min(numeric, MAX_TIME_MS));
    }
}
