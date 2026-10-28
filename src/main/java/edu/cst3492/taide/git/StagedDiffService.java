package edu.cst3492.taide.git;

import edu.cst3492.taide.action.automation.AutomationException;
import edu.cst3492.taide.action.automation.CommitMessageGenerator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class StagedDiffService {
    public static final int MAX_DIFF_BYTES = 180_000;
    private static final int MAX_FILES = 500;
    private final int byteLimit;

    public StagedDiffService() {
        this(MAX_DIFF_BYTES);
    }

    public StagedDiffService(int byteLimit) {
        if (byteLimit < 1) {
            throw new IllegalArgumentException("The diff size limit must be positive.");
        }
        this.byteLimit = byteLimit;
    }

    public StagedChanges read(Path directory) {
        try (Repository repository = open(directory)) {
            return read(repository);
        } catch (IOException error) {
            throw new AutomationException("The staged Git diff could not be read. Check the repository.", error);
        }
    }

    public String commit(StagedChanges approved, String message) {
        String validated = CommitMessageGenerator.validate(message);
        try (Repository repository = open(approved.repositoryRoot())) {
            StagedChanges current = read(repository);
            if (!current.fingerprint().equals(approved.fingerprint())) {
                throw new AutomationException("The staged files or HEAD changed. Generate a new commit message.");
            }
            if (!repository.getRepositoryState().canCommit()) {
                throw new AutomationException("Finish the current Git operation before committing.");
            }
            if (repository.getConfig().getString("user", null, "name") == null
                    || repository.getConfig().getString("user", null, "email") == null) {
                throw new AutomationException("Configure Git user.name and user.email before committing.");
            }
            try (Git git = new Git(repository)) {
                return git.commit().setMessage(validated).call().getName();
            }
        } catch (GitAPIException | IOException error) {
            throw new AutomationException(
                    "Git could not create the commit. Check hooks, signing, and repository permissions.", error);
        }
    }

    private StagedChanges read(Repository repository) throws IOException {
        DirCache index = repository.readDirCache();
        if (index.hasUnmergedPaths()) {
            throw new AutomationException("Resolve staged merge conflicts before generating a commit message.");
        }
        ObjectId head = repository.resolve(Constants.HEAD);
        var output = new LimitedOutputStream(byteLimit);
        List<StagedChanges.FileChange> files = new ArrayList<>();
        try (var reader = repository.newObjectReader();
             var walk = new RevWalk(repository);
             var formatter = new DiffFormatter(output)) {
            AbstractTreeIterator oldTree = new EmptyTreeIterator();
            if (head != null) {
                oldTree = new CanonicalTreeParser(null, reader, walk.parseCommit(head).getTree());
            }
            formatter.setRepository(repository);
            formatter.setDetectRenames(true);
            formatter.setPathFilter(TreeFilter.ALL);
            formatter.setBinaryFileThreshold(1_048_576);
            // 新仓库以空树为基线，右侧始终使用暂存区快照。
            List<DiffEntry> entries = formatter.scan(oldTree, new DirCacheIterator(index));
            if (entries.isEmpty()) {
                throw new AutomationException("The staging area is empty. Stage the intended files first.");
            }
            if (entries.size() > MAX_FILES) {
                throw new AutomationException("More than 500 files are staged. Split the changes into smaller commits.");
            }
            for (DiffEntry entry : entries) {
                FileHeader header = formatter.toFileHeader(entry);
                files.add(new StagedChanges.FileChange(
                        entry.getChangeType().name(), entry.getOldPath(), entry.getNewPath(),
                        header.getPatchType() == FileHeader.PatchType.BINARY));
                formatter.format(entry);
            }
            formatter.flush();
        } catch (DiffLimitException error) {
            throw new AutomationException(
                    "The complete staged diff exceeds " + byteLimit + " bytes. Stage a smaller set of changes.");
        }
        return new StagedChanges(repository.getWorkTree().toPath(), output.text(), files, fingerprint(index, head));
    }

    private static Repository open(Path directory) throws IOException {
        var builder = new FileRepositoryBuilder().findGitDir(directory.toFile());
        if (builder.getGitDir() == null) {
            throw new AutomationException("Select a project or file inside a Git repository.");
        }
        Repository repository = builder.build();
        if (repository.isBare()) {
            repository.close();
            throw new AutomationException("A Git working tree is required for this action.");
        }
        return repository;
    }

    private static String fingerprint(DirCache index, ObjectId head) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, head == null ? "" : head.name());
            for (int position = 0; position < index.getEntryCount(); position++) {
                var entry = index.getEntry(position);
                update(digest, entry.getPathString());
                update(digest, Integer.toString(entry.getStage()));
                update(digest, Integer.toString(entry.getRawMode()));
                update(digest, entry.getObjectId().name());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 must be available in Java 21.", error);
        }
    }

    private static void update(MessageDigest digest, String text) {
        digest.update(text.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static final class LimitedOutputStream extends OutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final int limit;

        private LimitedOutputStream(int limit) {
            this.limit = limit;
        }

        @Override
        public void write(int value) throws IOException {
            ensureCapacity(1);
            buffer.write(value);
        }

        @Override
        public void write(byte[] data, int offset, int length) throws IOException {
            ensureCapacity(length);
            buffer.write(data, offset, length);
        }

        private void ensureCapacity(int extra) throws DiffLimitException {
            if (extra > limit - buffer.size()) {
                throw new DiffLimitException();
            }
        }

        private String text() {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    private static final class DiffLimitException extends IOException {}
}
