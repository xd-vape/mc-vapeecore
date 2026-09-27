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
| `/tp` | `dev.vapee.core.utility.command.TeleportCommand` |
| `/tphere` | `dev.vapee.core.utility.command.TeleportHereCommand` |
| `/heal` | `dev.vapee.core.utility.command.HealCommand` |
| `/feed` | `dev.vapee.core.utility.command.FeedCommand` |
| Utility-Modul-Lifecycle und Command-Registrierung | `dev.vapee.core.utility.UtilityModule` |
| Lobby hotbar items | `dev.vapee.core.lobby.item.LobbyItemService` |
| Warp Navigator item material/name/lore | `LobbyItemService` |
| Warp Navigator GUI | `NavigatorMenu` und `NavigatorListener` |
| Player visibility | `LobbyVisibilityService` |
| Settings menu | `SettingsMenu` und `SettingsListener` |
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
| Freundschaftsanfragen erlauben/sperren | `PlayerSettingsService`, `FilePlayerRepository`, `SettingsMenu` (Slot 17) |
| Friends-Limits | `ConfigService`, `config.yml` → `friends.limits` |
| Gameplay-Coin-Rewards und gebündelte Persistence | `dev.vapee.core.reward` |
| Kumulative Online-/Playtime-Rewards | `dev.vapee.core.onlinereward` |
| Quest Progress Engine | `dev.vapee.core.quest.QuestService` |
| Quest Definition Domain | `QuestDefinition` und `QuestDefinitionRegistry` |
| Technische Quest Progress Keys | `QuestProgressKey` |
| Player Quest Persistence | `PlayerQuestState`, `PlayerQuestProgress` und `FilePlayerRepository` |
| Daily-Quest-Auswahl und Rotation | `dev.vapee.core.quest.daily` |
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
| `config.yml` | `ConfigService` | Servername, globaler Message-Prefix, Debug, `ranks.track`, Online-Reward-Regeln und -Nachricht, `friends.limits` | Ja | Durch Reload | `src/main/resources/config.yml` |
| `lobby.yml` | `LobbyConfig` / `LobbyModule` | Spawn, Teleport, Protection, `player.gamemode`, Join/Quit-Texte | Ja | Spawn durch `/setspawn`, übrige Werte durch Reload | `src/main/resources/lobby.yml` |
| `chat.yml` | `ChatConfig` / `ChatModule` | Globaler Chat und LuckPerms-Metaformat | Ja | Durch Reload | `src/main/resources/chat.yml` |
| `private-messages.yml` | `PrivateMessageConfig` / `PrivateMessageModule` | Aktivierung und PM-Formate | Ja | Durch Reload | `src/main/resources/private-messages.yml` |
| `presentation.yml` | `PresentationConfig` / `PresentationModule` | Sidebar, Tablist, Updateintervall | Ja | Durch Reload | `src/main/resources/presentation.yml` |
| `daily-quests.yml` | `DailyQuestConfig` / `DailyQuestModule` | Daily-Slots, Reset, Zeitzone und Quest-Katalog | Ja | Durch Reload; Player-Rotation erst beim nächsten Sync | `src/main/resources/daily-quests.yml` |
| `blackjack.yml` | `BlackjackTableConfig` / `BlackjackModule` | Physische Blackjack-Table-Drafts | Nein | Ja, atomar über `/blackjack setup …` | `src/main/resources/blackjack.yml` |
| `warps.yml` | `WarpConfig` / `WarpModule` | Dynamische Warps | Nein | Ja, atomar über `/warp …` | `src/main/resources/warps.yml` |
| `friends.yml` | `FriendModule` / `FileFriendRepository` | UUID-basierte Freundschaften und gerichtete Anfragen, `schema-version: 1` | Nein | Ja, atomar über `FriendService` | Kein Resource-Default; bei fehlender Datei leer, beim ersten Save erzeugt |

Die sechs Reload-Teilnehmer sind exakt `config.yml`, `lobby.yml`, `chat.yml`, `private-messages.yml`, `presentation.yml` und `daily-quests.yml`. `ranks.track` und `online-rewards` gehören zum vorhandenen `ConfigService`; Rank, Reward, OnlineReward und die generische Quest Foundation sind selbst keine Reload-Teilnehmer. Reward und Quest besitzen keine eigene Config- oder Datendatei; DailyQuest besitzt die Katalog-Datei. Der Reload wird zweiphasig vorbereitet und angewendet. `DailyQuestModule` tauscht beim Apply Config und Registry aus und stellt beim Rollback beide vorherigen Snapshots wieder her; Player-Assignments werden erst im nächsten normalen Sync berührt. `OnlineRewardService` liest die immutable Config-View bei jedem Processing neu. Bei Rollback setzt `LobbyModule` neben Config und Spawn auch die Gamemodes aller normalen Lobby-Spieler auf den vorherigen Wert zurück. BUILD-Spieler bleiben bis zum Build-Ende in `CREATIVE`.

## Module map und Reihenfolge

Die registrierte Reihenfolge ist eine Dependency-Reihenfolge und muss bei neuen Modulen bewusst gepflegt werden:

