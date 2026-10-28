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
 * Prompt v1.1 formatting acceptance against the real model: answers render as
 * clean plain text — no Markdown constructs, no excessive quotation marks.
 * Run with the DeepSeek key + corpus (live-model tag).
 */
@Tag("live-model")
class TaideAnswerFormatLiveTest {

    @Test
    void groundedAnswerIsPlainTextWithoutMarkdown() throws Exception {
        String key = LiveTestSupport.apiKey();
        Assumptions.assumeTrue(key != null, "no DeepSeek key");
        TaideServiceCore core = LiveTestSupport.buildCore(key);

        TaAnswer answer = core.ask("What is a chained exception?", CodeContext.empty());

        assertTrue(answer.grounded());
        assertFalse(answer.text().contains("```"), "code fences are markdown: " + answer.text());
        assertFalse(answer.text().contains("**"), "bold markers are markdown: " + answer.text());
        assertFalse(answer.text().matches("(?s).*\\n#{1,6} .*"), "headings are markdown: " + answer.text());
        long quotes = answer.text().chars().filter(c -> c == '"').count();
        assertTrue(quotes <= 1, "too many quotation marks (" + quotes + "): " + answer.text());
    }

    @Test
    void ungroundedAnswerIsPlainTextWithRequiredSentence() throws Exception {
        String key = LiveTestSupport.apiKey();
        Assumptions.assumeTrue(key != null, "no DeepSeek key");
        TaideServiceCore core = LiveTestSupport.buildCore(key);

        TaAnswer answer = core.ask("How do I center a div with CSS Flexbox?", CodeContext.empty());

        assertFalse(answer.grounded());
        assertTrue(answer.text().contains("general knowledge"));
        assertFalse(answer.text().contains("```"));
        assertFalse(answer.text().contains("**"));
    }
}
