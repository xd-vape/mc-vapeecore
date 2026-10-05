# Phase 33 – Performance and Reliability

## Baseline and pre-implementation decisions

Branch `phase/33-performance-reliability`; exact baseline
`15e59d26c60500a75dac5380814957ee5ff2fdf6`; initial working tree clean.
Branch, HEAD, status and decorated last 15 commits checked before production edits.
One worker in the existing checkout, no subagents, branch/worktree creation,
push, merge or rebase. Tracked baseline hashes captured in ignored local evidence.
Only DISPLAY-001, LIFE-002 and PERF-001 are in scope.

Current source and its callers are authoritative. Reviewed the 30A findings,
31 persistence contract, 32 security regression contract and Developer Guide,
the affected gateways/services, module teardown, command save entry points and
their existing harness fixtures. No guide or historical report changes are needed.

### Ownership / failure matrix (decided before production changes)

| Resource / owner | Registry, key and external handle | Create / update | Release and bulk policy | Shutdown, reconciliation and protection | Exception / reporting boundary |
|---|---|---|---|---|---|
| WorldDisplay / feature owner | handles; owner/id; typed entity UUID | Spawn then register; typed content update; teleport false currently forgets even live UUID | Release external UUID before forgetting; attempt each key independently; failed handle retained | Targeted remove/removeOwner/cleanup retries; module ultimately discards service after reporting incomplete physical cleanup; existing one-time tagged startup cleanup remains; foreign UUIDs never enumerated by owned cleanup | RuntimeException wrapped with service/key/UUID/operation; aggregate suppressed failures after all attempts; existing module/consumer logger records causes |
| Seat / casual or managed owner | assignmentsBySeat, seatsByPlayer, dismountHandlers; owner/id; optional entity UUID and player UUID | Reserve both indexes; mount records entity; delayed voluntary dismount validates expected key/UUID | External removal before deleting either index or callback; programmatic guard always reset; failed assignment retained; every selected seat attempted | Targeted release retry, then generic module cleanup; module discard is terminal and logged; existing tagged generic/legacy startup cleanup unchanged; exact owned entity/passenger only | RuntimeException context plus bulk aggregate, recorded by existing lifecycle/event boundary |
| Blackjack preview / admin and table | activePreviews; admin UUID; owner string, finite task and marker group | Create markers, schedule 240-tick timeout; replacement first clears previous identity | Independently attempt cancel and marker removal; retain only unfinished parts; failure blocks replacement; shutdown attempts all admins | Retry clear/shutdown; timeout checks exact state identity; completed task/removal not repeated; module discard leaves physical display owner with WorldDisplay for subsequent cleanup; scheduler plugin disable remains existing terminal boundary | Contextual cancel/remove failures aggregated; existing command/event/module logger receives original causes |
| Blackjack seats / table | assignmentsByPlayer, occupantsByTable; player + table/seat; delegated SeatRuntime | Reserve exact/lowest free, then mount via generic SeatService | Release external first, then both adapter indexes; unknown player does not release foreign generic seat; shutdown attempts each assignment | Retain failed adapter assignment while active; explicit retry; generic SeatService remains physical owner and is cleaned later on disable; scoped table owner fallback preserved | Contextual RuntimeException and aggregate; existing activity/table/module error reporting |
| Warp replacement / WarpConfig + WarpService | configFile, sibling temp; service warps map | Validate candidate, serialize and close temp, synchronous replacement, then map swap | Keep atomic -> ordinary move -> copy fallback, but one attempt each and no sleep; failed save does not publish | Temp cleanup remains; no startup rewrite, schema, backups or Phase-31 writer migration; only configured destination/temp touched | IOException with atomic/move suppressed causes; contextual IllegalStateException reaches command logger |
| Blackjack replacement / BlackjackTableConfig + table adapter | configFile, sibling temp; drafts and runtime table/session/index maps | Copy/validate draft, save, then drafts swap; enable/disable adapter retains existing rollback | Same single-attempt fallback policy as Warp; no busy retry or intentional wait | Existing adapter rollback, temp cleanup and load semantics preserved; no durability redesign | Same IOException/IllegalStateException boundary and existing command/table logging |

Bulk exceptions remain unchecked. All selected resources are attempted before the
aggregate is thrown; no failure is converted to a successful count. JVM Errors
are not caught as ordinary resource failures. Retaining a failed live handle is
an in-process retry policy, not a guarantee of recovery after module disposal.

### PERF-001 baseline experiment (before production edits)

An isolated copy of each unmodified production config class replaced only external
Files.move / final Files.copy and Thread.sleep calls with a deterministic probe.
The real WarpService.removeWarp and BlackjackTableConfig.createDraft paths ran
synchronously on the harness caller `main/1`. Each made 1 atomic attempt, 5 normal
move attempts (4 retries), then 1 copy attempt. Requested sleeps were exactly
25, 50, 75 and 100 ms: **250 ms deliberate delay**. Injected complete I/O failure
left runtime state and destination bytes unchanged. The probe recorded requested
waits without spending the delay; this is not a measurement of ordinary server
lag. Source call tracing connects these synchronous methods to Bukkit admin/setup
command execution. Isolated sources/classes were removed; ignored log retained.

