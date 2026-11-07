# Step 08D - Lobby & Player Interaction Quest Progress

Date: 2026-11-07. Branch: `codex/step08d-lobby-quest-progress`.
Verified clean starting master/origin/master: `550fadbf2e2b6d90306b04f4f9910d3b366733d4`.

## Scope and success boundaries

All fourteen requested technical keys are implemented. Definitions, names, targets and rewards remain YAML-owned. No reference quest catalog is installed or activated. Defaults remain `enabled: false`, `quests-per-day: 4`, `quests: {}`. There are still 27 modules and seven reload participants in their original order. Existing Movement/Community measurements, permissions, commands, reward messages, HUD rendering and player selection rules remain intact.

| Key | Concrete successful action |
| --- | --- |
| `lobby:spawn_arrived` | SpawnCommand invokes the new narrow LobbyService command wrapper; the existing actual spawn teleport returns true. Join/respawn use the unchanged silent helper. Missing configuration/world, cancelled/failed teleports and other teleport sources cannot count. |
| `lobby:warp_visit` | WarpService completes a Navigator-origin teleport. Both legacy and V2 UI paths already use `teleportFromNavigator`. |
| `lobby:warp_unique` | Successful ordinary or Navigator WarpService teleport to a still-current configured technical WarpPoint; first identity within each assignment. |
| `lobby:navigator_open` | Regular Navigator root open completes, with current owning bound holder and actual top inventory. Legacy and V2 are supported; preview/submenus/pages/automatic rerenders are silent. |
| `lobby:quests_open` | DailyQuestMenu completes a real bound open after its existing sync/permission/domain gates. New internal `openPage` keeps refresh/countdown/pagination silent. |
| `lobby:settings_open` | SettingsMenu completes a bound actual top-inventory open after permission and loaded settings checks. |
| `lobby:friends_open` | FriendMenu root completes a bound actual open. Incoming/outgoing tabs, pagination and refresh do not represent new root actions. |
| `lobby:visibility_open` | VisibilitySettingsMenu completes a bound actual top-inventory open after permission and loaded settings checks. |
| `lobby:coins_check` | Actual own loaded balance output in `CoinsCommand.sendOwnBalance`, only argument-free `/coins`. Admin/help/usage/console paths are excluded. |
| `lobby:rank_check` | Argument-free own RankCommand output with an available actual primary rank. Target/console/error paths are excluded. |
| `lobby:ranks_check` | Existing RanksCommand sends a nonempty available public track. Missing/empty tracks, usage and denies are excluded. |
| `lobby:ping_self` | Existing successful Ping output classified by actual UUID as self, including explicit own name. |
| `lobby:ping_other` | Existing successful Ping output for another online target, after existing Others permission. The producer additionally requires that target's current loaded session. |
| `social:meet_unique` | First qualifying nearby visible other player UUID, under the directional rules below. |

Owner-local subscriptions are deliberately small. Domain modules do not import QuestService. The adapter imports the command/menu owners; the quest domain itself remains independent of those APIs. Success callbacks isolate RuntimeExceptions and diagnostic failures. No observer error repeats a teleport or rolls back a successful menu/command. No string-level command listener exists.

Menus emit at the owning service, so real command, registered lobby-item and Player Menu destination routes share the same boundary. The adapter never owns an inventory. Rechecking the real bound holder, registry and current top inventory excludes cancelled/replaced/foreign/stale opens. Existing click-security harnesses are retained. Query and non-Navigator menu contexts retain their existing feature semantics; they are not arbitrarily restricted to the lobby or NORMAL. Navigator and encounters retain the appropriate lobby/NORMAL/no-Activity gates.

## Distinct identity and persistence contract

Both unique keys are restart-safe through normal full-profile saves, logout and clean shutdown. A generic optional `QuestDistinctProgress` is part of each immutable PlayerQuestProgress. The old three-argument constructor remains compatible. `QuestService.addDistinctProgress` / reporter `reportDistinct` fan out by technical progress key, independently per assignment, and update evidence and numeric progress together before the existing pending/reward path. The existing HUD observes the same generic numerical increases automatically.

