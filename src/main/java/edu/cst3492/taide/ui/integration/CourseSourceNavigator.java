package edu.cst3492.taide.ui.integration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
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
            notice(ui, "Set your materials folder in Settings → Tools → TAIDE first.", "Materials folder not configured", modality);
            return;
        }
        if (citation == null || citation.slideNumber() < 1) {
            notice(ui, "Citation has no valid page number. Check the original answer.", "Cannot open source", modality);
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
                        ui.notice("File moved or missing. Check the original materials folder.", "Cannot open source");
                        return;
                    }
                    try {
                        ui.open(file, citation.slideNumber());
                    } catch (ProcessCanceledException canceled) {
                        throw canceled;
                    } catch (RuntimeException failure) {
                        ui.notice("The source preview could not be opened. Check the source file and retry.", "Cannot open source");
                    }
                }, modality);
            } catch (ProcessCanceledException canceled) {
                throw canceled;
            } catch (InvalidPathException invalid) {
                notice(ui, "Invalid materials folder. Choose it again in Settings.", "Cannot open source", modality);
            } catch (IOException | UncheckedIOException | SecurityException failure) {
                notice(ui, "Cannot open source. Check the materials folder, file name and permissions. Only PDF/PPTX files inside that folder are supported; duplicate names need relative paths.",
                        "Cannot open source", modality);
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
            @Override public void open(VirtualFile file, int page) {
                SourcePreviewFileEditorProvider.open(project, file, page);
            }
            @Override public void notice(String message, String title) {
                Messages.showInfoMessage(project, message, title);
            }
        };
    }

    /** Small UI boundary: the actual resolver and VFS are also exercised in platform tests. */
    interface Ui {
        void open(VirtualFile file, int page);
        void notice(String message, String title);
    }
}
