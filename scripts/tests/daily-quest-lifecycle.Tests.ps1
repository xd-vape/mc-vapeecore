#requires -Version 7.0
[CmdletBinding()]
param([switch]$Offline, [string]$MavenRepository)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Set-StrictMode -Version Latest
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $projectRoot 'scripts/development-environment.ps1')
$environment = Initialize-DevelopmentEnvironment
$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('vapeecore-daily-negative-' + [guid]::NewGuid().ToString('N'))
$receiptDirectory = Join-Path $projectRoot 'target/verification/daily-quest-lifecycle'
$sourceDirectory = Join-Path $projectRoot 'src/main/java/dev/vapee/core/quest/daily'
$moduleHarness = 'dev.vapee.core.quest.daily.DailyQuestModuleLifecycleHarness'
$menuHarness = 'dev.vapee.core.quest.daily.menu.DailyQuestMenuLifecycleHarness'
$positives = @($moduleHarness, $menuHarness, 'dev.vapee.core.quest.daily.DailyQuestLifecycleHarness',
    'dev.vapee.core.quest.daily.DailyQuestConfigHarness', 'dev.vapee.core.quest.daily.DailyQuestCycleSelectorHarness',
    'dev.vapee.core.quest.daily.DailyQuestResetHarness', 'dev.vapee.core.quest.DailyQuestServiceHarness',
    'dev.vapee.core.quest.daily.menu.DailyQuestCountdownHarness', 'dev.vapee.core.quest.daily.menu.DailyQuestPaginationUpdateHarness',
    'dev.vapee.core.quest.daily.menu.DailyQuestForceHarness', 'dev.vapee.core.quest.daily.menu.QuestMenuHarness',
    'dev.vapee.core.quest.daily.menu.QuestMenuSecurityHarness', 'dev.vapee.core.quest.QuestModuleLifecycleHarness',
    'dev.vapee.core.quest.QuestLifecycleHarness', 'dev.vapee.core.quest.QuestServiceHarness',
    'dev.vapee.core.player.repository.PlayerQuestPersistenceHarness')
$results = [Collections.Generic.List[object]]::new()

function Replace-Once([string]$Source, [string]$Old, [string]$New) {
    if (($Source.Split(@($Old), [StringSplitOptions]::None)).Count -ne 2) { throw "Mutation anchor must occur once: $Old" }
    return $Source.Replace($Old, $New)
}
function Invoke-Harness([string]$ClassPath, [string]$Harness, [string[]]$Arguments = @()) {
    $output = & $environment.Java -cp $ClassPath $Harness @Arguments 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode=$LASTEXITCODE; Output=$output }
}
function Test-Positives([string]$Phase) {
    foreach ($harness in $positives) {
        $positive = Invoke-Harness $classpath $harness
        [IO.File]::WriteAllText((Join-Path $receiptDirectory ($harness.Split('.')[-1] + "-positive-$Phase.log")), $positive.Output)
        if ($positive.ExitCode -ne 0 -or $positive.Output -notmatch 'passed [1-9][0-9]* checks\.') { throw "Positive control failed: $harness`n$($positive.Output)" }
        Write-Host $positive.Output.Trim()
    }
}

