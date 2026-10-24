package edu.cst3492.taide.action.automation;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.CoreIconManager;
import com.intellij.util.ui.UIUtil;
import edu.cst3492.taide.git.StagedChanges;
import edu.cst3492.taide.rag.RetrievedChunk;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.plaf.basic.BasicHTML;
import java.awt.Component;
import java.awt.Container;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

public class AutomationPlatformTest extends BasePlatformTestCase {
    static {
        // 平台图标在测试类加载时启用，离屏截图使用实际图标资源。
        IconManager.Companion.activate(new CoreIconManager());
        IconLoader.activate();
    }

    public void testActionsAreRegistered() {
        assertInstanceOf(ActionManager.getInstance().getAction("taide.generateTests"), GenerateTestsAction.class);
        assertInstanceOf(ActionManager.getInstance().getAction("taide.generateCommitMessage"), GenerateCommitMessageAction.class);
        assertInstanceOf(ActionManager.getInstance().getAction("taide.courseReview"), CourseReviewAction.class);
    }

    public void testSelectionCapturesExactLinesAndClass() {
        myFixture.configureByText("Greeting.java", TestGeneratorTest.SOURCE);
        String text = myFixture.getEditor().getDocument().getText();
        int start = text.indexOf("    public String");
        int end = text.indexOf("    }\n") + "    }\n".length();
        myFixture.getEditor().getSelectionModel().setSelection(start, end);
        var selection = ActionCodeContext.capture(getProject(), myFixture.getEditor());
        assertEquals(3, selection.context().startLine());
        assertEquals(6, selection.context().endLine());
        assertEquals("demo.Greeting", selection.context().enclosingClass());
        assertEquals(text.substring(start, end), selection.context().selectedText());
        assertEquals(text, selection.fullSource());
    }

