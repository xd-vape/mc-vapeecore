#requires -Version 7.0
[CmdletBinding()]
param(
    [switch]$Offline,
    [string]$MavenRepository
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$pluginDirectory = Join-Path $projectRoot 'dev-server/plugins'
$sourceJar = Join-Path $projectRoot 'target/vapeecore-1.0-SNAPSHOT.jar'
$destinationJar = Join-Path $pluginDirectory 'vapeecore-1.0-SNAPSHOT.jar'

# Validation has no server side effects. Stop Paper only after the complete gate passes.
$shell = (Get-Process -Id $PID).Path
$verifyArgs = @('-NoProfile', '-File', (Join-Path $PSScriptRoot 'verify.ps1'))
if ($Offline) { $verifyArgs += '-Offline' }
if ($MavenRepository) { $verifyArgs += @('-MavenRepository', $MavenRepository) }
& $shell @verifyArgs
if ($LASTEXITCODE -ne 0) { throw 'Verification failed. The existing deployed plugin was not changed.' }
& $shell -NoProfile -File (Join-Path $PSScriptRoot 'stop-dev-server.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Stopping Paper failed. Deployment was cancelled.' }
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
