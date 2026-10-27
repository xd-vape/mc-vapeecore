# Phase 5H — Shutdown & Persistence Durability Policy Decision

## Executive decision

Keep reverse dependency order, make incomplete persistence observable, and repair the Reward shutdown
completion signal before designing global retries. Recommend **Phase 5I: Reward strict shutdown-flush
safety** next, then bounded diagnostic non-interference and explicit incomplete-shutdown reporting.
Only after those prerequisites consider opt-in, bounded same-process retries while original dependency
state remains usable. This is **Option B as the primary direction**, preceded by the Reward-local fix;
Option C is conditional and Option D requires separately approved crash/idempotency requirements.

The confirmed Reward defect is bookkeeping/signal loss, not unconditional coin loss: an ordinary failed
Reward save can yield normal module return and cleared dirty work, while Player later commits the same
wallet. If both fail, only the pending original Player cache contains the new value. A second normally
completed `ModuleManager.disableAll()` does not retry it. Logging faults separately cause skipped cleanup,
aborted shutdown loops, masked storage causes and conditional post-commit memory/disk divergence.

**No proposed policy is implemented here. The sole committed artifact is this decision report.**

## Baseline, toolchain and evidence

- Repository `xd-vape/mc-vapeecore`; clean integrated master
  `acf8c4d54e110bfb7c2cb56b133eb5c432f67a0a`; branch `phase/shutdown-policy-audit`.
- Preflight inspected status, HEAD, twelve commits and worktrees. No applicable AGENTS.md was present.
  No stash, recovery branch or bundle was inspected or changed.
- Oracle JDK `21.0.12.1+1-LTS-4`, Maven `3.9.16` from IntelliJ IDEA 2026.2.3, PowerShell `7.6.5`,
  Windows 11. Spotless `2.43.0`, Google Java Format `1.22.0/AOSP` and six-file pilot unchanged.
- Actual baseline: **203 discovered/executed/passed harnesses, 32,601 checks, Failed=0,
  Missing/Invalid=0; Maven clean package PASS; Spotless PASS; exit 0**. No probe executed before success.
- Current README, architecture/module/persistence/dependency/developer/code-style guides and reports
  5B/5C/5E/5F/5G were consulted. Historical intention is not counted as executable proof.
- All production/test/tool/config sources remain unchanged. External probes compile against their
  actual classes and ModuleRuntimeFixture. Reflection installs/observes external fault boundaries;
  it is not used to repair production state.
- Evidence root:
  `C:\Users\vapee\.codex\visualizations\2026\10\25\01a17432-7597-7ff1-bc9d-34201a027523\phase5h-evidence-20261027`.

Categories: **documented** means guides/reports; **source** means current method/installed bytecode;
**existing test** means unchanged canonical coverage; **new observation** means this audit's receipt;
**proposal** means future behavior, not implemented.

## Authoritative references

Java paths below are under `src/main/java/dev/vapee/core/` unless marked otherwise.

| Ref | Actual implementation / executable evidence |
|---|---|
| S1 | `VapeeCore.java:onEnable/onDisable`; `module/CoreModule.java`; `module/ModuleManager.java:enableAll, disableAll, disableEnabledModules, getModules, getEnabledModules` |
| S2 | `reward/RewardModule.java:disable, cleanup, flushSafely, clearRuntime`; `reward/RewardService.java:grantCoins, flushPlayer, flushAll, clearDirtyTracking` |
| S3 | `quest/QuestModule.java:disable, cleanup, sampleOnline, activeRuntime`; `quest/QuestService.java:flushPlayerOrThrow, flushAllOrThrow, FlushFailures`; `quest/QuestPlaytimeProducer.java:sample` |
| S4 | `player/PlayerModule.java:disable, cleanup, CleanupResources`; `player/PlayerService.java:savePlayer, unloadPlayer, saveAll, saveAllOrThrow, SaveFailures, clearLoadedPlayers` |
| S5 | `onlinereward/OnlineRewardModule.java:disable, cancelTask, processSafely`; `quest/daily/DailyQuestModule.java:disable, cleanup, owns` |
| S6 | `player/repository/FilePlayerRepository.java:save, loadDocument, protectMigration, storageFailure`; `player/repository/PlayerFileSchema.java:read, version, overlay, validateOwnedValues` |
| S7 | `persistence/SafeFileWriter.java:write, replace, requireReconciled, cleanup, FileAccess` |
| S8 | `economy/EconomyService.java:persistBalance, addCoins, addCoinsDeferred`; `economy/EconomyModule.java:disable` |
| S9 | Installed `dev-server/versions/1.21.11/paper-1.21.11.jar`, `PaperPluginInstanceManager.disablePlugin` bytecode; API `JavaPlugin.setEnabled` bytecode |
| T1 | `src/test/java/dev/vapee/core/module/ModuleExtensionHarness.java`: 11 synthetic composition checks; not every real module |
| T2 | RewardModuleLifecycleHarness `observeShutdown`, `shutdown`, `flushFailure`; RewardServiceHarness; PlayerModuleLifecycleHarness; QuestModuleLifecycleHarness; PlayerRewardPersistenceHarness; PlayerQuestPersistenceHarness; OnlineRewardModuleLifecycleHarness; DailyQuestModuleLifecycleHarness |
| E1 | External `ShutdownAudit.java`, `SupplementalAudit.java`, compile/run logs; `cases/<scenario>/receipt.json`, exact YAML snapshots; `summary.json`, `supplemental-summary.json`, `cold-process.json` |

