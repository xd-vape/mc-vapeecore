# Prototype Step 02 — Blackjack Removal

Completed 2026-10-29 on `prototype/remove-blackjack-from-core`.

## Baseline and bounded result

Baseline: `1a3dade15c7dfab69b0cf96ce74af6097c73145e` (integrated master). The existing Step-01 commits `b66eaabe1b172311115a7087eed0eb4027d8f9d3`, `b0b00c2819f335ba61bc8551c4b5fea9e407209e` and `ae5db46ce47dc16e3163953d9e79725b84dfc4bb` remain unchanged. Preflight checked status, current branch/HEAD, eight commits, worktrees, full diff and applicable AGENTS.md (none found). Only the approved Run-config change existed. A new branch was created without changing master, earlier feature/recovery branches or stashes.

The full fresh baseline passed **212 canonical harnesses / 33,644 checks**, Failed=0, Missing/Invalid=0, Maven clean package PASS and Spotless PASS, before production edits. Initial restricted-shell/Maven-cache attempts did not complete verification; the authorized run supplied JDK 21 and succeeded. Historical results were not substituted for this baseline.

Blackjack is no longer shipped or started by Core. **28 → 27 production modules**, unchanged relative order, **seven unchanged reload participants**, **37 → 36 command roots**, **50 → 49 permission nodes**, unchanged 15 positive permission-child edges. One additional local commit uses the message `Remove Blackjack from VapeeCore while preserving shared services`. Its full SHA is reported by the completion message and external post-commit receipt; this document is part of that commit. No push, merge or deployment.

## Source-level removal inventory (recorded before editing)

Repository-wide Java/resource/script/doc searches included `blackjack`, the activity key, `blackjack:win`, `/blackjack`, permission, resource, owners and PDC markers. Before-edit inventory and exact reference lines are retained in external evidence.

| Category | Inspection and disposition |
|---|---|
| 1. Blackjack-only production | All 32 files under `src/main/java/dev/vapee/core/activity/blackjack/`; remove entire implementation. Outside that package only VapeeCore imported its types. Remove root import/field/construction/registration and the Core help entry. |
| 2. Shared Core functionality | Keep every Activity model/service/listener, Seat, WorldDisplay, Lobby/BUILD rules, UI primitives, QuestProgressReporter, Reward and Economy. Warp shared two help titles; move unchanged EN/DE text to `warp.help.section.information` / `.management`. Keep SeatService's pre-existing legacy `blackjack_seat` PDC recognition for compatibility; it is not a game producer or a dependency on removed types. |
| 3. Blackjack-only tests/fixture | Seven executable harnesses plus one supporting presentation fixture, itemized below. Remove their subjects and these tests together. |
| 4. Other tests | Keep all 205 non-Blackjack original harnesses. Adapt exact root counts/order, descriptor oracles and obsolete help/catalog assertions. Replace the Seat adapter retry case with real generic Seat owner/retry isolation. Keep generic message-context styling using a neutral Practice example. Existing ConfigEvolution/Reload exclusion fixtures, quest fixtures using historical keys and the formatter exclusion sentinel remain valid. A historical SettingsHotbar registry-fixture comment has no production dependency. |
| 5. Bundled config/localization | Remove `blackjack.yml`, one command and one permission descriptor; remove only `blackjack.*`, `activity.blackjack` and `core.help.blackjack.help.description`. Keep `activity.context`, generic command/menu/confirmation keys and all non-Blackjack feature catalogs. |
| 6. Operator-owned persisted data | Retain installed blackjack.yml, players, balances, friends/clans, quests/daily cycles, worlds, LP files and recovery backups. No migration, truncation, cleanup or config rewrite. |
| 7. History/evidence | Keep historical reports and original Git commits. Current developer guidance is updated; Git history is the only authoritative removed-source archive. No duplicate source tree is shipped. |

### Removed production files

- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableService.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableSeatReference.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableOperationResult.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableInteractionMode.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableDraft.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableDefinition.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableConfig.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackSeatService.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackSeat.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackDisplayAnchor.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackBlockPosition.java`
- `src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackWorldViewService.java`
- `src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackPreviewService.java`
- `src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackInventoryService.java`
- `src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackDisplayGeometry.java`
- `src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackAction.java`
- `src/main/java/dev/vapee/core/activity/blackjack/interaction/BlackjackTableListener.java`
- `src/main/java/dev/vapee/core/activity/blackjack/command/BlackjackCommand.java`
- `src/main/java/dev/vapee/core/activity/blackjack/card/BlackjackSuit.java`
- `src/main/java/dev/vapee/core/activity/blackjack/card/BlackjackShoe.java`
- `src/main/java/dev/vapee/core/activity/blackjack/card/BlackjackRank.java`
- `src/main/java/dev/vapee/core/activity/blackjack/card/BlackjackHandView.java`
- `src/main/java/dev/vapee/core/activity/blackjack/card/BlackjackHand.java`
- `src/main/java/dev/vapee/core/activity/blackjack/card/BlackjackCard.java`
- `src/main/java/dev/vapee/core/activity/blackjack/BlackjackSession.java`
- `src/main/java/dev/vapee/core/activity/blackjack/BlackjackService.java`
- `src/main/java/dev/vapee/core/activity/blackjack/BlackjackRoundPhase.java`
- `src/main/java/dev/vapee/core/activity/blackjack/BlackjackPlayerRoundView.java`
- `src/main/java/dev/vapee/core/activity/blackjack/BlackjackPlayerRound.java`
- `src/main/java/dev/vapee/core/activity/blackjack/BlackjackOutcome.java`
- `src/main/java/dev/vapee/core/activity/blackjack/BlackjackModule.java`
- `src/main/java/dev/vapee/core/activity/blackjack/BlackjackActivityType.java`

These files covered the module, service/session/round/outcome engine, cards/deck/shoe, ActivityType, administrative command, table listener/config/validation/services, Seat adapter, hotbar and preview/world-display presentation. No unrelated production consumer depended on these classes.

### Composition, descriptors and localization

`VapeeCore` no longer imports, holds, constructs or registers BlackjackModule. Every remaining module stays in its original order. Reload remains ConfigService → LocalizationModule → LobbyModule → ChatModule → PrivateMessageModule → PresentationModule → DailyQuestModule.

`plugin.yml` loses exactly command `blackjack` (usage `/blackjack help`) and permission `vapeecore.blackjack.admin` (old default `op`). `/core help` no longer advertises it. No game executor or completer is installed. The clean JAR contains neither Blackjack production classes nor `blackjack.yml` (also checked directly on native Paper). Language catalog parity, placeholders, organization, safe rendering and fallback audits still execute; the global audits were not suppressed. Installed language overrides are untouched; any old unused override keys may remain.

## Original table data format and ownership

Former owner: `BlackjackTableConfig`, resource `src/main/resources/blackjack.yml`, installed path `plugins/VapeeCore/blackjack.yml`. Default was `tables: {{}}`; the file has no config-version field and was excluded from ConfigEvolution and `/core reload`. Its own protected replacement writer persisted explicit admin setup mutations; there is no writer after removal. It is an operator-owned historical file now.

`tables.<id>` uses IDs matching `[a-z0-9_-]+`; incomplete disabled drafts were valid persisted data. Do not assume every saved draft is runtime-valid. Original fields:

| Path beneath `tables.<id>` | Original representation |
|---|---|
| `enabled` | Boolean, absent defaults false |
| `area.pos1`, `area.pos2` | Optional objects: nonblank `world`, numeric `x/y/z`; area bounds normalize at validation |
| `dealer` | Optional `world`, numeric `x/y/z/yaw/pitch` |
| `interaction` | Optional legacy interaction block: `world`, integer `x/y/z` |
| `display` | Optional physical playing-surface center: `world`, numeric `x/y/z/yaw`; absent meant legacy display fallback |
| `seats.<number>` (legacy) | Direct `world/x/y/z/yaw/pitch` position object |
| `seats.<number>.position` (modern) | `world/x/y/z/yaw/pitch` seated position |
| `seats.<number>.block` (modern) | Exact clicked seat block: `world`, integer `x/y/z` |

The original definition validator enforced seats 1–5, consistent modern/legacy seat mode, area/world compatibility, unique seat blocks and required dealer/interaction rules. Legacy and modern representations must both be considered by any future deliberate migrator. The physical table blocks belong to the map builder; Core never stored a schematic.

### Source recovery and dependencies

Use read-only Git history, for example:

```powershell
git ls-tree -r 1a3dade15c7dfab69b0cf96ce74af6097c73145e -- src/main/java/dev/vapee/core/activity/blackjack/
git show 1a3dade15c7dfab69b0cf96ce74af6097c73145e:src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableConfig.java
git show 1a3dade15c7dfab69b0cf96ce74af6097c73145e:src/main/resources/blackjack.yml
```

Original package: `dev.vapee.core.activity.blackjack`, including card, command, interaction, presentation and table subpackages. Entry points were `/blackjack` and `vapeecore.blackjack.admin`. The module consumed ActivityModule, SeatModule, WorldDisplayModule, LobbyModule, MessageService, CommandHelpRenderer and QuestModule, plus Paper/Bukkit/Adventure facilities. LuckPerms remained Core's external required dependency, not a separate Blackjack download or integration. The removed feature had no wagering/economy dependency and no direct Coin grant.

## Shared infrastructure and historical quest state

ActivityModule remains available with zero built-in activity types, venues or sessions. Generic type registration/unregistration, sessions, participants, state changes and cleanup still execute. SeatService retains CASUAL/MANAGED reservations, mount/release and ownership boundaries. WorldDisplayService retains native keyed TextDisplay/ItemDisplay behavior. No shared production class was modified to remove a consumer.

The removed BlackjackModule reported one unit through `QuestProgressKey.of("blackjack:win")` only for WIN and BLACKJACK. This producer is gone. QuestProgressKey, QuestProgressReporter, QuestService, RewardService, EconomyService, Player schema and daily-cycle semantics are unchanged. Player YAML stores assigned quest IDs/progress/status and daily cycles rather than an independent global win counter. The regression fixture round-trips the quest assignment tied to a legacy definition, its progress and cycle; removing the definition does not discard that valid history.

An operator-defined daily quest with `progress-key: blackjack:win` remains valid under the generic parser, but normal Core activity will no longer advance it. **The operator must deliberately disable or replace such quests before using that daily catalog for progression.** No automatic conversion, removal, compensation Coins, reward replay or state reset occurs. Other future keys remain allowed and inert without a producer.

## World/resource safety and upgrade limitations

Fresh disposable startup registers no Blackjack activity, command, listener, scheduled runnable, table seat reservation, hotbar/menu, preview or hologram. Native inspection checks the actual command map, listener registrations, pending Core task bodies, Activity/Seat/WorldDisplay state and spawned entity owner markers. No replacement scheduler, demo game, module toggle or game API was added.

Old normal shutdown explicitly stopped Blackjack updates, closed sessions/venues, removed previews/displays/hotbar items/seats and unregistered the activity before the shared Seat/WorldDisplay shutdown. Entities were configured nonpersistent. Abnormal termination, legacy versions or third-party changes can still leave anomalies; absence of new resources is not proof about every historical world chunk.

The **existing** generic startup cleanup for VapeeCore seat/display markers (including the old seat marker) remains unchanged. This step adds no entity sweep, migration or automatic world mutation. No operator server was started, stopped or upgraded here.

Separate owner-verified remediation, if later required: first back up the affected world and operator table file during an approved maintenance window; perform a read-only inventory of exact entity UUID/type/world/position and typed VapeeCore PDC. Former table displays/seats used owner `blackjack:<tableId>`; previews used `blackjack-preview:<adminUUID>:<tableId>`. Shared PDC keys are `vapeecore:seat`, `seat_owner`, `seat_id`, `seat_type` and `world_display`, `world_display_owner`, `world_display_id`, `world_display_type`; old seats may carry `vapeecore:blackjack_seat`. Compare each candidate with the saved table and historical implementation. Only independently verified stale owned UUIDs may be removed under a separate explicit cleanup action. Never delete by entity material/type, location alone, unverified prefix or wildcard selector; preserve passengers, foreign entities and map blocks. Historical hotbar PDC was `blackjack_action` / `blackjack_item`; do not blanket-clear inventories. No such remediation was executed.

## Test-removal ledger

Each removed harness only tested the removed feature. File paths below are relative to `src/test/java/dev/vapee/core/activity/blackjack/`.

| Test path | Harness | Baseline checks | Removed subject; surviving generic coverage |
|---|---|---:|---|
| `BlackjackFairnessHarness.java` | `BlackjackFairnessHarness` | 393 | Six-deck shuffle/dealer/outcome fairness; no generic game rules remain to preserve. |
| `BlackjackHarness.java` | `BlackjackHarness` | 318 | Rounds, outcomes, Blackjack quest producer and gameplay lifecycle; ActivityHarness, QuestProgressReporterHarness and module lifecycle suites retain shared contracts. |
| `BlackjackPresentationHarness.java` | `BlackjackPresentationHarness` | 132 | Game hotbar/world view/geometry; UiItems, SeatHarness and WorldDisplayHarness retain generic presentation/resource behavior. |
| `BlackjackReadViewHarness.java` | `BlackjackReadViewHarness` | 21 | Immutable Blackjack hand/round snapshots; those model types are removed, shared state models remain tested. |
| `presentation/BlackjackPreviewHarness.java` | `BlackjackPreviewHarness` | 283 | Blackjack setup preview generation/timeouts/cleanup; shared task/resource ownership remains in Activity/Seat/WorldDisplay and module lifecycle tests. |
| `table/BlackjackReplacementHarness.java` | `BlackjackReplacementHarness` | 98 | Removed table-config replacement writer; Player/Lobby/config/repository replacement and recovery suites remain. |
| `table/BlackjackTableHarness.java` | `BlackjackTableHarness` | 313 | Table schema, validation, indices, game-specific seat adapter and table activation; Seat/Activity lifecycle and historical preservation tests remain. |

Dedicated harness removal: **7 harnesses / 1,558 checks**. Supporting non-executable `BlackjackPresentationFixture.java` is also removed; it has no independent canonical check count.

Partial changes keep the original non-Blackjack harness identities: CommandHelpHarness removes only removed setup/workflow/surface/completion cases and retains Warp/Coins/Core and permission filtering. SeatHarness replaces its deleted adapter case with five real generic retry/foreign-owner checks. LocalizationSurfacesHarness removes only the removed timeout surface; CatalogOrganizationHarness removes its deleted scalar/descendant example; CatalogUsageHarness removes the game-only ActivityState-caption loop while keeping full live catalog/source audits. Descriptor and lifecycle tests now independently expect actual 27/36/49 inventories. Dynamic audit counts naturally shrink when production files, catalogs and descriptors shrink.

### New regression coverage and verification

`activity/BlackjackRemovalHarness.java` adds **51 executable checks**: exact composition/reload source order backed by native runtime assertions, actual clean JAR/descriptor inspection, catalog removals and shared keys, real generic Activity registration/participation/shutdown, Seat/WorldDisplay services against controlled external entity gateways, actual legacy DailyQuestConfig parsing and real FilePlayerRepository round-trip without auto-progress/reward or historical table writes. Source checks are limited to composition details; native acceptance exercises the full root and seven reload participants. Existing Friend/Clan/Presence, Player/Reward/Quest and security suites retain executable behavior, also exercised natively below.

Canonical final command: `pwsh -File .\scripts\verify.ps1` with Java 21.

| Result | Baseline | Final |
|---|---:|---:|
| Discovered / executed / passed harnesses | 212 / 212 / 212 | 206 / 206 / 206 |
| Checks | 33,644 | 31,166 |
| Failed | 0 | 0 |
| Missing/Invalid | 0 | 0 |
| Maven clean package | PASS | PASS |
| Spotless six-file allowlist | PASS | PASS |
| git diff --check | PASS | PASS |

Count equation: 212 − 7 + 1 = 206 harnesses. Checks: 33,644 − 1,558 + 51 -971 = 31,166. All 205 unrelated original harnesses remain; there are no placeholders or hidden missing results.

Changes to surviving harness check totals (unchanged identities):

| Harness | Before | After | Delta |
|---|---:|---:|---:|
| `dev.vapee.core.command.CoreCommandHarness` | 80 | 79 | -1 |
| `dev.vapee.core.i18n.CatalogOrganizationHarness` | 43 | 42 | -1 |
| `dev.vapee.core.i18n.CatalogUsageHarness` | 3,393 | 2,706 | -687 |
| `dev.vapee.core.i18n.LocalizationHarness` | 981 | 782 | -199 |
| `dev.vapee.core.i18n.LocalizationSurfacesHarness` | 49 | 46 | -3 |
| `dev.vapee.core.message.CommandHelpHarness` | 87 | 56 | -31 |
| `dev.vapee.core.onlinereward.OnlineRewardLifecycleHarness` | 42 | 41 | -1 |
| `dev.vapee.core.permission.PermissionDescriptorHarness` | 890 | 848 | -42 |
| `dev.vapee.core.quest.QuestLifecycleHarness` | 42 | 41 | -1 |
| `dev.vapee.core.repository.RepoLinkHarness` | 91 | 94 | +3 |
| `dev.vapee.core.reward.RewardLifecycleHarness` | 40 | 39 | -1 |
| `dev.vapee.core.ui.GuiAuthoringAuditHarness` | 249 | 242 | -7 |

Early post-removal verification exposed twelve stale independent 28-module/37-command/50-permission expectations. These were updated explicitly without removing harnesses or weakening the runtime contracts. A subsequent run found a documentation link to this then-uncreated report; after creating it, the complete verifier passed. The final full run above is authoritative.

## Native Paper acceptance

Disposable **Paper 1.21.11 build 132 + LuckPerms 5.5.84 + Java 21.0.12.1**. Fresh worlds/configuration and synthetic disposable identities; immutable Paper runtime libraries/JARs were read/copied from the installed server. Only the isolated directory was mutated. No Minecraft client connected.

Final canonical-build JAR SHA-256: `3EAC836160F8E8B5DE82AC173548E7AD283E2AAA8CDA71490272831A9F992118`. It matches both target artifact and preserved tested JAR.

Two independent JVMs: **229 + 45 = 274 checks**, both PASS, exit 0, normal console `/stop`, all 27 module shutdown calls returned normally, zero unexpected ERROR/SEVERE. Successful seven-component reload, exact module order, actual command map/permission/JAR absence, no game types/tasks/listeners/owned entities, generic activity participant/session, generic managed seat reservation/release, real native TextDisplay create/update/remove, legacy file retention, legacy quest/daily cycle plus generic reporter completion and 12 persisted fixture Coins all passed. Cold restart independently reloads friends/clans/preferences, assigned quest progress and reward balance with no replay. Later ticks leave historical wins unchanged.

The Step-01 social acceptance helper was extended to retain Friends/Clan UUID RUN_COMMAND actions, rank colors, localization, Presence privacy/deduplication, Join Summary opt-out and real native ItemStack/SkullMeta profile checks. These use genuine Paper objects and registered handlers with synthetic Player/transport boundaries. Native seat mounting of a real network player and visual rendering still require a real client; canonical SeatHarness tests mount behavior at its entity boundary. Prior user-confirmed Step-01 Minecraft-client acceptance is an earlier result, not a new Step-02 client test.

Two exploratory native fixture runs exposed helper issues (package-private CraftTask inspection, then an unloaded cold-start display chunk); they are retained as failed evidence. The helper now inspects actual task bodies and loads its disposable display chunk before asserting. Both clean final runs, including the byte-identical canonical JAR run, pass. Production shared services were not changed to accommodate the helper.

## Operator protection and Git safety

Read-only inventory records path, size, SHA-256 and errors. Before/after: **918 installed server files**, all 918 comparable and identical at both measurement points, **0 locked/unreadable**, **0 externally changed**, no added/removed files. This states measured snapshots, not a guarantee that a running server could never mutate files between reads. The installed server was never stopped/restarted, overwritten, deployed to or used as a mutable fixture.

Existing operator `dev-server/plugins/VapeeCore/blackjack.yml` remains present with SHA-256 `0231E32A0C675EDF0C35B8437473716B8784995C7C06737586C03A0F2A437726`. World/player/LP/recovery/sync evidence is included where present. All **24 scripts and two .run files** also match their original bytes.

The sole approved unrelated tracked modification remains `.run/Start VapeeCore Dev Server.run.xml`: original full Git diff and SHA-256 `EEDE5A220EEC6A8D154D82F2BD1BF29CFEB59215D6B546D83CDB38A73EAF596F` are preserved. It changes interpreter independence/path representation and lacks the final newline. It is excluded from staging/commit and never rewritten or reverted. No unexpected tracked/untracked source changes were found. No reset, clean, blanket add, metadata repair, stash deletion or earlier commit amendment occurred.

External evidence directory: `C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/prototype-step02-blackjack-removal-20261029/`. It retains baseline/final logs and receipts, before-edit inventory, test ledger, native helper source/JAR/receipts/logs and tested Core JAR, operator inventories, Run diff/hash and exact path manifests. Disposable server trees may be removed only after JVM exit and copying receipts/data; no removed-source archive is added to Core.

## Exact commit paths (78)

All 41 deletions were reviewed: 32 production Blackjack files, eight dedicated test/fixture files and one bundled resource. No shared source file, operator file, historical report or original feature branch is deleted. Targeted staging uses the following explicit allowlist only.

### Production Java

- `src/main/java/dev/vapee/core/VapeeCore.java`
- `src/main/java/dev/vapee/core/activity/blackjack/BlackjackActivityType.java`
- `src/main/java/dev/vapee/core/activity/blackjack/BlackjackModule.java`
- `src/main/java/dev/vapee/core/activity/blackjack/BlackjackOutcome.java`
- `src/main/java/dev/vapee/core/activity/blackjack/BlackjackPlayerRound.java`
- `src/main/java/dev/vapee/core/activity/blackjack/BlackjackPlayerRoundView.java`
- `src/main/java/dev/vapee/core/activity/blackjack/BlackjackRoundPhase.java`
- `src/main/java/dev/vapee/core/activity/blackjack/BlackjackService.java`
- `src/main/java/dev/vapee/core/activity/blackjack/BlackjackSession.java`
- `src/main/java/dev/vapee/core/activity/blackjack/card/BlackjackCard.java`
- `src/main/java/dev/vapee/core/activity/blackjack/card/BlackjackHand.java`
- `src/main/java/dev/vapee/core/activity/blackjack/card/BlackjackHandView.java`
- `src/main/java/dev/vapee/core/activity/blackjack/card/BlackjackRank.java`
- `src/main/java/dev/vapee/core/activity/blackjack/card/BlackjackShoe.java`
- `src/main/java/dev/vapee/core/activity/blackjack/card/BlackjackSuit.java`
- `src/main/java/dev/vapee/core/activity/blackjack/command/BlackjackCommand.java`
- `src/main/java/dev/vapee/core/activity/blackjack/interaction/BlackjackTableListener.java`
- `src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackAction.java`
- `src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackDisplayGeometry.java`
- `src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackInventoryService.java`
- `src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackPreviewService.java`
- `src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackWorldViewService.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackBlockPosition.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackDisplayAnchor.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackSeat.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackSeatService.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableConfig.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableDefinition.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableDraft.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableInteractionMode.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableOperationResult.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableSeatReference.java`
- `src/main/java/dev/vapee/core/activity/blackjack/table/BlackjackTableService.java`
- `src/main/java/dev/vapee/core/command/CoreCommand.java`
- `src/main/java/dev/vapee/core/lobby/warp/command/WarpSubcommand.java`

### Tests

- `src/test/java/dev/vapee/core/activity/BlackjackRemovalHarness.java`
- `src/test/java/dev/vapee/core/activity/blackjack/BlackjackFairnessHarness.java`
- `src/test/java/dev/vapee/core/activity/blackjack/BlackjackHarness.java`
- `src/test/java/dev/vapee/core/activity/blackjack/BlackjackPresentationFixture.java`
- `src/test/java/dev/vapee/core/activity/blackjack/BlackjackPresentationHarness.java`
- `src/test/java/dev/vapee/core/activity/blackjack/BlackjackReadViewHarness.java`
- `src/test/java/dev/vapee/core/activity/blackjack/presentation/BlackjackPreviewHarness.java`
- `src/test/java/dev/vapee/core/activity/blackjack/table/BlackjackReplacementHarness.java`
- `src/test/java/dev/vapee/core/activity/blackjack/table/BlackjackTableHarness.java`
- `src/test/java/dev/vapee/core/clan/ClanIntegrationHarness.java`
- `src/test/java/dev/vapee/core/command/CoreCommandHarness.java`
- `src/test/java/dev/vapee/core/economy/EconomyIntegrationHarness.java`
- `src/test/java/dev/vapee/core/friend/FriendLifecycleHarness.java`
- `src/test/java/dev/vapee/core/i18n/CatalogOrganizationHarness.java`
- `src/test/java/dev/vapee/core/i18n/CatalogUsageHarness.java`
- `src/test/java/dev/vapee/core/i18n/LocalizationSurfacesHarness.java`
- `src/test/java/dev/vapee/core/identity/IdentityHarness.java`
- `src/test/java/dev/vapee/core/message/CommandHelpHarness.java`
- `src/test/java/dev/vapee/core/message/MessageStyleHarness.java`
- `src/test/java/dev/vapee/core/moderation/ModerationLifecycleHarness.java`
- `src/test/java/dev/vapee/core/moderation/command/ModerationCommandHarness.java`
- `src/test/java/dev/vapee/core/onlinereward/OnlineRewardLifecycleHarness.java`
- `src/test/java/dev/vapee/core/permission/PermissionDescriptorHarness.java`
- `src/test/java/dev/vapee/core/presence/PresenceLifecycleHarness.java`
- `src/test/java/dev/vapee/core/quest/QuestLifecycleHarness.java`
- `src/test/java/dev/vapee/core/quest/daily/DailyQuestLifecycleHarness.java`
- `src/test/java/dev/vapee/core/reward/RewardLifecycleHarness.java`
- `src/test/java/dev/vapee/core/seat/SeatHarness.java`
- `src/test/java/dev/vapee/core/visibility/VisibilityModuleHarness.java`

### Resources

- `src/main/resources/blackjack.yml`
- `src/main/resources/lang/de.yml`
- `src/main/resources/lang/en.yml`
- `src/main/resources/plugin.yml`

### Documentation/report

- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/CODE_STYLE.md`
- `docs/COMMAND_DEVELOPMENT.md`
- `docs/CONFIG_DEVELOPMENT.md`
- `docs/DEVELOPER_GUIDE.md`
- `docs/FORMATTING.md`
- `docs/PERMISSIONS.md`
- `docs/PERSISTENCE_DEVELOPMENT.md`
- `reports/prototype/STEP_02_BLACKJACK_REMOVAL.md`

## Deferred migration and next prototype step

No VapeeGames plugin, public add-on API, new Maven module, module-toggle system or persistence/lifecycle framework was implemented. A future separate plugin requires an explicit versioned service/API contract, dependency/start/stop and reload behavior, main-thread/async rules, activity participation and Lobby inventory handoff, collision-safe Seat/WorldDisplay ownership, Quest producer registration/idempotency and intentional legacy schema/location migration with verified backups. Removed implementation is recoverable from the baseline Git commit; copying it into another Core package is not the plan.

Remaining manual checks: connect an actual Minecraft client to a separately approved updated test instance, confirm `/blackjack` is absent from help/completion, no old table interaction or preview starts, generic casual sitting still works and Step-01 Friend/Clan/Join Summary/head presentation remains visually correct. Do not infer a new visual-client PASS from native API tests.

Recommended next bounded prototype step: validate a short player onboarding/lobby route and an operator-authored playtime-only daily quest catalog in a separate task. Decide scope before coding; Step 03 has not begun. Existing legacy game quests require an explicit operator decision.
