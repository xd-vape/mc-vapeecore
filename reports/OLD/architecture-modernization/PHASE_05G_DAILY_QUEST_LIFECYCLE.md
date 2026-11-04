# Phase 5G — DailyQuest Lifecycle & Observer Safety

## Baseline and scope

Repository `xd-vape/mc-vapeecore`, clean integrated `master`
`83a59f92dbd999271081fe88d1b0eaeef83cc7ac`; branch `phase/daily-quest-lifecycle`.
Preflight inspected status, current branch/HEAD, twelve commits and worktrees. Exactly the expected
clean master was present. No stash, recovery branch, bundle or sync-conflict evidence was inspected
or changed. Baseline canonical `scripts/verify.ps1`: **201 discovered/executed/passed harnesses,
32,110 checks, Failed=0, Missing/Invalid=0, exit 0**; Maven clean package and Spotless PASS.
Production was unchanged until this baseline and the pre-fix probes completed.

Actual toolchain: Oracle JDK **21.0.12.1+1-LTS-4**, Maven **3.9.16** from IntelliJ IDEA 2026.2.3,
PowerShell **7.6.5**. Spotless **2.43.0**, Google Java Format **1.22.0/AOSP**, existing six-file pilot.
The three new Java test sources were formatted individually and passed the pinned formatter dry run.
No POM, discovery, pilot, resource default, permission or deployment change.

Primary production ownership boundaries are DailyQuestModule and DailyQuestMenu. The two additional
production changes are justified by measured stale join execution and retired-menu reopening:
DailyQuestListener accepts an activation predicate; DailyQuestCommand gates its complete entry point
through the bound menu. Their previous public constructors remain compatible. MenuListener needs no
change because its real action checks now consult the menu's revoked authority; forged/stale-holder
click/drag cancellation remains intact.

## Unchanged production pre-fix experiments

The external probes compiled against the unmodified classes, real Bukkit HandlerLists, actual final
PluginCommand, real DailyQuest/Quest/Player/Reward/Economy services, disposable configuration and
FilePlayerRepository files. Observations occurred before fixture safety cleanup. Dependency module
shells exposed the actual borrowed services. Faulting observer Sets replace only the existing actual
callback collections; they do not implement subscription cleanup for production.

| Experiment | Observed original behavior |
|---|---|
| Final INFO logger throws | Same original exception, **two listeners / five actual handlers / one main task / one locale + one Quest observer**, both command bindings installed and getter open; DailyQuest definitions remain |
| Captured task after that failure | A newly loaded actor receives a real daily cycle and assignment |
| Listener 1/2, before/after registration effect | Ordinary rollback succeeds: no handlers/observers, previous empty catalog restored, original exception preserved |
| Unregister refuses before effect | Startup error is masked; five handlers remain; captured join still initializes a daily cycle |
| Unregister removes then throws | Startup error is masked; four handlers remain because later listener cleanup is skipped; captured join still operates |
| Double or reentrant enable | **Ten handlers / two tasks / four observers**; duplicate disable leaves the abandoned first generation |
| Main cancellation refuses | Five handlers and both observers remain, getter stays open; later cleanup is skipped |
| First locale add before/after effect | Ordinary module rollback removes the locale callback; no leak demonstrated in this specific case |
| Second Quest add before effect | Ordinary rollback succeeds |
| Second Quest add after effect | One actual Quest observer remains because the old successful-return flag never became true |
| Duplicate menu start | Locale add called twice, Quest add once; Set deduplication hides the repeated registration attempt |
| Locale removal refuses | Other observer cleanup and menu view closure are skipped; active physical owned view remains |
| Countdown cancellation refuses | Cancellation handle is lost; active view and Quest observer remain |
| Open through a retired menu reference | Sync executes again and the menu acquires an authoritative view |
| Captured countdown after same-menu reactivation | Old callback changes the fresh view's clock |
| Captured main timer after fresh module enable | Old timer uses the replacement DailyQuest runtime and initializes a cycle |
| Foreign executor/completer replacements | Both are overwritten by unconditional shutdown reset |
| Independent catalog replacement followed by startup scheduler failure | Rollback overwrites the foreign catalog with the previous empty catalog |
| Scheduler registers then throws before returning a handle | One unknown external task remains; original code's callback is inert after ordinary rollback |

