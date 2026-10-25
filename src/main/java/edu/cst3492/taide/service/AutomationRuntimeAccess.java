package edu.cst3492.taide.service;

import edu.cst3492.taide.llm.LlmClient;
import edu.cst3492.taide.rag.Retriever;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Issue #10 · A-C-01/A-C-02: scoped, type-safe access to the shared LLM and
 * retriever runtime for the F1 automation actions.
 *
 * <p>Implemented by the registered {@link TaideService} project service, so the
 * automation layer resolves it with {@code project.getService(TaideService.class)}
 * and casts — no additional plugin.xml registration is needed.
 *
 * <p>Operations run on the calling (background) thread. The implementation holds
 * a resource lease for the whole callback and releases it on success or failure;
 * cancellation exceptions propagate untouched.
 */
public interface AutomationRuntimeAccess {

    /** Model-only access (F1.1/F1.2): works without a built knowledge base and never loads ONNX. */
    <T> T withModel(Function<LlmClient, T> operation) throws TaideException;

    /** Model + retriever access (F1.3): requires the built index and shares F0's cached resources. */
    <T> T withModelAndRetriever(BiFunction<LlmClient, Retriever, T> operation) throws TaideException;
}
