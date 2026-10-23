package edu.cst3492.taide.action.automation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.util.PathUtil;
import edu.cst3492.taide.service.CodeContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TestGeneratorTest {
    static final String SOURCE = """
            package demo;
            public class Greeting {
                public String greet(String name) {
                    if (name == null || name.isBlank()) throw new IllegalArgumentException("name");
                    return "Hello " + name;
                }
            }
            """;
    static final String TEST_SOURCE = """
            package demo;
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertThrows;
            public class GreetingTest {
                @Test void rejectsEmptyName() {
                    assertThrows(IllegalArgumentException.class, () -> new Greeting().greet(""));
                }
            }
            """;
    static final CodeContext CONTEXT = new CodeContext(
            "/project/src/main/java/demo/Greeting.java", "JAVA", SOURCE, 1, 7, "demo.Greeting");

    @TempDir
    Path directory;

    @Test
    void passesScenarioAndFullSourceToModel() {
        var json = new JsonObject();
        json.addProperty("source", TEST_SOURCE);
        var model = new ModelStub(json.toString());
        GeneratedTest generated = new TestGenerator(model).generate("reject an empty name", CONTEXT, SOURCE);
        JsonObject input = JsonParser.parseString(model.userPrompt).getAsJsonObject();
        assertEquals("reject an empty name", input.get("scenario").getAsString());
        assertEquals(SOURCE, input.get("source").getAsString());
        assertEquals(SOURCE, input.get("selection").getAsString());
        assertEquals("demo/GreetingTest.java", generated.relativePath());
        assertTrue(model.systemPrompt.contains("untrusted"));
        assertEquals(1, model.calls);
    }

    @Test
    void generatedScenarioFixtureCompilesWithJunit5() throws Exception {
        GeneratedTest generated = TestGenerator.validate(TEST_SOURCE, "demo");
        Path main = directory.resolve("Greeting.java");
        Path test = directory.resolve("GreetingTest.java");
        Files.writeString(main, SOURCE);
        Files.writeString(test, generated.source());
        String junit = PathUtil.getJarPathForClass(Test.class);
        String apiGuardian = PathUtil.getJarPathForClass(org.apiguardian.api.API.class);
        String openTest4j = PathUtil.getJarPathForClass(org.opentest4j.AssertionFailedError.class);
        Path output = directory.resolve("javac.log");
        // 独立编译进程使用完整 JDK，避免平台类加载器影响编译器服务发现。
        var compiler = new ProcessBuilder(System.getProperty("taide.test.javac"),
                "--release", "21", "-encoding", "UTF-8", "-proc:none",
                "-classpath", String.join(java.io.File.pathSeparator, junit, apiGuardian, openTest4j),
                "-d", directory.toString(), main.toString(), test.toString())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(compiler.waitFor(30, TimeUnit.SECONDS), "示例编译应在 30 秒内完成。");
            assertEquals(0, compiler.exitValue(), Files.readString(output));
        } finally {
            compiler.destroyForcibly();
        }
        assertTrue(Files.exists(directory.resolve("demo/GreetingTest.class")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "package other; public class GreetingTest {}",
            "package demo; public class GreetingTest {",
            "package demo; public class GreetingTest {}",
            "package demo; import org.junit.Test; public class GreetingTest { @Test void test() {} }",
            "package demo; import org.junit.jupiter.api.Test; public class GreetingTest { @Test private void test() {} }",
            "package demo; import org.junit.jupiter.api.Test; public class GreetingTest { @Test static void test() {} }",
            "package demo; import org.junit.jupiter.api.Test; public class GreetingTest { @Test int test() { return 1; } }",
            "package demo; import org.junit.jupiter.api.Test; class GreetingTest { @Test void test() {} }",
            "package demo; import org.junit.jupiter.api.Test; public abstract class GreetingTest { @Test void test() {} }",
            "package demo; import org.junit.jupiter.api.Test; public class GreetingTest { @Test void test() {} } class Extra {}"
    })
    void rejectsInvalidTestSource(String source) {
        assertThrows(AutomationException.class, () -> TestGenerator.validate(source, "demo"));
    }

    @Test
    void acceptsDefaultPackageAndQualifiedAnnotation() {
        var test = TestGenerator.validate("""
                public class DefaultTest {
                    @org.junit.jupiter.api.Test void example() {}
                }
                """, "");
        assertEquals("DefaultTest.java", test.relativePath());
    }

    @Test
    void validatesInputBeforeNetworkCall() {
        var model = new ModelStub("{}");
        var generator = new TestGenerator(model);
        assertThrows(AutomationException.class, () -> generator.generate(" ", CONTEXT, SOURCE));
        assertThrows(AutomationException.class, () -> generator.generate("x".repeat(4_001), CONTEXT, SOURCE));
        assertThrows(AutomationException.class, () -> generator.generate("scenario", CodeContext.empty(), SOURCE));
        assertEquals(0, model.calls);
    }

    @Test
    void parsesFencedJsonAndRejectsMalformedResponses() {
        var json = new JsonObject();
        json.addProperty("source", TEST_SOURCE);
        assertEquals("GreetingTest", new TestGenerator(new ModelStub("```json\n" + json + "\n```"))
                .generate("empty name", CONTEXT, SOURCE).className());
        for (String response : new String[]{"", "{}", "{source: 'x'}", "{} {}", "```json\n{}", "[]", "null"}) {
            assertThrows(AutomationException.class,
                    () -> new TestGenerator(new ModelStub(response)).generate("empty name", CONTEXT, SOURCE));
        }
    }
}
