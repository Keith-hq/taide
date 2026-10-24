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
    private final JButton download = new JButton("下载并配置");
    private final JButton local = new JButton("使用已有目录…");
    private final JButton managed = new JButton("使用默认目录");
    private final JButton cancel = new JButton("取消下载");
    private AutoCloseable subscription;
    private Runnable statusChanged = () -> {};
    private boolean checkingDirectory;
    private boolean closed;

    public ModelPreparationPanel(Project project) {
        super(new GridBagLayout());
        this.project = project;
        service = ModelSetupService.getInstance();
        setOpaque(false);
        addRow(note("用于检索课件；回答由你配置的 API 生成。官方 MiniLM 模型约 90 MB，采用 Apache 2.0 许可。"), 0, 0);
        addRow(note("仅在点击下载后联网。下载的模型保存在 IDE 配置目录，所有项目共享，下载一次即可。"), 1, 4);
        status.getAccessibleContext().setAccessibleName("模型准备状态");
        addRow(status, 2, 10);
        location.setFocusable(true);
        location.getAccessibleContext().setAccessibleName("模型保存目录");
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
                        .withTitle("选择已有检索模型")
                        .withDescription("请选择同时包含 model.onnx 和 vocab.txt 的模型目录。"),
                project, null);
        if (selected == null || closed) return;
        Path selectedDirectory = selected.toNioPath();
        checkingDirectory = true;
        render();
        new Task.Backgroundable(project, "校验 TAIDE 本地模型", false) {
            @Override public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("正在校验模型与词表…");
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
                status.setText("所选目录未通过校验。请选择包含匹配 model.onnx 与 vocab.txt 的完整目录，并检查文件是否损坏或可读取。");
            }
        }.queue();
    }

    private void render() {
        if (closed) return;
        ModelSetupService.Status value = service.status();
        boolean busy = value.preparing() || checkingDirectory;
        boolean checking = !value.checked() && !value.preparing();
        String detail = value.detail();
        status.setText(checkingDirectory ? "正在校验所选目录中的模型与词表…"
                : detail == null || detail.isBlank()
                ? value.ready() ? "模型已就绪，可继续配置 API 与课件。"
                : checking ? "正在检查本地模型…" : "尚未准备模型。请下载或选择已有目录。"
                : detail);
        location.setText("模型目录：" + value.directory());
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
            progress.setString(checkingDirectory ? "校验中…" : "准备中…");
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
