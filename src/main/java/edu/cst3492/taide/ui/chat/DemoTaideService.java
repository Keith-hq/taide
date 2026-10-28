package edu.cst3492.taide.ui.chat;

import edu.cst3492.taide.service.*;
import java.util.List;
import java.util.function.Consumer;

/** Explicitly labelled UI fixture. Never calls a network or reads course materials. */
public final class DemoTaideService implements TaideService {
    public static final String ANSWER = "Constructor injection makes dependencies explicit when an object is created.\n\n"
            + "Compared with field injection, it has three common benefits:\n"
            + "• Dependencies can be final, so objects cannot remain partially initialized.\n"
            + "• Unit tests can pass test doubles directly without starting a Spring container.\n"
            + "• Required dependencies are visible; too many parameters suggest splitting responsibilities.\n\n"
            + "Example:\n```java\npublic UserService(UserRepository repository) {\n"
            + "    this.repository = repository;\n}\n```\n\n"
            + "With a single constructor, Spring usually does not require an explicit @Autowired annotation. Check your actual reference materials.";
    public static final List<Citation> CITATIONS = List.of(
            new Citation("Demo-Spring-Core.pdf", 12, "Sample excerpt: constructor injection declares required dependencies as constructor parameters.", .91),
            new Citation("Demo-Spring-Core.pdf", 17, "Sample excerpt: pass test doubles through the constructor in unit tests.", .86));
    @Override public TaAnswer ask(String question, CodeContext context) {
        return new TaAnswer(ANSWER, CITATIONS, true, "Demo · Fixed sample answer");
    }
    @Override public void askStreaming(String question, CodeContext context, Consumer<String> onToken,
                                       Consumer<TaAnswer> onComplete, Consumer<Throwable> onError) {
        try {
            Thread.sleep(800);
            for (int i = 0; i < ANSWER.length(); i += 5) {
                if (Thread.currentThread().isInterrupted()) return;
                onToken.accept(ANSWER.substring(i, Math.min(i + 5, ANSWER.length())));
                Thread.sleep(22);
            }
            onComplete.accept(ask(question, context));
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