The optional YAML section `quests.active.<id>.distinct` stores the progress key/target compatibility signature, sorted identities and `blocked: false`. A blocked record stores `blocked: true`. It resides in the existing wholly owned `quests.active` field and the same player file; schema version 1 remains valid. No other Player fields, wallet format, file, database or persistent cache is added. Ordinary profiles/saves with no distinct data retain their format.

Warp identity is `warp:` plus SHA-256 of the canonical technical ID, independent of display name and bounded even though existing valid Warp IDs have no length ceiling. Recreating the same ID retains identity for that assignment; renaming is a different technical identity. No guarantee of physical destination continuity is made. Encounter identity is `player:` plus actual UUID. Identity syntax is `[a-z0-9][a-z0-9_:/-]{0,127}`.

Evidence is bounded to 1024 identities per assignment and 4096 across a player's current assignments. A distinct target above 1024 or exhausted aggregate capacity cannot earn new evidence; targets are not silently clamped and no premature reward occurs. Operators should use compatible distinct targets and daily selection sizes. Limits affect the measurement API, not the existing general YAML schema.

A zero-progress legacy assignment can start evidence. Positive legacy progress without identity history fails closed until a fresh assignment/reset; reconstructing prior targets would permit recounts. Malformed, duplicated, excessive or numeric-inconsistent optional evidence produces a warning and a persisted blocked sentinel, while numeric progress, status and other profile fields remain available. It never becomes an empty countable history.

Same cycle and compatible reload retain evidence. Catalog changes invalidate pending runtime observations. Changed key/target signatures stop further counting without deleting evidence; a compatible catalog rollback can continue. Explicit Force retains only compatible surviving distinct assignments and starts incompatible/blocked ones fresh; a failed Force save restores the full prior records. New assignments and genuine daily reset start fresh. Unresolved pending rewards still block Force. Pending retry/completion retains evidence and uses the existing reward authority without duplicate increments or grants. Numeric calls cannot bypass an already distinct-managed active assignment.

The existing delayed full-profile flush semantics remain. This is not a new transaction journal; hard JVM/OS termination before the next successful aggregate save can lose unsaved state, as before. Normal restart persistence is tested; crash-proof external storage is not claimed.

## Encounter semantics and anti-abuse

Both actual current online instances and loaded CorePlayer profiles must be alive, NORMAL, outside Activity participation and in the same configured lobby world. Self is excluded. Actual 3D distance must be at most six blocks, including the exact boundary. `VisibilityService.canSee` combines the existing viewer-specific VisibilityPolicy (including bidirectional Ignore) and `Player.canSee` for foreign plugin hide ownership. Both directions are checked independently; one player's visibility never implies the other's.

Move, join, teleport/world/respawn, existing visibility refreshes, assignment and BUILD/Activity transitions may request a next-tick scan. One event-driven shared one-shot task processes at most 32 spatial queries per tick, with a 20-tick per-player interval and at most 32 candidate validations per query. Production uses `World.getNearbyPlayers`; a rotating offset advances through dense local results. The spatial API can still materialize a collection proportional to local density. There is no global online-pair scan, polling loop or per-player timer. A catalog without `social:meet_unique`, including the bundled empty default, adds no spatial work.

Maps retain at most 4096 current session slots and 1024 pending contexts, without Bukkit Player/World references. Overload drops additional observations and may undercount. Ordinary movement does not access files; actual new evidence follows the existing progress/reward/flush path. Repeated movement, logout/rejoin, new location, server restart or simple reload cannot recount a stored target. Queued work validates session/entity, loaded profile, assignment and catalog identities again; stale quits cannot erase replacement sessions.

