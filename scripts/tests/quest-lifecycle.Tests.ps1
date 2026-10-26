#requires -Version 7.0
[CmdletBinding()]
param([switch]$Offline, [string]$MavenRepository)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Set-StrictMode -Version Latest
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $projectRoot 'scripts/development-environment.ps1')
$environment = Initialize-DevelopmentEnvironment
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('vapeecore-quest-negative-' + [guid]::NewGuid().ToString('N'))
$receiptDirectory = Join-Path $projectRoot 'target/verification/quest-lifecycle'
$sourceDirectory = Join-Path $projectRoot 'src/main/java/dev/vapee/core/quest'
$moduleHarness = 'dev.vapee.core.quest.QuestModuleLifecycleHarness'
$positives = @($moduleHarness, 'dev.vapee.core.quest.QuestLifecycleHarness',
    'dev.vapee.core.quest.QuestServiceHarness', 'dev.vapee.core.quest.QuestPlaytimeProducerHarness',
    'dev.vapee.core.quest.QuestProgressReporterHarness', 'dev.vapee.core.quest.QuestFeedbackLocalizationHarness',
    'dev.vapee.core.quest.DailyQuestServiceHarness', 'dev.vapee.core.player.repository.PlayerQuestPersistenceHarness',
    'dev.vapee.core.onlinereward.OnlineRewardModuleLifecycleHarness',
    'dev.vapee.core.reward.RewardModuleLifecycleHarness', 'dev.vapee.core.player.PlayerModuleLifecycleHarness')
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
    $classpathFile = Join-Path $projectRoot 'target/quest-lifecycle-classpath.txt'
    $mavenArgs = @('-B', '-ntp')
    if ($Offline) { $mavenArgs += '-o' }
    if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$MavenRepository" }
    & $environment.Maven @mavenArgs test-compile dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
    if ($LASTEXITCODE -ne 0) { throw 'Quest lifecycle compilation/classpath preparation failed.' }
    $classpath = @((Join-Path $projectRoot 'target/test-classes'), (Join-Path $projectRoot 'target/classes'),
        (Get-Content -LiteralPath $classpathFile -Raw).Trim()) -join [IO.Path]::PathSeparator
    Test-Positives 'before'
    $variants = @(
        @{ Id='N1'; File='QuestModule.java'; Old='cleanup(acquired, exception);'; New='if (false) cleanup(acquired, exception);';
            Scenario='late-log'; Expected='late logging failure rolls back acquired task' },
        @{ Id='N2'; File='QuestModule.java'; Old='if (active || enabling || cleaning)'; New='if (enabling || cleaning)';
            Scenario='duplicate'; Expected='duplicate enable preserves active runtime and its single registrations' },
        @{ Id='N3'; File='QuestModule.java'; Old='if (active || enabling || cleaning)'; New='if (active || cleaning)';
            Scenario='reentrant'; Expected='reentrant enable acquires no second runtime' },
        @{ Id='N4'; File='QuestModule.java'; Old='return active && runtime == candidate;'; New='return active;';
            Scenario='stale-task'; Expected='old callback cannot sample or flush a new runtime generation' },
        @{ Id='N5'; File='QuestModule.java'; Old='// Failed startup never earns progress. Normal shutdown samples before persistence.';
            New='if (resources.task != null) return;';
            Scenario='cancel'; Expected='cancel refusal cannot skip independent final Quest flush' },
        @{ Id='N6'; File='QuestModule.java'; Old='if (resources.flushPending) {';
            New="if (resources.listener != null) { if (unregister(resources.listener, failure)) resources.listener = null; else return; }`n        if (resources.flushPending) {";
            Scenario='unregister'; Expected='listener refusal cannot skip final Quest flush or task cancellation' },
        @{ Id='N7'; File='QuestService.java'; Old='if (!failures.isEmpty()) throw new FlushFailures(failures);';
            New='dirtyPlayers.clear(); if (!failures.isEmpty()) throw new FlushFailures(failures);';
            Scenario='failed-save'; Expected='incomplete flush retains unresolved dirty tracking' },
        @{ Id='N8'; File='QuestModule.java'; Old='failure.addSuppressed(new IllegalStateException("Could not complete Quest profile flush", exception));';
            New='// incomplete saves hidden from lifecycle caller';
            Scenario='failed-save'; Expected='incomplete Quest save is reported to lifecycle caller' },
        @{ Id='N9'; File='QuestModule.java'; Old='listener = new QuestListener(service, playtime, plugin.getLogger(), () -> owns(this));';
            New='listener = new QuestListener(service, playtime, plugin.getLogger());';
            Scenario='stale-listener'; Expected='retired listener cannot retry pending persistence or sample' },
        @{ Id='N10'; File='QuestModule.java'; Old='active ? runtime : null'; New='active ? runtime : pendingCleanup';
            Scenario='cancel'; Expected='inactive lifecycle getters reject retired runtime' },
        @{ Id='N11'; File='QuestModule.java'; Old='throw exception;'; New='throw new IllegalStateException("masked startup exception", exception);';
            Scenario='late-log'; Expected='original late startup exception preserved by identity' },
        @{ Id='N12'; File='QuestModule.java'; Old='resources.finalSamplePending |= wasActive;'; New='resources.finalSamplePending = false;';
            Scenario='final-sample'; Expected='normal shutdown captures final available completed minutes' }
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
    Write-Host "Quest lifecycle negative controls: $($results.Count) detected; positive controls passed before and after."
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
        if (-not $resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or -not ([IO.Path]::GetFileName($resolved)).StartsWith('vapeecore-quest-negative-')) { throw "Unsafe temporary cleanup path: $resolved" }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
