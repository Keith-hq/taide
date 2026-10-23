package edu.cst3492.taide.action.automation;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import edu.cst3492.taide.git.StagedChanges;
import edu.cst3492.taide.llm.LlmClient;

import java.util.Objects;
import java.util.regex.Pattern;

public final class CommitMessageGenerator {
    private static final Pattern CONVENTIONAL = Pattern.compile(
            "^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)"
                    + "(\\([A-Za-z0-9_. /-]+\\))?!?: [^\\r\\n]+(?:\\n\\n[\\s\\S]+)?$");
    private final LlmClient model;

    public CommitMessageGenerator(LlmClient model) {
        this.model = Objects.requireNonNull(model);
    }

    public String generate(StagedChanges staged) {
        var input = new JsonObject();
        input.add("files", new Gson().toJsonTree(staged.files()));
        input.addProperty("stagedDiff", staged.patch());
        String response = model.complete(ModelOutput.prompt("commit"), input.toString(), 0.2);
        return validate(ModelOutput.text(response));
    }

    public static String validate(String message) {
        ModelOutput.checkLength(message, 8_000, "The commit message");
        String normalized = message.strip().replace("\r\n", "\n");
        String summary = normalized.lines().findFirst().orElse("");
        if (!CONVENTIONAL.matcher(normalized).matches() || summary.length() > 100
                || normalized.chars().anyMatch(character -> Character.isISOControl(character)
                && character != '\n' && character != '\t')) {
            throw new AutomationException(
                    "Use a Conventional Commit summary of at most 100 characters, such as fix(auth): handle missing users.");
        }
        return normalized;
    }
}
