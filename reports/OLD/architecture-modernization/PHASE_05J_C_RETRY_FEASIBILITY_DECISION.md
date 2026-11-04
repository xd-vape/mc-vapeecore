# Phase 5J-C — Bounded Retry Feasibility & Modernization Closure Decision

## Executive decision

**NO-GO for centralized automatic shutdown retries. Keep Option A and CLOSE architecture
modernization with the limitations below. Close Phase 5J-C after this report; do not start another
analysis or implementation phase. The next action is disposable-server acceptance with real players.**

A bounded retry has demonstrable, operation-specific benefits: a second final Player save can rescue
an injected one-attempt failure, and a second early Quest statistic read can recover an initially
unread minute. These are real observations, not grounds for claiming retries never help. However,
the existing owners already implement these explicit operations. Reward's measured transient failure
is already rescued by Player's later aggregate save; central retry adds a redundant commit. Resource
retries release inert handles earlier, while Paper also performs plugin-wide safety teardown.

There is no evidence that a new central protocol is necessary for current operating requirements or
that its classification, lifetime and partial-effect costs are justified. The manager's call statuses
cannot supply that protocol. A blanket second disable of any module that threw is not a safe general
policy; a deferred sweep is particularly unsuitable. Permanent storage faults, ownership conflicts
and unresolved recovery markers do not become solvable by immediately repeating the same call.

This decision does not promise lossless shutdown under arbitrary storage failure. It accepts the
documented synchronous/deferred persistence model, truthful incomplete reporting and explicit owner
recovery while original state remains usable. No production, existing test or tooling change is made.

## Baseline, toolchain and audit scope

- Repository: `xd-vape/mc-vapeecore`; observed SSH origin matches. Clean integrated `master` and
  `origin/master`: **`8ab5d6d954eafc209be572896852bc9fb58aa31e`**. Status, branch, HEAD, twelve
  commits, worktrees and refs were inspected before creating `phase/retry-feasibility-decision`.
  One existing worktree; no applicable AGENTS.md found in the checkout or ancestors.
- Baseline canonical `pwsh -File ./scripts/verify.ps1`: **207 discovered/executed/passed harnesses,
  33,334 checks, Failed=0, Missing/Invalid=0, exit 0; Maven clean package PASS; Spotless PASS**.
  Executable experiments began only after this result.
- Oracle JDK **21.0.12.1+1-LTS-4**, Maven **3.9.16** bundled with IntelliJ IDEA 2026.2.3,
  PowerShell **7.6.5**, Windows 11 amd64. Spotless **2.43.0**, GJF **1.22.0/AOSP**, six-file pilot
  and discovery rules unchanged. Process-local JAVA_HOME, no installation or environment changes.
- Reviewed README; ARCHITECTURE, MODULE_DEVELOPMENT, PERSISTENCE_DEVELOPMENT,
  SERVICE_DEPENDENCIES, DEVELOPER_GUIDE lifecycle/domain/build guidance and CODE_STYLE; completed
  reports 5C, 5E, 5F, 5G, 5H, 5I, 5J-A and 5J-B. Historical proposals are context, not current behavior.
- Authoritative source inspection: ModuleManager, ShutdownReport, CoreModule, VapeeCore;
  PlayerModule/PlayerService; RewardModule/RewardService/RewardListener;
  QuestModule/QuestService/QuestPlaytimeProducer/QuestProgressReporter;
  OnlineRewardModule; DailyQuestModule/DailyQuestMenu; FilePlayerRepository/SafeFileWriter.
  Existing ModuleShutdownReportingHarness, ModuleExtensionHarness, PlayerModuleLifecycleHarness,
  RewardShutdownFlushHarness, QuestModuleLifecycleHarness and persistence integration fixtures/tests
  were inspected and executed unchanged by canonical verification.

**The user-approved 5J-B restart contract is preserved.** Ordinary cleanup RuntimeExceptions do not
create a global restart ban. An explicit `enableAll()` may contact original owners, whose pending
guards remain authoritative. Unknown/interrupted cleanup has its existing separate acquisition guard.
Neither this audit nor a suggested future contract bypasses those guards or promises arbitrary hot swap.

## Current lifecycle and persistence guarantees

The real manager registers exact instances with stable validated names, enables in explicit order,
records successful enables only, and cleans them in reverse. A failed module owns its partial startup;
the manager rolls back previously successful predecessors. Ordinary diagnostic RuntimeExceptions
cannot skip callbacks or mask original startup errors. Independent ordinary cleanup failures do not
skip remaining predecessors. Fatal Errors propagate; publication in finally is best effort, not an
OOM/fatal-JVM recovery guarantee.

Ordinary shutdown/rollback clears enabled bookkeeping, preserves immutable report snapshots and
historical incomplete identities, and performs no automatic retry. Repeated `disableAll()` with no
enabled modules is a true no-op. Direct successful owner retries do not retroactively rewrite an
earlier manager report. A clean later lifecycle has its own result while historical failures remain.
Root distinguishes normal returns, diagnostics, rollback and incomplete shutdown without vetoing stop.

