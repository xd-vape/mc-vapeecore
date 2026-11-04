# Phase 34 – Full Regression and Documentation Audit

## Baseline and scope

Baseline: `a9a3d55d95732463b2a51e4181ce3d72542ee848`.
Branch: `phase/34-full-regression-documentation-audit`.
Initial working tree: clean. Last 20 commits reviewed. One worker in the existing
checkout; no subagents, new branches, worktrees, merge, rebase or push.
Only TEST-001, TEST-002, DOC-001 and this report are changed.

The Phase-33 baseline is 121 executable harnesses / 20,298 checks / zero failures
and zero skipped. Before changes, raw SHA-256 hashes were captured for all 497
tracked files, and all ten active VapeeCore YAML files. Source and wiring from
this baseline were compared with the current tree; architecture counts were
calculated from plugin.yml and VapeeCore.java, not copied from an old report.

Audit inputs: Full Codebase Audit (30A), Phase-31 persistence, Phase-32 security,
Phase-33 reliability reports, README, DEVELOPER_GUIDE, PERMISSIONS, FORMATTING,
pom.xml, all three existing dev-server scripts, ReloadService/Plan/Participant/
Result, CoreCommandHarness, StaffHierarchyConfig, QuestModule, DailyQuestModule,
UtilityModule and bootstrap wiring. No applicable AGENTS.md was present.

## TEST-001 — resolved, coverage only

CoreCommandHarness covers command responses through a ReloadResult supplier;
it does not exercise real coordinator failure execution. The new
`src/test/java/dev/vapee/core/reload/ReloadServiceHarness.java` instead instantiates
production ReloadService with deterministic A/B/C participants and real
ReloadPlan actions. It observes calls, public results and logger records. No
private-field/source-string assertions, server, file corruption, scheduler or
Thread.sleep is needed. No existing coordinator defect was reproduced and no
production fix was necessary.

### Observable contract and executed matrix

Registration order is immutable participant-list order. All prepares complete
before any apply. Preparation creates candidates without applying them; a
prepare exception stops subsequent preparation and returns PREPARE_FAILED.
Already prepared plans receive no rollback because no apply has started; this
is the existing coordinator contract, not an invented cleanup requirement.

On apply failure, the failing plan receives rollback first (including partial
apply cleanup), then successfully applied predecessors in reverse order. Later
prepared but unapplied plans receive neither apply nor rollback. RuntimeException
from one rollback is logged and does not stop remaining rollback attempts.
Original exceptions and participant names remain visible through logging.

| Case | Observed sequence/result and assertions |
|---|---|
| A/B/C success, repeated | prepare A/B/C then apply A/B/C; SUCCESS; prepared=3, applied=3; no rollback; next reload succeeds |
| B prepare fails | prepare A/B only; PREPARE_FAILED/B/1/0; zero applies and zero rollbacks; original prepare cause logged; recovery succeeds |
| B apply fails | prepares A/B/C, applies A/B, rollbacks B/A; APPLY_FAILED/B/3/1; C never applies; original cause logged; recovery succeeds |
| C apply fails | prepares A/B/C, applies A/B/C, rollbacks C/B/A; APPLY_FAILED/C/3/2; recovery succeeds |
| Failed A plan rollback fails | prepare A/B/C, apply A, rollback A only; ROLLBACK_INCOMPLETE/A/3/0; apply and rollback causes separately visible; recovery succeeds |
| C rollback throws | after C apply failure, attempts C/B/A despite C exception; ROLLBACK_INCOMPLETE and original cause/context/advice; recovery succeeds |
| B rollback throws | same complete attempted sequence despite middle failure; ROLLBACK_INCOMPLETE and cause/context/advice; recovery succeeds |
| C and B rollback throw | both causes visible; A still attempted; ROLLBACK_INCOMPLETE; recovery succeeds |
| Reentrant reload during prepare/apply/rollback | two real nested calls per phase return ALREADY_RUNNING with empty component and zero counts; neither starts a second participant chain nor clears the outer running flag; outer result/order preserved; later reload succeeds |

Every result checks status, isSuccess, failed component, prepared/applied counts
and nonnegative duration without timing thresholds. Success and each relevant
failure path are followed by a genuinely successful reload to prove finally-reset.
New harness: **213 checks**.

