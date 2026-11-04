# Phase 36 – Developer Experience and Config Evolution

Status: Phase 36 complete. Final build/deploy and both live smokes passed. Local delivery is one commit with subject `Phase 36 - Developer Experience and Config Evolution`; no push or merge.

## Baseline and scope

Exact baseline: `eacc618ad6ac4fc790f8f37c6b56b32c02537117`, initially clean `master`.
Working branch: `phase/36-developer-experience-config-evolution`.
One worker, original checkout, no additional worktree, push, merge, rebase or Phase 37 work.
Baseline discovery/execution: **126 harnesses, 20,690 checks, all passed, zero failures or missing/invalid results**.
All 507 baseline tracked files received raw SHA-256 receipts before changes; live YAML copies/hashes were retained under the ignored `dev-server/.vapeecore-dev/` evidence directory.

## Targeted readability audit

This is a focused customization audit, not a new domain/security refactor. Before production edits, the lobby item types, config/state owner, item creation/displacement, module wiring, and all seven menu implementations were inspected alongside README, Developer Guide, formatting, resources and UI helpers. Follow-up inspection covered lifecycle listeners, current click gates, native inventory tools and protected domain/persistence seams.

| Category | Evidence and decision |
|---|---|
| A – high-touch customization | Lobby material/name/lore/slots were embedded in item factories. Move presentation into `lobby.yml`, validate immutable definitions and keep identity/actions in Java. Seven menu layouts already mostly used shared constants; document their grids and name remaining empty/overview slots. Replace phase-led README and front-load concrete customization paths in the guide. |
| B – internal domain | Friend, Clan, Quest and Activity state transitions remain in their feature services. No rewriting of large mutation methods or new abstraction layers. |
| C – security-sensitive | Owner/holder/active-inventory and UUID mapping, current permission checks, staff hierarchy, native rank-color boundary and authorization remain intact. Reload item refresh alone gains loaded-player/no-activity eligibility in existing LobbyExperience integration, plus lobby/NORMAL/primary-thread gates. |
| D – persistence-sensitive | Existing `SafeFileWriter`, player repositories and Friend/Clan/Moderation schemas remain unchanged. New operator-only evolution has its own explicit allowlist and backup contract; no directory discovery or generic persistence migration. |
| E – already clear | `UiItemSpec`, `UiItems`, `Pagination`, named Settings/Visibility controls, Invsee snapshot constants, Blackjack hotbar constants and explicit 27-module registration already provide useful local boundaries. Retain them. |

The actual `LobbyItemType` contains only **NAVIGATOR, VISIBILITY, SETTINGS**. There is no Friends lobby item. The conditional Friends-head request is therefore not applicable: no type/action is invented. Self-head support and the YAML-only exercise use the existing Navigator; `/friend` and its GUI remain available.

## Lobby and GUI result

`LobbyItemsConfig` parses `items.navigator`, `items.visibility`, `items.settings` from the existing typed `LobbyConfig` snapshot. Each has enabled, hotbar slot, material, MiniMessage name/lore and head-owner. Visibility retains separate `filtered` presentation for the persisted off state. Defaults preserve slots 0/4/8, COMPASS/LIME_DYE/GRAY_DYE/COMPARATOR and current text.

Slots must be integers 0..8. Invalid inputs warn and use internal defaults. Enabled duplicate slots are resolved in stable enum order to an unused default or free hotbar slot. Invalid/non-item/air materials, malformed text or wrong types fall back without rewriting operator input. Lore lists preserve blank lines. Text is explicitly non-italic.

`PLAYER_HEAD` plus `head-owner: self` assigns the online player's existing native profile; no profile completion, network request, custom texture URL or async callback is introduced. Other materials ignore the head option. PDC `lobby_item` and type IDs still control recognition, cleanup and actions. Applying definitions removes only own marked items and safely displaces foreign target items into free non-reserved storage. Full storage preserves the foreign item and skips placement. Visibility refresh replaces only an empty slot or its own VISIBILITY item. Repeated application/rejoin cannot duplicate items; disabled items are removed on reconciliation.

