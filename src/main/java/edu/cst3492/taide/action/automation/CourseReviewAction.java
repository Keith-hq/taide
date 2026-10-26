package edu.cst3492.taide.action.automation;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import edu.cst3492.taide.service.CodeContext;

public final class CourseReviewAction extends JavaSelectionAction {
    @Override
    public String id() {
        return "taide.courseReview";
    }

    @Override
    public String displayName() {
        return "Review Code Against Course";
    }

    @Override
    public void perform(Project project, Editor editor, CodeContext context) {
        var services = AutomationServices.getInstance(project);
        var openSource = services.captureSourceOpener();
        services.background("TAIDE: Review Code Against Course",
                () -> services.withModelAndRetriever(
                        (model, retriever) -> new CourseReviewGenerator(model, retriever).generate(context)),
                review -> new AutomationDialogs.Review(project, review, openSource).show());
    }
}
