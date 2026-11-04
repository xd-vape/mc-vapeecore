# GUI authoring simplification

## Starting state and confirmed failure

Started on `master`, HEAD `0da6e86c569aea6f16cadfb5d25c0017a581e755`. The initial working tree contained one intentional operator edit: `SettingsMenu.java`. No operator changes were restored from Git. The prior successful full receipt contained 142 harnesses, 21,221 checks, zero failures.

The operator's Settings definition changed size to 45, Close to 40, Friend Presence to 14/23 and Visibility to 16/25, and removed Refresh placement and its alias. Scoreboard 10/19, Sounds 11/20, Private Messages 12/21 and Friend Requests 13/22 remained. This exact composition is preserved semantically in the final builder definition.

The unmodified operator state was compiled first. Maven testCompile failed with missing `REFRESH_SLOT` references in SettingsMenuHarness, SettingsMenuSecurityHarness and SettingsNavigationHarness. Rendering and SettingsListener also required the absent Refresh; the initial compile failure masked that runtime problem. Additionally every menu's requireActions compared injected action sets and arities with production defaults, preventing omission of buttons, features and status indicators. Moving slots was supported, changing composition was not.

## Audit and scope

All main/test Java sources were searched for `_SLOT`, SIZE/INVENTORY_SIZE/CONTENT_SIZE, default layouts, mandatory action lookups and ordinary control Actions. All seven normal menus are included: SettingsMenu, VisibilitySettingsMenu, VisiblePlayersMenu, FriendMenu, ClanMenu, NavigatorMenu and DailyQuestMenu. Before/after complete search receipts, operator snapshot/diff, baseline SHA-256 hashes, compile logs, individual focused logs, countercheck logs and canonical results are retained under ignored `dev-server/.vapeecore-dev/gui-authoring-simplification/`.

No applicable AGENTS.md or external compatibility consumer was found. Repository-internal harnesses were the consumers of the normal menus' mirrored public layout constants. Those are not compatibility contracts. 78 active mirror aliases were removed; the operator's already removed Settings REFRESH_SLOT was never reintroduced.

| Occurrence family | Classification and disposition |
| --- | --- |
| Normal menu public `*_SLOT`, SIZE, INVENTORY_SIZE, CONTENT_SIZE mirrors | Removed; only DEFAULT_LAYOUT and immutable instance APIs author layout/composition |
| SettingsMenuHarness / SettingsMenuSecurityHarness static aliases; SettingsNavigationHarness root Refresh | Replaced with explicit behavior-fixture composition, including intentionally present Refresh |
| Other normal menu/security/layout harnesses | Query explicit independent MenuTestLayouts / injected layouts; no production optional placement requirement |
| FriendIntegrationHarness / SettingsPrivacyProbe | Real persisted settings behavior remains; explicit fixture exposes the feature being tested |
| WarpHarness holder/layout assertions | Explicit fixture mapping and membership, replacing a mirror-equality assertion with actual target membership |
| Forged/unbound holder test construction | Pass the fixture's layout explicitly, so security fixtures do not validate against production geometry |
| Action enum symbols in tests | Supported behavior identity, not a requirement to expose an Action in production |
| Fixture `.slot(Action)` / `.actionSlot` | Explicitly guaranteed fixture placement; no lookup against optional production defaults |
| DEFAULT_LAYOUT in new composition harnesses | Dynamic smoke of current composition; no optional control forced to exist |
| Invsee native snapshot numbers, inventory storage/armor/offhand sizes | Genuine Minecraft contract; unchanged |
| Blackjack hotbar placement constants and PDC tests | Separate real-hotbar authority; existing centralized placement and runtime identity tests unchanged |
| Lobby item YAML/registry/PDC and hotbar ranges | Live configuration and physical hotbar contract; unchanged |
| Native Enderchest | Native inventory; unchanged |
| Pure pagination indices, security bottom/outside slots, unrelated numeric domain tests | Collection/domain/physical transfer constraints; not chest composition |

## Final authoring model

MenuLayout retains the small immutable value-object architecture and defensive copies. Its typed Java builder offers `setting(action, icon[, status])`, `button(action, slot)`, `content(orderedSlots)` and `emptyState(slot)`. Omit a placement to omit its presentation and click mapping. No YAML chest configuration, reflection, discovery, scripting, annotations or universal framework was added.

`actionSlot(action[, position])` returns OptionalInt; `placement(action)` returns an immutable empty list if absent. `actionAt(rawSlot)` resolves only actual placed positions. Production renderers use optional placement; listeners resolve semantic Actions/current entry maps. The strict `slot` convenience is used only for explicitly guaranteed fixture positions, not optional production controls.

Validation still rejects invalid chest sizes, negative/out-of-bounds declared slots, duplicate positions/actions, content/control collisions, empty/control collisions and invalid placement arity. Each placed button has one position; each placed setting has one icon and at most one independent status. Validation checks present placements, never requires an Action to be exposed. Paged content capacity remains structural; controls and empty markers are presentation choices. Clan overview displays can be omitted with -1; only present overview positions participate in bounds/distinctness/collision validation.