## Actual shutdown flow

S1 explicitly registers **28 production modules**. Seven reload participants remain ConfigService,
Localization, Lobby, Chat, PrivateMessage, Presentation and DailyQuest. Reload is not shutdown retry.
The exact reverse order is unchanged:

```text
LobbyExperience → Warp → Blackjack → WorldDisplay → Seat → Utility → Activity → Settings
→ Presentation → PrivateMessage → Chat → Visibility → Lobby → DailyQuest → Quest
→ OnlineReward → Reward → Clan → Presence → Friend → Moderation → Identity → Economy
→ Social → Player → Rank → Permission → Localization
```

Paper disables JavaPlugin; `VapeeCore.onDisable()` calls `moduleManager.disableAll()` then logs
`VapeeCore disabled.`. The manager records a module only after enable returns. A failing module owns
partial startup cleanup. With normally returning logging, startup failure rolls successful predecessors
back and rethrows the original error; predecessor cleanup errors are logged, not attached (H; S1/T1).

With normally returning logging, one RuntimeException during disable does not prevent previous owners
being called. The manager then clears `enabledModules`, resets its flag and logs all modules disabled.
It returns no structured completion result. `getModules()` retains configured objects, including objects
with pending state, while `getEnabledModules()` is empty. Repeating ordinary `disableAll()` calls none
of them (I/F). Configuration membership is not a retry queue.

Logger calls are not generally guarded: per-module INFO failure skips the actual disable inside that
try (G1); its SEVERE failure logger can abort iteration before list/flag clearing (G2). Retained entries
then include already retired modules. The startup rollback diagnostic can mask the original error and
skip rollback entirely (H2). This turns report 5B's explicitly untested logger edge into a new observation.

Production calls the manager shutdown only from root `onDisable`; it invokes no targeted pending retry.
There is no operator retry command, result API or automatic storage repair. Code already holding module
objects can call public `disable()`. That capability is not an existing operator workflow or support
for live plugin/service hot swap. Calling the same manager after ordinary shutdown does not reach it.

After ordinary list clearing the manager flag allows another enable sequence in principle (source S1;
synthetic successful restart in T1). There is no global pending preflight: individual module guards may
reject that sequence, as Player does in I. Earlier modules could already reacquire resources before a
later pending owner rejects startup. This is not an established whole-plugin recovery workflow.

### Exact Paper and JVM boundary

S9 was inspected in installed Paper **1.21.11-132-c5eb079**, not inferred from historical intention.
JavaPlugin changes `isEnabled` before calling `onDisable`. PaperPluginInstanceManager invokes
`setEnabled(false)` at bytecode offset 95, catches Throwable from that call, then proceeds through
classloader closure/unregistration, scheduler/global/async task cancellation, services and HandlerList
unregistration and other teardown. Bytecode excerpts are retained externally. Its own diagnostics
are not assumed infallible under an arbitrarily broken logger.

The void callback and a thrown module exception do not reliably veto server stop. No later scheduler
tick inside a disabled plugin is guaranteed. Paper safety teardown can remove residual registrations,
but cannot save VapeeCore aggregates or discover private pending work. Classloader teardown also rules
out plugin hot reload as an assumed recovery path. JVM exit discards private caches, samples and dirty
work. Separate JVM reads in E1 see only stored data; no hard-kill/power-loss guarantee was established.

