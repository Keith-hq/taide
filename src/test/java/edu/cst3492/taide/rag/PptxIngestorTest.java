package edu.cst3492.taide.rag;

import edu.cst3492.taide.rag.ingest.CourseMaterial;
import edu.cst3492.taide.rag.ingest.PptxIngestor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PptxIngestorTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsPerSlideTextWithOneBasedNumbering() throws IOException {
        Path pptx = tempDir.resolve("Lecture-Test.pptx");
        try (XMLSlideShow show = new XMLSlideShow()) {
            XSLFSlide slide1 = show.createSlide();
            slide1.createTextBox().setText("SLIDE_ONE_MARKER");
            XSLFSlide slide2 = show.createSlide();
            slide2.createTextBox().setText("SLIDE_TWO_MARKER 构造器注入让依赖关系在创建对象时就明确");
            try (FileOutputStream out = new FileOutputStream(pptx.toFile())) {
                show.write(out);
            }
        }

        CourseMaterial material = new PptxIngestor().ingest(pptx);

        assertEquals("Lecture-Test.pptx", material.documentName());
        assertEquals(2, material.slides().size());
        assertEquals(1, material.slides().get(0).number());
        assertEquals(2, material.slides().get(1).number());
        assertTrue(material.slides().get(0).text().contains("SLIDE_ONE_MARKER"));
        assertTrue(material.slides().get(1).text().contains("SLIDE_TWO_MARKER"));
        assertTrue(material.slides().get(1).text().contains("构造器注入"));
    }
}
