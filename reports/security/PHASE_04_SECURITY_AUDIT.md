# Phase 04 — VapeeCore Security Audit

Audit date: **2026-11-01, Europe/Berlin**. Audited production commit: **`94cc93dbf15209ad8969b00ac7c2bdd06358905d`**.

## Executive summary

No direct path from a previously unprivileged Minecraft player to administrator command execution, OP assignment, LuckPerms permission writes, arbitrary console commands or arbitrary filesystem paths was found in the examined source and executed tests. This is a bounded source/application audit, not proof that every server deployment or external plugin is secure.

The main confirmed weaknesses are **retained authority after permission revocation**: an existing BUILD session continues bypassing lobby protection, and an existing administrative Enderchest view is not reconciled when its capability is removed. Neither allows a never-authorized player to obtain that initial access. A separate **conditional privacy finding** demonstrates hidden-player name/online disclosure through public commands when a deployment expects vanish confidentiality. Two P3 observations concern GUI event/API usage and an inaccurate permission description.

| ID | Priority | Classification | Result |
|---|---|---|---|
| AUDIT-01 | P2 | Confirmed revocation boundary gap | Revoked BUILD state still permits protected block-break events; Creative state survives synchronization. |
| AUDIT-02 | P2 | Confirmed retained-view boundary gap | Revoked administrative Enderchest command access does not close or guard the previously opened live target inventory. Actual client item transfer was not attempted. |
| AUDIT-03 | P2, conditional | Confirmed disclosure; confidentiality impact depends on deployment policy | Public profile completion/lookup exposes an explicitly hidden/vanished online player. No staff action is granted. |
| AUDIT-04 | P3 | Confirmed API-contract mismatch; exploit unproven | Several GUI click handlers change open inventories synchronously despite Paper's documented event constraint. No item duplication was demonstrated. |
| AUDIT-05 | P3 | Confirmed documentation mismatch | The permission guide describes `vapeecore.admin` as reload-only, but it also authorizes `/core quest force`. The executor enforces the actual node. |

**P0: none confirmed. P1: none confirmed. No remediation was implemented.** Production sources, resources, module boundaries and existing tests remain unchanged. The added diagnostic intentionally characterizes current gaps; a passing diagnostic is not evidence that those gaps have been fixed.

## Initial state and method

- Repository: `E:/NEXTCLOUD/Programmierung/JAVA/MINECRAFT/VapeeCore`.
- Initial branch: `master`; HEAD, local master and freshly fetched `origin/master` all equal the audited commit above. `git fetch origin --prune` succeeded; Navigator V2 and its height follow-up are already in master. No merge/rebase/reset was performed.
- Dedicated branch: **`codex/security-audit-phase-04`**.
- Initial tracked modification: only the approved `.run/Start VapeeCore Dev Server.run.xml`. Its full diff and SHA-256 were captured before work. It is excluded from staging and the audit commit.
- Before security analysis, `pwsh -File .\scripts\verify.ps1` passed **216/216 harnesses, 32,086 checks, Failed=0, Missing/Invalid=0**, Maven clean package PASS and Spotless PASS. JDK 21; existing six-file Spotless pilot scope unchanged.
- Inventory: **343 production Java source files, 37 descriptor commands, six aliases, 27 registered modules and seven reload participants**. The command descriptor was reconciled with module/root registration and actual executor/completion code. Global searches covered command execution sinks, permission/OP writes, click components, inventory listeners, async entry points and persistence paths; sensitive application boundaries were then read directly.
- Evidence combines source inspection, the existing compiled behavior harnesses, the added 19-check diagnostic and bytecode inspection of the resolved Paper API's `PluginCommand.execute`. That bytecode checks plugin enabled state and `testPermission(sender)` before invoking the executor. Source checks below do not rely solely on descriptor permissions.
- No command or exploit probe was sent to the operator server. No native server/client was started for this audit. Existing process-local fixtures and disposable `target/harness-temp/security-audit-*` files are used for diagnostics.

Primary inventories: [plugin.yml](../../src/main/resources/plugin.yml), [root composition/registration](../../src/main/java/dev/vapee/core/VapeeCore.java), [Utility registration](../../src/main/java/dev/vapee/core/utility/UtilityModule.java), [Moderation registration](../../src/main/java/dev/vapee/core/moderation/ModerationModule.java). Historical audit reports were background only; their old conclusions/counts were not treated as current proof.

## Command and permission matrix

All abbreviated nodes in this section have the exact prefix **`vapeecore.`**. `true` and `op` are the actual defaults in `plugin.yml`, not recommendations for LuckPerms groups. Effective `hasPermission` may be overridden by explicit grants/denials and contexts. P = `Player`; C = `ConsoleCommandSender`; A = any sender with the relevant effective permission. GUI commands require P. “C + target” never means console gets implicit permission.

### Public/default-true capabilities

