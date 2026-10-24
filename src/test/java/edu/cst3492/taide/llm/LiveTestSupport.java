package edu.cst3492.taide.llm;

import edu.cst3492.taide.rag.embed.OnnxEmbeddingModel;
import edu.cst3492.taide.rag.index.IndexBuilder;
import edu.cst3492.taide.rag.index.InMemoryVectorIndex;
import edu.cst3492.taide.rag.retrieve.EmbeddingRetriever;
import edu.cst3492.taide.rag.retrieve.RetrievalEvaluator;
import edu.cst3492.taide.service.PromptLibrary;
import edu.cst3492.taide.service.TaideServiceCore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared setup for live-model tests: API key resolution and real-core assembly. */
public final class LiveTestSupport {

    private LiveTestSupport() {
    }

    public static String apiKey() {
        String env = System.getenv("TAIDE_LLM_API_KEY");
        if (env != null && !env.isBlank()) return env;
        String property = System.getProperty("taide.llm.api.key");
        if (property != null && !property.isBlank()) return property;
        Path file = Path.of(System.getProperty("user.home"), ".taide-api-key");
        try {
            if (Files.exists(file)) {
                String value = Files.readString(file, StandardCharsets.UTF_8).strip();
                if (!value.isBlank()) return value;
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    /** Builds a core over the real corpus + real DeepSeek client; throws if the environment is missing. */
    public static TaideServiceCore buildCore(String apiKey) throws Exception {
        Path materials = RetrievalEvaluator.resolveMaterialsDir();
        Path indexPath = Files.createTempDirectory("taide-live").resolve("index.json");
        OnnxEmbeddingModel model = OnnxEmbeddingModel.openDefault();
        new IndexBuilder(model).build(materials, indexPath, null);
        EmbeddingRetriever retriever = new EmbeddingRetriever(InMemoryVectorIndex.load(indexPath), model);
        OpenAiCompatibleLlmClient client =
                new OpenAiCompatibleLlmClient("https://api.deepseek.com", apiKey, "deepseek-chat");
        return new TaideServiceCore(retriever, client, new PromptLibrary(), "deepseek-chat");
    }
}
