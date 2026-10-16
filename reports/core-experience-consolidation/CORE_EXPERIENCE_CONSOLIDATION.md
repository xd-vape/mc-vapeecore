# Core Experience Consolidation

## Baseline and scope (recorded before production edits)

- Inspected source baseline: `69b14bcc299fa7d535a1d02041cbfdabbe9e6f9e`, `master`, localization merge containing `822e1b90c0e5561d938350128c47769e5aeac439`.
- Work branch: `phase/core-experience-consolidation`.
- Existing uncommitted `docs/DEVELOPER_GUIDE.md` and `docs/FORMATTING.md` are the preceding live-localization correction; preserve them and incorporate that current documentation.
- Measured before production edits: **168 discovered/executed/passed, 26,687 checks, 0 failures, 0 missing/invalid**. Previous 168 / 26,686 was a reference, not assumed evidence. Receipt: local `dev-server/.vapeecore-dev/core-experience-consolidation/baseline-results.json`.
- No push, merge, Phase 38, new game, queue, AFK, pay, profile backend, Chat Range, Auto-Join or Glide implementation.

## Before: lobby audit

`LobbyItemRegistrations` explicitly registers behavior; `LobbyItemsConfig` parses presentation by those IDs. `LobbyItemService` builds PDC-owned items and reconciles only owned inventory entries. `LobbyItemListener` cancels managed item movement, accepts main-hand right-click and dispatches registered IDs after `NavigatorAccessPolicy` checks loaded profile, online player, lobby world, NORMAL and no Activity. Registry activation occurs after dependent menus exist. Unknown YAML IDs warn and cannot create behavior. An explicit `items` section disables omitted registrations.

| Stable ID | Resource/default slot | Material | Interaction / owner | Enabled by default |
|---|---:|---|---|---|
| navigator | 0 | COMPASS | right-click existing NavigatorMenu / LobbyExperience | yes |
| visibility | 4 | LIME_DYE; filtered GRAY_DYE | right-click persisted boolean master / Visibility | yes |
| settings | 8 | COMPARATOR | existing permission-gated SettingsMenu / Settings | yes |
| friends | 1 | PLAYER_HEAD, self | existing permission-gated FriendMenu / Friends | yes |

The operator's current live composition differs: navigator 0, visibility 1, friends 7, settings 8. Those are operator choices, not behavior contracts. Last correction changed stock literal names/lore and presentation/PM/reward templates to i18n references without changing geometry, spawn or custom clan color.

## Before: complete normal GUI audit

Source inventory search finds seven normal custom chest menus. Invsee is an additional technical inventory view with a real Minecraft inventory mapping, not a normal menu to redesign. Blackjack inventory ownership is Activity equipment/state rather than another root chest menu. Profile exists as `/profile` chat presentation, not a profile GUI.

All seven normal menus already have exact holder/inventory/owner/current-top/active bindings, local `DEFAULT_LAYOUT`, in-place event-driven updates, and no visible production Refresh. Legacy optional REFRESH actions in fixture-compatible layouts are not default controls. Five paginated menus already hide unavailable navigation and keep holder target mappings current.

| Menu | Size before | Normal visible controls, excluding content/empty display | Content / empty / duplication | Decision before implementation |
|---|---:|---|---|---|
| Settings | 45 | 6 feature icons + 6 status panes + Close = 13 | scoreboard, sounds, PM, friend requests, presence, visibility; no paged content | One item per feature; keep state in lore and preserve optional status API |
| VisibilitySettings | 54 | 5 icons + 5 status panes + Manage + Back + Close = 13 | master, friends, staff, added users, unavailable game participants; status duplicates | Compact single-item filters; keep unavailable game entry honest; mode replaces boolean label |
| VisiblePlayers | 54 | Add, Back, Close = 3; up to 3 usable pagination controls | explicit 45 content slots; UUID/name/online/remove lore; empty state | Keep explicit Add and UUID mapping; concise consistent gesture wording |
| Friends | 54 | Add, Friends, Incoming, Outgoing, Close = 5; conditional pagination | explicit 45 content slots; UUID mappings and per-view empty state | Keep social view choices and mutations, shorten/standardize hints |
| Clan | 54 | no clan: Overview/Invites/Create/Close = 4; member: up to 5 | overview identity/member/role displays; members/invites list, contextual owner action | Preserve contextual domain controls, omit technical UUID from ordinary overview lore |
| Navigator | 54 | Close = 1; conditional pagination | explicit 45 slots, warp ID mapping, empty state | Keep destinations only; no universal menu expansion |
| DailyQuest | 54 | Close = 1; conditional pagination | feature-local adaptive 1–5 quest arrangement; read-only progress/reward status; empty state | Preserve adaptive layout and all commit-driven updates |