The existing Lobby reload participant publishes the new typed snapshot and reconciles eligible inventories; rollback reapplies the previous snapshot. Reload does not clear inventory. BUILD, offline, foreign-world, unloaded and active-activity players are skipped. The integration guard is fail-closed until LobbyExperience is enabled and cleared at disable. Normal entry/exit, build semantics, activity state machines and item actions are unchanged.

Seven menus were audited: Settings, VisibilitySettings, VisiblePlayers, Friend, Clan, Navigator and DailyQuest. Each now has a local zero-based layout block. Remaining empty-state slot 22 and Clan overview slots 20/22/24 have semantic names. Public control constants, footer/content boundaries, Pagination, common item rendering, holder target mappings and current click gates remain shared and unchanged. There is no menu DSL or universal GUI framework. Invsee/native Ender Chest and Blackjack hotbar were also reviewed; their existing named layouts remain unchanged.

## Operator configuration evolution

The complete explicit registry is:

| Managed file | Previous schema | Supported schema |
|---|---:|---:|
| config.yml | unversioned / 0 | 1 |
| lobby.yml | unversioned / 0 | 1 |
| chat.yml | unversioned / 0 | 1 |
| private-messages.yml | unversioned / 0 | 1 |
| presentation.yml | unversioned / 0 | 1 |
| daily-quests.yml | unversioned / 0 | 1 |

These all first acquire an operator schema in this release, which justifies version 1 for each rather than invented historical versions. Later releases deliberately bump only the affected file's registry/resource version. `ConfigEvolution.evolveAll()` runs once at the beginning of `VapeeCore.onEnable`, before loaders/modules. Missing files are left to existing default creation; a fresh install needs no migration backup. `/core reload` retains the same six participants and performs no evolution writes.

The production merge uses the **already provided SnakeYAML node API**. A local probe proved Bukkit preserves comments but drops unknown null entries on serialization. Node composition/serialization keeps comments, null, empty maps/lists, quoting and ordinary aliases. Missing mapping keys are added recursively. Existing values, complete lists, unknown keys and operator comments remain. Type conflicts warn rather than overwrite; feature loaders retain fallback policy. Only `config-version` is advanced. Candidate parsed semantics are checked against the original before disk writes. Duplicate/non-string keys in merged mappings, unsupported YAML merge keys and recursive aliases are rejected without a lossy rewrite.

An older supported file gets a unique CREATE_NEW exact-byte backup at `backups/config/<file>.v0-to-v1-<time>-<uuid>.bak` **before** candidate writing. The sibling temporary is byte-verified and parsed before replacement, with a check against concurrent operator edits. Atomic replace is preferred. Unsupported atomic move uses a warned recoverable replacement; failed fallback restores from the permanent backup. Temporary files are cleaned, backups retained. Failed writes prevent startup from publishing modules and keep original/backup recoverable.

Supported versions are no-ops, even if an operator deleted individual keys: no rewrite and no extra backup. Future or malformed version fields warn and remain byte-identical; there is no downgrade. Missing defaults for later schema changes require an intentional version bump.

Explicitly excluded: `warps.yml`, `blackjack.yml`, `players/**`, `friends.yml`, `clans.yml`, `moderation.yml` and all other runtime data. Existing schema-version handling remains separate. The implementation does not glob or recursively enumerate live files for migration.

## Documentation and usability exercises

README now explains the plugin publicly: capabilities, target requirements, installation/update, six operator configs versus persistence, YAML lobby customization, development commands, existing IntelliJ workflows, limited roadmap and documentation links. The phase chronology is removed from the product entry page; historical reports remain untouched. Requirements match current pom/descriptor and local runtime: Java 21, Paper 1.21.11, LuckPerms 5.5.x. Existing daily defaults remain disabled with an empty catalog; free-play Blackjack is not represented as betting. Roadmap only names discussed staff interaction/teleport consent and optional rank badges.

Developer Guide begins with **Common Changes / Where do I change this?**, exact live/resource/source paths, local menu/Listener guidance and feature package navigation. Config versus persistence is explicit. Outdated manual-new-key wording is replaced with version/backup/merge behavior; existing operator formats still require deliberate edits to change existing values. FORMATTING gets the same bounded explanation; PERMISSIONS and historical reports are unchanged.

