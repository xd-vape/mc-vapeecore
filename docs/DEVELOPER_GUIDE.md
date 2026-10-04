# VapeeCore Developer Guide

Diese Datei ist die praktische Landkarte für Änderungen an VapeeCore. Der aktuelle Zielserver ist Paper 1.21.11 mit Java 21 und LuckPerms 5.5.x; gebaut wird mit Maven. Die Main Class ist `dev.vapee.core.VapeeCore`, das Base Package ist `dev.vapee.core`.

VapeeCore ist ein modularer Monolith. `CoreModule` definiert den kleinen Enable-/Disable-Lifecycle, `ModuleManager` aktiviert Module in registrierter Reihenfolge und deaktiviert sie rückwärts. Abhängigkeiten werden explizit über Konstruktoren übergeben. Es gibt keine globalen Service-Singletons, keine Reflection-Discovery und keine statischen Player-State-Maps. Packages sind nach Features geschnitten. LuckPerms ist die Quelle für Gruppen und Permissions; Java-Code kennt keine Rangnamen wie „Builder“.

## Where do I change this?

| I want to change… | Owner / place |
|---|---|
| Normal lobby gamemode | Live: `plugins/VapeeCore/lobby.yml`, Default: `src/main/resources/lobby.yml`, Parsing: `LobbyConfig`, Anwendung: `LobbyPlayerStateService` |
| Join message text | `lobby.yml` unter `messages.join` |
| Quit message text | `lobby.yml` unter `messages.quit` |
| Join/Quit message rendering behavior | `dev.vapee.core.lobby.message.LobbyMessageService` |
| What happens when a player joins the lobby | `LobbyListener` und `LobbyPlayerStateService` |
| Lobby spawn | `LobbyService`, `LobbyConfig`, `/setspawn` in `SetSpawnCommand` |
| Lobby protection | `LobbyListener` |
| Build mode behavior | `dev.vapee.core.lobby.player.LobbyPlayerStateService` |
| `/build` command | `UtilityModule` und `BuildCommand` |
| Flight-/Speed-/Gamemode-Mutationen und transienter Cleanup | `dev.vapee.core.utility.UtilityService` |
| Utility Join-/Quit-Normalisierung | `dev.vapee.core.utility.UtilityListener` |
| `/fly` | `dev.vapee.core.utility.command.FlyCommand` |
| `/speed` | `dev.vapee.core.utility.command.SpeedCommand` |
| `/gamemode` und `/gm` | `dev.vapee.core.utility.command.GameModeCommand` |
| `/tp`, `/teleport` | `dev.vapee.core.utility.command.TeleportCommand`, `dev.vapee.core.utility.teleport.TeleportParser` |
| `/tphere` | `dev.vapee.core.utility.command.TeleportHereCommand` |
| `/heal` | `dev.vapee.core.utility.command.HealCommand` |
| `/feed` | `dev.vapee.core.utility.command.FeedCommand` |
| Utility-Modul-Lifecycle und Command-Registrierung | `dev.vapee.core.utility.UtilityModule` |
| Lobby hotbar items | `dev.vapee.core.lobby.item.LobbyItemService` |
| Warp Navigator item material/name/lore | `LobbyItemService` |
| Warp Navigator GUI | `NavigatorMenu` und `NavigatorListener` |
| Navigator visibility / order | `WarpNavigation`, `WarpService`, `/warp show`, `/warp hide`, `/warp order` |
| Navigator opening / click authorization | `NavigatorAccessPolicy` in LobbyExperience; online + loaded + lobby + NORMAL + no activity |
| Player visibility policy/runtime | `dev.vapee.core.visibility.VisibilityModule`, `VisibilityPolicy`, `VisibilityService` |
| Persistent visibility preferences | `PlayerVisibilitySettings`, `PlayerSettingsService`, `FilePlayerRepository` |
| Settings menu und Visibility UX | `SettingsMenu`, `dev.vapee.core.settings.visibility`, `SettingsCommand` |
| Warps | `dev.vapee.core.lobby.warp` |
| Warp persistence | Live: `plugins/VapeeCore/warps.yml`, Code: `WarpConfig` |
| Blackjack rules | `dev.vapee.core.activity.blackjack` |
| Physical Blackjack table setup | `dev.vapee.core.activity.blackjack.table` |
| Blackjack setup command | `BlackjackCommand` |
| Blackjack table persistence | Live: `plugins/VapeeCore/blackjack.yml`, Code: `BlackjackTableConfig` |
| Blackjack hotbar items and slots | `BlackjackInventoryService` |
| Blackjack seat/block interaction | `activity.blackjack.interaction.BlackjackTableListener` |
| Blackjack world cards and labels | `BlackjackWorldViewService` |
| Blackjack card positions and tuning | `BlackjackDisplayGeometry` |
| Casual seating behavior | `dev.vapee.core.seat.SeatListener` |
| Seat entity lifecycle | `dev.vapee.core.seat.SeatService` |
| Stair/slab seat position | `dev.vapee.core.seat.SeatPositionResolver` |
| Managed seat integration | `SeatService` und das konsumierende Feature |
| Blackjack seat allocation | `dev.vapee.core.activity.blackjack.table.BlackjackSeatService` |
| World text/item display lifecycle | `dev.vapee.core.worlddisplay.WorldDisplayService` |
| World display module lifecycle | `dev.vapee.core.worlddisplay.WorldDisplayModule` |
| Coins | `dev.vapee.core.economy` |
| Known-Player-Name/UUID-Lookup | `PlayerService`, `FilePlayerRepository` und `PlayerIdentityService` |
| `/profile` und Online-/Offline-Profilansicht | `dev.vapee.core.identity` |
| `/friend`, Friends-Regeln und zentrale Persistence | `dev.vapee.core.friend`, `friends.yml` |
| Runtime-Presence und Friend-Join-/Leave-Benachrichtigungen | `dev.vapee.core.presence`, `PresenceModule`, `FriendPresenceNotifier` |
| Presence-Benachrichtigungen erlauben/sperren | `PlayerSettingsService`, `FilePlayerRepository`, `SettingsMenu` (Feature 33, Status 42) |
| `/clan`, Clan-Regeln und zentrale Persistence | `dev.vapee.core.clan`, `clans.yml` |
| Moderationshistorie, aktive Mute-/Ban-Abfragen und Widerruf | `dev.vapee.core.moderation.ModerationService`; Domain ohne Bukkit-/Identity-Abhängigkeiten |
| Moderation Persistence und Schema | `FileModerationRepository`, `plugins/VapeeCore/moderation.yml` |
| Moderation Commands, Identity/Actor/Permission-Gates | `moderation.command`, `ModerationCommandContext` |
| Ban-/Mute-Dauer und UTC-/History-/Notice-/Disconnect-Components | `ModerationDurationParser`, `ModerationComponents` |
| Login-Ban-Enforcement vor Player-Join | `ModerationLoginListener`, synchroner `PlayerLoginEvent` |
| Friends-GUI, Inventar-Schutz und Navigation | `dev.vapee.core.friend.gui.FriendMenu`, `FriendMenuHolder`, `FriendMenuListener` |
| Freundschaftsanfragen erlauben/sperren | `PlayerSettingsService`, `FilePlayerRepository`, `SettingsMenu` (Feature 16, Status 25) |
| Friends-Limits | `ConfigService`, `config.yml` → `friends.limits` |
| Gameplay-Coin-Rewards und gebündelte Persistence | `dev.vapee.core.reward` |
| Kumulative Online-/Playtime-Rewards | `dev.vapee.core.onlinereward` |
| Quest Progress Engine | `dev.vapee.core.quest.QuestService` |
| Quest Definition Domain | `QuestDefinition` und `QuestDefinitionRegistry` |
| Technische Quest Progress Keys | `QuestProgressKey` |
| Player Quest Persistence | `PlayerQuestState`, `PlayerQuestProgress` und `FilePlayerRepository` |
| Daily-Quest-Auswahl und Rotation | `dev.vapee.core.quest.daily` |
| Quest-Feedback und Playtime-Quelle | `QuestProgressReporter`, `QuestPlaytimeProducer` |
| Daily-Quest-Command und lesendes Menü | `quest.daily.command`, `quest.daily.menu` |
| Player settings persistence | `dev.vapee.core.player.settings` und `FilePlayerRepository` |
| Ignore/social | `dev.vapee.core.social` und `dev.vapee.core.player.social` |
| Server rank source / rank assignment | LuckPerms, nicht VapeeCore |
| Öffentlicher Rank-Track | Live: `plugins/VapeeCore/config.yml` → `ranks.track`, Default: `ranks` |
| Rank integration | `dev.vapee.core.rank` |
| `/rank` | `dev.vapee.core.rank.command.RankCommand` |
| `/ranks` | `dev.vapee.core.rank.command.RanksCommand` |
| Rank display name / description / color / weight | `RankService` und `LuckPermsService` |
| Rank- und Playtime-Placeholder | `RankInfo`, `PresentationRenderer` und `PlaytimeFormatter` |
| Rank Prefix / Suffix / Badge | LuckPerms-Meta; Farbe separat über `vapeecore.rank.color` |
| Chat | `dev.vapee.core.chat` |
| Private messages | `dev.vapee.core.privatemessage` |
| Scoreboard / Tablist | `dev.vapee.core.presentation` |
| Module startup order | `VapeeCore.java` |
| Module lifecycle | `ModuleManager` und `CoreModule` |
| Reload participants | `ReloadService`-Wiring in `VapeeCore.java` |
| Commands / permissions | `src/main/resources/plugin.yml` und das jeweilige Feature-Command-Package |

## Defaults und Live-Konfiguration

**`src/main/resources/*.yml` sind nur Defaults, die in die Plugin-JAR gepackt werden. `plugins/VapeeCore/*.yml` sind die tatsächlich verwendeten Dateien eines laufenden Servers.**

Eine geänderte Resource ersetzt niemals automatisch eine bereits vorhandene Live-Datei. Bei einer bestehenden Dev- oder Produktionsinstallation muss der neue Wert auch in der Datei unter `plugins/VapeeCore/` eingetragen werden. `/core reload` liest seine sechs registrierten Live-Dateien neu. `blackjack.yml` und `warps.yml` werden stattdessen durch ihre Admin-Commands zur Laufzeit geschrieben und aktualisiert.

| File | Owner | Purpose | `/core reload`? | Runtime mutable? | Defaults |
|---|---|---|---:|---|---|
| `config.yml` | `ConfigService` | Servername, globaler Message-Prefix, Debug, `ranks.track`, Online-Reward-Regeln und -Nachricht, `friends.limits`, `clans.limits` | Ja | Durch Reload | `src/main/resources/config.yml` |
| `lobby.yml` | `LobbyConfig` / `LobbyModule` | Spawn, Teleport, Protection, `player.gamemode`, Join/Quit-Texte | Ja | Spawn durch `/setspawn`, übrige Werte durch Reload | `src/main/resources/lobby.yml` |
| `chat.yml` | `ChatConfig` / `ChatModule` | Globaler Chat und LuckPerms-Metaformat | Ja | Durch Reload | `src/main/resources/chat.yml` |
| `private-messages.yml` | `PrivateMessageConfig` / `PrivateMessageModule` | Aktivierung und PM-Formate | Ja | Durch Reload | `src/main/resources/private-messages.yml` |
| `presentation.yml` | `PresentationConfig` / `PresentationModule` | Sidebar, Tablist, Updateintervall | Ja | Durch Reload | `src/main/resources/presentation.yml` |
| `daily-quests.yml` | `DailyQuestConfig` / `DailyQuestModule` | Daily-Slots, Reset, Zeitzone und Quest-Katalog | Ja | Durch Reload; Player-Rotation erst beim nächsten Sync | `src/main/resources/daily-quests.yml` |
| `blackjack.yml` | `BlackjackTableConfig` / `BlackjackModule` | Physische Blackjack-Table-Drafts | Nein | Ja, atomar über `/blackjack setup …` | `src/main/resources/blackjack.yml` |
| `warps.yml` | `WarpConfig` / `WarpModule` | Dynamische Warps | Nein | Ja, atomar über `/warp …` | `src/main/resources/warps.yml` |
| `friends.yml` | `FriendModule` / `FileFriendRepository` | UUID-basierte Freundschaften und gerichtete Anfragen, `schema-version: 1` | Nein | Ja, atomar über `FriendService` | Kein Resource-Default; bei fehlender Datei leer, beim ersten Save erzeugt |
| `clans.yml` | `ClanModule` / `FileClanRepository` | UUID-basierte Clans, Mitglieder und Einladungen, `schema-version: 1` | Nein | Ja, atomar über `ClanService` | Kein Resource-Default; bei fehlender Datei leer, beim ersten Save erzeugt |
| `moderation.yml` | `ModerationModule` / `FileModerationRepository` | UUID-basierte Moderationshistorie, Schema 1 | Nein | Ja, atomar über `ModerationService` | Kein Resource-Default; fehlend bleibt bei Enable/Disable fehlend, erster erfolgreicher Save erzeugt die Datei |

Die sechs Reload-Teilnehmer sind exakt `config.yml`, `lobby.yml`, `chat.yml`, `private-messages.yml`, `presentation.yml` und `daily-quests.yml`. `ranks.track` und `online-rewards` gehören zum vorhandenen `ConfigService`; Rank, Reward, OnlineReward und die generische Quest Foundation sind selbst keine Reload-Teilnehmer. Reward und Quest besitzen keine eigene Config- oder Datendatei; DailyQuest besitzt die Katalog-Datei. Der Reload wird zweiphasig vorbereitet und angewendet. `DailyQuestModule` tauscht beim Apply Config und Registry aus und stellt beim Rollback beide vorherigen Snapshots wieder her; Player-Assignments werden erst im nächsten normalen Sync berührt. `OnlineRewardService` liest die immutable Config-View bei jedem Processing neu. Bei Rollback setzt `LobbyModule` neben Config und Spawn auch die Gamemodes aller normalen Lobby-Spieler auf den vorherigen Wert zurück. BUILD-Spieler bleiben bis zum Build-Ende in `CREATIVE`.

## Module map und Reihenfolge

Seit Phase 30D teilen die sechs Config-Owner nur kleine Lese-/Dateihelfer: `ConfigFiles` kopiert Resource-Bytes ausschließlich bei fehlender Datei; `ConfigValues` prüft Boolean, String und nichtleere Strings. Warnungen, Defaults, Missing-/Null-Sonderfälle, Domain-Validierung und typed Reload-State bleiben beim Feature. `ConfigService` nutzt weiterhin `saveDefaultConfig`. Utility erfasst Command-Hooks vor dem ersten Setter und Listener vor der Registrierung; fehlgeschlagenes Enable räumt lokale Kandidaten auf, isoliert Cleanup-Ausnahmen als suppressed und veröffentlicht keine Services. Normaler Disable versucht ebenfalls alle Ressourcen und leert anschließend die Referenzen. Details und Nachweise: [Config Registration and Module Cleanup](CONFIG_REGISTRATION_MODULE_CLEANUP.md).

Die registrierte Reihenfolge ist eine Dependency-Reihenfolge und muss bei neuen Modulen bewusst gepflegt werden:

1. **Permission** – lesender LuckPerms-Zugriff.
2. **Rank** – cachefreie Rank-Domain, öffentlicher Track und `/rank`-/`/ranks`-Commands.
3. **Player** – `CorePlayer`, Cache, YAML-Persistence, Settings und Player-Join-/Quit-Lifecycle.
4. **Social** – Ignore-State und Commands.
5. **Economy** – Coin-Wallet und `/coins`.
6. **Identity** – Known-Player-Lookup, immutable Profile und `/profile`.
7. **Moderation** – immutable Historie, separate Persistence, sieben Staff-Commands, synchroner Ban-Login-Listener und LOWEST-Mute-Chat-Listener mit async-sicherer Projektion.
8. **Friend** – zentrale UUID-basierte Freundschaften und Anfragen, `/friend` und Friends-GUI.
9. **Presence** – runtimebasierter Online-State und gefilterte Friend-Join-/Leave-Hinweise.
10. **Clan** – Clans, Einladungen, `/clan` und Clan-GUI.
11. **Reward** – zentrale Gameplay-Reward-API und gebündelte Player-Persistence.
12. **OnlineReward** – kumulative Minecraft-Spielzeit-Rewards und Player-Fortschritt.
13. **Quest** – generische Definitionen, Assignments, Fortschritt, Completion und gebündelte Player-Persistence.
14. **DailyQuest** – Daily-Katalog, Cycle-Berechnung, Auswahl, Assignment und Rotation.
15. **Lobby** – Config, Spawn, Protection, Player-State, Items, Messages, `/spawn`, `/setspawn`.
16. **Visibility** – zentrale Lobby-Policy, Paper-Show/Hide-Anwendung und Cleanup eigener Hide-Zustände.
17. **Chat** – globaler Chat und lesende Rank-Placeholder.
18. **PrivateMessage** – `/msg`, `/reply` und Session-Konversationen.
19. **Presentation** – Sidebar, Tablist und lesende Rank-Placeholder.
20. **Settings** – Settings-Inventar und `/settings`.
21. **Activity** – generische Runtime-Typen, Venues, Sessions und Memberships.
22. **Utility** – `/build`, grundlegende Player-Utilities und transienter Movement-Cleanup.
23. **Seat** – generische CASUAL-/MANAGED-Sitze, Seat-Entities und Event-Cleanup.
24. **WorldDisplay** – native keyed TextDisplay-/ItemDisplay-Lifecycles.
25. **Blackjack** – physische Tische, Seat-Allocation, Activity-Hotbar und native Weltanzeigen.
26. **Warp** – dynamische Warp-Persistence und Admin-Command.
27. **LobbyExperience** – Visibility-Events und Hotbar-Anwendung, Item-Interaktionen und Navigator-UI.

Shutdown läuft exakt rückwärts: LobbyExperience → Warp → Blackjack → WorldDisplay → Seat → Utility → Activity → Settings → Presentation → PrivateMessage → Chat → Visibility → Lobby → DailyQuest → Quest → OnlineReward → Reward → Clan → Presence → Friend → Moderation → Identity → Economy → Social → Player → Rank → Permission. Presence meldet seinen Listener ab und leert seinen reinen Runtime-State, solange Friend, Social und Player noch verfügbar sind. Visibility gibt dabei seine VapeeCore-eigenen Hide-Zustände frei, solange Lobby, Friend, Social und Player noch verfügbar sind. DailyQuest stoppt zuerst seinen Sync-Task; Quest stoppt dann seinen Flush-Task und speichert dirty Quest-State einschließlich Cycle-ID, während Reward und Player noch verfügbar sind. OnlineReward stoppt danach seinen Processing-Task; Reward flusht anschließend dirty Coins und jeweils den gesamten aktuellen `CorePlayer`, solange Economy und Player noch verfügbar sind. Clan, Friend und Identity deregistrieren ihre Commands; Moderation deaktiviert ausstehendes Chat-Feedback, deregistriert beide Listener und sieben Command-Hooks, leert die Projektion, gibt Runtime-Referenzen frei und schreibt nicht erneut. Rank besitzt keinen persistenten Player-State. Blackjack gibt seine MANAGED-Sitze frei, bevor Seat den globalen Rest bereinigt.

Die wichtigsten Dependency-Richtungen sind:

```text
Permission
  ↑
Rank
  ↑       ↑
Chat  Presentation

Player
  ↑
Lobby

Player + Economy
  ↑
Reward

Config + Player + Reward + Message
  ↑
OnlineReward

Player + Reward + Message
  ↑
Quest

Player + Quest + Message
  ↑
DailyQuest

Player + Social + Friend + Lobby
  ↑
Visibility

Player + Social + Friend + Message
  ↑
Presence

Lobby + Activity
  ↑
Utility

Lobby + Activity
  ↑
Seat

Seat + Activity + WorldDisplay + Lobby + Quest
  ↑
Blackjack

Lobby + Player + Settings + Warp + Visibility + Activity
  ↑
LobbyExperience
```

`RankModule` hängt ausschließlich von Plugin, Config, Permission und Message ab. Es hängt insbesondere nicht von Player, Economy, Lobby, Chat, Presentation, Activity oder Blackjack ab. `RewardModule` hängt nur von Plugin, Player und Economy ab. `OnlineRewardModule` konsumiert Config, Player, Reward und Message, aber nie Economy direkt. `QuestModule` konsumiert ausschließlich Plugin, Player und Reward; es kennt Economy, OnlineReward, Activity, Blackjack, Mine, Rank, Permission und LuckPerms nicht. `DailyQuestModule` konsumiert ausschließlich Plugin, Player und Quest, insbesondere weder Reward noch Economy direkt. Features hängen in Richtung `Gameplay-Producer → Quest → Reward → Economy → Player`, nie umgekehrt; DailyQuest verwaltet nur den Katalog und die Assignments. `PresenceModule` konsumiert Player, Social, Friend und Message, ohne Rückabhängigkeit aus diesen Modulen. `VisibilityModule` konsumiert Player, Social, Friend und Lobby, aber weder SettingsModule noch Activity; die spätere Game-Teilnehmer-Anbindung liegt hinter einem kleinen Predicate. `Presentation` bezieht Rank, Permission, Player, Economy und Lobby. Chat bezieht Rank, Permission und Social; PrivateMessageModule bezieht Player, Social und Moderation; PrivateMessageService erhält ausschließlich ein finales UUID-Predicate aus der Mute-Projektion, keinen ModerationService. `MessageService` sowie bei Bedarf `ConfigService` werden explizit injiziert. Utility besitzt keine Blackjack-Abhängigkeit. SeatService kennt weder Lobby noch Activity noch Blackjack; nur SeatListener erhält die Lobby-/Activity-Policy. WorldDisplay hängt nur vom Plugin ab.

## Player Identity & Profile Foundation (Phase 17C)

`IdentityModule` hängt nur von `JavaPlugin`, `PlayerModule`, `RankModule`, `EconomyModule` und `MessageService` ab. Es steht unmittelbar nach Economy und vor Friend, besitzt weder Listener noch Task noch eigene Config/Persistence und ist keiner der sechs Reload-Teilnehmer. Beim Shutdown werden Command-Executor und Tab-Completer entfernt und Service-Referenzen freigegeben. Friends und Clans greifen auf `PlayerIdentityService` zu und bauen keinen eigenen Name-Resolver oder Player-YAML-Scan.

`PlayerIdentity(UUID, name, firstJoin, lastJoin)` ist ein validierter immutable Snapshot ohne `CorePlayer`- oder Bukkit-Referenz. `PlayerIdentityService#findById(UUID)`, `findByName(String)` und `resolve(String)` geben bekannte Spieler als Snapshot zurück; Namensauflösung unterscheidet `FOUND`, `NOT_FOUND` und `AMBIGUOUS`. UUID-Eingaben müssen bereits in VapeeCore bekannt sein. `PlayerProfile` ergänzt `online`, `coins`, optionalen `RankInfo` und optionale Playtime-Ticks. `PlayerProfileService#getProfile(UUID)` und `resolveProfile(String)` kombinieren ausschließlich bestehende Quellen, ohne Player zu laden, Coins oder Settings zu mutieren oder LuckPerms-User nachzuladen.

Der Runtime-Name-Index gehört `FilePlayerRepository`: Beim Start werden kanonisch benannte `<uuid>.yml`-Dateien einmalig auf Name und Join-Zeitpunkte geprüft; der Index speichert normalisierten Namen (`Locale.ROOT`) → UUID-Set und hält keine `CorePlayer` dauerhaft im Speicher. Ungültige Metadaten werden protokolliert und übersprungen. Erst nach erfolgreichem atomarem Datei-Replace aktualisiert `save` den Index und entfernt den alten Namen. `PlayerService#findKnownIdsByName` bevorzugt aktuelle geladene Namen und filtert veraltete Disk-Namen geladener Spieler. Kollisionen werden nicht willkürlich entschieden; `/profile <uuid>` bleibt die eindeutige Alternative. Ein Lookup scannt nicht erneut das Verzeichnis. Der Index ist keine zusätzliche Datenbank oder Datei; das Player-YAML-Schema bleibt unverändert. Bukkit-`OfflinePlayer`, Mojang-Requests und externe UUID-Suche werden nicht benutzt.