| Registered command / aliases | Actual base node/default | Actual execution boundary and target scope | Sender and completion behavior | Source |
|---|---|---|---|---|
| `/vapeecore`, `/core` | No descriptor node | Overview/help/version public; reload and quest independently require `admin` | A for public/root reload; quest force has additional actor/target restrictions. Root completion exposes only help/version without admin. | [CoreCommand](../../src/main/java/dev/vapee/core/command/CoreCommand.java) |
| `/quests` | `quest.use` / true | Executor checks node and menu availability; self-only quest display, no claim/mutation action | P; empty completion | [DailyQuestCommand](../../src/main/java/dev/vapee/core/quest/daily/command/DailyQuestCommand.java) |
| `/spawn` | `lobby.spawn` / true | Executor checks node; only sender teleports to configured/loaded spawn | P; empty completion | [SpawnCommand](../../src/main/java/dev/vapee/core/lobby/command/SpawnCommand.java) |
| `/coins` | `economy.coins` / true | Self balance/help; get/add/remove/set additionally require `economy.admin`. Mutations require known, loaded online target and staff-target guard. | P for self; A for permitted get; P/C for guarded mutations. Admin actions/targets absent from ordinary completion. | [CoinsCommand](../../src/main/java/dev/vapee/core/economy/command/CoinsCommand.java) |
| `/profile` | `profile.view` / true | Executor checks node; self or known name/UUID; intentionally returns others' profile, coins and join dates | P for self, A with explicit target; completion enumerates online names, see AUDIT-03 | [ProfileCommand](../../src/main/java/dev/vapee/core/identity/command/ProfileCommand.java) |
| `/friend`, `/friends` | `friend.use` / true | Executor checks node; actor UUID always comes from actual sender. Own relationships/requests only; domain revalidates current relation, privacy and limits. | P; permission-aware actions and own relationship targets; add enumerates online users | [FriendCommand](../../src/main/java/dev/vapee/core/friend/command/FriendCommand.java) |
| `/clan`, `/clans` | `clan.use` / true | Executor checks node; own clan role is server-side. Owner operations require actual clan ownership; invitations require actual recipient. Public clan info is intentional. | P; root/target completion depends on current membership/ownership | [ClanCommand](../../src/main/java/dev/vapee/core/clan/command/ClanCommand.java) |
| `/msg` | `message.use` / true | Executor checks node; real sender, exact online recipient; service checks mute, loaded profiles, PM preference and both Ignore directions | P; permission-aware online-name completion, not vanish-aware | [MessageCommand](../../src/main/java/dev/vapee/core/privatemessage/command/MessageCommand.java) |
| `/reply`, `/r` | `message.use` / true | Executor checks node; reply UUID comes from server conversation map and flows through the same send checks | P; no target argument, empty completion | [ReplyCommand](../../src/main/java/dev/vapee/core/privatemessage/command/ReplyCommand.java) |
| `/settings` | `settings.use` / true | Executor checks node; settings/visibility add/remove always mutate actual sender's UUID, never argument-as-owner | P; own added-player removal targets; add enumerates online users | [SettingsCommand](../../src/main/java/dev/vapee/core/settings/command/SettingsCommand.java) |
| `/ignore` | `social.ignore` / true | Executor checks node; exact online target, sender owns Ignore list | P; online names except self/already ignored; not vanish-aware | [IgnoreCommand](../../src/main/java/dev/vapee/core/social/command/IgnoreCommand.java) |
| `/unignore` | `social.ignore` / true | Executor checks node; target must be in sender's current Ignore set; ambiguous stored names rejected | P; own ignored names/UUIDs only | [UnignoreCommand](../../src/main/java/dev/vapee/core/social/command/UnignoreCommand.java) |
| `/ignorelist` | `social.ignore` / true | Executor checks node; sender's own Ignore entries | P; empty completion | [IgnoreListCommand](../../src/main/java/dev/vapee/core/social/command/IgnoreListCommand.java) |
| `/rank` | `rank.view` / true | Executor checks node; reads online self/explicit target's actual primary rank, never changes LuckPerms | P for self, A with target; online-name completion, see AUDIT-03 | [RankCommand](../../src/main/java/dev/vapee/core/rank/command/RankCommand.java) |
| `/ranks` | `ranks.view` / true | Executor checks node; read-only configured public track | A; empty completion | [RanksCommand](../../src/main/java/dev/vapee/core/rank/command/RanksCommand.java) |

There are **14 default-true descriptor commands plus the public Core root**, backed by 11 shared default-true nodes. Other profile coins are already public through `/profile`; `/coins get` being admin-only does **not** make balances confidential. This is an explicit current product/data contract, not a demonstrated wallet mutation bypass.

### Administrative and staff capabilities

All nodes below default to **op**. There is no `isOp()` authorization shortcut in production sources: commands use effective permissions. The deprecated `lobby.build` node grants its declared child `utility.build`; the child does not grant unrelated utilities. Configured rank colors, group display text, weights and tablist order grant no capabilities.

