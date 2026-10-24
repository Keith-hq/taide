package edu.cst3492.taide.rag.index;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import edu.cst3492.taide.rag.Chunk;
import edu.cst3492.taide.rag.chunk.SlideAwareChunker;
import edu.cst3492.taide.rag.embed.EmbeddingModel;
import edu.cst3492.taide.rag.ingest.CourseMaterial;
import edu.cst3492.taide.rag.ingest.PdfIngestor;
import edu.cst3492.taide.rag.ingest.PptxIngestor;

import java.io.IOException;
import java.io.Reader;
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
    private static final int EMBEDDING_BATCH_SIZE = 32;

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
        checkCancelled(isCancelled);

        Map<String, float[]> cache = loadCache(indexPath);

        progress.update(0.05, "ingest");
        List<Chunk> chunks = new ArrayList<>();
        List<CourseMaterial> materials = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(materialsDir)) {
            for (Path file : stream) {
                checkCancelled(isCancelled);
                if (!Files.isRegularFile(file)) continue;
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
        checkCancelled(isCancelled);

        float[][] vectors = new float[chunks.size()][];
        List<String> pendingTexts = new ArrayList<>();
        List<Integer> pendingIndexes = new ArrayList<>();
        int reused = 0;
        for (int i = 0; i < chunks.size(); i++) {
            checkCancelled(isCancelled);
            float[] cached = cache.get(sha256(chunks.get(i).text()));
            if (cached != null) {
                vectors[i] = cached;
                reused++;
            } else {
                pendingTexts.add(chunks.get(i).text());
                pendingIndexes.add(i);
            }
            progress.update(0.15 + 0.20 * (i + 1) / chunks.size(), "cache");
        }
        for (int start = 0; start < pendingTexts.size(); start += EMBEDDING_BATCH_SIZE) {
            checkCancelled(isCancelled);
            int end = Math.min(start + EMBEDDING_BATCH_SIZE, pendingTexts.size());
            float[][] fresh = model.embed(pendingTexts.subList(start, end));
            checkCancelled(isCancelled);
            for (int j = 0; j < fresh.length; j++) {
                vectors[pendingIndexes.get(start + j)] = fresh[j];
            }
            progress.update(0.35 + 0.55 * end / pendingTexts.size(), "embed");
        }

        progress.update(0.95, "write");
        InMemoryVectorIndex index = new InMemoryVectorIndex();
        for (int i = 0; i < chunks.size(); i++) {
            index.add(chunks.get(i), vectors[i]);
        }
        checkCancelled(isCancelled); // cancellation before publication preserves the last good index
        index.save(indexPath, model.modelName());
        progress.update(1.0, "done");
        return new Result(materials.size(), chunks.size(), pendingTexts.size(), reused);
    }

    private static void checkCancelled(java.util.function.BooleanSupplier cancelled) throws IOException {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) {
            throw new IOException("Index build cancelled");
        }
    }

    /** Previous index vectors keyed by the SHA-256 of the chunk text. */
    private Map<String, float[]> loadCache(Path indexPath) {
        Map<String, float[]> cache = new HashMap<>();
        if (!Files.exists(indexPath)) return cache;
        try (Reader reader = Files.newBufferedReader(indexPath, StandardCharsets.UTF_8)) {
            InMemoryVectorIndex.IndexFile old = new Gson().fromJson(reader, InMemoryVectorIndex.IndexFile.class);
            if (old == null || old.version != InMemoryVectorIndex.FORMAT_VERSION
                    || !model.modelName().equals(old.model) || old.dim != model.dimension()
                    || old.chunks == null) return Map.of();
            for (InMemoryVectorIndex.ChunkEntry entry : old.chunks) {
                if (entry == null || entry.text == null || entry.vec == null
                        || entry.vec.length != model.dimension()) return Map.of();
                for (float value : entry.vec) if (!Float.isFinite(value)) return Map.of();
                cache.put(sha256(entry.text), entry.vec);
            }
        } catch (IOException | JsonParseException e) {
            // Stale or corrupt index: rebuild from scratch, do not fail the build.
            return Map.of();
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
