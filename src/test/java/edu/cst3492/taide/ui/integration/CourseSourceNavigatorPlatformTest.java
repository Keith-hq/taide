package edu.cst3492.taide.ui.integration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import edu.cst3492.taide.rag.RetrievedChunk;
import edu.cst3492.taide.service.Citation;
import edu.cst3492.taide.ui.settings.SettingsSnapshot;
import edu.cst3492.taide.ui.settings.TaideUiSettings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Exercises the registered navigator, actual filesystem/VFS, and EDT delivery without a PDF viewer. */
public final class CourseSourceNavigatorPlatformTest extends BasePlatformTestCase {
    private final List<Path> directories = new ArrayList<>();
    private CourseSourceNavigator navigator;
    private TaideUiSettings settings;
    private SettingsSnapshot original;

    @Override protected void setUp() throws Exception {
        super.setUp();
        navigator = CourseSourceNavigator.getInstance(getProject());
        settings = TaideUiSettings.getInstance(getProject());
        original = settings.snapshot();
    }

    @Override protected void tearDown() throws Exception {
        try {
            settings.update(original);
            for (Path directory : directories) FileUtil.delete(directory.toFile());
        } finally { super.tearDown(); }
    }

    public void testReviewSnapshotKeepsOriginalDirectoryAfterSettingsChange() throws Exception {
        Path before = directory();
        Path after = directory();
        Path expected = source(before, "week1/lecture.pdf");
        source(after, "week1/lecture.pdf");
        courseDirectory(before);
        var ui = new RecordingUi();
        var opener = navigator.snapshot(ui);
        courseDirectory(after);

        opener.accept(chunk("week1/lecture.pdf", 7));
        await(ui);

        assertEquals(expected.toRealPath(), ui.opened.toNioPath().toRealPath());
        assertTrue(ui.message.contains("第 7 页 / 张"));
        assertTrue(ui.message.contains("手动定位"));
    }

    public void testMissingOriginalSourceCannotRedirectToNewDirectory() throws Exception {
        Path before = directory();
        Path after = directory();
        Path removed = source(before, "lecture.pdf");
        source(after, "lecture.pdf");
        courseDirectory(before);
        var ui = new RecordingUi();
        var opener = navigator.snapshot(ui);
        Files.delete(removed);
        courseDirectory(after);

        opener.accept(chunk("lecture.pdf", 3));
        await(ui);

        assertNull(ui.opened);
        assertEquals("无法打开引用", ui.title);
    }

    public void testNavigationCompletesWhileReviewIsModal() throws Exception {
        Path root = directory();
        Path expected = source(root, "slides.pptx");
        var ui = new RecordingUi();
        Object review = new Object();
        LaterInvocator.enterModal(review);
        try {
            navigator.open(citation("slides.pptx", 11), root.toString(), ui);
            await(ui);
            assertEquals(expected.toRealPath(), ui.opened.toNioPath().toRealPath());
            assertTrue(ui.message.contains("第 11 页 / 张"));
        } finally { LaterInvocator.leaveModal(review); }
    }

    public void testBackgroundCallerStillOpensAndNotifiesOnEdt() throws Exception {
        Path root = directory();
        source(root, "lecture.pdf");
        var ui = new RecordingUi();
        var submitted = ApplicationManager.getApplication().executeOnPooledThread(
                () -> navigator.open(citation("lecture.pdf", 2), root.toString(), ui));
        await(ui);
        submitted.get();
        assertNotNull(ui.opened);
    }

    public void testMissingDirectoryAndInvalidPageHaveReadableFeedback() throws Exception {
        var missing = new RecordingUi();
        navigator.open(citation("lecture.pdf", 1), "", missing);
        await(missing);
        assertNull(missing.opened);
        assertEquals("未配置课件目录", missing.title);
        assertTrue(missing.message.contains("Settings"));

        var invalidPage = new RecordingUi();
        navigator.open(citation("lecture.pdf", 0), directory().toString(), invalidPage);
        await(invalidPage);
        assertNull(invalidPage.opened);
        assertTrue(invalidPage.message.contains("页码"));
    }

    public void testUnsafeMissingAmbiguousAndUnsupportedSourcesAreRejected() throws Exception {
        Path root = directory();
        Path outside = source(directory(), "outside.pdf");
        source(root, "notes.txt");
        source(root, "week1/duplicate.pdf");
        source(root, "week2/duplicate.pdf");
        for (String name : List.of("../outside.pdf", outside.toString(), "https://example.com/a.pdf",
                "notes.txt", "duplicate.pdf", "missing.pdf")) {
            var ui = new RecordingUi();
            navigator.open(citation(name, 1), root.toString(), ui);
            await(ui);
            assertNull(name, ui.opened);
            assertEquals(name, "无法打开引用", ui.title);
        }
    }

    public void testInvalidConfiguredPathHasReadableFeedback() throws Exception {
        var ui = new RecordingUi();
        navigator.open(citation("lecture.pdf", 1), "invalid\0directory", ui);
        await(ui);
        assertNull(ui.opened);
        assertTrue(ui.message.contains("目录无效"));
    }

    public void testEditorFailureHasReadableFeedbackWithoutExceptionDetails() throws Exception {
        Path root = directory();
        source(root, "lecture.pdf");
        RecordingUi ui = new RecordingUi() {
            @Override public void open(VirtualFile file) {
                ApplicationManager.getApplication().assertIsDispatchThread();
                throw new IllegalStateException("private implementation detail");
            }
        };
        navigator.open(citation("lecture.pdf", 1), root.toString(), ui);
        await(ui);
        assertEquals("无法打开引用", ui.title);
        assertTrue(ui.message.contains("文件关联"));
        assertFalse(ui.message.contains("private implementation detail"));
    }

    private Path directory() throws Exception {
        Path directory = Files.createTempDirectory("taide-citation-platform-");
        directories.add(directory);
        return directory;
    }

    private static Path source(Path root, String relative) throws Exception {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        // File selection is tested here; PDF/PPTX rendering belongs to the user's IDE viewer.
        return Files.writeString(path, "navigation fixture");
    }

    private void courseDirectory(Path root) {
        settings.update(new SettingsSnapshot(original.provider(), original.baseUrl(), original.model(),
                root.toString(), original.demoMode()));
    }

    private static Citation citation(String name, int page) { return new Citation(name, page, "excerpt", 0.84); }
    private static RetrievedChunk chunk(String name, int page) {
        return new RetrievedChunk("course-document", name, page, "excerpt", 0.84);
    }

    private static void await(RecordingUi ui) {
        PlatformTestUtil.waitWithEventsDispatching("Citation navigation completes", () -> ui.title != null, 10);
    }

    private static class RecordingUi implements CourseSourceNavigator.Ui {
        private VirtualFile opened;
        private String message;
        private String title;

        @Override public void open(VirtualFile file) {
            ApplicationManager.getApplication().assertIsDispatchThread();
            opened = file;
        }
        @Override public void notice(String message, String title) {
            ApplicationManager.getApplication().assertIsDispatchThread();
            this.message = message;
            this.title = title;
        }
    }
}