`EconomyService#getKnownCoins(UUID)` liest beim geladenen Player den aktuellen Wallet-Stand, bei bekanntem Offline-Player dessen persistiertes Wallet und liefert bei unbekannter UUID `OptionalLong.empty()`; `getCoins` und alle Mutationen behalten ihre Loaded-Player-Semantik. Der Online-Status stammt vom tatsächlichen Paper-Player (`isOnline`), nicht vom Cache. Nur online werden `RankService#getPrimaryRank` (LuckPerms-Cache, `RankInfo.displayComponent()` für die Farbe) und `Statistic.PLAY_ONE_MINUTE` gelesen. Offline sind Rank und Playtime bewusst leer; weder Rank noch Reward-Progress werden als Profile-Snapshot persistiert. Der gemeinsame `dev.vapee.core.format.PlaytimeFormatter` hält das bisherige Format; `presentation.PlaytimeFormatter` delegiert aus Kompatibilitätsgründen dorthin.

`/profile` zeigt dem Spieler das eigene Profil; die Console muss `/profile <player|uuid>` angeben. Mit einem Ziel funktionieren Online-Name, bekannter Offline-Name und bekannte UUID. Unbekannte oder mehrdeutige Namen erhalten kontrollierte Meldungen, Lesefehler werden geloggt und nicht als Stacktrace in Chat ausgegeben. `vapeecore.profile.view` ist standardmäßig für alle erlaubt. Tab Completion zeigt nur Online-Namen, keine Liste aller bekannten Offline-Spieler. Spielernamen werden ausschließlich als `Component.text` ausgegeben, nie als MiniMessage ausgewertet. First/Last Join werden als `yyyy-MM-dd HH:mm` in der Server-Zeitzone gezeigt; Last Join ist kein Last Seen. Kein GUI und kein neues Hauptmenü.

## Moderation Foundation (Phase 24)

Ownership seit Phase 26A: `ModerationModule` hängt von `JavaPlugin`, `IdentityModule`, `RankModule` und `MessageService` ab und steht weiterhin zwischen Identity und Friend. Beim Enable lädt es Repository/Service lokal, initialisiert die committed Mute-Projektion und installiert sieben Executor-/Completer-Paare sowie Login- und Chat-Listener und veröffentlicht erst danach seine Service-Referenz. Beschädigte Daten lassen Enable scheitern; partielle Hook-Fehler werden lokal bereinigt, bevor `ModuleManager` Vorgänger rückwärts stoppt. Disable deregistriert alle eigenen Hooks, nullt Runtime-Referenzen und speichert nicht erneut. Domain, Schema und Copy-on-write bleiben aus Phase 24 unverändert; lokale Factory-/Clock-/UUID-/Hook-Seams testen den tatsächlichen Lifecycle.

Domain und Service enthalten keine Bukkit-Player, Identity-, PlayerService-, Economy-, Rank-, Chat- oder PM-Abhängigkeiten. `ModerationService` besitzt genau einen `ModerationSnapshot` als Wahrheit; keine getrennten active/history Maps, Dirty-Queue oder zweite Persistence. Die gesamte API ist Main-Thread-owned, einschließlich Reads: spätere Async-Consumer müssen zuerst auf den Serverthread zurückkehren oder eine ausdrücklich entworfene immutable Projektion verwenden. Seit Phase 25B ist ausschließlich ModerationMuteProjection async-lesbar; die Service-API bleibt vollständig Main-Thread-owned.

`ModerationAction`: `WARNING` und `KICK` sind ausschließlich historische Ereignisse und dürfen weder Ablauf noch Widerruf haben. `MUTE` und `BAN` sind ohne Ablauf permanent; eine temporäre Sanktion verlangt `expiresAt > createdAt`. `ModerationActor(type, Optional<UUID> playerId)` erlaubt ausschließlich PLAYER mit UUID beziehungsweise CONSOLE ohne UUID. Namen und Bukkit-Objekte werden nicht gespeichert.

`ModerationRecord(id, action, targetId, actor, reason, createdAt, Optional<Instant> expiresAt, Optional<ModerationRevocation> revocation)` ist immutable. Record-ID und Target-ID sind getrennte UUIDs; mehrere Records pro Target sind ausdrücklich erlaubt. Reasons werden mit `strip()` getrimmt, dürfen danach nicht leer sein und höchstens 256 Unicode-Codepoints enthalten. Steuerzeichen, Zeilenumbrüche einschließlich Unicode-Line-/Paragraph-Separator sowie ungültige Surrogates werden vor dem Trimmen abgewiesen. Unicode und etwa `<red>` oder `&a` bleiben normaler literal Text, ohne MiniMessage-Verarbeitung. Derselbe Validator gilt für den optionalen Widerrufsgrund: absent ist gültig, present-blank nicht.

`ModerationRevocation(actor, revokedAt, Optional<String> reason)` erhält den ursprünglichen Record; `revokedAt >= createdAt`. Nur MUTE/BAN erlauben Widerruf. `withRevocation` erzeugt einen neuen Record mit derselben ID und unveränderten Originalfeldern, niemals einen zweiten History-Eintrag, und lehnt einen zweiten Widerruf ab. `isActiveAt(t)` gilt ausschließlich für MUTE/BAN bei `createdAt <= t`, vollständig fehlendem Widerruf und entweder fehlendem Ablauf oder `t < expiresAt`. Genau am Ablauf ist inactive. Future-created Records sind vorher inactive. Ein bereits widerrufener Record ist auch bei einer Abfrage vor `revokedAt` inactive: dies ist eine aktuelle Aktivzustandsregel, keine historische Rekonstruktion. Ablauf erzeugt keine Writes, Tasks oder Löschungen.

API: `issueWarning`, `recordKick`, `issueMute`, `issueBan`, `revokeMute`, `revokeBan`, `getActiveMute`, `getActiveBan`, `isMuted`, `isBanned`, `getHistory`, `getRecord`, `getAllRecords`. Targets sind UUIDs ohne Known-/Online-Player-Gate; Eingabeauflösung gehört zum separaten Phase-25A-Command-Adapter. Jede Mutation verwendet genau einen Zeitpunkt aus dem injizierten `Clock`; Runtime ist `Clock.systemUTC()`. Issue-IDs kommen aus einem `Supplier<UUID>`, Runtime `UUID::randomUUID`. Tests injizieren beide Quellen. Nullwerte und ID-Kollisionen scheitern ohne Save.

`ModerationResult`: SUCCESS enthält den erfolgreich gespeicherten neuen oder widerrufenen Record. ALREADY_MUTED, ALREADY_BANNED, NOT_MUTED und NOT_BANNED enthalten konsequent keinen Record und erzeugen keinen Save. Ein zweiter aktuell aktiver Mute/Ban wird abgewiesen; beide Typen dürfen gleichzeitig aktiv sein. Nach Ablauf oder Widerruf ist Reissue erlaubt, die alten Fakten bleiben erhalten. Revoke sucht ausschließlich aktuell aktive Fakten: absent, expired oder revoked liefert NOT_*. Falls geladene Fakten zeitlich überlappen, wählen Active-Abfragen deterministisch den neuesten aktiven Record, dann die kleinste lexikografische UUID; die Foundation verwirft keine gültige History wegen eines solchen Overlaps.

`ModerationSnapshot` prüft alle Nullwerte und doppelte Record-IDs und kopiert die Records immutable. Speicherreihenfolge ist `createdAt ASC`, dann kanonischer UUID-String ASC. History ist `createdAt DESC`, UUID-String ASC. Alle Listen sind immutable und alte Views bleiben bei späteren Änderungen unverändert. Copy-on-write: neuer Record beziehungsweise Ersatz eines widerrufenen Records → vollständiger validierter Kandidat → `repository.save(next)` → `committedPublisher.accept(next)` (ausschließlich lokale Projektion) → `state = next` → erfolgreicher Return. Der alte öffentliche Service-Konstruktor bleibt mit No-op-Publisher kompatibel; der zusätzliche Konstruktor ist package-private. Jede Repository-Exception propagiert; alle sechs Mutationen behalten bei Fehler exakt denselben vorherigen Snapshot, History und Active-State. Kein verzögertes Save und kein Save bei Reads.

### `moderation.yml`: vollständiges Schema 1

Einzige Datei: `plugins/VapeeCore/moderation.yml`. Es gibt weder Resource-Default noch Player-YAML-Felder, Migration oder Bukkit-Banlisten-Synchronisierung. Missing file lädt leer und legt nicht einmal das Parent-Verzeichnis an; der erste erfolgreiche Save erstellt Parent und Datei. Eine tatsächlich vorhandene leere, beschädigte, falsch typisierte oder unbekannt versionierte Datei ist ein Fehler, kein Empty-State-Fallback. Start, `/core reload` und Shutdown schreiben eine fehlende Datei nicht.

Alle unten gezeigten Keys sind Pflicht, auch nullable Keys. `records` ist eine Liste. Optionals werden als explizites YAML-null geschrieben. UUIDs sind kanonische Strings; beim Load ist Groß-/Kleinschreibung erlaubt. Enums müssen exakt geschrieben sein. Root erlaubt ausschließlich `schema-version` und `records`; Record, Actor, Revocation und Revocation-Actor haben ebenfalls exakt definierte Keys.

```yaml
schema-version: 1
records:
  - id: "00000000-0000-0000-0000-000000000001"
    action: MUTE
    target: "00000000-0000-0000-0000-000000000090"
    actor:
      type: PLAYER
      player: "00000000-0000-0000-0000-000000000080"
    reason: "Café 😀 <red>literal</red>"
    created-at: "2026-09-30T12:00:00.123456789Z"
    expires-at: "2026-09-30T13:00:00.123456789Z"
    revocation:
      actor:
        type: CONSOLE
        player: null
      revoked-at: "2026-09-30T12:30:00Z"
      reason: null
```

Zeitformat bewusst verlustfrei: `created-at`, present `expires-at` und `revoked-at` sind kanonische UTC-ISO-8601-Strings exakt wie `Instant.toString()`, validiert mit `Instant.parse()` und anschließendem Canonical-Vergleich. Anders als Epoch-Millis bleiben Nanosekunden und damit exakte Ablaufgrenzen sowie die volle Instant-Domain beim Roundtrip erhalten. Locale-Daten, Offsets statt Z, numerische Werte, Date-only, ungültige Kalendertage und nichtkanonische Strings werden abgewiesen. Dieses neue Schema ist kein Wechsel am Clan- oder Player-Schema.

`FileModerationRepository` liest UTF-8 mit SnakeYAML `SafeConstructor` und `allowDuplicateKeys=false`. Eine kleine lokale SafeConstructor-Erweiterung lehnt außerdem YAML-Merge-Schlüssel ab, bevor SnakeYAML sie entfernen könnte. Unbekannte/missing Keys, falsche Typen, unsafe Tags, zusätzliche Dokumente, nichtkanonische UUIDs, unbekannte Enums, doppelte IDs und sämtliche Domain-/Zeitinvarianten lassen den Load mit Dateikontext scheitern; die Quelldatei bleibt byte-identisch. Es gibt kein Skip einzelner Sanktionen, Reparieren oder Überschreiben auf Load.

Save schreibt deterministische LinkedHashMaps in Snapshot-Reihenfolge in eine UTF-8-Sibling-Tempdatei, schließt den Writer und versucht `ATOMIC_MOVE` mit `REPLACE_EXISTING`. Nur `AtomicMoveNotSupportedException` führt zum kontrollierten Replace-Fallback. Andere I/O-/Runtime-Fehler propagieren als `ModerationRepositoryException` mit Dateikontext. Fehlgeschlagenes Replace vor dem Move lässt die alte Datei lesbar und unverändert; Temp-Cleanup läuft best-effort und loggt Cleanup-Probleme über den injizierten Logger. Das Fallback verspricht keine Atomicität bei hartem JVM-/OS-Abbruch; mehrere externe Writer werden nicht koordiniert. Der Service ist der einzige Runtime-Writer, die Datei daher nie während laufender Mutationen manuell bearbeiten.

### Phase 25A – Moderation Commands und Ban-Enforcement

Phase 25A ergänzt ausschließlich `/warn <player|uuid> <reason...>`, `/ban <player|uuid> <duration|permanent> <reason...>`, `/unban <player|uuid> [reason...]`, `/kick <player|uuid> <reason...>` und `/history <player|uuid> [page]`. Owner bleibt ModerationModule; Module Count 26, Reload Count 6. Kein neuer Root-Dispatcher, GUI, Config-Key, Schemafeld oder Bukkit-Banlist-Abgleich. Vollständige Verifikation: `docs/MODERATION_TOOLS.md`.

Alle fünf Commands prüfen ihre unabhängige `vapeecore.moderation.<command>`-Permission zusätzlich zu `plugin.yml`; Defaults sind `op`, ohne Aliases, Children oder Wildcard. `CoreCommand` zeigt die neue Moderation-Section permission-gefiltert. Player wird zu `ModerationActor.player(UUID)`, Console zu `ModerationActor.console()`; andere Sender werden nicht still als Console auditiert. Alle Targets gehen zuerst durch `PlayerIdentityService.resolve`: Known name/UUID, online und offline, eindeutiger vollständiger Name, kein Phantom-UUID-/OfflinePlayer-/Netzwerk-Lookup. Self-Warn/Ban/Unban/Kick ist gesperrt; Self-History mit Permission erlaubt.

`ModerationDurationParser` akzeptiert case-insensitive `permanent`, `perm` oder genau eine positive dezimale Ganzzahl plus `s/m/h/d/w`. Keine Whitespace-Toleranz, Vorzeichen, Dezimalwerte, Exponenten, Combined-, Monats- oder Jahresdauer. `Math.multiplyExact` sowie Instant-Addition lehnen Overflow kontrolliert ab. Command und Service teilen denselben injizierten UTC-Clock. Feedback zeigt normalisierte kurze Dauer, Ban-Screen und History absolute UTC-Zeit. Für Extremwerte der vollständigen Instant-Domain gibt es einen kanonischen UTC-Fallback statt eines Renderingfehlers.

Warn/Ban/Unban/Kick mutieren nur über ModerationService. Save → State-Swap → INFO-Audit (Actor, Target UUID/Name, Action, vollständige Record-ID und Expiry) → Feedback → externe Aktion. Warn benachrichtigt nur online, niemals als Offline-Queue. Ban verwendet denselben Ban-Screen für Online-Disconnect und Login. Kick validiert echte Online-Präsenz vor `recordKick`, erzeugt offline keinen Record und nutzt einen eigenen Kick-Screen. ALREADY_BANNED/NOT_BANNED sind normale No-ops, ohne Save, Success-Audit oder erneuten Kick. Repositoryfehler erhalten SEVERE mit Actor/Target/Action und kontrollierte Senderausgabe; weder Notification noch Disconnect. Eine nachträgliche Notification-/Disconnect-Ausnahme erhält SEVERE und die ausdrücklich gespeicherte, aber extern fehlgeschlagene Aktion als Feedback; persistierte Fakten werden nicht zurückgerollt oder erneut angelegt.

`ModerationLoginListener` läuft bei `PlayerLoginEvent` mit HIGHEST auf dem synchronen Serverthread und fragt ausschließlich `getActiveBan(UUID)` ab. Active permanent/temporary → KICK_BANNED mit Adventure-Component; exakt am Ablauf, danach oder widerrufen → kein eigener Deny. Kein Join-/CorePlayer-Load nötig; kein Save, Expiry-Cleanup oder History-Rewrite. Kein AsyncPlayerPreLoginEvent oder AsyncChatEvent-Servicezugriff. Unerwarteter Queryfehler wird mit UUID als SEVERE geloggt, erfindet keinen Ban und hebt andere Rejections nicht auf. Paper 1.21.11 markiert diesen gewünschten kompatiblen Event bereits deprecated; die spätere API-Modernisierung muss die Main-Thread-Grenze ausdrücklich bewahren.

History übernimmt Service-Reihenfolge, hat fünf Records pro Seite und validiert positive ganze Seiten overflow-sicher. Empty State und nicht vorhandene Seiten sind konkret benannt. Jeder Eintrag zeigt Action, UTC-Erstellung, Actor (Known name/Console/UUID-Fallback), literal Reason und Recorded/Active/Expired/Revoked/Inactive; BAN/MUTE zusätzlich Expiry/Permanent, Widerruf zusätzlich Actor/Zeit/optionalen Reason. Die vollständige Record-ID steht im Hover. Previous/Next verwenden ausschließlich SUGGEST_COMMAND mit Target-UUID. Namen/Reasons sind stets Component.text, keine MiniMessage-Auswertung. Completion zeigt primär Online-Targets; Unban leitet aktive Target-UUIDs aus dem vorhandenen Snapshot ab. Eindeutige bekannte Namen, sonst UUID, deterministische Prefix-Filter; Self bei Mutation ausgeschlossen. Nur gezielte Identity-Lookups, kein Verzeichnis-/globaler Known-Player-Scan.

Seit Phase 26A ergänzt eine explizite Staff-Target-Hierarchie die weiterhin erforderlichen Permissions für alle sieben Moderationsbefehle. Die primäre Group-ID wird ausschließlich gegen die konfigurierte Schutzliste ausgewertet; Weight, Prefix und Track-Position bleiben reine Darstellung bzw. Metadaten.

### Phase 26A – Staff hierarchy / command authorization

`RankModule` besitzt zusätzlich den read-only `rank.staff.StaffHierarchyService` und veröffentlicht ihn nur nach erfolgreichem Enable; Disable entfernt die Referenz auch bei Hook-Fehlern. Rank kennt Moderation nicht. `VapeeCore` injiziert das vorhandene RankModule ausdrücklich in Moderation. Es gibt weder ein neues Modul noch einen zusätzlichen ReloadParticipant.

`StaffHierarchyConfig(List<String> protectedGroups)` ist immutable und normalisiert IDs trim/case-insensitive (`Locale.ROOT`). LOW→HIGH-Defaults sind builder, moderator, admin, owner. Der ConfigService liest `staff.hierarchy.protected-groups` in seinen bestehenden volatilen immutable `CoreConfigState`. Fehlend bedeutet kompatibler Default; explizit ungültig (auch null/empty/Controls/Whitespace/Duplikate) bedeutet WARNING + vollständiger Default, ohne Dateiänderung. Ein lokaler YAML-Node-Check erhält den Unterschied zwischen missing und explizitem null, das Bukkit ansonsten entfernt. Prepare mutiert nichts, Apply tauscht den Snapshot, Rollback stellt ihn wieder her. Der Service liest bei jeder Entscheidung genau einen Config-Snapshot für beide Levels.

`StaffTargetDecision` unterscheidet ALLOW, DENY_SAME_OR_HIGHER, DENY_ACTOR_NOT_PROTECTED und UNAVAILABLE. Bekannte ungeschützte Targets benötigen nur die Command-Permission. Geschützte Targets benötigen einen geschützten Actor mit strikt größerem Index; same/higher einschließlich owner→owner ist verboten. Primäre LP-Gruppe ist die einzige Level-Quelle. Keine Weight-/Prefix-/Suffix-/Color-/Track-/Inheritance-Maximum-/OP-Auswertung, keine Player-YAML-Quelle, keine eigene Staff-Mitgliedschaft oder langfristiger Gruppencache. Console umgeht nur die Hierarchie. Self-Mutation wird vorher abgewiesen; Self-History bleibt mit Permission erlaubt.

Command-Fluss: Permission → Sender/Actor → Arity → Known-Identity → Self-Regel → Hierarchie → command-specific execute → Domain-Save → Audit/Feedback/Notification/Disconnect. `AbstractModerationCommand` bindet alle sieben Commands an dieselbe lokale Grenze. Bereits geladene Zielgruppen werden synchron gelesen; bei fehlendem LP-User startet `LuckPermsService#loadPrimaryGroup(UUID)` einen CompletableFuture-Load. Der async Load ist ausschließlich eine Load Barrier; sein Gruppenergebnis ist nach dem Thread-Handoff keine Authority. Keine join/get-Warteoperation, keine Schleife, kein Sleep, keine LP-Saves oder Group-Mutationen. Der LP-Callback reicht ausschließlich Completion/Fehler über einen Runnable an den injizierten `Consumer<Runnable>` weiter; er berührt weder Bukkit-Player noch ModerationService/MessageService.

Production setzt einmalig `Scheduler.runTask(plugin, task)` ein. Erst die Hauptthread-Fortsetzung prüft die per-Enable aktive Generation, dieselbe ursprüngliche Online-Player-Session und aktuelle Command-Permission. Logout, Reconnect oder Disable/Re-enable verwirft alte Befehle ohne Mutation. Nach erfolgreichem Load ist der Authorization Point das Main-Thread-Resume: `StaffHierarchyService#decideLoaded(actorId, targetId)` liest die aktuellen geladenen Actor- und Target-Primary-Groups unter einem aktuellen immutable Config-Snapshot. Fehlt dort die aktuelle valide Target-Gruppe, gilt UNAVAILABLE und fail closed, auch bei einem zuvor gültigen Future-Ergebnis; kein Fallback auf die alte Gruppe. Load-Fehler und benötigte empty/invalid Gruppen werden SEVERE mit Actor/Target/Action-Kontext protokolliert und kontrolliert ohne Rangdetails gemeldet; kein Save, Success-Audit oder externe Aktion. Scheduling-Fehler loggen nur und führen keine Async-Player-Rückmeldung aus. Dies ist eine minimale lokale Fortsetzungsgrenze, kein allgemeines Async-Command-Framework.

Completion ist nicht die Security Boundary. Bekannte Online-Targets mit sicher synchron verfügbaren Gruppen werden gemäß derselben Regel gefiltert; unknown/invalid online groups werden verborgen. Self-History bleibt sichtbar, Self-Mutationen nicht. Aktive bekannte Offline-Unban/Unmute-Kandidaten bleiben aus dem committed Moderation-Snapshot erhalten. Keine N+1-LP-Loads während Completion; die Ausführung bleibt autoritativ.

`ModerationService`, Schema 1, ModerationRecord, Copy-on-write, Mute-Projektion, Chat-/PM-Enforcement und Login-Ban-Enforcement bleiben unverändert. Vorhandene Sanktionen werden weder migriert noch rückwirkend aufgehoben. Utility-/Teleport-/Inventory-/Economy-Target-Schutz sowie finaler Permission-/Inheritance-Audit sind seit 26B abgeschlossen. Empfohlene Parent-Struktur: vip→default, builder→default, moderator→default, admin→moderator, owner→admin; keine automatische Umsetzung. Sie ist nicht die Staff-Level-Quelle.

Verifikation: Hierarchie-Matrix im bestehenden ModerationCommandHarness, fokussierte StaffHierarchyServiceHarness und LuckPermsAsyncHarness, Config-/Default-/Lifecycle-Erweiterungen, alle bestehenden Harnesses, Maven und Paper/Reload. Umfang und Ergebnisse stehen in `docs/STAFF_HIERARCHY.md`.

### Phase 25B – Mute and Communication Enforcement

`ModerationModule` besitzt Service und `ModerationMuteProjection`. Beim Enable publiziert der package-private Service-Publisher die vollständig validierte geladene Historie, bevor Commands/Listener installiert oder Getter verfügbar werden. Jede erfolgreiche Mutation publiziert nach Save und vor Service-Return; No-ops und Save-Fehler publizieren nicht. Die Projektion gruppiert ausschließlich MUTE-Records in `volatile Map<UUID, List<ModerationRecord>>`; Map und deterministisch nach HISTORY_ORDER sortierte Listen sind immutable. Ein kompletter Kandidat wird vor dem einzigen volatile Write gebaut. Reads erfassen eine State-Referenz und einen Clock-Zeitpunkt, prüfen `isActiveAt(now)` und behalten die originale Expiry-/Revocation-Semantik einschließlich future-created/overlapping Records. Keine Boolean-only-Zweitwahrheit, Repository-/Bukkit-/Identity-Abhängigkeit, Dateiscans oder Ablaufwrites.

`MuteCommand`/`UnmuteCommand` verwenden die vorhandenen Actor-/Permission-/Known-Identity-/Self-Gates, DurationParser und Service-Mutationen. ALREADY_MUTED/NOT_MUTED sind No-ops. Reihenfolge: Save → Projektion → Servicezustand → INFO-Audit → Senderfeedback → Online-Notice. Gründe bleiben `Component.text`; Mute-Notice zeigt Reason und Permanent/absolute UTC, Unmute-Notice optionalen Widerrufsgrund. Offline keine Queue. Notification-Fehler loggen SEVERE und melden den bereits gespeicherten Status; kein Rollback. Unmute-Completion liest aktive committed MUTE-Records, löst einzelne sichere Namen auf und nutzt bei Ambiguität/unsicheren Namen UUIDs; Prefix-Sortierung deterministisch, Self ausgeschlossen.