`PluginCommand` is final. Its actual bytecode was copied into an external overlay and instrumented
with calls immediately before and after its real executor/completer setter bodies. All four original
setter fault cases preserved the original exception and ordinarily released registrations. This is
controlled external-failure evidence, not a claim that those simple API setters normally throw.
The module late-log probe had no active view, so its countdown count was zero; active-view/countdown failures were measured separately with the real menu. No fake PluginCommand or fake DailyQuestModule was substituted. Sources/logs of these probes remain
outside the checkout. The first menu-adapter attempt lacked a catalog, so no view opened; it was
corrected with a valid disposable definition before the counted view/countdown observations.

Confirmed defects are the rows above. No duplicate monetary payout, disk corruption, asynchronous
race or crash-durability defect is inferred from resource leaks. Scheduling without a returned handle
remains a real API limitation rather than a guarantee this patch can manufacture.

## Runtime ownership and protected start

DailyQuest owns its config/service, join listener, menu and menu listener, executor/completer instances,
one synchronous 1200-tick task, activation and pending cleanup references, and its startup catalog
installation. The menu owns exact active inventories/render state, one locale observer, one Quest
commit observer and one conditional shared synchronous 20-tick countdown.

PlayerService and profile files belong to PlayerModule; QuestService, its dirty markers and registry
instance belong to QuestModule; RewardService owns grants. MessageService/Localization, command
descriptor, native scheduler/plugin manager/inventories are borrowed. DailyQuest introduces no
repository, reward path, persistence task, flush policy, cache or generic resource framework.

Enable rejects active/enabling/cleaning/pending states before acquisition. Inside one exception
boundary it obtains the current borrowed services, initializes and validates config, captures the
previous immutable registry map, builds service/listeners/menu/command, installs configured definitions,
registers both listeners, binds executor and completer, subscribes the menu, acquires the main task and
performs final logging. Only after all of these return does it publish and authorize the runtime.
Getters and callbacks remain closed during reentrant startup. Failed startup invents no assignment,
reward, save or shutdown sample.

Each callback captures exact activation identity. A retired main timer never resolves a new module
runtime and cannot continue using its retired service. Join checks before/after actor UUID access.
Menu and command calls check the captured module predicate; old commands do not even perform sender
permission/message IO after retirement. Directly constructed listeners/menus retain their old APIs.
Old public service references remain ordinary Java objects; arbitrary external service calls are
not revocable by lifecycle gates.

## Independent cleanup and retry

Disable revokes active state and clears public runtime before external actions. It attempts main
cancellation; menu shutdown while the close listener remains registered; menu and join unregister;
executor release; completer release; and failed-start catalog restoration independently. Failure
wrappers carry resource context. The original startup exception is the same object and receives
suppressed cleanup failures. Normal disable reports an aggregate error.

Main cancellation checks actual `BukkitTask.isCancelled()` even after `cancel()` throws. Before-effect,
ineffective and status-unknown cancellation retain the exact private handle. Confirmed after-effect
cancellation can release the handle while still reporting its exception. Actual HandlerLists confirm
listener removal, including a removal that took effect before throwing. No plugin-wide task/listener
cancellation is used. Unresolved references stay in one private pending runtime and block new enable;
an explicit same-process `disable()` retries them. Complete repeated disable acquires/mutates nothing.

Executor and completer are independent. Only the exact activation's DailyQuestCommand may be reset.
A foreign executor can coexist with release of the owned completer and vice versa. Read-back determines
whether a failed setter still holds the binding; retries preserve newer foreign bindings. The native
Bukkit-owned descriptor remains installed. Actual Paper semantics: `setExecutor(null)` restores the
owning JavaPlugin fallback; `setTabCompleter(null)` leaves a null completer.

The menu marks each observer potentially acquired before invoking Add. It removes both independently,
retaining exact callback references after any removal exception, including an after-effect exception.
Successful repeated start adds nothing. Failed start retires and attempts shutdown, preserving the
original exception. Pending observer/cancellation cleanup must complete before another start.
Every successful reactivation creates fresh observer tokens/callbacks and a fresh countdown token;
captured old callbacks cannot refresh new views. There is no secondary observer registry.

