package edu.cst3492.taide.context;

/** SDK-free regression checks; execute main() with assertions independent of -ea. */
public final class SelectionRangeTest {
    private SelectionRangeTest() {
    }

    public static void main(String[] args) {
        String text = "alpha\nbeta\ngamma\n";
        check(text, 0, 6, 1, 1, "end at next line start excludes next line");
        check(text, 0, 7, 1, 2, "one selected character includes next line");
        check(text, 6, 10, 2, 2, "middle-line selection");
        check(text, 6, text.length(), 2, 3, "EOF after newline excludes empty final line");
        check(text, 7, 0, 1, 2, "reversed endpoint normalization");
        check(text, -99, 999, 1, 3, "stale endpoints clamped to document");
        check(text, 6, 6, 0, 0, "empty selection has no lines");
        check("", 0, 0, 0, 0, "empty document");
        check("abc", 0, 3, 1, 1, "EOF without newline");
        check("a\r\nb\r\nc", 0, 3, 1, 1, "CRLF line boundary");
        check("a\r\nb\r\nc", 3, 6, 2, 2, "CRLF middle line");
        check("\uD83D\uDE00\n中文", 0, 3, 1, 1, "UTF-16 editor offsets");
        try {
            SelectionRange.fromOffsets(0, 1, -1);
            throw new AssertionError("Negative document length accepted");
        } catch (IllegalArgumentException expected) {
            // Correct: caller must provide an actual document length.
        }
        System.out.println("SelectionRangeTest: 13 checks passed");
    }

    private static void check(String text, int start, int end, int firstLine, int lastLine, String label) {
        SelectionRange range = SelectionRange.fromOffsets(start, end, text.length());
        int actualFirst = range.startLine(offset -> lineAt(text, offset));
        int actualLast = range.endLine(offset -> lineAt(text, offset));
        if (actualFirst != firstLine || actualLast != lastLine) {
            throw new AssertionError(label + ": expected " + firstLine + ".." + lastLine
                    + ", got " + actualFirst + ".." + actualLast);
        }
        if (range.startOffset() < 0 || range.endOffset() > text.length()) {
            throw new AssertionError(label + ": invalid clamped offsets");
        }
    }

    private static int lineAt(String text, int offset) {
        int line = 0;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
