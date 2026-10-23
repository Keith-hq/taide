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
        for (String line : markdown.split("\\R", -1)) {
            if (line.stripLeading().startsWith("```")) {
                if (code == null) {
                    add(parts, prose, false);
                    code = new StringBuilder();
                } else {
                    add(parts, code, true);
                    code = null;
                }
            } else {
                (code == null ? prose : code).append(line).append('\n');
            }
        }
        if (code == null) add(parts, prose, false);
        else {
            prose.append(code).append("（代码块结束标记缺失）");
            add(parts, prose, false);
        }
        return List.copyOf(parts);
    }

    private static void add(List<Part> parts, StringBuilder value, boolean code) {
        String text = value.toString().replaceFirst("\\n+$", "");
        if (!text.isBlank()) parts.add(new Part(text, code));
        value.setLength(0);
    }
}
