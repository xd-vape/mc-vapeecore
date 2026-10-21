#requires -Version 7.0
[CmdletBinding()]
param([switch]$Offline, [string]$MavenRepository)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Set-StrictMode -Version Latest
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $projectRoot 'scripts/development-environment.ps1')
$environment = Initialize-DevelopmentEnvironment
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('vapeecore-settings-negative-' + [guid]::NewGuid().ToString('N'))
$receiptDirectory = Join-Path $projectRoot 'target/verification/settings-contracts'
$results = [Collections.Generic.List[object]]::new()
$menu = 'src/main/java/dev/vapee/core/settings/SettingsMenu.java'
$entries = 'src/main/java/dev/vapee/core/settings/SettingsMenuEntries.java'
$service = 'src/main/java/dev/vapee/core/player/settings/PlayerSettingsService.java'
$repository = 'src/main/java/dev/vapee/core/player/repository/FilePlayerRepository.java'
$state = 'src/main/java/dev/vapee/core/player/settings/PlayerSettings.java'
$synthetic = 'src/test/java/dev/vapee/core/settings/SettingsEntryExtensionHarness.java'
$persistenceHarness = 'dev.vapee.core.player.repository.SettingsPersistenceContractHarness'
$syntheticHarness = 'dev.vapee.core.settings.SettingsEntryExtensionHarness'

function Replace-Once([string]$Source, [string]$Old, [string]$New) {
    if (($Source.Split(@($Old), [StringSplitOptions]::None)).Count -ne 2) {
        throw "Mutation anchor must occur exactly once: $Old"
    }
    return $Source.Replace($Old, $New)
}

function Invoke-Harness([string]$ClassPath, [string]$Harness, [string[]]$Arguments = @()) {
    $output = & $environment.Java -cp $ClassPath $Harness @Arguments 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $output }
}

