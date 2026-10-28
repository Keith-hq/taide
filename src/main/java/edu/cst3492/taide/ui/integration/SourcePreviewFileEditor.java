package edu.cst3492.taide.ui.integration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** Native, read-only source viewer. Parsing and rendering never run on the event thread. */
final class SourcePreviewFileEditor extends UserDataHolderBase implements FileEditor {
    @FunctionalInterface interface PageLoader { SourcePageRenderer.Page load(Path source, int page) throws IOException; }

    private final Project project;
    private final SourcePreviewFile file;
    private final PageLoader loader;
    private final JPanel root = new JPanel(new BorderLayout(0, 8));
    private final JTextField pageInput = new JTextField(4);
    private final JLabel total = new JLabel("/ …");
    private final JButton previous = new JButton("Previous");
    private final JButton next = new JButton("Next");
    private final JButton retry = new JButton("Retry");
    private final JTextArea status = new JTextArea(2, 30);
    private final JProgressBar progress = new JProgressBar();
    private final PageCanvas canvas = new PageCanvas();
    private final JBScrollPane scroll = new JBScrollPane(canvas);
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private final ExecutorService renderer = AppExecutorUtil.createBoundedApplicationPoolExecutor("TAIDE source preview", 1);
    private final String unit;
    private Future<?> task;
    private long generation;
    private int requested;
    private int loaded;
    private int count;
    private volatile boolean disposed;

    SourcePreviewFileEditor(Project project, SourcePreviewFile file) {
        this(project, file, SourcePageRenderer::render);
    }

    SourcePreviewFileEditor(Project project, SourcePreviewFile file, PageLoader loader) {
        this.project = project;
        this.file = file;
        this.loader = loader;
        unit = file.source.getName().toLowerCase(Locale.ROOT).endsWith(".pptx") ? "Slide" : "Page";
        root.setBorder(JBUI.Borders.empty(12));
        root.setMinimumSize(JBUI.size(350, 260));
        root.setToolTipText(file.source.getPath());
        root.add(toolbar(), BorderLayout.NORTH);
        scroll.getVerticalScrollBar().setUnitIncrement(JBUI.scale(24));
        root.add(scroll, BorderLayout.CENTER);
        status.setEditable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setOpaque(false);
        status.setFont(UIUtil.getLabelFont());
        status.getAccessibleContext().setAccessibleName("Source preview status");
        root.add(status, BorderLayout.SOUTH);
        showPage(file.initialPage);
    }

