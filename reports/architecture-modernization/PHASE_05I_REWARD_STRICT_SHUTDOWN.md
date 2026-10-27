# Phase 5I — Reward Strict Shutdown-Flush Safety

## Baseline and scope

Clean integrated master `ba0617f39cd00c59f707906426155c7cb766350c`, branch
`phase/reward-strict-shutdown`, repository `xd-vape/mc-vapeecore`. Preflight inspected status, HEAD,
twelve commits and worktrees. No applicable AGENTS.md was present. No stash, recovery branch, bundle,
sync-conflict evidence or IDE/deploy configuration was inspected for repair or changed.

Actual baseline `scripts/verify.ps1`: **203 discovered/executed/passed harnesses, 32,601 checks,
Failed=0, Missing/Invalid=0, exit 0; Maven clean package PASS; Spotless PASS**. No production edit
preceded successful baseline or the current unchanged-class reproductions.

Oracle JDK **21.0.12.1+1-LTS-4**, Maven **3.9.16** from IntelliJ IDEA 2026.2.3, PowerShell **7.6.5**,
Windows 11. Spotless **2.43.0**, Google Java Format **1.22.0/AOSP**, existing six-file pilot unchanged.
New Java test sources were formatted individually; no unrelated formatter normalization.

The Phase-5H decision, particularly B/C/B2/F2/G3 and its Phase-5I contract, is authoritative design
context. Current Reward/Player/Economy/Quest implementations, lifecycle/persistence/dependency guides,
README/architecture/developer/code-style guidance and reports 5B/5C/5E/5F/5G were reviewed.

Primary production changes are RewardService and RewardModule. RewardListener is the only additional
production file, justified by the executable stale-listener observation below. ModuleManager, Player,
Economy, Quest, OnlineReward, DailyQuest, repositories, SafeFileWriter, schemas, commands, permissions,
defaults, 28-module order and seven reload participants remain unchanged. No global retry policy,
thread, scheduler, wallet, repository, journal or durable operation identity is introduced.

## Unchanged-class observations

External `RewardBeforeProbe` uses real Reward/Player/Economy modules, original service associations,
actual Bukkit HandlerLists and disposable FilePlayerRepository files. Only external repository outcomes,
task cancellation and listener release are controlled. Observations occur before fixture safety cleanup.

| Probe | Measured original behavior |
|---|---|
| R1: A +11 fails, B +22 succeeds | Reward disable returns normally; A memory 11/file 0; B file 22; both UUIDs attempted; dirty set empty and no pending Reward record |
| R2: Reward and Player A fail | A memory 11/file 0, B file 22; original Player cache pending; Reward dirty empty; manager enabled list empty; second manager disable makes zero extra saves |
| R3: repair then call captured RewardService flush methods | Zero additional attempts; file A remains 0 because lost dirty work cannot be rediscovered |
| R4: cancel returns without effect | Physical task still running, isCancelled=false, no pending handle retained |
| R5: refused listener release, then held old public service receives +11 | Getter already closed and unregister error reported, but retained listener's quit callback still saves the old work once, file A=11 |

R5 intentionally uses a caller holding the old service reference to make subsequent work observable;
it is not a claim that all arbitrary Java service references can be revoked. It proves the module-owned
retired listener lacked its own generation boundary. The new pending flush would otherwise leave old
dirty work exposed to this callback. Thus RewardListener receives a narrow compatible activation gate.

These confirm lost Reward bookkeeping and missing completion signals, not unconditional Coin loss.
Player can still save the original aggregate. If both owners fail and the JVM exits, private pending
memory is not a durability mechanism. Original probe sources, logs and compile evidence remain external.

## Strict service flush and public compatibility

`RewardService.flushPlayerOrThrow` factors the existing dirty check, loaded-owner handoff, Player save
and successful-marker removal. Public `flushPlayer(UUID)` still validates UUID, logs normal save failures
and retains dirty work; public `flushAll()` remains the normal log-and-continue batch used by the existing
20-tick task and quit behavior. Grant order, deferred wallet mutation, amounts, source/reason validation,
overflow, unavailable-player results, public constructors and method signatures are unchanged.

