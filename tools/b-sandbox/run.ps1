param([ValidateSet('buildPlugin','runIde','verifyPluginStructure','compileJava')][string]$Task = 'buildPlugin')
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) {
    $taideJbr = 'C:\Program Files\JetBrains\PyCharm 2025.1.3.1\jbr'
    if (Test-Path -LiteralPath "$taideJbr\bin\javac.exe") { $env:JAVA_HOME = $taideJbr }
}
Push-Location $PSScriptRoot
try { & .\gradlew.bat $Task --console=plain; if ($LASTEXITCODE -ne 0) { throw "Gradle failed ($LASTEXITCODE)" } }
finally { Pop-Location }