SettingsMenuEntries now contains independent feature definitions (renderers, existing getter/setter action and context). `defaults(layout)` selects only placed features and gives icon-only entries statusSlot -1. It does not force all supported features into a menu. Removing a GUI setting does not remove its preference, domain service or persistence. Every supported Settings feature is tested both with and without its status item.

Friend, Clan, VisiblePlayers, Navigator and DailyQuest derive rendering, navigation, pagination and runtime target maps from their instance layout as before, with all ordinary controls now optional. DailyQuest remains read-only. Content order/capacity and target authority are unaffected by omitted controls.

Permissions, owner/holder/exact inventory binding, active/current-top validation, unsupported gestures, stale/foreign/forged rejection, drag protection, profile guards, close/quit/disable cleanup and domain authorization remain in the existing paths. Native Esc close still cleans the Settings menu without a Close button.

## Tests and counterchecks

Behavior fixtures are explicit in test-only MenuTestLayouts and local fixtures. They expose the feature/control being tested independently of production DEFAULT_LAYOUT. Default smoke checks discover current placements and render only those. Existing security assertions and all 142 baseline harness entry points are retained.

Seven executable additions: SettingsCompositionHarness, VisibilityCompositionHarness (also covers VisiblePlayers), FriendCompositionHarness, ClanCompositionHarness, NavigatorCompositionHarness, QuestCompositionHarness and GuiAuthoringAuditHarness. New CompositionTestSupport only removes placements; it implements no feature behavior or authorization.

| Acceptance / negative control | Actual proof |
| --- | --- |
| Production Settings without Refresh | Current 45-slot operator layout opens; every populated slot matches actual composition; no Clock if REFRESH is absent; Maven testCompile succeeds |
| A: restore compile dependency | Temporarily added a real test source referencing removed SettingsMenu.REFRESH_SLOT. GuiAuthoringAuditHarness rejected it and Maven testCompile failed with that missing symbol. Temporary file removed in finally |
| B: make production require Refresh | Temporarily forced OptionalInt.orElseThrow in Settings placement for REFRESH. Mutation compiled; SettingsCompositionHarness failed in SettingsMenu.place/open with NoSuchElementException. Restored exact original bytes in finally; final regression rebuilds restored sources |
| C: no Scoreboard status | Icon-only fixture renders and toggles/persists correctly; absent status cannot toggle. All six supported Settings features additionally run icon-only and paired fixtures |
| D: omit complete feature | Scoreboard absent from Sounds-only fixture; no render/entry mapping/ghost click; underlying preference remains usable through PlayerSettingsService |
| E: move fixture Refresh | Refresh fixtures at slots 3 and 7 render and refresh existing state; old slot 52 remains inert; no listener edit and no mutation on Refresh |
| Minimal menus | Settings size27 with one icon/no buttons, empty size9 Settings, Visibility icon-only, all five paged menus size27 without controls; Clan can omit overview and empty markers |
| Omit every supported control | Each Action is independently omitted in Visibility, VisiblePlayers and all five paged menus; no item/Action/ghost navigation remains |
| Layout flexibility and security | Existing sparse/reordered/high-slot mapping, detached status, inventory bounds/collision, persistence and full menu/security harnesses remain active |

The B verifier originally expected an assertion message; the actual expected failure was the earlier NoSuchElementException. Its verifier was corrected and B rerun successfully. A later test-import shortening error was corrected before the final canonical run. Neither temporary failure is reported as a final passing regression.

## Regression and repository parity

Final canonical regression (`scripts/run-harnesses.ps1 -Offline`): **149 discovered/executed/passed, 21,777 checks, zero failures, zero missing/invalid**. All 142 baseline entry points retained with exactly the same per-harness check counts; seven additions contribute 556 checks. SettingsCompositionHarness 176; VisibilityCompositionHarness 37; FriendCompositionHarness 45; ClanCompositionHarness 48; NavigatorCompositionHarness 37; QuestCompositionHarness 39; GuiAuthoringAuditHarness 174. Preservation is verified in `harness-preservation.json`.

Maven `test-compile` and final offline `clean package`: **BUILD SUCCESS**, Java 21.0.12.1 and IntelliJ IDEA 2026.2.2 bundled Maven with the existing local repository. Final clean package completed on 2026-10-08 at 20:58:01 +02:00 in 14.316 seconds. Existing Unsafe/deprecation fixture warnings remain; no compilation error. Final regression and build ran after restoring all countercheck mutations.

SHA-256 audit verifies all 332 existing production/resource files outside the 12 affected UI files are byte-identical to the initial operator state. Historical reports are byte-identical. Domain services, persistence/schema/configs, commands/permissions, module/reload wiring, presentation, economy/moderation, Invsee, Enderchest, Blackjack and Lobby production are unchanged. Only this new report is added under reports/.

Documentation: docs/DEVELOPER_GUIDE.md now explains composition and position in DEFAULT_LAYOUT, Move/Remove/Icon-only/Hide, optional lookups, independent behavior fixtures and unchanged technical exceptions. Old compatibility-alias editing guidance is removed. Historical reports retain their phase-specific facts.

Work remains on master. A separate local authoring-simplification commit will be created only after complete validation; its actual SHA is returned after creation. No push, merge, branch or history rewrite is performed. This task has no claimed manual client result; real production rendering/listener/service behavior is tested with isolated Bukkit fixtures.