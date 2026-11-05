# Step 06D — Permission-Aware Command Visibility

Date: 2026-11-05. Base master/origin/master: `aa7d76598a075720fb88359a5619cc50dc29cc87` after safe fetch. Branch: `codex/step06d-command-visibility`. Existing source and protected worktrees were clean/preserved except the known untracked protected-worktree report. No operator server is a test fixture.

## Contract and implementation

Root suggestions are presentation, not authorization. Paper prepares its command tree; the existing PermissionModule owns one HIGHEST PlayerCommandSendEvent listener which only removes entries. Owned VapeeCore roots use their actual `Command.testPermissionSilent(player)` and effective Bukkit permission values. There is no role-name check, secondary permission cache, fixed full root whitelist, command-map mutation, registration trick, packet hook, preprocessor, extra dependency or new module. PermissionModule publishes its service only after listener acquisition and final logging succeed; partial registration rolls back, double enable is rejected, stop makes old callbacks inert and unregisters the exact listener, and re-enable creates one fresh handler.

Public `/staff`, `/core` and `/vapeecore` remain public because their actual root has no permission. `/coins` retains only its existing public base gate; admin actions remain in their executors. `/profile` retains its public permission and manual execution, but is hidden for ordinary players by default as an explicit cosmetic exception. Effective `vapeecore.admin` holders can see it if its own permission allows it. Existing subcommand completion, Step 06C usage/help/events and Step 06C.1 denial rendering are untouched.

Normal/default: public permitted roots and aliases remain visible, except profile cosmetics and identified technical roots. “VIP” with a Fly grant sees Fly; the label itself confers nothing. “Moderator” sees independently granted moderation roots and no Ban without that capability. “Admin” with actual permissions sees those administrative roots; the group name is not consulted. OP follows Bukkit effective defaults; explicit denials still apply. Permission children are interpreted by the existing Bukkit/LuckPerms provider, never reconstructed by the filter.

## Actual descriptor inventory (unchanged)

| Root | Aliases | Existing base permission |
|---|---|---|
| `tpa` | — | `vapeecore.tpa.use` |
| `tpahere` | — | `vapeecore.tpa.use` |
| `tpaccept` | — | `vapeecore.tpa.use` |
| `tpdeny` | — | `vapeecore.tpa.use` |
| `tpcancel` | — | `vapeecore.tpa.use` |
| `staff` | — | `— (public root)` |
| `navigator` | — | `vapeecore.navigator.admin` |
| `quests` | — | `vapeecore.quest.use` |
| `mute` | — | `vapeecore.moderation.mute` |
| `unmute` | — | `vapeecore.moderation.unmute` |
| `warn` | — | `vapeecore.moderation.warn` |
| `ban` | — | `vapeecore.moderation.ban` |
| `unban` | — | `vapeecore.moderation.unban` |
| `kick` | — | `vapeecore.moderation.kick` |
| `history` | — | `vapeecore.moderation.history` |
| `vapeecore` | `core` | `— (public root)` |
| `spawn` | — | `vapeecore.lobby.spawn` |
| `setspawn` | — | `vapeecore.lobby.setspawn` |
| `coins` | — | `vapeecore.economy.coins` |
| `profile` | — | `vapeecore.profile.view` |
| `friend` | `friends` | `vapeecore.friend.use` |
| `clan` | `clans` | `vapeecore.clan.use` |
| `msg` | — | `vapeecore.message.use` |
| `reply` | `r` | `vapeecore.message.use` |
| `settings` | — | `vapeecore.settings.use` |
| `ignore` | — | `vapeecore.social.ignore` |
| `unignore` | — | `vapeecore.social.ignore` |
| `ignorelist` | — | `vapeecore.social.ignore` |
| `warp` | — | `vapeecore.warp.admin` |
| `build` | — | `vapeecore.utility.build` |
| `fly` | — | `vapeecore.utility.fly` |
| `speed` | — | `vapeecore.utility.speed` |
| `gamemode` | `gm` | `vapeecore.utility.gamemode` |
| `tp` | `teleport` | `vapeecore.utility.teleport` |
| `tphere` | — | `vapeecore.utility.teleport.here` |
| `heal` | — | `vapeecore.utility.heal` |
| `feed` | — | `vapeecore.utility.feed` |
| `ping` | — | `vapeecore.utility.ping` |
| `clear` | — | `vapeecore.utility.clear` |
| `invsee` | — | `vapeecore.utility.invsee` |
| `enderchest` | — | `vapeecore.utility.enderchest` |
| `rank` | — | `vapeecore.rank.view` |
| `ranks` | — | `vapeecore.ranks.view` |

