# Phase 37 – Feature Extension & Code Discoverability

Status: complete; implementation, correction regression, build/deploy, migration, Paper smoke and corrected real client acceptance passed.

## Guard and baseline

PHASE37_BASELINE: `b3b4a05f8b216c452332064382a059691d8230dd` (actual master merge HEAD, also origin/master). Phase 36 `c02ff419d6f6042a0db31168e8faec85bc8d0be6` is an ancestor. Clean starting tree. Branch: `phase/37-feature-extension-code-discoverability`. No worktree, subagents, push, merge or rebase. Unmodified canonical baseline reproduced: **130 executable harnesses, 20,814 checks, 0 failures, 0 missing/invalid**.

## Real starting problem

After Phase 36 the operator manually added `items.friends` with PLAYER_HEAD/self in lobby.yml. **The item did not load and did not appear in game.** Presentation configuration only supported the three fixed enum IDs navigator, visibility and settings. This is the confirmed starting problem, not an invented hypothetical. The actual current dev-server v1 file already contains the operator's friends entry (slot 6, name `<aqua>Freunde`), visibility slot 1, navigator slot 0 and settings slot 8. These custom values must survive migration.

## Before-extension audit (recorded before production edits)

Paths below are repository-relative, under `src/main/java/dev/vapee/core/` unless specified.

### A. Lobby item

For a conventional hardcoded FRIENDS addition the actual source requires:

1. `lobby/item/LobbyItemType.java`: enum ID/fromPersistentId authority, three entries.
2. `lobby/config/LobbyItemsConfig.java`: enum-keyed defaults and enum iteration; type-specific filtered appearance.
3. `lobby/experience/LobbyItemListener.java`: central type switch, current feature actions and sound handling.
4. `lobby/experience/LobbyExperienceModule.java`: constructor injection/open-menu dependency, listener construction.
5. `VapeeCore.java`: inject FriendModule into LobbyExperienceModule.
6. `friend/FriendModule.java`: expose its existing FriendMenu (currently only FriendService exposed).
7. `src/main/resources/lobby.yml`: add presentation defaults and bump version.
8. `config/ConfigEvolution.java`: deliberately raise only lobby schema support.

Source audit refinement: LobbyItemService would automatically iterate an added enum member once its parser default exists; a conventional FRIENDS-only addition would **not** need an extra service branch. Its enum iteration is nevertheless a fixed-ID architecture constraint. LobbyConfig currently carries `Map<LobbyItemType, LobbyItemDefinition>` through read/state/reload; expanding the existing enum alone would not require changing its field type. LobbyModule supplies `LobbyConfig::getItems`. LobbyPlayerStateService owns NORMAL/BUILD inventory and gates reload refresh against profile/activity ownership. No cleanup switch exists; cleanup already uses PDC ownership. Exactly **three central routing/default authorities** are item enum, parser/defaults and listener, with service enum iteration as a fourth integration constraint. All enum callers have been enumerated: five production files and three harnesses, no persistence schema or public command references.

### B. Settings tile

`settings/SettingsMenu.java` declares icon/status slot constants, renders each icon and each status separately in refresh; `settings/SettingsListener.java` maps both slots to a feature slot, then a second switch chooses the PlayerSettingsService mutation. Scoreboard separately triggers presentation refresh. Permission is checked at SettingsMenu.open and every active-holder click (`SettingsCommand.PERMISSION`); missing profile closes safely. Current state is `player/settings/PlayerSettingsService.java` / PlayerSettings, backed by PlayerService. A new tile needs the menu layout/render and both listener routes, plus its real domain setter. This duplicated UI mapping is the scoped Settings extension seam to fix.

### C. Seven GUI extension paths

