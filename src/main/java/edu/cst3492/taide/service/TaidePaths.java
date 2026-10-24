package edu.cst3492.taide.service;

import com.intellij.openapi.application.PathManager;
import edu.cst3492.taide.model.ModelSetupService;

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
        return ModelSetupService.getInstance().directory();
    }

    public static Path indexPath(String projectHash) {
        return Path.of(PathManager.getConfigPath(), "taide", "index", projectHash + ".json");
    }
}
