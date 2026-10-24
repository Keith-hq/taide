package edu.cst3492.taide.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * OpenAI-compatible /chat/completions client over java.net.http — no extra
 * dependencies, works with DeepSeek and the Tongji AGI platform (both expose the
 * same wire format). A-4 requirements baked in: 30s timeout, 2 retries on
 * transient failures, readable {@link LlmException} instead of raw stack traces.
 */
public final class OpenAiCompatibleLlmClient implements LlmClient {

    public static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_ATTEMPTS = 3; // initial + 2 retries
    private static final Gson GSON = new Gson();

    private final HttpClient http;
    private final String endpoint;
    private final String apiKey;
    private final String model;

    /** {@code baseUrl} may be https://api.deepseek.com or .../v1 or the full chat/completions URL. */
    public OpenAiCompatibleLlmClient(String baseUrl, String apiKey, String model) {
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        this.endpoint = normalize(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
    }

    static String normalize(String baseUrl) {
        String trimmed = baseUrl.trim().replaceAll("/+$", "");
        if (trimmed.endsWith("/chat/completions")) return trimmed;
        if (trimmed.endsWith("/v1")) return trimmed + "/chat/completions";
        return trimmed + "/chat/completions";
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, double temperature) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", temperature);
        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));
        body.add("messages", messages);
        body.addProperty("stream", false);
        String response = post(body.toString());
        return extractContent(response);
    }

    @Override
    public void completeStreaming(String systemPrompt, String userPrompt, Consumer<String> onToken) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", 0.3);
        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));
        body.add("messages", messages);
        body.addProperty("stream", true);
        try {
            HttpRequest request = request(body.toString());
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new LlmException("Model API error " + response.statusCode() + ": " + firstLine(response.body()));
            }
            for (String line : response.body().split("\n")) {
                String data = line.stripLeading();
                if (!data.startsWith("data:")) continue;
                data = data.substring(5).strip();
                if (data.isEmpty() || "[DONE]".equals(data)) continue;
                try {
                    String token = JsonParser.parseString(data).getAsJsonObject()
                            .getAsJsonArray("choices").get(0).getAsJsonObject()
                            .getAsJsonObject("delta").get("content").getAsString();
                    if (!token.isEmpty()) onToken.accept(token);
                } catch (RuntimeException ignored) {
                    // tolerate malformed keep-alive frames
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Model call interrupted", e);
        } catch (IOException e) {
            throw new LlmException("Cannot reach the model API: " + e.getMessage(), e);
        }
    }

    private String post(String body) {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = http.send(request(body), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 500) {
                    last = new IOException("upstream " + response.statusCode());
                    continue;
                }
                if (response.statusCode() >= 400) {
                    throw new LlmException("Model API error " + response.statusCode()
                            + " (check the API key and model name): " + firstLine(response.body()));
                }
                return response.body();
            } catch (IOException e) {
                last = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException("Model call interrupted", e);
            }
        }
        throw new LlmException("Model API unreachable after " + MAX_ATTEMPTS + " attempts: " + last, last);
    }

    private HttpRequest request(String body) {
        return HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    static String extractContent(String responseBody) {
        try {
            return JsonParser.parseString(responseBody).getAsJsonObject()
                    .getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
        } catch (RuntimeException e) {
            throw new LlmException("Unexpected model response: " + firstLine(responseBody), e);
        }
    }

    private static String firstLine(String body) {
        String singleLine = body == null ? "" : body.replace('\n', ' ');
        return singleLine.length() <= 200 ? singleLine : singleLine.substring(0, 200);
    }
}
