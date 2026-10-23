package edu.cst3492.taide.action.automation;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import edu.cst3492.taide.git.StagedChanges;
import edu.cst3492.taide.git.StagedDiffService;
import edu.cst3492.taide.service.CodeContext;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public final class GenerateCommitMessageAction extends AnAction implements SmartAction {
    @Override
    public String id() {
        return "taide.generateCommitMessage";
    }

    @Override
    public String displayName() {
        return "Generate Commit Message";
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(event.getProject() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        var file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        String directory = file == null ? project.getBasePath()
                : file.isDirectory() ? file.getPath() : file.getParent().getPath();
        generate(project, directory);
    }

    @Override
    public void perform(Project project, Editor editor, CodeContext context) {
        String directory = context.filePath().isBlank() ? project.getBasePath()
                : Path.of(context.filePath()).toAbsolutePath().getParent().toString();
        generate(project, directory);
    }

    private static void generate(Project project, String directory) {
        var services = AutomationServices.getInstance(project);
        if (directory == null) {
            services.error("Open a local Git project first.");
            return;
        }
        var git = new StagedDiffService();
        services.background("TAIDE: Read Staged Changes", () -> git.read(Path.of(directory)), staged -> {
            if (!new AutomationDialogs.StagedPreview(project, staged).showAndGet()) {
                return;
            }
            services.background("TAIDE: Generate Commit Message",
                    () -> new CommitMessageGenerator(services.model()).generate(staged),
                    message -> preview(project, services, git, staged, message));
        });
    }

    private static void preview(Project project, AutomationServices services, StagedDiffService git,
                                StagedChanges staged, String message) {
        var preview = new AutomationDialogs.CommitPreview(project, message, staged);
        if (!preview.showAndGet()) {
            return;
        }
        if (Messages.showYesNoDialog(project,
                "Create a local commit for " + staged.files().size() + " staged files in\n"
                        + staged.repositoryRoot() + "?\nThis Git operation is outside editor undo.",
                "Commit Staged Files", "Commit", "Cancel", Messages.getQuestionIcon()) != Messages.YES) {
            return;
        }
        String approvedMessage = preview.message();
        services.background("TAIDE: Commit Staged Files", () -> git.commit(staged, approvedMessage), hash -> {
            LocalFileSystem.getInstance().refresh(true);
            VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
            services.info("Created local commit " + hash.substring(0, 8) + ".");
        });
    }
}