| Exercise | Result |
|---|---|
| A – lobby material using YAML only | Friends item absent as explained above. Production parser/service test changes only `items.navigator.material` to CLOCK; material changes while slot/PDC/action stay Navigator. Separate PLAYER_HEAD/self test verifies cached profile assignment and name/lore. No Java edit needed. |
| B – one Settings layout value | Isolated positive build changes only SCOREBOARD_SLOT 10→11, recompiles menu/Listener/alignment harness, then all six alignment checks pass. Production slot restored/unchanged. |
| C – old config upgrade | Production disk harness and real retained-dev-server upgrade evidence below; custom values/backups checked without deleting the plugin directory. |
| D – external README review | Entry page answers what the plugin does, requirements, installation, customization location and build/test workflow without needing phase history. All listed paths/actions correspond to actual source/scripts. Repository links are validated by RepoLinkHarness. |

## Regression and isolated controls

Focused run including the separately captured RepoLink check: **53 harnesses / 5,707 checks**, all exit 0. Includes all six config owners, lobby/config persistence, UI/menu security/lifecycle, Settings/Visibility, Friend/Clan GUI, daily menu, permissions, ReloadService, native nametag/color and repository persistence checks.

Four new production-path harnesses cover ConfigEvolution (81 checks), LobbyItemsConfig (32), LobbyItemRefresh (9) and SettingsLayoutAlignment (6). They use actual production merge, real disk writes and service paths; only external Bukkit/filesystem boundaries are supplied by fixtures. Cases include custom scalar/boolean/nested/unknown/null/list/comment values, type conflicts, future/invalid versions, invalid YAML/duplicates/recursive aliases, ordinary aliases, idempotency, all six real resources, excluded persistence, candidate write failures, failed/successful non-atomic fallback, concurrent edits, custom head/slot/material/name/lore, disabled items, invalid inputs, safe displacement/full inventory, PDC cleanup, repeat application, reload ownership and layout/action alignment.

The retained VisibilityModuleHarness still has all its checks; its text-default lookup follows the moved implementation to `LobbyItemsConfig`. The first full run correctly exposed that outdated source location; after correction the same harness passes 22 checks. No baseline harness source/loop was removed or weakened. PermissionDescriptor changes from 843 to 846 checks through current source inventory; command/permission counts do not change. RepoLink remains unmodified and checks every current active Markdown link: its count changes from 23 to 16 because obsolete README phase-report links were removed. This is an input-count change, not disabled link coverage.

| Isolated negative control | Detection |
|---|---|
| Overwrite an existing value with a default | Production preservation validator rejects candidate; ConfigEvolutionHarness exits 1. |
| Add warps.yml to operator migration registry | Exact-six/persistence allowlist assertion fails, exit 1. |
| Always rewrite current-version files | Second-migration/idempotency assertion fails, exit 1. |
| Move scoreboard rendering without moving click slot | Named rendered-slot alignment assertion fails, exit 1. |
| Overwrite a foreign hotbar item | Safe displacement/preservation assertion fails, exit 1. |

All five mutants compile in isolated ignored directories, are detected by the expected oracle and are removed. Production source SHA-256 values before/after controls match. Positive one-value layout control passes six checks.

Final canonical discovery/execution: **130 discovered, 130 executed, 130 passed; 20,814 checks; 0 failures, 0 missing/invalid**. All 126 baseline harnesses remain executable and pass, plus four new harnesses (128 new checks). The current baseline cohort contributes 20,686 checks after the +3 source-inventory / -7 obsolete-link changes described above. Counts come from captured process receipts, never hardcoded success output.

## Protected contracts and Full Audit

Required invariants remain **37 root commands, 50 permissions, 15 positive child edges, 27 modules, six reload participants**. No new command, permission, module or reload participant is introduced. Native rank-color code, formatting/domain authority, staff hierarchy, PDC ownership, inventory holder bindings and persistence implementations remain unchanged; only the documented lobby integration and presentation surfaces change. Presentation resource edits add schema/comments only; every existing presentation key/value stays equal.

