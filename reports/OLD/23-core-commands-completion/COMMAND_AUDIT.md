# Phase 23 – Core Commands Completion

## Audit vor Produktionsänderungen

Baseline: `be43a4addc46b74d5a7c88118830b065124edfb3`, Branch `phase/23-core-commands-completion`; Arbeitsstand zu Beginn sauber. Ein Worker. Alle Command-Klassen, zugehörigen Module, geforderten Services und bestehenden Command-Harnesses wurden vor dieser Matrix gelesen.

Permissions in der Tabelle sind relativ zu `vapeecore.`. `utility.<command>.others` ist die bestehende Others-Permission, kein neuer Node. P = Player, C = Console. Namen werden vollständig und case-insensitive aufgelöst; UUID/Known Player nur in den bereits dafür vorgesehenen Domains.

| Command / Alias | Current syntax | Permission | P / C | Self / Other | Target type | Current problems | Change needed | Reason |
|---|---|---|---|---|---|---|---|---|
| core / vapeecore | `[help\|version\|reload]` | öffentlich; reload `admin` | P+C | Status / kein Target | – | zentrale Hilfe unvollständig; zusätzliche Argumente nur generische Usage | YES | aktuelle Übersicht und konkrete Subcommand-Usage; Reload unverändert |
| spawn | `/spawn` | `lobby.spawn` | P | Self | – | Executor verlässt sich auf plugin.yml; abgebrochener Teleport nur unavailable | YES | explizite Permission und kontrolliertes Failed/Cancelled-Feedback |
| setspawn | `/setspawn` | `lobby.setspawn` | P | Self location | – | keine Executor-Permission; Save-Fehler wird erneut geworfen | YES | SEVERE mit UUID, kontrolliertes Feedback ohne Rethrow |
| coins | `[help\|get <player\|uuid>\|add/remove/set <player\|uuid> <amount>]` | `economy.coins`, Admin `economy.admin` | P; C explizite Admin-Form | Self / Other | known name / UUID; Mutation loaded online | keine konkrete Lücke | NO | Phase-22-Regeln, Persistence und 208 Command-Checks erhalten |
| profile | `[player\|uuid]` | `profile.view` | P; C Target | Self / Other | known name / UUID | keine konkrete Lücke | NO | Offline-Coins, Ambiguity und unavailable Rank/Playtime korrekt |
| rank | `[player]` | `rank.view` | P; C Target | Self / Other | online name | Executor-Permission fehlt; Wiring getPlayerExact statt eigener eindeutiger Resolution | YES | Permission und bestehender OnlinePlayerResolver |
| ranks | `/ranks` | `ranks.view` | P+C | kein Target | – | Executor-Permission fehlt | YES | Defense in depth; Track/Marker unverändert |
| friend / friends | `[help\|list\|requests\|add/accept/deny/cancel/remove <player\|uuid>]` | `friend.use` | P | eigene Beziehungen | known name / UUID | fehlende Zielauswahl/konkrete Usage; Request ohne Aktionen; Completion verliert unbekannte IDs und verwendet mehrdeutige Namen | YES | gezielte Text-UX, ausschließlich Suggest-Aktionen und sichere UUID-Fallbacks |
| clan / clans | `[help\|create <tag> <name...>\|info [tag\|uuid]\|invites\|invite/cancel/kick/transfer <player\|uuid>\|accept/deny <tag\|uuid>\|leave\|rename <name...>\|tag <tag>\|disband confirm]` | `clan.use` | P | eigene Clan-Rolle | known player / clan tag / UUID | keine konkrete Lücke | NO | bestehende Rollen-, Persistenz- und Text-UX-Tests erhalten |
| msg | `<player> <message...>` | `message.use` | P | Other | online name | Executor/Tab-Permission fehlt; getPlayerExact | YES | explizite Permission und vollständige eindeutige Online-Namen |
| reply / r | `<message...>` | `message.use` | P | letzter Partner | online UUID aus Session | Executor-Permission fehlt | YES | Alias und sämtliche Policy-/Session-Guards erhalten |
| ignore | `<player>` | `social.ignore` | P | Other | online name | Executor/Tab-Permission fehlt; getPlayerExact | YES | explizite Permission und OnlinePlayerResolver |
| unignore | `<player\|uuid>` | `social.ignore` | P | eigene Ignore-Liste | ignored known name / UUID | findet bei Namenskollision ersten Treffer; Permission fehlt; Completion ohne UUID-Fallback | YES | Mehrdeutigkeit ablehnen; eindeutige Namen oder UUIDs |
| ignorelist | `/ignorelist` | `social.ignore` | P | eigene Ignore-Liste | gespeicherte UUIDs | Executor-Permission fehlt; leere Liste nur Count | YES | klare Empty-State-Antwort; deterministische Reihenfolge erhalten |
| settings | `[visibility [add/remove <player\|uuid>]]` | `settings.use` | P | eigene Settings | known name / UUID | keine konkrete Lücke | NO | bestehende GUI-, Visibility-, Save- und UUID-Regeln erhalten |
| build | `/build` | `utility.build` | P | Self | – | keine konkrete Lücke | NO | Lobby/Activity/Inventory/Flight-Ownership korrekt |
| fly | `[player]` | `utility.fly[.others]` | P; C Target | UUID Self / Other | online name | keine konkrete Lücke | NO | BUILD, Activity und native Flight-Guards erhalten |
| speed | `<1-10> [player]` | `utility.speed[.others]` | P; C Target | UUID Self / Other | online name | keine konkrete Lücke | NO | Integer-Level, Walk/Fly-Kontext und Activity-Guard korrekt |
| gamemode / gm | `<mode> [player]` | `utility.gamemode[.others]` | P; C Target | UUID Self / Other | online name | keine konkrete Lücke | NO | alle vorhandenen Aliase und BUILD/Activity-Guards korrekt |
| tp / teleport | `<target>` / `<x> <y> <z> [yaw pitch]`, optional `<source>` und `world <world>` | `utility.teleport`, `.others`, `.world`, `.others.world`; `.bypass` | P; C Source | Form bestimmt Self / named Source | online name / coordinates / loaded world | keine konkrete Lücke | NO | Phase-18C-Regressionsbereich; Source-Form behält Others-Recht auch bei Self-Name |
| tphere | `<player>` | `utility.teleport.here`; `.bypass` | P | Other → Self | online name | keine konkrete Lücke | NO | Self-Ablehnung, beide Activity-Guards und Cancellation korrekt |
| heal | `[player]` | `utility.heal[.others]` | P; C Target | UUID Self / Other | online name | keine konkrete Lücke | NO | Health/Fire/Freeze ohne Potion-Cleanse korrekt |
| feed | `[player]` | `utility.feed[.others]` | P; C Target | UUID Self / Other | online name | keine konkrete Lücke | NO | bestehende Hunger/Saturation/Exhaustion-Regeln korrekt |
| ping | `[player]` | `utility.ping[.others]` | P; C Target | UUID Self / Other | online name | keine konkrete Lücke | NO | echte ms, keine erfundene Bewertung |
| clear | `[player]` | `utility.clear[.others]` | P; C Target | UUID Self / Other | online name | keine konkrete Lücke | NO | Inventory-Gesamtlöschung nur nach Activity/BUILD-Guards |
| invsee | `<player>` | `utility.invsee`; `.modify` reserviert/inaktiv | P Viewer | beliebiger online Owner | online name | keine konkrete Lücke | NO | Read-only Snapshot und Inventarschutz bleiben unverändert |
| enderchest | `[player]` | `utility.enderchest[.others]` | P Viewer | UUID Self / Other | online name | keine konkrete Lücke | NO | live online Chest, kein Offline-/Console-GUI |
| warp | `[help\|set/remove/info <id>\|list\|name <id> <name...>\|icon <id> <material>]` | `warp.admin` | P+C; set P | konfigurierte Warps | validated ID | keine konkrete Lücke | NO | Permission, Arity, literal Text und atomare Persistence vorhanden |
| blackjack | `[help\|setup [help\|create/delete/pos1/pos2/dealer/interaction/display/preview/enable/disable/info <id>\|seat/removeseat <id> <1-5>\|list]]` | `blackjack.admin` | P+C; World Capture/Preview P | konfigurierte Tische | validated ID | Completion liefert IDs auch für unbekannte Setup-Aktion | YES | kleine lokale Begrenzung auf bekannte Aktionen; Gameplay/Persistence unverändert |

