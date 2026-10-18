# Daily Quest reset countdown and layout

## Baseline and scope

Inspected `master` at `7940d63ca5f5f998173178ed1f4ac63416cd2476` (`Merge Lobby Glide`). Both `3f687e15727d08667584b8f1acf8e9afac2a3d9d` and `bc36d996baba7adf4a63c54c98fc51cd4f0021d9` are ancestors of master, verified before branching. Created the requested `phase/daily-quest-reset-ui` in the same checkout. No worktree, rebase, automatic merge or push.

The initial working tree contained two accidentally pasted Git commands inside `SettingsHotbarSecurityHarness.java`. With explicit operator authorization, only the damaged Unsafe expression was restored to its existing HEAD value; an exact original copy remains in the ignored task directory. This leaves no unrelated test change in the feature diff. The first restricted baseline run encountered denied atomic file replacements in sandbox temporary directories; a repeated canonical run with adequate local file rights measured **176 discovered/executed/passed, 27,271 checks, 0 failures, 0 missing/invalid** before feature edits. Receipts: `dev-server/.vapeecore-dev/daily-quest-reset-ui/baseline-results.json` and `baseline-inventory.txt`.

The original scope covers DailyQuest presentation and a read-only schedule API. The operator subsequently explicitly requested a testing command under `/core quest`; this authorized addition is documented below. No quest NPC, Citizens, hologram, TextDisplay, placement system, streak, producer, reward, claim action, new root command, permission, framework or Phase 38. The added command is a nested admin action under the existing root and permission. Historical reports and operator defaults remain unchanged.

## Layout and existing behavior

The previous default was 54 slots; the new **current default example** is 36 slots/four rows, owned solely by `DailyQuestMenu.DEFAULT_LAYOUT`:

| Placement | Current default slot |
|---|---:|
| Empty state | 22 |
| Previous, when available | 27 |
| Page information, multiple pages only | 31 |
| Next, when available | 33 |
| Reset information | 34 |
| Close | 35 |

Clock and Close remain present on every normally rendered page, including empty content. The Clock is `Material.CLOCK`, with no glint or submenu. The existing adaptive mapping is untouched: 1 → 13; 2 → 11/15; 3 → 11/13/15; 4 → 10/12/14/16; 5 → 9/11/13/15/17. Capacity stays five; pages of six/seven assignments retain a one-/two-item adaptive remainder. Tests consume the owning mapping instead of maintaining a second copy of its slot table.

Conditional pagination, clamping, actual slot→quest-ID mapping and holder.rendered action maps remain intact. RESET_INFO is an explicitly inert listener case; LEFT/RIGHT and other protected click types do not synchronize, rerender, change assignments, persist, grant coins or open another menu. Drag protection remains. The existing optional legacy REFRESH override is not added to the production layout. The alternative test layout additionally places RESET_INFO elsewhere to preserve optional composition and layout-authority coverage.

## Reset calculation and display

`DailyQuestCycleResolver.nextReset(Instant, ZoneId, LocalTime)` returns the strictly future scheduled local-day boundary. It and `resolve` share one boundary method using `LocalDate.atTime(...).atZone(...)`. `DailyQuestService.nextReset(Instant)` reads the current immutable `DailyQuestConfig.State` (`zone`, `resetTime`) and delegates without assignment or persistence work.

This follows the existing resolver semantics: spring-gap local times shift forward by the transition gap; overlap uses the earlier offset once. At an exact reset, nextReset advances to the next local date. Days are never treated as fixed 24-hour intervals: real Berlin examples cover 23- and 25-hour intervals, as well as a 02:30 gap/overlap reset. UTC and system zone are also covered.

`ResetCountdownFormatter` renders nonnegative elapsed hours/minutes/seconds as `HH:mm:ss`, with no 24-hour modulo. Tests cover zero, one second, 00:59:59, 01:00:00, 05:54:50, 23:59:59, 24:00:00, 25:00:00, negative durations and 125 hours. Injected Instant suppliers avoid wall-clock timing assumptions.

The clock is a **scheduled boundary**, not an assignment-rotation guarantee. Existing Daily-Sync and REWARD_PENDING protections remain authoritative; ordinary selector, batching and reward behavior is unchanged. The explicit force reconciliation adds the separately documented transactional save path.

## Countdown lifecycle and security

One synchronous 20-tick repeating task belongs to DailyQuestMenu and starts only when its update subscription is active and a tracked view contains RESET_INFO. It iterates only the existing active registry, with UUID-specific player lookup. Each tick checks online/profile/permission, expected holder, owner UUID, exact bound inventory, exact active registry entry, current top and rendered reset placement.