1. **Permission** – lesender LuckPerms-Zugriff.
2. **Rank** – cachefreie Rank-Domain, öffentlicher Track und `/rank`-/`/ranks`-Commands.
3. **Player** – `CorePlayer`, Cache, YAML-Persistence, Settings und Player-Join-/Quit-Lifecycle.
4. **Social** – Ignore-State und Commands.
5. **Economy** – Coin-Wallet und `/coins`.
6. **Identity** – Known-Player-Lookup, immutable Profile und `/profile`.
7. **Friend** – zentrale UUID-basierte Freundschaften und Anfragen, `/friend`.
8. **Reward** – zentrale Gameplay-Reward-API und gebündelte Player-Persistence.
9. **OnlineReward** – kumulative Minecraft-Spielzeit-Rewards und Player-Fortschritt.
10. **Quest** – generische Definitionen, Assignments, Fortschritt, Completion und gebündelte Player-Persistence.
11. **DailyQuest** – Daily-Katalog, Cycle-Berechnung, Auswahl, Assignment und Rotation.
12. **Lobby** – Config, Spawn, Protection, Player-State, Items, Messages, `/spawn`, `/setspawn`.
13. **Chat** – globaler Chat und lesende Rank-Placeholder.
14. **PrivateMessage** – `/msg`, `/reply` und Session-Konversationen.
15. **Presentation** – Sidebar, Tablist und lesende Rank-Placeholder.
16. **Settings** – Settings-Inventar und `/settings`.
17. **Activity** – generische Runtime-Typen, Venues, Sessions und Memberships.
18. **Utility** – `/build`, grundlegende Player-Utilities und transienter Movement-Cleanup.
19. **Seat** – generische CASUAL-/MANAGED-Sitze, Seat-Entities und Event-Cleanup.
20. **WorldDisplay** – native keyed TextDisplay-/ItemDisplay-Lifecycles.
21. **Blackjack** – physische Tische, Seat-Allocation, Activity-Hotbar und native Weltanzeigen.
22. **Warp** – dynamische Warp-Persistence und Admin-Command.
23. **LobbyExperience** – Visibility, Item-Interaktionen und Navigator-UI.

Shutdown läuft exakt rückwärts: LobbyExperience → Warp → Blackjack → WorldDisplay → Seat → Utility → Activity → Settings → Presentation → PrivateMessage → Chat → Lobby → DailyQuest → Quest → OnlineReward → Reward → Friend → Identity → Economy → Social → Player → Rank → Permission. DailyQuest stoppt zuerst seinen Sync-Task; Quest stoppt dann seinen Flush-Task und speichert dirty Quest-State einschließlich Cycle-ID, während Reward und Player noch verfügbar sind. OnlineReward stoppt danach seinen Processing-Task; Reward flusht anschließend dirty Coins und jeweils den gesamten aktuellen `CorePlayer`, solange Economy und Player noch verfügbar sind. Friend und Identity deregistrieren ihre Commands; Rank besitzt keinen persistenten Player-State. Blackjack gibt seine MANAGED-Sitze frei, bevor Seat den globalen Rest bereinigt.

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

Player + Reward
  ↑
Quest

Player + Quest
  ↑
DailyQuest

Lobby + Activity
  ↑
Utility

Lobby + Activity
  ↑
Seat

Seat + Activity + WorldDisplay + Lobby
  ↑
Blackjack

Lobby + Player + Settings + Warp
  ↑
LobbyExperience
```

`RankModule` hängt ausschließlich von Plugin, Config, Permission und Message ab. Es hängt insbesondere nicht von Player, Economy, Lobby, Chat, Presentation, Activity oder Blackjack ab. `RewardModule` hängt nur von Plugin, Player und Economy ab. `OnlineRewardModule` konsumiert Config, Player, Reward und Message, aber nie Economy direkt. `QuestModule` konsumiert ausschließlich Plugin, Player und Reward; es kennt Economy, OnlineReward, Activity, Blackjack, Mine, Rank, Permission und LuckPerms nicht. `DailyQuestModule` konsumiert ausschließlich Plugin, Player und Quest, insbesondere weder Reward noch Economy direkt. Features hängen in Richtung `Gameplay-Producer → Quest → Reward → Economy → Player`, nie umgekehrt; DailyQuest verwaltet nur den Katalog und die Assignments. `Presentation` bezieht Rank, Permission, Player, Economy und Lobby. Chat bezieht Rank, Permission und Social; PrivateMessage bezieht Player und Social. `MessageService` sowie bei Bedarf `ConfigService` werden explizit injiziert. Utility besitzt keine Blackjack-Abhängigkeit. SeatService kennt weder Lobby noch Activity noch Blackjack; nur SeatListener erhält die Lobby-/Activity-Policy. WorldDisplay hängt nur vom Plugin ab.

## Player Identity & Profile Foundation (Phase 17C)

`IdentityModule` hängt nur von `JavaPlugin`, `PlayerModule`, `RankModule`, `EconomyModule` und `MessageService` ab. Es steht unmittelbar nach Economy und vor Friend, besitzt weder Listener noch Task noch eigene Config/Persistence und ist keiner der sechs Reload-Teilnehmer. Beim Shutdown werden Command-Executor und Tab-Completer entfernt und Service-Referenzen freigegeben. Friends greift nun auf `PlayerIdentityService` zu; spätere Clans sollen ebenfalls keinen eigenen Name-Resolver oder Player-YAML-Scan bauen.

`PlayerIdentity(UUID, name, firstJoin, lastJoin)` ist ein validierter immutable Snapshot ohne `CorePlayer`- oder Bukkit-Referenz. `PlayerIdentityService#findById(UUID)`, `findByName(String)` und `resolve(String)` geben bekannte Spieler als Snapshot zurück; Namensauflösung unterscheidet `FOUND`, `NOT_FOUND` und `AMBIGUOUS`. UUID-Eingaben müssen bereits in VapeeCore bekannt sein. `PlayerProfile` ergänzt `online`, `coins`, optionalen `RankInfo` und optionale Playtime-Ticks. `PlayerProfileService#getProfile(UUID)` und `resolveProfile(String)` kombinieren ausschließlich bestehende Quellen, ohne Player zu laden, Coins oder Settings zu mutieren oder LuckPerms-User nachzuladen.