### Verifikation und Grenzen

Bestehende Harnesses bleiben ausführbar; fehlende Command-Grenzfälle werden gezielt ergänzt. `plugin.yml`-Aliases und Permission-Children sind konsistent, neue Nodes sind nicht nötig. Keine Services, Config-Defaults, Persistence-Schemas, Module-Anzahl oder Reload-Teilnehmer werden erweitert. Keine Offline-Utilities, Selector-, Moderations-, Pay-, Invsee-Modify- oder generischen Command/GUI-Frameworks.

## Finale Command-Matrix

`YES` bezeichnet Produktionsänderungen am Command oder seinem Wiring/Feedback, nicht lediglich zusätzliche Tests oder Hilfe-Einträge. Permissions sind weiterhin relativ zu `vapeecore.`. In gruppierten Aktionen gilt dieselbe Syntax für jede genannte Aktion.

| Command / Alias | Changed? | Reason | Final syntax | Permission |
|---|---|---|---|---|
| core / vapeecore | YES | aktuelle gefilterte Hilfe, genaue Arity, testbarer tatsächlicher Executor | `/core [help\|version\|reload]` | öffentlich; reload `admin` |
| spawn | YES | Executor-Permission, separates Cancel-/Failed-Feedback | `/spawn` | `lobby.spawn` |
| setspawn | YES | Executor-Permission, UUID/SEVERE und kein Rethrow bei Save-Fehler | `/setspawn` | `lobby.setspawn` |
| coins | NO | Phase-22-Economy-Semantik bleibt erhalten | `/coins`; `/coins help`; `/coins get <player\|uuid>`; `/coins add/remove/set <player\|uuid> <amount>` | `economy.coins`; get/add/remove/set zusätzlich `economy.admin` |
| profile | NO | bekannte Identitäten, Offline-Coins und Ambiguity korrekt | `/profile [player\|uuid]` | `profile.view` |
| rank | YES | explizite Permission, eindeutige Online-Auflösung und Online-Tab | `/rank [player]` | `rank.view` |
| ranks | YES | explizite Permission; Track und Current-Marker unverändert | `/ranks` | `ranks.view` |
| friend / friends | YES | tatsächliche Zielauswahl, Suggest-Buttons, sichere UUID-Argumente | `/friend`; `/friend help/list/requests`; `/friend add <player\|uuid>`; `/friend accept/deny/cancel/remove [player\|uuid]` | `friend.use` |
| clan / clans | NO | bestehende Rollen, Invite-UX und Save-Guards korrekt | `/clan`; `/clan help`; `/clan create <tag> <name...>`; `/clan info [tag\|uuid]`; `/clan invites`; `/clan invite/cancel/kick/transfer <player\|uuid>`; `/clan accept/deny <tag\|uuid>`; `/clan leave`; `/clan rename <name...>`; `/clan tag <tag>`; `/clan disband confirm` | `clan.use` |
| msg | YES | Executor-/Tab-Permission, vollständige Online-Namen | `/msg <player> <message...>` | `message.use` |
| reply / r | YES | Executor-Permission; Session/Policy unverändert | `/reply <message...>`; `/r <message...>` | `message.use` |
| ignore | YES | Executor-/Tab-Permission, Online-Auflösung | `/ignore <player>` | `social.ignore` |
| unignore | YES | Namenskollision ablehnen; eigene UUIDs sicher vorschlagen | `/unignore <player\|uuid>` | `social.ignore` |
| ignorelist | YES | Executor-Permission und klarer Empty State | `/ignorelist` | `social.ignore` |
| settings | NO | Settings-/Visibility-GUI und Persistenz unverändert | `/settings`; `/settings visibility`; `/settings visibility add/remove <player\|uuid>` | `settings.use` |
| build | NO | BUILD-, Lobby-, Activity- und Flight-Ownership korrekt | `/build` | `utility.build` |
| fly | NO | Self-UUID, Others und native Flight-Guards korrekt | `/fly [player]` | `utility.fly`, fremdes Target/Console `.fly.others` |
| speed | NO | 1–10 und Walk-/Fly-Kontext korrekt | `/speed <1-10> [player]` | `utility.speed`, fremdes Target/Console `.speed.others` |
| gamemode / gm | NO | Aliase, UUID-Self, BUILD und Activity korrekt | `/gamemode <survival\|creative\|adventure\|spectator\|s\|c\|a\|sp\|0\|1\|2\|3> [player]`; `/gm …` | `utility.gamemode`, fremdes Target/Console `.gamemode.others` |
| tp / teleport | NO | gesamte Phase-18C-Grammatik und Guards erhalten | `/tp <target>`; `/tp <x> <y> <z> [yaw pitch]`; `/tp <source> <target>`; `/tp <source> <x> <y> <z> [yaw pitch]`; `/tp world <world> <x> <y> <z> [yaw pitch]`; `/tp <source> world <world> <x> <y> <z> [yaw pitch]`; `/teleport …` | `utility.teleport`, `.teleport.others`, `.teleport.world`, `.teleport.others.world`; interner Guard-Bypass `.teleport.bypass` |
| tphere | NO | Player-only, Self-Ablehnung und Cancellation korrekt | `/tphere <player>` | `utility.teleport.here`; Guard-Bypass `.teleport.bypass` |
| heal | NO | Health/Fire/Freeze; kein Potion-Cleanse | `/heal [player]` | `utility.heal`, fremdes Target/Console `.heal.others` |
| feed | NO | Hunger/Saturation/Exhaustion korrekt | `/feed [player]` | `utility.feed`, fremdes Target/Console `.feed.others` |
| ping | NO | echte ms und UUID-Self korrekt | `/ping [player]` | `utility.ping`, fremdes Target/Console `.ping.others` |
| clear | NO | vollständige Löschung nur nach BUILD-/Activity-Guards | `/clear [player]` | `utility.clear`, fremdes Target/Console `.clear.others` |
| invsee | NO | unveränderter Read-only-Snapshot und Player-Viewer | `/invsee <player>` | `utility.invsee`; `.invsee.modify` bleibt inaktiv |
| enderchest | NO | Player-Viewer, live Online-Chest, UUID-Self korrekt | `/enderchest [player]` | `utility.enderchest`, fremdes Target `.enderchest.others` |
| warp | NO | Permission/Arity/literal Text und atomare Save-Guards korrekt | `/warp [help]`; `/warp set/remove/info <id>`; `/warp list`; `/warp name <id> <name...>`; `/warp icon <id> <material>` | `warp.admin` |
| blackjack | YES | keine Table-ID-Vorschläge für unbekannte Setup-Aktionen | `/blackjack [help]`; `/blackjack setup [help]`; `/blackjack setup create/delete/pos1/pos2/dealer/interaction/display/preview/enable/disable/info <id>`; `/blackjack setup seat/removeseat <id> <1-5>`; `/blackjack setup list` | `blackjack.admin` |