Counts describe this baseline's defaults, not permanent geometry contracts. Runtime control availability remains authoritative.

## Before: messages and localization audit

`MessageService.send` prepends `ConfigService.messages.prefix` for every normal send; `sendRaw` preserves complete PM/join wrappers. Default config and Java fallback currently insert `[VapeeCore]`. `LocalizationService` resolves each actual recipient, safely inserts component arguments and retains operator overrides. Both bundled catalogs have 901 keys at the inspected baseline. Existing literal operator templates remain literal by design; upgrading an installation needs explicit field changes as documented in FORMATTING.md.

| Class | Representative paths | Existing visual behavior | Intended consolidation |
|---|---|---|---|
| NORMAL | profile, rank, utility, lobby, social information | mixed white/gray/aqua/gold; global prefix | neutral body, accent only meaningful values; empty default prefix |
| SUCCESS | FriendMessages, ClanMessages, economy/reward, teleport | several entire green sentences, white dynamic names; rewards multicolor | neutral success sentence with accent value/state; retain green for useful state signals |
| WARNING | activity ownership guards, unavailable filters, ambiguity, usage | yellow/gold or red depending caller | restrained yellow; keep domain semantics |
| ERROR | permissions, persistence failure, invalid arguments, moderation | generally RED, some embedded red catalog tags | restrained red; safe dynamic arguments and rollback untouched |
| ACTIVITY | Activity/Blackjack sessions, round and result feedback | feature-specific wording and accents; no common core/activity distinction | explicit contextual Activity label where useful; neutral body, retain round/status cues |
| ADMIN/TECHNICAL | core reload/version/help, staff/diagnostics | operator-oriented details and severity | retain useful technical detail, no player-facing implementation-brand requirement |

Audit covers command adapters, notification adapters, both complete catalogs and config prefix, including quests, Friends, Clan, moderation, utility, Activity/Blackjack, lobby, economy and rewards. Colors in GUI specifications and technical hover/state indicators are not mechanically recolored as chat messages. No runtime reverse lookup of English display strings will be introduced.

## Implementation decisions (before production edits)

- Add explicit `player-menu` registration, own head, a 27-slot locally composed root GUI with supported existing Profile chat presentation, Visibility, Friends and Settings. Profile delegates to the existing permission-checked `/profile` path, not a new backend.
- Keep `friends` and `settings` registered but disabled/omitted from default explicit hotbar composition; normal default is Player Menu, Visibility, Navigator. Preserve live config choices unless deliberately adopting the new experience with a backup.
- Add a small persisted visibility enum, ALL/FILTERED/HIDDEN. Legacy boolean true maps ALL, false FILTERED. Advanced flags and added UUIDs remain independent. Unknown mode falls back to HIDDEN with a diagnostic; loading does not rewrite files. Service saves restore the exact previous mode on failure.
- Left-click Visibility cycles mode; right-click opens the existing advanced menu. Dynamic presentation remains registered behavior. Targeted quick interaction must use owned PDC, registry activation, current eligibility, target UUID and existing settings commit; a safer main-hand entity interaction is preferred over cancelled attack-event coupling.
- No framework, polling, generic event bus, translated identity routing or changes to Friend/Clan transactions.

## Validation and results

All existing harness entry points are retained. Initial focused runs deliberately exposed old assumptions about four enabled hotbar items, two-state toggles, cosmetic wording/caption colors and mandatory status slots. Tests now exercise the changed behavior, retain optional status/relocation coverage, and add direct production single-item lore checks. The generic test World proxy now uses ordinary Object identity/equality, enabling genuine same-world selection tests rather than weakening the production world gate.

Five new executable harnesses: `VisibilityModeHarness`, `PlayerMenuHarness`, `PlayerSelectionHarness`, `CoreLobbyCompositionHarness`, `MessageStyleHarness`. They cover real file load/save, exact rollback, unknown/null modes, loaded profiles, both locales, native event routing, unknown IDs, PDC/self-head, root ownership/active mappings, hidden actions, drag/click cancellation, permission loss, stale/forged inventories, close/quit/world/disable cleanup, actual message delivery and literal dynamic values.

Five negative controls were actually applied, compiled, executed and restored, including compiled classes. Every case produced the expected assertion failure and passed before/after restoration:

