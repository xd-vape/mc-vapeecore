# GUI pagination and automatic refresh

## Audit before implementation

Initial branch master, HEAD `3cd732095eee7c3fb920187062550f4ed2063b6a`. Nine pre-existing uncommitted files belong to the already tested server-name color fix (three production render paths, three harnesses, config comments and two docs). Their exact initial bytes/diff are saved under ignored `dev-server/.vapeecore-dev/gui-pagination-auto-refresh/`; these changes must remain intact and outside this GUI commit. The canonical baseline was executed before production edits: 149 discovered/executed/passed harnesses, 21,808 checks, zero failures and zero missing/invalid entry points.

Repository search found the five paginated chest menus Friend, Clan, VisiblePlayers, Navigator and DailyQuest and the fixed Settings/Visibility menus. Invsee/native Enderchest/Blackjack/Lobby hotbar remain technical exceptions. Internal refresh methods, relationship propagation, quest progress flush/cycle scheduling and presentation refresh are not manual buttons and must remain.

| Menu | Visible Refresh currently? | What does it refresh? | Replacement |
| --- | --- | --- | --- |
| Settings | No | Existing in-place setting/status rerender after persistence | Preserve operator layout; observe committed owner settings changes |
| VisibilitySettings | Yes | Master/filter status; stale outside changes | Remove default Clock; update existing view after saves / owner-setting notification |
| VisiblePlayers | Yes | Added-user list, identity/name and online state | Remove default Clock; committed owner-settings change and targeted join/quit notifications |
| Friend | Yes | Friend/request lists, counts and online status | Remove default Clock; committed pair changes (including requests), targeted presence events |
| Clan | Yes | Membership, role, invites and overview | Remove default Clock; committed snapshot changes affecting members/invite recipients |
| Navigator | No | Warp list is a snapshot; Page Info is unconditional | Narrow successful WarpService mutation notification; rebuild current mappings/page |
| DailyQuest | Yes | Progress/reward/assignment state | Remove default Clock; notify owner only after successful existing QuestService flush |

Friend/Social relationship listeners already protect visibility but do not cover send/deny/cancel request changes. Their existing semantics must not be broadened or duplicated. Clan and Warp lack a UI change seam. Quest uses deferred dirty tracking and an existing playtime/flush task, while DailyQuest has an existing 1200-tick cycle task: UI notification must follow successful persistence and reuse those execution points without changing schedules.

Previous/Next are conditionally drawn, but listeners still resolve potential layout Actions. Navigator and DailyQuest draw Page Info even on a single page; Friend/Clan/VisiblePlayers do not yet expose Page Info. Plan: pure Pagination.hasMultiplePages, local page-info rendering, holder snapshots of actual rendered Actions; unavailable controls cannot resolve, even with forged inventory items.

The current DailyQuest source has ordinary ordered content and no adaptive row table. Implement the requested feature-local 1–5 arrangement with capacity five; keep list-menu geometry unchanged. All live rerenders should retain the currently owned Inventory, clamp pages, republish immutable target/control data and preserve current-top/owner/binding/permission/domain guards. View navigation may still create a new Inventory.

## Implementation and results

### Pagination and runtime authority

`Pagination.hasMultiplePages()` is a pure query; existing calculations and Previous/Next queries stay domain-free. Each menu decides its own materials, lore and placement. No shared GUI framework was added.

| Runtime page | Previous | Page Info | Next |
| --- | --- | --- | --- |
| Empty or single page | Absent | Absent | Absent |
| First of multiple | Absent | Present | Present |
| Middle | Present | Present | Present |
| Last | Present | Present | Absent |

All five paginated holders publish an immutable completed render snapshot containing current target mappings, page/view and actually placed controls. Listeners resolve `holder.actionAt(slot)`, never a potential `DEFAULT_LAYOUT` action alone. Hidden slots remain inert even if a foreign ItemStack is inserted there; Page Info is display-only. The owner and native Inventory binding remain the original capability.

`refreshActive(UUID)` checks the active registry, exact current top/holder binding and existing access gates. It normally repaints the same Inventory without close/open flicker, republishes fresh slot mappings and clamps the requested page through Pagination. A still-valid page is retained. A Clan membership change that requires a different view/title may replace the Inventory through the existing open/binding path; the old instance stays inert. Render failures remove only the affected owned active capability and never close a foreign current inventory or roll back a completed domain save.

