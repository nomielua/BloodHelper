package ru.bloodhelper.client.lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded LRC parser: centiseconds/milliseconds, multiple stamps, offsets and empty cues. */
public final class LrcParser {
    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):([0-5]\\d)(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET = Pattern.compile("(?im)^\\s*\\[offset:([+-]?\\d{1,7})]\\s*$");
    private static final Pattern WORD_STAMP = Pattern.compile("<\\d{1,3}:[0-5]\\d(?:[.:]\\d{1,3})?>");
    private static final int MAX_CUES = 8192;

    private LrcParser() {}

    public static List<LyricLine> parse(String lrc) {
        if (lrc == null || lrc.isBlank() || lrc.length() > 1_048_576) return List.of();
        long offset = 0;
        Matcher offsets = OFFSET.matcher(lrc);
        while (offsets.find()) offset = Long.parseLong(offsets.group(1));
        List<LyricLine> result = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            String line = raw.strip();
            if (line.startsWith("\uFEFF")) line = line.substring(1);
            Matcher matcher = STAMP.matcher(line);
            List<Long> timestamps = new ArrayList<>();
            int end = 0;
            while (matcher.find() && line.substring(end, matcher.start()).isBlank()) {
                String fraction = matcher.group(3);
                long millis = fraction == null ? 0 : Long.parseLong((fraction + "000").substring(0, 3));
                long time = Long.parseLong(matcher.group(1)) * 60_000L + Long.parseLong(matcher.group(2)) * 1000L + millis;
                // Positive LRC offset advances the lyrics relative to the audio clock.
                timestamps.add(Math.max(0, time - offset));
                end = matcher.end();
                if (timestamps.size() >= 64) break;
            }
            if (timestamps.isEmpty()) continue;
            String text = WORD_STAMP.matcher(line.substring(end)).replaceAll("")
                    .replaceAll("§.", "").replaceAll("[\\p{Cntrl}]", " ").strip();
            if (text.codePointCount(0, text.length()) > 512) text = text.substring(0, text.offsetByCodePoints(0, 512));
            for (long time : timestamps) {
                result.add(new LyricLine(time, text));
                if (result.size() >= MAX_CUES) break;
            }
            if (result.size() >= MAX_CUES) break;
        }
        result.sort(Comparator.comparingLong(LyricLine::timestampMs));
        // Duplicate timestamps occur in translations and multi-stamp LRCs. Keep one
        // cue containing all non-empty fragments rather than flickering between them.
        List<LyricLine> merged = new ArrayList<>();
        for (LyricLine line : result) {
            if (!merged.isEmpty() && merged.getLast().timestampMs() == line.timestampMs()) {
                LyricLine previous = merged.removeLast();
                String text = previous.text().isBlank() ? line.text() : line.text().isBlank() || line.text().equals(previous.text())
                        ? previous.text() : previous.text() + " / " + line.text();
                if (text.length() > 1024) text = previous.text();
                merged.add(new LyricLine(line.timestampMs(), text));
            } else merged.add(line);
        }
        return List.copyOf(merged);
    }

    /** Distributes short phrases within their real cue interval; never crosses the next cue. */
    public static List<LyricLine> splitLongLines(List<LyricLine> original) {
        List<LyricLine> split = new ArrayList<>();
        for (int i = 0; i < original.size(); i++) {
            LyricLine line = original.get(i);
            long duration = i + 1 < original.size() ? original.get(i + 1).timestampMs() - line.timestampMs() : 4000;
            String[] words = line.text().split("\\s+");
            if (words.length <= 5 || duration < 900) { split.add(line); continue; }
            int chunks = Math.min((words.length + 2) / 3, Math.min(12, (int) (duration / 450)));
            int wordsPerChunk = (words.length + chunks - 1) / chunks;
            chunks = (words.length + wordsPerChunk - 1) / wordsPerChunk;
            for (int c = 0; c < chunks; c++) {
                int start = c * wordsPerChunk, end = Math.min(words.length, start + wordsPerChunk);
                StringBuilder text = new StringBuilder();
                for (int w = start; w < end; w++) { if (!text.isEmpty()) text.append(' '); text.append(words[w]); }
                split.add(new LyricLine(line.timestampMs() + duration * c / chunks, text.toString()));
            }
        }
        return List.copyOf(split);
    }

    /** Returns the latest cue at or before the playback time, including explicit blank cues. */
    public static int indexAt(List<LyricLine> lines, long timeMs) {
        int low = 0, high = lines.size() - 1, found = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).timestampMs() <= timeMs) { found = mid; low = mid + 1; }
            else high = mid - 1;
        }
        return found;
    }
}
