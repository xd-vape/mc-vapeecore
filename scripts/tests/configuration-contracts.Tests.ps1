#requires -Version 7.0
[CmdletBinding()]
param([switch]$Offline, [string]$MavenRepository)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Set-StrictMode -Version Latest
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $projectRoot 'scripts/development-environment.ps1')
$environment = Initialize-DevelopmentEnvironment
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('vapeecore-config-negative-' + [guid]::NewGuid().ToString('N'))
$receiptDirectory = Join-Path $projectRoot 'target/verification/configuration-contracts'
$evolution = 'src/main/java/dev/vapee/core/config/ConfigEvolution.java'
$core = 'src/main/java/dev/vapee/core/config/ConfigService.java'
$resourcesHarness = 'dev.vapee.core.config.ConfigResourceContractHarness'
$extensionHarness = 'dev.vapee.core.config.ConfigEvolutionExtensionHarness'
$integrationHarness = 'dev.vapee.core.config.ConfigReloadIntegrationHarness'
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
    $classpathFile = Join-Path $projectRoot 'target/configuration-contract-classpath.txt'
    $mavenArgs = @('-B', '-ntp')
    if ($Offline) { $mavenArgs += '-o' }
    if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$MavenRepository" }
    & $environment.Maven @mavenArgs test-compile dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
    if ($LASTEXITCODE -ne 0) { throw 'Configuration control compilation/classpath preparation failed.' }
    $classpath = (Join-Path $projectRoot 'target/test-classes') + [IO.Path]::PathSeparator +
        (Join-Path $projectRoot 'target/classes') + [IO.Path]::PathSeparator + (Get-Content -LiteralPath $classpathFile -Raw).Trim()
    foreach ($harness in @($resourcesHarness, $extensionHarness, $integrationHarness)) {
        $positive = Invoke-Harness $classpath $harness
        if ($positive.ExitCode -ne 0) { throw "Positive control failed: $harness`n$($positive.Output)" }
        Write-Host $positive.Output.Trim()
    }
    $mergeAnchor = '} else if (!path.equals(VERSION_KEY)) {'
    $overwrite = $mergeAnchor + "`n" + '                if (path.equals("example.enabled")) current.getValue().set(current.getValue().indexOf(present), entry.getValue());'
    $publish = 'CoreConfigState preparedState = readState();'
    $writeDuringPrepare = $publish + "`n" + '        try { Files.writeString(configFile, Files.readString(configFile) + "\n# forbidden reload write\n"); } catch (IOException failure) { throw new IllegalStateException(failure); }'
    $variants = @(
        @{ Id='N1'; Resource=$true; File='src/main/resources/config.yml'; Old='config-version: 1'; New='config-version: 2';
            Harness=$resourcesHarness; Expected='resource/registry version agrees: config.yml' },
        @{ Id='N2'; File=$evolution; Old=$mergeAnchor; New=$overwrite; Old2='!preserved(before, after, true)'; New2='false';
            Harness=$extensionHarness; Expected='existing false preserved independently' },
        @{ Id='N3'; File=$evolution; Old='Files.write(backup, original, java.nio.file.StandardOpenOption.CREATE_NEW);';
            New='Files.write(backup, new byte[0], java.nio.file.StandardOpenOption.CREATE_NEW);';
            Harness=$extensionHarness; Expected='exact backup exists before candidate write' },
        @{ Id='N4'; File=$evolution; Old='if (version > supported)'; New='if (version < 0)';
            Harness=$extensionHarness; Expected='future/current/invalid version refused: 3' },
        @{ Id='N5'; File=$core; Old=$publish; New=($publish + ' state = preparedState;');
            Harness=$integrationHarness; Expected='direct prepare does not publish: core track' },
        @{ Id='N6'; File=$core; Old='() -> state = previousState'; New='() -> { }';
            Harness=$integrationHarness; Arguments=@('coordinated'); Expected='core rollback: core track' },
        @{ Id='N7'; File=$evolution; Old='"config.yml", 1, "lobby.yml", 2, "chat.yml", 1,';
            New='"warps.yml", 1, "config.yml", 1, "lobby.yml", 2, "chat.yml", 1,';
            Harness=$resourcesHarness; Expected='exact frozen operator allowlist and versions' },
        @{ Id='N8'; File=$core; Old=$publish; New=$writeDuringPrepare; Arguments=@('coordinated');
            Harness=$integrationHarness; Expected='reload leaves exact file bytes: config.yml' }
    )
    foreach ($variant in $variants) {
        $directory = Join-Path $fixtureRoot $variant.Id
        New-Item -ItemType Directory -Path "$directory/classes" -Force | Out-Null
        $source = (Get-Content -LiteralPath $variant.File -Raw).Replace("`r`n", "`n")
        $mutated = Replace-Once $source $variant.Old $variant.New
        if ($variant.ContainsKey('Old2')) { $mutated = Replace-Once $mutated $variant.Old2 $variant.New2 }
        $compileExit = $null
        if ($variant.ContainsKey('Resource')) {
            [IO.File]::WriteAllText("$directory/classes/config.yml", $mutated, [Text.UTF8Encoding]::new($false))
        } else {
            $sourceFile = Join-Path $directory ([IO.Path]::GetFileName($variant.File))
            [IO.File]::WriteAllText($sourceFile, $mutated, [Text.UTF8Encoding]::new($false))
            $compile = & $environment.Javac -proc:none -encoding UTF-8 -cp $classpath -d "$directory/classes" $sourceFile 2>&1 | Out-String
            $compileExit = $LASTEXITCODE
            [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-compile.log"), $compile)
            if ($compileExit -ne 0) { throw "$($variant.Id) did not compile; NOT evidence of a detected behavioral regression.`n$compile" }
        }
        $arguments = if ($variant.ContainsKey('Arguments')) { $variant.Arguments } else { @() }
        $negative = Invoke-Harness ("$directory/classes" + [IO.Path]::PathSeparator + $classpath) $variant.Harness $arguments
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-behavior.log"), $negative.Output)
        if ($negative.ExitCode -eq 0 -or -not $negative.Output.Contains($variant.Expected)) {
            throw "$($variant.Id) did not fail for the intended contract.`n$($negative.Output)"
        }
        $results.Add([pscustomobject]@{ Id=$variant.Id; Kind=if($variant.ContainsKey('Resource')){'Resource'}else{'Java'};
            CompileExit=$compileExit; BehaviorExit=$negative.ExitCode; Expected=$variant.Expected; Detected=$true })
        Write-Host "$($variant.Id): isolated variant; intended contract failure detected."
    }
    foreach ($harness in @($resourcesHarness, $extensionHarness, $integrationHarness)) {
        $positive = Invoke-Harness $classpath $harness
        if ($positive.ExitCode -ne 0) { throw "Ordinary-classpath positive failed: $harness`n$($positive.Output)" }
        Write-Host $positive.Output.Trim()
    }
    $results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $receiptDirectory 'results.json') -Encoding utf8
    Write-Host 'Configuration controls: 8/8 detected (7 compiled Java variants, 1 resource override); restored positives PASS.'
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        $temporary = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
        if (-not $resolved.StartsWith($temporary, [StringComparison]::OrdinalIgnoreCase) -or
            -not ([IO.Path]::GetFileName($resolved)).StartsWith('vapeecore-config-negative-')) { throw "Unexpected cleanup target: $resolved" }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
