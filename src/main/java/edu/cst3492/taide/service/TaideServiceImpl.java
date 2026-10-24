package edu.cst3492.taide.service;

import com.intellij.openapi.project.Project;
import edu.cst3492.taide.llm.LlmClient;
import edu.cst3492.taide.llm.OpenAiCompatibleLlmClient;
import edu.cst3492.taide.rag.Retriever;
import edu.cst3492.taide.rag.embed.OnnxEmbeddingModel;
import edu.cst3492.taide.rag.index.IndexBuilder;
import edu.cst3492.taide.rag.index.InMemoryVectorIndex;
import edu.cst3492.taide.rag.retrieve.EmbeddingRetriever;
import edu.cst3492.taide.ui.integration.KnowledgeBaseController;
import edu.cst3492.taide.ui.settings.SettingsSnapshot;
import edu.cst3492.taide.ui.settings.TaideUiSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The IDE-side project service that wires TAIDE's RAG core into the plugin:
 * B's tool window resolves it via {@code project.getService(TaideService.class)}
 * and the settings page calls it as {@link KnowledgeBaseController} for Rebuild.
 * All heavy work (index, model, LLM) runs on the calling thread — callers must
 * stay off the EDT per the frozen contract.
 */
public final class TaideServiceImpl implements TaideService, KnowledgeBaseController {

    private final Project project;
    private volatile TaideServiceCore core;

    public TaideServiceImpl(Project project) {
        this.project = project;
    }

    /** Lazily builds the core from settings; throws a readable error when setup is missing. */
    private TaideServiceCore core() throws TaideException {
        TaideServiceCore current = core;
        if (current != null) return current;
        synchronized (this) {
            if (core != null) return core;
            SettingsSnapshot settings = TaideUiSettings.getInstance(project).snapshot();
            String apiKey = TaideUiSettings.getInstance(project).readApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                throw new TaideException("No API key configured: open Settings → Tools → TAIDE and enter one.");
            }
            Path indexPath = TaidePaths.indexPath(project.getLocationHash());
            if (!Files.exists(indexPath)) {
                throw new TaideException("Knowledge base not built yet: open Settings → Tools → TAIDE → Rebuild Knowledge Base.");
            }
            try {
                OnnxEmbeddingModel model = OnnxEmbeddingModel.openDefaultOr(TaidePaths.modelDir());
                InMemoryVectorIndex index = InMemoryVectorIndex.load(indexPath);
                Retriever retriever = new EmbeddingRetriever(index, model);
                LlmClient llm = new OpenAiCompatibleLlmClient(settings.baseUrl(), apiKey, settings.model());
                PromptLibrary prompts = new PromptLibrary();
                this.core = new TaideServiceCore(retriever, llm, prompts, settings.model());
            } catch (IOException e) {
                throw new TaideException("TAIDE failed to load its knowledge base or model: " + e.getMessage(), e);
            }
            return core;
        }
    }

    @Override
    public TaAnswer ask(String question, CodeContext context) throws TaideException {
        return core().ask(question, context);
    }

    @Override
    public void askStreaming(String question, CodeContext context,
                             Consumer<String> onToken,
                             Consumer<TaAnswer> onComplete,
                             Consumer<Throwable> onError) {
        try {
            core().askStreaming(question, context, onToken, onComplete, onError);
        } catch (TaideException error) {
            onError.accept(error);
        }
    }

    @Override
    public RebuildResult rebuild(Path materialsDirectory, ProgressListener progress, Cancellation cancellation)
            throws Exception {
        SettingsSnapshot settings = TaideUiSettings.getInstance(project).snapshot();
        OnnxEmbeddingModel model = OnnxEmbeddingModel.openDefaultOr(TaidePaths.modelDir());
        Path indexPath = TaidePaths.indexPath(project.getLocationHash());
        IndexBuilder.Result result = new IndexBuilder(model).build(materialsDirectory, indexPath,
                (fraction, detail) -> progress.update(fraction, detail),
                () -> {
                    cancellation.checkCanceled();
                    return false;
                });
        core = null; // next ask rebuilds the service around the fresh index
        return new RebuildResult(result.documentCount(), result.chunkCount());
    }
}
