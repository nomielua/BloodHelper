package ru.bloodhelper.client.lyrics;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LrcParserTest {
    @Test void parsesPrecisionMultipleTimestampsAndGlobalOffset() {
        List<LyricLine> lines = LrcParser.parse("[ar:Example]\n[00:01.2][00:03.456]Two cues\n[00:02.34]Middle\n[offset:+100]");
        assertEquals(List.of(new LyricLine(1100, "Two cues"), new LyricLine(2240, "Middle"),
                new LyricLine(3356, "Two cues")), lines);
    }

    @Test void blankCueStopsPreviousLyricAndSeekUsesBinarySearch() {
        List<LyricLine> lines = LrcParser.parse("[0:10]First\n[0:15]\n[0:20]Last");
        assertEquals(-1, LrcParser.indexAt(lines, 9999));
        assertEquals(0, LrcParser.indexAt(lines, 10_000));
        assertEquals("", lines.get(LrcParser.indexAt(lines, 16_000)).text());
        assertEquals(2, LrcParser.indexAt(lines, 21_000));
        assertEquals(0, LrcParser.indexAt(lines, 11_000));
    }

    @Test void removesWordStampsAndFormattingAndMergesDuplicateCues() {
        List<LyricLine> lines = LrcParser.parse("[00:01]§cHello <00:01.3>world\n[00:01]Привет\n[00:95]Invalid");
        assertEquals(List.of(new LyricLine(1000, "Hello world / Привет")), lines);
    }

    @Test void phraseTimingDoesNotCrossNextLineEvenForShortIntervals() {
        List<LyricLine> input = List.of(new LyricLine(0, "one two three four five six seven eight nine ten"),
                new LyricLine(1000, "next"));
        List<LyricLine> split = LrcParser.splitLongLines(input);
        assertEquals(0, split.getFirst().timestampMs());
        assertEquals("next", split.getLast().text());
        assertEquals(1000, split.getLast().timestampMs());
        for (int i = 1; i < split.size(); i++) assertTrue(split.get(i).timestampMs() > split.get(i - 1).timestampMs());
        assertEquals("one two three four five six seven eight nine ten", String.join(" ",
                split.subList(0, split.size() - 1).stream().map(LyricLine::text).toList()));
    }

    @Test void malformedInputAndNegativeOffsetAreHandled() {
        assertTrue(LrcParser.parse(null).isEmpty());
        assertTrue(LrcParser.parse("[offset:broken]\n[bad]nothing").isEmpty());
        assertEquals(1500, LrcParser.parse("[offset:-500]\n[00:01]Delayed").getFirst().timestampMs());
        assertEquals(0, LrcParser.parse("[offset:5000]\n[00:01]Advanced").getFirst().timestampMs());
    }
}
