package edu.cst3492.taide.rag.index;

import edu.cst3492.taide.rag.embed.EmbeddingModel;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexBuilderTest {

    /** Deterministic fake embedder: 4-dim one-hot by text hash, counts its calls. */
    static final class StubModel implements EmbeddingModel {
        int calls;

        @Override
        public float[][] embed(List<String> texts) {
            calls++;
            float[][] out = new float[texts.size()][4];
            for (int i = 0; i < texts.size(); i++) {
                out[i][Math.abs(texts.get(i).hashCode()) % 4] = 1;
            }
            return out;
        }

        @Override
        public int dimension() { return 4; }

        @Override
        public String modelName() { return "stub"; }
    }

    @Test
    void firstBuildEmbedsAllSecondBuildReusesAll(@TempDir Path dir) throws IOException {
        Path pdf = dir.resolve("Lec-1.pdf");
        writePdf(pdf, "constructor injection makes dependencies explicit");
        Path indexFile = dir.resolve("index.json");
        StubModel model = new StubModel();
        IndexBuilder builder = new IndexBuilder(model);

        IndexBuilder.Result first = builder.build(dir, indexFile, null);

        assertTrue(first.chunkCount() >= 1);
        assertEquals(first.chunkCount(), first.embeddedCount());
        assertEquals(1, model.calls);

        IndexBuilder.Result second = builder.build(dir, indexFile, null);

        assertEquals(first.chunkCount(), second.chunkCount());
        assertEquals(0, second.embeddedCount());
        assertEquals(second.chunkCount(), second.reusedCount());
        assertEquals(1, model.calls, "cache hit must not call the embedder");

        InMemoryVectorIndex loaded = InMemoryVectorIndex.load(indexFile);
        assertEquals(second.chunkCount(), loaded.size());
    }

    @Test
    void missingMaterialsFailLoudly(@TempDir Path dir) {
        IndexBuilder builder = new IndexBuilder(new StubModel());
        Path empty = dir.resolve("empty");
        try {
            java.nio.file.Files.createDirectory(empty);
            builder.build(empty, dir.resolve("index.json"), null);
            org.junit.jupiter.api.Assertions.fail("expected IOException");
        } catch (IOException expected) {
            // ok
        }
    }

    private static void writePdf(Path path, String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(path.toFile());
        }
    }
}
