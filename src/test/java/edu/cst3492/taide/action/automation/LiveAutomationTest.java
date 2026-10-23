package edu.cst3492.taide.action.automation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.util.PathUtil;
import edu.cst3492.taide.git.StagedDiffService;
import edu.cst3492.taide.llm.LlmClient;
import edu.cst3492.taide.rag.RetrievedChunk;
import edu.cst3492.taide.service.CodeContext;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@Tag("live-model")
class LiveAutomationTest {
    private static final String JDBC_SOURCE = """
            public ResultSet findUser(Connection connection, String name) throws SQLException {
                return connection.createStatement().executeQuery(
                        "SELECT * FROM users WHERE name = '" + name + "'");
            }
            """;
    private static final CodeContext JDBC_CONTEXT = new CodeContext(
            "UserRepository.java", "JAVA", JDBC_SOURCE, 1, 4, "UserRepository");
    private static final Path EVIDENCE = Path.of("build", "reports", "live-model");

    @TempDir
    Path directory;

    @Test
    void generatesAndRunsScenarioTestForBundledExample() throws Exception {
        String source = Files.readString(Path.of(
                "examples/junit-demo/src/main/java/demo/GreetingService.java"));
        var context = new CodeContext("demo/GreetingService.java", "JAVA", source, 1,
                (int) source.lines().count(), "demo.GreetingService");
        GeneratedTest generated = new TestGenerator(new LiveModel()).generate(
                "Reject null, empty, and whitespace-only names with IllegalArgumentException. "
                        + "Trim a valid name and return Hello followed by one space and the trimmed name.",
                context, source);
        Path mainFile = directory.resolve("GreetingService.java");
        Path testFile = directory.resolve(generated.className() + ".java");
        Files.writeString(mainFile, source);
        Files.writeString(testFile, generated.source());
        evidence(generated.className() + ".java", generated.source());
        String classpath = String.join(java.io.File.pathSeparator,
                PathUtil.getJarPathForClass(Test.class),
                PathUtil.getJarPathForClass(ParameterizedTest.class),
                PathUtil.getJarPathForClass(org.apiguardian.api.API.class),
                PathUtil.getJarPathForClass(org.opentest4j.AssertionFailedError.class));
        Path output = directory.resolve("javac.log");
        var compiler = new ProcessBuilder(System.getProperty("taide.test.javac"),
                "--release", "21", "-encoding", "UTF-8", "-proc:none",
                "-classpath", classpath, "-d", directory.toString(),
                mainFile.toString(), testFile.toString())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(compiler.waitFor(30, TimeUnit.SECONDS), "真实模型生成的测试应在时限内完成编译。");
            assertEquals(0, compiler.exitValue(), Files.readString(output));
        } finally {
            compiler.destroyForcibly();
        }
        assertTrue(Files.exists(directory.resolve("demo/" + generated.className() + ".class")));
        // 编译产物由隔离类加载器载入，执行实际生成的 JUnit 方法。
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (var loader = new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()},
                Test.class.getClassLoader())) {
            Thread.currentThread().setContextClassLoader(loader);
            Class<?> testClass = Class.forName("demo." + generated.className(), true, loader);
            var request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(DiscoverySelectors.selectClass(testClass)).build();
            var listener = new SummaryGeneratingListener();
            LauncherFactory.create().execute(request, listener);
            var summary = listener.getSummary();
            var report = new StringWriter();
            summary.printTo(new PrintWriter(report));
            summary.printFailuresTo(new PrintWriter(report));
            evidence("junit-execution.txt", report.toString());
            assertTrue(summary.getTestsFoundCount() > 0, "真实生成的测试应被 JUnit 发现。");
            assertEquals(0, summary.getTestsFailedCount(), report.toString());
            assertEquals(summary.getTestsFoundCount(), summary.getTestsSucceededCount(), report.toString());
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Test
    void generatesConventionalMessageFromActualStagingArea() throws Exception {
        try (Git git = Git.init().setDirectory(directory.toFile()).call()) {
            var config = git.getRepository().getConfig();
            config.setString("user", null, "name", "TAIDE Live Test");
            config.setString("user", null, "email", "taide-live@example.invalid");
            config.save();
            Files.writeString(directory.resolve("README.md"), "Greeting service\n");
            git.add().addFilepattern("README.md").call();
            git.commit().setMessage("chore: initialize fixture").call();
            Files.writeString(directory.resolve("GreetingService.java"), """
                    public class GreetingService {
                        public String greet(String name) {
                            if (name == null || name.isBlank()) {
                                throw new IllegalArgumentException("name");
                            }
                            return "Hello " + name.strip();
                        }
                    }
                    """);
            git.add().addFilepattern("GreetingService.java").call();
            Files.writeString(directory.resolve("README.md"), "UNSTAGED_FIXTURE\n");
            var staged = new StagedDiffService().read(directory);
            assertFalse(staged.patch().contains("UNSTAGED_FIXTURE"));
            String message = new CommitMessageGenerator(new LiveModel()).generate(staged);
            assertEquals(message, CommitMessageGenerator.validate(message));
            assertFalse(message.contains("UNSTAGED_FIXTURE"));
            evidence("commit-message.txt", message + "\n");
        }
    }

    @Test
    void reviewsCodeWithOriginalFixtureCitation() throws Exception {
        var source = new RetrievedChunk("fixture-jdbc", "JDBC Demonstration Notes.pdf", 3,
                "Use PreparedStatement and question-mark placeholders for user-supplied SQL values. "
                        + "Bind the values with setters such as setString instead of concatenating "
                        + "them into SQL text, so input is treated as data.", 0.95);
        CourseReview review = new CourseReviewGenerator(new LiveModel(), (query, count) -> List.of(source))
                .generate(JDBC_CONTEXT);
        assertTrue(review.grounded());
        assertFalse(review.findings().isEmpty(), "审查应识别示例中的 SQL 参数拼接。");
        for (var finding : review.findings()) {
            assertEquals(List.of(source), finding.sources());
        }
        evidence("course-review.txt", AutomationPanels.formatReview(review));
    }

    @Test
    void reviewsCodeInGeneralKnowledgeMode() throws Exception {
        CourseReview review = new CourseReviewGenerator(new LiveModel(), (query, count) -> List.of())
                .generate(JDBC_CONTEXT);
        assertFalse(review.grounded());
        assertTrue(review.findings().stream().allMatch(finding -> finding.sources().isEmpty()));
        String text = AutomationPanels.formatReview(review);
        assertTrue(text.startsWith(CourseReviewGenerator.GENERAL_KNOWLEDGE_NOTICE));
        evidence("general-review.txt", text);
    }

    private static void evidence(String name, String text) throws Exception {
        Files.createDirectories(EVIDENCE);
        Files.writeString(EVIDENCE.resolve(name), text);
    }

    // 真实模型适配器仅用于显式启用的验收，生产服务由 A 的实现提供。
    private static final class LiveModel implements LlmClient {
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NEVER).build();

        @Override
        public String complete(String systemPrompt, String userPrompt, double temperature) {
            URI base = URI.create(required("TAIDE_LLM_BASE_URL"));
            boolean local = base.getHost() != null
                    && List.of("localhost", "127.0.0.1", "[::1]").contains(base.getHost());
            if (base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null
                    || base.getFragment() != null
                    || !("https".equals(base.getScheme()) || ("http".equals(base.getScheme()) && local))) {
                throw new IllegalArgumentException("模型地址应使用 HTTPS；本地端点可使用 HTTP。");
            }
            var messages = new JsonArray();
            messages.add(message("system", systemPrompt));
            messages.add(message("user", userPrompt));
            var input = new JsonObject();
            input.addProperty("model", required("TAIDE_LLM_MODEL"));
            input.addProperty("temperature", temperature);
            input.addProperty("max_tokens", 4096);
            input.addProperty("stream", false);
            input.add("messages", messages);
            String url = base.toString().replaceAll("/+$", "") + "/chat/completions";
            var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + required("TAIDE_LLM_API_KEY"))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "TAIDE-integration-test/0.1")
                    .POST(HttpRequest.BodyPublishers.ofString(input.toString(), StandardCharsets.UTF_8))
                    .build();
            try {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    throw new AutomationException("真实模型请求返回 HTTP " + response.statusCode() + "。");
                }
                return JsonParser.parseString(response.body()).getAsJsonObject()
                        .getAsJsonArray("choices").get(0).getAsJsonObject()
                        .getAsJsonObject("message").get("content").getAsString();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AutomationException("真实模型测试已中断。");
            } catch (java.io.IOException error) {
                throw new AutomationException("真实模型连接失败或超时。");
            }
        }

        @Override
        public void completeStreaming(String systemPrompt, String userPrompt, Consumer<String> onToken) {
            throw new UnsupportedOperationException("自动化验收使用阻塞式生成接口。");
        }

        private static JsonObject message(String role, String text) {
            var message = new JsonObject();
            message.addProperty("role", role);
            message.addProperty("content", text);
            return message;
        }

        private static String required(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("真实模型测试需要环境变量 " + name + "。");
            }
            return value;
        }
    }
}
