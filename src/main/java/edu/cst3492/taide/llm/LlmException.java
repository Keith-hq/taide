package edu.cst3492.taide.llm;

/**
 * Unchecked failure wrapper for LLM calls. The frozen {@link LlmClient} contract
 * declares no checked exceptions, so implementations fail with this runtime type;
 * the service layer converts it to the user-facing checked {@code TaideException}.
 */
public final class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