## Details und bewusste Grenzen

Friend: `add` ohne Ziel nennt „Missing player.“ und seine konkrete Usage. Accept/Deny ohne Ziel zeigt ausschließlich tatsächliche eingehende Requests mit beiden Suggest-Aktionen; Cancel zeigt ausgehende Requests, Remove die eigenen Freunde. Die leeren Zustände sind „You have no pending friend requests.“, „You have no pending outgoing friend requests.“ und „You do not have any friends yet.“. Help/List/Requests mit Extra-Argumenten nennen jeweils ihre Syntax. Unbekannte Aktionen sind literal mit Verweis auf `/friend help`. Notifications tragen den Actor-Namen und Accept/Deny-Buttons. Eindeutige bekannte Namen sind erlaubt; Namenskollision, fehlende Identity, whitespace oder nicht übereinstimmende UUID führt zu UUID-Argumenten. Bestehende relation-eigene UUIDs sind auch ohne Identity ausführbar; neue Phantom-Identitäten für Add bleiben verboten. Keine RUN_COMMAND-Events.

Spawn und SetSpawn prüfen Permission, exakte Null-Arity und Player-Kontext. Spawn unterscheidet fehlende Config, fehlende Welt, Cancel/Failure und Success. SetSpawn loggt einen Save-Fehler einmal mit SEVERE, Exception und Actor-UUID, sendet kontrollierten Fehler und weder Rethrow noch falschen Success. Test-injizierte SEVERE-Fehler sind erwartete Harness-Fälle, keine Fehler des Paper-Smokes.

