package ru.bloodhelper.client.lyrics;

/** An immutable LRC cue; an empty text is an explicit end-of-lyric marker. */
public record LyricLine(long timestampMs, String text) {}
