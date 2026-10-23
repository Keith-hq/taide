package edu.cst3492.taide.rag;

public record RetrievedChunk(
        String docId,
        String documentName,
        int slideNumber,
        String text,
        double score
) {}
