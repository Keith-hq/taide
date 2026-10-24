package edu.cst3492.taide.context;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import edu.cst3492.taide.service.CodeContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Builds the frozen service DTO without retaining an Editor or PSI node in chat history. */
public final class CodeContextExtractor {
    private CodeContextExtractor() {
    }

    /**
     * Capture on EDT, before activating the tool window. The snapshot always belongs to the
     * clicked editor, even when another file becomes active during the background PSI lookup.
     */
    public static @Nullable SelectionSnapshot capture(@NotNull Editor editor) {
        if (editor.isDisposed()) {
            return null;
        }
        SelectionModel selection = editor.getSelectionModel();
        if (!selection.hasSelection()) {
            return null;
        }
        Document document = editor.getDocument();
        SelectionRange range = SelectionRange.fromOffsets(
                selection.getSelectionStart(), selection.getSelectionEnd(), document.getTextLength());
        if (range.isEmpty()) {
            return null;
        }
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        return new SelectionSnapshot(
                document,
                document.getModificationStamp(),
                file == null ? "" : file.getPath(),
                file == null ? "PlainText" : file.getFileType().getName(),
                document.getText(new TextRange(range.startOffset(), range.endOffset())),
                range.startOffset(),
                range.startLine(document::getLineNumber),
                range.endLine(document::getLineNumber));
    }

    /**
     * Convenience for callers already inside a read action with a committed document.
     * For an IDE action use capture() and the snapshot overload to keep PSI work off EDT.
     */
    public static @NotNull CodeContext extract(@NotNull Project project, @NotNull Editor editor) {
        return extract(project, capture(editor));
    }

    /** Call under a read action; use withDocumentsCommitted(project) for reliable Java PSI. */
    public static @NotNull CodeContext extract(@NotNull Project project,
                                              @Nullable SelectionSnapshot snapshot) {
        if (snapshot == null || project.isDisposed()) {
            return CodeContext.empty();
        }
        String language = snapshot.language();
        String enclosingClass = "";
        Document document = snapshot.document();
        PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
        // Keep the click-time code if the document changed. Never attach a class from a
        // newer or uncommitted PSI tree to the earlier selection.
        if (document.getModificationStamp() == snapshot.modificationStamp()
                && manager.isCommitted(document)) {
            PsiFile psiFile = manager.getPsiFile(document);
            if (psiFile != null && psiFile.isValid()) {
                language = psiFile.getLanguage().getID();
                if (psiFile instanceof PsiJavaFile) {
                    enclosingClass = findEnclosingClass(psiFile, snapshot);
                }
            }
        }
        return new CodeContext(snapshot.filePath(), language, snapshot.selectedText(),
                snapshot.startLine(), snapshot.endLine(), enclosingClass);
    }

    private static @NotNull String findEnclosingClass(@NotNull PsiFile file,
                                                     @NotNull SelectionSnapshot snapshot) {
        int start = snapshot.startOffset();
        int end = Math.min(file.getTextLength(), start + snapshot.selectedText().length());
        if (start >= end) {
            return "";
        }
        // Ignore boundary comments as well as indentation: a whole-class selection often
        // starts at a file-level comment. Use both ends so a selection across sibling or
        // nested classes is never incorrectly attributed to only its first class.
        PsiElement first = boundaryCodeElement(file, start, end, true);
        PsiElement last = boundaryCodeElement(file, start, end, false);
        if (first == null || last == null) {
            return "";
        }
        PsiElement common = PsiTreeUtil.findCommonParent(first, last);
        PsiClass enclosing = PsiTreeUtil.getParentOfType(common, PsiClass.class, false);
        if (enclosing == null) {
            return "";
        }
        String name = enclosing.getQualifiedName();
        if (name == null) {
            name = enclosing.getName();
        }
        return name == null ? "" : name;
    }

    private static @Nullable PsiElement boundaryCodeElement(@NotNull PsiFile file,
                                                            int start, int end,
                                                            boolean forwards) {
        int offset = forwards ? start : end - 1;
        while (offset >= start && offset < end) {
            ProgressManager.checkCanceled();
            PsiElement element = file.findElementAt(offset);
            if (element == null) {
                return null;
            }
            // Javadoc tokens are descendants of a PsiComment rather than comments
            // themselves, so skip their containing comment in one step.
            PsiComment comment = PsiTreeUtil.getParentOfType(element, PsiComment.class, false);
            PsiElement ignored = comment != null ? comment : element instanceof PsiWhiteSpace ? element : null;
            if (ignored == null) {
                return element;
            }
            TextRange range = ignored.getTextRange();
            offset = forwards ? Math.max(offset + 1, range.getEndOffset())
                    : Math.min(offset - 1, range.getStartOffset() - 1);
        }
        return null;
    }

    /** Immutable text/position values plus the original document only for the short PSI lookup. */
    public record SelectionSnapshot(
            @NotNull Document document,
            long modificationStamp,
            @NotNull String filePath,
            @NotNull String language,
            @NotNull String selectedText,
            int startOffset,
            int startLine,
            int endLine
    ) {
    }
}