## DISPLAY-001 — resolved

The gateway had no existence read. Added only `isAlive(UUID)`; the Bukkit adapter
looks up that exact UUID and requires non-null, valid, non-dead Entity. No scan,
cache, scheduler, movement retry or packet layer is introduced. A rejected
teleport still returns false. If lookup throws, ownership is also retained and
the exception propagates; no replacement is authorized by an uncertain lookup.

| Previous handle / external result | State transition and proven result |
|---|---|
| Unknown key | false; no external operation or implicit spawn |
| Live + teleport true | Same owned handle; successful movement |
| Live + teleport false | false; retain original handle; duplicate key creation refused |
| Missing/dead/invalid + teleport false | Remove stale handle; consumer may recreate once |
| Live rejection, then successful refresh | Original handle updates; no replacement |
| Live rejection, then removeOwner | Original entity is removed; no orphan |
| Several owned displays, one rejected movement | Others still update; no group corruption |

WorldDisplayHarness exercises the state machine, exact UUID lookup in the real
Bukkit gateway (missing/live/dead/invalid), retained handle identity, duplicate
prevention, later success and owner cleanup. BlackjackPresentationHarness executes
real WorldDisplayService and real BlackjackWorldViewService.onEnabled/render/apply
with only external Paper surfaces faked. All display UUIDs remain unchanged after
one rejected move; each movement is attempted once. A disappearance between text
update and teleport recreates exactly one display. Cleanup proves no old entity
survives. The consumer already gates creation on getHandle().isEmpty(), so its
production code, styles, geometry and session rules remain byte-identical.

## LIFE-002 — resolved

Each bulk operation snapshots only its selected keys and catches RuntimeException
per resource. It accumulates contextual failures and throws one unchecked
aggregate after every selected resource has been attempted. Original exceptions
remain causes; multiple failures remain suppressed entries. Individual failures
never produce a success count or success return. Existing module, command,
activity and event boundaries report these exceptions through their existing
loggers; no redundant per-resource log-and-rethrow stacktrace spam is added.

WorldDisplay removes its map entry only after gateway.remove returns. SeatService
keeps both indexes and its managed dismount callback until external removal
succeeds; the programmatic-dismount guard always resets in finally. A later real
voluntary dismount still invokes its retained callback once. Reservations with no
entity remain releasable without an external remove call.

BlackjackSeatService retains failed assignments and table occupancy; it attempts
all selected players during shutdown and table cleanup. A successful scoped
table cleanup still delegates the existing owner fallback. If individual releases
fail, it reports after all selected players without immediately retrying the same
failed resources through that fallback. Later explicit cleanup can retry. The
generic SeatService remains the physical owner. The adapter now supplies the exact
expected SeatKey and player UUID: an unknown adapter assignment cannot release a
foreign generic seat, and retry of an old assignment cannot remove a new seat of
the same player belonging to another feature. This is tested through both real
services and the public production adapter, not merely a copied adapter rule.

BlackjackPreview tracks each admin's exact state identity before creating markers.
Cancel and removeOwner have independent failure boundaries. Successful parts are
marked complete; failed parts remain available to clear/shutdown. Replacement
does not start while previous cleanup fails. Partial create/schedule failures
retain the original cause, attach cleanup failure and keep the marker owner for
retry. A timeout compares the actual state object, so an old callback cannot
remove a new preview even if admin and table produce the same owner string.
Expiration marks the finite task finished and retries only unfinished display
removal on later explicit cleanup. Duration remains 12 seconds / 240 ticks.

### Fault matrix and reconciliation limits

| Group | Injected fault positions | Per-resource evidence | Retry / isolation evidence |
|---|---|---|---|
| WorldDisplay removeOwner and cleanup | None, first, middle, last, first+last, all three | All three attempted; exact failed handles retained; aggregate cause count and owner/key/entity/remove context | Retry touches only failed UUIDs; repeat is inert; foreign entity and excluded owner untouched; all-owner cleanup crosses owner boundaries |
| Seat releaseOwner and cleanup | Same six cases | All three attempted; key and player indexes retain failures; cause count and owner/key/entity/player/release context | Only failed seats retried; repeated cleanup safe; foreign entity and excluded owner untouched; callback/guard recovery separately checked |
| BlackjackSeat cleanupTable and shutdown | Same six cases | All three attempted; failed assignment and occupied number retained; contextual aggregate causes | Failed players retried; second table and foreign generic seat preserved; same-player foreign replacement protected by real adapter |
| Preview shutdown | Six cases for cancel, display removal, and both | Every task and owner attempted even when the other part fails; exact leaf cause count plus service/admin/owner/operation context | Only unfinished parts retried; repeated shutdown inert; foreign production owner preserved |
| Preview other lifecycle | Failed replacement; stale timeout; failed expiration; quit; create/schedule plus remove failure | No duplicate replacement; original failure and cleanup cause both surfaced | Partial owner retained; timeout does not recancel a completed task; later shutdown releases partial markers |

