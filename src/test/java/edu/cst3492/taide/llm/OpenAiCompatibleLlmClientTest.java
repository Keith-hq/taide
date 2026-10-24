package edu.cst3492.taide.llm;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the client against a local HTTP stub: parsing, streaming, retries, errors. */
class OpenAiCompatibleLlmClientTest {

    private HttpServer server;
    private int port;
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile int statusCode = 200;
    private volatile String responseBody;
    private volatile HttpHandler handler;
    private ExecutorService serverExecutor;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        handler = exchange -> {
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        };
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            handler.handle(exchange);
        });
        serverExecutor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(serverExecutor);
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    private OpenAiCompatibleLlmClient client() {
        return new OpenAiCompatibleLlmClient("http://localhost:" + port, "test-key", "test-model");
    }

    @Test
    void completesAndExtractsContent() {
        responseBody = "{\"choices\":[{\"message\":{\"content\":\"hello from model\"}}]}";
        assertEquals("hello from model", client().complete("system", "user", 0.3));
        assertEquals(1, requestCount.get());
    }

    @Test
    void streamingAccumulatesDeltas() {
        responseBody = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}\n"
                + "data: [DONE]\n";
        List<String> tokens = new ArrayList<>();
        client().completeStreaming("system", "user", tokens::add);
        assertEquals("Hello world", String.join("", tokens));
    }

    @Test
    void firstTokenArrivesBeforeTheResponseBodyFinishes() throws Exception {
        CountDownLatch firstToken = new CountDownLatch(1);
        CountDownLatch releaseTail = new CountDownLatch(1);
        handler = exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write(delta("先").getBytes(StandardCharsets.UTF_8));
                output.flush();
                await(releaseTail);
                output.write((delta("后") + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8));
            }
        };
        List<String> tokens = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                client().completeStreaming("s", "u", token -> {
                    tokens.add(token);
                    firstToken.countDown();
                });
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        try {
            assertTrue(firstToken.await(3, TimeUnit.SECONDS), "first token must arrive while server holds the tail");
            assertTrue(worker.isAlive(), "request should still be waiting for the tail");
            assertEquals(List.of("先"), tokens);
        } finally {
            releaseTail.countDown();
            worker.join(3000);
            if (worker.isAlive()) worker.interrupt();
        }
        assertFalse(worker.isAlive());
        assertNull(failure.get());
        assertEquals(List.of("先", "后"), tokens);
    }

    @Test
    void ignoresRoleEmptyKeepaliveUsageAndFinishFrames() {
        responseBody = ": keepalive\n\n"
                + "data: \n\n"
                + "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":null}}]}\n\n"
                + delta("") + delta("内容")
                + "data: {\"choices\":[],\"usage\":{\"total_tokens\":5}}\n\n"
                + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: [DONE]\n\n";
        List<String> tokens = new ArrayList<>();
        client().completeStreaming("s", "u", tokens::add);
        assertEquals(List.of("内容"), tokens);
    }

    @Test
    void doneStopsWithoutWaitingForConnectionClose() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        handler = exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write((delta("done") + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8));
                output.flush();
                await(release);
            }
        };
        try {
            new OpenAiCompatibleLlmClient("http://localhost:" + port, "k", "m", Duration.ofSeconds(2))
                    .completeStreaming("s", "u", ignored -> {});
        } finally {
            release.countDown();
        }
    }

    @Test
    void interruptCancelsAStreamBlockedBetweenTokens() throws Exception {
        CountDownLatch firstToken = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        handler = exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write(delta("first").getBytes(StandardCharsets.UTF_8));
                output.flush();
                await(release);
            }
        };
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interruptRestored = new AtomicBoolean();
        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                client().completeStreaming("s", "u", ignored -> firstToken.countDown());
            } catch (Throwable e) {
                failure.set(e);
                interruptRestored.set(Thread.currentThread().isInterrupted());
            }
        });
        try {
            assertTrue(firstToken.await(3, TimeUnit.SECONDS));
            worker.interrupt();
            worker.join(2000);
            assertFalse(worker.isAlive(), "cancellation must not wait for the next network byte");
            assertTrue(failure.get() instanceof LlmException);
            assertTrue(interruptRestored.get());
            assertEquals(1, requestCount.get());
        } finally {
            release.countDown();
            worker.interrupt();
        }
    }

    @Test
    void stalledBodyHasAnOverallTimeout() {
        CountDownLatch release = new CountDownLatch(1);
        handler = exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write(": heartbeat\n\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                await(release);
            }
        };
        try {
            var shortTimeout = new OpenAiCompatibleLlmClient("http://localhost:" + port,
                    "k", "m", Duration.ofMillis(250));
            LlmException error = assertThrows(LlmException.class,
                    () -> shortTimeout.completeStreaming("s", "u", ignored -> {}));
            assertEquals("Model call timed out", error.getMessage());
            assertEquals(1, requestCount.get(), "a stalled body shares the call's deadline");
        } finally {
            release.countDown();
        }
    }

    @Test
    void streamingRetriesOnlyBeforeDeliveringContent() {
        handler = exchange -> {
            byte[] body = (requestCount.get() == 1 ? "private server error" : delta("ok"))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(requestCount.get() == 1 ? 503 : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        };
        List<String> tokens = new ArrayList<>();
        client().completeStreaming("s", "u", tokens::add);
        assertEquals(List.of("ok"), tokens);
        assertEquals(2, requestCount.get());
    }

    @Test
    void streamingRetriesAreBoundedAndDoNotExposeServerDiagnostics() {
        statusCode = 503;
        responseBody = "private-server-body-test-key";
        LlmException error = assertThrows(LlmException.class,
                () -> client().completeStreaming("s", "u", ignored -> {}));
        assertEquals(3, requestCount.get());
        assertEquals("Model API error 503 (check the API key and model name)", error.getMessage());
        assertNull(error.getCause());
    }

    @Test
    void interruptedBodyAfterATokenIsNotRetried() {
        handler = exchange -> {
            byte[] body = delta("partial").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length + 20L);
            exchange.getResponseBody().write(body);
            exchange.close(); // truncate the declared body to simulate a dropped connection
        };
        List<String> tokens = new ArrayList<>();
        assertThrows(LlmException.class, () -> client().completeStreaming("s", "u", tokens::add));
        assertEquals(List.of("partial"), tokens);
        assertEquals(1, requestCount.get(), "retrying would duplicate the already delivered prefix");
    }

    @Test
    void callbackFailureIsPropagatedWithoutRetry() {
        responseBody = delta("token") + "data: [DONE]\n\n";
        IllegalStateException callbackFailure = new IllegalStateException("consumer failed");
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> client().completeStreaming("s", "u", token -> { throw callbackFailure; }));
        assertSame(callbackFailure, error);
        assertEquals(1, requestCount.get());
    }

    @Test
    void callbackCompletionExceptionIsNotMistakenForAnHttpWrapper() {
        responseBody = delta("token");
        CompletionException callbackFailure = new CompletionException(new IllegalStateException("consumer failed"));
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> client().completeStreaming("s", "u", token -> { throw callbackFailure; }));
        assertSame(callbackFailure, error);
        assertEquals(1, requestCount.get());
    }

    @Test
    void invalidConfigurationNeverEchoesApiKeys() {
        var invalid = new OpenAiCompatibleLlmClient("http://localhost:" + port, "private-key\ninvalid", "m");
        LlmException error = assertThrows(LlmException.class,
                () -> invalid.completeStreaming("s", "u", ignored -> {}));
        assertEquals("Invalid model endpoint or request configuration", error.getMessage());
        assertNull(error.getCause());
        assertEquals(0, requestCount.get());
    }

    @Test
    void streamingErrorsNeverExposeTheServerBody() {
        statusCode = 401;
        responseBody = "private-server-body-test-key";
        LlmException error = assertThrows(LlmException.class,
                () -> client().completeStreaming("s", "u", ignored -> {}));
        assertFalse(error.toString().contains(responseBody));
        assertNull(error.getCause());
        assertEquals(1, requestCount.get());
    }

    @Test
    void malformedStreamingResponseFailsWithoutLeakingPayload() {
        responseBody = "data: private-server-body-test-key\n\n";
        LlmException error = assertThrows(LlmException.class,
                () -> client().completeStreaming("s", "u", ignored -> {}));
        assertEquals("Unexpected model streaming response", error.getMessage());
        assertNull(error.getCause());
        assertEquals(1, requestCount.get());
    }

    @Test
    void providerErrorFramesFailWithASanitizedMessage() {
        responseBody = "data: {\"error\":{\"message\":\"private-server-body-test-key\"}}\n\n";
        LlmException error = assertThrows(LlmException.class,
                () -> client().completeStreaming("s", "u", ignored -> {}));
        assertEquals("Model API returned a streaming error", error.getMessage());
        assertNull(error.getCause());
        assertEquals(1, requestCount.get());
    }

    @Test
    void retriesTransientServerErrors() {
        statusCode = 500;
        responseBody = "{\"error\":\"boom\"}";
        assertThrows(LlmException.class, () -> client().complete("s", "u", 0.3));
        assertEquals(3, requestCount.get(), "initial attempt + 2 retries");
    }

    @Test
    void authErrorsFailFastWithoutRetry() {
        statusCode = 401;
        responseBody = "{\"error\":\"invalid key\"}";
        LlmException error = assertThrows(LlmException.class, () -> client().complete("s", "u", 0.3));
        assertFalse(error.toString().contains(responseBody));
        assertEquals(1, requestCount.get());
    }

    @Test
    void malformedResponseIsWrapped() {
        responseBody = "not json at all";
        LlmException error = assertThrows(LlmException.class, () -> client().complete("s", "u", 0.3));
        assertTrue(error.getMessage().contains("Unexpected model response"));
    }

    @Test
    void unreachableEndpointFailsWithReadableErrorInsteadOfCrashing() {
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient("http://localhost:1", "k", "m");
        LlmException error = assertThrows(LlmException.class, () -> client.complete("s", "u", 0));
        assertTrue(error.getMessage().contains("unreachable after 3 attempts"));
    }

    @Test
    void normalizesBaseUrls() {
        assertEquals("https://api.deepseek.com/chat/completions",
                OpenAiCompatibleLlmClient.normalize("https://api.deepseek.com"));
        assertEquals("https://api.deepseek.com/v1/chat/completions",
                OpenAiCompatibleLlmClient.normalize("https://api.deepseek.com/v1"));
        assertEquals("https://x/chat/completions",
                OpenAiCompatibleLlmClient.normalize("https://x/chat/completions/"));
    }

    private static String delta(String token) {
        return "data: {\"choices\":[{\"delta\":{\"content\":\"" + token + "\"}}]}\n\n";
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IOException("stub response timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("stub interrupted", e);
        }
    }
}
