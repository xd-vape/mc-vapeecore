#requires -Version 7.0
[CmdletBinding()]
param([switch]$Offline, [string]$MavenRepository)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Set-StrictMode -Version Latest
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $projectRoot 'scripts/development-environment.ps1')
$environment = Initialize-DevelopmentEnvironment
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('vapeecore-presence-negative-' + [guid]::NewGuid().ToString('N'))
$receiptDirectory = Join-Path $projectRoot 'target/verification/presence-dependencies'
$module = 'src/main/java/dev/vapee/core/presence/PresenceModule.java'
$notifier = 'src/main/java/dev/vapee/core/presence/FriendPresenceNotifier.java'
$harness = 'dev.vapee.core.presence.PresenceDependencyBoundaryHarness'
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
    $classpathFile = Join-Path $projectRoot 'target/presence-dependency-classpath.txt'
    $mavenArgs = @('-B', '-ntp')
    if ($Offline) { $mavenArgs += '-o' }
    if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$MavenRepository" }
    & $environment.Maven @mavenArgs test-compile dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
    if ($LASTEXITCODE -ne 0) { throw 'Presence dependency compilation/classpath preparation failed.' }
    $classpath = (Join-Path $projectRoot 'target/test-classes') + [IO.Path]::PathSeparator +
        (Join-Path $projectRoot 'target/classes') + [IO.Path]::PathSeparator + (Get-Content -LiteralPath $classpathFile -Raw).Trim()
    $positive = Invoke-Harness $classpath
    if ($positive.ExitCode -ne 0) { throw "Positive control failed:`n$($positive.Output)" }
    Write-Host $positive.Output.Trim()
    $friendAssignment = "this.friendServiceProvider =`n                Objects.requireNonNull(friendServiceProvider, ""friendServiceProvider"");"
    $cachedFriend = @'
Supplier<FriendService> checked = Objects.requireNonNull(friendServiceProvider, "friendServiceProvider");
        this.friendServiceProvider = new Supplier<FriendService>() {
            private FriendService captured;
            public FriendService get() {
                if (captured == null) captured = checked.get();
                return captured;
            }
        };
'@
    $wrongFriend = @'
friendService = new FriendService(new dev.vapee.core.friend.FriendRepository() {
            public dev.vapee.core.friend.FriendSnapshot initialize() { return dev.vapee.core.friend.FriendSnapshot.empty(); }
            public void save(dev.vapee.core.friend.FriendSnapshot snapshot) { throw new AssertionError("wrong test dependency cannot persist"); }
        }, dev.vapee.core.friend.FriendLimits.defaults(), dev.vapee.core.friend.FriendRequestPolicy.allowAll(), java.time.Clock.systemUTC());
        PresenceService newService = new PresenceService();
'@
    $playerAssignment = "PlayerService playerService =`n                Objects.requireNonNull(`n                        playerServiceProvider.get(), ""PlayerService provider returned null"");"
    $publishNull = @'
PlayerService playerService = playerServiceProvider.get();
        if (playerService == null) { service = new PresenceService(); return; }
'@
    $variants = @(
        @{ Id='N1'; File=$module; Old='this.messageService = Objects.requireNonNull(messageService, "messageService");';
            New='this.messageService = Objects.requireNonNull(messageService, "messageService"); playerServiceProvider.get();';
            Expected='typed construction must not resolve services' },
        @{ Id='N2'; File=$module; Old=$friendAssignment; New=$cachedFriend;
            Expected='generation B notification policy recipients' },
        @{ Id='N3'; File=$module; Old='PresenceService newService = new PresenceService();'; New=$wrongFriend;
            Expected='generation A notification policy recipients' },
        @{ Id='N4'; File=$module; Old=$playerAssignment; New=$publishNull; Arguments=@('missing');
            Expected='required dependency must fail before publication: PlayerService' },
        @{ Id='N5'; File=$module; Old='if (listener != null) HandlerList.unregisterAll(listener);';
            New='if (listener != null) { /* omitted unregistration */ }'; Expected='Presence listener cleanup leaves no handler' },
        @{ Id='N6'; File=$module; Old='notifier = null;'; New='/* old notifier retained */';
            Expected='owned notifier reference released' },
        @{ Id='N7'; File=$notifier; Old='if (enabled.isEmpty() || !enabled.get()) continue;'; New='if (enabled.isEmpty()) continue;';
            Expected='generation A notification policy recipients' },
        @{ Id='N8'; File=$module; Old='Objects.requireNonNull(friendModule, "friendModule")::getFriendService';
            New='java.util.Optional.of(Objects.requireNonNull(friendModule, "friendModule").getFriendService()).<Supplier<FriendService>>map(value -> () -> value).orElseThrow()';
            Arguments=@('compatibility'); Expected='compatibility construction must not resolve services' }
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
        if ($compileExit -ne 0) { throw "$($variant.Id) did not compile; no behavioral evidence.`n$compile" }
        $arguments = if ($variant.ContainsKey('Arguments')) { $variant.Arguments } else { @() }
        $negative = Invoke-Harness ("$directory/classes" + [IO.Path]::PathSeparator + $classpath) $arguments
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-behavior.log"), $negative.Output)
        if ($negative.ExitCode -eq 0 -or -not $negative.Output.Contains($variant.Expected)) {
            throw "$($variant.Id) did not fail intended contract: $($variant.Expected)`n$($negative.Output)"
        }
        $results.Add([pscustomobject]@{ Id=$variant.Id; CompileExit=$compileExit; BehaviorExit=$negative.ExitCode; Contract=$variant.Expected })
        Write-Host "$($variant.Id): compiled mutation detected ($($variant.Expected))."
    }
    $positive = Invoke-Harness $classpath
    if ($positive.ExitCode -ne 0) { throw "Restored positive control failed:`n$($positive.Output)" }
    Write-Host $positive.Output.Trim()
    [pscustomobject]@{ Detected=$results.Count; Results=$results } | ConvertTo-Json -Depth 5 |
        Set-Content -LiteralPath (Join-Path $receiptDirectory 'results.json') -Encoding utf8
    Write-Host "Presence dependency negative controls: $($results.Count) detected; positives passed before and after."
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
        if (-not $resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or
            -not ([IO.Path]::GetFileName($resolved)).StartsWith('vapeecore-presence-negative-')) {
            throw "Refusing unsafe temporary cleanup: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
