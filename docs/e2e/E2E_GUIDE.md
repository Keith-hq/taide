# TAIDE Windows IDEA End-to-End (E2E) Acceptance Guide

This guide provides a walkthrough for manual acceptance on Windows with IntelliJ IDEA Community 2024.2.4. It was checked against `main` at `525b9e0` on 2026-10-26. All outcomes below are expectations; record actual results in [E2E_RESULTS.md](E2E_RESULTS.md). Passing CI or a mock-model test does not complete these scenarios.

---

## Preparation: Plugin Installation

1. Choose a successful [Build and Test run for `main`](https://github.com/Keith-hq/taide/actions/workflows/build.yml?query=branch%3Amain) matching the source commit being accepted, and download its `taide-plugin-<full-commit-sha>` artifact. Alternatively, build that checkout with `.\gradlew.bat buildPlugin` and use the newly generated `build/distributions/taide-0.1.0-SNAPSHOT.zip` (adjust for `pluginVersion` if changed).
2. If downloaded from GitHub, extract the outer zip to obtain `taide-0.1.0-SNAPSHOT.zip`.
3. Open IntelliJ IDEA Community 2024.2.4.
4. Navigate to `File > Settings > Plugins` (or `Ctrl+Alt+S`).
5. Click the gear icon (⚙) at the top of the Plugins dialog and select **Install Plugin from Disk...**.
6. Select `taide-0.1.0-SNAPSHOT.zip` and click OK.
7. Restart IDEA if prompted, open a project and verify that the **TAIDE** tool window appears on the right sidebar.
8. Record the installed ZIP's SHA-256, source commit, plugin version and CI run URL (or local build origin) in the ledger. Compute the hash using `Get-FileHash -LiteralPath '<path-to-inner-plugin.zip>' -Algorithm SHA256`.

Read [TEST_ENVIRONMENT.md](TEST_ENVIRONMENT.md) first. Use a working real-provider key for the six acceptance scenarios. API credentials are project-specific: configuring the main project does not configure `examples/junit-demo` or a temporary Git project. Do not use an older local ZIP merely because its filename matches.

---

## Scenario E2E-01: F0 Configuration & Real Courseware QA

### Objective
Verify that the shared runtime can ingest course materials, build an embedding index, answer student questions, and provide accurate citations.

### Steps
1. In IDEA, navigate to `File > Settings > Tools > TAIDE`.
2. In the **本地检索模型** section, click **下载并配置**, or **使用已有目录…** to select a directory containing the matching `all-MiniLM-L6-v2` `model.onnx` and `vocab.txt` files. Wait for the ready state. If downloading fails, check the IDEA HTTP proxy or use validated existing files; do not continue until model validation succeeds.
3. Configure the connection and course fields:
   - **Provider**: `DeepSeek` (or `AGI` with the institution's endpoint, key and model)
   - **API base URL**: `https://api.deepseek.com` for DeepSeek
   - **API key**: A working test key for that provider and endpoint
   - **Model ID**: `deepseek-chat`
   - **Course directory**: Select an actual readable local PDF/PPTX course folder containing material about Java exceptions. This repository does not include `tests/fixtures/materials`.
4. Uncheck **Use demo service (no network or API key required)**.
5. Click **Apply** and wait until settings and the key are saved. The rebuild button requires the selected course directory to have been applied.
6. Click **Rebuild Knowledge Base** and wait for the settings page to show nonzero document and chunk counts.
7. Click **OK** to close settings.
8. Open the **TAIDE** Tool Window (`View > Tool Windows > TAIDE`).
9. In the prompt input, enter:
   ```text
   What is the difference between checked and unchecked exceptions in Java?
   ```
10. Click **发送** (Send), or press Enter, to start a new question. Existing demo messages remain sample messages after configuration changes.

### Expected Evidence
- Settings page index status with document and chunk counts.
- Response streamed to the chat panel.
- Citations displayed with the answer referencing the actual courseware document name and 1-based slide/page number. Check the cited original passage. A general-knowledge answer with no citations does not pass this course-grounded scenario; confirm the corpus covers the question before retrying.
- Record: Screenshot of Settings with model readiness and index status, plus the real chat response and citation. Hide the API key.

---

## Scenario E2E-02: F1.1 JUnit 5 Test Generation (With Index)

### Objective
Verify that `GenerateTestsAction` generates compilable JUnit 5 tests, provides editable preview, saves cleanly to `src/test/java`, runs green, and does not write files if cancelled.

### Steps
1. Open `examples/junit-demo` as a separate Gradle project in IDEA. Wait for Gradle import/indexing and ensure the project's JDK/Gradle JVM is Java 21. Its build declares JUnit 5 dependencies.
2. In this sample project's TAIDE settings, configure and Apply the real provider, endpoint, model and key. Prepare the course index as in E2E-01 to establish this scenario's indexed-project condition; a different project's index is not reused. F1.1 itself does not need that index.
3. Open `src/main/java/demo/GreetingService.java`.
4. Highlight the `greet` method:
   ```java
   public String greet(String name) {
       if (name == null || name.isBlank()) {
           throw new IllegalArgumentException("name");
       }
       return "Hello " + name.strip();
   }
   ```
5. Right-click the editor and choose **TAIDE > Generate JUnit Tests...**.
6. In the **Generate JUnit Tests** dialog's **Test scenario** field, enter:
   ```text
   Generate JUnit 5 tests covering null, blank, and valid names.
   ```
7. Click **Generate** and wait for the result while checking that the IDEA UI remains responsive.
8. In the **Preview JUnit Test** dialog:
   - Verify that test code uses JUnit 5 (`org.junit.jupiter.api.Test`, `Assertions`).
   - Click **Cancel** first. Verify that **NO** test file is written to `src/test/java`.
9. Repeat steps 5–7.
10. In the preview, review/edit the generated code and select the appropriate **Test source root**. Verify the displayed destination, then click **Save Test**.
11. Verify the generated test is created and opened under `src/test/java/demo/`. The class name is model-generated and may differ from `GreetingServiceTest`; record the actual name. Existing files must not be overwritten.
12. Run the generated test using the green gutter arrow, or run `.\gradlew.bat test --rerun-tasks` from the `examples/junit-demo` directory. Verify that the generated class is included and tests pass. Generation validates syntax and structure; compilation and test success are acceptance checks, not guaranteed model output.

### Expected Evidence
- Scenario dialog screenshot.
- Preview dialog screenshot showing valid JUnit 5 assertions.
- Verification that cancellation created no files.
- Saved test file editor screenshot and green test execution runner tab.

---

## Scenario E2E-03: F1.1 Independent Model Access (Without Index)

### Objective
Verify that `GenerateTestsAction` uses `withModel` independently, without requiring a built course knowledge base index.

### Steps
1. In IDEA, create a brand-new Java project in a temporary directory (e.g., `C:\temp\no-index-demo`).
2. *Do not configure or rebuild any course materials in this project.*
3. In this new project's TAIDE settings, configure the provider, base URL, model and API key, then click **Apply**. Do not prepare the embedding model for this scenario if testing a clean IDE configuration; neither ONNX nor an index is required for F1.1.
4. Create a simple Java class:
   ```java
   package calc;
   public class Calculator {
       public int add(int a, int b) { return a + b; }
   }
   ```
5. Wait for IDEA indexing, select the `add` method in a recognized Java module, right-click, and select **TAIDE > Generate JUnit Tests...**.
6. Enter requirement: `Test positive, negative, and zero additions.` and click **Generate**.
7. Inspect **Preview JUnit Test**, then cancel. A missing-JUnit-dependency warning is expected if the fresh Java project has no JUnit dependency; this scenario verifies model access and preview, not compilation without dependencies.

### Expected Evidence
- Action successfully displays the **Preview JUnit Test** dialog.
- **No error** regarding missing knowledge base or unbuilt index.
- Screenshot of the preview dialog in the fresh unindexed project.

---

## Scenario E2E-04: F1.2 Staged-Only Git Commit Message

### Objective
Verify that `GenerateCommitMessageAction` only reads staged diffs, generates Conventional Commits, requires confirmation, checks fingerprints, and leaves unstaged files untouched.

### Steps
1. Open PowerShell and initialize a temporary Git repository:
   ```powershell
   $taideGitDemo = Join-Path ([System.IO.Path]::GetTempPath()) ("taide-git-demo-" + [guid]::NewGuid().ToString("N"))
   New-Item -ItemType Directory -Path $taideGitDemo | Out-Null
   Set-Location -LiteralPath $taideGitDemo
   git init
   git config user.name "Demo User"
   git config user.email "demo@example.com"
   Set-Content -LiteralPath base.txt -Value "initial content" -Encoding UTF8
   git add base.txt
   git commit -m "chore: initial commit"
   ```
2. Create two modified files:
   ```powershell
   Set-Content -LiteralPath fileA.txt -Value "staged change" -Encoding UTF8
   Set-Content -LiteralPath fileB.txt -Value "unstaged change" -Encoding UTF8
   git add fileA.txt
   ```
3. Open the newly created directory (shown by `$taideGitDemo`) in IDEA. Configure and Apply this project's real provider, endpoint, model and API key; no ONNX model or knowledge-base index is required.
4. Choose **Tools > TAIDE > Generate Commit Message...** (also available in the editor's TAIDE context submenu). The action is not registered in the project-view context menu.
5. In the **Staged Changes** review dialog:
   - Verify that **ONLY** `fileA.txt` appears; `fileB.txt` must **NOT** be listed.
   - Click **Send to Model** to proceed to generation.
6. In the **Commit Message** dialog:
   - Verify that the suggested message conforms to Conventional Commits (e.g., `feat: ...`).
   - Edit the message slightly (e.g., append `(e2e tested)`).
   - Click **Commit Staged Files**.
7. In the separate **Commit Staged Files** confirmation dialog, click **Commit**. This creates a local commit; it does not push to a remote.
8. In PowerShell, check Git status:
   ```powershell
   git log -1 --oneline
   git status
   ```

### Expected Evidence
- Staged Changes dialog screenshot showing only `fileA.txt`.
- Commit preview dialog screenshot with Conventional Commit message.
- PowerShell output showing the new commit hash and verifying `fileB.txt` remains `Untracked` / unstaged.
- Record the installed build and repository path. If staged content changes after preview, the fingerprint check should refuse the stale commit and require regeneration; record that check separately if exercised.

---

## Scenario E2E-05: F1.3 Course-Aware Code Review

### Objective
Verify that `CourseReviewAction` analyzes code against retrieved course materials, identifies anti-patterns, and displays citations.

### Steps
1. In the course project with an active index (from E2E-01), ensure the materials include JDBC/SQL injection guidance. Open or create a Java file containing a clear anti-pattern:
   ```java
   package demo;
   import java.sql.*;
   public class UserDao {
       public void queryUser(Connection conn, String input) throws SQLException {
           Statement stmt = conn.createStatement();
           ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE name = '" + input + "'");
       }
   }
   ```
2. Select the `queryUser` method.
3. Right-click and select **TAIDE > Review Code Against Course...**.
4. In the **Course-Aware Code Review** dialog:
   - Verify findings table lists SQL injection or string concatenation in queries.
   - Verify the displayed **Severity** value (`ERROR` or `WARNING`, as appropriate to the generated finding).
   - Verify the citation list shows the relevant course document (e.g., JDBC lecture slide).

### Expected Evidence
- Review dialog screenshot showing findings, explanation, suggestion and citation metadata. Check the quoted source against the original file. If no course material is retrieved, the general-knowledge banner and empty citations are the correct fallback, but do not count that result as a passed course-grounded review scenario.

---

## Scenario E2E-06: Course Source Navigation

### Objective
Verify that the **Open course source** control opens the referenced courseware through IDEA and displays the target slide/page number for manual navigation.

### Steps
1. In the review dialog from E2E-05, select a finding with citations.
2. Select a citation in the **Sources** list and click the adjacent folder icon with tooltip **Open course source**.
3. Check which editor/preview IDEA opens through `FileEditorManager`; this depends on installed file support and associations. The plugin does not guarantee launching the Windows default reader.
4. Verify the file path matches the directory captured when the review started, including any relative subdirectory in the citation.
5. Verify the **引用位置** message displays the original 1-based slide/page number for manual navigation. PDF/PPTX automatic page deep links are not implemented.
6. Record a failure if IDEA cannot provide a usable opening of that document in the test environment, with the file type and error. Opening a file is not proof that a requested page was automatically selected.

### Expected Evidence
- The page-position dialog and actual IDEA editor/preview opened with the referenced PDF/PPTX; record the file type and path.
- Optional regression: start a review in directory A, change the configured course directory to B after generation, then open the old review's source. It should still resolve in A. Opening the chat tool window beforehand must not be necessary for review navigation.