| Registered command / aliases | Actual executor capability beyond descriptor | Scope and sender restrictions | Completion | Source |
|---|---|---|---|---|
| `/navigator` | `navigator.admin` | P, live editor session/owner/revision and rechecked permission; draft/preview/save/region operations | Authorized players only; configured IDs only | [NavigatorEditor](../../src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditor.java) |
| `/setspawn` | `lobby.setspawn` | P, sender's current location; controlled lobby save | Empty | [SetSpawnCommand](../../src/main/java/dev/vapee/core/lobby/command/SetSpawnCommand.java) |
| `/warp` | `warp.admin` | All subcommands gated; `set` requires P position, remaining permitted metadata/read operations accept A. No public `/warp <id>` teleport fallback. | Permission-first; subcommand-specific Warp IDs/materials | [WarpCommand](../../src/main/java/dev/vapee/core/lobby/warp/command/WarpCommand.java) |
| `/build` | `utility.build` | P, lobby only, activity exclusion; grants runtime BUILD/Creative. Revocation gap: AUDIT-01. | Empty | [BuildCommand](../../src/main/java/dev/vapee/core/utility/command/BuildCommand.java) |
| `/fly` | `utility.fly` self; `utility.fly.others` other | P self; P/C explicit other; exact online UUID, hierarchy, activity, BUILD and native Creative/Spectator guards | Others node + eligible staff-target filtering | [FlyCommand](../../src/main/java/dev/vapee/core/utility/command/FlyCommand.java) |
| `/speed` | `utility.speed` self; `utility.speed.others` other | P self; P/C other; level 1..10, exact online target, hierarchy/activity guard | Capability-aware levels and eligible targets | [SpeedCommand](../../src/main/java/dev/vapee/core/utility/command/SpeedCommand.java) |
| `/gamemode`, `/gm` | `utility.gamemode` self; `utility.gamemode.others` other | P self; P/C other; validated mode, target hierarchy, activity and BUILD guards | Capability-aware modes/targets | [GameModeCommand](../../src/main/java/dev/vapee/core/utility/command/GameModeCommand.java) |
| `/tp`, `/teleport` | `utility.teleport`, `.others`, `.world`, `.others.world` according to parsed form | P self; P/C explicit source. Exact online players, finite/range-checked coordinates, loaded worlds, affected-source hierarchy. `.bypass` bypasses activity-state guard only, not base capability or hierarchy. | Each form/world/target capability checked | [TeleportCommand](../../src/main/java/dev/vapee/core/utility/command/TeleportCommand.java) |
| `/tphere` | `utility.teleport.here`; optional `utility.teleport.bypass` | P; exact other target, hierarchy; bypass concerns activity only | Node + eligible targets | [TeleportHereCommand](../../src/main/java/dev/vapee/core/utility/command/TeleportHereCommand.java) |
| `/heal` | `utility.heal` self; `utility.heal.others` other | P self; P/C other; exact target, hierarchy and activity guard | Others node + eligible targets | [HealCommand](../../src/main/java/dev/vapee/core/utility/command/HealCommand.java) |
| `/feed` | `utility.feed` self; `utility.feed.others` other | P self; P/C other; exact target, hierarchy and activity guard | Others node + eligible targets | [FeedCommand](../../src/main/java/dev/vapee/core/utility/command/FeedCommand.java) |
| `/ping` | `utility.ping` self; `utility.ping.others` other | P self; A explicit permitted other; read-only, no staff hierarchy mutation | Others node required | [PingCommand](../../src/main/java/dev/vapee/core/utility/command/PingCommand.java) |
| `/clear` | `utility.clear` self; `utility.clear.others` other | P self; P/C other; hierarchy; activity/BUILD state protected | Others node + eligible targets | [ClearCommand](../../src/main/java/dev/vapee/core/utility/command/ClearCommand.java) |
| `/invsee` | `utility.invsee` | P; exact online target + hierarchy; cloned read-only snapshot. `utility.invsee.modify` is reserved and grants no implemented edit mode. | Node + eligible targets | [InvseeCommand](../../src/main/java/dev/vapee/core/utility/command/InvseeCommand.java) |
| `/enderchest` | `utility.enderchest` self; `utility.enderchest.others` other | P; exact online owner + hierarchy; live inventory. Retained-view gap: AUDIT-02. | P + others node + eligible targets | [EnderChestCommand](../../src/main/java/dev/vapee/core/utility/command/EnderChestCommand.java) |
| `/mute` | `moderation.mute` | P/C; known name/UUID, duration/reason validation, no self mutation, hierarchy | Permission/actor first; applicable targets/durations | [MuteCommand](../../src/main/java/dev/vapee/core/moderation/command/MuteCommand.java) |
| `/unmute` | `moderation.unmute` | P/C; known target/current sanction, reason, hierarchy | Permission/actor first; relevant sanctioned targets | [UnmuteCommand](../../src/main/java/dev/vapee/core/moderation/command/UnmuteCommand.java) |
| `/warn` | `moderation.warn` | P/C; known target, bounded literal reason, no self mutation, hierarchy | Permission/actor first | [WarnCommand](../../src/main/java/dev/vapee/core/moderation/command/WarnCommand.java) |
| `/ban` | `moderation.ban` | P/C; known target, validated duration/reason, no self mutation, hierarchy; persistence before kick | Permission/actor first | [BanCommand](../../src/main/java/dev/vapee/core/moderation/command/BanCommand.java) |
| `/unban` | `moderation.unban` | P/C; known target/current sanction and hierarchy | Permission/actor first; sanctioned targets | [UnbanCommand](../../src/main/java/dev/vapee/core/moderation/command/UnbanCommand.java) |
| `/kick` | `moderation.kick` | P/C; known online target, reason, no self mutation, hierarchy; persistence before disconnect | Permission/actor first | [KickCommand](../../src/main/java/dev/vapee/core/moderation/command/KickCommand.java) |
| `/history` | `moderation.history` | P/C; known target/page and hierarchy, self-history exception still requires node | Permission/actor first, hierarchy-filtered known targets | [HistoryCommand](../../src/main/java/dev/vapee/core/moderation/command/HistoryCommand.java) |

Moderation's shared actual boundary is [AbstractModerationCommand.onCommand/authorize/resume/onTabComplete](../../src/main/java/dev/vapee/core/moderation/command/AbstractModerationCommand.java). Offline LuckPerms completion is only a load barrier: the continuation returns to the main thread, checks module active state, exact original live Player session, effective command permission, and fresh hierarchy before executing. A disconnect/reconnect with the same UUID does not retain the old callback's authority.

### Core subcommands, senders, aliases and target semantics

| Actual subcommand | Execution checks |
|---|---|
| `/core reload` | `CoreCommand.reloadConfig`, `admin`; no player-only requirement. A command block already granted this effective capability can invoke reload; there is no console-only restriction. |
| `/core quest force [player]` | Root and [CoreQuestCommand](../../src/main/java/dev/vapee/core/quest/daily/command/CoreQuestCommand.java) independently check `admin`; self requires P, explicit target requires online/loaded player and staff-target authorization. |
| `/coins get <known-name\|uuid>` | Both `economy.coins` and `economy.admin`; read-only known online/offline wallet. |
| `/coins add/remove/set <known-name\|uuid> <amount>` | Same two nodes, loaded/online target, [OnlineStaffTargetGuard](../../src/main/java/dev/vapee/core/command/OnlineStaffTargetGuard.java), numeric validation and persisted mutation; no untrusted offline wallet editing. |

The target guard accepts actual Players and `ConsoleCommandSender`; other sender types, including command blocks, fail closed for guarded mutations. Do not assume every remote/proxied command sender implements `ConsoleCommandSender`. Read-only forms and explicitly A-marked commands do not share that whitelist. Command blocks are operator-installed server content, not player-originated console identities.

Self/other utility selection uses actual UUIDs after exact name resolution. Utility names are not interpreted as offline UUID mutation targets. Name ambiguity is rejected for persisted identity commands. Staff-target hierarchy is an additional restriction, not a replacement for the operation's permission. The hierarchy uses configured protected **primary groups**, not presentation weights, group colors or OP status. There is no unconditional Owner/OP bypass for equal/higher protected targets. Correct primary-group/protected-group configuration remains an operator responsibility.