Only a changed localized reset `UiItemSpec` replaces the Clock ItemStack; an unchanged second does not even replace the Clock. Quest items, other controls, page and runtime target/action maps remain untouched. Timer ticks never call syncPlayer or quest persistence. Ordinary committed-state and locale observers still rerender the current inventory through the existing path.

The last Close/Quit cancels the task; stale/offline/unloaded/revoked/forged/replaced/unbound views are pruned. Module shutdown and failed-enable cleanup already call menu.shutdown, which cancels work, removes observers and clears views. Reopen and multiple viewers cannot create duplicate simultaneous tasks. No new ReloadParticipant; existing DailyQuest reload still closes production views, and reopen reads the committed schedule/text snapshot. The clock holds no new Player or ItemStack references beyond the existing inventory registry; its cached specification is immutable presentation data.

## Localization and operator ownership

The Clock adds three EN/DE keys: `daily-quests.reset-title`, `daily-quests.reset-countdown`, `daily-quests.reset-description`. **933 EN keys, 933 DE keys**, complete key and placeholder parity. Default title/body are restrained gray/dark gray; only the numeric `<time>` is aqua. Safe Component placeholders use the existing Translations/MessageService pipeline. No concatenated MiniMessage input or new localization authority.

Per-viewer English/German, active locale-change rendering, live title/countdown override, missing live-description fallback and unchanged operator-language bytes are covered. Operator language/config files are never replaced by bundled resources. New resource keys fall back normally when absent from older live files. Reset schedule reload and rollback tests use the actual config and module reload plan; rejected prepare retains previous schedule/catalog.

## Validation

Focused selection from the final canonical run: **32 harnesses / 8,496 checks passed**, covering existing Quest, DailyQuest menu/config/service/cycle/lifecycle, localization, GUI layout/security/pagination and reload. Added DailyQuestResetHarness: **55** checks; DailyQuestCountdownHarness: **244** checks; DailyQuestForceHarness: **77** checks. CoreCommandHarness now has **80** checks, including explicit root dispatch/completion gates. Focused coverage also includes OnlineStaffTargetGuard and PermissionDescriptor. All existing harness entrypoints retained. Receipts: `focused-results.json` (final canonical subset) and `full-regression.log`. The last separate focused run correctly rejected the obsolete descriptor alias expectation; updating that inventory was required by the operator's alias removal. The final canonical run covers every focused entrypoint successfully; the earlier focused.log is retained as that pre-correction diagnostic, not a final PASS receipt.

Nine actual temporary production mutations were compiled and caught by behavior tests, restored byte-for-byte and recompiled, with a positive test after every restoration:

| Control | Mutated defect | Actual failing assertion |
|---|---|---|
| A | Ignore configured zone in nextReset | actual next boundary before Berlin 04:00 |
| B | Replace boundary with now + 86400 seconds | actual 25-hour Berlin autumn boundary (DST-only test) |
| C | Move RESET_INFO from 34 to 32 | production reset clock is exactly slot 34 |
| D | Make Clock click invoke open/refresh | clock click inert: LEFT |
| E | Remove exact active-inventory ownership validation | invalid view receives no clock write: replaced |
| F | Disable completed-item glint | committed completion gains glint automatically without reopening |
| G | Hardcode force count to two | exact configured assignment count 3 |
| H | Omit failed-force assignment rollback | failed force restores assignments, progress and prior cycle |
| I | Remove handler admin permission gate | permission denial precedes lookup, hierarchy and mutation |

All nine complete receipts, assertion output and restored source SHA-256 hashes are in `negative-results.json` and `negative-A.log` through `negative-I.log`. No mutation remains. The initial negative-control helper's Windows newline matching was corrected before completing the final nine-control receipt; it did not change production behavior.

Full canonical regression: **179 discovered/executed/passed, 27,715 checks, 0 failures, 0 missing/invalid**. All **176/176** baseline entrypoints retained; only the focused reset/countdown/force harnesses added. Receipts: `full-results.json`, `full-inventory.txt`, `full-regression.log`. The net 444-check increase comprises 376 checks in the three added harnesses and 68 net additional existing dispatch/audit/composition/parity checks. The final alias removal drops its single collision check and four catalog-key checks; no harness entrypoint or behavior coverage was removed.

Offline Maven **clean package: BUILD SUCCESS** (16.747 s), after the successful canonical full regression. Deployed with the existing project stop/build/atomic-copy sequence; the running project server was stopped cleanly before the final follow-up build/deployment. Target/deployed SHA-256 match: `283255C750B604FE88067620505250BCF09FFA2802456DB13185F7FC00F00019`. Receipt: `package.log`, `deployed-sha256.txt`. No production changes followed validation.

