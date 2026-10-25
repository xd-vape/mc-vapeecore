# Phase 5A — Clan Lifecycle Hardening

Date: 2026-10-25. One bounded lifecycle change to ClanModule and ClanMenu. Domain behavior, Clan persistence, commands, permissions, localization and production layout are preserved. Phase 5B was not started.

## Baseline

- Clean integrated local `master`, also `origin/master`: `1487bab1d51ec4881a2f923e8887a6a237b71910`.
- `git merge-base --is-ancestor b8dc001efdc4a33a01bfc4721594abc246220378 HEAD`: exit 0. Phase 4C is integrated.
- Branch created: `phase/clan-lifecycle-hardening`.
- Actual unchanged canonical baseline: **194 discovered/executed/passed harnesses, 30,786 checks, Failed=0, Missing/Invalid=0**; Maven clean package and Spotless passed.
- JDK 21.0.12.1, PowerShell 7, offline Maven repository `C:\Users\mehdi\.m2\repository`. Original production sources and baseline receipts were saved outside the checkout before editing.

## Original defects: observed before editing

An isolated compiled `ClanPreFixProbe` used the unchanged ClanModule, real PluginCommand, ModuleRuntimeFixture's real HandlerLists, actual observer collections and disposable data. Leak assertions ran **before** safety teardown. Confirmed:

1. Locale add failing after effect: **5 remaining handlers, both command bindings, 1 locale callback, 0 ClanChange callbacks**; the service getter exposed the partially initialized runtime.
2. Actual final logging failure: **5 handlers, both command bindings, both callbacks** and a usable partial service getter remained.
3. Locale remove throwing after effect: menu shutdown skipped owned view closure, retained its active entry and never removed the ClanChange callback.

The first probe expected four handlers; source inspection showed the fifth Join handler. Correcting that probe-only count produced the three recorded confirmations. No production source/data was corrupted. Source inspection also confirmed separate sequential failed-enable/disable cleanup, no double/reentrant guard and no independent command cleanup after unregister failure.

## Ownership and preserved boundaries

| Resource | Creator/owner | Lifecycle treatment |
|---|---|---|
| ClanService / FileClanRepository | ClanModule creates the service/repository runtime | Fresh service reads durable data on re-enable; disable does not save empty data |
| ClanMenu | ClanModule | Owns its two callbacks, active inventories and render/presence context |
| ClanMenuListener | ClanModule | One Bukkit registration, five real handlers; remains installed during owned view closure |
| ClanCommand | ClanModule | Executor/completer installed once; each released independently while still owned |
| PluginCommand descriptor | Bukkit | Retained; cleanup preserves foreign replacement executor/completer bindings |
| JavaPlugin/Server/Logger, ConfigService, IdentityModule/PlayerIdentityService, MessageService/LocalizationService, HelpRenderer | Borrowed from existing owners | Constructor boundary unchanged; no borrowed service shutdown/cache clearing |
| `clans.yml` and snapshot commit | Existing ClanRepository / ClanService | Schema **1**, existing serialization and commit/notification ordering unchanged |

The current IdentityService is acquired once per activation. `config::getClanLimits` remains live; no early immutable limits snapshot. No Service locator, new module, ReloadParticipant, scheduler or generic lifecycle framework was introduced. The actual 28-module order remains Friend → Presence → Clan → Reward at this boundary; all seven reload participants are unchanged. CoreModule, ModuleManager, VapeeCore, ClanService, FileClanRepository, ClanCommand, ClanMenuListener/Holder, plugin.yml and bundled translations were not modified.

## Startup, cleanup and retry

The complete acquisition/construction/registration sequence now sits in one try/catch, including observer startup, runtime publication and actual final INFO logging. Only then is `active` set. The public `getClanService()` signature/message remain; it fails closed before completion, on failed startup and after disable. `active || enabling` rejects duplicate and reentrant calls before acquiring or cleaning resources from the valid runtime.

Five package-local checkpoints follow actual listener, executor, completer, observer and publication effects. The unchanged public constructor supplies a no-op. Logging failures use a real controlled Handler rather than a simulated checkpoint.