## Ownership and existing retry matrix

| Owner | Owned mutable/resource state | Borrowed / persistence owner | Success / failure / existing retry |
|---|---|---|---|
| Player | Repository/index; one aggregate cache, SettingsService, join/quit listener | Config/messages/plugin; **owns authoritative player YAML** | S4 strict loop tries every profile, clears cache only after complete success. UUID/cause errors retain original service privately with getters closed. Public disable retries it; no manager scheduling. Does not need stopped consumers, but only lives in this JVM |
| Reward | Dirty UUIDs, LOWEST quit listener, one 20-tick task | Economy and original Player; no wallet file | S2 cancels, best-effort flushes, unregisters, clears dirty/runtime. Ordinary save failure may return normally with no pending record. Resource errors may retain cleanup, but active=false retry has no new flush obligation. Old service flush cannot discover cleared work (B2) |
| Quest | Registry, dirty UUIDs/service, reporter, samples, listener, 100-tick task; final-sample/flush flags | Original Player/Reward; same profile file | S3 revokes, cancels, samples, strictly flushes and unregisters independently. Failed flush retains exact service and flags. Public disable works with original cache (J), accepts a successful Player save-before-clear handoff (D2), but cannot reconstruct unread sampling after cache clearance (D3) |
| OnlineReward | Service/renderer, 20-tick task, pending cancellation handle | Config/Player/Reward/messages; progress in CorePlayer | S5 revokes/cancels/confirms; **no shutdown grant/save/flush**. Refused known handle remains, callback inert. Disable/enable can reconcile that handle; no automatic manager retry. Resource-only retry after owners stop shown in F |
| DailyQuest | Config/service, two listeners, command pair, menu/views/observers/countdown, 1200-tick sync, cleanup record | Player/Quest/registry/messages/Bukkit | S5 revokes owned effects, preserves ordinary borrowed catalog; **no profile save/dirty clearing**. Exact failed cleanup remains private. Public resource retry shown in F; startup catalog conflict has separate ownership requirements |

An audit reference to an old CorePlayer is not another live authoritative cache. Receipts distinguish
`cacheLoaded` from retained observation objects. A later failed save can occur after a previous full
aggregate commit (G4); failed bookkeeping is not proof of stale disk or permanent loss.

Other source-inspected owners: Economy/Social directly save the same aggregate during operations;
Economy disable removes bindings/runtime, not another wallet. Friend/Clan/Warp and Moderation use
their existing feature mutation persistence and do not provide a second Player shutdown fallback.
Blackjack/Table/Activity close runtime sessions before Player. Their feature data/refund/session failure
policy would need separate experiments if coordination expands. No such financial failure is claimed
tested here. E1 uses six real modules (Player, Economy, Reward, OnlineReward, Quest, DailyQuest), not
a live 28-module server. Their production composition and full existing canonical coverage remain intact.

## New executable failure matrix

**20 scenarios / 92 behavioral assertions**: main probe 17/86, supplemental B2/F2/D3 3/6. The final
separate JVM independently reads **42 stored aggregates**, including Coins/progress/cycle and observed
ACTIVE status. Compile/run exits are zero. PASS means expected current behavior was reproduced, not
that a defective outcome is acceptable.

Disposable fixed UUIDs: A=`65b00000-0000-0000-0000-000000000001`,
B=`65b00000-0000-0000-0000-000000000002`, C=`65b00000-0000-0000-0000-000000000003`.
Baseline has zero Coins, ACTIVE `audit` progress 0 and a real current Europe/Berlin DailyQuest cycle.
Target 10/reward 5 avoids completion. Deferred Rewards are A +11/B +22, or A +11/C +33 in mixed cases.
Quest cases add A progress 1 plus two final sampled minutes (progress 3), B one minute. Mixed cases
instead use B progress 3/C progress 1. D/D2/G2/J grant no Rewards, preventing intervening Reward commits
from invalidating the intended Quest+Player save failure.

Save faults are before writer opening in the actual repository FileAccess seam. Every attempted write
and completed atomic move records phase and UUID. No failed file is reset/deleted to pass a test. G5
is explicitly after commit. Checkpoints preserve exact YAML/SHA-256, raw stored values, memory/status/
cycle/cache membership, dirty sets, getters, pending flags, manager lists/flag, handlers/tasks and logs.

