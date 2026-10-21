# Shared discovery only; dot-sourcing this file has no build/server side effects.
function Resolve-JavaTool([string]$Name) {
    $executable = if ($IsWindows) { "$Name.exe" } else { $Name }
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin/$executable"
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
        throw "Java 21 JDK is required. Missing $executable in JAVA_HOME '$env:JAVA_HOME'. Configure JAVA_HOME to a complete JDK 21."
    }
    $command = Get-Command $executable -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) { return $command.Source }
    throw "Java 21 JDK is required. $executable was not found. Configure JAVA_HOME or add the JDK bin directory to PATH."
}

function Get-Java21Jdk {
    $java = Resolve-JavaTool 'java'
    # java.home also resolves PATH launchers/symlinks to the actual installation.
    $details = (& $java -XshowSettings:properties -version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $details -notmatch 'version "(\d+)[^"]*"') {
        throw "Could not determine Java version using '$java'. Configure JAVA_HOME to a complete JDK 21."
    }
    if ($Matches[1] -ne '21') { throw "Java 21 JDK is required. Found: Java $($Matches[1]). Expected: Java 21. Configure JAVA_HOME." }
    if ($details -notmatch '(?m)^\s*java.home\s*=\s*(.+)\r?$') { throw 'Java did not report java.home. Configure JAVA_HOME to JDK 21.' }
    $env:JAVA_HOME = $Matches[1].Trim()
    $tools = @{}
    foreach ($name in 'java', 'javac', 'javap') {
        $tools[$name] = Resolve-JavaTool $name
        if ($name -ne 'java') {
            $version = (& $tools[$name] -version 2>&1 | Out-String).Trim()
            if ($LASTEXITCODE -ne 0 -or $version -notmatch '^(?:javac\s+)?21(?:\.|$)') {
                throw "Java 21 JDK is required. Found $name`: $version. Configure JAVA_HOME to a consistent JDK 21."
            }
        }
    }
    return [pscustomobject]@{ Java = $tools.java; Javac = $tools.javac; Javap = $tools.javap }
}

function Resolve-MavenExecutable {
    $executable = if ($IsWindows) { 'mvn.cmd' } else { 'mvn' }
    if ($env:MAVEN_HOME) {
        $candidate = Join-Path $env:MAVEN_HOME "bin/$executable"
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
        throw "Maven was not found in MAVEN_HOME '$env:MAVEN_HOME'. Install Maven 3.6.3+ or correct MAVEN_HOME."
    }
    $command = Get-Command $executable -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) { return $command.Source }
    # Optional compatibility fallback for existing Windows/IntelliJ installations.
    if ($IsWindows -and $env:ProgramFiles) {
        $jetBrainsRoot = Join-Path $env:ProgramFiles 'JetBrains'
        if (Test-Path -LiteralPath $jetBrainsRoot) {
            $candidate = Get-ChildItem -LiteralPath $jetBrainsRoot -Directory -Filter 'IntelliJ IDEA*' |
                Sort-Object Name -Descending |
                ForEach-Object { Join-Path $_.FullName 'plugins/maven-plugin/lib/maven3/bin/mvn.cmd' } |
                Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
            if ($candidate) { return $candidate }
        }
    }
    throw 'Maven was not found. Install Maven 3.6.3+, add its bin directory to PATH or configure MAVEN_HOME.'
}

function Initialize-DevelopmentEnvironment {
    $jdk = Get-Java21Jdk
    $maven = Resolve-MavenExecutable
    $version = (& $maven --version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $version -notmatch 'Apache Maven (\d+\.\d+\.\d+)') {
        throw "Could not determine Maven version using '$maven'. Install Maven 3.6.3+ or correct MAVEN_HOME."
    }
    if ([version]$Matches[1] -lt [version]'3.6.3') { throw "Maven 3.6.3+ is required. Found: $($Matches[1]). Upgrade Maven or correct MAVEN_HOME." }
    return [pscustomobject]@{ Maven = $maven; Java = $jdk.Java; Javac = $jdk.Javac; Javap = $jdk.Javap }
}
