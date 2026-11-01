package edu.cst3492.taide.ui;

import edu.cst3492.taide.model.ModelSetupService;
import edu.cst3492.taide.ui.chat.SetupStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Background revalidation must not turn completed setup into first-use setup. */
final class SetupStatusRegressionTest {
    private static final ProjectSetupChecker.Result READY = new ProjectSetupChecker.Result(true, true, "Ready");

    @Test void initialChecksStillShowPendingSetup() {
        SetupStatus initial = TaideToolWindowFactory.combineSetup(model(false, false, false), null);
        assertFalse(initial.checked());
        assertFalse(initial.ready());
    }

    @Test void readyModelDoesNotReplaceMissingInitialConfigurationCheck() {
        SetupStatus initial = TaideToolWindowFactory.combineSetup(model(false, true, false), null);
        assertFalse(initial.checked());
        assertFalse(initial.ready());
    }

    @Test void revalidatingAnExistingReadyModelKeepsLiveSetupReady() {
        assertTrue(TaideToolWindowFactory.combineSetup(model(true, true, false), READY).ready());
        // ModelSetupService.refresh retains ready for the same directory while checked is false.
        SetupStatus validating = TaideToolWindowFactory.combineSetup(model(false, true, false), READY);
        assertTrue(validating.checked());
        assertTrue(validating.ready(), "Settings open/close must not flash the first-use guide");
        assertTrue(TaideToolWindowFactory.combineSetup(model(true, true, false), READY).ready());
    }

    @Test void completedMissingModelCheckStillReturnsToSetup() {
        SetupStatus missing = TaideToolWindowFactory.combineSetup(model(true, false, false), READY);
        assertTrue(missing.checked());
        assertFalse(missing.ready());
        assertFalse(missing.modelReady());
    }

    @Test void completedConfigurationFailuresAreNotHiddenByThePreviousReadyModel() {
        SetupStatus noKey = TaideToolWindowFactory.combineSetup(model(true, true, false),
                new ProjectSetupChecker.Result(false, true, "Save an API key"));
        assertFalse(noKey.ready());
        assertFalse(noKey.apiReady());
        assertEquals("Save an API key", noKey.detail());
        SetupStatus staleIndex = TaideToolWindowFactory.combineSetup(model(true, true, false),
                new ProjectSetupChecker.Result(true, false, "Rebuild the knowledge base"));
        assertFalse(staleIndex.ready());
        assertFalse(staleIndex.indexReady());
        assertEquals("Rebuild the knowledge base", staleIndex.detail());
    }

    @Test void newOrPreparingModelDoesNotInheritReadiness() {
        assertFalse(TaideToolWindowFactory.combineSetup(model(false, false, false), READY).ready());
        SetupStatus downloading = TaideToolWindowFactory.combineSetup(model(false, false, true), READY);
        assertFalse(downloading.ready());
        assertTrue(downloading.preparing());
        assertTrue(downloading.detail().contains("25%"));
    }

    private static ModelSetupService.Status model(boolean checked, boolean ready, boolean preparing) {
        return new ModelSetupService.Status(Path.of("model"), checked, ready, preparing, 25, 100,
                preparing ? "Downloading model" : checked ? "Model checked" : "Validating retrieval model");
    }
}