Player owns the one loaded CorePlayer cache and file persistence. Save-before-eviction and strict
save-all-before-clear are the handoff contracts. Reward and Quest hold dirty UUIDs, not copied wallets
or profiles. Their strict cleanup attempts independent UUIDs and retains failures against the original
PlayerService. Every save serializes the current whole aggregate, including Coins, progress, settings,
online baseline and daily cycle; a later save cannot restore a stale per-domain snapshot.

FilePlayerRepository owns validation, schema 1/legacy 0, unknown-field preservation, exact migration
backups, candidate validation, source-change checks and post-success indexing. SafeFileWriter owns
candidate writing/validation, atomic replacement and unsupported-atomic fallback with protected recovery
evidence. Unresolved recovery, future schema and conflicting migration backups continue to refuse IO.
5J-A prevents cleanup diagnostics from manufacturing failed returns after confirmed replacement and
preserves original storage/restore causes. It does not resolve uncertain effectful IO or promise fsync.

## What manager reports allow a coordinator to infer

| Status / API | Exact knowledge | What remains unknown |
|---|---|---|
| RETURNED_NORMALLY | This callback returned normally | Actual file contents, private incomplete work, physical resources, durability or unsampled input |
| THREW_EXCEPTION | Exact ordinary RuntimeException and module identity/name | Before/after effect, current pending work, which operation is safe, whether release already happened |
| NOT_ATTEMPTED | Expected callback was not entered in this cleanup | Whether attempting it now is appropriate after an interruption and changed dependency lifetimes |
| OUTCOME_UNKNOWN | Entered without observed normal return or ordinary exception | Whether any/all effects completed; no failed-before-effect assumption is valid |
| Historical incomplete outcomes | Latest incomplete observation per exact registered identity, bounded by membership | Live pending state or authority to retry; direct repair is not automatically reflected here |
| Immutable snapshot | Stable copied outcome/diagnostic lists and original references | Deep immutability of module/Throwable objects, profile snapshots or transaction receipts |

CoreModule exposes only name, enable and disable. There is no typed resource/save/sample classification,
dependency availability query, public dirty UUID map or confirmation token. Startup failure may also
leave the failed module's own partial cleanup outside the predecessor report. Read-only report access
does not save, sample, grant or acquire anything. A production coordinator must not copy this audit's
test reflection, parse log messages, inspect suppressed-message text for UUIDs or turn history into a queue.

## Full composition and last usable dependency boundaries

The unchanged complete production shutdown order, from VapeeCore.onEnable registration, is:

```text
 1 LobbyExperience    2 Warp             3 Blackjack       4 WorldDisplay
 5 Seat               6 Utility          7 Activity        8 Settings
 9 Presentation      10 PrivateMessage  11 Chat           12 Visibility
13 Lobby             14 DailyQuest      15 Quest          16 OnlineReward
17 Reward            18 Clan            19 Presence       20 Friend
21 Moderation        22 Identity        23 Economy        24 Social
25 Player            26 Rank            27 Permission     28 Localization
```

All seven reload participants remain ConfigService, Localization, Lobby, Chat, PrivateMessage,
Presentation and DailyQuest. Reload prepares/applies/rolls back config; it is not cleanup retry.

| Owner at shutdown position | Needed during pending operation | Last supported boundary / deferred risk |
|---|---|---|
| DailyQuest 14 | Exact menu/task/listener/command APIs; observer removal against original QuestService and Localization; registry ownership for failed-start catalog restore | Immediate retry before Quest 15 retains intended lifetime. Ordinary resource-only retries may still remove old subscriptions after owners stop, but must never sync/rotate or recreate services. Failed-start catalog conflict requires explicit ownership reconciliation, not a late overwrite |
| Quest 15 | Original PlayerService plus online roster/statistics for final sampling; original RewardService/Economy for possible completion grants; repository for flush | Final sampling must precede Reward 17, with its borrowed grant path and Player 25 usable. Flush-only work requires the original loaded aggregate or verified save-before-clear handoff. After Player clears, unread statistics cannot be recovered |
| OnlineReward 16 | Exact BukkitTask handle/status only for cleanup | Its cleanup has no Player/Reward call. Resource-only retry can remain possible after those owners stop while scheduler/handle is usable; its old callback stays inert |
| Reward 17 | Original PlayerService/cache and repository for dirty flush; scheduler/HandlerLists for resource release | Flush uses Player, not a new Economy grant. Immediate before Clan 18 keeps dependencies. After Player 25, a verified successful save-and-clear can acknowledge handoff; retained Java reference alone cannot prove it |
| Player 25 | Original repository/cache and listener API; plugin logger | No stopped consumer is required for profile saves. Retry can work after preceding consumer shutdown while same JVM/cache/filesystem remain; no next tick or eventual operator repair during normal stop is promised |

