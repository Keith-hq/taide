# TAIDE Windows IDEA End-to-End (E2E) Acceptance Guide

This guide provides a reproducible walkthrough for team members executing manual acceptance of the TAIDE plugin on Windows with IntelliJ IDEA Community 2024.2.4.

---

## Preparation: Plugin Installation

1. Download the artifact from GitHub Actions Run [`40268100782`](https://github.com/Keith-hq/taide/actions/runs/40268100782) (or use local `build/distributions/taide-0.1.0-SNAPSHOT.zip`).
2. If downloaded from GitHub, extract the outer zip to obtain `taide-0.1.0-SNAPSHOT.zip`.
3. Open IntelliJ IDEA Community 2024.2.4.
4. Navigate to `File > Settings > Plugins` (or `Ctrl+Alt+S`).
5. Click the gear icon (⚙) at the top of the Plugins dialog and select **Install Plugin from Disk...**.
6. Select `taide-0.1.0-SNAPSHOT.zip` and click OK.
7. Restart IDEA or verify that the **TAIDE** tool window appears on the right sidebar.

---

## Scenario E2E-01: F0 Configuration & Real Courseware QA

### Objective
Verify that the shared runtime can ingest course materials, build an embedding index, answer student questions, and provide accurate citations.

### Steps
1. In IDEA, navigate to `File > Settings > Tools > TAIDE`.
2. Uncheck **Use demo service (no network or API key required)**.
3. Configure:
   - **Provider**: `DeepSeek` (or OpenAI-compatible mock server)
   - **API Key**: Testing key
   - **Model ID**: `deepseek-chat`
   - **Materials Directory**: Select the local folder containing course slides (e.g., `tests/fixtures/materials`).
4. Click **Rebuild Knowledge Base** and wait for the indexing notification (document count and chunk count).
5. Open the **TAIDE** Tool Window (`View > Tool Windows > TAIDE`).
6. In the prompt input, enter:
   ```text
   What is the difference between checked and unchecked exceptions in Java?
   ```
7. Click Send (or press Enter).

### Expected Evidence
- Index rebuild notification with document and chunk counts.
- Response streamed to the chat panel.
- Citations displayed below the answer referencing the courseware document name and slide number.
- Record: Screenshot of Settings dialog, notification, and chat response with citation.

---

## Scenario E2E-02: F1.1 JUnit 5 Test Generation (With Index)

### Objective
Verify that `GenerateTestsAction` generates compilable JUnit 5 tests, provides editable preview, saves cleanly to `src/test/java`, runs green, and does not write files if cancelled.

### Steps
1. Open the sample project in IDEA: `examples/junit-demo`.
2. Open `src/main/java/demo/GreetingService.java`.
3. Highlight the `greet` method:
   ```java
   public String greet(String name) {
       if (name == null || name.isBlank()) {
           throw new IllegalArgumentException("name");
       }
       return "Hello " + name.strip();
   }
   ```
4. Right-click the editor and choose **TAIDE > Generate JUnit Tests**.
5. In the **Test Scenario Requirements** dialog, enter:
   ```text
   Generate JUnit 5 tests covering null, blank, and valid names.
   ```
6. Click OK. The background progress indicator runs off-EDT.
7. In the **Generated Test Preview** dialog:
   - Verify that test code uses JUnit 5 (`org.junit.jupiter.api.Test`, `Assertions`).
   - Click **Cancel** first. Verify that **NO** test file is written to `src/test/java`.
8. Repeat steps 4–6.
9. In the Preview dialog, click **Save and Open Test File**.
10. Verify that `src/test/java/demo/GreetingServiceTest.java` is created and opened.
11. Click the green gutter run arrow next to `GreetingServiceTest` to execute the tests.

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
3. Ensure LLM API Key is configured in settings.
4. Create a simple Java class:
   ```java
   package calc;
   public class Calculator {
       public int add(int a, int b) { return a + b; }
   }
   ```
5. Select the `add` method, right-click, and select **TAIDE > Generate JUnit Tests**.
6. Enter requirement: `Test positive, negative, and zero additions.` and click OK.

### Expected Evidence
- Action successfully displays the Generated Test Preview dialog.
- **No error** regarding missing knowledge base or unbuilt index.
- Screenshot of the preview dialog in the fresh unindexed project.

---

## Scenario E2E-04: F1.2 Staged-Only Git Commit Message

### Objective
Verify that `GenerateCommitMessageAction` only reads staged diffs, generates Conventional Commits, requires confirmation, checks fingerprints, and leaves unstaged files untouched.

### Steps
1. Open PowerShell and initialize a temporary Git repository:
   ```powershell
   mkdir C:\temp\taide-git-demo; cd C:\temp\taide-git-demo
   git init
   git config user.name "Demo User"
   git config user.email "demo@example.com"
   "initial content" > base.txt
   git add base.txt
   git commit -m "chore: initial commit"
   ```
2. Create two modified files:
   ```powershell
   "staged change" > fileA.txt
   "unstaged change" > fileB.txt
   git add fileA.txt
   ```
3. Open the `C:\temp\taide-git-demo` directory in IntelliJ IDEA.
4. Right-click the project view or editor and select **TAIDE > Generate Commit Message**.
5. In the **Staged Changes** review dialog:
   - Verify that **ONLY** `fileA.txt` appears; `fileB.txt` must **NOT** be listed.
   - Click OK to proceed to generation.
6. In the **Commit Message Preview** dialog:
   - Verify that the suggested message conforms to Conventional Commits (e.g., `feat: ...`).
   - Edit the message slightly (e.g., append `(e2e tested)`).
   - Click OK.
7. In the confirmation dialog (`Commit Staged Files?`), click **Commit**.
8. In PowerShell, check Git status:
   ```powershell
   git log -1 --oneline
   git status
   ```

### Expected Evidence
- Staged Changes dialog screenshot showing only `fileA.txt`.
- Commit preview dialog screenshot with Conventional Commit message.
- PowerShell output showing the new commit hash and verifying `fileB.txt` remains `Untracked` / unstaged.

---

## Scenario E2E-05: F1.3 Course-Aware Code Review

### Objective
Verify that `CourseReviewAction` analyzes code against retrieved course materials, identifies anti-patterns, and displays citations.

### Steps
1. In the course project with active index (from E2E-01), open or create a Java file containing a clear anti-pattern:
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
3. Right-click and select **TAIDE > Review Code Against Course**.
4. In the **Course-Aware Code Review** dialog:
   - Verify findings table lists SQL injection or string concatenation in queries.
   - Verify Severity is highlighted (`ERROR` or `WARNING`).
   - Verify the citation list shows the relevant course document (e.g., JDBC lecture slide).

### Expected Evidence
- Review dialog screenshot showing findings, explanation, suggestion, and citation metadata.

---

## Scenario E2E-06: Course Source Navigation

### Objective
Verify that clicking **Open Source** opens the referenced courseware document and displays the target slide/page number.

### Steps
1. In the review dialog from E2E-05, select a finding with citations.
2. In the citations table, click the **Open Source** button (or link).
3. Verify that the Windows default document reader (Acrobat Reader, Edge, PowerPoint, etc.) opens the file.
4. Verify the file path matches the configured materials directory.
5. Verify the prompt displays the target slide or page number for manual navigation.

### Expected Evidence
- Notification / dialog showing target slide number.
- External document viewer opened with the course PDF/PPTX.
