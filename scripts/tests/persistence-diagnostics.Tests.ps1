#requires -Version 7.0
[CmdletBinding()]
param([switch]$Offline, [string]$MavenRepository)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Set-StrictMode -Version Latest
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $projectRoot 'scripts/development-environment.ps1')
$environment = Initialize-DevelopmentEnvironment
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('vapeecore-persistence-diagnostics-negative-' + [guid]::NewGuid().ToString('N'))
$receiptDirectory = Join-Path $projectRoot 'target/verification/persistence-diagnostics'
$writer = 'dev.vapee.core.persistence.SafeFileWriterDiagnosticHarness'
$repository = 'dev.vapee.core.player.repository.PlayerPersistenceDiagnosticHarness'
$positives = @($writer, $repository, 'dev.vapee.core.player.repository.PlayerSchemaPersistenceHarness',
    'dev.vapee.core.player.repository.PlayerSettingsYamlCodecHarness', 'dev.vapee.core.player.repository.SettingsPersistenceContractHarness',
    'dev.vapee.core.player.repository.PlayerVisibilityPersistenceHarness', 'dev.vapee.core.player.repository.PlayerRewardPersistenceHarness',
    'dev.vapee.core.player.repository.PlayerQuestPersistenceHarness', 'dev.vapee.core.economy.EconomyServiceHarness',
    'dev.vapee.core.economy.EconomyIntegrationHarness', 'dev.vapee.core.player.PlayerModuleLifecycleHarness',
    'dev.vapee.core.reward.RewardShutdownFlushHarness', 'dev.vapee.core.quest.QuestModuleLifecycleHarness')
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
        $result = Invoke-Harness $classpath $harness
        [IO.File]::WriteAllText((Join-Path $receiptDirectory ($harness.Split('.')[-1] + "-positive-$Phase.log")), $result.Output)
        if ($result.ExitCode -ne 0 -or $result.Output -notmatch 'passed [1-9][0-9]* checks\.') { throw "Positive control failed: $harness`n$($result.Output)" }
        Write-Host $result.Output.Trim()
    }
}
Push-Location -LiteralPath $projectRoot
try {
    New-Item -ItemType Directory -Path $fixtureRoot | Out-Null
    New-Item -ItemType Directory -Path $receiptDirectory -Force | Out-Null
    Remove-Item -LiteralPath (Join-Path $receiptDirectory 'results.json') -Force -ErrorAction SilentlyContinue
    $classpathFile = Join-Path $projectRoot 'target/persistence-diagnostics-classpath.txt'
    $mavenArgs = @('-B', '-ntp')
    if ($Offline) { $mavenArgs += '-o' }
    if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$MavenRepository" }
    & $environment.Maven @mavenArgs test-compile dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
    if ($LASTEXITCODE -ne 0) { throw 'Persistence diagnostic test compilation failed.' }
    $classpath = @((Join-Path $projectRoot 'target/test-classes'), (Join-Path $projectRoot 'target/classes'), (Get-Content -LiteralPath $classpathFile -Raw).Trim()) -join [IO.Path]::PathSeparator
    Test-Positives 'before'
    $variants = @(
        @{ Id='N1'; File='persistence/SafeFileWriter.java'; Old='if (cleanupDiagnostic != exception) exception.addSuppressed(cleanupDiagnostic);'; New='throw cleanupDiagnostic;'; Harness=$writer; Scenario='commits'; Expected='completed replacement survives cleanup and WARNING failure' },
        @{ Id='N2'; File='player/repository/FilePlayerRepository.java'; Old='if (diagnostic != failure) failure.addSuppressed(diagnostic);'; New='throw diagnostic;'; Harness=$repository; Scenario='storage'; Expected='repository preserves exact original IOException as cause' },
        @{ Id='N3'; File='persistence/SafeFileWriter.java'; Old='if (restorationDiagnostic != failure) failure.addSuppressed(restorationDiagnostic);'; New='throw restorationDiagnostic;'; Harness=$writer; Scenario='restoration'; Expected='original replacement error remains primary despite restore diagnostics' },
        @{ Id='N4'; File='persistence/SafeFileWriter.java'; Old='if (restoration != failure) failure.addSuppressed(restoration);'; New='// restoration cause lost'; Harness=$writer; Scenario='restoration'; Expected='restoration failure remains suppressed' },
        @{ Id='N5'; File='persistence/SafeFileWriter.java'; Old='if (restoration != failure) failure.addSuppressed(restoration);'; New='if (restoration != failure) failure.addSuppressed(restoration); cleanup(backup);'; Harness=$writer; Scenario='restoration'; Expected='failed restoration preserves byte-exact recovery evidence' },
        @{ Id='N6'; File='player/repository/FilePlayerRepository.java'; Old='throw storageFailure("save player data", playerFile, exception);'; New='return;'; Harness=$repository; Scenario='index'; Expected='name-index save completion matches actual commit result' },
        @{ Id='N7'; File='persistence/SafeFileWriter.java'; Old='validate.run(temporary);'; New='// semantic validation skipped'; Harness=$writer; Scenario='precommit'; Expected='candidate validation failure propagates before commit' },
        @{ Id='N8'; File='player/repository/FilePlayerRepository.java'; Old='fileWriter.write(playerFile, () ->'; New='indexName(player.getUniqueId(), player.getName()); fileWriter.write(playerFile, () ->'; Harness=$repository; Scenario='index'; Expected='name index publishes only confirmed successful commit' },
        @{ Id='N9'; File='persistence/SafeFileWriter.java'; Old='catch (AtomicMoveNotSupportedException unsupported)'; New='catch (IOException unsupported)'; Harness=$writer; Scenario='precommit'; Expected='true precommit IO remains primary and visible' },
        @{ Id='N10'; File='persistence/SafeFileWriter.java'; Old='beforeReplace.run(target);'; New='// source protection skipped'; Harness=$writer; Scenario='precommit'; Expected='source protection failure preserves external bytes' },
        @{ Id='N11'; File='persistence/SafeFileWriter.java'; Old='if (!Files.readString(temporary, StandardCharsets.UTF_8).equals(content))'; New='if (false && !Files.readString(temporary, StandardCharsets.UTF_8).equals(content))'; Harness=$writer; Scenario='precommit'; Expected='exact serialized candidate comparison remains enforced' },
        @{ Id='N12'; File='player/repository/FilePlayerRepository.java'; Old='            indexName(player.getUniqueId(), player.getName());'; New='            // confirmed index publication lost'; Harness=$repository; Scenario='index'; Expected='name index publishes only confirmed successful commit' },
        @{ Id='N13'; File='persistence/SafeFileWriter.java'; Old='files.copy(target, backup);'; New='files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);'; Harness=$writer; Scenario='recovery'; Expected='concurrent recovery evidence refuses fallback publication' },
        @{ Id='N14'; File='persistence/SafeFileWriter.java'; Old='if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS))'; New='if (false && Files.exists(backup, LinkOption.NOFOLLOW_LINKS))'; Harness=$writer; Scenario='recovery'; Expected='recovery refusal precedes candidate acquisition' }
    )
    foreach ($variant in $variants) {
        $directory = Join-Path $fixtureRoot $variant.Id
        New-Item -ItemType Directory -Path (Join-Path $directory 'classes') -Force | Out-Null
        $sourcePath = Join-Path $projectRoot ('src/main/java/dev/vapee/core/' + $variant.File)
        $source = (Get-Content -LiteralPath $sourcePath -Raw).Replace("`r`n", "`n")
        $sourceFile = Join-Path $directory ([IO.Path]::GetFileName($variant.File))
        [IO.File]::WriteAllText($sourceFile, (Replace-Once $source $variant.Old $variant.New), [Text.UTF8Encoding]::new($false))
        $compile = & $environment.Javac -proc:none -encoding UTF-8 -cp $classpath -d (Join-Path $directory 'classes') $sourceFile 2>&1 | Out-String
        $compileExit = $LASTEXITCODE
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-compile.log"), $compile)
        if ($compileExit -ne 0) { throw "$($variant.Id) failed compilation, not behavioral evidence.`n$compile" }
        $negative = Invoke-Harness ((Join-Path $directory 'classes') + [IO.Path]::PathSeparator + $classpath) $variant.Harness @($variant.Scenario)
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-behavior.log"), $negative.Output)
        if ($negative.ExitCode -eq 0 -or -not $negative.Output.Contains('java.lang.AssertionError') -or -not $negative.Output.Contains($variant.Expected)) { throw "$($variant.Id) did not fail intended assertion $($variant.Expected)`n$($negative.Output)" }
        $results.Add([pscustomobject]@{Id=$variant.Id;File=$variant.File;CompileExit=$compileExit;BehaviorExit=$negative.ExitCode;Scenario=$variant.Scenario;Contract=$variant.Expected})
        Write-Host "$($variant.Id): compiled mutation detected ($($variant.Expected))."
    }
    Test-Positives 'after'
    [pscustomobject]@{Detected=$results.Count;Results=$results} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $receiptDirectory 'results.json') -Encoding utf8
    Write-Host "Persistence diagnostic controls: $($results.Count) detected; positives passed before and after."
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
        if (-not $resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or -not ([IO.Path]::GetFileName($resolved)).StartsWith('vapeecore-persistence-diagnostics-negative-')) { throw "Unsafe temporary cleanup: $resolved" }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