Economy 23 and Social 24 are actual intervening predecessors of Player; no consumer may disable them
early. A saved service reference does not extend supported ownership. In a controlled late experiment,
Quest can still call its captured retired Reward/Economy objects while Player remains loaded and commit
7 Coins. This shows why mere Java callability is a poor eligibility test; it does not authorize that
sequence. When Player has saved/cleared, the same final sample is skipped and zero progress is recovered.

## Retry ownership matrix: exact retained state and public entry points

| Owner | Owns | Borrows / actual persistence owner | Retained pending references | Public explicit cleanup |
|---|---|---|---|---|
| DailyQuest | Config, DailyQuestService, join/menu listeners, menu, exact command bindings, 1200-tick task, startup catalog installation | Player, QuestService/registry, Messages/Localization, Bukkit descriptor; profile persistence belongs to Quest/Reward/Player | Retired RuntimeResources with exact task/listeners/menu/command/executor, config/service/players/registry and previous/installed maps; menu keeps exact observer callbacks, cancellation handle and pending flags | Same DailyQuestModule.disable(); normal disable does not sample, sync, save, clear borrowed catalog or award |
| Quest | Registry, QuestService dirty UUIDs, reporter, producer/sample map, listener, 100-tick task | Player, Reward, messages, Bukkit; FilePlayerRepository writes Player's aggregate | Original RuntimeResources/service/reporter/producer/registry/listener/task; finalSamplePending, flushPending, samplesReleased, exact cause; service retains original borrowed relationships | Same QuestModule.disable(); public QuestService flush methods retain log-and-continue semantics and are not strict completion receipts |
| OnlineReward | Service, renderer, one 20-tick processing task | Config, Player, Reward, messages; aggregate persistence through Reward/Player | Only exact pendingCancellation handle explicitly retained by module; the scheduler's Runnable may indirectly retain old captured service/renderer until released | Same OnlineRewardModule.disable(); enable also reconciles pending cancellation before acquiring anything |
| Reward | RewardService dirty UUIDs, LOWEST quit listener, one 20-tick flush task | Original Player/Economy; Player repository owns files | Exact remaining task/listener/service, flushPending independent of active, exact failure; service preserves original Player/Economy refs | Same RewardModule.disable(); captured public service flushes are not a coordinator eligibility/status API |
| Player | FilePlayerRepository/index, one PlayerService/cache, SettingsService, listener | Config/messages/Bukkit; Player itself owns profile persistence | Original PlayerService with same CorePlayer objects and/or exact listener plus failure; released runtime getters remain closed | Same PlayerModule.disable(); strict save-all is package-local, no operator retry command |

These are references to retired existing state, not a second cache. Public getters remain unavailable
after retirement. Reward/Quest/Player/DailyQuest pending guards prevent replacement. OnlineReward
reconciles its exact handle before new acquisition. All module-managed old callbacks are generation
inert; explicit direct calls on deliberately retained service objects are not globally revocable.

### Operation-specific safety, confirmation and partial effects

| Operation | Safe supported repetition and limits | Partial effects / confirmation / duplicate prevention |
|---|---|---|
| Known task cancel: all four task-owning modules | Repeat exact unconfirmed handle, while scheduler usable; no new task, no gameplay mutation | cancel may refuse, do nothing or throw after cancellation. isCancelled confirms when readable; confirmed release drops handle even if error remains visible. Unknown/unreturned handle cannot be invented. Generation gates prevent stale work |
| Exact Bukkit listener removal | Retry exact remaining owned listener, preserving foreign listeners | HandlerLists confirm removal for Reward/Quest/DailyQuest; Player conservatively retains on exception. A physically registered inert listener still costs registry/memory/dispatch and should be released, but has no retired gameplay authority |
| Daily menu observer/command cleanup | Remove exact tokens/bindings; retry only retained work, preserving external replacements | One unsubscribe/setter may succeed before another fails. Menu keeps possible subscriptions until successful remove; command read-back checks exact ownership. Countdown tokens retire first. No assignment or payout replay |
| Daily failed-start catalog restoration | Only while original exact installed map is still owned, or prior map untouched | External replacement must survive. A retry cannot resolve foreign ownership merely by repeating; normal shutdown deliberately preserves catalog |
| Daily view close | Best-effort detach and close own currently shown views | Close IO may fail; authority/active bindings detach first. Physical close is not a persistent retry queue. Re-running module cleanup cannot promise to close a view already forgotten |
| Reward dirty profile flush | Save original current aggregate; successful UUIDs removed, failed UUIDs retained; acknowledge genuine Player save-before-clear handoff | File may already have committed before exception. Another save can repeat physical IO, never grantCoins. No writes for already clean Reward UUIDs. Recovery/schema safeguards still apply |
| Quest dirty profile flush | Same aggregate rule, no progress signal or grant replay to repair a save | Removes marker after successful save, then notifies. Observer/diagnostic errors can be after-effect; report exception alone does not establish dirty work. Player can later persist generated progress |
| Quest final sampling | A before-statistic failure can be retried early using retained baseline and original owners | Baseline advances before report. Same tick prevents replay but cannot recover a report that failed before applying progress. Report may have applied partial progress/rewards. Completed/pending states prevent ordinary repeats, not universal uncertain-grant idempotency |
| Player strict profile save-all | Repeat original retained cache after reconciliation/recovery; no domain mutation or stopped consumer needed | Batch is not atomic: B may already be committed when A fails. Retry saves B again. Clear only after entire strict loop succeeds, including deferred diagnostic result. A diagnostic-only failure may retain fully committed profiles |
| Unknown callback outcome | No generic safe repetition | OUTCOME_UNKNOWN cannot distinguish before/after effect; needs owner-specific confirmation. Fatal interruption is not an ordinary transient retry class |

