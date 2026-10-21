#requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateRange(5, 120)]
    [int]$TimeoutSeconds = 45
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$serverRoot = Join-Path $projectRoot 'dev-server'
$stateRoot = Join-Path $serverRoot '.vapeecore-dev'
$pidFile = Join-Path $stateRoot 'server.pid'
$stopRequestFile = Join-Path $stateRoot 'stop.request'

. (Join-Path $PSScriptRoot 'development-environment.ps1')

$null = Get-Java21Jdk
$jps = Resolve-JavaTool 'jps'

function Find-ManagedServerProcess {
    if (-not $jps) {
        return $null
    }

    $marker = "-Dvapeecore.devServer=$projectRoot"
    $processes = @(& $jps -lv 2>$null)
    if ($LASTEXITCODE -ne 0) { throw 'Could not inspect Java processes with jps. Deployment was cancelled.' }
    foreach ($line in $processes) {
        if ($line -like "*$marker*" -and $line -match '^(\d+)\s') {
            return [int]$Matches[1]
        }
    }
    return $null
}

function Find-UnmanagedPaperProcess {
    if (-not $jps) {
        return $null
    }

    $processes = @(& $jps -lv 2>$null)
    if ($LASTEXITCODE -ne 0) { throw 'Could not inspect Java processes with jps. Deployment was cancelled.' }
    foreach ($line in $processes) {
        if ($line -match '^(\d+)\s+.*(?:paper-[^\s]*\.jar|paperclip|Paperclip)') {
            return [int]$Matches[1]
        }
    }
    return $null
}

$serverPid = 0
if (Test-Path -LiteralPath $pidFile) {
    [void][int]::TryParse((Get-Content -Raw -LiteralPath $pidFile).Trim(), [ref]$serverPid)
    if ($serverPid -gt 0 -and -not (Get-Process -Id $serverPid -ErrorAction SilentlyContinue)) {
        $serverPid = 0
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    }
}

if ($serverPid -le 0) {
    $detectedPid = Find-ManagedServerProcess
    if ($detectedPid) {
        $serverPid = $detectedPid
    }
}

if ($serverPid -le 0) {
    $unmanagedPid = Find-UnmanagedPaperProcess
    if ($unmanagedPid) {
        throw "Paper PID $unmanagedPid was not started through 'Start VapeeCore Dev Server'. Stop it with the console command 'stop' and run the build again."
    }
    Write-Host 'No managed Paper server is running.'
    exit 0
}

New-Item -ItemType Directory -Force -Path $stateRoot | Out-Null
Set-Content -LiteralPath $stopRequestFile -Value 'stop' -Encoding ascii
Write-Host "Requested a clean stop for Paper PID $serverPid."

$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
while ((Get-Process -Id $serverPid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) {
    Start-Sleep -Milliseconds 250
}

if (Get-Process -Id $serverPid -ErrorAction SilentlyContinue) {
    throw "Paper PID $serverPid did not stop within $TimeoutSeconds seconds. Deployment was cancelled; the process was not force-killed."
}

Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $stopRequestFile -Force -ErrorAction SilentlyContinue
Write-Host 'Paper stopped cleanly.'