| Case | Attempts / physical commits after baseline | Injected boundary and independently observed result |
|---|---:|---|
| A | 6 / 6 | Quest/Reward/Player save both; A=11 Coins/progress 3, B=22/progress 1; resources zero; cold read agrees |
| B | 5 / 3 | Reward A fails in public and module flush, B succeeds. Public service leaves A dirty; module clears it and returns normally, no Reward pending/boundary error. Player commits A=11/B=22 |
| C | 4 / 2 | Reward and Player A fail; B saves. Disk A=0, original cached memory A=11; Reward dirty gone, Player pending, manager list empty |
| D | 4 / 2 | Final sample generates A progress 3; Quest and Player A saves fail. Disk A progress 0; B progress 1; Quest dirty and both pending owners retained |
| D2 | 4 / 3 | Quest A fails, Player fallback commits progress 3/clears cache. Public Quest retry completes valid handoff without another write or reconstructing state |
| E | 7 / 3 | Reward A, Quest B, Player A/B fail. Disk A Coins 0/B progress 0; C=33 Coins/progress 1 independently committed |
| E2 | 7 / 4 | Same except Player B succeeds: disk B progress 3; A stale, pending Quest/Player remain |
| F | 6 / 6 | DailyQuest listener unregister and OnlineReward cancel refuse before effect; persistence completes. Repeated manager disable does not retry. After explicit external repair, public resource retries work after Player stops |
| G1 | 2 / 2 | INFO throws before Reward disable; Reward getter/task/listener remain despite manager stopped list. Player still commits both wallets |
| G2 | 2 / 1 | Quest A failure then manager SEVERE logger throws; later owners skipped, six entries remain including retired modules, caller sees diagnostic error |
| G3 | 4 / 2 | Reward A fails and its WARNING logger throws; public and shutdown loops never attempt B. Outer module SEVERE log returns; both markers cleared. Player rescues both wallets |
| G4 | 4 / 3 | Final Player A IO failure plus repository storage-error logger failure; strict loop saves B and retains cache, but aggregates diagnostic error instead of original IO cause. A already has 11 from Reward: no demonstrated coin loss |
| G5 | 1 / 1 | Immediate Economy +9 commits, then temp cleanup and WARNING logger fail. Caller gets exception, memory rolls back to 0, independent YAML/cold reader see 9: conditional divergence confirmed |
| H | 4 / 2 | Synthetic later consumer fails before resource acquisition after six real modules/mutations. Manager rolls predecessors back, failed saves leave Player pending. Original consumer exception preserved without attached rollback failures |
| H2 | 0 / 0 | Consumer fails, rollback SEVERE logger throws before rollback; original masked, six predecessors remain, neither wallet committed |
| I | 4 / 2 | C-like failures; second normal disableAll makes zero save attempts. enableAll rejected by Player pending state |
| J | 7 / 5 | D-like failure then deliberate storage repair; public Quest disable commits retained progress 3, public Player disable saves/releases original cache; no private-field recovery or domain replay |
| B2 | 6 / 4 | C-like failure then repair: captured original RewardService flushAll/flushPlayer make zero new attempts (dirty lost); public Player disable commits retained 11 |
| F2 | 4 / 4 | Exact owned Reward task cancel returns without effect. Reward drops handle/pending knowledge without checking status; physical task remains, captured callback inert. Controlled API fault, not ordinary native Paper behavior |
| D3 | 6 / 6 | A statistic read fails before final sampling; existing progress 1 saves and Player clears cache. Repaired late Quest retry releases pending state but cannot sample two unread minutes: disk remains 1. Unsampled-input limitation, not lost generated dirty progress |

B2 includes two repaired Player saves. F final state is after explicit resource retry; its earlier
checkpoint preserves both pending resources. D2/J retain pre-repair snapshots. G5 performs no later
save that could conceal divergence. Fixture safety cleanup only removes controlled registrations/
restores fake Bukkit context; it does not save or repair profiles.

### Precise Reward answers and the Phase 5B coverage gap

1. Yes, a failed save and normal Reward module return coexist (B/C).
2. After public service flush A is still dirty (B).
3. Module cleanup later clears that failed marker (B/C).
4. Original loaded wallet remains 11; dirty loss is not wallet zeroing (C).
5. Player fallback commits 11 when it succeeds (B).
6. If Player also fails, original cache retains 11, disk 0; termination loses unsaved delta (C).
7. Repository/service diagnostics exist, but no failed-return/pending result at Reward module boundary.
8. Captured RewardService survives as Java state but cannot discover cleared work (B2); Player can save
   its original aggregate if retained.
