package edu.cst3492.taide.model;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import edu.cst3492.taide.service.TaidePaths;
import edu.cst3492.taide.ui.settings.TaideSettingsConfigurable;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Future;

/** Real application-service registration and setup UI, with no network or large model fixture. */
public final class ModelSetupPlatformTest extends BasePlatformTestCase {
    private ModelSetupService service;
    private ModelSetupService.Location original;
    private String override;

    @Override protected void setUp() throws Exception {
        super.setUp();
        service = ModelSetupService.getInstance();
        original = service.getState();
        override = System.getProperty("taide.model.dir");
    }

    @Override protected void tearDown() throws Exception {
        try {
            service.loadState(original);
            if (override == null) System.clearProperty("taide.model.dir");
            else System.setProperty("taide.model.dir", override);
        } finally { super.tearDown(); }
    }

    public void testExplicitSelectionOverridesDeveloperLocationAndStateIsDetached() {
        Path developer = Path.of(System.getProperty("java.io.tmpdir"), "taide-developer-model");
        Path selected = developer.resolveSibling("taide-selected-model");
        System.setProperty("taide.model.dir", developer.toString());
        service.loadState(new ModelSetupService.Location());
        assertEquals(developer, TaidePaths.modelDir());
        long revision = service.revision();
        var saved = new ModelSetupService.Location(); saved.directory = selected.toString();
        service.loadState(saved);
        saved.directory = "unrelated";
        assertEquals(selected, TaidePaths.modelDir());
        service.getState().directory = "also-unrelated";
        assertEquals(selected, service.directory());
        assertTrue(service.revision() > revision);
        var managed = new ModelSetupService.Location(); managed.managed = true;
        service.loadState(managed);
        assertEquals(ModelSetupService.managedDirectory(), TaidePaths.modelDir());
    }

    public void testInvalidLocalDirectoryDoesNotReplaceSelection() throws Exception {
        Path empty = Files.createTempDirectory("taide-invalid-model-");
        Path previous = service.directory();
        long revision = service.revision();
        try {
            Future<Boolean> result = ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try { service.useDirectory(empty); return false; }
                catch (IOException expected) { return true; }
            });
            assertTrue(result.get());
            assertEquals(previous, service.directory());
            assertEquals(revision, service.revision());
        } finally { Files.delete(empty); }
    }

    public void testMissingModelFinishesCheckingAndOffersPreparationBeforeRebuild() throws Exception {
        Path empty = Files.createTempDirectory("taide-missing-model-");
        TaideSettingsConfigurable configurable = new TaideSettingsConfigurable(getProject());
        try {
            var state = new ModelSetupService.Location(); state.directory = empty.toString();
            service.loadState(state);
            JComponent form = configurable.createComponent();
            PlatformTestUtil.waitWithEventsDispatching("Local model check completes in settings",
                    () -> service.status().checked() && button(form, "下载并配置").isEnabled(), 10);
            assertFalse(service.status().ready());
            assertFalse(service.status().preparing());
            assertTrue(button(form, "下载并配置").isEnabled());
            assertTrue(button(form, "使用已有目录…").isEnabled());
            assertFalse(button(form, "Rebuild Knowledge Base").isEnabled());
            assertTrue(service.status().detail().contains("首次使用"));
            capture(form, 640);
            capture(form, 390);
        } finally { configurable.disposeUIResources(); Files.delete(empty); }
    }

    private static void capture(JComponent form, int width) throws IOException {
        form.setSize(width, 760);
        layout(form); layout(form);
        BufferedImage image = new BufferedImage(width, 760, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { form.printAll(graphics); } finally { graphics.dispose(); }
        Path output = Path.of("build", "b-verification", "screenshots", "settings-" + width + "-setup.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(image, "png", output.toFile());
    }

    private static void layout(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) if (child instanceof Container container) layout(container);
    }

    private static JButton button(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) return button;
            if (child instanceof Container container) {
                JButton found = findButton(container, text); if (found != null) return found;
            }
        }
        throw new AssertionError("Missing button: " + text);
    }

    private static JButton findButton(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) return button;
            if (child instanceof Container container) {
                JButton found = findButton(container, text); if (found != null) return found;
            }
        }
        return null;
    }
}
