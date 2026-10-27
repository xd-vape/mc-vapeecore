# Phase 5J-B — Incomplete Shutdown Reporting and Logger-Safe ModuleManager

## Baseline, toolchain and scope

Clean integrated master `15502b31abddedc38f0572a94b1860df32ce0d2c`, branch `phase/shutdown-reporting`,
repository `xd-vape/mc-vapeecore`. Preflight inspected status, branch, HEAD, twelve commits and worktrees.
No applicable AGENTS.md was present. No stash, recovery branch/bundle, sync-conflict evidence or Git
metadata was changed or repaired. No operator server or deployment script was executed.

Baseline `scripts/verify.ps1`: **206 discovered/executed/passed harnesses, 33,180 checks,
Failed=0, Missing/Invalid=0, exit 0; Maven clean package PASS; Spotless PASS**. Production changes
started only after passing baseline and unchanged-class reproductions.

Oracle JDK **21.0.12.1+1-LTS-4**, Maven **3.9.16** from IntelliJ IDEA 2026.2.3, PowerShell **7.6.5**,
Windows 11. Spotless **2.43.0**, Google Java Format **1.22.0/AOSP**, six-file pilot unchanged.
The new report/harness and substantively rewritten manager were formatted individually with the
pinned formatter; Root and unrelated sources were not globally reformatted.

Reviewed README, architecture/developer/lifecycle/persistence/dependency/style contracts, Phase 5H
G1/G2/H/H2/I and its 5J-B proposal, completed 5C/5E/5F/5G/5I/5J-A, current owner implementations and
the remaining CoreModule integration contracts. Current code and executable observations take precedence.

Production scope: ModuleManager, the Root onDisable completion boundary, and one new immutable
module-package result type. CoreModule, Player/Reward/Quest/OnlineReward/DailyQuest/Economy,
repositories/SafeFileWriter/schema, commands/permissions/config, reload participants, task periods,
module registration order and build/deployment infrastructure remain unchanged.

## Unchanged-class observations

External `ModuleManagerBeforeProbe` uses actual ModuleManager and actual allocated VapeeCore Root,
precisely controlled CoreModule callbacks and throwing Logger Handlers. It compiled and executed
against unchanged production classes: **13 measured observations**. Exact sources, compilation and
output receipts are preserved externally. Observations precede any external fixture cleanup.

| Case | Measured original behavior |
|---|---|
| G1 | INFO before B disable throws. Actual order C,A; B is skipped and remains active; caller receives no exception; enabled list empty/flag false; final success message still emitted. |
| G2 | B disable throws and SEVERE reporting throws. Actual order C,B; A remains active; enabled list still A,B,C including retired C, flag true. Caller receives the identical logger exception. |
| H2 | C enable fails; initial startup-failure SEVERE throws. Caller receives logger exception instead of exact original startup error; zero rollback callbacks; A/B remain active and enabled list retains both. |
| Initial startup INFO | Logger error escapes before any module callback. |
| Per-module startup INFO | B enable skipped, A rolled back; logger error becomes the apparent startup failure. |
| Final startup INFO | Caller receives error while all three modules remain active and manager enabled=true. |
| Initial shutdown INFO | Zero disable calls; all three remain enabled/active. |
| Final shutdown INFO | All C,B,A calls complete/list clears, yet caller receives logger failure. |
| Late getName | C disables, then B name getter throws outside the boundary; B/A never receive disable and enabled list remains stale. |
| Root completion | Actual Root emits `VapeeCore disabled.` after B refused disable and remained active. |

These are confirmed behavior, not just a claim that logging can throw. Registration-time getters
remain real validation inputs; they may reject a new registration. Root onEnable logging outside
ModuleManager and unrelated module-private diagnostics remain separate source-level boundaries,
not silently expanded into this correction.

## Stable identity and independent lifecycle attempts

Registration trims/validates a name once and caches it by exact CoreModule identity. Duplicate names
remain case-insensitive; duplicate instance registration is rejected even under a changed name.
Neither startup nor cleanup calls getName again. getModules/getEnabledModules retain their existing
module-instance snapshot API and order. Reentrant enable/disable/register during a transition rejects
before acquisition. No discovery, dependency sorting, alternate service registry or CoreModule change.

Every Manager INFO/WARNING/SEVERE is isolated from module calls. Diagnostic RuntimeExceptions retain
operation, optional exact module/name and original exception in separate immutable diagnostic records.
There is no recursive logging and no global logging disable. Fatal JVM Errors propagate; they are not
treated as recoverable diagnostic failures.