PM-Commands behalten enabled/loaded/recipient-setting/Ignore-/Session-Regeln. `/r` delegiert an denselben Reply-Executor. Social-Commands bleiben Player-only; Ignore-Tab schließt Self und bestehende Ignores aus. Unignore mutiert bei Namenskollision nichts; UUIDs müssen zur eigenen Liste gehören. Ignore/Unignore publizieren erst nach erfolgreichem Save; Fehler rollen den Domain-State zurück und lösen keine Relationship-Callbacks aus. IgnoreList sortiert bekannte Namen vor unbekannten UUIDs und meldet Empty State.

Rank/Ranks behalten cachefreie LuckPerms-Abfragen, öffentliche Track-Reihenfolge, Current-Marker und kontrollierte unavailable-/empty-Ausgaben. Profile, Economy, Clan, Settings und Visibility haben keine Produktionsänderung. Die gesamte Utility-Produktion bleibt unverändert. Neue Utility-Tests prüfen acht explizite Self-Formen mit ausschließlich Basispermission, fehlende Basisrechte, verborgenes Tab, Extra-Arity und Console ohne Others. Bestehende Harnesses decken Activity/BUILD/native Flight, Speed, Inventory-Snapshot-Sicherheit und Teleport-Mathematik/Cancellation ab.

