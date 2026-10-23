package edu.cst3492.taide.action.automation;

public record GeneratedTest(String packageName, String className, String source) {
    public String relativePath() {
        String directory = packageName.isBlank() ? "" : packageName.replace('.', '/') + "/";
        return directory + className + ".java";
    }
}
