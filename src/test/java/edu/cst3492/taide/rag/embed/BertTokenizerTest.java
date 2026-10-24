package edu.cst3492.taide.rag.embed;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Uses a tiny fixture vocab (src/test/resources/rag/vocab_test.txt) so the tokenizer
 * logic is tested hermetically: [PAD]=0 [UNK]=1 [CLS]=2 [SEP]=3 [MASK]=4
 * a=5 b=6 ##b=7 hello=8 world=9 play=10 ##ing=11 中=12 文=13.
 */
class BertTokenizerTest {

    private static BertTokenizer tokenizer() throws IOException {
        try (InputStream in = BertTokenizerTest.class.getResourceAsStream("/rag/vocab_test.txt")) {
            return new BertTokenizer(in);
        }
    }

    @Test
    void addsClsSepAndLooksUpVocabIds() throws IOException {
        assertEquals(List.of(2, 8, 9, 3), tokenizer().tokenize("hello world"));
    }

    @Test
    void wordpieceSplitsContinuations() throws IOException {
        assertEquals(List.of(2, 10, 11, 3), tokenizer().tokenize("playing"));
    }

    @Test
    void chineseCharsBecomeSingleTokens() throws IOException {
        assertEquals(List.of(2, 12, 13, 3), tokenizer().tokenize("中文"));
    }

    @Test
    void punctuationSplitsAndUnknownBecomesUnk() throws IOException {
        assertEquals(List.of(2, 5, 1, 6, 3), tokenizer().tokenize("a,b"));
    }

    @Test
    void unknownWordBecomesUnk() throws IOException {
        assertEquals(List.of(2, 1, 3), tokenizer().tokenize("zzz"));
    }

    @Test
    void lowercasesInput() throws IOException {
        assertEquals(List.of(2, 8, 9, 3), tokenizer().tokenize("Hello WORLD"));
    }

    @Test
    void controlCharsBecomeSpaces() throws IOException {
        assertEquals(List.of(2, 5, 6, 3), tokenizer().tokenize("a" + (char) 1 + "b"));
    }

    @Test
    void truncatesAtMaxLength() throws IOException {
        List<Integer> ids = tokenizer().tokenize("a ".repeat(600));
        assertEquals(BertTokenizer.MAX_LENGTH, ids.size());
        assertEquals(2, ids.get(0));
        assertEquals(3, ids.get(BertTokenizer.MAX_LENGTH - 1));
    }
}
