package edu.cst3492.taide.action.automation;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiTreeUtil;
import edu.cst3492.taide.service.CodeContext;

final class ActionCodeContext {
    private ActionCodeContext() {}

    static Selection capture(Project project, Editor editor) {
        if (editor == null || !editor.getSelectionModel().hasSelection()) {
            throw new AutomationException("Select a Java class or method in the editor first.");
        }
        var documents = PsiDocumentManager.getInstance(project);
        documents.commitDocument(editor.getDocument());
        var file = documents.getPsiFile(editor.getDocument());
        if (!(file instanceof PsiJavaFile) || file.getVirtualFile() == null) {
            throw new AutomationException("Select code from a Java source file.");
        }
        var selection = editor.getSelectionModel();
        int start = selection.getSelectionStart();
        int end = selection.getSelectionEnd();
        PsiClass enclosing = PsiTreeUtil.getParentOfType(file.findElementAt(start), PsiClass.class, false);
        String className = enclosing == null ? "" : enclosing.getQualifiedName();
        if (className == null) {
            className = enclosing.getName();
        }
        CodeContext context = new CodeContext(
                file.getVirtualFile().getPath(),
                "JAVA",
                selection.getSelectedText(),
                editor.getDocument().getLineNumber(start) + 1,
                editor.getDocument().getLineNumber(Math.max(start, end - 1)) + 1,
                className == null ? "" : className);
        return new Selection(context, editor.getDocument().getText(), file.getVirtualFile());
    }

    record Selection(CodeContext context, String fullSource, VirtualFile file) {}
}
