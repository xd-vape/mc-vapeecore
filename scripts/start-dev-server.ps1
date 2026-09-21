[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$serverRoot = Join-Path $projectRoot 'dev-server'
$stateRoot = Join-Path $serverRoot '.vapeecore-dev'
$pidFile = Join-Path $stateRoot 'server.pid'
$stopRequestFile = Join-Path $stateRoot 'stop.request'

function Resolve-JavaExecutable {
    if ($env:JAVA_HOME) {
        $javaFromHome = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Path -LiteralPath $javaFromHome) {
            return $javaFromHome
        }
    }

    $javaCommand = Get-Command 'java.exe' -ErrorAction SilentlyContinue
    if ($javaCommand) {
        return $javaCommand.Source
    }

    throw 'Java 21 was not found. Configure JAVA_HOME or add java.exe to PATH.'
}

New-Item -ItemType Directory -Force -Path $stateRoot | Out-Null

if (Test-Path -LiteralPath $pidFile) {
    $recordedPid = 0
    [void][int]::TryParse((Get-Content -Raw -LiteralPath $pidFile).Trim(), [ref]$recordedPid)
    if ($recordedPid -gt 0 -and (Get-Process -Id $recordedPid -ErrorAction SilentlyContinue)) {
        throw "The managed Paper server is already running with PID $recordedPid."
    }
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
}

Remove-Item -LiteralPath $stopRequestFile -Force -ErrorAction SilentlyContinue

$paperJar = Get-ChildItem -LiteralPath $serverRoot -File -Filter 'paper-*.jar' |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $paperJar) {
    throw "No paper-*.jar was found in $serverRoot."
}

$processInfo = [System.Diagnostics.ProcessStartInfo]::new()
$processInfo.FileName = Resolve-JavaExecutable
$processInfo.WorkingDirectory = $serverRoot
$processInfo.UseShellExecute = $false
$processInfo.RedirectStandardInput = $true
$processInfo.RedirectStandardOutput = $false
$processInfo.RedirectStandardError = $false
$processInfo.CreateNoWindow = $false
$processInfo.ArgumentList.Add('-Xms4096M')
$processInfo.ArgumentList.Add('-Xmx4096M')
$processInfo.ArgumentList.Add("-Dvapeecore.devServer=$projectRoot")
$processInfo.ArgumentList.Add('-jar')
$processInfo.ArgumentList.Add($paperJar.FullName)
$processInfo.ArgumentList.Add('--nogui')

$serverProcess = [System.Diagnostics.Process]::new()
$serverProcess.StartInfo = $processInfo
$exitCode = 1

try {
    if (-not $serverProcess.Start()) {
        throw 'Paper could not be started.'
    }

    Set-Content -LiteralPath $pidFile -Value $serverProcess.Id -Encoding ascii
    Write-Host "Managed Paper server started with PID $($serverProcess.Id)."
    Write-Host 'Build & Deploy will send a clean stop automatically.'
    Write-Host 'Paper console commands can be entered directly in this window.'

    $stopSent = $false
    $consoleInputOpen = $true
    $consoleReader = [System.IO.StreamReader]::new([Console]::OpenStandardInput())
    $consoleReadTask = $null
    while (-not $serverProcess.HasExited) {
        if (-not $stopSent -and (Test-Path -LiteralPath $stopRequestFile)) {
            Remove-Item -LiteralPath $stopRequestFile -Force -ErrorAction SilentlyContinue
            Write-Host 'Clean stop requested by Build & Deploy.'
            $serverProcess.StandardInput.WriteLine('stop')
            $serverProcess.StandardInput.Flush()
            $stopSent = $true
        }

        if ($consoleInputOpen -and $null -eq $consoleReadTask) {
            try {
                $consoleReadTask = $consoleReader.ReadLineAsync()
            } catch {
                $consoleInputOpen = $false
                Write-Warning "Console input is not available: $($_.Exception.Message)"
            }
        }

        if ($null -ne $consoleReadTask -and $consoleReadTask.IsCompleted) {
            try {
                $command = $consoleReadTask.GetAwaiter().GetResult()
                if ($null -eq $command) {
                    $consoleInputOpen = $false
                } elseif (-not $serverProcess.HasExited) {
                    $serverProcess.StandardInput.WriteLine($command)
                    $serverProcess.StandardInput.Flush()
                }
            } catch {
                $consoleInputOpen = $false
                Write-Warning "Console input could not be forwarded to Paper: $($_.Exception.Message)"
            } finally {
                $consoleReadTask = $null
            }
        }

        Start-Sleep -Milliseconds 100
    }

    $serverProcess.WaitForExit()
    $exitCode = $serverProcess.ExitCode
} finally {
    if ($serverProcess -and -not $serverProcess.HasExited) {
        try {
            $serverProcess.StandardInput.WriteLine('stop')
            $serverProcess.StandardInput.Flush()
            if (-not $serverProcess.WaitForExit(30000)) {
                Write-Warning 'Paper did not stop within 30 seconds; the process is left running for safety.'
            }
        } catch {
            Write-Warning "Paper could not be stopped through its console: $($_.Exception.Message)"
        }
    }

    if (-not $serverProcess -or $serverProcess.HasExited) {
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $stopRequestFile -Force -ErrorAction SilentlyContinue
    }
    if ($serverProcess) {
        $serverProcess.Dispose()
    }
}

exit $exitCode