This is a server proximity/visibility decision, not ray tracing, human perception or proof that a real client rendered a skin/entity. Very brief meetings, stationary players after an in-throttle change, and external show/hide changes without another observed event can wait until subsequent eligible movement. These conservative limits are intentional and documented.

## Lifecycle and ownership

`QuestModule.startLobby` follows the existing post-enable bindings and borrows exact Lobby, Warp, Navigator, DailyQuest, Settings, Friend, Visibility, command and context owners. It verifies current command ownership/executor types; replacement or retired source owners revoke progress authority. No additional module/reload participant is registered.

Release actions are recorded before acquisition. Startup failures release independent subscriptions and event handlers. Disable first revokes authority, clears temporary contexts/sessions, cancels the waiting task and independently releases all acquired callbacks. Failed cleanup retains original resources in the existing pending-cleanup mechanism for explicit retry and prevents re-enable until confirmed. Foreign observers are preserved. All exact handles are owned by this runtime; successful domain operations are never replayed.

## Executed verification

Baseline before implementation: **242/242 harnesses, 35,699 checks, Failed=0, Missing/Invalid=0, Maven and Spotless PASS**.

First full implementation run: **245/245 harnesses, 35,864 checks, Failed=0, Missing/Invalid=0, Maven and Spotless PASS**. Three new executable harnesses were discovered; every existing harness was retained. This precedes the final additional bounds/restart tests and Warp-ID digest refinement.

Second full run: **245/245 harnesses, 35,870 checks, Failed=0, Missing/Invalid=0, Maven and Spotless PASS**. This includes the added UUID restart/capacity tests and bounded Warp-ID digest, but predates the final empty-catalog fast path.

The final empty-catalog regression was actually compiled in isolation against the previously built producer. It exited 1 at `empty bundled catalog performs no spatial work`, proving that the test detects unnecessary default-catalog spatial work. The final guard returns before queueing when no encounter key exists. This focused expected red control is not a canonical failure and is retained with its class hash/log. Final canonical verification actually executed with `pwsh -NoProfile -File .\scripts\verify.ps1`: **245/245 harnesses, 35,871 checks, Failed=0, Missing/Invalid=0, Maven clean package PASS, Spotless PASS (existing pilot allowlist)**. All 242 baseline harnesses remain; exactly the three new executable harnesses were added. Final focused counts: Distinct **38**, Lobby **43**, Module ownership **54**. The final receipt/log and full baseline inventory comparison are archived externally. The passing final Lobby harness is the green counterpart of the compiled empty-catalog red control.

Focused executable coverage uses real Quest/Reward/Player/FilePlayerRepository services for independent same-key assignments, distinct deduplication, full disk roundtrip with reconstructed services, both warp and UUID evidence, malformed optional data, Force persistence rollback, signature reload/rollback, fresh resets, pending rewards, single configured coin payout, and aggregate evidence bounds. Producer tests cover session/owner invalidation, radius boundaries, visibility/eligibility rejection, rejoin, reset, throttling, bounded dense candidates, HUD and scheduling failures. Actual QuestModule tests inject before/after-effect subscription/registration failures, cleanup refusal/retry, foreign observers, re-enable and duplicate bind. Existing menu and command harnesses are extended with actual success-observer assertions, while all existing click-security/localization/rank tests still run.

QuestLifecycleHarness's former blanket source-import prohibition now permits Economy/Rank imports only in the narrow Lobby success adapter. Its domain/foundation checks remain enforced and exclude gameplay/menu/command coupling. No existing harness was deleted, disabled or bypassed. Behavior tests, rather than a source-text-only acceptance, cover the new production paths.

### Isolated native Paper / LuckPerms

Native Paper 1.21.11 build 132 (`c5eb079`), LuckPerms 5.5.84, JDK 21, loopback-only port 25664. `native-02` passed 41 executable checks; `native-03` passed 50, with normal own-process shutdown/exit 0 and no assertion failure. `native-04` repeated all 50 checks after bounded Warp-ID hashing. Final **native-05 passed 51 checks**, including the final empty-catalog guard, with normal process exit 0. The canonical artifact and native-tested copy are SHA-256 identical: `C2089E6C90B1784CD70E5827E8B2D3153A4BFB5DEE3F96C993AC49221D59AB90`.

