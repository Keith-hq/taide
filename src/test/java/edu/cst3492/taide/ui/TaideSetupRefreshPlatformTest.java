package edu.cst3492.taide.ui;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import edu.cst3492.taide.model.ModelSetupService;
import edu.cst3492.taide.ui.chat.ChatPanel;
import edu.cst3492.taide.ui.settings.SettingsSnapshot;
import edu.cst3492.taide.ui.settings.TaideUiSettings;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;

/** Exercises the real IDE adapter without downloading a model or contacting an API. */
public final class TaideSetupRefreshPlatformTest extends BasePlatformTestCase {
    public void testSettingsRecheckKeepsConfirmedSetupUntilTheWorkerReturns() throws Exception {
        ModelSetupService models = ModelSetupService.getInstance();
        Field modelStatus = ModelSetupService.class.getDeclaredField("status");
        modelStatus.setAccessible(true);
        Object originalStatus = modelStatus.get(models);
        Class<?> adapter = Class.forName(TaideToolWindowFactory.class.getName() + "$IdeHost");
        var constructor = adapter.getDeclaredConstructor(Project.class);
        constructor.setAccessible(true);
        Object adapterInstance = constructor.newInstance(getProject());
        ChatPanel.Host host = (ChatPanel.Host) adapterInstance;
        TaideUiSettings settings = TaideUiSettings.getInstance(getProject());
        SettingsSnapshot originalSettings = settings.snapshot();
        ChatPanel panel = null;
        try {
            settings.update(new SettingsSnapshot(originalSettings.provider(), originalSettings.baseUrl(),
                    originalSettings.model(), originalSettings.courseDirectory(), false));
            modelStatus.set(models, new ModelSetupService.Status(models.directory(), true, true, false, 100, 100, "Ready"));
            var config = new ProjectSetupChecker.Result(true, true, "Ready");
            Field configuration = adapter.getDeclaredField("configuration");
            configuration.setAccessible(true);
            configuration.set(adapterInstance, config);
            var publish = adapter.getDeclaredMethod("publishSetup");
            publish.setAccessible(true);
            publish.invoke(adapterInstance);
            assertTrue(host.setupStatus().ready());
            panel = new ChatPanel(host);
            JComponent guide = findGuide(panel);
            boolean originallyVisible = guide.isVisible();
            assertFalse("A configured live chat starts without the first-use guide", originallyVisible);
            var recheck = adapter.getDeclaredMethod("checkConfiguration", boolean.class);
            recheck.setAccessible(true);
            for (int repeat = 0; repeat < 3; repeat++) {
                recheck.invoke(adapterInstance, false);
                assertSame("A background recheck must not clear confirmed configuration", config, configuration.get(adapterInstance));
                assertTrue(host.setupStatus().ready());
                panel.refreshMode();
                assertEquals("Guide visibility must not bounce during revalidation", originallyVisible, guide.isVisible());
            }
            modelStatus.set(models, new ModelSetupService.Status(models.directory(), false, true, false, 100, 100, "Validating"));
            publish.invoke(adapterInstance);
            panel.refreshSetupStatus();
            assertTrue(host.setupStatus().ready());
            assertEquals(originallyVisible, guide.isVisible());
            modelStatus.set(models, new ModelSetupService.Status(models.directory(), true, true, false, 100, 100, "Ready"));
            configuration.set(adapterInstance, new ProjectSetupChecker.Result(false, false, "Reconfigure API and files"));
            publish.invoke(adapterInstance);
            panel.refreshSetupStatus();
            assertFalse(host.setupStatus().ready());
            assertTrue("A completed failed check still restores actionable setup", guide.isVisible());
        } finally {
            ((AutoCloseable) adapterInstance).close();
            if (panel != null) panel.close();
            modelStatus.set(models, originalStatus);
            settings.update(originalSettings);
        }
    }

    private static JComponent findGuide(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JComponent component && "first-use-guide".equals(component.getName())) return component;
            if (child instanceof Container nested) {
                JComponent found = findGuide(nested);
                if (found != null) return found;
            }
        }
        return null;
    }
}
