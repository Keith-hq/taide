package edu.cst3492.taide.service;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import edu.cst3492.taide.llm.LiveTestSupport;
import edu.cst3492.taide.rag.RetrievedChunk;
import edu.cst3492.taide.rag.embed.OnnxEmbeddingModel;
import edu.cst3492.taide.rag.index.IndexBuilder;
import edu.cst3492.taide.rag.retrieve.RetrievalEvaluator;
import edu.cst3492.taide.ui.settings.TaideUiSettings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Issue #10 · A-C-01/02 acceptance on the real platform: readable error paths run
 * everywhere (CI-safe), live happy paths skip when key/materials/model are absent.
 * Platform tests run on the EDT, so service calls go through a worker thread —
 * the frozen contract requires non-EDT callers.
 */
public class TaideServiceImplAutomationRuntimePlatformTest extends BasePlatformTestCase {

    public void testRegisteredServiceIsAnAutomationRuntimeAccess() {
        TaideService service = getProject().getService(TaideService.class);
        assertTrue(service instanceof AutomationRuntimeAccess);
        assertTrue(service instanceof TaideServiceImpl);
    }

    public void testWithModelWithoutApiKeyFailsReadably() throws Exception {
        TaideServiceImpl service = new TaideServiceImpl(getProject());
        try {
            callOffEdt(() -> service.withModel(model -> "unused"));
            fail("expected TaideException");
        } catch (TaideException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("API key"));
        } finally {
            service.dispose();
        }
    }

    public void testWithModelAndRetrieverWithoutIndexFailsReadably() throws Exception {
        // A live test may have built an index into the shared config dir; this test
        // asserts the no-index error, so start from a clean slate.
        Files.deleteIfExists(TaidePaths.indexPath(getProject().getLocationHash()));
        TaideServiceImpl service = new TaideServiceImpl(getProject());
        try {
            callOffEdt(() -> service.withModelAndRetriever((model, retriever) -> "unused"));
            fail("expected TaideException");
        } catch (TaideException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Knowledge base"));
        } finally {
            service.dispose();
        }
    }

    public void testCallsAfterDisposeAreRejected() throws Exception {
        TaideServiceImpl service = new TaideServiceImpl(getProject());
        service.dispose();
        try {
            callOffEdt(() -> service.withModel(model -> "unused"));
            fail("expected TaideException");
        } catch (TaideException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("closed"));
        }
    }

    /** Live: A-C-01 — model access works with valid settings and NO course index. */
    public void testLiveWithModelWorksWithoutIndex() throws Exception {
        String key = LiveTestSupport.apiKey();
        if (key == null) return; // live environment not configured
        callOffEdt(() -> {
            TaideUiSettings.getInstance(getProject()).saveApiKey("DeepSeek", key.toCharArray());
            return null;
        });
        TaideServiceImpl service = new TaideServiceImpl(getProject());
        try {
            String reply = callOffEdt(() -> service.withModel(
                    model -> model.complete("Reply with exactly: TAIDE_OK", "ping", 0)));
            assertTrue(reply, reply.contains("TAIDE_OK"));
        } finally {
            service.dispose();
        }
    }

    /** Live: A-C-02 — model + retriever share F0's runtime and index. */
    public void testLiveWithModelAndRetrieverWorksWithIndex() throws Exception {
        String key = LiveTestSupport.apiKey();
        Path materials;
        try {
            materials = RetrievalEvaluator.resolveMaterialsDir();
        } catch (IllegalStateException e) {
            return; // live environment not configured
        }
        if (key == null || !Files.exists(OnnxEmbeddingModel.defaultModelDir())) return;

        Path indexPath = TaidePaths.indexPath(getProject().getLocationHash());
        try (OnnxEmbeddingModel model = OnnxEmbeddingModel.openDefault()) {
            new IndexBuilder(model).build(materials, indexPath, null);
        }
        callOffEdt(() -> {
            TaideUiSettings.getInstance(getProject()).saveApiKey("DeepSeek", key.toCharArray());
            return null;
        });
        TaideServiceImpl service = new TaideServiceImpl(getProject());
        try {
            List<RetrievedChunk> hits = callOffEdt(() -> service.withModelAndRetriever(
                    (model, retriever) -> retriever.retrieve("What is a chained exception?", 3)));
            assertFalse(hits.isEmpty());
            assertTrue(hits.stream().anyMatch(hit ->
                    hit.documentName().equals("Lec-3.1-Exceptions.pdf") && hit.slideNumber() == 9));
        } finally {
            service.dispose();
        }
    }

    /** Runs {@code call} on a worker thread and rethrows its result or failure. */
    @SuppressWarnings("unchecked")
    private static <T> T callOffEdt(Callable<T> call) throws Exception {
        AtomicReference<Object> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                result.set(call.call());
            } catch (Throwable error) {
                failure.set(error);
            }
        }, "taide-test-worker");
        worker.start();
        worker.join(60_000);
        if (failure.get() != null) {
            Throwable error = failure.get();
            if (error instanceof RuntimeException runtime) throw runtime;
            if (error instanceof Error fatal) throw fatal;
            throw (Exception) error;
        }
        return (T) result.get();
    }
}
