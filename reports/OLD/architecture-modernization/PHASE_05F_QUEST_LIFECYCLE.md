# Phase 5F — Quest lifecycle and flush safety

## Baseline, scope and toolchain

Clean integrated master **cecc31c283c16181f6f028b42c330bf4ab69f678**, branch
**phase/quest-lifecycle-safety**. Only one worktree was present. No Git recovery/reset, ref rewrite,
stash inspection, recovery-branch/bundle interaction, sync-conflict deletion or repository move was
performed in this phase. The new feature branch was created from the verified expected master.

Oracle JDK **21.0.12.1+1-LTS-4**, Maven **3.9.16**, PowerShell **7.6.5**, Windows 11 amd64.
Baseline canonical verifier: **200 discovered/executed/passed harnesses, 31,835 checks, Failed=0,
Missing/Invalid=0; Maven clean package PASS; Spotless PASS**. No production edit preceded baseline
success. Applicable AGENTS.md discovery found none. Current project architecture, developer,
module, persistence, dependency and style contracts, Phase-5B/5C/5E reports, and relevant production
and test implementations informed this bounded change.

Evidence is retained outside the checkout under
`C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/phase5f-evidence-20261026/`.
It includes saved original sources/classes, the compiled pre-fix probe/fixture, original observations,
baseline/final receipts, formatting proof, compiled controls, native sources/runner/logs and operator
path/hash inventories. No operator runtime is a mutable fixture.

## Executable pre-fix observations

The unchanged real QuestModule, QuestService, listener, producer and reporter ran through controlled
Bukkit scheduler/PluginManager boundaries, actual HandlerLists and real disposable FilePlayerRepository
files. Observations occur before safety teardown. Source inspection alone is not counted as proof.

| Scenario | Observed unchanged behavior |
|---|---|
| Final Logger failure | **2 Quest handlers / 1 active task / 0 cancellations / getter open**, one seeded sample; original Logger exception preserved; leaked callback then granted **7 coins** and durably stored COMPLETED after failed startup |
| Register throws before or after effect, successful unregister | **0 handlers / 0 tasks**, original registration exception preserved |
| Register after-effect failure plus refused unregister | **1 handler remains**, original registration exception is masked by cleanup error; orphan listener can seed/sample/flush and store a **7-coin COMPLETED** quest |
| Double enable | **4 handlers / 2 active tasks**, public service replaced; its disable leaves **2 handlers / 1 active task** |
| Reentrant registration, scheduling or final Logger | Each produces **4 handlers / 2 tasks**; disable leaves **2 handlers / 1 task** |
| Refused cancel before flush | **2 handlers / 1 active task / getter open**, dirty state uncommitted; reporter remains feedback-enabled |
| One failed save during disable | Disable returns success, **0 dirty markers** despite unsaved A; B's independent file commits; original A aggregate still holds progress |
| Two failed saves | Disable returns success, **0 dirty markers**, both files remain stale while original loaded aggregates retain values |
| Player fallback while still failing | Original aggregate remains loaded, disk still stale; after storage recovery, existing Player save commits retained value **2** |
| Roster-read failure or Logger failure handling one sample | Disable throws before unregister/field release; **2 handlers / getter open**, later player progress and dirty flush skipped |
| Unexpected outer dirty-snapshot/flush exception | Logged but not reported to Disable caller; dirty tracking cleared; file still lacks pending quest |
| Disable reenters during statistic read | Normal cleanup runs but returning outer callback resurrects **1 retired producer sample** |
| Progress consumer throws before its effect | Producer already advances baseline; repeating the same statistic does not retry the minute report |

These confirm lifecycle authority/resource leaks, original-exception masking, skipped independent
cleanup, silent incomplete shutdown flush and discarded retry bookkeeping. The 7-coin observations
prove unauthorized abandoned-runtime activity, not duplicate monetary payout. No duplicate payout,
profile corruption, hard-crash exactly-once recovery or broader DailyQuest lifecycle safety is inferred.