### Isolated coordinator negative controls

Mutations were compiled from copies of the real production coordinator and
placed before unmodified production/test classes on an isolated classpath.
The real ReloadServiceHarness was then run unchanged.

| Mutation | Expected / actual exit | Detected assertion |
|---|---|---|
| Applied predecessors rolled back forwards | 1 / 1 | observed order differs from C/B/A |
| Return immediately after a predecessor rollback failure | 1 / 1 | remaining rollback attempts missing |
| Keep running=true in finally | 1 / 1 | expected SUCCESS but was ALREADY_RUNNING |

All three produced the intended AssertionError; none failed due to compilation
or classpath errors. Isolated sources/classes were removed. These controls do
not add to Java harness check totals.

## TEST-002 — resolved

Before Phase 34, `mvn clean package` compiled tests but did not execute their
main methods. The old build/deploy script checked Maven and immediately copied
the JAR. Ignored audit scripts required separate manual execution and were not
an authoritative tracked workflow.

`scripts/run-harnesses.ps1` is now the canonical full behavioral runner. It
uses PowerShell 7 and the existing Maven resolution order (MAVEN_HOME, PATH,
IntelliJ bundle), Java/javap from the configured JDK, and optional `-Offline`
and `-MavenRepository`. Committed scripts contain no user-specific absolute paths
or new dependencies. Maven runs test-compile and dependency:build-classpath;
a failed resolver cannot reuse a stale classpath file.

Discovery starts recursively at `src/test/java/**/*Harness.java`, maps each
package path/file name to its top-level class, and asks javap to verify the
compiled public static void main(String[]) or varargs signature, including
valid final/synchronized modifiers. No regex scan of Java comments or strings,
class-name inventory or reflective multi-main JVM is used. Package directories
must correspond to package names, as in the repository. Non-main source
fixtures and nested/generated classes do not become additional tests. Missing
compiled candidates fail discovery. Every executable candidate runs once, sorted
by fully qualified class name, in its own Java process with repository cwd.

All 121 baseline harnesses use the existing `<SimpleName> passed <integer>
checks.` stdout convention. The runner requires exactly one complete result
line and a positive integer fitting a long. Exit zero without a valid count is
MISSING_INVALID, never an invented zero-pass result. Nonzero process exits,
class/launch failures and missing results remain failures while later harnesses
continue. Separate stdout/stderr logs and actual exit/count are retained per
class. Build/classpath and discovery exceptions fail the overall run explicitly.
Start-error classification includes the Java exception type so German launcher
messages are correctly identified. Invocation uses argument arrays and
ProcessStartInfo.ArgumentList; stdout/stderr are drained concurrently.

Console/JSON summaries include Discovered, Executed, Passed, Failed,
Missing/Invalid and TotalChecks. Failed includes missing-result failures;
Missing/Invalid is the explicit subset. Success requires every discovered
harness to pass and execute, with no missing results. There is no skipped
category. Inventory, results.json, Maven log and per-process logs live under
ignored `dev-server/.vapeecore-dev/harnesses/`; only current inventory/results
are authoritative if old individual log files remain.

Build-and-deploy preserves managed stop and the no-auto-restart contract:
clean stop → Maven clean package → mandatory full runner → JAR existence check
→ temporary copy and replacement. There is no skip-regression parameter.
Build/runner failure occurs before any deployment temporary file or JAR write.
The project root is explicitly selected, so invocation from another cwd works.
Maven/Surefire alone still does not execute the main harnesses; docs say so.

### Isolated runner and deployment probes

A tiny temporary Maven project reused the final scripts and dependency
configuration in a directory whose name contains spaces. Both JDK and Maven
executable paths also contain spaces; compiled-output classpath entries were
inside that project. A varargs main and String[] main were discovered once each.
A fixture with a comment, a string containing a fake signature, an int overload,
a nested real main and a non-Harness source main produced no extra tests.