The countdown remains a single conditional 20-tick shared task. First/multiple/last viewer, stop,
shutdown and reactivation are tested. Revoking its token precedes cancellation. Its existing narrow
CountdownScheduler returns Runnable cancellation for compatibility. A successful custom Runnable
return is its cancellation contract; an exception retains it. The production adapter additionally
confirms native `isCancelled()`. No returned/null handle or a registered-then-thrown scheduler call
cannot guarantee exact external cancellation; its captured callback is nonetheless inert. Refused
known cancellation cannot create a second operational generation. A failing viewer, including a
throwing diagnostic handler, cannot prevent other countdown viewers from updating.

View cleanup snapshots and detaches Active state before close IO, checks current exact top inventory,
preserves foreign replacements and processes other viewers after a failure. Rendering/rerender context
is cleared. An ordinary failed physical close is logged and remains best effort, with all view authority
detached; it does not turn a valid config reload into an apply failure. A diagnostic or cancellation
error is collected after independent cleanup. Existing owner UUID, bound holder, current-top inventory,
permission and rendered action/target checks remain. A physically displayed former view grants no
new authority; security cancellation of item interactions remains.

## Registry and reload consistency

Actual `QuestDefinitionRegistry.replaceAll()` validates nulls/duplicate IDs into local maps and
publishes one immutable deterministic map only at the end. It has no ordinary partially published
catalog stage. Startup records the exact previous and installed map identities. Rollback restores the
previous definitions only while the installed map still belongs to that activation (or accepts the
untouched previous identity). A later independently installed map is preserved, the conflict is
suppressed on the original startup error, and both snapshots remain privately pending. Tests explicitly
reconcile ownership before retry; the patch does not overwrite foreign catalogs to manufacture success.
Normal shutdown preserves the borrowed registry's catalog, matching the original successful lifecycle.

Prepare validates disk configuration without mutating config or registry. Apply replaces registry,
then config, then closes views. Rollback restores both captured snapshots; a retired plan is rejected.
No participant, service, observer or task is recreated. The existing seven-participant ReloadService
and status policy remain unchanged. The supplemental bytecode overlays inject actual registry/config
API errors before/after apply and during rollback. Normal apply failures restore exact previous state;
external rollback refusal remains `ROLLBACK_INCOMPLETE`, potentially with divergent config/catalog
until explicit reconciliation. A later valid reload is tested. Closed views cannot be reopened by
config rollback; this is bounded state consistency, not an atomic reversible external UI transaction.

Existing daily cycle/reset/timezone/DST, deterministic selection, same-cycle stability, pending reward
blocking, unknown pending warnings, force reconciliation and aggregate persistence are unchanged.
Countdown never synchronizes, saves or awards. The main sync retains 1200 ticks and independent loaded
player handling, including a player failure followed by a throwing warning logger. No changes to
DailyQuestService, Quest, Reward, Player, OnlineReward, schemas, resources, ModuleManager or composition.
ModuleManager still records only successful enables, logs disable errors and forgets its enabled list;
it is not a global retry owner and cannot force refused API operations or retain pending data after exit.

## Executable tests and mutations

New canonical entries:

- `DailyQuestModuleLifecycleHarness`: **354 checks** against real module/services/HandlerLists,
  command/observer state, callbacks, reload and disposable FilePlayerRepository profiles.
- `DailyQuestMenuLifecycleHarness`: **133 checks** for partial subscriptions, independent removals,
  exact cancellation confirmation, generation gates, real menu opening, view closure and failure isolation.
- `DailyQuestLifecycleFixture`: support only, no canonical main entry.

All **201** original entries remain. DailyQuestLifecycle retains **16 checks** and its semantic
constructor/module order/cadence/priority/domain/descriptor/reload checks. Only its three affected
source-spelling assertions now call actual task/rollback/shutdown behavior helpers.

