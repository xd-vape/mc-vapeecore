# Phase 30C — GUI Inventory and Item Cleanup

## Baseline and scope

Repository: https://github.com/xd-vape/mc-vapeecore (verified origin; local checkout is authoritative).
Branch: `phase/30c-gui-inventory-item-cleanup`.
Baseline: `c69b9993721564e9138ec1adbdde84381d82680f`,
`Merge Phase 30B - Architecture Refactor`.
Branch, full SHA, subject and clean working tree were verified before edits.
458 tracked baseline files were hashed before implementation.
One worker performed the audit, implementation and validation; no subagents were used.

This phase resolves only GUI-001 and GUI-002. MOD-001 and API-001 remain resolved.
SEC-001 remains open. No subsequent phase work is included.
The delivery is one local commit named `Phase 30C - GUI Inventory and Item Cleanup`;
no push, reset, rebase, merge or history rewrite is part of this work.
The final commit SHA and post-commit clean-tree/count checks are supplied in the delivery message.

## Audited inventory surfaces

The eight custom inventory surfaces are the seven menus below plus Invsee.
Their holder/listener boundaries, rendering callers, pagination and cleanup owners were inspected.
Blackjack presentation, lobby managed items and native Enderchest were also checked as explicit exclusions.

| Surface | Layout / identity | Phase 30C treatment |
|---|---|---|
| FriendMenu | 54 slots, 45 content; FriendMenuHolder, view and slot→UUID targets | Shared literal items and pagination; isolated close |
| ClanMenu | 54/45; ClanMenuHolder, clan snapshot ID, view and slot→UUID targets | Shared literal items and pagination; isolated close |
| SettingsMenu | 54, no pagination; SettingsInventoryHolder | Shared spec/text/render; isolated close |
| VisibilitySettingsMenu | 54, no pagination; VisibilitySettingsHolder | Shared spec/text/render; isolated close |
| VisiblePlayersMenu | 54/45; VisiblePlayersHolder and slot→UUID targets | Shared literal items and pagination; isolated close |
| NavigatorMenu | 54/45; NavigatorInventoryHolder and slot→warp-ID targets | Shared literal items and pagination; existing cleanup retained |
| DailyQuestMenu | 54/45; DailyQuestInventoryHolder and page | Shared literal items and pagination; existing cleanup retained |
| InvseeService / InvseeInventoryHolder | Read-only cloned inventory snapshot, viewer/target UUIDs and exact inventory | Entire utility production surface unchanged |
| BlackjackInventoryService and presentation package | State-driven action hotbar, PDC markers, inventory transfer and ownership | Entire Blackjack production and test surface unchanged |
| Native Enderchest | Bukkit native player inventory view | Entire utility production surface unchanged |
| LobbyItemService / LobbyItemType | Reserved slots 0/4/8, managed-item PDC and safe displacement | Entire lobby-item production surface unchanged |

The historical audit's GUI matrix, GUI-001/GUI-002 evidence, duplication map and
extraction constraints informed the boundary. Relevant module enable, partial-enable cleanup,
disable and reload callers were reviewed: FriendModule, ClanModule, SettingsModule,
LobbyExperienceModule and DailyQuestModule. No module implementation was changed.

## GUI-001: root cause and smallest shared implementation

Seven menus duplicated the same new ItemStack / getItemMeta / displayName / lore /
setItemMeta sequence, literal nonitalic text helpers and local ItemSpec records.
Five menus duplicated page count, clamp and content range arithmetic. Sorting,
target authority, domain freshness and gestures were intentionally different.

Only three production classes were added in `dev.vapee.core.ui`:

| Class | Contract |
|---|---|
| UiItemSpec | Immutable record containing only Material, Component name and List<Component> lore |
| UiItems | Stateless literal text, gray literal-lore convenience, and ordinary name/lore ItemStack rendering |
| Pagination | Immutable page/pageCount/fromIndex/toIndex with pure arithmetic and previous/next predicates |

There is no module, lifecycle registration, listener, registry, global mutable state,
scheduler, task, asynchronous pipeline, reflection registration, base menu, DSL,
router, dispatcher or third-party UI dependency in this package.

### Item and renderer contract

