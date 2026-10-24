package edu.cst3492.taide.rag.embed;

import java.io.IOException;
import java.util.List;

/** Turns chunk texts into vectors. Implementations must be safe for background threads. */
public interface EmbeddingModel {

    /** Embeds texts in input order; returns {@code float[texts.size()][dimension]}. */
    float[][] embed(List<String> texts) throws IOException;

    int dimension();

    String modelName();
}
