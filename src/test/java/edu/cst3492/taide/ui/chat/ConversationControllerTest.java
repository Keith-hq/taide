package edu.cst3492.taide.ui.chat;

import edu.cst3492.taide.service.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Asynchronous lifecycle regressions: cancellation, stale callbacks and grounding. */
public final class ConversationControllerTest {
    public static void main(String[] args) throws Exception {
        var parts = AnswerParts.parse("说明\n```java\nint answer = 42;\n```\n结尾");
        check(parts.size() == 3 && !parts.get(0).code() && parts.get(1).code()
                && parts.get(1).text().equals("int answer = 42;") && !parts.get(2).code(),
                "fenced code is rendered as a separate block");
        var malformed = AnswerParts.parse("说明\n```java\ncode without a closer");
        check(malformed.size() == 2 && !malformed.getLast().code()
                && malformed.getLast().text().contains("code without a closer"), "unterminated code remains readable");
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
        }
        ConversationController disposed = new ConversationController(Runnable::run, snapshot -> { throw new AssertionError("disposed callback"); });
        disposed.close(); check(!disposed.send("已关闭", CodeContext.empty(), new Manual()), "disposed controller inert");
            System.out.println("ConversationControllerTest: 19 checks passed");
    }
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
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
}