The monetary activity checks install deterministic probe-only definitions and assignments into
the real retained service: through the published getter after Logger failure, or the captured
listener's service/registry in the refused-registration-cleanup case. Borrowed owners stay alive
until observation. Those amounts depend on this controlled catalog; the resource and callback
authority observations do not assume that ordinary failed startup populates its empty registry.

## Ownership and protected startup

Quest owns its definition registry, QuestService/dirty set, ProgressReporter, PlaytimeProducer/runtime
samples, one two-handler listener and one synchronous **100-tick delay/period** task. It borrows the
current PlayerService and RewardService, messages/localization and plugin/server/logger. Profile
files, wallets, Reward dirty tracking, OnlineReward's task and DailyQuest config/commands/menus/
observers remain outside Quest ownership. The explicit 28 modules and seven reload participants
are untouched.

Enable guards active/enabling/cleaning and unresolved prior cleanup before acquisition. Borrowed
services are acquired once inside the protected boundary; local RuntimeResources construction,
actual listener registration, initial seeding, scheduling and final logging all precede active
publication. The three existing public getters remain closed until success. Failed startup cancels
the known exact task, unregisters the known exact listener, silences the reporter and clears runtime
samples without inventing final progress or persistence work. The same original exception is
rethrown; independent release errors are contextual suppressed diagnostics.

The private per-generation resource object is not a module framework, dependency scanner, global
registry or second player cache. A task captures it; active identity must match before processing.
Listener callbacks use the same exact identity. Compatible public listener/producer constructors
remain; package-local activation seams bind module-managed instances. Producer authorization is
checked again after statistic access, preventing the reproduced reentrant sample resurrection.
No old task/listener is allowed to sample, grant, notify or flush a replacement runtime.

## Independent shutdown, exact external ownership and retry

Disable first revokes active getters/task/listener authority and retires the resource object. It
silences feedback, independently attempts task cancellation/status confirmation, final online
sampling, strict Quest flush and listener unregister/status confirmation. Samples are cleared only
after final sampling and flush obligations complete. A cancel error does not skip saves; a sample
or diagnostic error does not skip later players or existing dirty saves. Unregister errors do not
discard pending persistence.

Cancellation before effect, after effect, silent no-effect return and unavailable status are tested.
After-effect errors remain reported even when release is confirmed. Only the exact task handle and
listener identity are reconciled; unrelated plugin resources are never cancelled. If a scheduler
registers then throws before returning its task, no exact handle exists: callback authority is inert,
but external release is explicitly not guaranteed. No `cancelTasks(plugin)` shortcut is used.

Unresolved resources and/or persistence remain private pending cleanup. Enable is blocked until an
explicit same-process `disable()` retry succeeds. The old QuestService still borrows the original
PlayerService and loaded CorePlayer objects; no stale disk reconstruction occurs. Successful earlier
saves stay committed and need not be repeated by Quest. A final-sampling retry uses the retained
baselines; unchanged samples cannot duplicate already accounted minutes or completed-Quest rewards.
Such a retry can observe later available statistics while cleanup is still pending; it does not
restore scheduled/event authority. A failed save cannot generate a successful Quest commit event.

## Strict Quest flush and persistence boundaries

The pre-fix disk observations justify the narrow package-local `QuestService.flushAllOrThrow()`.
Public `flushPlayer()` and `flushAll()` keep their existing void/log-and-continue API. The strict
path attempts every snapshot UUID independently and retains an immutable UUID-to-original-cause
mapping for incomplete operations. Successful saves remove their own dirty markers and then notify;
failed saves retain their markers. Post-commit observer diagnostics remain distinguishable from
the actual disk result; a retained aggregate alone never proves a save.

QuestChangeListener commit events follow actual successful saves. Existing normal completion chat
still reports the in-memory grant before the deferred flush; it is not claimed as a durable-save
receipt. Shutdown sampling disables that feedback before it can run.

The six storage states are deliberately separate:

