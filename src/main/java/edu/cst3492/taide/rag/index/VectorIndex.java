package edu.cst3492.taide.rag.index;

import edu.cst3492.taide.rag.Chunk;

import java.util.List;

/**
 * Searchable set of chunk vectors. This is the seam TEAM.md mentions: if the corpus
 * ever outgrows memory, a SQLite-backed implementation can replace the in-memory one
 * without touching the rest of the pipeline.
 */
public interface VectorIndex {

    record ScoredChunk(Chunk chunk, double score) {}

    /** Returns up to {@code k} chunks closest to the query vector, best first. */
    List<ScoredChunk> search(float[] query, int k);

    int size();
}
