# TAIDE End-to-End Acceptance Results & Verification Ledger

This document records the manual verification results of the TAIDE plugin in IntelliJ IDEA Community 2024.2.4 on Windows.

> **Environment Note**: In accordance with the project verification protocol, because the current coding agent environment is a headless Linux WSL2 instance, interactive GUI tests cannot be executed locally and are strictly recorded as **`E2E_NOT_RUN`**. This ledger serves as the handover template for the Windows acceptance tester.

---

## 1. Acceptance Ledger Summary

- **Tester**: (To be filled by Windows tester)
- **Execution Date**: (To be filled by Windows tester)
- **Plugin Distribution**: `taide-0.1.0-SNAPSHOT.zip`
- **Plugin SHA-256**: `4a5a0a66744cabf1ce5df5cee70fa52ba44b94c9d6d0f868cba83cb57894f041`
- **GitHub Actions Run**: [`40268100782`](https://github.com/Keith-hq/taide/actions/runs/40268100782)
- **IntelliJ IDEA Version**: IntelliJ IDEA Community 2024.2.4 (`IC-242.23726.103`)
- **JDK Version**: OpenJDK 21

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
- **Index Stats**: (Expected: Document count > 0, Chunk count > 0)
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
- **Generated File**: `examples/junit-demo/src/test/java/demo/GreetingServiceTest.java`
- **Test Execution Result**: *(Passed / Failed count)*
- **Cancellation Check**: Verified that clicking Cancel creates no files on disk.
- **Screenshots**:
  - `screenshots/e2e-02-preview.png`
  - `screenshots/e2e-02-test-run-green.png`

### E2E-03: F1.1 Without Index
- **Status**: `E2E_NOT_RUN`
- **Target Project**: Standalone unindexed project folder
- **Observation**: Action successfully generates test preview without requesting knowledge base index.
- **Screenshots**:
  - `screenshots/e2e-03-no-index-success.png`

### E2E-04: F1.2 Staged-Only Git
- **Status**: `E2E_NOT_RUN`
- **Repo Location**: Standalone temporary repository
- **Staged File**: `fileA.txt`
- **Unstaged File**: `fileB.txt`
- **Generated Commit Message**: *(e.g. feat: add fileA content)*
- **Post-Commit Status**: `git status` verifies `fileB.txt` is untracked and untouched.
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
- **Action Triggered**: Clicked "Open Source" on citation item
- **Viewer Opened**: *(e.g. Microsoft Edge / Adobe Acrobat Reader)*
- **Slide Prompt**: Target slide number displayed for manual navigation.
- **Screenshots**:
  - `screenshots/e2e-06-opened-pdf.png`
