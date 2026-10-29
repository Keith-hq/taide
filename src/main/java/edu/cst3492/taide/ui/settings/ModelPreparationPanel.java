package edu.cst3492.taide.ui.settings;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import edu.cst3492.taide.model.ModelSetupService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** Shared model setup, displayed inside project settings without owning the download lifecycle. */
public final class ModelPreparationPanel extends JPanel implements AutoCloseable {
    private final Project project;
    private final ModelSetupService service;
    private final JTextArea status = note("");
    private final JTextArea location = note("");
    private final JProgressBar progress = new JProgressBar(0, 1_000);
    private final JButton download = new JButton("Download model");
    private final JButton local = new JButton("Use existing folder…");
    private final JButton managed = new JButton("Use default folder");
    private final JButton cancel = new JButton("Cancel download");
    private AutoCloseable subscription;
    private Runnable statusChanged = () -> {};
    private boolean checkingDirectory;
    private boolean closed;

    public ModelPreparationPanel(Project project) {
        super(new GridBagLayout());
        this.project = project;
        service = ModelSetupService.getInstance();
        setOpaque(false);
        addRow(note("Retrieves reference materials; your configured API generates the answers. The official MiniLM model is about 90 MB and uses the Apache 2.0 license."), 0, 0);
        addRow(note("Downloads from huggingface.co with automatic retry and fallback to hf-mirror.com. Both sources must pass the same SHA-256 check. The model is stored in the IDE configuration folder and shared across projects."), 1, 4);
        status.getAccessibleContext().setAccessibleName("Model setup status");
        addRow(status, 2, 10);
        location.setFocusable(true);
        location.getAccessibleContext().setAccessibleName("Model storage folder");
        addRow(location, 3, 4);
        progress.setStringPainted(true);
        progress.setVisible(false);
        addRow(progress, 4, 8);

        // Two short action rows stay usable in the minimum-width Settings window.
        JPanel primaryActions = actions(download, local);
        addRow(primaryActions, 5, 10);
        addRow(actions(managed, cancel), 6, 8);
        download.addActionListener(event -> service.download(project));
        cancel.addActionListener(event -> service.cancel());
        local.addActionListener(event -> chooseExistingDirectory());
        managed.addActionListener(event -> service.useManagedDirectory());
        subscription = service.listen(this::render);
        render();
        service.refresh();
    }

    void setStatusChanged(Runnable callback) {
        statusChanged = callback == null ? () -> {} : callback;
    }

    private void chooseExistingDirectory() {
        VirtualFile selected = FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        .withTitle("Choose an Existing Retrieval Model")
                        .withDescription("Choose a model folder containing both model.onnx and vocab.txt."),
                project, null);
        if (selected == null || closed) return;
        Path selectedDirectory = selected.toNioPath();
        checkingDirectory = true;
        render();
        new Task.Backgroundable(project, "Validating TAIDE local model", false) {
            @Override public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("Validating model and vocabulary…");
                try {
                    service.useDirectory(selectedDirectory);
                } catch (IOException error) {
                    throw new IllegalStateException("Local model validation failed", error);
                }
            }

            @Override public void onSuccess() {
                if (closed) return;
                checkingDirectory = false;
                render();
            }

            @Override public void onThrowable(@NotNull Throwable error) {
                if (closed) return;
                checkingDirectory = false;
                render();
                status.setText("Folder validation failed. Choose a complete folder with matching model.onnx and vocab.txt files, and check that both are readable and undamaged.");
            }
        }.queue();
    }

    private void render() {
        if (closed) return;
        ModelSetupService.Status value = service.status();
        boolean busy = value.preparing() || checkingDirectory;
        boolean checking = !value.checked() && !value.preparing();
        String detail = value.detail();
        status.setText(checkingDirectory ? "Validating the selected model and vocabulary…"
                : detail == null || detail.isBlank()
                ? value.ready() ? "Model ready. Configure your API and materials next."
                : checking ? "Checking local model…" : "Model not ready. Download it or choose an existing folder."
                : detail);
        location.setText("Model folder: " + value.directory());
        download.setEnabled(!busy && !checking && !value.ready());
        local.setEnabled(!busy && !checking);
        managed.setEnabled(!busy && !checking);
        cancel.setVisible(value.preparing());
        cancel.setEnabled(value.preparing());
        progress.setVisible(busy);
        progress.setIndeterminate(checkingDirectory || value.total() <= 0);
        if (value.total() > 0 && !checkingDirectory) {
            double fraction = Math.max(0, Math.min(1, (double) value.downloaded() / value.total()));
            progress.setValue((int) (fraction * 1_000));
            progress.setString(String.format(Locale.ROOT, "%.1f / %.1f MB",
                    value.downloaded() / 1_000_000.0, value.total() / 1_000_000.0));
        } else {
            progress.setString(checkingDirectory ? "Validating…" : "Preparing…");
        }
        revalidate();
        repaint();
        statusChanged.run();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        statusChanged = () -> {};
        AutoCloseable listener = subscription;
        subscription = null;
        if (listener != null) {
            try { listener.close(); }
            catch (Exception ignored) { /* Removing a UI listener must not affect an application download. */ }
        }
    }

    private void addRow(JComponent component, int row, int top) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = JBUI.insetsTop(top);
        add(component, constraints);
    }

    private static JPanel actions(JButton first, JButton second) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panel.setOpaque(false);
        panel.add(first);
        panel.add(Box.createHorizontalStrut(JBUI.scale(8)));
        panel.add(second);
        return panel;
    }

    private static JTextArea note(String text) {
        JTextArea label = new JTextArea(text, 0, 36);
        label.setLineWrap(true);
        label.setWrapStyleWord(true);
        label.setEditable(false);
        label.setFocusable(false);
        label.setOpaque(false);
        label.setBorder(JBUI.Borders.empty());
        label.setFont(UIManager.getFont("Label.font"));
        label.setForeground(JBColor.namedColor("Label.infoForeground", JBColor.GRAY));
        return label;
    }
}
