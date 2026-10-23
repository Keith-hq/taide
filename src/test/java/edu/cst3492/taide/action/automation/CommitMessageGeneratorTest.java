package edu.cst3492.taide.action.automation;

import com.google.gson.JsonParser;
import edu.cst3492.taide.git.StagedChanges;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommitMessageGeneratorTest {
    @Test
    void sendsCompleteStagedContextAsStructuredData() {
        var model = new ModelStub("fix(auth): reject empty names\n\nValidate the input before user lookup.");
        var staged = new StagedChanges(Path.of("/project"), "diff --git a/a.java b/a.java\n+check(name);",
                List.of(new StagedChanges.FileChange("MODIFY", "a.java", "a.java", false)), "fingerprint");
        String message = new CommitMessageGenerator(model).generate(staged);
        var input = JsonParser.parseString(model.userPrompt).getAsJsonObject();
        assertEquals(staged.patch(), input.get("stagedDiff").getAsString());
        assertEquals(1, input.getAsJsonArray("files").size());
        assertTrue(message.startsWith("fix(auth): "));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "feat: add account validation",
            "fix(api)!: require account identifiers",
            "docs(readme): document installation\n\nInclude the supported JDK version.",
            "refactor: simplify user lookup"
    })
    void acceptsConventionalCommits(String message) {
        assertEquals(message, CommitMessageGenerator.validate(message));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "Update files", "fix: ", "fix: subject\nbody without blank line", "unknown: change", "fix: bad\u0000message"
    })
    void rejectsInvalidMessages(String message) {
        assertThrows(AutomationException.class, () -> CommitMessageGenerator.validate(message));
    }

    @Test
    void rejectsOversizedSummary() {
        assertThrows(AutomationException.class, () -> CommitMessageGenerator.validate("feat: " + "a".repeat(100)));
    }
}