During Paper shutdown, known synchronous cleanup can run inside onDisable while its specific APIs and
dependencies remain usable. After plugin teardown, classloader closure and JVM exit, this is not an
operational recovery interface. No in-memory pending operation remains retryable after JVM termination;
only stored valid files and protected recovery/migration evidence survive for deliberate reconciliation.

## New executable experiments

External sources reuse unchanged ModuleRuntimeFixture, RewardShutdownFixture and QuestLifecycleFixture,
real ModuleManager/PlayerModule/RewardModule/QuestModule, actual Bukkit HandlerLists and executable
captured task callbacks. Real FilePlayerRepository writes disposable profiles; existing delegating
PlayerRepository seams inject before-save and after-return faults. No production class overlay,
coordinator, fixture cache reset or test cleanup substitutes for the behavior being measured.

**17 cases / 94 assertions:** RetryFeasibilityProbe **11 / 69**, QuestRetryFeasibilityProbe **6 / 25**;
both compile and exit 0. A separate ColdRetryReader JVM checks **29 saved profile snapshots / 36
wallet-and-Quest assertions**, raw YAML against fresh FilePlayerRepository reads, exit 0. Total
**130 supplemental assertions**, distinct from canonical totals. No setup failure is counted as a
behavioral result. Fixture safety cleanup occurs after receipts/profile copies; observations are
captured before any later cleanup or optional Player save. Commit counts below mean completed real
repository saves observed at the existing delegate seam (or the fault-free Quest save path), with
independent persisted-value checks; they are not an instrumented count of every NIO syscall or fsync.

Reward fixture UUIDs: A=`65c00000-0000-0000-0000-000000000001`,
B=`65c00000-0000-0000-0000-000000000002`. Quest fixture UUIDs are generated and recorded in receipts.
Each case uses new original owners and disposable files. Identity strings, original exception checks,
call/attempt order, pending/dirty observations, byte snapshots and independent stored values are
retained externally. Reflection is fixture-only observation/injection, not a proposed production API.

For F1/F2, actual manager composition is Player, Economy, Reward and reverse cleanup Reward → Economy
→ Player. The extra original Reward.disable is invoked by a test observer at the pre-Economy boundary,
after Reward's first call has returned/thrown and before Economy.disable actually runs. This models
one immediate attempt without implementing a manager retry policy. Recovery is explicitly controlled
to occur on the next A save opportunity; it is not measured spontaneous OS/storage recovery.

