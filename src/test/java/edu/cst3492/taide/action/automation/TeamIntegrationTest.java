package edu.cst3492.taide.action.automation;

import edu.cst3492.taide.context.SelectionRangeTest;
import edu.cst3492.taide.ui.chat.CitationFilesTest;
import edu.cst3492.taide.ui.chat.ConversationControllerTest;
import edu.cst3492.taide.ui.settings.SettingsValidationTest;
import org.junit.jupiter.api.Test;

class TeamIntegrationTest {
    @Test
    void checksSelectionRanges() {
        SelectionRangeTest.main(new String[0]);
    }

    @Test
    void checksConversationLifecycle() throws Exception {
        ConversationControllerTest.main(new String[0]);
    }

    @Test
    void checksCitationPaths() throws Exception {
        CitationFilesTest.main(new String[0]);
    }

    @Test
    void checksSettingsValidation() {
        SettingsValidationTest.main(new String[0]);
    }
}