9. Configured module references survive, but no manager unresolved queue/result exists (I).
10. JVM exit removes pending/cache memory; cold reader sees committed old data, not the lost delta.

T2's successful Reward shutdown checks cancellation-before-flush, flush-before-unregister and cleared
dirty tracking. `flushFailure` additionally makes diagnostics throw to test independent resource release,
then demonstrates Player fallback. Those assertions do not prove that an ordinary failed save remains
represented at module shutdown. This audit fills that composite coverage/contract gap; it does not
claim the old successful path regressed or weaken the existing tests.

## Confirmed findings and propagation

| Finding | Evidence | Explicit limit |
|---|---|---|
| Lost Reward dirty work / incomplete completion signal | B/C/B2/G3; S2 | Not every Reward failure loses Coins: earlier/later aggregate commit may save them |
| Ordinary manager retry knowledge discarded | I/F/H; S1 | Objects may remain reachable; reachability is not automatic retry |
| Logger errors change shutdown control flow | G1/G2/H2; S1 | Requires a throwing diagnostic boundary; not asserted normal native behavior |
| Storage cause masked by logger | G4; S6 | Player's own per-UUID logging guards do exist and still isolate saves |
| False failed return after physical commit | G5; S7/S8 | Two injected faults; not arbitrary Economy corruption or power-loss proof |
| Reward cancellation confirmation absent | F2; S2 | Residual callback is inert |
| Original cache/input lifetime limits retries | J succeeds, D3 cannot recover unread input; S3/S4 | Late retry cannot recreate missing gameplay state from old disk |

Ordinary propagation: Reward repository SEVERE/service WARNING normally swallowed by public/module
flush; Quest strict UUID FlushFailures → module aggregate → manager SEVERE; Player strict UUID
SaveFailures/incomplete-shutdown diagnostic → module aggregate → manager SEVERE. Resource failures
have separate suppressed context. Logger fault variants can mask/prevent these paths. Final success-
sounding INFO does not override earlier incomplete outcomes.

Not newly tested: native `/stop` with failed storage, hard kill/power loss/hardware ordering, failing
LuckPerms teardown, unsupported async callers, pending/completed Quest crash replay, Blackjack financial
failure, every other module's refused cleanup, or arbitrary Error/OOM. No duplicate Quest payout was
reproduced. Existing domain/schema/status harnesses are coverage, not a new crash/idempotency proof.
Throwing module-name access and every final root/manager logging boundary are also source-level risks,
not separately counted new experiments; the matrix identifies the exact logging sites actually tested.

## Durability classification and storage limits

These are classification labels, not automatically cumulative guarantees.

| Level | Supported behavior and limitation |
|---|---|
| 1 — In-memory consistency | Normal loaded aggregate holds current state (A/C/D). G5 shows conditional divergence after a committed save plus logging fault and immediate domain rollback |
| 2 — Deferred persistence | Active Reward/Quest dirty UUIDs represent pending work. Reward shutdown can discard it (C); Quest strict failures retain it (D). Online/daily state belongs to same aggregate, no separate journal |
| 3 — Aggregate commit | Normal successful repository return follows validation/replacement; independent cold reads agree. Physical commit can precede a thrown diagnostic (G5), so failed return does not prove no write |
| 4 — Same-process recovery | Requires original modules/cache and usable boundaries: J/B2 Player retry, D2 handoff. D3 unread sample unavailable after owner clears cache. No built-in operator orchestration |
| 5 — After JVM exit | Last valid committed data and preserved migration/recovery evidence can be read/reconciled. Unsaved C/H2 deltas cannot be recovered from private memory. No operation intent log or every-crash-point guarantee |
| 6 — Exactly-once transactions | One profile co-stores wallet/quest/cycle/reward baseline and domain guards matter. No general durable idempotency ID, journal, multi-file transaction or crash replay proof. Save retry alone does not establish this |

S6/S7 serialize before opening candidate, validate owned values/schema/exact text, detect changed
source, preserve allowed unknown fields and exact legacy backups. ATOMIC_MOVE is tried first; only
AtomicMoveNotSupportedException selects fallback. Fallback protects an existing destination without
overwriting recovery evidence, attempts restoration, and retains unresolved copies. requireReconciled
blocks reads/writes until deliberate reconciliation. Future schema/conflicting backups stay blocked.