Die Teleport-Grammatik ist absichtlich eine Vanilla-Teilmenge: keine Selector/Entities, keine Offline-/Mojang-Auflösung, kein World-Auto-Load, Safe-Ground-Shift, Nether-Scaling oder WorldBorder-Clamp. Console braucht eine explizite Source; deren Form verlangt Others auch für den eigenen Namen. Invsee-Modify bleibt inaktiv. Warp und Blackjack wurden auf Help/Arity/Permission/literal Input und atomare Fehlerbehandlung geprüft; keine Gameplay-/Persistence-Änderung. Der konkrete Blackjack-Tab-Fix besitzt positive/negative Tests.

`plugin.yml`, `docs/PERMISSIONS.md` und `docs/FORMATTING.md` bleiben unverändert: bestehende Nodes/Children/Aliases passen, keine globale Formatänderung. README und Developer Guide dokumentieren Phase 23, Ownership, Defense in Depth, Target-/Console-Regeln und Friend-UX. Kein neues Schema, keine Migration, weiterhin 25 Module und sechs Reload-Teilnehmer.

## Verifikation

**77 tatsächlich separat gestartete Harnesses, 3951 Checks, 0 Failures.** Alle 75 bisherigen Harnesses sind enthalten; zwei neue Command-Harnesses und gezielt ergänzte bestehende Checks kommen hinzu. Baseline: 75 Harnesses / 3702 Checks; Zuwachs: 2 Harnesses / 249 Checks. Kein Ersatz der gesamten Regression durch einzelne Stichproben.

Neue/erweiterte Command-Tests: FriendCommand 80, PM/Social 91, LobbyCommand 26, CoreCommand 57, RankCommand 16, RanksCommand 12, UtilityCommand 152, CommandHelp 85 Checks. Die PM-/Social-Fälle laufen im erweiterten `PrivateMessageSocialHarness` auf den tatsächlichen Executors; die Lobby-/Core-Fälle in den neuen fokussierten Harnesses. Profile bleibt mit 19 Checks grün. Warp 45, TeleportCommand 62, TeleportParser 58, BuildCommand 17 und UtilityInventory 36 Checks sichern die unveränderten Grenzen. CoinsCommand 208, EconomyService 44 und EconomyIntegration 24 sichern die Phase-22-Regression. Sämtliche Friend-/Clan-/Settings-/Visibility-/Reward-/OnlineReward-/Quest-/Blackjack- und übrigen Tests wurden ebenfalls erneut ausgeführt, siehe Einzelergebnisse unten.

Ein Vorlauf deckte eine Test-Fixture-Überschneidung auf: synthetische UUID-Completions konnten zufällig eine Namensprefix-Assertion aus der älteren Friend-Testgruppe beeinflussen. Die neuen Fälle besitzen jetzt eine getrennte Fixture. Der oben gezählte komplette finale Lauf startete erst nach dem letzten Neubau und ohne gleichzeitiges Neu-Kompilieren. Keine bekannten Phase-23-Produktionsfehler; **merge-ready** im geprüften Scope. Manuelle Ingame-UX bleibt mangels verbundenem Client ungeprüft.

### Alle separat ausgeführten Harnesses

