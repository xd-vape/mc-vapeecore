#requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Verify', 'BuildAndDeploy', 'Start', 'Stop')]
    [string]$Task,
    [switch]$Offline,
    [string]$MavenRepository
)

$ErrorActionPreference = 'Stop'
try {
    . (Join-Path $PSScriptRoot 'development-environment.ps1')
    $shell = Resolve-PowerShellExecutable
    $entry = switch ($Task) {
        'Verify' { 'verify.ps1' }
        'BuildAndDeploy' { 'build-and-deploy.ps1' }
        'Start' { 'start-dev-server.ps1' }
        'Stop' { 'stop-dev-server.ps1' }
    }
    if ($Task -in @('Start', 'Stop') -and ($Offline -or $MavenRepository)) {
        throw 'Offline and MavenRepository apply only to Verify or BuildAndDeploy.'
    }
    $arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $PSScriptRoot $entry))
    if ($Offline) { $arguments += '-Offline' }
    if ($MavenRepository) { $arguments += @('-MavenRepository', $MavenRepository) }
    & $shell @arguments
    exit $LASTEXITCODE
} catch {
    Write-Host "Development launch failed: $($_.Exception.Message)"
    exit 1
}
