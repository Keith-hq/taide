package edu.cst3492.taide.ui;

import com.google.gson.Gson;
import edu.cst3492.taide.rag.embed.OnnxEmbeddingModel;
import edu.cst3492.taide.rag.index.InMemoryVectorIndex;
import edu.cst3492.taide.ui.settings.SettingsSnapshot;
import edu.cst3492.taide.ui.settings.SettingsValidation;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CancellationException;

/** Local readiness only: this check never calls a model provider or builds an embedding session. */
final class ProjectSetupChecker {
    record Result(boolean apiReady, boolean indexReady, String detail) { }
    private static final Gson GSON = new Gson();

    static Result check(SettingsSnapshot value, boolean hasKey, Path indexPath) {
        interrupted();
        SettingsSnapshot live = new SettingsSnapshot(value.provider(), value.baseUrl(), value.model(), "", false);
        boolean validApi = SettingsValidation.validate(live, null).isEmpty();
        boolean apiReady = validApi && hasKey;
        String indexProblem = indexProblem(value.courseDirectory(), indexPath);
        if (!validApi) return new Result(false, indexProblem == null, "Enter a valid API base URL and model ID in Settings.");
        if (!hasKey) return new Result(false, indexProblem == null, "Save an API key for the current endpoint in Settings.");
        if (indexProblem != null) return new Result(apiReady, false, indexProblem);
        return new Result(true, true, "API settings saved and material index readable. API connection not tested yet.");
    }

    private static String indexProblem(String directory, Path indexPath) {
        if (directory.isBlank()) return "Choose a materials folder in Settings, apply it, then rebuild the knowledge base.";
        try {
            Path root = Path.of(directory).toAbsolutePath().normalize();
            if (!Files.isDirectory(root) || !Files.isReadable(root)) return "The materials folder cannot be read. Choose another folder.";
            if (!Files.isRegularFile(indexPath) || !Files.isReadable(indexPath)) return "Click Rebuild Knowledge Base in Settings.";
            InMemoryVectorIndex.IndexFile index;
            try (Reader reader = Files.newBufferedReader(indexPath, StandardCharsets.UTF_8)) {
                index = GSON.fromJson(reader, InMemoryVectorIndex.IndexFile.class);
            }
            interrupted();
            if (index == null || index.version != InMemoryVectorIndex.FORMAT_VERSION
                    || !OnnxEmbeddingModel.MODEL_NAME.equals(index.model) || index.dim != OnnxEmbeddingModel.DIMENSION
                    || index.chunks == null || index.chunks.isEmpty()) return "Knowledge base format mismatch. Rebuild the knowledge base.";
            for (var entry : index.chunks) {
                interrupted();
                if (entry == null || entry.doc == null || entry.doc.isBlank() || entry.slide < 1
                        || entry.text == null || entry.text.isBlank() || entry.vec == null || entry.vec.length != index.dim)
                    return "Knowledge base incomplete. Rebuild the knowledge base.";
                for (float number : entry.vec) if (!Float.isFinite(number)) return "Knowledge base vectors are invalid. Rebuild the knowledge base.";
                Path document = root.resolve(entry.doc).normalize();
                if (!document.getParent().equals(root) || !Files.isRegularFile(document) || !Files.isReadable(document))
                    return "The materials folder does not match the current index. Rebuild the knowledge base.";
            }
            var built = Files.getLastModifiedTime(indexPath);
            try (var stream = Files.newDirectoryStream(root)) {
                for (Path file : stream) {
                    interrupted();
                    String name = file.getFileName().toString();
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (!Files.isRegularFile(file) || !(lower.endsWith(".pdf") || lower.endsWith(".pptx"))) continue;
                    if (Files.getLastModifiedTime(file).compareTo(built) > 0) return "Materials have changed. Rebuild the knowledge base.";
                }
            }
            // A readable course file can yield no chunks (for example whitespace-only pages).
            // Referenced files and modification times are reliable checks; exact file-set equality is not.
            return null;
        } catch (CancellationException cancellation) {
            throw cancellation;
        } catch (IOException | RuntimeException failure) {
            return "Materials or knowledge base cannot be read. Check the folder and rebuild the knowledge base.";
        }
    }

    private static void interrupted() { if (Thread.currentThread().isInterrupted()) throw new CancellationException(); }
}
