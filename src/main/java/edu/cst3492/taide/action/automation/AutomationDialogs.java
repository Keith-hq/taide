package edu.cst3492.taide.action.automation;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import edu.cst3492.taide.git.StagedChanges;
import edu.cst3492.taide.rag.RetrievedChunk;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

final class AutomationDialogs {
    private AutomationDialogs() {}

    static void copy(String text) {
        CopyPasteManager.getInstance().setContents(new StringSelection(text));
    }

    static final class Scenario extends DialogWrapper {
        private final JTextArea scenario = AutomationPanels.text("", true, false);

        Scenario(Project project) {
            super(project);
            setTitle("Generate JUnit Tests");
            setOKButtonText("Generate");
            init();
        }

        String requirement() {
            return scenario.getText().strip();
        }

        @Override
        protected JComponent createCenterPanel() {
            var panel = new JPanel(new BorderLayout(0, 8));
            panel.add(new JLabel("Test scenario"), BorderLayout.NORTH);
            scenario.setRows(5);
            scenario.setColumns(55);
            panel.add(new JScrollPane(scenario), BorderLayout.CENTER);
            return panel;
        }

        @Override
        public JComponent getPreferredFocusedComponent() {
            return scenario;
        }

        @Override
        protected @Nullable ValidationInfo doValidate() {
            return requirement().isBlank() || requirement().length() > 4_000
                    ? new ValidationInfo("Enter a test scenario of 1 to 4000 characters.", scenario) : null;
        }
    }

    static final class TestPreview extends DialogWrapper {
        private final JTextArea source;
        private final JComboBox<Path> roots;
        private final String packageName;
        private final boolean hasJunit;
        private final JTextArea destination = AutomationPanels.text("", false, false);

        TestPreview(Project project, GeneratedTest test, List<Path> roots, boolean hasJunit) {
            super(project);
            this.source = AutomationPanels.text(test.source(), true, true);
            this.roots = new JComboBox<>(roots.toArray(Path[]::new));
            this.packageName = test.packageName();
            this.hasJunit = hasJunit;
            destination.setRows(2);
            destination.setOpaque(false);
            destination.setText(root().resolve(test.relativePath()).toString());
            setTitle("Preview JUnit Test");
            setOKButtonText("Save Test");
            init();
        }

        Path root() {
            return (Path) roots.getSelectedItem();
        }

        GeneratedTest generatedTest() {
            return TestGenerator.validate(source.getText(), packageName);
        }

        @Override
        protected JComponent createCenterPanel() {
            var panel = new JPanel(new BorderLayout(0, 8));
            panel.setPreferredSize(new Dimension(780, 520));
            var header = new JPanel(new BorderLayout(8, 0));
            header.add(new JLabel("Test source root"), BorderLayout.WEST);
            header.add(roots, BorderLayout.CENTER);
            panel.add(header, BorderLayout.NORTH);
            panel.add(new JScrollPane(source), BorderLayout.CENTER);
            var footer = new JPanel(new BorderLayout(0, 4));
            footer.add(destination, BorderLayout.CENTER);
            if (!hasJunit) {
                var warning = AutomationPanels.text(
                        "JUnit 5 is absent from this module's dependencies. The test requires JUnit Jupiter.", false, false);
                warning.setOpaque(false);
                warning.setRows(2);
                footer.add(warning, BorderLayout.SOUTH);
            }
            panel.add(footer, BorderLayout.SOUTH);
            return panel;
        }

        @Override
        public JComponent getPreferredFocusedComponent() {
            return source;
        }

        @Override
        protected @Nullable ValidationInfo doValidate() {
            try {
                GeneratedTest test = generatedTest();
                Path target = TestFileWriter.destination(root(), test);
                destination.setText(target.toString());
                return null;
            } catch (AutomationException error) {
                return new ValidationInfo(error.getMessage(), source);
            }
        }
    }

    static final class StagedPreview extends DialogWrapper {
        private final StagedChanges staged;

        StagedPreview(Project project, StagedChanges staged) {
            super(project);
            this.staged = staged;
            setTitle("Staged Changes");
            setOKButtonText("Send to Model");
            init();
        }

        @Override
        protected JComponent createCenterPanel() {
            var panel = new JPanel(new BorderLayout(0, 8));
            panel.setPreferredSize(new Dimension(780, 500));
            var header = AutomationPanels.text(staged.files().size() + " staged files\n"
                    + staged.repositoryRoot(), false, false);
            header.setRows(2);
            header.setOpaque(false);
            panel.add(header, BorderLayout.NORTH);
            panel.add(new JScrollPane(AutomationPanels.text(staged.patch(), false, true)), BorderLayout.CENTER);
            return panel;
        }
    }

    static final class CommitPreview extends DialogWrapper {
        private final JTextArea message;
        private final StagedChanges staged;

        CommitPreview(Project project, String message, StagedChanges staged) {
            super(project);
            this.message = AutomationPanels.text(message, true, false);
            this.staged = staged;
            setTitle("Commit Message");
            setOKButtonText("Commit Staged Files");
            setCancelButtonText("Close");
            init();
        }

        String message() {
            return CommitMessageGenerator.validate(message.getText());
        }

        @Override
        protected JComponent createCenterPanel() {
            var panel = new JPanel(new BorderLayout(0, 8));
            panel.setPreferredSize(new Dimension(680, 300));
            var header = AutomationPanels.text(staged.repositoryRoot().toString(), false, false);
            header.setRows(2);
            header.setOpaque(false);
            panel.add(header, BorderLayout.NORTH);
            panel.add(new JScrollPane(message), BorderLayout.CENTER);
            return panel;
        }

        @Override
        protected Action[] createActions() {
            return new Action[]{new AbstractAction("Copy Message") {
                @Override
                public void actionPerformed(ActionEvent event) {
                    copy(message.getText());
                }
            }, getOKAction(), getCancelAction()};
        }

        @Override
        protected @Nullable ValidationInfo doValidate() {
            try {
                message();
                return null;
            } catch (AutomationException error) {
                return new ValidationInfo(error.getMessage(), message);
            }
        }

        @Override
        public JComponent getPreferredFocusedComponent() {
            return message;
        }
    }

    static final class Review extends DialogWrapper {
        private final CourseReview review;
        private final Consumer<RetrievedChunk> openSource;

        Review(Project project, CourseReview review) {
            this(project, review, AutomationServices.getInstance(project).captureSourceOpener());
        }

        Review(Project project, CourseReview review, Consumer<RetrievedChunk> openSource) {
            super(project);
            this.review = review;
            this.openSource = Objects.requireNonNull(openSource);
            setTitle("Course-Aware Code Review");
            setOKButtonText("Close");
            init();
        }

        @Override
        protected JComponent createCenterPanel() {
            return AutomationPanels.review(review, openSource, AutomationDialogs::copy);
        }

        @Override
        protected Action[] createActions() {
            return new Action[]{getOKAction()};
        }
    }
}
