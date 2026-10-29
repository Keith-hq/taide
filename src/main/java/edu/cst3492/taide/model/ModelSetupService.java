package edu.cst3492.taide.model;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.*;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.net.HttpConnectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** One verified local embedding model shared by every project in this IDE installation. */
@Service(Service.Level.APP)
@State(name = "TaideModelSetup", storages = @Storage(value = "taide-model.xml", roamingType = RoamingType.DISABLED))
public final class ModelSetupService implements PersistentStateComponent<ModelSetupService.Location>, Disposable {
    private static final Logger LOG = Logger.getInstance(ModelSetupService.class);
    public static final class Location {
        public String directory = "";
        public boolean managed;
    }

    public record Status(Path directory, boolean checked, boolean ready, boolean preparing,
                         long downloaded, long total, String detail) { }

    private volatile Location location = new Location();
    private volatile Status status;
    private final AtomicLong revision = new AtomicLong();
    private final AtomicLong generation = new AtomicLong();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final AtomicReference<Download> download = new AtomicReference<>();
    private final Object selectionLock = new Object();
    private volatile boolean disposed;

    public static ModelSetupService getInstance() {
        return ApplicationManager.getApplication().getService(ModelSetupService.class);
    }

    @Override public @NotNull Location getState() {
        Location copy = new Location(); copy.directory = location.directory; copy.managed = location.managed; return copy;
    }

    @Override public void loadState(@NotNull Location value) {
        Location copy = new Location();
        copy.directory = value.directory == null ? "" : value.directory.trim(); copy.managed = value.managed;
        synchronized (selectionLock) {
            location = copy; generation.incrementAndGet(); revision.incrementAndGet(); status = null;
        }
    }

    public static Path managedDirectory() { return Path.of(PathManager.getConfigPath(), "taide", "model"); }

    public Path directory() {
        Location value = location;
        if (!value.directory.isBlank()) return Path.of(value.directory);
        if (!value.managed) {
            String override = System.getProperty("taide.model.dir");
            if (override == null || override.isBlank()) override = System.getenv("TAIDE_MODEL_DIR");
            if (override != null && !override.isBlank()) return Path.of(override);
        }
        return managedDirectory();
    }

    public long revision() { return revision.get(); }

    public Status status() {
        Status value = status;
        return value == null ? new Status(directory(), false, false, false, 0, totalBytes(), "Checking retrieval model…") : value;
    }

    public AutoCloseable listen(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void refresh() {
        final long request;
        final Path selected;
        synchronized (selectionLock) {
            if (disposed || download.get() != null) return;
            request = generation.incrementAndGet();
            selected = directory();
            Status previous = status;
            boolean ready = previous != null && previous.ready() && selected.equals(previous.directory());
            publish(new Status(selected, false, ready, false, ready ? totalBytes() : 0, totalBytes(), "Validating retrieval model…"));
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            boolean ready = false;
            try { ready = installer(null).isReady(selected, () -> checkCurrent(request)); }
            catch (IOException ignored) { }
            catch (ProcessCanceledException ignored) { return; }
            synchronized (selectionLock) {
                if (disposed || generation.get() != request) return;
                publish(new Status(selected, true, ready, false, ready ? totalBytes() : 0, totalBytes(),
                        ready ? "Retrieval model ready for offline use." : "First use: download the retrieval model or choose an existing folder."));
            }
        });
    }

    /** Validates before publishing the selected directory; call outside EDT. */
    public void useDirectory(Path selected) throws IOException {
        ApplicationManager.getApplication().assertIsNonDispatchThread();
        if (download.get() != null) throw new IOException("Cancel the current download first.");
        Path normalized = selected.toAbsolutePath().normalize();
        if (!installer(null).isReady(normalized, ProgressManager::checkCanceled)) {
            throw new IOException("This folder needs matching all-MiniLM-L6-v2 model.onnx and vocab.txt files. Use Download model to prepare them.");
        }
        synchronized (selectionLock) {
            if (disposed || download.get() != null) throw new IOException("Cancel the current download first.");
            select(normalized);
            publish(new Status(normalized, true, true, false, totalBytes(), totalBytes(), "Existing model validated. No download needed."));
        }
    }

    public void useManagedDirectory() {
        synchronized (selectionLock) {
            if (disposed || download.get() != null) return;
            Location next = new Location(); next.managed = true; location = next;
            revision.incrementAndGet(); refresh();
        }
    }

