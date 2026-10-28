package edu.cst3492.taide.ui.integration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.FileEditorManagerTestCase;
import edu.cst3492.taide.service.Citation;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exercises actual registered editor creation, physical page rendering, reuse and cancellation. */
public final class SourcePreviewFileEditorPlatformTest extends FileEditorManagerTestCase {
    private final List<Path> directories = new ArrayList<>();

    @Override protected void tearDown() throws Exception {
        try {
            var manager = FileEditorManager.getInstance(getProject());
            for (VirtualFile file : manager.getOpenFiles()) manager.closeFile(file);
            for (Path directory : directories) FileUtil.delete(directory.toFile());
        } finally { super.tearDown(); }
    }

    public void testChatCitationOpensRequestedPageAndReusesItsTab() throws Exception {
        Path root = directory();
        Path path = SourcePageRendererTest.pdf(root.resolve("Unit #1 notes.pdf"), new PDRectangle(144, 72));
        var navigator = CourseSourceNavigator.getInstance(getProject());
        navigator.open(new Citation(path.getFileName().toString(), 3, "excerpt", .8), root.toString());
        var manager = FileEditorManager.getInstance(getProject());
        PlatformTestUtil.waitWithEventsDispatching("Citation creates preview editor",
                () -> manager.getSelectedEditor() instanceof SourcePreviewFileEditor, 10);
        var editor = (SourcePreviewFileEditor) manager.getSelectedEditor();
        await(editor, 3);
        assertEquals(3, editor.pageCount());
        assertFalse(editor.isModified());
        assertEquals(path.toRealPath(), ((SourcePreviewFile) editor.getFile()).source.toNioPath().toRealPath());

        navigator.open(new Citation(path.getFileName().toString(), 1, "excerpt", .8), root.toString());
        await(editor, 1);
        assertSame(editor, manager.getSelectedEditor());
        assertEquals(1, manager.getOpenFiles().length);
    }

    public void testPptxSlideTargetAndNavigationHistoryArePreserved() throws Exception {
        Path path = SourcePageRendererTest.pptx(directory().resolve("slides.pptx"));
        VirtualFile source = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        assertNotNull(source);
        assertFalse(new SourcePreviewFileEditorProvider().accept(getProject(), source));
        SourcePreviewFileEditorProvider.open(getProject(), source, 2);
        var editor = (SourcePreviewFileEditor) FileEditorManager.getInstance(getProject()).getSelectedEditor();
        await(editor, 2);
        assertEquals(2, editor.pageCount());
        var history = editor.getState(FileEditorStateLevel.NAVIGATION);
        editor.showPage(1);
        await(editor, 1);
        editor.setState(history);
        await(editor, 2);
    }

    public void testAStaleRenderCannotReplaceANewerPageAndRenderingIsOffEdt() throws Exception {
        Path path = Files.writeString(directory().resolve("controlled.pdf"), "fixture");
        VirtualFile source = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean onEdt = new AtomicBoolean();
        var editor = new SourcePreviewFileEditor(getProject(), new SourcePreviewFile(source, 1), (file, page) -> {
            onEdt.set(onEdt.get() || ApplicationManager.getApplication().isDispatchThread());
            if (page == 1) { started.countDown(); uninterruptible(release); }
            return new SourcePageRenderer.Page(page, 3, new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB));
        });
        try {
            PlatformTestUtil.waitWithEventsDispatching("First render starts", () -> started.getCount() == 0, 10);
            editor.showPage(2);
            release.countDown();
            await(editor, 2);
            assertFalse(onEdt.get());
            assertEquals(2, editor.requestedPage());
        } finally { release.countDown(); editor.dispose(); }
    }

    public void testDisposalDiscardsALateRender() throws Exception {
        Path path = Files.writeString(directory().resolve("controlled.pdf"), "fixture");
        VirtualFile source = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch returned = new CountDownLatch(1);
        var editor = new SourcePreviewFileEditor(getProject(), new SourcePreviewFile(source, 1), (file, page) -> {
            started.countDown(); uninterruptible(release); returned.countDown();
            return new SourcePageRenderer.Page(page, 1, new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB));
        });
        try {
            PlatformTestUtil.waitWithEventsDispatching("Render starts", () -> started.getCount() == 0, 10);
            editor.dispose();
            release.countDown();
            PlatformTestUtil.waitWithEventsDispatching("Renderer finishes", () -> returned.getCount() == 0, 10);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            assertFalse(editor.isValid());
            assertEquals(0, editor.loadedPage());
        } finally { release.countDown(); editor.dispose(); }
    }

    private Path directory() throws Exception {
        Path directory = Files.createTempDirectory("taide-preview-");
        directories.add(directory);
        return directory;
    }

    private static void await(SourcePreviewFileEditor editor, int page) {
        PlatformTestUtil.waitWithEventsDispatching("Requested source page is rendered", () -> editor.loadedPage() == page, 15);
    }

    private static void uninterruptible(CountDownLatch latch) {
        boolean waiting = true;
        while (waiting) {
            try { latch.await(); waiting = false; }
            catch (InterruptedException ignored) { /* Simulate a renderer that finishes despite cancellation. */ }
        }
    }
}