    public void testJavaActionsDisableWithoutSelection() {
        myFixture.configureByText("Greeting.java", TestGeneratorTest.SOURCE);
        var context = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, getProject())
                .add(CommonDataKeys.EDITOR, myFixture.getEditor())
                .add(CommonDataKeys.PSI_FILE, myFixture.getFile()).build();
        var event = AnActionEvent.createFromDataContext("test", null, context);
        var action = new GenerateTestsAction();
        action.update(event);
        assertFalse(event.getPresentation().isEnabled());
        myFixture.getEditor().getSelectionModel().setSelection(0, 10);
        action.update(event);
        assertTrue(event.getPresentation().isEnabled());
    }

    public void testBindingsPreserveOriginalSourceMetadata() {
        var services = AutomationServices.getInstance(getProject());
        var source = source();
        var opened = new AtomicReference<RetrievedChunk>();
        var model = new ModelStub("{}");
        services.bind(model, (query, k) -> List.of(source), opened::set);
        assertTrue(services.canOpenSources());
        assertSame(model, services.model());
        assertSame(source, services.retriever().retrieve("query", 1).getFirst());
        services.openSource(source);
        assertSame(source, opened.get());
        opened.set(null);
        var captured = services.captureSourceOpener();
        services.bind(model, (query, k) -> List.of(), ignored -> fail("A captured callback must keep the original binding."));
        captured.accept(source);
        assertSame(source, opened.get());
    }

    public void testDefaultSourceNavigationIsAvailableWithoutOpeningChat() {
        var services = new AutomationServices(getProject());
        assertTrue(services.canOpenSources());
        assertNotNull(services.captureSourceOpener());
    }

    public void testReviewDialogKeepsCapturedSourceBinding() {
        var services = AutomationServices.getInstance(getProject());
        var source = source();
        var opened = new AtomicReference<RetrievedChunk>();
        var model = new ModelStub("{}");
        services.bind(model, (query, k) -> List.of(source), opened::set);
        var review = review(source);
        var dialog = new AutomationDialogs.Review(getProject(), review);
        try {
            services.bind(model, (query, k) -> List.of(), ignored -> fail("An existing review must retain its callback."));
            JButton open = findButton(dialog.createCenterPanel(), "Open course source");
            assertNotNull(open);
            assertTrue(open.isEnabled());
            open.doClick();
            assertSame(source, opened.get());
        } finally {
            dialog.close(AutomationDialogs.Review.OK_EXIT_CODE);
        }
    }

    public void testReviewDialogUsesRequestCallbackAfterBindingsChange() {
        var services = AutomationServices.getInstance(getProject());
        var source = source();
        var opened = new AtomicReference<RetrievedChunk>();
        var model = new ModelStub("{}");
        services.bind(model, (query, k) -> List.of(source), opened::set);
        var captured = services.captureSourceOpener();
        services.bind(model, (query, k) -> List.of(), ignored -> fail("The request callback must survive a later binding change."));
        var dialog = new AutomationDialogs.Review(getProject(), review(source), captured);
        try {
            JButton open = findButton(dialog.createCenterPanel(), "Open course source");
            assertNotNull(open);
            assertTrue(open.isEnabled());
            open.doClick();
            assertSame(source, opened.get());
        } finally {
            dialog.close(AutomationDialogs.Review.OK_EXIT_CODE);
        }
    }

    public void testSourceNavigationPropagatesCancellation() {
        var services = new AutomationServices(getProject());
        var cancelled = new ProcessCanceledException();
        services.bind(new ModelStub("{}"), (query, k) -> List.of(), ignored -> {
            throw cancelled;
        });
        try {
            services.captureSourceOpener().accept(source());
            fail("Cancellation must be propagated.");
        } catch (ProcessCanceledException error) {
            assertSame(cancelled, error);
        }
    }

    public void testMissingServicesHaveReadableErrors() {
        var services = new AutomationServices(getProject());
        try {
            services.model();
            fail("模型服务缺失时应提示配置。");
        } catch (AutomationException error) {
            assertTrue(error.getMessage().contains("language-model service"));
        }
        try {
            services.retriever();
            fail("检索服务缺失时应提示配置。");
        } catch (AutomationException error) {
            assertTrue(error.getMessage().contains("course retriever"));
        }
    }

    public void testModelTitlesAreRenderedAsPlainText() {
        var review = new CourseReview(List.of(new CourseReview.Finding(
                CourseReview.Severity.INFO, "<html><b>Literal model text</b></html>",
                "Explanation", "Suggestion", List.of())), false);
        var panel = AutomationPanels.review(review, null, ignored -> {});
        JTable table = findTable(panel);
        assertNotNull(table);
        var renderer = (JComponent) table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1);
        assertEquals(Boolean.TRUE, renderer.getClientProperty("html.disable"));
        assertNull(renderer.getClientProperty(BasicHTML.propertyKey));
    }

    public void testBackgroundWorkAndEdtCallback() throws Exception {
        var completed = new AtomicBoolean();
        var callbackOnEdt = new AtomicBoolean();
        var workOffEdt = new AtomicBoolean();
        var services = AutomationServices.getInstance(getProject());
        services.background("TAIDE test task", () -> {
            workOffEdt.set(!ApplicationManager.getApplication().isDispatchThread());
            return "complete";
        }, result -> {
            callbackOnEdt.set(ApplicationManager.getApplication().isDispatchThread());
            completed.set(result.equals("complete"));
        });
        pumpUntil(completed::get);
        assertTrue(workOffEdt.get());
        assertTrue(callbackOnEdt.get());
        assertFalse(services.isBusy());
    }

    public void testCancellationReleasesBusyState() throws Exception {
        var callback = new AtomicBoolean();
        var services = AutomationServices.getInstance(getProject());
        services.background("TAIDE cancelled task", () -> {
            throw new ProcessCanceledException();
        }, value -> callback.set(true));
        pumpUntil(() -> !services.isBusy());
        assertFalse(callback.get());
    }

    public void testCreatedTestCanBeUndone() throws Exception {
        Path root = Files.createTempDirectory("taide-vfs-test-");
        try {
            var generated = TestGenerator.validate(TestGeneratorTest.TEST_SOURCE, "demo");
            var file = TestFileWriter.write(getProject(), root, generated);
            assertTrue(file.isValid());
            assertEquals(generated.source(), Files.readString(root.resolve(generated.relativePath())));
            assertTrue(UndoManager.getInstance(getProject()).isUndoAvailable(null));
            UndoManager.getInstance(getProject()).undo(null);
            assertFalse(Files.exists(root.resolve(generated.relativePath())));
        } finally {
            var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root);
            if (file != null) {
                ApplicationManager.getApplication().runWriteAction(() -> {
                    try {
                        file.delete(this);
                    } catch (java.io.IOException error) {
                        throw new AssertionError(error);
                    }
                });
            }
        }
    }

    public void testReviewAndPreviewScreenshots() throws Exception {
        IconLoader.activate();
        var review = new CourseReview(List.of(new CourseReview.Finding(
                CourseReview.Severity.ERROR,
                "User input is concatenated into an SQL query",
                "The selected method builds SQL by joining a request parameter with the query text.",
                "Use a PreparedStatement with a bound parameter, as described by the retrieved JDBC excerpt.",
                List.of(source()))), true);
        render(AutomationPanels.review(review, ignored -> {}, ignored -> {}), 960, 620, "course-review-wide");
        var general = new CourseReview(List.of(new CourseReview.Finding(
                CourseReview.Severity.WARNING,
                "Missing empty-input validation",
                "The selected method accepts an empty name without validating it.",
                "Validate the method argument before invoking the persistence layer.", List.of())), false);
        render(AutomationPanels.review(general, null, ignored -> {}), 640, 560, "general-review-compact");
        var test = new AutomationDialogs.TestPreview(getProject(),
                TestGenerator.validate(TestGeneratorTest.TEST_SOURCE, "demo"), List.of(Path.of("/project/src/test/java")), true);
        try {
            render(test.createCenterPanel(), 800, 560, "junit-preview");
        } finally {
            test.close(AutomationDialogs.TestPreview.CANCEL_EXIT_CODE);
        }
        var staged = new StagedChanges(Path.of("/project"), "diff fixture", List.of(), "test");
        var commit = new AutomationDialogs.CommitPreview(getProject(),
                "fix(validation): reject empty user names\n\nValidate input before looking up the user record.", staged);
        try {
            render(commit.createCenterPanel(), 700, 320, "commit-preview");
        } finally {
            commit.close(AutomationDialogs.CommitPreview.CANCEL_EXIT_CODE);
        }
    }

    private static RetrievedChunk source() {
        return new RetrievedChunk("fixture-jdbc", "JDBC Demonstration Notes.pdf", 3,
                "Bind user input with PreparedStatement parameters.", 0.85);
    }

    private static CourseReview review(RetrievedChunk source) {
        return new CourseReview(List.of(new CourseReview.Finding(CourseReview.Severity.INFO,
                "Use bound parameters", "The course explains parameter binding.",
                "Use PreparedStatement.", List.of(source))), true);
    }

    private static void pumpUntil(BooleanSupplier finished) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        // 后台任务的完成与取消回调由 EDT 处理。
        while (!finished.getAsBoolean() && System.nanoTime() < deadline) {
            UIUtil.dispatchAllInvocationEvents();
            Thread.sleep(10);
        }
        assertTrue("后台任务应在时限内完成。", finished.getAsBoolean());
    }

    private static void render(JComponent component, int width, int height, String name) throws Exception {
        component.setSize(width, height);
        layout(component);
        // 换行文本的首轮布局确定宽度，第二轮据此计算实际高度。
        layout(component);
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        component.printAll(graphics);
        graphics.dispose();
        var colors = new HashSet<Integer>();
        for (int x = 0; x < width; x += 7) {
            for (int y = 0; y < height; y += 7) {
                colors.add(image.getRGB(x, y));
            }
        }
        assertTrue("界面截图应包含实际内容。", colors.size() > 1);
        Path output = Path.of("build", "reports", "ui", name + ".png");
        Files.createDirectories(output.getParent());
        ImageIO.write(image, "png", output.toFile());
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                layout(nested);
            }
        }
    }

    private static JTable findTable(Container container) {
        if (container instanceof JTable table) {
            return table;
        }
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                JTable table = findTable(nested);
                if (table != null) {
                    return table;
                }
            }
        }
        return null;
    }

    private static JButton findButton(Container container, String accessibleName) {
        if (container instanceof JButton button
                && accessibleName.equals(button.getAccessibleContext().getAccessibleName())) {
            return button;
        }
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                JButton button = findButton(nested, accessibleName);
                if (button != null) {
                    return button;
                }
            }
        }
        return null;
    }
}