All six aliases (`core`, `friends`, `clans`, `r`, `gm`, `teleport`) bind to their descriptor command's same executor. No label-specific alternate authorization branch was found. Namespaced labels that resolve to VapeeCore's PluginCommand retain that boundary. Commands belonging to Minecraft or other plugins, alias overrides and proxy routing are separate deployment surfaces.

## Interactive and GUI review

| Surface | Server-side boundary verified | Important scope/qualification |
|---|---|---|
| Friends chat RUN_COMMAND | UUID-bearing accept/deny invokes the ordinary FriendCommand, then current incoming request/recipient checks in FriendService | Receiving/copying/replaying another user's button cannot act as that user. Ignored/privacy/limit rules remain domain-owned. |
| Clan chat RUN_COMMAND | Parsed canonical clan UUID; actual sender's current invite and clan membership/ownership checked | A copied invite is not a membership/owner token. Clan Owner is a community role, not server administration. |
| SUGGEST_COMMAND | Help, friend add, clan create/disband, Warp edit suggestions, history paging and Navigator text prompts insert command text only | Even an actual RUN_COMMAND executes as the clicking player, not console. No production `dispatchCommand`, `performCommand`, `setOp`, attachment grant or OS process execution sink was found. |
| Books/signs | No plugin-authored executable-book/sign interaction or book/sign command dispatcher found in production inventory/search | External plugins and vanilla editable content are not claimed audited. |
| FriendMenu | [listener](../../src/main/java/dev/vapee/core/friend/gui/FriendMenuListener.java) cancels recognized holder events before owner/binding/active/current-view checks; rechecks `friend.use`; actions use holder slot→UUID and current service state | Item name/lore/head UUID never authorizes a relation mutation. Revocation removes exact owned binding; stale/foreign views remain inert. |
| ClanMenu | [listener](../../src/main/java/dev/vapee/core/clan/gui/ClanMenuListener.java) checks holder binding/owner/active/current view, `clan.use`, current clan ID and actual service ownership | `ClanMenu.open` relies on its trusted caller for initial capability; command and action boundaries enforce it, and refresh checks it. No untrusted direct-open route was identified. |
| Settings, VisibilitySettings, VisiblePlayers | [SettingsListener](../../src/main/java/dev/vapee/core/settings/SettingsListener.java), [VisibilitySettingsListener](../../src/main/java/dev/vapee/core/settings/visibility/VisibilitySettingsListener.java), [VisiblePlayersListener](../../src/main/java/dev/vapee/core/settings/visibility/VisiblePlayersListener.java): exact owner/inventory/holder/active/current binding, current `settings.use`, own UUID, supported click and target slot | All recognized top-menu drags are cancelled; unsupported/bottom/number-key/offhand interactions cannot become domain mutations. Persistence failure rollback remains in the settings service. |
| Current Player Menu / profile entry points | [PlayerMenu](../../src/main/java/dev/vapee/core/lobby/experience/player/PlayerMenu.java) and [PlayerSelectionListener](../../src/main/java/dev/vapee/core/lobby/experience/player/PlayerSelectionListener.java) check loaded NORMAL/lobby/activity eligibility and destination capability at use | There is no separate current profile-edit inventory. `/profile` is a read-only text command; root menu has visibility/friends/settings destinations. |
| Navigator player menu | [NavigatorListener](../../src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorListener.java), [NavigatorMenu.activateEntry/validateView/routeAllowed](../../src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenu.java): exact bound active view, current state, complete breadcrumb/link permissions and leaf entry permission | Destination existence/visibility rechecked at teleport; preview cannot teleport. Permission is per configured entry/link, not a global WarpService ACL. All public routes to a sensitive destination must be configured accordingly. |
| Navigator editor / region tool | Current `navigator.admin`, live authenticated session UUID/nonce, owner, exact view/revision or server lease + issued ItemStack/slot/menu/entry | Save is explicit and conflict-checked. Tool copies/stale/foreign PDC cannot grant a lease or bypass current permissions. Revocation is reconciled on use/move/held/inventory/command; orphan/quit/death/shutdown cleanup remains covered. |
| DailyQuest menu | [listener](../../src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestMenuListener.java): owner/binding/active/current view and current quest capability | Display/navigation only; clicking quest lore/item does not grant a reward. Reward APIs are trusted server APIs. |
| Invsee | [InvseeService](../../src/main/java/dev/vapee/core/utility/InvseeService.java): cloned snapshot, bound owner/active registry; click/drag/drop/offhand cancellation, close/quit/disable cleanup | No mutation mode even with reserved `invsee.modify`. A previously rendered snapshot is not equivalent to live target inventory authority. |
| Enderchest | Initial command permission and staff-target guard | Live native target inventory has no corresponding retained-access registry/revocation listener; AUDIT-02. |
| Lobby item PDC / heads / glide / seats | Registry-supported action plus feature eligibility/capability; [CachedPlayerHeads](../../src/main/java/dev/vapee/core/ui/CachedPlayerHeads.java) UUID is presentation only. Glide/seat interactions are bounded public mechanics, not administrative `/fly` or staff commands. | A recognized material/name or copied head is not a command capability. No generalized client-supplied action string is executed. |

Friend/Clan names, usernames, messages and moderation reasons use literal Components or safe placeholder insertion. Operator-authored templates and LuckPerms metadata may use formatting; their formatting does not replace permission checks. The inspected PM path checks recipient preference and both Ignore directions, treats message body as `Component.text`, and resolves replies through the server UUID conversation map. Ignore confidentiality is not a general promise to hide all online existence; see AUDIT-03 and the policy questions below.

## Confirmed findings and hardening observations

### AUDIT-01 — P2: BUILD authority survives permission removal

