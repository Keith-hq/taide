package edu.cst3492.taide.rag.retrieve;

import com.google.gson.Gson;
import edu.cst3492.taide.rag.Retriever;
import edu.cst3492.taide.rag.RetrievedChunk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A-3 acceptance harness: runs the 20-question eval set against a Retriever.
 * Judged questions need the expected (document, slide) inside the top-3;
 * no-answer questions must come back empty. Acceptance: top-3 hit rate >= 70%,
 * empty-result accuracy >= 80%.
 */
public final class RetrievalEvaluator {

    public record EvalQuestion(String question, String expectDoc, Integer expectSlide) {
        public boolean isJudged() {
            return expectDoc != null && !expectDoc.isBlank();
        }
    }

    public record EvalResult(EvalQuestion question, List<RetrievedChunk> top3, boolean hit, boolean isEmpty) {}

    public record Summary(int judgedTotal, int hitCount, int noAnswerTotal, int emptyCount,
                          List<EvalResult> results) {
        public double top3HitRate() {
            return judgedTotal == 0 ? 0 : (double) hitCount / judgedTotal;
        }

        public double emptyAccuracy() {
            return noAnswerTotal == 0 ? 1 : (double) emptyCount / noAnswerTotal;
        }

        public boolean passesAcceptance() {
            return top3HitRate() >= 0.70 && emptyAccuracy() >= 0.80;
        }
    }

    public static final class EvalFile {
        public List<EvalQuestion> questions;
    }

    public static EvalFile loadEvalFile(Path path) throws IOException {
        return new Gson().fromJson(Files.readString(path, StandardCharsets.UTF_8), EvalFile.class);
    }

    public static Summary evaluate(Retriever retriever, EvalFile file) {
        List<EvalResult> results = new ArrayList<>();
        int judged = 0;
        int hits = 0;
        int noAnswer = 0;
        int empties = 0;
        for (EvalQuestion question : file.questions) {
            List<RetrievedChunk> top3 = retriever.retrieve(question.question(), 8).stream().limit(3).toList();
            boolean isEmpty = top3.isEmpty();
            boolean hit = question.isJudged() && top3.stream().anyMatch(chunk ->
                    chunk.documentName().equals(question.expectDoc())
                            && chunk.slideNumber() == question.expectSlide());
            results.add(new EvalResult(question, top3, hit, isEmpty));
            if (question.isJudged()) {
                judged++;
                if (hit) hits++;
            } else {
                noAnswer++;
                if (isEmpty) empties++;
            }
        }
        return new Summary(judged, hits, noAnswer, empties, results);
    }

    /** Dev-time course materials dir: taide.materials.dir property, else TAIDE_MATERIALS_DIR env. */
    public static Path resolveMaterialsDir() {
        String property = System.getProperty("taide.materials.dir");
        String env = System.getenv("TAIDE_MATERIALS_DIR");
        String value = property != null && !property.isBlank() ? property
                : env != null && !env.isBlank() ? env : null;
        if (value == null) {
            throw new IllegalStateException(
                    "Course materials dir not configured: set taide.materials.dir or TAIDE_MATERIALS_DIR");
        }
        return Path.of(value);
    }
}