Der Runtime-Name-Index gehört `FilePlayerRepository`: Beim Start werden kanonisch benannte `<uuid>.yml`-Dateien einmalig auf Name und Join-Zeitpunkte geprüft; der Index speichert normalisierten Namen (`Locale.ROOT`) → UUID-Set und hält keine `CorePlayer` dauerhaft im Speicher. Ungültige Metadaten werden protokolliert und übersprungen. Erst nach erfolgreichem atomarem Datei-Replace aktualisiert `save` den Index und entfernt den alten Namen. `PlayerService#findKnownIdsByName` bevorzugt aktuelle geladene Namen und filtert veraltete Disk-Namen geladener Spieler. Kollisionen werden nicht willkürlich entschieden; `/profile <uuid>` bleibt die eindeutige Alternative. Ein Lookup scannt nicht erneut das Verzeichnis. Der Index ist keine zusätzliche Datenbank oder Datei; das Player-YAML-Schema bleibt unverändert. Bukkit-`OfflinePlayer`, Mojang-Requests und externe UUID-Suche werden nicht benutzt.

`EconomyService#getKnownCoins(UUID)` liest beim geladenen Player den aktuellen Wallet-Stand, bei bekanntem Offline-Player dessen persistiertes Wallet und liefert bei unbekannter UUID `OptionalLong.empty()`; `getCoins` und alle Mutationen behalten ihre Loaded-Player-Semantik. Der Online-Status stammt vom tatsächlichen Paper-Player (`isOnline`), nicht vom Cache. Nur online werden `RankService#getPrimaryRank` (LuckPerms-Cache, `RankInfo.displayComponent()` für die Farbe) und `Statistic.PLAY_ONE_MINUTE` gelesen. Offline sind Rank und Playtime bewusst leer; weder Rank noch Reward-Progress werden als Profile-Snapshot persistiert. Der gemeinsame `dev.vapee.core.format.PlaytimeFormatter` hält das bisherige Format; `presentation.PlaytimeFormatter` delegiert aus Kompatibilitätsgründen dorthin.

`/profile` zeigt dem Spieler das eigene Profil; die Console muss `/profile <player|uuid>` angeben. Mit einem Ziel funktionieren Online-Name, bekannter Offline-Name und bekannte UUID. Unbekannte oder mehrdeutige Namen erhalten kontrollierte Meldungen, Lesefehler werden geloggt und nicht als Stacktrace in Chat ausgegeben. `vapeecore.profile.view` ist standardmäßig für alle erlaubt. Tab Completion zeigt nur Online-Namen, keine Liste aller bekannten Offline-Spieler. Spielernamen werden ausschließlich als `Component.text` ausgegeben, nie als MiniMessage ausgewertet. First/Last Join werden als `yyyy-MM-dd HH:mm` in der Server-Zeitzone gezeigt; Last Join ist kein Last Seen. Kein GUI und kein neues Hauptmenü.

## Friends Foundation und Integration (Phase 18A.1/18A.2)

`FriendModule` startet direkt nach Identity und vor Reward. Es injiziert Plugin, `ConfigService`, Player, Social, Identity, `MessageService` und den gemeinsamen Help-Renderer. Es besitzt `FriendService`, das zentrale `FileFriendRepository` für `plugins/VapeeCore/friends.yml` und den einen `/friend`-Command mit Alias `/friends`. Beim Disable werden Executor und Tab-Completer entfernt. Es registriert keinen Listener, Task oder weiteren Reload-Teilnehmer und hat keine Economy-, Rank-, Quest- oder Presentation-Abhängigkeit.

`friends.yml` ist die einzige Friends-Datendatei und enthält `schema-version: 1`, ungerichtete Freundschaften und gerichtete Anfragen mit UUIDs und Zeitpunkten. Eine fehlende Datei bedeutet einen leeren State; das erste erfolgreiche Speichern erzeugt sie. Der Repository-Start validiert den vollständigen Snapshot; beschädigte oder nicht unterstützte Daten lassen das Modul kontrolliert scheitern statt sie zu überschreiben. Mutationen schreiben zuerst einen neuen, validierten Snapshot per temporärer Datei und atomarem Move, mit Replace-Fallback falls das Dateisystem keinen atomaren Move unterstützt. Erst nach erfolgreichem Save tauscht `FriendService` den In-Memory-State aus; ein Fehler behält den vorherigen Zustand. Domain und Service speichern keine Bukkit-`Player`-Referenzen. Lese-APIs für Beziehung, Freunde, eingehende und ausgehende Anfragen bleiben unveränderliche Snapshots.

`FriendService#sendRequest` prüft Self, bestehende Freundschaft/Anfrage, Policy und Limits. Eine Gegenanfrage wird atomar als Freundschaft angenommen. `acceptRequest` nimmt nur eine vorhandene eingehende Anfrage an; `denyRequest`, `cancelRequest` und `removeFriend` entfernen nur ihre jeweilige gerichtete Anfrage oder Freundschaft. `FriendLimits` stammen als immutable View aus `ConfigService`: `friends.limits.max-friends: 100`, `max-incoming-requests: 25`, `max-outgoing-requests: 25`. Fehlende oder ungültige nichtpositive Werte warnen und verwenden Defaults. Der Service liest die aktuellen Limits bei jeder relevanten Operation, daher wirken erfolgreiche `/core reload`-Änderungen ohne Friend-Reload. Bereits bestehende Beziehungen/Anfragen werden bei einer Senkung nicht gelöscht; neue Aktionen beachten die neuen Grenzen. Ein fehlgeschlagener Reload stellt den vorherigen Config-Snapshot wieder her.