| Probe | Expected / actual exit | Observed result |
|---|---|---|
| Spaces/discovery positive control | 0 / 0 | 2 discovered/executed/passed, 2 checks; fixtures excluded |
| Failing harness | 1 / 1 | PROCESS_FAILED; other harness still passed |
| Exit-zero without result | 1 / 1 | MISSING_INVALID=1; other harness still passed |
| Class removed after discovery by preceding fixture | 1 / 1 | LAUNCH_FAILED/ClassNotFoundException; 2 attempted executions |
| Real build-and-deploy with failing harness | 1 / 1 | Maven BUILD SUCCESS, regression failure, deployment blocked |
| Real build-and-deploy with invalid production source | 1 / 1 | Maven BUILD FAILURE, no runner/deployment started |

Before and after every probe, the isolated previous deployed JAR SHA-256 was
`E83A4C8859765EC09387A62148242A4465C6EB019347CD041F71C39083D242DB`.
No .deploying file or server restart occurred. Synthetic sources, classes,
artifacts and deployment directory were fully removed. These five negative
controls and one positive control are separate from executable-harness totals.
The initial launch-classification probe exposed a locale-dependent label; the
final repeated probe set above uses the corrected exception-type classification.

## DOC-001 — resolved

| Stale current statement | Source truth and exact correction |
|---|---|
| Overview: Java knows no rank names such as Builder | StaffHierarchyConfig.DEFAULT_GROUPS deliberately defines builder/moderator/admin/owner, low to high. Overview now separates data-driven public rank presentation from explicit protected hierarchy defaults and states independence from LP inheritance/public track. |
| Quest/Daily dependency prose omitted MessageService | Constructors and VapeeCore wiring: Quest(JavaPlugin, PlayerModule, RewardModule, MessageService); DailyQuest(JavaPlugin, PlayerModule, QuestModule, MessageService). Both prose lists now name all four dependencies. |
| Utility diagram/overview omitted Rank and staff protection | UtilityModule(JavaPlugin, LobbyModule, ActivityModule, RankModule, MessageService); Rank owns StaffHierarchyService, Utility consumes it via OnlineStaffTargetGuard at command boundaries. Diagram, dependency prose and enable description now say this; UtilityService remains policy-free. |
| Later Utility passage said Builder/Moderator/Admin were entirely unknown; command section said only permissions were checked | Both blanket claims now distinguish ordinary capability/presentation from protected administrative target hierarchy and its deliberate safe defaults. |
| Build section called Maven the full build and maintained an incomplete manual harness list | Replaced with canonical runner link, discovery/individual JVM/output/summary contract, runtime inventory, Java/Maven resolution, PowerShell 7, offline invocation, mandatory deploy gate and separate Paper smoke instructions. No static executable class inventory remains as authority. |
| Current docs versus historical reports unclear | Maintenance rule now explicitly defines docs as durable current guidance and reports as historical phase evidence. |
| README deploy recipe would become stale after adding the gate | Added runner/guide links and accurately described mandatory regression before replacement; no architecture rewriting. |

DEVELOPER_GUIDE was reread against final source for rank/staff, Quest/Daily,
Utility, workflow, Maven behavior, counts and documentation ownership. Existing
phase-specific historical passages and dated counts remain historical. README
architecture counts were already correct. PERMISSIONS and FORMATTING are
byte-identical to baseline. All 19 historical reports are byte-identical.
Active Markdown links pass RepoLinkHarness; the new script links resolve.

## Regression, architecture and protected scope

Focused run: **18 harnesses / 7,525 checks / 0 failures / 0 missing results**.
It includes every explicitly requested class: ReloadService, ModerationCommand,
FriendMenuSecurity, SettingsHotbarSecurity, SettingsMenuSecurity,
VisibilityMenuSecurity, WorldDisplay, Seat, BlackjackPreview,
BlackjackReplacement, WarpReplacement, LobbyPersistence,
PlayerSchemaPersistence, PresentationLifecycle, ScoreboardOwnership,
TablistOwnership, RepoLink and PermissionDescriptor.

Canonical final runner: **122 discovered / 122 executed / 122 passed /
20,512 checks / 0 failures / 0 missing-invalid / 0 skipped**.
The official build-and-deploy runs the same final runner again before copying.
Count reconciliation: 20,298 baseline + 213 ReloadService checks + 1 active
README guide link checked by RepoLinkHarness = **20,512**. All 121 old executable
classes remain present, with identical per-harness counts except RepoLink
22→23. No old assertion or test source was reduced or changed.

