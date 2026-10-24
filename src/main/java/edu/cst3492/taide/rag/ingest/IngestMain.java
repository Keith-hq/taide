package edu.cst3492.taide.rag.ingest;

import edu.cst3492.taide.rag.Chunk;
import edu.cst3492.taide.rag.chunk.SlideAwareChunker;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Dev CLI for A-1 acceptance: ingests every PDF/PPTX in a directory and prints
 * chunk statistics plus 10 random samples for manual slide-number verification.
 * Usage: {@code ./gradlew ingestMaterials --args="<materials-dir>"}
 */
public final class IngestMain {

    public static void main(String[] args) throws IOException {
        if (args.length != 1 || !Files.isDirectory(Path.of(args[0]))) {
            System.err.println("usage: IngestMain <materials-dir>");
            System.exit(2);
        }
        SlideAwareChunker chunker = new SlideAwareChunker();
        List<Chunk> all = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of(args[0]))) {
            for (Path file : stream) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                try {
                    CourseMaterial material = name.endsWith(".pdf") ? new PdfIngestor().ingest(file)
                            : name.endsWith(".pptx") ? new PptxIngestor().ingest(file)
                            : null;
                    if (material == null) continue;
                    List<Chunk> chunks = chunker.chunk(material);
                    all.addAll(chunks);
                    System.out.printf("%s | %d slides | %d chunks%n",
                            material.documentName(), material.slides().size(), chunks.size());
                } catch (IOException e) {
                    failures.add(file.getFileName() + ": " + e.getMessage());
                }
            }
        }
        System.out.println("TOTAL chunks: " + all.size());
        failures.forEach(failure -> System.out.println("FAILED " + failure));
        List<Chunk> samples = new ArrayList<>(all);
        Collections.shuffle(samples, new Random(42));
        System.out.println("--- 10 random samples (verify slide numbers against the source files) ---");
        for (Chunk chunk : samples.subList(0, Math.min(10, samples.size()))) {
            System.out.printf("%s | %s slide %d | %s%n",
                    chunk.id(), chunk.documentName(), chunk.slideNumber(), preview(chunk.text()));
        }
    }

    private static String preview(String text) {
        String singleLine = text.replace('\n', ' ');
        return singleLine.length() <= 80 ? singleLine : singleLine.substring(0, 80) + "...";
    }
}
