#requires -Version 7.0
[CmdletBinding()]
param(
    [switch]$Offline,
    [string]$MavenRepository
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'development-environment.ps1')
$receipt = Join-Path $projectRoot 'target/verification/harnesses/results.json'

Push-Location -LiteralPath $projectRoot
try {
    $environment = Initialize-DevelopmentEnvironment
    Write-Host "Validating with JDK 21 ($($environment.Java)) and Maven ($($environment.Maven))."
    # A failed child must never leave an old success receipt usable by this run.
    Remove-Item -LiteralPath $receipt -Force -ErrorAction SilentlyContinue
    $mavenArgs = @()
    if ($Offline) { $mavenArgs += '-o' }
    if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$MavenRepository" }
    & $environment.Maven @mavenArgs clean package
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed (exit $LASTEXITCODE)." }
    & $environment.Maven @mavenArgs spotless:check
    if ($LASTEXITCODE -ne 0) { throw "Formatting validation failed (exit $LASTEXITCODE). Run mvn spotless:apply and review the pilot diff." }
    Write-Host 'Formatting validation: PASS (pilot allowlist only).'

    $shell = (Get-Process -Id $PID).Path
    $harnessArgs = @('-NoProfile', '-File', (Join-Path $PSScriptRoot 'run-harnesses.ps1'))
    if ($Offline) { $harnessArgs += '-Offline' }
    if ($MavenRepository) { $harnessArgs += @('-MavenRepository', $MavenRepository) }
    & $shell @harnessArgs
    if ($LASTEXITCODE -ne 0) { throw "Harness regression failed (exit $LASTEXITCODE). See target/verification/harnesses/." }
    $summary = Get-Content -LiteralPath $receipt -Raw | ConvertFrom-Json
    if ($summary.Discovered -le 0 -or $summary.Discovered -ne $summary.Executed -or
        $summary.Executed -ne $summary.Passed -or $summary.Failed -ne 0 -or
        $summary.MissingInvalid -ne 0 -or $summary.Failure -or $summary.TotalChecks -le 0 -or
        @($summary.Results).Count -ne $summary.Executed -or
        @($summary.Results | Where-Object { $_.Status -ne 'PASS' -or $_.ExitCode -ne 0 -or $_.Checks -le 0 }).Count -gt 0) {
        throw 'Harness receipt is incomplete or unsuccessful. All discovered harnesses must execute and pass.'
    }
    Write-Host "VERIFICATION SUCCEEDED: Build and formatting successful; $($summary.Passed) harnesses, $($summary.TotalChecks) checks; Failed=0 Missing/Invalid=0."
    exit 0
} catch {
    Write-Host "VERIFICATION FAILED: $($_.Exception.Message)"
    exit 1
} finally {
    Pop-Location
}
