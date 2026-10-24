param([string]$JavaHome)
$ErrorActionPreference = 'Stop'
$taideRoot = Split-Path $PSScriptRoot -Parent
if (-not $JavaHome) { $JavaHome = $env:JAVA_HOME }
if (-not $JavaHome) {
    $taideJavac = Get-Command javac -ErrorAction SilentlyContinue
    if ($taideJavac) { $JavaHome = Split-Path (Split-Path $taideJavac.Source -Parent) -Parent }
}
if (-not $JavaHome) { $JavaHome = 'C:\Program Files\JetBrains\PyCharm 2025.1.3.1\jbr' }
$taideJava = Join-Path $JavaHome 'bin\java.exe'
$taideJavac = Join-Path $JavaHome 'bin\javac.exe'
if (-not (Test-Path -LiteralPath $taideJavac)) { throw 'JDK 21 required. Supply -JavaHome or set JAVA_HOME.' }
$taideOutput = Join-Path $taideRoot 'build\b-verification'
$taideClasses = Join-Path $taideOutput 'classes'
New-Item -ItemType Directory -Path $taideClasses -Force | Out-Null
$taideSources = @(
    # Prefer A's real contract when present, matching the root Gradle source set.
    $taideSupport = Join-Path $taideRoot 'integration-support\src\main\java'
    Get-ChildItem -LiteralPath $taideSupport -Filter '*.java' -Recurse | ForEach-Object {
        $taideRelative = $_.FullName.Substring($taideSupport.Length + 1)
        $taideRealContract = Join-Path (Join-Path $taideRoot 'src\main\java') $taideRelative
        if (Test-Path -LiteralPath $taideRealContract) { Get-Item -LiteralPath $taideRealContract } else { $_ }
    }
    Get-ChildItem -LiteralPath (Join-Path $taideRoot 'src\main\java\edu\cst3492\taide\ui\chat') -Filter '*.java'
    Get-Item -LiteralPath (Join-Path $taideRoot 'src\main\java\edu\cst3492\taide\context\SelectionRange.java')
    Get-Item -LiteralPath (Join-Path $taideRoot 'src\main\java\edu\cst3492\taide\ui\settings\SettingsSnapshot.java')
    Get-Item -LiteralPath (Join-Path $taideRoot 'src\main\java\edu\cst3492\taide\ui\settings\SettingsValidation.java')
    Get-Item -LiteralPath (Join-Path $taideRoot 'src\test\java\edu\cst3492\taide\context\SelectionRangeTest.java')
    Get-Item -LiteralPath (Join-Path $taideRoot 'src\test\java\edu\cst3492\taide\ui\chat\ConversationControllerTest.java')
    Get-Item -LiteralPath (Join-Path $taideRoot 'src\test\java\edu\cst3492\taide\ui\chat\ChatPanelTest.java')
    Get-Item -LiteralPath (Join-Path $taideRoot 'src\test\java\edu\cst3492\taide\ui\chat\CitationFilesTest.java')
    Get-Item -LiteralPath (Join-Path $taideRoot 'src\test\java\edu\cst3492\taide\ui\settings\SettingsValidationTest.java')
    Get-Item -LiteralPath (Join-Path $taideRoot 'tools\preview\NativePreview.java')
) | ForEach-Object { $_.FullName }
& $taideJavac -encoding UTF-8 --release 21 -d $taideClasses @taideSources
if ($LASTEXITCODE -ne 0) { throw 'B verification compilation failed' }
$taideChecks = @('edu.cst3492.taide.context.SelectionRangeTest', 'edu.cst3492.taide.ui.chat.ConversationControllerTest', 'edu.cst3492.taide.ui.chat.ChatPanelTest', 'edu.cst3492.taide.ui.chat.CitationFilesTest', 'edu.cst3492.taide.ui.settings.SettingsValidationTest')
foreach ($taideCheck in $taideChecks) {
    & $taideJava '-Dfile.encoding=UTF-8' -cp $taideClasses $taideCheck
    if ($LASTEXITCODE -ne 0) { throw "Failed: $taideCheck" }
}
& $taideJava '-Djava.awt.headless=true' '-Dfile.encoding=UTF-8' -cp $taideClasses NativePreview (Join-Path $taideOutput 'screenshots')
if ($LASTEXITCODE -ne 0) { throw 'Native Swing rendering failed' }
Write-Output "B verification complete: $taideOutput"