    private JComponent toolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(0, 8));
        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0));
        JLabel label = new JLabel(unit);
        label.setLabelFor(pageInput);
        navigation.add(label);
        pageInput.getAccessibleContext().setAccessibleName(unit + " number");
        pageInput.setToolTipText("Enter a number and press Enter to jump");
        pageInput.addActionListener(event -> jump());
        navigation.add(pageInput);
        navigation.add(total);
        JButton go = new JButton("Go");
        go.addActionListener(event -> jump());
        navigation.add(go);
        previous.addActionListener(event -> showPage(requested - 1));
        next.addActionListener(event -> showPage(requested + 1));
        navigation.add(previous);
        navigation.add(next);
        toolbar.add(navigation, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0));
        JComboBox<String> zoom = new JComboBox<>(new String[]{"Fit width", "100%", "150%", "200%"});
        zoom.getAccessibleContext().setAccessibleName("Preview zoom");
        zoom.addActionListener(event -> {
            canvas.zoom = switch (zoom.getSelectedIndex()) { case 1 -> 1; case 2 -> 1.5; case 3 -> 2; default -> 0; };
            canvas.revalidate(); canvas.repaint();
        });
        actions.add(zoom);
        JButton original = new JButton("Open original");
        original.setToolTipText("Open the source file using the IDE's normal file association");
        original.addActionListener(event -> {
            try { FileEditorManager.getInstance(project).openFile(file.source, true); }
            catch (RuntimeException failure) { status.setText("The original file could not be opened using the IDE's file association."); }
        });
        actions.add(original);
        retry.addActionListener(event -> showPage(requested));
        retry.setVisible(false);
        actions.add(retry);
        toolbar.add(actions, BorderLayout.CENTER);
        progress.setIndeterminate(true);
        progress.setPreferredSize(new Dimension(0, JBUI.scale(4)));
        toolbar.add(progress, BorderLayout.SOUTH);
        return toolbar;
    }

    private void jump() {
        try { showPage(Integer.parseInt(pageInput.getText().trim())); }
        catch (NumberFormatException invalid) { status.setText("Enter a whole page or slide number."); }
    }

    void showPage(int page) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        if (disposed || project.isDisposed()) return;
        if (page < 1 || (count > 0 && page > count)) {
            status.setText(count > 0 ? "Enter a number from 1 to " + count + "." : "Page and slide numbers start at 1.");
            return;
        }
        long request = ++generation;
        if (task != null) task.cancel(true);
        requested = page;
        loaded = 0;
        pageInput.setText(Integer.toString(page));
        canvas.image = null;
        canvas.revalidate(); canvas.repaint();
        retry.setVisible(false);
        progress.setVisible(true);
        status.setText("Loading " + unit.toLowerCase(Locale.ROOT) + " " + page + "…");
        updateNavigation();
        // Capture modality on the caller's EDT, including a modal automation review dialog.
        ModalityState modality = ModalityState.defaultModalityState();
        task = renderer.submit(() -> {
            try {
                SourcePageRenderer.Page result = loader.load(file.source.toNioPath(), page);
                if (Thread.currentThread().isInterrupted()) return;
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!active(request)) return;
                    count = result.count();
                    loaded = result.number();
                    total.setText("/ " + count);
                    canvas.image = result.image();
                    canvas.revalidate(); canvas.repaint();
                    scroll.getViewport().setViewPosition(new Point());
                    progress.setVisible(false);
                    status.setText(unit + " " + loaded + " of " + count + " · " + file.source.getName());
                    updateNavigation();
                }, modality);
            } catch (IOException | RuntimeException failure) {
                if (Thread.currentThread().isInterrupted()) return;
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!active(request)) return;
                    if (failure instanceof SourcePageRenderer.PageOutOfRangeException invalid) {
                        count = invalid.count();
                        total.setText("/ " + count);
                        status.setText(invalid.getMessage());
                    } else status.setText("Could not render this page. Retry, or use Open original to view the source file.");
                    progress.setVisible(false);
                    retry.setVisible(true);
                    updateNavigation();
                }, modality);
            }
        });
    }

    private boolean active(long request) { return !disposed && !project.isDisposed() && generation == request; }
    private void updateNavigation() {
        previous.setEnabled(count > 0 && requested > 1);
        next.setEnabled(count > 0 && requested < count);
    }

    int requestedPage() { return requested; }
    int loadedPage() { return loaded; }
    int pageCount() { return count; }
    @Override public @NotNull JComponent getComponent() { return root; }
    @Override public JComponent getPreferredFocusedComponent() { return pageInput; }
    @Override public @NotNull String getName() { return "Source preview"; }
    @Override public VirtualFile getFile() { return file; }
    @Override public boolean isModified() { return false; }
    @Override public boolean isValid() { return !disposed && file.source.isValid(); }
    @Override public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) { changes.addPropertyChangeListener(listener); }
    @Override public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) { changes.removePropertyChangeListener(listener); }
    @Override public @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) { return new PageState(requested); }
    @Override public void setState(@NotNull FileEditorState state) {
        if (state instanceof PageState page && page.page != requested) showPage(page.page);
    }
    private record PageState(int page) implements FileEditorState {
        @Override public boolean canBeMergedWith(@NotNull FileEditorState state, @NotNull FileEditorStateLevel level) {
            return state instanceof PageState other && other.page == page;
        }
    }
    @Override public void dispose() {
        disposed = true;
        generation++;
        if (task != null) task.cancel(true);
        renderer.shutdownNow();
        canvas.image = null;
    }

    private final class PageCanvas extends JPanel implements Scrollable {
        private BufferedImage image;
        private double zoom;
        private double scale() {
            return zoom > 0 ? zoom : image == null ? 1
                    : Math.min(1, Math.max(1, scroll.getViewport().getWidth() - JBUI.scale(24)) / (double) image.getWidth());
        }
        @Override public Dimension getPreferredSize() {
            return image == null ? new Dimension(1, 1) : new Dimension(
                    (int) Math.ceil(image.getWidth() * scale()) + JBUI.scale(24),
                    (int) Math.ceil(image.getHeight() * scale()) + JBUI.scale(24));
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (image == null) return;
            int width = (int) Math.ceil(image.getWidth() * scale());
            int height = (int) Math.ceil(image.getHeight() * scale());
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image, Math.max(JBUI.scale(12), (getWidth() - width) / 2), JBUI.scale(12), width, height, null);
            } finally { g.dispose(); }
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return JBUI.size(800, 600); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return JBUI.scale(24); }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(JBUI.scale(24), (orientation == SwingConstants.VERTICAL ? visible.height : visible.width) - JBUI.scale(24));
        }
        @Override public boolean getScrollableTracksViewportWidth() { return zoom == 0; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