1. Successful Quest save: profile committed, its Quest dirty marker removed.
2. Failed Quest save: original aggregate changed in memory, stale file, dirty marker retained.
3. Old implementation's lost bookkeeping: unchanged memory, stale file, dirty set erroneously cleared.
4. Later successful PlayerModule fallback: same aggregate saved before loaded-cache release.
5. PlayerModule fallback also fails: the same PlayerService/cache remains privately owned for its own retry.
6. JVM exit with unresolved changes: retained memory can be lost; no durable recovery journal exists.

When a dirty UUID is already unloaded, the existing save-before-eviction/successful Player shutdown
contract remains the persistence handoff. Tests prove that path separately from simultaneous Quest
and Player failures. For the latter, explicit Quest and Player retries commit the original objects
after storage recovery. Neither module reloads unsaved progress from stale files. ModuleManager
continues reverse shutdown, logs errors, clears its enabled list and does not schedule retries;
operators need original owners/JVM alive to complete them. No global shutdown-policy change is made.

RewardService ownership and existing Quest ACTIVE/REWARD_PENDING/COMPLETED behavior, progress,
coin amounts, RewardSource.QUEST, `quest:<id>` reason and commit notification rules remain. Quest
does not terminate Reward/Player, clear Reward tracking, add a flush scheduler or force a pending
payout during startup/cleanup. Legitimate final completed-minute sampling can still complete a
Quest and use the established RewardService aggregate boundary, with the reporter already silent.

## Playtime and event compatibility; explicit residual boundary

PLAY_ONE_MINUTE, runtime baselines, completed minute boundaries, multiple minutes, initial seed
without historical awards, statistic decrease rebasing, unloaded-player skipping, quit forgetting
and successful runtime release are preserved. Quest remains separate from OnlineRewardProgress.
Join remains MONITOR, Quest quit LOWEST, Player quit NORMAL. Actual quit sampling precedes Quest
flush and Player save-before-unload.

The independently reproduced sample-update-before-report behavior is retained. Automatically moving
the sample or replaying a failed report could duplicate already-applied partial progress/rewards.
A transaction spanning reporting/rewards would exceed this lifecycle phase. The external probe and
canonical characterization explicitly record that a throwing report can consume a minute boundary;
there is no promise to recover a statistic that could not be read or progress never applied. Default
public producer behavior is unchanged; only module activation authority is added.

DailyQuest stops before Quest, which stops before OnlineReward/Reward and Player. A full ordered
restart reacquires fresh registry/service/task/listener identities. No live isolated Quest hot-swap
under active DailyQuest is supported. DailyQuest late logging, observer/menu and sequential cleanup
risks remain a separate deferred phase; its production code is untouched.

## Behavioral tests and compiled controls

New **QuestModuleLifecycleHarness: 271 checks**, supported by QuestLifecycleFixture with actual
HandlerLists/callbacks/domain services and real temporary FilePlayerRepository files. It covers
L1–L40: normal cadence and commits, dependency/registration/schedule/log failures, original/suppressed
exceptions, duplicate and three reentry points, refused external cleanup and retry, inactive getters,
stale task/listener behavior, final one/multiple/no-minute sampling and silence, per-player/roster/
diagnostic failures, one/multiple save failures, exact cause maps, observers only after commit,
original-aggregate retention, outer flush failure, reentrant reads, pending rewards, native event
priorities, real Player/Reward owner fallback/restart, manager rollback and consumer ordering.

QuestLifecycleHarness retains its canonical **42 checks**; exactly two brittle callback/shutdown
source spellings are replaced by stronger cadence/final-sampling/independent-cleanup behavior.
All 200 old canonical entry points remain. Newly created Java test sources are formatted with the
pinned **Google Java Format 1.22.0/AOSP**, using a separate evidence POM/classpath; project POM,
Spotless 2.43.0 and six-file formatter pilot are unchanged. Early focused compilation found two
test-only uses of a package-private wallet setter; tests were corrected to use the existing public
EconomyService API. No product API was widened for a test.

