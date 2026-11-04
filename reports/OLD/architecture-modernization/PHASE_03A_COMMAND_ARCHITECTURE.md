# Phase 3A - Command Architecture & Extension Patterns

## Baseline

- 2026-10-21, initial clean integrated local `master`: `1b7abc2197cc3b54624288896b8f45f576c8d18a` (`Merge Lifecycle & Ownership Consistency`). The observed local origin/master ref pointed to the same expected merge; this is the inspected repository state, not an assumption about a freshly fetched remote.
- `git merge-base --is-ancestor 09289e4abb3cc248352751eff86cb9660fe83981 HEAD` exited 0. No merge, rebase, cherry-pick or reset was needed.
- Branch: `phase/command-extension-patterns`.
- Official baseline: **182 discovered/executed/passed harnesses, 28,351 checks, 0 failures, 0 missing/invalid**, Maven clean package PASS, Spotless PASS; command exit 0.

## Initial architecture findings

Inspected plugin.yml, composition-root registration, command/help infrastructure, Warp, Friend, Clan, Settings, Utility and DailyQuest adapters/modules and relevant harnesses. Root names/aliases/defaults/children are declared in plugin.yml. Feature modules generally acquire services and bind executor/completer; the Core root is a composition-root exception and delegates `/core quest force` unchanged. Handler classes parse/authorize/report; domain services retain persistence and mutation authority.

Useful variations are preserved: simple player-only `/quests` has no completion; Friend/Clan choices are domain-specific and player-only; Warp is an admin subcommand tree with player location required only for set; Profile reads known offline identities; Coins mutates online wallets under staff protection; Teleport distinguishes moving source from destination and keeps its specialized parser. A universal parser/base class would hide these differences.

Accidental synchronization in Warp: SUBCOMMANDS, HELP_PAGE entries, string dispatch, usage strings and a separate ID-completion action list repeated supported tokens. Help already used the shared renderer/localization but its entries did not carry the admin node because the whole invocation was guarded. WarpModule protected two setters, but published/logged outside the catch; cleanup setters were sequential and could prevent independent cleanup if one failed.

Registration comparison: UtilityModule already owns command pairs before acquisition and uses a small local best-effort cleanup pattern. SettingsModule protects its full listener/menu/observer/binding startup after Phase 2B. Friend/Clan still start some menu observers outside their registration catch; no broad migration was performed. The Core composition-root binding is not replaced by another registry or service locator.

## Ownership model and infrastructure reused

`plugin.yml -> owning module -> command adapter -> domain service`; the shared CommandHelpEntry/Page/Section/Renderer presents permission-aware localized help, and MessageService/LocalizationService resolve recipient text. Harnesses independently enforce observable contracts. These are responsibilities, not a new inheritance hierarchy.

Reused unchanged: the existing help renderer/models, localization/message pipeline, CoreModule, WarpService/WarpConfig/domain results, Bukkit PluginCommand semantics, descriptor and domain/security suites, ModuleRuntimeFixture and Utility's local cleanup idiom. No new shared command-binding helper, suggestion engine, public permission, root, alias, subcommand, translation key or schema was added.

## Pilot and measured extension complexity

Added one immutable, package-local WarpSubcommand enum. Its row owns canonical token, syntax suffix, help-description key, help section and existing-ID completion flag. Its three help-section keys and nine descriptions are the existing keys, including the historical blackjack namespace for two shared headings. HELP intentionally has no help row. No key meaning or installed language file was changed.

WarpCommand derives HELP_PAGE, root candidates, usage strings and eligible ID actions from the enum. SHOW/HIDE keep visibility filtering; ICON keeps explicit material-context completion. A typed exhaustive switch expression still routes to named action methods. No callbacks/reflection/anonymous handlers hide execution; a new enum constant requires a deliberate routing case. Argument validation and WarpService calls stay feature-local. Root completion remains case-insensitive, sorted and capped at 100.