This is per-file safety, not a transaction across players. No FileChannel.force, explicit fsync,
directory persistence barrier or application journal exists in S7. Close/read-back/atomic namespace
replacement do not prove survival of arbitrary OS/power/storage failure. Recovery copies protect
particular fallback histories, not every unsaved grant. G5 disproves the unqualified cleanup comment
under a throwing logger: treat that as intended behavior needing a bounded diagnostic fix.

## Architecture options

| Dimension | A: current | B: explicit outcomes/diagnostics | C: bounded same-process coordination | D: durable protocol |
|---|---|---|---|---|
| Loss risk | Unsaved memory lost at exit, Reward gap hidden | Same writes, clearer incomplete status | Some transient recovery before owner state disappears; permanent errors remain | Crash recovery only with fully specified durable protocol |
| Save correctness | Player/Quest retain, Reward gap | Honest save/resource/unknown distinction | Strict outcomes and dependency/handoff eligibility required | Atomic intent/commit/replay plus validation required |
| Duplicate rewards | Domain guards, no global crash proof | No new replay | Retry save/cleanup only, never grant/progress replay | Durable IDs and wallet/status reconciliation required |
| Ownership/API | Void CoreModule, local pending | Compatible report addition, original owners | Explicit opt-in contract/exact retained objects; no private-field inference | New authoritative protocol, not a second YAML writer |
| Complexity/maintenance | Low code, high ambiguity | Low/moderate and measurable benefit | Moderate/high lifecycle/attempt/handoff states | High migration/compaction/recovery/version maintenance |
| Operational cost | Interpret logs manually | UUID/module/action/cause summary | Bounded attempts; permanent failures still need operator | Journal storage, backup/replay procedures |
| Testing cost | Composite gaps shown | Logger faults, honest summaries, all owners attempted | Lifetime, partial UUIDs, bounds, no replay, permanent failure | Independent process kills at every boundary, torn writes, migration, replay |
| Compatibility | Existing behavior | Keep order/schema/config/commands | Ordinary-module adapters, explicit opt-in, no reactivation | Explicit version/migration/downgrade policy |
| Hard termination | Unsaved data unavailable | Does not improve stored bytes | Cannot recover memory never committed | Only protocol/filesystem assumptions actually demonstrated |

**B first, with Reward-local correctness prerequisite.** A alone leaves confirmed hidden work. C before
strict outcomes/logging fences would coordinate wrong information or retry after cache destruction.
D is disproportionate without product requirements and cannot be approximated by emergency YAML or
re-granting rewards. Proposed bounded phases follow; none starts automatically.

## Staged future implementation — proposals only

### Phase 5I: Reward strict shutdown-flush safety

Single objective: truthful Reward shutdown completion and retention of exact failed persistence work,
without changing active/public reward behavior or duplicating Player ownership.

Scope: `reward/RewardService.java`, `reward/RewardModule.java`, focused tests, relevant guidance and
a new report. Keep public constructors and public log-and-continue flush APIs, amounts, LOWEST quit
priority, 20-tick cadence, module order and profile schema. No Quest/Player/global manager rewrite.

Factor strict per-player flush and a package-local strict batch: attempt every dirty UUID independently,
preserve original UUID/cause mapping, remove markers only after successful commit/valid handoff. Disable
revokes authority, independently releases task/listener and runs strict flush. Keep unresolved service
and a private **flushPending obligation independently of active**: retries run with active=false and
must still flush. Do not derive retry solely from the former active flag. Getter stays closed; new
enable must not replace unresolved original work. Confirm exact task cancellation/listener removal;
ineffective/unconfirmed release remains represented. No plugin-wide cancellation or borrowed cache
clearing. An unloaded UUID is a handoff only under Player's successful save-before-clear contract.

Acceptance: ordinary A-fails/B-succeeds without throwing logger returns real incomplete result,
retains A dirty/exact service, keeps B committed and wallet 11, releases unrelated resources. Public
disable retry with original owner saves retained values and releases pending state; fresh enable only
after success. Cover simultaneous Player failure, fallback success, startup rollback, reentry, stale
timer, cancellation before/after/no effect, listener refusal, original exception and post-commit errors.

Compiled negative controls: swallowed ordinary flush, dirty clear/failed UUID loss, skipped B,
retry tied to active, pending getter opened, borrowed cache cleared, wrong task/unconfirmed handle
lost, original exception masked, duplicated grant. Native acceptance: fresh Paper/LuckPerms, actual
reverse shutdown before Paper safety cleanup, controlled failed save and original-owner retry, exact
Coins and separate JVM read, zero unexpected errors. No operator data, migration, hot reload or journal.

