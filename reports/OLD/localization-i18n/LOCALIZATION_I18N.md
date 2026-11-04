# English and German localization

## Pre-implementation baseline and translation inventory

Clean baseline: `fabc95ae251dcbec1ffe1b4a71eeaec75f0a21cf`, dedicated existing branch `phase/localization-i18n`. GUI cleanup and server-name color changes are committed. No worktree, rebase, push, merge or Phase 38 work. Fresh pre-edit canonical regression: 156 discovered/executed/passed harnesses, 22,301 checks, 0 failures, 0 missing/invalid. All were run against the clean current baseline.

Actual Paper dependency verified with javap: `Player.locale()` and `org.bukkit.event.player.PlayerLocaleChangeEvent.locale()` both return `java.util.Locale`. Deprecated String getters will not be used.

The repository-wide pre-edit search covers all main Java and resource files, including Component/UiItems/UiItemSpec, MiniMessage, send/kick/disallow, inventory titles, broadcasts, sidebar/tablist and game/world displays. Literal occurrence inventory, source search and hashes are stored in ignored `dev-server/.vapeecore-dev/localization-i18n/`. Candidate counts below measure lexical visible-prose candidates, including multiline diagnostic continuations; each migration family requires semantic review rather than blind replacement.

| Surface | Example source | Player-visible? | Localize? | Classification / reason |
| --- | --- | --- | --- | --- |
| Core status/version/reload/help | command/CoreCommand, command/help | Yes | Yes | PLUGIN_OWNED_VISIBLE: headings, help prose and feedback; command tokens unchanged |
| Settings and visibility | settings/*Menu*, *Listener*, SettingsMenuEntries | Yes | Yes | PLUGIN_OWNED_VISIBLE: labels, status, lore, empty state, errors |
| Friends and presence | friend/command, FriendMessages, gui, presence/FriendPresenceNotifier | Yes | Yes | PLUGIN_OWNED_VISIBLE: each recipient resolves its own locale |
| Clan | ClanMessages, command, gui | Yes | Yes | PLUGIN_OWNED_VISIBLE: labels/errors; USER_GENERATED names/tags literal |
| Ignore/social | social/command | Yes | Yes | PLUGIN_OWNED_VISIBLE: feedback; UUID/name identity unchanged |
| Lobby spawn/build/protection | lobby/command, utility/command, lobby/message | Yes | Yes where built-in | PLUGIN_OWNED_VISIBLE feedback; configurable join/quit wrappers OPERATOR_AUTHORED_VISIBLE |
| Lobby hotbar | lobby/config, item, resources/lobby.yml | Yes | Yes for fresh defaults and localized maps | OPERATOR_AUTHORED_VISIBLE: preserve scalar/list text; Java defaults become bilingual presentation |
| Navigator/Warp | lobby/experience/navigator, warp/command | Yes | Yes | PLUGIN_OWNED_VISIBLE controls/errors; OPERATOR_AUTHORED_VISIBLE destination names, TECHNICAL_IDENTIFIER IDs/worlds |
| Utility/Invsee/Enderchest | utility/*, command/OnlineStaffTargetGuard | Yes | Yes | PLUGIN_OWNED_VISIBLE usage feedback/title/labels; COMMAND_SYNTAX mode tokens/aliases unchanged |
| Economy/rewards | economy/command, onlinereward, reward | Yes when feedback exists | Yes | PLUGIN_OWNED_VISIBLE labels/reward notices; PERSISTENCE_VALUE balances and reward IDs unchanged |
| Rank/Profile | rank/command, identity/command | Yes | Yes | PLUGIN_OWNED_VISIBLE labels; EXTERNAL_METADATA LuckPerms rank display/prefix/suffix untouched |
| Moderation | moderation/command, ModerationComponents, login/chat listeners | Yes | Yes | PLUGIN_OWNED_VISIBLE feedback/duration/disconnect; USER_GENERATED staff reasons literal, LOGGER_ONLY audit entries remain English |
| Private messages | privatemessage/* | Yes | Yes | PLUGIN_OWNED_VISIBLE feedback/default wrappers; USER_GENERATED body untouched; operator scalar templates remain universal |
| Chat | chat/* | Yes | Limited to built-in system feedback | USER_GENERATED body; OPERATOR_AUTHORED_VISIBLE shared format stays shared; no renderer redesign |
| Blackjack | activity/blackjack/command, presentation | Yes | Yes for personal UI/feedback | PLUGIN_OWNED_VISIBLE labels; shared world-display language limitation must be explicit; IDs/coordinates technical |
| Quest/DailyQuest | quest/daily/menu, command, QuestProgressReporter | Yes | Yes | PLUGIN_OWNED_VISIBLE UI; OPERATOR_AUTHORED_VISIBLE quest name/description accept scalar or language map |
| Presentation/sidebar/tablist/nametag | presentation/*, resources/presentation.yml | Yes | Defaults/per-viewer fields where safe | OPERATOR_AUTHORED_VISIBLE formats, server brand, metadata; avoid changing shared/subject state into viewer text |
| Vanilla material/game mode/card concepts | UI/game render paths | Yes | Native translations or catalogs | EXTERNAL_METADATA/native Minecraft concepts where applicable; game state stays enums |
| Plugin descriptor | resources/plugin.yml | Yes in platform help | No per-player descriptor | COMMAND_SYNTAX/canonical English metadata; VapeeCore help prose uses catalog keys |
| Persistent snapshots/YAML serialization | player/repository, friend/clan/quest/moderation | Data | No | PERSISTENCE_VALUE stable IDs/enums and user content, never localized |
| Logger/exception diagnostics | services, modules, configs, repositories | Operator/developer | No | LOGGER_ONLY English; directly relayed exceptions need safe player-facing catalog feedback |
| Runtime keys/permissions/PDC/config paths | all features | Sometimes visible identifiers | No | TECHNICAL_IDENTIFIER/COMMAND_SYNTAX: never presentation authority |

### Config resource treatment decided before edits

| Resource family | Visible field/source | Scope | Treatment |
| --- | --- | --- | --- |
| config.yml | server.name, message-prefix | Operator brand/prefix | Preserve universal MiniMessage scalar; never translate brand automatically |
| lobby.yml | items.*.name/lore, filtered appearance | Individual recipient | Support additive en/de maps with scalar/list compatibility; fresh defaults bilingual; existing files never rewritten |
| lobby.yml | messages.join/quit.format | Broadcast wrapper | Preserve legacy scalar; where localized map is supplied render per recipient |
| chat.yml | format | Existing shared chat wrapper | Operator universal text; no translation of player body or architecture rewrite |
| private-messages.yml | outgoing/incoming templates | Sender/recipient independently | Support localized config values where clean; universal legacy scalar stays valid |
| presentation.yml | sidebar/tablist/nametag templates | Own sidebar vs subject/shared output | Preserve universal operator content; document limits and per-viewer decisions after renderer audit |
| daily-quests.yml | quests.*.name/description | Quest viewer | Bukkit-free localized value with old scalar constructors/parser compatibility; IDs/progress persistence unchanged |
| plugin.yml | descriptions/usage/permissions | Bukkit metadata | Canonical English; localized custom help independently |

### Source candidate counts before edits

| Feature | Hardcoded visible prose candidates before | Final treatment/catalog prefix |
| --- | ---: | --- |
| VapeeCore.java | 3 | Semantic feature/common keys; final audit will classify remaining candidates |
| activity | 293 | Semantic feature/common keys; final audit will classify remaining candidates |
| chat | 7 | Semantic feature/common keys; final audit will classify remaining candidates |
| clan | 168 | Semantic feature/common keys; final audit will classify remaining candidates |
| command | 88 | Semantic feature/common keys; final audit will classify remaining candidates |
| economy | 52 | Semantic feature/common keys; final audit will classify remaining candidates |
| format | 2 | Semantic feature/common keys; final audit will classify remaining candidates |
| friend | 116 | Semantic feature/common keys; final audit will classify remaining candidates |
| identity | 22 | Semantic feature/common keys; final audit will classify remaining candidates |
| lobby | 170 | Semantic feature/common keys; final audit will classify remaining candidates |
| moderation | 103 | Semantic feature/common keys; final audit will classify remaining candidates |
| module | 1 | Semantic feature/common keys; final audit will classify remaining candidates |
| onlinereward | 12 | Semantic feature/common keys; final audit will classify remaining candidates |
| permission | 8 | Semantic feature/common keys; final audit will classify remaining candidates |
| player | 60 | Semantic feature/common keys; final audit will classify remaining candidates |
| presence | 11 | Semantic feature/common keys; final audit will classify remaining candidates |
| presentation | 12 | Semantic feature/common keys; final audit will classify remaining candidates |
| privatemessage | 49 | Semantic feature/common keys; final audit will classify remaining candidates |
| quest | 79 | Semantic feature/common keys; final audit will classify remaining candidates |
| rank | 24 | Semantic feature/common keys; final audit will classify remaining candidates |
| reload | 4 | Semantic feature/common keys; final audit will classify remaining candidates |
| reward | 9 | Semantic feature/common keys; final audit will classify remaining candidates |
| seat | 5 | Semantic feature/common keys; final audit will classify remaining candidates |
| settings | 139 | Semantic feature/common keys; final audit will classify remaining candidates |
| social | 31 | Semantic feature/common keys; final audit will classify remaining candidates |
| utility | 159 | Semantic feature/common keys; final audit will classify remaining candidates |
| visibility | 9 | Semantic feature/common keys; final audit will classify remaining candidates |
| worlddisplay | 5 | Semantic feature/common keys; final audit will classify remaining candidates |

## Implementation and verification

Completed on 2026-10-13 on the existing `phase/localization-i18n` branch. The baseline GUI/server-name commits were not amended. No worktree, rebase, push, merge or Phase 38 work.

### Language authority, delivery and reload

`LanguageResolver` chooses German exactly when `Locale.getLanguage()` equals `de`; German regional variants work. Unsupported/null locales and non-player audiences use English. Production reads `Player.locale()` and `PlayerLocaleChangeEvent.locale()`. There is no persisted language field, language command or stale player-locale cache.

Bundled `lang/en.yml` and `lang/de.yml` each contain **901 UTF-8 keys**, with complete key and dynamic-placeholder parity. Live files are optional partial overrides: copy only if missing, never overwrite or run through ConfigEvolution. German lookup: live DE → bundled DE → live EN → bundled EN; English: live EN → bundled EN. A truly unknown key logs a development diagnostic and returns literal `[missing:key]`.

`LocalizationModule` starts first and owns one immutable snapshot containing both languages. Prepare reads/validates both candidates without publication; apply swaps the whole snapshot; rollback restores the exact previous snapshot. Malformed YAML, duplicate keys, invalid/null/blank values and placeholder mismatches are rejected. Existing normal MiniMessage semantics retain implicit style closing and can treat unknown tags literally; no new strict template language was introduced.

`Translations.tr` carries named literal Adventure Components in a locale-independent template. Existing `MessageService` remains the delivery/prefix authority and resolves the actual recipient. `sendRaw` uses that same boundary for complete PM/join/quit wrappers. `<i18n:key>` is an explicit presentation reference, never a command/action router. No reflection, scripting or duplicate messenger was added.

### Runtime surfaces and configuration

- All seven normal menus localize titles, names, status, lore, controls and empty states per owner. Locale events rerender current items through existing active registries without replacing inventory/holder/owner/page/target bindings. Native titles update on next open. The new event locale is scoped only during synchronous callbacks, since Paper may update its getter afterward. Observers are removed on shutdown. No polling task was added.
- Core/help, Settings/Visibility feedback, Friend/Presence, Clan, Ignore/Social, Spawn/Warp/Navigator, Utility/Invsee, Coins/online rewards, Rank/Profile, moderation/disconnect, private messages, Blackjack personal hotbar/setup/game feedback and Quest feedback use semantic catalog keys. Gameplay, save order, authorization and permissions remain in their original owners.
- Friend/Clan/Presence and Blackjack notices resolve per recipient. Localized Lobby join/quit maps are delivered individually with English console output; legacy scalar formats retain the original universal event path. PM selects wrappers independently for sender and recipient while preserving the exact body.
- `LocalizedText` is an immutable Bukkit-free presentation value. Quest names/descriptions accept legacy scalars or `en/de` maps with required English fallback and render as plain literal text. IDs, progress keys, assignments, rewards and persistence remain unchanged. Completion/retry feedback chooses the localized quest name at the reporter application boundary after domain commit.
- Generic Lobby names/lore (including filtered appearance), PM outgoing/incoming and join/quit formats accept additive language maps. No feature-specific parser branch was added. Java registrations/PDC/UUIDs route actions. The actual managed Friends head preserves configured slot, cached own skin and no duplicates during locale changes.
- Personal sidebar/header/footer resolve explicit catalog references. Fresh defaults use those references; existing operator scalars/lists remain universal. PM `<outgoing>/<incoming>` and reward `<reward>` provide complete catalog wrappers. Existing operator text is preserved. No operator/persistence schema version was increased merely for text interpretation.

### Explicit exclusions and shared-state limits

| Remaining family | Reason |
| --- | --- |
| Logger/exception/validation/cleanup diagnostics | English developer/operator output; player-facing failures use catalog templates. Blackjack diagnostic strings are technical adapter inputs, not raw player output. |
| Module/reload names, permission nodes, config/PDC keys, UUIDs, enum/record IDs | Stable technical identities; Core status can display them verbatim. |
| Command roots, aliases, syntax/argument/duration tokens | Parsing/completion authority remains unchanged; surrounding help and feedback are translated. |
| `plugin.yml` descriptions | Static canonical English platform metadata; custom VapeeCore help is localized. |
| Chat/PM bodies, player names, Clan name/tag, moderation reason | User-generated literal values; never automatically translated or evaluated as markup. |
| Warp display names, world names, server brand/prefix and operator custom text | Operator authority; legacy strings/lists remain universal. Maps/references explicitly opt into localization. |
| LuckPerms rank name/description/prefix/suffix/group | External/operator metadata, not translated behavioral identity. |
| Numbers, card ranks/suits/glyphs, UTC timestamps, compact duration units | Stable numeric/domain notation; surrounding captions are localized. |
| `VapeeCore` and `Blackjack` proper names | Brand/domain proper nouns remain unchanged. |
| Shared native team/tablist-name state and Blackjack/preview TextDisplays | One common native text state; own catalog captions render in English. No per-viewer packet/render framework was introduced. Both catalogs still own the captions. |
| Global chat wrapper | Existing shared operator format; body untouched; no chat architecture redesign. |
| Early ban login rejection | English before reliable client locale; online disconnect/warning/mute feedback uses target locale. |

No unexplained hardcoded normal runtime prose remains. Remaining English lexical candidates are diagnostics, technical matching/syntax, proper names, metadata or universal operator templates. Optional Java `REFRESH` and its caption remain for explicit non-default layouts/tests; none of the seven production defaults gains a manual Refresh button.

### Source audit

Before: **1,641 lexical visible-prose candidates** across all feature families. **1,112 initial candidate occurrences** disappeared from their original Java files. After: **551 residual candidates**, reviewed under the exclusions above. These counts include fragments and multiline diagnostic continuations: they are not distinct-message counts or a claim that every lexical candidate was player-facing. Templates consolidate fragments and reorder safe arguments.

Post-edit searches covered every main Java/resource file: multiline `Component.text`, literal UiItems/list lore, inventory creation, kick/disallow, send/MessageService, broadcasts and config fallbacks. The final search exposed and corrected Rank/Ranks/You headings and Quest completion/retry feedback. `CatalogUsageHarness` renders every key/placeholder safely in both languages and checks Java/resource references and actual ActivityState/ModerationAction captions. All **27 existing historical report files** retain their initial SHA-256 bytes.

### Security and negative controls

Injection-shaped names/message/reason/quest values remain literal and cannot introduce color/click actions. UUID/name ties, a translated `Schließen` display collision, current inventory ownership, stale/forged holders, rendered controls, PDC identity, page clamping, permissions and save rollback remain checked. The productive Settings constructor now delegates through the same localized constructor/render path as fixtures, fixing the discovered path inconsistency.

| Control | Actual evidence |
| --- | --- |
| A: missing bundled German fixture key | English fallback verified |
| B: missing live German key | Bundled DE wins over live EN override |
| C: malformed German reload | Candidate rejected; both active catalogs unchanged, also through actual coordinator |
| D: missing/changed placeholder | Candidate validation fails as expected |
| E: MiniMessage injection | Literal values; no injected click/color; GUI/PM/Quest/broadcast paths covered |
| F: Japanese locale | English fallback, including PM operator maps |
| G: wrong broadcast locale | Actual source mutation rendered one subject-locale component for everyone; two-locale test failed at English-recipient assertion |
| H: translated target identity | Actual listener mutation derived UUID from localized heading; existing FriendMenuSecurityHarness failed at authorized action 0, and translated-display collision test failed |

G/H files were restored byte-for-byte in `finally`, rebuilt, and all restored controls passed before full final regression. The mutations are absent from final source. Source hashes did not change during final validation.

### Validation and runtime evidence

- Fresh baseline: **156/156 harnesses, 22,301 checks**, 0 failures, 0 missing/invalid.
- Passing focused receipts: **48 harnesses, 12,313 checks**. Includes all seven simultaneous DE/EN menus, active locale switches/bindings, actual hotbar/PDC/skin, independent PM wrappers, Quest names/committed rewards, personal sidebar/header/footer, target-locale moderation, console/broadcasts, catalog usage and real reload rollback.
- Final canonical offline regression: **Discovered 168, Executed 168, Passed 168, Failed 0, Missing/Invalid 0, TotalChecks 26,686**. All 156 baseline entry points retained; 12 new executable language harnesses added. Intermediate failures were repaired, not reported as passes.
- Requested offline `clean package`: **BUILD SUCCESS**. Final JAR contains both complete catalogs and is deployed to the local dev server.
- Two real Paper smokes: **Paper 1.21.11-132-c5eb079, Java 21.0.12.1, LuckPerms 5.5.84**. Startup, real console status/version, successful `/core reload`, reverse shutdown, exit 0 and **0 VapeeCore ERROR/SEVERE**. Nine existing root YAML files retain exact bytes on both starts. First start copied missing language files; second start preserved both existing language files byte-for-byte.
- Actual runtime: **28 VapeeCore modules, 7 prepared reload participants, 37 registered/bound own command roots, 50 registered descriptor permission nodes**. Commands/permissions were inspected by a temporary read-only native Paper probe after enable. Its JAR and remapped copy were removed; it is not shipped with VapeeCore.
- **Manual Minecraft client language test not performed.** Smokes and fixture APIs do not establish visual client acceptance. Follow-up: connect DE/EN clients simultaneously, compare seven menus and feedback, change language while open, reopen for native title, verify self-skin Friends head and PM/Quest/reward/online moderation notices. Shared displays follow the documented English limitation.

Receipts/logs and detailed literal inventories are retained in ignored `dev-server/.vapeecore-dev/localization-i18n/`. Counts are actual current results, not old phase claims.

### Final Git workflow

One local commit: `Add English and German localization`, based on `fabc95ae251dcbec1ffe1b4a71eeaec75f0a21cf`. The actual SHA is returned after commit; this report belongs to that commit and avoids embedding a self-referential hash. Working tree is checked afterward. Push: no. Merge: no. Phase 38: not started. Paper is cleanly stopped; the final tested plugin remains deployed for the next client test.

### Source candidate counts after audit

| Feature | Before candidates | After candidates (reviewed exclusions) | Catalog / treatment |
| --- | ---: | ---: | --- |
| VapeeCore.java | 3 | 3 | Technical/diagnostic/operator metadata; no own runtime prose |
| activity | 293 | 119 | blackjack.* |
| chat | 7 | 7 | Technical/diagnostic/operator metadata; no own runtime prose |
| clan | 168 | 42 | clan.* / clans.* |
| command | 88 | 2 | core.* / common.* |
| economy | 52 | 12 | economy.* |
| format | 2 | 2 | Technical/diagnostic/operator metadata; no own runtime prose |
| friend | 116 | 38 | friends.* |
| i18n | 0 | 1 | Technical/diagnostic/operator metadata; no own runtime prose |
| identity | 22 | 4 | profile.* |
| lobby | 170 | 75 | lobby.* / spawn.* / warp.* / navigator.* |
| moderation | 103 | 29 | moderation.* |
| module | 1 | 1 | Technical/diagnostic/operator metadata; no own runtime prose |
| onlinereward | 12 | 10 | rewards.online.* |
| permission | 8 | 8 | Technical/diagnostic/operator metadata; no own runtime prose |
| player | 60 | 58 | Technical/diagnostic/operator metadata; no own runtime prose |
| presence | 11 | 8 | friends.presence.* |
| presentation | 12 | 12 | presentation.* |
| privatemessage | 49 | 15 | private-messages.* |
| quest | 79 | 47 | quests.* / daily-quests.* |
| rank | 24 | 5 | rank.* / ranks.* |
| reload | 4 | 4 | Technical/diagnostic/operator metadata; no own runtime prose |
| reward | 9 | 9 | Technical/diagnostic/operator metadata; no own runtime prose |
| seat | 5 | 5 | Technical/diagnostic/operator metadata; no own runtime prose |
| settings | 139 | 12 | settings.* / visibility.* / visible-players.* |
| social | 31 | 4 | social.* |
| utility | 159 | 8 | utility.* / common.* |
| visibility | 9 | 6 | visibility.* / lobby.visibility.* |
| worlddisplay | 5 | 5 | Technical/diagnostic/operator metadata; no own runtime prose |

### Exact changed files

167 task files, including this report; no historical report/operator file is committed.

- `README.md`
- `docs/DEVELOPER_GUIDE.md`
- `docs/FORMATTING.md`
- `reports/localization-i18n/LOCALIZATION_I18N.md`
- `src/main/java/dev/vapee/core/VapeeCore.java`
- `src/main/java/dev/vapee/core/activity/blackjack/BlackjackModule.java`
- `src/main/java/dev/vapee/core/activity/blackjack/BlackjackService.java`
- `src/main/java/dev/vapee/core/activity/blackjack/command/BlackjackCommand.java`
- `src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackInventoryService.java`
- `src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackPreviewService.java`
- `src/main/java/dev/vapee/core/activity/blackjack/presentation/BlackjackWorldViewService.java`
- `src/main/java/dev/vapee/core/clan/ClanMessages.java`
- `src/main/java/dev/vapee/core/clan/command/ClanCommand.java`
- `src/main/java/dev/vapee/core/clan/gui/ClanMenu.java`
- `src/main/java/dev/vapee/core/clan/gui/ClanMenuListener.java`
- `src/main/java/dev/vapee/core/command/CoreCommand.java`
- `src/main/java/dev/vapee/core/command/OnlineStaffTargetGuard.java`
- `src/main/java/dev/vapee/core/command/help/CommandHelpEntry.java`
- `src/main/java/dev/vapee/core/command/help/CommandHelpPage.java`
- `src/main/java/dev/vapee/core/command/help/CommandHelpRenderer.java`
- `src/main/java/dev/vapee/core/command/help/CommandHelpSection.java`
- `src/main/java/dev/vapee/core/economy/command/CoinsCommand.java`
- `src/main/java/dev/vapee/core/friend/FriendMessages.java`
- `src/main/java/dev/vapee/core/friend/command/FriendCommand.java`
- `src/main/java/dev/vapee/core/friend/gui/FriendMenu.java`
- `src/main/java/dev/vapee/core/friend/gui/FriendMenuListener.java`
- `src/main/java/dev/vapee/core/i18n/Language.java`
- `src/main/java/dev/vapee/core/i18n/LanguageResolver.java`
- `src/main/java/dev/vapee/core/i18n/LocalizationModule.java`
- `src/main/java/dev/vapee/core/i18n/LocalizationService.java`
- `src/main/java/dev/vapee/core/i18n/LocalizedText.java`
- `src/main/java/dev/vapee/core/i18n/TranslationCatalog.java`
- `src/main/java/dev/vapee/core/i18n/Translations.java`
- `src/main/java/dev/vapee/core/identity/command/ProfileCommand.java`
- `src/main/java/dev/vapee/core/lobby/LobbyModule.java`
- `src/main/java/dev/vapee/core/lobby/command/SetSpawnCommand.java`
- `src/main/java/dev/vapee/core/lobby/command/SpawnCommand.java`
- `src/main/java/dev/vapee/core/lobby/config/LobbyConfig.java`
- `src/main/java/dev/vapee/core/lobby/config/LobbyItemsConfig.java`
- `src/main/java/dev/vapee/core/lobby/experience/LobbyExperienceListener.java`
- `src/main/java/dev/vapee/core/lobby/experience/LobbyExperienceModule.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorListener.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenu.java`
- `src/main/java/dev/vapee/core/lobby/item/LobbyItemDefinition.java`
- `src/main/java/dev/vapee/core/lobby/item/LobbyItemRegistrations.java`
- `src/main/java/dev/vapee/core/lobby/item/LobbyItemService.java`
- `src/main/java/dev/vapee/core/lobby/message/LobbyMessageService.java`
- `src/main/java/dev/vapee/core/lobby/warp/command/WarpCommand.java`
- `src/main/java/dev/vapee/core/message/MessageService.java`
- `src/main/java/dev/vapee/core/moderation/ModerationComponents.java`
- `src/main/java/dev/vapee/core/moderation/ModerationLoginListener.java`
- `src/main/java/dev/vapee/core/moderation/ModerationModule.java`
- `src/main/java/dev/vapee/core/moderation/command/AbstractModerationCommand.java`
- `src/main/java/dev/vapee/core/moderation/command/BanCommand.java`
- `src/main/java/dev/vapee/core/moderation/command/HistoryCommand.java`
- `src/main/java/dev/vapee/core/moderation/command/KickCommand.java`
- `src/main/java/dev/vapee/core/moderation/command/ModerationCommandContext.java`
- `src/main/java/dev/vapee/core/moderation/command/MuteCommand.java`
- `src/main/java/dev/vapee/core/moderation/command/UnbanCommand.java`
- `src/main/java/dev/vapee/core/moderation/command/UnmuteCommand.java`
- `src/main/java/dev/vapee/core/moderation/command/WarnCommand.java`
- `src/main/java/dev/vapee/core/onlinereward/OnlineRewardConfig.java`
- `src/main/java/dev/vapee/core/onlinereward/OnlineRewardMessageRenderer.java`
- `src/main/java/dev/vapee/core/player/PlayerListener.java`
- `src/main/java/dev/vapee/core/presence/FriendPresenceNotifier.java`
- `src/main/java/dev/vapee/core/presentation/PresentationModule.java`
- `src/main/java/dev/vapee/core/presentation/PresentationRenderer.java`
- `src/main/java/dev/vapee/core/presentation/config/PresentationConfig.java`
- `src/main/java/dev/vapee/core/privatemessage/PrivateMessageService.java`
- `src/main/java/dev/vapee/core/privatemessage/command/MessageCommand.java`
- `src/main/java/dev/vapee/core/privatemessage/command/ReplyCommand.java`
- `src/main/java/dev/vapee/core/privatemessage/config/PrivateMessageConfig.java`
- `src/main/java/dev/vapee/core/quest/QuestDefinition.java`
- `src/main/java/dev/vapee/core/quest/QuestProgressReporter.java`
- `src/main/java/dev/vapee/core/quest/daily/DailyQuestConfig.java`
- `src/main/java/dev/vapee/core/quest/daily/command/DailyQuestCommand.java`
- `src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestMenu.java`
- `src/main/java/dev/vapee/core/rank/command/RankCommand.java`
- `src/main/java/dev/vapee/core/rank/command/RanksCommand.java`
- `src/main/java/dev/vapee/core/settings/SettingsListener.java`
- `src/main/java/dev/vapee/core/settings/SettingsMenu.java`
- `src/main/java/dev/vapee/core/settings/SettingsMenuEntries.java`
- `src/main/java/dev/vapee/core/settings/command/SettingsCommand.java`
- `src/main/java/dev/vapee/core/settings/visibility/VisibilitySettingsListener.java`
- `src/main/java/dev/vapee/core/settings/visibility/VisibilitySettingsMenu.java`
- `src/main/java/dev/vapee/core/settings/visibility/VisiblePlayersListener.java`
- `src/main/java/dev/vapee/core/settings/visibility/VisiblePlayersMenu.java`
- `src/main/java/dev/vapee/core/social/command/IgnoreCommand.java`
- `src/main/java/dev/vapee/core/social/command/IgnoreListCommand.java`
- `src/main/java/dev/vapee/core/social/command/UnignoreCommand.java`
- `src/main/java/dev/vapee/core/ui/UiItems.java`
- `src/main/java/dev/vapee/core/utility/InvseeService.java`
- `src/main/java/dev/vapee/core/utility/UtilityModule.java`
- `src/main/java/dev/vapee/core/utility/command/BuildCommand.java`
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
- `src/main/java/dev/vapee/core/utility/teleport/TeleportParseException.java`
- `src/main/java/dev/vapee/core/utility/teleport/TeleportParser.java`
- `src/main/java/dev/vapee/core/visibility/VisibilityLobbyItemAction.java`
- `src/main/resources/config.yml`
- `src/main/resources/lang/de.yml`
- `src/main/resources/lang/en.yml`
- `src/main/resources/lobby.yml`
- `src/main/resources/presentation.yml`
- `src/main/resources/private-messages.yml`
- `src/test/java/dev/vapee/core/activity/blackjack/BlackjackHarness.java`
- `src/test/java/dev/vapee/core/activity/blackjack/BlackjackPresentationHarness.java`
- `src/test/java/dev/vapee/core/activity/blackjack/presentation/BlackjackPreviewHarness.java`
- `src/test/java/dev/vapee/core/activity/blackjack/table/BlackjackTableHarness.java`
- `src/test/java/dev/vapee/core/clan/ClanIntegrationHarness.java`
- `src/test/java/dev/vapee/core/clan/gui/ClanLocalizationHarness.java`
- `src/test/java/dev/vapee/core/clan/gui/ClanMenuFixture.java`
- `src/test/java/dev/vapee/core/config/ConfigServiceHarness.java`
- `src/test/java/dev/vapee/core/economy/EconomyIntegrationHarness.java`
- `src/test/java/dev/vapee/core/economy/command/CoinsCommandHarness.java`
- `src/test/java/dev/vapee/core/friend/FriendLifecycleHarness.java`
- `src/test/java/dev/vapee/core/friend/gui/FriendLocalizationHarness.java`
- `src/test/java/dev/vapee/core/friend/gui/FriendMenuFixture.java`
- `src/test/java/dev/vapee/core/friend/gui/FriendsLobbyItemHarness.java`
- `src/test/java/dev/vapee/core/i18n/CatalogUsageHarness.java`
- `src/test/java/dev/vapee/core/i18n/I18nTestSupport.java`
- `src/test/java/dev/vapee/core/i18n/LocalizationHarness.java`
- `src/test/java/dev/vapee/core/i18n/LocalizationReloadHarness.java`
- `src/test/java/dev/vapee/core/i18n/LocalizationSurfacesHarness.java`
- `src/test/java/dev/vapee/core/identity/IdentityHarness.java`
- `src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorFixture.java`
- `src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorLocalizationHarness.java`
- `src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenuHarness.java`
- `src/test/java/dev/vapee/core/lobby/item/LobbyItemsConfigHarness.java`
- `src/test/java/dev/vapee/core/message/CommandHelpHarness.java`
- `src/test/java/dev/vapee/core/moderation/ModerationLifecycleHarness.java`
- `src/test/java/dev/vapee/core/moderation/ModerationTestSupport.java`
- `src/test/java/dev/vapee/core/moderation/command/ModerationCommandHarness.java`
- `src/test/java/dev/vapee/core/onlinereward/OnlineRewardLifecycleHarness.java`
- `src/test/java/dev/vapee/core/permission/PermissionDescriptorHarness.java`
- `src/test/java/dev/vapee/core/presence/FriendPresenceNotifierHarness.java`
- `src/test/java/dev/vapee/core/presence/PresenceLifecycleHarness.java`
- `src/test/java/dev/vapee/core/presentation/PresentationFixture.java`
- `src/test/java/dev/vapee/core/presentation/PresentationHarness.java`
- `src/test/java/dev/vapee/core/privatemessage/PrivateMessageSocialHarness.java`
- `src/test/java/dev/vapee/core/quest/QuestFeedbackLocalizationHarness.java`
- `src/test/java/dev/vapee/core/quest/QuestLifecycleHarness.java`
- `src/test/java/dev/vapee/core/quest/QuestProgressReporterHarness.java`
- `src/test/java/dev/vapee/core/quest/daily/DailyQuestLifecycleHarness.java`
- `src/test/java/dev/vapee/core/quest/daily/menu/QuestLocalizationHarness.java`
- `src/test/java/dev/vapee/core/quest/daily/menu/QuestMenuFixture.java`
- `src/test/java/dev/vapee/core/reward/RewardLifecycleHarness.java`
- `src/test/java/dev/vapee/core/settings/SettingsLocalizationHarness.java`
- `src/test/java/dev/vapee/core/settings/SettingsMenuFixture.java`
- `src/test/java/dev/vapee/core/settings/visibility/VisibilityLocalizationHarness.java`
- `src/test/java/dev/vapee/core/settings/visibility/VisibilityMenuFixture.java`
- `src/test/java/dev/vapee/core/settings/visibility/VisibilitySettingsMenuHarness.java`
- `src/test/java/dev/vapee/core/settings/visibility/VisiblePlayersLocalizationHarness.java`
- `src/test/java/dev/vapee/core/ui/UiItemsHarness.java`
- `src/test/java/dev/vapee/core/utility/command/BuildCommandHarness.java`
- `src/test/java/dev/vapee/core/utility/command/TeleportCommandHarness.java`
- `src/test/java/dev/vapee/core/utility/command/UtilityCommandHarness.java`
- `src/test/java/dev/vapee/core/visibility/VisibilityModuleHarness.java`