Supplemental `scripts/tests/quest-lifecycle.Tests.ps1`: **12/12 variants compile and fail the named
behavioral AssertionError**; compilation failure is never counted. Only temporary source/classes
are overlaid on each child JVM classpath. Positive controls pass before and after: new lifecycle
271, old QuestLifecycle 42, QuestService 44, Producer 18, Reporter 18, feedback localization 5,
DailyQuestService 20, PlayerQuestPersistence 21, OnlineReward lifecycle 171, Reward lifecycle 136,
Player lifecycle 204: **950 checks each round**. Detailed receipts were saved outside target before
any final clean can erase them.

| Control | Compiled defect detected |
|---|---|
| N1 | Missing rollback after late Logger failure |
| N2 | Duplicate runtime acquisition |
| N3 | Reentrant runtime acquisition |
| N4 | Missing exact task/runtime identity |
| N5 | Cancellation failure skips final flush |
| N6 | Unregister failure skips final flush |
| N7 | Clearing dirty UUIDs after incomplete strict saves |
| N8 | Hiding incomplete flush from lifecycle caller |
| N9 | Listener retains authority over retired pending persistence |
| N10 | Getter exposes pending retired runtime |
| N11 | Wrapping/replacing original startup exception |
| N12 | Missing normal final shutdown sample |

## Native acceptance and final canonical verification

**Native PASS: 79 checks**, **39** in the first complete Paper/JVM process and **40** in the second.
Paper **1.21.11-132-c5eb079**, LuckPerms **5.5.84**, Java 21, loopback-only **127.0.0.1:25597**.
Both processes exit **0**, with **0 ERROR/SEVERE**. Fault scenarios remain in the isolated standalone
fixtures; the first native acceptance run and its cold restart both pass cleanly.

Each process confirms the exact **28 module order**, the **seven actual reload participant classes**
and a successful existing reload, **one Quest listener / two native handlers** with MONITOR-Join and
LOWEST-Quit, one synchronous period-100 CraftTask and the existing five total production repeaters.
The actual task executes on the primary server thread at ticks **99, 199, 299**. Scheduled samples
save partial progress; a configured target-two Quest reaches COMPLETED through real RewardService,
grants **11 coins** and emits one ordinary completion message. An unchanged repeat adds nothing.

Native selected RegisteredListener event executors preserve Player NORMAL load, DailyQuest HIGHEST
assignment and Quest MONITOR seed; quit executes Reward LOWEST, Quest LOWEST, Player NORMAL.
The quit profile is saved and unloaded with the final progress and correct reward. The primary
profile's final shutdown sample crosses two additional minute boundaries, reaches the target-four
Quest, grants its **13 coins** and saves **partial=4 / coins=24** with **no shutdown feedback**.

The normal native ModuleManager reverse chain is observed before Paper-wide safety cleanup:
DailyQuest stops while Quest remains available; then Quest revokes getters, removes its exact two
handlers and cancels its task while Reward/Player remain active; Reward stops before Player. All
core-owned handlers/tasks are gone before Paper's safety teardown. Captured old task and listener
callbacks perform no statistic reads, feedback or file mutations after this shutdown.

A separate second Java process loads the same disposable profiles, with the completed statuses and
24-coin primary wallet intact. Fresh samples advance primary partial progress **4 → 8** without
repeating old rewards; its saved balance remains **24**. The quit profile also retains/reconciles
its legitimate completion state. Exactly two intended player YAML files remain. No closed plugin
classloader is reactivated and no live Quest runtime is swapped under an active DailyQuest.

This is **native scheduler/event/API/service acceptance using controlled Player adapters**, not
real network login, a full unfiltered Bukkit event bus or visual Minecraft-client testing. The
probe wraps the native CraftTask Runnable and executes the unchanged original callback. Only its
online-roster boundary and the final Quest shutdown sample receive controlled actors, with the
native Server restored afterwards. Reporter recipient lookup/transport is adapted to those actors;
the actual reporter, localization, MessageService, grant logic and repositories run. Actual native
registered executors for the selected join/quit handlers are called in their real priority order.
Other production modules retain the native empty online roster. These limitations are intentional
and are not described as client acceptance.