Only normal module enable return adds the instance to enabledModules. On module startup failure,
the exact original RuntimeException stays primary. Every successful predecessor receives rollback in
reverse order; the failed module remains responsible for its own partial startup. Rollback failures
and diagnostic exceptions remain accessible in the report and are attached to startup where safe.
Identity graph traversal prevents self-suppression, duplicates and newly introduced cause cycles.
If an external Throwable has an unreadable cause getter, attachment cannot replace the primary; both
references remain separately observable. Existing suppressed trees are not flattened into strings.

Cleanup snapshots all expected exact instances in reverse order, marks actual call entry and records
normal return or RuntimeException independently. A normal runtime failure cannot skip another callback.
A finally boundary clears manager enabled bookkeeping and publishes the observed snapshot even after
an interrupting Error when publication can still execute. That does not promise recovery from OOM or
other fatal JVM conditions. It does not silently call remaining modules after a fatal Error.

## Actual report model

One public `ShutdownReport` record contains phase, immutable outcome/diagnostic lists and optional
original startup exception context. Nested records preserve module identity/name/status/original
RuntimeException, and diagnostic operation/module/name/original RuntimeException.

| Status | Exact observation |
|---|---|
| RETURNED_NORMALLY | This specific disable call returned normally; internal persistence/resource completion is not independently proven. |
| THREW_EXCEPTION | The call threw the exact saved RuntimeException; it may have thrown before or after resource/file effects. |
| NOT_ATTEMPTED | The expected callback was not entered, e.g. after a fatal interrupt. |
| OUTCOME_UNKNOWN | The callback was entered without observed normal return or ordinary RuntimeException, e.g. a propagating Error. No exception cause is fabricated for this unknown status. |

`allCallsReturnedNormally()` means exactly that. `completedWithoutReportedErrors()` additionally
requires no diagnostic failures and no startup-failure context. Thus successful predecessor cleanup
does not reclassify failed startup as an error-free lifecycle. Normal method return is not a disk
transaction receipt; THREW_EXCEPTION is not proof of persistent data loss or surviving resources.

Lists are copied and immutable. Original CoreModule/Throwable references are intentionally retained;
those Java objects themselves remain mutable. There is no deep copy of exceptions or player data.
The report contains no guessed UUID map, private dirty-set reflection or message/log parser.

Manager getters expose last cleanup, last ordinary shutdown and last startup rollback separately.
Root diagnostic attachment creates another immutable snapshot with unchanged module outcomes; already
published snapshots remain valid. Read-only getters perform no lifecycle callback, persistence, grant,
progress replay, task registration or service replacement.

## Repeat, restart and retained observations

Ordinary complete/incomplete cleanup clears enabledModules and enabled=false. An empty stop after
cleanup is a true no-op: no callback/logging/retry and no erased report. Startup rollback followed by
explicit shutdown does not relabel rollback as a new ordinary attempt. A real empty successful startup
still receives its own coherent empty shutdown result.

`getObservedIncompleteOutcomes()` retains at most the latest incomplete observation per registered
instance until manager lifetime ends. Configured membership and these historical observations are
not a retry queue or live pending-state query. A later normal return does not silently rewrite an
older observed failure. Current clean lifecycle reports still contain only their own results. Exact
registered module objects were already held by the manager; no successful service runtime or profile
aggregate is separately retained by the reporting layer.

**Explicit user clarification:** preserve the existing explicit restart contract and its unchanged
ModuleExtensionHarness. A blanket ban after every RuntimeException would reject its proven after-effect
cleanup case. An explicit caller-requested enableAll may therefore invoke the original owners again;
the original Player/Reward/Quest pending guards still reject unresolved replacement before changing
their own services. Selected real-owner tests confirm original Player identity survives that refusal.
The manager does not claim a general safe hot swap or inspect/override owner-private state. A prior
NOT_ATTEMPTED/OUTCOME_UNKNOWN interruption does reject new acquisition because completion is unknown.
No acknowledgment/automatic reactivation framework is introduced.

There is no automatic retry, dependency revival, early Player disable, new save, reconstruction from
older disk, reward grant, quest replay, dirty clear or future scheduler tick used to make a report green.
Module-local explicit retries require original dependencies to remain usable; metadata retention is
not authority to retry them after dependencies have ended. Private pending memory can be lost at JVM exit.

## Root completion

