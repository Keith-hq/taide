package edu.cst3492.taide.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the client against a local HTTP stub: parsing, streaming, retries, errors. */
class OpenAiCompatibleLlmClientTest {

    private HttpServer server;
    private int port;
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile int statusCode = 200;
    private volatile String responseBody;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
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
        assertThrows(LlmException.class, () -> client().complete("s", "u", 0.3));
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
}