| Ordinary hypothetical `<id>` extension | Before | After |
|---|---:|---:|
| Mandatory production Java edit locations | 5 | 3 |
| Canonical token string definitions | 5 | 1 |
| Java files normally edited | 1 | 2 |
| Help/root/ID completion | Independently edited | Derived from the row |
| Missing execution decision | String lists could drift | Exhaustive enum switch plus independent behavior oracle |
| Runtime authorization | One existing root guard | Same guard; no per-action copy |

Before locations: root list, help entry, dispatch, action/usage method, ID-completion list. After: enum row, typed case, named action method. Hypothetical `inspect <id>` appears only as a documented example; it was not implemented. Translation and test edits remain mandatory in both designs and are excluded from Java counts. A genuinely new completion context still needs an explicit branch and focused tests. Tradeoff: navigating a second small Java file and retaining explicit dispatch in exchange for fewer synchronization points and token copies.

## Behavior, permissions, help and completion

All ten actions preserved: help, set, remove, list, info, name, icon, show, hide, order. Syntax/count validation, unknown-action behavior, player-only set (including usage-vs-sender precedence), console eligibility of other actions, case handling, service mutation/rollback, feedback components/placeholders and saved data schemas remain unchanged.

`vapeecore.warp.admin` remains the descriptor/runtime authority, default OP. Both execution and tab completion guard it before dispatch/state access. Namespaced/upper-case labels do not bypass the guard; Warp still has no declared aliases. Generated help entries now also reference that same existing node, so an unauthorized direct renderer omits admin entries. This does not grant or remove command capability: invocation already required the node. The existing help harness now renders Warp with an explicitly authorized fixture and adds an unauthorized-renderer assertion, retaining all prior authorized help checks.

Help keeps Create & Edit / Information / Management order, existing footer/descriptions, EN/DE recipient resolution and SUGGEST_COMMAND clicks. Set/name/icon hints retain exact prior text. No translation resources or live catalogs were edited. Dynamic input remains literal safe Components.

Only remove/info/name/icon/show/hide/order offer ID completion. SHOW only suggests hidden Warps, HIDE visible Warps; ICON's third argument offers displayable materials. Prefix, spelling, ordering and existing cap are preserved. Denied senders receive no root, ID or material suggestions. Existing OnlinePlayerResolver/NameSuggestions are documented, not replaced; the latter has five real Utility call sites.

## Registration/lifecycle

WarpModule now protects binding, runtime publication and success logging together. Failed enable independently restores the executor's Bukkit plugin fallback, clears completion and clears the new in-memory service, retaining the original RuntimeException and suppressing independent cleanup failures. Disable uses the same narrow cleanup, clears its fields and surfaces collected failure after independent attempts. Repeated disable is safe; retry loads saved data into a fresh service and binds a fresh matching executor/completer. Disk persistence is not cleared on disable.

A package-local two-stage callback permits failure immediately after the real executor or completer setter. Public production construction supplies a no-op. Tests also inject a real logger failure after publication and exercise a missing descriptor. No new module/command framework or competing shared binding abstraction was introduced. CoreCommand, UtilityModule, ClanCommand, Settings and Phase-2B production files are unchanged.

## Focused tests and preserved contracts

| Harness | Checks | Coverage |
|---|---:|---|
| WarpCommandContractHarness (new) | 297 | Frozen independent ten-action and nine-help-entry oracle; execution of every action; usage, unknown input, actual persisted effects, console/player distinctions, EN/DE descriptions and click syntax, contextual completion, denied labels and unchanged bytes |
| WarpModuleLifecycleHarness (new) | 67 | Actual WarpModule/PluginCommand, partial setter effects, failure after publication, missing descriptor, fallback cleanup, safe repeat disable, fresh service/executor on retry and persisted reload |
| WarpCommandHarness | 86 | Existing order parsing, visibility, contextual IDs, denial and failed-save rollback unchanged |
| CommandHelpHarness | 87 | Existing structured help plus explicitly authorized Warp and unauthorized metadata filtering |
| PermissionDescriptorHarness | 884 | Existing nodes/defaults/children/roots/aliases and forbidden-dependency checks |
| SettingsModuleLifecycleHarness | 195 | Phase-2B lifecycle contracts retained |
| LobbyExperienceLifecycleHarness | 340 | Phase-2B callbacks/runtime ownership retained |
| WarpHarness | 118 | Existing domain/persistence/navigation contracts |
| NavigatorSecurityHarness | 78 | Existing ownership/security/access contracts |

