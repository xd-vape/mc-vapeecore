# Phase 2B - Lifecycle & Ownership Consistency

## Baseline and scope

- Date: 2026-10-21; Paper 1.21.11, Java 21, Maven, PowerShell 7.
- Initial branch: `master`, clean; baseline `503ed25a36f637d53a992784f6995587587ea990` (`Merge Development Conventions`).
- Git ancestry checks succeeded for Phase 1 `8fa3d7bcfc55e45c43f6f427fb0818e926e21dd4` and Phase 2A `e1cd69e2b494ba0bea29f721f3195c4b2d4f3a1e`.
- Work branch: `phase/lifecycle-ownership-consistency`.
- Official baseline: **181 discovered/executed/passed harnesses, 27,863 checks, 0 failures, 0 missing/invalid**. Maven clean package and Spotless passed.
- No gameplay, layouts, permissions, commands, config schema, persistence, module order or reload participants changed. No historical report was rewritten.

## Confirmed defects and implementation

### LobbyExperience

Previously the failed-enable catch reset only Visibility Back, although startup installed both Visibility and Settings Back. The Settings callback retained the failed runtime's Player Menu. Cleanup could also abort on a failed glide detach, callback reset, menu shutdown or locale unsubscribe, masking the startup exception and leaving independent resources registered.

The module now has one private, module-local cleanup path shared by failed enable and normal disable. All runtime references are owned before external registration begins. These references let rollback find even partially registered resources; the registry remains the runtime activation gate. Four package-local `EnableStage` checkpoints permit exact failure injection before callbacks, after Visibility Back, after Settings Back and after runtime activation. The existing public constructor supplies a no-op; no lifecycle interface or generic container was added.

Startup order remains flight/state/activity subscriptions, six Bukkit listeners, existing online visibility synchronization, Player Menu locale observer, both Back callbacks, Navigator locale/warp observers, registry activation, module locale observer, item-refresh eligibility/reconciliation and online glide availability. The success log is now inside the protected startup boundary as well.

Rollback/disable order:

1. Deactivate the item registry and set item-refresh eligibility to false.
2. Reset **both** callbacks through the existing SettingsModule APIs to `Player::closeInventory`.
3. Remove the module locale observer and deactivate delayed Experience synchronization.
4. Shut down Player Menu and Navigator, closing only their current owned inventories before unregistering their handlers.
5. Independently remove flight/state/activity subscriptions and shut down the owned glide runtime.
6. Independently unregister Glide, Player Menu, Player Selection, Navigator, Lobby Item and Experience listeners.
7. Clear every owned runtime field, including the visibility action and both menu references.

Java lobby feature definitions are deliberately retained across disable/retry. Only their runtime activation is removed. A second disable has no owned runtime to release. Successful navigation remains Player Menu -> Settings/Visibility -> Back -> current Player Menu; unavailable parent navigation closes safely and captures no old parent.

### SettingsModule

Previously only event/command registration was inside try/catch; published references and the three observer starts were outside. A later observer failure therefore bypassed module-local rollback.

