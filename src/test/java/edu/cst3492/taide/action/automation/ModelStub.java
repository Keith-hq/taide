package edu.cst3492.taide.action.automation;

import edu.cst3492.taide.llm.LlmClient;

import java.util.function.Consumer;

final class ModelStub implements LlmClient {
    private final String response;
    String systemPrompt;
    String userPrompt;
    int calls;

    ModelStub(String response) {
        this.response = response;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, double temperature) {
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        calls++;
        return response;
    }

    @Override
    public void completeStreaming(String systemPrompt, String userPrompt, Consumer<String> onToken) {
        onToken.accept(complete(systemPrompt, userPrompt, 0));
    }
}
