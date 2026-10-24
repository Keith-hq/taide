package edu.cst3492.taide.rag;

import edu.cst3492.taide.rag.ingest.CourseMaterial;
import edu.cst3492.taide.rag.ingest.PdfIngestor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfIngestorTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsPerPageTextWithOneBasedNumbering() throws IOException {
        Path pdf = tempDir.resolve("Lecture-Test.pdf");
        writeThreePagePdf(pdf);

        CourseMaterial material = new PdfIngestor().ingest(pdf);

        assertEquals("Lecture-Test.pdf", material.documentName());
        assertEquals(3, material.slides().size());
        assertEquals(1, material.slides().get(0).number());
        assertEquals(2, material.slides().get(1).number());
        assertEquals(3, material.slides().get(2).number());
        assertTrue(material.slides().get(0).text().contains("PAGE_ONE_MARKER"));
        assertTrue(material.slides().get(1).text().contains("PAGE_TWO_MARKER"));
        assertTrue(material.slides().get(2).text().contains("PAGE_THREE_MARKER"));
        assertFalse(material.slides().get(0).text().contains("PAGE_TWO_MARKER"));
    }

    private static void writeThreePagePdf(Path path) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (String marker : new String[]{"PAGE_ONE_MARKER", "PAGE_TWO_MARKER", "PAGE_THREE_MARKER"}) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 720);
                    content.showText(marker + " with some page body text");
                    content.endText();
                }
            }
            document.save(path.toFile());
        }
    }
}