All **20/20** closed Full Codebase Audit findings retain their current regression coverage: MOD-001 and SEC-001 (moderation and current menu authorization); DISPLAY-001/LIFE-002 (display/seat/blackjack cleanup); PERSIST-001/002 (lobby/player schema persistence); GUI-001/002 (UI/pagination/current menu binding); LIFE-001 (module cleanup); CONFIG-001 (config owners/helpers); CMD-001/DUP-001 (commands/identity); API-001 (read views); PRES-001/002 (presentation/native colors); PERF-001 (replacement indices); TEST-001/002 (real reload and mandatory runner/deploy gate); DOC-001 (current guide/links/inventories); LEGACY-001 (active Blackjack/domain coverage). Remaining findings: 0. This preserves the resolved audit baseline, without opening a new audit-finding phase.

## Final build, live upgrade and file inventory

The exact requested standalone offline Maven `clean package` passed, followed by the unchanged official `scripts/build-and-deploy.ps1 -Offline -MavenRepository C:\Users\mehdi\.m2\repository` mandatory clean-build/regression/deploy gate. No bypass flags or workflow edits. The developer JDK is `C:\Program Files\Java\jdk-21.0.12.1`; local Paper is 1.21.11-132-c5eb079 and LuckPerms 5.5.84. Phase 37 not started.

Final artifact: `target/vapeecore-1.0-SNAPSHOT.jar`, **1,111,787 bytes**.
SHA-256: `A2661FD12EE308987C25E6111F5D21AFB7106DF188592C5DAB96DF1DBADDEEE1`.
Deployed artifact `dev-server/plugins/vapeecore-1.0-SNAPSHOT.jar` has exactly the same size/hash.

The first real smoke retained the existing plugin directory and all ten live YAML paths. A stopped-server copy of the actual unversioned operator configurations was the controlled legacy fixture; no replacement of the live folder or deletion was used. Independent parsed-value audit confirmed every original value, list and unknown key still exists after migration, including the custom lobby spawn and existing nametag/clan templates. Six exact original-byte backups match those copies. Only six allowlisted operator files changed; the existing persistence files `blackjack.yml`, `warps.yml`, `clans.yml`, `friends.yml` remained byte-identical. No player/moderation file was generated. Absent persistence remains absent.

The intentional additions in each of config/chat/PM/presentation/daily are only `config-version: 1`. Lobby gains `config-version: 1` and the `items` subtree: enabled, slot, material, name, lore, head-owner for navigator/visibility/settings, plus material/name/lore/head-owner under visibility.filtered. All previously configured presentation values remain equal. The backup files under `dev-server/plugins/VapeeCore/backups/config/` are:

| File | Exact-byte backup |
|---|---|
| chat.yml | `chat.yml.v0-to-v1-1791383682788-fa16b2f6-6dd9-4476-9e71-dfdabb4352df.bak` |
| config.yml | `config.yml.v0-to-v1-1791383682804-930a47dd-1fa6-4633-a69b-fdca232d5c87.bak` |
| daily-quests.yml | `daily-quests.yml.v0-to-v1-1791383682814-2ddb1558-ba36-4e50-9262-3bf894adf84b.bak` |
| lobby.yml | `lobby.yml.v0-to-v1-1791383682825-cfd9e44a-4137-40de-9303-ad642eeec259.bak` |
| presentation.yml | `presentation.yml.v0-to-v1-1791383682837-6dfed41d-3904-43cf-aac2-cc364d67816e.bak` |
| private-messages.yml | `private-messages.yml.v0-to-v1-1791383682849-a962c588-f6aa-4e23-bde2-ebcfbcacd93c.bak` |

| Real Paper smoke | Upgrade | Subsequent normal start/reload/stop |
|---|---|---|
| Time (Europe/Berlin, 2026-10-07) | 16:34:07–16:34:47 | 16:35:46–16:36:04 |
| Managed wrapper / Paper exit | 0 / clean shutdown | 0 / clean shutdown |
| Modules enable/disable | 27 / 27, exact reverse | 27 / 27, exact reverse |
| Descriptor command roots | Exact 37 | Exact 37 |
| Reload participants | Exact six, successful transaction | Exact six, successful transaction |
| ERROR / SEVERE | 0 | 0 |
| Existing YAML paths | Exact same ten | Exact same ten |
| Byte-identical live YAMLs | Four persistence files; six intentional migrations | All ten files |
| Backup count | Six unique originals | Still six; every backup hash equal |
| Remaining config temporaries | 0 | 0 |

