package edu.cst3492.taide.ui.integration;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/** Opens TAIDE citations at a physical page, reusing one preview tab per source document. */
public final class SourcePreviewFileEditorProvider implements FileEditorProvider, DumbAware {
    public static final String ID = "taide.sourcePreview";

    @Override public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return file instanceof SourcePreviewFile;
    }

    @Override public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        return new SourcePreviewFileEditor(project, (SourcePreviewFile) file);
    }

    @Override public @NotNull String getEditorTypeId() { return ID; }
    @Override public @NotNull FileEditorPolicy getPolicy() { return FileEditorPolicy.HIDE_DEFAULT_EDITOR; }

    static void open(Project project, VirtualFile source, int page) {
        FileEditorManager manager = FileEditorManager.getInstance(project);
        SourcePreviewFile target = null;
        for (VirtualFile open : manager.getOpenFiles()) {
            if (open instanceof SourcePreviewFile preview && preview.source.equals(source)) {
                target = preview;
                break;
            }
        }
        boolean reused = target != null;
        if (target == null) target = new SourcePreviewFile(source, page);
        FileEditor[] editors = manager.openFile(target, true);
        for (FileEditor editor : editors) {
            if (editor instanceof SourcePreviewFileEditor preview) {
                // A new tab already started loading its initial target in the constructor.
                if (reused) preview.showPage(page);
                manager.setSelectedEditor(target, ID);
                return;
            }
        }
        throw new IllegalStateException("The source preview editor is unavailable.");
    }
}
