package edu.cst3492.taide.ui.chat;

import edu.cst3492.taide.service.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Thread-safe streaming boundary. The view only receives immutable snapshots on its UI executor. */
public final class ConversationController implements AutoCloseable {
    public enum Status { STREAMING, COMPLETE, CANCELLED, ERROR }
    public record Message(boolean user, String text, CodeContext context, List<Citation> citations,
                          boolean grounded, String model, Status status, boolean demo, String courseDirectory) {}
    public record Snapshot(List<Message> messages, boolean busy, String notice) {}
    private final List<Message> history = new ArrayList<>();
    private final Executor ui;
    private final Consumer<Snapshot> listener;
    private final ExecutorService worker;
    private Future<?> task;
    private long generation;
    private boolean busy, closed, demo, uiQueued;
    private String notice = "可以开始提问";
    private String lastQuestion;
    private CodeContext lastContext;
    private String courseDirectory = "";
    private final StringBuilder partial = new StringBuilder();
    public static final int MAX_QUESTION = 16_000;
    public static final int MAX_ANSWER = 120_000;

    public ConversationController(Executor ui, Consumer<Snapshot> listener) {
        this.ui = ui; this.listener = listener;
        worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "TAIDE-chat"); thread.setDaemon(true); return thread;
        });
    }

    public boolean send(String question, CodeContext context, TaideService service) {
        return send(question, context, service, service instanceof DemoTaideService);
    }

    public boolean send(String question, CodeContext context, TaideService service, boolean demonstration) {
        return send(question, context, service, demonstration, "");
    }

    /** Capture source settings so later changes cannot relabel history or redirect old citations. */
    public synchronized boolean send(String question, CodeContext context, TaideService service,
                                     boolean demonstration, String materialsDirectory) {
        if (closed || busy || question == null || question.isBlank()) return false;
        if (question.length() > MAX_QUESTION) { notice = "问题过长，请控制在 16000 个字符内"; emit(); return false; }
        Objects.requireNonNull(service);
        demo = demonstration;
        courseDirectory = Objects.requireNonNullElse(materialsDirectory, "");
        lastQuestion = question.strip(); lastContext = context == null ? CodeContext.empty() : context;
        while (history.size() >= 100) history.subList(0, 2).clear();
        history.add(new Message(true, lastQuestion, lastContext, List.of(), false, "", Status.COMPLETE, demo, courseDirectory));
        history.add(new Message(false, "", CodeContext.empty(), List.of(), false, "", Status.STREAMING, demo, courseDirectory));
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
        if (token.length() > room) { stopWithNotice("回答达到显示上限，请缩小问题后重试"); return; }
        replace(new Message(false, partial.toString(), CodeContext.empty(), List.of(), false, "", Status.STREAMING, demo, courseDirectory));
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
                Objects.requireNonNullElse(answer.model(), ""), Status.COMPLETE, demo, courseDirectory));
        busy = false; notice = answer.text().length() > MAX_ANSWER
                ? "回答超出显示上限，已截断；请缩小问题后重试" : "回答完成"; emit();
    }

    private synchronized void fail(long request, Throwable failure) {
        if (!active(request)) return;
        replace(new Message(false, partial.toString(), CodeContext.empty(), List.of(), false, "", Status.ERROR, demo, courseDirectory));
        busy = false; notice = readableFailure(failure); emit();
    }

    /** Providers may include credentials or request contents in any exception, regardless of key format. */
    private static String readableFailure(Throwable failure) {
        String message = failure == null ? "" : Objects.requireNonNullElse(failure.getMessage(), "");
        if (message.startsWith("No API key configured:")) return "尚未配置 API Key，请打开设置填写模型连接信息";
        if (message.startsWith("Knowledge base not built yet:")) return "课程知识库尚未建立，请在设置中重建知识库";
        if (message.startsWith("TAIDE failed to load its knowledge base or model:")
                || message.startsWith("TAIDE failed to load its knowledge base or model."))
            return "无法载入课程知识库或嵌入模型，请检查配置并重建知识库";
        if (message.equals("模型未返回有效回答")) return message;
        if (message.startsWith("Model API error 401") || message.startsWith("Model API error 403"))
            return "模型认证失败，请在设置中检查 API Key 与访问权限";
        if (message.startsWith("Model API error 429")) return "模型服务请求过于频繁，请稍后重试";
        if (failure instanceof InterruptedException || message.equals("Model call interrupted")) return "请求已中断，可重新发送";
        return "服务调用失败，请检查网络、模型配置和知识库后重试";
    }

    public synchronized void cancel() { if (busy) stopWithNotice("已停止生成；已接收的内容保留"); }
    private void stopWithNotice(String text) {
        ++generation; busy = false;
        if (task != null) task.cancel(true);
        replace(new Message(false, partial.toString(), CodeContext.empty(), List.of(), false, "", Status.CANCELLED, demo, courseDirectory));
        notice = text; emit();
    }
    public synchronized void clear() {
        if (closed) return;
        ++generation; busy = false;
        if (task != null) task.cancel(true);
        history.clear(); partial.setLength(0); lastQuestion = null; lastContext = null; courseDirectory = "";
        notice = "新对话 · 仅保存在本次 IDE 会话"; emit();
    }
    public synchronized boolean retry(TaideService service) {
        return retry(service, service instanceof DemoTaideService);
    }
    public synchronized boolean retry(TaideService service, boolean demonstration) {
        return retry(service, demonstration, "");
    }
    public synchronized boolean retry(TaideService service, boolean demonstration, String materialsDirectory) {
        return lastQuestion != null && send(lastQuestion, lastContext, service, demonstration, materialsDirectory);
    }
    public synchronized Snapshot snapshot() { return new Snapshot(List.copyOf(history), busy, notice); }
    private boolean active(long request) { return !closed && busy && generation == request; }
    private void replace(Message message) { history.set(history.size() - 1, message); }
    private void emit() {
        // One queued UI delivery reads the latest state. Fast tokens must not fill the EDT queue
        // with an immutable history copy for every token while rendering is throttled by the view.
        if (uiQueued || closed) return;
        uiQueued = true;
        ui.execute(() -> { synchronized (ConversationController.this) {
            uiQueued = false;
            if (!closed) listener.accept(snapshot());
        }});
    }
    @Override public synchronized void close() {
        closed = true; ++generation; busy = false;
        if (task != null) task.cancel(true);
        worker.shutdownNow(); history.clear(); partial.setLength(0); lastQuestion = null; lastContext = null; courseDirectory = "";
    }
}
