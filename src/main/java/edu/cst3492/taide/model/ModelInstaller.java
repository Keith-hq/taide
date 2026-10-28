package edu.cst3492.taide.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** Downloads an exact, verified model release without exposing partially downloaded files. */
public final class ModelInstaller {
    public static final String REVISION = "1110a243fdf4706b3f48f1d95db1a4f5529b4d41";
    private static final String BASE = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/"
            + REVISION + "/";
    private static final List<Artifact> ARTIFACTS = List.of(
            new Artifact("model.onnx", URI.create(BASE + "onnx/model.onnx"), 90_405_214,
                    "6fd5d72fe4589f189f8ebc006442dbb529bb7ce38f8082112682524616046452"),
            new Artifact("vocab.txt", URI.create(BASE + "vocab.txt"), 231_508,
                    "07eced375cec144d27c900241f3e339478dec958f92fddbc551f295c992038a3"));
    private static final ReentrantLock[] LOCKS = new ReentrantLock[64];
    private static final int BUFFER_SIZE = 64 * 1024;

    static {
        for (int i = 0; i < LOCKS.length; i++) LOCKS[i] = new ReentrantLock();
    }

    public record Artifact(String fileName, URI uri, long size, String sha256) {
        public Artifact {
            Objects.requireNonNull(fileName);
            Objects.requireNonNull(uri);
            Objects.requireNonNull(sha256);
            if (fileName.isBlank() || fileName.equals(".") || fileName.equals("..")
                    || fileName.contains("/") || fileName.contains("\\") || fileName.contains(":")) {
                throw new IllegalArgumentException("Artifact must have a simple file name");
            }
            if (size <= 0 || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Artifact requires an exact size and SHA-256");
            }
        }
    }

    /** The adapter owns network/proxy policy and must bound connect and read timeouts. */
    @FunctionalInterface
    public interface Transport {
        InputStream open(URI uri) throws IOException;
    }

    @FunctionalInterface
    public interface Cancellation {
        void checkCanceled();
    }

    @FunctionalInterface
    public interface Progress {
        void update(long completed, long total, String detail);
    }

    private final Transport transport;
    private final List<Artifact> manifest;
    private final long total;

    public ModelInstaller(Transport transport) {
        this(transport, ARTIFACTS);
    }

    ModelInstaller(Transport transport, List<Artifact> manifest) {
        this.transport = Objects.requireNonNull(transport);
        this.manifest = List.copyOf(manifest);
        if (this.manifest.isEmpty()) throw new IllegalArgumentException("Empty model manifest");
        Set<String> names = new HashSet<>();
        long bytes = 0;
        for (Artifact artifact : this.manifest) {
            if (!names.add(artifact.fileName())) throw new IllegalArgumentException("Duplicate artifact name");
            bytes = Math.addExact(bytes, artifact.size());
        }
        total = bytes;
    }

    public static List<Artifact> artifacts() {
        return ARTIFACTS;
    }

    /** A local directory is ready only when both files match the pinned release byte for byte. */
    public boolean isReady(Path directory, Cancellation cancellation) throws IOException {
        Objects.requireNonNull(directory);
        Objects.requireNonNull(cancellation);
        cancellation.checkCanceled();
        if (!Files.isDirectory(directory)) return false;
        for (Artifact artifact : manifest) {
            if (!matches(directory.resolve(artifact.fileName()), artifact, cancellation)) return false;
        }
        return true;
    }

    /**
     * Installs in a dedicated cache directory. Unknown files in an incomplete target are preserved
     * by refusing replacement; callers should choose a separate managed directory in that case.
     * The transport's timeout bounds cancellation latency while a network read is in progress.
     */
    public Path install(Path directory, Progress progress, Cancellation cancellation) throws IOException {
        Objects.requireNonNull(directory);
        Objects.requireNonNull(progress);
        Objects.requireNonNull(cancellation);
        cancellation.checkCanceled();
        Path requested = directory.toAbsolutePath().normalize();
        Path parent = requested.getParent();
        if (parent == null || requested.getFileName() == null) {
            throw new IOException("A dedicated model directory is required");
        }
        Files.createDirectories(parent);
        Path target = parent.toRealPath().resolve(requested.getFileName());
        ReentrantLock lock = LOCKS[(target.hashCode() & Integer.MAX_VALUE) % LOCKS.length];
        acquire(lock, cancellation);
        try {
            Path lockFile = target.resolveSibling("." + target.getFileName() + ".install.lock");
            try (FileChannel channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = acquire(channel, cancellation)) {
                return installLocked(target, progress, cancellation);
            }
        } finally {
            lock.unlock();
        }
    }

