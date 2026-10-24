package edu.cst3492.taide.rag.retrieve;

import edu.cst3492.taide.rag.Retriever;
import edu.cst3492.taide.rag.embed.OnnxEmbeddingModel;
import edu.cst3492.taide.rag.index.IndexBuilder;
import edu.cst3492.taide.rag.index.InMemoryVectorIndex;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-corpus acceptance gate for A-3, excluded by default. Run with:
 * TAIDE_MODEL_DIR=... TAIDE_MATERIALS_DIR=... ./gradlew test -PliveModelTests=true
 * Acceptance: top-3 hit rate >= 70%, empty-result accuracy >= 80%.
 */
@Tag("live-model")
class RetrievalEvaluatorTest {

    @Test
    void acceptanceThresholdsHold() throws IOException {
        Path materials = RetrievalEvaluator.resolveMaterialsDir();
        Path evalFile = Path.of("src/test/resources/rag/eval-questions.json");
        Path indexPath = Files.createTempDirectory("taide-eval").resolve("index.json");

        OnnxEmbeddingModel model = OnnxEmbeddingModel.openDefault();
        new IndexBuilder(model).build(materials, indexPath, null);
        Retriever retriever = new EmbeddingRetriever(InMemoryVectorIndex.load(indexPath), model);
        RetrievalEvaluator.Summary summary = RetrievalEvaluator.evaluate(retriever,
                RetrievalEvaluator.loadEvalFile(evalFile));

        for (RetrievalEvaluator.EvalResult result : summary.results()) {
            System.out.printf("%s %-8s %s -> %s%n",
                    result.question().isJudged() ? (result.hit() ? "HIT " : "MISS") : (result.isEmpty() ? "EMPTY" : "FALSE-HIT"),
                    result.question().isJudged() ? result.question().expectDoc() + " p" + result.question().expectSlide() : "no-answer",
                    result.top3().isEmpty() ? "-" : result.top3().get(0).documentName() + " p" + result.top3().get(0).slideNumber(),
                    result.question().question());
        }
        System.out.printf("top3-rate=%.2f empty-accuracy=%.2f%n",
                summary.top3HitRate(), summary.emptyAccuracy());
        assertTrue(summary.passesAcceptance(),
                () -> "A-3 acceptance failed: top3=" + summary.top3HitRate()
                        + " empty=" + summary.emptyAccuracy());
    }
}