In-process explicit retry remains possible while the service exists. During final
module disposal, incomplete cleanup is logged and the module reference is dropped
as before; retaining a Java map is not described as durable recovery. Blackjack
physical handles remain with generic WorldDisplay/Seat until their later reverse
module cleanup. Paper owns finite plugin tasks and cancels them at plugin disable.
Generic services retain the existing one-time startup cleanup of their tagged
nonpersistent entities. No new shutdown scanner, periodic sweeper or background
retry is added, and no physical cleanup guarantee after an external failure is
claimed. JVM Errors remain outside the ordinary failure boundary.

## PERF-001 — resolved

Each affected config now tries atomic move once, ordinary replacement move once
if needed, and the existing copy fallback once if needed. Both retry constants,
both wait methods and both retry loops are removed. There is **zero deliberate
backoff**, versus 250 ms in the controlled baseline failure experiment. There
is no busy retry, async handoff, new executor, cache, pool or change of mutation
thread. Actual filesystem latency is still synchronous and is not bounded by this
change. No normal server lag or throughput benchmark is claimed.

The two local package-private ReplacementIO boundaries substitute only move/copy
for fault tests; default implementations directly use Files. Serialization,
temporary-file lifecycle, state publication and fallback decisions remain actual
production code. The small seams are intentionally local: no shared persistence
architecture, new schema, migration or backup protocol was introduced.

| Case, tested for both Warp and Blackjack | Result |
|---|---|
| Atomic stage succeeds | One stage, one disk commit, candidate remains unpublished until save returns |
| Atomic unsupported or ordinary atomic IOException; normal move succeeds | Existing broad IOException fallback preserved; one normal move |
| Both moves fail; copy succeeds | One copy commit; candidate then publishes; own temp removed |
| All three fail | Controlled exception retaining copy cause and both suppressed move causes; exact old runtime map retained |
| Failure before external writes | Existing bytes, including operator comment/foreign YAML, remain intact |
| Load existing operator YAML | No eager rewrite |
| Successful save/readback | Existing domain schema and values roundtrip; normal duplicate/no-op behavior retained |
| Blackjack adapter enable/disable save fails | Existing enable rollback removes runtime/session/venue/index; disable rollback restores definition/session/venue/index; config draft remains old |

WarpReplacementHarness and BlackjackReplacementHarness are separate small
replacement tests because the existing broad domain harnesses already cover
loading, commands, navigation, sessions and seat schemas. ReplacementFaultProbe is
test-only external I/O instrumentation. It records operations, caller IDs, commit
count and ThreadMXBean waited counts at actual replacement boundaries. Injected
failed moves do not wait; equality of boundary wait counts proves no sleep between
stages, without a fragile elapsed-time threshold. Restoring the actual old loop
and Thread.sleep method in isolated copies makes each wait assertion fail. Atomic
success is a controlled gateway success backed by a real ordinary file move;
existing domain harnesses also execute the default Files-based implementation.

Persistence limitations are unchanged: the final copy is not atomic and may
partially modify a destination before throwing in a real filesystem failure.
Tests asserting unchanged bytes inject failure before writes; they do not claim
new crash durability or rollback of arbitrary I/O damage. Existing serializers'
normal-save treatment of foreign YAML fields/comments is unchanged. Phase-31
SafeFileWriter, player schema/recovery and lobby safe persistence are untouched.

## Verification results

Focused run: **34 executable harnesses, 9,687 checks, 0 failures, 0 skipped**.
Full run: **121 executable harnesses, 20,298 checks, 0 failures, 0 skipped**.
All 119 baseline harnesses remain, with no reduced check count. Added checks: 1,120.
Every active public static void main in a *Harness.java ran in its own Java process.
No full-test runner, Maven integration, CI or dependency changes are committed.

### Per-harness count changes

| Harness | Baseline checks | Final checks | Added |
|---|---:|---:|---:|
| activity.blackjack.BlackjackPresentationHarness | 123 | 132 | 9 |
| activity.blackjack.presentation.BlackjackPreviewHarness | 16 | 283 | 267 |
| activity.blackjack.table.BlackjackReplacementHarness | 0 | 98 | 98 |
| activity.blackjack.table.BlackjackTableHarness | 90 | 313 | 223 |
| lobby.warp.WarpReplacementHarness | 0 | 98 | 98 |
| seat.SeatHarness | 52 | 277 | 225 |
| worlddisplay.WorldDisplayHarness | 27 | 227 | 200 |

19,178 baseline + 1,120 added = **20,298**. PermissionDescriptor stays 842;
ModerationCommand stays 4,414. All named Phase-32 security, Phase-31 persistence,
MOD-001 and Phase-30F ownership/lifecycle regressions pass unchanged. Focused
membership and all per-process final counts appear in the complete inventory.