All 43 roots and six ordinary aliases remain registered. Future owned roots automatically follow their declared base gate. Foreign/unresolved future commands are preserved. Descriptor gates and execution, permissions/defaults/children, staff hierarchy, target safety, player data and persistence are unchanged.

## Technical roots, owners and conflicts

Ordinary-player cosmetics remove Bukkit `?`, `help`, `about`, `version`, `ver`, `plugins`, `pl`, plus the actual LuckPerms root `luckperms` and aliases `lp`, `perm`, `perms`, **`permission`**, `permissions` discovered in the isolated native inventory. A cosmetic operator exception uses existing effective `vapeecore.admin` plus the existing command gate and upstream offer. It neither adds an entry absent from Paper's collection nor grants any LuckPerms/other-plugin operation; an LP root with no base descriptor gate still owns its internal action checks.

The Minecraft `w` alias is hidden for ordinary players only when actual platform ownership is Minecraft and VapeeCore owns the competing `msg` root. Other vanilla roots are not broadly filtered. Operator-marker holders can retain `w` if its own gate permits it. All ordinary VapeeCore aliases, including `r`, `friends`, `clans`, `gm`, `teleport`, `core`, remain available. No alias is disabled or unregistered.

Owner identity precedes every name rule. PluginIdentifiableCommand uses the owning plugin instance for VapeeCore; LP is identified by its registered plugin and canonical root. Exact legacy builtin classes identify Bukkit commands. Paper 1.21.11 exposes Bukkit, Minecraft and foreign Brigadier roots through VanillaCommandWrapper; its public help-owner and API plugin metadata are read only for that exact known wrapper type. Native Paper information roots use help owner `Paper`, Minecraft roots use `Minecraft`; actual plugin metadata always protects a foreign wrapper even if its help label says Paper. Missing/unknown metadata remains visible. Names are normalized only to classify that already-verified owner; no generic prefix/name guessing is used. Same-name foreign plugin commands remain untouched. This narrow Paper metadata adapter avoids an NMS compilation dependency; a future incompatible wrapper intentionally loses cosmetic filtering until reviewed, rather than hiding foreign commands.

Native inventory: 310 registered command-map labels were inspected read-only, including all namespaces. This is an inventory row count, not 310 behavioral acceptance checks.

## Namespaces and server configuration

Recommended operator-managed `spigot.yml`:

```yaml
commands:
  send-namespaced: false
```