Normal production `DEFAULT_LAYOUT`s contain no Refresh placement. Root Settings keeps its existing operator geometry unchanged. The optional `REFRESH` Action/renderer remains available to explicitly authored Java overrides and older independent fixtures, preserving the existing authoring API; automatic-update tests never require that button. Internal rerender functions and unrelated presentation/domain refresh paths remain intact.

### Committed update sources and lifecycle

| Menu | Automatic source | Scope and transaction boundary |
| --- | --- | --- |
| Settings | `PlayerSettingsChangeListener` | Successful owner settings save; existing GUI toggles also rerender in place |
| VisibilitySettings | Same settings observer | Successful owner master/filter save; existing toggle still applies visibility/hotbar behavior; no duplicated policy |
| VisiblePlayers | Same settings observer; existing GUI listener Join/Quit handlers | Successful added-user save/removal; active viewers listing the affected UUID for presence/name changes |
| Friend | `FriendChangeListener`; existing GUI listener Join/Quit handlers | Saved relationship/request map diff yields affected endpoint UUIDs; online changes target active views containing that friend |
| Clan | `ClanChangeListener`; existing GUI listener Join/Quit handlers | Saved clan-record and invitation symmetric diff yields affected members, invite recipients and source owners; related member presence updates |
| Navigator | `WarpChangeListener` | Successful existing admin WarpService persistence updates the shared catalog; only currently active Navigator views rebuild |
| DailyQuest | `QuestChangeListener` | Successful existing `QuestService.flushPlayer` updates only that owner's active view, including persisted progress/reward state and Daily rotation |

Five small domain-specific observer interfaces were added; no global bus, generic observable framework or domain dependency on Bukkit/menu classes. Existing Friend/Social relationship notifications remain authoritative for visibility and retain their original relationship-only semantics. The new Friend observer covers request-only changes as well, without changing relationship truth. Saves happen before published snapshots and UI notifications. Failed saves publish no UI success; settings rollback and existing quest dirty/retry behavior are preserved. An observer exception is logged and isolated from other callbacks; it cannot undo the committed domain state.

Clan notification uses changed invitations from both old and new snapshots, including cancellation of the final invitation. A focused test holds both inviter and recipient inventories open and proves that both mappings clear automatically. Clan name/tag/member-count changes also notify pending invitation recipients because their entries display source-clan data; the rename case is tested.

Module enable activates observers; disable/failure cleanup removes them and closes only owned views. Normal menu close leaves the module observer available for later reopen. Daily configuration reload closes owned menus through its existing path without accidentally detaching the enabled module subscription. No per-tick/second GUI task was introduced. Existing Quest flushing every 100 ticks and Daily cycle synchronization every 1200 ticks are unchanged. Quest UI updates on committed flush, not unpersisted progress; its callback renders current state without invoking another Daily sync. Thus deferred persistence retains its existing latency.

Join/Quit updates scan only active views and check whether the affected target is relevant. A transient, event-local presence override renders Quit as offline even if Bukkit still resolves that player as online during the event; it is cleared afterward. No permanent UUID cache or global player-GUI rebuild was introduced.

### DailyQuest arrangement

One feature-local `DailyQuestMenu.Layout` in `DEFAULT_LAYOUT` owns the arrangement:

| Actual visible quests | Current default slots |
| --- | --- |
| 0 | Existing configurable Empty State |
| 1 | 13 |
| 2 | 11, 15 |
| 3 | 11, 13, 15 |
| 4 | 10, 12, 14, 16 |
| 5 | 9, 11, 13, 15, 17 |

Maximum capacity is five. Six assignments use the one-item arrangement on page two; seven use the two-item arrangement. The local API derives allowed content-slot union, exposes `pageSize()` and `visibleContentSlots(count)`, and validates complete exclusive rows and collisions. The union is not itself the page capacity. Tests query this owning API instead of duplicating the arrangement table. An explicit uniform-layout adapter keeps independent fixtures usable. The four list menus retain their ordered `contentSlots()` capacities and geometry; no global centering was applied. Quest items remain read-only; no new domain mutation was invented.

### Security preservation

UUID/warp ID/quest ID are rebuilt from current domain state into each rendered holder snapshot. No identity is inferred from material, title, display name, lore or an old list index. Current-top, owner, exact inventory, active registry, permission/profile and existing domain/activity/lobby gates remain in force. Click/drag cancellation and unsupported gesture restrictions remain. Friend/Clan/VisiblePlayers explicitly forget an old owned active binding when a stale inventory event arrives while a foreign inventory is current, preserving the prior security harness contract without closing the foreign inventory.

