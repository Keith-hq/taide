package edu.cst3492.taide.context;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import edu.cst3492.taide.action.AskTaideAction;
import edu.cst3492.taide.service.CodeContext;

/** Real document/Java PSI checks complement the SDK-free offset checks. */
public final class CodeContextExtractorPlatformTest extends BasePlatformTestCase {
    public void testNoSelectionProducesEmptyContextAndDisablesAction() {
        myFixture.configureByText("Example.java", "class Example {}\n");
        assertNull(CodeContextExtractor.capture(myFixture.getEditor()));
        assertEquals(CodeContext.empty(), extract(null));

        var event = AnActionEvent.createFromDataContext("test", null, SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, getProject())
                .add(CommonDataKeys.EDITOR, myFixture.getEditor()).build());
        var action = new AskTaideAction();
        action.update(event);
        assertTrue(event.getPresentation().isVisible());
        assertFalse(event.getPresentation().isEnabled());
        myFixture.getEditor().getSelectionModel().setSelection(0, 5);
        action.update(event);
        assertTrue(event.getPresentation().isEnabled());
    }

    public void testSelectedTextAndExclusiveEndLineMatchTheEditor() {
        String source = "package demo;\nclass Example {\n    int count;\n    void run() {}\n}\n";
        myFixture.configureByText("Example.java", source);
        int start = source.indexOf("    int count;");
        int end = source.indexOf("    void run");
        CodeContext context = select(start, end);
        assertEquals(source.substring(start, end), context.selectedText());
        assertEquals(3, context.startLine());
        assertEquals(3, context.endLine());
        assertEquals("demo.Example", context.enclosingClass());
        assertEquals("JAVA", context.language());
        assertEquals(myFixture.getFile().getVirtualFile().getPath(), context.filePath());
    }

    public void testWholeClassWithBoundaryCommentsStillFindsItsClass() {
        String source = "package demo;\n// Introductory comment\n/** Class documentation. */\n"
                + "class Example { int value; }\n// Closing comment\n";
        myFixture.configureByText("Example.java", source);
        CodeContext context = select(source.indexOf("// Introductory"), source.length());
        assertEquals("demo.Example", context.enclosingClass());
    }

    public void testSelectionAcrossNestedClassUsesTheirCommonClass() {
        String source = "package demo;\nclass Outer {\n    class Inner { int inside; }\n    int outside;\n}\n";
        myFixture.configureByText("Outer.java", source);
        CodeContext context = select(source.indexOf("int inside"), source.indexOf("int outside;") + 12);
        assertEquals("demo.Outer", context.enclosingClass());
    }

    public void testSelectionAcrossTopLevelClassesHasNoEnclosingClass() {
        String source = "class First { int first; }\nclass Second { int second; }\n";
        myFixture.configureByText("Example.java", source);
        CodeContext context = select(source.indexOf("int first"), source.indexOf("int second;") + 11);
        assertEquals("", context.enclosingClass());
    }

    public void testSelectionRemainsFrozenAfterAnEditAndCommit() {
        String source = "class Original { int original; }\n";
        myFixture.configureByText("Original.java", source);
        var editor = myFixture.getEditor();
        int start = source.indexOf("int original;");
        editor.getSelectionModel().setSelection(start, start + "int original;".length());
        var snapshot = CodeContextExtractor.capture(editor);
        WriteCommandAction.runWriteCommandAction(getProject(),
                () -> editor.getDocument().setText("class Replacement { int other; }\n"));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        CodeContext context = extract(snapshot);
        assertEquals("int original;", context.selectedText());
        assertEquals(1, context.startLine());
        assertEquals(1, context.endLine());
        assertEquals("", context.enclosingClass());
    }

    public void testSnapshotUsesTheOriginalFileAfterTheActiveEditorChanges() {
        myFixture.configureByText("Original.java", "class Original { int original; }\n");
        myFixture.getEditor().getSelectionModel().setSelection(17, 30);
        var snapshot = CodeContextExtractor.capture(myFixture.getEditor());
        myFixture.configureByText("Other.java", "class Other { int other; }\n");
        CodeContext context = extract(snapshot);
        assertEquals("int original;", context.selectedText());
        assertEquals("Original", context.enclosingClass());
        assertTrue(context.filePath().endsWith("Original.java"));
    }

    public void testPlainTextAndWhitespaceSelectionsDoNotInventAClass() {
        myFixture.configureByText("notes.txt", "course notes\n");
        CodeContext text = select(0, 12);
        assertEquals("course notes", text.selectedText());
        assertEquals("", text.enclosingClass());

        String source = "class Example {\n    int value;\n}\n";
        myFixture.configureByText("Example.java", source);
        CodeContext whitespace = select(source.indexOf("    "), source.indexOf("    ") + 4);
        assertEquals("    ", whitespace.selectedText());
        assertEquals("", whitespace.enclosingClass());
    }

    private CodeContext select(int start, int end) {
        myFixture.getEditor().getSelectionModel().setSelection(start, end);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        return extract(CodeContextExtractor.capture(myFixture.getEditor()));
    }

    private CodeContext extract(CodeContextExtractor.SelectionSnapshot snapshot) {
        return ReadAction.compute(() -> CodeContextExtractor.extract(getProject(), snapshot));
    }
}