Both runs exercise `core`, `core version`, `core help`, `core reload`, `bukkit:help VapeeCore`, `version`, `lp info`, `list`, then the official clean stop. No environment/world error required a repeat. The final server is stopped. Client head/layout behavior is verified through production item/menu harnesses; these console smokes do not claim a new human client visual test.

Evidence receipts remain in ignored `dev-server/.vapeecore-dev/`: `phase36-baseline-*`, `phase36-focused-results.json`, `phase36-final-regression-results.json`, `phase36-negative-control-results.json`, `phase36-final-maven-clean-package.log`, `phase36-final-official-build-deploy.log`, `phase36-upgrade-semantic-audit.log`, `phase36-upgrade-*`, `phase36-idempotent-*`, `phase36-final-artifact-backup-audit.json`, `phase36-protected-source-audit.json`. Historical evidence files were not overwritten.

Complete final diff/source review covers every added and modified file, operator-versus-persistence scope and active documentation. Raw hash comparison of the 507 baseline tracked files finds changes only in the 23 listed paths below. All **22 historical report files are byte-identical**. `plugin.yml`, pom, scripts/IntelliJ configurations, native rank/color/presentation Java, player/Friend/Clan/Moderation persistence, domain state machines, Economy, reward processing, visibility authority, reload transaction and common UI helpers are unchanged. The only Friend/Clan Java edits are local menu layout constants/comments; the only player-state integration is gated lobby item reconciliation.

### Added (8)

- `reports/36-developer-experience-config-evolution/DEVELOPER_EXPERIENCE_CONFIG_EVOLUTION.md`
- `src/main/java/dev/vapee/core/config/ConfigEvolution.java`
- `src/main/java/dev/vapee/core/lobby/config/LobbyItemsConfig.java`
- `src/main/java/dev/vapee/core/lobby/item/LobbyItemDefinition.java`
- `src/test/java/dev/vapee/core/config/ConfigEvolutionHarness.java`
- `src/test/java/dev/vapee/core/lobby/item/LobbyItemsConfigHarness.java`
- `src/test/java/dev/vapee/core/lobby/player/LobbyItemRefreshHarness.java`
- `src/test/java/dev/vapee/core/settings/SettingsLayoutAlignmentHarness.java`

### Modified (23)

- `README.md`
- `docs/DEVELOPER_GUIDE.md`
- `docs/FORMATTING.md`
- `src/main/java/dev/vapee/core/VapeeCore.java`
- `src/main/java/dev/vapee/core/clan/gui/ClanMenu.java`
- `src/main/java/dev/vapee/core/friend/gui/FriendMenu.java`
- `src/main/java/dev/vapee/core/lobby/LobbyModule.java`
- `src/main/java/dev/vapee/core/lobby/config/LobbyConfig.java`
- `src/main/java/dev/vapee/core/lobby/experience/LobbyExperienceModule.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenu.java`
- `src/main/java/dev/vapee/core/lobby/item/LobbyItemService.java`
- `src/main/java/dev/vapee/core/lobby/player/LobbyPlayerStateService.java`
- `src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestMenu.java`
- `src/main/java/dev/vapee/core/settings/SettingsMenu.java`
- `src/main/java/dev/vapee/core/settings/visibility/VisibilitySettingsMenu.java`
- `src/main/java/dev/vapee/core/settings/visibility/VisiblePlayersMenu.java`
- `src/main/resources/chat.yml`
- `src/main/resources/config.yml`
- `src/main/resources/daily-quests.yml`
- `src/main/resources/lobby.yml`
- `src/main/resources/presentation.yml`
- `src/main/resources/private-messages.yml`
- `src/test/java/dev/vapee/core/visibility/VisibilityModuleHarness.java`

Removed: **0**. Total changed files: **31**.

The review groups are operator evolution/resources (allowlist and version boundary), lobby display/inventory integration, local GUI layout readability, production-path regression, and current documentation/report. No ignored dev-server receipts, fixtures, probes, binaries or live backups are included in the commit.