Final canonical verifier: **201 discovered/executed/passed harnesses, 32,110 checks, Failed=0,
Missing/Invalid=0, exit 0**. **Maven clean package PASS; Spotless PASS**. All **200** baseline entries
remain. Exact **+275 checks**: new QuestModuleLifecycle **+271**, GuiAuthoringAudit **232 → 234**
for two new Java test sources, RepoLink **81 → 83** for two maintained guide links. Every other
old harness count remains unchanged. The new PowerShell script parses with zero errors.

Pinned new-test formatter version **1.22.0/AOSP** and its dry-run check pass; the original six pilot
files/POM remain unchanged. The native-tested artifact equals the final canonical package:
SHA-256 **dfe5658c4f03535f1a22bdfacd3a76a14ed2b01881347a8fcd0e4fd2ffe512df**.

| Resource/state boundary | Pre-fix observation | Corrected executable observation |
|---|---|---|
| Late logging error | 2 handlers / 1 task / getter available | Known resources rolled back; getter closed; no invented progress/save |
| Double/reentrant acquisition | 4 handlers / 2 tasks, one generation lost | Second acquisition rejected; valid 2-handler / 1-task runtime preserved |
| Refused cancellation | Operational old task; flush skipped | Physical refused task retained privately and inert; final sample/flush and listener release still attempted |
| Refused unregister | Orphan listener can process; original error masked | Exact ownership retained; callback inert; independent saves run; original startup error preserved |
| Failed profile save | Dirty tracking cleared and Disable returns success | Failed UUID/cause reported, dirty marker and original aggregate retained, independent successful files committed |
| Clean native shutdown | Not inferred from source | Quest 2 handlers / 1 task → 0; all 5 core timers → 0 before safety teardown, both processes |
| Register-then-throw without task handle | Exact cancellation unavailable | Still no exact release promise; known listener released and unknown task callback inert |

## Operator protection and exact scope

Before tests, the actual complete protected dev-server path/length/SHA-256 inventory contains
**934 files**. `.run` and all top-level development/deployment scripts have a separate hash inventory.
Operator profiles, coins/quests, LuckPerms, worlds, deployed JARs, libraries, configurations and
migration/recovery/sync-conflict evidence are never mutable fixtures. Tests use temporary profiles
or a separate native server with fresh world/feature data.

Final complete comparison: **934 before / 934 after, 0 added, 0 deleted, 0 changed protected
paths/lengths/SHA-256 values; 0 protected Run/config/development-script changes**. Both native
processes exited before the exact verified disposable server/world directory was removed. Probe
sources/classes, logs, receipts, tested configs and synthetic profiles remain outside the checkout;
operator backups, sync-conflict files, stash and recovery material remain untouched.

Intended changed paths:

1. `src/main/java/dev/vapee/core/quest/QuestModule.java` — main lifecycle owner.
2. `src/main/java/dev/vapee/core/quest/QuestService.java` — reproduced need for strict completion signal.
3. `src/main/java/dev/vapee/core/quest/QuestListener.java` — reproduced stale-listener authority.
4. `src/main/java/dev/vapee/core/quest/QuestPlaytimeProducer.java` — reproduced post-statistic reentry authority.
5. `src/test/java/dev/vapee/core/quest/QuestModuleLifecycleHarness.java`.
6. `src/test/java/dev/vapee/core/quest/QuestLifecycleFixture.java`.
7. `src/test/java/dev/vapee/core/quest/QuestLifecycleHarness.java` — two affected brittle assertions only.
8. `scripts/tests/quest-lifecycle.Tests.ps1`.
9. `docs/MODULE_DEVELOPMENT.md`.
10. `docs/PERSISTENCE_DEVELOPMENT.md` — strict flush versus original-owner fallback clarification.
11. This report.

No DailyQuest, Reporter, Reward, OnlineReward, PlayerModule, ModuleManager, composition, presentation,
commands, permissions, config defaults, persistence schema, build/deploy or IntelliJ file changes.
The phase uses one local commit `Harden Quest module lifecycle and flush safety`, without push,
merge, deployment, stash/recovery changes or beginning Phase 5G.
