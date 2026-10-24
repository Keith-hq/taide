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
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
    private final Duration timeout;

    /** {@code baseUrl} may be https://api.deepseek.com or .../v1 or the full chat/completions URL. */
    public OpenAiCompatibleLlmClient(String baseUrl, String apiKey, String model) {
        this(baseUrl, apiKey, model, TIMEOUT);
    }

    OpenAiCompatibleLlmClient(String baseUrl, String apiKey, String model, Duration timeout) {
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.endpoint = normalize(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
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
        Objects.requireNonNull(onToken, "onToken");
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", 0.3);
        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));
        body.add("messages", messages);
        body.addProperty("stream", true);
        AtomicBoolean emitted = new AtomicBoolean();
        long deadline = System.nanoTime() + timeout.toNanos();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new LlmException("Model call timed out");
            AtomicBoolean active = new AtomicBoolean(true);
            AtomicReference<StreamingSubscriber> subscriber = new AtomicReference<>();
            CompletableFuture<HttpResponse<Void>> pending = http.sendAsync(request(body.toString()), info -> {
                StreamingSubscriber stream = new StreamingSubscriber(info.statusCode(), onToken, emitted, active);
                subscriber.set(stream);
                return stream;
            });
            try {
                remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new TimeoutException();
                pending.get(remaining, TimeUnit.NANOSECONDS);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException("Model call interrupted", e);
            } catch (TimeoutException e) {
                throw new LlmException("Model call timed out");
            } catch (ExecutionException e) {
                Throwable failure = e.getCause();
                while (failure instanceof CompletionException && failure.getCause() != null) {
                    failure = failure.getCause();
                }
                if (failure instanceof CallbackFailure callback) failure = callback.getCause();
                if (failure instanceof ResponseStatusException status) {
                    if (status.retryable() && !emitted.get() && attempt < MAX_ATTEMPTS) continue;
                    throw new LlmException("Model API error " + status.statusCode
                            + " (check the API key and model name)");
                }
                if (failure instanceof IOException) {
                    if (!emitted.get() && attempt < MAX_ATTEMPTS) continue;
                    throw new LlmException(emitted.get() ? "Model stream interrupted; retry the request"
                            : "Model API unreachable after " + MAX_ATTEMPTS + " attempts");
                }
                // In particular, preserve exceptions raised by the consumer instead of hiding them.
                if (failure instanceof RuntimeException runtime) throw runtime;
                if (failure instanceof Error error) throw error;
                throw new LlmException("Model streaming failed");
            } finally {
                active.set(false);
                StreamingSubscriber stream = subscriber.get();
                if (stream != null) stream.cancel();
                pending.cancel(true);
            }
        }
    }

    /** Decode UTF-8 incrementally with the JDK line subscriber, without buffering the HTTP body. */
    private static final class StreamingSubscriber implements HttpResponse.BodySubscriber<Void> {
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private final HttpResponse.BodySubscriber<Void> lines;
        private final int statusCode;
        private final AtomicBoolean active;
        private volatile Flow.Subscription subscription;

        private StreamingSubscriber(int statusCode, Consumer<String> onToken,
                                    AtomicBoolean emitted, AtomicBoolean active) {
            this.statusCode = statusCode;
            this.active = active;
            lines = HttpResponse.BodySubscribers.fromLineSubscriber(new Flow.Subscriber<String>() {
                private Flow.Subscription lineSubscription;

                @Override
                public void onSubscribe(Flow.Subscription value) {
                    lineSubscription = value;
                    value.request(1);
                }

                @Override
                public void onNext(String line) {
                    if (!active.get() || completion.isDone()) return;
                    try {
                        String data = line.stripLeading();
                        if (data.startsWith("data:")) {
                            data = data.substring(5).strip();
                            if ("[DONE]".equals(data)) {
                                completion.complete(null);
                                cancel();
                                return;
                            }
                            String token = streamToken(data);
                            if (token != null && !token.isEmpty() && active.get()) {
                                // Set before calling user code: a throwing callback must never be retried.
                                emitted.set(true);
                                try {
                                    onToken.accept(token);
                                } catch (Throwable failure) {
                                    throw new CallbackFailure(failure);
                                }
                            }
                        }
                        lineSubscription.request(1);
                    } catch (Throwable failure) {
                        completion.completeExceptionally(failure);
                        cancel();
                    }
                }

                @Override
                public void onError(Throwable failure) {
                    completion.completeExceptionally(failure);
                }

                @Override
                public void onComplete() {
                    completion.complete(null);
                }
            });
        }

        @Override
        public CompletionStage<Void> getBody() {
            return completion;
        }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            if (!active.get()) {
                value.cancel();
            } else if (statusCode < 200 || statusCode >= 300) {
                completion.completeExceptionally(new ResponseStatusException(statusCode));
                value.cancel();
            } else {
                lines.onSubscribe(value);
            }
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (active.get() && !completion.isDone()) lines.onNext(buffers);
        }

        @Override
        public void onError(Throwable failure) {
            lines.onError(failure);
            completion.completeExceptionally(failure);
        }

        @Override
        public void onComplete() {
            lines.onComplete();
        }

        private void cancel() {
            Flow.Subscription current = subscription;
            if (current != null) current.cancel();
        }
    }

    private static String streamToken(String data) {
        if (data.isEmpty()) return null;
        try {
            JsonObject frame = JsonParser.parseString(data).getAsJsonObject();
            if (frame.has("error")) throw new LlmException("Model API returned a streaming error");
            JsonArray choices = frame.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) return null; // usage / heartbeat frame
            JsonObject delta = choices.get(0).getAsJsonObject().getAsJsonObject("delta");
            if (delta == null || !delta.has("content") || delta.get("content").isJsonNull()) return null;
            return delta.get("content").getAsString();
        } catch (LlmException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new LlmException("Unexpected model streaming response");
        }
    }

    private static final class ResponseStatusException extends IOException {
        private final int statusCode;

        private ResponseStatusException(int statusCode) {
            this.statusCode = statusCode;
        }

        private boolean retryable() {
            return statusCode == 408 || statusCode == 429 || statusCode >= 500;
        }
    }

    /** Preserve even a consumer's CompletionException through HttpClient's own completion wrappers. */
    private static final class CallbackFailure extends RuntimeException {
        private CallbackFailure(Throwable cause) {
            super(cause);
        }
    }

    private String post(String body) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = http.send(request(body), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 500) {
                    continue;
                }
                if (response.statusCode() >= 400) {
                    throw new LlmException("Model API error " + response.statusCode()
                            + " (check the API key and model name)");
                }
                return response.body();
            } catch (IOException e) {
                // Retry transport failures without exposing endpoint or provider diagnostics.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException("Model call interrupted", e);
            }
        }
        throw new LlmException("Model API unreachable after " + MAX_ATTEMPTS + " attempts");
    }

    private HttpRequest request(String body) {
        try {
            return HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
        } catch (IllegalArgumentException e) {
            throw new LlmException("Invalid model endpoint or request configuration");
        }
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
            throw new LlmException("Unexpected model response");
        }
    }
}
