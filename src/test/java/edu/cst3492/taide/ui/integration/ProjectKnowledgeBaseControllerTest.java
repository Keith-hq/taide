package edu.cst3492.taide.ui.integration;

import com.intellij.openapi.project.Project;
import edu.cst3492.taide.service.CodeContext;
import edu.cst3492.taide.service.TaAnswer;
import edu.cst3492.taide.service.TaideService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ProjectKnowledgeBaseControllerTest {
    @Test void rebuildUsesTheSameServiceAsChat() throws Exception {
        RecordingService service = new RecordingService();
        var adapter = new ProjectKnowledgeBaseController(projectWith(service));
        var progress = (KnowledgeBaseController.ProgressListener) (fraction, detail) -> { };
        var cancellation = (KnowledgeBaseController.Cancellation) () -> { };
        Path folder = Path.of("course-materials");
        assertEquals(new KnowledgeBaseController.RebuildResult(2, 8), adapter.rebuild(folder, progress, cancellation));
        assertSame(folder, service.folder);
        assertSame(progress, service.progress);
        assertSame(cancellation, service.cancellation);
    }

    @Test void unsupportedServiceHasAnActionableError() {
        var adapter = new ProjectKnowledgeBaseController(projectWith(null));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> adapter.rebuild(Path.of("course-materials"), (f, d) -> { }, () -> { }));
        assertTrue(failure.getMessage().contains("does not support knowledge base rebuilding"));
    }

    @Test void canceledRebuildDoesNotResolveOrCallTheBackend() {
        AtomicInteger resolved = new AtomicInteger();
        Project project = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(), new Class<?>[]{Project.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getService")) resolved.incrementAndGet();
                    return null;
                });
        var adapter = new ProjectKnowledgeBaseController(project);
        assertThrows(CancellationException.class, () -> adapter.rebuild(Path.of("course-materials"), (f, d) -> { },
                () -> { throw new CancellationException(); }));
        assertEquals(0, resolved.get());
    }

    private static Project projectWith(TaideService service) {
        return (Project) Proxy.newProxyInstance(Project.class.getClassLoader(), new Class<?>[]{Project.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getService")) {
                        assertEquals(TaideService.class, args[0]);
                        return service;
                    }
                    throw new AssertionError("Unexpected Project method: " + method.getName());
                });
    }

    private static final class RecordingService implements TaideService, KnowledgeBaseController {
        private Path folder;
        private ProgressListener progress;
        private Cancellation cancellation;

        @Override public RebuildResult rebuild(Path folder, ProgressListener progress, Cancellation cancellation) {
            this.folder = folder;
            this.progress = progress;
            this.cancellation = cancellation;
            return new RebuildResult(2, 8);
        }

        @Override public TaAnswer ask(String question, CodeContext context) { throw new UnsupportedOperationException(); }
        @Override public void askStreaming(String question, CodeContext context, Consumer<String> onToken,
                                           Consumer<TaAnswer> onComplete, Consumer<Throwable> onError) {
            throw new UnsupportedOperationException();
        }
    }
}
