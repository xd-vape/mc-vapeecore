#requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$LiveFile,
    [Parameter(Mandatory)][string]$CandidateFile,
    [switch]$Apply,
    [switch]$Offline,
    [string]$MavenRepository
)

# Explicit developer/operator action, never called by startup, reload or deployment.
# The candidate is organized manually; this script only validates and optionally adopts it.
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$live = (Resolve-Path -LiteralPath $LiveFile).Path
$candidate = (Resolve-Path -LiteralPath $CandidateFile).Path
$maven = if ($env:MAVEN_HOME -and (Test-Path -LiteralPath (Join-Path $env:MAVEN_HOME 'bin/mvn.cmd'))) {
    Join-Path $env:MAVEN_HOME 'bin/mvn.cmd'
} elseif (Get-Command mvn.cmd -ErrorAction SilentlyContinue) {
    (Get-Command mvn.cmd).Source
} else {
    Get-ChildItem -LiteralPath (Join-Path $env:ProgramFiles 'JetBrains') -Directory -Filter 'IntelliJ IDEA*' |
        Sort-Object Name -Descending |
        ForEach-Object { Join-Path $_.FullName 'plugins/maven-plugin/lib/maven3/bin/mvn.cmd' } |
        Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $maven) { throw 'Maven is required in MAVEN_HOME, PATH or the IntelliJ installation.' }
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java.exe).Source }
Push-Location -LiteralPath $projectRoot
try {
    $mavenArgs = @()
    if ($Offline) { $mavenArgs += '-o' }
    if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$MavenRepository" }
    $classpathFile = Join-Path $projectRoot 'target/language-catalog-classpath.txt'
    & $maven @mavenArgs test-compile dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
    if ($LASTEXITCODE -ne 0) { throw 'Catalog validator build failed; no live file was changed.' }
    $classpath = @((Join-Path $projectRoot 'target/test-classes'), (Join-Path $projectRoot 'target/classes'),
        (Get-Content -Raw -LiteralPath $classpathFile).Trim()) -join [IO.Path]::PathSeparator
    $mode = if ($Apply) { 'adopt' } else { 'preview' }
    & $java -cp $classpath dev.vapee.core.i18n.LanguageCatalogFiles $mode $live $candidate
    if ($LASTEXITCODE -ne 0) { throw 'Catalog validation/adoption rejected; inspect the diagnostic and any reported backup.' }
} finally { Pop-Location }