Only onDisable completion handling changes; onEnable composition and command/reload wiring remain.
Root runs ModuleManager cleanup before its completion diagnostic and distinguishes normal shutdown
returns, diagnostic errors, startup rollback and incomplete/unconfirmed module calls. Incomplete cases
name observed modules and the JVM-exit/no-automatic-retry limitation, without claiming Paper stop veto.
Null manager/no recorded attempt is handled without inventing module outcomes. Root logger failures
are retained privately and, if a report exists, as a new report diagnostic; cleanup outcomes survive.

The unchanged production registration order is:
Localization, Permission, Rank, Player, Social, Economy, Identity, Moderation, Friend, Presence, Clan,
Reward, OnlineReward, Quest, DailyQuest, Lobby, Visibility, Chat, PrivateMessage, Presentation, Settings,
Activity, Utility, Seat, WorldDisplay, Blackjack, Warp, LobbyExperience. Shutdown reverses that complete
list, preserving all intervening owners. All seven reload participants remain config, localization,
lobby, chat, private messages, presentation and daily quest.

## Executable behavioral evidence

`ModuleShutdownReportingHarness` tests actual manager/root objects, controlled exact callback boundaries,
immutable snapshots, fatal interruptions, exception graphs and real owner fixtures. Focused run:
**150 checks**. Unchanged ModuleExtensionHarness remains **11 checks**, including explicit restart.
No previous test entry, discovery rule or behavioral assertion is weakened.

| Required cases | Evidence |
|---|---|
| M1–M2, M19–M22, M26, M32, M35, M39–M40 | normal/registration: exact order/identity, snapshots, normal returns, fresh lifecycle and inert repeat/inspection |
| M3–M7, M11, M31, M36 | diagnostics: initial/per-module/final startup/shutdown INFO, separate diagnostics, final post-effect logger failure and consistent bookkeeping |
| M8–M10, M21, M23, M27, M30, M38 | failures: multiple before/after-effect errors, original trees/identity, diagnostic isolation, immutable historical observations and no automatic retry |
| M12–M15, M18, M37–M38 | rollback/exceptionGraphs: original startup, independent reverse callbacks, failed module not manager-enabled, separate phases, self/duplicate/cycle-safe secondary handling |
| M16–M17 | registration: trimmed stable names, case-insensitive duplicates, changed/throwing getter and duplicate identity |
| M24–M25 | root: truthful normal/incomplete/rollback messages, null manager and throwing completion logger |
| M28–M29, M32 | owners: actual file bytes, save counters, original loaded aggregate/service identity and no reward/progress replay |
| M33–M34 | native exact production order/reload composition and retained existing command/composition harnesses |
| Additional interruption/reentry | interrupted/reentrant: fatal Errors propagate; NOT_ATTEMPTED versus UNKNOWN remains truthful; nested lifecycle calls cannot duplicate acquisition/cleanup |

Actual FilePlayerRepository owner observations before fixture teardown:

| Case | Executable distinction |
|---|---|
| P1 | Reward A save fails, later Player commits A=11/B=22; report keeps Reward THREW and Player RETURNED. Successful fallback cannot rewrite Reward history. |
| P2 | Reward and Player A fail; predecessors still stop; original cache/aggregate A=11 remains over disk 0. Both failure trees survive. Explicit startup respects original Player pending guard. |
| P3 | Quest A final flush fails, later Player commits progress 1; Quest THREW and Player RETURNED remain distinct. No Quest completion/reward replay. |
| P4 | Reward task refuses cancellation; file A=11 is committed but report retains exact resource failure. Physical stale callback is inert; manager never retries it. |
| P5 | Successful saves followed by repeated stop/report reads cause zero additional writes, registrations, service replacement or grants. Explicit owner repair preserves wallet values. |

`scripts/tests/module-shutdown-reporting.Tests.ps1`: **16/16 compiled negative controls detected**.
Every variant compiled with exit 0, executed its specifically intended scenario and failed the named
AssertionError with exit 1. No compilation/setup failure is counted. Production/canonical sources
and classes are untouched by the temporary class overlay. Before and after positives pass:
**12 actual harnesses / 2,003 checks in each round**. Compile/behavior/positive logs and receipts remain.

| Control | Named regression detected |
|---|---|
| N1–N3 | Escaping pre-disable INFO, disable-failure SEVERE, startup-failure diagnostic |
| N4 | Predecessor rollback skipped |
| N5 | One of several module failures discarded |
| N6 | Incomplete/diagnostic result called error-free |
| N7 | Original module exception replaced |
| N8 | Retained failing module identity lost |
| N9 | Late hostile getName skips actual disable |
| N10–N11 | Automatic failed-module retry or repeated successful cleanup |
| N12 | Mutable outcome collection published |
| N13 | Rollback mislabeled ordinary shutdown |
| N14 | Root emits unconditional success after incomplete cleanup |
| N15 | Diagnostic bookkeeping silently suppresses real disable |
| N16 | No-op stop erases previous report |

