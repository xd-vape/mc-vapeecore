#requires -Version 7.0
[CmdletBinding()]
param([switch]$Offline, [string]$MavenRepository)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Set-StrictMode -Version Latest
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $projectRoot 'scripts/development-environment.ps1')
$environment = Initialize-DevelopmentEnvironment
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('vapeecore-module-shutdown-negative-' + [guid]::NewGuid().ToString('N'))
$receiptDirectory = Join-Path $projectRoot 'target/verification/module-shutdown-reporting'
$harness = 'dev.vapee.core.module.ModuleShutdownReportingHarness'
$positives = @($harness, 'dev.vapee.core.module.ModuleExtensionHarness', 'dev.vapee.core.module.VerificationScriptHarness',
    'dev.vapee.core.command.CoreCommandHarness', 'dev.vapee.core.player.PlayerModuleLifecycleHarness',
    'dev.vapee.core.reward.RewardModuleLifecycleHarness', 'dev.vapee.core.reward.RewardShutdownFlushHarness',
    'dev.vapee.core.quest.QuestModuleLifecycleHarness', 'dev.vapee.core.onlinereward.OnlineRewardModuleLifecycleHarness',
    'dev.vapee.core.quest.daily.DailyQuestModuleLifecycleHarness', 'dev.vapee.core.persistence.SafeFileWriterDiagnosticHarness',
    'dev.vapee.core.player.repository.PlayerPersistenceDiagnosticHarness')
