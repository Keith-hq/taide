package edu.cst3492.taide.action.automation;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import edu.cst3492.taide.service.CodeContext;

public interface SmartAction {
    String id();

    String displayName();

    /** 由 EDT 调用，耗时工作交给项目后台任务。 */
    void perform(Project project, Editor editor, CodeContext context);
}
