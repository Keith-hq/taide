package edu.cst3492.taide.rag.ingest;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Page-aware PDF parsing: one {@link CourseMaterial.Slide} per physical page.
 * Uses PDFBox (the engine underneath Tika's PDF parser) directly so page boundaries
 * are exact — page numbers must match what the PDF reader displays, or every
 * citation downstream would be wrong.
 */
public final class PdfIngestor {

    public CourseMaterial ingest(Path pdfPath) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(false); // logical reading order, not visual coordinates
            List<CourseMaterial.Slide> slides = new ArrayList<>(document.getNumberOfPages());
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = CourseMaterial.sanitize(stripper.getText(document));
                if (!text.isEmpty()) {
                    slides.add(new CourseMaterial.Slide(page, text));
                }
            }
            if (slides.isEmpty()) {
                throw new IOException("No extractable text in " + pdfPath
                        + " (image-only PDFs would need OCR, which is out of scope)");
            }
            return new CourseMaterial(pdfPath.getFileName().toString(), slides);
        }
    }
}