UiItemSpec rejects null material, name, lore and null lore elements.
List.copyOf takes an immutable snapshot; later mutations of caller-owned lists cannot affect it.
Component.empty() lore separators remain in their original position and are not filtered,
restyled or replaced. The spec contains no slot, permission, owner UUID, action,
holder, page, inventory reference, callbacks or domain state.

UiItems.text uses Component.text(value, color).decoration(ITALIC, false).
Player, clan, warp and quest strings remain literal; markup is never parsed.
UiItems.literal preserves input lore order, producing literal gray lines for the
menus that previously used exactly that convention. Mixed-color lore still
uses explicit feature-local Components.

UiItems.render creates the requested material, reads its metadata, sets only
display name and lore, reapplies metadata and returns the item. It adds no PDC,
enchantment/glow, skull owner, custom model data, action marker or domain metadata.
The implementation preserves the former production rendering sequence.

Every menu keeps its own injectable InventoryFactory and ItemRenderer.
The renderer now receives UiItemSpec; production constructors use UiItems::render.
Existing spec-capture fixtures were updated only to the shared type and, where
required, logger injection. No Paper registry bootstrap was introduced into these
fixtures. No visible expected output was changed to accommodate the helper.

The isolated UiItemsHarness exercises pure spec/text behavior (17 checks).
Existing menu harnesses continue to verify the rendered spec's material, text,
colors, lore, controls and target behavior through the established injected seam.
Direct real ItemStack metadata rendering is not executed by that standalone
harness: it requires the Paper runtime. The production assignment sequence was
reviewed and the packaged plugin is covered by server smoke; this does not claim
that a client opened or visually inspected a menu.

All seven replaced local ItemSpec records and default render/text methods were removed.
Replaced local spec factories and pageCount methods were removed as well.
Feature-local item/layout methods remain because they supply each menu's own
colors, status and renderer seam.

### Pagination contract and overflow proof

Pagination.of(itemCount, requestedPage, contentSize) requires itemCount >= 0
and contentSize > 0; requestedPage may be any int. It returns a clamped page,
page count, a half-open [fromIndex,toIndex) range, hasPrevious() and hasNext().

- Empty input returns page 0, pageCount 1 and [0,0), with neither navigation flag.
- Negative requested pages clamp to zero; excessive values including Integer.MAX_VALUE
  clamp to the last page.
- Page count is itemCount == 0 ? 1 : 1 + (itemCount - 1) / contentSize.
  It never forms itemCount + contentSize - 1 in int arithmetic.
- For nonempty input, clamping gives page <= floor((itemCount - 1) / contentSize).
  Therefore page * contentSize <= itemCount - 1 <= Integer.MAX_VALUE - 1.
  For empty input the product is zero.
- toIndex is fromIndex + min(contentSize, itemCount - fromIndex), so the sum
  cannot exceed itemCount. It avoids overflow in fromIndex + contentSize.
- hasNext compares page < pageCount - 1, without adding to the requested page.
  Friend/Clan/VisiblePlayers retain their negative-page guard and read fresh
  candidate sizes before checking availability.

PaginationHarness checks 0, 1, 44, 45, 46, 89, 90, 91, MAX_VALUE-1 and MAX_VALUE
items, content sizes 1/2/45/MAX_VALUE-1/MAX_VALUE, and MIN_VALUE/-1/0/1/2/100/MAX_VALUE
requested pages against an independent long-arithmetic oracle. It verifies bounds,
exact ranges, navigation and invalid input rejection: 1,409 checks.

Only Friend, Clan, VisiblePlayers, Navigator and DailyQuest consume pagination.
Settings and VisibilitySettings remain nonpaginated. Filtering, sorting, title
generation, target bindings, controls and click behavior remain feature-owned.

## Per-menu parity and authority

