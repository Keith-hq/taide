# TAIDE End-to-End Acceptance Results

Manual acceptance of the TAIDE plugin in IntelliJ IDEA Community 2024.2.4 on Windows.

**Status: all six scenarios passed.**

- Environment: Windows, IntelliJ IDEA Community 2024.2.4 (`IC-242.23726.103`), JDK 21
- Model backend: DeepSeek (`https://api.deepseek.com`, `deepseek-chat`) with the team's own API key
- Embedding model: `all-MiniLM-L6-v2` (`model.onnx` + `vocab.txt`)
- Acceptance build: current `main`

## Results

| Scenario | Scope | Result |
|---|---|---|
| E2E-01 | F0 configuration and course Q&A | PASS |
| E2E-02 | F1.1 JUnit 5 generation with an index | PASS |
| E2E-03 | F1.1 independent model access without an index | PASS |
| E2E-04 | F1.2 staged-only Git commit generation | PASS |
| E2E-05 | F1.3 course-aware review and citations | PASS |
| E2E-06 | F1.3 course source document navigation | PASS |

## What each scenario verified

- **E2E-01** — Course directory configured and the knowledge base rebuilt; a course question answered with a citation naming a real lecture document and page.
- **E2E-02** — A JUnit 5 test class generated from a natural-language scenario, saved into the test source root, compiled and ran green.
- **E2E-03** — JUnit generation works in a project with no index and no embedding model loaded.
- **E2E-04** — Only staged changes enter the diff; the Conventional Commit message is applied after explicit confirmation, and unstaged edits are left untouched.
- **E2E-05** — A Java selection reviewed against retrieved lecture chunks; findings carry severity, explanation, suggestion and citations.
- **E2E-06** — A citation opens the in-IDE preview at the correct 1-based PDF page or PPTX slide; a second citation reuses the same tab.

Execution steps are documented in [E2E_GUIDE.md](E2E_GUIDE.md). Detailed evidence is shown live during the demonstration rather than archived here.
