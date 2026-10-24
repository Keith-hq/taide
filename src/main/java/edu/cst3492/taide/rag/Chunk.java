package edu.cst3492.taide.rag;

/**
 * One indexable piece of course material. Chunks never cross a page/slide boundary,
 * so {@link #slideNumber} is always exact (1-based, matching PDF reader display).
 */
public record Chunk(String id, String documentName, int slideNumber, String text) {

    public Chunk {
        if (slideNumber < 1) throw new IllegalArgumentException("slideNumber must be 1-based");
    }
}
