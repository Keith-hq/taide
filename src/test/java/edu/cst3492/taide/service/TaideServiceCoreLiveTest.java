package edu.cst3492.taide.service;

import edu.cst3492.taide.llm.LlmClient;
import edu.cst3492.taide.rag.embed.OnnxEmbeddingModel;
import edu.cst3492.taide.rag.index.IndexBuilder;
import edu.cst3492.taide.rag.index.InMemoryVectorIndex;
import edu.cst3492.taide.rag.retrieve.EmbeddingRetriever;
import edu.cst3492.taide.rag.retrieve.RetrievalEvaluator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end A-5 acceptance with the REAL retrieval chain (model + corpus) and a
 * stub LLM: grounded answers carry course citations, off-corpus questions do not.
 * Run with TAIDE_MODEL_DIR + TAIDE_MATERIALS_DIR + -PliveModelTests=true.
 */
@Tag("live-model")
class TaideServiceCoreLiveTest {

    static final class EchoLlm implements LlmClient {
        @Override
        public String complete(String systemPrompt, String userPrompt, double temperature) {
            return "echo";
        }

        @Override
        public void completeStreaming(String systemPrompt, String userPrompt, Consumer<String> onToken) {
            onToken.accept("echo");
        }
    }

    @Test
    void groundedAndUngroundedPathsWorkEndToEnd() throws Exception {
        Path materials = RetrievalEvaluator.resolveMaterialsDir();
        Path indexPath = Files.createTempDirectory("taide-service").resolve("index.json");
        OnnxEmbeddingModel model = OnnxEmbeddingModel.openDefault();
        new IndexBuilder(model).build(materials, indexPath, null);
        EmbeddingRetriever retriever = new EmbeddingRetriever(InMemoryVectorIndex.load(indexPath), model);
        TaideServiceCore core = new TaideServiceCore(retriever, new EchoLlm(), new PromptLibrary(), "deepseek-chat");

        TaAnswer grounded = core.ask("What is a chained exception?", CodeContext.empty());
        assertTrue(grounded.grounded());
        assertFalse(grounded.citations().isEmpty());
        assertTrue(grounded.citations().stream()
                .anyMatch(c -> c.documentName().equals("Lec-3.1-Exceptions.pdf") && c.slideNumber() == 9));

        TaAnswer ungrounded = core.ask("How do I center a div with CSS Flexbox?", CodeContext.empty());
        assertFalse(ungrounded.grounded());
        assertTrue(ungrounded.citations().isEmpty());
    }
}
