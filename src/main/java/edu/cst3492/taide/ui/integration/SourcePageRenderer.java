package edu.cst3492.taide.ui.integration;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.xslf.usermodel.XMLSlideShow;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** Renders one physical, 1-based PDF page or PPTX slide without changing the source file. */
public final class SourcePageRenderer {
    private static final int MAX_EDGE = 1800;

    private SourcePageRenderer() {}

    public record Page(int number, int count, BufferedImage image) {}

    public static Page render(Path source, int page) throws IOException {
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return pdf(source, page);
        if (name.endsWith(".pptx")) return pptx(source, page);
        throw new IOException("Only PDF and PPTX source previews are supported.");
    }

    private static Page pdf(Path source, int page) throws IOException {
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            int count = document.getNumberOfPages();
            checkPage(page, count);
            var bounds = document.getPage(page - 1).getCropBox();
            float scale = scale(bounds.getWidth(), bounds.getHeight());
            PDFRenderer renderer = new PDFRenderer(document);
            renderer.setSubsamplingAllowed(true);
            return new Page(page, count, renderer.renderImage(page - 1, scale, ImageType.RGB));
        }
    }

    private static Page pptx(Path source, int page) throws IOException {
        // READ avoids rewriting a presentation or its metadata when the package is closed.
        try (OPCPackage file = OPCPackage.open(source.toFile(), PackageAccess.READ);
             XMLSlideShow document = new XMLSlideShow(file)) {
            int count = document.getSlides().size();
            checkPage(page, count);
            var bounds = document.getPageSize();
            float scale = scale(bounds.getWidth(), bounds.getHeight());
            int width = Math.max(1, (int) Math.ceil(bounds.getWidth() * scale));
            int height = Math.max(1, (int) Math.ceil(bounds.getHeight() * scale));
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graphics.scale(scale, scale);
                document.getSlides().get(page - 1).draw(graphics);
            } finally { graphics.dispose(); }
            return new Page(page, count, image);
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException invalid) {
            throw new IOException("The presentation format is invalid.", invalid);
        }
    }

    private static float scale(double width, double height) throws IOException {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0)
            throw new IOException("The document page size is invalid.");
        // Retain just the current page; cap its raster size even for oversized source pages.
        return (float) Math.min(2, MAX_EDGE / Math.max(width, height));
    }

    private static void checkPage(int page, int count) throws PageOutOfRangeException {
        if (page < 1 || page > count) throw new PageOutOfRangeException(page, count);
    }

    public static final class PageOutOfRangeException extends IOException {
        private final int count;
        public PageOutOfRangeException(int page, int count) {
            super(count == 0 ? "This document has no pages or slides."
                    : "Page / slide " + page + " is outside this document (1–" + count + ").");
            this.count = count;
        }
        public int count() { return count; }
    }
}
