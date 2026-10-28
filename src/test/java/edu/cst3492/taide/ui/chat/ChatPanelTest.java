package edu.cst3492.taide.ui.chat;

import edu.cst3492.taide.service.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;

/** Headless regressions for source labels, citation actions and dock-width text reflow. */
public final class ChatPanelTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        checks = 0;
        var host = new TestHost();
        ChatPanel[] holder = new ChatPanel[1];
        SwingUtilities.invokeAndWait(() -> { holder[0] = new ChatPanel(host); holder[0].setSize(768, 900); layout(holder[0]); });
        ChatPanel panel = holder[0];
        try {
            panel.conversation().send("问题", CodeContext.empty(), host.service(), true, host.courseDirectory());
            await(panel);
            SwingUtilities.invokeAndWait(() -> {
                panel.refreshMode(); layout(panel);
                check(labels(panel).stream().anyMatch(s -> s.contains("Sample citation")), "demo citations labelled as examples");
                host.demo = false; panel.refreshMode(); layout(panel);
                check(labels(panel).stream().noneMatch(s -> s.contains("Similarity")), "changing mode does not add fake scores to history");
                citationButtons(panel).getFirst().doClick();
                check(host.opened.equals(List.of(true)), "historical demo click retains demonstration source");

                int wide = messageHeight(panel);
                panel.setSize(390, 900); layout(panel);
                check(messageHeight(panel) > wide, "answer height grows after narrowing the dock");
                checkTextHeights(panel);
                panel.setSize(280, 900); layout(panel); checkTextHeights(panel);
            });
            panel.conversation().send("真实问题", CodeContext.empty(), host.service(), false, host.courseDirectory());
            await(panel);
            SwingUtilities.invokeAndWait(() -> {
                panel.refreshMode(); layout(panel);
                check(labels(panel).stream().anyMatch(s -> s.contains("Similarity 80%")), "real citations retain retrieval scores");
                host.demo = true; host.directory = "materials-B"; panel.refreshMode(); layout(panel);
                var citations = citationButtons(panel);
                citations.getLast().doClick();
                check(host.opened.equals(List.of(true, false)), "real citation action stays real after switching to demo mode");
                check(host.openedRoots.equals(List.of("materials-A", "materials-A")), "historical citation keeps its original materials directory after settings change");
            });
        } finally { SwingUtilities.invokeAndWait(panel::close); }
        setupGuide();
        System.out.println("ChatPanelTest: " + checks + " checks passed");
    }
    private static void setupGuide() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TestHost host = new TestHost(); host.setup = SetupStatus.unknown();
            ChatPanel panel = new ChatPanel(host);
            try {
                panel.setSize(390, 900); layout(panel);
                check(labels(panel).stream().anyMatch(s -> s.contains("checking")), "first use shows actual pending checks");
                check(!button(panel, "Download model").isEnabled(), "download waits for existing model check");
                host.setup = new SetupStatus(true, false, false, false, false, "Model not ready");
                panel.refreshMode(); layout(panel);
                button(panel, "Download model").doClick(); button(panel, "Settings").doClick(); button(panel, "Refresh").doClick();
                check(host.prepares == 1 && host.settings == 1 && host.refreshes == 1, "setup actions invoke host operations");
                check(!button(panel, "Start live chat").isEnabled(), "live switch requires all setup steps");
                host.setup = new SetupStatus(true, false, true, false, true, "Downloading model 32%");
                panel.refreshMode(); layout(panel);
                check(!button(panel, "Preparing…").isEnabled(), "preparing blocks duplicate model downloads");
                check(labels(panel).stream().anyMatch(s -> s.equals("Downloading model 32%")), "download detail preserves true progress");
                host.setup = new SetupStatus(true, true, true, true, false, "");
                panel.refreshMode(); layout(panel); button(panel, "Start live chat").doClick();
                check(host.liveSwitches == 1, "ready demo can explicitly switch to real service");
                host.demo = false; panel.refreshMode(); layout(panel);
                check(!guide(panel).isVisible(), "completed live setup releases conversation space");
                host.setup = new SetupStatus(true, true, true, false, false, "Materials have changed. Rebuild the knowledge base.");
                panel.refreshMode(); layout(panel);
                check(guide(panel).isVisible() && labels(panel).stream().anyMatch(s -> s.contains("rebuild index")), "stale index returns to actionable setup");
            } finally { panel.close(); }
        });
    }
    private static JComponent guide(ChatPanel panel) {
        return descendants(panel).stream().filter(JComponent.class::isInstance).map(JComponent.class::cast)
                .filter(component -> "first-use-guide".equals(component.getName())).findFirst().orElseThrow();
    }
    private static JButton button(ChatPanel panel, String title) {
        return descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> title.equals(button.getText())).findFirst().orElseThrow();
    }
    private static void await(ChatPanel panel) throws InterruptedException {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (panel.conversation().snapshot().busy() && System.nanoTime() < deadline) Thread.sleep(5);
        if (panel.conversation().snapshot().busy()) throw new AssertionError("fixture did not complete");
    }
    private static List<Component> descendants(Container container) {
        var result = new ArrayList<Component>();
        for (Component child : container.getComponents()) {
            result.add(child);
            if (child instanceof Container nested) result.addAll(descendants(nested));
        }
        return result;
    }
    private static List<String> labels(Container container) {
        return descendants(container).stream()
                .filter(component -> component instanceof JLabel || component instanceof JTextArea)
                .map(component -> component instanceof JLabel label ? label.getText() : ((JTextArea) component).getText()).toList();
    }
    private static List<JButton> citationButtons(Container container) {
        return descendants(container).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> button.getAccessibleContext().getAccessibleName().startsWith("Open ")).toList();
    }
    private static int messageHeight(Container panel) {
        return descendants(panel).stream().filter(Scrollable.class::isInstance).filter(JPanel.class::isInstance)
                .mapToInt(component -> component.getPreferredSize().height).max().orElseThrow();
    }
    private static void checkTextHeights(Container panel) {
        check(descendants(panel).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast)
                .filter(area -> !area.isEditable()).allMatch(area -> area.getHeight() >= area.getPreferredSize().height),
                "wrapped answer and code text stay within their measured rows at " + panel.getWidth() + "px");
    }
    private static void layout(Container parent) {
        for (int pass = 0; pass < 5; pass++) layoutOnce(parent);
    }
    private static void layoutOnce(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container nested) layoutOnce(nested);
    }
    private static void check(boolean condition, String label) {
        checks++; if (!condition) throw new AssertionError(label);
    }
    private static final class TestHost implements ChatPanel.Host {
        boolean demo = true;
        String directory = "materials-A";
        SetupStatus setup;
        int prepares, settings, refreshes, liveSwitches;
        final List<Boolean> opened = new ArrayList<>();
        final List<String> openedRoots = new ArrayList<>();
        public boolean demoMode() { return demo; }
        public String courseDirectory() { return directory; }
        public SetupStatus setupStatus() { return setup; }
        public void prepareModel() { prepares++; }
        public void refreshSetup() { refreshes++; }
        public void useLiveMode() { liveSwitches++; }
        public void settings() { settings++; }
        public void openCitation(Citation citation) { throw new AssertionError("source mode was lost"); }
        public void openCitation(Citation citation, boolean demonstration) { opened.add(demonstration); }
        public void openCitation(Citation citation, boolean demonstration, String materialsDirectory) {
            opened.add(demonstration); openedRoots.add(materialsDirectory);
        }
        public void copy(String text) { }
        public TaideService service() {
            return new TaideService() {
                public TaAnswer ask(String question, CodeContext context) { throw new UnsupportedOperationException(); }
                public void askStreaming(String question, CodeContext context, Consumer<String> token,
                                         Consumer<TaAnswer> done, Consumer<Throwable> error) {
                    done.accept(new TaAnswer(DemoTaideService.ANSWER, List.of(new Citation("Course.pdf", 2, "课程依据", .8)), true, "fixture"));
                }
            };
        }
    }
}