$results = [Collections.Generic.List[object]]::new()
function Replace-Once([string]$Source, [string]$Old, [string]$New) {
    if (($Source.Split(@($Old), [StringSplitOptions]::None)).Count -ne 2) { throw "Mutation anchor must occur once: $Old" }
    return $Source.Replace($Old, $New)
}
function Invoke-Harness([string]$ClassPath, [string]$Harness, [string[]]$Arguments = @()) {
    $output = & $environment.Java -cp $ClassPath $Harness @Arguments 2>&1 | Out-String
    return [pscustomobject]@{ExitCode=$LASTEXITCODE;Output=$output}
}
function Test-Positives([string]$Phase) {
    foreach ($class in $positives) {
        $positive = Invoke-Harness $classpath $class
        [IO.File]::WriteAllText((Join-Path $receiptDirectory ($class.Split('.')[-1] + "-positive-$Phase.log")), $positive.Output)
        if ($positive.ExitCode -ne 0 -or $positive.Output -notmatch 'passed [1-9][0-9]* checks\.') { throw "Positive control failed: $class`n$($positive.Output)" }
        Write-Host $positive.Output.Trim()
    }
}
Push-Location -LiteralPath $projectRoot
try {
    New-Item -ItemType Directory -Path $fixtureRoot | Out-Null
    New-Item -ItemType Directory -Path $receiptDirectory -Force | Out-Null
    Remove-Item -LiteralPath (Join-Path $receiptDirectory 'results.json') -Force -ErrorAction SilentlyContinue
    $classpathFile = Join-Path $projectRoot 'target/module-shutdown-reporting-classpath.txt'
    $mavenArgs = @('-B', '-ntp')
    if ($Offline) { $mavenArgs += '-o' }
    if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$MavenRepository" }
    & $environment.Maven @mavenArgs test-compile dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
    if ($LASTEXITCODE -ne 0) { throw 'Module shutdown reporting compilation failed.' }
    $classpath = @((Join-Path $projectRoot 'target/test-classes'), (Join-Path $projectRoot 'target/classes'), (Get-Content -LiteralPath $classpathFile -Raw).Trim()) -join [IO.Path]::PathSeparator
    Test-Positives 'before'
    $variants = @(
        @{Id='N1';File='module/ModuleManager.java';Old='diagnostics.add(';New='if (operation.equals("shutdown.module")) throw failure; diagnostics.add(';Scenario='diagnostics';Expected='ordinary shutdown diagnostic failure does not escape coordinator'},
        @{Id='N2';File='module/ModuleManager.java';Old='diagnostics.add(';New='if (operation.equals("shutdown.failure")) throw failure; diagnostics.add(';Scenario='failures';Expected='SEVERE failure cannot abort independent cleanup'},
        @{Id='N3';File='module/ModuleManager.java';Old='diagnostics.add(';New='if (operation.equals("startup.failure")) throw failure; diagnostics.add(';Scenario='rollback';Expected='startup diagnostic cannot replace exact original startup exception'},
        @{Id='N4';File='module/ModuleManager.java';Old='for (int i = 0; i < outcomes.size(); i++)';New='for (int i = 0; i < (phase == ShutdownReport.Phase.STARTUP_ROLLBACK ? 0 : outcomes.size()); i++)';Scenario='rollback';Expected='startup rollback always attempts successful predecessors in reverse order'},
        @{Id='N5';File='module/ShutdownReport.java';Old='Objects.requireNonNull(status, "status");';New='Objects.requireNonNull(status, "status"); if (name.equals("B") && status == Status.THREW_EXCEPTION) { status = Status.RETURNED_NORMALLY; failure = null; }';Scenario='failures';Expected='multiple failures represented independently'},
        @{Id='N6';File='module/ShutdownReport.java';Old='return startupFailure == null && allCallsReturnedNormally() && diagnostics.isEmpty();';New='return true;';Scenario='diagnostics';Expected='diagnostic failure distinct from normal module returns'},
        @{Id='N7';File='module/ShutdownReport.java';Old='Objects.requireNonNull(status, "status");';New='Objects.requireNonNull(status, "status"); if (status == Status.THREW_EXCEPTION) failure = new IllegalStateException("diagnostic replaced original");';Scenario='failures';Expected='exact original module exceptions retained'},
        @{Id='N8';File='module/ModuleManager.java';Old='retained.add(outcome);';New='retained.add(new ShutdownReport.ModuleOutcome(modules.getFirst(), outcome.name(), outcome.status(), outcome.failure()));';Scenario='failures';Expected='read-only observed failures distinguished from configured membership'},
        @{Id='N9';File='module/ModuleManager.java';Old='module.disable();';New='module.getName(); module.disable();';Scenario='registration';Expected='hostile name getter cannot skip exact module disable'},
        @{Id='N10';File='module/ModuleManager.java';Old='if (!enabled && enabledModules.isEmpty())';New='if (!enabled && enabledModules.isEmpty()) { for (var prior : incompleteOutcomes) { try { prior.module().disable(); } catch (RuntimeException ignored) {} } return; } if (false)';Scenario='failures';Expected='incomplete repeat and report inspection do not automatically retry'},
        @{Id='N11';File='module/ModuleManager.java';Old='if (!enabled && enabledModules.isEmpty())';New='if (!enabled && enabledModules.isEmpty()) { for (var prior : modules) prior.disable(); return; } if (false)';Scenario='normal';Expected='repeated successful stop and inspection inert with same report'},
        @{Id='N12';File='module/ShutdownReport.java';Old='outcomes = List.copyOf(outcomes);';New='outcomes = new ArrayList<>(outcomes);';Scenario='normal';Expected='published report lists immutable'},
        @{Id='N13';File='module/ShutdownReport.java';Old='public enum Phase {';New='public Phase phase() { return Phase.SHUTDOWN; } public enum Phase {';Scenario='rollback';Expected='rollback phase and expected exact instances correctly attributed'},
        @{Id='N14';File='VapeeCore.java';Old='!report.allCallsReturnedNormally()';New='false && !report.allCallsReturnedNormally()';Scenario='root';Expected='root cannot log unconditional success after incomplete cleanup'},
        @{Id='N15';File='module/ModuleManager.java';Old='module.disable();';New='if (diagnostics.isEmpty()) module.disable();';Scenario='diagnostics';Expected='shutdown INFO failure cannot skip any actual disable'},
        @{Id='N16';File='module/ModuleManager.java';Old='if (!enabled && enabledModules.isEmpty())';New='if (!enabled && enabledModules.isEmpty()) { lastCleanupReport = null; return; } if (false)';Scenario='normal';Expected='repeated successful stop and inspection inert with same report'}
    )
    foreach ($variant in $variants) {
        $directory = Join-Path $fixtureRoot $variant.Id
        New-Item -ItemType Directory -Path (Join-Path $directory 'classes') -Force | Out-Null
        $source = (Get-Content -LiteralPath (Join-Path $projectRoot ('src/main/java/dev/vapee/core/' + $variant.File)) -Raw).Replace("`r`n", "`n")
        $sourceFile = Join-Path $directory ([IO.Path]::GetFileName($variant.File))
        [IO.File]::WriteAllText($sourceFile, (Replace-Once $source $variant.Old $variant.New), [Text.UTF8Encoding]::new($false))
        $compile = & $environment.Javac -proc:none -encoding UTF-8 -cp $classpath -d (Join-Path $directory 'classes') $sourceFile 2>&1 | Out-String
        $compileExit = $LASTEXITCODE
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-compile.log"), $compile)
        if ($compileExit -ne 0) { throw "$($variant.Id) compile failure is not behavioral detection.`n$compile" }
        $negative = Invoke-Harness ((Join-Path $directory 'classes') + [IO.Path]::PathSeparator + $classpath) $harness @($variant.Scenario)
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-behavior.log"), $negative.Output)
        if ($negative.ExitCode -eq 0 -or -not $negative.Output.Contains('java.lang.AssertionError') -or -not $negative.Output.Contains($variant.Expected)) { throw "$($variant.Id) did not fail its specifically intended assertion: $($variant.Expected)`n$($negative.Output)" }
        $results.Add([pscustomobject]@{Id=$variant.Id;File=$variant.File;CompileExit=$compileExit;BehaviorExit=$negative.ExitCode;Scenario=$variant.Scenario;Contract=$variant.Expected})
        Write-Host "$($variant.Id): compiled mutation detected ($($variant.Expected))."
    }
    Test-Positives 'after'
    [pscustomobject]@{Detected=$results.Count;Results=$results} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $receiptDirectory 'results.json') -Encoding utf8
    Write-Host "Module shutdown reporting controls: $($results.Count) detected; positives passed before and after."
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
        if (-not $resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or -not ([IO.Path]::GetFileName($resolved)).StartsWith('vapeecore-module-shutdown-negative-')) { throw "Unsafe temporary cleanup: $resolved" }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