Die Policy prüft `SocialService#isKnownIgnoring` in beiden Richtungen, dann `PlayerSettingsService#areKnownFriendRequestsEnabled` beim Empfänger. Beide Abfragen verwenden für geladene Spieler den aktuellen State und für bekannte Offline-Spieler die persistierten Player-Daten lesend, ohne sie in den Player-Cache zu laden. Unbekannte Empfänger werden abgelehnt. Ignore blockiert neue und anzunehmende Anfragen; deaktivierte `settings.friend-requests` verhindern nur neue Anfragen, nicht das Annehmen bestehender. Der Key ist standardmäßig `true`, wird beim Player-Save unter `settings.friend-requests` gespeichert und im bestehenden 27-Slot-`SettingsMenu` über Slot 17 umgeschaltet. Fehlende/alte Player-Keys laden als `true`, ein Save-Fehler rollt den Settings-Wert zurück.

`/friend help|add|accept|deny|cancel|remove|list|requests` ist ausschließlich für Spieler mit `vapeecore.friend.use` (Default `true`). `FriendCommand` nutzt `PlayerIdentityService#resolve` für bekannte Namen/UUIDs, weist unbekannte und mehrdeutige Namen kontrolliert ab und zeigt Namen als Adventure-Text mit UUID-Fallback an. Erfolgreiche neue Anfragen und Annahmen benachrichtigen den Gegenpart nur, wenn er gerade online ist; es gibt keine Offline-Mail. Tab Completion beschränkt sich auf sinnvolle Subcommands und bekannte beziehungsweise online sichtbare Ziele. Keine Friends-GUI, kein Teleport, kein Presence-Service und keine zusätzlichen Player-YAML-Friends-Listen.

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

Das Quest-System besitzt ausschließlich die fachliche Frage, ob ein geladener Spieler eine zugewiesene Quest erfüllt hat. Gameplay-Features werden später kleine Producer und rufen `QuestService#addProgress(UUID, QuestProgressKey, long)` auf. Der Service kennt diese Quellen nicht und importiert insbesondere weder Mine, Blackjack, Activity noch OnlineReward. Bei Completion delegiert er den Coin-Grant an `RewardService`; Economy wird niemals direkt verwendet. `QuestModule` hängt deshalb nur von `JavaPlugin`, `PlayerModule` und `RewardModule` ab. Es ist kein `ReloadParticipant` und besitzt keine Config, Commands, Permissions, Messages oder Presentation.

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

Quest-Mutationen sind Main-Thread-owned und sofort im `CorePlayer` sichtbar. `QuestService` hält nur dirty UUIDs und ruft nicht pro Signal `savePlayer` auf. `QuestModule` besitzt genau einen gemeinsamen synchronen 100-Tick-Flush, also ungefähr fünf Sekunden. `flushPlayer` speichert nur geladene dirty Player über `PlayerService#savePlayer`; Erfolg entfernt den Marker, ein Save-Fehler bleibt isoliert und wird im nächsten Batch erneut versucht. `flushAll` arbeitet über einen UUID-Snapshot, damit ein Fehler andere Player nicht blockiert. `QuestListener` läuft bei Quit mit `LOWEST` vor dem normalen Player-Unload. Beim Disable wird zuerst der Task gestoppt, dann geflusht, der Listener abgemeldet und das Dirty Tracking geleert.

RewardService und QuestService speichern jeweils den gesamten aktuellen `CorePlayer`, niemals getrennte Balance- oder Quest-Snapshots. Ein Reward-Flush kann daher aktuellen Quest-State mitpersistieren und ein späterer Quest-Flush redundant sein, aber keiner kann einen alten Teilzustand zurückschreiben. Bei einem harten JVM-/OS-Abbruch können die letzten ungefähr fünf Sekunden Quest-Progress verloren gehen; Coins und Completion-State im selben noch nicht gespeicherten In-Memory-Profil teilen dieses Batching-Fenster. Normales Quit, Plugin-Disable und Server-Shutdown flushen kontrolliert.

Phase 17B lädt Definitionen und rotiert Daily-Assignments über die bestehende Quest Foundation. Konkrete Default-Quests und Producer für `playtime:minute`, `mine:block:any`, `blackjack:win`, `activity:complete` oder `location:visit:mine` sind noch nicht implementiert. `/quests`, GUI, Claim-Button, Completion-Feedback, Mine und Blackjack-Hooks folgen später.

## Daily Quest Cycle & Configuration

`DailyQuestModule` hängt nur von `JavaPlugin`, `PlayerModule` und `QuestModule` ab. Es ist Owner von `plugins/VapeeCore/daily-quests.yml`, `DailyQuestConfig`, `DailyQuestService`, einem Join-Listener und genau einem gemeinsamen synchronen 1200-Tick-Sync-Task. Es besitzt weder eigenen Save-Task noch Quit-Listener, Command, Permission, GUI oder Player-Nachricht. Die generische Quest Foundation bleibt Owner von Definition-Registry, Progress, Completion, Reward-Retry und dem 100-Tick-Dirty-Flush.

Die Resource liefert `enabled: false`, `quests-per-day: 4`, `reset.time: "00:00"`, `reset.timezone: "system"` und `quests: {}`. Das ist bewusst noch kein sichtbares Feature: Ohne UI und Production-Progress-Producer würden aktive Standardquests den Spielern nichts Nützliches bieten. Administratoren können Definitionen etwa so ergänzen; die Keys sind erst nach einem späteren Producer tatsächlich fortschreitbar:

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