Package-local `flushAllOrThrow()` snapshots the linked dirty UUID set and attempts each once. Each
RuntimeException is collected independently, so A cannot skip B. Failed UUIDs remain dirty; successful
ones become clean. A package-local `FlushFailures` owns an immutable copied UUID-to-original-exception
map and UUID-context suppressed causes. No mutable collection is exposed. The strict loop performs no
diagnostic call between saves, so a throwing Reward WARNING logger cannot interrupt it.

“Original” means the exact exception delivered by the Player/repository boundary. If that repository
already replaced its IO cause with a logger error, Reward cannot reconstruct it. That broader diagnostic
defect remains Phase 5J-A. A save exception may also occur after physical commit; strict failure is
reported as incomplete/possibly uncertain, not proof that the file remained uncommitted. Tests inspect
the actual files separately.

## RewardModule ownership and cleanup

Reward owns its service/dirty UUIDs, exact LOWEST listener and one synchronous 20-tick flush task.
PlayerService/EconomyService are borrowed; FilePlayerRepository remains the aggregate writer. There is
no copied Player cache, direct YAML write, borrowed-owner disable or Quest dirty-marker mutation.

Disable captures the current owned resources, records `flushPending` for the first active shutdown,
then clears active/public runtime before calling external cleanup. Cancellation, strict flush and exact
listener unregister have independent failure boundaries. A failed flush preserves the original service,
Player association, dirty UUIDs and explicit obligation; `active=false` never erases it. Successful
strict completion clears only that obligation. Completed handles/services are released independently,
and repeated completed disable performs no additional saves or external registrations.

The explicit pending state blocks enable before any resource acquisition. Unlike the previous implicit
enable-time reconciliation, callers must successfully retry disable first. Failed enable never replaces
pending work. Double/reentrant enable and enable/disable during cleanup remain guarded. Failed startup
revokes provisional fields, independently cleans acquired resources, preserves the exact original
exception and attaches release failures. It never invents a flush obligation, grant or profile save.

Task cancellation calls cancel then inspects isCancelled even if cancel threw. Before-effect refusal,
silent ineffective cancellation and unknown status retain the exact handle. Confirmed after-effect
cancellation releases it while still reporting its exception. There is no cancelTasks(plugin).
Listener unregister is checked against actual registered listener identity; refused ownership remains
retryable, and an after-effect exception can be confirmed released. No unrelated listener is removed.

The scheduler callback retains its service identity and acts only for the same active generation.
RewardListener's package-local predicate does likewise, before event-player access and before flush;
its original public constructor remains an always-authorized direct-use adapter. LOWEST priority is
unchanged. Retired task/listener callbacks cannot flush either pending old work or a replacement runtime.
Arbitrary held service references remain ordinary Java objects, outside module-owned callback authority.

## Save, handoff, fallback and retry are distinct facts

| State | Implemented interpretation |
|---|---|
| Successful save | Existing repository call returned; marker removed; independent file read confirms measured values |
| Failed call before effect | Marker and original cause retained; old file may remain unchanged |
| Failed/uncertain call after effect | Marker/cause retained even though file may already contain the latest aggregate; retry saves again without adding Coins |
| Successful Player unload | Save-before-eviction established owner handoff; strict Reward cleanup removes marker without another write |
| Failed unload | Original aggregate stays loaded; dirty work is still attempted and retained on failure |
| Player fallback saves/clears cache | Original aggregate persisted by owner; Reward can acknowledge that verified existing contract without reconstructing offline data |
| Both owners fail | Original Player cache and Reward service remain pending; getter closure is separate from actual memory retention |
| JVM exits while pending | Unsaved memory can disappear; no journal or general crash recovery exists |

