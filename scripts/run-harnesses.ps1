#requires -Version 7.0
[CmdletBinding()]
param(
    [switch]$Offline,
    [string]$MavenRepository
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$logRoot = Join-Path $projectRoot 'dev-server/.vapeecore-dev/harnesses'
New-Item -ItemType Directory -Force -Path $logRoot | Out-Null
$results = [System.Collections.Generic.List[object]]::new()
$classes = [System.Collections.Generic.List[string]]::new()
$stage = 'Build/Classpath'
$failure = $null

function Resolve-MavenExecutable {
    if ($env:MAVEN_HOME) {
        $candidate = Join-Path $env:MAVEN_HOME 'bin/mvn.cmd'
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    $command = Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $jetBrainsRoot = Join-Path $env:ProgramFiles 'JetBrains'
    if (Test-Path -LiteralPath $jetBrainsRoot) {
        $candidate = Get-ChildItem -LiteralPath $jetBrainsRoot -Directory -Filter 'IntelliJ IDEA*' |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'plugins/maven-plugin/lib/maven3/bin/mvn.cmd' } |
            Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
        if ($candidate) { return $candidate }
    }
    throw 'Maven was not found in MAVEN_HOME, PATH, or the IntelliJ installation.'
}

function Invoke-CapturedProcess([string]$Executable, [string[]]$Arguments) {
    $info = [System.Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $Executable
    $info.WorkingDirectory = $projectRoot
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    foreach ($argument in $Arguments) { $info.ArgumentList.Add($argument) }
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $info
    try {
        if (-not $process.Start()) { throw "Could not start $Executable" }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            Stdout = $stdout.GetAwaiter().GetResult()
            Stderr = $stderr.GetAwaiter().GetResult()
        }
    } finally { $process.Dispose() }
}

Push-Location -LiteralPath $projectRoot
try {
    $mavenArgs = @()
    if ($Offline) { $mavenArgs += '-o' }
    if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$MavenRepository" }
    $classpathFile = Join-Path $projectRoot 'target/harness-classpath.txt'
    # Never accept a stale classpath receipt after a failed resolver run.
    Remove-Item -LiteralPath $classpathFile -Force -ErrorAction SilentlyContinue
    & (Resolve-MavenExecutable) @mavenArgs test-compile dependency:build-classpath "-Dmdep.outputFile=$classpathFile" *> (Join-Path $logRoot 'maven.log')
    if ($LASTEXITCODE -ne 0) { throw "Maven test compilation/classpath failed ($LASTEXITCODE); see maven.log." }
    if (-not (Test-Path -LiteralPath $classpathFile)) { throw 'Maven did not produce the dependency classpath.' }
    $dependencies = (Get-Content -Raw -LiteralPath $classpathFile).Trim()
    foreach ($entry in ($dependencies -split [regex]::Escape([string][IO.Path]::PathSeparator))) {
        if (-not $entry -or -not (Test-Path -LiteralPath $entry)) { throw "Missing classpath entry: $entry" }
    }
    $classpath = @((Join-Path $projectRoot 'target/test-classes'), (Join-Path $projectRoot 'target/classes'), $dependencies) -join [IO.Path]::PathSeparator
    $stage = 'Discovery'
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java.exe -ErrorAction Stop).Source }
    $javap = Join-Path (Split-Path -Parent $java) 'javap.exe'
    if (-not (Test-Path -LiteralPath $javap)) { throw 'A Java 21 JDK with javap is required (JAVA_HOME or PATH).' }
    $sourceRoot = Join-Path $projectRoot 'src/test/java'
    $candidates = @(Get-ChildItem -LiteralPath $sourceRoot -Recurse -File -Filter '*Harness.java' |
        ForEach-Object {
            [IO.Path]::GetRelativePath($sourceRoot, $_.FullName).Replace('\', '.').Replace('/', '.') -replace '\.java$', ''
        } | Sort-Object -CaseSensitive -Unique)
    if ($candidates.Count -eq 0) { throw 'Discovery mismatch: no *Harness.java sources found.' }
    foreach ($candidate in $candidates) {
        # Inspect only each source's corresponding top-level class, never nested/generated classes.
        # javap verifies bytecode, so comments, strings, overloads and historical mains cannot count.
        $inspection = Invoke-CapturedProcess $javap @('-classpath', (Join-Path $projectRoot 'target/test-classes'), '-public', $candidate)
        if ($inspection.ExitCode -ne 0) { throw "Discovery mismatch for $candidate`: $($inspection.Stderr)" }
        if ($inspection.Stdout -match '(?m)^\s+public static (?:(?:final|synchronized|native|strictfp) )*void main\(java\.lang\.String(?:\[\]|\.\.\.)\)(?: throws [^;]+)?;\s*$') {
            $classes.Add($candidate)
        }
    }
    if ($classes.Count -eq 0) { throw 'Discovery mismatch: no executable harnesses found.' }
    $classes | Set-Content -LiteralPath (Join-Path $logRoot 'inventory.txt') -Encoding utf8
    Write-Host "Discovered $($classes.Count) executable harnesses."
    $stage = 'Execution'
    foreach ($class in $classes) {
        $exitCode = $null
        $stdout = ''
        $stderr = ''
        $checks = $null
        $status = 'LAUNCH_FAILED'
        try {
            $run = Invoke-CapturedProcess $java @('-cp', $classpath, $class)
            $exitCode = $run.ExitCode
            $stdout = $run.Stdout
            $stderr = $run.Stderr
            $simpleName = ($class -split '\.')[-1]
            $pattern = '(?m)^' + [regex]::Escape($simpleName) + ' passed ([0-9]+) checks\.\r?$'
            $matchesFound = [regex]::Matches($stdout, $pattern)
            [long]$parsedCount = 0
            if ($exitCode -ne 0) {
                $status = if ($stderr -match 'Could not find or load main class|ClassNotFoundException|NoClassDefFoundError|UnsupportedClassVersionError') { 'LAUNCH_FAILED' } else { 'PROCESS_FAILED' }
            } elseif ($matchesFound.Count -ne 1 -or -not [long]::TryParse($matchesFound[0].Groups[1].Value, [ref]$parsedCount) -or $parsedCount -le 0) {
                $status = 'MISSING_INVALID'
                $checks = $null
            } else { $status = 'PASS'; $checks = $parsedCount }
        } catch { $stderr += $_.Exception.ToString() }
        $stdoutPath = Join-Path $logRoot ($class + '.stdout.log')
        $stderrPath = Join-Path $logRoot ($class + '.stderr.log')
        $stdout | Set-Content -LiteralPath $stdoutPath -Encoding utf8
        $stderr | Set-Content -LiteralPath $stderrPath -Encoding utf8
        $results.Add([pscustomobject]@{ Class = $class; Status = $status; ExitCode = $exitCode; Checks = $checks; Stdout = $stdoutPath; Stderr = $stderrPath })
        if ($status -eq 'PASS') { Write-Host "PASS $class ($checks checks)" } else { Write-Host "$status $class" }
        if ($status -ne 'PASS') { Write-Host "$stdout`n$stderr" }
    }
} catch {
    $failure = "$stage`: $($_.Exception.Message)"
    Write-Host $failure
} finally {
    Pop-Location
}
$passed = @($results | Where-Object Status -eq 'PASS').Count
$missing = @($results | Where-Object Status -eq 'MISSING_INVALID').Count
$failed = $results.Count - $passed
if ($failure) { $failed++ }
$total = [long](($results | Where-Object Status -eq 'PASS' | Measure-Object Checks -Sum).Sum)
$summary = [pscustomobject]@{
    Discovered = $classes.Count; Executed = $results.Count; Passed = $passed
    Failed = $failed; MissingInvalid = $missing; TotalChecks = $total; Failure = $failure
    Results = @($results.ToArray())
}
$summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $logRoot 'results.json') -Encoding utf8
Write-Host "Discovered=$($classes.Count) Executed=$($results.Count) Passed=$passed Failed=$failed Missing/Invalid=$missing TotalChecks=$total"
if ($failed -gt 0 -or $missing -gt 0 -or $classes.Count -eq 0 -or $classes.Count -ne $results.Count) { exit 1 }
exit 0
