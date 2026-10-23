# B module sandbox

An auxiliary build for reviewing the native IntelliJ UI and available automation actions.
The root Gradle build provides the integrated test, packaging, and compatibility workflow.

Requires JDK 21, IntelliJ Platform artifacts and network access on first build.

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
.\tools\b-sandbox\run.ps1 buildPlugin
.\tools\b-sandbox\run.ps1 runIde
```

The Gradle source set includes `src/main` plus `integration-support/src/main/java`.
The latter supplies TEAM §4 contracts while the corresponding types are absent from
`src/main`. Each formal type takes priority over its support copy during compilation.
This sandbox package carries the `b-demo` version suffix.

A registers its implementation as a project service for `TaideService` and implements
the optional B-owned `KnowledgeBaseController` SPI for rebuild. B settings expose a
snapshot and an off-EDT PasswordSafe accessor. Demo mode is clearly labelled and on
by default. C's actions use the shared `META-INF/taide-automation.xml` registration
fragment and the project-level `AutomationServices` integration described in
`docs/AUTOMATION.md`.

SDK-free behavioral and native Swing visual checks:

```powershell
.\tools\verify-b.ps1
```

The report and PNGs are generated under `build/b-verification`. They are not browser
mockups: the exact `ChatPanel` Swing class is rendered at 390, 768 and 1440 pixels,
at the 1366×768 project check size, and at a 420-pixel docked panel size.
