#requires -Version 7.0
[CmdletBinding()]
param([switch]$Offline, [string]$MavenRepository)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Set-StrictMode -Version Latest
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $projectRoot 'scripts/development-environment.ps1')
$environment = Initialize-DevelopmentEnvironment
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('vapeecore-player-negative-' + [guid]::NewGuid().ToString('N'))
$receiptDirectory = Join-Path $projectRoot 'target/verification/player-lifecycle'
$moduleFile = 'src/main/java/dev/vapee/core/player/PlayerModule.java'
$serviceFile = 'src/main/java/dev/vapee/core/player/PlayerService.java'
$listenerFile = 'src/main/java/dev/vapee/core/player/PlayerListener.java'
$moduleHarness = 'dev.vapee.core.player.PlayerModuleLifecycleHarness'
$positives = @($moduleHarness, 'dev.vapee.core.player.repository.PlayerSchemaPersistenceHarness',
    'dev.vapee.core.player.repository.PlayerSettingsYamlCodecHarness',
    'dev.vapee.core.reward.RewardModuleLifecycleHarness', 'dev.vapee.core.module.ModuleExtensionHarness')
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
    $classpathFile = Join-Path $projectRoot 'target/player-lifecycle-classpath.txt'
    $mavenArgs = @('-B', '-ntp')
    if ($Offline) { $mavenArgs += '-o' }
    if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$MavenRepository" }
    & $environment.Maven @mavenArgs test-compile dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
    if ($LASTEXITCODE -ne 0) { throw 'Player lifecycle compilation/classpath preparation failed.' }
    $classpath = @((Join-Path $projectRoot 'target/test-classes'), (Join-Path $projectRoot 'target/classes'),
        (Get-Content -LiteralPath $classpathFile -Raw).Trim()) -join [IO.Path]::PathSeparator
    Test-Positives 'before'
    $retryFromDisk = @'
if (pendingCleanup != null) {
                    FilePlayerRepository stale = new FilePlayerRepository(plugin.getDataFolder().toPath().resolve("players"), plugin.getLogger());
                    for (CorePlayer player : resources.service.getLoadedPlayers()) {
                        player.updateName(stale.findByUniqueId(player.getUniqueId()).orElseThrow().getName());
                    }
                }
                resources.service.saveAllOrThrow();
'@
    $variants = @(
        @{ Id='N1'; File=$moduleFile; Old='HandlerList.unregisterAll(resources.listener);'; New='/* omitted listener rollback */';
            Scenario='registration-after'; Expected='listener rollback leaves no registered handler' },
        @{ Id='N2'; File=$moduleFile; Old='if (playerService != null || enabling || cleaning)'; New='if (enabling || cleaning)';
            Scenario='duplicate'; Expected='duplicate enable preserves existing runtime without duplicate resources' },
        @{ Id='N3'; File=$moduleFile; Old='// A refused listener removal must never skip independent profile commits.';
            New='if (failure.getSuppressed().length != 0) return;';
            Scenario='unregister'; Expected='unregister failure cannot skip persistence' },
        @{ Id='N4'; File=$moduleFile; Old='failure.addSuppressed(exception);';
            New='resources.service.clearLoadedPlayers(); failure.addSuppressed(exception);';
            Scenario='multi-1'; Expected='failed profile retains original unsaved state' },
        @{ Id='N5'; File=$serviceFile; Old='failures.put(player.getUniqueId(), exception);';
            New='failures.put(player.getUniqueId(), exception); if (requireComplete) break;';
            Scenario='multi-1'; Expected='independent later profile is durably saved' },
        @{ Id='N6'; File=$serviceFile; Old='if (requireComplete && !failures.isEmpty())';
            New='if (false && requireComplete && !failures.isEmpty())';
            Scenario='multi-1'; Expected='incomplete shutdown is reported to module caller' },
        @{ Id='N7'; File=$moduleFile; Old='if (pendingCleanup != null)'; New='if (false && pendingCleanup != null)';
            Scenario='multi-1'; Expected='unsafe re-enable is blocked without replacing pending data' },
        @{ Id='N8'; File=$moduleFile; Old='throw exception;'; New='throw new IllegalStateException("masked startup", exception);';
            Scenario='registration-after'; Expected='original startup exception identity' },
        @{ Id='N9'; File=$moduleFile; Old='() -> !enabling && !cleaning && playerService == listenerService'; New='() -> true';
            Scenario='stale'; Expected='old listener cannot act as second runtime' },
        @{ Id='N10'; File=$moduleFile; Old='resources.service.saveAllOrThrow();'; New=$retryFromDisk;
            Scenario='multi-1'; Expected='retry commits retained unsaved values rather than stale disk' }
    )
    foreach ($variant in $variants) {
        $directory = Join-Path $fixtureRoot $variant.Id
        New-Item -ItemType Directory -Path (Join-Path $directory 'classes') -Force | Out-Null
        $sources = @()
        foreach ($productionFile in @($moduleFile, $serviceFile, $listenerFile)) {
            $source = (Get-Content -LiteralPath $productionFile -Raw).Replace("`r`n", "`n")
            if ($productionFile -eq $variant.File) { $source = Replace-Once $source $variant.Old $variant.New }
            $sourceFile = Join-Path $directory ([IO.Path]::GetFileName($productionFile))
            [IO.File]::WriteAllText($sourceFile, $source, [Text.UTF8Encoding]::new($false))
            $sources += $sourceFile
        }
        $compile = & $environment.Javac -proc:none -encoding UTF-8 -cp $classpath -d (Join-Path $directory 'classes') @sources 2>&1 | Out-String
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
    Write-Host "Player lifecycle negative controls: $($results.Count) detected; positive controls passed before and after."
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
        if (-not $resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or
            -not ([IO.Path]::GetFileName($resolved)).StartsWith('vapeecore-player-negative-')) {
            throw "Refusing unsafe temporary cleanup: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
