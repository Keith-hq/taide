package edu.cst3492.taide.ui.chat;

import java.util.ArrayList;
import java.util.List;

/** Small, dependency-free display parser for fenced Markdown code in model answers. */
public final class AnswerParts {
    public record Part(String text, boolean code) {}
    private AnswerParts() {}

    public static List<Part> parse(String markdown) {
        if (markdown == null || markdown.isEmpty()) return List.of();
        List<Part> parts = new ArrayList<>();
        StringBuilder prose = new StringBuilder();
        StringBuilder code = null;
        char fence = 0;
        int fenceLength = 0;
        for (String line : markdown.split("\\R", -1)) {
            String trimmed = line.stripLeading();
            char marker = trimmed.isEmpty() ? 0 : trimmed.charAt(0);
            int count = 0;
            if (marker == '`' || marker == '~') while (count < trimmed.length() && trimmed.charAt(count) == marker) count++;
            if (code == null && count >= 3) {
                add(parts, prose, false);
                code = new StringBuilder(); fence = marker; fenceLength = count;
            } else if (code != null && marker == fence && count >= fenceLength && trimmed.substring(count).isBlank()) {
                add(parts, code, true);
                code = null;
            } else {
                (code == null ? prose : code).append(line).append('\n');
            }
        }
        if (code == null) add(parts, prose, false);
        else add(parts, code, true); // An unfinished streaming fence is still code, not an error.
        return List.copyOf(parts);
    }

    private static void add(List<Part> parts, StringBuilder value, boolean code) {
        String text = value.toString().replaceFirst("\\n+$", "");
        if (!text.isBlank()) parts.add(new Part(text, code));
        value.setLength(0);
    }
}
