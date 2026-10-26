# TAIDE End-to-End Acceptance Results & Verification Ledger

This document records the manual verification results of the TAIDE plugin in IntelliJ IDEA Community 2024.2.4 on Windows.

> **Evidence status (2026-10-26)**: These scenarios were originally handed over from a headless WSL2 development environment. No completed Windows GUI execution or screenshots have been entered in this ledger. All six therefore remain **`E2E_NOT_RUN`**, regardless of the current editor/agent environment. Source review and automated CI results do not change manual acceptance status.

---

## 1. Acceptance Ledger Summary

- **Tester**: (To be filled by Windows tester)
- **Execution Date**: (To be filled by Windows tester)
- **Installed Plugin Version / Distribution**: (Record actual version and inner ZIP filename; current source version: `0.1.0-SNAPSHOT`)
- **Source Commit**: (Record full SHA matching the installed build)
- **Plugin SHA-256**: (Compute from the installed inner ZIP with `Get-FileHash -LiteralPath '<plugin.zip>' -Algorithm SHA256`)
- **GitHub Actions Run / Local Build Origin**: (Record matching run URL, or local build command and source commit; identify any local modifications)
- **IntelliJ IDEA Version**: (Record actual version/build; target: Community 2024.2.4, `IC-242.23726.103`)
- **JDK Version**: (Record actual version; build and sample-project target: Java 21)
- **Provider / Base URL / Model ID**: (Record non-secret configuration; identify mock tests separately)
- **Course Materials / Model Directory**: (Record tested locations and corpus; embedding model: `all-MiniLM-L6-v2`, `model.onnx` and `vocab.txt`)
- **Display / Scale / Theme**: (Record actual settings)

Historical handover provenance, retained for traceability only: run [`40268100782`](https://github.com/Keith-hq/taide/actions/runs/40268100782), artifact `taide-plugin-74ea4425d50e0056d3a21c23e545aac1a446ccdd`, reported inner-ZIP SHA-256 `4a5a0a66744cabf1ce5df5cee70fa52ba44b94c9d6d0f868cba83cb57894f041`. These values do not identify a later build or prove any scenario passed. Use the build selection instructions in [E2E_GUIDE.md](E2E_GUIDE.md) for a new acceptance run.

Enter `PASS` or `FAIL` only after execution, with tester/date and actual evidence; keep `E2E_NOT_RUN` otherwise. The screenshot paths below are requested evidence filenames, not existing attachments. Do not include credentials in screenshots or logs.

| Scenario ID | Test Scope | Status | Executed By | Evidence Attached |
|---|---|---|---|---|
| **E2E-01** | F0 Configuration & Course QA | `E2E_NOT_RUN` | Pending Windows Host | Not attached |
| **E2E-02** | F1.1 JUnit 5 Generation (With Index) | `E2E_NOT_RUN` | Pending Windows Host | Not attached |
| **E2E-03** | F1.1 Independent Model Access (No Index) | `E2E_NOT_RUN` | Pending Windows Host | Not attached |
| **E2E-04** | F1.2 Staged-Only Git Commit Generation | `E2E_NOT_RUN` | Pending Windows Host | Not attached |
| **E2E-05** | F1.3 Course-Aware Review & Citations | `E2E_NOT_RUN` | Pending Windows Host | Not attached |
| **E2E-06** | F1.3 Course Source Document Navigation | `E2E_NOT_RUN` | Pending Windows Host | Not attached |

---

## 2. Detailed Execution Logs & Evidence Placeholders

### E2E-01: F0 Configuration & Course QA
- **Status**: `E2E_NOT_RUN`
- **Index Stats**: *(Pending: record actual document and chunk counts; expected both > 0)*
- **Prompt**: `What is the difference between checked and unchecked exceptions in Java?`
- **Output Sample**: *(Paste first 100 characters of response)*
- **Citation Recorded**: *(e.g. Lec-3.1-Exceptions.pdf Slide 9)*
- **Screenshots**:
  - `screenshots/e2e-01-settings.png`
  - `screenshots/e2e-01-qa-citation.png`

### E2E-02: F1.1 JUnit 5 Generation
- **Status**: `E2E_NOT_RUN`
- **Target File**: `examples/junit-demo/src/main/java/demo/GreetingService.java`
- **Requirement Input**: `Generate JUnit 5 tests covering null, blank, and valid names.`
- **Generated File**: *(Pending: record actual generated class/path under `examples/junit-demo/src/test/java/demo/`; class name may vary)*
- **Test Execution Result**: *(Passed / Failed count)*
- **Cancellation Check**: *(Pending: confirm whether Cancel left the test directory unchanged)*
- **Screenshots**:
  - `screenshots/e2e-02-preview.png`
  - `screenshots/e2e-02-test-run-green.png`

### E2E-03: F1.1 Without Index
- **Status**: `E2E_NOT_RUN`
- **Target Project**: Standalone unindexed project folder
- **Observation**: *(Pending: record whether Preview JUnit Test opens without an index or embedding model; record dependency warnings separately)*
- **Screenshots**:
  - `screenshots/e2e-03-no-index-success.png`

### E2E-04: F1.2 Staged-Only Git
- **Status**: `E2E_NOT_RUN`
- **Repo Location**: Standalone temporary repository
- **Staged File**: `fileA.txt`
- **Unstaged File**: `fileB.txt`
- **Generated Commit Message**: *(e.g. feat: add fileA content)*
- **Post-Commit Status**: *(Pending: paste new commit hash and `git status --short`; expected `fileB.txt` remains untracked and untouched)*
- **Screenshots**:
  - `screenshots/e2e-04-staged-dialog.png`
  - `screenshots/e2e-04-git-log.png`

### E2E-05: F1.3 Course-Aware Review
- **Status**: `E2E_NOT_RUN`
- **Anti-Pattern Tested**: Unchecked SQL string concatenation or empty catch block
- **Finding Title**: *(e.g. SQL Injection via Statement concatenation)*
- **Severity**: *(ERROR / WARNING / INFO)*
- **Citation Match**: *(e.g. Lec-08-JDBC.pdf, Slide 14, score 0.82)*
- **Screenshots**:
  - `screenshots/e2e-05-review-findings.png`

### E2E-06: Course Source Navigation
- **Status**: `E2E_NOT_RUN`
- **Action Triggered**: *(Pending: select a Sources entry and click the Open course source folder icon)*
- **Viewer Opened / Resolved Path**: *(Pending: record actual IDEA editor/preview and resolved PDF/PPTX path; an external default reader is not guaranteed)*
- **Slide Prompt**: *(Pending: record displayed 1-based page/slide; manual navigation is expected, with no automatic page deep link)*
- **Screenshots**:
  - `screenshots/e2e-06-opened-pdf.png`