After repairing storage, explicit Reward disable retries only its unresolved UUIDs while the original
Player cache remains usable. Completed B is not reattempted by Reward. The retry never calls grantCoins,
replays a reward or loads stale offline data to calculate money. Player may later save all retained
profiles again; that is aggregate persistence, not another monetary grant. If Player already saved and
cleared, existing save-before-clear handoff is acknowledged without extra writes.

ModuleManager still logs errors, continues ordinary reverse shutdown and clears its enabled list. It
does not schedule this private retry; a second normally completed disableAll does nothing. No built-in
operator retry command or stop veto was added. Code must retain the original modules/owners and use
their public disable paths. Consumers stop before Reward and Player; no unsupported live hot swap.

## Executable coverage and existing tests

New `RewardShutdownFlushHarness` and support-only `RewardShutdownFixture` use the real modules/services,
FilePlayerRepository, actual HandlerLists, executable exact task handles and immutable original
aggregate identities. Repository outcomes can fail before or after the delegate's real file commit;
after-effect tests independently verify committed bytes and unchanged wallet during retry.

Coverage map for the requested matrix:

| Requirements | Executed methods / evidence |
|---|---|
| S1–S3, S23 | normal: no-dirty/no-write, aggregated grants/save once, repeat inert |
| S4–S15, S37–S39 | failureAndRetry / strictFailures: A/B isolation, exact map/causes, immutable failures, dirty retention, getter/pending identity, inactive retry and fresh enable |
| S16–S22 | cancellation / listeners / stale: before/after/no-effect/status unknown, independent flush/release, exact retry and retired-generation rejection |
| S24–S25 | strictFailures: throwing WARNING boundary never enters the strict loop, all UUIDs attempted and original causes preserved |
| S26–S31, S36 | handoff / fallback: real failed/successful unload, actual ModuleManager reverse shutdown, successful/failed Player fallback and explicit original-cache recovery |
| S32 | consumers: actual Quest completion +5 and actual OnlineReward interval +7, consumers stopped before Reward; saved aggregate Coins 12/status/baseline retained |
| S33 | unchanged resource/schema/permission/config production scope plus retained canonical contract harnesses |
| S34–S35, S38 | startup and existing RewardModuleLifecycleHarness: exact startup error, no invented persistence, duplicate/reentrant guards, cleanup-time guards and no acquisition while pending |
| S40 | uncertain: delegate physically commits then throws; marker retained, failure explicitly uncertain, repeat save never adds Coins |

All 203 existing canonical entries remain. RewardModuleLifecycleHarness changes only the failed-flush
dirty assertion (now retained) and uses explicit disable reconciliation before fresh enable in its
refused-task case. Its successful cleanup/order checks remain and it still reports 136 checks.
RewardServiceHarness remains 126 and RewardLifecycleHarness 40. Quest/OnlineReward/DailyQuest/Player
contracts and discovery are unchanged.

Final new harness: **209 behavioral checks**. The complete canonical run executes these with real
temporary profile commits. There are no source-string checks standing in for lifecycle behavior.

`scripts/tests/reward-strict-shutdown.Tests.ps1` passed **15/15 compiled negative controls**. Every
variant compiled with exit 0, executed its intended scenario and failed the named behavioral assertion
with exit 1/AssertionError. Compilation failure is not counted. Variants use a temporary class overlay;
production sources and canonical classes are not replaced. Positive controls passed before and after:
**11 actual harnesses / 1,554 checks in each round**, including the final 209-check new harness.

| Control | Detected mutation |
|---|---|
| N1 | Swallow incomplete strict flush |
| N2 | Clear failed dirty UUIDs |
| N3 | Stop the strict loop after A, skipping B |
| N4 | Lose inactive retry obligation by assigning only the current active flag |
| N5 | Discard the original service while flush remains pending |
| N6 | Permit enable with unresolved previous cleanup |
| N7 | Expose the retired pending service through the getter |
| N8 | Re-grant Coins during retry |
| N9 | Treat silent ineffective cancellation as confirmed release |
| N10 | Skip strict flush when a task remains unresolved |
| N11 | Replace original per-UUID causes with diagnostic exceptions |
| N12 | Disable the borrowed Player owner from Reward cleanup |
| N13 | Falsely treat all pending UUIDs as unloaded handoffs |
| N14 | Let an old timer flush the replacement service |
| N15 | Remove the retired listener's generation predicate |