`scripts/tests/daily-quest-lifecycle.Tests.ps1`: **15 variants compiled successfully (exit 0) and
failed their specifically intended behavioral assertion (exit 1)**. N1 late rollback omitted; N2 duplicate
enable; N3 reentrant enable; N4 omit menu-listener release after actual acquisition; N5 foreign executor
reset; N6 foreign completer reset; N7 skip second observer removal; N8 lose refused countdown handle;
N9 remove exact main-runtime gate; N10 let retired Quest observer refresh fresh views; N11 skip owned
view cleanup after observer failure; N12 omit catalog rollback; N13 expose pending service; N14 ungated
retired join listener; N15 mask original startup error. Compilation failures do not count.

Both focused positive rounds run sixteen existing/new harnesses; all pass. Supplemental real final-API
overlays add **68 command-boundary checks + 56 registry/config/reload checks = 124**, separately from
canonical totals. No production source/class is replaced. Overlay/JVM data lives in checked temporary
paths; logs/receipts were preserved outside target before canonical clean.

## Final canonical verification and native acceptance

Final canonical `scripts/verify.ps1`: **203 discovered/executed/passed harnesses, 32,601 checks,
Failed=0, Missing/Invalid=0, exit 0; Maven clean package PASS; Spotless PASS**. All 201 baseline
entries remain. Exact **+491 checks**: ModuleLifecycle +354, MenuLifecycle +133, GuiAuthoringAudit
234 → 237 (+3 new Java test sources), RepoLink 83 → 84 (+1 maintained guide link). Every other
existing harness count remains unchanged, including DailyQuestLifecycle 16. New-test pinned formatter
dry run and supplemental PowerShell parsing pass. Focused positives: **16 harnesses / 1,564 checks
per round**, both before and after all 15 mutations; the final formatted classes were rechecked in a
second complete successful supplemental run. Actual command/reload API overlays add 124 separate
checks, not to the canonical count. Baseline/final receipts and exact per-entry comparison are saved.

**Native PASS: 119 API/service checks (58 + 61) in two separate JVM/server processes**, Paper
**1.21.11-132-c5eb079**, LuckPerms **5.5.84**, JDK 21, loopback **127.0.0.1:25598**, fresh disposable
worlds/configs/profiles. Both processes exit **0**, with **0 console ERROR/SEVERE** and no unexpected
errors. The deliberately invalid reload produces exactly **one expected SEVERE diagnostic per
process**, captured at the existing ReloadService logger boundary rather than printed to the console;
its actual `PREPARE_FAILED` result and unchanged active catalog are asserted. This diagnostic is not
misrepresented as absent.

The accepted `native-3` run covers all 28 exact modules, seven actual reload participants, both listeners
with five handlers, native command descriptor/bindings, actual selected RegisteredListener execution
of Player NORMAL → DailyQuest HIGHEST → Quest MONITOR, saved daily cycle and catalog assignments,
same-cycle stability, actual holder-bound 36-slot Paper inventories, clock/close slots, one shared
20-tick countdown, one/last-viewer close, localized reset text, real committed Quest refresh and glint,
actual configured seven-Coin RewardService completion, successful coordinated reload, rejected invalid
reload, successful retry and stable runtime/task/observer identities. The real Paper scheduler invokes
the unchanged original 1200-tick DailyQuest callback once on its main thread in each JVM.

A ModuleManager shutdown boundary inspects DailyQuest before Quest stops: exact callbacks/listeners,
main/countdown tasks, owned command bindings and views are released while Quest/Player remain available.
All core handlers and all five base timers (six with an open menu) are gone before Paper's own plugin
safety teardown. Captured old native callback/command/menu references are inert. Actual Quest/Player
owners persist the aggregate; the second JVM independently reads the same cycle, exact quest progress/
statuses and seven Coins, without reroll or a repeated reward.

Controlled Player and InventoryView adapters provide synthetic actor IO over **real Paper inventories,
items, holder binding, services and file repositories**. The menu's recipient lookup resolves those
actors. Locale delivery calls the exact actually registered DailyQuest callback with the actor's DE
locale; close delivery calls the actual menu listener. Selected join handlers use their real native
RegisteredListener executors in actual priority order. Other modules keep Paper's empty network roster.
The countdown receives a controlled clock value for the stale/current callback assertion. The main
CraftTask wrapper observes and invokes its unmodified original production Runnable; it does not replace
the synchronization logic. This is controlled API acceptance, **not an unfiltered global event-bus,
network login or Minecraft-client visual test**.