| Control | Temporary production mutation | Detecting behavior test |
|---|---|---|
| A | Always prepend `[VapeeCore]` regardless of config | `MessageStyleHarness`: default success has no plugin prefix |
| B | Remove the real BlackjackService context wrapper | `BlackjackHarness`: actual activity feedback retains contextual label |
| C | Deserialize dynamic Friend target name as MiniMessage | `MessageStyleHarness`: literal `<red>Steve` and accent must survive |
| D | Erase Friends/added UUID preferences on mode change | `VisibilityModeHarness`: saved preferences survive mode roundtrip |
| E | Re-enable omitted optional registrations in parser fallback | `CoreLobbyCompositionHarness`: exactly three configured primary items |

Receipt: local `negative-controls.json` plus each failing log. Automated results, the client correction rounds and final acceptance are recorded below.

## After: player experience and implementation

| Surface | Result |
|---|---|
| Default hotbar | Player Menu (own head), Visibility, Navigator; current example slots 0/1/8, always config-driven |
| Registered optional shortcuts | Friends and Settings retained, off without explicit selection; existing configured IDs remain usable even if their legacy entry omits `enabled` |
| Player Menu | 27-slot menu; advanced Visibility, Friends, Settings, Close; chat-only Profile entry removed following client feedback |
| Settings | Current default has five general feature/status pairs and Close; duplicate Visibility entry omitted, state rerenders immediately |
| Visibility Settings | Compact default puts mode and filters in one feature row with status panes below; Manage Players at bottom right, Back to Player Menu, Close; unavailable game integration remains visibly unavailable |
| Visible Players | Existing explicit Add, UUID list, online/offline and removal; consistent click hints; conditional pagination/automatic updates retained |
| Friends | Existing Friends/Incoming/Outgoing/Add/Close are all purposeful, retained; consistent gesture hints; committed updates and UUID mappings unchanged |
| Clan | Existing contextual member/owner/invite actions retained; technical clan UUID removed from ordinary role lore; separate overview fields show distinct identity/capacity/role facts |
| Navigator | Existing destinations-only menu, unchanged page/content authority and warp IDs |
| DailyQuest | Existing feature-local adaptive 1–5 placements, conditional pagination and committed updates unchanged; no Reset Clock added |

Player Menu is owned by LobbyExperience, adding no module, root command or permission. The initial chat-only Profile destination was removed after the operator’s actual client test. `/profile` remains unchanged as an independent command. The three remaining destinations call existing menu open gates. `DEFAULT_LAYOUT` chooses root composition; runtime rendered actions, current permissions, profile and lobby ownership must all agree before dispatch. Rendering clears actionable controls before rebuilding, so a failed partial render cannot leave an old hidden action live.

Selection uses main-hand right-click on a player rather than attacks. It cancels its recognized interaction but never uncancels another handler or handles damage. It requires active registry, enabled `player-menu`, exact managed PDC ID, current Settings permission, eligible owner/target, same world, online loaded profiles and different UUIDs. Added Players remains the sole UUID authority. Save failure restores the selection and emits no visibility success. Changing selection does not silently enable its filter or change the quick mode.

`VisibilityMode` is plain domain data. ALL first honors Ignore, then shows eligible lobby players. FILTERED evaluates the stored positive filters. HIDDEN honors self visibility but hides other eligible players even when all advanced flags are true. Legacy true→ALL, false→FILTERED; no stored value→existing ALL default. Unknown, wrong-typed, lowercase or explicit null mode→HIDDEN plus warning. Raw YAML presence is retained for null validation. Loading performs no rewrite. Next ordinary save writes the additive owned `settings.visibility.mode` field alongside the compatibility boolean projection in the existing schema 1, preserving unknown siblings. Service failures restore the exact enum, including HIDDEN through an old boolean setter. No saved filter or added UUID is changed by cycling.

Dynamic item `variants` are generic presentation-only definitions. Visibility registers its hidden variant; the parser does not branch on its ID. Click support is similarly action-owned through `LobbyItemAction.supports`: ordinary actions retain right-click behavior while Visibility opts into left-click cycling and right-click advanced settings. Deferred block clicks recheck current ownership/runtime/config eligibility.

Message delivery keeps configured prefixes and operator templates intact; new default/fallback prefix is empty. `MessageStyle` is a small semantic palette, not a theme/template framework. Core success adapters use neutral bodies and Aqua literal values; warning/error/state colors retain meaning. Default online-reward text uses the same restrained value emphasis. Actual Blackjack gameplay feedback uses a localized contextual wrapper, while administration/setup stays technical. Only selected audited text groups/gesture hints were changed, not a blanket recoloring of the full catalogs.