Real **Paper 1.21.11-132 smoke passed 33 checks**: native 36-slot chest, actual Clock/Barrier on 34/35, native ItemMeta lore from the actual active reset config, native scheduled Clock update, unchanged other item bytes and exact current inventory, inert native-dispatched clock click, permission denial and countdown cancellation, new schedule after actual `/core reload`, restored schedule after a second successful `/core reload`, Close cleanup and no quest assignment from countdown work. Native production quest-item rendering verifies glint only for COMPLETED, absent for ACTIVE/REWARD_PENDING/Clock, plus native ItemStack serialization round-trip of the glint with no actual enchantments. Native command registration additionally confirms /quests has no aliases and that /quest and /vapeecore:quest are absent. The actual Core executor/console dispatcher also exercises force root/nested completion, immediate configured selection, one native profile write with cycle and assignments, automatic exact-menu mapping update, and denied nonadmin execution leaving profile bytes unchanged. Clean shutdown exit 0; **0 VapeeCore/DailyResetProbe ERROR/SEVERE**. Actual counts: **28 modules, 7 reload participants per run (14 prepared across two successful reloads), 37 command roots, 50 permissions**.

The probe uses real Paper inventories/items/events/scheduler and the real core DailyQuest service/config/ReloadParticipant. Its isolated menu instance substitutes only virtual-player lookup, controlled time and a CURRENT preparation result so an empty isolated profile can render the view without assigning quests from the operator's catalog. For the force dispatch check, the temporary delegate substitutes only the virtual target lookup; the real Core dispatcher, DailyQuestService, QuestService, profile repository, staff guard, translations and menu observer are used. The original delegate is restored during cleanup. This is not a real Minecraft client or client visual acceptance. For the schedule test only, it temporarily changed the live reset to 04:00 Europe/Berlin, used the actual console reload, then restored exact original bytes and reloaded again. All **10 operator YAMLs plus 2 language files** remain hash-identical; the isolated profile was removed, subscriptions/tasks detached and probe JAR/remapped copy removed. Server remains stopped for the operator. Receipts: `paper-smoke.log`, `paper-receipt.json`.

## Operator feedback and completion glint

The operator confirmed that the layout looks good, reported seeing two quests after adding two further catalog entries, and requested an enchantment shimmer on completed quest items. Inspection found a valid four-entry live catalog with `quests-per-day: 3`; the saved current-day profile still contained its original two assignments (`play_30_minutes`, `win_3_blackjack`) in cycle 2026-10-18. This is the existing same-cycle preservation contract, not a parsing/layout failure. Reload must not reroll/reset ongoing progress. A new behavior regression exercises two initial assignments, an expanded four-entry catalog/count three, unchanged same-cycle state/view, and three new assignments after normal next-cycle rotation. Operator config and player files were not changed to force reassignment.

The operator-authorized follow-up adds `ItemMeta.setEnchantmentGlintOverride(true)` only to COMPLETED quest items in the existing feature-local item renderer. No actual enchantment, shared UI framework/spec change, new reward or assignment behavior. ACTIVE and REWARD_PENDING stay unglinted; existing material/color/lore remain intact. Tests cover committed completion, absence of optimistic visual success, pending retry, locale/reopen retention, countdown preserving the actual completed ItemStack, stable target mapping, and no extra reward. An additional actual temporary negative control F disabled the glint and failed the completed-item assertion; restoration passed all 13 glint-only checks. The final focused/full regressions and all nine negative controls include this operator follow-up; Paper validation is recorded in the validation section.

## Explicit operator force command

The latest operator clarification authorizes immediate testing of the currently configured daily count, rather than automatic reload changes or an instant-completion command. `/core quest force` targets the calling player; `/core quest force <player>` targets an exact case-insensitive online name. Console requires the target argument. This is a subcommand of the existing CoreCommand, not a new command root. Both dispatcher and handler enforce existing `vapeecore.admin`; other-player execution/completion uses the existing OnlineStaffTargetGuard, including fail-closed higher-staff/unknown-group policy. No offline Player/LP load. Help, nested completion and all nine new `core.quest.*`/help text keys use the existing EN/DE localization service.

After `/core reload`, force calls `DailyQuestService.forcePlayer` with the configured zone/reset and existing deterministic DailyQuestSelector. It applies exactly `min(quests-per-day, available definitions)` current-cycle IDs. Retained IDs keep immutable progress/status; newly selected IDs start ACTIVE at zero; nonselected IDs leave the active selection. It does not complete quests, issue Coins or rewind the configured time. Any pending reward blocks the operation without attempting payout or clearing its state. Ordinary same-cycle sync/reload stays unchanged.

