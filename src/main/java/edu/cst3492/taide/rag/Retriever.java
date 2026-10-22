package edu.cst3492.taide.rag;

import java.util.List;

public interface Retriever {
    List<RetrievedChunk> retrieve(String query, int k);
}