Ownership-Kurzform: Daily-Anzahl, Reset und Definitionen → `daily-quests.yml`; Cycle-Rechnung → `DailyQuestCycleResolver`; Auswahl → `DailyQuestSelector`; Assignment/Rotation → `DailyQuestService`; Fortschritt, Completion und Coin-Reward → `QuestService` und `RewardService`. Die sichtbare Quest-UX und erste Producer folgen später. `docs/FORMATTING.md` bleibt unverändert, da Phase 17B keine Player-facing Templates einführt.

## Ranks & Server Identity

LuckPerms besitzt Gruppen, Mitgliedschaften, Primary Group, Display Name, Prefix, Suffix, Weight, Tracks und Permissions vollständig. VapeeCore liest und präsentiert diese Informationen ausschließlich; es existieren weder `Player.rank`, eigene Rank-Mitgliedschaften, eine Rank-Datenbank noch Rank-Zuweisungen in `plugins/VapeeCore/players/`. Administratoren ändern Ränge weiterhin mit LuckPerms. `/rank` und `/ranks` sind reine Lese-Commands im VapeeCore-Namespace und besitzen bewusst keine Alias-Flut.

`PermissionModule` stellt die einzige LuckPerms-Provider-Verbindung her. `LuckPermsService` kapselt geladene User, Group Information und Track Groups, ohne LuckPerms-API-Typen in andere Features zu leaken. Eine Group-Auflösung liest Display Name, Description, rohe Color Meta und Weight gemeinsam. `RankService` bildet diese Werte auf das immutable `RankInfo(id, displayName, description, color, weight)` ab. Normale Online-Abfragen verwenden ausschließlich `UserManager#getUser`; es gibt kein `loadUser`, kein `join()` und keinen dauerhaften Rank-Cache. Ist der User nicht geladen, liefert `getPrimaryRank` `Optional.empty()`. Fehlt die Group unerwartet, bleibt die bekannte ID erhalten und Display Name, Description, Color und Weight fallen kontrolliert zurück.

Der öffentliche Track steht unter `config.yml` → `ranks.track`; Default und Fallback sind `ranks`. Der Wert muss ein non-blank String sein, sonst warnt `ConfigService`, verwendet `ranks` und verändert die Datei nicht. Nur die in diesem LuckPerms-Track enthaltenen Gruppen erscheinen in `/ranks`; interne Gruppen bleiben unsichtbar. `Track#getGroups()` bestimmt die Reihenfolge, nicht Alphabet oder Weight. Da `RankService` den aktuellen Configwert bei jeder Listenabfrage liest, wirkt ein geänderter Track nach `/core reload` sofort, ohne sechsten Reload-Teilnehmer.

Jeder öffentliche Rank sollte in LuckPerms einen Group Display Name besitzen. Fehlt er, erzeugt VapeeCore ohne hartcodiertes Mapping einen neutralen Namen aus der Group-ID, zum Beispiel `senior_builder` → `Senior Builder`. Die optionale Beschreibung kommt aus `vapeecore.rank.description`; die Rank-Farbe kommt aus `vapeecore.rank.color`; Weight bleibt reine Metadateninformation. `RankService` akzeptiert Adventure Named Colors wie `gray`, `gold`, `aqua`, `green`, `red`, `dark_red` und `light_purple` sowie sechsstellige Hexwerte wie `#55ffaa`. Fehlende oder ungültige Werte bleiben `Optional.empty()` und werden bei der Ausgabe neutral weiß dargestellt. Prefix und Suffix bleiben unabhängig davon kompatibel und sind für Rank-Farbe nicht erforderlich.

Neue Ränge benötigen keine VapeeCore-Codeänderung. Der empfohlene LuckPerms-Ablauf ist: Gruppe erstellen, Group Display Name setzen, `vapeecore.rank.color` setzen, optional `vapeecore.rank.description` setzen und die Gruppe an den konfigurierten `ranks`-Track anhängen. Weder Gruppen-IDs noch deren Farben werden in Production Code abgebildet.

Feature-Zugriff basiert ausschließlich auf Permissions, nie auf Rank-Namen. Builder-Funktionen prüfen `vapeecore.utility.build`. Spätere Mine-Zugriffe verwenden `vapeecore.mine.<mine>`; spätere Reward-Multiplikatoren könnten über Permissions oder optionales LuckPerms-Meta modelliert werden. Phase 16C implementiert weder Mine noch `coin-multiplier`; Online Rewards sind für alle Ränge identisch und besitzen keine LuckPerms-Abhängigkeit.

Presentation und Chat unterstützen `<rank>` als farbiges Component aus dem freundlichen Primary-Rank-Namen, `<rank_name>` als normalen Player Display Name in der Primary-Rank-Farbe sowie `<rank_id>` als rohe Primary Group. `<name>` bleibt der unveränderte Player Display Name; `<group>` bleibt als Compatibility-Alias identisch zu `<rank_id>`; `<prefix>` und `<suffix>` bleiben die effektiven LuckPerms-Metawerte. Der Chat-Renderer wird pro `AsyncChatEvent` neu erzeugt, damit Papers viewer-unaware Cache nicht die erste Nachricht für Folge-Events wiederverwendet. Der Async-Pfad liest nur bereits geladene LuckPerms-Cached-Data, verwendet keine Player-Statistik und setzt Playertext weiterhin als sichere Adventure Component ein.

