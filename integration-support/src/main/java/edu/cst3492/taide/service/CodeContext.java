package edu.cst3492.taide.service;

/** Frozen TEAM §4 contract. Sandbox only; A supplies this type in the integrated project. */
public record CodeContext(String filePath, String language, String selectedText,
                          int startLine, int endLine, String enclosingClass) {
    public static CodeContext empty() { return new CodeContext("", "", "", 0, 0, ""); }
}