| Surface | Display / target authority | Action routing / current security | Existing extension work |
|---|---|---|---|
| Settings | SettingsMenu.refresh, bound SettingsInventoryHolder | SettingsListener, current permission/profile/active inventory | Menu + two listener mappings + domain action |
| VisibilitySettings | settings/visibility/VisibilitySettingsMenu.render, fixed filter/status slots | VisibilitySettingsListener click-to-Toggle and toggle service switch; settings permission/profile, save then visibility apply | Two UI files plus real filter state/service; special unavailable game marker retained |
| VisiblePlayers | VisiblePlayersMenu.entries/entryItem; VisiblePlayersHolder slot→UUID | VisiblePlayersListener.navigate/remove; current permission, active/bound owner, PlayerSettingsService | Two UI files for action, existing UUID list authority |
| Friend | friend/gui/FriendMenu entryItem/footer; FriendMenuHolder slot→UUID and view | FriendMenuListener.navigate/view-specific action/service calls; FriendMenu.open and click check current FriendCommand.PERMISSION | Two UI files for a GUI action; domain service only if action is new |
| Clan | clan/gui/ClanMenu overview/entries; ClanMenuHolder clan ID + slot→UUID | ClanMenuListener.navigate/mutate; current permission, current clan ID, service owner/member authority | Two UI files; service handles new domain operation |
| Navigator | lobby/experience/navigator/NavigatorMenu.createWarpItem; NavigatorInventoryHolder slot→warp ID | NavigatorListener and WarpService.teleportFromNavigator; NavigatorAccessPolicy rechecks loaded NORMAL lobby/no activity | Existing warp config/service adds destinations without GUI router changes; new action uses menu + listener + service |
| DailyQuest | quest/daily/menu/DailyQuestMenu.questItem; holder owner/page, read-only entries | DailyQuestMenuListener footer; current permission/profile and DailyQuestService synchronization | Two UI files for footer action; new quest behavior belongs to quest domain, never inferred from display text |

Friend/Clan target UUIDs, Navigator warp IDs and domain authority remain separate; no universal GUI refactor is warranted. VisibilitySettings uses a domain Toggle identifier already; this phase does not redesign its distinct filter semantics.

### D. Config, command and module extension

Operator config: `src/main/resources/<file>.yml` → explicit version in `config/ConfigEvolution.MANAGED_CONFIGS` → feature typed config (LobbyConfig.readState/State, generic LobbyItemsConfig only for registered item presentation) → module/service consumer. Startup migration recursively adds missing values with exact backups, preserves comments/scalars/lists/null/unknown keys, refuses future versions and avoids persistence. Reload remains six prepared participants, no migration writes during reload.

Existing feature command: add command executor in feature command package, register descriptor/permission in plugin.yml only if needed, wire executor/tab completer in feature Module.enable with failure/disable cleanup, document permission and harness behavior. New module: implement module/CoreModule, constructor-inject dependencies, explicitly construct/register in VapeeCore before enableAll. ModuleManager enables in registration order, rolls back enabled modules and disables in reverse order. Friend is enabled before Lobby; Settings and Warp before final LobbyExperience. Menus are module-owned; registry actions must fail closed before runtime availability and after teardown.

## Final lobby architecture

`LobbyItemRegistrations.register` is the single explicit Java list: navigator, visibility, settings, friends. Registration supplies stable ID, safe Java presentation fallback, feature adapter and optional per-player appearance resolver. `LobbyItemRegistry` validates lowercase stable IDs (1..64 characters, letters/digits/hyphen), rejects duplicates, seals before LobbyConfig initialization and gates behavior until the last LobbyExperience module activates it. Constructor injection/order remains explicit, 27 modules unchanged. Menu suppliers bridge this existing enable order; there is no service locator, reflection, scanner, YAML action DSL or new framework.

LobbyConfig retains immutable State but items are `Map<String, LobbyItemDefinition>`. `LobbyItemsConfig` iterates registered entries, never a built-in enum/list. Unknown YAML IDs warn with the offending `items.<id>` and are omitted. Presentation fields only: enabled, slot, material, name, lore, head-owner, plus the existing Visibility filtered appearance. action/command/class/method fields have no behavior authority. Normal MiniMessage implicit color closing now supports the actual operator's `<aqua>Freunde` and the required synthetic `<aqua>Synthetic`; invalid field types still warn/fall back. Existing join/quit template validation is unchanged.

