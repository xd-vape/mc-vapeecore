#requires -Version 7.0
[CmdletBinding()]
param([switch]$Offline, [string]$MavenRepository)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Set-StrictMode -Version Latest
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $projectRoot 'scripts/development-environment.ps1')
$environment = Initialize-DevelopmentEnvironment
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('vapeecore-reward-strict-negative-' + [guid]::NewGuid().ToString('N'))
$receiptDirectory = Join-Path $projectRoot 'target/verification/reward-strict-shutdown'
$sourceDirectory = Join-Path $projectRoot 'src/main/java/dev/vapee/core/reward'
$moduleHarness = 'dev.vapee.core.reward.RewardShutdownFlushHarness'
$positives = @($moduleHarness, 'dev.vapee.core.reward.RewardModuleLifecycleHarness',
    'dev.vapee.core.reward.RewardServiceHarness', 'dev.vapee.core.reward.RewardLifecycleHarness',
    'dev.vapee.core.quest.QuestModuleLifecycleHarness', 'dev.vapee.core.onlinereward.OnlineRewardModuleLifecycleHarness',
    'dev.vapee.core.quest.daily.DailyQuestModuleLifecycleHarness', 'dev.vapee.core.player.PlayerModuleLifecycleHarness',
    'dev.vapee.core.player.repository.PlayerRewardPersistenceHarness', 'dev.vapee.core.player.repository.PlayerQuestPersistenceHarness',
    'dev.vapee.core.module.ModuleExtensionHarness')
$results = [Collections.Generic.List[object]]::new()

function Replace-Once([string]$Source, [string]$Old, [string]$New) {
    if (($Source.Split(@($Old), [StringSplitOptions]::None)).Count -ne 2) { throw "Mutation anchor must occur once: $Old" }
    return $Source.Replace($Old, $New)
}
function Invoke-Harness([string]$ClassPath, [string]$Harness, [string[]]$Arguments = @()) {
    $output = & $environment.Java -cp $ClassPath $Harness @Arguments 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode=$LASTEXITCODE; Output=$output }
}
function Test-Positives([string]$Phase) {
    foreach ($harness in $positives) {
        $positive = Invoke-Harness $classpath $harness
        [IO.File]::WriteAllText((Join-Path $receiptDirectory ($harness.Split('.')[-1] + "-positive-$Phase.log")), $positive.Output)
        if ($positive.ExitCode -ne 0 -or $positive.Output -notmatch 'passed [1-9][0-9]* checks\.') { throw "Positive control failed: $harness`n$($positive.Output)" }
        Write-Host $positive.Output.Trim()
    }
}