Presentation besitzt vollständig `<server>`, `<name>`, `<rank_name>`, `<prefix>`, `<suffix>`, `<rank>`, `<rank_id>`, `<group>`, `<playtime>`, `<coins>`, `<online>` und `<max_players>`; dieselben Placeholder gelten für Scoreboard, `tablist.name-format`, Header und Footer. `<playtime>` stammt ausschließlich aus `Player#getStatistic(Statistic.PLAY_ONE_MINUTE)`. Der historische Statistikname ist irreführend: Der Wert sind Ticks, also 20 Ticks pro Sekunde. `PlaytimeFormatter` rechnet mit `long`, klemmt negative Werte auf null und zeigt unter einer Stunde Minuten, unter einem Tag Stunden und Minuten sowie ab einem Tag Tage und Stunden. Es existieren weder eigene Persistence noch zusätzliche Scheduler- oder Polling-Tasks.

Das Default-Scoreboard zeigt Coins, den freundlichen Rank mit `<rank>` in seiner Rank-Farbe und Playtime im kompakten Label/Wert-Layout. Chat und Tablist verwenden standardmäßig `<rank_name>` für den rankfarbigen Spielernamen. `<name>` bleibt der normale, nicht automatisch rankgefärbte Display Name; `<rank_id>` ist die technische Primary Group und `<group>` deren Compatibility-Alias. Prefix und Suffix bleiben für eigene Formate verfügbar.

Neue Resource-Defaults überschreiben weder `plugins/VapeeCore/chat.yml` noch `plugins/VapeeCore/presentation.yml`. Ein vorhandenes `<group>` funktioniert weiterhin. Für Rank-Farben auf bestehenden Servern müssen Administratoren `chat.yml` auf `format: "<rank_name><dark_gray> » </dark_gray><white><message></white>"` und `presentation.yml` unter `tablist` auf `name-format: "<rank_name>"` umstellen; optionale Prefix-/Suffix-Varianten sind möglich. Danach aktiviert `/core reload` die Änderung.

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
| Technische Quest Progress Keys | `QuestProgressKey` und später die Konstanten des produzierenden Features |
| Player Quest Persistence | `PlayerQuestState`, `PlayerQuestProgress` und `FilePlayerRepository` |
| Daily-Quest-Anzahl, Reset, Zeitzone und Definitionen | `daily-quests.yml` |
| Daily-Cycle-Berechnung | `DailyQuestCycleResolver` |
| Deterministische Daily-Auswahl | `DailyQuestSelector` |
| Daily-Assignment und Rotation | `DailyQuestService` |

## Utility ownership und Lifecycle

`UtilityModule` bleibt genau ein `CoreModule`. Beim Enable bezieht es Lobby- und Activity-Services, erstellt `UtilityService`, `UtilityListener` und den read-only `InvseeService`, registriert zwölf Commands und anschließend beide Listener. Die Startup-Zahl wird aus der tatsächlich registrierten Command-Liste abgeleitet. Utility ist kein `ReloadParticipant` und besitzt keine Configdatei; Module Count und Reload Count betragen 23 beziehungsweise 6. Beim Disable werden offene Invsee-Snapshots geschlossen, deren UUID-Metadaten geleert, verwaltetes Flight und beide Speed-Kanäle online normalisiert, Listener abgemeldet und Executor sowie TabCompleter entfernt. Der Gamemode wird dabei bewusst nicht zurückgesetzt.

`UtilityService` ist der einzige Owner der eigentlichen Bukkit-Mutationen für Flight, Speed, Gamemode, Utility-Teleports, Heal, Feed, sicheres Inventory-Clear und das Öffnen echter Enderchests. Alle Mutationen sind Main-Thread-only. Der Service hält niemals dauerhafte `Player`-Referenzen, sondern ausschließlich UUID-Mengen für command-managed Flight und Speed. Diese Daten werden weder in `CorePlayer` noch in `PlayerSettings` oder `players/<uuid>.yml` persistiert. Teleportziele und letzte Positionen werden ebenfalls nicht gespeichert; `/back` gehört nicht zu dieser Phase.

`UtilityListener` entfernt beim Quit den UUID-State und normalisiert verwaltete Bewegung soweit noch sicher möglich. Beim Join vergisst er zunächst möglichen stale Runtime-State und plant die eigentliche Normalisierung einen Tick später. Dadurch läuft zuerst der bereits geplante Lobby-Join-State; anschließend setzt Utility Walk Speed auf `0.2F`, Fly Speed auf `0.1F` und entfernt in `SURVIVAL`/`ADVENTURE` unerwartetes Flight. Native Flight-Semantik in `CREATIVE`/`SPECTATOR` bleibt erhalten. Diese Join-Normalisierung deckt auch Prozessabbrüche ab, bei denen reguläres Disable-Cleanup nicht lief.

`OnlinePlayerResolver` löst Targets ausschließlich aus `Server#getOnlinePlayers()` über einen vollständigen, case-insensitive Namen auf. Es gibt weder Partial-/Fuzzy-Matches noch `OfflinePlayer`, Identity-Lookup oder Netzwerkzugriff. Bei optionalem Target reicht für Self die Basispermission; ein anderes Target und Console-mit-Target benötigen `.others`. Player-Completion wird ohne die erforderliche Permission nicht offengelegt und sonst case-insensitive stabil sortiert. Playernamen werden ausschließlich als `Component.text` gerendert. Gruppen oder Ränge sind keine Business-Logik; die kanonischen externen LuckPerms-Empfehlungen stehen in `docs/PERMISSIONS.md`.

VapeeCore kennt die Gruppennamen Builder, Moderator und Admin ausdrücklich nicht; sie sind nur Beispiele für eine Serverkonfiguration.

