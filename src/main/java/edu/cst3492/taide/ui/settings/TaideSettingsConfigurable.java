package edu.cst3492.taide.ui.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import edu.cst3492.taide.model.ModelSetupService;
import edu.cst3492.taide.ui.integration.KnowledgeBaseController;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

/** Project settings. PasswordSafe and rebuild operations always run outside the EDT. */
public final class TaideSettingsConfigurable implements SearchableConfigurable {
    public static final String ID = "edu.cst3492.taide.settings";
    private final Project project;
    private final TaideUiSettings settings;
    private volatile JPanel root;
    private JPanel form;
    private ModelPreparationPanel modelPreparation;
    private JComboBox<String> provider;
    private JBTextField baseUrl;
    private JBTextField model;
    private JPasswordField apiKey;
    private JBCheckBox clearKey;
    private JBCheckBox demoMode;
    private TextFieldWithBrowseButton directory;
    private JButton rebuild;
    private JButton cancel;
    private JProgressBar progress;
    private JTextArea keyStatus;
    private JTextArea indexStatus;
    private volatile RebuildOperation rebuildOperation;
    private SettingsSnapshot baseline;
    private boolean resetting;
    private boolean rebuilding;
    private int generation;
    private int row;

    public TaideSettingsConfigurable(Project project) {
        this.project = project;
        settings = TaideUiSettings.getInstance(project);
    }

    @Override public @NotNull String getId() { return ID; }
    @Override public String getDisplayName() { return "TAIDE"; }
    @Override public @Nullable JComponent getPreferredFocusedComponent() { return provider; }