`ModerationMuteChatListener` läuft bei AsyncChatEvent mit LOWEST/ignoreCancelled. Er liest nur Spieler-UUID und Projektion und cancelt vor dem unveränderten NORMAL/ignoreCancelled-ChatListener. Ein einmaliger `runTask(plugin, task)` führt Feedback auf dem Hauptthread aus, mit neuer UUID→Online-Player-Auflösung. Der Task erfasst keinen Player, Service oder Repository. Scheduler-Fehler werden kontrolliert SEVERE geloggt und heben Cancellation niemals auf. Disable deaktiviert den Listener vor Deregistrierung, sodass bereits eingereihte Feedback-Tasks nichts mehr senden; danach Projektion clear und Referenzen null. Kein Poller, kein Blocked-Message-Auditspam.

PrivateMessageService erhält ein finales `Predicate<UUID>` (`projection::isMuted`). Alte öffentliche und lokale Testkonstruktoren delegieren auf `id -> false`. Send-Reihenfolge: enabled → sender loaded → sender muted → self → recipient online → recipient loaded/settings → Ignore → Render/Send → Conversation. Reply prüft enabled/loaded/mute vor Partner-Lookup und Cleanup. SENDER_MUTED rendert/sendet keine PM und verändert keine Konversation; `/msg`, `/reply` und Alias `/r` zeigen dieselbe kontrollierte Meldung. Empfangen bleibt für gemutete Spieler erlaubt, unter bestehenden Settings/Ignore-Gates. Config-Reload/Rollback ersetzt nur RuntimeState, niemals das Predicate; weiterhin sechs Reload-Teilnehmer.

26 Module, 36 Root-Commands, sieben unabhängige op-Permissions. Partielle Enable-Fehler an allen sieben Command- und beiden Listener-Grenzen werden bereinigt; Getter bleiben unavailable und Retry funktioniert. Moderation ist kein Reload-Teilnehmer. Phase 25B ergänzte keine Rank-Hierarchie, Freeze-, Bypass- oder globale Command-Sperre, Schemaänderung oder neue Player-Persistence. Phase 26A ergänzt anschließend ausschließlich die Moderations-Target-Hierarchie; Utility/Economy-Hardening wurde anschließend in Phase 26B abgeschlossen. Historische Berichte COMMAND_AUDIT, MODERATION_FOUNDATION und MODERATION_TOOLS sowie FORMATTING bleiben unverändert. Bericht: `docs/MODERATION_MUTES.md`.

## Friends Foundation und Integration (Phase 18A.1/18A.2)

`FriendModule` startet direkt nach Moderation und vor Presence/Clan/Reward. Es injiziert Plugin, `ConfigService`, Player, Social, Identity, `MessageService` und den gemeinsamen Help-Renderer. Es besitzt `FriendService`, das zentrale `FileFriendRepository` für `plugins/VapeeCore/friends.yml`, den `/friend`-Command mit Alias `/friends` und seit Phase 18B genau einen GUI-Listener. Beim Disable schließt es offene eigene Inventare, meldet den Listener ab und entfernt Executor und Tab-Completer. Bei einem fehlgeschlagenen Enable werden teilweise registrierte Listener und Command-Handler ebenfalls entfernt. Es besitzt keinen Task oder weiteren Reload-Teilnehmer und hat keine Economy-, Rank-, Quest- oder Presentation-Abhängigkeit.

`friends.yml` ist die einzige Friends-Datendatei und enthält `schema-version: 1`, ungerichtete Freundschaften und gerichtete Anfragen mit UUIDs und Zeitpunkten. Eine fehlende Datei bedeutet einen leeren State; das erste erfolgreiche Speichern erzeugt sie. Der Repository-Start validiert den vollständigen Snapshot; beschädigte oder nicht unterstützte Daten lassen das Modul kontrolliert scheitern statt sie zu überschreiben. Mutationen schreiben zuerst einen neuen, validierten Snapshot per temporärer Datei und atomarem Move, mit Replace-Fallback falls das Dateisystem keinen atomaren Move unterstützt. Erst nach erfolgreichem Save tauscht `FriendService` den In-Memory-State aus; ein Fehler behält den vorherigen Zustand. Domain und Service speichern keine Bukkit-`Player`-Referenzen. Lese-APIs für Beziehung, Freunde, eingehende und ausgehende Anfragen bleiben unveränderliche Snapshots.

`FriendService#sendRequest` prüft Self, bestehende Freundschaft/Anfrage, Policy und Limits. Eine Gegenanfrage wird atomar als Freundschaft angenommen. `acceptRequest` nimmt nur eine vorhandene eingehende Anfrage an; `denyRequest`, `cancelRequest` und `removeFriend` entfernen nur ihre jeweilige gerichtete Anfrage oder Freundschaft. `FriendLimits` stammen als immutable View aus `ConfigService`: `friends.limits.max-friends: 100`, `max-incoming-requests: 25`, `max-outgoing-requests: 25`. Fehlende oder ungültige nichtpositive Werte warnen und verwenden Defaults. Der Service liest die aktuellen Limits bei jeder relevanten Operation, daher wirken erfolgreiche `/core reload`-Änderungen ohne Friend-Reload. Bereits bestehende Beziehungen/Anfragen werden bei einer Senkung nicht gelöscht; neue Aktionen beachten die neuen Grenzen. Ein fehlgeschlagener Reload stellt den vorherigen Config-Snapshot wieder her.

Die Policy prüft `SocialService#isKnownIgnoring` in beiden Richtungen, dann `PlayerSettingsService#areKnownFriendRequestsEnabled` beim Empfänger. Beide Abfragen verwenden für geladene Spieler den aktuellen State und für bekannte Offline-Spieler die persistierten Player-Daten lesend, ohne sie in den Player-Cache zu laden. Unbekannte Empfänger werden abgelehnt. Ignore blockiert neue und anzunehmende Anfragen; deaktivierte `settings.friend-requests` verhindern nur neue Anfragen, nicht das Annehmen bestehender. Der Key ist standardmäßig `true`, wird beim Player-Save unter `settings.friend-requests` gespeichert und im 54-Slot-`SettingsMenu` über Feature 16 oder Status 25 umgeschaltet. Fehlende/alte Player-Keys laden als `true`, ein Save-Fehler rollt den Settings-Wert zurück.

`/friend help|add|accept|deny|cancel|remove|list|requests` ist ausschließlich für Spieler mit `vapeecore.friend.use` (Default `true`). `/friend` und `/friends` ohne Argumente öffnen die GUI; explizites `/friend help` nutzt weiterhin den gemeinsamen Help-Renderer. `FriendCommand` nutzt `PlayerIdentityService#resolve` für bekannte Namen/UUIDs, weist unbekannte und mehrdeutige Namen kontrolliert ab und zeigt Namen als Adventure-Text mit UUID-Fallback an. Erfolgreiche neue Anfragen und Annahmen benachrichtigen den Gegenpart nur, wenn er gerade online ist; es gibt keine Offline-Mail. Command und GUI teilen die kleine `FriendMessages`-Logik für Resultate und Online-Benachrichtigungen. Tab Completion beschränkt sich unverändert auf sinnvolle Subcommands und bekannte beziehungsweise online sichtbare Ziele. Kein Teleport und keine zusätzlichen Player-YAML-Friends-Listen; der spätere Presence-Service liest Freundschaften ausschließlich über `getFriends`.

### Notifications & Presence (Phase 27)

`PresenceModule` startet direkt nach Friend und vor Clan. Es ist ein normales `CoreModule`, kein `ReloadParticipant`, und konsumiert ausschließlich `PlayerModule`, `SocialModule`, `FriendModule` und `MessageService`. Sein `PresenceService` ist main-thread-owned und hält nur ein `HashSet<UUID>`: `markOnline` und `markOffline` liefern ausschließlich bei einem echten Übergang `true`, Reads und Snapshots sind null-sicher beziehungsweise unveränderlich. Es gibt keine Bukkit-Referenz, Datei, Queue, Schema-Version, Config, Permission, Command oder Scheduler. Enable registriert genau einen Listener und seedet anschließend bereits online befindliche Spieler nur dann, wenn `PlayerService#isLoaded` gilt; dieser Seed erzeugt keine Meldungen. Ein Registrierungs- oder Seed-Fehler deregistriert den Listener und leert den Kandidatenstate. Disable meldet den Listener ab, leert Presence und verwirft alle Runtime-Referenzen.

Der Join-Handler läuft auf `MONITOR`, prüft vor `markOnline` erneut den geladenen Player-State und benachrichtigt nur beim ersten Übergang. Der Quit-Handler läuft auf `LOWEST`, sodass Friend-, Settings- und Ignore-Daten noch verfügbar sind, und benachrichtigt ebenfalls nur beim echten Übergang. Presence verarbeitet weder World Change, Respawn noch Teleport und verändert niemals die von `LobbyExperienceListener` besessenen globalen Join-/Quit-Components.

`FriendPresenceNotifier` fragt ausschließlich `FriendService#getFriends(subject)` ab; Pending Requests und historische Beziehungen sind damit ausgeschlossen und die vom Service gelieferte UUID-Reihenfolge bleibt erhalten. Pro Empfänger gelten nacheinander: tatsächlicher Paper-Player vorhanden und online, geladenes Setting vorhanden und `true`, kein Ignore in irgendeiner Richtung. Der Name des Subjects kommt direkt aus dem Join-/Quit-Event und wird mit `Component.text` eingesetzt. Die Body-Texte sind exakt `Friend <name> is now online.` und `Friend <name> went offline.`; `MessageService` ergänzt nur den zentralen Prefix. Es wird kein Presence-Sound abgespielt. Ein Friend-Lookup-Fehler wird kontrolliert gewarnt; Lookup-, Settings-, Social- oder Sendefehler eines einzelnen Empfängers enthalten Subject-/Recipient-UUID im Log und lassen alle späteren Empfänger weiterlaufen. Der bereits vollzogene Presence-Übergang wird niemals zurückgerollt.

`PlayerSettings.friendPresenceNotificationsEnabled` ist ein opt-in Boolean mit Default `false`. `FilePlayerRepository` speichert ihn als `settings.friend-presence-notifications`; fehlende Legacy-Keys bleiben `false`, ungültige optionale Werte warnen und fallen auf `false` zurück, ohne Eager-Rewrite. `PlayerSettingsService#areFriendPresenceNotificationsEnabled` liest nur geladene Settings. Der Setter verwendet dieselbe sofortige Ganzprofil-Persistence und denselben exakten Runtime-Rollback wie die übrigen Boolean-Toggles. Im Settings-Root liegen `Friend Presence` (`BELL`) in Slot 33 und sein rot/grüner Status in Slot 42; beide toggeln denselben Wert und behalten alle bestehenden Holder-, Inventory- und Klick-Sicherheitsgrenzen. Weitere Details und Verifikation stehen in `docs/NOTIFICATIONS_PRESENCE.md`.

### Friends-GUI (Phase 18B)

`FriendMenu` besitzt die Darstellung und eine instanzgebundene Registry aktuell geöffneter Inventare; `FriendMenuListener` besitzt ausschließlich die Inventory-Events und delegiert jede fachliche Mutation an `FriendService`. Der 54-Slot-View hat 45 Content-Slots und die Navigation unten: 45 Previous, 46 Add Friend, 47 Friends, 48 Incoming, 49 Close, 50 Outgoing, 52 Refresh, 53 Next. `FriendMenuView` unterscheidet `FRIENDS`, `INCOMING` und `OUTGOING`. Eine leere Liste bleibt gültige Seite 0; Seiten werden nach einer Mutation auf die letzte existierende Seite begrenzt. Freundeseinträge werden stabil nach bekanntem Namen ohne Groß-/Kleinschreibung, dann UUID sortiert. Fehlende Identity-Namen erscheinen als UUID. `Server#getPlayer(UUID)` plus `isOnline()` ist die einzige Online-Quelle; für Offline-Spieler werden weder Skin- noch Netzwerk-/`OfflinePlayer`-Lookups gestartet.

`FriendMenuHolder` speichert Besitzer-UUID, View, Page und die serverseitige Content-Slot→Target-UUID-Zuordnung und bindet genau eine Inventory-Instanz. Der Listener cancelt zunächst jeden Click/Drag eines FriendMenu-Holders, auch in Bottom Inventory oder bei gefälschtem Holder. Eine Aktion erfordert zusätzlich passenden Viewer, exakt gebundenes Inventory und Registrierung als aktuell geöffnetes Menü; alte oder nachgebildete Inventare bleiben inert. Weder Titel noch Item-Name, Lore, SkullMeta oder NBT sind Sicherheits- oder Target-Quelle. Bei Close/Quit wird der aktive Verweis nur für genau dieses Inventar entfernt, beim Modul-Disable werden noch offene Friends-Inventare geschlossen.

Im Friends-Tab entfernt nur Shift + Rechtsklick; normale Klicks bleiben inert. Eingehend bedeutet Linksklick Accept und Rechtsklick Deny, ausgehend bedeutet Links- oder Rechtsklick Cancel. Bei jedem Klick prüft der `FriendService` die Relation erneut; verschwundene Requests/Freundschaften erhalten eine kontrollierte Meldung und die Ansicht wird neu aufgebaut. Nach erfolgreichem Accept wird der online befindliche Sender wie beim Text-Command benachrichtigt. Persistenz-/Runtime-Fehler werden mit UUID-Kontext geloggt, die GUI wird geschlossen und der Spieler erhält nur eine kurze Fehlermeldung. Der Add-Button schließt die GUI und sendet eine Adventure-Component mit `suggestCommand("/friend add ")`; er startet kein Chat-, Sign- oder Anvil-Capture. Es gibt keinen Tick-Refresh, keine zweite Privacy-Einstellung und keine neue Permission.

## Clan Integration und GUI (Phase 19B)

`ClanModule` startet direkt nach Presence und vor Reward. Es besitzt `ClanService`, `FileClanRepository`, `/clan` (Alias `/clans`), die drei Clan-Menüansichten und genau einen GUI-Listener. Bei Enable werden alle Bestandteile vor Veröffentlichung der Runtime-Referenzen aufgebaut; ein partieller Fehler entfernt Listener und Command-Handler. Disable schließt eigene offene Inventare, deregistriert Listener und Handler und leert Referenzen. Es gibt weder Scheduler noch einen zusätzlichen Reload-Teilnehmer. Die Clan Foundation (`Clan`, `ClanMember`, `ClanInvite`, `ClanSnapshot`, `ClanService`) bleibt Bukkit-arm und ist Source of Truth; das UI liest und mutiert ausschließlich über den Service.

`plugins/VapeeCore/clans.yml` ist die einzige Clan-Datendatei. Eine fehlende Datei lädt als leerer Snapshot und wird nicht voreilig geschrieben. Der Repository-Validator prüft `schema-version: 1` und alle Domain-Invarianten. Mutationen speichern vor dem In-Memory-Tausch über eine temporäre Datei; Fehler lassen den alten Zustand intakt und werden mit Spieler-UUID geloggt, nicht im Chat als Stacktrace gezeigt. `ConfigService` liefert `clans.limits` als immutable View mit Defaults 25 Mitglieder, 25 ausgehende, 10 eingehende Einladungen, Namenslänge 3–24 und Taglänge 2–8. Ungültige Werte warnen und fallen auf sichere Defaults zurück. `ClanService` bezieht den Supplier `configService::getClanLimits`; nach erfolgreichem `/core reload` gelten neue Limits für künftige Mutationen, vorhandene Daten werden nicht gelöscht.

`/clan` ist Player-only und prüft `vapeecore.clan.use` (Default `true`). `/clan help` verwendet den gemeinsamen Help-Renderer. `create <tag> <name...>` und `rename <name...>` erhalten mehrteilige Namen. `info`, `accept` und `deny` lösen exakten Clan-Tag oder kanonische Clan-UUID auf. `invite`, `cancel`, `kick` und `transfer` nutzen `PlayerIdentityService#resolve` für bekannte Namen/UUIDs; unbekannte oder mehrdeutige Namen werden kontrolliert abgelehnt. Ein bereits bekannter Offline-Spieler darf eingeladen werden; es gibt weder `OfflinePlayer`- oder Netzwerk-/Mojang-Lookup noch eine Offline-Benachrichtigungsqueue. `ClanMessages` teilt Result-Mapping und unmittelbare Online-Notifications zwischen Command und GUI und setzt User-Namen ausschließlich als literale Adventure-Components ein. `disband` erfordert das ausdrückliche Argument `confirm`.

`ClanMenu` zeigt 54 Slots mit 45 Content-Slots pro Seite und Views `OVERVIEW`, `MEMBERS`, `INVITES`. Clanlose Spieler sehen Create und eingehende Einladungen; Create schließt die GUI und schlägt `/clan create TAG Clan Name` per Click-Event vor. Die Übersicht zeigt Name, Tag, Mitgliedszahl, Owner und eigene Rolle. Mitglieder werden mit Owner zuerst, dann nach bekanntem Namen und UUID sortiert; fehlende Identity-Namen erscheinen als UUID. Online ist nur `Server#getPlayer(UUID)` plus `isOnline()`. Der Owner sieht ausgehende Einladungen; Mitglieder ohne Owner-Rolle erhalten keine Owner-Verwaltung. Eingehende Einladungen werden links angenommen, rechts abgelehnt; ausgehende Einladungen nur rechts abgebrochen. Mitglieder können nur mit Shift + Rechtsklick gekickt oder Shift + Linksklick zum Owner gemacht werden. GUI-Disband bietet nur mit Shift + Rechtsklick einen Vorschlag für `/clan disband confirm`, keine direkte Löschung.

`ClanMenuHolder` trägt Besitzer-UUID, View, Page und serverseitige Slot→UUID-Ziele; er bindet genau eine Inventarinstanz. Der Listener cancelt jeden Click und Drag einschließlich Bottom Inventory, Number-Key, Collect und Drop zunächst pauschal. Eine fachliche Aktion erfordert zusätzlich passenden Viewer, aktive Inventarinstanz, exakte Holder-Bindung und einen explizit erlaubten Klicktyp. Item-Name, Lore, NBT und Titel sind nie Target-Quelle. Vor jeder Mutation liest der Service die aktuelle Mitgliedschaft/Owner-Rolle und Einladungen erneut; verschwundene Ziele liefern kontrollierte Resultate und die Seite wird mit Page-Clamp neu aufgebaut. Close/Quit entfernen nur den jeweils aktiven Eintrag, Disable schließt alle offenen Clan-Inventare. Kein Tick-Refresh und kein globales GUI-Framework. Clan-Tag-Presentation in Chat, Tablist, Nametag oder Scoreboard ist ausdrücklich eine spätere Phase; `Presentation` und `presentation.yml` bleiben unverändert.

## Economy Completion (Phase 22)

`EconomyModule` bleibt Owner des EconomyService und der `/coins`-Command-Hooks. Es bezieht PlayerService aus PlayerModule und bleibt vor IdentityModule; keine Economy→Identity-/Presentation-Abhängigkeit, kein Scheduler oder ReloadParticipant. Seit Phase 27 sind es insgesamt 27 Module und unverändert sechs Reload-Teilnehmer.

`CoinWallet` hält eine nichtnegative ganze `long`-Balance, initial 0 Coins. `economy.coins` bleibt der unveränderte Player-YAML-Key; keine Migration, zusätzliche Economy-Datei, Cap oder Config. `getCoins/hasCoins` lesen nur geladene Player, `getKnownCoins` bevorzugt deren Runtime-Wallet und liest sonst bekannte gespeicherte Offline-Daten, ohne sie zu laden. Alle bestehenden APIs bleiben unverändert.

`setCoins/addCoins/removeCoins` mutieren geladene Wallets und speichern sofort genau einmal. Bei Save-Exception wird exakt die vorherige Runtime-Balance wiederhergestellt und die Exception propagiert. Auch Set auf den gleichen Wert behält seinen Save. Set erlaubt null bis Long.MAX_VALUE; Add/Remove verlangen positive Werte. Math.addExact lehnt Add-Overflow ohne Mutation/Save ab; unzureichende Coins bei Remove ebenfalls.

`addCoinsDeferred` mutiert ohne Save ausschließlich für RewardService. RewardService bleibt Owner des Dirty Tracking und Batch-Flush; Save-Fehler beim Reward-Flush behalten Runtime-Coins und Dirty-Marker für Retry, statt innerhalb Economy zurückzurollen. OnlineReward und Quest verwenden weiterhin diese Grenze. Präsentation übernimmt aktuelle Coins im normalen Update-Task; Offline-Profile verwenden weiter getKnownCoins.

`CoinsCommand` besitzt einen kleinen privaten Resolver und Target-Record, kein Lookup-Framework. Kanonische UUIDs gehen an PlayerService.findKnownPlayer, sonst vollständige Namen an findKnownIdsByName. Die vorhandene PlayerService-Semantik bevorzugt aktuelle geladene Namen gegenüber dem persistenten Index. Null Treffer sind unknown, mehrere Treffer ambiguous und verlangen eine UUID; keine Teilnamen, OfflinePlayer-, Mojang- oder Netzwerk-Auflösung. GET verwendet getKnownCoins für bekannte Online-/Offline-Ziele. Add/Remove/Set prüfen Server.getPlayer(UUID) plus isOnline sowie geladenen Playerzustand; bekannt-offline und online-but-not-loaded liefern unterschiedliche kontrollierte Fehler. Kein künstliches Load oder Save eines Offline-Snapshots.

Basispermission vapeecore.economy.coins wird für alle Command- und Completion-Pfade direkt geprüft, Adminpermission zusätzlich für get/add/remove/set. plugin.yml gibt Admin die Basispermission als Child. Der bestehende CommandHelpRenderer filtert Player-/Administration-Sections. Falsche Arity nennt die genaue Subcommand-Syntax, unbekannte Eingaben werden separat benannt. Amounts sind ausschließlich dezimale Ziffern als long: kein Vorzeichen, Komma, Bruch oder Exponent; Set erlaubt 0, Add/Remove nicht.

Nach EconomyResult.SUCCESS und erfolgreichem Save protokolliert INFO Actor (Console eindeutig CONSOLE), Target UUID/Name, Action, Amount, Previous und New Balance. Erst dann folgen Adminfeedback und kurze Notification an den weiterhin online befindlichen Target; eigene Target-UUID erhält keine zweite Nachricht. Fehlgeschlagene Mutationen erzeugen keine Success-Notification/Audit; Persistenzfehler erhalten SEVERE mit Actor/Target/Action und kurze Meldung ohne Stacktrace im Chat. Dynamische Namen und unbekannte Command-Eingaben werden ausschließlich als literal Component.text ausgegeben.

Completion zeigt permission-aware help/get/add/remove/set und primär Online-Targets. Ein aktueller Name wird nur vorgeschlagen, wenn der indexbasierte Lookup exakt diese UUID eindeutig liefert; andernfalls die UUID. Es gibt keinen Storage-Scan und keine Offline-Snapshot-Reads pro Completion. Bekannte Offline-GET-Ziele können trotzdem manuell eingegeben werden.

Future Economy Finding: Player-to-player transfers require a transaction boundary covering two wallets before /pay should be implemented.

Future Persistence Finding: Offline admin wallet mutations should only be introduced after a safe offline known-player mutation API exists that cannot overwrite a concurrently loaded/stale CorePlayer.

Tests: EconomyServiceHarness prüft Read-/Write-Grenzen, Invarianten, sofortiges/deferred Speichern und alle Rollbacks; CoinsCommandHarness prüft Permissions, Lookup, UX, Amounts, Notifications, Audit, Komponenten-Sicherheit und Completion. EconomyIntegrationHarness testet echte FilePlayerRepository-Roundtrips einschließlich Long.MAX_VALUE und unveränderter Begleitdaten sowie sourcebasierte Modul-Wiring-/Cleanup- und Permission-Prüfungen. Tatsächliches Module-Enable/Disable wird im Paper-Smoke geprüft, nicht durch einen erfundenen Standalone-JavaPlugin-Lifecycle.

## Reward Foundation

