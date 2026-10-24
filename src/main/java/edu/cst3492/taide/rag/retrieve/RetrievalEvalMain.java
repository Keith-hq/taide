package edu.cst3492.taide.rag.retrieve;

import edu.cst3492.taide.rag.Retriever;
import edu.cst3492.taide.rag.RetrievedChunk;
import edu.cst3492.taide.rag.embed.EmbeddingModel;
import edu.cst3492.taide.rag.embed.OnnxEmbeddingModel;
import edu.cst3492.taide.rag.index.IndexBuilder;
import edu.cst3492.taide.rag.index.InMemoryVectorIndex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dev CLI for A-3 tuning: builds the index, runs the eval set, prints per-question
 * top-3 and the summary. Usage:
 * {@code ./gradlew evalRetrieval --args="<materials-dir> <index-json> <eval-json>"}
 */
public final class RetrievalEvalMain {

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("usage: RetrievalEvalMain <materials-dir> <index-json> <eval-json>");
            System.exit(2);
        }
        Path materials = Path.of(args[0]);
        Path indexPath = Path.of(args[1]);
        Path evalPath = Path.of(args[2]);
        if (!Files.isDirectory(materials) || !Files.exists(evalPath)) {
            System.err.println("materials dir or eval file missing");
            System.exit(2);
        }

        EmbeddingModel model = OnnxEmbeddingModel.openDefault();
        new IndexBuilder(model).build(materials, indexPath, null);
        Retriever retriever = new EmbeddingRetriever(InMemoryVectorIndex.load(indexPath), model);
        RetrievalEvaluator.Summary summary = RetrievalEvaluator.evaluate(retriever,
                RetrievalEvaluator.loadEvalFile(evalPath));

        int index = 1;
        for (RetrievalEvaluator.EvalResult result : summary.results()) {
            String verdict = result.question().isJudged() ? (result.hit() ? "HIT " : "MISS") : (result.isEmpty() ? "EMPTY" : "FALSE-HIT");
            String top = result.top3().isEmpty() ? "-"
                    : result.top3().get(0).documentName() + " p" + result.top3().get(0).slideNumber()
                    + String.format(" (%.3f)", result.top3().get(0).score());
            String expect = result.question().isJudged()
                    ? result.question().expectDoc() + " p" + result.question().expectSlide()
                    : "no answer expected";
            System.out.printf("Q%02d %-8s top1=%s | expect %s | %s%n",
                    index++, verdict, top, expect, truncate(result.question().question(), 60));
        }
        System.out.printf("SUMMARY judged=%d hit=%d top3-rate=%.1f%% | no-answer=%d empty=%d accuracy=%.1f%% | %s%n",
                summary.judgedTotal(), summary.hitCount(), summary.top3HitRate() * 100,
                summary.noAnswerTotal(), summary.emptyCount(), summary.emptyAccuracy() * 100,
                summary.passesAcceptance() ? "ACCEPTANCE PASS" : "ACCEPTANCE FAIL");
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