## Isolated negative controls

All controls compiled independently, loaded only the altered production class
ahead of normal classes, and failed with the specified AssertionError and exit 1.
They are excluded from normal harness/check totals. The isolated sources/classes
were fully removed; final production/test classes were never overwritten.
The sleep controls restore the actual baseline retry loop and wait method, with
the same narrow I/O substitution; the waited-count assertion detects the sleeps.
An initial ownership mutation also touched a typed lookup and failed too early;
it was narrowed to remove only and rerun to obtain the intended ownership proof.

| Mutation | Detecting assertion | Exit |
|---|---|---:|
| display-forget | rejected live teleport retains original ownership | 1 |
| blackjack-display-forget | Blackjack apply retains live rejected original handle | 1 |
| display-abort | display cleanup attempts every owned resource despite failure | 1 |
| seat-abort | seat cleanup attempts every owned resource despite failure | 1 |
| preview-abort | preview shutdown attempts every task and owner despite failure | 1 |
| blackjack-seat-abort | Blackjack seat cleanup attempts every owned resource despite failure | 1 |
| display-lost-owner | failed display retains owned handle | 1 |
| seat-lost-owner | failed seat retains key ownership | 1 |
| preview-lost-owner | preview retry cancels only unfinished tasks | 1 |
| blackjack-seat-lost-owner | failed Blackjack seat retains assignment | 1 |
| warp-sleeps | Warp replacement performs zero deliberate waits | 1 |
| blackjack-sleeps | Blackjack replacement performs zero deliberate waits | 1 |

## Descriptor, architecture and scope

Unchanged **37 root commands, 50 permissions, 15 positive child edges,
27 modules, 6 reload participants**. Descriptor parsing and the unchanged
PermissionDescriptorHarness verify commands/permissions; real Paper confirms
module order, root registrations and reload. Reload remains config.yml, lobby.yml,
chat.yml, private-messages.yml, presentation.yml and daily-quests.yml. No new
permission, command, module, config file, default, schema or reload participation.

## Final build and artifact

