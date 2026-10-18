package ru.bloodhelper.client.music;

/** One immutable Windows media observation. Position is in milliseconds, relative to track start. */
public record MediaSnapshot(String title, String artist, long positionMs, long durationMs,
                            boolean playing, byte[] artwork, long revision) {
    public MediaSnapshot {
        title = title == null ? "" : title;
        artist = artist == null ? "" : artist;
        durationMs = Math.max(0, durationMs);
        positionMs = Math.max(0, positionMs);
        if (durationMs > 0) positionMs = Math.min(positionMs, durationMs);
        artwork = artwork == null ? null : artwork.clone();
    }

    /** Call only when revision changes: this accessor returns a defensive copy. */
    @Override
    public byte[] artwork() {
        return artwork == null ? null : artwork.clone();
    }

    public static MediaSnapshot empty() {
        return new MediaSnapshot("", "", 0, 0, false, null, 0);
    }

    public boolean available() {
        return !title.isBlank() || !artist.isBlank();
    }
}
