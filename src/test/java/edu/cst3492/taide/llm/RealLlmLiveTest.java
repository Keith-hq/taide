package edu.cst3492.taide.llm;

import edu.cst3492.taide.service.CodeContext;
import edu.cst3492.taide.service.TaAnswer;
import edu.cst3492.taide.service.TaideServiceCore;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A-4 acceptance against the REAL DeepSeek API. Skipped when no key is available
 * (TAIDE_LLM_API_KEY env / taide.llm.api.key property / ~/.taide-api-key file).
 * Run with TAIDE_MODEL_DIR + TAIDE_MATERIALS_DIR + -PliveModelTests=true.
 */
@Tag("live-model")
class RealLlmLiveTest {

    @Test
    void deepSeekRoundTripCompleteAndStreaming() {
        String key = LiveTestSupport.apiKey();
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
        String key = LiveTestSupport.apiKey();
        Assumptions.assumeTrue(key != null, "no DeepSeek key: set TAIDE_LLM_API_KEY or ~/.taide-api-key");
        TaideServiceCore core;
        try {
            core = LiveTestSupport.buildCore(key);
        } catch (IllegalStateException e) {
            Assumptions.abort("course materials not configured");
            return;
        }

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