Before/after counts calculated from baseline/current descriptor and wiring:
**37 command roots / 50 permissions / 15 positive child edges / 27 modules /
6 reload participants**, unchanged. PermissionDescriptorHarness remains 842
checks. MOD-001, SEC-001, Phase-31 persistence, Phase-33 reliability and Phase-30F
ownership regressions are included in focused and full runs.

Raw protected-file parity: all **331 src/main files**, **136 existing test
files**, **19 historical reports** remain byte-identical. Production parity
covers moderation, GUI security fixes, all persistence, WorldDisplay/Seat,
Warp/Blackjack replacement, presentation, player, friend/clan and visibility,
as well as ReloadService, all resources and bootstrap wiring. pom.xml,
PERMISSIONS, FORMATTING, start/stop scripts and IDE configurations are unchanged.
Per-file before/after SHA-256 receipts are retained in ignored local evidence.

### Protected-group SHA-256 receipts

Digest input: sorted `path:raw-file-SHA256` records joined by LF (UTF-8, no trailing LF). Before and after group digests are identical.

| Group | Files | Before = after digest |
|---|---:|---|
| src/main/ | 331 | `7E0B72E1F71537358CEBD7AEB8841583A1D47671AE1C7173923DE5F48E9FD89C` |
| src/test/ | 136 | `E5028D7BFB3DF6E0141A63515E2A18D767B89A7398B6B455BF8EAF9D1AF81D52` |
| reports/ | 19 | `9228A92582A12EA53C2ACBB8977CCDAB518C1C4BB7031B8ABA3FDED355B5AAC5` |

## Final build, deployment and Paper smoke