The native helper uses the actual built plugin, real CraftPlayer inventories, inventory-open cancellation, actual legacy/V2 Navigator click listeners, successful CraftPlayer teleports, cancelled PlayerTeleportEvents, loaded LuckPerms ranks/public track, existing menu/item/Player Menu destination owners, generic HUD packets, native YAML files and real VisibilityPolicy/Player.canSee behavior. The added negative stages verify no encounter credit for foreign hide ownership, Ignore, BUILD, Activity exclusion, another world, outside-radius distance, offline targets or unloaded profiles, followed by exactly one valid target credit.

Boundaries are explicit: actors have synthetic ServerPlayer connections and controlled online-session/permission responses at the Player API boundary; LuckPerms rank/track data is real; encounter candidate enumeration is controlled, while real coordinates, lobby/context/visibility gates and distance filtering run in production. Activity exclusion uses a controlled membership projection in the disposable fixture rather than a gameplay activity session. The Player Menu destination is invoked through its existing gated owner; lobby-item actions use the actual registered action route. Navigator InventoryClickEvents use real native top/bottom inventories with a controlled event-view player identity. These are API/service assertions, not authenticated network clicks or a client-rendered encounter. No test facade/NMS code enters production.

`native-01` contains only external helper compilation errors (LuckPerms appendGroup argument and newer InventoryView interface surface); no server or successful acceptance ran there. The helper was corrected, with no production workaround. Every launched disposable process is independently owned and exits normally. Evidence is outside the repository under `C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/step08d-lobby-quest-progress-20261107-020153/`.

## Protected resources and delivery

Before snapshots cover 1503 original files across both existing worktrees, including the two explicitly protected ignored receipts. Only the intended implementation/test/guide paths listed below may differ. Both Run files, both receipts, existing branch/remote/tag refs and Recovery branches, master/origin/master and the protected `chat-polish-wt` (including its existing untracked report) must match the initial snapshots. The final comparison verifies all 1503 original files: only the 33 intended existing paths differ, and seven intended files are new, for 40 exact feature paths. No unexpected original file change was found. Post-commit confirmation is recorded in the external delivery receipt.


Verified protected SHA-256 values:

| Path | SHA-256 |
| --- | --- |
| `.run/Build and Deploy VapeeCore.run.xml` | `FFE3EDC5279855568BA0E906434D82A2901540D42C0976202C4F3DFCE06E8E56` |
| `.run/Start VapeeCore Dev Server.run.xml` | `EEDE5A220EEC6A8D154D82F2BD1BF29CFEB59215D6B546D83CDB38A73EAF596F` |
| `reports/06b/STEP_06B_1_PREFIX_STYLING_CORRECTION_COMMIT.txt` | `A47BA33C7BC90D77B233150F84E692713A9CB1753F1013915928719CB19EA6B0` |
| `reports/06b/STEP_06B_CHAT_DESIGN_SYSTEM_COMMIT.txt` | `8E4EDAB77B7792D7EB16C1CEB500A6F614FD95A3277583B707A0FCD25E8886D9` |

New internal `refs/codex/turn-diffs/...` metadata refs were observed and left untouched. They are recorded separately from feature branches in the external receipt; no existing branch, remote-tracking ref, tag or Recovery branch is rewritten.

No operator files are used as a mutable fixture. Ignored live world/log/player files may change externally and are not claimed byte-identical. The operator server is neither stopped nor modified. All native configuration/world/player changes occur only in newly created disposable environments.

The Developer Guide was restored from the previously clean verified HEAD after an explicitly diagnosed failed Windows-codepage write, then updated using explicit UTF-8. Its final Git diff contains only the intended contract updates. No unrelated work was restored or discarded.

