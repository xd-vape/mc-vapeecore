# GUI layout architecture

## Before implementation audit

Baseline: clean `master`, equal to `origin/master`, HEAD `06aa130a2464febceccda6cbbfd656cde82dce7e` (2026-10-08). Canonical baseline: 135 harnesses, 20,915 checks, zero failures. Current descriptor contracts: 37 commands, 50 permissions, 15 positive child edges, 27 modules, six reload participants.

Repository-wide searches covered all main/test Java sources: inventory creation/opening, holders, sizes, raw slots, item access, constants, ranges, relative positions and source-reading assertions. Raw search receipts and tracked-file SHA-256 baseline are in ignored `dev-server/.vapeecore-dev/gui-layout-architecture/`. No AGENTS.md applies.

| Surface | Before | Refactor decision |
| --- | --- | --- |
| SettingsMenu | 54; named feature/status slots; entry validation tied to static size; static listener controls | Inject immutable layout; retain SettingsMenuEntry behavior and paired positions |
| VisibilitySettingsMenu | 54; paired icon/status constants; listener switch on slots | Semantic actions, independent status positions |
| VisiblePlayersMenu | 54; content 0–44; empty 22; range-based target validation | Explicit ordered content slots and actual UUID map |
| FriendMenu | Same geometry; UUID mapping already present | Preserve domain gestures and security; replace geometry |
| ClanMenu | Same geometry plus overview displays 20/22/24 | Paged layout plus separately validated exclusive overview positions |
| NavigatorMenu | Same geometry; warp IDs in holder | Explicit content mapping; preserve current access policy/warp resolution |
| DailyQuestMenu | Same geometry; read-only quest views | Explicit content; record actual rendered quest IDs; retain read-only behavior |
| InvseeService | 54-slot snapshot; storage, armor, offhand mapping | Preserve physical inventory contract and read-only protections |
| BlackjackInventoryService | Real hotbar; PDC action authority | Preserve production behavior; tests follow named placement constants |
| UtilityService Enderchest | Native owner.getEnderChest() | No custom chest layout to refactor |
| LobbyItemService | Real player hotbar, registered feature/PDC routing | Preserve existing YAML presentation and reconciliation contracts |

All inventory creation/opening surfaces were checked; no additional normal custom chest menus were found. Blackjack displays/previews are entity/block presentation, not additional chest GUIs.

### Critical review of `Fixes for hardcoded GUI Items`

Commit `06aa130` usefully centralized Settings defaults and made several tests read named constants. However, static inventory bounds remain; paged menus still equate item index and slot with capacity 45; FriendIntegrationHarness still requires status = feature + 9 and size 54, then searches Java source for a getter/setter instead of exercising the tile. Visibility tests require the same geometric relationship. Other menu/security harnesses still click historical numbers, require specific unused slots, and use fixed page fixtures. These are layout constraints, not product contracts.

### Architecture plan recorded before production edits

A small typed immutable MenuLayout holds valid chest size, ordered content slots, empty-state position and semantic action-to-slot lists. A paired Settings/Visibility action has independent icon/status positions. Menu-local defaults remain Java definitions; constructors allow alternative fixtures. Rendering and listeners read the instance layout; holders validate actual content membership. Pagination uses list length. Clan overview positions are validated separately because overview and paginated entries are exclusive views. No YAML/DSL/reflection framework, domain changes or descriptor changes are planned.

Existing owner, exact binding, active/current inventory, permission/profile, gesture, drag, close/quit and lifecycle guards must remain. Empty markers may share a content position only in the exclusive empty state; they may not collide with controls.

## Implementation and validation

### Final architecture

`ui/MenuLayout<A extends Enum<A>>` is one small immutable record; each menu owns its local Action enum and Java `DEFAULT_LAYOUT`. It contains chest size, ordered explicit content positions, empty marker and action→position lists. One position is a normal control; two positions are an icon/status pair. Defensive copies protect the list, map and nested action lists. There is no GUI YAML, reflection, discovery, DSL or service container.

The constructor rejects invalid chest sizes (9–54, multiples of nine), negative/out-of-bounds slots, duplicate content, duplicate controls, content/control collisions and empty/control collisions with descriptive IllegalArgumentException before inventory creation. Paged constructors require content; fixed constructors reject paged metadata. Required menu actions and position counts are checked when injecting layouts. SettingsMenu additionally validates actual entry positions against the instance size and reserved controls, so synthetic entries cannot bypass bounds/collision checks. SettingsMenuEntry no longer depends on the default inventory size.

Every runtime menu owns its immutable instance layout. Rendering reads `layout.size()`, `layout.slot(Action)` and `layout.contentSlot(indexOnPage)`. Listeners read that same layout or the holder's actual rendered slot→domain target. Bounds use the actual top inventory. Pagination remains a pure helper and receives `layout.pageSize()`, exactly `contentSlots.size()`; physical inventory slot never equals a domain index by assumption.

