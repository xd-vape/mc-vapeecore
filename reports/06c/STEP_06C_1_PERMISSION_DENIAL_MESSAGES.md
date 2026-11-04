# Step 06C.1 — Unified Permission Denial Messages

Date: 2026-11-05. Base: `d424e7fa72a23554bf69c16a364d5502d71c9a4b`.
Branch: `codex/step06c1-permission-denial-messages`.

## Problem and implementation

Ordinary command failures used different wording, colors and sometimes a redundant Error label. StaffPanel exposed its primary-group/panel-node implementation details. All 43 registered command roots were inspected, including aliases and delegated CoreQuest execution. Only feedback at existing failed checks changes.

`MessageStyle.commandDenied()` composes the canonical translatable `common.command-denied` with semantic ERROR. `MessageService.commandDenied(recipient[, legacyKey])` renders the current localized component and protects deliberate operator overrides. It never checks a permission or sends a packet. Existing callers retain their normal/raw delivery and SocialChannel. Build, Invsee and TeleportHere inject only the rendering callback into their existing local test seams; no authorization callback, service, module, dependency or cache was added.

Standard EN: `You do not have permission to use this command.`

Standard DE: `Du hast keine Berechtigung, diesen Befehl zu verwenden.`

Examples: `[Friends] You do not have permission to use this command.`, `[Clan] You do not have permission to use this command.`, `[Staff] You do not have permission to use this command.`, `[Team] Du hast keine Berechtigung, diesen Befehl zu verwenden.`.

The entire default body is ERROR/RED. There is no added Error label, node, group name or newline. Prefix brackets remain BRACKET/DARK_GRAY; Friends/Freunde label GOLD, Clan DARK_PURPLE, Staff/Team DARK_GRAY. Other existing feature identities and global operator wrappers are unchanged. Commands without a feature prefix acquire none. TPA and Navigator retain raw delivery.

## Complete focused command inventory

“Shared” means the existing executor's ordinary failed permission check now uses the shared renderer. Descriptor gates may run earlier.

| Registered root(s) | Existing ordinary source key(s) / migration | Preserved distinctions |
|---|---|---|
| `vapeecore` (`core`) | Shared: `core.reload.denied`, `core.quest.denied`; delegated CoreQuest also migrated | Public help/info; admin checks at root and delegate, target guard, reload failures |
| `warn`, `mute`, `unmute`, `kick`, `ban`, `unban`, `history` | Shared: `common.command-denied` at initial and async resumed check, Staff channel | Actor types, current session/runtime, staff hierarchy, self target, offline target, audit/persistence errors |
| `build` | Shared: `utility.build.denied` | Player-only, lobby world, activity, mode state/flight cleanup |
| `invsee` | Shared: `utility.invsee.denied` | Player-only, exact online target, hierarchy, read-only snapshot and modify denial |
| `tphere` | Shared: `utility.teleport-here.denied` | Console, self target, hierarchy, bypass/activity and teleport failure |
| `fly` | Retained `utility.fly.denied-self` / `denied-other` | Meaningful self/others and activity policy |
| `speed` | Retained `utility.speed.denied-self` / `denied-other` | Self/others, level and activity |
| `gamemode` (`gm`) | Retained `utility.game-mode.denied-self` / `denied-other` | Self/others, valid mode, lobby managed state |
| `tp` (`teleport`) | Retained `utility.teleport.denied-self` / `denied-other` / `denied-pair` | Self, source/target, explicit world, others world, hierarchy/activity |
| `heal` | Retained `utility.heal.denied-self` / `denied-other` | Self/others, hierarchy/activity |
| `feed` | Retained `utility.feed.denied-self` / `denied-other` | Self/others, hierarchy/activity |
| `clear` | Retained `utility.clear.denied-self` / `denied-other` | Self/others, protected/managed inventory and hierarchy |
| `enderchest` | Retained `utility.enderchest.denied-self` / `denied-other` | Self/others and inventory access/session policy |
| `ping` | Retained `utility.ping.denied-self` / `denied-other` | Self/others and target visibility |
| `friend` (`friends`) | Shared: existing `common.command-denied`, Friends channel | Requests, relationships, limits, privacy, interactive UUID/request actions |
| `clan` (`clans`) | Shared: `clan.command.denied`, Clan channel | Clan roles, ownership, invite and disband confirmation |
| `settings` | Shared: existing `common.command-denied` | Player-only, visibility edits, loaded data and inventory sessions |
| `profile` | Shared: existing `common.command-denied` | Known online/offline identity, unknown data and usage |
| `coins` | Shared: `economy.denied` at base and admin capability checks | Separate actual checks retained; target hierarchy, wallet availability and mutation policy |
| `rank`, `ranks` | Shared: `rank.denied` | Target identity, current ranks/integration availability |
| `msg`, `reply` (`r`) | Shared: `private-messages.error.denied` | Player-only, mute/ignore/privacy, unknown target/conversation |
| `ignore`, `unignore`, `ignorelist` | Shared: `social.denied` | Self target, identity, stored social state |
| `spawn`, `setspawn` | Shared: `spawn.use-denied`, `spawn.set-denied` | Player-only, lobby restrictions, location/persistence failure |
| `warp` | Shared: `warp.denied` | Player location requirements, syntax, existing target and storage checks |
| `navigator` | Shared: former inline EN/DE no-editor-permission feedback | Same authorized predicate, release/draft/save/tool/session and inventory binding checks |
| `staff` | Shared: `staff.denied`, Staff channel | Permission AND current primary staff group, runtime/current player, sessions/inventory; public `list` |
| `quests` | Shared: `daily-quests.denied` | Player-only, loaded daily data and inventory safety |
| `tpa`, `tpahere`, `tpaccept`, `tpdeny`, `tpcancel` | Shared: `tpa.permission`, raw delivery | Current online sessions, UUID/request identity, expiry, privacy/ignore/activity, acceptance/denial and cancellation |