Delivery is exactly one local feature commit, without amend, push, merge, PR, deployment, branch deletion or Step 08E. The SHA and clean post-commit status belong to the final answer/external receipt, without a self-referential report hash.

## Real Minecraft-client acceptance still open

Confirm command/item/Player Menu opens, denied/cancelled opens, pages/tabs/locale refresh silence, legacy and V2 clicks, explicit /spawn versus join/respawn, ordinary/unique Warp visits across reconnect/restart, two physically nearby visible players, asymmetric visibility/Ignore/BUILD/Activity transitions, six-block/other-world boundaries, reload/Force/reset, localized HUD percentage and temporary XP restoration/Navigator priority, and exactly one configured reward. API acceptance does not replace these visual/network-client checks.

## Exact changed paths

- `docs/DEVELOPER_GUIDE.md`
- `reports/08d/STEP_08D_LOBBY_QUEST_PROGRESS.md`
- `src/main/java/dev/vapee/core/VapeeCore.java`
- `src/main/java/dev/vapee/core/economy/command/CoinsCommand.java`
- `src/main/java/dev/vapee/core/friend/gui/FriendMenu.java`
- `src/main/java/dev/vapee/core/lobby/LobbyService.java`
- `src/main/java/dev/vapee/core/lobby/command/SpawnCommand.java`
- `src/main/java/dev/vapee/core/lobby/experience/LobbyExperienceModule.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenu.java`
- `src/main/java/dev/vapee/core/lobby/warp/WarpService.java`
- `src/main/java/dev/vapee/core/player/repository/FilePlayerRepository.java`
- `src/main/java/dev/vapee/core/quest/PlayerQuestProgress.java`
- `src/main/java/dev/vapee/core/quest/QuestDistinctProgress.java`
- `src/main/java/dev/vapee/core/quest/QuestLobbyProducer.java`
- `src/main/java/dev/vapee/core/quest/QuestModule.java`
- `src/main/java/dev/vapee/core/quest/QuestProgressReporter.java`
- `src/main/java/dev/vapee/core/quest/QuestService.java`
- `src/main/java/dev/vapee/core/quest/daily/DailyQuestModule.java`
- `src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestMenu.java`
- `src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestMenuListener.java`
- `src/main/java/dev/vapee/core/rank/command/RankCommand.java`
- `src/main/java/dev/vapee/core/rank/command/RanksCommand.java`
- `src/main/java/dev/vapee/core/settings/SettingsMenu.java`
- `src/main/java/dev/vapee/core/settings/visibility/VisibilitySettingsMenu.java`
- `src/main/java/dev/vapee/core/utility/command/PingCommand.java`
- `src/main/java/dev/vapee/core/visibility/VisibilityService.java`
- `src/test/java/dev/vapee/core/economy/command/CoinsCommandHarness.java`
- `src/test/java/dev/vapee/core/friend/gui/FriendMenuHarness.java`
- `src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenuHarness.java`
- `src/test/java/dev/vapee/core/quest/QuestDistinctHarness.java`
- `src/test/java/dev/vapee/core/quest/QuestLifecycleHarness.java`
- `src/test/java/dev/vapee/core/quest/QuestLobbyHarness.java`
- `src/test/java/dev/vapee/core/quest/QuestLobbyModuleHarness.java`
- `src/test/java/dev/vapee/core/quest/QuestLobbyTestSupport.java`
- `src/test/java/dev/vapee/core/quest/daily/menu/DailyQuestMenuLifecycleHarness.java`
- `src/test/java/dev/vapee/core/rank/RankCommandHarness.java`
- `src/test/java/dev/vapee/core/rank/RanksCommandHarness.java`
- `src/test/java/dev/vapee/core/settings/SettingsMenuHarness.java`
- `src/test/java/dev/vapee/core/settings/visibility/VisibilitySettingsMenuHarness.java`
- `src/test/java/dev/vapee/core/utility/command/UtilityCommandHarness.java`