Two earlier disposable probe runs are explicitly excluded from acceptance. `native-1` failed after
23 counted assertions because a global locale event reached unrelated Presentation IO (`displayName`)
that the synthetic actor did not implement. Only probe delivery was narrowed to the registered DailyQuest
callback. `native-2` failed at assertion 31 because the probe compared native ItemStack wrapper identity;
Paper may return fresh wrappers on reads. Only the probe changed to compare item contents. Both failed
logs/source snapshots remain; neither is called successful and neither caused a production workaround.
The accepted run uses a new fresh server/data copy. All server processes exited before their exact
validated disposable server/world directories were removed; sources, logs, configurations, receipts and
synthetic profiles remain outside the checkout.

Native-tested artifact equals final canonical JAR SHA-256:
`ef1d2cc7c2d2d584ba107599dbca4d9e05465163f6a2e40a3092de12c2e691d8`.
Production-source hashes remain identical throughout final canonical verification and native tests.

## Operator protection and exact paths

Before tests, the actual installed `dev-server` complete relative path, length and SHA-256 inventory
contained **934 files**; this is freshly measured, not assumed from Phase 5F. It includes all worlds,
profiles/coins/quests/daily cycles, plugins/deployed JARs, LuckPerms, configurations, libraries and
existing migration/recovery/sync-conflict evidence. A separate complete inventory protects nine actual
`.run` and top-level development/deployment script files. No operator server or deploy script ran.
All mutable tests use disposable YAML/profiles or a fresh server outside the checkout.

Final full comparison after all tests and disposable-server removal: **934 before / 934 after;
0 added, 0 removed, 0 changed paths/lengths/SHA-256 values; 0 changes to the nine protected tool/run
files**. No real operator path was unavailable. Deployed VapeeCore JAR, profiles, cycles/quests,
coins, worlds, LuckPerms, configs and recovery/conflict files remain unchanged. No deploy or operator
server start/stop script was executed. `git diff --check` passes and intended-path review contains
exactly the eleven files below. The working tree/commit SHA are reported after the single local commit.

Evidence root:
`C:\Users\vapee\.codex\visualizations\2026\10\25\01a17432-7597-7ff1-bc9d-34201a027523\phase5g-evidence-20261026`.
Pre-fix original sources/classes and observations, supplemental sources/logs/receipts, baseline/final
canonical inventories, native probe/source/console/config/synthetic-profile evidence remain there.

Exactly eleven intended changed paths:

1. `src/main/java/dev/vapee/core/quest/daily/DailyQuestModule.java`.
2. `src/main/java/dev/vapee/core/quest/daily/DailyQuestListener.java`.
3. `src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestMenu.java`.
4. `src/main/java/dev/vapee/core/quest/daily/command/DailyQuestCommand.java`.
5. `src/test/java/dev/vapee/core/quest/daily/DailyQuestModuleLifecycleHarness.java`.
6. `src/test/java/dev/vapee/core/quest/daily/DailyQuestLifecycleFixture.java`.
7. `src/test/java/dev/vapee/core/quest/daily/menu/DailyQuestMenuLifecycleHarness.java`.
8. `src/test/java/dev/vapee/core/quest/daily/DailyQuestLifecycleHarness.java`.
9. `scripts/tests/daily-quest-lifecycle.Tests.ps1`.
10. `docs/MODULE_DEVELOPMENT.md`.
11. This `reports/architecture-modernization/PHASE_05G_DAILY_QUEST_LIFECYCLE.md`.

No historical reports, other production modules, installed operator files, generated server/build
artifacts, local IDE/run configurations or development/deployment tools belong to this commit.
Exactly one local commit is requested: `Harden DailyQuest lifecycle and observer ownership`.
The actual full SHA and post-commit status are returned separately to avoid a self-referential hash.
No push, merge, deployment, stash modification or subsequent phase.

Deferred: permanently refused external API release, lost unknown scheduler handles, arbitrary external
stale service references, global incomplete-shutdown policy and hard-crash durability. Broader module
hardening requires a separate explicit task. Recommended follow-up is a separately scoped decision on
incomplete shutdown/retry policy; no next phase is started automatically. Real Minecraft-client visual
and network acceptance remains outstanding unless explicitly documented with an actual client.