Receipts and complete per-control compile/behavior/positive logs are saved in external
`negative-controls-final/`, alongside `negative-controls-final.log` and
`negative-controls-summary.json`. The earlier pre-format run is retained separately.

## Native Paper acceptance

Fresh external Paper **1.21.11 build 132**, LuckPerms **5.5.84**, Java 21 and the final built
VapeeCore JAR ran on loopback with disposable worlds, configuration and synthetic profiles. The
operator runtime binaries were read-only copy sources. Tested JAR SHA-256:
`632f2436816396d9cadce09c6a319100cd7e74b4d9177151e38abc240c466d9c`.
Both acceptance compositions used this exact JAR; the final on-disk build hash still agrees.

| Composition | JVM 1 | Separate JVM 2 | Error classification |
|---|---|---|---|
| Clean | 23 checks, exit 0; A=26, B=22 | 25 checks, exit 0; A=35, B=22 | Zero ERROR/SEVERE in both consoles |
| Deliberately faulted, separate fresh data | 20 checks, exit 0; repaired A=11, B=22, permanent C file=0/memory=33 | 11 checks, exit 0; A=11, B=22, C=0 | Exactly four expected incomplete-shutdown diagnostics in JVM 1; zero in JVM 2 |

Totals: **48 clean checks + 31 fault-composition checks, four successful process exits**. Different
runtime tokens and a persisted first-process marker establish separate JVMs rather than plugin reload.
The permanently failed C=33 is deliberately not durable; JVM 2 correctly reads its last committed 0.
The faulted run is not classified as an error-free normal shutdown.

Every JVM checks all **28 exact module activations/order**, **seven reload participants**, one actual
Reward LOWEST listener, one native synchronous 20-tick Reward task and the five base production tasks.
In the clean composition, the existing CraftTask executes its unchanged captured production Runnable
on Paper's main thread. A small observing wrapper calls that Runnable and checks files; an immediate
unchanged repeat makes no save and adds no Coins. Quest grants configured +7, OnlineReward grants +3,
and pending +5 commits at Reward shutdown. The second JVM checks the prior completed Quest status,
DailyQuest cycle and online baseline 1,200, then exercises another timer and shutdown save.

An observing logger handler at the actual ModuleManager reverse-shutdown boundary confirms Quest,
OnlineReward and DailyQuest have retired before Reward, while Player remains usable. It verifies the
Reward commit and exact task/listener release before Paper-wide safety teardown; the complete core
composition has zero remaining handlers/tasks before that teardown. Shutdown does not depend on a
subsequent Reward scheduler tick and does not veto Paper stop.

The fault composition disables only its fresh DailyQuest catalog to avoid an earlier Quest aggregate
save masking the controlled Reward failure. Its test-only delegating repository boundary fails A and C
before the real file write, while B commits independently. After the first actual Reward shutdown,
repairing A and explicitly calling the original module's public disable at the observed boundary saves
A=11 while C remains incomplete. B is not repeated by Reward; original A=11/C=33 wallets are unchanged.
Player's later fallback also reports C incomplete and retains its original cache. A repeated manager
disable schedules no automatic retry. The four expected diagnostics are Reward manager, Player's
per-profile failure, Player incomplete shutdown and Player manager failure. The later clean JVM makes
the last-committed versus lost-memory distinction executable.

