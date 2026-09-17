[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$pluginDirectory = Join-Path $projectRoot 'dev-server\plugins'
$sourceJar = Join-Path $projectRoot 'target\vapeecore-1.0-SNAPSHOT.jar'
$destinationJar = Join-Path $pluginDirectory 'vapeecore-1.0-SNAPSHOT.jar'

function Resolve-MavenExecutable {
    if ($env:MAVEN_HOME) {
        $mavenFromHome = Join-Path $env:MAVEN_HOME 'bin\mvn.cmd'
        if (Test-Path -LiteralPath $mavenFromHome) {
            return $mavenFromHome
        }
    }

    $mavenCommand = Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue
    if ($mavenCommand) {
        return $mavenCommand.Source
    }

    $jetBrainsRoot = Join-Path $env:ProgramFiles 'JetBrains'
    if (Test-Path -LiteralPath $jetBrainsRoot) {
        $bundledMaven = Get-ChildItem -LiteralPath $jetBrainsRoot -Directory -Filter 'IntelliJ IDEA*' |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'plugins\maven-plugin\lib\maven3\bin\mvn.cmd' } |
            Where-Object { Test-Path -LiteralPath $_ } |
            Select-Object -First 1
        if ($bundledMaven) {
            return $bundledMaven
        }
    }

    throw 'Maven was not found in MAVEN_HOME, PATH, or the IntelliJ installation.'
}

& (Join-Path $PSScriptRoot 'stop-dev-server.ps1')

$maven = Resolve-MavenExecutable
Write-Host "Building VapeeCore with $maven"
& $maven clean package
if ($LASTEXITCODE -ne 0) {
    throw "Maven build failed with exit code $LASTEXITCODE. The existing deployed plugin was not changed."
}

if (-not (Test-Path -LiteralPath $sourceJar)) {
    throw "Build succeeded but the expected JAR is missing: $sourceJar"
}

New-Item -ItemType Directory -Force -Path $pluginDirectory | Out-Null
$temporaryJar = Join-Path $pluginDirectory ('.vapeecore-' + [Guid]::NewGuid().ToString('N') + '.deploying')
try {
    Copy-Item -LiteralPath $sourceJar -Destination $temporaryJar -Force
    try {
        [System.IO.File]::Move($temporaryJar, $destinationJar, $true)
    } catch {
        Copy-Item -LiteralPath $temporaryJar -Destination $destinationJar -Force
        Remove-Item -LiteralPath $temporaryJar -Force
    }
} finally {
    Remove-Item -LiteralPath $temporaryJar -Force -ErrorAction SilentlyContinue
}

$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $destinationJar).Hash
$size = (Get-Item -LiteralPath $destinationJar).Length
Write-Host "Deployed $destinationJar"
Write-Host "Size: $size bytes"
Write-Host "SHA-256: $hash"
Write-Host "The server remains stopped. Run 'Start VapeeCore Dev Server' when you are ready."
