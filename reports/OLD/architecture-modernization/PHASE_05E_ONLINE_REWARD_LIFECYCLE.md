# Phase 5E — OnlineReward module lifecycle and task ownership

## Baseline and scope

Baseline integrated master: `7d6ac5e04bcae03349ea6c44b0bcadb169684fc0`.
Local branch: `phase/online-reward-lifecycle`. Oracle JDK **21.0.12.1+1-LTS-4**, Apache Maven
**3.9.16** bundled with IntelliJ IDEA 2026.2.3, PowerShell **7.6.5**, Windows 11 amd64.

The first preflight stopped on local IntelliJ changes. A later user Git log revealed sync-conflict
files in both sources and Git refs and a local commit on the older Phase-5A history. With explicit
user approval, both histories were preserved as recovery branches and a verified complete bundle,
README edits were saved outside the checkout, the invalid reference was quarantined, and local
master was restored to the expected integrated commit. No stash was inspected or changed. Phase
5E began only after this recovery produced a clean master and the requested feature branch.

Canonical baseline: **199 discovered/executed/passed harnesses, 31,662 checks, Failed=0,
Missing/Invalid=0; Maven clean package PASS; Spotless PASS**. Production was not edited before
that successful baseline. Architecture, module/dependency/persistence guides and the Phase-5B/5C
reports were consulted alongside current Reward, Clan, Player and ModuleManager implementations.
No applicable AGENTS.md was present.

Only OnlineRewardModule production code is changed. Public constructor, services, serialization,
config schema, commands, permissions, module registration/order, seven reload participants,
formatting configuration and operator development scripts remain unchanged.

Evidence is outside the checkout under
`C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/phase5e-evidence-20261026/`.
It includes the unchanged module source, compiled pre-fix probe and observations, baseline receipt,
compiled controls, native probe/runner/logs, final receipts and complete operator inventories.

## Observed pre-fix behavior

The separately compiled `OnlineRewardPreFixProbe` used the actual unchanged module, actual
OnlineRewardService/RewardService/PlayerService and an isolated controlled Bukkit scheduler with
real owned handle/status/callback behavior. All observations precede safety teardown.

| Scenario | Actual observation before fixture teardown |
|---|---|
| Final Logger throws after task acquisition | 1 registered task, 0 cancellations; getter available; invoking its callback reads the player statistic and initializes progress; original exception object preserved |
| Enable twice | 2 registrations, 2 executable callbacks; published service replaced; 7 coins total for the measured single interval, **not** a reproduced duplicate payout |
| Disable after double enable | Only 1 cancellation; 1 registered task remains because the first handle was overwritten |
| Cancel throws before effect | 1 task remains registered; getter incorrectly remains available |
| Enable after refused cancellation | 2 registered tasks; old callback reads/processes current runtime; later disable cancels only the newly stored task, leaving the old handle lost |
| Cancel takes effect then throws | 0 registered tasks but getter still available; manually captured old callback remains operational after fresh enable |
| Reentrant enable during scheduling | 2 registrations; one remains after disable |
| Callback during scheduling | Premature getter available; statistic read and progress initialized before startup completed |
| Scheduler throws before registration | 0 tasks, getter closed, same exception object |
| Scheduler registers then throws before returning | 1 registered task, no acquired handle/cancellation; getter closed and callback inert |

Confirmed defects are leaked/lost task ownership, premature/abandoned service publication,
duplicate/reentrant acquisition and callback generation confusion. No duplicate monetary mutation,
profile corruption or crash-durability guarantee is inferred from these results. The last scheduler
case is an explicitly reproduced boundary limitation, rather than an ordinary Paper startup claim.

## Bounded ownership and lifecycle

Owned: one OnlineRewardService, one OnlineRewardMessageRenderer and one synchronous 20-tick
processing task. Borrowed: current PlayerService and RewardService, live ConfigService/config,
MessageService, JavaPlugin/server/logger. No own repository, wallet database, quit listener,
command, menu, second rank/profile cache or additional flush scheduler is introduced.