One private cleanup path is shared by failed enable and disable: menu shutdown → Bukkit listener removal → executor release → completer release. The last three cannot be skipped by a failed earlier release. Published runtime fields are cleared and inactive. Startup retains the **identical original exception object**, with contextual cleanup failures suppressed. Ordinary disable throws an aggregate through the existing ModuleManager logging contract.

ClanMenu records each potentially acquired subscription **before** add, including APIs that throw after effect. Successful repeated start is idempotent. Stop attempts both removals independently and only clears each uncertainty flag after remove returns successfully. A refused remove remains visible and retryable; start must reconcile pending removal before any new add. The actual callback collections remain authoritative.

Menu shutdown always attempts owned inventory cleanup after observer failure and preserves both observer/close failures if necessary. Current exact tracked inventories close while their Clan listener is still registered. Other viewers continue after an individual close failure, foreign current inventories stay open, and active bindings plus render/rerender/presence context are cleared. Old holder/click/close paths remain protected by existing identity/permission/context checks.

A small private `CleanupResources` record retains retry references after a failed module cleanup; it is not active runtime or another subscription registry. A later disable can retry; enable must successfully reconcile it before creating another runtime. Normal successful cleanup releases the record as well. A permanently refusing external API cannot be forced to unregister: tests explicitly observe the retained real callback/handler, the error and blocked acquisition until the refusal is lifted. Arbitrary external Java references to an old service are not revoked; only its former registrations/views are detached.

## Measured safety

Counts below describe comparable logical operations, not claims that every source statement is an independent transaction. Startup stages use the fourteen pre-existing stages in the task's acquisition list; activation marking is the new fifteenth stage.

| Measure | Before | After |
|---|---|---|
| Independent resource registrations/bindings | 5: listener, executor, completer, locale, ClanChange | Same 5 |
| Listener / actual handler registrations | 1 / 5 | 1 / 5 |
| Observer subscriptions / command bindings | 2 / 2 | 2 / 2 |
| Protected logical startup stages | 4 of 14: command construction, listener, executor, completer | All 14 plus final active marking |
| Test checkpoints after real startup effects | 0 | 5; plus actual add/remove/logger boundaries |
| Module cleanup implementations | Separate failed-enable and disable bodies | 1 shared private cleanup path |
| Independent module cleanup boundaries | Sequential operations could short-circuit | 4 separately protected releases |
| Late-start leaks confirmed by probe | Locale startup and final logging | Both covered by successful rollback tests |
| Refused external cleanup | Could skip unrelated releases/views | Error retained, independent cleanup attempted, retry before new runtime |
| Duplicate/reentrant startup guard | None | Explicit guard preserving current runtime |
| Production class lines | Module 91, Menu 342 | Module 161, Menu 393 |
| Production classes changed | — | Exactly 2 |

Concrete failure coverage includes five stage faults, missing IdentityService/command, after-effect real listener registration, each observer add, actual logging, combined unsubscribe/close errors, real before-effect HandlerList refusal, and before/after-effect menu subscription failures. Counts of checks below are test results, not a target for inflating coverage.

## Real lifecycle tests

`ClanModuleLifecycleHarness`: **337 checks**. Covers normal activation; missing dependency/descriptor; five real-effect stage failures; both observer startup failures; final log failure; original exception identity with suppressed independent causes; repeated disable; duplicate/reentrant enable; recovery after failures; four fresh runtime components; current borrowed IdentityService; externally renamed durable clan state reloaded; exact durable bytes unchanged on disable. Registered close handlers process exact owned close events before unregistration. Separate before-effect refused observer/HandlerList cases prove honest residual registrations, independent cleanup and successful retry. Foreign executor/completer replacements survive own cleanup.

`ClanMenuLifecycleHarness`: **65 checks**. Covers locale/ClanChange add before and after effect, idempotent start, independent removals, refused removal with blocked duplicate acquisition and retry, real rendered multi-view shutdown including one failing close and a foreign replacement, exact stale/forged/current bindings, permission revocation, old close versus fresh view, actual authorized invitation acceptance, committed/failed persistence refresh, locale refresh/unsubscribe and render/presence context cleanup.