    private Path installLocked(Path target, Progress progress, Cancellation cancellation) throws IOException {
        progress.update(0, total, "Validating local model");
        if (isReady(target, cancellation)) {
            progress.update(total, total, "Model ready");
            return target;
        }
        ensureReplaceable(target);
        Path staging = Files.createTempDirectory(target.getParent(), "." + target.getFileName() + ".download-");
        try {
            long completed = 0;
            for (Artifact artifact : manifest) {
                cancellation.checkCanceled();
                Path stagedFile = staging.resolve(artifact.fileName());
                Path cachedFile = target.resolve(artifact.fileName());
                boolean reused = false;
                if (Files.isRegularFile(cachedFile, LinkOption.NOFOLLOW_LINKS)) {
                    try (InputStream cached = Files.newInputStream(cachedFile)) {
                        transfer(cached, stagedFile, artifact, null, cancellation, completed);
                        reused = true;
                    } catch (IOException invalidCache) {
                        Files.deleteIfExists(stagedFile);
                    }
                }
                if (!reused) {
                    progress.update(completed, total, "Downloading " + artifact.fileName());
                    try (InputStream input = transport.open(artifact.uri())) {
                        transfer(input, stagedFile, artifact, progress, cancellation, completed);
                    }
                }
                completed += artifact.size();
                progress.update(completed, total, "Verified " + artifact.fileName());
            }
            cancellation.checkCanceled();
            // Check again immediately before moving the old directory, in case an external writer used it.
            ensureReplaceable(target);
            publish(staging, target);
            // This is the commit boundary: avoid callbacks that could turn a late Cancel into a
            // reported failure after the complete model has already been published.
            return target;
        } finally {
            deleteKnownFiles(staging);
        }
    }

    private void transfer(InputStream input, Path destination, Artifact artifact, Progress progress,
                          Cancellation cancellation, long completed) throws IOException {
        MessageDigest digest = sha256();
        long received = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (OutputStream output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
            while (true) {
                cancellation.checkCanceled();
                int count = input.read(buffer);
                cancellation.checkCanceled();
                if (count < 0) break;
                if (count == 0) continue;
                received += count;
                if (received > artifact.size()) {
                    throw new IOException("Downloaded model file exceeds its expected size: " + artifact.fileName());
                }
                output.write(buffer, 0, count);
                digest.update(buffer, 0, count);
                if (progress != null) progress.update(completed + received, total, "Downloading " + artifact.fileName());
            }
        }
        if (received != artifact.size()
                || !HexFormat.of().formatHex(digest.digest()).equals(artifact.sha256())) {
            throw new IOException("Model file verification failed: " + artifact.fileName());
        }
    }

    private static boolean matches(Path file, Artifact artifact, Cancellation cancellation) throws IOException {
        cancellation.checkCanceled();
        // Read-only validation also accepts Hugging Face snapshot directories with linked files.
        // Replacement still rejects links in ensureReplaceable and never writes through them.
        if (!Files.isRegularFile(file) || Files.size(file) != artifact.size()) return false;
        MessageDigest digest = sha256();
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = Files.newInputStream(file)) {
            while (true) {
                cancellation.checkCanceled();
                int count = input.read(buffer);
                cancellation.checkCanceled();
                if (count < 0) break;
                if (count > 0) digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest()).equals(artifact.sha256());
    }

    private void ensureReplaceable(Path target) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(target) || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Model cache path must be a dedicated directory");
        }
        Set<String> names = new HashSet<>();
        for (Artifact artifact : manifest) names.add(artifact.fileName());
        try (var entries = Files.list(target)) {
            if (entries.anyMatch(file -> !names.contains(file.getFileName().toString())
                    || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))) {
                throw new IOException("Model cache directory contains other files; choose a separate model directory");
            }
        }
    }

    private void publish(Path staging, Path target) throws IOException {
        Path backup = null;
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            backup = Files.createTempDirectory(target.getParent(), "." + target.getFileName() + ".previous-");
            Files.delete(backup);
            move(target, backup);
        }
        try {
            move(staging, target);
        } catch (IOException publishFailure) {
            if (backup != null) {
                try {
                    move(backup, target);
                } catch (IOException restoreFailure) {
                    publishFailure.addSuppressed(restoreFailure);
                }
            }
            throw publishFailure;
        }
        // Removal is best effort after a successful publication. Never delete unexpected files.
        if (backup != null) deleteKnownFiles(backup);
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(from, to);
        }
    }

    private void deleteKnownFiles(Path directory) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return;
        for (Artifact artifact : manifest) {
            try { Files.deleteIfExists(directory.resolve(artifact.fileName())); }
            catch (IOException ignored) { /* A later attempt may reclaim an abandoned temporary directory. */ }
        }
        try { Files.deleteIfExists(directory); }
        catch (IOException ignored) { /* Preserve unexpected files and do not turn success into failure. */ }
    }

    private static void acquire(ReentrantLock lock, Cancellation cancellation) throws IOException {
        try {
            while (true) {
                cancellation.checkCanceled();
                if (lock.tryLock(100, TimeUnit.MILLISECONDS)) return;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Model preparation interrupted");
        }
    }

    private static FileLock acquire(FileChannel channel, Cancellation cancellation) throws IOException {
        while (true) {
            cancellation.checkCanceled();
            try {
                FileLock lock = channel.tryLock();
                if (lock != null) return lock;
            } catch (OverlappingFileLockException busy) {
                // Another caller in this VM can hold the OS lock through a different path alias.
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Model preparation interrupted");
            }
        }
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
