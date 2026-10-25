#requires -Version 7.0
[CmdletBinding()]
param([switch]$Offline, [string]$MavenRepository)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Set-StrictMode -Version Latest
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $projectRoot 'scripts/development-environment.ps1')
$environment = Initialize-DevelopmentEnvironment
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('vapeecore-reward-negative-' + [guid]::NewGuid().ToString('N'))
$receiptDirectory = Join-Path $projectRoot 'target/verification/reward-lifecycle'
$moduleFile = 'src/main/java/dev/vapee/core/reward/RewardModule.java'
$moduleHarness = 'dev.vapee.core.reward.RewardModuleLifecycleHarness'
$positives = @($moduleHarness, 'dev.vapee.core.reward.RewardLifecycleHarness',
    'dev.vapee.core.reward.RewardServiceHarness', 'dev.vapee.core.module.ModuleExtensionHarness')
$results = [Collections.Generic.List[object]]::new()

function Replace-Once([string]$Source, [string]$Old, [string]$New) {
    if (($Source.Split(@($Old), [StringSplitOptions]::None)).Count -ne 2) {
        throw "Mutation anchor must occur once: $Old"
    }
    return $Source.Replace($Old, $New)
}
function Invoke-Harness([string]$ClassPath, [string]$Harness, [string[]]$Arguments = @()) {
    $output = & $environment.Java -cp $ClassPath $Harness @Arguments 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $output }
}
function Test-Positives([string]$Phase) {
    foreach ($harness in $positives) {
        $positive = Invoke-Harness $classpath $harness
        [IO.File]::WriteAllText((Join-Path $receiptDirectory ($harness.Split('.')[-1] + "-positive-$Phase.log")), $positive.Output)
        if ($positive.ExitCode -ne 0 -or $positive.Output -notmatch 'passed [1-9][0-9]* checks\.') {
            throw "Positive control failed: $harness`n$($positive.Output)"
        }
        Write-Host $positive.Output.Trim()
    }
}

Push-Location -LiteralPath $projectRoot
try {
    New-Item -ItemType Directory -Path $fixtureRoot | Out-Null
    New-Item -ItemType Directory -Force -Path $receiptDirectory | Out-Null
    Remove-Item -LiteralPath (Join-Path $receiptDirectory 'results.json') -Force -ErrorAction SilentlyContinue
    $classpathFile = Join-Path $projectRoot 'target/reward-lifecycle-classpath.txt'
    $mavenArgs = @('-B', '-ntp')
    if ($Offline) { $mavenArgs += '-o' }
    if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$MavenRepository" }
    & $environment.Maven @mavenArgs test-compile dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
    if ($LASTEXITCODE -ne 0) { throw 'Reward lifecycle compilation/classpath preparation failed.' }
    $classpath = @((Join-Path $projectRoot 'target/test-classes'), (Join-Path $projectRoot 'target/classes'),
        (Get-Content -LiteralPath $classpathFile -Raw).Trim()) -join [IO.Path]::PathSeparator
    Test-Positives 'before'
    $variants = @(
        @{ Id='N1'; Old='() -> HandlerList.unregisterAll(resources.listener())'; New='() -> { }';
            Scenario='late-log'; Expected='listener rollback leaves no registered handler' },
        @{ Id='N2'; Old='resources.task()::cancel'; New='() -> { }';
            Scenario='late-log'; Expected='task rollback cancels acquired timer' },
        @{ Id='N3'; Old='if (!cleanup(acquired, false, exception)) pendingCleanup = acquired;';
            New='if (newFlushTask == null && !cleanup(acquired, false, exception)) pendingCleanup = acquired;';
            Scenario='late-log'; Expected='listener rollback leaves no registered handler' },
        @{ Id='N4'; Old='// Keep the existing shutdown flush while borrowed Economy/Player owners are still available.';
            New='if (failure.getSuppressed().length != failuresBefore) return false;';
            Scenario='cleanup'; Expected='cancel failure cannot skip independent flush and handler release' },
        @{ Id='N5'; Old='if (active || enabling || cleaning)'; New='if (enabling || cleaning)';
            Scenario='duplicate'; Expected='duplicate enable preserves existing runtime without duplicate resources' },
        @{ Id='N6'; Old='if (active || enabling || cleaning)'; New='if (active || cleaning)';
            Scenario='reentrant'; Expected='reentrant enable installs no second runtime' },
        @{ Id='N7'; Old='if (active && rewardService == scheduledService) flushSafely(scheduledService);';
            New='if (active) flushSafely(rewardService);';
            Scenario='stale-task'; Expected='old task cannot flush a fresh runtime' },
        @{ Id='N8'; Old='() -> flushSafely(resources.service())'; New='() -> { }';
            Scenario='shutdown'; Expected='shutdown flush persists pending reward once' },
        @{ Id='N9'; Old='throw exception;'; New='throw new IllegalStateException("masked startup", exception);';
            Scenario='late-log'; Expected='original startup exception preserved after final logging' }
    )
    foreach ($variant in $variants) {
        $directory = Join-Path $fixtureRoot $variant.Id
        New-Item -ItemType Directory -Path (Join-Path $directory 'classes') -Force | Out-Null
        $source = (Get-Content -LiteralPath $moduleFile -Raw).Replace("`r`n", "`n")
        $mutated = Replace-Once $source $variant.Old $variant.New
        $sourceFile = Join-Path $directory 'RewardModule.java'
        [IO.File]::WriteAllText($sourceFile, $mutated, [Text.UTF8Encoding]::new($false))
        $compile = & $environment.Javac -proc:none -encoding UTF-8 -cp $classpath -d (Join-Path $directory 'classes') $sourceFile 2>&1 | Out-String
        $compileExit = $LASTEXITCODE
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-compile.log"), $compile)
        if ($compileExit -ne 0) { throw "$($variant.Id) did not compile; this is not behavioral evidence.`n$compile" }
        $overlayClasspath = (Join-Path $directory 'classes') + [IO.Path]::PathSeparator + $classpath
        $negative = Invoke-Harness $overlayClasspath $moduleHarness @($variant.Scenario)
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-behavior.log"), $negative.Output)
        if ($negative.ExitCode -eq 0 -or -not $negative.Output.Contains('java.lang.AssertionError') -or
            -not $negative.Output.Contains($variant.Expected)) {
            throw "$($variant.Id) did not fail its intended assertion: $($variant.Expected)`n$($negative.Output)"
        }
        $results.Add([pscustomobject]@{ Id=$variant.Id; CompileExit=$compileExit;
            BehaviorExit=$negative.ExitCode; Scenario=$variant.Scenario; Contract=$variant.Expected })
        Write-Host "$($variant.Id): compiled mutation detected by behavior ($($variant.Expected))."
    }
    Test-Positives 'after'
    [pscustomobject]@{ Detected=$results.Count; Results=$results } | ConvertTo-Json -Depth 5 |
        Set-Content -LiteralPath (Join-Path $receiptDirectory 'results.json') -Encoding utf8
    Write-Host "Reward lifecycle negative controls: $($results.Count) detected; positive controls passed before and after."
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
        if (-not $resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or
            -not ([IO.Path]::GetFileName($resolved)).StartsWith('vapeecore-reward-negative-')) {
            throw "Refusing unsafe temporary cleanup: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
