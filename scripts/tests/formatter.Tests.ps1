#requires -Version 7.0
[CmdletBinding()]
param(
    [switch]$Offline,
    [string]$MavenRepository,
    [string]$OriginalDirectory
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $projectRoot 'scripts/development-environment.ps1')
$environment = Initialize-DevelopmentEnvironment
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('vapeecore-formatter-' + [guid]::NewGuid().ToString('N'))
$checks = 0
$mavenArgs = @('-B', '-ntp')
if ($Offline) { $mavenArgs += '-o' }
if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$MavenRepository" }
function Assert([bool]$Condition, [string]$Reason) {
    $script:checks++
    if (-not $Condition) { throw $Reason }
}
function Write-Fixture([string]$Relative, [string]$Value) {
    $path = Join-Path $fixtureRoot $Relative
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $path) | Out-Null
    [IO.File]::WriteAllText($path, $Value)
}
function Snapshot {
    return @(Get-ChildItem -LiteralPath (Join-Path $fixtureRoot 'src') -File -Filter '*.java' -Recurse |
        Sort-Object FullName | ForEach-Object { $_.FullName + ':' + (Get-FileHash -LiteralPath $_.FullName).Hash }) -join "`n"
}
function Format([string]$Goal, [string[]]$Extra = @()) {
    $output = & $environment.Maven @mavenArgs -f (Join-Path $fixtureRoot 'pom.xml') $Goal @Extra 2>&1 | Out-String
    $code = $LASTEXITCODE
    if ($code -ne 0) { Write-Verbose $output }
    return $code
}
try {
    New-Item -ItemType Directory -Force -Path $fixtureRoot | Out-Null
    [xml]$pom = Get-Content -LiteralPath (Join-Path $projectRoot 'pom.xml') -Raw
    $plugin = $pom.SelectSingleNode("//*[local-name()='plugin'][*[local-name()='artifactId']='spotless-maven-plugin']")
    Assert ([string]$plugin.version -match '^\d+[.]\d+[.]\d+$' -and [string]$plugin.configuration.java.googleJavaFormat.version -match '^\d+[.]\d+[.]\d+$') 'Plugin and formatter must have explicit release versions.'
    Assert ($plugin.configuration.java.googleJavaFormat.style -eq 'AOSP' -and $plugin.configuration.java.googleJavaFormat.reflowLongStrings -eq 'false' -and $plugin.configuration.java.googleJavaFormat.formatJavadoc -eq 'false') 'Formatter must match four-space editor defaults and preserve string/Javadoc reflow policy.'
    $pilot = @($plugin.configuration.java.includes.include | ForEach-Object { [string]$_ })
    Assert ($pilot.Count -le 8 -and $pilot.Count -eq @($pilot | Sort-Object -Unique).Count) 'Pilot must be explicit, unique and at most eight files.'
    Assert (@($pilot | Where-Object { $_ -match '[*?]' }).Count -eq 0) 'Pilot must not contain Java globs.'
    $production = @($pilot | Where-Object { $_.StartsWith('src/main/java/') }).Count
    $tests = @($pilot | Where-Object { $_.StartsWith('src/test/java/') }).Count
    Assert ($production -ge 3 -and $production -le 6 -and $tests -ge 1 -and $tests -le 2 -and $production + $tests -eq $pilot.Count) 'Pilot responsibility counts must remain bounded.'
    Assert (@($pilot | Where-Object { $_ -match '/(VapeeCore|BlackjackCommand|FilePlayerRepository)[.]java$' }).Count -eq 0) 'Excluded large classes must stay outside the pilot.'
    $attributes = @(Get-Content (Join-Path $projectRoot '.gitattributes') | Where-Object { $_ -and -not $_.StartsWith('#') })
    Assert ((($attributes | Sort-Object) -join "`n") -eq (($pilot | ForEach-Object { "$_ text eol=lf" } | Sort-Object) -join "`n")) 'Git LF rules must cover exactly the pilot.'
    $pluginXml = $plugin.OuterXml.Replace(' xmlns="http://maven.apache.org/POM/4.0.0"', '')
    Write-Fixture 'pom.xml' ("<project xmlns=`"http://maven.apache.org/POM/4.0.0`"><modelVersion>4.0.0</modelVersion><groupId>dev.vapee.fixture</groupId><artifactId>formatter-tests</artifactId><version>1</version><build><plugins>" + $pluginXml + '</plugins></build></project>')
    foreach ($file in $pilot) { Write-Fixture $file (Get-Content -LiteralPath (Join-Path $projectRoot $file) -Raw) }
    $utility = $pilot | Where-Object { $_.EndsWith('/ResetCountdownFormatter.java') } | Select-Object -First 1
    Assert ([bool]$utility) 'Countdown utility provides the isolated mutation/Java 21 probe.'
    $utilityPath = Join-Path $fixtureRoot $utility
    [IO.File]::AppendAllText($utilityPath, @'

record FormatterJava21Probe(Object value) {
String label() {return switch(value) {case String text when !text.isBlank() -> text;default -> "fallback";};}
}
'@)
    # Parse, do not attribute: no Bukkit/dependency resolution is needed for an AST comparison.
    Write-Fixture 'SourceEquivalence.java' @'
import com.sun.source.util.JavacTask;
import java.nio.file.Path;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
class SourceEquivalence {
    static String snapshot(String file) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var errors = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(errors, null, java.nio.charset.StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, manager, errors,
                    List.of("--release", "21", "-proc:none"), null,
                    manager.getJavaFileObjects(Path.of(file)));
            var tree = task.parse().iterator().next();
            if (errors.getDiagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR))
                throw new AssertionError(errors.getDiagnostics());
            return tree.getPackageName() + "\n" + tree.getImports().stream()
                    .map(Object::toString).sorted().toList() + "\n" + tree.getTypeDecls();
        }
    }
    public static void main(String[] args) throws Exception {
        for (int i = 0; i < args.length; i += 2)
            if (!snapshot(args[i]).equals(snapshot(args[i + 1])))
                throw new AssertionError("Java structure/literals changed: " + args[i]);
    }
}
'@
    $comparisonArgs = @()
    foreach ($file in $pilot) {
        $saved = Join-Path $fixtureRoot "before/$file"
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $saved) | Out-Null
        Copy-Item -LiteralPath (Join-Path $fixtureRoot $file) -Destination $saved
        $comparisonArgs += @($saved, (Join-Path $fixtureRoot $file))
    }
    Write-Fixture 'src/main/java/OutsidePilot.java' 'class OutsidePilot{int deliberatelyUnformatted=1;}'
    Write-Fixture 'src/test/java/OutsidePilotTest.java' 'class OutsidePilotTest{int deliberatelyUnformatted=2;}'
    $outsideBefore = Snapshot
    Assert ((Format 'spotless:apply') -eq 0) 'First apply must succeed with the real pinned formatter.'
    & $environment.Java (Join-Path $fixtureRoot 'SourceEquivalence.java') @comparisonArgs
    Assert ($LASTEXITCODE -eq 0) 'Formatting must preserve parsed declarations, signatures, statements, literals and import sets.'
    & $environment.Javac --release 21 -d (Join-Path $fixtureRoot 'classes') $utilityPath
    Assert ($LASTEXITCODE -eq 0) 'Formatted record/guarded pattern switch must compile with Java 21.'
    $first = Snapshot
    Assert ($first -ne $outsideBefore) 'Fixture must actually exercise formatting.'
    Assert ((Format 'spotless:apply') -eq 0 -and (Snapshot) -eq $first) 'Second uncached apply must be byte-idempotent.'
    Assert ((Format 'spotless:check') -eq 0 -and (Snapshot) -eq $first) 'Successful check must not modify Java files.'
    Assert ((Get-Content (Join-Path $fixtureRoot 'src/main/java/OutsidePilot.java') -Raw) -eq 'class OutsidePilot{int deliberatelyUnformatted=1;}') 'Non-pilot production source must remain byte-identical.'
    Assert ((Get-Content (Join-Path $fixtureRoot 'src/test/java/OutsidePilotTest.java') -Raw) -eq 'class OutsidePilotTest{int deliberatelyUnformatted=2;}') 'Non-pilot test source must remain byte-identical.'
    $cleanBytes = [IO.File]::ReadAllBytes($utilityPath)
    $cleanText = [IO.File]::ReadAllText($utilityPath)
    [IO.File]::WriteAllText($utilityPath, $cleanText.Replace('public final class', 'public  final   class'))
    $violated = Snapshot
    Assert ($violated -ne $first) 'Negative control must alter a pilot fixture.'
    Assert ((Format 'spotless:check') -ne 0 -and (Snapshot) -eq $violated) 'Real check must reject bad formatting without correcting it.'

    # Run the real Verify wrapper and real formatter. Only the expensive build and runner are stubs.
    New-Item -ItemType Directory -Force -Path (Join-Path $fixtureRoot 'scripts') | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectRoot 'scripts/verify.ps1') -Destination (Join-Path $fixtureRoot 'scripts/verify.ps1')
    $quotedMaven = $environment.Maven.Replace("'", "''")
    Write-Fixture 'scripts/fixture-maven.ps1' ("if (`$args -contains 'clean') { exit 0 }`n& '$quotedMaven' @args`nexit `$LASTEXITCODE`n")
    Write-Fixture 'scripts/development-environment.ps1' 'function Initialize-DevelopmentEnvironment { [pscustomobject]@{Maven=(Join-Path $PSScriptRoot "fixture-maven.ps1");Java="controlled JDK 21"} }'
    Write-Fixture 'scripts/run-harnesses.ps1' 'Set-Content (Join-Path $PSScriptRoot "../runner-called") "must not run"; exit 0'
    $shell = (Get-Process -Id $PID).Path
    $verifyArgs = @('-NoProfile', '-File', (Join-Path $fixtureRoot 'scripts/verify.ps1'))
    if ($Offline) { $verifyArgs += '-Offline' }
    if ($MavenRepository) { $verifyArgs += @('-MavenRepository', $MavenRepository) }
    $output = & $shell @verifyArgs 2>&1 | Out-String
    Assert ($LASTEXITCODE -ne 0 -and $output.Contains('Formatting validation failed') -and -not $output.Contains('VERIFICATION SUCCEEDED')) 'Real formatter failure must fail official verification.'
    Assert (-not (Test-Path (Join-Path $fixtureRoot 'runner-called')) -and (Snapshot) -eq $violated) 'Formatting failure must stop before regression and preserve fixture bytes.'
    Assert (-not (Test-Path (Join-Path $fixtureRoot 'dev-server'))) 'Verification must not create a dev-server directory.'
    [IO.File]::WriteAllBytes($utilityPath, $cleanBytes)
    Assert ((Format 'spotless:check') -eq 0 -and (Snapshot) -eq $first) 'Exact-byte restoration must return to a passing check.'
    Assert ((Format 'spotless:apply' @('-DspotlessFiles=.*OutsidePilot[.]java')) -eq 0 -and (Snapshot) -eq $first) 'A file selector must not widen the allowlist.'
    [IO.File]::WriteAllText($utilityPath, $cleanText.Replace('public final class', 'public  final   class'))
    Assert ((Format 'spotless:apply' @('-DspotlessFiles=.*ResetCountdownFormatter[.]java')) -eq 0 -and (Snapshot) -eq $first) 'An explicitly selected pilot file must format without touching other files.'

    $beforeUtility = Join-Path $fixtureRoot "before/$utility"
    [IO.File]::WriteAllText($utilityPath, $cleanText.Replace('"%02d:%02d:%02d"', '"%02d/%02d/%02d"'))
    & $environment.Java (Join-Path $fixtureRoot 'SourceEquivalence.java') $beforeUtility $utilityPath 2>&1 | Out-String | Write-Verbose
    Assert ($LASTEXITCODE -ne 0) 'AST comparison must detect an actual string-literal behavior change.'
    [IO.File]::WriteAllBytes($utilityPath, $cleanBytes)
    if ($OriginalDirectory) {
        $actualPairs = @()
        foreach ($file in $pilot) { $actualPairs += @((Join-Path $OriginalDirectory $file), (Join-Path $projectRoot $file)) }
        & $environment.Java (Join-Path $fixtureRoot 'SourceEquivalence.java') @actualPairs
        Assert ($LASTEXITCODE -eq 0) 'Actual pilot must match its pre-format Java AST, including literals and signatures.'
    }
    Write-Host "Formatter tests passed $checks checks. Offline=$([bool]$Offline) Pilot=$($pilot.Count)."
} finally {
    $resolved = [IO.Path]::GetFullPath($fixtureRoot)
    $temporary = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if (-not $resolved.StartsWith($temporary, [StringComparison]::OrdinalIgnoreCase) -or
        -not ([IO.Path]::GetFileName($resolved)).StartsWith('vapeecore-formatter-')) { throw 'Unsafe formatter fixture cleanup path.' }
    if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