1. **Affected source/methods:** [BuildCommand.onCommand](../../src/main/java/dev/vapee/core/utility/command/BuildCommand.java#L83); [LobbyPlayerStateService.getMode/isBuildMode/synchronize](../../src/main/java/dev/vapee/core/lobby/player/LobbyPlayerStateService.java#L68); [LobbyListener.onBlockBreak/onBlockPlace/canBuild](../../src/main/java/dev/vapee/core/lobby/LobbyListener.java#L127).
2. **Prerequisites:** Player legitimately entered `/build` in the configured lobby while `vapeecore.utility.build` was true, then loses that effective node without quitting/leaving/resetting BUILD. No OP flag is required. A never-authorized player is denied by the positive control.
3. **Failed boundary:** Command entry checks permission once; subsequent protection events accept the cached BUILD UUID alone. No permission-change reconciliation was found. `synchronize` explicitly retains BUILD and Creative. A denied later `/build` returns before exiting the stale mode.
4. **Impact:** A now-unprivileged former builder can continue receiving lobby block break/place/drop/pickup exemptions and retaining BUILD's Creative mode until existing cleanup. This is a revocation gap, not a new player's ability to invoke `/build` or obtain arbitrary admin commands.
5. **Safe reproduction/evidence:** The diagnostic uses the actual BuildCommand, real LobbyPlayerStateService and actual LobbyListener with isolated Bukkit transport and an enabled protection config. Before grant, BlockBreakEvent is cancelled. Grant → command → revoke → BlockBreakEvent is **not cancelled**, permission=false, OP=false, BUILD=true, mode=CREATIVE. Synchronization retains it; quit cleanup restores block denial. No block setter is called and no world exists in this fixture.
6. **Minimal remediation:** Before honoring BUILD as a protection exemption, validate the current self-build capability and fail closed. Reconcile the owned BUILD/Creative session through existing exit/normal-state cleanup on the main thread, with event-safe timing. Preserve unrelated plugin ownership; do not globally undo game modes/flight deliberately assigned to a recipient by an authorized staff command.
7. **Regression tests:** Convert the audit characterization into deny/cleanup expectations; retain never-granted/granted controls. Add permission/LP-context removal while breaking, placing, dropping, using Creative inventory, respawning, synchronizing, leaving world and reconnecting. Existing LobbyHarness/BuildCommandHarness remain the lifecycle/entry controls.

### AUDIT-02 — P2: live foreign Enderchest lacks retained-access enforcement

1. **Affected source/methods:** [EnderChestCommand.onCommand](../../src/main/java/dev/vapee/core/utility/command/EnderChestCommand.java#L66); [UtilityService.openEnderChest/cleanup](../../src/main/java/dev/vapee/core/utility/UtilityService.java#L146); [UtilityListener](../../src/main/java/dev/vapee/core/utility/UtilityListener.java); [InvseeService.onInventoryClick](../../src/main/java/dev/vapee/core/utility/InvseeService.java#L107).
2. **Prerequisites:** Actor had `.enderchest.others` and passed target hierarchy when opening the other online player's chest; effective access is then revoked, or relevant target authority changes while the view stays open. Other plugins must not independently protect/close it. A base-only player cannot obtain the initial foreign view.
3. **Failed boundary:** `viewer.openInventory(owner.getEnderChest())` opens the target's native live inventory. Utility retains no owner/view capability binding for it. Invsee's read-only checks apply only to InvseeInventoryHolder. Command re-entry denies after revocation, but does not release the previous view; Utility shutdown cleanup concerns managed speed/flight and Invsee views, not issued Enderchest views.
4. **Impact:** The still-open native container has no VapeeCore permission/hierarchy recheck for subsequent vanilla inventory operations, retaining previously authorized target-content access after removal of authority. This is not a demonstrated item duplication or cross-user GUI forgery. Exact native transfer/persistence behavior after target disconnect remains to be tested in a disposable server.
5. **Safe reproduction/evidence:** Diagnostic proves initial other-target denial, authorized open of the exact target inventory object, subsequent permission=false/OP=false, denied fresh command with no extra open, retained prior target view, and an uncancelled click through Utility's Invsee listener. No inventory contents or real player data are changed. For native confirmation, use two disposable accounts outside other inventory restrictions: open foreign chest with a grant, revoke grant, try one harmless test-item transfer, close and check owner contents.
6. **Minimal remediation:** Track only plugin-issued administrative live views with exact viewer/owner/inventory identity. Validate current capability and current target hierarchy before accepting click/drag/transfer; release only the still-owned current view on revoke, quit and disable. Cover bottom/shift/number-key/offhand/collect routes. Do not wrap all inventories in a new framework or close unrelated/vanilla personal Enderchest views.
7. **Regression tests:** Grant/open/revoke/mutate; hierarchy change; actor reconnect and owner quit; close replacement; direct and shifted transfers/drag; module disable. Existing UtilityCommandHarness checks initial permissions/targets, UtilityInventoryHarness the distinct Invsee contract.

### AUDIT-03 — P2 conditional: hidden online identities leak through public lookup/completion

1. **Affected source/methods:** [ProfileCommand.onTabComplete/sendProfile](../../src/main/java/dev/vapee/core/identity/command/ProfileCommand.java#L101); [PlayerProfileService.buildProfile](../../src/main/java/dev/vapee/core/identity/PlayerProfileService.java); [OnlinePlayerResolver.suggest/resolveExact](../../src/main/java/dev/vapee/core/utility/OnlinePlayerResolver.java); RankCommand completion and several PM/social/friend/settings online-target completions.
2. **Prerequisites:** Deployment uses a vanish/hidden-player policy that intends to conceal online identity, the target remains in Paper's server-side online collection, and its plugin does not separately intercept these commands. Ordinary default-true `profile.view` suffices for the demonstrated disclosure; no prior staff permission is needed.
3. **Failed boundary:** Online collections are treated as public disclosure lists without viewer-aware filtering. Profile lookup's `online` flag uses actual `Player.isOnline`, independent of viewer visibility. This differs from Navigator region population, which explicitly filters `canSee`, invisibility and `vanished`/`vanish` metadata. Friends/Clan/presence also publish online state without a general vanish disclosure policy; their implemented Ignore/preferences checks should not be mistaken for one.
4. **Impact:** A regular player can learn hidden online names from completion and confirm a known UUID is online through profile text. This is a **confirmed behavior**, with a **conditional confidentiality impact**: ordinary personal lobby visibility is a rendering preference, not automatically a secrecy guarantee. No command capability, moderation ability or LP write is obtained. Public profile coins/join dates are intentional current fields, not separately claimed vulnerabilities.
5. **Safe reproduction/evidence:** Synthetic target is online, has truthy vanished metadata, and viewer.canSee(target)=false. A non-OP viewer with only profile.view receives `[HiddenStaff]` in actual ProfileCommand completion and `Status: Online` through known-UUID execution. Shared resolver also returns that name. No server/client or external vanish plugin is needed to prove the disclosure decision; actual integration behavior still depends on that plugin's interception.
6. **Minimal remediation:** First agree the disclosure policy. If vanish secrecy is required, apply a small viewer-aware disclosure decision to relevant command/presence outputs and known-target results, without inventing rank/OP bypasses or confusing user-selected lobby filtering with global vanish. Preserve intentionally public offline profile data and safe explicit relationships where the chosen policy permits them.
7. **Regression tests:** Hidden/visible, truthy/false metadata, ordinary versus legitimately permitted observer, completion and explicit name/UUID, PM feedback, presence/summary and friend/clan online badges. Test grants/revokes and current-session/world transitions with the chosen external integration.

### AUDIT-04 — P3: synchronous inventory replacement within click events

1. **Affected source/methods:** [NavigatorEditor.click → GUI action callbacks/showRegion/showMenu](../../src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditor.java#L735), NavigatorListener → openPage/back/activateEntry, FriendMenuListener.navigate, ClanMenuListener.navigate and Settings/Visibility navigation callbacks.
2. **Prerequisites:** An ordinary eligible player interacts with a valid menu; unusual click timing/cursor state may expose protocol issues. No forged server object or admin permission is assumed for public menus.
3. **Boundary:** Paper's [InventoryClickEvent API](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/inventory/InventoryClickEvent.html) directs inventory-view changes into a next-tick task. Current navigation performs open/close synchronously from the event callback.
4. **Impact:** Confirmed unsupported event-timing pattern; possible state desynchronization/reentrancy needs native investigation. Existing click cancellation and identity checks are valuable mitigations. **No item duplication, privilege escalation or exploitable packet sequence was demonstrated.** This is a P3 hardening observation, not a verified exploit.
5. **Safe test:** On an isolated server/client, cycle menus with cursor items, queued old-window clicks and next-tick close/quit/revoke. Existing process-local event harnesses prove cancellation/ownership, but do not execute the full vanilla container transaction.
6. **Minimal remediation:** Defer only actual view transitions, carrying exact ownership/revision/session identity and rechecking online/module/permission/current view before applying. No generalized GUI rewrite is recommended.
7. **Regression tests:** Deferred transition interrupted by replacement, close, logout, shutdown, permission loss or new editor revision; no action/slot replay and no item loss. A small first-menu pilot plus real-client transaction checks is preferable to an unreviewed bulk conversion.

### AUDIT-05 — P3: permission guide understates Core administration scope

1. **Affected source/methods:** [PERMISSIONS.md, line 282](../../docs/PERMISSIONS.md#L282), CoreCommand.onCommand/onTabComplete, CoreQuestCommand.onCommand.
2. **Prerequisites:** Operator follows the reload-only description when delegating `vapeecore.admin`. That effective node is already explicitly granted; this is not an unauthorized player finding a missing executor check.
3. **Mismatch:** Guide says the node is only for technical Core reload. Current root and quest executor both use it for `/core quest force [player]` as well.
4. **Impact:** Delegated authority may be broader than the operator expects. Force still requires valid online/loaded target and staff hierarchy. No reward-farming exploit is claimed.
5. **Safe reproduction:** Compare the cited source/descriptor with the guide; a sender with admin sees quest in root completion and passes that capability gate. Existing QuestCommandHarness verifies the actual force behavior and denial paths.
6. **Minimal remediation:** Correct the authority catalogue/help documentation first. If reload-only delegation is a requirement, separately review capability separation and migration before changing nodes. No new node is invented by this audit.
7. **Regression tests:** Keep the permission matrix aligned with registered subcommands; test a technical-operator role against the approved intended scope, in addition to default user and full admin roles.

## Service, lifecycle, persistence and integration boundaries

| Area / owning modules | Evidence and assessment |
|---|---|
| Localization, Permission, Rank | PermissionModule requires a real LuckPerms service or startup fails. LuckPermsService reads metadata/groups and can asynchronously load a known user; it has no LP grant/save/primary-group mutation. Effective Bukkit permissions authorize operations. Rank display/weight cannot substitute for them. |
| Player, Social, Economy, Identity, Settings | UUID-owned Player cache and repository; name ambiguity explicit. Settings/Ignore mutations derive owner from actual player. Offline identity/profile/wallet reads do not authorize offline wallet writes. [FilePlayerRepository](../../src/main/java/dev/vapee/core/player/repository/FilePlayerRepository.java) derives player filenames from UUID, not arbitrary names/arguments. |
| Moderation, Friend, Clan | Validated domain snapshots and persistence-before-publication; current requests/recipient/member/owner checked, bounded reasons/names/limits. No caller-provided display string is accepted as an authenticated actor. Domain APIs are trusted server APIs, not separately exposed network endpoints. |
| Presence, Visibility | Main-thread online state and own hide/show ownership; preference/Ignore checks remain effective within their documented scope. Global vanish confidentiality is not consistently provided by those features; AUDIT-03. A `.visibility.staff` marker changes visibility selection, not administrative authority. |
| Reward, OnlineReward, Quest, DailyQuest | No user-facing arbitrary reward grant/claim command. Progress producers and reward-service APIs are trusted plugin code. Pending rewards/flush ownership and persistence rollback are covered by existing lifecycle/negative controls. `/core quest force` is explicitly privileged; AFK playtime reward eligibility is gameplay policy, not proven permission escalation. |
| Lobby, Utility, Warp, LobbyExperience | Public spawn/Navigator/seat/glide operations are intentional bounded mechanics. Warp administration is permission-gated; sensitive utilities use operation nodes and affected-target hierarchy. BUILD/live Enderchest retained authority are the identified exceptions. |
| Chat, PrivateMessage, Presentation | Chat/PM bodies use Components; trusted formatting does not run server commands. [ChatListener](../../src/main/java/dev/vapee/core/chat/ChatListener.java) reads immutable Ignore snapshots from ConcurrentMap; [ModerationMuteProjection](../../src/main/java/dev/vapee/core/moderation/ModerationMuteProjection.java) publishes immutable committed facts through a volatile state for async chat. Mute feedback schedules onto main thread. Presentation changes do not grant permissions. |
| Activity, Seat, WorldDisplay | No new public admin command surface or built-in Blackjack producer remains. Client interactions reach owned seat/activity state, not arbitrary service objects. Service calls from another installed plugin are inside the server-code trust boundary. |

This accounts for all **27 registered modules**. The root's seven ordered reload participants remain exactly **configService, localizationModule, lobbyModule, chatModule, privateMessageModule, presentationModule, dailyQuestModule**.

[ModuleManager](../../src/main/java/dev/vapee/core/module/ModuleManager.java) records failed/unconfirmed cleanup and rolls back enabled predecessors in reverse order; module owners retain their own pending state. [ReloadService](../../src/main/java/dev/vapee/core/reload/ReloadService.java) prepares before applying, rolls back the failing and previously applied plans, rejects reentrant calls and distinguishes incomplete rollback. The existing explicit-restart cleanup contract was not changed. These are local server lifecycle operations; public Core commands cannot invoke reload without admin.

Persistence targets are fixed plugin files or canonical UUID filenames. Friend/Clan names and command arguments do not become filesystem paths. Navigator IDs/slots/types/permissions/graphs are strictly validated; only WARP/MENU/UNAVAILABLE actions exist, no arbitrary command action. Invalid Navigator config selects an unavailable runtime rather than silently exposing gated routes. Duplicate YAML keys and unsafe object construction are rejected by the inspected strict/SafeConstructor readers. [SafeFileWriter](../../src/main/java/dev/vapee/core/persistence/SafeFileWriter.java) validates candidate bytes, uses atomic replacement where available and preserves unresolved recovery copies; Navigator Save adds exact-byte conflict detection, backup verification and runtime/disk rollback.

No player-reachable arbitrary file read/write path or config-to-console execution sink was identified. This does **not** claim complete protection against an attacker already able to change the server filesystem, parent-directory links, plugin JARs or operator config files. Deliberate operator settings, such as disabling protection or adding a public route to a sensitive Warp, are authorization policy decisions rather than an untrusted player's parser bypass.

Malformed scalar lobby protection flags retain the safe true fallback. Invalid reload spawn is rejected; initial unconfigured/invalid spawn can leave no recognized lobby world, so protection is not global across all server worlds. Moderation login query exceptions log and return no fabricated ban, preserving existing event rejection; runtime-query fail-closed policy is an operational question, not a demonstrated player-triggered bypass in this audit.

Async entry points were checked for authority after resumption and access to mutable domain state. Sensitive moderation continuation uses exact session + fresh permissions; chat Ignore/mute use published read-only state. Utilities and inventory lifecycle enforce/assume main-thread ownership at their application boundaries. No claimed general guarantee is made for arbitrary third-party callers invoking main-thread-owned service APIs from async code. CachedPlayerHeads uses only online/Paper cached profiles, UUID validation and completed textures; no new network downloader/cache or callback can acquire menu authority.

## Verification and reproducibility

New diagnostic: [Phase04SecurityAuditHarness](../../src/test/java/dev/vapee/core/utility/command/Phase04SecurityAuditHarness.java), **19 executable checks**. It reuses existing Lobby/Utility fixtures by reflection and invokes production command/service/listener methods. Reflection/Unsafe only wire unused Bukkit transport dependencies; they are not proposed attack primitives and do not modify production behavior. No OP command, world write, inventory transfer or operator file write is executed.

Expected observations before remediation:

```text
AUDIT-01: permission=false, BUILD=true, mode=CREATIVE, blockBreakCancelled=false
AUDIT-02: othersPermission=false, retainedTargetInventory=true, invseeListenerCancelled=false
AUDIT-03: viewerCanSee=false, vanished=true, completion=[HiddenStaff], disclosedOnline=true
```

The first diagnostic compilation caught a test-only access to ProfileCommand's private constant. The test now uses the independently checked descriptor node. No production change was made to accommodate the diagnostic.

Relevant baseline behavior coverage, all passing:

| Existing harness | Checks / relevant boundary |
|---|---|
| CoreCommandHarness / QuestCommandHarness | 79 / 45: public roots versus admin reload/force |
| UtilityCommandHarness / TeleportCommandHarness / BuildCommandHarness | 1,305 / 894 / 17: self/other, sender types, hierarchy and bypass scope |
| CoinsCommandHarness | 2,015: denied ordinary mutation, known/offline read, loaded-only writes, targets and persistence failures |
| ModerationCommandHarness / StaffHierarchyServiceHarness | 4,426 / 109: actor/target authority, offline-load resume, permission/session changes, fail-closed hierarchy |
| FriendCommandHarness / ClanCommandHarness | 133 / 130: actual UUID chat actions and current domain state |
| FriendMenuSecurityHarness / ClanMenuSecurityHarness | 156 / 114: stale, foreign, forged, unsupported transfers, revoked capabilities/roles |
| SettingsMenuSecurityHarness / VisibilityMenuSecurityHarness | 205 / 327: owner/capability/UUID target and persistence boundaries |
| NavigatorSecurityHarness / NavigatorRegionToolHarness / NavigatorRegionHeightHarness | 78 / 79 / 98: bindings, route/target revalidation, tool leases, permission loss and draft persistence |
| QuestMenuSecurityHarness / UtilityInventoryHarness | 45 / existing Invsee snapshot suite: no quest-item reward grant, no snapshot editing |
| ConfigReloadIntegrationHarness / ReloadServiceHarness | 428 / 213: prepare/apply/rollback and reentry |

To reproduce after compiling with the canonical runner, run `dev.vapee.core.utility.command.Phase04SecurityAuditHarness` with `target/test-classes`, `target/classes` and the dependencies from `target/harness-classpath.txt`, exactly as `scripts/run-harnesses.ps1` launches other harnesses. Its assertions are **audit characterizations of unfixed behavior**, not future desired regression expectations. Preserve the ordinary-denial positive controls when converting them after approved fixes.

Final canonical run: **217/217 discovered/executed/passed harnesses, 32,106 checks, Failed=0, Missing/Invalid=0; Maven clean package and Spotless PASS**. All previous 216 harness identities remain and none loses checks. Delta: the new 19-check diagnostic plus one existing GUI-authoring source-inventory check. The canonical runner's documentation-link count remains 100; a separate audit-specific check additionally verified all **75 local report links**, including cited source line bounds. No prior phase's native acceptance totals are reused as this audit's verification.

## Environmental assumptions and open questions

1. **Player identity authentication:** public server online-mode/secure proxy UUID forwarding, LuckPerms grants/contexts/wildcards and other plugin command overrides were not changed or independently penetration-tested. A player able to impersonate another UUID through deployment misconfiguration crosses a different trust boundary.
2. **Revocation policy:** confirm ongoing BUILD and administrative live-container access must end when effective capability changes; decide the intended treatment of command outcomes such as staff-assigned game mode/flight separately. The proposed fixes should not revoke legitimate grants to recipients who never needed the issuer's command permission.
3. **Vanish contract:** is hidden staff existence meant confidential, and which external vanish integration/authorized-observer rules apply? Personal lobby filtering and potion invisibility should not silently become a universal account-secrecy policy. Public profile coins/join dates are currently deliberate; confirm operators understand that.
4. **Staff protection:** protected primary groups must match actual LuckPerms primary-group configuration. OP and secondary group weights do not automatically make a target protected.
5. **Operational failure policy:** establish how the operator handles unset lobby spawn, startup-disable, failed moderation query and incomplete rollback/flush. Do not assume a disabled plugin protects worlds or that logged cleanup failure is a completed durable write.
6. **External integration/supply chain:** audit covers VapeeCore's LuckPerms calls and Paper-facing boundaries, not a CVE scan/certification of Paper, LuckPerms, the JVM, proxies, all dependencies or other installed plugins. No external credentials/RCON or real player database were probed.
7. **Native client checks:** actual Enderchest transfer after revoke and vanilla menu transaction timing remain safe disposable-environment acceptance tasks. No real-client exploitation/visual acceptance is claimed here.
8. **Abuse volume:** no destructive flooding/load test was performed. Public commands can cause synchronous local YAML reads/writes; validated arguments and relationship limits are not a general throughput guarantee. Measure bounded disposable load before claiming a practical denial-of-service exploit or selecting rate limits.

## Recommended remediation order and next implementation tasks

1. Review AUDIT-01 and agree immediate fail-closed BUILD revocation behavior. Implement the narrow event boundary and existing owned-state cleanup; add deny/restore regression controls first.
2. Review AUDIT-02 and implement narrowly owned administrative live-view tracking/revalidation, including hierarchy changes and disable. Confirm transfer behavior with disposable accounts/items and ensure unrelated personal inventories remain usable.
3. Resolve the AUDIT-03 disclosure policy before changing public lookup/presence semantics. Then add targeted viewer-aware filtering and integration tests across the affected outputs.
4. Correct AUDIT-05's capability documentation; only consider permission separation after the intended delegation contract is approved.
5. Address AUDIT-04 in a small event-safe transition pilot with native transaction tests, followed by scoped menu changes if warranted. Avoid a framework rewrite.

Stop for review before remediation. No Staff Panel, new feature phase, persistence redesign, new rank cache, scheduler or configuration architecture is part of this audit.

## Change scope and operator protection

Intended changes are exactly this report and `src/test/java/dev/vapee/core/utility/command/Phase04SecurityAuditHarness.java`. Existing production/test/resource bytes are protected by a tracked-file SHA-256 manifest. No existing harness is removed, weakened or renamed. No production security fixes have been applied.

Run configuration expected SHA-256: **`EEDE5A220EEC6A8D154D82F2BD1BF29CFEB59215D6B546D83CDB38A73EAF596F`**. Full before/after diff and hash are compared, without staging/restoring/rewriting it. Operator server snapshots distinguish identical, externally changed, unreadable/locked, added and removed paths; no byte-identity claim is made for files that cannot be compared.

Evidence root: `C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/phase04-security-audit-20261101/`. Includes preflight HEAD, descriptor/source inventories, tracked-file manifests, Paper PluginCommand bytecode, canonical receipts/logs, diagnostic logs and operator/Run snapshots. At most one local audit commit is created with explicitly named paths. **Push: no. Merge: no. Deployment: no.**

### Final preservation measurements

- **All 706 previously tracked files are byte-identical to preflight**, including all 343 production Java files and every existing harness/resource/document. The only added paths are the report and diagnostic listed above.
- **All 952 protected server/script/Run paths were readable and SHA-256-identical in the before/after snapshots**: zero changed, unreadable, added or removed paths. This is a measured snapshot comparison for this audit, not an assertion that a running server can never change its own data.
- Run full diff and SHA-256 match preflight exactly. The file remains locally modified, un-staged and excluded from the commit; this is the approved exception, not an unexpected modification.
- No unexpected Git worktree changes were found. Master and earlier commits are preserved; the new commit is on the dedicated audit branch only.
- `baseline-results.json`, `final-results.json`, `harness-ledger.json`, `diagnostic-final.log`, `report-link-check.json`, `tracked-before.json` and `protection-comparison.json` contain the corresponding evidence. Diagnostic stdout includes the actual permission/mode/cancellation/disclosure values, not merely material or source-text assertions.