Paper handles namespace omission during usable-tree preparation. VapeeCore adds no generic colon-removal filter. With the setting true, permitted namespace duplicates can remain. Namespaced manual execution is unchanged. This task never writes operator spigot/Paper/LP files. Only the disposable native fixture receives this setting. [Paper setting reference](https://docs.papermc.io/paper/reference/spigot-configuration/) and [Paper 1.21.11 event contract](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerCommandSendEvent.html).

## Optional runtime configuration and reload

Optional additions to existing `config.yml` (these are runtime defaults when omitted):

```yaml
commands:
  visibility:
    enabled: true
    hide-profile: true
```

The two booleans are part of ConfigService's immutable state. No generalized allow/deny lists or replacement config architecture were added. Missing keys preserve compatible defaults; explicit invalid flags retain visibility, and malformed sections disable the cosmetic filter with warnings. Explicit YAML null is distinguished from missing data. Malformed YAML fails preparation without publishing state. Files are read only; no automatic addition of optional keys, ConfigEvolution revision or resource-default migration is introduced. `src/main/resources/config.yml` remains unchanged; options are documented rather than triggering installed-file evolution.

Prepare has no visible effect. Apply publishes before refresh; rollback restores before refresh. Only an actual change to either flag calls `Player.updateCommands()` once per still-online player. Equal state causes no redundant synchronization. The existing seven-participant reload list is unchanged; reload never registers another handler.

## Permission refresh and threading

The listener reads effective permissions on every standard send and never calls `updateCommands()` itself. The inspected LuckPerms 5.5.84 implementation already owns command-list refresh for changed user data, resolves a currently online player and schedules on the server thread. VapeeCore therefore adds no LP event subscription, scheduler, background process or polling task and needs no unsubscribe for such a resource. Logout checks avoid permission/map access from stale event fixtures; config refresh checks online state before each update. The inspected native LuckPerms configuration uses its default `update-client-command-list: true`; this is an environmental prerequisite for LP-owned automatic refresh. If an operator disables it, another explicit refresh/reconnect is needed. VapeeCore never rewrites that preference. Other permission providers remain responsible for their normal Paper update. [LuckPerms updater implementation](https://github.com/LuckPerms/LuckPerms/blob/master/bukkit/src/main/java/me/lucko/luckperms/bukkit/listeners/BukkitCommandListUpdater.java) and [Player.updateCommands API](https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/Player.html#updateCommands()). Actual 5.5.84 bytecode is retained as evidence, rather than relying only on current upstream source.

## Verification and native boundary

Baseline only: 231 harnesses / 34,484 checks, Maven/Spotless PASS, 358 native checks from Step 06C.1. Those prior results are not the Step 06D verification.

Focused: CommandVisibilityHarness 102 checks; CommandVisibilityConfigHarness 25; PermissionDenialHarness 430; PermissionDescriptorHarness 917. All passed. The visibility harness uses the actual descriptor, real Bukkit PermissibleBase/PermissionAttachment calculation, defaults, children and explicit denials, executable PluginCommands, concrete Bukkit information commands, real handler lists and the actual module. It tests public composites, future commands without a whitelist, foreign collisions, exact remove-only behavior, no added entries, opt-out/profile settings, manual execution, grants/revokes on update, no recursion/offline reads, failed acquisition, double stop and re-enable. Config tests exercise the real coordinator rollback and byte-preserving optional settings, including invalid/null candidates.

Final new execution of `pwsh -NoProfile -File .\scripts\verify.ps1`: **PASS**. Maven clean package PASS; Spotless PASS (existing pilot scope unchanged); Discovered=233, Executed=233, Passed=233, Failed=0, Missing/Invalid=0, **34,615 checks**. All 231 existing harnesses remain present and passing; no existing harness was edited, removed or weakened. The final run includes the corrected native-informed owner adapter. A prior all-green canonical run preceded that metadata correction and is retained separately, not substituted for this final run.

Baseline delta: +102 CommandVisibilityHarness, +25 CommandVisibilityConfigHarness, +2 dynamic permission-descriptor test-source checks and +2 GUI-authoring checks for the additional Java test files = +131 checks. Existing usage/help, permission-denial, localization/config reload, descriptor and staff/moderation/utility/lifecycle/security suites pass. No command-executor, TabCompleter, MessageStyle/MessageService, plugin.yml, permission-node/default/child or server-config resource was modified.

Native final acceptance: **483 checks, zero failures**, Paper 1.21.11 build 132 (`c5eb079`), LuckPerms 5.5.84, Java 21.0.12.1. Final fresh loopback fixture: port 25658, own PID 27752 exited cleanly with code 0. Its tested JAR is byte-identical to the final canonical artifact (SHA-256 `C3E92019DE3752C4B81A1D32F64ABEE00165146A2BF26F6CA42BDCC9C7F012DF`). All 27 modules and 43 descriptor roots remained present; the existing seven-participant reload completed successfully. The successful final server log has no ERROR-level entries or exception/assertion stack traces; ordinary offline-mode and target-version update notices remain visible and are not misreported as plugin failures.

The probe dispatches real PlayerCommandSendEvent through Paper's registered handlers, with synthetic Player/CommandSource boundaries and actual LuckPerms cached users, effective Bukkit checks, children and grants/denials. It invokes Paper's actual usable-tree preparation into a disposable copied root: `send-namespaced=false` yields no namespaces, while a positive control with the setting true yields namespace entries. It exercises manual owned profile/namespaced-Friends execution, existing command/denial gates, public staff list, all staff access combinations, technical operator exceptions, actual Paper/Minecraft owner metadata, a foreign Paper-wrapper metadata collision, no recursive refresh, config disable/re-enable and owned handler stop/restart. It does not intercept or modify packets or mutate the live command map. These are native API/preparation assertions, not observed client packets or a real connected-player session.

Initial native diagnostics found that Paper's map keySet enumeration alone omitted resolvable legacy aliases; the probe now enumerates the full entry set. They also established the actual builtin help owner `Paper`, leading to the narrowly corrected production metadata adapter with a foreign-plugin countercontrol. A reused LP fixture's stored primary group was explicitly reset with its required inheritance membership between test sections. Earlier diagnostic results remain separate; only the complete final 483-check run is claimed as passing. No execution permission or registered alias was changed to satisfy a probe.

Visual Minecraft-client acceptance remains **open**: no claim of a connected-client inspection follows from synthetic Player, CommandSource or API probes. In-game validation still needs the actual slash suggestion list, aliases/namespaces, profile toggle and live permission-context/grant/revoke refresh. No actual operator-server behavior or mutable world/log/player files are claimed byte-identical.

## Scope protection and commit

Final SHA-256 comparison covers all 1,474 existing tracked/nonignored files across both original worktrees, explicitly including the two ignored Step-06B receipts. Only the five intended existing files differ; five intended files are new. Git status matches the explicit ten-path commit allowlist, with no unexpected changes. Both Run configurations have an empty Git diff and unchanged SHA-256 values; both receipts are unchanged. All existing master/origin, feature and recovery refs remain at their original commits before this task's local commit. The protected chat-polish-wt, its branch/HEAD and existing untracked report are byte/status unchanged. Operator-owned runtime worlds/logs/player files were neither test fixtures nor claimed byte-identical.

Local evidence outside Git: `C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/step06d-command-visibility-20261105/` contains initial/final protection snapshots, exact descriptor inventory, both canonical receipts/logs, actual LP/Paper bytecode evidence, separate native diagnostic runs, and the successful native-05 483-check receipt/log/process exit. Its final delivery receipt records the full local commit SHA and exact committed paths.

Exactly one focused local commit contains the reviewed paths below; its full SHA is recorded outside this self-referencing report in the delivery response/receipt. No push, merge, PR, deployment, operator-server changes, branch deletion, stash, reset, rebase or foreign-worktree cleanup is performed.

## Complete changed paths

- `AGENTS.md`
- `docs/COMMAND_DEVELOPMENT.md`
- `reports/06d/STEP_06D_COMMAND_VISIBILITY.md`
- `src/main/java/dev/vapee/core/VapeeCore.java`
- `src/main/java/dev/vapee/core/config/ConfigService.java`
- `src/main/java/dev/vapee/core/permission/CommandVisibilityConfig.java`
- `src/main/java/dev/vapee/core/permission/CommandVisibilityListener.java`
- `src/main/java/dev/vapee/core/permission/PermissionModule.java`
- `src/test/java/dev/vapee/core/config/CommandVisibilityConfigHarness.java`
- `src/test/java/dev/vapee/core/permission/CommandVisibilityHarness.java`