| Menu | Preserved presentation and actions | Preserved authority |
|---|---|---|
| Friend | Friends/incoming/outgoing tabs, empty states, names/status, 45-slot paging, refresh and add prompt; incoming left accept/right decline, outgoing cancel, shift-right friendship removal | Exact holder/binding, owner and active registry; current relationship/privacy/limits stay in FriendService |
| Clan | Overview/members/invites; owner-first members; name/tag/role/count; current invites; create/disband prompts; shift-right kick and shift-left transfer | Exact holder/binding, owner, active registry, snapshot clan-ID comparison, existing clan permission and fresh service role checks |
| Settings | Scoreboard, Sounds, Private Messages, Friend Requests, Friend Presence and Visibility; existing status panes, mixed-color lore and empty separators; refresh in place | Exact holder/binding, owner, active registry and actual current top; loaded preferences and persistence rollback stay local |
| VisibilitySettings | Master ON/FILTERED, friends/staff/added filters, game-participant UNAVAILABLE state, manage/back/close/refresh and status panes | Exact holder/binding, owner, active registry and actual current top; preferences, visibility apply and hotbar refresh remain local |
| VisiblePlayers | Known-name-first deterministic sorting, UUID fallback, online green/offline gray, UUID lore, right-click removal, add prompt, paging and refresh | Server slot→UUID binding, exact holder/owner/active/current top and current added-user state |
| Navigator | Visible warps only, order then ID, configured literal names/icons, 45 content; previous 45, info 49, close 50, next 53 | Original slot warp-ID, live existence/visibility, loaded/online/lobby/NORMAL/not-participating checks, current top, cancellation and successful teleport behavior |
| DailyQuest | Daily sync, ACTIVE/COMPLETED/REWARD_PENDING presentation, automatic-reward notices, read-only entries; previous 45, info 49, close 50, refresh 52, next 53 | Exact holder/owner/active/current top, loaded profile, existing current permission and daily preparation; no reward/progress mutation by items |

Capitalization and punctuation remain exact, including Navigator's "Previous Page"
and Quest's "Previous page"; no text normalization occurred. Settings/Navigator titles
keep their former Component construction rather than acquiring a new decoration.
All other former literal helpers retain explicit nonitalic styling.

All seven holder and listener files remain byte-identical to the baseline.
Friend/Clan deliberately retain their event-top/bound-owner/active-map checks without
adding Settings/Navigator/Quest's actual-current-top requirement.
Visibility's existing actual-current-top checks remain unchanged too.
Navigator and Quest still publish active entries only after their existing successful,
actual-open validations; no open-publication order was centralized or changed.

Existing cancellation covers recognized inventories before authorization, forbidden
click types, bottom transfers, forged/unbound holders, foreign viewers, stale pages
and exact-instance close. Navigator's valid bottom-only drag behavior remains distinct
from the menus that cancel every drag. No item material/name/lore supplies authority.

SEC-001 is deliberately still open: no permission recheck or permission-revocation
behavior was added to Friend, Settings or Visibility. Existing Clan and Quest permission
checks and Navigator access policy are unchanged.

## GUI-002: close failure isolation

Previously a RuntimeException during lookup, isOnline, getOpenInventory,
getTopInventory or closeInventory aborted Friend, Clan, Settings,
VisibilitySettings and VisiblePlayers cleanup, leaving later entries unattempted
and skipping registry clear.

Each of those five methods now:

1. Traverses List.copyOf(active.entrySet()) so synchronous close-event removal
   cannot invalidate traversal.
2. Encloses each owner's lookup, online check, current-top read and close in its
   own RuntimeException catch.
3. Logs WARNING with the concrete menu type, owner UUID and original exception.
4. Continues to the remaining owners after ordinary lookup/read/close failures.
5. Closes only when the current top is exactly the tracked Inventory instance.
6. Clears its entire active map in an outer finally, including when logging itself throws.

Null/offline owners are skipped. If tracked A has been replaced by foreign B,
B stays open and A's registry entry is released. No scan or close of all online
players was added. The production plugin logger is injected; there is no global
logger or logging abstraction. The loops remain local to their menu lifecycle.
Five package-private activeCount probes allow direct registry-size assertions.

Navigator and DailyQuest already had the relevant isolation/finally pattern;
their cleanup implementations were not changed.

### Runtime fault proof

The test-only MenuCloseProbe constructs real feature menus through existing
factory/renderer seams. It dispatches the real corresponding listener's
InventoryCloseEvent during close, so reentrant map removal is exercised.

For each affected menu it injects each of five failure points (lookup,
isOnline, getOpenInventory, getTopInventory, closeInventory), with failure roles
and insertion order swapped. Assertions require both owners to be looked up,
healthy B to close, faulty A's injected exception to appear in exactly one
contextual WARNING, and activeCount == 0. No assertion relies on HashMap order.

