#requires -Version 7.0
[CmdletBinding()]
param([switch]$Offline, [string]$MavenRepository)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Set-StrictMode -Version Latest
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $projectRoot 'scripts/development-environment.ps1')
$environment = Initialize-DevelopmentEnvironment
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('vapeecore-settings-codec-negative-' + [guid]::NewGuid().ToString('N'))
$receiptDirectory = Join-Path $projectRoot 'target/verification/player-settings-codec'
$codec = 'src/main/java/dev/vapee/core/player/repository/PlayerSettingsYamlCodec.java'
$repository = 'src/main/java/dev/vapee/core/player/repository/FilePlayerRepository.java'
$schema = 'src/main/java/dev/vapee/core/player/repository/PlayerFileSchema.java'
$service = 'src/main/java/dev/vapee/core/player/settings/PlayerSettingsService.java'
$harness = 'dev.vapee.core.player.repository.PlayerSettingsYamlCodecHarness'
$results = [Collections.Generic.List[object]]::new()

function Replace-Once([string]$Source, [string]$Old, [string]$New) {
    if (($Source.Split(@($Old), [StringSplitOptions]::None)).Count -ne 2) { throw "Mutation anchor must occur once: $Old" }
    return $Source.Replace($Old, $New)
}
function Invoke-Harness([string]$ClassPath, [string[]]$Arguments = @()) {
    $output = & $environment.Java -cp $ClassPath $harness @Arguments 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode=$LASTEXITCODE; Output=$output }
}

