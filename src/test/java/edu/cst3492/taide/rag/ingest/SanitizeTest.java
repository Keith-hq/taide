package edu.cst3492.taide.rag.ingest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the A-1 acceptance promise: chunk text never carries U+FFFD mojibake,
 * horizontal whitespace collapses, newlines survive for paragraph-aware chunking.
 */
class SanitizeTest {

    @Test
    void dropsUnmappedGlyphs() {
        String dirty = "throwing a" + (char) 0xFFFD + "n exception" + (char) 0xFFFD;
        String clean = CourseMaterial.sanitize(dirty);
        assertFalse(clean.contains(String.valueOf((char) 0xFFFD)));
        assertEquals("throwing a n exception", clean);
    }

    @Test
    void collapsesHorizontalWhitespaceButKeepsNewlines() {
        String dirty = "line one" + (char) 9 + (char) 0x0B + "  line one end"
                + (char) 0x0A + "line two" + (char) 0x00A0 + "tail";
        assertEquals("line one line one end\nline two tail", CourseMaterial.sanitize(dirty));
    }

    @Test
    void trimsLeadingAndTrailingWhitespace() {
        assertEquals("body", CourseMaterial.sanitize("  " + (char) 0x00A0 + " body " + (char) 0x0C));
    }
}