### Validation

- Baseline: 149/149 harnesses passed, 21,808 checks; 0 failed, 0 missing/invalid.
- Focused GUI/domain set: 64 harnesses executed; obsolete reopen/always-visible pagination assertions were updated to the new behavior. All prior entry points remain. Friend/Clan/Visibility security suites were rerun after the stale-active cleanup correction (156/114/327 checks respectively), all passing.
- Seven new executable harnesses cover the five pagination matrices and Root/Visibility automatic updates, plus the shared assertion helper. Sparse independent list layouts deliberately force first/middle/last pages. Empty/one-page controls and hidden-slot forged-item clicks are covered in every paginated menu. Tests cover successful GUI/external updates, page disappearance/clamping, pagination appearing/disappearing, current mappings, online/offline changes, no unrelated-view repaint, persistence failure, lifecycle detach/restart and reopen.
- Daily adaptive tests cover 0 through 7 assignments and a three-page case, deriving slots from the owning layout API. Dirty progress is not optimistically shown; failed save does not rerender; successful flush/rotation rebuilds current content and targets.
- Final Clan-focused verification: 76 checks, including both views after the final invitation is removed.
- Final canonical regression/build results: 156 discovered/executed/passed harnesses, 22,301 checks, 0 failures, 0 missing/invalid; all 149 baseline entry points retained plus 7 new. Offline `clean package`: BUILD SUCCESS (2026-10-13 16:49:58 Europe/Berlin). Validation uses the preserved current working tree, including the nine pre-existing server-color changes excluded from this GUI commit.
- Production Refresh/layout search and complete maintained-guide review found no remaining requirement to press a Refresh item in a normal default menu. Remaining `REFRESH` code is optional override rendering/routing; remaining `refresh` methods are legitimate internal/domain/presentation operations. Historical reports were not rewritten.
- No Paper deployment/smoke or manual Minecraft client acceptance was performed in this task. Native visual balance/flicker and real multi-client behavior therefore remain unclaimed; harnesses verify the binding and in-place paths.

Canonical commands used:

```powershell
pwsh -File .\scripts\run-harnesses.ps1 -Offline -MavenRepository 'C:\Users\mehdi\.m2\repository'
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -o '-Dmaven.repo.local=C:\Users\mehdi\.m2\repository' clean package
```

### Executed negative controls

Each intentional mutation compiled and failed its designated test for the expected reason, then was restored byte-for-byte. Final regression recompiles the restored source.

| Control | Temporary defect | Detection |
| --- | --- | --- |
| A | Navigator listener accepts potential NEXT slot on one page | Hidden-control click unexpectedly reopens/navigates |
| B | Navigator renders Previous on the first page | Pagination matrix rejects the visible unavailable control |
| C | Friend post-commit active rerender disabled | External acceptance fails to create pagination in the same inventory |
| D | Friend holder reads original target map after rebuild | Last-page removal fails fresh-target UUID assertion |
| E | Settings automatic-update test demands a Refresh click before checking updated state | No Refresh slot exists; required click fails immediately |

Ignored local receipts: `dev-server/.vapeecore-dev/gui-pagination-auto-refresh/` contains baseline/final JSON and logs, focused/security logs, counter mutation sources/results and initial byte backups. No negative mutation or receipt enters the commit.

### Git scope and exact changed files

Work is isolated on `phase/gui-pagination-auto-refresh`, based on the initial local HEAD. One local GUI commit is intended after final validation, titled `Improve GUI pagination and automatic refresh`; no push, merge or Phase 38 work. Nine initial server-color working changes remain outside this commit, including the server-color row in the guide (index content excludes only that pre-existing row).

The following files constitute the GUI change; no resources/operator config or historical reports are part of it:

