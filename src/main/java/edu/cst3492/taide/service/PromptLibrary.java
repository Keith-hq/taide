package edu.cst3492.taide.service;

import edu.cst3492.taide.rag.RetrievedChunk;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Loads the versioned prompt templates from resources/prompts and builds the
 * system/user messages for grounded and general-knowledge answers. Prompts are
 * files, never string concatenations — the report cites them and reviewers read
 * them (TEAM A-6 rule).
 */
public final class PromptLibrary {

    private static final String QA_PROMPT_RESOURCE = "/prompts/qa.md";
    private static final String SNIPPET_SEPARATOR =
            "\n--- Retrieved course snippets ---\n";

    private final String systemPrompt;

    public PromptLibrary() throws IOException {
        this.systemPrompt = loadQaPrompt();
    }

    private static String loadQaPrompt() throws IOException {
        try (InputStream in = PromptLibrary.class.getResourceAsStream(QA_PROMPT_RESOURCE)) {
            if (in == null) {
                throw new IOException("Missing prompt resource " + QA_PROMPT_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    /** Grounded mode: snippets are formatted with explicit document and page numbers. */
    public String userMessageWithSnippets(String question, CodeContext context, List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        appendCodeContext(sb, context);
        sb.append(SNIPPET_SEPARATOR);
        int index = 1;
        for (RetrievedChunk chunk : chunks) {
            sb.append('[').append(index++).append("] ")
                    .append(chunk.documentName()).append(" · page ").append(chunk.slideNumber())
                    .append(" (similarity ").append(String.format(Locale.ROOT, "%.3f", chunk.score())).append(")\n")
                    .append(chunk.text()).append('\n');
        }
        sb.append("--- End of retrieved snippets ---\n\n");
        sb.append("Question: ").append(question);
        return sb.toString();
    }

    /** General-knowledge mode: no snippets, the prompt's mode-2 rule applies. */
    public String userMessageWithoutSnippets(String question, CodeContext context) {
        StringBuilder sb = new StringBuilder();
        appendCodeContext(sb, context);
        sb.append("Question: ").append(question);
        return sb.toString();
    }

    private static void appendCodeContext(StringBuilder sb, CodeContext context) {
        if (context == null || context.selectedText() == null || context.selectedText().isBlank()) {
            return;
        }
        sb.append("The student selected this code (file ").append(context.filePath());
        if (context.startLine() > 0) {
            sb.append(", lines ").append(context.startLine()).append('-').append(context.endLine());
        }
        sb.append("):\n```").append(context.language() == null ? "java" : context.language())
                .append('\n').append(context.selectedText()).append("\n```\n\n");
    }
}
