package edu.cst3492.taide.rag.index;

import edu.cst3492.taide.rag.Chunk;
import edu.cst3492.taide.rag.chunk.SlideAwareChunker;
import edu.cst3492.taide.rag.embed.EmbeddingModel;
import edu.cst3492.taide.rag.ingest.CourseMaterial;
import edu.cst3492.taide.rag.ingest.PdfIngestor;
import edu.cst3492.taide.rag.ingest.PptxIngestor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * End-to-end index construction: ingest -> chunk -> embed -> persist.
 * Rebuilds reuse vectors from the previous index file keyed by the chunk text hash,
 * so an unchanged corpus costs zero inference calls (cache hit well under 3s).
 */
public final class IndexBuilder {

    @FunctionalInterface
    public interface Progress {
        void update(double fraction, String detail);
    }

    public record Result(int documentCount, int chunkCount, int embeddedCount, int reusedCount) {}

    private static final Progress NOOP = (fraction, detail) -> { };

    private final EmbeddingModel model;

    public IndexBuilder(EmbeddingModel model) {
        this.model = model;
    }

    public Result build(Path materialsDir, Path indexPath, Progress progress) throws IOException {
        return build(materialsDir, indexPath, progress, () -> false);
    }

    /**
     * Builds the index; {@code isCancelled} is polled between chunks. Cancellation
     * preserves the previous index file because the save below is atomic.
     */
    public Result build(Path materialsDir, Path indexPath, Progress progress,
                        java.util.function.BooleanSupplier isCancelled) throws IOException {
        progress = progress == null ? NOOP : progress;

        Map<String, float[]> cache = loadCache(indexPath);

        progress.update(0.05, "ingest");
        List<Chunk> chunks = new ArrayList<>();
        List<CourseMaterial> materials = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(materialsDir)) {
            for (Path file : stream) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                CourseMaterial material = name.endsWith(".pdf") ? new PdfIngestor().ingest(file)
                        : name.endsWith(".pptx") ? new PptxIngestor().ingest(file)
                        : null;
                if (material == null) continue;
                materials.add(material);
                chunks.addAll(new SlideAwareChunker().chunk(material));
            }
        }
        if (chunks.isEmpty()) {
            throw new IOException("No PDF/PPTX materials found in " + materialsDir);
        }
        if (isCancelled.getAsBoolean()) {
            throw new IOException("Index build cancelled");
        }

        float[][] vectors = new float[chunks.size()][];
        List<String> pendingTexts = new ArrayList<>();
        List<Integer> pendingIndexes = new ArrayList<>();
        int reused = 0;
        for (int i = 0; i < chunks.size(); i++) {
            if (isCancelled.getAsBoolean()) {
                throw new IOException("Index build cancelled");
            }
            float[] cached = cache.get(sha256(chunks.get(i).text()));
            if (cached != null) {
                vectors[i] = cached;
                reused++;
            } else {
                pendingTexts.add(chunks.get(i).text());
                pendingIndexes.add(i);
            }
            progress.update(0.15 + 0.70 * (i + 1) / chunks.size(), "embed");
        }
        if (!pendingTexts.isEmpty()) {
            float[][] fresh = model.embed(pendingTexts);
            for (int j = 0; j < fresh.length; j++) {
                vectors[pendingIndexes.get(j)] = fresh[j];
            }
        }

        progress.update(0.95, "write");
        InMemoryVectorIndex index = new InMemoryVectorIndex();
        for (int i = 0; i < chunks.size(); i++) {
            index.add(chunks.get(i), vectors[i]);
        }
        index.save(indexPath, model.modelName());
        progress.update(1.0, "done");
        return new Result(materials.size(), chunks.size(), pendingTexts.size(), reused);
    }

    /** Previous index vectors keyed by the SHA-256 of the chunk text. */
    private Map<String, float[]> loadCache(Path indexPath) {
        Map<String, float[]> cache = new HashMap<>();
        if (!Files.exists(indexPath)) return cache;
        try {
            InMemoryVectorIndex old = InMemoryVectorIndex.load(indexPath);
            for (int i = 0; i < old.size(); i++) {
                cache.put(sha256(old.chunkAt(i).text()), old.vectorAt(i));
            }
        } catch (IOException e) {
            // Stale or corrupt index: rebuild from scratch, do not fail the build.
        }
        return cache;
    }

    static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
