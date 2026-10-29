# TAIDE E2E Test Environment Specification

This document describes the configuration and prerequisites for manual TAIDE acceptance on Windows 10/11. Documentation was checked against `main` at `525b9e0` on 2026-10-26; this source review does not constitute a completed GUI test.

---

## 1. Hardware & Operating System Baseline

- **Operating System**: Microsoft Windows 10 (Version 22H2+) or Windows 11 (64-bit)
- **Architecture**: x86_64 / amd64
- **Minimum Display Resolution**: 1366 × 768 (Recommended: 1920 × 1080)
- **Available RAM**: Minimum 8 GB (16 GB recommended for running IDEA + local ONNX model)
- **Available Disk Space**: At least 5 GB free disk space

---

## 2. Software Runtime Requirements

| Component | Target Version | Verification Command | Notes |
|---|---|---|---|
| **IntelliJ IDEA** | Community 2024.2.4 (`IC-242.23726.103`) | `Help > About` | Target platform for CST3492 course submission |
| **Java JDK** | OpenJDK 21 (Temurin / JBR 21) | `java -version` | Toolchain Java 21 required for building and testing |
| **Git** | 2.40+ for Windows | `git --version` | Required for F1.2 JGit compatibility and demo repo setup |
| **PowerShell** | 5.1+ or PowerShell 7+ | `$PSVersionTable.PSVersion` | Used for executing `verify_e2e_env.ps1` validation script |

---

## 3. Plugin Artifact Information

- **Plugin Identifier**: `edu.cst3492.taide`
- **Current Version**: `0.1.0-SNAPSHOT`
- **Distribution Package**: `taide-0.1.0-SNAPSHOT.zip`
- **Artifact selection**: Choose a successful [Build and Test run for `main`](https://github.com/Keith-hq/taide/actions/workflows/build.yml?query=branch%3Amain), and download `taide-plugin-<full-commit-sha>` from that run. Match the run's commit to the source being accepted. Alternatively, build that checkout with `.\gradlew.bat buildPlugin`.
- **Version check**: The filename above reflects the current `pluginVersion` in `gradle.properties`; use the actual generated filename if that version changes.
- **Artifact wrapper**: GitHub Actions wraps artifacts in an outer ZIP. Extract the outer ZIP first and install the inner plugin distribution ZIP.
- **Evidence**: Record the installed ZIP's SHA-256, full source commit, actual version and CI run URL in [E2E_RESULTS.md](E2E_RESULTS.md) before testing. A local ZIP can be stale even if its filename is unchanged.
- **Historical handover only**: Run [`40268100782`](https://github.com/Keith-hq/taide/actions/runs/40268100782) supplied artifact `taide-plugin-74ea4425d50e0056d3a21c23e545aac1a446ccdd`. It is not evidence for a later `main` build or a completed manual acceptance run.

---

## 4. Model & Knowledge Base Configuration

### 4.1 Embedding Model and Course Materials (F0, F1.3)
- **Model**: `sentence-transformers/all-MiniLM-L6-v2` (384-dimensional ONNX embedding model).
- **Preparation**: In `Settings > Tools > TAIDE`, click **下载并配置** (Download and configure), or **使用已有目录…** (Use existing directory). Wait until the model is reported ready. Downloading is initiated by the user; the plugin checks the pinned files' sizes and SHA-256 values.
- **Default Location**: `<IDE config directory>\taide\model\`. A normal Windows IDEA Community 2024.2 installation commonly uses `%APPDATA%\JetBrains\IdeaIC2024.2\taide\model\`; the development sandbox may differ. Use the model directory shown in TAIDE settings as the actual path.
- **Reuse**: The selected model directory is application-wide and can be reused by projects in the same IDE configuration. Per-project API credentials and index data are configured separately.
- **Required Files**:
  - `model.onnx`
  - `vocab.txt`
- **Model revision**: `1110a243fdf4706b3f48f1d95db1a4f5529b4d41`, pinned by `ModelInstaller`. Do not substitute a BGE model or `tokenizer.json`.
- **Network failure**: Configure IDEA's HTTP proxy if appropriate, then retry; an existing directory must contain the matching pinned files and pass validation. Model setup does not require a course index or API key.
- **Course materials**: Provide a readable local directory of actual PDF/PPTX course files. Include the relevant topic for the chosen QA/review example; `tests/fixtures/materials` is not supplied by this repository.
- **Index**: Apply the course directory in settings, then click **Rebuild Knowledge Base**. Wait for nonzero document and chunk counts in the settings page's status area. The index is stored at `<IDE config directory>\taide\index\<project-hash>.json`.

F1.1 and F1.2 use model-only access: they require a configured language-model endpoint and key, but do not require these embedding files, course materials or an index.

### 4.2 Language Model Provider (F0, F1.1, F1.2, F1.3)
In `Settings > Tools > TAIDE`:
- **Mode**: Uncheck `Use demo service (no network or API key required)` for real F0 chat acceptance. The default chat demo produces fixed, labeled samples. F1 actions use the configured live service independently of that chat demo switch.
- **Provider**: The dropdown offers `DeepSeek` and `AGI`; acceptance used `DeepSeek` with the team's own key.
- **API base URL**: `https://api.deepseek.com` for DeepSeek. The `AGI` option expects the institution's OpenAI-compatible base URL. Use the provider's model ID and its own key. There is no separate `Custom OpenAI-compatible` dropdown option.
- **Model ID**: `deepseek-chat`
- **API key**: A working key for the selected provider and endpoint. Save with **Apply**; keys are stored through IDE PasswordSafe, scoped by project/provider/endpoint. A newly opened sample or temporary project needs its own key configuration.
- **Apply before use**: Save the API fields, course directory and intended demo state before rebuilding or invoking an action. Leave the key field blank only when retaining a key already stored for that scope.

A local OpenAI-compatible mock endpoint, such as `http://127.0.0.1:8000/v1`, is allowed for isolated integration testing when a matching server is running. Record mock tests separately: fixed/mock responses do not demonstrate real model quality or satisfy the real-provider E2E scenarios.

### 4.3 Course Source Viewer

TAIDE resolves citations within the captured course directory and opens its built-in read-only source preview at the exact physical 1-based PDF page or PPTX slide. No external PDF/PPTX viewer is required for citation navigation. Check the rendered content as well as the page/slide field: a numeric label alone is insufficient evidence. The toolbar supports Previous, Next, Go and zoom; Open original separately uses IDEA's file association. Fonts and complex presentation effects may differ from Office, and slide animations are not played.

---

## 5. Security & Isolation Constraints

1. **No Production Index Deletion**: Never manually delete production index directories to simulate no-index scenarios; use separate project folders.
2. **Credential Safety**: Never hardcode API keys into demo project files or Git commit messages.
3. **Staged Git Isolation**: Always test F1.2 inside a standalone temporary Git repository outside the main project workspace.
