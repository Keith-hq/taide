package edu.cst3492.taide.ui.chat;

import edu.cst3492.taide.service.*;
import java.util.List;
import java.util.function.Consumer;

/** Explicitly labelled UI fixture. Never calls a network or reads course materials. */
public final class DemoTaideService implements TaideService {
    public static final String ANSWER = "构造器注入让依赖关系在创建对象时就明确。\n\n"
            + "与字段注入相比，它有三个常见优点：\n"
            + "• 依赖可以声明为 final，避免对象处于未完成初始化的状态。\n"
            + "• 单元测试可以直接通过构造器传入替身，无需启动 Spring 容器。\n"
            + "• 必需依赖一目了然，构造器参数过多也会提醒你拆分职责。\n\n"
            + "示例：\n```java\npublic UserService(UserRepository repository) {\n"
            + "    this.repository = repository;\n}\n```\n\n"
            + "只有一个构造器时，Spring 通常不要求显式标注 @Autowired。请以实际课程材料为准。";
    public static final List<Citation> CITATIONS = List.of(
            new Citation("Demo-Spring-Core.pdf", 12, "演示片段：构造器注入将必需依赖作为构造器参数。", .91),
            new Citation("Demo-Spring-Core.pdf", 17, "演示片段：在单元测试中通过构造器传入测试替身。", .86));
    @Override public TaAnswer ask(String question, CodeContext context) {
        return new TaAnswer(ANSWER, CITATIONS, true, "UI 演示 · 固定回答");
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