Friend, Clan and VisiblePlayers keep immutable UUID target maps, validated by explicit content membership. Navigator keeps its immutable warp-ID map. DailyQuest now also records rendered slot→quest-definition ID; entries remain read-only and clicks still cannot grant rewards or mutate quest state. Empty markers may share content because they render only when content is absent. DailyQuest preserves its existing empty view with safe controls and no new empty marker item. Clan's small local Layout record validates overview display positions separately: overview and paginated views are exclusive, so their positions may overlap; controls may not overlap either view.

Current production appearance/default sizes remain unchanged. Existing public constants and default constructors remain usable aliases/seams. `CONTENT_SIZE` now means default list length, not a slot range; `*_SLOT` and size constants are derived from DEFAULT_LAYOUT. They are no longer Java compile-time literal constants; internal numeric switches now use semantic Actions. Consumers should use `menu.layout()` for injected instances and rebuild with the plugin when changing Java layouts.

### Preserved behavior and security

Owner UUID, exact holder/inventory binding, active inventory registry, existing current-top checks, permission/profile checks, current Friend/Clan/Warp/Visibility/Quest service authority, supported gestures, drag/click cancellation, stale/forged/foreign rejection, close/quit and disable cleanup remain in their existing feature paths. Removing geometry gates does not remove authorization gates. Target IDs remain server-side authority even if displayed ItemStacks are replaced. No domain schema, config version, command, permission, module wiring, rank, presentation, economy or moderation behavior changed.

SHA-256 baseline parity verified all 322 existing main/resource files outside the 21 affected menu/holder/listener/entry files. All resources and historical reports remain unchanged. InvseeService, UtilityService's native Enderchest, Blackjack production classes, lobby registry/config/service/listener and domain services remain byte-identical. The one added production class is MenuLayout.

### Regression results

| Validation | Result |
| --- | --- |
| Canonical before | 135/135 harnesses, 20,915 checks, zero failures |
| Canonical after: scripts/run-harnesses.ps1 -Offline | 142/142 executed and passed; 21,221 checks; zero failures; zero missing/invalid |
| Existing harness preservation | All 135 retained; no removals; seven additions; 306 additional checks |
| SettingsLayoutHarness | 23 checks: size 45, close 8, feature 10/status 43, all real toggle pairs, persistence, refresh, foreign/stale/gesture guards |
| VisibilityLayoutHarness | 66 checks: fixed pairs plus VisiblePlayers sparse/reordered/high-slot mapping, exact removal and parent navigation |
| FriendLayoutHarness | 69 checks: sparse pagination, exact UUID removal, incoming request accept, tabs/refresh/close and stale inventory |
| ClanLayoutHarness | 60 checks: alternative overview positions, sparse member pagination, exact kick target, current permission and stale close |
| NavigatorLayoutHarness | 36 checks: warp mapping, teleport, sparse/reordered pagination, stale/drag/close |
| QuestLayoutHarness | 36 checks: quest-ID mapping, read-only state/save/reward boundary, pagination/refresh/stale/close |
| MenuLayoutHarness | 16 checks: immutable geometry, size/bounds/duplicate/collision validation, target membership and capacity |
| Maven | BUILD SUCCESS; clean package; Java 21.0.12.1; IntelliJ IDEA 2026.2.2 bundled Maven; existing local repository in offline mode |
| Paper 1.21.11-132 smoke | Successful startup, core/version/help/reload, Bukkit command listing, LuckPerms info, zero online players, clean stop |
| Actual live contracts | 27 modules enabled/disabled in reverse order; six reload participants; all 37 descriptor command roots; zero ERROR/SEVERE |
| Live file parity | All 17 existing VapeeCore files byte-identical; all ten YAML files identical; exact YAML file set retained |

The current PermissionDescriptorHarness also preserves 50 permission nodes and 15 positive child edges. Build/fixture warnings already present in the baseline are not failures. Server smoke verifies real initialization/reload/shutdown; menu interaction assertions use real production listeners/services with isolated Bukkit fixtures, not an invented live-client result.

FriendIntegrationHarness's two weak layout/source-string assertions were replaced with actual GUI rendering and icon/status listener events against its real file repository. After unload, persisted privacy and the existing FriendRequestPolicy still observe the setting. Architecture-only source checks for module wiring/security contracts remain; tests no longer search for a setter string as proof of behavior.

### Required counterchecks (Negative Controls)

