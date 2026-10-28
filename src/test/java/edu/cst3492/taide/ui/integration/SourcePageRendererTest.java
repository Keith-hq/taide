package edu.cst3492.taide.ui.integration;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class SourcePageRendererTest {
    @TempDir Path temporary;

    @Test void pdfTargetsPhysicalPagesIncludingBlankPagesWithoutModifyingTheSource() throws Exception {
        Path source = pdf(temporary.resolve("Unit #1 notes.pdf"), new PDRectangle(144, 72));
        byte[] original = Files.readAllBytes(source);
        var first = SourcePageRenderer.render(source, 1);
        var blank = SourcePageRenderer.render(source, 2);
        var third = SourcePageRenderer.render(source, 3);
        assertEquals(3, third.count());
        assertEquals(3, third.number());
        assertColor(Color.RED, first);
        assertColor(Color.WHITE, blank);
        assertColor(Color.BLUE, third);
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test void pptxOpensTheRequestedSlideAndDoesNotRewriteThePresentation() throws Exception {
        Path source = pptx(temporary.resolve("lecture.pptx"));
        byte[] original = Files.readAllBytes(source);
        assertColor(Color.RED, SourcePageRenderer.render(source, 1));
        var page = SourcePageRenderer.render(source, 2);
        assertEquals(2, page.count());
        assertEquals(2, page.number());
        assertColor(Color.BLUE, page);
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test void outOfRangePagesAreRejectedInsteadOfSilentlyOpeningAnotherPage() throws Exception {
        for (Path source : new Path[]{pdf(temporary.resolve("notes.pdf"), new PDRectangle(144, 72)), pptx(temporary.resolve("slides.pptx"))}) {
            for (int page : new int[]{0, -1, 99}) {
                var failure = assertThrows(SourcePageRenderer.PageOutOfRangeException.class,
                        () -> SourcePageRenderer.render(source, page));
                assertTrue(failure.count() >= 2);
                assertTrue(failure.getMessage().contains(Integer.toString(page)));
            }
        }
    }

    @Test void oversizedPdfPagesHaveBoundedRasterDimensions() throws Exception {
        Path source = pdf(temporary.resolve("large.pdf"), new PDRectangle(10000, 2000));
        var page = SourcePageRenderer.render(source, 3);
        assertTrue(page.image().getWidth() <= 1800);
        assertTrue(page.image().getHeight() <= 1800);
        assertColor(Color.BLUE, page);
    }

    @Test void corruptAndUnsupportedFilesFailClearly() throws Exception {
        Path text = Files.writeString(temporary.resolve("text.txt"), "not a document");
        assertThrows(IOException.class, () -> SourcePageRenderer.render(text, 1));
        Path broken = Files.writeString(temporary.resolve("broken.pdf"), "not a PDF");
        assertThrows(IOException.class, () -> SourcePageRenderer.render(broken, 1));
    }

    static Path pdf(Path path, PDRectangle bounds) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (Color color : new Color[]{Color.RED, Color.WHITE, Color.BLUE}) {
                PDPage page = new PDPage(bounds);
                document.addPage(page);
                try (var content = new PDPageContentStream(document, page)) {
                    content.setNonStrokingColor(color);
                    content.addRect(0, 0, bounds.getWidth(), bounds.getHeight());
                    content.fill();
                }
            }
            document.save(path.toFile());
        }
        return path;
    }

    static Path pptx(Path path) throws IOException {
        try (XMLSlideShow document = new XMLSlideShow()) {
            document.setPageSize(new Dimension(360, 240));
            for (Color color : new Color[]{Color.RED, Color.BLUE}) {
                var slide = document.createSlide();
                var shape = slide.createAutoShape();
                shape.setShapeType(ShapeType.RECT);
                shape.setAnchor(new Rectangle2D.Double(0, 0, 360, 240));
                shape.setFillColor(color);
                shape.setLineColor(color);
            }
            try (var output = Files.newOutputStream(path)) { document.write(output); }
        }
        return path;
    }

    private static void assertColor(Color expected, SourcePageRenderer.Page page) {
        Color actual = new Color(page.image().getRGB(page.image().getWidth() / 2, page.image().getHeight() / 2));
        assertTrue(Math.abs(expected.getRed() - actual.getRed()) <= 2);
        assertTrue(Math.abs(expected.getGreen() - actual.getGreen()) <= 2);
        assertTrue(Math.abs(expected.getBlue() - actual.getBlue()) <= 2);
    }
}