LobbyItemService iterates the same registry and definition IDs, creates native ItemStacks, assigns an online cached profile only for self/SkullMeta, writes `vapeecore:lobby_item` STRING, reconciles managed items and safely displaces foreign inventory. There is no per-ID builder or action switch. `refreshItem(player, id)` is generic; the old `refreshVisibilityItem` is just an existing-caller wrapper and does not route behavior. Cleanup remains namespace/PDC-based, including obsolete/unknown owned IDs. Full foreign inventory remains untouched if no safe storage slot exists.

LobbyItemListener cancels owned item movement, checks lobby/main-hand/right-click and online/loaded/NORMAL/no-activity ownership through the existing NavigatorAccessPolicy, reads only registered PDC identity, checks current enabled presentation and invokes the Java registry. No built-in switch. A true immediate action result triggers existing preference-gated UI sound; failed/unavailable/denied opens cannot sound. Registry deactivate precedes feature teardown. Failed LobbyExperience startup clears suppliers, eligibility, listeners and menus. Deferred Visibility block clicks recheck active registry/current enabled item and lobby ownership before saving.

### LobbyItemType decision

Removed the redundant enum after enumerating every caller: five production files (including the enum itself), three harnesses, no descriptor/persistence contract. Registry is the single valid-ID authority. Existing navigator/visibility/settings PDC strings remain identical. Tests now exercise String IDs and real registry resolution rather than extending an enum. No ID-specific compatibility router was retained.

### Productive Friends

Java registration `friends` → `friend/gui/FriendsLobbyItemAction` → existing `FriendMenu.open`. Default slot **1**, verified free in the bundled 0/4/8 layout; PLAYER_HEAD, self cached online profile, aqua `Freunde`, lore `Verwalte deine Freunde`, blank line, `Klicke zum Öffnen`. The existing operator's friends slot **6** survives migration (visibility stays 1). No new command, permission or module.

FriendMenu's existing current `FriendCommand.PERMISSION` (`vapeecore.friend.use`) remains the sole Friend open gate; the adapter contains no parallel permission check and never bypasses the menu. Menu.open now returns whether native opening actually succeeded, allowing safe feedback. Unauthorized clicks reuse the existing denial, cause no Friend save/mutation, sound or foreign inventory close. Existing Friend listener still checks current permission and holder target UUIDs for retained GUI actions. SettingsMenu also reports actual open success without moving its permission/profile checks.

## Settings extension seam and GUI audit outcome

SettingsMenuEntry binds icon slot, optional status slot, renderer functions from current PlayerSettings and the feature action. `SettingsMenuEntries.defaults` is the single six-real-entry list; Menu renders it and Listener resolves that same entry with entryAt. Duplicate/reserved/out-of-range slots are rejected. Actual setters and scoreboard refresh remain in the Settings feature; holder/active inventory/current permission/profile/click checks stay before action invocation. There is no second raw-slot switch. No new production setting.

Synthetic Settings fixture alone adds icon 28/status 37; both render and route the exact same action through the production menu/listener, and revocation blocks it. Other six surfaces remain domain-specific as in the audit table. Friend/VisiblePlayers UUID targets, Clan current clan/owner authority, Navigator Warp ID/access policy and DailyQuest read-only synchronization remain unchanged. VisibilitySettings already routes its UI to a local domain Toggle; a new actual filter necessarily has domain state/service work, so no universal registry was imposed.

## Measured extension budget and walkthrough

| Measure | Before | After |
|---|---|---|
| Conventional FRIENDS integration paths, excluding domain behavior | 8 existing production files listed in audit | Adapter + one registration point + resource/schema; explicit dependency/menu exposure when not already available |
| Per-ID generic parser/service/listener edits | Enum/default parser + listener; service automatically iterated enum | **0** |
| ID/action authority | Enum + default map + listener type switch (service constrained to enum) | **One explicit Java registration list** |
| Settings UI mapping | Menu icon/status + listener slot mapping + listener setter switch | One descriptor/action in SettingsMenuEntries |
| Isolated unseen lobby ID | Requires enum/default/listener wiring | Synthetic registration + YAML fixture only; 0 edits to five generic classes |