Security invariants retained: exact holders/inventories/owners/active top views; actual rendered actions; UUID/warp/quest mappings; registry/PDC identity; destination authorization; NORMAL/BUILD/Activity boundaries; Friend/Clan copy-on-write and persistence rollback; Ignore privacy; literal user values; i18n parity/fallback and safe MiniMessage arguments; rank/server-name formatting; conditional pagination, page clamping and automatic refresh. No translated display field is used as identity.

## Lobby Glide Follow-up Plan

Audit only in this phase. Current `LobbyPlayerStateService` owns NORMAL/BUILD inventory, including armor clearing; Activity/Blackjack owns separate inventory state, and `UtilityService` owns `/fly` via a managed UUID set and clears allowFlight on transitions. There is no existing Glide controller or PlayerToggleFlightEvent path. Utility/Activity already have quit/world/disable cleanup; another unconditional flight reset would conflict with their ownership.

Paper 1.21.11 supports `LivingEntity.setGliding`, including without Elytra, but documents that an unequipped glide is immediately reverted unless the relevant event is handled. This is an API capability, not evidence of a satisfactory client experience. Source: [Paper 1.21.11 LivingEntity#setGliding](https://javadocs.papermc.io/paper/1.21.11/org/bukkit/entity/LivingEntity.html#setGliding(boolean)).

Recommended follow-up architecture:

1. First prototype **armor-free** glide on the target Paper/client pair. A narrow lobby Glide controller may observe `PlayerToggleFlightEvent` for its deliberate double-jump and own `EntityToggleGlideEvent` handling only while its current eligible glide is active. Never suppress normal landing/exit transitions globally. No Elytra/chest-slot mutation means no real armor can be lost and no temporary item can duplicate. Do not ship solely because a server flag is true; require a real client flight/landing test.
2. Eligibility must use the current loaded/online/lobby/NORMAL/no-Activity boundary. Require no existing `/fly` or foreign flight ownership. Add a narrow read-only Utility ownership query and an explicit handoff hook before `/fly` changes; do not infer authority solely from `allowFlight`.
3. Each activation owns a generation token and the prior flight/gliding state. Release synchronously before BUILD, Activity inventory takeover, world leave, respawn/death, quit and disable. Restore only properties still owned by that token; when Utility or another state takes ownership, hand off instead of overwriting its new state. This prevents an allowFlight leak or a stale cleanup disabling `/fly`.
4. Integrate release at existing state transition boundaries before they clear inventory/armor. Do not introduce a competing inventory snapshot, equip items, or poll to continually undo other owners. An invalid ownership transition terminates glide first; it never continues outside NORMAL.
5. If armor-free client behavior is inadequate, stop and design a chest-slot escrow transaction before implementing temporary Elytra: clone exact original stack/meta/PDC, unique temporary nonce and owner generation, block move/drop/swap/transfer of the temporary stack, release before all ownership boundaries, remove only the matching temporary item, restore the exact original without overwriting a foreign replacement, and retain a recoverable escrow when no safe storage exists. Require crash/reconnect recovery and failure/duplication tests. An untracked `setChestplate(ELYTRA)` is unacceptable. This more invasive alternative is not implemented or approved as production-ready by this report.

Required follow-up acceptance: real armor bytes unchanged, zero temporary items after all exits, no duplicated/dropped escrow, no flight outside NORMAL, `/fly` handoff correctness, Activity/BUILD priority and quit/world/disable cleanup. No Glide events, scheduler, launch or equipment code were added in this phase.

## Capability inventory

| Category | Existing source-owned capabilities |
|---|---|
| FOUNDATION | module lifecycle, safe configuration/evolution/reload, SafeFileWriter and repositories, localization/message delivery, identity/profile lookup, LuckPerms permission/rank integration |
| CORE PLAYER EXPERIENCE | Lobby NORMAL/BUILD, navigation/warps, presentation, visibility/settings, Friends/social/Clan, coins/rewards/quests, moderation, utility, presence and seats |
| SERVER-SPECIFIC EXPERIENCE | Activity/Blackjack tables and round presentation; future Glide, Chat Range, Auto-Join, queue and additional games remain deferred |

This is product classification only; no package/module split.

## Initial measured validation and deployment (before client corrections)

| Verification | Actual result |
|---|---|
| Baseline before production edits | 168/168 harnesses, 26,687 checks, 0 failures, 0 missing/invalid |
| Focused runs | GUI, lobby registry, settings, persistence, messages, localization and Blackjack tests passed after correcting old-default assumptions; individual run receipts retained locally |
| Full canonical regression after negative controls | 173/173 harnesses, 26,934 checks, 0 failures, 0 missing/invalid |
| Supported offline build and deploy | Maven `clean package` succeeded; deployment reran all 173 harnesses / 26,934 checks with 0 failures and 0 missing/invalid |
| New harness entry points | Five; all 168 baseline entry points retained |
| Bundled localization parity | 925 English and 925 German keys |
| Native Paper smoke | Paper 1.21.11-132, Java 21; 27 probe checks passed, clean reload and shutdown, 28 modules, seven reload participants |
| Final smoke errors | 0 VapeeCore/probe ERROR or SEVERE in the successful smoke run |
| Real client acceptance | Operator actually tested and reported **deviations found**; details requested, acceptance remains open |

Build receipts live under ignored `dev-server/.vapeecore-dev/core-experience-consolidation/`: `baseline-results.json`, `focused-first.json`, `focused-second.json`, `focused-third.json`, `full-results.json`, `deploy-results.json`, `build-and-deploy.log`, `negative-controls.json` and `paper-receipt.json`. The built and deployed JARs matched SHA-256 `7F8EAE0AECA875AAC3F607F499E15A11DCD641110B34D880BBF6BDDFF65A9871`.

The native probe used real Paper inventories, metadata, PDC, self-head ownership, registered menu/event paths and file persistence, with virtual Player adapters. It checked the three-item live hotbar, root destinations, DE rendering, single-item Settings composition, all three visibility modes, filter preservation and UUID selection. It is not a real network client test. DailyQuest assignments are disabled in this development configuration; its adaptive content was covered by harnesses, not claimed as a native client check.

The first diagnostic smoke exposed a limitation of the virtual Player adapter: Paper's vanilla command bridge cannot convert that proxy into a real CraftPlayer command source. The corrected probe asserts the fixed `profile` argument and calls the actual PluginCommand executor and authorization. No production command path was changed to accommodate the adapter. The first diagnostic log remains preserved as `paper-smoke-first-virtual-player-limit.log`; the successful second run is `paper-smoke.log`. Temporary probe JARs, remapped copies and temporary player files were removed after the smoke.

For this local acceptance test, the operator's development configuration deliberately adopted the new three-item composition after an exact-byte backup (`live-backup-20261015-185551`). Only the lobby `items` subtree, the known stock message prefix and exact matching stock translation values were updated. Unrelated lobby/config fields, custom translations and other data files were preserved. A separate live-adoption verifier passed 24 checks. Across the subsequent Paper startup/reload/shutdown, all ten checked operator/data YAML files and both existing language files retained their bytes. Production startup does not silently adopt this new composition or overwrite custom values.

The real client test subsequently started the ordinary server with only LuckPerms and VapeeCore. The inspected startup/join log contains no VapeeCore exception. This does not resolve the reported visual or interaction deviations. The final phase commit is withheld until those observations are identified and corrected; nothing has been pushed or merged.

## Actual client feedback and correction

The operator's actual client test rejected five details of the initial implementation. These explicit follow-up preferences override the initial single-item/no-status-pane design target:

| Observed concern / requested behavior | Correction |
|---|---|
| Right-click Visibility should cycle, not open settings | Both ordinary click gestures cycle the same saved ALL → FILTERED → HIDDEN state; lore advertises Right Click. Advanced settings remain in Player Menu. Cooldown, deferred block-click eligibility, persistence and rollback remain intact. |
| Restore visible glass status indicators in toggle menus | Settings and Visibility default layouts restore feature/status pairs. Green/red show boolean state; green/yellow/red show ALL/FILTERED/HIDDEN. Both icon and pane trigger the same existing action, and committed changes rerender the same inventory immediately. Icon-only layouts remain supported. |
| Profile head only prints chat information and is unnecessary | Remove the Profile action and its routing from Player Menu; three real menu destinations remain. Existing `/profile` command is unchanged. |
| Visibility Back unexpectedly opens Player Settings | Experience supplies a narrow parent callback to Player Menu. Exact current menu/owner/permission checks still apply. Unavailable parent closes safely; callback resets on failure/disable, retaining no stale Player Menu reference. VisiblePlayers Back still returns to Visibility. |
| Hidden state switches from dyes to unrelated Redstone | All three defaults use dyes: Lime Dye, Gray Dye, Red Dye. Registry fallback and live YAML agree; operator-configurable materials remain supported. |

Focused correction validation: **17 harnesses, 5,258 checks, 0 failures** (`client-focused.json`). Tests exercise restored status panes and immediate updates, all mode colors, right-click persistence, experience-supplied Back, stale navigation, root composition, localization and security. The first focused run correctly rejected the old test expectation that status panes must be absent; it was updated to the operator's new requirement while preserving automatic-update assertions.

The live correction touches only the visibility presentation subtree and exact matching stock mode/Back translation lines. An exact-byte backup and hashes for all observed operator/data/language files are in `client-live-adoption.json` and its referenced `client-backup-*` directory. Spawn, protection, unrelated items, custom templates and player data are not rewritten by this adoption.

The first deployment attempt stopped Paper cleanly but the existing wrapper treated the stop helper's unset/stale native exit status as failure. A subsequent attempt built successfully but canonical harness discovery rejected the ambient Java path because `javap` was unavailable there. Both diagnostic logs are preserved. The validated run selects the installed Java 21 JDK explicitly; production code was not changed for either tooling issue.

First correction automated validation completed before the next client feedback:

| Check | Result after client corrections |
|---|---|
| Focused regression | 17 harnesses, 5,258 checks, 0 failures |
| Full canonical regression in supported build/deploy | 173 discovered/executed/passed, 26,968 checks, 0 failures, 0 missing/invalid |
| Build | Offline Maven clean package succeeded; corrected JAR deployed |
| Native Paper probe | 33 checks passed, including actual Visibility Back → PlayerMenuHolder, six Settings panes, five Visibility panes, no profile head, every mode a dye, right-click cycles without opening an inventory |
| Startup/reload/shutdown | Clean; 28 modules and seven reload participants; 0 ERROR/SEVERE in the correction smoke log |
| Operator data stability through native smoke | Ten operator/data YAML files and both language files byte-identical; no new language files |
| Bundled catalogs | 922 EN keys and 922 DE keys; obsolete new Profile/hotbar-settings-hint keys removed |
| Real client | Initial actual test supplied the five concerns above. The renewed actual test supplied the three further concerns documented below; acceptance remains open. |

Final correction JAR SHA-256: `BD4A25429F0F9D647C61BA8005D38341F12879728CCF132CC69B0AD7FD3F4207` (target and deployed copy match, 1,275,156 bytes). Receipts: `client-focused.json`, `client-full-results.json`, `client-build-deploy.log`, `client-paper-smoke.log`, `client-paper-receipt.json`. The initial native smoke receipts were archived as `initial-paper-*` before the correction run. Temporary probe JAR/cache copies and temporary player files were removed; only the two original real-player files remain. The development server is left stopped for the operator to start through the supported launcher. No completion commit, push or merge has occurred; renewed real-client acceptance remains the final gate.

## Second client feedback: compact layout and exclusive right click

The renewed actual client test supplied three further concrete preferences. The Player Settings default now omits its Visibility icon/status pair, leaving Visibility in Player Menu; the optional supported Action/domain destination remains available for explicit Java composition overrides, with no invisible default click target. The remaining five settings are centered as one group. Visibility Settings is reduced from six to four chest rows: mode and all four filters share one row, status panes sit directly below, and Manage Players is the lower-right chest. Geometry remains exclusively menu-local in `DEFAULT_LAYOUT`.

The Visibility hotbar action now supports **only RIGHT_CLICK_AIR / RIGHT_CLICK_BLOCK**. Left-click is rejected by both registration support and the action itself in ALL, FILTERED and HIDDEN; tests verify no state change, persistence, feedback, cooldown or deferred task. Right-click keeps the persisted mode cycle, deferred block-click ownership validation and cooldown. This changes the hotbar gesture; ordinary in-menu settings clicks retain their established behavior.

The compact-layout test exposed one stale negative-test assumption that the last slot was empty; that slot deliberately contains the chest now. The test now selects an actually unplaced slot from the owning layout and continues to verify that it is inert. Additional tests assert a common feature row and the requested lower-right management position without copying a slot table into listeners or tests.

No operator YAML changes are required in this round because the live lore already describes Right Click and all three materials are dyes.

| Final automated check after second client correction | Actual result |
|---|---|
| Focused correction regression | 17 harnesses, 5,276 checks, 0 failures |
| Full canonical regression in supported build/deploy | 173 discovered/executed/passed, 26,986 checks, 0 failures, 0 missing/invalid |
| Baseline entry-point retention | All 168 retained; five new entry points, none removed |
| Offline clean package and local deployment | Succeeded; target and deployed JAR match |
| Native Paper probe | 46 checks passed, including five Settings status panes with no Visibility shortcut, all mode/filter features in one row, management chest in the lower-right corner, left-click inert in all three modes, existing Back/security/i18n/persistence paths |
| Native environment | Paper 1.21.11-132, Java 21; 28 modules, 37 declared command roots, 50 declared permissions, seven reload participants |
| Native lifecycle/data | Clean startup/reload/shutdown, 0 ERROR/SEVERE; ten operator/data YAML files and both existing language files byte-identical. Probe artifacts and temporary player files removed; server left stopped. |
| Real client | On 2026-10-15 the operator explicitly confirmed “Ja, diese Anpassungen passen” after the requested real-client retest: no Settings Visibility entry, compact shared feature row/status panes/lower-right chest, and right-click-only hotbar cycling. |

JAR SHA-256: `1C689626D9C05088D069C92F06962FEB95E8AB0E674DDC62B609847B88D33024`, size 1,275,112 bytes. Latest receipts are `client2-focused.json`, `client2-build-deploy.log`, `client2-full-results.json`, `client2-paper-smoke.log` and `client2-paper-receipt.json`; first-correction native/full receipts were preserved under `client1-*`.

## Final client acceptance and phase completion

The operator accepted the latest requested adjustments in an actual Minecraft client on 2026-10-15. The acceptance covers the concrete retest described above, following the two earlier rounds of real observations and corrections; it is not a claim that every hypothetical multi-client/domain scenario was manually tested. DailyQuest content was disabled in this development configuration and its adaptive content remains harness-verified. Native probes are separately identified as virtual-player tests.

The operator restarted the development server for this acceptance; the final inspected ordinary startup/join log has no VapeeCore exception. The running acceptance server is preserved. No production or test code changed after the passing final build/regression/native smoke; final edits record documentation and the user-confirmed result only. This report and its exact file inventory are finalized for the single local phase commit `Consolidate core player experience` on `phase/core-experience-consolidation`. The actual full commit SHA is returned in the task response. No push, merge, Phase 38 or deferred feature implementation is included.

## Exact phase file inventory

Current phase changes include the two preserved pre-existing documentation edits. Ignored runtime data, local receipts and temporary probe artifacts are excluded.

- `docs/DEVELOPER_GUIDE.md`
- `docs/FORMATTING.md`
- `reports/core-experience-consolidation/CORE_EXPERIENCE_CONSOLIDATION.md`
- `src/main/java/dev/vapee/core/activity/blackjack/BlackjackService.java`
- `src/main/java/dev/vapee/core/clan/ClanMessages.java`
- `src/main/java/dev/vapee/core/clan/gui/ClanMenu.java`
- `src/main/java/dev/vapee/core/config/ConfigService.java`
- `src/main/java/dev/vapee/core/economy/command/CoinsCommand.java`
- `src/main/java/dev/vapee/core/friend/FriendMessages.java`
- `src/main/java/dev/vapee/core/friend/gui/FriendsLobbyItemAction.java`
- `src/main/java/dev/vapee/core/lobby/config/LobbyItemsConfig.java`
- `src/main/java/dev/vapee/core/lobby/experience/LobbyExperienceModule.java`
- `src/main/java/dev/vapee/core/lobby/experience/LobbyItemListener.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorLobbyItemAction.java`
- `src/main/java/dev/vapee/core/lobby/experience/player/PlayerMenu.java`
- `src/main/java/dev/vapee/core/lobby/experience/player/PlayerMenuHolder.java`
- `src/main/java/dev/vapee/core/lobby/experience/player/PlayerMenuListener.java`
- `src/main/java/dev/vapee/core/lobby/experience/player/PlayerSelectionListener.java`
- `src/main/java/dev/vapee/core/lobby/item/LobbyItemAction.java`
- `src/main/java/dev/vapee/core/lobby/item/LobbyItemDefinition.java`
- `src/main/java/dev/vapee/core/lobby/item/LobbyItemRegistrations.java`
- `src/main/java/dev/vapee/core/lobby/item/LobbyItemRegistry.java`
- `src/main/java/dev/vapee/core/lobby/warp/command/WarpCommand.java`
- `src/main/java/dev/vapee/core/message/MessageService.java`
- `src/main/java/dev/vapee/core/message/MessageStyle.java`
- `src/main/java/dev/vapee/core/player/repository/FilePlayerRepository.java`
- `src/main/java/dev/vapee/core/player/repository/PlayerFileSchema.java`
- `src/main/java/dev/vapee/core/player/settings/PlayerSettingsService.java`
- `src/main/java/dev/vapee/core/player/settings/PlayerVisibilitySettings.java`
- `src/main/java/dev/vapee/core/player/settings/VisibilityMode.java`
- `src/main/java/dev/vapee/core/settings/SettingsLobbyItemAction.java`
- `src/main/java/dev/vapee/core/settings/SettingsMenu.java`
- `src/main/java/dev/vapee/core/settings/SettingsMenuEntries.java`
- `src/main/java/dev/vapee/core/settings/SettingsModule.java`
- `src/main/java/dev/vapee/core/settings/visibility/VisibilitySettingsListener.java`
- `src/main/java/dev/vapee/core/settings/visibility/VisibilitySettingsMenu.java`
- `src/main/java/dev/vapee/core/utility/command/ClearCommand.java`
- `src/main/java/dev/vapee/core/utility/command/EnderChestCommand.java`
- `src/main/java/dev/vapee/core/utility/command/FeedCommand.java`
- `src/main/java/dev/vapee/core/utility/command/FlyCommand.java`
- `src/main/java/dev/vapee/core/utility/command/GameModeCommand.java`
- `src/main/java/dev/vapee/core/utility/command/HealCommand.java`
- `src/main/java/dev/vapee/core/utility/command/InvseeCommand.java`
- `src/main/java/dev/vapee/core/utility/command/PingCommand.java`
- `src/main/java/dev/vapee/core/utility/command/SpeedCommand.java`
- `src/main/java/dev/vapee/core/utility/command/TeleportCommand.java`
- `src/main/java/dev/vapee/core/utility/command/TeleportHereCommand.java`
- `src/main/java/dev/vapee/core/visibility/VisibilityLobbyItemAction.java`
- `src/main/java/dev/vapee/core/visibility/VisibilityPolicy.java`
- `src/main/resources/config.yml`
- `src/main/resources/lang/de.yml`
- `src/main/resources/lang/en.yml`
- `src/main/resources/lobby.yml`
- `src/test/java/dev/vapee/core/activity/blackjack/BlackjackHarness.java`
- `src/test/java/dev/vapee/core/clan/gui/ClanCommandHarness.java`
- `src/test/java/dev/vapee/core/clan/gui/ClanMenuHarness.java`
- `src/test/java/dev/vapee/core/config/LobbyConfigUpgradeHarness.java`
- `src/test/java/dev/vapee/core/friend/gui/FriendMenuHarness.java`
- `src/test/java/dev/vapee/core/friend/gui/FriendsLobbyItemHarness.java`
- `src/test/java/dev/vapee/core/i18n/CatalogUsageHarness.java`
- `src/test/java/dev/vapee/core/lobby/experience/player/PlayerMenuHarness.java`
- `src/test/java/dev/vapee/core/lobby/item/CoreLobbyCompositionHarness.java`
- `src/test/java/dev/vapee/core/lobby/item/LobbyItemRegistryFixture.java`
- `src/test/java/dev/vapee/core/lobby/item/LobbyItemsConfigHarness.java`
- `src/test/java/dev/vapee/core/lobby/item/PlayerSelectionHarness.java`
- `src/test/java/dev/vapee/core/message/MessageStyleHarness.java`
- `src/test/java/dev/vapee/core/player/repository/VisibilityModeHarness.java`
- `src/test/java/dev/vapee/core/settings/SettingsCompositionHarness.java`
- `src/test/java/dev/vapee/core/settings/SettingsHotbarSecurityHarness.java`
- `src/test/java/dev/vapee/core/settings/visibility/SettingsNavigationHarness.java`
- `src/test/java/dev/vapee/core/settings/visibility/VisibilityAutomaticUpdateHarness.java`
- `src/test/java/dev/vapee/core/settings/visibility/VisibilityCompositionHarness.java`
- `src/test/java/dev/vapee/core/settings/visibility/VisibilityLayoutHarness.java`
- `src/test/java/dev/vapee/core/settings/visibility/VisibilitySettingsMenuHarness.java`
- `src/test/java/dev/vapee/core/settings/visibility/VisiblePlayersMenuHarness.java`
- `src/test/java/dev/vapee/core/visibility/VisibilityLobbyItemActionHarness.java`
- `src/test/java/dev/vapee/core/visibility/VisibilityModuleHarness.java`
