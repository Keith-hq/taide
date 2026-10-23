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
        if (document == null || document.isBlank()) throw new IOException("引用缺少文档名");
        Path root = courseRoot.toRealPath();
        Path relative;
        try { relative = Path.of(document); } catch (InvalidPathException e) { throw new IOException("引用文档名无效"); }
        if (relative.isAbsolute() || document.contains(":") || relative.normalize().startsWith(".."))
            throw new IOException("引用文件必须位于课件目录内");
        Path direct = root.resolve(relative).normalize();
        if (!direct.startsWith(root)) throw new IOException("引用文件必须位于课件目录内");
        if (Files.isRegularFile(direct)) return check(root, direct);
        if (relative.getNameCount() != 1) throw new IOException("没有找到引用文件，请检查课件目录");
        try (Stream<Path> files = Files.walk(root, 8)) {
            List<Path> matches = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(document)).limit(2).toList();
            if (matches.isEmpty()) throw new IOException("没有找到 " + document + "，请检查课件目录");
            if (matches.size() > 1) throw new IOException("存在多个同名课件，请让引用包含相对目录");
            return check(root, matches.getFirst());
        }
    }
    private static Path check(Path root, Path file) throws IOException {
        Path actual = file.toRealPath();
        if (!actual.startsWith(root)) throw new IOException("引用文件不能指向课件目录之外");
        String name = actual.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".pdf") && !name.endsWith(".pptx")) throw new IOException("仅支持 PDF 和 PPTX 课件引用");
        return actual;
    }
}
