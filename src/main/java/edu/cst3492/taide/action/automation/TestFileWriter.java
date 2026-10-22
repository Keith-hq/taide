package edu.cst3492.taide.action.automation;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class TestFileWriter {
    private TestFileWriter() {}

    static List<Path> roots(Project project, VirtualFile sourceFile) {
        var module = ModuleUtilCore.findModuleForFile(sourceFile, project);
        if (module == null) {
            throw new AutomationException("Open the Java file as part of a project module before generating tests.");
        }
        var roots = new ArrayList<Path>();
        for (var entry : ModuleRootManager.getInstance(module).getContentEntries()) {
            for (var folder : entry.getSourceFolders()) {
                if (folder.getRootType().equals(JavaSourceRootType.TEST_SOURCE) && folder.getFile() != null) {
                    roots.add(Path.of(folder.getFile().getPath()));
                }
            }
        }
        if (roots.isEmpty()) {
            Path buildRoot = buildRoot(Path.of(sourceFile.getPath()).getParent());
            if (buildRoot != null) {
                // Gradle 常将 main 与 test 导入为不同模块，按最近的构建文件匹配测试源集。
                for (var candidate : ModuleManager.getInstance(project).getModules()) {
                    for (var entry : ModuleRootManager.getInstance(candidate).getContentEntries()) {
                        for (var folder : entry.getSourceFolders()) {
                            if (folder.getRootType().equals(JavaSourceRootType.TEST_SOURCE) && folder.getFile() != null) {
                                Path root = Path.of(folder.getFile().getPath());
                                if (Objects.equals(buildRoot, buildRoot(root))) {
                                    roots.add(root);
                                }
                            }
                        }
                    }
                }
                if (roots.isEmpty()) {
                    roots.add(buildRoot.resolve("src/test/java"));
                }
                return roots.stream().distinct().toList();
            }
            VirtualFile content = ProjectFileIndex.getInstance(project).getContentRootForFile(sourceFile);
            if (content == null) {
                throw new AutomationException("The Java module has no writable content root.");
            }
            roots.add(Path.of(content.getPath(), "src", "test", "java"));
        }
        return List.copyOf(roots);
    }

    static Path buildRoot(Path directory) {
        for (Path current = directory; current != null; current = current.getParent()) {
            if (Files.isRegularFile(current.resolve("build.gradle.kts"))
                    || Files.isRegularFile(current.resolve("build.gradle"))
                    || Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
        }
        return null;
    }

    static Path destination(Path root, GeneratedTest test) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path target = normalizedRoot.resolve(test.relativePath()).normalize();
        if (!target.startsWith(normalizedRoot) || target.equals(normalizedRoot)) {
            throw new AutomationException("The generated test path is outside the selected test source root.");
        }
        for (Path parent = target; parent != null; parent = parent.getParent()) {
            if (Files.isSymbolicLink(parent)) {
                throw new AutomationException("Choose a test destination with ordinary directories instead of symbolic links.");
            }
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new AutomationException("A test file with this name already exists. Rename the generated class.");
        }
        return target;
    }

    static VirtualFile write(Project project, Path root, GeneratedTest test) {
        Path target = destination(root, test);
        VirtualFile[] created = new VirtualFile[1];
        try {
            WriteCommandAction.writeCommandAction(project).withName("Create TAIDE JUnit Test").withGlobalUndo().run(() -> {
                destination(root, test);
                VirtualFile directory = VfsUtil.createDirectoryIfMissing(
                        FileUtil.toSystemIndependentName(target.getParent().toString()));
                if (directory == null) {
                    throw new AutomationException("The test directory could not be created.");
                }
                String name = target.getFileName().toString();
                if (directory.findChild(name) != null) {
                    throw new AutomationException("A test file with this name already exists. Rename the generated class.");
                }
                created[0] = directory.createChildData(TestFileWriter.class, name);
                try {
                    VfsUtil.saveText(created[0], test.source());
                } catch (IOException error) {
                    created[0].delete(TestFileWriter.class);
                    throw error;
                }
            });
        } catch (IOException error) {
            throw new AutomationException("The test file could not be written. Check directory permissions.", error);
        }
        return created[0];
    }
}
