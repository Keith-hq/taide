package edu.cst3492.taide.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import edu.cst3492.taide.llm.LlmClient;
import edu.cst3492.taide.llm.OpenAiCompatibleLlmClient;
import edu.cst3492.taide.model.ModelSetupService;
import edu.cst3492.taide.rag.Retriever;
import edu.cst3492.taide.rag.embed.OnnxEmbeddingModel;
import edu.cst3492.taide.rag.index.IndexBuilder;
import edu.cst3492.taide.rag.index.InMemoryVectorIndex;
import edu.cst3492.taide.rag.retrieve.EmbeddingRetriever;
import edu.cst3492.taide.ui.integration.KnowledgeBaseController;
import edu.cst3492.taide.ui.settings.SettingsSnapshot;
import edu.cst3492.taide.ui.settings.SettingsValidation;
import edu.cst3492.taide.ui.settings.TaideUiSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The IDE-side project service that wires TAIDE's RAG core into the plugin:
 * B's tool window resolves it via {@code project.getService(TaideService.class)}
 * and the settings page calls it as {@link KnowledgeBaseController} for Rebuild.
 * All heavy work (index, model, LLM) runs on the calling thread — callers must
 * stay off the EDT per the frozen contract.
 */
public final class TaideServiceImpl implements TaideService, KnowledgeBaseController, Disposable {

    private final Project project;
    private final ServiceResourceCache<CoreResources> resources = new ServiceResourceCache<>();
    private final ReentrantLock rebuildLock = new ReentrantLock();
    private final AtomicBoolean disposed = new AtomicBoolean();

    private record CoreResources(TaideServiceCore core, OnnxEmbeddingModel model) implements AutoCloseable {
        @Override public void close() throws IOException { model.close(); }
    }

    public TaideServiceImpl(Project project) {
        this.project = project;
    }

    /** Lazily builds the core from settings; throws a readable error when setup is missing. */
    private ServiceResourceCache<CoreResources>.Lease core() throws TaideException {
        ApplicationManager.getApplication().assertIsNonDispatchThread();
        if (disposed.get()) throw new TaideException("TAIDE project has been closed.");
        TaideUiSettings preferences = TaideUiSettings.getInstance(project);
        long revision = preferences.revision() + ModelSetupService.getInstance().revision();
        SettingsSnapshot settings = preferences.snapshot();
        Path modelDirectory = TaidePaths.modelDir();
        return resources.acquire(revision, () -> {
            if (!SettingsValidation.validate(settings, null).isEmpty()) {
                throw new TaideException("Invalid TAIDE settings. Check the provider, API base URL and model in Settings → Tools → TAIDE.");
            }
            String apiKey = preferences.readApiKey(settings);
            if (apiKey == null || apiKey.isBlank()) {
                throw new TaideException("No API key configured: open Settings → Tools → TAIDE and enter one.");
            }
            Path indexPath = TaidePaths.indexPath(project.getLocationHash());
            if (!Files.exists(indexPath)) {
                throw new TaideException("Knowledge base not built yet: open Settings → Tools → TAIDE → Rebuild Knowledge Base.");
            }
            OnnxEmbeddingModel model = null;
            try {
                model = OnnxEmbeddingModel.openDirectory(modelDirectory);
                InMemoryVectorIndex index = InMemoryVectorIndex.load(indexPath);
                Retriever retriever = new EmbeddingRetriever(index, model);
                LlmClient llm = new OpenAiCompatibleLlmClient(settings.baseUrl(), apiKey, settings.model());
                PromptLibrary prompts = new PromptLibrary();
                return new CoreResources(new TaideServiceCore(retriever, llm, prompts, settings.model()), model);
            } catch (IOException | RuntimeException e) {
                if (model != null) try { model.close(); } catch (IOException ignored) { }
                throw new TaideException("TAIDE failed to load its knowledge base or model. Check the configured model files and rebuild the index.", e);
            }
        });
    }

    @Override
    public TaAnswer ask(String question, CodeContext context) throws TaideException {
        try (var lease = core()) { return lease.value().core().ask(question, context); }
    }

    @Override
    public void askStreaming(String question, CodeContext context,
                             Consumer<String> onToken,
                             Consumer<TaAnswer> onComplete,
                             Consumer<Throwable> onError) {
        try (var lease = core()) {
            lease.value().core().askStreaming(question, context, onToken, onComplete, onError);
        } catch (TaideException error) {
            onError.accept(error);
        }
    }

    @Override
    public RebuildResult rebuild(Path materialsDirectory, ProgressListener progress, Cancellation cancellation)
            throws Exception {
        ApplicationManager.getApplication().assertIsNonDispatchThread();
        cancellation.checkCanceled();
        if (disposed.get()) throw new TaideException("TAIDE project has been closed.");
        if (!rebuildLock.tryLock()) throw new TaideException("A knowledge base rebuild is already running.");
        try (OnnxEmbeddingModel model = OnnxEmbeddingModel.openDirectory(TaidePaths.modelDir())) {
            cancellation.checkCanceled();
            Path indexPath = TaidePaths.indexPath(project.getLocationHash());
            IndexBuilder.Result result = new IndexBuilder(model).build(materialsDirectory, indexPath,
                    progress::update,
                    () -> {
                        cancellation.checkCanceled();
                        return disposed.get();
                    });
            resources.invalidate(); // the controller delegates to this same chat service instance
            return new RebuildResult(result.documentCount(), result.chunkCount());
        } finally { rebuildLock.unlock(); }
    }

    @Override public void dispose() {
        disposed.set(true);
        // Model initialisation/cleanup must never block project shutdown on the EDT.
        ApplicationManager.getApplication().executeOnPooledThread(resources::close);
    }
}