### Phase 5J-A: Persistence diagnostic non-interference

Single objective: logging cannot change repository commit outcome or replace primary storage failure.
Scope S6 `storageFailure`, S7 cleanup/restoration diagnostics, narrow technical tests/report. Preserve
schema/recovery/write semantics and format; do not change Economy gameplay.

Construct/retain primary storage exceptions before diagnostics; attach logging failures without losing
original cause. Cleanup after successful replacement must not manufacture failed-save return that
triggers Economy/Settings rollback. Still report failed cleanup through bounded diagnostics. Failed
restore retains original plus suppressed restore errors and recovery copy even if logging fails.
Do not turn every exception into success or hide a genuine pre-commit failure.

Acceptance/negative controls: G4 IOException survives and B still attempted; G5 memory/disk agree;
pre-commit error still rolls memory back; combined restore/logger failure preserves backup and causes;
candidate/migration/source-change safety unchanged. Native tests inject disposable storage/diagnostics
around immediate and deferred real mutations plus stop. No new schema or fsync/journal guarantee.
This independent fix is a prerequisite to trusting broader retry outcomes.

### Phase 5J-B: Incomplete reporting and logger-safe manager iteration

Single objective: attempt every intended shutdown operation and expose an honest immutable summary
even when diagnostics fail. Scope ModuleManager/root completion message, small result model if needed,
tests/docs; retain CoreModule compatibility and order. **No automatic retry in this same phase.**

Separate diagnostic exceptions from module calls: INFO failure cannot skip disable, SEVERE failure
cannot skip predecessors, startup rollback preserves original startup error with independent cleanup
and diagnostic context. Summary distinguishes retired callbacks, unresolved resources, incomplete saves/
UUIDs and unknown outcome. Retain relevant failed module references/outcome metadata until explicit
acknowledgment or exit; this is not another profile cache. Define how strict participants expose facts;
do not parse logs or read private fields in production.

Acceptance/negative controls: G1/G2/H2 cannot change call order; H preserves startup plus rollback
failures; repeated shutdown behavior explicitly specified without duplicated work; successful calls
idempotent; all 28 order/seven reload participants unchanged. Native real root/Paper disable must expose
summary before teardown and still allow Paper to stop. No invented operator command without approval.

### Phase 5J-C: Optional bounded same-process coordination

Single objective: a small explicit persistence/cleanup retry budget over original state while its
owner is usable. Only after 5I/5J-A/B. Scope proposed opt-in outcome/retry contract, coordinator and
explicitly participating Player/Reward/Quest/OnlineReward/DailyQuest owners. Others keep compatible
ordinary one-pass disable. No generic resource framework or implicit all-module reactivation.

Preserve reverse dependency order. A failed consumer may receive an additional immediate synchronous
attempt **before** its borrowed owner stops. Budget at most two attempts per eligible obligation per
shutdown invocation, no sleep and no next scheduled tick. Count bounds do not bound one blocking IO
call; document that limitation rather than promise a hard stop deadline. Saves serialize the same
current aggregate; never replay grants/progress/selection to repair persistence. Resource-only refusal
must not skip an independent persistence owner. After Player save-and-clear, only a proven handoff is
eligible; do not call unavailable sampling recovery (D3). If Player stays pending, its original cache
may support consumer-first retry then Player retry (J), through explicit participant contracts.
Permanent failure produces incomplete summary, not stop veto or durable private memory.

Acceptance/negative controls: A–J/B2/F2/D3; early versus late sampling, shared cache identity, partial
UUID successes, repeated permanent errors, no replay/retry after dependency replacement, attempt bounds,
no worker thread/tick dependency and composition order. Native actual isolated shutdown must inspect
timing before owner state clears, transient/permanent faults and independent cold read. No migration
or durable journal in this phase.

### Phase 5K: Crash durability and reward idempotency — requirements gated

Only if approved, a separate objective: crash-recoverable operation protocol with explicit durable
identity/replay rules. First specify crash versus power-loss assumptions, acceptable loss window,
performance budget and participating domains; then intent/commit ordering, journal/aggregate authority,
operation IDs, versioning, compaction and reconciliation.