34 roots migrate ordinary denials; nine roots retain explanatory self/others variants. Legacy feature keys remain in both catalogs for compatibility. The only changed bundled text is German `common.command-denied`; English already had the requested text. All migrated keys have no dynamic placeholders. Existing menu-only feedback (Friends/Clan/settings/visibility/quest inventory access, stale/session failures), Staff action-denied/action-unavailable/target-unavailable, TPA request rejection `tpa.denied`, activity, hierarchy and technical errors remain distinct. No command order, guard, completion, permission node/default or descriptor is changed. Internal Navigator session guard remains a separate fail-closed invariant; its ordinary command boundary no longer exposes technical permission detail.

## Staff security and platform boundary

StaffPanel still requires `vapeecore.staff.panel` AND `StaffHierarchyService.isStaff` using the loaded current primary LuckPerms group. Permission alone, group alone and OP alone remain insufficient. Display rank, weight, inherited group labels and chat style do not confer panel eligibility. Public `/staff list` remains ahead of the private access path. Runtime, current player identity, sessions, inventory binding, permission revocation and action target hierarchy remain enforced.

`plugin.yml` is byte-unchanged. Paper/Bukkit may reject descriptor permissions before the executor. Those platform-owned failures can use platform feedback, rather than this localized component. No descriptor permission was removed, weakened or deferred, no preprocess interception or foreign/global command override was introduced. Step 06D visibility/filtering is absent. Step 06C usage/usageSyntax, CommandHelpRenderer, accents, suggestions, optional arguments and interactive actions are unchanged.

## Operator compatibility and reload

The adapter follows Step 06C's exact declared-default comparison: a live value equal to the bundled value is a copied default and uses the common renderer. A genuinely different legacy feature value retains its complete wording, explicit colors and events and takes precedence over the canonical override. Otherwise `common.command-denied` overrides apply to every migrated default. Override selection uses the recipient language, matching existing locale resolution.

A single bounded historical default is recognized: precisely `Du darfst diesen Command nicht verwenden.` under German `common.command-denied`. It uses the current bundled German wording without writing the installed file. Similar text or different explicit formatting is treated as custom. An intentional value exactly identical to that known old default is indistinguishable from a copied default. Deliberate overrides can intentionally retain nonstandard colors, text length or technical wording; they are an explicit compatibility exception to uniform defaults.

