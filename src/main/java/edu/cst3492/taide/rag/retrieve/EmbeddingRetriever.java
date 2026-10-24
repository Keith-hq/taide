package edu.cst3492.taide.rag.retrieve;

import edu.cst3492.taide.rag.Retriever;
import edu.cst3492.taide.rag.RetrievedChunk;
import edu.cst3492.taide.rag.embed.EmbeddingModel;
import edu.cst3492.taide.rag.index.InMemoryVectorIndex;
import edu.cst3492.taide.rag.index.VectorIndex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * README pipeline stages 5-6: embed the query, take top candidates, drop everything
 * below {@link #SIMILARITY_THRESHOLD}, then MMR-deduplicate down to k results.
 * An empty result means "no course material is relevant" — the service layer turns
 * that into the grounded=false, no-citation answer mode.
 */
public final class EmbeddingRetriever implements Retriever {

    /** Tuned on the 20-question eval set (see TEAM A progress notes). */
    public static final double SIMILARITY_THRESHOLD = 0.45;
    /** MMR trade-off: higher favors relevance, lower favors diversity. */
    public static final double MMR_LAMBDA = 0.7;
    private static final int CANDIDATE_MULTIPLIER = 3;

    private final VectorIndex index;
    private final EmbeddingModel model;

    public EmbeddingRetriever(VectorIndex index, EmbeddingModel model) {
        this.index = index;
        this.model = model;
    }

    @Override
    public List<RetrievedChunk> retrieve(String query, int k) {
        if (query == null || query.isBlank()) return List.of();
        float[] queryVector = embed(query);
        int candidates = Math.max(k * CANDIDATE_MULTIPLIER, 16);
        List<VectorIndex.ScoredChunk> pool = new ArrayList<>();
        for (VectorIndex.ScoredChunk hit : index.search(queryVector, candidates)) {
            if (hit.score() >= SIMILARITY_THRESHOLD) {
                pool.add(hit);
            }
        }
        if (pool.isEmpty()) return List.of();
        return mmr(queryVector, pool, Math.min(k, pool.size())).stream()
                .map(hit -> new RetrievedChunk(
                        hit.chunk().id(),
                        hit.chunk().documentName(),
                        hit.chunk().slideNumber(),
                        hit.chunk().text(),
                        hit.score()))
                .toList();
    }

    /**
     * Maximal Marginal Relevance: greedily pick chunks that are relevant to the query
     * but not redundant with chunks already picked.
     */
    static List<VectorIndex.ScoredChunk> mmr(float[] query, List<VectorIndex.ScoredChunk> pool, int k) {
        List<VectorIndex.ScoredChunk> selected = new ArrayList<>();
        boolean[] used = new boolean[pool.size()];
        for (int step = 0; step < k; step++) {
            int best = -1;
            double bestValue = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < pool.size(); i++) {
                if (used[i]) continue;
                double redundancy = 0;
                for (VectorIndex.ScoredChunk picked : selected) {
                    redundancy = Math.max(redundancy,
                            InMemoryVectorIndex.cosine(pool.get(i).vector(), picked.vector()));
                }
                double value = MMR_LAMBDA * pool.get(i).score() - (1 - MMR_LAMBDA) * redundancy;
                if (value > bestValue) {
                    bestValue = value;
                    best = i;
                }
            }
            if (best < 0) break;
            used[best] = true;
            selected.add(pool.get(best));
        }
        return selected;
    }

    private float[] embed(String query) {
        try {
            return model.embed(List.of(query))[0];
        } catch (IOException e) {
            throw new IllegalStateException("Query embedding failed", e);
        }
    }
}