`RewardService` besitzt ausschließlich die technische Frage, **wie** ein positiver Gameplay-Coin-Reward dem geladenen Player gutgeschrieben und effizient gespeichert wird. Das jeweilige Feature bleibt Owner der fachlichen Fragen, **wann** und **warum** der Reward entsteht. Es erzeugt einen `RewardGrant(UUID playerId, long coins, RewardSource source, String reason)` oder nutzt die gleichwertige Convenience-Methode. Quellen sind `PLAYTIME`, `QUEST`, `ACTIVITY`, `EVENT`, `ACHIEVEMENT`, `ADMIN` und `SYSTEM`; Resultate sind `SUCCESS`, `PLAYER_NOT_LOADED` oder `BALANCE_OVERFLOW` und enthalten bei Erfolg die resultierende Balance.

Der Service ist Main-Thread-owned und hält niemals Bukkit-`Player`-Referenzen. Ein erfolgreicher Grant verändert das Wallet sofort über den klar abgegrenzten Deferred-Pfad des `EconomyService` und markiert ausschließlich die UUID dirty. Das `RewardModule` besitzt genau einen gemeinsamen synchronen 20-Tick-Task. Dieser flusht jeden dirty Player unabhängig von der Anzahl seiner Grants nur einmal, indem der aktuelle `CorePlayer` gespeichert wird. Damit schreiben auch zwischenzeitliche direkte Economy-Mutationen keinen veralteten Snapshot zurück.

`RewardListener` läuft bei Quit mit `LOWEST` und flusht vor dem regulären `PlayerListener` mit `NORMAL`, der den Player speichert und aus dem Cache entfernt. Beim Modul-Shutdown wird zuerst der gemeinsame Task gestoppt und danach geflusht; wegen der rückwärts laufenden Modulreihenfolge stehen Economy und Player dabei noch bereit. Ein fehlgeschlagener Save wird pro Player protokolliert, blockiert keine anderen Player und lässt dessen UUID für den nächsten Tick dirty. Das Wallet wird dabei absichtlich nicht zurückgerollt. Ist ein dirty Player bereits ungeladen, wird der Marker entfernt, weil `PlayerService#unloadPlayer` vor dem Entfernen immer gespeichert hat. Ein harter Prozessabbruch kann höchstens ungefähr das 20-Tick-Fenster seit dem letzten erfolgreichen Flush verlieren.

Direkte und administrative Operationen `EconomyService#setCoins`, `addCoins` und `removeCoins` bleiben sofort persistiert und rollen ihre Wallet-Mutation bei einem Save-Fehler weiterhin zurück. Der Deferred-Economy-Pfad ist ausschließlich eine Infrastrukturgrenze für `RewardService`; Feature-Code darf ihn nicht direkt aufrufen. OnlineReward verwendet `PLAYTIME`, die Quest Foundation `QUEST`; spätere Mine-, Minigame- oder Event-Systeme können ihre passende Quelle nutzen, ohne Reward zu koppeln.

Reward selbst führt keine Config oder eigene Datei ein und besitzt keine History, kein Ledger, keine Offline-Queue, keine Multiplikatoren, Commands, Permissions oder Player-Nachrichten. Konkrete Consumer erweitern das bestehende Player-Profil nur um ihren eigenen notwendigen Zustand: OnlineReward unter `rewards.online`, Quest unter `quests.active`. Eine exakt einmalige, über harte Abstürze hinweg garantierte Auszahlung ist deshalb weiterhin nicht Teil der Foundation.

## Online / Playtime Rewards

`OnlineRewardModule` besitzt genau einen synchronen 20-Tick-Task. Der Adapter iteriert `Server#getOnlinePlayers()`, liest `Player#getStatistic(Statistic.PLAY_ONE_MINUTE)` und übergibt UUID plus Tickwert an den Bukkit-armen `OnlineRewardService`. Der historische Statistikname bedeutet weiterhin Ticks mit 20 Ticks pro Sekunde. Presentation nutzt unverändert dieselbe Quelle für `<playtime>`; OnlineReward führt keine eigene Sekundenuhr und keine neuen Presentation-Placeholder ein. Ein technischer Fehler eines Players wird isoliert, damit der wiederkehrende Task alle anderen weiterverarbeitet.

Der persistente Domain-State ist `OnlineRewardProgress`. Initialisiert enthält er genau `processedPlaytimeTicks >= 0`; andernfalls ist er uninitialized. `FilePlayerRepository` schreibt den Wert als `rewards.online.processed-playtime-ticks`. Fehlt `rewards`, `online` oder der Key, bleibt das Profil gültig und uninitialized. Negative, falsch typisierte oder strukturell ungültige optionale Reward-Daten erzeugen eine Warnung und werden ebenfalls als uninitialized behandelt. Beim ersten Processing wird ausschließlich die aktuelle Minecraft-Statistik als Baseline gesetzt: Es gibt keine Auszahlung für bereits vor Phase 16C gespielte Zeit.

Normaler Fortschritt ist `currentPlaytimeTicks - processedPlaytimeTicks`. Unter einem vollständigen Intervall bleibt `processed` unverändert, sodass der Rest automatisch Logout, Login und Serverneustart überlebt. Bei Fälligkeit werden alle vollständigen Intervalle in genau einem `RewardService.grantCoins(uuid, totalCoins, PLAYTIME, "online:playtime")` aggregiert. Nur `SUCCESS` erhöht `processed` um `intervalCount × intervalTicks`; der Rest bleibt bestehen. `PLAYER_NOT_LOADED`, Balance- oder Multiplikations-Overflow verändern den Fortschritt nicht. Ein Statistik-Rollback beziehungsweise negativer Wert rebased ohne Reward sicher auf den nicht negativen aktuellen Wert.

Ein erfolgreicher Grant mutiert das Wallet sofort und markiert den Player in der Reward Foundation dirty. OnlineReward aktualisiert danach im selben Main-Thread-Durchlauf den Progress. Der nächste Reward-Batch-Flush speichert dadurch Coins und Fortschritt gemeinsam; `OnlineRewardService` ruft niemals `savePlayer` oder Economy direkt auf. Baseline-, Disable- und Rebase-Änderungen werden erst durch Quit, Shutdown oder einen anderen normalen Player-Save dauerhaft. Bei einem harten Crash kann deren letzte In-Memory-Aktualisierung deshalb fehlen; dieses begrenzte Baseline-Fenster ist bewusst akzeptiert.

Die immutable Config-View aus `config.yml` enthält `online-rewards.enabled`, `interval-minutes`, `coins`, `message.enabled` und `message.format`. Defaults sind `true`, `60`, `250`, `true` und die dokumentierte MiniMessage. Positive Ganzzahlen, sichere Minuten-zu-Ticks-Konvertierung und das Nachrichtenformat werden beim Laden validiert; Fehler warnen, verwenden Defaults und verändern die Datei nicht. Ein Reload setzt keinen Fortschritt zurück: Ein kleineres Intervall kann vorhandenen Rest beim nächsten Check fällig machen, ein neuer Coin-Wert gilt nur für die nächste Auszahlung.

Bei `enabled: false` wird die In-Memory-Baseline regelmäßig auf die aktuelle Statistik gesetzt. Deaktivierte Zeit wird daher nach Reaktivierung nicht bezahlt; gespeichert wird weiterhin nur über den normalen Player-Lifecycle. Erfolgreiche Rewards können über den globalen `MessageService` gemeldet werden. `<coins>`, `<minutes>`, `<intervals>` und `<balance>` werden als sichere unparsed Placeholder eingesetzt. Ein ungültiges Config-Template fällt bereits beim Laden zurück; ein unerwarteter Sendefehler ändert weder Coins noch Progress. Phase 16C besitzt keine AFK-Erkennung, daher zählt verbundene Minecraft-Spielzeit derzeit auch während AFK. Es gibt keine Permission, Commands, Claims, Limits, Zufallswerte, Multiplikatoren oder Kopplung an Welt, Activity, Blackjack, Rank, Mine oder Quest.

## Quest Foundation

`QuestService` besitzt die fachliche Frage, ob ein geladener Spieler eine zugewiesene Quest erfüllt hat. Seit Phase 29 melden kleine Producer über `QuestProgressReporter#report(UUID, QuestProgressKey, long)`; der Reporter delegiert an `QuestService#addProgress` und sendet danach best-effort Chat-Feedback. Der Service kennt die Quellen nicht und importiert weder Mine, Blackjack, Activity, OnlineReward, MessageService noch Adventure. Bei Completion delegiert er an RewardService; Economy wird niemals direkt verwendet. QuestModule hängt von JavaPlugin, PlayerModule, RewardModule und MessageService ab und besitzt Reporter sowie Playtime-Producer. Es ist kein ReloadParticipant; Command und Menü gehören zu DailyQuest.

`QuestDefinition` ist ein immutable Record aus `id`, `name`, `description`, `progressKey`, positivem `long target` und positiven `long rewardCoins`. Die technische ID erfüllt `[a-z0-9][a-z0-9_-]{0,63}`; Name und Beschreibung sind non-blank, bleiben aber normale Domain-Strings ohne MiniMessage-Verarbeitung. `QuestProgressKey` ist ein validiertes Value Object nach `[a-z0-9][a-z0-9:._-]{0,127}`. Keys werden ausschließlich exakt verglichen. Es existieren weder ein hartcodiertes Quest-Type-Enum noch Prefix-/Wildcard-Matching; ein Producer kann bei echtem Bedarf mehrere Signale wie `mine:block:any` und `mine:block:stone` melden.

`QuestDefinitionRegistry` hält den zur Laufzeit bekannten Katalog. `findById`, `snapshot` und `snapshotById` liefern immutable Sichten in deterministischer ID-Reihenfolge. `replaceAll` baut zuerst einen vollständigen Kandidaten auf und lehnt Nullwerte oder Duplicate-IDs ab, bevor es den Runtime-Katalog austauscht. Ein Fehler lässt den alten Katalog vollständig erhalten. `QuestModule` erzeugt zunächst ein leeres Registry; `DailyQuestModule` lädt danach die Definitionen aus `daily-quests.yml` hinein. Der ausgelieferte Katalog ist absichtlich leer.

`CorePlayer` besitzt genau einen `PlayerQuestState`. Dessen aktuelle Assignment-Map enthält pro Quest ausschließlich ein immutable `PlayerQuestProgress(questId, progress, status)`; Definition, Name, Beschreibung, Target und Reward werden nicht dupliziert. Die Statuswerte bedeuten:

- `ACTIVE`: Fortschritt liegt im normalen Servicepfad unter dem aktuellen Target.
- `REWARD_PENDING`: Target wurde erreicht, aber der Reward wurde nicht erfolgreich bestätigt; der Fortschritt wird am Target gehalten.
- `COMPLETED`: Target und erfolgreicher Reward sind bestätigt; weitere Progress-Signale werden vollständig ignoriert.

Die Assignment-API arbeitet nur gegen bereits geladene `CorePlayer`. `assignQuest` liefert für unbekannte Definition oder ungeladenen Player ein Domain-Result und setzt eine vorhandene Assignment niemals auf null zurück. `replaceAssignments` validiert vor jeder Mutation alle IDs, Duplicates und Definitionen und ersetzt danach atomisch den gesamten State durch frische `ACTIVE`-Einträge bei null. Diese Grenze ist für den späteren Daily Reset vorgesehen. `clearAssignments` ist bei leerem State ein sauberer No-op. `getActiveQuests` liefert für geladene Player eine immutable, definition-backed `QuestView`-Liste; persistierte IDs ohne aktuelle Definition bleiben intern erhalten und werden in dieser Sicht ausgelassen.

`addProgress` akzeptiert ausschließlich positive Mengen. Ein Signal aktualisiert jede `ACTIVE`-Assignment mit exakt gleichem `QuestProgressKey`; andere, unbekannte und completed Assignments bleiben unverändert. Der Service berechnet zuerst `remaining = target - current` und wendet höchstens `min(amount, remaining)` an. Dadurch kann selbst `Long.MAX_VALUE` weder überlaufen noch Progress über das Target heben. Erreicht eine Quest das Target, wird sie vor dem Grant auf `REWARD_PENDING` gesetzt und anschließend synchron über `RewardService.grantCoins(playerId, rewardCoins, RewardSource.QUEST, "quest:" + id)` ausgezahlt. Jede gleichzeitig abgeschlossene Quest besitzt ihren eigenen Grant und technischen Grund; Rewards verschiedener Quests werden nicht aggregiert.

Nur `RewardStatus.SUCCESS` setzt den Status auf `COMPLETED`. `PLAYER_NOT_LOADED`, `BALANCE_OVERFLOW` und andere normale Misserfolge lassen `REWARD_PENDING` bestehen. `retryPendingRewards` versucht alle bekannten Pending-Assignments erneut. Auch ein weiteres passendes Progress-Signal darf diesen kontrollierten Retry auslösen, erhöht den Fortschritt aber nicht. Ein fehlgeschlagener Retry verändert keinen State und erzeugt keinen unnötigen Dirty-Marker; ein erfolgreicher Retry wird dirty. Eine unerwartete `RuntimeException` aus dem Reward-Pfad wird geloggt und lässt die Quest mindestens pending, statt Completion vorzutäuschen oder Fortschritt zu verlieren.

`FilePlayerRepository` persistiert den aktuellen Zustand deterministisch im bestehenden Profil:

```yaml
quests:
  active:
    daily_miner:
      progress: 120
      status: ACTIVE
    daily_playtime:
      progress: 30
      status: COMPLETED
```

Fehlt `quests` oder `quests.active`, wird `PlayerQuestState.empty()` geladen. Eine falsch typisierte Quest-Section oder ein beschädigter einzelner Eintrag erzeugt eine kontrollierte Warning und wird als optionaler Feature-State ausgelassen, ohne Name, Settings, Wallet, Social oder OnlineReward des Players unbrauchbar zu machen. Gültige unbekannte Quest-IDs werden dagegen bewusst geladen und beim nächsten Save erhalten. Ohne Registry-Definition gibt es für sie weder Progress, Completion noch Reward. Load allein führt niemals einen Reward oder eine automatische Completion aus; `ACTIVE`, `REWARD_PENDING` und `COMPLETED` überleben Logout und Restart.

Quest-Mutationen sind Main-Thread-owned und sofort im CorePlayer sichtbar. QuestService hält nur dirty UUIDs und speichert nicht pro Signal. Der eine gemeinsame synchrone 100-Tick-Task von QuestModule verarbeitet zuerst sichere Online-/Loaded-Playtime-Samples und flusht dann alle dirty Player. Save-Fehler bleiben dirty, sind pro Player isoliert und werden erneut versucht. QuestListener finalisiert bei Quit mit LOWEST vor PlayerListener NORMAL, flusht und vergisst den Sample auch bei Statistikfehlern. Disable stoppt den Task, deaktiviert Reporter-Feedback, verarbeitet letzte sichere Samples, flusht, meldet den Listener ab und leert Producer, Dirty Tracking und Referenzen.

RewardService und QuestService speichern jeweils den gesamten aktuellen `CorePlayer`, niemals getrennte Balance- oder Quest-Snapshots. Ein Reward-Flush kann daher aktuellen Quest-State mitpersistieren und ein späterer Quest-Flush redundant sein, aber keiner kann einen alten Teilzustand zurückschreiben. Bei einem harten JVM-/OS-Abbruch können die letzten ungefähr fünf Sekunden Quest-Progress verloren gehen; Coins und Completion-State im selben noch nicht gespeicherten In-Memory-Profil teilen dieses Batching-Fenster. Normales Quit, Plugin-Disable und Server-Shutdown flushen kontrolliert.

Phase 17B lädt Definitionen und rotiert Daily-Assignments über die bestehende Quest Foundation. Phase 29 ergänzt genau die Quellen playtime:minute und blackjack:win, /quests, GUI und Chat-Feedback. Andere valide Keys, etwa future:event, mine:block:any, activity:complete oder location:visit:mine, bleiben akzeptiert und ohne Producer inert. Es gibt keine festen Default-Quests oder manuelle Claims.

### Quest Completion (Phase 29)

`QuestProgressResult.reachedQuestIds` enthält ausschließlich ACTIVE-Quests, die mit diesem Signal erstmals das Target erreichen. Die vorhandenen Completed-/Pending-Listen behalten ihre Bedeutung; Retry-Ergebnisse haben keine Reach-IDs. Der Reporter hat keinen eigenen Quest-State, Katalog, Save-Task oder Reward-Pfad. In deterministischer ID-Reihenfolge meldet er den ersten erfolgreichen Abschluss als `Daily quest complete: <name>` plus `+<coins> Coins`, erstmaliges Pending als Completion-Zeile plus `Your reward is pending and will be retried.` und späteren erfolgreichen Progress-Retry als `Quest reward delivered: <name>` plus Coins. Wiederholte erfolglose Retries bleiben still. Name bleibt literal Component.text; Chatfehler werden mit UUID und Cause geloggt, ohne State oder Coins zurückzusetzen. Daily-Retries vor Rotation behalten ihre Domain-Semantik und erzeugen keine zusätzliche Delivery-Nachricht.

`QuestPlaytimeProducer` hält UUID → letztes Statistic.PLAY_ONE_MINUTE-Tick-Sample nur im Speicher. Enable und MONITOR-Join seeden online/geladen vorgefundene Spieler. Eine erste spätere Probe ist ebenfalls nur Baseline. Fortschritt ist `currentTicks / 1200 - previousTicks / 1200`; mehrere Grenzen werden als ein positives Signal gemeldet. Negativwerte werden auf null geklemmt; ein kleinerer aktueller Wert rebased mit UUID-Warning ohne Fortschritt. Quit vergisst, Disable leert Samples. Kein zusätzlicher Task, Offline-Fortschritt, AFK-Filter oder persistierter Counter; OnlineRewardProgress, processed-playtime-ticks und OnlineReward-Schedule bleiben unabhängig. Daily enabled: false deaktiviert Assignment/UI, nicht die generische Quelle für bereits vorhandene Assignments.

`BlackjackService` besitzt einen standardmäßig leeren `BiConsumer<UUID, BlackjackOutcome>`. Er setzt Outcome und SETTLED-State, meldet danach einmal je Player/Runde und führt Refresh und Reset weiter aus. BlackjackModule konsumiert den früher gestarteten QuestModule-Reporter: ausschließlich WIN und BLACKJACK melden blackjack:win mit Amount 1; PUSH, LOSS und BUST bleiben inert. Callback-Ausnahmen warnen mit UUID, Outcome und Cause; andere Player, Refresh und Reset laufen weiter. Shutdown setzt den Callback auf No-op zurück. Kein Blackjack-Coin-Grant, Quest-Import in der Blackjack-Domain oder umgekehrte Abhängigkeit.

`DailyQuestMenu` hat 54 Slots: Content 0–44, Previous 45, Page-Info 49, Close 50, Refresh 52, Next 53. Seiten werden geklemmt; Namen, Beschreibungen und long-Werte bleiben literal. ACTIVE zeigt In Progress, COMPLETED grün mit geliefertem Reward, REWARD_PENDING gelb mit automatischem Retry-Hinweis. Keine technischen IDs/Keys in Lore; Quest-Items sind lesend. Owner-UUID, Holder, exakt gebundene Inventory-Instanz, aktive Registrierung und aktuell geöffnetes Top müssen übereinstimmen. Jeder erkannte Klick und Drag wird zuerst gecancelt; nur LEFT/RIGHT auf Top-Controls handeln nach aktuellen Online-/Loaded-/Permission-Checks. Kein Lobby-, BUILD- oder Activity-Gate. Stale/forged Menüs bleiben inert; alter Close entfernt keine neue Seite. Quit vergisst; Disable schließt nur eigene aktuelle Views, isoliert Close-Fehler und leert alle Bindings.

`/quests` und `/quest` verlangen zuerst vapeecore.quest.use (Default true), dann Player, dann null Argumente. Vor jedem Open/Refresh synchronisiert das Menü mit syncPlayer(UUID, Instant.now()). DISABLED, NO_DEFINITIONS, PLAYER_NOT_LOADED und ASSIGNMENT_FAILED liefern kontrollierte Hinweise ohne Open; Fehler werden mit UUID und vorhandener Cause geloggt. CURRENT, INITIALIZED, ROTATED öffnen. BLOCKED_PENDING_REWARD zeigt die alte Sicht plus `A previous quest reward is still pending.`. Refresh rerollt im gleichen Cycle nicht. Core help zeigt Gameplay-Eintrag und Alias permission-gefiltert; keine Quest-Subcommands oder Tab-Vorschläge.

Reload bleibt der sechste Teilnehmer: Prepare verändert nichts, Apply wechselt Config/Registry und schließt Views, Rollback stellt beide Snapshots wieder her. Player-State wird bei Apply nicht ersetzt. Ein später fehlgeschlagener anderer Apply kann Views geschlossen lassen; nächster Open synchronisiert gegen den zurückgerollten State. Dadurch bleiben keine stale Definitionen sichtbar und kein neuer UI-Scheduler wird benötigt.

## Daily Quest Cycle & Configuration

DailyQuestModule hängt von JavaPlugin, PlayerModule, QuestModule und MessageService ab. Es besitzt daily-quests.yml, DailyQuestConfig, DailyQuestService, Join-Listener, /quests-Executor und TabCompleter, konkretes Menü mit separatem UI-Listener sowie genau einen 1200-Tick-Sync-Task. Kein Save-Task. Enable-Fehler räumen Task, beide Listener, Handler, Menü und Referenzen auf und stellen den vorigen Registry-Katalog wieder her. Disable stoppt den Task, schließt eigene Views, meldet beide Listener ab und entfernt beide Handler. Die Quest Foundation bleibt Owner von Registry, Progress, Completion, Reward-Retry und 100-Tick-Flush.

Die Resource bleibt byte-identisch mit enabled: false, quests-per-day: 4, reset.time: "00:00", reset.timezone: "system" und quests: {}. Betreiber bestimmen Inhalte und Balance selbst. Unterstützte Keys sind ausschließlich playtime:minute und blackjack:win; eine Parser-Allowlist gibt es nicht. Das folgende Beispiel ist Dokumentation, kein ausgelieferter Default:

```yaml
enabled: true
quests-per-day: 4
reset:
  time: "04:00"
  timezone: "Europe/Berlin"
quests:
  play_30_minutes:
    name: "Regular"
    description: "Play for 30 minutes."
    progress-key: "playtime:minute"
    target: 30
    reward-coins: 250
```

Slots, Reset-Zeit, Zeitzone sowie Quest-Name, Beschreibung, Progress-Key, Target und Reward werden in `daily-quests.yml` geändert. `DailyQuestConfig` verwendet für fehlende oder falsch typisierte allgemeine Einstellungen sichere Defaults und Warnungen; ungültige Quest-Definitionen brechen dagegen den vollständigen Prepare-Schritt ab. `/core reload` bereitet alle sechs Teilnehmer vor, bevor DailyQuest Config und Registry austauscht; ein Rollback stellt beide alten Snapshots wieder her. Apply verändert keine Player-Assignments. Bestehende IDs erhalten bei geändertem Target oder Reward sofort die neue Definition; entfernte IDs bleiben im Playerprofil, bekommen aber weder Fortschritt noch Reward. IDs während eines laufenden Cycles deshalb möglichst nicht entfernen oder umbenennen.

`DailyQuestCycleId` ist das lokale ISO-Datum des letzten Reset-Boundary, gespeichert als `quests.daily.cycle-id: "yyyy-MM-dd"`. `DailyQuestCycleResolver` berechnet es aus `Instant`, konfigurierter `ZoneId` und `LocalTime`: `system` nutzt die aktuelle Systemzeitzone, explizite IANA-IDs etwa `Europe/Berlin` sind möglich. Der Resolver vergleicht echte Instants mit dem zonengerechten Boundary und bleibt damit auch bei DST-Lücken und doppelten Stunden stabil; er wartet nie pauschal 24 Stunden. Bei Reset 04:00 gehört 03:59 noch zum Vortag und 04:00 zum neuen Cycle.

`DailyQuestSelector` sortiert alle Definitionen nach SHA-256 über kanonische Spieler-UUID, Cycle-ID und Quest-ID, mit Quest-ID als Tie-Breaker. Es wählt höchstens `quests-per-day` unterschiedliche IDs, unabhängig von der YAML-/Collection-Reihenfolge. `DailyQuestService#syncPlayer(UUID, Instant)` wählt beim ersten Sync ausschließlich den aktuellen Cycle, nicht versäumte Tage. Gleiche Cycle-ID ist ein No-op: weder Reload noch Restart setzt Progress zurück, selbst wenn sich `quests-per-day` inzwischen geändert hat. Beim nächsten Cycle ersetzt `QuestService#replaceAssignments` alte ACTIVE-/COMPLETED-Einträge durch neue ACTIVE-Einträge bei null; erst danach wird die Cycle-ID gesetzt. Ein leerer aktiver Katalog erzeugt keine Assignments und keinen Cycle-Fortschritt; ein kleiner Katalog füllt nur die vorhandenen Slots. `enabled: false` lässt vorhandenen State unangetastet. Änderungen an Reset-Zeit oder Zeitzone können beim nächsten Sync einen anderen aktuellen Cycle ergeben.

