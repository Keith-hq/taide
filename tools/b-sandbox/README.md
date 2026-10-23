# B module sandbox

An isolated build for reviewing B's native IntelliJ UI before A and C deliver their modules.
It intentionally does not create or replace C's root Gradle build.

Requires JDK 21, IntelliJ Platform artifacts and network access on first build.

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
.\tools\b-sandbox\run.ps1 buildPlugin
.\tools\b-sandbox\run.ps1 runIde
```

The Gradle source set includes `src/main` plus `integration-support/src/main/java`.
The latter reproduces TEAM §4 contracts solely for this sandbox. When A's same-named
types arrive under `src/main`, remove the support directory from this source set;
do not copy or package duplicate classes. The sandbox package is an explicit B demo,
not an integrated production release.

A registers its implementation as a project service for `TaideService` and implements
the optional B-owned `KnowledgeBaseController` SPI for rebuild. B settings expose a
snapshot and an off-EDT PasswordSafe accessor. Demo mode is clearly labelled and on
by default. C's three action registrations remain XML comments until real class names
and implementations are supplied. No runtime references to missing C classes.

SDK-free behavioral and native Swing visual checks:

```powershell
.\tools\verify-b.ps1
```

The report and PNGs are generated under `build/b-verification`. They are not browser
mockups: the exact `ChatPanel` Swing class is rendered at 390, 768 and 1440 pixels,
at the 1366×768 project check size, and at a 420-pixel docked panel size.