`/fly` verwaltet ausschließlich Flight in `SURVIVAL` und `ADVENTURE`. BUILD sowie `CREATIVE`/`SPECTATOR` behalten ihren jeweiligen nativen Owner. `/speed` akzeptiert Level 1–10; Level 1 entspricht Walk `0.2F` beziehungsweise Fly `0.1F`, Level 10 jeweils `1.0F`. Der Fly-Kanal gilt bei aktivem Fliegen sowie in `CREATIVE`/`SPECTATOR`, sonst der Walk-Kanal. `/gamemode` akzeptiert vollständige Namen, `s/c/a/sp` und `0/1/2/3`; Completion zeigt nur vollständige Namen. Ein BUILD-Target wird abgewiesen. Ein späterer Lobby-Resync darf einen temporär gesetzten Gamemode wieder auf `lobby.yml` normalisieren; Creative allein schaltet nie BUILD oder Protection-Bypass ein.

`/tp <target>` teleportiert den ausführenden Player zum exakten Online-Ziel und benötigt `vapeecore.utility.teleport`. `/tp <player> <target>` teleportiert die benannte Quelle zum benannten Ziel, funktioniert auch aus der Console und benötigt unabhängig von Self-Rechten `vapeecore.utility.teleport.others`. Die Console darf die Ein-Argument-Form nicht verwenden, weil sie keinen Player-Standort beziehungsweise keine Player-Quelle besitzt. Gleiche Quelle und Ziel werden ohne Mutation abgewiesen. `/tphere <player>` bleibt Player-only und benötigt `vapeecore.utility.teleport.here`.

Ohne `vapeecore.utility.teleport.bypass` schützen `/tp` und `/tphere` Activity-Teilnehmer als bewegte Quelle und Activity-Ziele als internen State-Guard. Der Bypass überspringt ausschließlich diese VapeeCore-Prüfungen. Der eigentliche synchrone Bukkit/Paper-Teleport läuft unverändert weiter; ein gecanceltes `PlayerTeleportEvent`, ein technischer Fehler oder `teleport(...) == false` wird weiterhin als Fehler gemeldet. BUILD-Teleports werden nicht doppelt behandelt: Ein tatsächlicher Weltwechsel löst den bestehenden `LobbyListener`-Cleanup aus, ein Teleport innerhalb derselben Lobby-Welt behält BUILD.

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
| `/tp <target>` | Selbst zum Online-Spieler teleportieren | `TeleportCommand` | `vapeecore.utility.teleport` |
| `/tp <player> <target>` | Online-Spieler A zu B teleportieren | `TeleportCommand` | `vapeecore.utility.teleport.others`; interner Guard-Bypass: `.teleport.bypass` |
| `/tphere <player>` | Online-Spieler zum Sender teleportieren | `TeleportHereCommand` | `vapeecore.utility.teleport.here` |
| `/heal [player]` | Aktuelle Max-Health wiederherstellen | `HealCommand` | `vapeecore.utility.heal`, fremde Targets: `.heal.others` |
| `/feed [player]` | Hunger, Saturation und Exhaustion normalisieren | `FeedCommand` | `vapeecore.utility.feed`, fremde Targets: `.feed.others` |
| `/ping [player]` | Online-Latenz anzeigen | `PingCommand` | `vapeecore.utility.ping`, fremde Targets: `.ping.others` |
| `/clear [player]` | Sicheres Player-Inventar löschen | `ClearCommand` | `vapeecore.utility.clear`, fremde Targets: `.clear.others` |
| `/invsee <player>` | Read-only Inventory-Snapshot öffnen | `InvseeCommand` | `vapeecore.utility.invsee`; `.invsee.modify` reserviert/inaktiv |
| `/enderchest [player]` | Echtes Enderchest öffnen | `EnderChestCommand` | `vapeecore.utility.enderchest`, fremde Targets: `.enderchest.others` |
| `/coins`, `/coins help`, `/coins …` | Eigene Coins anzeigen / permission-aware Hilfe / Online-Balances administrieren | `CoinsCommand` | Basis `vapeecore.economy.coins`, Mutationen zusätzlich `vapeecore.economy.admin` |
| `/msg`, `/reply`, `/r` | Private Online-Nachrichten | `MessageCommand`, `ReplyCommand` | `vapeecore.message.use` |
| `/settings` | Settings-Menü öffnen | `SettingsCommand` | `vapeecore.settings.use` |
| `/friend`, `/friends` | Freundschaften und Anfragen anzeigen/verwalten | `FriendCommand` | `vapeecore.friend.use` (Default `true`) |
| `/ignore`, `/unignore`, `/ignorelist` | Ignore-State verwalten | `IgnoreCommand`, `UnignoreCommand`, `IgnoreListCommand` | `vapeecore.social.ignore` |
| `/rank [player]` | Eigenen oder den Rank eines Online-Spielers anzeigen | `RankCommand` | `vapeecore.rank.view` (Default `true`) |
| `/ranks` | Öffentliche LuckPerms-Track-Reihenfolge anzeigen | `RanksCommand` | `vapeecore.ranks.view` (Default `true`) |
| `/blackjack`, `/blackjack help`, `/blackjack setup …` | Strukturierte Hilfe und Verwaltung physischer Blackjack-Tische | `BlackjackCommand` | `vapeecore.blackjack.admin` |
| `/warp`, `/warp help`, `/warp …` | Strukturierte Hilfe und Verwaltung dynamischer Warps | `WarpCommand` | `vapeecore.warp.admin` |