Vor jeder Rotation versucht der Service vorhandene `REWARD_PENDING`-Einträge über `QuestService#retryPendingRewards` erneut. Bleibt ein Pending offen, bleiben alte Assignments und Cycle-ID stehen; eine unbekannte Pending-ID wird nur sparsam gewarnt und muss durch Wiederherstellung ihrer Definition oder bewusste administrative Bereinigung gelöst werden. Ein erfolgreicher Retry erlaubt die Rotation, ohne doppelte Auszahlung. Der Join-Listener läuft bei `HIGHEST` nach `PlayerListener` (`NORMAL`); bei fehlendem CorePlayer überspringt er den Join und der globale Task versucht es später. Daily-Cycle und Assignments werden zusammen im bestehenden `CorePlayer` gespeichert, weil `replaceAssignments` QuestService dirty markiert. Bei einem Hard Crash vor dem Flush kann der alte Cycle wieder erscheinen, aber UUID, Datum und Katalog ergeben erneut dieselbe Auswahl. Legacy-Profile ohne Cycle-ID sind gültig; ungültige optionale Daily-Sections warnen und werden uninitialisiert, während `quests.active` weiter geladen wird.

Ownership: Daily-Anzahl, Reset und Definitionen → daily-quests.yml; Cycle → Resolver; Auswahl → Selector; Assignment/Rotation → DailyQuestService; Fortschritt/Completion/Coins → QuestService und RewardService; Chat → QuestProgressReporter; Playtime-Samples → QuestPlaytimeProducer; Spieleransicht → DailyQuestMenu. Kein neues Player-YAML-Feld, Schema, History-File oder Claim-Pfad. FORMATTING.md und historische Phasenberichte bleiben unverändert. Vollständige Verifikation: [QUEST_COMPLETION.md](QUEST_COMPLETION.md).

## Ranks & Server Identity

LuckPerms besitzt Gruppen, Mitgliedschaften, Primary Group, Display Name, Prefix, Suffix, Weight, Tracks und Permissions vollständig. VapeeCore liest und präsentiert diese Informationen ausschließlich; es existieren weder `Player.rank`, eigene Rank-Mitgliedschaften, eine Rank-Datenbank noch Rank-Zuweisungen in `plugins/VapeeCore/players/`. Administratoren ändern Ränge weiterhin mit LuckPerms. `/rank` und `/ranks` sind reine Lese-Commands im VapeeCore-Namespace und besitzen bewusst keine Alias-Flut.

`PermissionModule` stellt die einzige LuckPerms-Provider-Verbindung her. `LuckPermsService` kapselt geladene User, Group Information und Track Groups, ohne LuckPerms-API-Typen in andere Features zu leaken. Eine Group-Auflösung liest Display Name, Description, rohe Color Meta und Weight gemeinsam. `RankService` bildet diese Werte auf das immutable `RankInfo(id, displayName, description, color, weight)` ab. Normale Rank-/Presentation-Abfragen verwenden ausschließlich `UserManager#getUser`, ohne `loadUser`, Blocking oder dauerhaften Rank-Cache. Die separate Phase-26A-Staff-Autorisierung darf über die neue read-only API `LuckPermsService#loadPrimaryGroup(UUID)` für ungecachete bekannte Moderations-Targets asynchron `UserManager#loadUser` verwenden; sie speichert oder verändert keine User. Ist der User nicht geladen, liefert `getPrimaryRank` `Optional.empty()`. Fehlt die Group unerwartet, bleibt die bekannte ID erhalten und Display Name, Description, Color und Weight fallen kontrolliert zurück.

Der öffentliche Track steht unter `config.yml` → `ranks.track`; Default und Fallback sind `ranks`. Der Wert muss ein non-blank String sein, sonst warnt `ConfigService`, verwendet `ranks` und verändert die Datei nicht. Nur die in diesem LuckPerms-Track enthaltenen Gruppen erscheinen in `/ranks`; interne Gruppen bleiben unsichtbar. `Track#getGroups()` bestimmt die Reihenfolge, nicht Alphabet oder Weight. Da `RankService` den aktuellen Configwert bei jeder Listenabfrage liest, wirkt ein geänderter Track nach `/core reload` sofort, ohne zusätzlichen Reload-Teilnehmer (aktuell insgesamt sechs).

Jeder öffentliche Rank sollte in LuckPerms einen Group Display Name besitzen. Fehlt er, erzeugt VapeeCore ohne hartcodiertes Mapping einen neutralen Namen aus der Group-ID, zum Beispiel `senior_builder` → `Senior Builder`. Die optionale Beschreibung kommt aus `vapeecore.rank.description`; die Rank-Farbe kommt aus `vapeecore.rank.color`; Weight bleibt reine Metadateninformation. `RankService` akzeptiert Adventure Named Colors wie `gray`, `gold`, `aqua`, `green`, `red`, `dark_red` und `light_purple` sowie sechsstellige Hexwerte wie `#55ffaa`. Fehlende oder ungültige Werte bleiben `Optional.empty()` und werden bei der Ausgabe neutral weiß dargestellt. Prefix und Suffix bleiben unabhängig davon kompatibel und sind für Rank-Farbe nicht erforderlich.

Neue Ränge benötigen keine VapeeCore-Codeänderung. Der empfohlene LuckPerms-Ablauf ist: Gruppe erstellen, Group Display Name setzen, `vapeecore.rank.color` setzen, optional `vapeecore.rank.description` setzen und die Gruppe an den konfigurierten `ranks`-Track anhängen. Die Rank-Darstellung besitzt kein festes Mapping von Gruppen-IDs oder Farben. Ausschließlich die separate StaffHierarchyConfig definiert vier sichere Schutzdefaults; zusätzliche geschützte Gruppen benötigen nur eine explizite Configänderung.

Feature-Zugriff bleibt Permission-basiert. Die zusätzliche Phase-26A-Moderations-Target-Autorisierung ist von der Rank-Darstellung unabhängig und wertet nur ausdrücklich konfigurierte primäre Staff-Group-IDs aus; sie gewährt keine Fähigkeit. Builder-Funktionen prüfen `vapeecore.utility.build`. Spätere Mine-Zugriffe verwenden `vapeecore.mine.<mine>`; spätere Reward-Multiplikatoren könnten über Permissions oder optionales LuckPerms-Meta modelliert werden. Phase 16C implementiert weder Mine noch `coin-multiplier`; Online Rewards sind für alle Ränge identisch und besitzen keine LuckPerms-Abhängigkeit.

Presentation und Chat unterstützen `<rank>` als farbiges Component aus dem freundlichen Primary-Rank-Namen, `<rank_name>` als normalen Player Display Name in der Primary-Rank-Farbe sowie `<rank_id>` als rohe Primary Group. `<name>` bleibt der unveränderte Player Display Name; `<group>` bleibt als Compatibility-Alias identisch zu `<rank_id>`; `<prefix>` und `<suffix>` bleiben die effektiven LuckPerms-Metawerte. Der Chat-Renderer wird pro `AsyncChatEvent` neu erzeugt, damit Papers viewer-unaware Cache nicht die erste Nachricht für Folge-Events wiederverwendet. Der Async-Pfad liest nur bereits geladene LuckPerms-Cached-Data, verwendet keine Player-Statistik und setzt Playertext weiterhin als sichere Adventure Component ein.

Presentation besitzt vollständig `<server>`, `<name>`, `<rank_name>`, `<prefix>`, `<suffix>`, `<rank>`, `<rank_id>`, `<group>`, `<playtime>`, `<coins>`, `<online>` und `<max_players>`; dieselben Placeholder gelten für Scoreboard, `tablist.name-format`, Header und Footer. `<playtime>` stammt ausschließlich aus `Player#getStatistic(Statistic.PLAY_ONE_MINUTE)`. Der historische Statistikname ist irreführend: Der Wert sind Ticks, also 20 Ticks pro Sekunde. `PlaytimeFormatter` rechnet mit `long`, klemmt negative Werte auf null und zeigt unter einer Stunde Minuten, unter einem Tag Stunden und Minuten sowie ab einem Tag Tage und Stunden. Es existieren weder eigene Persistence noch zusätzliche Scheduler- oder Polling-Tasks.

Das Default-Scoreboard zeigt Coins, den freundlichen Rank mit `<rank>` in seiner Rank-Farbe und Playtime im kompakten Label/Wert-Layout. Chat und Tablist verwenden standardmäßig `<rank_name>` für den rankfarbigen Spielernamen. `<name>` bleibt der normale, nicht automatisch rankgefärbte Display Name; `<rank_id>` ist die technische Primary Group und `<group>` deren Compatibility-Alias. Prefix und Suffix bleiben für eigene Formate verfügbar.

Neue Resource-Defaults überschreiben weder `plugins/VapeeCore/chat.yml` noch `plugins/VapeeCore/presentation.yml`. Ein vorhandenes `<group>` funktioniert weiterhin. Für Rank-Farben auf bestehenden Servern müssen Administratoren `chat.yml` auf `format: "<rank_name><dark_gray> » </dark_gray><white><message></white>"` und `presentation.yml` unter `tablist` auf `name-format: "<rank_name>"` umstellen; optionale Prefix-/Suffix-Varianten sind möglich. Danach aktiviert `/core reload` die Änderung.

## Visibility foundation und Profile UX (Phase 20A/20B)

`VisibilityModule` ist der einzige Owner der Visibility-Policy, der Paper-Show/Hide-Anwendung und der von VapeeCore gesetzten Hide-Zustände. Es startet nach Lobby und vor Chat, konsumiert `PlayerModule`, `SocialModule`, `FriendModule` und `LobbyModule` und ist kein `ReloadParticipant`. `LobbyExperienceModule` erzeugt keinen zweiten Visibility-Service: Seine Listener beziehen `visibilityModule.getVisibilityService()` und liefern nur Join-, Quit-, World-, Respawn- und Hotbar-Ereignisse. Globales World-Visibility, Commands und ein periodischer Refresh-Task existieren nicht.

`PlayerSettings` bündelt die Visibility-Werte in `PlayerVisibilitySettings`; die Compatibility-Methoden `isLobbyPlayersVisible()` und `setLobbyPlayersVisible(...)` delegieren auf den Masterwert. Das Modell enthält nur Booleans und UUIDs, keine Bukkit-Player oder Namen:

| Player-YAML-Key | Domainfeld | Default |
|---|---|---:|
| `settings.lobby-players-visible` | `allPlayersVisible` | `true` |
| `settings.visibility.show-friends` | `showFriends` | `false` |
| `settings.visibility.show-staff` | `showStaff` | `false` |
| `settings.visibility.show-added-users` | `showAddedUsers` | `false` |
| `settings.visibility.show-game-participants` | `showGameParticipants` | `false` |
| `settings.visibility.added-players` | `Set<UUID> addedPlayers` | leer |

Alte Dateien ohne `settings.visibility` behalten deshalb ihr bisheriges Verhalten. Optionale ungültige Booleans oder eine ungültige Visibility-Section erzeugen eine Warnung und verwenden den sicheren Default, statt den Login zu verwerfen. `added-players` wird als deterministisch lexikografisch sortierte UUID-String-Liste geschrieben. Beim Laden werden ungültige und nichtkanonische UUID-Strings, Nicht-Strings und die eigene UUID gewarnt und ignoriert; Duplikate werden zusammengeführt. Unbekannte, aber syntaktisch gültige UUIDs bleiben erlaubt, da eine spätere Eingabegrenze Identitäten prüfen wird. Lesezugriff auf die Collection liefert eine unveränderliche Kopie.

`PlayerSettingsService` stellt Reads und sofort persistierende Mutationen für Master, Friends, Staff, Added Users und Game Participants sowie `getLobbyAddedVisiblePlayers`, `isLobbyAddedVisiblePlayer`, `addLobbyVisiblePlayer` und `removeLobbyVisiblePlayer` bereit. Normale Added-User-Zustände werden über `AddedVisiblePlayerResult` (`SUCCESS`, `OWNER_NOT_LOADED`, `CANNOT_ADD_SELF`, `ALREADY_ADDED`, `NOT_ADDED`) ausgedrückt. Die Domain prüft weder Identity Storage noch Online-Status des Targets. Jede erfolgreiche Mutation speichert den gesamten geladenen Player sofort; schlägt der Save fehl, werden Boolean oder Set exakt auf den vorherigen Runtime-Zustand zurückgerollt.

`VisibilityPolicy#shouldShow(viewer, target, targetIsStaff)` ist die einzige Entscheidungsstelle. Ihre Priorität ist:

1. Selbst bleibt sichtbar.
2. `SocialService#isKnownIgnoring` in einer der beiden Richtungen ist ein harter Deny für beide Sicht-Richtungen.
3. Der aktive Master zeigt alle sonst zulässigen Lobby-Spieler.
4. Im gefilterten Modus reicht eine aktivierte positive Regel: echte `FriendRelation.FRIENDS`, aktueller Staff-Marker, UUID in `addedPlayers` oder gemeinsamer Game-Teilnehmer.
5. Ohne Treffer bleibt das Target verborgen.

Ein- und ausgehende Friend Requests zählen ausdrücklich nicht als Freundschaft; `FriendService` bleibt die Source of Truth. Staff wird ausschließlich über die aktuelle Online-Permission `vapeecore.visibility.staff` erkannt, nie über Primary-Group- oder Rangnamen. Dieser Node ist nur ein Klassifizierungsmarker und gewährt keine Command- oder Admin-Fähigkeit. Die Game-Regel ist als injizierbares `BiPredicate<UUID, UUID>` testbar; Production verwendet in 20A bewusst `false`, damit keine falsche Activity-Abhängigkeit entsteht.

`VisibilityService` wendet die Policy nur für Online-Viewer und -Targets in der Lobby-Welt an. `synchronizePlayer` aktualisiert die neue Person als Viewer und anschließend alle vorhandenen Lobby-Viewer in Gegenrichtung; die Präferenzen dürfen deshalb asymmetrisch sein. `applyViewerPreference` berechnet nach einer persönlichen Setting-Mutation alle relevanten Targets des Viewers neu. `refreshPair(first, second)` löst beide Online-Spieler auf und bewertet ausschließlich in der Lobby beide Richtungen neu. Sichtbarkeit nutzt ausschließlich `viewer.hidePlayer(plugin, target)` und `viewer.showPlayer(plugin, target)`. Der Service merkt nur die von dieser Plugin-Instanz versteckten Paare. `restorePlayer` gibt beim Lobby-Austritt oder Quit alle eigenen Paare mit dieser Person frei; `restoreAll` läuft beim Disable des VisibilityModule und setzt die Runtime-Referenz anschließend auch bei einzelnen Restore-Fehlern zurück. Fremde Plugin-Zustände werden nicht verwaltet.

Das Hotbar-Item beschreibt den Masterzustand als `Players: Visible` beziehungsweise `Players: Filtered`. Rechtsklick toggelt den bestehenden Master, speichert ihn, wendet die Viewer-Präferenz unmittelbar an, aktualisiert das Item und behält den bestehenden 10-Tick-Cooldown sowie das optionale UI-Soundverhalten. Der Master im Visibility-Menü mutiert denselben Wert und ruft danach ebenfalls `LobbyItemService#refreshVisibilityItem` auf.

### Visibility Settings UX (Phase 20B)

`SettingsModule` besitzt die Spieler-UX und injiziert gezielt `IdentityModule`, `VisibilityModule` und `LobbyModule`; es implementiert weder eine zweite Policy noch eigene Persistence. Seit Phase 21 bietet `SettingsMenu` eine 54-Slot-Übersicht mit getrennten Feature-/Status-Zeilen und der Kategorie `Player Visibility`. `VisibilitySettingsMenu` rendert in einem separaten 54-Slot-Inventar Feature-Items und direkt darunter Status-Panes für All Players, Friends, Staff, Added Users und Game Participants. Master-OFF heißt `FILTERED`, nicht „niemand sichtbar“. Friends, Staff und Added Users lassen sich auch bei aktivem Master vorbereiten. Game Participants bleibt sichtbar `Unavailable`, verändert den persistierten Wert nicht und wartet auf eine echte Activity-/Game-Anbindung.

`VisiblePlayersMenu` verwaltet ausschließlich `PlayerVisibilitySettings.addedPlayers`: 45 Content-Slots, deterministische Sortierung nach bekanntem Namen und UUID, tatsächlicher Online-Status über `Server#getPlayer(UUID)` plus `isOnline()`, UUID-Fallback und eine untere Navigationszeile. `VisiblePlayersHolder` hält die serverseitige Slot→UUID-Zuordnung; Itemname und Lore werden nie zurückgeparst. Nur Rechtsklick entfernt. Refresh liest Settings, Identity und Online-Status neu, Seiten werden geklemmt und der Empty State behält den Add-Button. Dieser schließt das Inventar und sendet eine Adventure-Component mit `suggestCommand("/settings visibility add ")`; Chat-, Sign- oder Anvil-Capture existiert nicht.

`/settings visibility` öffnet das Filtermenü. `/settings visibility add <player|uuid>` validiert bekannte Online- und Offline-Identitäten über `PlayerIdentityService`, lehnt unbekannte oder mehrdeutige Namen, Self und Duplikate kontrolliert ab und wendet einen erfolgreichen Save sofort über `VisibilityService#applyViewerPreference` an. `/settings visibility remove <player|uuid>` löst Namen ebenfalls über Identity auf; eine bereits gespeicherte unbekannte UUID darf ausdrücklich direkt entfernt werden. Tab Completion zeigt für Add geeignete Online-Spieler und für Remove nur Added Users, bei unbekannten oder mehrdeutigen Namen als UUID. Alle Playerdaten werden als literal Adventure-Text ausgegeben. Console bleibt ausgeschlossen und `vapeecore.settings.use` ist die einzige Permission.

Alle drei Settings-Menüs binden Owner, Holder und exakt eine Inventory-Instanz und führen zusätzlich pro Viewer eine Active-Inventory-Registry. Clicks einschließlich Bottom-Inventory-Transfer, Shift, Number Key, Double Click, Offhand, Collect, Drop und Creative sowie Drags werden zuerst gecancelt; nur definierte Aktionen auf dem aktuell gebundenen Inventar laufen weiter. Close, Quit und `SettingsModule#disable` entfernen aktive Referenzen beziehungsweise schließen eigene Menüs. Save-Fehler werden mit UUID geloggt, nutzen das Rollback des `PlayerSettingsService`, spielen keinen Success-Sound und bauen den aktuellen State kontrolliert neu auf.

`FriendService` und `SocialService` stellen kleine domain-neutrale Relationship-Listener bereit. Sie feuern ausschließlich nach erfolgreich persistiertem Accept/Auto-Accept/Remove beziehungsweise Ignore/Unignore. `VisibilityModule` registriert beide Listener beim Enable, ruft `refreshPair` auf und entfernt sie beim Disable; Friend und Social importieren keine Visibility- oder Bukkit-Klassen. Damit werden Friends-Filter und Ignore-Hard-Deny ohne Relog sofort in beiden asymmetrischen Richtungen aktualisiert. Pending/Denied/Canceled Friend Requests ändern keine Freundschaft und feuern deshalb keinen Refresh. Eine sofortige LuckPerms-Event-Anbindung bleibt außerhalb des Scopes: Der Staff-Marker wird bei jeder normalen Neuberechnung aktuell gelesen.

`/profile [player|uuid]` bleibt unverändert; „Profile UX“ bezeichnet ausschließlich die persönlichen Visibility-Einstellungen. Das Hauptmenü-Redesign wurde in Phase 21 ergänzt; ein generisches GUI-Framework entsteht dabei nicht. Chat Range, Game Auto-Join und produktive Game-Participant-Integration bleiben aufgeschoben.

### Settings Completion & Redesign (Phase 21)

`SettingsModule` besitzt Root-, Visibility- und Visible-Players-UX. `SettingsMenu` heißt weiterhin `Player Settings` und nutzt 54 Slots ohne Filler oder künstliche Features:

| Feature | Icon-Slot | Status-Slot |
| --- | --- | --- |
| Scoreboard (`MAP`) | 10 | 19 |
| Sounds (`NOTE_BLOCK`) | 12 | 21 |
| Private Messages (`WRITABLE_BOOK`) | 14 | 23 |
| Friend Requests (`PLAYER_HEAD`) | 16 | 25 |
| Player Visibility (`SPYGLASS`) | 31 | 40 |
| Friend Presence (`BELL`) | 33 | 42 |

Normale Booleans zeigen grün `Enabled` oder rot `Disabled`; Visibility zeigt grün `All Players` oder gelb `Filtered`. Das Visibility-Untermenü verwendet dieselben Modusfarben, während Game Participants unverändert grau `Unavailable` bleibt. Icon und Status der fünf General-Einstellungen toggeln dieselbe Preference. Beide Visibility-Items öffnen ausschließlich das Untermenü. Close (`BARRIER`, 49) schließt; Refresh (`CLOCK`, 52) rendert den aktuellen Runtime-State ohne Mutation oder Sound.

Das Root-Menü bindet Owner und Holder exakt an eine Inventory-Instanz und führt `Map<UUID, Inventory>` für aktive Ansichten. `isActive` verlangt zusätzlich die tatsächlich geöffnete Top-Inventory-Instanz. Der Listener cancelt jedes erkannte Root-Holder-Event vor Validierung, einschließlich gefälschter, ungebundener oder veralteter Inventare und Bottom-Transfers. Nur LEFT/RIGHT auf definierten Slots führt Aktionen aus. Drags werden vollständig gecancelt. Close entfernt nur die konkrete aktive Instanz; Quit vergisst die Viewer-UUID auch bei bereits gewechseltem View.

Toggles und Refresh aktualisieren das bestehende Root-Inventar. `PlayerSettingsService` bleibt alleiniger State-/Persistence-Owner einschließlich Rollback. Nach erfolgreichem Scoreboard-Save folgt `PresentationService#updatePlayer`; andere General-Toggles ändern keine Presentation. Sound-Feedback prüft den gespeicherten aktuellen Wert: Ausschalten bleibt still, Einschalten kann Feedback geben. Bei Save-Failure folgt SEVERE mit UUID, kontrollierte Meldung und Rendering des zurückgerollten Zustands, ohne Presentation-Aktion oder Sound. Ein verschwundenes Profil schließt die Ansicht kontrolliert.

Navigation: Root → Visibility → Manage Visible Players; Back folgt jeweils dem direkten Parent, Root hat keinen Back. Close öffnet keine andere Ansicht, Refresh bleibt in seiner Ansicht. Visibility-Toggles, Hotbar-Master-Synchronisierung, Added-User-Commands und Friend-/Ignore-Live-Refresh behalten ihre bestehenden Service-Boundaries. Einträge zeigen Namen als literal Components, Online grün, Offline grau, UUID und Rechtsklick-Geste.

Beim Disable schließt SettingsModule alle drei eigenen getrackten Menütypen vor Listener-/Command-Cleanup und nullt anschließend Runtime-Referenzen. Fremde aktuell geöffnete Inventare bleiben erhalten. Settings hat weiterhin keinen ReloadParticipant, Scheduler, neuen Config-Key, neue Permission oder neuen Command. Chat Range, Game Auto-Join und andere Settings ohne Backend werden nicht angezeigt.

Phase 30C: Die sieben Menüs Friend, Clan, Settings, VisibilitySettings, VisiblePlayers, Navigator und DailyQuest teilen ausschließlich die kleinen Primitiven in `dev.vapee.core.ui`: `UiItemSpec` hält Material, Component-Name und defensiv kopierte Lore; `UiItems` erzeugt literal, nicht-kursive Texte und setzt nur Material/Name/Lore. Leere Component-Lorezeilen und die injizierbaren InventoryFactory-/ItemRenderer-Testseams bleiben erhalten. `Pagination.of` berechnet ohne Bukkit oder Domain-State leere, geklemmte und überlaufsichere Seitenbereiche für die fünf paginierten Menüs. Layouts, Farben, Controls, Slot-Zielbindungen, Aktionen, Holder-/Owner-/Active-Prüfungen und die bestehenden Current-Top-Unterschiede bleiben featurelokal; es gibt kein GUI-Framework. Bulk-Cleanup isoliert RuntimeException je getracktem Owner, protokolliert Menü/UUID/Ursache und leert die Registry in `finally`; nur das exakt getrackte, aktuell offene Inventar wird geschlossen. Invsee-Snapshots, Lobby-PDC-Items, Blackjack-Hotbar und native Enderchest bleiben eigenständig. SEC-001 bleibt offen. Details und Verifikation: `docs/GUI_INVENTORY_ITEM_CLEANUP.md`.