The command fixture uses real WarpService/WarpConfig/MessageService and an existing external Paper registry fixture for material completion. Its controlled legacy-material conversion is a test boundary, not production behavior; native Paper additionally checks real material completion. No production behavior was changed to make a standalone registry-free test pass. ModuleRuntimeFixture only gained an optional real root name and a missing-command switch; existing callers keep the default settings root.

## Compiled negative controls

Each variant was compiled in its own directory, prepended on the classpath and required to fail with the intended AssertionError. A compile failure was not accepted. Working production sources were never overwritten; ordinary-classpath positive tests passed afterward (297 / 67 checks).

| Control | Compiled mutation | Detection |
|---|---|---|
| N1 | Drop ORDER only from root completion | `root completion retains every action in stable order` |
| N2 | INFO dispatch sends unknown instead of executing | `[INFO, alpha] expected Order: 17, got Unknown subcommand 'INFO'` |
| N3 | Remove first runtime permission guard | `permission before dispatch/help/grammar warp/help` |
| N4 | Remove tab-completion permission guard | `no protected ID suggestions` |
| N5 | Retain the old completer instead of clearing it | `Bukkit fallback/completer cleanup` |
| N6 | Reject an eligible Player before SET | `[SeT, ALPHA] expected created, got Only players can set warp positions` |

**6/6 compiled, 6/6 detected (exit 1); positive restoration exit 0.** Temporary variants did not touch real server files.

## Official verification and native Paper

**Maven clean package PASS; Spotless PASS (same six-file allowlist); 184 discovered/executed/passed harnesses, 28,714 checks; 0 failures, 0 missing/invalid; official exit 0.**

All 182 baseline entry points are retained; the two new entries are WarpCommandContractHarness and WarpModuleLifecycleHarness. Check changes are recorded below; the unchanged CatalogUsageHarness performs 12 fewer literal call-site checks because nine descriptions and three section headings now flow through the enum; actual generated EN/DE help, exact English section order, all description texts and suggest-command syntax are covered by the behavior tests. Other unchanged audits add checks for the new source files/links. The final total is taken from the actual receipt, not inferred from the new harness sizes.

- `dev.vapee.core.i18n.CatalogUsageHarness`: 3382 -> 3370
- `dev.vapee.core.lobby.warp.command.WarpCommandContractHarness`: 0 -> 297
- `dev.vapee.core.lobby.warp.WarpModuleLifecycleHarness`: 0 -> 67
- `dev.vapee.core.message.CommandHelpHarness`: 85 -> 87
- `dev.vapee.core.permission.PermissionDescriptorHarness`: 883 -> 884
- `dev.vapee.core.repository.RepoLinkHarness`: 34 -> 40
- `dev.vapee.core.ui.GuiAuthoringAuditHarness`: 214 -> 216

Canonical command, with the existing installed JDK 21 and IntelliJ Maven:

```powershell
pwsh -NoProfile -File scripts/verify.ps1 -Offline -MavenRepository 'C:\Users\mehdi\.m2\repository'
```

The six-file Spotless 2.43.0 / GJF 1.22.0 AOSP allowlist and Git rules are unchanged. Only the three newly created Java files were individually formatted using the same pinned GJF/AOSP. Existing files received bounded local edits, not a general formatter pass.

Native smoke: isolated copied **Paper 1.21.11-132-c5eb079**, **LuckPerms 5.5.84**, **JDK 21.0.12.1**, loopback **25586**, disposable new normal world and separate plugin data. Tested JAR SHA-256 `237a69d984315aea49cfff61d9da66c2308d89f8c6a04716fc717d9d2dbbb547`. Operator `warps.yml`, profiles, language files and deployed JAR were not changed.