After complete regression, the requested command was executed exactly:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -o '-Dmaven.repo.local=C:\Users\mehdi\.m2\repository' clean package
```

**BUILD SUCCESS, exit 0**, 15.224 seconds, completed 2026-10-05 21:28:53
Europe/Berlin. 322 production and 136 test/support sources compiled. Maven's
normal build still does not run the executable main harnesses; the preceding
explicit per-process run supplies that evidence (TEST-002 remains open).
Compiler notices concern annotation processing, existing deprecated/Unsafe APIs
and the preview test's PlayerQuitEvent constructor; no compiler error.

Artifact: `target/vapeecore-1.0-SNAPSHOT.jar`, **1078672 bytes**.
SHA-256: `7CF0EFAE14F7A1702A5BC38BD2FB5899D1886EAA76433D0BBB67D48826A6B38E`.
The deployed dev-server/plugins/vapeecore-1.0-SNAPSHOT.jar was independently
hashed and is identical. No JAR, log or ignored local evidence is committed.

## Real Paper smoke

2026-10-05 21:30:45–21:31:41 Europe/Berlin. Existing Paper **1.21.11 build 132 /
c5eb079**, Oracle Java **21.0.12.1+1-LTS-4**, LuckPerms **5.5.84**.
Reached Done; 27 modules enabled. Console version, lp info, list, core,
core version, core help, core reload and bukkit:help VapeeCore all executed.
Core reported Running, 27 active modules, LuckPerms Connected and zero loaded
players. Reload prepared exactly six participants and completed in 16 ms.
Bukkit help's 37 roots exactly match plugin.yml. Stop disabled all 27 modules
in reverse enable order, saved zero loaded players, completed shutdown and
exited 0. **ERROR/SEVERE: 0**. Existing Paper newer-Minecraft-release notice and
spark's Windows Java-engine fallback are not plugin errors.

An earlier startup accidentally used the previous deployed JAR after a denied
local CIM process read interrupted the copy step. That run was stopped cleanly
and excluded from Phase-33 smoke acceptance. Its YAML hashes were unchanged.
The accepted run above started only after successful deployment/hash comparison.

All **10 active VapeeCore YAMLs** (eight configs and two players) retain exact
before/after bytes; file-set parity also checked. No Warp/Blackjack/Player/Lobby
mutation was issued. The table paths are relative to dev-server/plugins/VapeeCore/.

**Live client test: not performed.** No visual or multiplayer verification is
claimed. Entity/task/I/O fault scenarios are deterministic service harnesses;
the real server smoke verifies boot, registration, reload and shutdown.

| Active YAML | SHA-256 before | SHA-256 after |
|---|---|---|
| blackjack.yml | `0231E32A0C675EDF0C35B8437473716B8784995C7C06737586C03A0F2A437726` | `0231E32A0C675EDF0C35B8437473716B8784995C7C06737586C03A0F2A437726` |
| chat.yml | `765C15BF0EE5CEFF416773E182E49D5240587D869129C985B48B7105F293B18F` | `765C15BF0EE5CEFF416773E182E49D5240587D869129C985B48B7105F293B18F` |
| config.yml | `D13C980946AFCBCE6B530F8EA2FE233B4FAB6A53F94E8C81B60F645250FBDA03` | `D13C980946AFCBCE6B530F8EA2FE233B4FAB6A53F94E8C81B60F645250FBDA03` |
| daily-quests.yml | `29A646FAF708113F0B77B3A94759E1E6FFD8B8AB9522154242DABE12C20CE96E` | `29A646FAF708113F0B77B3A94759E1E6FFD8B8AB9522154242DABE12C20CE96E` |
| lobby.yml | `58CB38F4C5276F2BA640C21A2D6AF44DF9891AF536D1647A19F5509B26FE3419` | `58CB38F4C5276F2BA640C21A2D6AF44DF9891AF536D1647A19F5509B26FE3419` |
| presentation.yml | `DCF427C1D6316B822390F1D2749A7A6A7090C206A13E9D44350824CC2484FA2E` | `DCF427C1D6316B822390F1D2749A7A6A7090C206A13E9D44350824CC2484FA2E` |
| private-messages.yml | `1B6BC765776D44872A11625A4C70457A34FE1D4DED70F964F5F37E4277EE6467` | `1B6BC765776D44872A11625A4C70457A34FE1D4DED70F964F5F37E4277EE6467` |
| warps.yml | `A6DF27819FB3CBC5151F109D43C322E26E533BF01565E7D807B8057A703845A3` | `A6DF27819FB3CBC5151F109D43C322E26E533BF01565E7D807B8057A703845A3` |
| players/0d9efbe6-33bf-4cf7-ae67-61d6238b2561.yml | `B133BFDD5875ED00C3B6B18EDDBEE4B1B126336202732037F4E994F0367FA5E1` | `B133BFDD5875ED00C3B6B18EDDBEE4B1B126336202732037F4E994F0367FA5E1` |
| players/7cf63c54-1791-41fa-9aed-ef91ce90af06.yml | `6DFE9926FADA32EA3B60DADD401A71E78072C5DEC8E0EFEF78021A7D980DF511` | `6DFE9926FADA32EA3B60DADD401A71E78072C5DEC8E0EFEF78021A7D980DF511` |

## Changed-file inventory and diff audit

**4 files added, 12 modified, 0 removed.** Six production files modified; six
existing test/support files extended; two focused replacement harnesses and one
test-only I/O probe added; this report is the sole documentation addition.
Status, diff --check, diff --stat, diff --name-status and the complete source diff
were reviewed. Every change belongs to DISPLAY-001, LIFE-002, PERF-001, their
tests or this report. No IDE files, deployment files, JARs or ignored evidence.

| Status | File | Category |
|---|---|---|
| M | src/main/java/dev/vapee/core/worlddisplay/WorldDisplayService.java | DISPLAY-001 + LIFE-002 |
| M | src/main/java/dev/vapee/core/seat/SeatService.java | LIFE-002 |
| M | src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackPreviewService.java | LIFE-002 |
| M | src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackSeatService.java | LIFE-002 |
| M | src/main/java/dev/vapee/core/lobby/warp/WarpConfig.java | PERF-001 |
| M | src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableConfig.java | PERF-001 |
| M | src/test/java/dev/vapee/core/worlddisplay/WorldDisplayHarness.java | Tests: display, cleanup, real Bukkit lookup |
| M | src/test/java/dev/vapee/core/seat/SeatHarness.java | Tests: cleanup, callbacks, real Blackjack/generic adapter |
| M | src/test/java/dev/vapee/core/activity/blackjack/BlackjackPresentationFixture.java | Tests: external entity movement fault seam |
| M | src/test/java/dev/vapee/core/activity/blackjack/BlackjackPresentationHarness.java | Tests: real WorldView apply integration |
| M | src/test/java/dev/vapee/core/activity/blackjack/presentation/BlackjackPreviewHarness.java | Tests: cancel/remove/creation/timeout fault matrix |
| M | src/test/java/dev/vapee/core/activity/blackjack/table/BlackjackTableHarness.java | Tests: seat cleanup and persistence adapter rollback |
| A | src/test/java/dev/vapee/core/lobby/warp/WarpReplacementHarness.java | Tests: real replacement, wait and publication boundary |
| A | src/test/java/dev/vapee/core/activity/blackjack/table/BlackjackReplacementHarness.java | Tests: real replacement, wait and publication boundary |
| A | src/test/java/dev/vapee/core/config/ReplacementFaultProbe.java | Tests: narrow external I/O instrumentation |
| A | reports/33-performance-reliability/PERFORMANCE_RELIABILITY.md | Phase-33 report |

## Protected-file verification

All 493 baseline tracked files were hashed individually before and after work:
**481 byte-identical, exactly 12 intended modifications**. The protected groups
below contain **127 files**, every one byte-identical. This includes all 18
historical reports, all three durable guides, README, pom.xml, every resource
YAML and all explicitly protected production packages. Other baseline files,
including module wiring, WorldView, WarpService, BlackjackTableService and old
MOD/31/32/30F regression sources, are also checked in the complete local manifest.

Group digest algorithm: SHA-256 over UTF-8, path-sorted `path + space + file-hash`
records joined by LF, no trailing LF. Both baseline and final group digests were
computed independently from the per-file hashes and compared.

| Protected group | Files | SHA-256 baseline = final |
|---|---:|---|
| Historical reports | 18 | `171E3E22FC0035E64EBEC2C0C22B8C5E150B46CCCB52EFED08451DD8C61ED620` |
| Durable guides | 3 | `7D5DE592BD7C6F9D24A5CF86A1F4A1FC54AB24ECACE0224439FB38D3071F24A9` |
| README and Maven | 2 | `BAC2B5048465B6DAE1C9245F12ACD8E8C2B5197BB50DE3C0F44616039E1155CD` |
| Resource YAMLs | 9 | `9DD0A3CA4802BD5D17D0066A36C9C480831F4AC81014588CD299362694A8D46B` |
| Moderation | 29 | `94D1E9DCDD30D971C1BE2E110180BECA32C1BECB3C8A515300434ECDF67768EF` |
| Rank | 8 | `753BE73489F11EC296E052E0B8E498F57F37806D7A003482C03E8C2D2A4CDDC5` |
| Friend | 22 | `ECB638ED543A549CF0E9E44509BE704153D727BCC728908C006221747C637CB2` |
| Settings | 11 | `BC0A193B6AD09F62E3E7389A080E05D143E1F27BDC307F58CF89EE46A4B665A3` |
| Visibility | 3 | `38B6BBEA72E41AEE927623ED31F9E9E3DB4FAD5597E63061871085AD2E3F5E52` |
| Presentation | 8 | `AE9D71BD0DC422C179DD275183138E8EFFABD881D5ABCA7FACA65DA538DF68E6` |
| Player | 12 | `9477B219CE72A64AA2893ED9CF2F74A5EDAE966BD2FA8F915915F692E0080E76` |
| Persistence | 1 | `0940B64510B81B3A00AA4E8F2807990CB93ECE5F1D30E429DC3EED3A7CC4F5D0` |
| Lobby safe config | 1 | `6AB76CE915EFCF75DA6FD50636D25440FF3BF2574094720E6E3141B6B19997EE` |

## Complete per-process regression inventory

Each row exited 0; none skipped. Focused members also passed separately with the
same count. Prefix dev.vapee.core. omitted for readability. Every baseline class
is present, and every baseline count is retained or increased.

| Harness | Baseline | Final | Delta | Focused |
|---|---:|---:|---:|---|
| activity.ActivityHarness | 153 | 153 | 0 | yes |
| activity.blackjack.BlackjackFairnessHarness | 393 | 393 | 0 | yes |
| activity.blackjack.BlackjackHarness | 317 | 317 | 0 | yes |
| activity.blackjack.BlackjackPresentationHarness | 123 | 132 | 9 | yes |
| activity.blackjack.BlackjackReadViewHarness | 21 | 21 | 0 | yes |
| activity.blackjack.presentation.BlackjackPreviewHarness | 16 | 283 | 267 | yes |
| activity.blackjack.table.BlackjackReplacementHarness | 0 | 98 | 98 | yes |
| activity.blackjack.table.BlackjackTableHarness | 90 | 313 | 223 | yes |
| chat.ChatHarness | 17 | 17 | 0 |  |
| chat.config.ChatConfigHarness | 30 | 30 | 0 |  |
| clan.ClanDomainHarness | 52 | 52 | 0 |  |
| clan.ClanIntegrationHarness | 15 | 15 | 0 |  |
| clan.ClanPersistenceHarness | 39 | 39 | 0 | yes |
| clan.ClanServiceHarness | 136 | 136 | 0 |  |
| clan.gui.ClanCommandHarness | 99 | 99 | 0 |  |
| clan.gui.ClanMenuHarness | 84 | 84 | 0 |  |
| clan.gui.ClanMenuSecurityHarness | 114 | 114 | 0 | yes |
| command.CoreCommandHarness | 75 | 75 | 0 |  |
| command.OnlineStaffTargetGuardHarness | 268 | 268 | 0 |  |
| config.ConfigHelpersHarness | 33 | 33 | 0 |  |
| config.ConfigServiceHarness | 93 | 93 | 0 |  |
| config.DefaultConsistencyHarness | 12 | 12 | 0 |  |
| economy.command.CoinsCommandHarness | 1951 | 1951 | 0 |  |
| economy.EconomyIntegrationHarness | 27 | 27 | 0 |  |
| economy.EconomyServiceHarness | 44 | 44 | 0 |  |
| friend.FriendCommandHarness | 96 | 96 | 0 |  |
| friend.FriendDomainHarness | 29 | 29 | 0 |  |
| friend.FriendIntegrationHarness | 28 | 28 | 0 |  |
| friend.FriendLifecycleHarness | 12 | 12 | 0 |  |
| friend.FriendPersistenceHarness | 28 | 28 | 0 | yes |
| friend.FriendServiceHarness | 79 | 79 | 0 |  |
| friend.gui.FriendMenuHarness | 159 | 159 | 0 |  |
| friend.gui.FriendMenuSecurityHarness | 156 | 156 | 0 | yes |
| identity.IdentityCommandArgumentHarness | 47 | 47 | 0 |  |
| identity.IdentityHarness | 26 | 26 | 0 |  |
| identity.ProfileHarness | 19 | 19 | 0 |  |
| lobby.command.LobbyCommandHarness | 26 | 26 | 0 |  |
| lobby.config.LobbyConfigHarness | 45 | 45 | 0 | yes |
| lobby.config.LobbyPersistenceHarness | 112 | 112 | 0 | yes |
| lobby.experience.navigator.NavigatorMenuHarness | 113 | 113 | 0 |  |
| lobby.experience.navigator.NavigatorSecurityHarness | 82 | 82 | 0 |  |
| lobby.player.LobbyHarness | 53 | 53 | 0 |  |
| lobby.warp.command.WarpCommandHarness | 86 | 86 | 0 | yes |
| lobby.warp.WarpHarness | 118 | 118 | 0 | yes |
| lobby.warp.WarpReplacementHarness | 0 | 98 | 98 | yes |
| message.CommandHelpHarness | 85 | 85 | 0 |  |
| moderation.command.ModerationCommandHarness | 4414 | 4414 | 0 | yes |
| moderation.command.ModerationDurationHarness | 53 | 53 | 0 |  |
| moderation.ModerationBanEnforcementHarness | 31 | 31 | 0 |  |
| moderation.ModerationDomainHarness | 112 | 112 | 0 |  |
| moderation.ModerationLifecycleHarness | 149 | 149 | 0 |  |
| moderation.ModerationMuteEnforcementHarness | 52 | 52 | 0 |  |
| moderation.ModerationMuteProjectionHarness | 20 | 20 | 0 |  |
| moderation.ModerationPersistenceHarness | 523 | 523 | 0 | yes |
| moderation.ModerationServiceHarness | 141 | 141 | 0 |  |
| onlinereward.OnlineRewardLifecycleHarness | 42 | 42 | 0 |  |
| onlinereward.OnlineRewardServiceHarness | 42 | 42 | 0 |  |
| permission.LuckPermsAsyncHarness | 16 | 16 | 0 |  |
| permission.PermissionDescriptorHarness | 842 | 842 | 0 | yes |
| player.repository.PlayerQuestPersistenceHarness | 21 | 21 | 0 | yes |
| player.repository.PlayerRewardPersistenceHarness | 11 | 11 | 0 | yes |
| player.repository.PlayerSchemaPersistenceHarness | 124 | 124 | 0 | yes |
| player.repository.PlayerVisibilityPersistenceHarness | 17 | 17 | 0 | yes |
| player.settings.PlayerSettingsServiceHarness | 28 | 28 | 0 |  |
| player.settings.PlayerVisibilitySettingsHarness | 11 | 11 | 0 |  |
| presence.FriendPresenceNotifierHarness | 8 | 8 | 0 |  |
| presence.PresenceLifecycleHarness | 14 | 14 | 0 |  |
| presence.PresenceServiceHarness | 15 | 15 | 0 |  |
| presentation.config.PresentationConfigHarness | 32 | 32 | 0 |  |
| presentation.PlaytimeFormatterHarness | 14 | 14 | 0 |  |
| presentation.PresentationHarness | 10 | 10 | 0 |  |
| presentation.PresentationLifecycleHarness | 38 | 38 | 0 | yes |
| presentation.scoreboard.ScoreboardOwnershipHarness | 17 | 17 | 0 | yes |
| presentation.tablist.TablistOwnershipHarness | 58 | 58 | 0 | yes |
| privatemessage.config.PrivateMessageConfigHarness | 26 | 26 | 0 |  |
| privatemessage.PrivateMessageSocialHarness | 117 | 117 | 0 |  |
| quest.daily.command.QuestCommandHarness | 45 | 45 | 0 |  |
| quest.daily.DailyQuestConfigHarness | 35 | 35 | 0 |  |
| quest.daily.DailyQuestCycleSelectorHarness | 18 | 18 | 0 |  |
| quest.daily.DailyQuestLifecycleHarness | 16 | 16 | 0 |  |
| quest.daily.menu.QuestMenuHarness | 20 | 20 | 0 |  |
| quest.daily.menu.QuestMenuSecurityHarness | 45 | 45 | 0 | yes |
| quest.DailyQuestServiceHarness | 20 | 20 | 0 |  |
| quest.QuestDefinitionHarness | 29 | 29 | 0 |  |
| quest.QuestLifecycleHarness | 42 | 42 | 0 |  |
| quest.QuestPlaytimeProducerHarness | 18 | 18 | 0 |  |
| quest.QuestProgressReporterHarness | 18 | 18 | 0 |  |
| quest.QuestServiceHarness | 44 | 44 | 0 |  |
| rank.RankCommandHarness | 16 | 16 | 0 |  |
| rank.RanksCommandHarness | 12 | 12 | 0 |  |
| rank.RankServiceHarness | 23 | 23 | 0 |  |
| rank.staff.StaffHierarchyServiceHarness | 109 | 109 | 0 |  |
| repository.RepoLinkHarness | 22 | 22 | 0 | yes |
| reward.RewardLifecycleHarness | 40 | 40 | 0 |  |
| reward.RewardServiceHarness | 126 | 126 | 0 |  |
| seat.SeatHarness | 52 | 277 | 225 | yes |
| settings.command.SettingsCommandHarness | 36 | 36 | 0 |  |
| settings.SettingsHotbarSecurityHarness | 12 | 12 | 0 | yes |
| settings.SettingsMenuHarness | 66 | 66 | 0 |  |
| settings.SettingsMenuSecurityHarness | 206 | 206 | 0 | yes |
| settings.visibility.SettingsModuleLifecycleHarness | 23 | 23 | 0 |  |
| settings.visibility.SettingsNavigationHarness | 17 | 17 | 0 |  |
| settings.visibility.VisibilityMenuSecurityHarness | 327 | 327 | 0 | yes |
| settings.visibility.VisibilitySettingsMenuHarness | 29 | 29 | 0 |  |
| settings.visibility.VisiblePlayersMenuHarness | 74 | 74 | 0 |  |
| ui.PaginationHarness | 1409 | 1409 | 0 |  |
| ui.UiItemsHarness | 17 | 17 | 0 |  |
| utility.command.BuildCommandHarness | 17 | 17 | 0 |  |
| utility.command.NameSuggestionsHarness | 8 | 8 | 0 |  |
| utility.command.TeleportCommandHarness | 894 | 894 | 0 |  |
| utility.command.UtilityCommandHarness | 1302 | 1302 | 0 |  |
| utility.TeleportParserHarness | 58 | 58 | 0 |  |
| utility.UtilityInventoryHarness | 742 | 742 | 0 |  |
| utility.UtilityModuleLifecycleHarness | 452 | 452 | 0 |  |
| utility.UtilityServiceHarness | 35 | 35 | 0 |  |
| visibility.FriendVisibilityRefreshHarness | 11 | 11 | 0 |  |
| visibility.IgnoreVisibilityRefreshHarness | 7 | 7 | 0 |  |
| visibility.VisibilityModuleHarness | 22 | 22 | 0 |  |
| visibility.VisibilityPolicyHarness | 28 | 28 | 0 |  |
| visibility.VisibilityServiceHarness | 10 | 10 | 0 |  |
| worlddisplay.WorldDisplayHarness | 27 | 227 | 200 | yes |

## Acceptance, remaining findings and Git delivery

**DISPLAY-001 resolved:** live rejected movement retains ownership, never permits
duplicate recreation, and subsequent refresh/cleanup operates on the original.
Actually missing/dead/invalid entities reconcile through the minimal targeted
gateway read. Real Blackjack apply exercises the safe behavior.

**LIFE-002 resolved:** all four owner groups attempt every selected resource
despite per-resource external failures, retain unfinished ownership while active,
report contextual original causes, preserve foreign resources and have tested
repeat/retry policies. Final module disposal and existing reconciliation limits
are explicit; there is no new periodic scanner.

**PERF-001 resolved:** both synchronous save paths have no deliberate retry sleep,
no busy loop, and deterministic fault/wait coverage. Save-before-publication and
Blackjack adapter rollback remain; schema/durability and threading are unchanged.

Exactly **3 audit findings remain: TEST-001, TEST-002, DOC-001**. Phase 34 is not
started. SEC-001, MOD-001, Phase-31 persistence and Phase-30F presentation remain
protected and green.

Delivery is exactly one local commit, **Phase 33 - Performance and Reliability**,
on `phase/33-performance-reliability`, with
`15e59d26c60500a75dac5380814957ee5ff2fdf6` as parent. Implementation, focused run,
negative controls, full regression, diff audit, exact build, accepted Paper smoke
and this completed report precede the commit. Afterwards HEAD, subject, parent,
one-commit baseline distance and clean working tree are verified. The containing
commit cannot embed its own SHA; the final response and ignored
phase33-commit-receipt.json provide that post-commit identity and status.
No push, merge, rebase, force push, branch or worktree creation is performed.

Ignored local evidence under dev-server/.vapeecore-dev/ includes baseline/final
hash manifests, baseline performance proof, per-process focused/full logs and
counts, regression deltas, all 12 negative-control results, reviewed/staged diff,
build completion output, artifact hash, accepted Paper log/summary, live before/
after hashes and post-commit receipt. None is a committed runner or dependency.