## Lobby & Navigation Completion (Phase 28)

`WarpModule` besitzt weiterhin `WarpConfig`, `WarpService` und den administrativen `/warp`-Root. `LobbyExperienceModule` besitzt Compass-Interaktion, `NavigatorMenu` und `NavigatorListener`. Es bezieht zusätzlich das bereits früher gestartete `ActivityModule` für die kleine konkrete `NavigatorAccessPolicy`; es gibt keine Reverse Dependency. Die 27 Module, sechs Reload-Teilnehmer, 36 Root-Commands und 49 Permission-Nodes bleiben unverändert. Warp ist kein ReloadParticipant; Admin-Mutationen wirken sofort, `/core reload` lädt `warps.yml` nicht neu.

`WarpPoint` enthält ID, literal Display Name, Item-Icon, `WarpPosition` und den immutable Record `WarpNavigation(visible, order)`. Der kompatible Vier-Argument-Konstruktor und neue `/warp set`-Ziele verwenden `visible=true`, `order=0`. Hidden bedeutet ausschließlich im Compass verborgen: Existenz, Admin-Verwaltung und generische `WarpService#teleport`-Semantik bleiben erhalten. Weder Lobby-Spawn noch bestimmte IDs sind reserviert oder automatisch erzeugt.

```yaml
warps:
  example:
    display-name: Example
    icon: ENDER_PEARL
    navigator:
      visible: true
      order: 0
    location:
      world: world
      x: 0.0
      y: 64.0
      z: 0.0
      yaw: 0.0
      pitch: 0.0
```

Legacy-Dateien ohne Navigator-Section oder einzelne Keys erhalten Runtime-Defaults und werden bei Start nicht geschrieben. Eine falsche Section, nicht-boolesches Visible oder nicht-ganzzahliges, negatives beziehungsweise außerhalb `0..Integer.MAX_VALUE` liegendes Order erzeugt WARNING mit Warp-/Key-Kontext und sicheren Fallback. Andere gültige Metadaten bleiben erhalten. Ein fokussierter YAML-Node-Check hält explizite Nullwerte von fehlenden Legacy-Keys unterscheidbar, weil Bukkit Nullwerte entfernt. Pflichtdaten bleiben strikt validiert; ein ungültiger Warp wird weiterhin unabhängig übersprungen.

`saveWarps` schreibt beide Metadaten im bestehenden Tempdatei-/Atomic-Move-/Replace-Fallback. Service-Mutationen bauen den nächsten Zustand, speichern ihn und tauschen erst danach die Runtime-Map. Save-Fehler propagieren bei unverändertem vorherigem Zustand; Commands melden sie kontrolliert. Position, Name und Icon erhalten Navigation; Visible erhält Order und Order erhält Visible. Show/Hide auf gleichem Zustand und Order auf gleichem Wert liefern SUCCESS ohne Datei-Write. Ein normaler späterer Save darf Legacy-Metadaten mitnormalisieren.

`getWarps()` liefert weiterhin alle Warps immutable nach ID für Admins. `getNavigatorWarps()` liefert ausschließlich sichtbare Ziele immutable nach `order ASC`, dann `id ASC`. Der 54-Slot-Navigator behält 45 Content-Slots und die Controls 45/49/50/53. Seitenzahl, Content und Page Info zählen nur sichtbare Ziele; Seiten werden bei jeder neuen Darstellung geklemmt. Hidden-only ist derselbe normale Empty State wie eine leere Registry: `No Destinations Available`. Itemname bleibt literal Aqua, Lore ausschließlich `Click to teleport.`; technische IDs stehen nur in der Verwaltung.

Die Admin-Erweiterungen sind `/warp show <id>`, `/warp hide <id>`, `/warp order <id> <order>` unter dem vorhandenen `vapeecore.warp.admin`-Gate. Order akzeptiert ausschließlich dezimale Ziffern mit kontrolliertem Int-Overflow, keine Vorzeichen, Leerzeichen, Brüche oder Exponenten. Help erklärt Navigator-only Visibility, Info zeigt Visible/Hidden und Order, List weiterhin alle IDs mit Metadaten. Completion ist permission-first und case-insensitive: Show nur hidden, Hide nur visible, Order alle gespeicherten IDs. Es gibt keinen direkten Player-`/warp <id>` und keinen Navigator-Command.

`NavigatorMenu` besitzt `Map<UUID, Inventory> activeInventories`. Erst ein erfolgreiches tatsächliches Open veröffentlicht die neue Instanz; gecancelte Opens erzeugen keinen neuen aktiven Eintrag. Ein Menü muss gleichzeitig Owner-UUID, `inventory.getHolder() == holder`, `holder.isBoundTo(inventory)`, Active-Registry und die tatsächlich geöffnete Top-Inventory erfüllen. `isBoundTo` funktioniert auch für ungebundene Holder ohne NPE. Ein alter Seiten-Holder kann weder teleportieren noch Controls auslösen. Close entfernt nur exakt die aktive Instanz; verspätetes Close einer alten Seite entfernt die neue Seite nicht.

Der Listener cancelt jedes erkannte Navigator-Click-Event zuerst, einschließlich ungebundener/forged Holder, fremder Viewer, Bottom-Transfers und stale Seiten. Nur LEFT/RIGHT auf dem aktiven Top-Inventar dürfen Aktionen auslösen. Shift, Number-Key, Double, Middle, Drop und Offhand-Swap bleiben inert. Drags über Top-Slots sind gecancelt; Bottom-only bleibt nur bei gültiger aktiver Ansicht und gültigem Zugriff erlaubt. Titel, Material, Name und Lore sind keine Action- oder Sicherheitsquelle.

`NavigatorAccessPolicy` verlangt bei Open, nach tatsächlichem Open sowie bei Click/Drag: Player online, `PlayerService#isLoaded`, aktuelle Lobby-Welt, `LobbyPlayerMode.NORMAL`, keine `ActivityService#isParticipating`-Membership. BUILD- oder Activity-Inventory-Übernahme schließt im vorhandenen State-Flow bereits die UI; die erneute Prüfung schützt zusätzlich stale Managed Items und Menüs. Denial öffnet nichts und schließt/vergisst eine eigene Navigator-Ansicht, ohne fremde Inventare zu schließen. Policy bleibt im Lobby-Application-Layer; WarpService kennt weder Lobby, PlayerService, Activity noch Staff-Hierarchie.

Die Anzeige ist ein Snapshot, die Ausführung liest aktuellen Domain-State. Holder speichern Slot→ID, niemals Slot→aktueller Index. `teleportFromNavigator` prüft aktuelle Existenz und Visible, danach dieselbe generische Teleport-Logik. Hidden/Removed liefern `That destination is no longer available.` und einen frischen View derselben, nötigenfalls geklemmten Seite. Reorder ändert keine bereits gebundene ID; Positionsänderungen verwenden bei Click die aktuelle Position. Nicht geladene Zielwelt wird nicht geladen; Bukkit/Paper-Cancellation liefert TELEPORT_FAILED und keinen Erfolg. Erfolgreicher Teleport verwendet PLUGIN, setzt FallDistance/Velocity zurück und schließt/vergisst das Menü, sodass wiederholte stale Events nicht erneut teleportieren. Sichtbare Ziele in derselben Lobby oder einer anderen geladenen Welt sind erlaubt; beim Verlassen entfernt der bestehende LobbyListener die Hotbar.

Quit vergisst die UUID auch bei bereits gewechseltem View. World Leave schließt/vergisst Navigator sofort; World Enter öffnet keine UI. Disable schließt getrackte eigene Ansichten vor Listener-Cleanup, leert die gesamte Active-Map auch bei einzelnen Close-Fehlern und nullt die Modulreferenzen. Partielle Enable-Fehler deaktivieren Experience-Callbacks, unregisteren alle drei Listener und reinigen den lokalen Navigator. Der bisherige Spawn-/Protection-/BUILD-/Activity-/Visibility-/Settings-/Presence-Flow bleibt bestehen, einschließlich globaler Join-/Quit-Texte.

Der Compass bleibt Warp Navigator; Hotbar exakt 0/4/8. Kein Server Menu, keine Kategorien, festen Ziele, zusätzlichen Items, neuen Scheduler, Async-Pipeline, Datenbank oder allgemeines GUI-Framework. Phase 29 ergänzt später Quest Completion; Shared GUI-/Inventory-/Item-Cleanup bleibt Phase 30C, target-controlled Team-Teleport-Consent bleibt Phase 30G. Vollständige Verifikation und Dateiinventar: `docs/LOBBY_NAVIGATION.md`.

## Lobby player state

`LobbyPlayerMode` enthält bewusst nur `NORMAL` und `BUILD`. Der Runtime-State liegt in `LobbyPlayerStateService` als kleine Menge aktiver BUILD-UUIDs. Er wird nicht im `CorePlayer`, nicht in `PlayerSettings` und nicht in Player-YAML gespeichert. Mutationen sind Main-Thread-only.

### NORMAL

In `NORMAL` besitzt die Lobby die Inventory-Präsentation vollständig. `LobbyPlayerStateService` schließt ein offenes Inventar, leert Cursor, Storage, Armor und Offhand, wählt Hotbar-Slot 0, setzt `player.gamemode` aus `lobby.yml` und lässt `LobbyItemService` die drei Items erzeugen:

- Slot 0: Warp Navigator (`navigator`)
- Slot 4: Player Visibility (`visibility`)
- Slot 8: Settings (`settings`)

Die PDC-ID liegt weiterhin unter dem Namespaced Key `lobby_item`. Die Ender Chest wird nicht angefasst. Zulässige Gamemodes sind die Bukkit-Werte, case-insensitive gelesen; Default und Fallback sind `ADVENTURE`. Ein ungültiger Wert wie `BANANA` erzeugt eine Warnung, startet trotzdem und verändert die Datei nicht.

### BUILD

`BUILD` ist ein temporärer administrativer Lobby-Zustand. Beim Eintritt werden UI und normales Inventory bereinigt, Lobby-Items entfernt, der Runtime-State gesetzt und der Gamemode auf `CREATIVE` gestellt. Währenddessen darf `LobbyListener` Block Break/Place sowie Item Drop/Pickup nur wegen dieses aktiven Zustands passieren lassen. Eine Permission allein ist kein Protection-Bypass.

Beim Austritt werden sämtliche Creative-Items, Armor, Offhand und Cursor-Inhalte entfernt; danach folgen konfigurierter NORMAL-Gamemode und Lobby-Hotbar. Quit entfernt den Runtime-State. Reconnect und Plugin-Neustart starten immer in `NORMAL`. Verlässt ein BUILD-Spieler die Lobby-Welt, werden State und Build-Inventar entfernt, der konfigurierte normale Gamemode gesetzt und keine Lobby-Items in der fremden Welt erzeugt. Bei einem Lobby-Respawn bleibt ein legitimer aktiver BUILD-Zustand erhalten.

Beim Modul-Shutdown werden online befindliche BUILD-Spieler ebenfalls bereinigt und aus `CREATIVE` zurückgesetzt. Managed Lobby-Items werden entfernt; sie werden während des Shutdowns bewusst nicht neu angewendet.

## Join-, Respawn- und World-Flow

Der Join läuft wegen der registrierten Modulreihenfolge nachvollziehbar so:

```text
PlayerJoinEvent
  → PlayerListener / PlayerService lädt das CorePlayer-Profil
  → LobbyListener plant den Lobby-Schritt für den nächsten Tick
  → optional LobbyService.teleportToSpawn
  → LobbyPlayerStateService.synchronizeJoin
      → BUILD-Runtime-State sicher entfernen
      → NORMAL-Gamemode
      → vollständiger Inventory-Reset
      → LobbyItemService.applyLobbyItems
  → UtilityListener läuft danach ebenfalls geplant
      → Walk/Fly Speed auf Vanilla-Defaults
      → SURVIVAL/ADVENTURE Flight-Leaks entfernen
      → CREATIVE/SPECTATOR Flight nicht beschädigen
  → LobbyExperienceListener synchronisiert danach Visibility/UI
```

Die geplante Ausführung prüft `PlayerService.isLoaded`, Online-Status, Plugin-Status und die tatsächliche Lobby-Welt. Dadurch läuft der Lobby-State nicht gegen fehlgeschlagenes Player-Laden. Join überschreibt insbesondere einen vor dem Disconnect gespeicherten oder anderweitig verbliebenen `CREATIVE`-Gamemode.

Nach Respawn plant `LobbyListener` ebenfalls eine Synchronisierung anhand der tatsächlichen Welt im Folgetick. `synchronize` stellt für `NORMAL` Gamemode und Lobby-Inventar wieder her; bei aktivem `BUILD` bleiben Modus und temporäres Build-Inventar erhalten. Beim Wechsel in die Lobby wird derselbe State synchronisiert. Beim Wechsel aus der Lobby ruft der Listener `leaveLobby` auf und verhindert Build-, Creative- und Hotbar-Leaks. Quit ruft ausschließlich Runtime-Cleanup des Lobby-States auf; Activity- und Sitz-Cleanup bleiben ihren eigenen Modulen überlassen.

`LobbyExperienceListener` ist nur noch der Event-Einstieg für Join-/Quit-Nachrichten sowie Visibility-Synchronisierung. Den Nachrichtentext und sicheren Fallback rendert `LobbyMessageService`; die Texte selbst bleiben in `lobby.yml`.

## `/build` flow und Konflikte

Aktivierung:

```text
/build
  → Bukkit-Permission vapeecore.utility.build
  → BuildCommand: Player + keine Argumente
  → LobbyService: Spieler ist in der Lobby-Welt
  → ActivityService: Spieler nimmt an keiner Activity teil
  → LobbyPlayerStateService.enterBuildMode
  → CREATIVE, keine Lobby-Items, temporäres Build-Inventory
```

Deaktivierung wird immer zuerst erkannt und bleibt damit auch als sicherer Exit möglich, falls sich die Welt unerwartet geändert hat:

```text
/build
  → LobbyPlayerStateService.exitBuildMode
  → Build-Inventory vollständig löschen
  → konfigurierten NORMAL-Gamemode setzen
  → in der Lobby die drei Lobby-Items neu erzeugen
```

`ActivityService.isParticipating` blockiert NORMAL → BUILD. In Gegenrichtung fragt Blackjack den vom `LobbyModule` gelieferten `LobbyPlayerStateService` ab und lehnt BUILD-Spieler vor Definition-, Sitz- oder Membership-Änderungen ab. Es gibt keine `UtilityModule → BlackjackModule`-Dependency und keine Lobby-Abhängigkeit im generischen Activity-Framework.

Vor NORMAL → BUILD gibt `BuildCommand` eventuell durch `/fly` verwaltetes Flight an `UtilityService` zurück. Erst danach übernimmt `LobbyPlayerStateService` über `CREATIVE`; damit besitzen Utility und BUILD Flight nie gleichzeitig.

Inventory-Ownership darf nie gleichzeitig bei zwei Systemen liegen: `NORMAL` gehört der Lobby, `BUILD` ist das temporäre Creative-Inventory und aktive Blackjack-Teilnahme gehört `BlackjackInventoryService`. `relinquishNormalInventory` entfernt Lobby-Items und leert alle Inventory-Flächen vor der Übernahme. Reguläres Leave stellt NORMAL nur in der Lobby wieder her; Quit, World Change, Tod und Plugin-Shutdown bereinigen ohne Reapply.

## Community seating

`SeatService` ist der alleinige Owner von Reservation, Occupancy, UUID-basierter Player-Zuordnung, technischer ArmorStand-Entity, Mount, programmatic Dismount, Owner-Cleanup und stale Cleanup. `SeatKey(owner, id)` gruppiert Sitze ohne Feature-Sonderlogik; `SeatType` trennt `CASUAL` von `MANAGED`. Ein Spieler und ein Key können gleichzeitig jeweils höchstens eine Zuordnung besitzen. Die immutable `SeatAssignment` speichert Welt, Position, Rotation und optional die Entity-UUID. Runtime-Mutationen sind Main-Thread-only; direkte dauerhafte Player- oder Entity-Referenzen werden nicht gespeichert.

Die generischen PDC-Keys sind `seat`, `seat_owner`, `seat_id` und `seat_type`. Neue Entities sind unsichtbar, ohne Gravitation, invulnerable, silent, nicht kollidierbar, nicht persistent, ohne Baseplate/Arme/AI und soweit von Paper unterstützt unbeweglich. Der technische Passenger-Offset `-1.70D` liegt ausschließlich in `SeatService`. Beim Start werden markierte generische Entities und als Migrationsschutz auch alte `blackjack_seat`-ArmorStands entfernt. Fremde ArmorStands bleiben unangetastet.

`SeatListener` erlaubt Casual Seating ausschließlich in der konfigurierten Lobby-Welt: Main-Hand-Rechtsklick, leere Hand, nicht sneaken, kein BUILD, keine Activity-Membership, außerhalb jeder registrierten ActivityVenue-Area, kein bestehendes Vehicle/Seat und passierbarer Raum über dem Block. Unterstützt werden Bottom-Stairs sowie Bottom- und Top-Slabs; Double-Slabs und Top-Stairs werden bewusst abgelehnt. `SeatPositionResolver` setzt den Blockmittelpunkt, Bottom-Oberflächen auf `y + 0.5`, Top-Slabs auf `y + 1.0`, Slab-Yaw auf den Player-Yaw und Stair-Yaw auf die Gegenrichtung des Stair-Facings. Es gibt keinen Tick-/Move-Listener, keine Permission, kein `/sit`, keine Config und keine Persistence.

Normales Shift-Dismount entfernt Assignment und Entity. Ein `MANAGED`-Callback wird einen Tick verzögert nur bei einem echten Player-Dismount ausgeführt und prüft vorher Plugin-, Online- und Relevanzzustand. Programmatic Release, Quit, World Change, Tod und der Abbau eines belegten Casual-Blocks bereinigen ohne freiwilligen Feature-Callback. `CASUAL` erzeugt keine Activity und keinen neuen LobbyPlayerMode und verändert weder Gamemode noch Inventory.

`BlackjackSeatService` ist der fachliche Allocation-Adapter: moderne Sitzklicks reservieren exakt die angeklickte Nummer, Legacy-Interaktionen weiterhin die niedrigste freie Nummer. Table-/Seat-Mapping, `blackjack:<table-id>`-Owner und Reservation/Mount/Release delegieren an SeatService. `activity.blackjack.interaction.BlackjackTableListener` priorisiert Hotbar-Actions, dann moderne Sitzblöcke, dann Legacy-Interaktionsblöcke. Der transaktionale Ablauf Reservation → Activity-Join → Mount → Inventory-Übernahme besitzt vollständigen Rollback.

## Native world displays

`WorldDisplayService` verwaltet runtime-eindeutige `WorldDisplayKey(owner, id)` und immutable `WorldDisplayHandle`-Werte mit Entity-UUID und Typ. Es erzeugt native `TextDisplay`- und `ItemDisplay`-Entities, aktualisiert Inhalt typsicher, teleportiert, entfernt idempotent und kann alle Displays eines Owners freigeben. ItemStacks werden bei Create und Update defensiv geklont. Ein Duplicate-Key ist ein kontrollierter Programmierfehler; eine verschwundene Entity liefert bei Update/Teleport `false` und entfernt den stale Registry-Eintrag. Ein falscher Update-Typ wirft konsistent `IllegalArgumentException`.

Die PDC-Keys sind `world_display`, `world_display_owner`, `world_display_id` und `world_display_type`. Displays sind nicht persistent, ohne Gravitation, invulnerable und silent. Feature-spezifische Transformation, Billboard, Scale, Brightness, Textausrichtung und Interpolation bleiben beim Consumer. Beim Modulstart werden ausschließlich markierte Text-/ItemDisplays entfernt; beim Shutdown alle registrierten Displays. Es gibt keinen Polling-Task, keine Display-Config/-Persistence, keine Commands, keine Packet-/NMS-Abhängigkeit und keine Demo-Entities.

WorldDisplay ist kein Admin-Hologramm-System. Ein externes Hologramm-Plugin darf parallel für frei konfigurierbare statische Hologramme verwendet werden, VapeeCore besitzt jedoch keine harte Dependency darauf.

## Physical Blackjack Table

Der eigentliche Blackjack-Tisch wird vom Map-Builder aus normalen Minecraft-Blöcken gebaut und bleibt vollständig Map-owned. VapeeCore kennt keine Blockliste des Tisches, speichert keine Schematic und führt weder automatische Reparatur noch Regeneration aus. Nur die konfigurierten Seat-Blöcke sind konkrete technische Sitzpunkte; die `ActivityArea` beschreibt den gesamten Gameplay-/Ownership-Bereich mit Tisch, Sitzen und Dealerbereich. Der Builder kann die Struktur jederzeit ändern, solange Area, Seat-Blöcke und Spielflächen-Anchor danach weiterhin stimmen oder neu gesetzt werden. Es gibt bewusst kein Furniture-Modul, keine ItemDisplay-Möbel, keine CustomModelData- und keine Resource-Pack-Abhängigkeit.

Als unverbindliches Baubeispiel eignet sich ein sieben bis neun Blöcke breiter und vier bis fünf Blöcke tiefer Tisch: grüne Spielfläche, dunkler Rand, bis zu fünf Stairs oder einzelne Slabs als Sitze und der Dealer gegenüber der Spielerseite. Diese Maße sind nur eine Empfehlung; die visuelle Wahrheit ist der Table-Surface-Anchor, nicht eine fest codierte Tischform.

Der Abschnitt `tables.<id>.display` ist semantisch die Mitte der nutzbaren physischen Spielfläche: `x/z` sind ihr Center, `y` ist exakt ihre Oberfläche und `yaw` ihre Hauptausrichtung. `/blackjack setup display <id>` speichert Blockmittelpunkt, reale Collision-Shape-Oberkante und Player-Yaw. Aus dem Yaw leitet `BlackjackDisplayGeometry` normalisierte Forward-/Right-Vektoren für Playerkarten und Status ab. Playerkarten liegen zwischen Seat und Center, beziehen ihre Y-Höhe aber immer von `display.y + CARD_HOVER`; Ergebnisse liegen knapp hinter der jeweiligen Kartenfläche.

Der Dealer besitzt davon unabhängig ein einzelnes aufrechtes Floating-Hand-`TextDisplay`. `/blackjack setup dealer <id>` wird ausgeführt, während der Admin an der Dealerposition steht und zum Tisch schaut. Die gespeicherte Dealer-Blickrichtung bestimmt den horizontalen Forward-Vektor; `dealerHandLocation` verschiebt das Display von der Dealerposition um `DEALER_HAND_FORWARD_OFFSET` nach vorne und `DEALER_HAND_VERTICAL_OFFSET` nach oben. Es besteht keine Abhängigkeit von Table-Surface-Y oder Tischform.

Der vollständige Setup-Ablauf ist:

1. Physischen Tisch in der Welt bauen.
2. `/blackjack setup create <id>`
3. `/blackjack setup pos1 <id>`
4. `/blackjack setup pos2 <id>`
5. An der Dealerposition stehen, zum Tisch schauen und `/blackjack setup dealer <id>` ausführen.
6. Spielfläche ansehen und `/blackjack setup display <id>` ausführen.
7. Jeden Sitz ansehen und `/blackjack setup seat <id> <number>` ausführen.
8. Mit `/blackjack setup preview <id>` visuell kalibrieren.
9. `/blackjack setup enable <id>`

Die Preview benötigt keine ActivitySession und funktioniert bei disabled, enabled und teilweise konfigurierten Tabellen. Für zwölf Sekunden zeigt sie `CENTER`, optional `DEALER HAND`, `STATUS` und nur die vorhandenen `SEAT n`-Marker; fehlende Komponenten werden zusätzlich im Chat genannt. Der Dealer-Marker verwendet exakt dieselbe `dealerHandLocation` wie die Production-Anzeige. Ihr Owner `blackjack-preview:<adminUuid>:<tableId>` ist vom Produktions-Owner getrennt. Timeout, eine neue Preview desselben Admins, Player-Quit und Modul-Shutdown räumen sie auf, ohne Produktionsanzeigen zu berühren. Setup-Markierungen werden nie persistiert.

