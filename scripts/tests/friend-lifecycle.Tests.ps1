#requires -Version 7.0
[CmdletBinding()]
param([switch]$Offline, [string]$MavenRepository)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Set-StrictMode -Version Latest
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $projectRoot 'scripts/development-environment.ps1')
$environment = Initialize-DevelopmentEnvironment
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('vapeecore-friend-negative-' + [guid]::NewGuid().ToString('N'))
$receiptDirectory = Join-Path $projectRoot 'target/verification/friend-lifecycle'
$module = 'src/main/java/dev/vapee/core/friend/FriendModule.java'
$menu = 'src/main/java/dev/vapee/core/friend/gui/FriendMenu.java'
$moduleHarness = 'dev.vapee.core.friend.FriendModuleLifecycleHarness'
$menuHarness = 'dev.vapee.core.friend.gui.FriendMenuLifecycleHarness'
$extensionHarness = 'dev.vapee.core.module.ModuleExtensionHarness'
$results = [Collections.Generic.List[object]]::new()

function Replace-Once([string]$Source, [string]$Old, [string]$New) {
    if (($Source.Split(@($Old), [StringSplitOptions]::None)).Count -ne 2) { throw "Mutation anchor must occur once: $Old" }
    return $Source.Replace($Old, $New)
}
function Invoke-Harness([string]$ClassPath, [string]$Harness, [string[]]$Arguments = @()) {
    $output = & $environment.Java -cp $ClassPath $Harness @Arguments 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode=$LASTEXITCODE; Output=$output }
}
Push-Location -LiteralPath $projectRoot
try {
    New-Item -ItemType Directory -Path $fixtureRoot | Out-Null
    New-Item -ItemType Directory -Force -Path $receiptDirectory | Out-Null
    Remove-Item -LiteralPath (Join-Path $receiptDirectory 'results.json') -Force -ErrorAction SilentlyContinue
    $classpathFile = Join-Path $projectRoot 'target/friend-lifecycle-classpath.txt'
    $mavenArgs = @('-B', '-ntp')
    if ($Offline) { $mavenArgs += '-o' }
    if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$MavenRepository" }
    & $environment.Maven @mavenArgs test-compile dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
    if ($LASTEXITCODE -ne 0) { throw 'Friend lifecycle compilation/classpath preparation failed.' }
    $classpath = (Join-Path $projectRoot 'target/test-classes') + [IO.Path]::PathSeparator +
        (Join-Path $projectRoot 'target/classes') + [IO.Path]::PathSeparator + (Get-Content -LiteralPath $classpathFile -Raw).Trim()
    foreach ($harness in @($moduleHarness, $menuHarness, $extensionHarness)) {
        $positive = Invoke-Harness $classpath $harness
        if ($positive.ExitCode -ne 0) { throw "Positive control failed: $harness`n$($positive.Output)" }
        Write-Host $positive.Output.Trim()
    }
    $variants = @(
        @{ Id='N1'; File=$module; Old='() -> HandlerList.unregisterAll(listener)'; New='() -> { }';
            Expected='listener rollback leaves no registered handler' },
        @{ Id='N2'; File=$module; Old='() -> command.setExecutor(null)'; New='() -> { }';
            Expected='executor cleanup restores Bukkit fallback' },
        @{ Id='N3'; File=$module; Old='() -> command.setTabCompleter(null)'; New='() -> { }';
            Expected='completer cleanup removes obsolete executor' },
        @{ Id='N4'; File=$menu; Old='messages.localization().removeLocaleListener(localeChanges);'; New='// omitted locale unsubscribe';
            Expected='locale observer removed' },
        @{ Id='N5'; File=$menu; Old='friends.removeChangeListener(committedChanges);'; New='// omitted friend unsubscribe';
            Expected='friend observer removed' },
        @{ Id='N6'; File=$menu; Old='                closeOpenInventories();'; New='                /* omitted view closure */';
            Expected='owned view registry cleared' },
        @{ Id='N7'; File=$module; Old='throw exception;'; New='throw new IllegalStateException("masked startup exception", exception);';
            Expected='original startup exception preserved' },
        @{ Id='N8'; File=$module; Old='if (active || enabling)'; New='if (false)'; Arguments=@('retry');
            Expected='retry cannot duplicate listener or observer' }
    )
    foreach ($variant in $variants) {
        $directory = Join-Path $fixtureRoot $variant.Id
        New-Item -ItemType Directory -Path "$directory/classes" -Force | Out-Null
        $source = (Get-Content -LiteralPath $variant.File -Raw).Replace("`r`n", "`n")
        $mutated = Replace-Once $source $variant.Old $variant.New
        $sourceFile = Join-Path $directory ([IO.Path]::GetFileName($variant.File))
        [IO.File]::WriteAllText($sourceFile, $mutated, [Text.UTF8Encoding]::new($false))
        $compile = & $environment.Javac -proc:none -encoding UTF-8 -cp $classpath -d "$directory/classes" $sourceFile 2>&1 | Out-String
        $compileExit = $LASTEXITCODE
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-compile.log"), $compile)
        if ($compileExit -ne 0) { throw "$($variant.Id) did not compile; this is not behavioral evidence.`n$compile" }
        $arguments = if ($variant.ContainsKey('Arguments')) { $variant.Arguments } else { @() }
        $negative = Invoke-Harness ("$directory/classes" + [IO.Path]::PathSeparator + $classpath) $moduleHarness $arguments
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-behavior.log"), $negative.Output)
        if ($negative.ExitCode -eq 0 -or -not $negative.Output.Contains($variant.Expected)) {
            throw "$($variant.Id) did not fail the intended behavior: $($variant.Expected)`n$($negative.Output)"
        }
        $results.Add([pscustomobject]@{ Id=$variant.Id; CompileExit=$compileExit; BehaviorExit=$negative.ExitCode; Contract=$variant.Expected })
        Write-Host "$($variant.Id): compiled mutation detected by behavior ($($variant.Expected))."
    }
    # Overlays are used only as the first classpath entry in their individual child JVMs.
    # Original class files and all production/operator files remain untouched.
    foreach ($harness in @($moduleHarness, $menuHarness, $extensionHarness)) {
        $positive = Invoke-Harness $classpath $harness
        if ($positive.ExitCode -ne 0) { throw "Restored positive control failed: $harness`n$($positive.Output)" }
        Write-Host $positive.Output.Trim()
    }
    [pscustomobject]@{ Detected=$results.Count; Results=$results } | ConvertTo-Json -Depth 5 |
        Set-Content -LiteralPath (Join-Path $receiptDirectory 'results.json') -Encoding utf8
    Write-Host "Friend lifecycle negative controls: $($results.Count) detected; positive controls passed before and after."
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
        if (-not $resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or
            -not ([IO.Path]::GetFileName($resolved)).StartsWith('vapeecore-friend-negative-')) {
            throw "Refusing unsafe temporary cleanup: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
