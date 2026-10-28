import edu.cst3492.taide.service.*;
import edu.cst3492.taide.ui.chat.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Renders the actual native panel without opening or controlling a desktop window. */
public final class NativePreview {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]); Files.createDirectories(output);
        for (int width : new int[]{390, 420, 768, 1440}) render(output, width, 900, "answer");
        render(output, 1366, 768, "answer"); render(output, 420, 900, "welcome");
        render(output, 420, 900, "ungrounded"); render(output, 420, 900, "error");
        for (int width : new int[]{390, 768, 1440}) render(output, width, 900, "setup");
        render(output, 390, 900, "setup-ready"); render(output, 420, 900, "setup-preparing");
        render(output, 390, 900, "setup-error");
        render(output, 390, 900, "live-welcome"); render(output, 420, 900, "live-answer");
        System.out.println("NativePreview: sixteen Swing screenshots rendered");
    }
    static void render(Path out, int width, int height, String state) throws Exception {
        ChatPanel[] holder = new ChatPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            ChatPanel panel = new ChatPanel(new ChatPanel.Host() {
                public TaideService service() { return new DemoTaideService(); }
                public boolean demoMode() { return !state.startsWith("live"); }
                public SetupStatus setupStatus() {
                    return switch (state) {
                        case "setup" -> new SetupStatus(true, false, false, false, false, "About 90 MB to download. Or choose a local model in Settings.");
                        case "setup-ready" -> new SetupStatus(true, true, true, true, false, "");
                        case "setup-preparing" -> new SetupStatus(true, false, false, false, true, "Downloading retrieval model · 32%");
                        case "setup-error" -> new SetupStatus(true, false, true, false, false, "Download failed. Check your proxy and retry, or choose a local model.");
                        default -> null;
                    };
                }
                public void settings() {} public void openCitation(Citation c) {} public void copy(String text) {}
            });
            holder[0] = panel; panel.setSize(width, height); layout(panel);
            if (!state.endsWith("welcome") && !state.startsWith("setup")) panel.conversation().send("Why use constructor injection?", new CodeContext(
                    "src/main/java/UserService.java", "JAVA", "private UserRepository repository;", 8, 12, "UserService"),
                    new TaideService() {
                        public TaAnswer ask(String q, CodeContext c) { return null; }
                        public void askStreaming(String q, CodeContext c, Consumer<String> token, Consumer<TaAnswer> done, Consumer<Throwable> error) {
                            if (state.equals("error")) { error.accept(new IllegalStateException("Network connection failed. Check Settings and retry.")); return; }
                            String answer = state.equals("ungrounded") ? "No matching source found. In general, constructor injection makes dependencies explicit and helps you write unit tests without starting a Spring container." : DemoTaideService.ANSWER;
                            done.accept(new TaAnswer(answer, DemoTaideService.CITATIONS, !state.equals("ungrounded"),
                                    state.startsWith("live") ? "Preview fixture" : "Demo · Fixed sample answer"));
                        }
                    }, !state.startsWith("live"));
        });
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (holder[0].conversation().snapshot().busy() && System.nanoTime() < deadline) Thread.sleep(10);
        SwingUtilities.invokeAndWait(() -> { holder[0].refreshMode(); for (int i = 0; i < 5; i++) layout(holder[0]); });
        SwingUtilities.invokeAndWait(() -> {});
        SwingUtilities.invokeAndWait(() -> {
            ChatPanel panel = holder[0]; for (int i = 0; i < 5; i++) layout(panel);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON); panel.printAll(g); g.dispose();
            try { ImageIO.write(image, "png", out.resolve("native-" + width + "-" + state + ".png").toFile()); }
            catch (Exception failure) { throw new RuntimeException(failure); }
            panel.close();
        });
    }
    static void layout(Container parent) { parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container nested) layout(nested); }
}
