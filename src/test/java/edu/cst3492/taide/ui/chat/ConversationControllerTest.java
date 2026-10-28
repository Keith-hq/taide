package edu.cst3492.taide.ui.chat;

import edu.cst3492.taide.service.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Asynchronous lifecycle regressions: cancellation, stale callbacks and grounding. */
public final class ConversationControllerTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        checks = 0;
        var parts = AnswerParts.parse("说明\n```java\nint answer = 42;\n```\n结尾");
        check(parts.size() == 3 && !parts.get(0).code() && parts.get(1).code()
                && parts.get(1).text().equals("int answer = 42;") && !parts.get(2).code(),
                "fenced code is rendered as a separate block");
        var malformed = AnswerParts.parse("说明\n```java\ncode without a closer");
        check(malformed.size() == 2 && malformed.getLast().code()
                && malformed.getLast().text().equals("code without a closer"), "unfinished streaming code preserves its format");
        var nested = AnswerParts.parse("````markdown\n```java\nint x;\n```\n````\nend");
        check(nested.size() == 2 && nested.getFirst().code() && nested.getFirst().text().contains("```java"),
                "shorter nested fence does not close outer code block");
        var tilde = AnswerParts.parse("~~~java\nint x;\n~~~");
        check(tilde.size() == 1 && tilde.getFirst().code(), "tilde code fences supported");
        try (ConversationController controller = new ConversationController(Runnable::run, snapshot -> {})) {
            Manual backend = new Manual();
            check(!controller.send(" ", CodeContext.empty(), backend), "blank question rejected");
            check(controller.send("问题", CodeContext.empty(), backend), "request accepted"); backend.await();
            check(!controller.send("重复", CodeContext.empty(), backend), "overlapping send rejected");
            backend.token.accept("第一段"); backend.token.accept("第二段");
            check(controller.snapshot().messages().getLast().text().equals("第一段第二段"), "stream order preserved");
            var fixture = List.of(new Citation("notes.pdf", 2, "test evidence", .8));
            backend.done.accept(new TaAnswer("最终回答", fixture, false, "test"));
            var finalMessage = controller.snapshot().messages().getLast();
            check(!controller.snapshot().busy(), "completion releases input");
            check(finalMessage.text().equals("最终回答"), "complete answer is authoritative");
            check(finalMessage.citations().isEmpty(), "ungrounded answer never exposes citations");

            Manual cancelled = new Manual(); controller.send("取消", CodeContext.empty(), cancelled); cancelled.await();
            cancelled.token.accept("保留片段"); controller.cancel();
            check(controller.snapshot().messages().getLast().status() == ConversationController.Status.CANCELLED, "cancel state");
            cancelled.done.accept(new TaAnswer("不应出现", fixture, true, "test"));
            check(controller.snapshot().messages().getLast().text().equals("保留片段"), "late completion after cancel ignored");

            Manual cleared = new Manual(); controller.send("清空", CodeContext.empty(), cleared); cleared.await(); controller.clear();
            cleared.token.accept("过期消息"); cleared.error.accept(new RuntimeException("late error"));
            check(controller.snapshot().messages().isEmpty(), "clear isolates obsolete callbacks");

            Manual failed = new Manual(); controller.send("失败", CodeContext.empty(), failed); failed.await();
            failed.error.accept(new RuntimeException("Bearer sk-secret"));
            check(!controller.snapshot().notice().contains("secret"), "credential-like errors not echoed");
            check(controller.snapshot().messages().getLast().status() == ConversationController.Status.ERROR, "readable error state");
            Manual retry = new Manual(); check(controller.retry(retry), "retry starts new request"); retry.await();
            check(retry.question.equals("失败"), "retry preserves original question");
            retry.done.accept(new TaAnswer("成功", fixture, true, "test"));
            check(controller.snapshot().messages().getLast().citations().size() == 1, "grounded citations preserved");
            check(!controller.send("x".repeat(16001), CodeContext.empty(), retry), "oversized question rejected");

            Manual unsafe = new Manual(); controller.send("报错", CodeContext.empty(), unsafe); unsafe.await();
            unsafe.error.accept(new RuntimeException("api_key=private-value"));
            check(!controller.snapshot().notice().contains("private-value"), "unknown API key formats never echoed");

            Manual auth = new Manual(); controller.send("认证", CodeContext.empty(), auth); auth.await();
            auth.error.accept(new RuntimeException("Model API error 401: api_key=private-value"));
            check(controller.snapshot().notice().contains("authentication failed") && !controller.snapshot().notice().contains("private-value"),
                    "authentication failures show a safe actionable message");
            for (String suffix : List.of(": private-detail", ". Check local setup.")) {
                Manual load = new Manual(); controller.send("载入失败", CodeContext.empty(), load); load.await();
                load.error.accept(new RuntimeException("TAIDE failed to load its knowledge base or model" + suffix));
                check(controller.snapshot().notice().contains("rebuild the knowledge base"), "both model loading error formats provide repair guidance");
            }

            Manual longStream = new Manual(); controller.send("过长", CodeContext.empty(), longStream); longStream.await();
            longStream.token.accept("x".repeat(ConversationController.MAX_ANSWER + 1));
            check(!controller.snapshot().busy() && controller.snapshot().messages().getLast().text().length() == ConversationController.MAX_ANSWER,
                    "an oversized token stops at the display limit immediately");
            check(controller.snapshot().messages().getLast().status() == ConversationController.Status.CANCELLED, "oversized token marks answer incomplete");

            Manual longFinal = new Manual(); controller.send("过长最终回答", CodeContext.empty(), longFinal); longFinal.await();
            longFinal.done.accept(new TaAnswer("x".repeat(ConversationController.MAX_ANSWER + 1), List.of(), false, "test"));
            check(controller.snapshot().notice().contains("truncated"), "oversized final answer reports truncation");

            Manual demo = new Manual(); controller.send("示例", CodeContext.empty(), demo, true); demo.await();
            demo.done.accept(new TaAnswer("示例回答", fixture, true, "fixture"));
            Manual real = new Manual(); controller.send("真实", CodeContext.empty(), real, false); real.await();
            real.done.accept(new TaAnswer("真实回答", fixture, true, "model"));
            var messages = controller.snapshot().messages();
            check(messages.get(messages.size() - 3).demo() && !messages.getLast().demo(), "historical source mode survives later mode changes");
        }
        var deliveries = new ConcurrentLinkedQueue<Runnable>();
        var rendered = new java.util.ArrayList<ConversationController.Snapshot>();
        try (ConversationController queued = new ConversationController(deliveries::add, rendered::add)) {
            Manual burst = new Manual(); queued.send("快速流", CodeContext.empty(), burst); burst.await();
            for (int i = 0; i < 100; i++) burst.token.accept("x");
            check(deliveries.size() == 1, "streaming burst queues only one UI delivery");
            queued.clear();
            deliveries.remove().run();
            check(rendered.size() == 1 && rendered.getFirst().messages().isEmpty(), "queued delivery reads latest clear state, never stale tokens");
            Manual closing = new Manual(); queued.send("关闭", CodeContext.empty(), closing); closing.await(); queued.close();
            deliveries.remove().run();
            check(rendered.size() == 1, "queued UI delivery after disposal is ignored");
        }
        try (ConversationController running = new ConversationController(Runnable::run, snapshot -> { })) {
            Blocking backend = new Blocking(); running.send("耗时请求", CodeContext.empty(), backend); backend.await();
            running.cancel();
            check(backend.interrupted.await(3, TimeUnit.SECONDS), "stop interrupts the calling service thread");
            Manual next = new Manual(); running.send("重新提问", CodeContext.empty(), next); next.await();
            next.done.accept(new TaAnswer("可以继续", List.of(), false, "test"));
            check(running.snapshot().messages().getLast().text().equals("可以继续"), "cancelled worker accepts a subsequent request");
            Blocking closing = new Blocking(); running.send("关闭耗时请求", CodeContext.empty(), closing); closing.await(); running.close();
            check(closing.interrupted.await(3, TimeUnit.SECONDS), "disposal interrupts the service thread");
        }
        ConversationController disposed = new ConversationController(Runnable::run, snapshot -> { throw new AssertionError("disposed callback"); });
        disposed.close(); check(!disposed.send("已关闭", CodeContext.empty(), new Manual()), "disposed controller inert");
        System.out.println("ConversationControllerTest: " + checks + " checks passed");
    }
    static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    static final class Manual implements TaideService {
        volatile Consumer<String> token;
        volatile Consumer<TaAnswer> done;
        volatile Consumer<Throwable> error;
        volatile String question;
        final CountDownLatch ready = new CountDownLatch(1);
        public TaAnswer ask(String q, CodeContext c) { throw new UnsupportedOperationException(); }
        public void askStreaming(String q, CodeContext c, Consumer<String> t, Consumer<TaAnswer> d, Consumer<Throwable> e) {
            question = q; token = t; done = d; error = e; ready.countDown();
        }
        void await() throws InterruptedException { if (!ready.await(3, TimeUnit.SECONDS)) throw new AssertionError("worker did not start"); }
    }
    static final class Blocking implements TaideService {
        final CountDownLatch ready = new CountDownLatch(1), interrupted = new CountDownLatch(1);
        public TaAnswer ask(String question, CodeContext context) { throw new UnsupportedOperationException(); }
        public void askStreaming(String question, CodeContext context, Consumer<String> token,
                                 Consumer<TaAnswer> done, Consumer<Throwable> error) {
            ready.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException stopped) { interrupted.countDown(); Thread.currentThread().interrupt(); error.accept(stopped); }
        }
        void await() throws InterruptedException { if (!ready.await(3, TimeUnit.SECONDS)) throw new AssertionError("worker did not start"); }
    }
}
