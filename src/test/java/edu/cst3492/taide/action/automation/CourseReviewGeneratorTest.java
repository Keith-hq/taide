package edu.cst3492.taide.action.automation;

import com.google.gson.JsonParser;
import edu.cst3492.taide.rag.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CourseReviewGeneratorTest {
    private static final RetrievedChunk COURSE = new RetrievedChunk(
            "lecture-4", "Lecture04-JDBC.pdf", 17, "Bind user values with PreparedStatement parameters.", 0.8);
    private static final String FINDING = """
            {"findings":[{"severity":"ERROR","title":"Unbound SQL value",
            "explanation":"User input is concatenated into an SQL query.",
            "suggestion":"Use a PreparedStatement parameter.","sourceIds":["S1"]}]}
            """;

    @Test
    void citationsComeFromRetrieverMetadata() {
        var model = new ModelStub(FINDING);
        var generator = new CourseReviewGenerator(model, (query, k) -> {
            assertEquals(TestGeneratorTest.CONTEXT.selectedText(), query);
            assertEquals(8, k);
            return List.of(COURSE);
        });
        CourseReview review = generator.generate(TestGeneratorTest.CONTEXT);
        assertTrue(review.grounded());
        assertSame(COURSE, review.findings().getFirst().sources().getFirst());
        var input = JsonParser.parseString(model.userPrompt).getAsJsonObject();
        assertEquals("course", input.get("mode").getAsString());
        assertEquals("S1", input.getAsJsonArray("courseExcerpts").get(0).getAsJsonObject().get("id").getAsString());
    }

    @Test
    void generalKnowledgeHasExplicitNoticeAndEmptySources() {
        var model = new ModelStub(FINDING.replace("[\"S1\"]", "[]"));
        var review = new CourseReviewGenerator(model, (query, k) -> List.of()).generate(TestGeneratorTest.CONTEXT);
        assertFalse(review.grounded());
        assertTrue(review.findings().getFirst().sources().isEmpty());
        assertEquals("general", JsonParser.parseString(model.userPrompt).getAsJsonObject().get("mode").getAsString());
        assertTrue(AutomationPanels.formatReview(review).contains(CourseReviewGenerator.GENERAL_KNOWLEDGE_NOTICE));
    }

    @Test
    void rejectsInventedOrMissingCitations() {
        for (String references : List.of("[\"S999\"]", "[]", "[17]", "[\"Lecture.pdf p.9\"]")) {
            var model = new ModelStub(FINDING.replace("[\"S1\"]", references));
            assertThrows(AutomationException.class, () -> new CourseReviewGenerator(model, (q, k) -> List.of(COURSE))
                    .generate(TestGeneratorTest.CONTEXT));
        }
    }

    @Test
    void generalKnowledgeCannotAcquireModelInventedSources() {
        assertThrows(AutomationException.class,
                () -> new CourseReviewGenerator(new ModelStub(FINDING), (q, k) -> List.of())
                        .generate(TestGeneratorTest.CONTEXT));
    }

    @Test
    void acceptsEmptyFindingsAndDeduplicatesSources() {
        var model = new ModelStub("{\"findings\":[]}");
        var review = new CourseReviewGenerator(model, (q, k) -> List.of(COURSE, COURSE))
                .generate(TestGeneratorTest.CONTEXT);
        assertTrue(review.findings().isEmpty());
        assertEquals(1, JsonParser.parseString(model.userPrompt).getAsJsonObject().getAsJsonArray("courseExcerpts").size());
    }

    @Test
    void rejectsBadRetrievalMetadataBeforeCallingModel() {
        var invalid = new RetrievedChunk("doc", "Course.pdf", 0, "text", 0.8);
        var model = new ModelStub(FINDING);
        assertThrows(AutomationException.class,
                () -> new CourseReviewGenerator(model, (q, k) -> List.of(invalid)).generate(TestGeneratorTest.CONTEXT));
        assertEquals(0, model.calls);
    }

    @Test
    void rejectsMalformedFindings() {
        for (String response : List.of("{}", "{\"findings\":null}", "{\"findings\":[0]}",
                FINDING.replace("ERROR", "CRITICAL"), FINDING.replace("\"suggestion\"", "\"other\""))) {
            assertThrows(AutomationException.class,
                    () -> new CourseReviewGenerator(new ModelStub(response), (q, k) -> List.of(COURSE))
                            .generate(TestGeneratorTest.CONTEXT));
        }
    }
}