| Harness | Checks | Result |
|---|---:|---|
+| `dev.vapee.core.activity.ActivityHarness` | 153 | PASS |
| `dev.vapee.core.activity.blackjack.BlackjackFairnessHarness` | 393 | PASS |
| `dev.vapee.core.activity.blackjack.BlackjackHarness` | 218 | PASS |
| `dev.vapee.core.activity.blackjack.BlackjackPresentationHarness` | 43 | PASS |
| `dev.vapee.core.activity.blackjack.presentation.BlackjackPreviewHarness` | 16 | PASS |
| `dev.vapee.core.activity.blackjack.table.BlackjackTableHarness` | 90 | PASS |
| `dev.vapee.core.chat.ChatHarness` | 17 | PASS |
| `dev.vapee.core.clan.ClanDomainHarness` | 52 | PASS |
| `dev.vapee.core.clan.ClanIntegrationHarness` | 15 | PASS |
| `dev.vapee.core.clan.ClanPersistenceHarness` | 39 | PASS |
| `dev.vapee.core.clan.ClanServiceHarness` | 136 | PASS |
| `dev.vapee.core.clan.gui.ClanCommandHarness` | 83 | PASS |
| `dev.vapee.core.clan.gui.ClanMenuHarness` | 84 | PASS |
| `dev.vapee.core.clan.gui.ClanMenuSecurityHarness` | 37 | PASS |
| `dev.vapee.core.command.CoreCommandHarness` | 57 | PASS |
| `dev.vapee.core.config.ConfigServiceHarness` | 34 | PASS |
| `dev.vapee.core.config.DefaultConsistencyHarness` | 11 | PASS |
| `dev.vapee.core.economy.command.CoinsCommandHarness` | 208 | PASS |
| `dev.vapee.core.economy.EconomyIntegrationHarness` | 24 | PASS |
| `dev.vapee.core.economy.EconomyServiceHarness` | 44 | PASS |
| `dev.vapee.core.friend.FriendCommandHarness` | 80 | PASS |
| `dev.vapee.core.friend.FriendDomainHarness` | 29 | PASS |
| `dev.vapee.core.friend.FriendIntegrationHarness` | 28 | PASS |
| `dev.vapee.core.friend.FriendLifecycleHarness` | 12 | PASS |
| `dev.vapee.core.friend.FriendPersistenceHarness` | 28 | PASS |
| `dev.vapee.core.friend.FriendServiceHarness` | 79 | PASS |
| `dev.vapee.core.friend.gui.FriendMenuHarness` | 159 | PASS |
| `dev.vapee.core.friend.gui.FriendMenuSecurityHarness` | 37 | PASS |
| `dev.vapee.core.identity.IdentityHarness` | 26 | PASS |
| `dev.vapee.core.identity.ProfileHarness` | 19 | PASS |
| `dev.vapee.core.lobby.command.LobbyCommandHarness` | 26 | PASS |
| `dev.vapee.core.lobby.player.LobbyHarness` | 53 | PASS |
| `dev.vapee.core.lobby.warp.WarpHarness` | 45 | PASS |
| `dev.vapee.core.message.CommandHelpHarness` | 85 | PASS |
| `dev.vapee.core.onlinereward.OnlineRewardLifecycleHarness` | 42 | PASS |
| `dev.vapee.core.onlinereward.OnlineRewardServiceHarness` | 42 | PASS |
| `dev.vapee.core.player.repository.PlayerQuestPersistenceHarness` | 21 | PASS |
| `dev.vapee.core.player.repository.PlayerRewardPersistenceHarness` | 11 | PASS |
| `dev.vapee.core.player.repository.PlayerVisibilityPersistenceHarness` | 12 | PASS |
| `dev.vapee.core.player.settings.PlayerSettingsServiceHarness` | 24 | PASS |
| `dev.vapee.core.player.settings.PlayerVisibilitySettingsHarness` | 11 | PASS |
| `dev.vapee.core.presentation.PlaytimeFormatterHarness` | 14 | PASS |
| `dev.vapee.core.presentation.PresentationHarness` | 10 | PASS |
| `dev.vapee.core.privatemessage.PrivateMessageSocialHarness` | 91 | PASS |
| `dev.vapee.core.quest.daily.DailyQuestConfigHarness` | 13 | PASS |
| `dev.vapee.core.quest.daily.DailyQuestCycleSelectorHarness` | 18 | PASS |
| `dev.vapee.core.quest.daily.DailyQuestLifecycleHarness` | 14 | PASS |
| `dev.vapee.core.quest.DailyQuestServiceHarness` | 20 | PASS |
| `dev.vapee.core.quest.QuestDefinitionHarness` | 29 | PASS |
| `dev.vapee.core.quest.QuestLifecycleHarness` | 42 | PASS |
| `dev.vapee.core.quest.QuestServiceHarness` | 44 | PASS |
| `dev.vapee.core.rank.RankCommandHarness` | 16 | PASS |
| `dev.vapee.core.rank.RanksCommandHarness` | 12 | PASS |
| `dev.vapee.core.rank.RankServiceHarness` | 23 | PASS |
| `dev.vapee.core.reward.RewardLifecycleHarness` | 40 | PASS |
| `dev.vapee.core.reward.RewardServiceHarness` | 126 | PASS |
| `dev.vapee.core.seat.SeatHarness` | 52 | PASS |
| `dev.vapee.core.settings.command.SettingsCommandHarness` | 25 | PASS |
| `dev.vapee.core.settings.SettingsMenuHarness` | 56 | PASS |
| `dev.vapee.core.settings.SettingsMenuSecurityHarness` | 34 | PASS |
| `dev.vapee.core.settings.visibility.SettingsModuleLifecycleHarness` | 23 | PASS |
| `dev.vapee.core.settings.visibility.SettingsNavigationHarness` | 17 | PASS |
| `dev.vapee.core.settings.visibility.VisibilityMenuSecurityHarness` | 41 | PASS |
| `dev.vapee.core.settings.visibility.VisibilitySettingsMenuHarness` | 29 | PASS |
| `dev.vapee.core.settings.visibility.VisiblePlayersMenuHarness` | 74 | PASS |
| `dev.vapee.core.utility.command.BuildCommandHarness` | 17 | PASS |
| `dev.vapee.core.utility.command.TeleportCommandHarness` | 62 | PASS |
| `dev.vapee.core.utility.command.UtilityCommandHarness` | 152 | PASS |
| `dev.vapee.core.utility.TeleportParserHarness` | 58 | PASS |
| `dev.vapee.core.utility.UtilityInventoryHarness` | 36 | PASS |
| `dev.vapee.core.utility.UtilityServiceHarness` | 35 | PASS |
| `dev.vapee.core.visibility.FriendVisibilityRefreshHarness` | 11 | PASS |
| `dev.vapee.core.visibility.IgnoreVisibilityRefreshHarness` | 7 | PASS |
| `dev.vapee.core.visibility.VisibilityModuleHarness` | 22 | PASS |
| `dev.vapee.core.visibility.VisibilityPolicyHarness` | 28 | PASS |
| `dev.vapee.core.visibility.VisibilityServiceHarness` | 10 | PASS |
| `dev.vapee.core.worlddisplay.WorldDisplayHarness` | 27 | PASS |

