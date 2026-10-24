package edu.cst3492.taide.rag.chunk;

import edu.cst3492.taide.rag.Chunk;
import edu.cst3492.taide.rag.ingest.CourseMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits slide text into chunks of ~{@link #TARGET_TOKENS} tokens with ~{@link #OVERLAP_TOKENS}
 * tokens of overlap between consecutive chunks. The one invariant that matters: chunks NEVER
 * cross slide boundaries — that is what keeps (document, slide) citations exact and cheap.
 */
public final class SlideAwareChunker {

    public static final int TARGET_TOKENS = 512;
    public static final int OVERLAP_TOKENS = 64;

    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n");
    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[。！？!?；;.])");

    public List<Chunk> chunk(CourseMaterial material) {
        List<Chunk> chunks = new ArrayList<>();
        for (CourseMaterial.Slide slide : material.slides()) {
            if (slide.text().isBlank()) continue;
            String text = slide.text().replaceAll("\\R+", "\n").trim();
            int index = 0;
            for (String piece : pieces(text)) {
                chunks.add(new Chunk(
                        material.documentName() + "#" + slide.number() + "#" + index,
                        material.documentName(),
                        slide.number(),
                        piece));
                index++;
            }
        }
        return chunks;
    }

    /** Sliding-window pieces: each fits {@link #TARGET_TOKENS}; consecutive pieces overlap. */
    private List<String> pieces(String text) {
        List<String> result = new ArrayList<>();
        String rest = text;
        while (estimateTokens(rest) > TARGET_TOKENS) {
            String head = takeUpTo(rest, TARGET_TOKENS);
            result.add(head);
            rest = tailTokens(head, OVERLAP_TOKENS) + rest.substring(head.length());
        }
        if (!rest.isBlank()) result.add(rest);
        return result;
    }

    /** Longest prefix of {@code text} that fits the budget, cut at paragraph/sentence boundaries. */
    private String takeUpTo(String text, int budget) {
        if (estimateTokens(text) <= budget) return text;
        StringBuilder acc = new StringBuilder();
        for (String paragraph : PARAGRAPH_BREAK.split(text)) {
            if (paragraph.isBlank()) continue;
            int remaining = budget - estimateTokens(acc.toString());
            if (estimateTokens(paragraph) > remaining) {
                acc.append(cutSegment(paragraph, remaining));
                return acc.toString();
            }
            acc.append(paragraph).append('\n');
        }
        return acc.toString();
    }

    /** Longest prefix of {@code paragraph} fitting the budget, cut at sentence boundaries or hard-cut. */
    private String cutSegment(String paragraph, int budget) {
        StringBuilder acc = new StringBuilder();
        for (String sentence : SENTENCE_BREAK.split(paragraph)) {
            if (sentence.isBlank()) continue;
            int remaining = budget - estimateTokens(acc.toString());
            if (remaining <= 0) break;
            if (estimateTokens(sentence) > remaining) {
                acc.append(hardCut(sentence, remaining));
                break;
            }
            acc.append(sentence);
        }
        return acc.toString();
    }

    /** Longest prefix of {@code text} that fits the budget, at arbitrary character positions. */
    private String hardCut(String text, int budget) {
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (estimateTokens(text.substring(0, mid)) <= budget) lo = mid;
            else hi = mid - 1;
        }
        return text.substring(0, lo);
    }

    /** Suffix of {@code text} worth about {@code budget} tokens, cut at sentence boundaries. */
    private String tailTokens(String text, int budget) {
        if (estimateTokens(text) <= budget) return text;
        String[] sentences = SENTENCE_BREAK.split(text);
        StringBuilder acc = new StringBuilder();
        for (int i = sentences.length - 1; i >= 0; i--) {
            String sentence = sentences[i];
            if (sentence.isBlank()) continue;
            if (estimateTokens(sentence) > budget) {
                // One punctuation-free run: take a hard tail instead of the whole sentence.
                acc.insert(0, sentence.substring(Math.max(0, sentence.length() - budget * 4)));
                break;
            }
            acc.insert(0, sentence);
            if (estimateTokens(acc.toString()) >= budget) break;
        }
        return acc.toString();
    }

    /**
     * Rough token estimate for sizing only: CJK chars ~1 token each, ASCII words ~1.3.
     * Chunk budgets do not need exactness, only stability.
     */
    static int estimateTokens(String text) {
        int cjk = 0;
        int asciiWords = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < text.length()) {
                i++;
                continue;
            }
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                cjk++;
                inWord = false;
            } else if (Character.isLetterOrDigit(c)) {
                if (!inWord) {
                    asciiWords++;
                    inWord = true;
                }
            } else {
                inWord = false;
            }
        }
        return cjk + (int) Math.ceil(asciiWords * 1.3);
    }
}