| Case | Save attempts / physical commits after fixture initialization | Observed result and interpretation |
|---|---:|---|
| F1, current policy | 4 / 3 | Reward A fails before delegate, B saves; next A attempt at Player succeeds. File A=11/B=22; original A wallet 11. Reward remains historically THREW and locally dirty despite successful Player handoff |
| F1, one immediate Reward retry | 5 / 4 | Retry saves only dirty A before Economy; Player later saves both. Same A=11/B=22, no re-grant. Earlier commit and cleared Reward pending, **one additional write**, no improved final file over fallback |
| F2, permanent, current | 4 / 2 | Reward and Player A fail; B commits twice. File A=0/B=22, original A memory 11 and both owners pending |
| F2, permanent, immediate | 5 / 2 | Extra A attempt also fails. Same stale A, valid B and retained original state. No data improvement; exact additional exception is recorded separately |
| F6, final Player only, current | 2 / 1 | Reward clean; direct deferred wallet values A=11/B=22. Player A fails, B saves; file A=0/B=22, original loaded A retained |
| F6, explicit original Player retry | 4 / 3 | Recovered A saves to 11, B re-saves 22 byte-for-byte unchanged; original wallet stays 11; cache clears. This is a **real final-state benefit beyond later fallback**, using an existing local entry point |
| F4, ineffective task cancel then retry | 1 / 1 at checkpoint | First Reward stop commits 11, task remains physically uncancelled but inert. Second exact cleanup confirms cancellation; no repeated flush or Coins. Paper safety cleanup is a separate later protection |
| F5, listener refuses then retry | 1 / 1 at checkpoint | Actual HandlerList retains one inert owned handler. Second exact removal succeeds; no repeated completed flush. Physical release removes residual registry state |
| F7, save completes then throws | 2 / 2 | Real delegated save commits A=11 then throws exact injected error; dirty retained. Retry commits 11 again, wallet unchanged. Failed return is not evidence of absent commit |
| F7, cancellation completes then throws | 0 / 0 | Error remains visible, isCancelled=true confirms release, no pending record; subsequent disable performs no second cancel |
| F8, after-effect fatal interruption | No persistence operation | Controlled CoreModule increments an external counter then throws Error. Manager records UNKNOWN for it and NOT_ATTEMPTED for dependency. Repeat manager stop has no extra effect and fresh acquisition is rejected; unknown is not treated as failed-before-effect |
| F3, early unread statistic | 3 / 3 | A read fails, B completes/persists 7 Coins. Early original Quest retry reads A successfully, persists A=7 and leaves B=7. A saves twice including initial assignment; B once. No repeated B progress/reward |
| F3, deferred after Player cleared | 3 / 3 before late retry; 0 additional | Quest read failed before baseline, assignment 0 persisted; real Reward/Economy/Player then stop. Later Quest retry returns and clears pending but cannot process unloaded A. Stored progress/Coins remain 0 |
| F3, producer report fails before effect | 1 / 1 | Producer consumes 0→1200 baseline then controlled report throws before real Quest mutation. Same-statistic retry makes no second report; persisted progress 0. Repeating sample cannot recover consumed input |
| F3, producer report fails after progress | 1 / 1 | Real QuestService applies progress 1 then controlled callback throws. Same-statistic retry does not repeat; stored progress 1, Coins 0. Caller exception cannot distinguish this from prior row without operation knowledge |
| F7, uncertain Domain grant | 2 / 2 | At existing package-local RewardGranter test seam, real +7 grant occurs then injected acknowledgment failure. Quest persists REWARD_PENDING/7. Explicit grant retry then persists COMPLETED/14. Same quest reason is not an idempotency identifier |
| F3, captured retired Reward reference | 2 / 2 at checkpoint | Quest read initially fails; actual Reward/Economy retire; original Player remains loaded. Late Quest retry still calls captured old services, grants/flushes 7. Java callability does not establish supported lifetime or live Reward ownership |

The last two rows are **controlled boundary/counterfactual experiments**, not a claim that normal
production RewardService spontaneously throws after adding Coins or that production runs a deferred
Quest retry. No duplicate payout is attributed to ordinary supported module cleanup. Current normal
final sampling does not call retryPendingRewards merely to make persistence succeed. The grant probe
shows why replaying uncertain domain operations would need a stronger contract than a cleanup status.

F4/F5 deliberately use a held old public RewardService to add a further +3 after retirement, solely
to make stale callbacks observable. Captured task and quit callbacks perform zero saves; resource-only
retry does not revive that service or flush this new caller-created work. Checkpoint file stays 11,
memory 14; a later explicit Player save is outside those checkpoint receipts. This is not a retry
duplicating a grant or an assertion that arbitrary external Java calls can be revoked.

F2's extra error is captured by the external observer rather than logged as a second manager failure.
Measured SEVERE records are five in both variants, not a claimed measured log increase. A production
retry policy would need to report attempt/result separately while retaining the original error; blindly
printing another full permanent-error tree adds volume, not a diagnosis or repair. F1 likewise keeps
the first historical THREW result even after the externally invoked successful owner retry.

### Existing executable coverage and unconfirmed cases

Canonical execution retains ModuleShutdownReporting 150, ModuleExtension 11, PlayerModuleLifecycle
204, RewardShutdownFlush 209, QuestModuleLifecycle 271, OnlineRewardModuleLifecycle 171,
DailyQuestModuleLifecycle 354, DailyQuestMenuLifecycle 133, SafeFileWriterDiagnostic 129 and
PlayerPersistenceDiagnostic 233 checks, alongside all other harnesses. These cover independent
cleanup, actual owner guards, original aggregate identity, schema/recovery refusal, task/listener
before/after effects, reload, observer ownership and persistence boundaries.

Completed phases recorded compiled controls: Player 10, OnlineReward 10, Quest 12, DailyQuest 15,
Reward strict 15, persistence diagnostics 14 and ModuleManager reporting 16. These are reviewed
historical results, **not newly rerun mutation counts in 5J-C**. No assertion or discovery rule is weakened.

**NOT TESTED in this phase:** new native fault-injected Paper runs; actual connected Minecraft clients;
spontaneous real storage recovery probability within two adjacent synchronous calls; disk-full recovery
without intervention; real power loss/hardware ordering; hard kills at every commit/grant boundary;
arbitrary JVM Error/OOM recovery; every module's private cleanup and all unsupported external callers.
The 17 probes use selected real owner compositions, not a new all-28-module native run. This is a
bounded decision audit, not proof of universal lifecycle safety or exactly-once business operations.

## Immediate versus deferred retry

