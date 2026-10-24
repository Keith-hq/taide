package edu.cst3492.taide.rag.embed;

import edu.cst3492.taide.rag.index.InMemoryVectorIndex;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-model checks, excluded by default (needs TAIDE_MODEL_DIR with model.onnx +
 * vocab.txt). Run with: ./gradlew test -PliveModelTests=true.
 */
@Tag("live-model")
class OnnxEmbeddingModelTest {

    @Test
    void tokenizerMatchesReferenceBertIds() throws IOException {
        BertTokenizer tokenizer = new BertTokenizer(OnnxEmbeddingModel.defaultModelDir().resolve("vocab.txt"));
        // Known BERT ids: hello=7592, world=2088.
        assertEquals(List.of(101, 7592, 2088, 102), tokenizer.tokenize("hello world"));
    }

    @Test
    void embedsAndNormalizesToUnitVectors() throws IOException {
        OnnxEmbeddingModel model = OnnxEmbeddingModel.openDefault();
        float[][] vectors = model.embed(List.of(
                "hello world",
                "hello world",
                "the course covers Java enterprise application development"));

        assertEquals(OnnxEmbeddingModel.DIMENSION, vectors[0].length);
        // Identical texts must be identical vectors (L2-normalized).
        assertEquals(1.0, InMemoryVectorIndex.cosine(vectors[0], vectors[1]), 1e-4);
        // Unrelated sentences must be clearly farther apart.
        assertTrue(InMemoryVectorIndex.cosine(vectors[0], vectors[2]) < 0.85,
                "expected dissimilar embeddings, got " + InMemoryVectorIndex.cosine(vectors[0], vectors[2]));
    }
}
