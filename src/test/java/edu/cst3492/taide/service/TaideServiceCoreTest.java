package edu.cst3492.taide.service;

import edu.cst3492.taide.llm.LlmClient;
import edu.cst3492.taide.rag.Retriever;
import edu.cst3492.taide.rag.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaideServiceCoreTest {

    static final class StubRetriever implements Retriever {
        private final List<RetrievedChunk> chunks;

        StubRetriever(List<RetrievedChunk> chunks) {
            this.chunks = chunks;
        }

        @Override
        public List<RetrievedChunk> retrieve(String query, int k) {
            return chunks;
        }
    }

    static final class StubLlm implements LlmClient {
        String lastSystemPrompt;
        String lastUserPrompt;

        @Override
        public String complete(String systemPrompt, String userPrompt, double temperature) {
            lastSystemPrompt = systemPrompt;
            lastUserPrompt = userPrompt;
            return "stub answer";
        }

        @Override
        public void completeStreaming(String systemPrompt, String userPrompt, Consumer<String> onToken) {
            lastSystemPrompt = systemPrompt;
            lastUserPrompt = userPrompt;
            for (String token : new String[]{"stub", " ", "answer"}) {
                onToken.accept(token);
            }
        }
    }

    private static TaideServiceCore core(Retriever retriever, StubLlm llm) throws Exception {
        return new TaideServiceCore(retriever, llm, new PromptLibrary(), "stub-model");
    }

    private static RetrievedChunk chunk(String doc, int slide, String text) {
        return new RetrievedChunk(doc + "#" + slide + "#0", doc, slide, text, 0.9);
    }

    @Test
    void groundedAnswerCarriesCitationsAndPromptContainsSnippets() throws Exception {
        StubLlm llm = new StubLlm();
        RetrievedChunk chunk = chunk("Lec-2.pdf", 6, "Constructors make objects explicit");
        TaideServiceCore core = core(new StubRetriever(List.of(chunk)), llm);

        TaAnswer answer = core.ask("what is a constructor", CodeContext.empty());

        assertTrue(answer.grounded());
        assertEquals(1, answer.citations().size());
        assertEquals("Lec-2.pdf", answer.citations().get(0).documentName());
        assertEquals(6, answer.citations().get(0).slideNumber());
        assertEquals("stub-model", answer.model());
        assertTrue(llm.lastUserPrompt.contains("Lec-2.pdf · page 6"));
        assertTrue(llm.lastUserPrompt.contains("Constructors make objects explicit"));
    }

    @Test
    void noRetrievalYieldsUngroundedAnswerWithoutCitations() throws Exception {
        StubLlm llm = new StubLlm();
        TaideServiceCore core = core(new StubRetriever(List.of()), llm);

        TaAnswer answer = core.ask("some question", CodeContext.empty());

        assertFalse(answer.grounded());
        assertTrue(answer.citations().isEmpty());
        assertFalse(llm.lastUserPrompt.contains("Retrieved course snippets"));
    }

    @Test
    void selectedCodeIsInjectedIntoPrompt() throws Exception {
        StubLlm llm = new StubLlm();
        TaideServiceCore core = core(new StubRetriever(List.of()), llm);
        CodeContext context = new CodeContext("UserService.java", "java", "private final X x;", 3, 5, "UserService");

        core.ask("explain this", context);

        assertTrue(llm.lastUserPrompt.contains("UserService.java"));
        assertTrue(llm.lastUserPrompt.contains("lines 3-5"));
        assertTrue(llm.lastUserPrompt.contains("private final X x;"));
    }

    @Test
    void streamingForwardsTokensAndCompletesWithAnswer() throws Exception {
        StubLlm llm = new StubLlm();
        RetrievedChunk chunk = chunk("Lec-2.pdf", 6, "text");
        TaideServiceCore core = core(new StubRetriever(List.of(chunk)), llm);

        List<String> tokens = new ArrayList<>();
        AtomicReference<TaAnswer> completed = new AtomicReference<>();
        AtomicReference<Throwable> failed = new AtomicReference<>();

        core.askStreaming("q", CodeContext.empty(), tokens::add, completed::set, failed::set);

        assertEquals("stub answer", String.join("", tokens));
        assertEquals("stub answer", completed.get().text());
        assertTrue(completed.get().grounded());
        assertEquals(1, completed.get().citations().size());
        assertTrue(failed.get() == null);
    }

    @Test
    void blankQuestionIsRejected() throws Exception {
        TaideServiceCore core = core(new StubRetriever(List.of()), new StubLlm());
        assertThrows(TaideException.class, () -> core.ask("  ", CodeContext.empty()));
    }
}
