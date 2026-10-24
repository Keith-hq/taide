package edu.cst3492.taide.ui.integration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import edu.cst3492.taide.rag.RetrievedChunk;
import edu.cst3492.taide.service.Citation;
import edu.cst3492.taide.ui.chat.CitationFiles;
import edu.cst3492.taide.ui.settings.TaideUiSettings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/** Shared file navigation for chat and automation; independent of the tool-window lifecycle. */
@Service(Service.Level.PROJECT)
public final class CourseSourceNavigator {
    private final Project project;

    public CourseSourceNavigator(Project project) { this.project = project; }

    public static CourseSourceNavigator getInstance(Project project) {
        return project.getService(CourseSourceNavigator.class);
    }

    /** Capture before generation so later settings changes cannot redirect an old review. */
    public Consumer<RetrievedChunk> snapshot() { return snapshot(ideUi()); }

    Consumer<RetrievedChunk> snapshot(Ui ui) {
        String directory = TaideUiSettings.getInstance(project).snapshot().courseDirectory();
        return source -> {
            Citation citation = source == null ? null
                    : new Citation(source.documentName(), source.slideNumber(), source.text(), source.score());
            open(citation, directory, ui);
        };
    }

    /** Chat passes its message's stored directory, rather than the latest settings. */
    public void open(Citation citation, String directory) { open(citation, directory, ideUi()); }

    void open(Citation citation, String directory, Ui ui) {
        if (project.isDisposed()) return;
        ModalityState modality = ModalityState.defaultModalityState();
        String root = Objects.requireNonNullElse(directory, "");
        if (root.isBlank()) {
            notice(ui, "请先在 Settings → Tools → TAIDE 中配置课件目录。", "未配置课件目录", modality);
            return;
        }
        if (citation == null || citation.slideNumber() < 1) {
            notice(ui, "引用缺少有效页码，请核对原始回答。", "无法打开引用", modality);
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (project.isDisposed()) return;
            try {
                Path path = CitationFiles.resolve(Path.of(root), citation.documentName());
                if (project.isDisposed()) return;
                VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
                onUi(() -> {
                    if (file == null || !file.isValid()) {
                        ui.notice("文件已移动或不存在，请检查原课件目录。", "无法打开引用");
                        return;
                    }
                    try {
                        ui.open(file);
                        ui.notice("已打开 " + file.getName() + "。请手动定位到第 "
                                + citation.slideNumber() + " 页 / 张。", "引用位置");
                    } catch (ProcessCanceledException canceled) {
                        throw canceled;
                    } catch (RuntimeException failure) {
                        ui.notice("IDE 无法打开该课件，请检查文件关联后重试。", "无法打开引用");
                    }
                }, modality);
            } catch (ProcessCanceledException canceled) {
                throw canceled;
            } catch (InvalidPathException invalid) {
                notice(ui, "课件目录无效，请在设置中重新选择。", "无法打开引用", modality);
            } catch (IOException | UncheckedIOException | SecurityException failure) {
                notice(ui, "未能打开引用。请检查原课件目录、文件名和权限；仅支持目录内的 PDF/PPTX，重名文件需提供相对路径。",
                        "无法打开引用", modality);
            }
        });
    }

    private void notice(Ui ui, String message, String title, ModalityState modality) {
        onUi(() -> ui.notice(message, title), modality);
    }

    private void onUi(Runnable action, ModalityState modality) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed()) action.run();
        }, modality);
    }

    private Ui ideUi() {
        return new Ui() {
            @Override public void open(VirtualFile file) {
                FileEditorManager.getInstance(project).openFile(file, true);
            }
            @Override public void notice(String message, String title) {
                Messages.showInfoMessage(project, message, title);
            }
        };
    }

    /** Small UI boundary: the actual resolver and VFS are also exercised in platform tests. */
    interface Ui {
        void open(VirtualFile file);
        void notice(String message, String title);
    }
}