The original service was not falsely counted as needing a new FRIENDS case: it already generically iterated the closed enum. The benefit is eliminating closed-ID/default and click-routing edits, not claiming five historical switches. Necessary constructor injection for a newly exposed module remains visible and is counted separately from registration; it is not automatic discovery.

### Later CLANS item: concrete final-code path

1. Feature-owned adapter: follow actual `src/main/java/dev/vapee/core/friend/gui/FriendsLobbyItemAction.java` in the existing `src/main/java/dev/vapee/core/clan/gui/` feature package, implementing actual `lobby/item/LobbyItemAction`. Reuse the existing Clan authorized open flow/current `clan/command/ClanCommand.PERMISSION`; ClanMenu itself currently relies on the command open gate and its listener click gate, so do not blindly imitate Friend's direct menu call without supplying that existing feature authorization. No Clan work was implemented in this phase.
2. Registration file: `src/main/java/dev/vapee/core/lobby/item/LobbyItemRegistrations.java`; add the one clans ID/default/adapter registration. Generic parser, service, listener and builder remain untouched.
3. Dependency exposure if needed: existing `clan/ClanModule.java` currently exposes only ClanService, so make the authorized opener/menu access explicit there; inject ClanModule via `lobby/experience/LobbyExperienceModule.java` and its existing construction in `VapeeCore.java`. These three predictable wiring sites are still required when the feature does not already expose/inject its opener; the guide does not hide them in a magic registry.
4. Resource `src/main/resources/lobby.yml`: add items.clans in a verified free slot. Bump only lobby supported version **2→3** in that resource and `src/main/java/dev/vapee/core/config/ConfigEvolution.java`. Add migration/authority/behavior harnesses and real client check. No clan persistence, permission engine or new command required just for lobby integration.

Developer Guide now directly covers Adding a new lobby item, Settings tile, GUI action, config key/schema, existing-feature command, new module and Feature Extension Map with real paths. README mentions Friends as a normal product feature, registered IDs and supported operator schema versions; no phase chronology.

## Config migration and preserved contracts

Only `lobby.yml` schema **1→2**. ConfigEvolution algorithm unchanged; other five schemas remain 1. The upgrade harness uses the real bundled resource: preserves navigator slot6/CLOCK/comment/unknown list, adds friends, exact-byte v1 backup; second v2 evolution is byte-identical/no new backup; existing manually configured friends keeps custom slot/name and receives missing defaults; fresh bundled v2 has friends with no migration rewrite/backup.

The 81-check ConfigEvolutionHarness retains all prior merge/failure/alias/backup/comment/list/null/unknown/future/idempotency/atomic-fallback/concurrent-edit checks. Its generic v1 custom fixture now uses chat.yml (still version1); actual bundled resources compare against each explicit registered supported version. No checks removed. Join/quit formatting and spawn save remain unchanged. Reload still reads/applies six states without schema writes.

Protected source parity: **115 baseline files byte-identical**, including player/repository, rank/presentation, moderation/social/clan, Friend service/repository/request policy/command/listener, plugin.yml and historical reports. Phase35 rank/overhead/tab/clan literal/team ownership and Phase36 migration/inventory/reload contracts remain covered by focused/full tests. Full Codebase Audit baseline **20/20 resolved, 0 open** retained; reviewed new code introduces no competing domain/security authority or unrelated persistence change. Phase38 not started.

## Regression and negative controls

