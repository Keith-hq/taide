package edu.cst3492.taide.action.automation;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import edu.cst3492.taide.llm.LlmClient;
import edu.cst3492.taide.rag.RetrievedChunk;
import edu.cst3492.taide.rag.Retriever;
import edu.cst3492.taide.service.AutomationRuntimeAccess;
import edu.cst3492.taide.service.TaideException;
import edu.cst3492.taide.service.TaideService;
import edu.cst3492.taide.ui.integration.CourseSourceNavigator;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

@Service(Service.Level.PROJECT)
public final class AutomationServices {
    private final Project project;
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile Bindings bindings = new Bindings(null, null, null);

    public AutomationServices(Project project) {
        this.project = project;
    }

    public static AutomationServices getInstance(Project project) {
        return project.getService(AutomationServices.class);
    }

    public void bind(LlmClient model, Retriever retriever, Consumer<RetrievedChunk> sourceOpener) {
        bindings = new Bindings(Objects.requireNonNull(model), Objects.requireNonNull(retriever),
                Objects.requireNonNull(sourceOpener));
    }

    public <T> T withModel(Function<LlmClient, T> operation) {
        Objects.requireNonNull(operation, "operation");
        if (project.isDisposed()) {
            throw new AutomationException("TAIDE project has been closed.");
        }
        LlmClient testModel = bindings.model();
        if (testModel != null) {
            return operation.apply(testModel);
        }
        AutomationRuntimeAccess runtime = runtimeAccess();
        try {
            return runtime.withModel(operation);
        } catch (ProcessCanceledException error) {
            throw error;
        } catch (TaideException error) {
            throw new AutomationException(error.getMessage(), error);
        }
    }

    public <T> T withModelAndRetriever(BiFunction<LlmClient, Retriever, T> operation) {
        Objects.requireNonNull(operation, "operation");
        if (project.isDisposed()) {
            throw new AutomationException("TAIDE project has been closed.");
        }
        LlmClient testModel = bindings.model();
        Retriever testRetriever = bindings.retriever();
        if (testModel != null && testRetriever != null) {
            return operation.apply(testModel, testRetriever);
        }
        AutomationRuntimeAccess runtime = runtimeAccess();
        try {
            return runtime.withModelAndRetriever(operation);
        } catch (ProcessCanceledException error) {
            throw error;
        } catch (TaideException error) {
            throw new AutomationException(error.getMessage(), error);
        }
    }

    private AutomationRuntimeAccess runtimeAccess() {
        TaideService service = project.getService(TaideService.class);
        if (service instanceof AutomationRuntimeAccess runtime) {
            return runtime;
        }
        throw new AutomationException(
                "The TAIDE language-model service is unavailable. Configure the model integration first.");
    }

    public LlmClient model() {
        LlmClient model = bindings.model();
        if (model == null) {
            model = project.getService(LlmClient.class);
        }
        if (model == null) {
            throw new AutomationException("The TAIDE language-model service is unavailable. Configure the model integration first.");
        }
        return model;
    }

    public Retriever retriever() {
        Retriever retriever = bindings.retriever();
        if (retriever == null) {
            retriever = project.getService(Retriever.class);
        }
        if (retriever == null) {
            throw new AutomationException("The TAIDE course retriever is unavailable. Configure the knowledge-base integration first.");
        }
        return retriever;
    }

    public boolean canOpenSources() {
        return !project.isDisposed();
    }

    /** Captures the navigation binding and course directory for one review request. */
    public Consumer<RetrievedChunk> captureSourceOpener() {
        var opener = bindings.sourceOpener();
        if (opener == null) {
            opener = CourseSourceNavigator.getInstance(project).snapshot();
        }
        var captured = opener;
        return source -> {
            if (project.isDisposed()) {
                return;
            }
            try {
                captured.accept(source);
            } catch (ProcessCanceledException error) {
                throw error;
            } catch (Exception error) {
                error("The course document could not be opened. Check its local location.");
            }
        };
    }

    public void openSource(RetrievedChunk source) {
        captureSourceOpener().accept(source);
    }

    public boolean isBusy() {
        return busy.get();
    }

    public <T> void background(String title, Callable<T> work, Consumer<T> onSuccess) {
        if (!busy.compareAndSet(false, true)) {
            error("TAIDE is handling another automation request. Wait for it to finish or cancel it.");
            return;
        }
        new Task.Backgroundable(project, title, true) {
            private T result;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.checkCanceled();
                try {
                    result = work.call();
                    indicator.checkCanceled();
                } catch (ProcessCanceledException error) {
                    throw error;
                } catch (Exception error) {
                    throw error instanceof AutomationException automation
                            ? automation : new AutomationException(
                            "The automation request failed. Check the model configuration or connection and retry.", error);
                }
            }

            @Override
            public void onSuccess() {
                busy.set(false);
                if (!project.isDisposed()) {
                    try {
                        onSuccess.accept(result);
                    } catch (ProcessCanceledException error) {
                        throw error;
                    } catch (AutomationException error) {
                        error(error.getMessage());
                    } catch (Exception error) {
                        error("The automation preview could not be displayed. Check the project and retry.");
                    }
                }
            }

            @Override
            public void onCancel() {
                busy.set(false);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                busy.set(false);
                if (!project.isDisposed()) {
                    error(error instanceof AutomationException ? error.getMessage() : "The automation request failed.");
                }
            }
        }.queue();
    }

    public void error(String text) {
        notify(text, NotificationType.ERROR);
    }

    public void info(String text) {
        notify(text, NotificationType.INFORMATION);
    }

    private void notify(String text, NotificationType type) {
        if (!project.isDisposed()) {
            NotificationGroupManager.getInstance().getNotificationGroup("TAIDE Automation")
                    .createNotification("TAIDE", text, type).notify(project);
        }
    }

    private record Bindings(LlmClient model, Retriever retriever, Consumer<RetrievedChunk> sourceOpener) {}
}