## Native Paper acceptance

Final JAR ran on fresh loopback-only Paper **1.21.11 build 132**, LuckPerms **5.5.84**, Java 21,
disposable worlds/configs/profiles. JAR SHA-256:
`1be81b90cb000230cc10de797c912d499586e9a511e2194c8f011a16ed77c722`.
Final native runs use this identical build; their hashes agree with the final artifact.

| Accepted composition | First JVM | Independent second JVM | Diagnostics |
|---|---|---|---|
| `native-clean-verified` | 23 checks, exit 0; A=16/B=22 | 26 checks, exit 0; cold A=16 then final A=25/B=22 | Zero console ERROR/SEVERE in both |
| `native-fault` | 27 checks, exit 0; Reward failure followed by Player commit A=11/B=22 | 20 checks, exit 0; cold A=11/B=22, clean report | Exactly two expected console errors in JVM 1; zero in JVM 2 |

**49 clean + 47 fault-composition checks = 96 accepted native checks across four JVMs**. Distinct
runtime tokens and persisted first-process markers establish actual cold processes rather than plugin
disable/enable or hot reload. Two earlier observer setup runs are rejected and not counted: Paper had
closed the dependent helper's classloader before it lazily linked report/domain types. Production
cleanup returned normally, but no success receipt existed. Their sources/logs/profile snapshots remain.
The corrected external runner extracts and preloads observer type references before shutdown; no
production edit or relaxed acceptance assertion was used to fix this test-only setup boundary.

Each accepted JVM verifies **all 28 exact production module identities/order**, **seven reload
participants**, five base tasks, synchronous Reward **20** / Quest **100** tick cadence and one LOWEST
Reward listener. Clean runs use actual loaded synthetic profiles, actual deferred grants, a real Paper
timer executing the unchanged captured production Runnable, unchanged repeat callback, actual Quest
progress and DailyQuest cycle. Pending Reward +5 and Quest progress persist through shutdown without
another reward grant; the cold second JVM reads the prior profile and continues the same contracts.

The helper requests the **actual Paper server stop**. Paper calls the real VapeeCore.onDisable;
an external logger observer captures Root completion inside that method, before Paper-wide safety
teardown. It asserts the full exact reverse outcome list, empty manager enabled list, zero actual
core handlers/tasks, cancelled exact Reward task, original aggregate value and stored profile. It
checks immutable report lists, truthful Root text/severity, no saves/retries on inspection/repeated
disableAll, schema 1 and cold profile identity. The runner also requires the truthful Root line in
the actual console after process exit. No future scheduler tick is needed after shutdown starts.

The deliberately faulted composition injects one real FileAccess IOException before A's Reward write
and uses a test-only Logger on the actual production manager. Initial shutdown INFO, Reward pre-disable
INFO and Reward failure SEVERE each throw independently: **three exact manager diagnostic errors**
are retained in the report. Every earlier module still receives cleanup. Player fallback commits
A=11/B=22, but Reward remains THREW_EXCEPTION with original module/IO identity and its private dirty
bookkeeping. Player remains a separate RETURNED_NORMALLY outcome. Root explicitly reports incomplete
cleanup; reading/repeating the report schedules no retry or extra save. Global server/module logging
is not disabled; the private manager fixture log is retained in `phase05jb-controlled-manager-1.txt`.

Exactly two console ERRORs are expected there: the original repository save failure and truthful Root
incomplete completion. These are not classified as a clean shutdown. Manager diagnostic failures are
separately counted, not substituted for the original persistence failure. JVM 2 has no injected fault
and loads the Player-committed values; it does not claim the prior Reward call succeeded retroactively.

An additional actual ModuleManager composition runs on native Paper's main thread with precisely
controlled predecessor modules: later startup failure, rollback failure, three throwing diagnostics
and a late hostile name getter. It preserves the exact original startup exception, independent
reverse rollback, separate rollback report, failed-module ownership and inert repeated stop.
`phase05jb-startup-rollback.txt` records its order and diagnostic count. This is native coordinator
acceptance with controlled modules, not a claim of inducing failed startup across the full 28-module
production composition. Fatal-interruption and exception-graph corners are covered non-natively.