### Finaler Build und Paper-Smoke (2026-09-30)

- `mvn clean package`: **BUILD SUCCESS**, 265 Produktions- und 82 Test-Source-Dateien kompiliert. Surefire ersetzt hier nicht die gesondert gestarteten `main`-Harnesses.
- JAR: `target/vapeecore-1.0-SNAPSHOT.jar`, **908806 Bytes**.
- SHA-256: `4FB6C19F0BCD765F75361D97C7187C81D30F6D01010903F51663591B5D5A662D`.
- Die deployte JAR unter `dev-server/plugins/` hat denselben Hash und wurde im anschließenden Smoke tatsächlich geladen.
- Paper **1.21.11-132-ver/1.21.11@c5eb079**, Minecraft 1.21.11; Oracle Java **21.0.12.1+1-LTS-4**; LuckPerms **5.5.84**.
- Startup **25 Module**, Shutdown **25 Module** rückwärts; Reload **6 Teilnehmer**: config, lobby, chat, private-messages, presentation, daily-quests. Reload erfolgreich, 12 ms laut Log.
- Registrierungsindex `bukkit:help VapeeCore` enthält alle 29 Root-Commands. Alle 35 Namen inklusive `/core`, `/vapeecore`, `/friends`, `/clans`, `/r`, `/gm` und `/teleport` wurden direkt dispatcht; zusätzlich wurden die Feature-Namespace-Registrierungen ausgeführt. `tp`, `teleport`, `gm` und `r` wurden auch über Bukkit-Help auf den korrekten Plugin-Command geprüft.
- `/core`, `/core help`, `/core version`, spezifische Extra-Arity, literal unbekannte Core-/Warp-/Blackjack-Aktionen, Player-only-Ausgaben und Console-mit-fehlendem-Target waren korrekt.
- Read-only Offline-Prüfung: `/profile 0d9efbe6-33bf-4cf7-ae67-61d6238b2561` zeigt rxaq, Coins 0 und Rank/Playtime unavailable while offline; `/coins get` bestätigt 0. Keine Wallet-Mutation. `/rank phase23_missing` und Utility-UUID-Target wurden als nicht online abgewiesen.
- Warp-/Blackjack-Hilfe, leere Listen und fehlende IDs wurden kontrolliert beantwortet. Keine Admin-Mutation mit Online-Target behauptet: **0 Spieler online**.
- Clean Stop am Ende, Exitcode 0; kein Server-PID-Marker übrig. **0 ERROR/SEVERE im gesamten aktuellen Paper-Log**, insbesondere keine VapeeCore-Fehler. Paper weist lediglich auf die neuere Minecraft-Release-Linie hin; kein Versionswechsel war Teil des Auftrags.
- **Live client test: not performed.** Klick-Events wurden automatisiert in Harnesses geprüft, nicht manuell in einem Minecraft-Client.
- `git diff --check` ohne Fehler; keine Änderungen an Utility-Produktion, TeleportParser, Domain-Services, Schema-/Config-Defaults, `plugin.yml`, Permissions oder FORMATTING.