These are native API/service/file assertions using directly loaded synthetic profiles and a controlled
test plugin, reflection for observation/fault injection and an observing scheduler wrapper. They do not
exercise a real Minecraft connection, network login/quit or visual client presentation. No real-client
acceptance is claimed, and no unsupported hot reload or live consumer service replacement is used.
Native sources, runner, compile logs, both consoles/receipts per composition, hashes, exact configs and
synthetic profile bytes remain external. Only the two newly created server directories were removed
after both JVM exits, profile-copy hash verification and evidence preservation; `native-cleanup.json`
records the checked paths. Installed server data was never a mutable fixture.

## Canonical verification and operator protection

Final `pwsh -File ./scripts/verify.ps1`: **204 discovered/executed/passed harnesses, 32,814 checks,
Failed=0, Missing/Invalid=0, exit 0; Maven clean package PASS; Spotless PASS**. All **203** baseline
entries remain. The added 213 checks consist of the new harness's **209**, two additional existing
GUI source-audit checks for its two new Java files, and two existing repository-link checks for the
new guide-to-report links. No existing behavioral harness lost checks; the two changed audit counts
are 237 to 239 and 84 to 86. Baseline/final inventories, receipts and a per-entry comparison are saved.
The canonical discovery rules, six-file formatting pilot and Maven configuration remain unchanged.

The pinned 1.22.0/AOSP formatter dry run passes for both new Java files. Final compiled controls ran
after the canonical run against the same production source; source hashes and final native JAR hashes
confirm the tested production did not change. `git diff --check` passes.

Fresh protected inventory before testing contains **934 actual server files** and **nine** separate
`.run`/top-level tool files. Operator worlds/profiles/Coins/Quest/cycles, installed JARs, LuckPerms,
configuration and recovery/migration/conflict evidence are never mutable fixtures. No operator server
or deployment script is run. Native worlds/configs/profiles are fresh and disposable outside checkout.

Final full relative-path/length/SHA-256 inventories agree exactly: **934/934 operator files** and
**9/9 Run/development-tool files**, with **0 added, 0 removed, 0 changed paths** in either protected
set. `operator-before.json`, `operator-after.json`, both protected-tool inventories and
`operator-comparison.json` retain the complete comparison. The new supplemental test script resides
under `scripts/tests/`; existing top-level development and deployment scripts are unchanged.

Evidence root:
`C:\Users\vapee\.codex\visualizations\2026\10\25\01a17432-7597-7ff1-bc9d-34201a027523\phase5i-evidence-20261027`.
It retains original sources/probe observations, baseline/final receipts, formatting evidence, compiled
mutations and positive logs, native sources/runner/console/receipts/configs/synthetic profiles and full
operator comparisons. Generated overlays/server/build files are outside the commit.

Exact intended changed paths:

1. `src/main/java/dev/vapee/core/reward/RewardModule.java`.
2. `src/main/java/dev/vapee/core/reward/RewardService.java`.
3. `src/main/java/dev/vapee/core/reward/RewardListener.java` (reproduced stale owned callback).
4. `src/test/java/dev/vapee/core/reward/RewardShutdownFlushHarness.java`.
5. `src/test/java/dev/vapee/core/reward/RewardShutdownFixture.java`.
6. `src/test/java/dev/vapee/core/reward/RewardModuleLifecycleHarness.java`.
7. `scripts/tests/reward-strict-shutdown.Tests.ps1`.
8. `docs/MODULE_DEVELOPMENT.md`.
9. `docs/PERSISTENCE_DEVELOPMENT.md`.
10. This `reports/architecture-modernization/PHASE_05I_REWARD_STRICT_SHUTDOWN.md`.

Deferred: FilePlayerRepository/SafeFileWriter diagnostic-cause and G5 post-commit immediate Economy
divergence (Phase 5J-A); global ModuleManager reporting/retry policy; permanently refused external
release; scheduler registration throwing before a handle is returned; arbitrary stale public service
references; crash durability/idempotency. No guarantee of every power-loss/filesystem failure surviving
a Java save. No next phase is started. Deliver exactly one local commit
`Harden Reward shutdown flush and persistence retry`; actual SHA/clean status returned separately.
No push, merge, deployment, stash/recovery modification or gameplay change.
