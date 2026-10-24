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
        if (!validApi) return new Result(false, indexProblem == null, "请在设置中填写有效的 API 地址和模型名称。");
        if (!hasKey) return new Result(false, indexProblem == null, "请在设置中保存当前 API 地址对应的 API Key。");
        if (indexProblem != null) return new Result(apiReady, false, indexProblem);
        return new Result(true, true, "API 配置已保存，课件索引可读取；尚未测试 API 联网。");
    }

    private static String indexProblem(String directory, Path indexPath) {
        if (directory.isBlank()) return "请在设置中选择课件目录，应用后重建知识库。";
        try {
            Path root = Path.of(directory).toAbsolutePath().normalize();
            if (!Files.isDirectory(root) || !Files.isReadable(root)) return "课件目录不可读取，请重新选择。";
            if (!Files.isRegularFile(indexPath) || !Files.isReadable(indexPath)) return "请在设置中点击重建知识库。";
            InMemoryVectorIndex.IndexFile index;
            try (Reader reader = Files.newBufferedReader(indexPath, StandardCharsets.UTF_8)) {
                index = GSON.fromJson(reader, InMemoryVectorIndex.IndexFile.class);
            }
            interrupted();
            if (index == null || index.version != InMemoryVectorIndex.FORMAT_VERSION
                    || !OnnxEmbeddingModel.MODEL_NAME.equals(index.model) || index.dim != OnnxEmbeddingModel.DIMENSION
                    || index.chunks == null || index.chunks.isEmpty()) return "知识库格式不匹配，请重建知识库。";
            for (var entry : index.chunks) {
                interrupted();
                if (entry == null || entry.doc == null || entry.doc.isBlank() || entry.slide < 1
                        || entry.text == null || entry.text.isBlank() || entry.vec == null || entry.vec.length != index.dim)
                    return "知识库内容不完整，请重建知识库。";
                for (float number : entry.vec) if (!Float.isFinite(number)) return "知识库向量无效，请重建知识库。";
                Path document = root.resolve(entry.doc).normalize();
                if (!document.getParent().equals(root) || !Files.isRegularFile(document) || !Files.isReadable(document))
                    return "课件目录与现有索引不一致，请重建知识库。";
            }
            var built = Files.getLastModifiedTime(indexPath);
            try (var stream = Files.newDirectoryStream(root)) {
                for (Path file : stream) {
                    interrupted();
                    String name = file.getFileName().toString();
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (!Files.isRegularFile(file) || !(lower.endsWith(".pdf") || lower.endsWith(".pptx"))) continue;
                    if (Files.getLastModifiedTime(file).compareTo(built) > 0) return "课件已更新，请重建知识库。";
                }
            }
            // A readable course file can yield no chunks (for example whitespace-only pages).
            // Referenced files and modification times are reliable checks; exact file-set equality is not.
            return null;
        } catch (CancellationException cancellation) {
            throw cancellation;
        } catch (IOException | RuntimeException failure) {
            return "课件或知识库不可读取，请检查目录后重建知识库。";
        }
    }

    private static void interrupted() { if (Thread.currentThread().isInterrupted()) throw new CancellationException(); }
}
