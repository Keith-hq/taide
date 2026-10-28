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
    private String notice = "Ready to chat";
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
        if (question.length() > MAX_QUESTION) { notice = "Question too long. Use 16,000 characters or fewer."; emit(); return false; }
        Objects.requireNonNull(service);
        demo = demonstration;
        courseDirectory = Objects.requireNonNullElse(materialsDirectory, "");
        lastQuestion = question.strip(); lastContext = context == null ? CodeContext.empty() : context;
        while (history.size() >= 100) history.subList(0, 2).clear();
        history.add(new Message(true, lastQuestion, lastContext, List.of(), false, "", Status.COMPLETE, demo, courseDirectory));
        history.add(new Message(false, "", CodeContext.empty(), List.of(), false, "", Status.STREAMING, demo, courseDirectory));
        busy = true; partial.setLength(0); notice = "Generating · Stop anytime";
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
        if (room <= 0) { stopWithNotice("Answer reached the display limit. Narrow your question and retry."); return; }
        partial.append(token, 0, Math.min(room, token.length()));
        if (token.length() > room) { stopWithNotice("Answer reached the display limit. Narrow your question and retry."); return; }
        replace(new Message(false, partial.toString(), CodeContext.empty(), List.of(), false, "", Status.STREAMING, demo, courseDirectory));
        emit();
    }

    private synchronized void complete(long request, TaAnswer answer) {
        if (!active(request)) return;
        if (answer == null || answer.text() == null || answer.text().isBlank()) {
            fail(request, new IllegalStateException("The model returned no valid answer")); return;
        }
        List<Citation> citations = answer.grounded() && answer.citations() != null
                ? answer.citations().stream().filter(Objects::nonNull).limit(30).toList() : List.of();
        replace(new Message(false, answer.text().substring(0, Math.min(answer.text().length(), MAX_ANSWER)),
                CodeContext.empty(), citations, answer.grounded(),
                Objects.requireNonNullElse(answer.model(), ""), Status.COMPLETE, demo, courseDirectory));
        busy = false; notice = answer.text().length() > MAX_ANSWER
                ? "Answer truncated at the display limit. Narrow your question and retry." : "Answer complete"; emit();
    }

    private synchronized void fail(long request, Throwable failure) {
        if (!active(request)) return;
        replace(new Message(false, partial.toString(), CodeContext.empty(), List.of(), false, "", Status.ERROR, demo, courseDirectory));
        busy = false; notice = readableFailure(failure); emit();
    }

    /** Providers may include credentials or request contents in any exception, regardless of key format. */
    private static String readableFailure(Throwable failure) {
        String message = failure == null ? "" : Objects.requireNonNullElse(failure.getMessage(), "");
        if (message.startsWith("No API key configured:")) return "API key not configured. Open Settings to configure the model connection.";
        if (message.startsWith("Knowledge base not built yet:")) return "Knowledge base not built yet. Rebuild the knowledge base in Settings.";
        if (message.startsWith("TAIDE failed to load its knowledge base or model:")
                || message.startsWith("TAIDE failed to load its knowledge base or model."))
            return "Cannot load the knowledge base or embedding model. Check Settings and rebuild the knowledge base.";
        if (message.equals("The model returned no valid answer")) return message;
        if (message.startsWith("Model API error 401") || message.startsWith("Model API error 403"))
            return "Model authentication failed. Check your API key and permissions in Settings.";
        if (message.startsWith("Model API error 429")) return "Model request rate limit reached. Try again later.";
        if (failure instanceof InterruptedException || message.equals("Model call interrupted")) return "Request interrupted. You can send it again.";
        return "Request failed. Check your network, model settings and knowledge base, then retry.";
    }

    public synchronized void cancel() { if (busy) stopWithNotice("Generation stopped. Received text is kept."); }
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
        notice = "New chat · Stored only for this IDE session"; emit();
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