Additional cases cover null lookup, offline player, foreign B replacing tracked A,
healthy close alongside skipped entries, repeat cleanup, and a throwing logger
whose failure still executes the final clear. There are 77 new checks per menu,
385 in total. The probe is test support, not a production controller or test-runner
replacement, and has no main method.

Module review: Friend/Clan disable already use finally for listener/command/reference
cleanup; Settings attempts each menu before its existing listener/command/reference
cleanup; Navigator has its module-local guarded cleanup. Clan partial enable and
DailyQuest partial enable/disable/reload call menu cleanup; the caught owner failures
no longer abort those callers. Friend/Settings partial-enable registration candidates
are unpublished and their rollback does not introduce user-opened views. Existing
lifecycle harnesses and the live reverse shutdown remain green. No broader module
rollback or logging-failure policy was introduced; LIFE-001 remains Phase 30D.

## Regression results

Baseline: 102 active harnesses / 15,788 checks / 0 failures.
Final full run: 104 active harnesses / 17,602 checks / 0 failures / 0 skipped.
All active *Harness.java public static void main entry points were discovered and
executed in separate Java processes. Maven package is not the harness runner.

Focused run: 46 harnesses / 9,494 checks / 0 failures.
It includes both primitive harnesses, all seven menu/security groups,
SettingsNavigationHarness, relevant lifecycle/integration harnesses, Invsee,
the Blackjack suite and ModerationCommandHarness.
The focused set was also run incrementally after each menu migration.

| Required coverage | Checks |
|---|---:|
| UiItemsHarness | 17 |
| PaginationHarness | 1409 |
| FriendMenuHarness / FriendMenuSecurityHarness | 159 / 114 |
| ClanMenuHarness / ClanMenuSecurityHarness | 84 / 114 |
| SettingsMenuHarness / SettingsMenuSecurityHarness | 66 / 111 |
| VisibilitySettingsMenuHarness | 29 |
| VisiblePlayersMenuHarness / VisibilityMenuSecurityHarness | 74 / 195 |
| SettingsNavigationHarness / SettingsModuleLifecycleHarness | 17 / 23 |
| NavigatorMenuHarness / NavigatorSecurityHarness | 113 / 82 |
| QuestMenuHarness / QuestMenuSecurityHarness | 20 / 45 |
| FriendLifecycleHarness / ClanIntegrationHarness / DailyQuestLifecycleHarness | 12 / 15 / 16 |
| UtilityInventoryHarness | 742 |
| ModerationCommandHarness | 4344 |
| BlackjackReadViewHarness | 21 |
| BlackjackHarness / BlackjackPresentationHarness | 317 / 123 |
| BlackjackFairnessHarness / BlackjackTableHarness / BlackjackPreviewHarness | 393 / 90 / 16 |

The +1,814 check delta is exactly +17 item +1,409 pagination +385 close faults
+3 PermissionDescriptorHarness source checks for the three new production files.
PermissionDescriptorHarness is 835 (formerly 832); permission semantics did not change.
No existing behavior expectation was removed or weakened.
MOD-001's fresh-authority regression remains green and its code/tests are unchanged.
API-001's deep immutable Blackjack read API remains closed to mutable state;
all Blackjack sources/tests, including the 687-line legacy comment block, are unchanged.

## Build, artifact and Paper smoke

The prescribed offline build completed with BUILD SUCCESS on 2026-10-04 at
20:30:59 Europe/Berlin, duration 12.633 seconds:

```powershell
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -o '-Dmaven.repo.local=C:\Users\mehdi\.m2\repository' clean package
```

| Artifact | Result |
|---|---|
| Build path | target/vapeecore-1.0-SNAPSHOT.jar |
| Size | 1,047,520 bytes |
| SHA-256 | F3C1F227EF6CBE19D7A5BCB92B118A1386C2DD3C0C5CB3947222FBD208A7D6B7 |
| Deployed path | dev-server/plugins/vapeecore-1.0-SNAPSHOT.jar |
| Deployed SHA-256 | F3C1F227EF6CBE19D7A5BCB92B118A1386C2DD3C0C5CB3947222FBD208A7D6B7 |

