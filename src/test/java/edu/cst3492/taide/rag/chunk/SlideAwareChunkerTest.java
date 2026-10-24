package edu.cst3492.taide.rag.chunk;

import edu.cst3492.taide.rag.Chunk;
import edu.cst3492.taide.rag.ingest.CourseMaterial;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlideAwareChunkerTest {

    private static final SlideAwareChunker CHUNKER = new SlideAwareChunker();

    @Test
    void neverCrossesSlideBoundaries() {
        CourseMaterial material = new CourseMaterial("Doc.pdf", List.of(
                new CourseMaterial.Slide(1, "slide one marker ".repeat(80)),
                new CourseMaterial.Slide(2, "slide two marker ".repeat(80))));

        List<Chunk> chunks = CHUNKER.chunk(material);

        assertFalse(chunks.isEmpty());
        for (Chunk chunk : chunks) {
            boolean fromOne = chunk.text().contains("slide one marker");
            boolean fromTwo = chunk.text().contains("slide two marker");
            assertFalse(fromOne && fromTwo, "chunk mixes two slides: " + chunk.id());
            if (fromOne) assertEquals(1, chunk.slideNumber(), chunk.id());
            if (fromTwo) assertEquals(2, chunk.slideNumber(), chunk.id());
        }
    }

    @Test
    void longPunctuationFreeSlideIsHardSplitWithinBudget() {
        String cjk = "这是一个很长的没有标点的中文句子用来测试硬切分逻辑是否正常工作并且不超过预算".repeat(100);
        CourseMaterial material = new CourseMaterial("Doc.pdf", List.of(new CourseMaterial.Slide(3, cjk)));

        List<Chunk> chunks = CHUNKER.chunk(material);

        assertTrue(chunks.size() > 1);
        for (Chunk chunk : chunks) {
            assertEquals(3, chunk.slideNumber());
            assertTrue(SlideAwareChunker.estimateTokens(chunk.text()) <= SlideAwareChunker.TARGET_TOKENS,
                    "chunk over budget: " + chunk.id());
        }
        // Consecutive chunks overlap: chunk N+1 starts with the tail of chunk N.
        String tailOfFirst = chunks.get(0).text().substring(chunks.get(0).text().length() - 20);
        assertTrue(chunks.get(1).text().contains(tailOfFirst));
    }

    @Test
    void idsAndSlideNumbersAreStable() {
        CourseMaterial material = new CourseMaterial("Lec-5-Building.pdf",
                List.of(new CourseMaterial.Slide(12, "some slide body text")));

        List<Chunk> chunks = CHUNKER.chunk(material);

        assertEquals(1, chunks.size());
        assertEquals("Lec-5-Building.pdf#12#0", chunks.get(0).id());
        assertEquals(12, chunks.get(0).slideNumber());
        assertEquals("Lec-5-Building.pdf", chunks.get(0).documentName());
    }

    @Test
    void emptySlidesAreSkipped() {
        CourseMaterial material = new CourseMaterial("Doc.pdf", List.of(
                new CourseMaterial.Slide(1, "   "),
                new CourseMaterial.Slide(2, "")));

        assertTrue(CHUNKER.chunk(material).isEmpty());
    }

    @Test
    void tokenEstimateCountsCjkAndAsciiDifferently() {
        assertEquals(4, SlideAwareChunker.estimateTokens("四个汉字"));
        assertEquals((int) Math.ceil(2 * 1.3), SlideAwareChunker.estimateTokens("hello world"));
    }
}
