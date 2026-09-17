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

function Resolve-JpsExecutable {
    if ($env:JAVA_HOME) {
        $jpsFromHome = Join-Path $env:JAVA_HOME 'bin\jps.exe'
        if (Test-Path -LiteralPath $jpsFromHome) {
            return $jpsFromHome
        }
    }

    $jpsCommand = Get-Command 'jps.exe' -ErrorAction SilentlyContinue
    if ($jpsCommand) {
        return $jpsCommand.Source
    }

    return $null
}

function Find-ManagedServerProcess {
    $jps = Resolve-JpsExecutable
    if (-not $jps) {
        return $null
    }

    $marker = "-Dvapeecore.devServer=$projectRoot"
    foreach ($line in (& $jps -lv 2>$null)) {
        if ($line -like "*$marker*" -and $line -match '^(\d+)\s') {
            return [int]$Matches[1]
        }
    }
    return $null
}

function Find-UnmanagedPaperProcess {
    $jps = Resolve-JpsExecutable
    if (-not $jps) {
        return $null
    }

    foreach ($line in (& $jps -lv 2>$null)) {
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