## Future findings / Phase 24

Phase 24 ist **Moderation Foundation**. Nicht implementiert: `/ban`, Moderations-`/kick`, `/mute`, `/warn`, `/freeze`, History, Staff Notes oder Punishment Storage. Weitere getrennte spätere Themen: generisches Command-/GUI-Framework, Selector, Offline-Utility-Mutation, Invsee-Modify und sichere Zwei-Wallet-`/pay`-Transaktion. Diese Punkte sind absichtliche Scope-Grenzen, keine Phase-23-Fehler.

## Datei-Inventar des Abschlusses

Neu (3):

- `docs/COMMAND_AUDIT.md`
- `src/test/java/dev/vapee/core/command/CoreCommandHarness.java`
- `src/test/java/dev/vapee/core/lobby/command/LobbyCommandHarness.java`

Geändert (24):

- `README.md`
- `docs/DEVELOPER_GUIDE.md`
- `src/main/java/dev/vapee/core/activity/blackjack/command/BlackjackCommand.java`
- `src/main/java/dev/vapee/core/command/CoreCommand.java`
- `src/main/java/dev/vapee/core/friend/FriendMessages.java`
- `src/main/java/dev/vapee/core/friend/FriendModule.java`
- `src/main/java/dev/vapee/core/friend/command/FriendCommand.java`
- `src/main/java/dev/vapee/core/lobby/LobbyModule.java`
- `src/main/java/dev/vapee/core/lobby/command/SetSpawnCommand.java`
- `src/main/java/dev/vapee/core/lobby/command/SpawnCommand.java`
- `src/main/java/dev/vapee/core/privatemessage/command/MessageCommand.java`
- `src/main/java/dev/vapee/core/privatemessage/command/ReplyCommand.java`
- `src/main/java/dev/vapee/core/rank/RankModule.java`
- `src/main/java/dev/vapee/core/rank/command/RankCommand.java`
- `src/main/java/dev/vapee/core/rank/command/RanksCommand.java`
- `src/main/java/dev/vapee/core/social/command/IgnoreCommand.java`
- `src/main/java/dev/vapee/core/social/command/IgnoreListCommand.java`
- `src/main/java/dev/vapee/core/social/command/UnignoreCommand.java`
- `src/test/java/dev/vapee/core/friend/FriendCommandHarness.java`
- `src/test/java/dev/vapee/core/message/CommandHelpHarness.java`
- `src/test/java/dev/vapee/core/privatemessage/PrivateMessageSocialHarness.java`
- `src/test/java/dev/vapee/core/rank/RankCommandHarness.java`
- `src/test/java/dev/vapee/core/rank/RanksCommandHarness.java`
- `src/test/java/dev/vapee/core/utility/command/UtilityCommandHarness.java`

Entfernt: keine. `git diff be43a4addc46b74d5a7c88118830b065124edfb3..HEAD --name-only` ist nach dem lokalen Commit das vollständige maschinenprüfbare Inventar. Build-Ausgaben unter `target/` und der lokale `dev-server/` bleiben ignoriert und gehören nicht zum Commit.
