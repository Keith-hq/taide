package edu.cst3492.taide.action.automation;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

abstract class JavaSelectionAction extends AnAction implements SmartAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        var project = event.getProject();
        var editor = event.getData(CommonDataKeys.EDITOR);
        var file = event.getData(CommonDataKeys.PSI_FILE);
        event.getPresentation().setEnabled(project != null && editor != null
                && file instanceof PsiJavaFile && editor.getSelectionModel().hasSelection());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        var project = event.getProject();
        if (project == null) {
            return;
        }
        var editor = event.getData(CommonDataKeys.EDITOR);
        try {
            var selection = ActionCodeContext.capture(project, editor);
            perform(project, editor, selection.context());
        } catch (AutomationException error) {
            AutomationServices.getInstance(project).error(error.getMessage());
        }
    }
}
