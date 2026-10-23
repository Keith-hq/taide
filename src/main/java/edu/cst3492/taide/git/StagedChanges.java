package edu.cst3492.taide.git;

import java.nio.file.Path;
import java.util.List;

public record StagedChanges(Path repositoryRoot, String patch, List<FileChange> files, String fingerprint) {
    public StagedChanges {
        files = List.copyOf(files);
    }

    public record FileChange(String type, String oldPath, String newPath, boolean binary) {}
}