The existing ClanIntegrationHarness's fragile cleanup string assertion was replaced by a real-module verification method. Its other composition/order/permission/descriptor/no-scheduler/reload checks remain, with **15 checks**. No existing behavioral check or entry point was deleted. ClanMenuFixture only adds an opt-in close-failure seam; its existing default behavior is unchanged.

Focused existing regression: **13 harnesses / 1,074 checks**, all pass:

- ClanDomain 52; ClanIntegration 15; ClanPersistence 39; ClanService 136.
- ClanCommand 99; ClanComposition 50; ClanLayout 60; ClanLocalization 8; ClanMenu 84; ClanMenuSecurity 114; ClanPaginationUpdate 76.
- FriendModuleLifecycle 310; FriendMenuLifecycle 31.

Existing domain tests preserve creation, validation, invitations, roles, transfer/removal/disband, limits, rollback and notifications. Existing repository tests preserve strict v1 structure, atomic/fallback behavior and failed-save data. No Domain/Persistence implementation change was needed. OVERVIEW/MEMBERS/INVITES, the current default 54-slot layout, semantic actions/content slots, pagination, mappings, translations and refresh behavior remain unchanged.

## Compiled behavioral mutations

`scripts/tests/clan-lifecycle.Tests.ps1` reuses the established temporary overlay approach and normal Maven classpath preparation. **10 compiled variants: compile exit 0, intended behavioral assertion exit 1 for each**. Original classes are not replaced. Before and after controls passed Module 337 + Menu 65 + ModuleExtension 11 = **413 checks per positive round**. Temporary mutation directories are cleaned by checked absolute paths; no second canonical discovery system was added.

| ID | Controlled defect | Intended detection |
|---|---|---|
| N1 | Omit listener rollback | Actual registered HandlerList leak |
| N2 | Omit executor cleanup | Obsolete executor still installed |
| N3 | Omit completer cleanup | Obsolete completer still installed |
| N4 | Omit locale unsubscribe | Actual locale callback remains |
| N5 | Omit ClanChange unsubscribe | Actual committed callback remains |
| N6 | Skip owned view closure | Owned active registry not cleared |
| N7 | Replace original startup exception | Exact original exception contract fails |
| N8 | Allow duplicate enable | Duplicate listener/observer or runtime replacement |
| N9 | Retain publication after final log failure | Published runtime references not released |
| N10 | Short-circuit after menu cleanup error | Independent handler/command release skipped |

## Native acceptance

**PASS: 56 native checks**, all **28 modules** enabled and cleanly disabled, normal server shutdown, **0 ERROR/SEVERE** in the final clean stdout/stderr. The process exited. Isolated copied Paper 1.21.11-132, LuckPerms 5.5.84, JDK 21, disposable configurations/worlds/profiles/clan data; final JAR only. Native actors use real ServerPlayer/CraftPlayer objects, controlled Bukkit permission attachments and dummy network channels; commands, Bukkit events and real holder-bound inventories execute actual plugin code. This is **not a Minecraft-client visual test**.

The first probe incorrectly required Bukkit.dispatchCommand to return true for a denied command. Bukkit correctly returned false at descriptor permission enforcement. A second probe additionally assumed that the resulting server-level denial packet would contain the English word “permission”. Both probe-only assumptions were corrected: native denial is checked by its false dispatch, unchanged Clan state/data and empty completion; plugin feedback text remains covered by existing command tests. The third probe exposed an incomplete synthetic actor setup: the native actor was online but `Entity.valid` remained false. Local Paper bytecode confirms that `Player.isImmobile()` then makes `handleContainerClose` return before the close event, invalidating the probe’s detachment assumption. Marking the disposable native actor valid (and asserting it is interactive) reaches the normal Paper close-event path; the unchanged plugin then passes the stale-inventory check. All three failed probe logs/sources are retained separately and are not counted as acceptance. Each run uses a fresh server/world/data copy. No production change was made to accommodate these test corrections.