Ränge sind nicht in Java hardcodiert. LuckPerms vergibt Permissions, etwa `vapeecore.utility.build` an eine frei benannte Gruppe; VapeeCore prüft nur die Permission und kennt den Gruppennamen nicht. `/rank` und `/ranks` liegen im VapeeCore-Namespace und mutieren LuckPerms nicht. Die `.others`-Nodes der Self-/Others-Commands besitzen die jeweilige Basispermission als Child; die Basispermission gewährt niemals umgekehrt `.others`. `vapeecore.lobby.build` ist eine deprecated Compatibility-Permission in `plugin.yml`, deren Child die neue Permission gewährt. Die vollständige Matrix und das externe Bukkit-/Vanilla-Lockdown stehen in `docs/PERMISSIONS.md`.

## Command UX Standard

Einfache Commands wie `/spawn`, `/settings`, `/build` oder `/ignorelist` zeigen bei falscher Eingabe nur einen kurzen, kontrollierten Hinweis mit `Invalid usage.` und der exakten Syntax. Komplexe Commands mit mehreren Aktionen besitzen dagegen eine strukturierte `CommandHelpPage` mit logisch benannten `CommandHelpSection`s und je einer `CommandHelpEntry` pro sichtbarer Syntaxzeile. Die gemeinsamen immutable Modelle und der reine Presentation-Renderer liegen unter `dev.vapee.core.command.help`; Parsing, Permission-Gates und Service-Aufrufe bleiben in der jeweiligen dünnen Command-Klasse.

Der Renderer filtert Einträge ausschließlich über die am Entry hinterlegte Bukkit-Permission und unterdrückt danach leere Sections. Eine Help Page wird als ein mehrzeiliger Adventure-`Component` gesendet, damit der globale Prefix exakt einmal erscheint. Syntax wird immer über `Component.text` erzeugt: Platzhalter wie `<id>`, `<player>` oder `<amount>` bleiben sichtbarer Plain Text und werden nie als MiniMessage-Tags ausgewertet. Klickbare Syntax verwendet ausschließlich `ClickEvent.suggestCommand`; administrative Aktionen dürfen niemals durch einen Help-Klick ausgeführt werden.

Erwartete Benutzerfehler werden vollständig durch den Command behandelt und liefern `true`; `plugin.yml`-Usage bleibt nur ein kurzer technischer Fallback. Ein konkretes Subcommand mit falschen Argumenten zeigt seine genaue Syntax statt der gesamten Help Page. Unbekannte Subcommands benennen den unbekannten Wert sicher und verweisen auf das passende `help`. Success-Ausgaben nennen Aktion, Objekt und – wo hilfreich – das neue Ergebnis; Errors sind konkret, Warnungen beschreiben einen sicheren nächsten Schritt.

Tab Completion ist case-insensitive, stabil sortiert, permission-aware und möglichst klein. Spielerargumente stammen nur aus aktuell online befindlichen Spielern, dynamische IDs aus dem bereits geladenen Servicezustand und Warp-Materialien nur aus tatsächlich darstellbaren Items. Keine Completion lädt Offline-Player. Frei beeinflussbare IDs, Namen, Display Names und andere dynamische Werte werden mit `Component.text`, `Placeholder.unparsed` oder `Placeholder.component` eingesetzt, niemals per String-Konkatenation in ein MiniMessage-Template.

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
- `dev.vapee.core.lobby.player.LobbyHarness`
- `dev.vapee.core.utility.command.BuildCommandHarness`
- `dev.vapee.core.utility.UtilityServiceHarness`
- `dev.vapee.core.utility.command.UtilityCommandHarness`
- `dev.vapee.core.utility.UtilityInventoryHarness`
- `dev.vapee.core.message.CommandHelpHarness`
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
- `dev.vapee.core.identity.IdentityHarness`
- `dev.vapee.core.identity.ProfileHarness`
- `dev.vapee.core.friend.FriendPersistenceHarness`
- `dev.vapee.core.friend.FriendServiceHarness`
- `dev.vapee.core.friend.FriendDomainHarness`
- `dev.vapee.core.friend.FriendIntegrationHarness`
- `dev.vapee.core.friend.FriendCommandHarness`
- `dev.vapee.core.friend.FriendLifecycleHarness`
- `dev.vapee.core.reward.RewardServiceHarness`
- `dev.vapee.core.reward.RewardLifecycleHarness`
- `dev.vapee.core.onlinereward.OnlineRewardServiceHarness`
- `dev.vapee.core.onlinereward.OnlineRewardLifecycleHarness`
- `dev.vapee.core.player.repository.PlayerRewardPersistenceHarness`
- `dev.vapee.core.quest.QuestDefinitionHarness`
- `dev.vapee.core.quest.QuestServiceHarness`
- `dev.vapee.core.player.repository.PlayerQuestPersistenceHarness`
- `dev.vapee.core.quest.QuestLifecycleHarness`

Nach relevanten Änderungen folgen ein Paper-1.21.11-Smoke-Test mit Java 21 und LuckPerms 5.5.x, allen 23 Modulen, `/core`, `/core reload`, `/profile`, `/friend`, Command-Registrierung und sauberem Shutdown. Ein „Live Client Test“ darf nur dokumentiert werden, wenn wirklich ein Minecraft-Client verbunden war und die Schritte ausgeführt wurden; Serverstart oder Harness allein zählen nicht als Live-Client-Test.

## Documentation maintenance rule

Diese Datei ist Teil der Codebase. Wenn eine zukünftige Änderung eine Klasse verschiebt, einen Config-Key ändert, einen Command oder ein Modul hinzufügt, Ownership verschiebt, Permissions verändert oder Join-/Player-State-Flows anpasst, muss `docs/DEVELOPER_GUIDE.md` im selben Arbeitsschritt aktualisiert werden. Das README bleibt die Projektübersicht; die praktische Detaildokumentation bleibt hier.
