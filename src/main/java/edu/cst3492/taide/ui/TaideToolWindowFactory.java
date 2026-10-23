package edu.cst3492.taide.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.wm.*;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.*;
import com.intellij.util.ui.UIUtil;
import edu.cst3492.taide.service.*;
import edu.cst3492.taide.ui.chat.*;
import edu.cst3492.taide.ui.settings.*;
import org.jetbrains.annotations.NotNull;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.nio.file.*;

/** IntelliJ adapter. All visible chat content is the same native Swing component used by visual QA. */
public final class TaideToolWindowFactory implements ToolWindowFactory, DumbAware {
    public static final String ID = "TAIDE";
    @Override public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow window) {
        ChatPanel panel = new ChatPanel(new IdeHost(project));
        Content content = ContentFactory.getInstance().createContent(panel, "课程问答", false);
        content.setDisposer(panel::close);
        window.getContentManager().addContent(content);
    }
    public static void openAndAsk(Project project, CodeContext context) {
        if (project.isDisposed()) return;
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ID);
        if (window == null) {
            Messages.showInfoMessage(project, "请通过 View → Tool Windows → TAIDE 打开课程助教。", "TAIDE"); return;
        }
        window.activate(() -> {
            if (project.isDisposed()) return;
            Content content = window.getContentManager().getSelectedContent();
            if (content != null && content.getComponent() instanceof ChatPanel panel) panel.askAbout(context);
        });
    }
    private record IdeHost(Project project) implements ChatPanel.Host {
        public boolean demoMode() { return TaideUiSettings.getInstance(project).snapshot().demoMode(); }
        public TaideService service() {
            if (demoMode()) return new DemoTaideService();
            TaideService service = project.getService(TaideService.class);
            if (service != null) return service;
            return new TaideService() {
                public TaAnswer ask(String q, CodeContext c) throws TaideException {
                    throw new TaideException("A 的问答服务尚未接入，请启用演示模式或完成服务注册");
                }
                public void askStreaming(String q, CodeContext c, java.util.function.Consumer<String> t,
                        java.util.function.Consumer<TaAnswer> done, java.util.function.Consumer<Throwable> error) {
                    error.accept(new TaideException("A 的问答服务尚未接入，请启用演示模式或完成服务注册"));
                }
            };
        }
        public void settings() {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, TaideSettingsConfigurable.class);
            ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ID);
            if (window != null) {
                Content content = window.getContentManager().getSelectedContent();
                if (content != null && content.getComponent() instanceof ChatPanel panel) panel.refreshMode();
            }
        }
        public void copy(String value) { CopyPasteManager.getInstance().setContents(new StringSelection(value)); }
        public Font font() { return UIUtil.getLabelFont().deriveFont(14f); }
        public javax.swing.JScrollPane scrollPane(Component view) { return new JBScrollPane(view); }
        public Color color(String role, Color light) {
            int dark = switch (role) {
                case "ground" -> 0x171717;
                case "surface" -> 0x202020;
                case "raised" -> 0x2B2B2B;
                case "text" -> 0xEEEEEE;
                case "muted" -> 0xB4B4B4;
                case "border" -> 0x363636;
                case "accent" -> 0x75C2D0;
                case "warning-bg" -> 0x332A1C;
                case "warning" -> 0xF0CF9A;
                default -> light.getRGB();
            };
            return new JBColor(light, new Color(dark));
        }
        public void openCitation(Citation citation) {
            if (demoMode() && citation.documentName() != null && citation.documentName().startsWith("Demo-")) {
                Messages.showInfoMessage(project, "这是界面演示引用，没有附带实际课件。接入真实问答服务并配置课件目录后，即可打开源文件。", "演示引用"); return;
            }
            String root = TaideUiSettings.getInstance(project).snapshot().courseDirectory();
            if (root.isBlank()) {
                Messages.showInfoMessage(project, "请先在 Settings → Tools → TAIDE 中配置课件目录。", "未配置课件目录"); return;
            }
            if (citation.slideNumber() < 1) {
                Messages.showInfoMessage(project, "引用缺少有效页码，请核对原始回答。", "无法打开引用"); return;
            }
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    Path path = CitationFiles.resolve(Path.of(root), citation.documentName());
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (project.isDisposed()) return;
                        var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
                        if (file == null) { Messages.showInfoMessage(project, "文件已移动或不存在，请重新选择课件目录。", "无法打开引用"); return; }
                        FileEditorManager.getInstance(project).openFile(file, true);
                        // SDK has no stable PDF/PPTX page-navigation API. Never pretend the requested page was selected.
                        Messages.showInfoMessage(project, "已打开 " + file.getName() + "。请手动定位到第 " + citation.slideNumber() + " 页 / 张。", "引用位置");
                    });
                } catch (Exception failure) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (!project.isDisposed()) Messages.showInfoMessage(project,
                                failure.getMessage() == null ? "无法打开课件，请检查目录权限。" : failure.getMessage(), "无法打开引用");
                    });
                }
            });
        }
    }
}
