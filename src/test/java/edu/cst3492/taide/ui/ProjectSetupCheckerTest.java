package edu.cst3492.taide.ui;

import com.google.gson.Gson;
import edu.cst3492.taide.rag.embed.EmbeddingModel;
import edu.cst3492.taide.rag.embed.OnnxEmbeddingModel;
import edu.cst3492.taide.rag.index.IndexBuilder;
import edu.cst3492.taide.rag.index.InMemoryVectorIndex;
import edu.cst3492.taide.ui.settings.SettingsSnapshot;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ProjectSetupCheckerTest {
    @TempDir Path temporary;

    @Test void configurationAndReadableIndexDoNotClaimApiConnectivity() throws Exception {
        Path root = materials(); Path index = index(root, OnnxEmbeddingModel.DIMENSION);
        var state = ProjectSetupChecker.check(settings(root), true, index);
        assertTrue(state.apiReady()); assertTrue(state.indexReady());
        assertTrue(state.detail().contains("尚未测试 API 联网"));
    }

    @Test void apiCredentialsAreRequiredEvenWhenDemoPreferencesAreSaved() throws Exception {
        Path root = materials(); Path index = index(root, OnnxEmbeddingModel.DIMENSION);
        var state = ProjectSetupChecker.check(settings(root), false, index);
        assertFalse(state.apiReady()); assertTrue(state.indexReady()); assertTrue(state.detail().contains("API Key"));
    }

    @Test void invalidProviderEndpointDoesNotDisableIndependentIndexCheck() throws Exception {
        Path root = materials(); Path index = index(root, OnnxEmbeddingModel.DIMENSION);
        var invalid = new SettingsSnapshot(SettingsSnapshot.DEEPSEEK, "http://public.example", "model", root.toString(), true);
        var state = ProjectSetupChecker.check(invalid, true, index);
        assertFalse(state.apiReady()); assertTrue(state.indexReady());
    }

    @Test void missingCourseDirectoryAndMissingIndexStayIncomplete() throws Exception {
        var empty = ProjectSetupChecker.check(SettingsSnapshot.defaults(), true, temporary.resolve("missing.json"));
        assertFalse(empty.indexReady()); assertTrue(empty.detail().contains("选择课件"));
        Path root = materials();
        assertFalse(ProjectSetupChecker.check(settings(root), true, temporary.resolve("missing.json")).indexReady());
    }

    @Test void incompatibleAndMalformedIndexesAreRejected() throws Exception {
        Path root = materials(); Path index = index(root, 2);
        assertFalse(ProjectSetupChecker.check(settings(root), true, index).indexReady());
        Files.writeString(index, "{invalid");
        assertFalse(ProjectSetupChecker.check(settings(root), true, index).indexReady());
    }

    @Test void modifiedCourseRequiresRebuild() throws Exception {
        Path root = materials(); Path index = index(root, OnnxEmbeddingModel.DIMENSION);
        Files.setLastModifiedTime(root.resolve("Course.pdf"), FileTime.fromMillis(Files.getLastModifiedTime(index).toMillis() + 2000));
        var state = ProjectSetupChecker.check(settings(root), true, index);
        assertFalse(state.indexReady()); assertTrue(state.detail().contains("课件已更新"));
    }

    @Test void addedAndRemovedDocumentsCannotReuseAnotherCourseIndex() throws Exception {
        Path root = materials(); Path index = index(root, OnnxEmbeddingModel.DIMENSION);
        Files.writeString(root.resolve("Other.pptx"), "fixture");
        Files.setLastModifiedTime(root.resolve("Other.pptx"), FileTime.fromMillis(Files.getLastModifiedTime(index).toMillis() + 2000));
        assertFalse(ProjectSetupChecker.check(settings(root), true, index).indexReady());
        Files.delete(root.resolve("Other.pptx")); Files.delete(root.resolve("Course.pdf"));
        assertFalse(ProjectSetupChecker.check(settings(root), true, index).indexReady());
    }

    @Test void missingCitationPagesAreNotReady() throws Exception {
        Path root = materials(); Path index = index(root, OnnxEmbeddingModel.DIMENSION);
        Files.writeString(index, Files.readString(index).replace("\"slide\":1", "\"slide\":0"));
        assertFalse(ProjectSetupChecker.check(settings(root), true, index).indexReady());
    }

    @Test void nonFiniteVectorsAreNotReady() throws Exception {
        Path root = materials(); Path index = index(root, OnnxEmbeddingModel.DIMENSION);
        String original = Files.readString(index);
        String invalid = original.replace("\"vec\":[0.0", "\"vec\":[1e100");
        assertNotEquals(original, invalid);
        Files.writeString(index, invalid);
        assertFalse(ProjectSetupChecker.check(settings(root), true, index).indexReady());
    }

    @Test void realBuilderCanIncludeWhitespaceOnlyCourseFilesWithoutChunks() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("mixed-course"));
        writePdf(root.resolve("Text.pdf"), "constructor injection makes dependencies explicit");
        writePdf(root.resolve("Whitespace.pdf"), "    ");
        Path index = temporary.resolve("mixed-index.json");
        EmbeddingModel stub = new EmbeddingModel() {
            public float[][] embed(List<String> texts) {
                float[][] result = new float[texts.size()][dimension()];
                for (float[] row : result) row[0] = 1;
                return result;
            }
            public int dimension() { return OnnxEmbeddingModel.DIMENSION; }
            public String modelName() { return OnnxEmbeddingModel.MODEL_NAME; }
        };
        IndexBuilder.Result built = new IndexBuilder(stub).build(root, index, null);
        assertEquals(2, built.documentCount()); assertTrue(built.chunkCount() > 0);
        var stored = new Gson().fromJson(Files.readString(index), InMemoryVectorIndex.IndexFile.class);
        assertTrue(stored.chunks.stream().noneMatch(chunk -> chunk.doc.equals("Whitespace.pdf")));
        assertTrue(ProjectSetupChecker.check(settings(root), true, index).indexReady());
    }

    private void writePdf(Path path, String text) throws Exception {
        try (var document = new PDDocument()) {
            var page = new PDPage(); document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(40, 700); content.showText(text); content.endText();
            }
            document.save(path.toFile());
        }
    }

    private Path materials() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("materials"));
        Files.writeString(root.resolve("Course.pdf"), "fixture");
        Files.setLastModifiedTime(root.resolve("Course.pdf"), FileTime.fromMillis(1_000));
        return root;
    }

    private Path index(Path root, int dimension) throws Exception {
        var entry = new InMemoryVectorIndex.ChunkEntry();
        entry.id = "one"; entry.doc = "Course.pdf"; entry.slide = 1; entry.text = "课程片段"; entry.vec = new float[dimension];
        var file = new InMemoryVectorIndex.IndexFile();
        file.version = InMemoryVectorIndex.FORMAT_VERSION; file.model = OnnxEmbeddingModel.MODEL_NAME;
        file.dim = dimension; file.chunks = List.of(entry);
        Path path = temporary.resolve("index.json"); Files.writeString(path, new Gson().toJson(file)); return path;
    }

    private SettingsSnapshot settings(Path root) {
        return new SettingsSnapshot(SettingsSnapshot.DEEPSEEK, SettingsSnapshot.DEFAULT_URL, "deepseek-chat", root.toString(), true);
    }
}