One immediate retry can preserve a still-valid dependency window. F1, F3 and F6 demonstrate different
possible benefits: earlier redundant write, recovery of readable-but-unsampled input, and an improved
final profile. F4/F5 improve physical release while callback authority was already revoked. Existing
local cleanup tracks completed substeps, so confirmed resources and successful dirty UUIDs need not
be retried. Player is different: its strict retry repeats all profiles in the retained cache.

A two-attempt limit bounds calls, **not wall-clock IO latency**. Immediate repetition does not fix
permanent access, storage capacity, future schemas, backup conflicts or broken APIs. No sleep, polling,
asynchronous writer, delayed tick, shutdown veto or waiting for operator intervention is appropriate.
The audit measures controlled next-attempt recovery; it does not establish its frequency or likely
benefit during ordinary Paper stop. No unsupported numerical reliability improvement is claimed.

A global sweep after all 28 stops loses consumer lifetimes. Quest may skip unread input after cache
clear, or call retired direct service objects if some original Player cache remains; neither is a
general recovery protocol. Reward may acknowledge an already successful Player handoff, which repairs
bookkeeping without improving stored bytes, or require still-loaded original state if Player failed.
Player and known task cancellation can remain locally retryable, but those exceptions do not authorize
the same deferred rule for all modules. Stop after JVM termination cannot retry any retained memory.

## Options A–D and maintenance cost

| Option | Benefit | Cost / risk | Recommendation |
|---|---|---|---|
| A — current owner policy | Strict independent persistence; explicit original-owner retries; immutable honest reports; Player fallback; no new domain replay | Accepted loss of unresolved memory at exit; no automatic final-save rescue or console retry workflow | **Keep** for current requirements; diagnose actual incomplete results and preserve evidence |
| B — one immediate module retry | Measured F6 improvement and early F3/resource recovery; dependencies can remain alive | CoreModule cannot classify pending work. 28 arbitrary disables and mixed sample/save/resource obligations are not universally idempotent. Can duplicate IO, hit uncertain effects, obscure history or add blocking latency | **NO-GO** as a blanket manager policy; technical feasibility for selected operations is insufficient |
| C — optional owner-approved operation retry | Could restrict coordinator to exact safe operations while dependencies live | New owner API/state model, eligibility queries, invalidation, attempt/history accounting, five owner integrations, partial-effect and native tests; current report is not this contract | No implementation now. Requirements below are conditional prerequisites if a demonstrated operational need later appears, not a new scheduled phase |
| D — durable recovery protocol | Could address approved crash-recovery/idempotency requirements that synchronous retries cannot | Versioned intent/transaction records, stable operation IDs, effect reconciliation, atomic ordering, migration/replay/compaction/backup procedures, multi-domain consistency and crash-point testing | Out of scope and disproportionate without a concrete product requirement; no journal design or automatic follow-up |

Option C would require typed pending **operations**, exact owner/generation, original dependency lifetime
requirements, completed-effect confirmation and explicit uncertain/refused classification. Status queries
must be pure: no persistence, resource acquisition, sampling, grant, progress mutation or ownership
transfer. A capability would need a strict additional-attempt budget, no domain replay, no reactivation,
stale-generation rejection and retained original failures even after later success. Resource-only,
aggregate-save and final-sample work must not be collapsed into a Boolean retry flag. It cannot impose
a new global ordinary-exception restart ban or override the original module guards.

This is substantive protocol and maintenance work, rather than simply iterating a second list. Tests
would have to preserve all existing behaviors and add dependency retirement/replacement, uncertain
effects, partial UUID successes, cancellation confirmation, repeated permanent errors, pure status
queries, maximum attempts and history. Native acceptance is possible for a future concrete scope at
the actual pre-dependency onDisable boundary, with transient/permanent disposable IO, independent cold
reads and no next tick. Its feasibility is not evidence that the scope is currently needed.

### Decision against the ten GO criteria

| Criterion | Finding |
|---|---|
| Real unresolved mode | Yes: final Player failure and unread Quest input are observable; permanent/uncertain faults are already documented |
| Central coordination improves outcome | A second call helps selected injected transient cases; no central authority is required by those operations |
| Not adequately covered already | F1 covered by Player fallback. F6 has no later automatic save, but original Player already supports explicit retry; operational need for an automatic adjacent attempt is unproven |
| Safe exact partial-effect semantics | Profile/resource operations have bounded local contracts; mixed Quest/domain and unknown outcomes lack universal safe replay |
| Dependencies usable | Only selected immediate windows; deferred policy fails this general condition |
| No reward/progress replay | Current save-only retries satisfy it; blanket/uncertain domain replay cannot claim it, as the controlled grant probe demonstrates |
| Prior failures visible | Current manager preserves them; any new retry accounting would need a separately specified compatible result model |
| Narrow production scope | Central solution requires an explicit owner operation contract and several owner changes, beyond a small second loop |
| Meaningful native acceptance possible | Yes for a separately justified scope; not newly conducted here |
| Maintenance justified | No frequency, requirement or uncovered operational incident establishes enough benefit over existing owners/fallback to justify central protocol now |

