package edu.cst3492.taide.rag.ingest;

import java.util.List;

/** One parsed course file: slides/pages in order, each with a 1-based number and raw text. */
public record CourseMaterial(String documentName, List<Slide> slides) {

    /** A single PDF page or PPTX slide. */
    public record Slide(int number, String text) {
        public Slide {
            if (number < 1) throw new IllegalArgumentException("slide number must be 1-based");
        }
    }

    /**
     * Shared cleanup for extracted text: unmapped glyphs (U+FFFD, common in Canvas PDF
     * exports) become spaces and horizontal whitespace runs collapse, while newlines are
     * kept for paragraph-aware chunking. Chunk text must not carry mojibake into
     * citations or prompts.
     */
    static String sanitize(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean space = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != 0x0A && (c == 0xFFFD || Character.isWhitespace(c) || c == 0x00A0)) {
                space = true;
            } else {
                if (space && sb.length() > 0) sb.append(' ');
                space = false;
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
