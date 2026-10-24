package edu.cst3492.taide.rag.index;

import edu.cst3492.taide.rag.embed.EmbeddingModel;
import edu.cst3492.taide.rag.embed.OnnxEmbeddingModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Dev CLI for A-2 acceptance: build (or rebuild) the index from a materials directory
 * and optionally run a query against it. Rebuild timing shows the cache hit rate.
 * Usage: {@code ./gradlew buildIndex --args="<materials-dir> <index-json> [query]"}
 */
public final class BuildIndexMain {

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || !Files.isDirectory(Path.of(args[0]))) {
            System.err.println("usage: BuildIndexMain <materials-dir> <index-json> [query words...]");
            System.exit(2);
        }
        Path materials = Path.of(args[0]);
        Path indexPath = Path.of(args[1]);
        String query = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : null;

        long start = System.nanoTime();
        EmbeddingModel model = OnnxEmbeddingModel.openDefault();
        IndexBuilder.Result result = new IndexBuilder(model).build(materials, indexPath, null);
        long millis = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("index=%s docs=%d chunks=%d embedded=%d reused=%d total=%dms%n",
                indexPath, result.documentCount(), result.chunkCount(),
                result.embeddedCount(), result.reusedCount(), millis);

        if (query != null) {
            InMemoryVectorIndex index = InMemoryVectorIndex.load(indexPath);
            float[] queryVector = model.embed(List.of(query))[0];
            System.out.println("query: " + query);
            for (VectorIndex.ScoredChunk hit : index.search(queryVector, 5)) {
                System.out.printf("  %.3f | %s | %s%n",
                        hit.score(), hit.chunk().id(), preview(hit.chunk().text()));
            }
        }
    }

    private static String preview(String text) {
        String singleLine = text.replace('\n', ' ');
        return singleLine.length() <= 60 ? singleLine : singleLine.substring(0, 60) + "...";
    }
}
