package edu.cst3492.taide.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import edu.cst3492.taide.action.AskTaideAction;
import edu.cst3492.taide.service.TaideService;
import edu.cst3492.taide.service.TaideServiceImpl;
import edu.cst3492.taide.ui.integration.KnowledgeBaseController;
import edu.cst3492.taide.ui.integration.ProjectKnowledgeBaseController;
import edu.cst3492.taide.ui.settings.SettingsSnapshot;
import edu.cst3492.taide.ui.settings.TaideUiSettings;

/** Exercises B's real plugin descriptor and platform state without model files or API calls. */
public final class BIntegrationPlatformTest extends BasePlatformTestCase {
    public void testRealServicesAndEditorActionAreRegistered() {
        TaideService chat = getProject().getService(TaideService.class);
        assertInstanceOf(chat, TaideServiceImpl.class);
        assertSame(chat, getProject().getService(TaideService.class));
        assertInstanceOf(getProject().getService(KnowledgeBaseController.class), ProjectKnowledgeBaseController.class);
        assertInstanceOf(ActionManager.getInstance().getAction("taide.askAboutCode"), AskTaideAction.class);
    }

    public void testPreferencesReturnDetachedStateAndInvalidateConfiguration() {
        TaideUiSettings settings = TaideUiSettings.getInstance(getProject());
        SettingsSnapshot original = settings.snapshot();
        long revision = settings.revision();
        var changed = new SettingsSnapshot(SettingsSnapshot.AGI, "https://model.example/v1", "course-model", "", false);
        settings.update(changed);
        assertTrue(settings.revision() > revision);
        assertEquals(changed, settings.snapshot());
        var serialized = settings.getState();
        serialized.baseUrl = "https://unrelated.example";
        assertEquals(changed.baseUrl(), settings.snapshot().baseUrl());
        settings.update(original);
    }
}
