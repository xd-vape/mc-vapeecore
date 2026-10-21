#requires -Version 7.0
# Runs only disposable project copies. No live server/source/configuration is mutated.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$shell = (Get-Process -Id $PID).Path
$scriptsRoot = Split-Path -Parent $PSScriptRoot
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('vapeecore-verification-' + [guid]::NewGuid().ToString('N'))
$checks = 0
function Assert([bool]$Condition, [string]$Reason) {
    $script:checks++
    if (-not $Condition) { throw $Reason }
}
function Write-Fixture([string]$Relative, [string]$Value) {
    $path = Join-Path $fixtureRoot $Relative
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $path) | Out-Null
    [IO.File]::WriteAllText($path, $Value)
}
function Invoke-Fixture([string]$Entry = 'verify.ps1') {
    $output = & $shell -NoProfile -File (Join-Path $fixtureRoot "scripts/$Entry") -Offline -MavenRepository (Join-Path $fixtureRoot 'repository with spaces') 2>&1 | Out-String
    return [pscustomobject]@{ Code = $LASTEXITCODE; Output = $output }
}
function Server-Snapshot {
    return @(Get-ChildItem -LiteralPath (Join-Path $fixtureRoot 'dev-server') -File -Recurse |
        ForEach-Object { $_.FullName + ':' + (Get-FileHash -LiteralPath $_.FullName).Hash }) -join "`n"
}
try {
    New-Item -ItemType Directory -Force -Path (Join-Path $fixtureRoot 'scripts') | Out-Null
    Copy-Item -LiteralPath (Join-Path $scriptsRoot 'verify.ps1'), (Join-Path $scriptsRoot 'build-and-deploy.ps1') -Destination (Join-Path $fixtureRoot 'scripts')
    Write-Fixture 'scripts/development-environment.ps1' @'
function Initialize-DevelopmentEnvironment {
    if (Test-Path (Join-Path $PSScriptRoot '../missing-prerequisite')) { throw 'Java 21 JDK is required. Configure JAVA_HOME.' }
    return [pscustomobject]@{ Maven = (Join-Path $PSScriptRoot 'fixture-maven.ps1'); Java = 'fixture JDK 21' }
}
'@
    Write-Fixture 'scripts/fixture-maven.ps1' @'
$root = Split-Path -Parent $PSScriptRoot
$args | ConvertTo-Json | Set-Content (Join-Path $root 'build-arguments.json')
if (Test-Path (Join-Path $root 'build-failure')) { exit 17 }
New-Item -ItemType Directory -Force -Path (Join-Path $root 'target') | Out-Null
Set-Content (Join-Path $root 'target/vapeecore-1.0-SNAPSHOT.jar') 'new-artifact'
exit 0
'@
    Write-Fixture 'scripts/run-harnesses.ps1' @'
param([switch]$Offline, [string]$MavenRepository)
$root = Split-Path -Parent $PSScriptRoot
@{ Offline = [bool]$Offline; Repository = $MavenRepository } | ConvertTo-Json | Set-Content (Join-Path $root 'harness-arguments.json')
if (Test-Path (Join-Path $root 'harness-failure')) { exit 23 }
if (Test-Path (Join-Path $root 'missing-receipt')) { exit 0 }
$receipt = Join-Path $root 'target/verification/harnesses/results.json'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $receipt) | Out-Null
if (Test-Path (Join-Path $root 'malformed-receipt')) { Set-Content $receipt 'not JSON'; exit 0 }
$result = @{ Discovered = 1; Executed = 1; Passed = 1; Failed = 0; MissingInvalid = 0; TotalChecks = 3; Failure = $null
    Results = @(@{ Class = 'FixtureHarness'; Status = 'PASS'; ExitCode = 0; Checks = 3 }) }
