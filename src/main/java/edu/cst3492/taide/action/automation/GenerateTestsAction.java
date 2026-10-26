package edu.cst3492.taide.action.automation;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import edu.cst3492.taide.service.CodeContext;

public final class GenerateTestsAction extends JavaSelectionAction {
    @Override
    public String id() {
        return "taide.generateTests";
    }

    @Override
    public String displayName() {
        return "Generate JUnit Tests";
    }

    @Override
    public void perform(Project project, Editor editor, CodeContext context) {
        var services = AutomationServices.getInstance(project);
        try {
            var selection = ActionCodeContext.capture(project, editor);
            var roots = TestFileWriter.roots(project, selection.file());
            var scenario = new AutomationDialogs.Scenario(project);
            if (!scenario.showAndGet()) {
                return;
            }
            String requirement = scenario.requirement();
            services.background("TAIDE: Generate JUnit Tests",
                    () -> services.withModel(
                            model -> new TestGenerator(model).generate(requirement, context, selection.fullSource())),
                    generated -> DumbService.getInstance(project).runWhenSmart(() -> {
                        if (project.isDisposed()) {
                            return;
                        }
                        if (!selection.file().isValid()) {
                            services.error("The source file is no longer available.");
                            return;
                        }
                        var root = LocalFileSystem.getInstance().findFileByNioFile(roots.getFirst());
                        var module = ModuleUtilCore.findModuleForFile(root == null ? selection.file() : root, project);
                        var scope = module == null ? GlobalSearchScope.projectScope(project)
                                : GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true);
                        boolean hasJunit = JavaPsiFacade.getInstance(project)
                                .findClass("org.junit.jupiter.api.Test", scope) != null;
                        var preview = new AutomationDialogs.TestPreview(project, generated, roots, hasJunit);
                        if (preview.showAndGet()) {
                            try {
                                var file = TestFileWriter.write(project, preview.root(), preview.generatedTest());
                                FileEditorManager.getInstance(project).openFile(file, true);
                            } catch (AutomationException error) {
                                services.error(error.getMessage());
                            }
                        }
                    }));
        } catch (AutomationException error) {
            services.error(error.getMessage());
        }
    }
}
