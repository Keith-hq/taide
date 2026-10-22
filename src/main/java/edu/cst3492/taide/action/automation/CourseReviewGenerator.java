package edu.cst3492.taide.action.automation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import edu.cst3492.taide.llm.LlmClient;
import edu.cst3492.taide.rag.RetrievedChunk;
import edu.cst3492.taide.rag.Retriever;
import edu.cst3492.taide.service.CodeContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CourseReviewGenerator {
    public static final String GENERAL_KNOWLEDGE_NOTICE =
            "Response is based on general knowledge; no specific course material is referenced.";
    private final LlmClient model;
    private final Retriever retriever;

    public CourseReviewGenerator(LlmClient model, Retriever retriever) {
        this.model = Objects.requireNonNull(model);
        this.retriever = Objects.requireNonNull(retriever);
    }

    public CourseReview generate(CodeContext context) {
        ModelOutput.checkLength(context.selectedText(), 30_000, "The code selection");
        List<RetrievedChunk> retrieved = retriever.retrieve(context.selectedText(), 8);
        if (retrieved == null) {
            throw new AutomationException("The course retriever returned an invalid result.");
        }
        Map<String, RetrievedChunk> sources = selectSources(retrieved);
        var input = new JsonObject();
        input.addProperty("selection", context.selectedText());
        input.addProperty("className", context.enclosingClass());
        input.addProperty("startLine", context.startLine());
        input.addProperty("endLine", context.endLine());
        input.addProperty("mode", sources.isEmpty() ? "general" : "course");
        var excerpts = new JsonArray();
        sources.forEach((id, source) -> {
            var excerpt = new JsonObject();
            excerpt.addProperty("id", id);
            excerpt.addProperty("text", source.text());
            excerpts.add(excerpt);
        });
        input.add("courseExcerpts", excerpts);
        JsonObject response = ModelOutput.json(
                model.complete(ModelOutput.prompt("review"), input.toString(), 0.1));
        return parse(response, sources);
    }

    private static Map<String, RetrievedChunk> selectSources(List<RetrievedChunk> retrieved) {
        var result = new LinkedHashMap<String, RetrievedChunk>();
        int totalCharacters = 0;
        for (RetrievedChunk chunk : retrieved) {
            if (result.size() == 8) {
                break;
            }
            if (chunk == null || chunk.docId() == null || chunk.docId().isBlank()
                    || chunk.documentName() == null || chunk.documentName().isBlank()
                    || chunk.slideNumber() < 1 || chunk.text() == null || chunk.text().isBlank()
                    || !Double.isFinite(chunk.score()) || chunk.score() < 0 || chunk.score() > 1) {
                throw new AutomationException("The course retriever returned invalid citation metadata.");
            }
            if (result.values().contains(chunk)) {
                continue;
            }
            totalCharacters += chunk.text().length();
            if (totalCharacters > 40_000) {
                throw new AutomationException("The course excerpts are too large. Use smaller knowledge chunks.");
            }
            result.put("S" + (result.size() + 1), chunk);
        }
        return result;
    }

    private static CourseReview parse(JsonObject response, Map<String, RetrievedChunk> sources) {
        JsonElement entries = response.get("findings");
        if (entries == null || !entries.isJsonArray() || entries.getAsJsonArray().size() > 15) {
            throw new AutomationException("The review must contain an array of at most 15 findings.");
        }
        var findings = new ArrayList<CourseReview.Finding>();
        for (JsonElement entry : entries.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                throw new AutomationException("The model returned an invalid review finding.");
            }
            JsonObject finding = entry.getAsJsonObject();
            CourseReview.Severity severity;
            try {
                severity = CourseReview.Severity.valueOf(ModelOutput.string(finding, "severity"));
            } catch (IllegalArgumentException error) {
                throw new AutomationException("The review contains an unknown severity.", error);
            }
            JsonElement references = finding.get("sourceIds");
            if (references == null || !references.isJsonArray()) {
                throw new AutomationException("Each finding must include a sourceIds array.");
            }
            var citations = new ArrayList<RetrievedChunk>();
            for (JsonElement reference : references.getAsJsonArray()) {
                if (!reference.isJsonPrimitive() || !reference.getAsJsonPrimitive().isString()
                        || !sources.containsKey(reference.getAsString())) {
                    throw new AutomationException("The review cites a source outside the retrieved course material.");
                }
                RetrievedChunk source = sources.get(reference.getAsString());
                if (!citations.contains(source)) {
                    citations.add(source);
                }
            }
            if (!sources.isEmpty() && citations.isEmpty()) {
                throw new AutomationException("Each course-based finding must cite a retrieved source.");
            }
            findings.add(new CourseReview.Finding(
                    severity,
                    ModelOutput.string(finding, "title"),
                    ModelOutput.string(finding, "explanation"),
                    ModelOutput.string(finding, "suggestion"),
                    citations));
        }
        return new CourseReview(findings, !sources.isEmpty());
    }
}