if (Test-Path (Join-Path $root 'incomplete-receipt')) { $result.Executed = 0 }
if (Test-Path (Join-Path $root 'invalid-output')) { $result.MissingInvalid = 1 }
$result | ConvertTo-Json -Depth 5 | Set-Content $receipt
exit 0
'@
    Write-Fixture 'scripts/stop-dev-server.ps1' 'Set-Content (Join-Path $PSScriptRoot "../stop-called") "stopped"; if (Test-Path (Join-Path $PSScriptRoot "../stop-failure")) { exit 29 }; exit 0'
    Write-Fixture 'scripts/start-dev-server.ps1' 'throw "Verification must never start a server"'
    Write-Fixture 'dev-server/plugins/vapeecore-1.0-SNAPSHOT.jar' 'previous-artifact'
    Write-Fixture 'dev-server/plugins/VapeeCore/config.yml' 'operator: unchanged'
    Write-Fixture 'dev-server/.vapeecore-dev/server.pid' 'fixture-only'
    $before = Server-Snapshot
    $success = Invoke-Fixture
    Assert ($success.Code -eq 0 -and $success.Output.Contains('VERIFICATION SUCCEEDED')) 'Success must exit zero with a clear result.'
    Assert ((Server-Snapshot) -eq $before -and -not (Test-Path (Join-Path $fixtureRoot 'stop-called'))) 'Verify must not deploy or stop/start a server.'
    $buildArgs = Get-Content (Join-Path $fixtureRoot 'build-arguments.json') -Raw | ConvertFrom-Json
    Assert ($buildArgs -contains '-o' -and $buildArgs -contains "-Dmaven.repo.local=$(Join-Path $fixtureRoot 'repository with spaces')") 'Build must forward offline and the exact custom repository.'
    Assert ($buildArgs -contains 'clean' -and $buildArgs -contains 'package') 'Verify must package the plugin.'
    $harnessArgs = Get-Content (Join-Path $fixtureRoot 'harness-arguments.json') -Raw | ConvertFrom-Json
    Assert ($harnessArgs.Offline -and $harnessArgs.Repository -eq (Join-Path $fixtureRoot 'repository with spaces')) 'Harness runner must receive offline and the custom repository.'

    foreach ($failure in 'build-failure', 'harness-failure', 'missing-prerequisite', 'missing-receipt', 'incomplete-receipt', 'invalid-output', 'malformed-receipt') {
        # Leave a valid old receipt first: even an exit-zero child without new output must fail.
        Assert ((Invoke-Fixture).Code -eq 0) "Fixture reset before $failure"
        Write-Fixture $failure 'fail'
        Remove-Item (Join-Path $fixtureRoot 'harness-arguments.json') -ErrorAction SilentlyContinue
        $run = Invoke-Fixture
        Assert ($run.Code -ne 0 -and $run.Output.Contains('VERIFICATION FAILED')) "$failure must fail clearly."
        Assert (-not $run.Output.Contains('VERIFICATION SUCCEEDED')) "$failure must not claim success."
        if ($failure -in 'build-failure', 'missing-prerequisite') {
            Assert (-not (Test-Path (Join-Path $fixtureRoot 'harness-arguments.json'))) "$failure must stop before regression."
        }
        if ($failure -eq 'missing-prerequisite') { Assert ($run.Output.Contains('JAVA_HOME')) 'Prerequisite failure must explain the remedy.' }
        Assert ((Server-Snapshot) -eq $before) "$failure must leave dev-server bytes unchanged."
        if ($failure -in 'build-failure', 'harness-failure') {
            $deploy = Invoke-Fixture 'build-and-deploy.ps1'
            Assert ($deploy.Code -ne 0 -and (Server-Snapshot) -eq $before) 'Deployment must reject validation failure without replacing the old JAR.'
            Assert (-not (Test-Path (Join-Path $fixtureRoot 'stop-called'))) 'A failed gate must not stop Paper.'
        }
        Remove-Item -LiteralPath (Join-Path $fixtureRoot $failure)
    }
    Write-Fixture 'stop-failure' 'fail'
    Assert ((Invoke-Fixture 'build-and-deploy.ps1').Code -ne 0 -and (Server-Snapshot) -eq $before) 'Stop failure must prevent JAR replacement.'
    Remove-Item -LiteralPath (Join-Path $fixtureRoot 'stop-failure')
    $deploy = Invoke-Fixture 'build-and-deploy.ps1'
    Assert ($deploy.Code -eq 0 -and $deploy.Output.Contains('SHA-256:')) 'Successful deployment must report artifact hash.'
    Assert ((Get-Content (Join-Path $fixtureRoot 'dev-server/plugins/vapeecore-1.0-SNAPSHOT.jar') -Raw).Trim() -eq 'new-artifact') 'Successful gate must deploy the built artifact.'

    # Exercise real discovery diagnostics too, independently of the wrapper's controlled seam.
    Copy-Item -LiteralPath (Join-Path $scriptsRoot 'development-environment.ps1') -Destination (Join-Path $fixtureRoot 'scripts/real-environment.ps1')
    Write-Fixture 'scripts/java17.ps1' 'Write-Output ''openjdk version "17.0.12"''; exit 0'
    Write-Fixture 'scripts/prerequisite-check.ps1' @'
param([string]$Kind)
. (Join-Path $PSScriptRoot 'real-environment.ps1')
try {
    if ($Kind -eq 'java') { $env:JAVA_HOME = Join-Path $PSScriptRoot 'absent-jdk'; Get-Java21Jdk | Out-Null }
    elseif ($Kind -eq 'java17') {
        function Resolve-JavaTool { Join-Path $PSScriptRoot 'java17.ps1' }
        Get-Java21Jdk | Out-Null
    } else { $env:MAVEN_HOME = Join-Path $PSScriptRoot 'absent-maven'; Resolve-MavenExecutable | Out-Null }
    exit 0
} catch { Write-Output $_.Exception.Message; exit 1 }
'@
    foreach ($kind in 'java', 'maven') {
        $output = & $shell -NoProfile -File (Join-Path $fixtureRoot 'scripts/prerequisite-check.ps1') $kind 2>&1 | Out-String
        Assert ($LASTEXITCODE -ne 0 -and $output.Contains($(if ($kind -eq 'java') { 'JAVA_HOME' } else { 'MAVEN_HOME' }))) "Real $kind discovery must give actionable diagnostics."
    }
    $output = & $shell -NoProfile -File (Join-Path $fixtureRoot 'scripts/prerequisite-check.ps1') java17 2>&1 | Out-String
    Assert ($LASTEXITCODE -ne 0 -and $output.Contains('Found: Java 17') -and $output.Contains('Expected: Java 21')) 'Real Java version check must explain an unsupported JDK.'
    Write-Host "Verification script tests passed $checks checks."
} finally {
    $resolved = [IO.Path]::GetFullPath($fixtureRoot)
    $temporary = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if (-not $resolved.StartsWith($temporary, [StringComparison]::OrdinalIgnoreCase) -or
        -not ([IO.Path]::GetFileName($resolved)).StartsWith('vapeecore-verification-')) { throw 'Unsafe fixture cleanup path.' }
    if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
