package edu.cst3492.taide.rag.retrieve;

import edu.cst3492.taide.rag.Chunk;
import edu.cst3492.taide.rag.RetrievedChunk;
import edu.cst3492.taide.rag.embed.EmbeddingModel;
import edu.cst3492.taide.rag.index.InMemoryVectorIndex;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fast tests with hand-crafted vectors: threshold, MMR dedup, mapping. */
class EmbeddingRetrieverTest {

    static final class QueryModel implements EmbeddingModel {
        private final float[] queryVector;

        QueryModel(float[] queryVector) {
            this.queryVector = queryVector;
        }

        @Override
        public float[][] embed(List<String> texts) {
            float[][] out = new float[texts.size()][];
            for (int i = 0; i < texts.size(); i++) out[i] = queryVector.clone();
            return out;
        }

        @Override
        public int dimension() { return queryVector.length; }

        @Override
        public String modelName() { return "test-query-model"; }
    }

    private static Chunk chunk(String id, String text) {
        return new Chunk(id, "doc.pdf", 1, text);
    }

    @Test
    void mmrPrefersDiverseOverNearDuplicate() {
        InMemoryVectorIndex index = new InMemoryVectorIndex();
        index.add(chunk("dup1", "near duplicate one"), new float[]{0.9f, 0.1f, 0});
        index.add(chunk("dup2", "near duplicate two"), new float[]{0.85f, 0.3f, 0});
        index.add(chunk("diverse", "relevant but different"), new float[]{0.85f, -0.3f, 0});
        EmbeddingRetriever retriever = new EmbeddingRetriever(index, new QueryModel(new float[]{1, 0, 0}));

        List<RetrievedChunk> hits = retriever.retrieve("query", 2);

        assertEquals(2, hits.size());
        assertEquals("dup1", hits.get(0).docId());
        // dup2 is nearly redundant with dup1; MMR must pick the diverse chunk instead.
        assertEquals("diverse", hits.get(1).docId());
    }

    @Test
    void belowThresholdCandidatesYieldEmptyResult() {
        InMemoryVectorIndex index = new InMemoryVectorIndex();
        index.add(chunk("low", "unrelated"), new float[]{0.2f, 0.98f, 0});

        EmbeddingRetriever retriever = new EmbeddingRetriever(index, new QueryModel(new float[]{1, 0, 0}));

        assertTrue(retriever.retrieve("query", 8).isEmpty());
    }

    @Test
    void blankQueryYieldsEmptyResult() {
        InMemoryVectorIndex index = new InMemoryVectorIndex();
        index.add(chunk("a", "text"), new float[]{1, 0, 0});

        EmbeddingRetriever retriever = new EmbeddingRetriever(index, new QueryModel(new float[]{1, 0, 0}));

        assertTrue(retriever.retrieve("   ", 8).isEmpty());
        assertTrue(retriever.retrieve(null, 8).isEmpty());
    }

    @Test
    void chunkFieldsMapToRetrievedChunk() {
        InMemoryVectorIndex index = new InMemoryVectorIndex();
        index.add(new Chunk("Lec-9.pdf#3#0", "Lec-9.pdf", 3, "some slide text"), new float[]{1, 0, 0});

        EmbeddingRetriever retriever = new EmbeddingRetriever(index, new QueryModel(new float[]{1, 0, 0}));
        RetrievedChunk hit = retriever.retrieve("query", 3).get(0);

        assertEquals("Lec-9.pdf#3#0", hit.docId());
        assertEquals("Lec-9.pdf", hit.documentName());
        assertEquals(3, hit.slideNumber());
        assertEquals("some slide text", hit.text());
        assertEquals(1.0, hit.score(), 1e-6);
    }
}