Push-Location -LiteralPath $projectRoot
try {
    New-Item -ItemType Directory -Path $fixtureRoot | Out-Null
    New-Item -ItemType Directory -Force -Path $receiptDirectory | Out-Null
    Remove-Item -LiteralPath (Join-Path $receiptDirectory 'results.json') -Force -ErrorAction SilentlyContinue
    $classpathFile = Join-Path $projectRoot 'target/reward-strict-shutdown-classpath.txt'
    $mavenArgs = @('-B', '-ntp')
    if ($Offline) { $mavenArgs += '-o' }
    if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$MavenRepository" }
    & $environment.Maven @mavenArgs test-compile dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
    if ($LASTEXITCODE -ne 0) { throw 'Reward strict shutdown compilation/classpath preparation failed.' }
    $classpath = @((Join-Path $projectRoot 'target/test-classes'), (Join-Path $projectRoot 'target/classes'),
        (Get-Content -LiteralPath $classpathFile -Raw).Trim()) -join [IO.Path]::PathSeparator
    Test-Positives 'before'
    $variants = @(
        @{ Id='N1'; File='RewardModule.java'; Old='failure.addSuppressed(new IllegalStateException("Could not complete pending Reward flush", exception));'; New='// incomplete flush swallowed'; Scenario='failure'; Expected='ordinary failed shutdown reported incomplete' },
        @{ Id='N2'; File='RewardService.java'; Old='if (!failures.isEmpty()) throw new FlushFailures(failures);'; New='dirtyPlayers.clear(); if (!failures.isEmpty()) throw new FlushFailures(failures);'; Scenario='failure'; Expected='failed UUID retained and successful UUID clean' },
        @{ Id='N3'; File='RewardService.java'; Old='failures.put(uniqueId, exception);'; New='failures.put(uniqueId, exception); break;'; Scenario='strict'; Expected='strict failure cannot skip independent profile' },
        @{ Id='N4'; File='RewardModule.java'; Old='resources.flushPending |= flushRewards;'; New='resources.flushPending = flushRewards;'; Scenario='failure'; Expected='inactive retry attempts only unresolved UUID' },
        @{ Id='N5'; File='RewardModule.java'; Old='if (!resources.flushPending) resources.service = null;'; New='resources.service = null;'; Scenario='failure'; Expected='exact original service and flush obligation retained' },
        @{ Id='N6'; File='RewardModule.java'; Old='if (pendingCleanup != null) {'; New='if (false && pendingCleanup != null) {'; Scenario='failure'; Expected='pending enable rejects without new resource acquisition or lost state' },
        @{ Id='N7'; File='RewardModule.java'; Old='active ? rewardService : null'; New='active ? rewardService : pendingCleanup == null ? null : pendingCleanup.service'; Scenario='failure'; Expected='pending getter closed' },
        @{ Id='N8'; File='RewardModule.java'; Old='resources.service.flushAllOrThrow();'; New='if (pendingCleanup != null) resources.service.grantCoins(playerModule.getPlayerService().getLoadedPlayers().stream().map(p -> p.getUniqueId()).sorted().findFirst().orElseThrow(), 11L, RewardSource.SYSTEM, "mutant:replay"); resources.service.flushAllOrThrow();'; Scenario='failure'; Expected='retry saves without re-granting coins' },
        @{ Id='N9'; File='RewardModule.java'; Old='if (task.isCancelled()) return true;'; New='if (task.isCancelled() || failure.getSuppressed().length == 0) return true;'; Scenario='cancel'; Expected='unconfirmed or throwing cancellation reported' },
        @{ Id='N10'; File='RewardModule.java'; Old='// This obligation survives active=false. Startup rollback never invents persistence work.'; New='if (resources.task != null) return;'; Scenario='cancel'; Expected='cancel failure cannot skip strict flush' },
        @{ Id='N11'; File='RewardService.java'; Old='failures.put(uniqueId, exception);'; New='failures.put(uniqueId, new IllegalStateException("diagnostic masked storage"));'; Scenario='strict'; Expected='exact failed UUID to original cause mapping' },
        @{ Id='N12'; File='RewardModule.java'; Old='if (!resources.flushPending) resources.service = null;'; New='if (!resources.flushPending) { playerModule.disable(); resources.service = null; }'; Scenario='normal'; Expected='Reward preserves borrowed Player owner and cache' },
        @{ Id='N13'; File='RewardService.java'; Old='if (!playerService.isLoaded(uniqueId)) {'; New='if (true) {'; Scenario='failure'; Expected='ordinary failed shutdown reported incomplete' },
        @{ Id='N14'; File='RewardModule.java'; Old='if (active && rewardService == scheduledService) flushSafely(scheduledService);'; New='if (active) flushSafely(rewardService);'; Scenario='stale'; Expected='old task and listener isolated from replacement runtime' },
        @{ Id='N15'; File='RewardModule.java'; Old='() -> active && rewardService == listenerService'; New='() -> true'; Scenario='listener'; Expected='retired listener cannot flush captured old service' }
    )
    foreach ($variant in $variants) {
        $directory = Join-Path $fixtureRoot $variant.Id
        New-Item -ItemType Directory -Path (Join-Path $directory 'classes') -Force | Out-Null
        $source = (Get-Content -LiteralPath (Join-Path $sourceDirectory $variant.File) -Raw).Replace("`r`n", "`n")
        $mutated = Replace-Once $source $variant.Old $variant.New
        $sourceFile = Join-Path $directory $variant.File
        [IO.File]::WriteAllText($sourceFile, $mutated, [Text.UTF8Encoding]::new($false))
        $compile = & $environment.Javac -proc:none -encoding UTF-8 -cp $classpath -d (Join-Path $directory 'classes') $sourceFile 2>&1 | Out-String
        $compileExit = $LASTEXITCODE
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-compile.log"), $compile)
        if ($compileExit -ne 0) { throw "$($variant.Id) failed compilation; not accepted behavioral evidence.`n$compile" }
        $overlayClasspath = (Join-Path $directory 'classes') + [IO.Path]::PathSeparator + $classpath
        $negative = Invoke-Harness $overlayClasspath $moduleHarness @($variant.Scenario)
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-behavior.log"), $negative.Output)
        if ($negative.ExitCode -eq 0 -or -not $negative.Output.Contains('java.lang.AssertionError') -or -not $negative.Output.Contains($variant.Expected)) {
            throw "$($variant.Id) did not fail its intended assertion: $($variant.Expected)`n$($negative.Output)"
        }
        $results.Add([pscustomobject]@{ Id=$variant.Id; File=$variant.File; CompileExit=$compileExit; BehaviorExit=$negative.ExitCode; Scenario=$variant.Scenario; Contract=$variant.Expected })
        Write-Host "$($variant.Id): compiled mutation detected ($($variant.Expected))."
    }
    Test-Positives 'after'
    [pscustomobject]@{ Detected=$results.Count; Results=$results } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $receiptDirectory 'results.json') -Encoding utf8
    Write-Host "Reward strict shutdown negative controls: $($results.Count) detected; positive controls passed before and after."
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
        if (-not $resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or -not ([IO.Path]::GetFileName($resolved)).StartsWith('vapeecore-reward-strict-negative-')) { throw "Unsafe temporary cleanup path: $resolved" }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