The existing dev-server ran Paper 1.21.11 build 132, revision c5eb079,
Oracle Java 21.0.12.1+1-LTS-4 and LuckPerms 5.5.84 on Windows.
Startup began at 20:31:41 and reached Done at 20:31:55 (14.590 seconds).
VapeeCore enabled all 27 modules and connected to LuckPerms.

Console checks at 20:32:40: version, lp info, list, core, core version,
core help, core reload and bukkit:help VapeeCore. Status reported Running,
27 active modules and zero loaded/online players. Help enumerated all 37 root
commands, matching the actual descriptor exactly. Reload prepared and applied
config.yml, lobby.yml, chat.yml, private-messages.yml, presentation.yml and
daily-quests.yml successfully in 16 ms.

Stop at 20:32:53 disabled all 27 modules in exact reverse startup order,
finished world saves and exited with code 0. The complete captured log contains
zero ERROR/SEVERE entries, including zero VapeeCore ERROR/SEVERE entries.
Paper's newer-Minecraft-release warning and spark's Windows Java-engine fallback
were informational environment output; no server/plugin dependency was changed.

All eight existing VapeeCore live YAML files have identical before/after SHA-256:

| File | Unchanged SHA-256 |
|---|---|
| blackjack.yml | 0231E32A0C675EDF0C35B8437473716B8784995C7C06737586C03A0F2A437726 |
| chat.yml | 765C15BF0EE5CEFF416773E182E49D5240587D869129C985B48B7105F293B18F |
| config.yml | D13C980946AFCBCE6B530F8EA2FE233B4FAB6A53F94E8C81B60F645250FBDA03 |
| daily-quests.yml | 29A646FAF708113F0B77B3A94759E1E6FFD8B8AB9522154242DABE12C20CE96E |
| lobby.yml | 58CB38F4C5276F2BA640C21A2D6AF44DF9891AF536D1647A19F5509B26FE3419 |
| presentation.yml | DCF427C1D6316B822390F1D2749A7A6A7090C206A13E9D44350824CC2484FA2E |
| private-messages.yml | 1B6BC765776D44872A11625A4C70457A34FE1D4DED70F964F5F37E4277EE6467 |
| warps.yml | A6DF27819FB3CBC5151F109D43C322E26E533BF01565E7D807B8057A703845A3 |

No additional live YAML appeared. The preexisting absent moderation.yml remained absent.
Live client test: not performed.
The smoke validates startup, console registration, reload and shutdown. It does
not claim real-player GUI opening, visual review, clicks, permission revocation
or a played Blackjack round; those behavior claims are limited to harness coverage.

## Protected files and structural counts

The SHA-256 baseline comparison confirms the three historical reports unchanged:

| File | SHA-256 |
|---|---|
| docs/FULL_CODEBASE_AUDIT.md | 73F7FBF38E4DD01F5A3328275A8045C9FB3812D4B4BF6BA4D817458F1FFEA2CC |
| docs/MODERATION_AUTHORIZATION_HOTFIX.md | 99735909CC7A5B05F5CBE1DA6D01E563061988EC98F6A16B6F8B3D84BA09982A |
| docs/ARCHITECTURE_REFACTOR.md | 0D0DC4032A1C0CAA95855DC7DBB39BE1D794E5029C973B638F7D54B6683A404A |

plugin.yml is byte-identical:
`1E4A9591559E669CD5753812885B0604A03CE070F48BD1DE39AD95B505D9DF5C`.
pom.xml is byte-identical:
`4410C0CAE35B81F3CF251D566B913947E71F0DA8EBFD7D9178900CF79D20BC5C`.
Every bundled resource/config and README remains byte-identical.
The actual descriptor still contains 37 root commands, 50 permission nodes and
15 positive child edges. VapeeCore still registers 27 modules and six reload
participants. No dependency, descriptor, alias, default, config, persistence,
scheduler, command grammar or module-graph change occurred.

All moderation, staff hierarchy, permission, economy/reward, quest domain/service,
Blackjack, activity, seat, world display, utility, warp domain/service, FriendService,
ClanService, player and persistence production files remain unchanged.
Only the seven listed menu classes changed in existing production code.

## File inventory

Added (7):

