package edu.cst3492.taide.llm;

import edu.cst3492.taide.rag.embed.OnnxEmbeddingModel;
import edu.cst3492.taide.rag.index.IndexBuilder;
import edu.cst3492.taide.rag.index.InMemoryVectorIndex;
import edu.cst3492.taide.rag.retrieve.EmbeddingRetriever;
import edu.cst3492.taide.rag.retrieve.RetrievalEvaluator;
import edu.cst3492.taide.service.CodeContext;
import edu.cst3492.taide.service.PromptLibrary;
import edu.cst3492.taide.service.TaAnswer;
import edu.cst3492.taide.service.TaideServiceCore;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A-4 acceptance against the REAL DeepSeek API. Skipped when no key is available
 * (TAIDE_LLM_API_KEY env / taide.llm.api.key property / ~/.taide-api-key file).
 * Run with TAIDE_MODEL_DIR + TAIDE_MATERIALS_DIR + -PliveModelTests=true.
 */
@Tag("live-model")
class RealLlmLiveTest {

    static String apiKey() {
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

    @Test
    void deepSeekRoundTripCompleteAndStreaming() {
        String key = apiKey();
        Assumptions.assumeTrue(key != null, "no DeepSeek key: set TAIDE_LLM_API_KEY or ~/.taide-api-key");
        OpenAiCompatibleLlmClient client =
                new OpenAiCompatibleLlmClient("https://api.deepseek.com", key, "deepseek-chat");

        String reply = client.complete("Reply with exactly: TAIDE_OK", "ping", 0);
        assertTrue(reply.contains("TAIDE_OK"), "unexpected reply: " + reply);

        StringBuilder tokens = new StringBuilder();
        client.completeStreaming("You are a counter. Count: one, two, three.", "count please", tokens::append);
        assertFalse(tokens.toString().isBlank());
    }

    @Test
    void realEndToEndGroundedAnswer() throws Exception {
        String key = apiKey();
        Assumptions.assumeTrue(key != null, "no DeepSeek key: set TAIDE_LLM_API_KEY or ~/.taide-api-key");
        Path materials;
        try {
            materials = RetrievalEvaluator.resolveMaterialsDir();
        } catch (IllegalStateException e) {
            Assumptions.abort("course materials not configured");
            return;
        }
        Path indexPath = Files.createTempDirectory("taide-real").resolve("index.json");
        OnnxEmbeddingModel model = OnnxEmbeddingModel.openDefault();
        new IndexBuilder(model).build(materials, indexPath, null);
        EmbeddingRetriever retriever = new EmbeddingRetriever(InMemoryVectorIndex.load(indexPath), model);
        OpenAiCompatibleLlmClient client =
                new OpenAiCompatibleLlmClient("https://api.deepseek.com", key, "deepseek-chat");
        TaideServiceCore core = new TaideServiceCore(retriever, client, new PromptLibrary(), "deepseek-chat");

        TaAnswer grounded = core.ask("What is a chained exception?", CodeContext.empty());
        assertTrue(grounded.grounded());
        assertFalse(grounded.citations().isEmpty());
        assertTrue(grounded.text().length() > 20, "real answer should be substantive");

        TaAnswer ungrounded = core.ask("How do I center a div with CSS Flexbox?", CodeContext.empty());
        assertFalse(ungrounded.grounded());
        assertTrue(ungrounded.text().contains("general knowledge"),
                "general-knowledge mode must state the required sentence");
    }
}