`QuestService.reconcileAssignments` validates before mutation, snapshots the existing state, writes the full profile once, clears prior dirty tracking only after success, then notifies the existing committed-state observers. Save failure restores the assignment snapshot and retains prior dirty tracking; DailyQuestService restores the prior cycle, including uninitialized state. No optimistic GUI success, discarded pending reward or new YAML schema. Tests cover exact 2→3→4→5→6→7 counts, cap to catalog, retained completion/partial progress, no Coins, same active inventory/mappings, page clamp on shrink, normal reload unaffected, failed save/runtime+disk/cycle rollback, normal retry of prior dirty progress, pending block, invalid IDs, self/console/other target, exact names, permissions, staff protection, completion and safe localized names. `CoreCommandHarness` also exercises real nested dispatch and completion gates.

Final full regression and the 33-check native Paper probe include this explicit force addition. The operator subsequently reported the tested points as matching and explicitly confirmed correct multi-page placement/navigation for more than five assignments. Exact tested 3/4/5 counts and every individual retained-state/glint case were not separately enumerated.

## Client acceptance and Git

The operator confirmed the layout, decreasing hover countdown, quest completion and Coin payout, subsequently reported tested points as matching, and explicitly confirmed correct placement and next-page navigation with more than five assignments. The exact tested 3/4/5 counts and each retained-progress/glint case were not individually enumerated. Reset-plan reload and every info/Close click case were not separately confirmed in the latest feedback; automated/native coverage is recorded above.

The operator reports that English/German custom quest texts do not switch, identifying their own single-string YAML entries as the cause. Source inspection confirms this is the supported universal-literal behavior, not a translation of operator-authored strings. `name`/`description` already support per-language maps with required `en` fallback and optional `de`; the maintained guide now includes a direct example. No live operator text was overwritten. Full real-client DE/EN acceptance is therefore not claimed; automatic/native localization tests pass.

The operator requested only `/quests` for the player command. Removed the `/quest` alias from plugin.yml, removed its help hint and obsolete EN/DE hint keys, and updated command help, descriptor/lifecycle regressions, the existing canonical/namespaced executor cases, and both maintained command/permission documents. The nested admin command `/core quest force [player]` remains as requested. Alias removal takes effect with the new plugin at server startup.

Final Git deliverable: one local commit on `phase/daily-quest-reset-ui`, containing exactly the 28 files below, with subject `Add daily quest reset countdown and refine menu layout`. The full commit SHA is returned in the closing response after commit verification. No push or merge. No NPC or Phase 38 work.

## Changed files

- `docs/DEVELOPER_GUIDE.md`
- `docs/PERMISSIONS.md`
- `reports/daily-quest-reset-ui/DAILY_QUEST_RESET_UI.md`
- `src/main/java/dev/vapee/core/VapeeCore.java`
- `src/main/java/dev/vapee/core/command/CoreCommand.java`
- `src/main/java/dev/vapee/core/quest/PlayerQuestState.java`
- `src/main/java/dev/vapee/core/quest/QuestAssignmentResult.java`
- `src/main/java/dev/vapee/core/quest/QuestService.java`
- `src/main/java/dev/vapee/core/quest/daily/DailyQuestCycleResolver.java`
- `src/main/java/dev/vapee/core/quest/daily/DailyQuestService.java`
- `src/main/java/dev/vapee/core/quest/daily/PlayerDailyQuestState.java`
- `src/main/java/dev/vapee/core/quest/daily/command/CoreQuestCommand.java`
- `src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestInventoryHolder.java`
- `src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestMenu.java`
- `src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestMenuListener.java`
- `src/main/java/dev/vapee/core/quest/daily/menu/ResetCountdownFormatter.java`
- `src/main/resources/lang/de.yml`
- `src/main/resources/lang/en.yml`
- `src/main/resources/plugin.yml`
- `src/test/java/dev/vapee/core/command/CoreCommandHarness.java`
- `src/test/java/dev/vapee/core/permission/PermissionDescriptorHarness.java`
- `src/test/java/dev/vapee/core/quest/QuestLifecycleHarness.java`
- `src/test/java/dev/vapee/core/quest/daily/DailyQuestResetHarness.java`
- `src/test/java/dev/vapee/core/quest/daily/command/QuestCommandHarness.java`
- `src/test/java/dev/vapee/core/quest/daily/menu/DailyQuestCountdownHarness.java`
- `src/test/java/dev/vapee/core/quest/daily/menu/DailyQuestForceHarness.java`
- `src/test/java/dev/vapee/core/quest/daily/menu/QuestMenuFixture.java`
- `src/test/java/dev/vapee/core/ui/MenuTestLayouts.java`

Ignored test receipts and isolated native probe sources remain only under `dev-server/.vapeecore-dev/daily-quest-reset-ui`; no probe is shipped in the production JAR.