The final clean run covers root/alias resolution, help/completion, descriptor and GUI permission revocation, actual command create/invite, native GUI accept/transfer, all three views, exact holder/target binding, forged/detached/old-runtime inventory rejection, unrelated current inventory preservation, menu/handler/binding/observer cleanup, inactive getter, fresh four-component runtime, saved clan reload, stale close-event isolation and inert old callbacks. Exact `clans.yml` bytes remain unchanged during Clan disable and complete module shutdown. Startup-fault injections remain standalone, not intentionally broken native runtime. No real client or cross-module dependency hot-swap is claimed.

Tested JAR SHA256: `44e30fd71a17ed492ee42fbefe4ba0ece7451224b79f5fa74d50de18b2101264`.

## Canonical verification and data isolation

Final `scripts/verify.ps1 -Offline -MavenRepository 'C:\Users\mehdi\.m2\repository'`: **PASS**. Maven clean package and Spotless passed; **196 discovered/executed/passed harnesses, 31,190 checks, Failed=0, Missing/Invalid=0**. Actual inventory comparison retains all **194** baseline entry points and adds exactly the two new lifecycle harnesses. The **+404** check delta is accounted for by 337 + 65 new behavioral checks and GuiAuthoringAudit 226 → 228 (+2 new test source files). Every other existing harness keeps its count, including ClanIntegration 15 and RepoLink 74. The original six-file Spotless 2.43.0/GJF 1.22.0 AOSP enforcement scope remains unchanged. Only the two newly created Java harnesses were individually formatted; existing files received bounded functional edits.

Initial actual operator path/SHA256 inventory contains **22 files** (installed JAR/configs/languages/profiles/clan/feature data). The final **complete protected path sets and SHA256 hashes match all 22 installed files**, with 0 added/removed/changed protected paths. After confirming process exit, the exact resolved disposable server/world directory was removed; native logs/result and synthetic clan fixture were retained outside the checkout. The native-tested JAR hash matches the final canonical build. No actual-server deployment occurred and no existing operator backup was deleted.

Evidence is retained outside the checkout in `C:\Users\mehdi\AppData\Local\Temp\vapeecore-phase05a-evidence` (baseline/original sources, pre-fix observations, focused/canonical logs, mutation receipts and native source/logs/results). Canonical receipts remain under `target/verification/harnesses/`.

## Exact change inventory

Production (2): `src/main/java/dev/vapee/core/clan/ClanModule.java`, `src/main/java/dev/vapee/core/clan/gui/ClanMenu.java`.

Tests (4): new `src/test/java/dev/vapee/core/clan/ClanModuleLifecycleHarness.java`, new `src/test/java/dev/vapee/core/clan/gui/ClanMenuLifecycleHarness.java`, existing `src/test/java/dev/vapee/core/clan/ClanIntegrationHarness.java`, existing `src/test/java/dev/vapee/core/clan/gui/ClanMenuFixture.java`.

Supplemental script (1): new `scripts/tests/clan-lifecycle.Tests.ps1`.

Maintained docs (4): `docs/MODULE_DEVELOPMENT.md`, `README.md`, `docs/ARCHITECTURE.md`, `docs/DEVELOPER_GUIDE.md`.

Report (1): this new `reports/architecture-modernization/PHASE_05A_CLAN_LIFECYCLE.md`. Existing historical reports are unchanged. No real YAML, profiles, deployed JAR, generated server/world/build, compiled overlay or backup artifacts belong to the commit.

## Deferred risks and recommendation

External permanently refused unregister operations remain errors with retryable references; lifecycle code cannot guarantee forced external release. Externally held service objects remain ordinary Java references. This does not add active hot-swapping of dependencies or asynchronous lifecycle management. Other legacy modules are not automatically hardened by this change.

Recommend selecting the next single older module by observed failed-enable/shutdown risk and applying independent behavior tests before another bounded hardening. Do not broaden into a generic framework or change Clan domain/persistence. Phase 5B has not started.

Git review: `git diff --check` passed. Exactly the twelve files above are intended (eight modified and four new before staging); no unrelated formatting, operator data or generated artifacts are included. Deliver as one local commit `Harden clan module lifecycle and observer cleanup`; full SHA and post-commit status are returned separately to avoid a self-referential report hash. No push, merge, rebase, reset or automatic next phase.
