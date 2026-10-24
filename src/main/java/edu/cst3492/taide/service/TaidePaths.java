package edu.cst3492.taide.service;

import com.intellij.openapi.application.PathManager;

import java.nio.file.Path;

/**
 * Where TAIDE keeps its runtime files inside the IDE config directory
 * (TEAM 4.5: never the project directory, never the repository):
 * {@code <config>/taide/model/} and {@code <config>/taide/index/<project-hash>.json}.
 * Dev runs can override the model dir via taide.model.dir / TAIDE_MODEL_DIR.
 */
public final class TaidePaths {

    private TaidePaths() {
    }

    public static Path modelDir() {
        String property = System.getProperty("taide.model.dir");
        String env = System.getenv("TAIDE_MODEL_DIR");
        String value = property != null && !property.isBlank() ? property
                : env != null && !env.isBlank() ? env : null;
        if (value != null) return Path.of(value);
        return Path.of(PathManager.getConfigPath(), "taide", "model");
    }

    public static Path indexPath(String projectHash) {
        return Path.of(PathManager.getConfigPath(), "taide", "index", projectHash + ".json");
    }
}