- Original focused: **68 harnesses / 4,646 checks / 0 failures**; config/lobby/friend/settings/seven GUI surfaces, visibility lifecycle, descriptor/reload/RepoLink, presentation/nametag, rank and player persistence.
- Correction-focused: **5 harnesses / 115 checks / 0 failures** (real parser, registry, Friends menu, migration and Visibility).
- Final canonical runner: **135 discovered/executed/passed, 20,915 checks, 0 failures, 0 missing/invalid**. All **130** baseline classes retained.
- New production-path harnesses: LobbyItemRegistry **32**, FriendsLobbyItem **27**, SettingsEntryExtension **10**, LobbyConfigUpgrade **13**, VisibilityLobbyItemAction **10** = **92 checks**. Existing LobbyItemsConfig gains one productive PDC assertion (32→33); PermissionDescriptor automatically performs one legacy-permission guard per production Java file; nine additions minus the removed enum add eight guards (846→854), with the descriptor unchanged. Total growth **101** checks, no artificial reduction.
- Synthetic `synthetic-test` uses only Java registration and YAML fixture (slot7 DIAMOND/aqua Synthetic). It is built, placed, clicked once, updated/disabled without duplicate or foreign loss. No edits for that ID in LobbyItemsConfig, LobbyItemService, LobbyItemListener, generic parser or builder. Unknown `not-registered` YAML warns, cannot place/run even when registry is active; unknown/foreign PDC and lookalikes cannot click.
- Visibility runtime harness executes actual save/apply/hotbar refresh, native cooldown scheduling and deferred block click. Queued ownership/config/teardown changes fail closed.

Six isolated negative controls (all re-run successfully against the final corrected compiled production stand): each mutated production source compiled successfully, then the real corresponding harness failed with its expected assertion (not compilation failure). **6/6 killed**:

| Mutation | Detection |
|---|---|
| Listener hardcoded built-in whitelist | synthetic registered click cannot execute |
| YAML action executes registered Java behavior during read | active unknown configured ID executes forbidden action |
| FriendMenu current permission bypass | unauthorized real GUI opens |
| Service accepts foreign/unmarked item identity | foreign PDC accepted |
| Settings renderer moves icon, resolver stays at descriptor slot | rendered slot diverges |
| Lobby registry/resource stay version1 despite friends default | real v1→v2 upgrade contract fails |

Mutant sources/classes were confined to checked ignored receipt directories and removed after each run. No synthetic production item/settings feature or mutation remains. Recipes/logs/results stay under ignored `dev-server/.vapeecore-dev/phase37/`.

Structural invariants: **37 root commands, 50 permissions, 15 positive child edges, 27 modules, 6 reload participants**. Descriptor and domain authority unchanged; PermissionDescriptorHarness, ReloadServiceHarness and actual Paper smoke validate these contracts.

## Build, deploy, Paper and client acceptance

Required standalone Maven offline clean package succeeded before the first client test (receipt `standalone-build.log`). After the client correction, the official build/deploy repeated clean package and its complete canonical gate: **135 harnesses / 20,915 checks / 0 failures / 0 missing-invalid**, no bypass. Final artifact **1,129,693 bytes**, target and deployed SHA256 **`AEC729D0178B3ADD590890E2EA726E11A3AFD280E675E5D315CA6C1B93B80BDD`**, exact deploy parity. This corrected artifact supersedes the earlier pre-client build.

Real existing-installation Paper **1.21.11** smoke completed twice through the official managed start/stop scripts. Both: 27 modules enable, exactly reversed 27 disable, six prepared reload participants and successful completed reload, exact 37 descriptor command roots, 0 ERROR/SEVERE, clean exit0/shutdown. Commands exercised: core, core version/help/reload, Bukkit help, version, LuckPerms info and list. No player joined these automated smokes.

Upgrade: ten preexisting live YAML files retained; nine byte-identical, only lobby.yml changed. Complete raw before/after diff contains only config-version 1→2 and the missing three friends lore lines. Existing navigator0/COMPASS, visibility1, settings8 and manually configured friends6/PLAYER_HEAD/self/aqua name remain unchanged. Exact original backup SHA256 **`F6FB118FFF1FE91014909DA9E1388F98870E8A5790E096A454715A3B2BFF2336`** matches the one new `backups/config/lobby.yml.v1-to-v2-1791395861505-c35b6d9f-925e-4fec-b0b8-92912cfabb52.bak`. All six historical backup files are byte-identical. Other five evolved operator configs, the other operator/persistence files and existing player file are unchanged; absent friends.yml/moderation.yml were not created. The plugin directory was preserved.

