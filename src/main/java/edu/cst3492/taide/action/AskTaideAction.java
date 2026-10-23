package edu.cst3492.taide.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import edu.cst3492.taide.context.CodeContextExtractor;
import edu.cst3492.taide.ui.TaideToolWindowFactory;
import org.jetbrains.annotations.NotNull;

/** F0.4: transfer the selected code to TAIDE and ask the default explanation question. */
public final class AskTaideAction extends DumbAwareAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        boolean available = project != null && !project.isDisposed()
                && editor != null && !editor.isDisposed();
        event.getPresentation().setVisible(available);
        event.getPresentation().setEnabled(available && editor.getSelectionModel().hasSelection());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (project == null || project.isDisposed() || editor == null || editor.isDisposed()) {
            return;
        }
        CodeContextExtractor.SelectionSnapshot snapshot = CodeContextExtractor.capture(editor);
        if (snapshot == null) {
            return;
        }
        ReadAction.nonBlocking(() -> CodeContextExtractor.extract(project, snapshot))
                .withDocumentsCommitted(project)
                .expireWith(project)
                .finishOnUiThread(ModalityState.nonModal(),
                        context -> TaideToolWindowFactory.openAndAsk(project, context))
                .submit(AppExecutorUtil.getAppExecutorService());
    }
}