At **20:30:39 Europe/Berlin, 2026-10-21** the temporary probe reported **`PHASE03A_NATIVE_PASS checks=27`**, **0 ERROR/SEVERE**, 28 modules enabled and normally disabled, then clean server shutdown. It exercised real PluginCommand binding/execution/completion, authorized help, denied executor calls for all ten actions, descriptor denial, root/ID denial, unchanged denied file bytes, console SET rejection, native ServerPlayer/CraftPlayer SET, disposable name/icon/hide/show/order/info/list/remove, contextual hidden-ID/material suggestions, binding cleanup and fresh re-enable/help. No command implementation was replaced by a probe. This is automated native acceptance, not a real-client visual test.

Temporary server data/plugins/variants are removed after collecting evidence. The test server is stopped.

## Documentation and changed files

Production (3):

- `src/main/java/dev/vapee/core/lobby/warp/command/WarpSubcommand.java` (new)
- `src/main/java/dev/vapee/core/lobby/warp/command/WarpCommand.java`
- `src/main/java/dev/vapee/core/lobby/warp/WarpModule.java`

Tests (4):

- `src/test/java/dev/vapee/core/lobby/warp/command/WarpCommandContractHarness.java` (new)
- `src/test/java/dev/vapee/core/lobby/warp/WarpModuleLifecycleHarness.java` (new)
- `src/test/java/dev/vapee/core/message/CommandHelpHarness.java`
- `src/test/java/dev/vapee/core/module/ModuleRuntimeFixture.java`

Docs/report (5):

- `docs/COMMAND_DEVELOPMENT.md` (new practical guide)
- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/DEVELOPER_GUIDE.md`
- `reports/architecture-modernization/PHASE_03A_COMMAND_ARCHITECTURE.md` (new)

The guide covers all requested categories, root/subcommand/permission changes, safe completion/help, console/online/offline targets, lifecycle, focused vs mandatory full tests and a contributor checklist. `docs/PERMISSIONS.md` required no correction and remains unchanged. Historical reports remain unchanged.

Git diff summary: **12 files changed, 1,050 insertions, 80 deletions**. Five files are new (metadata enum, two harnesses, practical guide and this report). No generated/operator file is included.

## Prioritized handoff (not implemented)

Phase 3B - Settings Architecture & Extension Patterns:

1. Map SettingsMenuEntries and visibility entries to PlayerSettingsService mutation/default/persistence contracts; preserve the Phase-2B atomic startup, exact inventory bindings and status-pane layouts.
2. Define the smallest supported new-setting recipe across domain, menu composition, localization, command visibility paths and tests; measure duplicate definitions before adding metadata. Do not force GUI entries through the Warp command enum.
3. Add an independent synthetic-setting extension proof and preserve committed-state rerender/security behavior. Friend/Clan late menu-observer startup is a separate recorded lifecycle follow-up, not silently included here.

Phase 3C - Configuration Architecture & Evolution:

1. Audit ConfigService/CoreConfigState, feature immutable snapshots and ConfigEvolution's fixed operator allowlist; document one value's path through default, validation, prepare/apply/rollback and tested additive evolution.
2. Keep operator schemas/versions and exact-byte backup/unknown-key/comment preservation explicit. Separate resource defaults from installed live values; preserve reload atomicity.
3. Retain the distinction between operator configuration and `warps.yml`/player/friend/clan/moderation persistence. Add extension/failure proofs at the owning parser; do not introduce a global config registry or persistence redesign merely for uniformity.

## Git completion and limits

One local commit: `Standardize command extension patterns`; its actual SHA is returned in the final result. Final working tree checked clean. Push: no. Merge: no. New gameplay features: none. No next phase started.

The pilot is intentionally Warp-local. Specialized target/relationship grammars and other modules' remaining lifecycle gaps are documented rather than migrated. Tests cover the selected runtime exceptions and the public action contract, not every hypothetical provider/VM failure. No real-client visual test was performed.
