package edu.cst3492.taide.action.automation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.FileSystemException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TestFileWriterTest {
    @TempDir
    Path directory;

    @Test
    void derivesPathInsideTestSourceRoot() {
        GeneratedTest test = TestGenerator.validate(TestGeneratorTest.TEST_SOURCE, "demo");
        Path root = directory.resolve("module/src/test/java");
        assertEquals(root.resolve("demo/GreetingTest.java"), TestFileWriter.destination(root, test));
    }

    @Test
    void protectsExistingFiles() throws Exception {
        Files.createDirectories(directory.resolve("demo"));
        Path existing = directory.resolve("demo/GreetingTest.java");
        Files.writeString(existing, "existing test");
        var generated = TestGenerator.validate(TestGeneratorTest.TEST_SOURCE, "demo");
        assertThrows(AutomationException.class, () -> TestFileWriter.destination(directory, generated));
        assertEquals("existing test", Files.readString(existing));
    }

    @Test
    void rejectsPathsOutsideRootAndSymbolicLinks() throws Exception {
        var malicious = new GeneratedTest("", "../../../OutsideTest", "");
        assertThrows(AutomationException.class, () -> TestFileWriter.destination(directory, malicious));
        Path target = Files.createDirectory(directory.resolve("actual"));
        Path linked;
        try {
            linked = Files.createSymbolicLink(directory.resolve("linked"), target);
        } catch (FileSystemException | SecurityException unsupported) {
            // Windows workstations without Developer Mode/SeCreateSymbolicLinkPrivilege
            // cannot create the fixture. The production guard remains covered on CI and
            // by the non-link traversal assertion above.
            assumeTrue(false, "symbolic-link fixture unavailable: " + unsupported.getMessage());
            return;
        }
        var generated = TestGenerator.validate(TestGeneratorTest.TEST_SOURCE, "demo");
        assertThrows(AutomationException.class, () -> TestFileWriter.destination(linked, generated));
    }

    @Test
    void findsNearestBuildRootForGradleSourceSetsAndNestedMavenModules() throws Exception {
        Files.writeString(directory.resolve("build.gradle.kts"), "plugins { java }");
        Path nested = Files.createDirectories(directory.resolve("child/src/main/java/demo"));
        assertEquals(directory, TestFileWriter.buildRoot(nested));
        Files.writeString(directory.resolve("child/pom.xml"), "<project/>");
        assertEquals(directory.resolve("child"), TestFileWriter.buildRoot(nested));
    }
}
