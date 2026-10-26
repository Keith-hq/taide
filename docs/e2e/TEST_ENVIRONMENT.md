# TAIDE E2E Test Environment Specification

This document records the exact configuration, dependencies, and environment prerequisites for conducting the TAIDE IntelliJ Platform Plugin End-to-End (E2E) verification on a Windows 10/11 host.

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
- **Package Size**: ~113 MB (Contains packaged dependencies including JGit, ONNX Runtime, and Markdown renderer)
- **GitHub Actions Source**: Run ID [`40268100782`](https://github.com/Keith-hq/taide/actions/runs/40268100782)
  - Artifact Name: `taide-plugin-74ea4425d50e0056d3a21c23e545aac1a446ccdd`
  - *Note: GitHub Actions wraps artifacts in an outer ZIP. Extract the outer ZIP first to obtain `taide-0.1.0-SNAPSHOT.zip`.*

---

## 4. Model & Knowledge Base Configuration

### 4.1 Embedding Model
- **Model**: `bge-small-zh-v1.5` (ONNX format)
- **Default Location**: `%APPDATA%\JetBrains\IdeaIC2024.2\taide\model\` or local custom directory
- **Required Files**:
  - `model.onnx`
  - `tokenizer.json`

### 4.2 Language Model Provider (F0, F1.1, F1.2, F1.3)
In `Settings > Tools > TAIDE`:
- **Mode**: Uncheck `Use demo service (no network or API key required)`.
- **Provider**: `DeepSeek` or `Custom OpenAI-compatible`
- **Base URL**: `https://api.deepseek.com` (or local mock server `http://127.0.0.1:8000/v1`)
- **Model ID**: `deepseek-chat`
- **API Key**: Testing key (authorized for course verification, never commit to git)

---

## 5. Security & Isolation Constraints

1. **No Production Index Deletion**: Never manually delete production index directories to simulate no-index scenarios; use separate project folders.
2. **Credential Safety**: Never hardcode API keys into demo project files or Git commit messages.
3. **Staged Git Isolation**: Always test F1.2 inside a standalone temporary Git repository outside the main project workspace.