**Decision: NO-GO.** Do not convert a possible local retry benefit into an unconditional central feature.
No required prerequisite implementation follows this decision. Revisit only upon a concrete changed
reliability requirement or measured incident that existing owner recovery/fallback cannot adequately handle.

## Paper and JVM boundaries

Installed Paper **1.21.11-132 / c5eb079** bytecode was inspected read-only; no operator server was
started. PaperPluginInstanceManager calls JavaPlugin.setEnabled(false) at offset 95, catches Throwable,
closes/unregisters the classloader, then cancels plugin scheduler/global/async tasks, unregisters
services and HandlerLists. The current pinned upstream implementation agrees:
[Paper disablePlugin source](https://github.com/PaperMC/Paper/blob/c5eb0790f199da6c38d0a650e1e5cd5415b28185/paper-server/src/main/java/io/papermc/paper/plugin/manager/PaperPluginInstanceManager.java).
Normal platform teardown therefore provides additional physical cleanup, not a profile-save fallback.
Its own logging/external APIs can also fail; this is not an infallible stop/recovery guarantee.

The versioned [BukkitTask API](https://jd.papermc.io/paper/1.21.11/org/bukkit/scheduler/BukkitTask.html)
separates cancellation attempt from cancelled status, and
[HandlerList](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/HandlerList.html)
provides exact registration inspection/removal. Physical release removes residual work/registry
retention even if a callback was already inert. Paper cannot infer private dirty UUIDs or flush a
retired domain service. A void callback/exception does not reliably veto stop, and no subsequent
disabled-plugin tick or service hot reload is a recovery opportunity.

Historical native acceptance remains relevant but is not relabeled new testing: 5J-B ran 96 accepted
API/service checks across four isolated JVMs, including actual Root stop, exact 28-module order,
logger faults, Reward failure/Player fallback and cold reads. Earlier owner phases tested native
schedulers/events/menus with controlled actors. All explicitly excluded real-client/network acceptance.
Current canonical/probe classes remain unchanged production; this audit introduces no integration change.

Hard JVM exit discards private pending cache, baselines and dirty work. Per-file replacement/read-back
does not guarantee power-loss durability. There is no explicit fsync/directory-sync barrier, durable
grant intent or multi-file/multi-domain transaction. An independently matching file can confirm observed
bytes, not which uncertain operation completed exactly once. After-effect save errors, immediate-domain
RAM/disk divergence and retained recovery markers require deliberate reconciliation; never delete
evidence, reconstruct stale money and re-grant, or invent an emergency second writer.

## Modernization closure and risk classification

**Recommendation: CLOSE with documented limitations.** Startup ownership, callback generation isolation,
independent shutdown iteration, Player strict fallback, Reward retention, Quest strict flush/final
sampling, DailyQuest observer/resource ownership and error reporting now form a tested coherent system.
The historical confirmed 5H Reward and logger defects received the bounded 5I/5J-A/5J-B corrections.
Protected recovery evidence remains intact. No new critical backend defect within current supported
requirements was demonstrated that requires postponing real-player acceptance for more infrastructure.
This is not a blanket certification of every module, arbitrary external failure or lossless shutdown.

| Category | Current assessment / practical boundary |
|---|---|
| Blocking backend defects found here | **None proven within supported planned use.** A real storage/recovery conflict on a target installation must be resolved before using that installation; this audit does not certify operator storage health |
| Remaining operational release gate | Real player/client end-to-end acceptance has not passed. Run the checklist below before production use/feature expansion; failures found there should be triaged concretely |
| Nonblocking technical debt | Unread/consumed exceptional Quest sample boundaries; uncertain IO returns and possible immediate-domain RAM/disk disagreement; optional/startup logger sites outside the bounded isolation; retained resource/API refusal and unsupported stale external service references |
| Accepted operational limitations | Deferred Reward/Quest loss window under hard failure; unresolved final saves lost at JVM exit; no stop veto, built-in retry command or general hot-swap contract. Back up files, preserve causes/markers, reconcile storage, and keep original state only while actually usable |
| Optional future hardening | Owner-local bounded retry or richer diagnostics only if observed need justifies it; additional targeted lifecycle tests for a demonstrated defect. None is a prerequisite or automatically opened phase |
| Out-of-scope projects | Crash journal/recovery protocol, universal grant idempotency, cross-domain transactions, power-loss guarantees and a generalized persistence framework |

## Practical real-player acceptance: next operational action

Run one short, recorded session on a **disposable isolated Paper 1.21.11 + LuckPerms server**, with a
known tested JAR and two actual Minecraft clients. Use only synthetic UUID profiles, temporary worlds,
small known reward amounts and a disposable configured Quest catalog. No production deployment/test
is authorized by this report. Record build/config hashes, expected balances/progress, console and
independent files at each save/restart boundary.

1. Join both actual players; confirm one profile each and expected settings, visibility and daily
   assignment/cycle. Quit one and rejoin; verify save-before-unload and no unexpected duplicate listener
   effects or disconnect diagnostics.
2. Give known Coins and earn one configured OnlineReward interval. Record initial and final balance,
   baseline and remainder; unchanged repeated actions/time must not pay the same interval twice.
3. Progress and complete one Quest through actual gameplay; confirm one payout, COMPLETED status,
   progress for an unfinished Quest and unchanged repeat behavior. Check real `/quests` menu/feedback.
4. Verify DailyQuest same-cycle stability over quit/rejoin and reload; exercise the next cycle using
   disposable test configuration/time boundaries. Confirm pending rewards block rotation appropriately.
5. Toggle player settings/visibility, test their effect against the second client, quit/rejoin and
   verify stored values. Include real permission-denied/stale view behavior where relevant.
6. Run `/core reload` after one valid disposable config edit and one invalid edit. Check expected
   success/refusal, retained active state on failure, no duplicate tasks/payouts, correct menus/clock.
7. Leave known deferred Reward and unfinished Quest progress, run normal `/stop`, inspect console for
   truthful completion and no unexpected errors, and independently read resulting Coins/status/cycle.
8. Start a genuinely new JVM; rejoin both clients. Verify all values, no repeated completed reward,
   correct same-cycle selection/settings/visibility, and earning only new subsequent progress.
9. In a **separate disposable copy**, exercise legacy migration with exact original-byte backup,
   future-schema refusal and unresolved recovery-marker refusal. Preserve bytes/markers on failure;
   reconcile deliberately rather than bypassing validation. Verify appropriate console/connection feedback.

Do not call API-only actors a passed client test. Real network join/quit, gameplay, UI/visibility and
client observations must actually be executed and recorded. This session has **not** been performed
in 5J-C. It offers more immediate product confidence than another generalized lifecycle analysis.

## Evidence, protection, verification and sole deliverable

Evidence root outside checkout:
`C:\Users\vapee\.codex\visualizations\2026\10\25\01a17432-7597-7ff1-bc9d-34201a027523\phase5jc-evidence-20261028`.
It retains preflight/refs/stash listings, full path/length/SHA-256 inventories, baseline/final receipts,
external Java sources/classes/compile logs, 17 case receipts, 29 synthetic profile snapshots, cold-read
log and exact installed Paper bytecode. Final receipts are in `reward-cases-verified/` and
`quest-cases-verified/`, with `*-final.log` compilation/run/cold-read logs. The earlier equivalent run
is retained separately and is not double-counted. Experimental fixtures are disposable; their copied evidence
is external, not a new permanent repository test framework.

Fresh inventories before work: **934 installed server files**, **nine .run/top-level development-tool
files**, **710 existing tracked files**, **12 recovery/ref-log/sync-conflict evidence files**. These
are current measurements, not reused historical counts. The full operator set includes worlds,
balances/profiles, quests/cycles, LuckPerms, installed JARs/configs/libraries and existing backup/marker
evidence. All existing tracked scripts/tests/docs/source/config are separately covered by tracked hashes.
No operator start/stop/deploy tool ran and no installed file was used as a mutable test fixture.

Final complete path/length/SHA-256 comparison after experiments and final verification:

| Protected set | Before / after | Added / removed / changed / inaccessible |
|---|---:|---:|
| Installed operator server | **934 / 934** | **0 / 0 / 0 / 0** |
| Run and top-level development tools | **9 / 9** | **0 / 0 / 0 / 0** |
| Every pre-existing tracked source/test/doc/config/script | **710 / 710** | **0 / 0 / 0 / 0** |
| Recovery refs/reflogs and sync-conflict evidence | **12 / 12** | **0 / 0 / 0 / 0** |

Existing ref names/values and empty user stash listings agree. No recovery branch/bundle or conflict
evidence was repaired, removed or rewritten. `protection-comparison.json` and all before/after
inventories contain the complete measured results; no inaccessible operator file is silently excluded.
The new branch/report commit is separate from these existing-ref comparisons.

Final canonical `pwsh -File ./scripts/verify.ps1`, exit **0**: **207 discovered/executed/passed,
33,334 checks, Failed=0, Missing/Invalid=0; Maven clean package PASS; Spotless PASS**.
All 207 baseline entries retain identical class names, check counts, status and exit results:
**zero per-entry differences, total check delta 0**. `verification-comparison.json` retains that
comparison. No production/test source changed between canonical and external runs. Report metadata
completion does not change any tested code. `git diff --check` and exact intended-path review pass;
the new file is additionally checked in the staged diff before the single commit.

The only intended tracked change is this file:
`reports/architecture-modernization/PHASE_05J_C_RETRY_FEASIBILITY_DECISION.md`.
Exactly one local commit: `Evaluate bounded shutdown retry feasibility`. Its SHA is returned separately
to avoid a self-referential document. No push, merge, deployment, stash/recovery change, production/test
change, gameplay change or subsequent phase. Normal creation of the requested branch/commit is the
only intended user-branch/HEAD/index/reflog update; existing user/recovery/remote refs are preserved.