Push-Location -LiteralPath $projectRoot
try {
    New-Item -ItemType Directory -Path $fixtureRoot | Out-Null
    New-Item -ItemType Directory -Path $receiptDirectory -Force | Out-Null
    Remove-Item -LiteralPath (Join-Path $receiptDirectory 'results.json') -Force -ErrorAction SilentlyContinue
    $classpathFile = Join-Path $projectRoot 'target/settings-contract-classpath.txt'
    $mavenArgs = @('-B', '-ntp')
    if ($Offline) { $mavenArgs += '-o' }
    if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$MavenRepository" }
    & $environment.Maven @mavenArgs test-compile dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
    if ($LASTEXITCODE -ne 0) { throw 'Settings control compilation/classpath preparation failed.' }
    $classpath = (Join-Path $projectRoot 'target/test-classes') + [IO.Path]::PathSeparator +
        (Join-Path $projectRoot 'target/classes') + [IO.Path]::PathSeparator + (Get-Content -LiteralPath $classpathFile -Raw).Trim()
    $positiveHarnesses = @('dev.vapee.core.settings.SettingsDefinitionContractHarness', $syntheticHarness,
        $persistenceHarness, 'dev.vapee.core.settings.SettingsMenuSecurityHarness')
    foreach ($harness in $positiveHarnesses) {
        $positive = Invoke-Harness $classpath $harness
        if ($positive.ExitCode -ne 0) { throw "Positive control failed: $harness`n$($positive.Output)" }
        Write-Host $positive.Output.Trim()
    }
    $registryCheck = '&& activeInventories.get(viewer.getUniqueId()) == inventory'
    $currentCheck = '&& viewer.getOpenInventory().getTopInventory() == inventory'
    $readAnchor = '"settings.friend-presence-notifications",' + "`n" + '                settings.isFriendPresenceNotificationsEnabled()'
    $variants = @(
        @{ Id='N1'; File=$entries; Old='layout.actionSlot(action, 1).orElse(-1)'; New='-1';
            Harness='dev.vapee.core.settings.SettingsDefinitionContractHarness'; Expected='Missing/mismatched Settings entry' },
        @{ Id='N2'; File=$synthetic; Old='enabled = !enabled;'; New='decoy = !decoy;';
            Harness=$syntheticHarness; Expected='synthetic callback changes intended value only' },
        @{ Id='N3'; File=$service; Old='update.accept(settings, previousValue);'; New='/* rollback deliberately removed */';
            Harness=$persistenceHarness; Expected='real setter rolls back WRITE' },
        @{ Id='N4'; File=$service; Old='playerService.savePlayer(validatedUniqueId);'; New='notifyChanged(validatedUniqueId); playerService.savePlayer(validatedUniqueId);';
            Harness=$persistenceHarness; Expected='observer sees durable committed presence=true' },
        @{ Id='N5'; File=$menu; Old=$registryCheck; New='';
            Harness='dev.vapee.core.settings.SettingsMenuSecurityHarness'; Expected='even opened forged holder cannot mutate' },
        @{ Id='N6'; File=$repository; Old=$readAnchor; New=$readAnchor.Replace('friend-presence-notifications', 'friend-presence-notifications-broken');
            Harness=$persistenceHarness; Arguments=@('roundtrip'); Expected='independent repository reload retains true' },
        @{ Id='N7'; File=$state; Old='DEFAULT_FRIEND_PRESENCE_NOTIFICATIONS_ENABLED = false'; New='DEFAULT_FRIEND_PRESENCE_NOTIFICATIONS_ENABLED = true';
            Harness=$persistenceHarness; Expected='frozen presence default false' },
        @{ Id='N8'; File=$menu; Old=$registryCheck; New=''; Old2=$currentCheck; New2='';
            Harness=$syntheticHarness; Arguments=@('stale'); Expected='replaced inventory cannot execute synthetic mutation' }
    )
    foreach ($variant in $variants) {
        $directory = Join-Path $fixtureRoot $variant.Id
        New-Item -ItemType Directory -Path "$directory/classes" -Force | Out-Null
        $source = (Get-Content -LiteralPath $variant.File -Raw).Replace("`r`n", "`n")
        $mutated = Replace-Once $source $variant.Old $variant.New
        if ($variant.ContainsKey('Old2')) { $mutated = Replace-Once $mutated $variant.Old2 $variant.New2 }
        $sourceFile = Join-Path $directory ([IO.Path]::GetFileName($variant.File))
        [IO.File]::WriteAllText($sourceFile, $mutated, [Text.UTF8Encoding]::new($false))
        $compile = & $environment.Javac -proc:none -encoding UTF-8 -cp $classpath -d "$directory/classes" $sourceFile 2>&1 | Out-String
        $compileExit = $LASTEXITCODE
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-compile.log"), $compile)
        if ($compileExit -ne 0) { throw "$($variant.Id) did not compile; this is NOT a detected regression.`n$compile" }
        $arguments = if ($variant.ContainsKey('Arguments')) { $variant.Arguments } else { @() }
        $negative = Invoke-Harness ("$directory/classes" + [IO.Path]::PathSeparator + $classpath) $variant.Harness $arguments
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-behavior.log"), $negative.Output)
        if ($negative.ExitCode -eq 0 -or -not $negative.Output.Contains($variant.Expected)) {
            throw "$($variant.Id) did not fail for the intended contract.`n$($negative.Output)"
        }
        $results.Add([pscustomobject]@{ Id=$variant.Id; CompileExit=$compileExit; BehaviorExit=$negative.ExitCode; Expected=$variant.Expected; Detected=$true })
        Write-Host "$($variant.Id): compiled; intended behavioral regression detected."
    }
    foreach ($harness in $positiveHarnesses) {
        $positive = Invoke-Harness $classpath $harness
        if ($positive.ExitCode -ne 0) { throw "Restored positive control failed: $harness`n$($positive.Output)" }
        Write-Host $positive.Output.Trim()
    }
    $results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $receiptDirectory 'results.json') -Encoding utf8
    Write-Host 'Settings negative controls: 8/8 compiled and detected; ordinary-classpath positives pass again.'
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        $temporary = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
        if (-not $resolved.StartsWith($temporary, [StringComparison]::OrdinalIgnoreCase) -or
            -not ([IO.Path]::GetFileName($resolved)).StartsWith('vapeecore-settings-negative-')) {
            throw "Refusing unexpected cleanup target: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