There is no connected Minecraft client, network login/quit acceptance or visual-client claim. Actors
are direct synthetic profile loads; test reflection changes only Logger/FileAccess boundaries and
observes private native resources. No real PlayerService is replaced, no global plugin re-enable is
used, and no installed operator world/profile/config/JAR is mutable. Sources/runner/preload lists,
compile logs, consoles, raw reports/receipts, configs/profile bytes and binary hashes remain external.
All four newly created server directories, including the two rejected observer setups, were removed
only after confirmed JVM exits, hash-verified profile copies and saved configs/sources/helper JARs.
`native-cleanup.json` records their checked absolute paths and separate acceptance classifications.

## Canonical verification and operator protection

Final `pwsh -File ./scripts/verify.ps1`: **207 discovered/executed/passed harnesses, 33,334 checks,
Failed=0, Missing/Invalid=0, exit 0; Maven clean package PASS; Spotless PASS**. All **206** baseline
entries remain and no existing behavioral count decreases. The increase of **154** comprises the
new harness's **150**, one existing production source/permission audit (885 to 886), one existing
GUI source audit (242 to 243), and two repository links (87 to 89). No permission node or GUI feature
was added by those repository audits. Full inventories, receipts and per-entry comparison are saved.

Focused pinned formatter dry-run checks pass. The full final verifier and compiled positives use
the same production source. Native instrumentation changes remain external; final source/JAR hashes
are checked against the tested build. `git diff --check` passes; no pilot, Maven or discovery changes.

Fresh before inventory measured **934 installed server files** and **nine Run/top-level development-tool
files**, with paths, lengths and SHA-256. Profiles/Coins/Quest/cycles, worlds, LuckPerms, installed JARs,
configs, migration/recovery and sync-conflict evidence are not mutable fixtures. Native worlds/configs/
profiles belong only to disposable external paths; installed binaries are read-only copy sources.

Final full inventories agree exactly: **934/934 operator files**, **9/9 Run/development-tool files**;
**0 added, 0 removed, 0 changed, 0 inaccessible** in either set. Before/after path/length/SHA-256
receipts and `operator-comparison.json` retain complete evidence. Only the new supplemental test
script under `scripts/tests/` is added; existing build/deploy/development and IntelliJ files remain.

Evidence root:
`C:\Users\vapee\.codex\visualizations\2026\10\25\01a17432-7597-7ff1-bc9d-34201a027523\phase5jb-evidence-20261027`.
It retains unchanged-class sources/observations, baseline/final receipts/inventories, formatting evidence,
compiled overlay/positive logs, native sources/runner/consoles/receipts/config/profile snapshots and
full protected inventories/comparisons. Generated evidence and binaries are outside the phase commit.

Exact intended changed paths:

1. `src/main/java/dev/vapee/core/module/ModuleManager.java`.
2. `src/main/java/dev/vapee/core/VapeeCore.java`.
3. `src/main/java/dev/vapee/core/module/ShutdownReport.java`.
4. `src/test/java/dev/vapee/core/module/ModuleShutdownReportingHarness.java`.
5. `scripts/tests/module-shutdown-reporting.Tests.ps1`.
6. `docs/MODULE_DEVELOPMENT.md`.
7. `docs/ARCHITECTURE.md` (new public report/stable-identity contract).
8. This `reports/architecture-modernization/PHASE_05J_B_SHUTDOWN_REPORTING.md`.

## Unresolved knowledge and next phase

CoreModule does not expose private pending UUIDs, actual committed profiles, resource release confirmation
or usable dependency lifetimes. A future optional typed participant snapshot, if needed, must be owned
by each module, preserve exact current UUID/cause/outcome facts without copying aggregates, distinguish
commit/uncertainty/resource obligations and never grant retry authority merely by returning metadata.
That contract is described only; no owner/interface change or UUID parsing is implemented here.

Void-returning legacy owners may still hide internal incomplete work. Exceptions may be after-effect.
Arbitrary module callbacks and root onEnable diagnostics outside this scope retain their own boundaries.
A report is shallow immutable evidence of observed calls, not proof of persistence, a stop veto,
a retry/recovery protocol or a crash-survival guarantee. Fatal interruption may leave calls unknown;
original fatal Errors propagate rather than being treated as ordinary recoverable logger failures.

Recommended next phase: optional **5J-C** bounded same-process coordination only with a separately
approved owner/dependency contract and demonstrated need. It is not started. Exactly one local commit:
`Harden ModuleManager shutdown reporting and diagnostic isolation`. Full SHA/clean status returned
separately; no push, merge, deployment, stash/recovery change or gameplay alteration.