Push-Location -LiteralPath $projectRoot
try {
    New-Item -ItemType Directory -Path $fixtureRoot | Out-Null
    New-Item -ItemType Directory -Force -Path $receiptDirectory | Out-Null
    Remove-Item -LiteralPath (Join-Path $receiptDirectory 'results.json') -Force -ErrorAction SilentlyContinue
    $classpathFile = Join-Path $projectRoot 'target/player-settings-codec-classpath.txt'
    $mavenArgs = @('-B', '-ntp')
    if ($Offline) { $mavenArgs += '-o' }
    if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$MavenRepository" }
    & $environment.Maven @mavenArgs test-compile dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
    if ($LASTEXITCODE -ne 0) { throw 'Settings codec compilation/classpath preparation failed.' }
    $classpath = (Join-Path $projectRoot 'target/test-classes') + [IO.Path]::PathSeparator +
        (Join-Path $projectRoot 'target/classes') + [IO.Path]::PathSeparator + (Get-Content -LiteralPath $classpathFile -Raw).Trim()
    $positive = Invoke-Harness $classpath
    if ($positive.ExitCode -ne 0) { throw "Positive control failed:`n$($positive.Output)" }
    [IO.File]::WriteAllText((Join-Path $receiptDirectory 'positive-before.log'), $positive.Output)
    Write-Host $positive.Output.Trim()
    $soundsFallback = '"settings.sounds",' + "`n" + '                        settings.isSoundsEnabled()));'
    $soundsWrong = '"settings.sounds",' + "`n" + '                        false));'
    $variants = @(
        @{ Id='N1'; File=$codec; Mode='defaults'; Edits=@(@{ Old=$soundsFallback; New=$soundsWrong });
            Expected='missing/malformed settings: sounds=true' },
        @{ Id='N2'; File=$codec; Mode='write'; Edits=@(@{
            Old='configuration.set("settings.sounds", settings.isSoundsEnabled());';
            New='configuration.set("settings.soundz", settings.isSoundsEnabled());' });
            Expected='independent canonical Settings paths and values' },
        @{ Id='N3'; File=$codec; Mode='legacy'; Edits=@(@{
            Old='"settings.lobby-players-visible",' + "`n" + '                        settings.isLobbyPlayersVisible()));';
            New='"settings.legacy-ignored",' + "`n" + '                        settings.isLobbyPlayersVisible()));' });
            Expected='legacy missing mode preserves Boolean false' },
        @{ Id='N4'; File=$codec; Mode='invalid-mode'; Edits=@(@{
            Old='visibility.setMode(VisibilityMode.HIDDEN);'; New='visibility.setMode(VisibilityMode.ALL);' });
            Expected='invalid mode fails closed or explicit mode wins UNKNOWN' },
        @{ Id='N5'; File=$codec; Mode='uuid'; Edits=@(
            @{ Old='if (target.equals(owner))'; New='if (false)' },
            @{ Old='visibility.addPlayer(owner, target);'; New='visibility.addPlayer(new UUID(9, 9), target);' });
            Expected='UUID policy rejects self/noncanonical/invalid and deduplicates' },
        @{ Id='N6'; File=$repository; Mode='aggregate'; Edits=@(@{
            Old='fileWriter.write(playerFile, () -> PlayerFileSchema.overlay(preserved, configuration), candidate -> {';
            New='if (preserved.get("settings") instanceof Map<?, ?> settingsSource) settingsSource.remove("future-extension");' + "`n" +
                '            fileWriter.write(playerFile, () -> PlayerFileSchema.overlay(preserved, configuration), candidate -> {' });
            Expected='unknown Settings extension preserved' },
        @{ Id='N7'; File=$repository; Mode='safety'; Edits=@(@{
            Old='PlayerFileSchema.validateOwnedValues(written.configuration(), configuration);';
            New='/* omitted canonical candidate validation */' });
            Expected='noncanonical candidate cannot claim aggregate commit' },
        @{ Id='N8'; File=$service; Mode='rollback'; Edits=@(@{
            Old='update.accept(settings, previousValue);'; New='/* omitted failed mutation rollback */' });
            Expected='failed Settings mutation rolls back WRITE' },
        @{ Id='N9'; File=$repository; Mode='migration'; Edits=@(@{
            Old='protectMigration(target, source.bytes());'; New='/* omitted migration protection */' });
            Expected='legacy migration backup exact bytes' },
        @{ Id='N10'; File=$schema; Mode='future'; Edits=@(@{
            Old='if (version > CURRENT_VERSION)'; New='if (false)' });
            Expected='unsafe/future source refuses load' }
    )
    foreach ($variant in $variants) {
        $directory = Join-Path $fixtureRoot $variant.Id
        New-Item -ItemType Directory -Path "$directory/classes" -Force | Out-Null
        $source = (Get-Content -LiteralPath $variant.File -Raw).Replace("`r`n", "`n")
        foreach ($edit in $variant.Edits) { $source = Replace-Once $source $edit.Old $edit.New }
        $sourceFile = Join-Path $directory ([IO.Path]::GetFileName($variant.File))
        [IO.File]::WriteAllText($sourceFile, $source, [Text.UTF8Encoding]::new($false))
        $compile = & $environment.Javac -proc:none -encoding UTF-8 -cp $classpath -d "$directory/classes" $sourceFile 2>&1 | Out-String
        $compileExit = $LASTEXITCODE
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-compile.log"), $compile)
        if ($compileExit -ne 0) { throw "$($variant.Id) did not compile; no behavioral evidence.`n$compile" }
        $negative = Invoke-Harness ("$directory/classes" + [IO.Path]::PathSeparator + $classpath) @($variant.Mode)
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-behavior.log"), $negative.Output)
        if ($negative.ExitCode -eq 0 -or -not $negative.Output.Contains($variant.Expected)) {
            throw "$($variant.Id) did not fail intended contract: $($variant.Expected)`n$($negative.Output)"
        }
        $results.Add([pscustomobject]@{ Id=$variant.Id; CompileExit=$compileExit; BehaviorExit=$negative.ExitCode; Contract=$variant.Expected })
        Write-Host "$($variant.Id): compiled mutation detected ($($variant.Expected))."
    }
    $positive = Invoke-Harness $classpath
    if ($positive.ExitCode -ne 0) { throw "Restored positive control failed:`n$($positive.Output)" }
    [IO.File]::WriteAllText((Join-Path $receiptDirectory 'positive-after.log'), $positive.Output)
    Write-Host $positive.Output.Trim()
    [pscustomobject]@{ Detected=$results.Count; Results=$results } | ConvertTo-Json -Depth 5 |
        Set-Content -LiteralPath (Join-Path $receiptDirectory 'results.json') -Encoding utf8
    Write-Host "Settings codec negative controls: $($results.Count) detected; positives passed before and after."
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
        if (-not $resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or
            -not ([IO.Path]::GetFileName($resolved)).StartsWith('vapeecore-settings-codec-negative-')) {
            throw "Refusing unsafe temporary cleanup: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