The module records inactive/enabling/active/cleaning state and, only when required, one pending
task cancellation. An active/transition guard rejects duplicate and reentrant enable before
acquisition. Pending cancellation is reconciled before obtaining new dependencies or scheduling.
Dependency acquisition, local construction, actual schedule and final logging share one protected
startup boundary. Runtime fields and getter become active only after logging succeeds.

Failed startup clears runtime authority and attempts exact-handle rollback. The same startup
exception is rethrown. Cancellation and cancellation-status errors are attached with resource
context as suppressed diagnostics. No automatic reward grant, flush or profile save occurs.

Each callback captures its own exact service and renderer. Authorization requires active state
and identical published service. It is checked before roster processing, for each player, after
the external statistic read, and before notification. Failed/disabled/cancel-refused/old-generation
callbacks cannot use a fresh activation. Reentrant disable during the statistic read also prevents
the grant and further player processing. Deliberately retained external Java service references
are outside this callback/getter authority contract.

Disable revokes authority and clears published references before external cancellation. It
attempts `cancel()` and checks `isCancelled()` independently. A before-effect error remains pending
and inert; an after-effect error is still reported but a confirmed cancelled handle is released.
Unknown status or silent no-effect cancellation remains pending. Another disable or enable retries
the same handle; unresolved cleanup cannot create another operational generation. Successfully
released disable has no repeated external effects. Runtime release is not skipped by cancel errors.

