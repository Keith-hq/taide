package edu.cst3492.taide.llm;

import java.util.function.Consumer;

public interface LlmClient {
    String complete(String systemPrompt, String userPrompt, double temperature);

    void completeStreaming(String systemPrompt, String userPrompt, Consumer<String> onToken);
}