### Blackjack world UX

`BlackjackTableInteractionMode` trennt `MODERN_SEAT_CLICK` und `LEGACY_INTERACTION`. Ein moderner Seat speichert unter `seats.<n>.block` die konkrete Welt-/Blockposition und unter `seats.<n>.position` die aufgelöste Sitzposition samt Yaw/Pitch. `/blackjack setup seat <id> <1-5>` verlangt einen Zielblock in höchstens sechs Blöcken Entfernung und verwendet `SeatPositionResolver`; Bottom-Stairs sowie einzelne Bottom-/Top-Slabs sind erlaubt, Top-Stairs, Double-Slabs und andere Blöcke nicht. Die resolved Position darf über der Area-Grenzebene liegen, solange der konkrete Sitzblock die Area berührt. Vollständig alte, flache Seat-Positionen bleiben lesbar und benötigen `interaction`. Moderne Tische ignorieren einen eventuell noch vorhandenen alten Interaction-Key. Gemischte Schemas, doppelte Blöcke innerhalb einer Definition und Blockkonflikte zwischen aktivierten Tischen sind ungültig.

`BlackjackDisplayAnchor` bleibt optional: Alte Dateien ohne den Abschnitt laden unverändert, `BlackjackTableService` protokolliert den Legacy-Fallback und `/blackjack setup info <id>` zeigt bei `Table Surface` den Hinweis `Missing - using legacy geometry`. Neue Tabellen sollten vor dem Enable immer den oben beschriebenen Preview-Schritt verwenden.

`BlackjackTableService` hält getrennte O(1)-Indizes für Legacy-Interaktionen und moderne `BlackjackBlockPosition → BlackjackTableSeatReference`-Zuordnungen. Vor Runtime-Aktivierung wird ein moderner Block erneut durch `SeatPositionResolver` validiert. Enable erzeugt sofort die Idle-Anzeige; Disable, Rollback und Shutdown entfernen alle Anzeigen und Seat-Indizes.

`BlackjackInventoryService` besitzt während der Teilnahme die Player-Hotbar. Die Action-PDC heißt `blackjack_action`; Status ist markiert, aber nicht ausführbar. Slots: `0` Deal oder Hit, `1` Stand, `2` Double, `4` Status, `8` Leave. `AVAILABLE` zeigt Deal/Status/Leave. Nur der aktuelle Spielerzug zeigt Hit/Stand und – solange legal – Double. Fremder Zug, Dealer-Zug und Settlement zeigen ausschließlich Status/Leave. Beim Activity-Reset löscht `BlackjackSession.onReset()` ausschließlich Rundendaten. `ActivityService` wechselt danach auf `AVAILABLE` und ruft erst dann `onResetCompleted()` auf; Blackjack aktualisiert dort Hotbar und Weltansicht. Fehler im Post-Reset-Hook werden protokolliert und schließen die Session kontrolliert. Dadurch bleiben Participant, Sitz und Inventory-Ownership erhalten, während Deal sofort wieder in Slot 0 erscheint. Drop, Inventory-Click inklusive Number-Key, Drag, Offhand-Swap und Pickup werden nur für aktuelle Blackjack-Owner blockiert. `PlayerDeathEvent` entfernt markierte Drops; der generische Activity-Listener verlässt mit `DEATH`.

Double Down liegt in `BlackjackService.doubleDown`: Teilnahme, `ACTIVE`, `PLAYER_TURNS`, eigener Zug, unfertige Hand, genau zwei Karten und kein Natural sind zwingend. Der Timeout wird abgebrochen, genau eine Karte gezogen, `BlackjackPlayerRound.doubledDown` gesetzt, die Hand beendet und zum nächsten Spieler beziehungsweise Dealer weitergeschaltet. Es gibt weiterhin keine Wette und keine Economy-Auswirkung.

`BlackjackWorldViewService` nutzt ausschließlich `WorldDisplayService`. Owner ist `blackjack:<tableId>`; Keys sind `status`, genau ein `dealer-hand`, `seat-<n>-label` und `seat-<n>-card-<i>`. Playerkarten bleiben einzelne horizontale Displays auf der Table Surface. Die Dealerhand ist dagegen ein vertikales Billboard an der Dealerposition: Während `PLAYER_TURNS` zeigt sie beispielsweise `K♦   ◆` und darunter `Dealer • 10`; in `DEALER_TURN` und `SETTLED` werden sämtliche Karten und der Gesamtwert im selben Display sichtbar. Dealer-Hits führen deshalb nur zu `updateText` und Teleport des bestehenden Keys, unabhängig von der Kartenanzahl. Ab der fünften Karte wird die Hand nach vier Karten umgebrochen. Beim Reset verschwindet `dealer-hand`, Disable und Shutdown bereinigen weiterhin den kompletten Owner.

Ein Refresh aktualisiert bestehende TextDisplays, erzeugt fehlende und entfernt nur nicht mehr gewünschte Keys; nur ein tatsächlicher Stilwechsel ersetzt den betroffenen Display-Key. Er wird durch Join/Leave und Rundenzustandsänderungen ausgelöst, nicht durch einen Tick-Task. Idle zeigt ausschließlich `Blackjack` plus `n / capacity`. Aktive Spielerhände zeigen Karten, kleinen Zahlenwert und Zugstatus; Settlement ersetzt den Wert durch ein kleines farbiges Resultat nahe den Karten. `OPEN_CARD`, `DEALER_HAND`, `STATUS`, `RESULT` und `VALUE` besitzen getrennte Styles. Presentation-Fehler werden protokolliert und brechen das Gameplay nicht ab.

`BlackjackService.requiresDealerPlay` überspringt die Dealer-Ausspielung, wenn ausschließlich Bust-Hände oder bereits sichere Naturals übrig sind; normale Live-Hände spielen den Dealer weiterhin bis mindestens 17 aus, inklusive Stand auf Soft 17. `BlackjackShoe` erzeugt sechs vollständige Decks (312 Karten), mischt per Fisher-Yates mit `nextInt(index + 1)` und verwendet keinen konstanten Seed oder dynamische Spielerbevorzugung. `BlackjackFairnessHarness` prüft mit festen Seeds Verteilung, Reproduzierbarkeit, unterschiedliche Reihenfolgen, Ziehen ohne Replacement sowie Dealer-/Outcome-Invarianten; er besitzt absichtlich keine zufällige Winrate-Grenze.

Die Blackjack-Engine besitzt den mutablen Runden-, Hand- und Shoe-Zustand. Presentation liest auf dem Hauptthread ausschließlich immutable Snapshots über `BlackjackSession#getDealerHandView`, `getPlayerRoundView` und `getPlayerRoundViews`: `BlackjackHandView` kopiert die Kartenliste und speichert den zentral berechneten Wert sowie Natural-Status; `BlackjackPlayerRoundView` enthält diese Handansicht, Spieler-UUID, Finished-/Double-Flags und optionales Outcome. Bereits gelesene Views ändern sich durch spätere Engine-Mutationen oder Reset nicht. Mutable Session-/Round-Getter bleiben package-private; es gibt keinen öffentlichen Current-Shoe-Read. Details und Regression: [ARCHITECTURE_REFACTOR.md](ARCHITECTURE_REFACTOR.md).

### Blackjack tuning map

| Frage | Stelle |
|---|---|
| Wo ändere ich den Table-/Card-Anchor? | `/blackjack setup display <id>` und `BlackjackDisplayGeometry` |
| Wo ändere ich den Abstand zwischen Karten? | `BlackjackDisplayGeometry.CARD_SPACING` |
| Wo ändere ich die Kartengröße? | `BlackjackDisplayGeometry.CARD_SCALE` |
| Wo ändere ich die Kartenhöhe? | `BlackjackDisplayGeometry.CARD_HOVER` |
| Wo ändere ich Handwert-Größe? | `BlackjackDisplayGeometry.HAND_VALUE_SCALE` |
| Wo ändere ich Resultat-Größe/-Höhe? | `BlackjackDisplayGeometry.RESULT_SCALE` / `RESULT_HEIGHT` |
| Wo ändere ich Status-Größe/-Position? | `BlackjackDisplayGeometry.STATUS_SCALE` / `STATUS_HEIGHT` |
| Wo ändere ich den Abstand der Playerkarten? | `BlackjackDisplayGeometry.PLAYER_CARD_DISTANCE` |
| Wo ändere ich den Dealer-Display-Abstand? | `BlackjackDisplayGeometry.DEALER_HAND_FORWARD_OFFSET` |
| Wo ändere ich die Dealer-Display-Höhe? | `BlackjackDisplayGeometry.DEALER_HAND_VERTICAL_OFFSET` |
| Wo ändere ich die Dealer-Display-Größe? | `BlackjackDisplayGeometry.DEALER_HAND_SCALE` |
| Wo ändere ich die Hotbar Items? | Factory-Aufrufe in `BlackjackInventoryService.refreshPlayer` |
| Wo ändere ich die Hotbar Slots? | `PRIMARY_SLOT`, `STAND_SLOT`, `DOUBLE_SLOT`, `STATUS_SLOT`, `LEAVE_SLOT` in `BlackjackInventoryService` |
| Wo ändere ich die Seat-Setup-Regeln? | `BlackjackCommand.seat` und `SeatPositionResolver` |
| Wo ändere ich Double? | `BlackjackService.doubleDown` und `BlackjackPlayerRound` |
| Wo ändere ich die Reset-Presentation? | Activity-Reset-Lifecycle und `BlackjackSession.onResetCompleted` |
| Wo ändere ich die Dealer-Play-Entscheidung? | `BlackjackService.requiresDealerPlay` |

## Should I change Java or configuration?

| Änderung | Richtiger Ort |
|---|---|
| Wortlaut einer bestehenden Join-/Quit-Nachricht | Live-`lobby.yml` |
| Default-Text für einen neuen Server | Resource-`lobby.yml` und internen Fallback gemeinsam prüfen |
| Rendering, Placeholder oder Laufzeit-Fallback | `LobbyMessageService` |
| Normaler Lobby-Gamemode | Live-`lobby.yml`, Key `player.gamemode` |
| BUILD-Regeln, Cleanup oder Inventory-Semantik | `LobbyPlayerStateService` |
| Warp-Position | `/warp set …` beziehungsweise `warps.yml` |
| Blackjack-Tischposition | `/blackjack setup …` beziehungsweise `blackjack.yml` |
| Blackjack-Table-/Card-Anchor | `/blackjack setup display <id>` und `BlackjackDisplayGeometry` |
| Casual-Sitzbedingungen und Cleanup-Events | `SeatListener` |
| Seat-Reservation, Entity und PDC | `SeatService` |
| Stair-/Slab-Geometrie | `SeatPositionResolver` |
| Blackjack-Sitzverteilung | `BlackjackSeatService` |
| Blackjack-Hotbar-Materialien und Slots | `BlackjackInventoryService` |
| Blackjack-Kartenposition, Abstand und Höhe | `BlackjackDisplayGeometry` |
| Floating Dealer Hand: Abstand, Höhe und Größe | `BlackjackDisplayGeometry.DEALER_HAND_FORWARD_OFFSET`, `DEALER_HAND_VERTICAL_OFFSET`, `DEALER_HAND_SCALE` |
| Blackjack-Display-Texte und Ausrichtung | `BlackjackWorldViewService` |
| Blackjack-Round-Reset-Presentation | `ActivityService.resetSessionInternal`, `ActivitySession.onResetCompleted`, `BlackjackSession` |
| Blackjack-Dealer-Ausspielentscheidung | `BlackjackService.requiresDealerPlay` |
| Double-Down-Regeln | `BlackjackService.doubleDown` |
| Native Text-/ItemDisplays | `WorldDisplayService` |
| Command-Help-Design und Rendering | `dev.vapee.core.command.help` |
| Hauptübersicht und `/core help` | `CoreCommand` |
| Coin-Command-UX | `CoinsCommand` |
| Blackjack-Command-UX | `BlackjackCommand` |
| Warp-Command-UX | `WarpCommand` |
| Utility-Bukkit-Mutationen und Flight-/Speed-Cleanup | `UtilityService` |
| Utility-Argumente, Rechte, Guards, Texte oder Completion | jeweilige Klasse unter `utility/command` |
| Invsee-Snapshot, Read-only-Schutz und View-Lifecycle | `InvseeService` und `InvseeInventoryHolder` |
| Quest Progress Engine | `QuestService` |
| Quest Definition Domain und Katalog | `QuestDefinition` / `QuestDefinitionRegistry` |
| Technische Quest Progress Keys | `QuestProgressKey`; playtime:minute im QuestPlaytimeProducer, blackjack:win im BlackjackModule |
| Player Quest Persistence | `PlayerQuestState`, `PlayerQuestProgress` und `FilePlayerRepository` |
| Daily-Quest-Anzahl, Reset, Zeitzone und Definitionen | `daily-quests.yml` |
| Daily-Cycle-Berechnung | `DailyQuestCycleResolver` |
| Deterministische Daily-Auswahl | `DailyQuestSelector` |
| Daily-Assignment und Rotation | `DailyQuestService` |

## Utility ownership und Lifecycle

`UtilityModule` bleibt genau ein `CoreModule`. Beim Enable bezieht es Lobby- und Activity-Services, erstellt `UtilityService`, `UtilityListener` und den read-only `InvseeService`, registriert zwölf Commands und anschließend beide Listener. Die Startup-Zahl wird aus der tatsächlich registrierten Command-Liste abgeleitet. Utility ist kein `ReloadParticipant` und besitzt keine Configdatei; seit Phase 27 betragen Module Count und Reload Count insgesamt 27 beziehungsweise 6. Beim Disable werden offene Invsee-Snapshots geschlossen, deren UUID-Metadaten geleert, verwaltetes Flight und beide Speed-Kanäle online normalisiert, Listener abgemeldet und Executor sowie TabCompleter entfernt. Der Gamemode wird dabei bewusst nicht zurückgesetzt.

`UtilityService` ist der einzige Owner der eigentlichen Bukkit-Mutationen für Flight, Speed, Gamemode, Utility-Teleports, Heal, Feed, sicheres Inventory-Clear und das Öffnen echter Enderchests. Alle Mutationen sind Main-Thread-only. Der Service hält niemals dauerhafte `Player`-Referenzen, sondern ausschließlich UUID-Mengen für command-managed Flight und Speed. Diese Daten werden weder in `CorePlayer` noch in `PlayerSettings` oder `players/<uuid>.yml` persistiert. Teleportziele und letzte Positionen werden ebenfalls nicht gespeichert; `/back` gehört nicht zu dieser Phase.

`UtilityListener` entfernt beim Quit den UUID-State und normalisiert verwaltete Bewegung soweit noch sicher möglich. Beim Join vergisst er zunächst möglichen stale Runtime-State und plant die eigentliche Normalisierung einen Tick später. Dadurch läuft zuerst der bereits geplante Lobby-Join-State; anschließend setzt Utility Walk Speed auf `0.2F`, Fly Speed auf `0.1F` und entfernt in `SURVIVAL`/`ADVENTURE` unerwartetes Flight. Native Flight-Semantik in `CREATIVE`/`SPECTATOR` bleibt erhalten. Diese Join-Normalisierung deckt auch Prozessabbrüche ab, bei denen reguläres Disable-Cleanup nicht lief.

`OnlinePlayerResolver` löst Targets ausschließlich aus `Server#getOnlinePlayers()` über einen vollständigen, case-insensitive Namen auf. Es gibt weder Partial-/Fuzzy-Matches noch `OfflinePlayer`, Identity-Lookup oder Netzwerkzugriff. Bei optionalem Target reicht für Self die Basispermission; ein anderes Target und Console-mit-Target benötigen `.others`. Player-Completion wird ohne die erforderliche Permission nicht offengelegt und sonst case-insensitive stabil sortiert. Playernamen werden ausschließlich als `Component.text` gerendert. Gruppen oder Ränge sind keine Business-Logik; die kanonischen externen LuckPerms-Empfehlungen stehen in `docs/PERMISSIONS.md`.

VapeeCore kennt die Gruppennamen Builder, Moderator und Admin ausdrücklich nicht; sie sind nur Beispiele für eine Serverkonfiguration.

`/fly` verwaltet ausschließlich Flight in `SURVIVAL` und `ADVENTURE`. BUILD sowie `CREATIVE`/`SPECTATOR` behalten ihren jeweiligen nativen Owner. `/speed` akzeptiert Level 1–10; Level 1 entspricht Walk `0.2F` beziehungsweise Fly `0.1F`, Level 10 jeweils `1.0F`. Der Fly-Kanal gilt bei aktivem Fliegen sowie in `CREATIVE`/`SPECTATOR`, sonst der Walk-Kanal. `/gamemode` akzeptiert vollständige Namen, `s/c/a/sp` und `0/1/2/3`; Completion zeigt nur vollständige Namen. Ein BUILD-Target wird abgewiesen. Ein späterer Lobby-Resync darf einen temporär gesetzten Gamemode wieder auf `lobby.yml` normalisieren; Creative allein schaltet nie BUILD oder Protection-Bypass ein.

`/tp` und sein Alias `/teleport` gehören VapeeCore; `/minecraft:tp` und `/minecraft:teleport` bleiben Vanilla-Namespace-Commands und werden nicht abgefangen. `TeleportParser` unter `utility.teleport` besitzt die Grammatik und Koordinatenmathematik ohne `CommandSender`-Abhängigkeit. `TeleportCommand` besitzt Sender-/Permission-Matrix, exakte Online-Player-/World-Auflösung, Activity-Guards, Completion und Adventure-Feedback. `UtilityService` führt ausschließlich den synchronen Bukkit-Teleport aus und propagiert `false` bei Event-Cancellation oder technischer Ablehnung; Ziel-Locations werden kopiert. Vor jeder Mutation werden alle Argumente vollständig geprüft.

Die Grammatik ist `/tp <target>`, `/tp <x> <y> <z> [yaw pitch]`, `/tp <source> <target>`, `/tp <source> <x> <y> <z> [yaw pitch]`, `/tp world <world> <x> <y> <z> [yaw pitch]` oder `/tp <source> world <world> <x> <y> <z> [yaw pitch]`. Player-Namen sind case-insensitive, aber exakt und ausschließlich online; keine Selectors, Entities, Partial-/Offline-/Identity-Lookups. Weltnamen sind ebenfalls case-insensitive und exakt, aber nur bereits geladene Welten werden akzeptiert. Player→Player darf ohne World-Recht zwischen Welten wechseln. Eine explizite Welt ersetzt nur die Zielwelt: Es gibt weder Nether-Skalierung noch World-Auto-Load, Safe-Ground-Verschiebung, WorldBorder-Clamp oder dauerhafte Chunk-Tickets.

XYZ unterstützt endliche absolute Zahlen und `~`-relative Offsets auf Basis der **bewegten Quelle**. Sobald ein `^` vorkommt, müssen alle drei XYZ-Tokens lokal sein: `^X` entlang der lokalen Linksachse, `^Y` entlang der nach oben gedrehten Achse, `^Z` entlang der Blickrichtung. Die Basis ist immer Position plus Yaw/Pitch der Quelle, auch wenn der ausführende Sender ein anderer Spieler oder die Console ist. Ohne Yaw/Pitch bleibt die Source-Rotation erhalten; optional können beide Werte absolut oder mit `~` relativ zur Source-Rotation sein. Nicht-endliche Werte werden abgewiesen, Pitch wird deterministisch auf −90° bis +90° geklemmt. Das Ziel wird weder nachträglich geerdet noch an Weltgrenzen angepasst.

Self→Player/-Koordinaten erfordert `.teleport`, Self→explizite Welt `.teleport.world`, Other→Player/-Koordinaten `.teleport.others` und Other→explizite Welt `.teleport.others.world`. In `plugin.yml` gewähren `.others` und `.world` jeweils `.teleport` als Child; `.others.world` gewährt beide speziellen Rechte. Die Console darf nur Formen mit explizit benannter Online-Quelle ausführen und hat nie einen impliziten Self-Standort. Gleiche Quelle und Ziel werden ohne Mutation abgewiesen. `/tphere <player>` bleibt Player-only mit eigenem Recht `.teleport.here`.

Ohne `vapeecore.utility.teleport.bypass` schützen `/tp` und `/tphere` Activity-Teilnehmer als bewegte Quelle und bei Player→Player auch Activity-Ziele als internen State-Guard. Koordinatenziele haben keinen Zielspieler-Guard. Der Bypass überspringt ausschließlich diese VapeeCore-Prüfungen, nicht Syntax, Rechte, Offline-/World-Checks, `PlayerTeleportEvent`-Cancellation oder `teleport(...) == false`. Fehlgeschlagene Teleports erhalten keine Erfolgsmeldung. BUILD-Teleports werden nicht doppelt behandelt: Ein tatsächlicher Weltwechsel löst den bestehenden `LobbyListener`-Cleanup aus, ein Teleport innerhalb derselben Lobby-Welt behält BUILD.

Alle Utility-Mutationen, die laufenden Gameplay-State stören würden, fragen direkt und schmal `ActivityService.isParticipating(UUID)` ab. Das generische Activity-Framework erhält keine Utility-Regeln. `/heal` setzt aktuelle Max-Health sowie Fire-/Freeze-Ticks zurück, verändert aber weder Hunger, Inventory, Gamemode noch Potion Effects. `/feed` setzt Food 20, Saturation 20 und Exhaustion 0, verändert aber Health nicht. `/clear` blockiert sowohl Activity- als auch BUILD-Targets und besitzt absichtlich keinen Bypass; erst nach den Guards löscht `UtilityService` Storage, Armor und Offhand.

`/ping` liest ausschließlich den synchron verfügbaren Ping eines Online-Spielers und erzeugt weder Persistence noch Scheduler. `/enderchest` öffnet für Self oder ein online befindliches Others-Target das echte Enderchest. Die Others-Form ist deshalb eine mutierbare Staff-Funktion und nur für Admin/Owner vorgesehen; die Console wird abgewiesen, weil sie kein Inventar-UI öffnen kann.

`/invsee <player>` erstellt dagegen immer einen 54-Slot-Snapshot: Slots 0–8 enthalten die Hotbar, 9–35 das Main Inventory, 45–48 Helmet/Chestplate/Leggings/Boots und Slot 50 die Offhand. Jedes Item wird geklont. `InvseeInventoryHolder` bindet Viewer-UUID, Target-UUID und exakt die erzeugte Inventory-Instanz; Titel werden nie als Sicherheitsmerkmal verwendet. `InvseeService` canceln jeden Click einschließlich Shift, Number Key, Double Click, Collect, Drop, Creative und Offhand/Hotbar Swap sowie jeden Drag. Forged, falsche oder stale Holder bleiben ebenfalls read-only. Close, Viewer-Quit, Target-Quit und Module-Disable entfernen die UUID-basierte Runtime-View; dauerhafte Player-Referenzen oder Scheduler existieren nicht. `vapeecore.utility.invsee.modify` ist reserviert und in Phase 17C.1 nicht aktiv.

## Commands und permissions

