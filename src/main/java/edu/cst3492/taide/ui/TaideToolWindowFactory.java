package edu.cst3492.taide.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.*;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.*;
import com.intellij.util.ui.UIUtil;
import edu.cst3492.taide.model.ModelSetupService;
import edu.cst3492.taide.service.*;
import edu.cst3492.taide.ui.chat.*;
import edu.cst3492.taide.ui.integration.CourseSourceNavigator;
import edu.cst3492.taide.ui.settings.*;
import org.jetbrains.annotations.NotNull;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/** IntelliJ adapter. All visible chat content is the same native Swing component used by visual QA. */
public final class TaideToolWindowFactory implements ToolWindowFactory, DumbAware {
    public static final String ID = "TAIDE";
    @Override public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow window) {
        IdeHost host = new IdeHost(project);
        ChatPanel panel = new ChatPanel(host);
        Content content = ContentFactory.getInstance().createContent(panel, "课程问答", false);
        content.setDisposer(() -> { host.close(); panel.close(); });
        window.getContentManager().addContent(content);
        host.bind(panel);
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
    private static final class IdeHost implements ChatPanel.Host, AutoCloseable {
        private final Project project;
        private final ModelSetupService models;
        private final AtomicLong generation = new AtomicLong();
        private final javax.swing.Timer preferences;
        private volatile boolean closed;
        private volatile SetupStatus setup = SetupStatus.unknown();
        private ProjectSetupChecker.Result configuration;
        private AutoCloseable modelListener;
        private Future<?> checkTask;
        private ChatPanel panel;
        private long observedSettingsRevision = -1;

        private IdeHost(Project project) {
            this.project = project; models = ModelSetupService.getInstance();
            preferences = new javax.swing.Timer(1000, event -> {
                if (!closed && !project.isDisposed()
                        && TaideUiSettings.getInstance(project).revision() != observedSettingsRevision) {
                    if (panel != null) panel.refreshMode();
                    checkConfiguration(false);
                }
            });
        }

        private void bind(ChatPanel value) {
            panel = value;
            modelListener = models.listen(this::publishSetup);
            preferences.start(); refreshSetup();
        }

        public SetupStatus setupStatus() { return setup; }
        public void prepareModel() {
            if (!closed && !project.isDisposed()) models.download(project);
        }
        public void refreshSetup() {
            if (closed || project.isDisposed()) return;
            models.refresh(); checkConfiguration(false);
        }
        public void useLiveMode() {
            if (!closed && !project.isDisposed() && setup.ready()) checkConfiguration(true);
        }

        /** Only this worker reads secure storage, course files or the JSON index. */
        private void checkConfiguration(boolean switchToLive) {
            if (closed || project.isDisposed()) return;
            long request = generation.incrementAndGet();
            Future<?> previous = checkTask;
            if (previous != null) previous.cancel(true);
            TaideUiSettings settings = TaideUiSettings.getInstance(project);
            long revision = settings.revision();
            SettingsSnapshot snapshot = settings.snapshot();
            observedSettingsRevision = revision;
            configuration = null; publishSetup();
            checkTask = ApplicationManager.getApplication().executeOnPooledThread(() -> {
                ProjectSetupChecker.Result checked;
                try {
                    String key = settings.readApiKey(snapshot);
                    checked = ProjectSetupChecker.check(snapshot, key != null && !key.isBlank(), TaidePaths.indexPath(project.getLocationHash()));
                } catch (CancellationException canceled) { return; }
                catch (RuntimeException failure) {
                    checked = new ProjectSetupChecker.Result(false, false, "无法读取 API Key 或本地配置，请打开设置检查后重试。");
                }
                if (closed || project.isDisposed() || generation.get() != request || Thread.currentThread().isInterrupted()) return;
                ProjectSetupChecker.Result result = checked;
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (closed || project.isDisposed() || generation.get() != request) return;
                    if (settings.revision() != revision) { checkConfiguration(false); return; }
                    configuration = result; publishSetup();
                    if (switchToLive && setup.ready()) {
                        settings.update(new SettingsSnapshot(snapshot.provider(), snapshot.baseUrl(), snapshot.model(), snapshot.courseDirectory(), false));
                        observedSettingsRevision = settings.revision();
                        if (panel != null) panel.refreshMode();
                    }
                });
            });
        }

        /** ModelSetupService delivers listeners on EDT; this method only combines detached state. */
        private void publishSetup() {
            if (closed || project.isDisposed()) return;
            ModelSetupService.Status model = models.status();
            ProjectSetupChecker.Result config = configuration;
            boolean checked = config != null && (model.checked() || model.preparing());
            String detail = !model.ready() || model.preparing() ? model.detail()
                    : config == null ? "正在检查 API 配置和课件索引…" : config.detail();
            if (model.preparing() && model.total() > 0) {
                long percent = Math.min(100, Math.max(0, Math.round(100d * model.downloaded() / model.total())));
                detail += " · " + percent + "%";
            }
            SetupStatus next = new SetupStatus(checked, model.ready(), config != null && config.apiReady(),
                    config != null && config.indexReady(), model.preparing(), detail);
            if (Objects.equals(next, setup)) return;
            setup = next;
            if (panel != null) panel.refreshSetupStatus();
        }

        @Override public void close() {
            closed = true; generation.incrementAndGet(); preferences.stop();
            Future<?> task = checkTask; if (task != null) task.cancel(true);
            if (modelListener != null) {
                try { modelListener.close(); } catch (Exception ignored) { }
                modelListener = null;
            }
            panel = null;
        }
        public boolean demoMode() { return TaideUiSettings.getInstance(project).snapshot().demoMode(); }
        public String courseDirectory() { return TaideUiSettings.getInstance(project).snapshot().courseDirectory(); }
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
            if (closed || project.isDisposed()) return;
            ShowSettingsUtil.getInstance().showSettingsDialog(project, TaideSettingsConfigurable.class);
            if (closed || project.isDisposed()) return;
            if (panel != null) panel.refreshMode();
            refreshSetup();
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
            openCitation(citation, false);
        }
        public void openCitation(Citation citation, boolean demonstration) {
            openCitation(citation, demonstration, courseDirectory());
        }
        public void openCitation(Citation citation, boolean demonstration, String root) {
            if (project.isDisposed()) return;
            if (demonstration) {
                Messages.showInfoMessage(project, "这是界面演示引用，没有附带实际课件。接入真实问答服务并配置课件目录后，即可打开源文件。", "演示引用"); return;
            }
            CourseSourceNavigator.getInstance(project).open(citation, root);
        }
    }
}
