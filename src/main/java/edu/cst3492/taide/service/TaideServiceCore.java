package edu.cst3492.taide.service;

import edu.cst3492.taide.llm.LlmClient;
import edu.cst3492.taide.rag.Retriever;
import edu.cst3492.taide.rag.RetrievedChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Platform-free engine behind the TAIDE tool window: retrieve course chunks,
 * build a grounded prompt, call the LLM, and produce a {@link TaAnswer} with
 * citations. {@code grounded=false} with empty citations means the answer used
 * general knowledge only — the frozen contract forbids citations in that mode.
 */
public final class TaideServiceCore implements TaideService {

    private static final int CITATION_SNIPPET_LENGTH = 180;

    private final Retriever retriever;
    private final LlmClient llm;
    private final PromptLibrary prompts;
    private final String modelName;

    public TaideServiceCore(Retriever retriever, LlmClient llm, PromptLibrary prompts, String modelName) {
        this.retriever = retriever;
        this.llm = llm;
        this.prompts = prompts;
        this.modelName = modelName;
    }

    @Override
    public TaAnswer ask(String question, CodeContext context) throws TaideException {
        requireQuestion(question);
        List<RetrievedChunk> chunks = retriever.retrieve(question, 8);
        String userMessage = chunks.isEmpty()
                ? prompts.userMessageWithoutSnippets(question, context)
                : prompts.userMessageWithSnippets(question, context, chunks);
        String answer;
        try {
            answer = llm.complete(prompts.systemPrompt(), userMessage, 0.3);
        } catch (edu.cst3492.taide.llm.LlmException e) {
            throw new TaideException(e.getMessage(), e);
        }
        return toAnswer(answer, chunks);
    }

    @Override
    public void askStreaming(String question, CodeContext context,
                             Consumer<String> onToken,
                             Consumer<TaAnswer> onComplete,
                             Consumer<Throwable> onError) {
        try {
            requireQuestion(question);
            List<RetrievedChunk> chunks = retriever.retrieve(question, 8);
            String userMessage = chunks.isEmpty()
                    ? prompts.userMessageWithoutSnippets(question, context)
                    : prompts.userMessageWithSnippets(question, context, chunks);
            StringBuilder answer = new StringBuilder();
            llm.completeStreaming(prompts.systemPrompt(), userMessage, token -> {
                answer.append(token);
                onToken.accept(token);
            });
            onComplete.accept(toAnswer(answer.toString(), chunks));
        } catch (Throwable error) {
            if (error instanceof edu.cst3492.taide.llm.LlmException llmError) {
                onError.accept(new TaideException(llmError.getMessage(), llmError));
            } else {
                onError.accept(error);
            }
        }
    }

    private TaAnswer toAnswer(String text, List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return new TaAnswer(text, List.of(), false, modelName);
        }
        List<Citation> citations = new ArrayList<>(chunks.size());
        for (RetrievedChunk chunk : chunks) {
            citations.add(new Citation(
                    chunk.documentName(),
                    chunk.slideNumber(),
                    snippet(chunk.text()),
                    chunk.score()));
        }
        return new TaAnswer(text, citations, true, modelName);
    }

    private static String snippet(String text) {
        String singleLine = text.replace('\n', ' ');
        return singleLine.length() <= CITATION_SNIPPET_LENGTH
                ? singleLine : singleLine.substring(0, CITATION_SNIPPET_LENGTH);
    }

    private static void requireQuestion(String question) throws TaideException {
        if (question == null || question.isBlank()) {
            throw new TaideException("Question is empty.");
        }
    }
}