| Countercheck | Execution and result |
| --- | --- |
| 1. Move Close only | Temporarily changed Settings DEFAULT_LAYOUT Close 49→8. Existing Settings menu/security/extension and Friend integration harnesses passed without listener/test edits. |
| 2. Change content positions/order | Temporarily reversed Navigator's central content list 44→0. Existing Navigator menu/security and Warp harnesses passed. Additionally all five paged menus use injected `(10,12,14,28,30,32)`, `(32,10,30,12,28,14)` and high-content `(51,10,30,12,28,14)` fixtures, checking every actual ID and the next-page boundary. |
| 3. Status independent of feature | Temporarily changed Settings scoreboard pair `(10,19)`→`(10,43)`. Existing behavior/security/Friend integration passed. New Settings/Visibility fixtures independently exercise both positions. |
| 4. Close/Next collision | MenuLayoutHarness deliberately assigns both to slot 8; descriptive IllegalArgumentException before Bukkit creation. |
| 5. Out-of-bounds | MenuLayoutHarness deliberately uses size 27/close 40; descriptive IllegalArgumentException before Bukkit creation. |
| Additional size proof | Temporarily changed only Settings DEFAULT_LAYOUT to size 45, close 8, refresh 44; existing menu/security/extension/navigation/Friend integration harnesses passed. |
| Actual hotbar proof | Temporarily changed only BlackjackInventoryService.LEAVE_SLOT 8→7; BlackjackPresentationHarness passed all 132 checks. PDC routing and ownership stay unchanged. |

All temporary production mutations were restored byte-for-byte in a finally block. The final canonical run and clean package occurred after restoration. Receipts: counter-*.log, final-results.json, build.log, gui-layout-smoke-result.json and parity JSON in the ignored audit directory. The sparse Clan test caught and removed an initially remaining physical-slot/page-capacity comparison; the final audit has none.

### Final repository-wide audit and remaining numeric constraints

The complete Section-1 search was rerun over all main and test Java sources; inventory creation/opening still resolves to exactly seven normal menus plus the explicit technical surfaces. Findings are classified as follows:

| Remaining match family | Classification / reason |
| --- | --- |
| Seven DEFAULT_LAYOUT definitions, including explicit default 0–44 lists | Central layout definition only, independently editable per menu; never inferred as ranges |
| Derived SIZE/INVENTORY_SIZE/CONTENT_SIZE/*_SLOT aliases | Compatibility names reading central defaults; not extra numeric authorities |
| `layout.slot(action, 1)` for status, page zero, indexOnPage loops | Semantic pair/list indexing; no spatial offset |
| `top.getSize()`, raw slot <0/>= actual size, inactive-page controls | Actual inventory bounds/availability, not fixed chest geometry |
| MenuLayout size%9 and max54 | Minecraft chest structure; invalid shapes rejected centrally |
| Clan overview/content or empty/content overlap | Explicitly validated exclusive views/states; no shared simultaneous controls |
| Invsee size54, storage0–35, armor45–48, offhand50 and snapshot harnesses | Real player-inventory snapshot contract, item cloning and readonly protection; intentionally unchanged |
| PlayerInventory storage36, armor4, hotbar9, offhand/native equipment slot, bottom inventory fixtures | Actual Bukkit/Minecraft structure and transfer protection, not normal chest UI positions |
| Blackjack primary/stand/double/status/leave constants and hand/table geometry | Single real-hotbar placement definition or game/display-domain constraint; action authority is PDC |
| Lobby YAML fixture slots/PDC IDs/reconciliation and LobbyItemListener hotbar-button reads | Real hotbar operator presentation and security; no new chest framework or routing |
| Utility native Enderchest | Existing native inventory, no custom slot geometry |
| SeatListener EquipmentSlot.HAND/event item | Block seating interaction gate; no GUI or numeric chest routing |
| Explicit alternative layout numbers in new tests | Deliberate geometry fixtures/invalid definitions, not historical production appearance assertions |
| Synthetic Settings entry fixture | Finds unused positions in the current default and asserts descriptor positions; no copied production slots |
| Security fake inventory sizes/targets, bottom/outside/unused cases | Default size/list or actual top size/semantic controls; unsupported clicks and ownership are tested independently of design |
| PaginationHarness numerical indices/capacities | Pure collection slicing/overflow tests, not physical inventory slots |
| Other broad numeric search hits | Domain counts, timestamps, config fixtures, overflow sentinels and physical game/display geometry; no additional inventory creation surface |

No remaining normal-menu production listener recognizes content by `< CONTENT_SIZE` or `< pageSize`. No `+9` status rule, historical numeric listener case or raw historical normal-menu test click remains. Empty-state tests read the configured slot; sorting/page fixtures derive their boundary sizes from capacity. No problematic GUI match was left as an unexplained exception.

### Developer workflow and Git

See `docs/DEVELOPER_GUIDE.md` → **How to change a GUI layout**. Change one menu's DEFAULT_LAYOUT size/actions/content/empty positions (and Clan overview fields), then build and run harnesses. Rendering, action dispatch, capacity, holder targets and default aliases follow automatically. Existing data, permissions and lifecycle gates are independent of geometry. Legacy guide paragraphs/tables containing obsolete Settings positions and fixed footer/range contracts were also corrected.

Work stayed on current master, starting at `06aa130a2464febceccda6cbbfd656cde82dce7e`. One local commit is requested with subject `Refactor GUI layout architecture`; its actual SHA is emitted after creation. No branch, push, merge or history rewrite is performed. The tested artifact is also installed in the stopped dev-server; the previous jar is preserved in the ignored audit directory.