    @Override public @Nullable JComponent createComponent() {
        form = new JPanel(new GridBagLayout());
        form.setBorder(JBUI.Borders.empty(20, 20, 24, 20));
        row = 0;
        addWide(title("TAIDE 首次使用"), 0);
        addWide(note("1. 准备检索模型 → 2. 配置 API 与课件目录并 Apply → 3. 重建知识库，关闭演示模式后发送新问题。"), 8);
        addWide(note("Project: " + project.getName() + ". Demo mode uses clearly marked sample answers."), 8);
        addWide(title("本地检索模型"), 24);
        modelPreparation = new ModelPreparationPanel(project);
        modelPreparation.setStatusChanged(this::updateRebuildAvailability);
        addWide(modelPreparation, 8);
        addWide(title("TAIDE connection"), 28);
        demoMode = new JBCheckBox("Use demo service (no network or API key required)");
        addWide(demoMode, 16);
        provider = new JComboBox<>(new String[]{SettingsSnapshot.DEEPSEEK, SettingsSnapshot.AGI});
        baseUrl = new JBTextField();
        model = new JBTextField();
        apiKey = new JPasswordField();
        apiKey.getAccessibleContext().setAccessibleName("API key");
        apiKey.setToolTipText("Leave blank to keep the stored key. Enter a new key to replace it.");
        clearKey = new JBCheckBox("Remove the stored key for this provider and endpoint");
        keyStatus = note("Checking secure storage…");
        addField("Provider", provider);
        addField("API base URL", baseUrl);
        addWide(note("DeepSeek: https://api.deepseek.com · AGI: use the base URL supplied by your institution."), 0);
        addField("Model ID", model);
        addField("API key", apiKey);
        addWide(keyStatus, 0);
        addWide(clearKey, 4);
        addWide(note("Keys are saved in IDE PasswordSafe for this project, provider and endpoint. A different endpoint needs its own key."), 4);

        addWide(title("Course knowledge base"), 28);
        addWide(note("Choose the local folder containing your PDF and PPTX course materials."), 8);
        directory = new TextFieldWithBrowseButton();
        directory.addBrowseFolderListener("Course Materials", "Choose a folder containing PDF and PPTX files.",
                project, FileChooserDescriptorFactory.createSingleFolderDescriptor());
        addField("Course directory", directory);
        rebuild = new JButton("Rebuild Knowledge Base");
        cancel = new JButton("Cancel rebuild");
        cancel.setVisible(false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.add(rebuild);
        actions.add(Box.createHorizontalStrut(JBUI.scale(8)));
        actions.add(cancel);
        addWide(actions, 12);
        progress = new JProgressBar(0, 100);
        progress.setStringPainted(true);
        progress.setVisible(false);
        addWide(progress, 8);
        indexStatus = note("");
        addWide(indexStatus, 8);
        addWide(note("Apply the course directory before rebuilding. Rebuild immediately replaces the index; canceling Settings cannot undo a completed rebuild."), 8);
        GridBagConstraints filler = constraints(0, row++);
        filler.gridwidth = 2;
        filler.weighty = 1;
        form.add(Box.createGlue(), filler);
        root = new JPanel(new BorderLayout());
        JBScrollPane scroll = new JBScrollPane(form);
        scroll.setBorder(JBUI.Borders.empty());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        root.add(scroll, BorderLayout.CENTER);
        root.setPreferredSize(JBUI.size(640, 620));
        form.setMinimumSize(new Dimension(JBUI.scale(360), 0));

        provider.addActionListener(event -> {
            if (!resetting) {
                apiKey.setText("");
                apiKey.setEnabled(true);
                clearKey.setSelected(false);
                refreshKeyStatus();
            }
        });
        clearKey.addActionListener(event -> {
            if (clearKey.isSelected()) apiKey.setText("");
            apiKey.setEnabled(!clearKey.isSelected());
        });
        baseUrl.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override protected void textChanged(@NotNull DocumentEvent event) {
                if (resetting) return;
                generation++;
                clearKey.setSelected(false);
                apiKey.setEnabled(true);
                keyStatus.setText("Endpoint changed. Its stored key will be checked when you leave this field.");
            }
        });
        baseUrl.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent event) { refreshKeyStatus(); }
        });
        directory.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
            @Override protected void textChanged(@NotNull DocumentEvent event) { updateRebuildAvailability(); }
        });
        rebuild.addActionListener(event -> rebuildKnowledgeBase());
        cancel.addActionListener(event -> {
            RebuildOperation operation = rebuildOperation;
            if (operation != null) operation.cancel();
            cancel.setEnabled(false);
            indexStatus.setText("Canceling rebuild…");
        });
        reset();
        return root;
    }

    @Override public boolean isModified() {
        if (root == null || baseline == null) return false;
        char[] pending = apiKey.getPassword();
        try { return !current().equals(baseline) || pending.length > 0 || clearKey.isSelected(); }
        finally { Arrays.fill(pending, '\0'); }
    }

    @Override public void apply() throws ConfigurationException {
        if (root == null) return;
        SettingsSnapshot next = current();
        List<String> errors = SettingsValidation.validate(next, baseline);
        if (!errors.isEmpty()) throw new ConfigurationException(String.join("\n", errors));
        char[] pending = apiKey.getPassword();
        boolean remove = clearKey.isSelected();
        boolean directoryChanged = !next.courseDirectory().equals(baseline.courseDirectory());
        AtomicReference<String> failure = new AtomicReference<>();
        try {
            if (pending.length > 0 && isBlank(pending)) {
                throw new ConfigurationException("Enter a non-empty API key or leave the field blank to keep the saved key.");
            }
            if ((pending.length > 0 || remove) && SettingsValidation.endpointError(next.baseUrl()) != null) {
                throw new ConfigurationException("Enter a valid API base URL before saving or removing a key for that endpoint.");
            }
            if (pending.length > 0 || remove || directoryChanged) {
                ProgressManager.getInstance().run(new Task.Modal(project, "Saving TAIDE settings", false) {
                    @Override public void run(@NotNull ProgressIndicator indicator) {
                        indicator.setIndeterminate(true);
                        try {
                            if (directoryChanged && !next.courseDirectory().isBlank()) {
                                Path folder = Path.of(next.courseDirectory());
                                if (!Files.isDirectory(folder) || !Files.isReadable(folder)) {
                                    failure.set("The course directory must be an existing, readable folder.");
                                    return;
                                }
                            }
                            if (remove) settings.clearApiKey(next);
                            else if (pending.length > 0) settings.saveApiKey(next, pending);
                        } catch (RuntimeException ex) {
                            failure.set("The IDE could not save the API key in PasswordSafe. Check PasswordSafe settings and try again.");
                        }
                    }
                });
            }
            if (failure.get() != null) throw new ConfigurationException(failure.get());
            settings.update(next);
            reset();
        } finally { Arrays.fill(pending, '\0'); }
    }

    @Override public void reset() {
        if (root == null) return;
        baseline = settings.snapshot();
        resetting = true;
        try {
            provider.setSelectedItem(baseline.provider());
            baseUrl.setText(baseline.baseUrl());
            model.setText(baseline.model());
            directory.setText(baseline.courseDirectory());
            demoMode.setSelected(baseline.demoMode());
            apiKey.setText("");
            apiKey.setEnabled(true);
            clearKey.setSelected(false);
        } finally { resetting = false; }
        refreshKeyStatus();
        updateRebuildAvailability();
    }

    private SettingsSnapshot current() {
        return new SettingsSnapshot((String) provider.getSelectedItem(), baseUrl.getText(), model.getText(),
                directory.getText(), demoMode.isSelected());
    }

    private void refreshKeyStatus() {
        int request = ++generation;
        SettingsSnapshot selected = current();
        if (SettingsValidation.endpointError(selected.baseUrl()) != null) {
            keyStatus.setText("Enter a valid API base URL to check the key stored for this endpoint.");
            return;
        }
        keyStatus.setText("Checking secure storage…");
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String message;
            try {
                String stored = settings.readApiKey(selected);
                message = stored == null || stored.isBlank() ? "No API key stored. Enter a key to save it on Apply."
                        : "API key stored for this endpoint. Leave the field blank to keep it, or enter a replacement.";
            } catch (RuntimeException ex) {
                message = "Secure storage is unavailable. Check Settings → Appearance & Behavior → System Settings → Passwords.";
            }
            String status = message;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (root != null && !project.isDisposed() && generation == request) keyStatus.setText(status);
            }, ModalityState.any());
        });
    }

    private @Nullable KnowledgeBaseController controller() {
        return project.getService(KnowledgeBaseController.class);
    }

    private void updateRebuildAvailability() {
        if (rebuild == null || indexStatus == null || rebuilding) return;
        boolean connected = controller() != null;
        boolean folderSelected = !directory.getText().trim().isEmpty();
        boolean directorySaved = baseline != null && directory.getText().trim().equals(baseline.courseDirectory());
        ModelSetupService.Status modelStatus = ModelSetupService.getInstance().status();
        boolean modelReady = modelStatus.ready() && !modelStatus.preparing();
        rebuild.setEnabled(connected && modelReady && folderSelected && directorySaved);
        indexStatus.setText(!connected ? "A indexing service is not connected. Rebuild becomes available after integration."
                : !modelReady ? "请先在上方准备本地检索模型，再重建知识库。"
                : !folderSelected ? "Choose a course directory before rebuilding."
                : !directorySaved ? "Apply the selected course directory before rebuilding."
                : "Ready to rebuild. The existing index is kept if the operation is canceled.");
    }

    private void rebuildKnowledgeBase() {
        KnowledgeBaseController backend = controller();
        if (backend == null || rebuilding) return;
        ModelSetupService.Status modelStatus = ModelSetupService.getInstance().status();
        if (!modelStatus.ready() || modelStatus.preparing()) {
            indexStatus.setText("请先在上方准备本地检索模型，再重建知识库。");
            return;
        }
        if (baseline == null || !directory.getText().trim().equals(baseline.courseDirectory())) {
            indexStatus.setText("Apply the selected course directory before rebuilding.");
            return;
        }
        JPanel activePanel = root;
        final Path folder;
        try { folder = Path.of(directory.getText().trim()); }
        catch (InvalidPathException ex) { indexStatus.setText("Choose a valid local course directory."); return; }
        rebuilding = true;
        RebuildOperation operation = new RebuildOperation();
        rebuildOperation = operation;
        rebuild.setEnabled(false);
        directory.setEnabled(false);
        cancel.setEnabled(true);
        cancel.setVisible(true);
        progress.setVisible(true);
        progress.setIndeterminate(true);
        indexStatus.setText("Preparing course materials…");
        new Task.Backgroundable(project, "Rebuilding TAIDE knowledge base", true) {
            private KnowledgeBaseController.RebuildResult result;
            private boolean invalidFolder;

            @Override public void run(@NotNull ProgressIndicator indicator) {
                operation.indicator = indicator;
                if (root != activePanel || project.isDisposed()) operation.cancel();
                operation.checkCanceled();
                if (!Files.isDirectory(folder) || !Files.isReadable(folder)) {
                    invalidFolder = true;
                    throw new IllegalArgumentException("Choose an existing, readable course directory.");
                }
                try {
                    result = backend.rebuild(folder, (fraction, detail) -> {
                        // fraction=1 is a completion notification after the atomic publish.
                        if (fraction < 1) operation.checkCanceled();
                        indicator.setIndeterminate(fraction < 0);
                        if (fraction >= 0) indicator.setFraction(Math.max(0, Math.min(1, fraction)));
                        indicator.setText(detail);
                        ApplicationManager.getApplication().invokeLater(() -> {
                            if (root != activePanel || rebuildOperation != operation || !rebuilding || project.isDisposed()) return;
                            progress.setIndeterminate(fraction < 0);
                            if (fraction >= 0) progress.setValue((int) (Math.max(0, Math.min(1, fraction)) * 100));
                            indexStatus.setText(detail == null || detail.isBlank() ? "Building course index…" : detail);
                        }, ModalityState.any());
                    }, operation::checkCanceled);
                } catch (RuntimeException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new IllegalStateException("Course indexing failed", ex);
                }
                // A successful return is the atomic publish point. A late Cancel must not
                // claim that the previous index survived after the new one was committed.
                if (result == null) throw new IllegalStateException("Indexing service returned no result");
            }

            @Override public void onSuccess() {
                if (root != activePanel || rebuildOperation != operation || project.isDisposed()) return;
                finishRebuild(result.documentCount() + " documents · " + result.chunkCount() + " chunks indexed.");
            }
            @Override public void onCancel() {
                if (root == activePanel && rebuildOperation == operation) {
                    finishRebuild(result != null
                            ? result.documentCount() + " documents · " + result.chunkCount() + " chunks indexed before cancellation."
                            : "Rebuild canceled. The previous index was kept.");
                }
            }
            @Override public void onThrowable(@NotNull Throwable error) {
                if (root != activePanel || rebuildOperation != operation) return;
                finishRebuild(invalidFolder ? "Choose an existing, readable course directory, then try again."
                        : rebuildFailure(error));
            }
        }.queue();
    }

    private void finishRebuild(String message) {
        rebuilding = false;
        rebuildOperation = null;
        if (root == null || project.isDisposed()) return;
        directory.setEnabled(true);
        cancel.setVisible(false);
        progress.setVisible(false);
        updateRebuildAvailability();
        indexStatus.setText(message);
    }

    @Override public void disposeUIResources() {
        generation++;
        RebuildOperation operation = rebuildOperation;
        if (operation != null) operation.cancel();
        rebuildOperation = null;
        if (apiKey != null) apiKey.setText("");
        if (directory != null) directory.dispose();
        if (modelPreparation != null) modelPreparation.close();
        modelPreparation = null;
        root = null;
        form = null;
        baseline = null;
        rebuilding = false;
    }

    /** Owns cancellation even before Backgroundable has supplied its indicator. */
    private static final class RebuildOperation {
        private final AtomicBoolean canceled = new AtomicBoolean();
        private volatile ProgressIndicator indicator;

        private void cancel() {
            canceled.set(true);
            ProgressIndicator current = indicator;
            if (current != null) current.cancel();
        }

        private void checkCanceled() {
            if (canceled.get()) throw new ProcessCanceledException();
            ProgressIndicator current = indicator;
            if (current != null) current.checkCanceled();
        }
    }

    private static boolean isBlank(char[] value) {
        for (char character : value) if (!Character.isWhitespace(character)) return false;
        return true;
    }

    private static String rebuildFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message == null) continue;
            if (message.startsWith("Model files not found")) {
                return "本地检索模型未就绪。请在本页上方点击“下载并配置”，或选择包含 model.onnx 与 vocab.txt 的已有目录，再重建。";
            }
            if (message.startsWith("No PDF/PPTX materials found")) {
                return "No PDF or PPTX text was found. Add readable course materials directly to the selected folder, then rebuild.";
            }
            if (message.contains("does not support knowledge base rebuilding")) {
                return "The active TAIDE service does not support rebuilding. Check the plugin service integration.";
            }
        }
        return "Rebuild failed. Check that the course files are readable and the embedding model is configured, then try again.";
    }

    private void addField(String labelText, JComponent component) {
        JBLabel label = new JBLabel(labelText);
        label.setLabelFor(component);
        GridBagConstraints labelConstraints = constraints(0, row);
        labelConstraints.insets = JBUI.insets(10, 0, 0, 16);
        form.add(label, labelConstraints);
        GridBagConstraints inputConstraints = constraints(1, row++);
        inputConstraints.weightx = 1;
        inputConstraints.fill = GridBagConstraints.HORIZONTAL;
        inputConstraints.insets = JBUI.insetsTop(10);
        form.add(component, inputConstraints);
    }

    private void addWide(JComponent component, int top) {
        GridBagConstraints constraints = constraints(0, row++);
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = JBUI.insetsTop(top);
        form.add(component, constraints);
    }

    private static GridBagConstraints constraints(int x, int y) {
        GridBagConstraints result = new GridBagConstraints();
        result.gridx = x;
        result.gridy = y;
        result.anchor = GridBagConstraints.NORTHWEST;
        return result;
    }

    private static JBLabel title(String text) {
        JBLabel label = new JBLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() + 1));
        return label;
    }

    private static JTextArea note(String text) {
        JTextArea label = new JTextArea(text, 0, 45);
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