Second startup plus reload: **all ten YAML files byte-identical**, all seven backups unchanged, no additional backup or persistence file. Both automated smoke runs stopped cleanly; the operator subsequently started the server for actual client acceptance.

Client acceptance: **passed after correction**, explicitly confirmed by the operator: “Hat nun funktioniert und der rest auch”. This answers the full corrected-build checklist: own head/skin, name/lore, Friends menu opening, slot/name/lore/material reload without duplication or foreign item loss, current denied Friend permission preventing menu/success sound and allowed permission opening normally, chosen values/permission restored, known-ID warning/ERROR/SEVERE absence. The rename-to-test/removal/reinstatement re-test also passed. Visual and permission acceptance comes from the real player's confirmation, not an inferred harness result.

Independent final client-log snapshot records four completed reloads, a player issuing reload commands, **0 ERROR/SEVERE**, **0 warnings for known IDs**, and exactly one expected unregistered items.test warning. Final live Friends configuration is enabled, **slot7** (the operator's chosen post-test slot), PLAYER_HEAD/self, aqua Freunde and the three requested lore lines. Migration had preserved original slot6; the subsequent slot7 is a deliberate operator presentation edit. Deployed JAR remains byte-identical to the tested final artifact. The server was not stopped during this final read-only observation. Receipt/log: ignored `phase37/final-client-acceptance.json` and `final-client-server.log`.

## Live client correction: removed/renamed entry

The first Phase37 client test confirmed that configured `friends` opens the existing menu. It also exposed a presentation fallback defect: renaming `friends` to unregistered `test`, or deleting the friends block, left the Java fallback Friends item enabled. Visibility already occupied fallback slot1, so collision reconciliation moved the Friends fallback to slot2 (third hotbar position); it still carried the legitimate registered friends PDC/action. The unknown `test` YAML did not gain behavior, but the unexpected resurrected Friends fallback made it look that way.

Generic correction (no friends-specific branch): when a valid explicit items section exists, a missing registered ID receives a disabled definition. Reconciliation removes the old managed item, and the existing current-enabled click gate rejects its retained PDC. No fallback reserves/moves its slot. Empty `items: {}` disables all; malformed/absent whole sections keep the original legacy fallback compatibility. Missing fields of a present known item still use typed Java defaults. Fresh installs and startup v1→v2 migration retain the complete bundled items section/defaults.

Productive and synthetic real-path harnesses cover renamed unknown ID/warning, removed managed head, stale click suppression, restoring friends at its chosen slot, empty section, and preserved foreign items. Existing checks retained with explicit-section item counts updated for this intentional policy change. Correction full and focused regression, six re-run negative controls and official build/deploy have passed; final hash is recorded above. Corrected real Paper smoke also passed: native startup, all eight console smoke commands including core reload, 27 modules enabled/reverse-disabled, six reload participants, 37 command roots, clean shutdown/exit0, ten live YAML files unchanged, 0 ERROR/SEVERE. Real client re-test now passed as recorded above, completing the final pre-commit acceptance gate.

## File inventory and final diff review

**16 added / 21 modified / 1 removed = 38 files.**

### Lobby extension architecture

| Change | Exact path |
|---|---|
| Modified | `src/main/java/dev/vapee/core/VapeeCore.java` |
| Modified | `src/main/java/dev/vapee/core/lobby/LobbyModule.java` |
| Modified | `src/main/java/dev/vapee/core/lobby/config/LobbyConfig.java` |
| Modified | `src/main/java/dev/vapee/core/lobby/config/LobbyItemsConfig.java` |
| Modified | `src/main/java/dev/vapee/core/lobby/experience/LobbyExperienceModule.java` |
| Modified | `src/main/java/dev/vapee/core/lobby/experience/LobbyItemListener.java` |
| Modified | `src/main/java/dev/vapee/core/lobby/item/LobbyItemService.java` |
| Removed | `src/main/java/dev/vapee/core/lobby/item/LobbyItemType.java` |
| Added | `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorLobbyItemAction.java` |
| Added | `src/main/java/dev/vapee/core/lobby/item/LobbyItemAction.java` |
| Added | `src/main/java/dev/vapee/core/lobby/item/LobbyItemRegistrations.java` |
| Added | `src/main/java/dev/vapee/core/lobby/item/LobbyItemRegistry.java` |
| Added | `src/main/java/dev/vapee/core/visibility/VisibilityLobbyItemAction.java` |

### Friends lobby integration

| Change | Exact path |
|---|---|
| Modified | `src/main/java/dev/vapee/core/friend/FriendModule.java` |
| Modified | `src/main/java/dev/vapee/core/friend/gui/FriendMenu.java` |
| Added | `src/main/java/dev/vapee/core/friend/gui/FriendsLobbyItemAction.java` |

### Settings extension seam

| Change | Exact path |
|---|---|
| Modified | `src/main/java/dev/vapee/core/settings/SettingsListener.java` |
| Modified | `src/main/java/dev/vapee/core/settings/SettingsMenu.java` |
| Added | `src/main/java/dev/vapee/core/settings/SettingsLobbyItemAction.java` |
| Added | `src/main/java/dev/vapee/core/settings/SettingsMenuEntries.java` |
| Added | `src/main/java/dev/vapee/core/settings/SettingsMenuEntry.java` |

### Config migration v1→v2

| Change | Exact path |
|---|---|
| Modified | `src/main/java/dev/vapee/core/config/ConfigEvolution.java` |
| Modified | `src/main/resources/lobby.yml` |

### Tests

| Change | Exact path |
|---|---|
| Modified | `src/test/java/dev/vapee/core/config/ConfigEvolutionHarness.java` |
| Modified | `src/test/java/dev/vapee/core/friend/FriendIntegrationHarness.java` |
| Modified | `src/test/java/dev/vapee/core/lobby/item/LobbyItemsConfigHarness.java` |
| Modified | `src/test/java/dev/vapee/core/lobby/player/LobbyHarness.java` |
| Modified | `src/test/java/dev/vapee/core/settings/SettingsHotbarSecurityHarness.java` |
| Modified | `src/test/java/dev/vapee/core/visibility/VisibilityModuleHarness.java` |
| Added | `src/test/java/dev/vapee/core/config/LobbyConfigUpgradeHarness.java` |
| Added | `src/test/java/dev/vapee/core/friend/gui/FriendsLobbyItemHarness.java` |
| Added | `src/test/java/dev/vapee/core/lobby/item/LobbyItemRegistryFixture.java` |
| Added | `src/test/java/dev/vapee/core/lobby/item/LobbyItemRegistryHarness.java` |
| Added | `src/test/java/dev/vapee/core/settings/SettingsEntryExtensionHarness.java` |
| Added | `src/test/java/dev/vapee/core/visibility/VisibilityLobbyItemActionHarness.java` |

### Documentation

| Change | Exact path |
|---|---|
| Modified | `README.md` |
| Modified | `docs/DEVELOPER_GUIDE.md` |

### Report

| Change | Exact path |
|---|---|
| Added | `reports/37-feature-extension-code-discoverability/FEATURE_EXTENSION_CODE_DISCOVERABILITY.md` |

Final source/diff review: every changed production file read fully; registration/config/service/listener and Settings mapping reviewed for duplicated routing, permission gates, lifecycle failure/teardown, managed ownership, disabled/unknown IDs and domain boundaries. No unrelated production/persistence/descriptor/historical report edit, discovery/reflection framework or YAML behavior authority. git diff --check passes. Protected baseline parity remains 115/115. All required pre-commit acceptance gates passed. Publication policy: exactly one local commit titled `Phase 37 - Feature Extension and Code Discoverability`; verify its SHA, baseline distance1 and clean working tree after committing, with the receipt outside tracked files to avoid a self-referential commit hash. No push/merge/rebase; Phase38 not started.
