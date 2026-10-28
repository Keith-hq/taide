package edu.cst3492.taide.ui.chat;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Resolves untrusted citation metadata only inside the user's configured course directory. */
public final class CitationFiles {
    private CitationFiles() {}
    public static Path resolve(Path courseRoot, String document) throws IOException {
        if (document == null || document.isBlank()) throw new IOException("Citation is missing a document name");
        Path root = courseRoot.toRealPath();
        Path relative;
        try { relative = Path.of(document); } catch (InvalidPathException e) { throw new IOException("Citation document name is invalid"); }
        if (relative.isAbsolute() || document.contains(":") || relative.normalize().startsWith(".."))
            throw new IOException("Source files must be inside the materials folder");
        Path direct = root.resolve(relative).normalize();
        if (!direct.startsWith(root)) throw new IOException("Source files must be inside the materials folder");
        if (Files.isRegularFile(direct)) return check(root, direct);
        if (relative.getNameCount() != 1) throw new IOException("Source file not found. Check the materials folder");
        try (Stream<Path> files = Files.walk(root, 8)) {
            List<Path> matches = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(document)).limit(2).toList();
            if (matches.isEmpty()) throw new IOException("File not found: " + document + ". Check the materials folder");
            if (matches.size() > 1) throw new IOException("Multiple files share this name. The citation needs a relative path");
            return check(root, matches.getFirst());
        }
    }
    private static Path check(Path root, Path file) throws IOException {
        Path actual = file.toRealPath();
        if (!actual.startsWith(root)) throw new IOException("Source files cannot point outside the materials folder");
        String name = actual.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".pdf") && !name.endsWith(".pptx")) throw new IOException("Only PDF and PPTX source files are supported");
        return actual;
    }
}
