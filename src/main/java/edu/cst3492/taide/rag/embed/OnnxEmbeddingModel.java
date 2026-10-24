package edu.cst3492.taide.rag.embed;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OnnxTensor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * all-MiniLM-L6-v2 via ONNX Runtime on CPU. The exported model returns raw hidden
 * states, so vectors are mean-pooled over real tokens and L2-normalized, matching
 * the sentence-transformers reference (so dot product equals cosine similarity).
 */
public final class OnnxEmbeddingModel implements EmbeddingModel {

    public static final String MODEL_NAME = "all-MiniLM-L6-v2";
    public static final int DIMENSION = 384;
    private static final int BATCH_SIZE = 32;

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final BertTokenizer tokenizer;

    public OnnxEmbeddingModel(Path modelPath, Path vocabPath) throws IOException {
        try {
            this.environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors());
            this.session = environment.createSession(modelPath.toString(), options);
        } catch (OrtException e) {
            throw new IOException("Failed to load embedding model " + modelPath, e);
        }
        this.tokenizer = new BertTokenizer(vocabPath);
    }

    /**
     * Dev-time model location: {@code taide.model.dir} system property, else the
     * TAIDE_MODEL_DIR env var, else ./taide-models/all-MiniLM-L6-v2.
     */
    public static Path defaultModelDir() {
        String property = System.getProperty("taide.model.dir");
        String env = System.getenv("TAIDE_MODEL_DIR");
        String value = property != null && !property.isBlank() ? property
                : env != null && !env.isBlank() ? env : null;
        return value != null ? Path.of(value) : Path.of("taide-models/all-MiniLM-L6-v2");
    }

    public static OnnxEmbeddingModel openDefault() throws IOException {
        Path dir = defaultModelDir();
        Path model = dir.resolve("model.onnx");
        Path vocab = dir.resolve("vocab.txt");
        if (!Files.exists(model) || !Files.exists(vocab)) {
            throw new IOException("Model files not found in " + dir
                    + " (set TAIDE_MODEL_DIR; expected model.onnx and vocab.txt)");
        }
        return new OnnxEmbeddingModel(model, vocab);
    }

    @Override
    public int dimension() {
        return DIMENSION;
    }

    @Override
    public String modelName() {
        return MODEL_NAME;
    }

    @Override
    public float[][] embed(List<String> texts) throws IOException {
        float[][] result = new float[texts.size()][];
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, texts.size());
            float[][] batch = embedBatch(texts.subList(start, end));
            System.arraycopy(batch, 0, result, start, batch.length);
        }
        return result;
    }

    private float[][] embedBatch(List<String> texts) throws IOException {
        int maxLength = 0;
        int[][] ids = new int[texts.size()][];
        for (int i = 0; i < texts.size(); i++) {
            ids[i] = tokenizer.tokenize(texts.get(i)).stream().mapToInt(Integer::intValue).toArray();
            maxLength = Math.max(maxLength, ids[i].length);
        }
        long[][] inputIds = new long[texts.size()][maxLength];
        long[][] attentionMask = new long[texts.size()][maxLength];
        long[][] tokenTypes = new long[texts.size()][maxLength];
        for (int i = 0; i < texts.size(); i++) {
            for (int j = 0; j < ids[i].length; j++) {
                inputIds[i][j] = ids[i][j];
                attentionMask[i][j] = 1;
            }
        }
        try (OnnxTensor idsTensor = OnnxTensor.createTensor(environment, inputIds);
             OnnxTensor maskTensor = OnnxTensor.createTensor(environment, attentionMask);
             OnnxTensor typesTensor = OnnxTensor.createTensor(environment, tokenTypes);
             OrtSession.Result run = session.run(Map.of(
                     "input_ids", idsTensor, "attention_mask", maskTensor, "token_type_ids", typesTensor))) {
            float[][][] hidden = (float[][][]) run.get(0).getValue();
            float[][] vectors = new float[texts.size()][];
            for (int i = 0; i < texts.size(); i++) {
                vectors[i] = meanPool(hidden[i], attentionMask[i]);
            }
            return vectors;
        } catch (OrtException e) {
            throw new IOException("Embedding inference failed", e);
        }
    }

    private static float[] meanPool(float[][] hidden, long[] mask) {
        float[] vector = new float[DIMENSION];
        long count = 0;
        for (int t = 0; t < hidden.length; t++) {
            if (mask[t] == 0) continue;
            for (int d = 0; d < DIMENSION; d++) {
                vector[d] += hidden[t][d];
            }
            count++;
        }
        double norm = 0;
        for (int d = 0; d < DIMENSION; d++) {
            vector[d] = (float) (vector[d] / count);
            norm += (double) vector[d] * vector[d];
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int d = 0; d < DIMENSION; d++) {
                vector[d] = (float) (vector[d] / norm);
            }
        }
        return vector;
    }
}