# Instrument the actual final API classes in a disposable overlay, without changing method bodies.
# Faults bracket real operations, so both before-effect and after-effect state is observable.
function Test-ApiBoundaries {
    $api = Join-Path $fixtureRoot 'api'
    $classes = Join-Path $api 'classes'
    New-Item -ItemType Directory -Path $classes -Force | Out-Null
    $hooks = @'
package dev.vapee.core.quest.daily;
public final class ApiFaults {
    public static String method = "";
    public static boolean after;
    public static int calls;
    public static int failOn = 1;
    public static final RuntimeException fault = new IllegalStateException("controlled external API boundary fault");
    public static void hit(String name, boolean applied) {
        if (method.equals(name) && after == applied && ++calls == failOn) throw fault;
    }
}
'@
    $instrument = @'
import java.nio.file.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
public final class InstrumentApi {
    public static void main(String[] args) throws Exception {
        for (String resource : List.of("org/bukkit/command/PluginCommand", "dev/vapee/core/quest/QuestDefinitionRegistry", "dev/vapee/core/quest/daily/DailyQuestConfig")) {
            byte[] bytes = ClassLoader.getSystemResourceAsStream(resource + ".class").readAllBytes();
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    MethodVisitor delegate = super.visitMethod(access, name, desc, signature, exceptions);
                    if (!Set.of("setExecutor", "setTabCompleter", "replaceAll", "applyState").contains(name)) return delegate;
                    return new MethodVisitor(Opcodes.ASM8, delegate) {
                        void hook(boolean applied) {
                            super.visitLdcInsn(name);
                            super.visitInsn(applied ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/vapee/core/quest/daily/ApiFaults", "hit", "(Ljava/lang/String;Z)V", false);
                        }
                        public void visitCode() { super.visitCode(); hook(false); }
                        public void visitInsn(int op) { if (op == Opcodes.RETURN) hook(true); super.visitInsn(op); }
                    };
                }
            }, 0);
            Path target = Path.of(args[0], resource + ".class");
            Files.createDirectories(target.getParent());
            Files.write(target, writer.toByteArray());
        }
    }
}
'@
    $hookFile = Join-Path $api 'ApiFaults.java'
    $instrumentFile = Join-Path $api 'InstrumentApi.java'
    [IO.File]::WriteAllText($hookFile, $hooks)
    [IO.File]::WriteAllText($instrumentFile, $instrument)
    $output = & $environment.Javac -proc:none --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED -cp $classpath -d $classes $hookFile $instrumentFile 2>&1 | Out-String
    $code = $LASTEXITCODE
    [IO.File]::WriteAllText((Join-Path $receiptDirectory 'api-compile.log'), $output)
    if ($code -ne 0) { throw "API adapter compilation failed: $output" }
    & $environment.Java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED -cp ($classes + [IO.Path]::PathSeparator + $classpath) InstrumentApi $classes
    if ($LASTEXITCODE -ne 0) { throw 'API boundary instrumentation failed.' }
    foreach ($scenario in @('command-api', 'reload-api')) {
        $result = Invoke-Harness ($classes + [IO.Path]::PathSeparator + $classpath) $moduleHarness @($scenario)
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$scenario.log"), $result.Output)
        if ($result.ExitCode -ne 0 -or $result.Output -notmatch 'passed [1-9][0-9]* checks\.') { throw "API boundary case failed: $($result.Output)" }
        Write-Host "$scenario : $($result.Output.Trim())"
    }
}