- `docs/DEVELOPER_GUIDE.md`
- `reports/gui-pagination-auto-refresh/GUI_PAGINATION_AUTO_REFRESH.md`
- `src/main/java/dev/vapee/core/clan/ClanChangeListener.java`
- `src/main/java/dev/vapee/core/clan/ClanModule.java`
- `src/main/java/dev/vapee/core/clan/ClanService.java`
- `src/main/java/dev/vapee/core/clan/gui/ClanMenu.java`
- `src/main/java/dev/vapee/core/clan/gui/ClanMenuHolder.java`
- `src/main/java/dev/vapee/core/clan/gui/ClanMenuListener.java`
- `src/main/java/dev/vapee/core/friend/FriendChangeListener.java`
- `src/main/java/dev/vapee/core/friend/FriendModule.java`
- `src/main/java/dev/vapee/core/friend/FriendService.java`
- `src/main/java/dev/vapee/core/friend/gui/FriendMenu.java`
- `src/main/java/dev/vapee/core/friend/gui/FriendMenuHolder.java`
- `src/main/java/dev/vapee/core/friend/gui/FriendMenuListener.java`
- `src/main/java/dev/vapee/core/lobby/experience/LobbyExperienceModule.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorInventoryHolder.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorListener.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenu.java`
- `src/main/java/dev/vapee/core/lobby/warp/WarpChangeListener.java`
- `src/main/java/dev/vapee/core/lobby/warp/WarpService.java`
- `src/main/java/dev/vapee/core/player/settings/PlayerSettingsChangeListener.java`
- `src/main/java/dev/vapee/core/player/settings/PlayerSettingsService.java`
- `src/main/java/dev/vapee/core/quest/QuestChangeListener.java`
- `src/main/java/dev/vapee/core/quest/QuestService.java`
- `src/main/java/dev/vapee/core/quest/daily/DailyQuestModule.java`
- `src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestInventoryHolder.java`
- `src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestMenu.java`
- `src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestMenuListener.java`
- `src/main/java/dev/vapee/core/settings/SettingsMenu.java`
- `src/main/java/dev/vapee/core/settings/SettingsModule.java`
- `src/main/java/dev/vapee/core/settings/visibility/VisibilitySettingsListener.java`
- `src/main/java/dev/vapee/core/settings/visibility/VisibilitySettingsMenu.java`
- `src/main/java/dev/vapee/core/settings/visibility/VisiblePlayersHolder.java`
- `src/main/java/dev/vapee/core/settings/visibility/VisiblePlayersListener.java`
- `src/main/java/dev/vapee/core/settings/visibility/VisiblePlayersMenu.java`
- `src/main/java/dev/vapee/core/ui/Pagination.java`
- `src/test/java/dev/vapee/core/clan/ClanIntegrationHarness.java`
- `src/test/java/dev/vapee/core/clan/gui/ClanMenuHarness.java`
- `src/test/java/dev/vapee/core/clan/gui/ClanPaginationUpdateHarness.java`
- `src/test/java/dev/vapee/core/friend/FriendLifecycleHarness.java`
- `src/test/java/dev/vapee/core/friend/gui/FriendMenuHarness.java`
- `src/test/java/dev/vapee/core/friend/gui/FriendPaginationUpdateHarness.java`
- `src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenuHarness.java`
- `src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorPaginationUpdateHarness.java`
- `src/test/java/dev/vapee/core/quest/daily/menu/DailyQuestPaginationUpdateHarness.java`
- `src/test/java/dev/vapee/core/quest/daily/menu/QuestLayoutHarness.java`
- `src/test/java/dev/vapee/core/quest/daily/menu/QuestMenuFixture.java`
- `src/test/java/dev/vapee/core/quest/daily/menu/QuestMenuHarness.java`
- `src/test/java/dev/vapee/core/quest/daily/menu/QuestMenuSecurityHarness.java`
- `src/test/java/dev/vapee/core/settings/SettingsAutomaticUpdateHarness.java`
- `src/test/java/dev/vapee/core/settings/visibility/SettingsModuleLifecycleHarness.java`
- `src/test/java/dev/vapee/core/settings/visibility/SettingsNavigationHarness.java`
- `src/test/java/dev/vapee/core/settings/visibility/VisibilityAutomaticUpdateHarness.java`
- `src/test/java/dev/vapee/core/settings/visibility/VisibilitySettingsMenuHarness.java`
- `src/test/java/dev/vapee/core/settings/visibility/VisiblePlayersMenuHarness.java`
- `src/test/java/dev/vapee/core/settings/visibility/VisiblePlayersPaginationUpdateHarness.java`
- `src/test/java/dev/vapee/core/ui/MenuTestLayouts.java`
- `src/test/java/dev/vapee/core/ui/PaginationAssertions.java`