The protected area now includes all three listener registrations, executor/completer binding, all three menu observer starts, runtime publication and success logging. Fields are published after all observer starts succeed. Failure and disable share a private cleanup routine over the exact owned menus/listeners/command. It shuts down Visibility, Visible Players and Settings while their close handlers remain installed, unregisters each owned listener independently, resets executor with `setExecutor(null)` (Bukkit's plugin fallback), clears the completer and releases the runtime references. Disabled getters remain fail-closed.

SettingsMenu, VisibilitySettingsMenu, VisiblePlayersMenu and NavigatorMenu now remove their own domain callback even if subscription threw after taking effect but before setting `observingChanges`. Locale and domain removal are attempted independently, preserving both failures if necessary. Shutdown closes/forgets owned inventories in finally even if unsubscribe throws. PlayerMenu likewise closes its views after failed locale removal and isolates online lookup/close errors per owner. No layout, render mapping or action authority was changed.

### Exception and ownership contracts

The original RuntimeException from enable is rethrown as the same object. Each failing cleanup step contributes a resource-labelled suppressed exception; cleanup continues. Normal disable logs the collected failures after attempting the remaining steps. ModuleManager remains unchanged: it rolls back only successfully started predecessors in reverse order, while the failing module cleans itself. CoreModule remains unchanged.

Only owned observers and handlers are removed. Unrelated locale subscriptions, Settings handlers during LobbyExperience cleanup and unrelated/replaced currently open inventories survive. Cleared active maps, exact holder/inventory binding, owner/profile/permission checks and existing listeners keep stale or forged inventories inert.

## Tests and failure coverage

`SettingsModuleLifecycleHarness` uses the real module, menus, listener registration and command object. It retains partial Bukkit-registration failures at all three positions. New cases fail the first/second/third domain observer and locale observer **after add takes effect**, add a cleanup failure after removal, check the primary/suppressed exception, preserve an unrelated observer and retry with exactly three new listeners and one observer set. Failed enable and normal disable are exercised with actual menu open/binding, multiple owned inventories, one replaced foreign inventory, cleanup failure and stale/forged views. No owned collection is manually cleared to obtain success.

`LobbyExperienceLifecycleHarness` tests all four checkpoints, successful retry, single locale/warp/glide subscription sets, callback behavior during success and after failure/disable, released fields, inactive item refresh, deactivated experience work and disabled glide runtime. Additional real menu-open cases cover failure with Player Menu/Navigator inventories and delayed respawn synchronization, plus normal disable with owned views. Replayed inventories are inert, queued synchronization does nothing, and owned views close before Bukkit handlers are unregistered. A locale cleanup fault does not interrupt independent cleanup.

Focused positive results:

| Harness | Checks |
|---|---:|
| SettingsModuleLifecycleHarness | 195 |
| LobbyExperienceLifecycleHarness | 340 |
| SettingsNavigationHarness | 19 |
| SettingsMenuSecurityHarness | 205 |
| VisibilityMenuSecurityHarness | 327 |
| PlayerMenuHarness | 60 |
| LobbyGlideHarness | 60 |
| LobbyGlideElytraHarness | 92 |
| LobbyGlideHandoffHarness | 43 |
| PermissionDescriptorHarness | 883 |
| NavigatorSecurityHarness | 78 |
| VisibilityModuleHarness | 22 |
| ModerationLifecycleHarness (includes actual ModuleManager rollback/retry) | 149 |
| LocalizationReloadHarness | 11 |
| LocalizationSurfacesHarness | 49 |

The existing persistence tests require an unrestricted local test process on this machine: sandbox atomic temp-file moves can produce AccessDeniedException. The canonical baseline/final runs use the same normal local environment; no persistence behavior was changed to accommodate the sandbox.

## Two source-sensitive test groups improved

| Converted group | Former fragile dependency | Replacement and preserved contract |
|---|---|---|
| NavigatorSecurityHarness lifecycle cleanup | Literal `cleanupRuntime(newNavigatorMenu)`, `deactivate`, exact `HandlerList.unregisterAll(local/field)` spelling and source index ordering | Executes real LobbyExperience lifecycle scenarios: menu runtime shutdown, delayed action deactivation, actual HandlerList cleanup, owned views closing before handlers, callbacks and retry. Existing Navigator access, target mapping and stale-click tests remain. |
| VisibilityModuleHarness Settings cleanup | Literal `closeVisibilityMenus()` and exact two unregister call strings | Executes real Settings observer rollback/retry scenarios; asserts actual absence of all owned handlers and observers, command restoration, released runtime fields and ownership of unrelated subscriptions. Existing explicit dependency/menu/command wiring checks remain. |

For each, an isolated copy split `HandlerList.unregisterAll(...)` across a Java line break. The converted harness passed (F1 Navigator: 78; F2 Visibility: 22). Broken behavior was detected by N1/N2/N4/N5 below. The old substring checks would reject this harmless line break. Replacements invoke real behavior rather than merely checking that another test class exists.

Inspected and deferred: Core bootstrap/module-order strings, DailyQuest wiring/task/reload checks, permission descriptor/forbidden dependency checks, Visibility relationship wiring, Presence bootstrap checks and reload participant strings. Replacing these safely would need broader composition-root seams or stable metadata; they remain intact. No descriptor, permission, forbidden dependency, reload atomicity or GUI security contract was deleted. ModuleManager required no production change.

## Isolated negative controls

All variants were compiled into separate output directories and placed before the normal classes on the test classpath. Production working files were never overwritten. A compilation failure did not count as a detected mutation; each compiled variant had to fail with the intended AssertionError. Final positive lifecycle runs used the ordinary production classpath.

| Control | Mutation | Observed result |
|---|---|---|
| N1 | Omit failed/disabled Settings Back reset | Exit 1: `fallback captures no stale parent` |
| N2 | Omit failed/disabled Visibility Back reset | Exit 1: `fallback captures no stale parent` |
| N3 | Skip root menu cleanup only in failed enable | Exit 1: `only unrelated subscription survives rollback` |
| N4 | Omit the root listener only from failed-enable cleanup | Exit 1: `failed enable releases partially registered handlers at 1` |
| N5 | Retain failed listener and register it again on retry in addition to new listeners | Exit 1: `retry installs exactly three current listeners` |
| N6 | Wrap/replace the original observer startup exception | Exit 1: `observer failure remains primary false/1` |
| F1 | Harmless LobbyExperience unregister line wrapping | Exit 0: NavigatorSecurityHarness 78 checks |
| F2 | Harmless Settings unregister line wrapping | Exit 0: VisibilityModuleHarness 22 checks |

N3/N4 were also run against the shared rollback/disable cleanup path: they failed with `real module disable closes every owned menu type` / `disable removes actual registered handlers`. The narrower failed-enable-only variants above additionally prove that normal-disable tests cannot conceal a broken startup rollback. All six required mutations and both harmless formatting variants compiled; all eight deliberately broken variants failed and both formatting variants passed. Positive restoration: LobbyExperienceLifecycleHarness 340 checks and SettingsModuleLifecycleHarness 195 checks.

## Official final verification

**PASS: Maven clean package, Spotless check, all 182 discovered/executed/passed harnesses, 28,351 checks, 0 failures, 0 missing/invalid; official command exit 0.**

All 181 baseline harness entry points are retained. The sole additional executable entry point is LobbyExperienceLifecycleHarness. Check-count changes: new lifecycle harness +340; Settings lifecycle 44 -> 195 (+151); Navigator security 82 -> 78 (-4 literal source checks replaced by the stronger behavioral group); unchanged GuiAuthoringAudit source automatically checks the new Java test file (213 -> 214, +1). Net +488 checks. No old harness was removed or renamed.

Command:

```powershell
pwsh -NoProfile -File scripts/verify.ps1 -Offline -MavenRepository 'C:\Users\mehdi\.m2\repository'
```

The six-file Spotless 2.43.0 / Google Java Format 1.22.0 AOSP allowlist and Git rules are unchanged. Spotless apply only adjusted the already permitted SettingsModuleLifecycleHarness. The newly added LobbyExperienceLifecycleHarness was formatted individually with the same pinned GJF/AOSP formatter; it was not added to the pilot. No existing non-pilot source received general formatting.

## Native Paper smoke

Executed against an isolated copied Paper **1.21.11-132-c5eb079**, LuckPerms **5.5.84**, JDK **21.0.12.1**, dedicated temporary data/world directory and loopback port **25585**. The existing dev-server plugin JAR, operator YAML and player profiles were not replaced. Tested plugin JAR SHA-256: `f38c9b249f7f101d5a2e5026c20de3addac40f1fe88cd802038b22741b35af38`. A temporary dependent probe used a native ServerPlayer/CraftPlayer, embedded connection, real Paper inventories, command dispatch and actual inventory-click event delivery. It did not substitute GUI/module implementations.

Final clean run on 2026-10-21 at **19:09:36 Europe/Berlin**: **`PHASE02B_NATIVE_PASS checks=25`**, **0 ERROR/SEVERE**, all **28** modules initially enabled and normally disabled, complete clean server shutdown. Tested `/settings`, Settings Back -> Player Menu, Player Menu -> Visibility, Visibility Back -> Player Menu, Player Menu -> Settings, owned click cancellation, inactive-parent fallback for both callbacks, forced LobbyExperience failure at RUNTIME_ACTIVE, primary exception identity, inactive registry/released parent, both fallback resets, retry/navigation, Settings disable closing its native view, command fallback and module-manager ownership.

Two preliminary test-world runs also passed the 25 lifecycle checks but emitted `No key layers in MapLike[{}]` from an incomplete flat-world generator setup. These were test setup errors, not accepted as a clean log. The final run used a new normal world and had no such errors. Test-only plugins, variant classes and isolated worlds were removed after evidence collection. The server is stopped. This automated native probe does **not** constitute real-client visual acceptance.

## Changed files

Production (7):

- `src/main/java/dev/vapee/core/lobby/experience/LobbyExperienceModule.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenu.java`
- `src/main/java/dev/vapee/core/lobby/experience/player/PlayerMenu.java`
- `src/main/java/dev/vapee/core/settings/SettingsModule.java`
- `src/main/java/dev/vapee/core/settings/SettingsMenu.java`
- `src/main/java/dev/vapee/core/settings/visibility/VisibilitySettingsMenu.java`
- `src/main/java/dev/vapee/core/settings/visibility/VisiblePlayersMenu.java`

Tests (5):

- `src/test/java/dev/vapee/core/lobby/experience/LobbyExperienceLifecycleHarness.java` (new)
- `src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorSecurityHarness.java`
- `src/test/java/dev/vapee/core/module/ModuleRuntimeFixture.java`
- `src/test/java/dev/vapee/core/settings/visibility/SettingsModuleLifecycleHarness.java`
- `src/test/java/dev/vapee/core/visibility/VisibilityModuleHarness.java`

Documentation/report (4):

- `docs/ARCHITECTURE.md`
- `docs/DEVELOPER_GUIDE.md`
- `docs/CODE_STYLE.md`
- `reports/architecture-modernization/PHASE_02B_LIFECYCLE_OWNERSHIP.md`

## Git completion and remaining limits

One local commit: `Harden module lifecycle cleanup and ownership`; its actual full SHA is returned with the final task result. No push, merge, rebase, cherry-pick or next phase. Working tree checked clean after commit. No operator data, temporary probe, generated class or mutation fixture is committed.

The work proves selected RuntimeException paths and ownership/retry invariants for these two modules, not universal recovery for every module, VM Error, external API failure mode or future resource. FaultSet deliberately models add/remove failures after their external effect, including the dangerous partially registered observer case. A provider that refuses removal before any effect cannot be forced to release by a module-local cleanup helper; independent cleanup still proceeds and errors remain observable. Other modules' late-enable paths, whole-module constructor dependencies and the deferred source-sensitive composition checks remain possible future audit work. No new lifecycle engine was introduced.
