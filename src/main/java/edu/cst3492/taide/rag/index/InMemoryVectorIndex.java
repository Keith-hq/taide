package edu.cst3492.taide.rag.index;

import com.google.gson.Gson;
import edu.cst3492.taide.rag.Chunk;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Cosine-similarity index held in memory, serialized to the TEAM §4.5 JSON schema:
 * {@code {version, model, dim, chunks: [{id, doc, slide, text, vec}]}}.
 * The index is one file, not a database — thousands of chunks fit in memory and the
 * whole pipeline stays inspectable with a text editor.
 */
public final class InMemoryVectorIndex implements VectorIndex {

    public static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new Gson();

    private final List<Chunk> chunks = new ArrayList<>();
    private final List<float[]> vectors = new ArrayList<>();
    private int dimension;

    /** JSON file schema — field names are the frozen §4.5 contract. */
    public static final class IndexFile {
        public int version;
        public String model;
        public int dim;
        public List<ChunkEntry> chunks;
    }

    public static final class ChunkEntry {
        public String id;
        public String doc;
        public int slide;
        public String text;
        public float[] vec;
    }

    public void add(Chunk chunk, float[] vector) {
        if (dimension == 0) {
            dimension = vector.length;
        } else if (vector.length != dimension) {
            throw new IllegalArgumentException("vector dimension mismatch");
        }
        chunks.add(chunk);
        vectors.add(vector);
    }

    @Override
    public int size() {
        return chunks.size();
    }

    @Override
    public List<ScoredChunk> search(float[] query, int k) {
        if (chunks.isEmpty()) return List.of();
        if (query.length != dimension) {
            throw new IllegalArgumentException("query dimension mismatch");
        }
        PriorityQueue<ScoredChunk> top = new PriorityQueue<>(
                (a, b) -> Double.compare(a.score(), b.score()));
        for (int i = 0; i < chunks.size(); i++) {
            top.add(new ScoredChunk(chunks.get(i), cosine(query, vectors.get(i))));
            if (top.size() > k) top.poll();
        }
        List<ScoredChunk> result = new ArrayList<>(top);
        result.sort((a, b) -> Double.compare(b.score(), a.score()));
        return result;
    }

    /** Cosine similarity of two vectors; NaN-safe. */
    public static double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public void save(Path path, String modelName) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        IndexFile file = new IndexFile();
        file.version = FORMAT_VERSION;
        file.model = modelName;
        file.dim = dimension;
        file.chunks = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            ChunkEntry entry = new ChunkEntry();
            entry.id = chunks.get(i).id();
            entry.doc = chunks.get(i).documentName();
            entry.slide = chunks.get(i).slideNumber();
            entry.text = chunks.get(i).text();
            entry.vec = vectors.get(i);
            file.chunks.add(entry);
        }
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            GSON.toJson(file, writer);
        }
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static InMemoryVectorIndex load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            IndexFile file = GSON.fromJson(reader, IndexFile.class);
            if (file.version != FORMAT_VERSION) {
                throw new IOException("Unsupported index version " + file.version);
            }
            InMemoryVectorIndex index = new InMemoryVectorIndex();
            if (file.chunks != null) {
                for (ChunkEntry entry : file.chunks) {
                    index.add(new Chunk(entry.id, entry.doc, entry.slide, entry.text), entry.vec);
                }
            }
            return index;
        }
    }

    /** Read-only accessors used by IndexBuilder to reuse cached vectors. */
    Chunk chunkAt(int i) {
        return chunks.get(i);
    }

    float[] vectorAt(int i) {
        return vectors.get(i);
    }
}
