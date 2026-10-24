package edu.cst3492.taide.ui.chat;

import org.junit.jupiter.api.Test;

/** Runs the standalone B regressions in the normal Gradle/JUnit verification pipeline too. */
final class ChatPanelRegressionTest {
    @Test void conversationLifecycle() throws Exception { ConversationControllerTest.main(new String[0]); }
    @Test void citationPathsStayInsideMaterialsDirectory() throws Exception { CitationFilesTest.main(new String[0]); }
    @Test void nativePanelReflowsAndPreservesSourceModes() throws Exception { ChatPanelTest.main(new String[0]); }
}
