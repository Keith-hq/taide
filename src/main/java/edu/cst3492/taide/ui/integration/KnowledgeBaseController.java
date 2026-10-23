package edu.cst3492.taide.ui.integration;

import java.nio.file.Path;

/**
 * Optional A-to-B adapter. A registers its implementation as a project service with this
 * interface in plugin.xml. No implementation means Rebuild is explicitly unavailable.
 */
public interface KnowledgeBaseController {
    /**
     * Blocking; invoked on a background thread. Implementations call cancellation.checkCanceled()
     * between chunks and publish the completed index atomically so cancellation preserves the old one.
     */
    RebuildResult rebuild(Path materialsDirectory, ProgressListener progress, Cancellation cancellation) throws Exception;

    @FunctionalInterface interface ProgressListener {
        /** fraction is in [0,1], or -1 for indeterminate progress. */
        void update(double fraction, String detail);
    }

    @FunctionalInterface interface Cancellation { void checkCanceled(); }

    record RebuildResult(int documentCount, int chunkCount) {
        public RebuildResult {
            if (documentCount < 0 || chunkCount < 0) throw new IllegalArgumentException("Counts cannot be negative");
        }
    }
}