- docs/GUI_INVENTORY_ITEM_CLEANUP.md
- src/main/java/dev/vapee/core/ui/UiItemSpec.java
- src/main/java/dev/vapee/core/ui/UiItems.java
- src/main/java/dev/vapee/core/ui/Pagination.java
- src/test/java/dev/vapee/core/ui/UiItemsHarness.java
- src/test/java/dev/vapee/core/ui/PaginationHarness.java
- src/test/java/dev/vapee/core/ui/MenuCloseProbe.java

Changed (18):

- docs/DEVELOPER_GUIDE.md — one focused replacement paragraph.
- src/main/java/dev/vapee/core/friend/gui/FriendMenu.java
- src/main/java/dev/vapee/core/clan/gui/ClanMenu.java
- src/main/java/dev/vapee/core/settings/SettingsMenu.java
- src/main/java/dev/vapee/core/settings/visibility/VisibilitySettingsMenu.java
- src/main/java/dev/vapee/core/settings/visibility/VisiblePlayersMenu.java
- src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenu.java
- src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestMenu.java
- src/test/java/dev/vapee/core/friend/gui/FriendMenuFixture.java
- src/test/java/dev/vapee/core/friend/gui/FriendMenuSecurityHarness.java
- src/test/java/dev/vapee/core/clan/gui/ClanMenuFixture.java
- src/test/java/dev/vapee/core/clan/gui/ClanMenuSecurityHarness.java
- src/test/java/dev/vapee/core/settings/SettingsMenuFixture.java
- src/test/java/dev/vapee/core/settings/SettingsMenuSecurityHarness.java
- src/test/java/dev/vapee/core/settings/visibility/VisibilityMenuFixture.java
- src/test/java/dev/vapee/core/settings/visibility/VisibilityMenuSecurityHarness.java
- src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorFixture.java
- src/test/java/dev/vapee/core/quest/daily/menu/QuestMenuFixture.java

Removed files: none. Removed duplicate methods/records are within the seven menus.
Local runner scripts, hashes and logs reside under ignored
`dev-server/.vapeecore-dev/phase30c-*`; no test infrastructure was added to Maven.

## Remaining findings and next phase

Resolved by this phase: GUI-001 and GUI-002.
Previously resolved and preserved: MOD-001 and API-001.
Exactly 16 historical audit findings remain open:

SEC-001, DISPLAY-001, PERSIST-001, PERSIST-002, LIFE-001, CONFIG-001,
CMD-001, DUP-001, PRES-001, PRES-002, LIFE-002, PERF-001, TEST-001,
TEST-002, DOC-001 and LEGACY-001.

Phase 30D is the next config/registration/module cleanup phase for LIFE-001 and
CONFIG-001; it was not started. Team teleport consent remains Phase 30G.
No persistence, wider security, display/performance, presentation, command helper,
test-runner or historical legacy cleanup was brought into this phase.
GUI-001 and GUI-002 are ready for merge within the stated scope: focused and full
regressions, the final artifact build, Paper smoke, exact protected-file hashes
and the complete diff audit pass. The explicit limitation is the unperformed
live-client test. The final delivery records the single local commit and clean
working tree; no merge or push is performed here.

## Complete full-run harness inventory

