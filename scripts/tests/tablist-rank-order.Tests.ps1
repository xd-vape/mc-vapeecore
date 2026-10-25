#requires -Version 7.0
[CmdletBinding()]
param([switch]$Offline, [string]$MavenRepository)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Set-StrictMode -Version Latest
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $projectRoot 'scripts/development-environment.ps1')
$environment = Initialize-DevelopmentEnvironment
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('vapeecore-tablist-order-' + [guid]::NewGuid().ToString('N'))
$receiptDirectory = Join-Path $projectRoot 'target/verification/tablist-rank-order'
$tablistFile = 'src/main/java/dev/vapee/core/presentation/tablist/TablistService.java'
$presentationFile = 'src/main/java/dev/vapee/core/presentation/PresentationService.java'
$harness = 'dev.vapee.core.presentation.tablist.TablistRankOrderHarness'
$positives = @($harness, 'dev.vapee.core.presentation.tablist.TablistOwnershipHarness',
    'dev.vapee.core.presentation.PresentationLifecycleHarness', 'dev.vapee.core.presentation.nametag.NametagLifecycleHarness')
$results = [Collections.Generic.List[object]]::new()
function Invoke-Harness([string]$ClassPath, [string]$Class, [string[]]$Arguments = @()) {
    $output = & $environment.Java -cp $ClassPath $Class @Arguments 2>&1 | Out-String
    [pscustomobject]@{Code=$LASTEXITCODE; Output=$output}
}
function Test-Positives([string]$Stage) {
    foreach ($class in $positives) {
        $result = Invoke-Harness $classpath $class
        [IO.File]::WriteAllText((Join-Path $receiptDirectory ($class.Split('.')[-1] + "-$Stage.log")), $result.Output)
        if ($result.Code -ne 0 -or $result.Output -notmatch 'passed [1-9][0-9]* checks\.') { throw "Positive failed: $class`n$($result.Output)" }
        Write-Host $result.Output.Trim()
    }
}
Push-Location -LiteralPath $projectRoot
try {
    New-Item -ItemType Directory -Path $fixtureRoot | Out-Null
    New-Item -ItemType Directory -Force -Path $receiptDirectory | Out-Null
    Remove-Item -LiteralPath (Join-Path $receiptDirectory 'results.json') -Force -ErrorAction SilentlyContinue
    $classpathFile = Join-Path $projectRoot 'target/tablist-rank-order-classpath.txt'
    $mavenArgs = @('-B', '-ntp')
    if ($Offline) { $mavenArgs += '-o' }
    if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$MavenRepository" }
    & $environment.Maven @mavenArgs test-compile dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation/classpath preparation failed.' }
    $classpath = @((Join-Path $projectRoot 'target/test-classes'), (Join-Path $projectRoot 'target/classes'),
        (Get-Content -LiteralPath $classpathFile -Raw).Trim()) -join [IO.Path]::PathSeparator
    Test-Positives 'before'
    $variants = @(
        @{Id='N1'; File=$tablistFile; Old='Comparator.reverseOrder()'; New='Comparator.naturalOrder()';
            Case='weights'; Expected='higher primary weight first'; Count=1},
        @{Id='N2'; File=$tablistFile; Old='thenComparing(OrderTarget::name, String.CASE_INSENSITIVE_ORDER)';
            New='thenComparing(OrderTarget::name)'; Case='ties'; Expected='equal priority uses case-insensitive actual username'; Count=1},
        @{Id='N3'; File=$tablistFile; Old='thenComparing(OrderTarget::id)';
            New='thenComparing(target -> new UUID(0, 0))'; Case='ties'; Expected='case-insensitive tie uses UUID'; Count=1},
        @{Id='N4'; File=$tablistFile; Old='player.getPlayerListOrder() == lastWritten'; New='true';
            Case='ownership'; Expected='external order ownership survives cleanup'; Count=1},
        @{Id='N5'; File=$tablistFile; Old='int before = written ? previous : current;'; New='int before = current;';
            Case='ownership'; Expected='refresh retains original order baseline'; Count=1},
        @{Id='N6'; File=$tablistFile; Old='fields.order.write(target.player(), roster.size() - index);'; New='/* no actual Minecraft write */';
            Case='weights'; Expected='higher primary weight first'; Count=1},
        @{Id='N7'; File=$presentationFile; Old='updateTablistOrder(online);'; New='/* ordering disconnected from refresh */';
            Case='lifecycle'; Expected='join presentation refresh orders complete roster'; Count=2},
        @{Id='N8'; File=$tablistFile; Old='priorities.put(id, new Priority(comparable && !anchored ? 0 : Long.MIN_VALUE, index + 1));';
            New='priorities.put(id, new Priority(comparable && !anchored ? 0 : Long.MIN_VALUE, track.size() - index));';
            Case='fallback'; Expected='weightless track uses highest ending group first'; Count=1}
    )
    foreach ($variant in $variants) {
        $directory = Join-Path $fixtureRoot $variant.Id
        $classes = Join-Path $directory 'classes'
        New-Item -ItemType Directory -Force -Path $classes | Out-Null
        $source = (Get-Content -LiteralPath $variant.File -Raw).Replace("`r`n", "`n")
        if ($source.Split(@($variant.Old), [StringSplitOptions]::None).Count -ne $variant.Count + 1) { throw "Unexpected mutation anchor: $($variant.Id)" }
        $file = Join-Path $directory ([IO.Path]::GetFileName($variant.File))
        [IO.File]::WriteAllText($file, $source.Replace($variant.Old, $variant.New), [Text.UTF8Encoding]::new($false))
        $compiled = & $environment.Javac -proc:none -encoding UTF-8 -cp $classpath -d $classes $file 2>&1 | Out-String
        $compileCode = $LASTEXITCODE
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-compile.log"), $compiled)
        if ($compileCode -ne 0) { throw "$($variant.Id) did not compile; no behavioral evidence.`n$compiled" }
        $negative = Invoke-Harness ($classes + [IO.Path]::PathSeparator + $classpath) $harness @($variant.Case)
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-behavior.log"), $negative.Output)
        if ($negative.Code -eq 0 -or -not $negative.Output.Contains('java.lang.AssertionError') -or -not $negative.Output.Contains($variant.Expected)) {
            throw "$($variant.Id) missed intended assertion $($variant.Expected)`n$($negative.Output)"
        }
        $results.Add([pscustomobject]@{Id=$variant.Id; CompileExit=$compileCode; BehaviorExit=$negative.Code; Contract=$variant.Expected})
        Write-Host "$($variant.Id): compiled defect detected ($($variant.Expected))."
    }
    Test-Positives 'after'
    [pscustomobject]@{Detected=$results.Count; Results=$results} | ConvertTo-Json -Depth 5 |
        Set-Content -LiteralPath (Join-Path $receiptDirectory 'results.json')
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
        if (-not $resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or
            -not [IO.Path]::GetFileName($resolved).StartsWith('vapeecore-tablist-order-')) { throw "Unsafe cleanup path: $resolved" }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
