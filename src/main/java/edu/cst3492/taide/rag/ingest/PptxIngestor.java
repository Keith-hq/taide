package edu.cst3492.taide.rag.ingest;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Slide-aware PPTX parsing with POI XSLF: one {@link CourseMaterial.Slide} per slide
 * (1-based), including speaker notes. Deliberately NOT Tika — Tika's plain-text
 * output does not preserve slide boundaries (TEAM risk R3).
 */
public final class PptxIngestor {

    public CourseMaterial ingest(Path pptxPath) throws IOException {
        List<CourseMaterial.Slide> slides = new ArrayList<>();
        try (InputStream in = Files.newInputStream(pptxPath);
             XMLSlideShow show = new XMLSlideShow(in)) {
            for (XSLFSlide slide : show.getSlides()) {
                StringBuilder text = new StringBuilder();
                for (XSLFShape shape : slide.getShapes()) {
                    appendShapeText(shape, text);
                }
                appendNotes(slide, text);
                String slideText = CourseMaterial.sanitize(text.toString());
                if (!slideText.isEmpty()) {
                    slides.add(new CourseMaterial.Slide(slide.getSlideNumber(), slideText));
                }
            }
        }
        if (slides.isEmpty()) {
            throw new IOException("No slide text extracted from " + pptxPath);
        }
        return new CourseMaterial(pptxPath.getFileName().toString(), slides);
    }

    private static void appendShapeText(XSLFShape shape, StringBuilder out) {
        if (shape instanceof XSLFTextShape textShape) {
            out.append(textShape.getText()).append('\n');
        } else if (shape instanceof XSLFTable table) {
            for (XSLFTableRow row : table.getRows()) {
                for (XSLFTableCell cell : row.getCells()) {
                    out.append(cell.getText()).append('\n');
                }
            }
        } else if (shape instanceof XSLFGroupShape group) {
            for (XSLFShape child : group.getShapes()) {
                appendShapeText(child, out);
            }
        }
    }

    private static void appendNotes(XSLFSlide slide, StringBuilder out) {
        try {
            XSLFNotes notes = slide.getNotes();
            if (notes == null) return;
            for (XSLFShape shape : notes.getShapes()) {
                appendShapeText(shape, out);
            }
        } catch (RuntimeException e) {
            // Notes are optional; a broken notes part must not fail the whole slide.
        }
    }
}