| Command | Zweck | Zuständige Klasse | Permission |
|---|---|---|---|
| `/vapeecore`, `/core help`, `/core version`, `/core reload` | Status, permission-aware Hilfe, Version, koordinierter Reload | `CoreCommand` | Nur Reload: `vapeecore.admin` |
| `/spawn` | Zum Lobby-Spawn teleportieren | `SpawnCommand` | `vapeecore.lobby.spawn` |
| `/setspawn` | Lobby-Spawn speichern | `SetSpawnCommand` | `vapeecore.lobby.setspawn` |
| `/build` | Temporären Lobby-BUILD-Modus umschalten | `BuildCommand` | `vapeecore.utility.build` |
| `/fly [player]` | Command-managed Flight umschalten | `FlyCommand` | `vapeecore.utility.fly`, fremde Targets: `.fly.others` |
| `/speed <1-10> [player]` | Kontextabhängigen Walk-/Fly-Speed setzen | `SpeedCommand` | `vapeecore.utility.speed`, fremde Targets: `.speed.others` |
| `/gamemode`, `/gm` | Gamemode setzen | `GameModeCommand` | `vapeecore.utility.gamemode`, fremde Targets: `.gamemode.others` |
| `/tp`, `/teleport` | Self/Other zu Player, XYZ oder expliziter Welt teleportieren | `TeleportCommand`, `TeleportParser` | `.teleport`, `.teleport.others`, `.teleport.world`, `.teleport.others.world`; interner Guard-Bypass: `.teleport.bypass` |
| `/tphere <player>` | Online-Spieler zum Sender teleportieren | `TeleportHereCommand` | `vapeecore.utility.teleport.here` |
| `/heal [player]` | Aktuelle Max-Health wiederherstellen | `HealCommand` | `vapeecore.utility.heal`, fremde Targets: `.heal.others` |
| `/feed [player]` | Hunger, Saturation und Exhaustion normalisieren | `FeedCommand` | `vapeecore.utility.feed`, fremde Targets: `.feed.others` |
| `/ping [player]` | Online-Latenz anzeigen | `PingCommand` | `vapeecore.utility.ping`, fremde Targets: `.ping.others` |
| `/clear [player]` | Sicheres Player-Inventar löschen | `ClearCommand` | `vapeecore.utility.clear`, fremde Targets: `.clear.others` |
| `/invsee <player>` | Read-only Inventory-Snapshot öffnen | `InvseeCommand` | `vapeecore.utility.invsee`; `.invsee.modify` reserviert/inaktiv |
| `/enderchest [player]` | Echtes Enderchest öffnen | `EnderChestCommand` | `vapeecore.utility.enderchest`, fremde Targets: `.enderchest.others` |
| `/coins`, `/coins help`, `/coins …` | Eigene Coins / Hilfe / bekannte Online-/Offline-Balances lesen, Online-Balances administrieren | `CoinsCommand` | Basis `vapeecore.economy.coins`, get/add/remove/set zusätzlich `vapeecore.economy.admin` |
| `/msg`, `/reply`, `/r` | Private Online-Nachrichten | `MessageCommand`, `ReplyCommand` | `vapeecore.message.use` |
| `/settings`, `/settings visibility [add|remove …]` | Settings-/Visibility-Menüs öffnen und Added Users verwalten | `SettingsCommand`, `VisibilitySettingsMenu`, `VisiblePlayersMenu` | `vapeecore.settings.use` |
| `/quests`, `/quest` | Aktuelle Daily-Quests nach Sync read-only anzeigen | `DailyQuestCommand`, `DailyQuestMenu` | `vapeecore.quest.use` (Default true, keine Children) |
| `/friend`, `/friends` | Ohne Argumente Friends-GUI; mit Subcommands Freundschaften und Anfragen verwalten | `FriendCommand`, `FriendMenu` | `vapeecore.friend.use` (Default `true`) |
| `/ignore`, `/unignore`, `/ignorelist` | Ignore-State verwalten | `IgnoreCommand`, `UnignoreCommand`, `IgnoreListCommand` | `vapeecore.social.ignore` |
| `/rank [player]` | Eigenen oder den Rank eines Online-Spielers anzeigen | `RankCommand` | `vapeecore.rank.view` (Default `true`) |
| `/ranks` | Öffentliche LuckPerms-Track-Reihenfolge anzeigen | `RanksCommand` | `vapeecore.ranks.view` (Default `true`) |
| `/blackjack`, `/blackjack help`, `/blackjack setup …` | Strukturierte Hilfe und Verwaltung physischer Blackjack-Tische | `BlackjackCommand` | `vapeecore.blackjack.admin` |
| `/warp`, `/warp help`, `/warp …` | Strukturierte Hilfe und Verwaltung dynamischer Warps | `WarpCommand` | `vapeecore.warp.admin` |
| `/mute <player\|uuid> <duration\|permanent> <reason...>` | Known online/offline; Chat und ausgehende PM sperren | `MuteCommand`, `ModerationMuteProjection`, `ModerationMuteChatListener` | `vapeecore.moderation.mute` |
| `/unmute <player\|uuid> [reason...]` | Aktiven Mute widerrufen | `UnmuteCommand` | `vapeecore.moderation.unmute` |
| `/warn <player\|uuid> <reason...>` | Known online/offline warnen | `WarnCommand` | `vapeecore.moderation.warn` |
| `/ban <player\|uuid> <duration\|permanent> <reason...>` | Known online/offline bannen; Login sperren | `BanCommand`, `ModerationLoginListener` | `vapeecore.moderation.ban` |
| `/unban <player\|uuid> [reason...]` | Aktiven Ban widerrufen | `UnbanCommand` | `vapeecore.moderation.unban` |
| `/kick <player\|uuid> <reason...>` | Online-Kick nach Record-Save | `KickCommand` | `vapeecore.moderation.kick` |
| `/history <player\|uuid> [page]` | Read-only Moderationshistorie | `HistoryCommand` | `vapeecore.moderation.history` |

Ränge sind nicht in Java hardcodiert. LuckPerms vergibt Permissions, etwa `vapeecore.utility.build` an eine frei benannte Gruppe; VapeeCore prüft nur die Permission und kennt den Gruppennamen nicht. `/rank` und `/ranks` liegen im VapeeCore-Namespace und mutieren LuckPerms nicht. Die `.others`-Nodes der Self-/Others-Commands besitzen die jeweilige Basispermission als Child; die Basispermission gewährt niemals umgekehrt `.others`. `vapeecore.lobby.build` ist eine deprecated Compatibility-Permission in `plugin.yml`, deren Child die neue Permission gewährt. Die vollständige Matrix und das externe Bukkit-/Vanilla-Lockdown stehen in `docs/PERMISSIONS.md`.

## Command UX Standard

Einfache Commands wie `/spawn`, `/build` oder `/ignorelist` zeigen bei falscher Eingabe nur einen kurzen, kontrollierten Hinweis mit `Invalid usage.` und der exakten Syntax. `/settings visibility add|remove` nennt stattdessen direkt die konkrete Syntax des betroffenen Subcommands. Komplexe Commands mit mehreren Aktionen besitzen eine strukturierte `CommandHelpPage` mit logisch benannten `CommandHelpSection`s und je einer `CommandHelpEntry` pro sichtbarer Syntaxzeile. Die gemeinsamen immutable Modelle und der reine Presentation-Renderer liegen unter `dev.vapee.core.command.help`; Parsing, Permission-Gates und Service-Aufrufe bleiben in der jeweiligen dünnen Command-Klasse.

Der Renderer filtert Einträge ausschließlich über die am Entry hinterlegte Bukkit-Permission und unterdrückt danach leere Sections. Eine Help Page wird als ein mehrzeiliger Adventure-`Component` gesendet, damit der globale Prefix exakt einmal erscheint. Syntax wird immer über `Component.text` erzeugt: Platzhalter wie `<id>`, `<player>` oder `<amount>` bleiben sichtbarer Plain Text und werden nie als MiniMessage-Tags ausgewertet. Klickbare Syntax verwendet ausschließlich `ClickEvent.suggestCommand`; administrative Aktionen dürfen niemals durch einen Help-Klick ausgeführt werden.

Erwartete Benutzerfehler werden vollständig durch den Command behandelt und liefern `true`; `plugin.yml`-Usage bleibt nur ein kurzer technischer Fallback. Ein konkretes Subcommand mit falschen Argumenten zeigt seine genaue Syntax statt der gesamten Help Page. Unbekannte Subcommands benennen den unbekannten Wert sicher und verweisen auf das passende `help`. Success-Ausgaben nennen Aktion, Objekt und – wo hilfreich – das neue Ergebnis; Errors sind konkret, Warnungen beschreiben einen sicheren nächsten Schritt.

Tab Completion ist case-insensitive, stabil sortiert, permission-aware und möglichst klein. Online-Command-Targets stammen nur aus aktuell online befindlichen Spielern, relation-basierte Friend-/Unignore-Targets aus der eigenen Beziehung und bekannten Identity-Domain, dynamische IDs aus dem bereits geladenen Servicezustand und Warp-Materialien nur aus tatsächlich darstellbaren Items. Keine Completion lädt Bukkit-Offline-Player. Frei beeinflussbare IDs, Namen, Display Names und andere dynamische Werte werden mit `Component.text`, `Placeholder.unparsed` oder `Placeholder.component` eingesetzt, niemals per String-Konkatenation in ein MiniMessage-Template.

### Phase 23: Command-Grenzen und Ownership

Die vollständige Vorab-Auditmatrix und die gemessene Verifikation stehen in `docs/COMMAND_AUDIT.md`. `CoreCommand` besitzt Status-/Versionsausgabe, die zentrale Übersicht und die Delegation an den unveränderten Reload-Koordinator. Package-private, funktionale Konstruktor-Seams ermöglichen echte Executor-Tests ohne laufenden Server; sie ersetzen weder einen Domain-Service noch führen sie einen generischen Command-Dispatcher ein. Jede Feature-Command-Klasse bleibt Owner ihrer Permission-, Arity-, Sender- und Target-Prüfungen. `plugin.yml` ist Registrierungs- und Fallbackschicht; die Executor-Prüfung ist zusätzliche Defense in Depth.

`OnlinePlayerResolver` ist ausschließlich für vollständige, case-insensitive, eindeutige aktuell online befindliche Spielernamen zuständig. Utilities, `/msg`, `/ignore` und `/rank` verwenden keine Offline-/Mojang-Auflösung, UUID-Argumente oder Selector. Known Player/UUID bleiben auf bestehende Friend-, Clan-, Profile-, Economy-, Unignore- und Settings-Visibility-Domains begrenzt. `/unignore` sucht nur in der eigenen Ignore-Liste; doppelte bekannte Namen werden abgelehnt und als UUIDs vorgeschlagen. IgnoreList zeigt bekannte Namen sortiert vor unbekannten UUIDs.

Für Fly, Speed, Gamemode, Heal, Feed, Ping, Clear und Enderchest entscheidet die Ziel-UUID über Self/Other: ein expliziter eigener Name benötigt nur die Basispermission. Console verlangt bei Self/Other-Utilities ein explizites Target und die bestehende Others-Permission. Invsee/Enderchest brauchen immer einen Player als GUI-Viewer. `/tp` ist die absichtlich begrenzte Vanilla-Teilmenge aus Phase 18C: die explizite Source-Form verlangt weiterhin Others, auch wenn Source die eigene UUID ist. Koordinaten, lokale/relative Achsen, Worlds, Yaw/Pitch, Activity-Guards, Bypass und Cancellation bleiben unverändert; `/minecraft:tp` bleibt Vanilla. Invsee bleibt ein geschützter Read-only-Snapshot, `.modify` ist weiterhin inaktiv.

`FriendCommand` rendert ohne Ziel für Accept/Deny eingehende Requests, für Cancel ausgehende Requests und für Remove tatsächliche Freunde; leere Zustände werden konkret benannt. `FriendMessages` besitzt die Request-Benachrichtigung inklusive literal Actor-Name und Suggest-Buttons. Ein Argument verwendet nur einen whitespace-freien, eindeutig auf dieselbe UUID auflösbaren bekannten Namen; andernfalls die UUID. Dadurch funktionieren auch alte Beziehungen ohne Identity-Eintrag. Buttons führen niemals sofort eine Aktion aus. Mutationen, Save-Rollback und Visibility-Callbacks bleiben ausschließlich im `FriendService`. `SetSpawnCommand` protokolliert Save-Fehler mit UUID und SEVERE ohne Rethrow oder Success-Ausgabe; `SpawnCommand` unterscheidet nicht konfiguriert, World unavailable und fehlgeschlagen/abgebrochen.

### Checkliste für einen neuen Command

1. Command mit kurzer Description und technischer Fallback-Usage in `plugin.yml` registrieren.
2. Bestehende Permission verwenden oder den benötigten Node ausdrücklich deklarieren; keine Ränge hardcoden.
3. Command-Klasse als dünnen Parser und Input-Adapter halten.
4. Business Logic ausschließlich über den zuständigen Service ausführen.
5. Für erwartete Benutzerfehler eine eigene Meldung senden und niemals `return false` verwenden.
6. Komplexe Commands mit einer permission-aware Help Page ausstatten.
7. Einfache Commands mit einem kurzen, exakten Usage Hint ausstatten.
8. Tab Completion case-insensitive, stabil, klein und permission-aware implementieren.
9. Dynamischen Text mit sicheren Adventure Components oder unparsed Placeholders rendern.
10. Diese Developer-Dokumentation und die „Where do I change this?“-Tabelle aktualisieren.
11. Passende Harness-Prüfungen ergänzen und den Paper-Smoke-Test durchführen.

## Ein Feature hinzufügen

1. Domain und Ownership klar definieren.
2. Ein passendes Feature-Package wählen.
3. Nur bei echtem Lifecycle-Bedarf ein `CoreModule` hinzufügen.
4. Business Logic in einen kleinen Service legen.
5. Listener und Commands als dünne Event-/Input-Einstiege halten.
6. Abhängigkeiten explizit per Konstruktor injizieren.
7. Keine globalen Statics oder Service-Locator einführen.
8. `plugin.yml` nur für neue Commands und Permissions erweitern.
9. Relevante Harness- oder Integrationstests hinzufügen.
10. Diese Developer-Dokumentation aktualisieren.

## Nicht direkt bearbeiten

- `target/` ist generierter Maven-Build-Output, kein Quellcode.
- `dev-server/plugins/VapeeCore/*.yml` sind Runtime-Daten der lokalen Serverinstanz und nicht die Resource-Defaults.
- `dev-server/plugins/*.jar` und andere kompilierte JARs niemals direkt verändern.
- Player-, Blackjack- und Warp-YAMLs nicht während eines schreibenden Servervorgangs manuell überschreiben.

## Build und Tests

Der vollständige Build ist:

```bash
mvn clean package
```

Die ausführbaren Harnesses liegen unter `src/test/java`:

- `dev.vapee.core.activity.ActivityHarness`
- `dev.vapee.core.activity.blackjack.BlackjackHarness`
- `dev.vapee.core.activity.blackjack.BlackjackFairnessHarness`
- `dev.vapee.core.activity.blackjack.table.BlackjackTableHarness`
- `dev.vapee.core.activity.blackjack.BlackjackPresentationHarness`
- `dev.vapee.core.activity.blackjack.presentation.BlackjackPreviewHarness`
- `dev.vapee.core.lobby.warp.WarpHarness`
- `dev.vapee.core.lobby.warp.command.WarpCommandHarness`
- `dev.vapee.core.lobby.experience.navigator.NavigatorMenuHarness`
- `dev.vapee.core.lobby.experience.navigator.NavigatorSecurityHarness`
- `dev.vapee.core.lobby.player.LobbyHarness`
- `dev.vapee.core.utility.command.BuildCommandHarness`
- `dev.vapee.core.utility.UtilityServiceHarness`
- `dev.vapee.core.utility.TeleportParserHarness`
- `dev.vapee.core.utility.command.TeleportCommandHarness`
- `dev.vapee.core.utility.command.UtilityCommandHarness`
- `dev.vapee.core.utility.UtilityInventoryHarness`
- `dev.vapee.core.message.CommandHelpHarness`
- `dev.vapee.core.command.CoreCommandHarness`
- `dev.vapee.core.lobby.command.LobbyCommandHarness`
- `dev.vapee.core.privatemessage.PrivateMessageSocialHarness`
- `dev.vapee.core.seat.SeatHarness`
- `dev.vapee.core.worlddisplay.WorldDisplayHarness`
- `dev.vapee.core.rank.RankServiceHarness`
- `dev.vapee.core.rank.RankCommandHarness`
- `dev.vapee.core.rank.RanksCommandHarness`
- `dev.vapee.core.presentation.PresentationHarness`
- `dev.vapee.core.presentation.PlaytimeFormatterHarness`
- `dev.vapee.core.chat.ChatHarness`
- `dev.vapee.core.config.ConfigServiceHarness`
- `dev.vapee.core.economy.EconomyServiceHarness`
- `dev.vapee.core.economy.command.CoinsCommandHarness`
- `dev.vapee.core.economy.EconomyIntegrationHarness`
- `dev.vapee.core.identity.IdentityHarness`
- `dev.vapee.core.identity.ProfileHarness`
- `dev.vapee.core.moderation.ModerationDomainHarness`
- `dev.vapee.core.moderation.ModerationServiceHarness`
- `dev.vapee.core.moderation.ModerationPersistenceHarness`
- `dev.vapee.core.moderation.ModerationLifecycleHarness`
- `dev.vapee.core.moderation.command.ModerationDurationHarness`
- `dev.vapee.core.moderation.command.ModerationCommandHarness`
- `dev.vapee.core.moderation.ModerationBanEnforcementHarness`
- `dev.vapee.core.moderation.ModerationMuteProjectionHarness`
- `dev.vapee.core.moderation.ModerationMuteEnforcementHarness`
- `dev.vapee.core.friend.FriendPersistenceHarness`
- `dev.vapee.core.friend.FriendServiceHarness`
- `dev.vapee.core.friend.FriendDomainHarness`
- `dev.vapee.core.friend.FriendIntegrationHarness`
- `dev.vapee.core.friend.FriendCommandHarness`
- `dev.vapee.core.friend.FriendLifecycleHarness`
- `dev.vapee.core.friend.gui.FriendMenuHarness`
- `dev.vapee.core.friend.gui.FriendMenuSecurityHarness`
- `dev.vapee.core.presence.PresenceServiceHarness`
- `dev.vapee.core.presence.FriendPresenceNotifierHarness`
- `dev.vapee.core.presence.PresenceLifecycleHarness`
- `dev.vapee.core.reward.RewardServiceHarness`
- `dev.vapee.core.reward.RewardLifecycleHarness`
- `dev.vapee.core.onlinereward.OnlineRewardServiceHarness`
- `dev.vapee.core.onlinereward.OnlineRewardLifecycleHarness`
- `dev.vapee.core.player.repository.PlayerRewardPersistenceHarness`
- `dev.vapee.core.player.repository.PlayerVisibilityPersistenceHarness`
- `dev.vapee.core.player.settings.PlayerVisibilitySettingsHarness`
- `dev.vapee.core.player.settings.PlayerSettingsServiceHarness`
- `dev.vapee.core.visibility.VisibilityPolicyHarness`
- `dev.vapee.core.visibility.VisibilityServiceHarness`
- `dev.vapee.core.visibility.VisibilityModuleHarness`
- `dev.vapee.core.visibility.FriendVisibilityRefreshHarness`
- `dev.vapee.core.visibility.IgnoreVisibilityRefreshHarness`
- `dev.vapee.core.settings.SettingsMenuHarness`
- `dev.vapee.core.settings.SettingsMenuSecurityHarness`
- `dev.vapee.core.settings.visibility.SettingsNavigationHarness`
- `dev.vapee.core.settings.visibility.SettingsModuleLifecycleHarness`
- `dev.vapee.core.settings.command.SettingsCommandHarness`
- `dev.vapee.core.settings.visibility.VisibilitySettingsMenuHarness`
- `dev.vapee.core.settings.visibility.VisiblePlayersMenuHarness`
- `dev.vapee.core.settings.visibility.VisibilityMenuSecurityHarness`
- `dev.vapee.core.quest.QuestDefinitionHarness`
- `dev.vapee.core.quest.QuestServiceHarness`
- `dev.vapee.core.player.repository.PlayerQuestPersistenceHarness`
- `dev.vapee.core.quest.QuestLifecycleHarness`

Nach relevanten Änderungen folgen ein Paper-1.21.11-Smoke-Test mit Java 21 und LuckPerms 5.5.x, allen 27 Modulen, `/core`, `/core reload`, `/profile`, `/friend`, `/clan`, Command-Registrierung und sauberem Shutdown. Ein „Live Client Test“ darf nur dokumentiert werden, wenn wirklich ein Minecraft-Client verbunden war und die Schritte ausgeführt wurden; Serverstart oder Harness allein zählen nicht als Live-Client-Test.

## Documentation maintenance rule

Diese Datei ist Teil der Codebase. Wenn eine zukünftige Änderung eine Klasse verschiebt, einen Config-Key ändert, einen Command oder ein Modul hinzufügt, Ownership verschiebt, Permissions verändert oder Join-/Player-State-Flows anpasst, muss `docs/DEVELOPER_GUIDE.md` im selben Arbeitsschritt aktualisiert werden. Das README bleibt die Projektübersicht; die praktische Detaildokumentation bleibt hier.

### Phase 26B – Administrative online targets / permissions

VapeeCore injiziert RankModule ausdrücklich in UtilityModule und EconomyModule. Rank besitzt unverändert den einzigen StaffHierarchyService; es hängt nicht von Utility oder Economy ab. Beide Module erstellen einen kleinen Bukkit-Application-Guard `command.OnlineStaffTargetGuard`, ohne zweiten Hierarchie-Algorithmus, Group-/Level-Cache, Scheduler oder neuen ReloadParticipant. Enable-Reihenfolge, 26 Module und sechs Reload-Teilnehmer bleiben unverändert; Executor/Completer-Hooks werden wie bisher entfernt.

Command-Capability und Syntax bleiben Pflicht. Nach bestehender exakter Online-Zielauflösung und UUID-Self-Klassifikation folgt die geladene Entscheidung vor Activity-/BUILD-Guards, Flight-Cleanup, Player-/Wallet-Mutation, Snapshot-Erstellung oder GUI-Open. `check` erhält StaffTargetDecision einschließlich UNAVAILABLE; `authorize` unterscheidet kontrolliertes Denial und unavailable Feedback. Fehlende geladene Gruppen oder unerwartete RuntimeException: fail closed, ein contextual WARNING mit Actor-UUID, Target-UUID und Action; gewöhnliche Hierarchie-Denials erzeugen keinen Logspam. Completion prüft dieselben loaded-only Regeln still und lädt keine LP-User.

Self (implizit oder ausdrücklich gleiche UUID) bleibt ohne LP-Abfrage erlaubt, sofern vorhandene Permissions und State-Guards erfüllt sind. Console erhält Hierarchie-Autorität nur in bereits unterstützten expliziten Target-Formen; tphere, invsee und enderchest bleiben Player-only. Andere Sender sind kein Console-Ersatz. OP und teleport.bypass umgehen niemals Hierarchie.

Geschützt sind fly, speed, gamemode, heal, feed, clear, invsee (sensitives Lesen), enderchest (reales mutierbares Inventar), tphere und tp mit anderer bewegter SOURCE. Bei tp ist DESTINATION keine administrative Mutation: Self zu höherem Staff oder fremde erlaubte Source zu höherem Staff bleiben möglich. Player→Player, Koordinaten, relative/lokale Rotation und explizite Weltformen behalten Parser und Permissions. Auch benannte eigene SOURCE in Others-Grammatik benötigt weiterhin das Others-Recht, ist aber hierarchie-exempt. Destination-Activity und externes false/Cancel bleiben verbindlich.

TP-Completion: erster Player-Token ist die bestehende Self-DESTINATION-Grammatik und wird nicht als Staff-Ziel gefiltert. Erster Console-Token ist SOURCE und wird entsprechend autorisiert. Nach eingegebener SOURCE bleiben DESTINATION, Koordinaten und Weltvorschläge von der Staff-Hierarchie ungefiltert; die tatsächliche Ausführung prüft die bewegte SOURCE verbindlich. Kein Parser- oder UX-Grammatik-Umbau.

Coins add/remove/set prüfen nach Known-Identity und echter Online-Präsenz die Hierarchie, vor Wallet-Zugriff, Save, INFO-Audit und Notice. Self-/Console-Capability bleibt erhalten. Mutation-Completion filtert online, GET bleibt capability-only, einschließlich bekannter Offline-Reads ohne LP-Load. EconomyService, RewardService, UtilityService und InvseeService bleiben frei von Staff-Policy.

Ping bleibt absichtlich ein nicht-sensitives, permission-geschütztes Latenz-Read. Build bleibt Self-only. Moderation einschließlich Schema, Mute-Projektion und Chat/PM-Enforcement ist unverändert. Historische Berichte und FORMATTING bleiben unverändert. plugin.yml, config.yml und StaffHierarchyService wurden nicht geändert.

Kanonischer Audit: `docs/PERMISSIONS.md` (aktuell 37 Roots, 50 Nodes, 15 Child-Kanten und konservative Parent-Empfehlung). Vollständige Verification/Datei-Inventare: `docs/PERMISSION_HARDENING.md`. Phase 27 Notifications & Presence bleibt außerhalb dieser Änderung: keine AFK-/Presence-/Friend-Alert-/Join-Quit-Neugestaltung.
