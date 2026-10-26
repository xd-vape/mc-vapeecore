#requires -Version 7.0
[CmdletBinding()]
param([switch]$Offline, [string]$MavenRepository)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Set-StrictMode -Version Latest
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $projectRoot 'scripts/development-environment.ps1')
$environment = Initialize-DevelopmentEnvironment
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('vapeecore-online-reward-negative-' + [guid]::NewGuid().ToString('N'))
$receiptDirectory = Join-Path $projectRoot 'target/verification/online-reward-lifecycle'
$moduleFile = 'src/main/java/dev/vapee/core/onlinereward/OnlineRewardModule.java'
$moduleHarness = 'dev.vapee.core.onlinereward.OnlineRewardModuleLifecycleHarness'
$positives = @($moduleHarness, 'dev.vapee.core.onlinereward.OnlineRewardLifecycleHarness',
    'dev.vapee.core.onlinereward.OnlineRewardServiceHarness', 'dev.vapee.core.reward.RewardModuleLifecycleHarness',
    'dev.vapee.core.player.PlayerModuleLifecycleHarness', 'dev.vapee.core.player.repository.PlayerRewardPersistenceHarness')
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
    $classpathFile = Join-Path $projectRoot 'target/online-reward-lifecycle-classpath.txt'
    $mavenArgs = @('-B', '-ntp')
    if ($Offline) { $mavenArgs += '-o' }
    if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$MavenRepository" }
    & $environment.Maven @mavenArgs test-compile dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
    if ($LASTEXITCODE -ne 0) { throw 'OnlineReward lifecycle compilation/classpath preparation failed.' }
    $classpath = @((Join-Path $projectRoot 'target/test-classes'), (Join-Path $projectRoot 'target/classes'),
        (Get-Content -LiteralPath $classpathFile -Raw).Trim()) -join [IO.Path]::PathSeparator
    Test-Positives 'before'
    $variants = @(
        @{ Id='N1'; Old='if (newTask != null && !cancelTask(newTask, exception)) pendingCancellation = newTask;';
            New='if (false && newTask != null && !cancelTask(newTask, exception)) pendingCancellation = newTask;';
            Scenario='late-log'; Expected='late failure attempts exact task rollback' },
        @{ Id='N2'; Old='if (active || enabling || cleaning)'; New='if (enabling || cleaning)';
            Scenario='duplicate'; Expected='duplicate enable preserves existing runtime and single task' },
        @{ Id='N3'; Old='if (active || enabling || cleaning)'; New='if (active || cleaning)';
            Scenario='reentrant'; Expected='reentrant enable acquires no second task' },
        @{ Id='N4'; Old="pendingCancellation = task != null && !cancelTask(task, failure) ? task : null;`n            if (failure.getSuppressed().length != 0) throw failure;";
            New='pendingCancellation = task != null && !cancelTask(task, failure) ? task : null;';
            Scenario='cancellation'; Expected='refused cancellation is reported' },
        @{ Id='N5'; Old='return active && onlineRewardService == service;'; New='return !active || onlineRewardService == service;';
            Scenario='stale-task'; Expected='old callback has no authority after disable' },
        @{ Id='N6'; Old='return active && onlineRewardService == service;'; New='return active;';
            Scenario='stale-task'; Expected='old callback cannot process fresh generation' },
        @{ Id='N7'; Old='pendingCancellation = task != null && !cancelTask(task, failure) ? task : null;';
            New='if (task != null) cancelTask(task, failure); pendingCancellation = null;';
            Scenario='cancellation'; Expected='pending cancellation blocks new task acquisition and retries exact handle' },
        @{ Id='N8'; Old='active ? onlineRewardService : null';
            New='onlineRewardService == null ? new OnlineRewardService(configService, playerModule.getPlayerService(), rewardModule.getRewardService(), plugin.getLogger()) : onlineRewardService';
            Scenario='getter'; Expected='inactive getter rejects abandoned runtime' },
        @{ Id='N9'; Old='PROCESS_INTERVAL_TICKS = 20L'; New='PROCESS_INTERVAL_TICKS = 21L';
            Scenario='cadence'; Expected='owned synchronous twenty-tick delay and period' },
        @{ Id='N10'; Old='throw exception;'; New='throw new IllegalStateException("masked startup exception", exception);';
            Scenario='late-log'; Expected='original late logger exception identity preserved' }
    )
    foreach ($variant in $variants) {
        $directory = Join-Path $fixtureRoot $variant.Id
        New-Item -ItemType Directory -Path (Join-Path $directory 'classes') -Force | Out-Null
        $source = (Get-Content -LiteralPath $moduleFile -Raw).Replace("`r`n", "`n")
        $mutated = Replace-Once $source $variant.Old $variant.New
        $sourceFile = Join-Path $directory 'OnlineRewardModule.java'
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
    Write-Host "OnlineReward lifecycle negative controls: $($results.Count) detected; positive controls passed before and after."
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
        if (-not $resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or
            -not ([IO.Path]::GetFileName($resolved)).StartsWith('vapeecore-online-reward-negative-')) {
            throw "Refusing unsafe temporary cleanup: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
