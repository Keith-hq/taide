package edu.cst3492.taide.rag.embed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal BERT WordPiece tokenizer for all-MiniLM-L6-v2, matching the reference
 * BertTokenizer behavior: BertNormalizer (clean text, CJK chars kept as single
 * tokens, lowercase) plus greedy longest-match WordPiece with "##" continuations.
 * Special token ids are read from the vocab so test fixtures can use tiny vocabs.
 */
public final class BertTokenizer {

    public static final int MAX_LENGTH = 512;
    private static final int MAX_CHARS_PER_WORD = 100;

    private final Map<String, Integer> vocab;
    private final int clsId;
    private final int sepId;
    private final int unkId;

    public BertTokenizer(Path vocabPath) throws IOException {
        this(Files.readAllLines(vocabPath, StandardCharsets.UTF_8));
    }

    public BertTokenizer(List<String> vocabTokens) {
        this.vocab = new HashMap<>(vocabTokens.size() * 2);
        for (int i = 0; i < vocabTokens.size(); i++) {
            vocab.put(vocabTokens.get(i), i);
        }
        this.clsId = vocab.getOrDefault("[CLS]", 101);
        this.sepId = vocab.getOrDefault("[SEP]", 102);
        this.unkId = vocab.getOrDefault("[UNK]", 100);
    }

    public BertTokenizer(InputStream vocabStream) throws IOException {
        this(readLines(vocabStream));
    }

    private static List<String> readLines(InputStream stream) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** Tokenizes text to {@code [CLS] ... [SEP]} ids, truncated to {@link #MAX_LENGTH}. */
    public List<Integer> tokenize(String text) {
        List<Integer> ids = new ArrayList<>();
        ids.add(clsId);
        for (String word : whitespaceTokenize(clean(text))) {
            for (Integer id : tokenizeWord(word)) {
                if (ids.size() >= MAX_LENGTH - 1) return close(ids);
                ids.add(id);
            }
        }
        return close(ids);
    }

    private List<Integer> close(List<Integer> ids) {
        ids.add(sepId);
        return ids;
    }

    private List<Integer> tokenizeWord(String word) {
        List<Integer> ids = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (isPunct(c) || isChineseChar(c)) {
                if (buf.length() > 0) {
                    ids.addAll(wordpiece(buf.toString()));
                    buf.setLength(0);
                }
                ids.add(vocab.getOrDefault(String.valueOf(c), unkId));
            } else {
                buf.append(c);
            }
        }
        if (buf.length() > 0) {
            ids.addAll(wordpiece(buf.toString()));
        }
        return ids;
    }

    private List<Integer> wordpiece(String word) {
        List<Integer> ids = new ArrayList<>();
        if (word.length() > MAX_CHARS_PER_WORD) {
            ids.add(unkId);
            return ids;
        }
        int start = 0;
        boolean first = true;
        while (start < word.length()) {
            int end = word.length();
            boolean found = false;
            while (start < end) {
                String candidate = first ? word.substring(start, end) : "##" + word.substring(start, end);
                Integer id = vocab.get(candidate);
                if (id != null) {
                    ids.add(id);
                    start = end;
                    found = true;
                    break;
                }
                end--;
            }
            if (!found) {
                ids.add(unkId);
                break;
            }
            first = false;
        }
        return ids;
    }

    /** BertNormalizer clean_text: control chars become spaces; text is lowercased. */
    private static String clean(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 0 || c == 0xFFFD) continue;
            sb.append(Character.isISOControl(c) ? ' ' : c);
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static List<String> whitespaceTokenize(String text) {
        List<String> words = new ArrayList<>();
        int start = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            boolean ws = Character.isWhitespace(text.charAt(i));
            if (ws && inWord) {
                words.add(text.substring(start, i));
                inWord = false;
            } else if (!ws && !inWord) {
                start = i;
                inWord = true;
            }
        }
        if (inWord) words.add(text.substring(start));
        return words;
    }

    private static boolean isPunct(char c) {
        if ((c >= 33 && c <= 47) || (c >= 58 && c <= 64) || (c >= 91 && c <= 96)
                || (c >= 123 && c <= 126)) {
            return true;
        }
        int type = Character.getType(c);
        return type == Character.OTHER_PUNCTUATION || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION || type == Character.END_PUNCTUATION
                || type == Character.CONNECTOR_PUNCTUATION || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION;
    }

    private static boolean isChineseChar(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)
                || (c >= 0x3400 && c <= 0x4DBF)
                || (c >= 0xF900 && c <= 0xFAFF);
    }
}