LocalizationModule, LocalizationService and TranslationCatalog remain unchanged. Their existing immutable snapshot prepare/apply/rollback and strict placeholder validation are used; no migration or installed language-file rewrite occurs. There is no additional cache. Operator dev-server configuration/data is not a test fixture.

## Verification

Baseline only: Step 06C documented 230 harnesses / 34,069 checks, Maven and Spotless PASS, 279 native API checks. These are not the new results.

Focused execution: PermissionDenialHarness 430 checks; CommandUsageHarness 454; StaffPanelHarness 126; UtilityCommandHarness 1,305. All passed. Five existing UI assertions are updated for the new actual denial body; no harness is removed or disabled. The new harness checks EN/DE exact output and real inherited color spans, one-packet delivery, channel colors, all migrated key defaults, complete copied catalogs, old German default, real Friend/Clan commands, no mutation/completion access, all staff group/permission combinations, OP/display-rank nonauthority, revocation/runtime, deliberate overrides, interactions, prepare/apply/restore and rejected placeholder candidates.

Full canonical verification (new execution, 2026-11-05): `pwsh -NoProfile -File .\scripts\verify.ps1` **PASS**. Maven clean package PASS; Spotless PASS (existing six-file pilot unchanged); Discovered=231, Executed=231, Passed=231, Failed=0, Missing/Invalid=0, **34,484 checks**. All 230 baseline harnesses remain; the only added harness is PermissionDenialHarness.

The first complete run exposed three obsolete UI expectations: Core's reload-specific wording, the extra moderation Error label on resumed permission revocation, and TPA's old cannot-use wording. All three were precisely aligned with the intended new output, without changing execution/security assertions. Separate compiled reruns passed Core 80, Moderation 4,433 and TPA 162 checks before the successful complete rerun. Additional assertions cover one-message/error-color output and the resumed moderation prefix. The earlier Friends usage and StaffPanel feedback assertions were likewise aligned; their check counts remain unchanged.

Check-count change from baseline: +430 new denial checks, +1 Core, +7 moderation revocation, +1 TPA, +1 GUI-authoring audit of the additional Java test file, and -25 dynamically counted direct `tr(...)` source references consolidated into the shared adapter = net +415. CatalogUsageHarness still renders the complete catalog; the new harness explicitly validates all 17 migrated keys in both languages. No harness or authorization/behavior check was removed or disabled.

Native acceptance: **358 checks, zero failures**, Paper 1.21.11 build 132 (`c5eb079`), Java 21.0.12.1, LuckPerms 5.5.84. Fresh disposable fixture on loopback port 25652; its own process exited cleanly with code 0. All 27 modules enabled; real seven-participant reload completed successfully in the native log. The probe exercised registered Paper descriptor gates (including a sentinel proving the executor is not invoked), direct executor EN/DE feedback, actual loaded LuckPerms user/group/permission data, grants/revokes, public staff list and unchanged granted Friends usage. StaffPanel was instantiated with a controlled online-player lookup and synthetic Player/InventoryView boundary, while using the actual LP hierarchy and native Paper inventories/ItemStack metadata. Both required access conditions, missing-condition denials, revocation and stale binding rejection passed. This is API acceptance with synthetic senders, not a connected-player or visual-client test. No production source changed after the smoke; subsequent changes only align test expectations and documentation. The final canonical JAR and the native-tested JAR have identical contents for all 570 archive entries; their container SHA-256 values differ because of archive metadata. The entry-content comparison is retained with the external test evidence.

Not performed: visual Minecraft-client acceptance, live operator-server checks, Step 06D command filtering. No claim is made about client appearance or actual connected-player end-to-end behavior based only on synthetic senders/API assertions.

## Repository protection and commit

