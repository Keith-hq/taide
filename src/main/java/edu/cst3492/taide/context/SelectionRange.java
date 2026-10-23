package edu.cst3492.taide.context;

import java.util.function.IntUnaryOperator;

/** An editor selection uses an inclusive start and an exclusive end. */
public record SelectionRange(int startOffset, int endOffset) {
    public SelectionRange {
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException("Invalid selection offsets");
        }
    }

    /** Normalizes reversed endpoints and clamps stale offsets to the current document. */
    public static SelectionRange fromOffsets(int first, int second, int documentLength) {
        if (documentLength < 0) {
            throw new IllegalArgumentException("Document length must not be negative");
        }
        int start = Math.max(0, Math.min(documentLength, Math.min(first, second)));
        int end = Math.max(0, Math.min(documentLength, Math.max(first, second)));
        return new SelectionRange(start, end);
    }

    public boolean isEmpty() {
        return startOffset == endOffset;
    }

    /** User-facing lines are one-based; an absent selection has no line (0). */
    public int startLine(IntUnaryOperator zeroBasedLineAtOffset) {
        return isEmpty() ? 0 : zeroBasedLineAtOffset.applyAsInt(startOffset) + 1;
    }

    /** The next line is excluded when the selection ends at that line's first column. */
    public int endLine(IntUnaryOperator zeroBasedLineAtOffset) {
        return isEmpty() ? 0 : zeroBasedLineAtOffset.applyAsInt(endOffset - 1) + 1;
    }
}
