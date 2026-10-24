package edu.cst3492.taide.model;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real local HTTP transfers and injected failures; no public network or large model is required. */
class ModelInstallerTest {
    @TempDir Path temporary;
    private static final byte[] MODEL = "test-model-bytes".getBytes(StandardCharsets.UTF_8);
    private static final byte[] VOCAB = "[PAD]\n[UNK]\nhello\n".getBytes(StandardCharsets.UTF_8);
    private static final ModelInstaller.Cancellation ACTIVE = () -> {};
    private static final ModelInstaller.Progress QUIET = (completed, total, detail) -> {};
    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int status = 200;
    private volatile byte[] servedModel = MODEL;
    private volatile byte[] servedVocab = VOCAB;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] bytes = exchange.getRequestURI().getPath().equals("/model") ? servedModel : servedVocab;
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private List<ModelInstaller.Artifact> manifest() throws Exception {
        return List.of(artifact("model.onnx", "/model", MODEL), artifact("vocab.txt", "/vocab", VOCAB));
    }

    private ModelInstaller.Artifact artifact(String name, String endpoint, byte[] bytes) throws Exception {
        return new ModelInstaller.Artifact(name,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + endpoint),
                bytes.length, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }

    private ModelInstaller installer() throws Exception {
        return new ModelInstaller(uri -> {
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(1_000);
            connection.setReadTimeout(1_000);
            return connection.getInputStream();
        }, manifest());
    }

    private Path target() { return temporary.resolve("models").resolve("MiniLM"); }

    private void assertNoTemporaryDirectories() throws IOException {
        Path parent = target().getParent();
        if (!Files.exists(parent)) return;
        try (var entries = Files.list(parent)) {
            assertFalse(entries.anyMatch(path -> path.getFileName().toString().contains(".download-")
                    || path.getFileName().toString().contains(".previous-")));
        }
    }

    private void existing(byte[] model, byte[] vocab) throws IOException {
        Files.createDirectories(target());
        Files.write(target().resolve("model.onnx"), model);
        Files.write(target().resolve("vocab.txt"), vocab);
    }

    @Test
    void defaultManifestPinsBothFilesToTheReviewedRevision() {
        List<ModelInstaller.Artifact> artifacts = ModelInstaller.artifacts();
        assertEquals(List.of("model.onnx", "vocab.txt"), artifacts.stream().map(ModelInstaller.Artifact::fileName).toList());
        assertEquals(90_405_214, artifacts.get(0).size());
        assertEquals(231_508, artifacts.get(1).size());
        assertTrue(artifacts.stream().allMatch(a -> a.uri().getScheme().equals("https")
                && a.uri().getPath().contains("/resolve/" + ModelInstaller.REVISION + "/")));
        assertThrows(UnsupportedOperationException.class, () -> artifacts.clear());
    }

    @Test
    void downloadsAndVerifiesBothFilesBeforePublishingTheDirectory() throws Exception {
        ModelInstaller installer = installer();
        List<Long> updates = new ArrayList<>();
        Path result = installer.install(target(), (completed, total, detail) -> {
            assertEquals(MODEL.length + VOCAB.length, total);
            assertTrue(completed >= 0 && completed <= total);
            if (!updates.isEmpty()) assertTrue(completed >= updates.getLast());
            updates.add(completed);
            // No cancellable callback may run after publication and misreport a committed install.
            assertFalse(Files.exists(target()));
        }, ACTIVE);
        assertEquals(target().toAbsolutePath(), result);
        assertArrayEquals(MODEL, Files.readAllBytes(result.resolve("model.onnx")));
        assertArrayEquals(VOCAB, Files.readAllBytes(result.resolve("vocab.txt")));
        assertTrue(installer.isReady(result, ACTIVE));
        assertEquals(2, requests.get());
        assertEquals((long) MODEL.length + VOCAB.length, updates.getLast());
        assertNoTemporaryDirectories();
    }

    @Test
    void readyCacheDoesNotContactTheNetworkOrRemoveOtherFiles() throws Exception {
        existing(MODEL, VOCAB);
        Files.writeString(target().resolve("README.txt"), "leave this alone");
        ModelInstaller installer = installer();
        installer.install(target(), QUIET, ACTIVE);
        assertTrue(installer.isReady(target(), ACTIVE));
        assertEquals(0, requests.get());
        assertEquals("leave this alone", Files.readString(target().resolve("README.txt")));
        assertNoTemporaryDirectories();
    }

    @Test
    void incompleteCacheReusesTheVerifiedFile() throws Exception {
        Files.createDirectories(target());
        Files.write(target().resolve("model.onnx"), MODEL);
        ModelInstaller installer = installer();
        installer.install(target(), QUIET, ACTIVE);
        assertEquals(1, requests.get());
        assertTrue(installer.isReady(target(), ACTIVE));
        assertNoTemporaryDirectories();
    }

    @Test
    void sameSizeCorruptCacheIsReplacedInsteadOfTrusted() throws Exception {
        byte[] badModel = MODEL.clone();
        badModel[0] ^= 1;
        existing(badModel, VOCAB);
        ModelInstaller installer = installer();
        assertFalse(installer.isReady(target(), ACTIVE));
        installer.install(target(), QUIET, ACTIVE);
        assertEquals(1, requests.get());
        assertTrue(installer.isReady(target(), ACTIVE));
        assertNoTemporaryDirectories();
    }

    @Test
    void sameSizeWrongDownloadFailsHashCheckAndKeepsPreviousFiles() throws Exception {
        byte[] previousVocab = "incomplete".getBytes(StandardCharsets.UTF_8);
        existing(MODEL, previousVocab);
        servedVocab = VOCAB.clone();
        servedVocab[0] ^= 1;
        assertThrows(IOException.class, () -> installer().install(target(), QUIET, ACTIVE));
        assertArrayEquals(MODEL, Files.readAllBytes(target().resolve("model.onnx")));
        assertArrayEquals(previousVocab, Files.readAllBytes(target().resolve("vocab.txt")));
        assertNoTemporaryDirectories();
    }

    @Test
    void truncatedDownloadNeverPublishesASingleFile() throws Exception {
        servedVocab = Arrays.copyOf(VOCAB, VOCAB.length - 1);
        assertThrows(IOException.class, () -> installer().install(target(), QUIET, ACTIVE));
        assertFalse(Files.exists(target()));
        assertNoTemporaryDirectories();
    }

    @Test
    void oversizedDownloadIsBoundedAndClosed() throws Exception {
        AtomicInteger readCount = new AtomicInteger();
        AtomicBoolean closed = new AtomicBoolean();
        InputStream endless = new InputStream() {
            @Override public int read() { readCount.incrementAndGet(); return 1; }
            @Override public int read(byte[] buffer, int offset, int length) {
                Arrays.fill(buffer, offset, offset + length, (byte) 1);
                readCount.addAndGet(length);
                return length;
            }
            @Override public void close() { closed.set(true); }
        };
        ModelInstaller installer = new ModelInstaller(uri -> endless, manifest());
        assertThrows(IOException.class, () -> installer.install(target(), QUIET, ACTIVE));
        assertEquals(64 * 1024, readCount.get());
        assertTrue(closed.get());
        assertFalse(Files.exists(target()));
        assertNoTemporaryDirectories();
    }

    @Test
    void networkFailureDoesNotReplaceOrRemoveTheExistingCache() throws Exception {
        byte[] previousVocab = "previous partial data".getBytes(StandardCharsets.UTF_8);
        existing(MODEL, previousVocab);
        status = 503;
        assertThrows(IOException.class, () -> installer().install(target(), QUIET, ACTIVE));
        assertArrayEquals(previousVocab, Files.readAllBytes(target().resolve("vocab.txt")));
        assertArrayEquals(MODEL, Files.readAllBytes(target().resolve("model.onnx")));
        assertNoTemporaryDirectories();
    }

    @Test
    void cancelDuringTransferClosesTheStreamAndRemovesStaging() throws Exception {
        byte[] large = new byte[150_000];
        Arrays.fill(large, (byte) 5);
        AtomicBoolean canceled = new AtomicBoolean();
        AtomicBoolean closed = new AtomicBoolean();
        ModelInstaller installer = new ModelInstaller(uri -> new ByteArrayInputStream(large) {
            @Override public void close() { closed.set(true); }
        }, List.of(artifact("model.onnx", "/model", large)));
        assertThrows(CancellationException.class, () -> installer.install(target(), (completed, total, detail) -> {
            if (completed > 0) canceled.set(true);
        }, () -> { if (canceled.get()) throw new CancellationException(); }));
        assertTrue(closed.get());
        assertFalse(Files.exists(target()));
        assertNoTemporaryDirectories();
    }

    @Test
    void cancelAtThePublicationBoundaryKeepsThePreviousDirectory() throws Exception {
        byte[] previous = "not yet ready".getBytes(StandardCharsets.UTF_8);
        existing(MODEL, previous);
        AtomicBoolean canceled = new AtomicBoolean();
        assertThrows(CancellationException.class, () -> installer().install(target(), (completed, total, detail) -> {
            if (completed == total) canceled.set(true);
        }, () -> { if (canceled.get()) throw new CancellationException(); }));
        assertArrayEquals(previous, Files.readAllBytes(target().resolve("vocab.txt")));
        assertArrayEquals(MODEL, Files.readAllBytes(target().resolve("model.onnx")));
        assertNoTemporaryDirectories();
    }

    @Test
    void cancellationBeforeStartingDoesNotCreateDirectoriesOrRequests() throws Exception {
        assertThrows(CancellationException.class, () -> installer().install(target(), QUIET,
                () -> { throw new CancellationException(); }));
        assertFalse(Files.exists(target().getParent()));
        assertEquals(0, requests.get());
    }

    @Test
    void incompleteDirectoryWithUnrelatedFilesIsNeverModified() throws Exception {
        Files.createDirectories(target());
        Files.writeString(target().resolve("important.txt"), "user data");
        assertThrows(IOException.class, () -> installer().install(target(), QUIET, ACTIVE));
        assertEquals("user data", Files.readString(target().resolve("important.txt")));
        assertEquals(0, requests.get());
        assertNoTemporaryDirectories();
    }

    @Test
    void concurrentInstallersDownloadOnlyOneCompleteSet() throws Exception {
        CountDownLatch downloading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger downloads = new AtomicInteger();
        Map<String, byte[]> content = Map.of("/model", MODEL, "/vocab", VOCAB);
        ModelInstaller.Transport transport = uri -> {
            if (downloads.incrementAndGet() == 1) {
                downloading.countDown();
                await(release);
            }
            return new ByteArrayInputStream(content.get(uri.getPath()));
        };
        ModelInstaller first = new ModelInstaller(transport, manifest());
        ModelInstaller second = new ModelInstaller(transport, manifest());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var firstResult = executor.submit(() -> first.install(target(), QUIET, ACTIVE));
            assertTrue(downloading.await(2, TimeUnit.SECONDS));
            var secondResult = executor.submit(() -> second.install(target(), QUIET, ACTIVE));
            release.countDown();
            assertEquals(firstResult.get(3, TimeUnit.SECONDS), secondResult.get(3, TimeUnit.SECONDS));
            assertEquals(2, downloads.get());
            assertTrue(first.isReady(target(), ACTIVE));
        } finally {
            release.countDown();
        }
        assertNoTemporaryDirectories();
    }

    @Test
    void waitingForAnotherInstallerCanBeCanceled() throws Exception {
        CountDownLatch downloading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch waiting = new CountDownLatch(1);
        AtomicBoolean cancelSecond = new AtomicBoolean();
        AtomicInteger checks = new AtomicInteger();
        ModelInstaller first = new ModelInstaller(uri -> {
            downloading.countDown();
            await(release);
            return new ByteArrayInputStream(uri.getPath().equals("/model") ? MODEL : VOCAB);
        }, manifest());
        ModelInstaller second = installer();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var firstResult = executor.submit(() -> first.install(target(), QUIET, ACTIVE));
            assertTrue(downloading.await(2, TimeUnit.SECONDS));
            var secondResult = executor.submit(() -> second.install(target(), QUIET, () -> {
                if (checks.incrementAndGet() >= 3) waiting.countDown();
                if (cancelSecond.get()) throw new CancellationException();
            }));
            assertTrue(waiting.await(2, TimeUnit.SECONDS));
            cancelSecond.set(true);
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> secondResult.get(1, TimeUnit.SECONDS));
            assertInstanceOf(CancellationException.class, failure.getCause());
            assertFalse(firstResult.isDone());
            release.countDown();
            firstResult.get(3, TimeUnit.SECONDS);
            assertTrue(first.isReady(target(), ACTIVE));
            assertEquals(0, requests.get());
        } finally {
            release.countDown();
        }
        assertNoTemporaryDirectories();
    }

    @Test
    void waitingForAnOperatingSystemLockCanBeCanceledWithoutStartingADownload() throws Exception {
        Files.createDirectories(target().getParent());
        Path lockPath = target().resolveSibling("." + target().getFileName() + ".install.lock");
        CountDownLatch waiting = new CountDownLatch(1);
        AtomicInteger checks = new AtomicInteger();
        AtomicBoolean canceled = new AtomicBoolean();
        ModelInstaller installer = installer();
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock();
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var result = executor.submit(() -> installer.install(target(), QUIET, () -> {
                if (checks.incrementAndGet() >= 3) waiting.countDown();
                if (canceled.get()) throw new CancellationException();
            }));
            try {
                assertTrue(waiting.await(2, TimeUnit.SECONDS));
                canceled.set(true);
                ExecutionException failure = assertThrows(ExecutionException.class,
                        () -> result.get(1, TimeUnit.SECONDS));
                assertInstanceOf(CancellationException.class, failure.getCause());
            } finally {
                canceled.set(true);
            }
        }
        assertEquals(0, requests.get());
        assertFalse(Files.exists(target()));
        assertNoTemporaryDirectories();
    }

    @Test
    void localValidationChecksSizeAndHashWithoutChangingAnything() throws Exception {
        ModelInstaller installer = installer();
        assertFalse(installer.isReady(target(), ACTIVE));
        existing(MODEL, new byte[VOCAB.length]);
        assertFalse(installer.isReady(target(), ACTIVE));
        Files.write(target().resolve("vocab.txt"), new byte[VOCAB.length + 1]);
        assertFalse(installer.isReady(target(), ACTIVE));
        Files.write(target().resolve("vocab.txt"), VOCAB);
        assertTrue(installer.isReady(target(), ACTIVE));
        assertEquals(0, requests.get());
    }

    @Test
    void linkedSnapshotFilesCanBeReadButAreNeverReplaced() throws Exception {
        Path source = temporary.resolve("hugging-face-snapshot");
        Files.createDirectories(source);
        Files.write(source.resolve("model.onnx"), MODEL);
        Files.write(source.resolve("vocab.txt"), VOCAB);
        Files.createDirectories(target());
        try {
            Files.createSymbolicLink(target().resolve("model.onnx"), source.resolve("model.onnx"));
            Files.createSymbolicLink(target().resolve("vocab.txt"), source.resolve("vocab.txt"));
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException | SecurityException unavailable) {
            assumeTrue(false, "Symbolic link creation is unavailable: " + unavailable.getClass().getSimpleName());
        }
        ModelInstaller installer = installer();
        assertTrue(installer.isReady(target(), ACTIVE));
        installer.install(target(), QUIET, ACTIVE);
        assertTrue(Files.isSymbolicLink(target().resolve("model.onnx")));
        assertTrue(Files.isSymbolicLink(target().resolve("vocab.txt")));
        Files.write(source.resolve("vocab.txt"), new byte[VOCAB.length]);
        assertFalse(installer.isReady(target(), ACTIVE));
        assertThrows(IOException.class, () -> installer.install(target(), QUIET, ACTIVE));
        assertArrayEquals(MODEL, Files.readAllBytes(source.resolve("model.onnx")));
        assertEquals(0, requests.get());
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(4, TimeUnit.SECONDS)) throw new IOException("Test transfer timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException(interrupted);
        }
    }
}