| Class | Checks | Exit |
|---|---:|---:|
| dev.vapee.core.activity.ActivityHarness | 153 | 0 |
| dev.vapee.core.activity.blackjack.BlackjackFairnessHarness | 393 | 0 |
| dev.vapee.core.activity.blackjack.BlackjackHarness | 317 | 0 |
| dev.vapee.core.activity.blackjack.BlackjackPresentationHarness | 123 | 0 |
| dev.vapee.core.activity.blackjack.BlackjackReadViewHarness | 21 | 0 |
| dev.vapee.core.activity.blackjack.presentation.BlackjackPreviewHarness | 16 | 0 |
| dev.vapee.core.activity.blackjack.table.BlackjackTableHarness | 90 | 0 |
| dev.vapee.core.chat.ChatHarness | 17 | 0 |
| dev.vapee.core.clan.ClanDomainHarness | 52 | 0 |
| dev.vapee.core.clan.ClanIntegrationHarness | 15 | 0 |
| dev.vapee.core.clan.ClanPersistenceHarness | 39 | 0 |
| dev.vapee.core.clan.ClanServiceHarness | 136 | 0 |
| dev.vapee.core.clan.gui.ClanCommandHarness | 83 | 0 |
| dev.vapee.core.clan.gui.ClanMenuHarness | 84 | 0 |
| dev.vapee.core.clan.gui.ClanMenuSecurityHarness | 114 | 0 |
| dev.vapee.core.command.CoreCommandHarness | 75 | 0 |
| dev.vapee.core.command.OnlineStaffTargetGuardHarness | 268 | 0 |
| dev.vapee.core.config.ConfigServiceHarness | 74 | 0 |
| dev.vapee.core.config.DefaultConsistencyHarness | 12 | 0 |
| dev.vapee.core.economy.command.CoinsCommandHarness | 1942 | 0 |
| dev.vapee.core.economy.EconomyIntegrationHarness | 27 | 0 |
| dev.vapee.core.economy.EconomyServiceHarness | 44 | 0 |
| dev.vapee.core.friend.FriendCommandHarness | 80 | 0 |
| dev.vapee.core.friend.FriendDomainHarness | 29 | 0 |
| dev.vapee.core.friend.FriendIntegrationHarness | 28 | 0 |
| dev.vapee.core.friend.FriendLifecycleHarness | 12 | 0 |
| dev.vapee.core.friend.FriendPersistenceHarness | 28 | 0 |
| dev.vapee.core.friend.FriendServiceHarness | 79 | 0 |
| dev.vapee.core.friend.gui.FriendMenuHarness | 159 | 0 |
| dev.vapee.core.friend.gui.FriendMenuSecurityHarness | 114 | 0 |
| dev.vapee.core.identity.IdentityHarness | 26 | 0 |
| dev.vapee.core.identity.ProfileHarness | 19 | 0 |
| dev.vapee.core.lobby.command.LobbyCommandHarness | 26 | 0 |
| dev.vapee.core.lobby.experience.navigator.NavigatorMenuHarness | 113 | 0 |
| dev.vapee.core.lobby.experience.navigator.NavigatorSecurityHarness | 82 | 0 |
| dev.vapee.core.lobby.player.LobbyHarness | 53 | 0 |
| dev.vapee.core.lobby.warp.command.WarpCommandHarness | 86 | 0 |
| dev.vapee.core.lobby.warp.WarpHarness | 118 | 0 |
| dev.vapee.core.message.CommandHelpHarness | 85 | 0 |
| dev.vapee.core.moderation.command.ModerationCommandHarness | 4344 | 0 |
| dev.vapee.core.moderation.command.ModerationDurationHarness | 53 | 0 |
| dev.vapee.core.moderation.ModerationBanEnforcementHarness | 31 | 0 |
| dev.vapee.core.moderation.ModerationDomainHarness | 112 | 0 |
| dev.vapee.core.moderation.ModerationLifecycleHarness | 149 | 0 |
| dev.vapee.core.moderation.ModerationMuteEnforcementHarness | 52 | 0 |
| dev.vapee.core.moderation.ModerationMuteProjectionHarness | 20 | 0 |
| dev.vapee.core.moderation.ModerationPersistenceHarness | 523 | 0 |
| dev.vapee.core.moderation.ModerationServiceHarness | 141 | 0 |
| dev.vapee.core.onlinereward.OnlineRewardLifecycleHarness | 42 | 0 |
| dev.vapee.core.onlinereward.OnlineRewardServiceHarness | 42 | 0 |
| dev.vapee.core.permission.LuckPermsAsyncHarness | 16 | 0 |
| dev.vapee.core.permission.PermissionDescriptorHarness | 835 | 0 |
| dev.vapee.core.player.repository.PlayerQuestPersistenceHarness | 21 | 0 |
| dev.vapee.core.player.repository.PlayerRewardPersistenceHarness | 11 | 0 |
| dev.vapee.core.player.repository.PlayerVisibilityPersistenceHarness | 17 | 0 |
| dev.vapee.core.player.settings.PlayerSettingsServiceHarness | 28 | 0 |
| dev.vapee.core.player.settings.PlayerVisibilitySettingsHarness | 11 | 0 |
| dev.vapee.core.presence.FriendPresenceNotifierHarness | 8 | 0 |
| dev.vapee.core.presence.PresenceLifecycleHarness | 14 | 0 |
| dev.vapee.core.presence.PresenceServiceHarness | 15 | 0 |
| dev.vapee.core.presentation.PlaytimeFormatterHarness | 14 | 0 |
| dev.vapee.core.presentation.PresentationHarness | 10 | 0 |
| dev.vapee.core.privatemessage.PrivateMessageSocialHarness | 117 | 0 |
| dev.vapee.core.quest.daily.command.QuestCommandHarness | 45 | 0 |
| dev.vapee.core.quest.daily.DailyQuestConfigHarness | 13 | 0 |
| dev.vapee.core.quest.daily.DailyQuestCycleSelectorHarness | 18 | 0 |
| dev.vapee.core.quest.daily.DailyQuestLifecycleHarness | 16 | 0 |
| dev.vapee.core.quest.daily.menu.QuestMenuHarness | 20 | 0 |
| dev.vapee.core.quest.daily.menu.QuestMenuSecurityHarness | 45 | 0 |
| dev.vapee.core.quest.DailyQuestServiceHarness | 20 | 0 |
| dev.vapee.core.quest.QuestDefinitionHarness | 29 | 0 |
| dev.vapee.core.quest.QuestLifecycleHarness | 42 | 0 |
| dev.vapee.core.quest.QuestPlaytimeProducerHarness | 18 | 0 |
| dev.vapee.core.quest.QuestProgressReporterHarness | 18 | 0 |
| dev.vapee.core.quest.QuestServiceHarness | 44 | 0 |
| dev.vapee.core.rank.RankCommandHarness | 16 | 0 |
| dev.vapee.core.rank.RanksCommandHarness | 12 | 0 |
| dev.vapee.core.rank.RankServiceHarness | 23 | 0 |
| dev.vapee.core.rank.staff.StaffHierarchyServiceHarness | 109 | 0 |
| dev.vapee.core.reward.RewardLifecycleHarness | 40 | 0 |
| dev.vapee.core.reward.RewardServiceHarness | 126 | 0 |
| dev.vapee.core.seat.SeatHarness | 52 | 0 |
| dev.vapee.core.settings.command.SettingsCommandHarness | 25 | 0 |
| dev.vapee.core.settings.SettingsMenuHarness | 66 | 0 |
| dev.vapee.core.settings.SettingsMenuSecurityHarness | 111 | 0 |
| dev.vapee.core.settings.visibility.SettingsModuleLifecycleHarness | 23 | 0 |
| dev.vapee.core.settings.visibility.SettingsNavigationHarness | 17 | 0 |
| dev.vapee.core.settings.visibility.VisibilityMenuSecurityHarness | 195 | 0 |
| dev.vapee.core.settings.visibility.VisibilitySettingsMenuHarness | 29 | 0 |
| dev.vapee.core.settings.visibility.VisiblePlayersMenuHarness | 74 | 0 |
| dev.vapee.core.ui.PaginationHarness | 1409 | 0 |
| dev.vapee.core.ui.UiItemsHarness | 17 | 0 |
| dev.vapee.core.utility.command.BuildCommandHarness | 17 | 0 |
| dev.vapee.core.utility.command.TeleportCommandHarness | 894 | 0 |
| dev.vapee.core.utility.command.UtilityCommandHarness | 1221 | 0 |
| dev.vapee.core.utility.TeleportParserHarness | 58 | 0 |
| dev.vapee.core.utility.UtilityInventoryHarness | 742 | 0 |
| dev.vapee.core.utility.UtilityServiceHarness | 35 | 0 |
| dev.vapee.core.visibility.FriendVisibilityRefreshHarness | 11 | 0 |
| dev.vapee.core.visibility.IgnoreVisibilityRefreshHarness | 7 | 0 |
| dev.vapee.core.visibility.VisibilityModuleHarness | 22 | 0 |
| dev.vapee.core.visibility.VisibilityPolicyHarness | 28 | 0 |
| dev.vapee.core.visibility.VisibilityServiceHarness | 10 | 0 |
| dev.vapee.core.worlddisplay.WorldDisplayHarness | 27 | 0 |
