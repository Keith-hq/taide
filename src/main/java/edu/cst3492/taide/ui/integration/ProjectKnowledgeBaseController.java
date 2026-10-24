package edu.cst3492.taide.ui.integration;

import com.intellij.openapi.project.Project;
import edu.cst3492.taide.service.TaideService;

import java.nio.file.Path;

/** Resolves A's single project service so rebuilding invalidates the same instance used for chat. */
public final class ProjectKnowledgeBaseController implements KnowledgeBaseController {
    private final Project project;

    public ProjectKnowledgeBaseController(Project project) { this.project = project; }

    @Override public RebuildResult rebuild(Path materialsDirectory, ProgressListener progress,
                                           Cancellation cancellation) throws Exception {
        cancellation.checkCanceled();
        TaideService service = project.getService(TaideService.class);
        if (!(service instanceof KnowledgeBaseController controller)) {
            throw new IllegalStateException("The configured TAIDE service does not support knowledge base rebuilding.");
        }
        return controller.rebuild(materialsDirectory, progress, cancellation);
    }
}
