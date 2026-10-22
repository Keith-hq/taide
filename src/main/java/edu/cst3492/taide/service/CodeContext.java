package edu.cst3492.taide.service;

import java.util.Objects;

public record CodeContext(
        String filePath,
        String language,
        String selectedText,
        int startLine,
        int endLine,
        String enclosingClass
) {
    public CodeContext {
        Objects.requireNonNull(filePath);
        Objects.requireNonNull(language);
        Objects.requireNonNull(selectedText);
        Objects.requireNonNull(enclosingClass);
    }

    public static CodeContext empty() {
        return new CodeContext("", "", "", 0, 0, "");
    }
}