Push-Location -LiteralPath $projectRoot
try {
    New-Item -ItemType Directory -Path $fixtureRoot | Out-Null
    New-Item -ItemType Directory -Force -Path $receiptDirectory | Out-Null
    Remove-Item -LiteralPath (Join-Path $receiptDirectory 'results.json') -Force -ErrorAction SilentlyContinue
    $classpathFile = Join-Path $projectRoot 'target/daily-quest-lifecycle-classpath.txt'
    $mavenArgs = @('-B', '-ntp')
    if ($Offline) { $mavenArgs += '-o' }
    if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$MavenRepository" }
    & $environment.Maven @mavenArgs test-compile dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
    if ($LASTEXITCODE -ne 0) { throw 'DailyQuest lifecycle compilation/classpath preparation failed.' }
    $classpath = @((Join-Path $projectRoot 'target/test-classes'), (Join-Path $projectRoot 'target/classes'),
        (Get-Content -LiteralPath $classpathFile -Raw).Trim()) -join [IO.Path]::PathSeparator
    Test-Positives 'before'
    $variants = @(
        @{ Id='N1'; File='DailyQuestModule.java'; Old='cleanup(acquired, exception);'; New='if (false) cleanup(acquired, exception);'; Scenario='late'; Expected='all exact handlers released' },
        @{ Id='N2'; File='DailyQuestModule.java'; Old='if (active || enabling || cleaning)'; New='if (enabling || cleaning)'; Scenario='duplicate'; Expected='duplicate enable rejected' },
        @{ Id='N3'; File='DailyQuestModule.java'; Old='if (active || enabling || cleaning)'; New='if (active || cleaning)'; Scenario='reentrant'; Expected='reentrant enable rejected before acquisition' },
        @{ Id='N4'; File='DailyQuestModule.java'; Old='resources.menuListener != null && unregister(resources.menuListener, failure)'; New='resources.menuListener != null && false && unregister(resources.menuListener, failure)'; Scenario='late'; Expected='all exact handlers released' },
        @{ Id='N5'; File='DailyQuestModule.java'; Old='if (resources.command.getExecutor() == resources.executor) resources.command.setExecutor(null);'; New='resources.command.setExecutor(null);'; Scenario='foreign-executor'; Expected='foreign executor preserved' },
        @{ Id='N6'; File='DailyQuestModule.java'; Old='if (resources.command.getTabCompleter() == resources.executor) resources.command.setTabCompleter(null);'; New='resources.command.setTabCompleter(null);'; Scenario='foreign-completer'; Expected='foreign completer preserved' },
        @{ Id='N7'; File='menu/DailyQuestMenu.java'; Old='if (questSubscriptionPending) {'; New='if (questSubscriptionPending && failure.getSuppressed().length == 0) {'; Scenario='observers'; Menu=$true; Expected='second unsubscribe attempted independently' },
        @{ Id='N8'; File='menu/DailyQuestMenu.java'; Old='if (completed) cancelCountdown = null;'; New='if (completed || true) cancelCountdown = null;'; Scenario='countdown'; Menu=$true; Expected='exact countdown cancellation handle retained until confirmation' },
        @{ Id='N9'; File='DailyQuestModule.java'; Old='return active && runtime == resources;'; New='return active;'; Scenario='stale'; Expected='old main cannot synchronize any retired service' },
        @{ Id='N10'; File='menu/DailyQuestMenu.java'; Old='committedChanges = owner -> { if (observes(started)) refreshActive(owner); };'; New='committedChanges = owner -> refreshActive(owner);'; Scenario='stale'; Menu=$true; Expected='retired observer cannot refresh new view' },
        @{ Id='N11'; File='menu/DailyQuestMenu.java'; Old='try { closeOpenInventories(); }'; New='if (failure == null) try { closeOpenInventories(); }'; Scenario='observers'; Menu=$true; Expected='observer error cannot skip owned view cleanup' },
        @{ Id='N12'; File='DailyQuestModule.java'; Old='acquired.restoreCatalog = true;'; New='acquired.restoreCatalog = false;'; Scenario='late'; Expected='late failure restores exact previous definitions' },
        @{ Id='N13'; File='DailyQuestModule.java'; Old='active ? runtime : null'; New='active ? runtime : pendingCleanup'; Scenario='pending'; Expected='getter closed during incomplete shutdown' },
        @{ Id='N14'; File='DailyQuestModule.java'; Old='new DailyQuestListener(acquired.service, () -> owns(acquired))'; New='new DailyQuestListener(acquired.service)'; Scenario='stale'; Expected='all retired entry points inert before player IO' },
        @{ Id='N15'; File='DailyQuestModule.java'; Old='throw exception;'; New='throw new IllegalStateException("masked startup", exception);'; Scenario='late'; Expected='original late logger exception retained' }
    )
    foreach ($variant in $variants) {
        $directory = Join-Path $fixtureRoot $variant.Id
        New-Item -ItemType Directory -Path (Join-Path $directory 'classes') -Force | Out-Null
        $source = (Get-Content -LiteralPath (Join-Path $sourceDirectory $variant.File) -Raw).Replace("`r`n", "`n")
        $mutated = Replace-Once $source $variant.Old $variant.New
        $sourceFile = Join-Path $directory ([IO.Path]::GetFileName($variant.File))
        [IO.File]::WriteAllText($sourceFile, $mutated, [Text.UTF8Encoding]::new($false))
        $compile = & $environment.Javac -proc:none -encoding UTF-8 -cp $classpath -d (Join-Path $directory 'classes') $sourceFile 2>&1 | Out-String
        $compileExit = $LASTEXITCODE
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-compile.log"), $compile)
        if ($compileExit -ne 0) { throw "$($variant.Id) failed compilation; not accepted behavioral evidence.`n$compile" }
        $overlayClasspath = (Join-Path $directory 'classes') + [IO.Path]::PathSeparator + $classpath
        $entry = if ($variant.ContainsKey('Menu')) { $menuHarness } else { $moduleHarness }
        $negative = Invoke-Harness $overlayClasspath $entry @($variant.Scenario)
        [IO.File]::WriteAllText((Join-Path $receiptDirectory "$($variant.Id)-behavior.log"), $negative.Output)
        if ($negative.ExitCode -eq 0 -or -not $negative.Output.Contains('java.lang.AssertionError') -or -not $negative.Output.Contains($variant.Expected)) {
            throw "$($variant.Id) did not fail its intended assertion: $($variant.Expected)`n$($negative.Output)"
        }
        $results.Add([pscustomobject]@{ Id=$variant.Id; File=$variant.File; CompileExit=$compileExit; BehaviorExit=$negative.ExitCode; Scenario=$variant.Scenario; Contract=$variant.Expected })
        Write-Host "$($variant.Id): compiled mutation detected ($($variant.Expected))."
    }
    Test-ApiBoundaries
    Test-Positives 'after'
    [pscustomobject]@{ Detected=$results.Count; Results=$results } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $receiptDirectory 'results.json') -Encoding utf8
    Write-Host "DailyQuest lifecycle negative controls: $($results.Count) detected; positive controls passed before and after."
} finally {
    Pop-Location
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolved = (Resolve-Path -LiteralPath $fixtureRoot).Path
        $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
        if (-not $resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or -not ([IO.Path]::GetFileName($resolved)).StartsWith('vapeecore-daily-negative-')) { throw "Unsafe temporary cleanup path: $resolved" }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