The final standalone canonical runner exited 0, followed by the real final
`scripts/build-and-deploy.ps1 -Offline -MavenRepository <existing local cache>`.
The resolver selected the existing IntelliJ IDEA 2026.2.2 Maven bundle and ran
this exact equivalent normal build:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -o '-Dmaven.repo.local=C:\Users\mehdi\.m2\repository' clean package
```

BUILD SUCCESS, exit 0. The subsequent mandatory runner again reported
122/122 passed, 20,512 checks, zero failed/missing; only then did the script
copy the JAR. The full build/deploy process exited 0 and left Paper stopped.

Artifact `target/vapeecore-1.0-SNAPSHOT.jar`: **1,078,672 bytes**.
Built and deployed SHA-256 are identical:
`987EDDD6AB0C9DEC4D710CA0E70EAAF0C8E993218A6F5B3383DB5336F34CF855`.

Real smoke: **2026-10-06, 17:37–17:38 Europe/Berlin**, using the existing managed
start/stop scripts. Actual runtime: Java **21.0.12.1+1-LTS-4**, Paper
**1.21.11-132-ver/1.21.11@c5eb079**, LuckPerms **5.5.84**. The actual deployed
artifact above was loaded. Startup enabled all 27 modules. `core` reported
Running, 27 modules, zero loaded players and connected LuckPerms. `core version`
reported the versions; `core help` rendered the command overview.
`core reload` prepared and applied exactly config.yml, lobby.yml, chat.yml,
private-messages.yml, presentation.yml and daily-quests.yml successfully.
`bukkit:help VapeeCore` returned exactly the 37 descriptor roots. `version`,
`lp info` and `list` additionally confirmed runtime and zero clients.

The managed stop channel sent the regular stop command; all 27 modules disabled
in exact reverse enable order, VapeeCore disabled, worlds saved and the managed
server process exited **0**. The stop script also exited 0. No forced termination
or automatic restart. **0 ERROR/SEVERE** across the complete smoke log. Paper's
existing newer-release advisory produced WARN lines; no plugin warning/error
was present. These advisories do not change the requested fixed server version.

All **10/10 active VapeeCore YAML files** have unchanged raw hashes and identical
file membership across runner, real build/deploy, startup, reload and shutdown.
No live defaults or persistent player/domain files were edited.

**Live client test: not performed.** No GUI, nametag or multiplayer visual
verification is claimed from server-only smoke or harness execution.

### Active YAML before/after hashes

| File under plugins/VapeeCore | Before = after SHA-256 |
|---|---|
| blackjack.yml | `0231E32A0C675EDF0C35B8437473716B8784995C7C06737586C03A0F2A437726` |
| chat.yml | `765C15BF0EE5CEFF416773E182E49D5240587D869129C985B48B7105F293B18F` |
| config.yml | `D13C980946AFCBCE6B530F8EA2FE233B4FAB6A53F94E8C81B60F645250FBDA03` |
| daily-quests.yml | `29A646FAF708113F0B77B3A94759E1E6FFD8B8AB9522154242DABE12C20CE96E` |
| lobby.yml | `58CB38F4C5276F2BA640C21A2D6AF44DF9891AF536D1647A19F5509B26FE3419` |
| presentation.yml | `DCF427C1D6316B822390F1D2749A7A6A7090C206A13E9D44350824CC2484FA2E` |
| private-messages.yml | `1B6BC765776D44872A11625A4C70457A34FE1D4DED70F964F5F37E4277EE6467` |
| warps.yml | `A6DF27819FB3CBC5151F109D43C322E26E533BF01565E7D807B8057A703845A3` |
| players/0d9efbe6-33bf-4cf7-ae67-61d6238b2561.yml | `B133BFDD5875ED00C3B6B18EDDBEE4B1B126336202732037F4E994F0367FA5E1` |
| players/7cf63c54-1791-41fa-9aed-ef91ce90af06.yml | `6DFE9926FADA32EA3B60DADD401A71E78072C5DEC8E0EFEF78021A7D980DF511` |

## Complete canonical process inventory

Each row below is copied from the final canonical runner receipt, not a discovery list maintained in code. All exit codes are zero.

| Fully qualified class | Checks | Result |
|---|---:|---|
| dev.vapee.core.activity.ActivityHarness | 153 | PASS |
| dev.vapee.core.activity.blackjack.BlackjackFairnessHarness | 393 | PASS |
| dev.vapee.core.activity.blackjack.BlackjackHarness | 317 | PASS |
| dev.vapee.core.activity.blackjack.BlackjackPresentationHarness | 132 | PASS |
| dev.vapee.core.activity.blackjack.BlackjackReadViewHarness | 21 | PASS |
| dev.vapee.core.activity.blackjack.presentation.BlackjackPreviewHarness | 283 | PASS |
| dev.vapee.core.activity.blackjack.table.BlackjackReplacementHarness | 98 | PASS |
| dev.vapee.core.activity.blackjack.table.BlackjackTableHarness | 313 | PASS |
| dev.vapee.core.chat.ChatHarness | 17 | PASS |
| dev.vapee.core.chat.config.ChatConfigHarness | 30 | PASS |
| dev.vapee.core.clan.ClanDomainHarness | 52 | PASS |
| dev.vapee.core.clan.ClanIntegrationHarness | 15 | PASS |
| dev.vapee.core.clan.ClanPersistenceHarness | 39 | PASS |
| dev.vapee.core.clan.ClanServiceHarness | 136 | PASS |
| dev.vapee.core.clan.gui.ClanCommandHarness | 99 | PASS |
| dev.vapee.core.clan.gui.ClanMenuHarness | 84 | PASS |
| dev.vapee.core.clan.gui.ClanMenuSecurityHarness | 114 | PASS |
| dev.vapee.core.command.CoreCommandHarness | 75 | PASS |
| dev.vapee.core.command.OnlineStaffTargetGuardHarness | 268 | PASS |
| dev.vapee.core.config.ConfigHelpersHarness | 33 | PASS |
| dev.vapee.core.config.ConfigServiceHarness | 93 | PASS |
| dev.vapee.core.config.DefaultConsistencyHarness | 12 | PASS |
| dev.vapee.core.economy.command.CoinsCommandHarness | 1951 | PASS |
| dev.vapee.core.economy.EconomyIntegrationHarness | 27 | PASS |
| dev.vapee.core.economy.EconomyServiceHarness | 44 | PASS |
| dev.vapee.core.friend.FriendCommandHarness | 96 | PASS |
| dev.vapee.core.friend.FriendDomainHarness | 29 | PASS |
| dev.vapee.core.friend.FriendIntegrationHarness | 28 | PASS |
| dev.vapee.core.friend.FriendLifecycleHarness | 12 | PASS |
| dev.vapee.core.friend.FriendPersistenceHarness | 28 | PASS |
| dev.vapee.core.friend.FriendServiceHarness | 79 | PASS |
| dev.vapee.core.friend.gui.FriendMenuHarness | 159 | PASS |
| dev.vapee.core.friend.gui.FriendMenuSecurityHarness | 156 | PASS |
| dev.vapee.core.identity.IdentityCommandArgumentHarness | 47 | PASS |
| dev.vapee.core.identity.IdentityHarness | 26 | PASS |
| dev.vapee.core.identity.ProfileHarness | 19 | PASS |
| dev.vapee.core.lobby.command.LobbyCommandHarness | 26 | PASS |
| dev.vapee.core.lobby.config.LobbyConfigHarness | 45 | PASS |
| dev.vapee.core.lobby.config.LobbyPersistenceHarness | 112 | PASS |
| dev.vapee.core.lobby.experience.navigator.NavigatorMenuHarness | 113 | PASS |
| dev.vapee.core.lobby.experience.navigator.NavigatorSecurityHarness | 82 | PASS |
| dev.vapee.core.lobby.player.LobbyHarness | 53 | PASS |
| dev.vapee.core.lobby.warp.command.WarpCommandHarness | 86 | PASS |
| dev.vapee.core.lobby.warp.WarpHarness | 118 | PASS |
| dev.vapee.core.lobby.warp.WarpReplacementHarness | 98 | PASS |
| dev.vapee.core.message.CommandHelpHarness | 85 | PASS |
| dev.vapee.core.moderation.command.ModerationCommandHarness | 4414 | PASS |
| dev.vapee.core.moderation.command.ModerationDurationHarness | 53 | PASS |
| dev.vapee.core.moderation.ModerationBanEnforcementHarness | 31 | PASS |
| dev.vapee.core.moderation.ModerationDomainHarness | 112 | PASS |
| dev.vapee.core.moderation.ModerationLifecycleHarness | 149 | PASS |
| dev.vapee.core.moderation.ModerationMuteEnforcementHarness | 52 | PASS |
| dev.vapee.core.moderation.ModerationMuteProjectionHarness | 20 | PASS |
| dev.vapee.core.moderation.ModerationPersistenceHarness | 523 | PASS |
| dev.vapee.core.moderation.ModerationServiceHarness | 141 | PASS |
| dev.vapee.core.onlinereward.OnlineRewardLifecycleHarness | 42 | PASS |
| dev.vapee.core.onlinereward.OnlineRewardServiceHarness | 42 | PASS |
| dev.vapee.core.permission.LuckPermsAsyncHarness | 16 | PASS |
| dev.vapee.core.permission.PermissionDescriptorHarness | 842 | PASS |
| dev.vapee.core.player.repository.PlayerQuestPersistenceHarness | 21 | PASS |
| dev.vapee.core.player.repository.PlayerRewardPersistenceHarness | 11 | PASS |
| dev.vapee.core.player.repository.PlayerSchemaPersistenceHarness | 124 | PASS |
| dev.vapee.core.player.repository.PlayerVisibilityPersistenceHarness | 17 | PASS |
| dev.vapee.core.player.settings.PlayerSettingsServiceHarness | 28 | PASS |
| dev.vapee.core.player.settings.PlayerVisibilitySettingsHarness | 11 | PASS |
| dev.vapee.core.presence.FriendPresenceNotifierHarness | 8 | PASS |
| dev.vapee.core.presence.PresenceLifecycleHarness | 14 | PASS |
| dev.vapee.core.presence.PresenceServiceHarness | 15 | PASS |
| dev.vapee.core.presentation.config.PresentationConfigHarness | 32 | PASS |
| dev.vapee.core.presentation.PlaytimeFormatterHarness | 14 | PASS |
| dev.vapee.core.presentation.PresentationHarness | 10 | PASS |
| dev.vapee.core.presentation.PresentationLifecycleHarness | 38 | PASS |
| dev.vapee.core.presentation.scoreboard.ScoreboardOwnershipHarness | 17 | PASS |
| dev.vapee.core.presentation.tablist.TablistOwnershipHarness | 58 | PASS |
| dev.vapee.core.privatemessage.config.PrivateMessageConfigHarness | 26 | PASS |
| dev.vapee.core.privatemessage.PrivateMessageSocialHarness | 117 | PASS |
| dev.vapee.core.quest.daily.command.QuestCommandHarness | 45 | PASS |
| dev.vapee.core.quest.daily.DailyQuestConfigHarness | 35 | PASS |
| dev.vapee.core.quest.daily.DailyQuestCycleSelectorHarness | 18 | PASS |
| dev.vapee.core.quest.daily.DailyQuestLifecycleHarness | 16 | PASS |
| dev.vapee.core.quest.daily.menu.QuestMenuHarness | 20 | PASS |
| dev.vapee.core.quest.daily.menu.QuestMenuSecurityHarness | 45 | PASS |
| dev.vapee.core.quest.DailyQuestServiceHarness | 20 | PASS |
| dev.vapee.core.quest.QuestDefinitionHarness | 29 | PASS |
| dev.vapee.core.quest.QuestLifecycleHarness | 42 | PASS |
| dev.vapee.core.quest.QuestPlaytimeProducerHarness | 18 | PASS |
| dev.vapee.core.quest.QuestProgressReporterHarness | 18 | PASS |
| dev.vapee.core.quest.QuestServiceHarness | 44 | PASS |
| dev.vapee.core.rank.RankCommandHarness | 16 | PASS |
| dev.vapee.core.rank.RanksCommandHarness | 12 | PASS |
| dev.vapee.core.rank.RankServiceHarness | 23 | PASS |
| dev.vapee.core.rank.staff.StaffHierarchyServiceHarness | 109 | PASS |
| dev.vapee.core.reload.ReloadServiceHarness | 213 | PASS |
| dev.vapee.core.repository.RepoLinkHarness | 23 | PASS |
| dev.vapee.core.reward.RewardLifecycleHarness | 40 | PASS |
| dev.vapee.core.reward.RewardServiceHarness | 126 | PASS |
| dev.vapee.core.seat.SeatHarness | 277 | PASS |
| dev.vapee.core.settings.command.SettingsCommandHarness | 36 | PASS |
| dev.vapee.core.settings.SettingsHotbarSecurityHarness | 12 | PASS |
| dev.vapee.core.settings.SettingsMenuHarness | 66 | PASS |
| dev.vapee.core.settings.SettingsMenuSecurityHarness | 206 | PASS |
| dev.vapee.core.settings.visibility.SettingsModuleLifecycleHarness | 23 | PASS |
| dev.vapee.core.settings.visibility.SettingsNavigationHarness | 17 | PASS |
| dev.vapee.core.settings.visibility.VisibilityMenuSecurityHarness | 327 | PASS |
| dev.vapee.core.settings.visibility.VisibilitySettingsMenuHarness | 29 | PASS |
| dev.vapee.core.settings.visibility.VisiblePlayersMenuHarness | 74 | PASS |
| dev.vapee.core.ui.PaginationHarness | 1409 | PASS |
| dev.vapee.core.ui.UiItemsHarness | 17 | PASS |
| dev.vapee.core.utility.command.BuildCommandHarness | 17 | PASS |
| dev.vapee.core.utility.command.NameSuggestionsHarness | 8 | PASS |
| dev.vapee.core.utility.command.TeleportCommandHarness | 894 | PASS |
| dev.vapee.core.utility.command.UtilityCommandHarness | 1302 | PASS |
| dev.vapee.core.utility.TeleportParserHarness | 58 | PASS |
| dev.vapee.core.utility.UtilityInventoryHarness | 742 | PASS |
| dev.vapee.core.utility.UtilityModuleLifecycleHarness | 452 | PASS |
| dev.vapee.core.utility.UtilityServiceHarness | 35 | PASS |
| dev.vapee.core.visibility.FriendVisibilityRefreshHarness | 11 | PASS |
| dev.vapee.core.visibility.IgnoreVisibilityRefreshHarness | 7 | PASS |
| dev.vapee.core.visibility.VisibilityModuleHarness | 22 | PASS |
| dev.vapee.core.visibility.VisibilityPolicyHarness | 28 | PASS |
| dev.vapee.core.visibility.VisibilityServiceHarness | 10 | PASS |
| dev.vapee.core.worlddisplay.WorldDisplayHarness | 227 | PASS |

## File inventory and diff audit

| Status | File | Scope |
|---|---|---|
| Added | src/test/java/dev/vapee/core/reload/ReloadServiceHarness.java | TEST-001 |
| Added | scripts/run-harnesses.ps1 | TEST-002 |
| Modified | scripts/build-and-deploy.ps1 | TEST-002 |
| Modified | docs/DEVELOPER_GUIDE.md | DOC-001 |
| Modified | README.md | necessary current runner/workflow reference |
| Added | reports/34-full-regression-documentation-audit/FULL_REGRESSION_DOCUMENTATION_AUDIT.md | phase evidence and closure index |

Total: **3 added / 3 modified / 0 removed**. No production edits, old test edits,
POM/dependency changes or historical-report edits. Full diff, name/status, stat
and whitespace checks reviewed before the commit. Only logs/hash/result receipts
from verification remain in ignored evidence; no isolated mutation or synthetic
project is retained.

## Current Full Codebase Audit closure index

The original 30A report is unchanged. This is the current closure index for its
20 counted findings, with current executable evidence rather than retroactive
rewriting of the historical audit.

| Finding | Resolved phase | Current regression evidence | Status |
|---|---|---|---|
| MOD-001 | 30A.1 | ModerationCommandHarness: authorization freshness during resume | resolved |
| SEC-001 | 32 | FriendMenuSecurity, SettingsHotbarSecurity, SettingsMenuSecurity, VisibilityMenuSecurity | resolved |
| DISPLAY-001 | 33 | WorldDisplayHarness and BlackjackPresentationHarness: rejected live teleport ownership | resolved |
| PERSIST-001 | 31 | LobbyPersistenceHarness: protected candidate write and failure paths | resolved |
| PERSIST-002 | 31 | PlayerSchemaPersistenceHarness: version/unknown-field/forward-schema protection | resolved |
| GUI-001 | 30C | UiItemsHarness, PaginationHarness and complete GUI suite | resolved |
| GUI-002 | 30C | Friend/Clan/Settings/Visibility GUI lifecycle and fault-isolated close coverage | resolved |
| LIFE-001 | 30D | UtilityModuleLifecycleHarness: partial enable cleanup and retry | resolved |
| CONFIG-001 | 30D | ConfigHelpersHarness and feature config/lifecycle harnesses | resolved |
| CMD-001 | 30E | UtilityCommandHarness, TeleportCommandHarness, BuildCommandHarness | resolved |
| DUP-001 | 30E | NameSuggestionsHarness and known-identity command regressions | resolved |
| API-001 | 30B | BlackjackReadViewHarness plus Blackjack domain/presentation tests | resolved |
| PRES-001 | 30F | PresentationHarness and PresentationLifecycleHarness: existing ownership/extension boundaries | resolved |
| PRES-002 | 30F | TablistOwnershipHarness and ScoreboardOwnershipHarness | resolved |
| LIFE-002 | 33 | WorldDisplay, Seat, BlackjackPreview, BlackjackTable fault isolation and retry | resolved |
| PERF-001 | 33 | WarpReplacementHarness and BlackjackReplacementHarness: bounded replacement/no deliberate waits | resolved |
| TEST-001 | 34 | Real ReloadServiceHarness, 213 checks and three coordinator mutation controls | resolved |
| TEST-002 | 34 | Canonical 122-process runner plus runner/deploy controls and real successful deploy | resolved |
| DOC-001 | 34 | Source-to-guide audit, runtime inventory and RepoLinkHarness | resolved |
| LEGACY-001 | 30E | Active BlackjackHarness, source parity with cleaned baseline, verified compiled-main discovery | resolved |

**20/20 counted findings resolved. Remaining Full Codebase Audit findings: 0.**
This closes the counted audit work, not every possible future bug or product
feature. Phase 35 (Nametag & Rank Presentation) and Phase 36 (Staff Interaction /
Team Teleport Consent) remain separate future product phases and were not started.

## Git delivery

One local commit is required after all verification with subject
`Phase 34 - Full Regression and Documentation Audit`, directly on the existing
branch above. The final commit SHA, baseline-to-HEAD distance of exactly one
and clean working tree are verified after committing and returned in the final
response and ignored commit receipt. A commit cannot embed its own final SHA
in this report. No push, merge, rebase, branch/worktree creation or force action.
