package edu.cst3492.taide.rag.index;

import edu.cst3492.taide.rag.Chunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryVectorIndexTest {

    private static Chunk chunk(String id, int slide, String text) {
        return new Chunk(id, "doc.pdf", slide, text);
    }

    @Test
    void searchReturnsTopKBestFirst() {
        InMemoryVectorIndex index = new InMemoryVectorIndex();
        index.add(chunk("a", 1, "one"), new float[]{1, 0});
        index.add(chunk("b", 2, "two"), new float[]{0.9f, 0.1f});
        index.add(chunk("c", 3, "three"), new float[]{0, 1});

        List<VectorIndex.ScoredChunk> hits = index.search(new float[]{1, 0}, 2);

        assertEquals(2, hits.size());
        assertEquals("a", hits.get(0).chunk().id());
        assertEquals(1.0, hits.get(0).score(), 1e-6);
        assertEquals("b", hits.get(1).chunk().id());
        assertTrue(hits.get(0).score() >= hits.get(1).score());
    }

    @Test
    void searchOnEmptyIndexReturnsEmpty() {
        assertEquals(List.of(), new InMemoryVectorIndex().search(new float[]{1, 0}, 5));
    }

    @Test
    void jsonRoundTripKeepsSchemaAndVectors(@TempDir Path dir) throws IOException {
        InMemoryVectorIndex index = new InMemoryVectorIndex();
        index.add(chunk("doc.pdf#3#0", 3, "constructor injection"), new float[]{0.5f, 0.5f});
        Path file = dir.resolve("index.json");

        index.save(file, "all-MiniLM-L6-v2");

        String raw = Files.readString(file);
        for (String key : new String[]{"\"version\":1", "\"model\"", "\"dim\"", "\"chunks\"",
                "\"id\"", "\"doc\"", "\"slide\"", "\"text\"", "\"vec\""}) {
            assertTrue(raw.contains(key), "missing key " + key);
        }
        InMemoryVectorIndex loaded = InMemoryVectorIndex.load(file);
        assertEquals(1, loaded.size());
        assertEquals(1, loaded.search(new float[]{0.5f, 0.5f}, 3).size());
        assertEquals(1.0, loaded.search(new float[]{0.5f, 0.5f}, 3).get(0).score(), 1e-6);
    }

    @Test
    void loadRejectsUnknownVersion(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("index.json");
        Files.writeString(file, "{\"version\":99,\"model\":\"x\",\"dim\":2,\"chunks\":[]}");
        assertThrows(IOException.class, () -> InMemoryVectorIndex.load(file));
    }
}