    public void download(@Nullable Project project) {
        Download operation = new Download();
        final Status previous;
        Path target = managedDirectory();
        synchronized (selectionLock) {
            if (disposed || !download.compareAndSet(null, operation)) return;
            previous = status();
            generation.incrementAndGet();
            publish(new Status(target, false, false, true, 0, totalBytes(), "Connecting to model download source…"));
        }
        new Task.Backgroundable(project, "Preparing TAIDE retrieval model", true) {
            private boolean installed;
            @Override public void run(@NotNull ProgressIndicator indicator) {
                operation.indicator = indicator;
                indicator.setIndeterminate(false);
                try {
                    installer(operation).install(target, (done, total, detail) -> {
                        operation.checkCanceled();
                        indicator.setFraction(total == 0 ? 0 : (double) done / total);
                        indicator.setText(detail);
                        long now = System.nanoTime();
                        if (now - operation.lastUpdate > 100_000_000L || done == total) {
                            operation.lastUpdate = now;
                            publish(new Status(target, false, false, true, done, total, detail));
                        }
                    }, operation::checkCanceled);
                    // A successful return follows atomic publication. A late Cancel cannot undo it.
                    synchronized (selectionLock) { select(target); installed = true; }
                } catch (IOException error) {
                    operation.checkCanceled();
                    throw new ModelDownloadException(error);
                }
            }
            @Override public void onSuccess() { finish(""); }
            @Override public void onCancel() { finish("Download canceled. Your existing model is unchanged. You can retry anytime."); }
            @Override public void onThrowable(@NotNull Throwable error) {
                LOG.warn("Retrieval model preparation failed", error);
                finish(failureDetail(error));
            }
            private void finish(String failure) {
                synchronized (selectionLock) {
                    if (!download.compareAndSet(operation, null) || disposed) return;
                    if (installed) publish(new Status(target, true, true, false, totalBytes(), totalBytes(), "Model downloaded, verified and ready to use."));
                    else {
                        boolean retained = previous.ready() && directory().equals(previous.directory());
                        publish(new Status(directory(), true, retained, false, retained ? totalBytes() : 0, totalBytes(), failure));
                    }
                }
            }
        }.queue();
    }

    public void cancel() {
        Download operation = download.get();
        if (operation == null) return;
        operation.canceled = true;
        ProgressIndicator indicator = operation.indicator;
        if (indicator != null) indicator.cancel();
        HttpURLConnection connection = operation.connection.getAndSet(null);
        if (connection != null) ApplicationManager.getApplication().executeOnPooledThread(connection::disconnect);
    }

    private ModelInstaller installer(@Nullable Download operation) {
        return new ModelInstaller(uri -> open(uri, operation));
    }

    /** IDE HTTP proxy and authentication are applied to the origin and every HTTPS redirect. */
    private InputStream open(URI uri, @Nullable Download operation) throws IOException {
        for (int redirects = 0; redirects < 8; redirects++) {
            if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IOException("Model source requires HTTPS.");
            if (operation != null) operation.checkCanceled();
            HttpURLConnection connection = HttpConnectionUtils.openHttpConnection(uri.toString());
            connection.setConnectTimeout(8_000); connection.setReadTimeout(30_000);
            connection.setRequestProperty("User-Agent", "TAIDE/0.1 model-installer");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setInstanceFollowRedirects(false);
            if (operation != null) operation.connection.set(connection);
            try {
                if (operation != null) operation.checkCanceled();
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String redirect = connection.getHeaderField("Location");
                    if (redirect == null) throw new IOException("Model source returned an invalid redirect.");
                    uri = uri.resolve(redirect); connection.disconnect();
                    if (operation != null) operation.connection.compareAndSet(connection, null);
                    continue;
                }
                if (code != 200) throw new IOException("Model download returned HTTP " + code);
                InputStream input = connection.getInputStream();
                return new FilterInputStream(input) {
                    @Override public void close() throws IOException {
                        try { super.close(); }
                        finally {
                            connection.disconnect();
                            if (operation != null) operation.connection.compareAndSet(connection, null);
                        }
                    }
                };
            } catch (IOException | RuntimeException failure) {
                connection.disconnect();
                if (operation != null) operation.connection.compareAndSet(connection, null);
                throw failure;
            }
        }
        throw new IOException("Too many model download redirects.");
    }

    private void select(Path selected) {
        Location next = new Location(); next.directory = selected.toString();
        location = next; generation.incrementAndGet(); revision.incrementAndGet();
    }

    private void checkCurrent(long request) {
        if (disposed || generation.get() != request) throw new ProcessCanceledException();
        ProgressManager.checkCanceled();
    }

    private void publish(Status next) {
        status = next;
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!disposed) for (Runnable listener : listeners) listener.run();
        }, ModalityState.any());
    }

    private static long totalBytes() { return ModelInstaller.artifacts().stream().mapToLong(ModelInstaller.Artifact::size).sum(); }
    static String failureDetail(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ModelInstaller.DownloadException) {
                return cause.getMessage() + " Check Settings → Appearance & Behavior → System Settings → HTTP Proxy, "
                        + "then retry or choose an existing model folder.";
            }
            String message = cause.getMessage();
            if (message == null) continue;
            if (message.contains("verification failed") || message.contains("exceeds its expected size")) {
                return "Model verification failed. The downloaded files were not activated. Retry or choose a complete existing model folder.";
            }
            if (message.contains("dedicated directory") || message.contains("contains other files")) {
                return "The default model folder contains other files. Move them before retrying, or choose an existing model folder.";
            }
        }
        return "Download incomplete. Check Settings → Appearance & Behavior → System Settings → HTTP Proxy, then retry or choose an existing model folder.";
    }
    @Override public void dispose() { disposed = true; cancel(); listeners.clear(); generation.incrementAndGet(); }
    private static final class ModelDownloadException extends RuntimeException {
        ModelDownloadException(IOException cause) { super("Model preparation failed", cause); }
    }
    private final class Download {
        volatile boolean canceled;
        volatile ProgressIndicator indicator;
        volatile long lastUpdate;
        final AtomicReference<HttpURLConnection> connection = new AtomicReference<>();
        void checkCanceled() {
            if (canceled || disposed) throw new ProcessCanceledException();
            ProgressIndicator current = indicator;
            if (current != null) current.checkCanceled();
        }
    }
}
