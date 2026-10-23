package edu.cst3492.taide.ui.chat;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;

public final class CitationFilesTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("taide-citation-test-");
        try {
            Path slides = Files.createDirectories(root.resolve("week5"));
            Path pdf = Files.writeString(slides.resolve("Spring.pdf"), "fixture");
            check(CitationFiles.resolve(root, "week5/Spring.pdf").equals(pdf), "relative source");
            check(CitationFiles.resolve(root, "Spring.pdf").equals(pdf), "nested basename fallback");
            rejects(root, "../Spring.pdf"); rejects(root, pdf.toAbsolutePath().toString());
            rejects(root, "https://example.org/notes.pdf"); rejects(root, "missing.pdf");
            Files.writeString(root.resolve("launch.exe"), "fixture"); rejects(root, "launch.exe");
            Files.writeString(slides.resolve("Duplicate.pdf"), "fixture");
            Files.createDirectories(root.resolve("other"));
            Files.writeString(root.resolve("other/Duplicate.pdf"), "fixture");
            rejects(root, "Duplicate.pdf");
        } finally {
            try (var files = Files.walk(root)) { for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
        }
        System.out.println("CitationFilesTest: 8 checks passed");
    }
    private static void rejects(Path root, String input) throws IOException {
        try { CitationFiles.resolve(root, input); throw new AssertionError("Accepted " + input); }
        catch (IOException expected) { }
    }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}