Potential scope includes Reward/Quest operation boundaries, Player repository and new versioned protocol.
It is **not authorized or designed down to code here**. Acceptance requires independent JVM kills at
every protocol boundary, duplicate replay, REWARD_PENDING/COMPLETED transitions, torn/partial writes,
unsupported/corrupt versions, disk-full, migration rollback and preservation of recovery evidence.
Native Paper verifies integration, not hardware crash semantics. Exactly-once is bounded by proven
idempotent protocol; external effects, multiple files and power loss remain explicit assumptions.
No silent downgrade or automatic deletion of profiles/backups.

## Operator guidance: present capability versus recommended policy

Current action is diagnosis/deliberate storage repair, not a new command. Capture affected UUIDs,
module/action, original/suppressed exceptions, last save boundary, paths, schema and recovery/migration
markers. Preserve exact bytes/hashes/logs within trusted incident evidence; do not unnecessarily expose
real player data.

Proposed WARNING: recoverable active-runtime periodic save/sample failure with retained work/known
later opportunity. Proposed SEVERE: incomplete final aggregate save, failed restoration, uncertain
shutdown persistence or skipped persistence owner. Report resource failures separately from failed
profile commits. Include exact failed UUIDs, resource/module context and original causes. Final disabled
INFO must not override an incomplete result.

If JVM/original owners still exist, repair capacity/access/IO or deliberately reconcile evidence before
discretionary termination where operationally possible. Avoid a restart that discards the sole unsaved
cache, but do not claim VapeeCore can prevent stop already in progress. Public module retries require
trusted code retaining original objects; there is no current console workflow. Do not enable replacement
Player/Quest services or unsupported plugin hot swap over unresolved state.

Never delete recovery markers, migration backups, future-schema files, bundles or sync-conflict evidence
to bypass validation. Never reconstruct wallet from old disk and re-grant without proven operation
identity. Do not flush asynchronously against shutting-down services, schedule after onDisable, sleep/
loop without bounds or keep JVM alive indefinitely. An emergency second YAML dump bypassing repository
validation is not a crash protocol.

## Verification, native limits, protection and sole deliverable

Final canonical `scripts/verify.ps1`: **203 discovered/executed/passed harnesses, 32,601 checks, Failed=0, Missing/Invalid=0; Maven clean package PASS; Spotless PASS; exit 0**. All 203 baseline entries retain the exact same check counts/status/exit results; **delta 0**. Full per-entry comparison is retained in `verification-comparison.json`. No production/test change was needed or permitted.

No new native server was started for this report-only audit. Exact installed platform bytecode was
inspected; external probes use real HandlerLists/modules/services and controlled scheduler/actor IO.
Separate JVM runs read stored data (initial 36, final 42 aggregates). This proves the measured repository/
process boundary, not native `/stop` with IO failure, client behavior or power-loss durability. Phase 5G
native results are historical context, not new Phase 5H evidence. Future native requirements are above.

Fresh inventory: **934 installed server files** by complete relative path/length/SHA-256, plus **nine**
protected `.run`/top-level development-tool files. Worlds, real profiles/coins/quests/cycles, installed
JARs, LuckPerms/config and migration/recovery/conflict evidence remain operator-owned. Probes use new
external directories; no deploy/operator start/stop script runs. All originally tracked hashes were
captured before experiments to prove production/test/config/guide/tool preservation.

Final comparison after all experiments and final verifier: **934 before / 934 after; zero changed, added or missing paths/lengths/SHA-256 values; nine protected tool/run files before and after, zero differences**. Every originally tracked source/test/config/guide/tool hash is unchanged. All 42 case files also match their final measured SHA-256 after independent cold reads. `git diff --check` passes; the sole repository change is this new report.

Evidence retains probe source revisions, compile logs, 20 receipts, per-boundary YAML/SHA snapshots,
write/physical-commit records, logs/exception trees, canonical inventories/comparisons, separate JVM
results and Paper/API bytecode. PASS means a correctly reproduced observation; failed storage outcomes
are preserved, not silently reset.

Exactly one intended changed path:
`reports/architecture-modernization/PHASE_05H_SHUTDOWN_POLICY_DECISION.md`.
No guide/historical report, production, canonical test/runner, build/deploy tool, IDE/operator file,
schema, command, permission or feature default change. Deliver one local commit
`Document shutdown and persistence durability policy`; actual full SHA/clean state returned separately.
No push, merge, deployment, stash change or automatically started Phase 5I.