[Paper BukkitTask 1.21.11](https://jd.papermc.io/paper/1.21.11/org/bukkit/scheduler/BukkitTask.html)
defines cancellation status and synchronous ownership; the
[scheduler API](https://jd.papermc.io/paper/1.21.11/org/bukkit/scheduler/BukkitScheduler.html)
returns the task handle from `runTaskTimer`. A nonconforming scheduler that registers and then
throws before returning leaves no exact handle. Its callback is inert, but full external resource
release is not promised. Plugin-wide `cancelTasks(plugin)` is intentionally absent because other
modules own tasks on the same plugin. A native CraftTask confirms cancellation normally.

## Reward and persistence compatibility

OnlineRewardService, progress/config/renderer and RewardService are unchanged. Existing behavior
remains authoritative: PLAY_ONE_MINUTE; 20-tick processing; live config; initialization without
retroactive payouts; configured interval and coins; aggregation/remainder; statistic rebasing;
disabled-time consumption; online-roster adaptation with unloaded profiles skipped; per-player
failure isolation; RewardSource.PLAYTIME and `online:playtime`; existing notification renderer.

OnlineReward disable does not flush Reward, call PlayerService.saveAll, clear aggregates, reset
progress or change coins. Real temporary profile tests observe unchanged bytes across OnlineReward
disable and pending Reward dirty tracking still present. Actual RewardModule and PlayerModule
then perform their established shutdown saves; a new full owner chain reads matching coins and
progress and rewards only the next interval. Wallet and progress retain their existing shared
CorePlayer storage boundary, without new format/schema or multi-file transaction.

ModuleManager behavior is unchanged. A failed OnlineReward startup rolls itself back; a later
failed Quest consumer causes manager rollback of the successfully started OnlineReward before
Reward and Player. Normal reverse shutdown stops Quest before OnlineReward before its borrowed
owners. Quest/DailyQuest are never hot-swapped against replacement services. Their separate late
startup/cancellation risks remain deferred; Player/Reward persistence failures and crashes do not
become exactly-once durable transactions through this module change.

## Behavioral tests and compiled controls

New `OnlineRewardModuleLifecycleHarness`: **171 checks** in the focused production run. Covers
L1–L28 with actual module/services, owned observable task handles, before/after-effect and
unconfirmed cancellation, real final Logger failure, exact/suppressed exceptions, duplicate and
reentrant guards, getter transitions, scheduling/activation boundaries, old callbacks, live config,
interval/remainder/disabled/reset semantics, isolated player failure, borrowed ownership, real
disposable persistence and actual ModuleManager rollback/reverse shutdown.

OnlineRewardLifecycleHarness remains a canonical **42-check** entry. Only two brittle callback
spelling/cancel-before-clear assertions are replaced by stronger real task/generation/transition
observations. Its twenty-tick scheduling, module-order/reload and per-player isolation contracts
are retained. Every old canonical harness remains, including OnlineRewardService, Reward lifecycle,
Player lifecycle, PlayerRewardPersistence and Quest/DailyQuest persistence/lifecycle tests.

`scripts/tests/online-reward-lifecycle.Tests.ps1`: **10/10 mutations compiled successfully and
failed the intended behavioral AssertionError**. Compile failures do not count. Temporary overlay
classes precede normal classes only in the child JVM; original sources/classes remain untouched.

| ID | Compiled defect | Intended case |
|---|---|---|
| N1 | Omit late-start task rollback | Exact acquired timer cancellation |
| N2 | Allow duplicate enable | Existing runtime and single registration |
| N3 | Allow reentrant enable | No nested task acquisition |
| N4 | Suppress cancellation diagnostics | Refused cancellation reported |
| N5 | Authorize disabled old callback | No old player reads after disable |
| N6 | Omit exact service-generation identity | Old task cannot process new runtime |
| N7 | Discard pending handle | Retry exact handle before scheduling |
| N8 | Expose inactive runtime through getter | Inactive/transition getter closed |
| N9 | Change cadence to 21 ticks | Exact synchronous 20-tick delay/period |
| N10 | Replace original startup exception | Exact original Logger exception |

Positive controls pass before and after: OnlineRewardModuleLifecycle **171**, OnlineRewardLifecycle
**42**, OnlineRewardService **42**, RewardModuleLifecycle **136**, PlayerModuleLifecycle **204**,
PlayerRewardPersistence **11**: **606 checks per round**. The initial sandboxed focused persistence
run encountered Windows AccessDenied on atomic rename in its temporary directory; the unchanged
test then passed outside the sandbox, as does the canonical verifier. It is not a product defect.

## Native acceptance and final verification

**Native PASS: 71 checks across two complete JVM/server processes**, first run **35**, cold restart
**36**. Paper **1.21.11-132-c5eb079**, LuckPerms **5.5.84**, Java 21, loopback-only
**127.0.0.1:25596**. Both processes exit **0** with **0 ERROR/SEVERE**. Each first callback
intentionally injects one isolated statistic-read RuntimeException, reported as WARNING; the
other adapter still processes normally. These expected warnings are retained in the logs.

Both processes start the exact **28 production modules**, the existing **five production timers**
and exactly **one native synchronous OnlineReward CraftTask** with period 20. Actual task executions
occur at server ticks **19, 39, 59, 79** in each process. A configured real service interval of
one minute / 1,200 ticks grants **17 coins**, unchanged playtime grants nothing further, and two
additional intervals aggregate a **34-coin** grant with one notification and a 50-tick remainder.
The first process durably stores **51 coins / 13,600 processed ticks**. The cold process reads
that profile, retains the remainder in its controlled statistic input, grants only new intervals,
and stores **102 coins / 17,200 processed ticks**. Notifications occur exactly twice per process.

This is **native scheduler/API/service acceptance with controlled Player adapters**, not network
login or visual Minecraft-client testing. The probe wraps the actual native task's Runnable and
calls the unchanged original production callback on Paper's main thread. Only during that call it
temporarily adapts the core plugin's Server online roster to two Player proxies, delegates other
Server operations and restores the native Server in finally. Statistics and message delivery are
controlled adapter boundaries; other production modules keep the native roster. Profiles are
loaded through the real PlayerService. No production module/service implementation is replaced.

The actual ModuleManager reverse shutdown is invoked before Paper's plugin-wide safety teardown
so resource leaks cannot be concealed. At Reward's shutdown boundary, OnlineReward's exact task is
cancelled, its getter is closed and both borrowed owners remain available. Reward stops before
Player. All production handlers/tasks are released and the captured old callback performs no
player reads/messages after shutdown. Normal Paper stop then closes the process. The second run
starts a new JVM/composition root against the disposable saved data: no live plugin unload,
unsupported classloader reload, module hot-swap or replacement under active Quest/DailyQuest.

Final canonical verifier: **200 discovered/executed/passed harnesses, 31,835 checks, Failed=0,
Missing/Invalid=0, exit 0**. **Maven clean package PASS; Spotless PASS**. The pinned Spotless 2.43.0,
Google Java Format 1.22.0/AOSP and six-file pilot are untouched. All **199 baseline entries** are
retained. Exact **+173** check delta: new OnlineRewardModuleLifecycleHarness **+171**,
GuiAuthoringAudit **231 → 232** for the added test source, RepoLink **80 → 81** for the maintained
guide/report link. Every other old harness retains its count. The new PowerShell script parses
with zero errors.

Native-tested JAR SHA-256, identical to the final canonical artifact:
`850cd56f2d6c7f0a77dd0acb823fba3cf94f178efb550861ce87ea933931a441`.
The canonical clean removed generated negative-control individual logs after their successful
first run. The same **10/10 controls and both positive rounds passed again** after canonical
completion. Detailed receipts are copied outside target; normal production source and bytecode
SHA-256 values are unchanged by the overlays.

| Resource boundary | Before correction | Executable corrected observation |
|---|---|---|
| Late Logger failure | 1 registered task / 0 cancel attempts / published getter | Exact acquired task cancelled once; getter closed; no player effects |
| Duplicate/reentrant enable | 2 registered tasks; one lost on disable | Second acquisition rejected; one task/service preserved |
| Refused cancellation before effect | Getter open; old task can operate and handle can be lost | 1 external task retained privately; getter closed; 0 callback player reads; new acquisition blocked |
| Cancellation after effect then throw | Cancelled task but public service remains open | Error reported, confirmed handle released, getter closed |
| Clean native shutdown | Not used as a pre-fix claim | 1 OnlineReward / 5 total production timers → 0 before Paper safety teardown, in both processes |
| Scheduler registers then throws without handle | 1 external task remains | Still cannot cancel unknown exact handle; callback is inert, limitation explicit |

## Operator protection and changed paths

Before testing, the complete actual `dev-server` full relative path/length/SHA-256 inventory captured
**934 protected files**. Historical 913/22 counts are not assumed. All standalone/native data is
disposable; operator worlds, profiles, live configs, deployed JARs, LuckPerms data and backups are
never mutable fixtures. `.run` and top-level development/deployment scripts have a separate hash
inventory. Final comparison: **934 before / 934 after; 0 added, 0 deleted, 0 changed protected
paths/lengths/SHA-256 values; 0 protected tool/config changes**. This includes 22 actual files under
`plugins/VapeeCore/`, deployed plugin JARs and existing sync-conflict copies. No real operator
path was changed to make tests pass. Native configs/worlds/profiles are confined to the evidence
server, with sources/logs/receipts, tested configuration and synthetic profiles retained outside
the checkout. Both native processes exited before their exact validated disposable server/world
directory was removed. The final native-tested and canonical artifact hashes still match.

Scoped inventory:

1. Production: `src/main/java/dev/vapee/core/onlinereward/OnlineRewardModule.java`.
2. New behavioral test: `src/test/java/dev/vapee/core/onlinereward/OnlineRewardModuleLifecycleHarness.java`.
3. Retained behavioral entry: `src/test/java/dev/vapee/core/onlinereward/OnlineRewardLifecycleHarness.java`.
4. New compiled controls: `scripts/tests/online-reward-lifecycle.Tests.ps1`.
5. Maintained guide: `docs/MODULE_DEVELOPMENT.md`.
6. This report: `reports/architecture-modernization/PHASE_05E_ONLINE_REWARD_LIFECYCLE.md`.

No Phase-5C, presentation/tablist, PlayerModule, RewardService, Quest/DailyQuest, ModuleManager,
VapeeCore registration, operator/run-config or build/deploy script changes belong to this phase.
Completion requires exactly one local commit named `Harden OnlineReward module lifecycle and task
ownership`; no push, merge, stash modification, deployment or subsequent phase.
