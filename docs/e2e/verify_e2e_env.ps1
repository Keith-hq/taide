# ==============================================================================
# TAIDE Windows Environment & Artifact Verification Script
# Usage: powershell -ExecutionPolicy Bypass -File .\docs\e2e\verify_e2e_env.ps1
# ==============================================================================

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  TAIDE E2E Environment Pre-Flight Check (Windows Host)  " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$passed = $true

# 1. Check Java JDK
Write-Host "`n[1/4] Checking Java JDK..." -NoNewline
try {
    $javaVerOutput = java -version 2>&1 | Out-String
    if ($javaVerOutput -match 'version "(\d+)') {
        $majorVer = [int]$matches[1]
        if ($majorVer -ge 21) {
            Write-Host " [OK] Java $majorVer detected" -ForegroundColor Green
        } else {
            Write-Host " [WARN] Java $majorVer detected (Java 21 required)" -ForegroundColor Yellow
            $passed = $false
        }
    } else {
        Write-Host " [FAIL] Could not parse java version string" -ForegroundColor Red
        $passed = $false
    }
} catch {
    Write-Host " [FAIL] java command not found in PATH" -ForegroundColor Red
    $passed = $false
}

# 2. Check Git
Write-Host "[2/4] Checking Git..." -NoNewline
try {
    $gitVer = git --version
    Write-Host " [OK] $gitVer" -ForegroundColor Green
} catch {
    Write-Host " [FAIL] git command not found in PATH" -ForegroundColor Red
    $passed = $false
}

# 3. Check Demo Project
Write-Host "[3/4] Checking examples/junit-demo..." -NoNewline
$demoPath = Join-Path $PSScriptRoot "..\..\examples\junit-demo"
if (Test-Path $demoPath) {
    Write-Host " [OK] Found at $demoPath" -ForegroundColor Green
} else {
    Write-Host " [FAIL] Missing examples/junit-demo directory" -ForegroundColor Red
    $passed = $false
}

# 4. Check Plugin ZIP Artifact
Write-Host "[4/4] Checking TAIDE Plugin Distribution..."
$possibleZipPaths = @(
    (Join-Path $PSScriptRoot "..\..\build\distributions\taide-0.1.0-SNAPSHOT.zip"),
    ".\taide-0.1.0-SNAPSHOT.zip",
    "C:\taide-plugin.zip"
)

$foundZip = $null
foreach ($path in $possibleZipPaths) {
    if (Test-Path $path) {
        $foundZip = (Resolve-Path $path).Path
        break
    }
}

if ($foundZip) {
    Write-Host "  Found plugin artifact: $foundZip" -ForegroundColor Gray
    $fileHash = (Get-FileHash -Path $foundZip -Algorithm SHA256).Hash.ToLower()
    Write-Host "  SHA-256: $fileHash" -ForegroundColor Gray
    Write-Host "  [NOTICE] File found; verify its source commit and record its hash before installation." -ForegroundColor Yellow
} else {
    Write-Host "  [NOTICE] No local taide-0.1.0-SNAPSHOT.zip found in standard paths." -ForegroundColor Yellow
    Write-Host "  Download taide-plugin-<commit> from a successful main build matching the source under test:" -ForegroundColor Yellow
    Write-Host "  https://github.com/Keith-hq/taide/actions/workflows/build.yml?query=branch%3Amain" -ForegroundColor Cyan
}

Write-Host "`n----------------------------------------------------------" -ForegroundColor Cyan
if ($passed) {
    Write-Host "  Basic checks passed. Follow E2E_GUIDE.md to verify IDEA, the artifact, model, key and course index." -ForegroundColor Green
} else {
    Write-Host "  Please resolve warnings/failures before running acceptance." -ForegroundColor Yellow
}
Write-Host "----------------------------------------------------------`n" -ForegroundColor Cyan