Initial master and origin/master matched the expected base after safe fetch; initial main working tree was clean. The new branch was created directly from that master. Existing branches and the protected `chat-polish-wt` (including its existing untracked report) are preserved. Run configurations and both ignored local Step-06B commit receipts are compared by SHA-256 against the baseline. No operator server is stopped, modified or used for mutable tests. Exactly one focused local commit contains these reviewed changes; its full SHA is recorded in the delivery response/receipt outside this self-referencing report. No push, merge, PR or deployment is authorized or performed.

Final protection check: all 1,472 existing tracked/nonignored files across the main and protected worktree were compared to the initial SHA-256 snapshot. Only the 35 intended existing files differ, plus two intended new files. The 37-path Git status matches the explicit commit allowlist; unexpected changes=0. Run Git diff remains empty, both Run SHA-256 values and both receipts are unchanged, existing branch/remote refs remain at their initial commits. RepoLinkHarness passed 120 checks after the final report update. The pre-existing untracked protected-worktree report remains unchanged.

Local evidence outside Git: `C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/step06c1-permission-denial-20261104/` contains both canonical-run receipts/logs, focused tests, native probe/results/logs and clean process exit, archive-content comparison, initial/final protection snapshots and the final commit delivery receipt.

## Complete changed paths

- `AGENTS.md`
- `docs/development/chat-design-system.md`
- `reports/06c/STEP_06C_1_PERMISSION_DENIAL_MESSAGES.md`
- `src/main/java/dev/vapee/core/clan/command/ClanCommand.java`
- `src/main/java/dev/vapee/core/command/CoreCommand.java`
- `src/main/java/dev/vapee/core/economy/command/CoinsCommand.java`
- `src/main/java/dev/vapee/core/friend/command/FriendCommand.java`
- `src/main/java/dev/vapee/core/identity/command/ProfileCommand.java`
- `src/main/java/dev/vapee/core/lobby/command/SetSpawnCommand.java`
- `src/main/java/dev/vapee/core/lobby/command/SpawnCommand.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditor.java`
- `src/main/java/dev/vapee/core/lobby/warp/command/WarpCommand.java`
- `src/main/java/dev/vapee/core/message/MessageService.java`
- `src/main/java/dev/vapee/core/message/MessageStyle.java`
- `src/main/java/dev/vapee/core/moderation/command/AbstractModerationCommand.java`
- `src/main/java/dev/vapee/core/privatemessage/command/MessageCommand.java`
- `src/main/java/dev/vapee/core/privatemessage/command/ReplyCommand.java`
- `src/main/java/dev/vapee/core/quest/daily/command/CoreQuestCommand.java`
- `src/main/java/dev/vapee/core/quest/daily/command/DailyQuestCommand.java`
- `src/main/java/dev/vapee/core/rank/command/RankCommand.java`
- `src/main/java/dev/vapee/core/rank/command/RanksCommand.java`
- `src/main/java/dev/vapee/core/settings/command/SettingsCommand.java`
- `src/main/java/dev/vapee/core/social/command/IgnoreCommand.java`
- `src/main/java/dev/vapee/core/social/command/IgnoreListCommand.java`
- `src/main/java/dev/vapee/core/social/command/UnignoreCommand.java`
- `src/main/java/dev/vapee/core/staff/StaffPanel.java`
- `src/main/java/dev/vapee/core/utility/command/BuildCommand.java`
- `src/main/java/dev/vapee/core/utility/command/InvseeCommand.java`
- `src/main/java/dev/vapee/core/utility/command/TeleportHereCommand.java`
- `src/main/java/dev/vapee/core/utility/tpa/TeleportRequests.java`
- `src/main/resources/lang/de.yml`
- `src/test/java/dev/vapee/core/clan/gui/CommandUsageHarness.java`
- `src/test/java/dev/vapee/core/clan/gui/PermissionDenialHarness.java`
- `src/test/java/dev/vapee/core/command/CoreCommandHarness.java`
- `src/test/java/dev/vapee/core/moderation/command/ModerationCommandHarness.java`
- `src/test/java/dev/vapee/core/staff/StaffPanelHarness.java`
- `src/test/java/dev/vapee/core/utility/tpa/TeleportRequestsHarness.java`
