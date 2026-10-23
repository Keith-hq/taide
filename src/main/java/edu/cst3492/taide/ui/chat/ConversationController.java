package edu.cst3492.taide.ui.chat;

import edu.cst3492.taide.service.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Thread-safe streaming boundary. The view only receives immutable snapshots on its UI executor. */
public final class ConversationController implements AutoCloseable {
    public enum Status { STREAMING, COMPLETE, CANCELLED, ERROR }
    public record Message(boolean user, String text, CodeContext context, List<Citation> citations,
                          boolean grounded, String model, Status status) {}
    public record Snapshot(List<Message> messages, boolean busy, String notice) {}
    private final List<Message> history = new ArrayList<>();
    private final Executor ui;
    private final Consumer<Snapshot> listener;
    private final ExecutorService worker;
    private Future<?> task;
    private long generation;
    private boolean busy, closed;
    private String notice = "可以开始提问";
    private String lastQuestion;
    private CodeContext lastContext;
    private final StringBuilder partial = new StringBuilder();
    public static final int MAX_QUESTION = 16_000;
    public static final int MAX_ANSWER = 120_000;

    public ConversationController(Executor ui, Consumer<Snapshot> listener) {
        this.ui = ui; this.listener = listener;
        worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "TAIDE-chat"); thread.setDaemon(true); return thread;
        });
    }

    public synchronized boolean send(String question, CodeContext context, TaideService service) {
        if (closed || busy || question == null || question.isBlank()) return false;
        if (question.length() > MAX_QUESTION) { notice = "问题过长，请控制在 16000 个字符内"; emit(); return false; }
        Objects.requireNonNull(service);
        lastQuestion = question.strip(); lastContext = context == null ? CodeContext.empty() : context;
        while (history.size() >= 100) history.subList(0, 2).clear();
        history.add(new Message(true, lastQuestion, lastContext, List.of(), false, "", Status.COMPLETE));
        history.add(new Message(false, "", CodeContext.empty(), List.of(), false, "", Status.STREAMING));
        busy = true; partial.setLength(0); notice = "正在回答 · 可随时停止";
        long request = ++generation;
        String prompt = lastQuestion; CodeContext captured = lastContext;
        emit();
        task = worker.submit(() -> {
            try {
                service.askStreaming(prompt, captured, token -> token(request, token),
                        answer -> complete(request, answer), failure -> fail(request, failure));
            } catch (Throwable failure) {
                if (failure instanceof Error error) throw error;
                fail(request, failure);
            }
        });
        return true;
    }

    private synchronized void token(long request, String token) {
        if (!active(request) || token == null) return;
        int room = MAX_ANSWER - partial.length();
        if (room <= 0) { stopWithNotice("回答达到显示上限，请缩小问题后重试"); return; }
        partial.append(token, 0, Math.min(room, token.length()));
        replace(new Message(false, partial.toString(), CodeContext.empty(), List.of(), false, "", Status.STREAMING));
        emit();
    }

    private synchronized void complete(long request, TaAnswer answer) {
        if (!active(request)) return;
        if (answer == null || answer.text() == null || answer.text().isBlank()) {
            fail(request, new IllegalStateException("模型未返回有效回答")); return;
        }
        List<Citation> citations = answer.grounded() && answer.citations() != null
                ? answer.citations().stream().filter(Objects::nonNull).limit(30).toList() : List.of();
        replace(new Message(false, answer.text().substring(0, Math.min(answer.text().length(), MAX_ANSWER)),
                CodeContext.empty(), citations, answer.grounded(),
                Objects.requireNonNullElse(answer.model(), ""), Status.COMPLETE));
        busy = false; notice = "回答完成"; emit();
    }

    private synchronized void fail(long request, Throwable failure) {
        if (!active(request)) return;
        String reason = failure == null || failure.getMessage() == null ? "服务暂时不可用，请检查模型配置后重试" : failure.getMessage();
        // Never echo exceptions: providers may put credentials or full request bodies in their messages.
        if (reason.length() > 180 || reason.toLowerCase(Locale.ROOT).contains("bearer") || reason.contains("sk-"))
            reason = "服务调用失败，请检查网络与模型配置后重试";
        replace(new Message(false, partial.toString(), CodeContext.empty(), List.of(), false, "", Status.ERROR));
        busy = false; notice = reason; emit();
    }

    public synchronized void cancel() { if (busy) stopWithNotice("已停止生成；已接收的内容保留"); }
    private void stopWithNotice(String text) {
        ++generation; busy = false;
        if (task != null) task.cancel(true);
        replace(new Message(false, partial.toString(), CodeContext.empty(), List.of(), false, "", Status.CANCELLED));
        notice = text; emit();
    }
    public synchronized void clear() {
        if (closed) return;
        ++generation; busy = false;
        if (task != null) task.cancel(true);
        history.clear(); lastQuestion = null; lastContext = null; notice = "新对话 · 仅保存在本次 IDE 会话"; emit();
    }
    public synchronized boolean retry(TaideService service) {
        return lastQuestion != null && send(lastQuestion, lastContext, service);
    }
    public synchronized Snapshot snapshot() { return new Snapshot(List.copyOf(history), busy, notice); }
    private boolean active(long request) { return !closed && busy && generation == request; }
    private void replace(Message message) { history.set(history.size() - 1, message); }
    private void emit() {
        Snapshot event = snapshot(); long epoch = generation;
        ui.execute(() -> { synchronized (ConversationController.this) {
            if (!closed && epoch == generation) listener.accept(event);
        }});
    }
    @Override public synchronized void close() {
        closed = true; ++generation; busy = false;
        if (task != null) task.cancel(true);
        worker.shutdownNow(); history.clear();
    }
}
