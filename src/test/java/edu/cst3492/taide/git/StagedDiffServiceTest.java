package edu.cst3492.taide.git;

import edu.cst3492.taide.action.automation.AutomationException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StagedDiffServiceTest {
    @TempDir
    Path directory;
    Git git;
    final StagedDiffService service = new StagedDiffService();

    @BeforeEach
    void createRepository() throws Exception {
        git = Git.init().setDirectory(directory.toFile()).call();
        var config = git.getRepository().getConfig();
        config.setString("user", null, "name", "TAIDE Test");
        config.setString("user", null, "email", "taide-test@example.invalid");
        config.save();
    }

    @AfterEach
    void closeRepository() {
        git.close();
    }

    private void stage(String name, String text) throws Exception {
        Path file = directory.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
        git.add().addFilepattern(name).call();
    }

    private void baseline() throws Exception {
        stage("README.md", "baseline\n");
        git.commit().setMessage("chore: initialize repository").call();
    }

    @Test
    void readsInitialCommitWithoutHead() throws Exception {
        stage("src/First.java", "class First {}\n");
        StagedChanges changes = service.read(directory.resolve("src"));
        assertEquals(directory, changes.repositoryRoot());
        assertEquals(1, changes.files().size());
        assertEquals("ADD", changes.files().getFirst().type());
        assertTrue(changes.patch().contains("+class First {}"));
    }

    @Test
    void includesAllStagedFilesAndExcludesUnstagedAndUntrackedContent() throws Exception {
        baseline();
        stage("a.txt", "staged A\n");
        stage("nested/b.txt", "staged B\n");
        Files.writeString(directory.resolve("a.txt"), "unstaged secret\n");
        Files.writeString(directory.resolve("untracked.txt"), "untracked secret\n");
        StagedChanges changes = service.read(directory);
        assertEquals(2, changes.files().size());
        assertTrue(changes.patch().contains("+staged A"));
        assertTrue(changes.patch().contains("+staged B"));
        assertFalse(changes.patch().contains("unstaged secret"));
        assertFalse(changes.patch().contains("untracked secret"));
    }

    @Test
    void reportsRenamesAndDeletions() throws Exception {
        stage("old.txt", "rename content\n");
        stage("removed.txt", "delete content\n");
        git.commit().setMessage("chore: add baseline files").call();
        Files.move(directory.resolve("old.txt"), directory.resolve("new.txt"));
        git.rm().addFilepattern("old.txt").addFilepattern("removed.txt").call();
        git.add().addFilepattern("new.txt").call();
        StagedChanges changes = service.read(directory);
        assertTrue(changes.files().stream().anyMatch(file -> file.type().equals("RENAME")
                && file.oldPath().equals("old.txt") && file.newPath().equals("new.txt")));
        assertTrue(changes.files().stream().anyMatch(file -> file.type().equals("DELETE")));
    }

    @Test
    void binaryContentIsMetadataOnlyAndStillChangesFingerprint() throws Exception {
        baseline();
        Files.write(directory.resolve("image.bin"), new byte[]{0, 1, 2});
        git.add().addFilepattern("image.bin").call();
        StagedChanges first = service.read(directory);
        assertTrue(first.files().getFirst().binary());
        Files.write(directory.resolve("image.bin"), new byte[]{0, 3, 4});
        git.add().addFilepattern("image.bin").call();
        StagedChanges second = service.read(directory);
        assertNotEquals(first.fingerprint(), second.fingerprint());
        assertTrue(second.patch().contains("Binary files"));
    }

    @Test
    void rejectsEmptyNonGitAndOversizedDiffs() throws Exception {
        assertThrows(AutomationException.class, () -> service.read(directory));
        Path outside = Files.createTempDirectory("taide-outside-");
        try {
            assertThrows(AutomationException.class, () -> service.read(outside));
        } finally {
            Files.delete(outside);
        }
        stage("large.txt", "a".repeat(1_000));
        assertThrows(AutomationException.class, () -> new StagedDiffService(32).read(directory));
    }

    @Test
    void rejectsMergeConflicts() throws Exception {
        var cache = git.getRepository().lockDirCache();
        try (var inserter = git.getRepository().newObjectInserter()) {
            var builder = cache.builder();
            for (int stage = 1; stage <= 3; stage++) {
                var entry = new DirCacheEntry("conflict.txt", stage);
                entry.setFileMode(FileMode.REGULAR_FILE);
                entry.setObjectId(inserter.insert(Constants.OBJ_BLOB, ("stage" + stage).getBytes()));
                builder.add(entry);
            }
            inserter.flush();
            builder.commit();
        } finally {
            cache.unlock();
        }
        assertThrows(AutomationException.class, () -> service.read(directory));
    }

    @Test
    void preventsCommitAfterIndexChanges() throws Exception {
        baseline();
        stage("a.txt", "first\n");
        StagedChanges approved = service.read(directory);
        stage("a.txt", "second\n");
        var head = git.getRepository().resolve(Constants.HEAD);
        assertThrows(AutomationException.class, () -> service.commit(approved, "feat: add text"));
        assertEquals(head, git.getRepository().resolve(Constants.HEAD));
    }

    @Test
    void createsOnlyApprovedStagedCommitAndPreservesWorkingTreeEdits() throws Exception {
        baseline();
        stage("a.txt", "staged version\n");
        StagedChanges approved = service.read(directory);
        Files.writeString(directory.resolve("a.txt"), "unstaged version\n");
        String commit = service.commit(approved, "feat: add staged text");
        assertEquals(commit, git.getRepository().resolve(Constants.HEAD).name());
        assertEquals("unstaged version\n", Files.readString(directory.resolve("a.txt")));
        var status = git.status().call();
        assertTrue(status.getChanged().isEmpty());
        assertTrue(status.getModified().contains("a.txt"));
    }

    @Test
    void changedHeadInvalidatesApproval() throws Exception {
        baseline();
        stage("a.txt", "staged\n");
        StagedChanges approved = service.read(directory);
        git.commit().setMessage("feat: other commit").call();
        stage("b.txt", "new staged\n");
        assertThrows(AutomationException.class, () -> service.commit(approved, "feat: outdated message"));
    }
}
