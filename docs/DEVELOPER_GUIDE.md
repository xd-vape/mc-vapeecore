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
| Gameplay-Coin-Rewards und gebündelte Persistence | `dev.vapee.core.reward` |
| Kumulative Online-/Playtime-Rewards | `dev.vapee.core.onlinereward` |
| Quest Progress Engine | `dev.vapee.core.quest.QuestService` |
| Quest Definition Domain | `QuestDefinition` und `QuestDefinitionRegistry` |
| Technische Quest Progress Keys | `QuestProgressKey` |
| Player Quest Persistence | `PlayerQuestState`, `PlayerQuestProgress` und `FilePlayerRepository` |
| Daily-Quest-Auswahl und Rotation | Noch nicht implementiert; folgt in Phase 17B |
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

Eine geänderte Resource ersetzt niemals automatisch eine bereits vorhandene Live-Datei. Bei einer bestehenden Dev- oder Produktionsinstallation muss der neue Wert auch in der Datei unter `plugins/VapeeCore/` eingetragen werden. `/core reload` liest nur seine fünf registrierten Live-Dateien neu. `blackjack.yml` und `warps.yml` werden stattdessen durch ihre Admin-Commands zur Laufzeit geschrieben und aktualisiert.

| File | Owner | Purpose | `/core reload`? | Runtime mutable? | Defaults |
|---|---|---|---:|---|---|
| `config.yml` | `ConfigService` | Servername, globaler Message-Prefix, Debug, `ranks.track`, Online-Reward-Regeln und -Nachricht | Ja | Durch Reload | `src/main/resources/config.yml` |
| `lobby.yml` | `LobbyConfig` / `LobbyModule` | Spawn, Teleport, Protection, `player.gamemode`, Join/Quit-Texte | Ja | Spawn durch `/setspawn`, übrige Werte durch Reload | `src/main/resources/lobby.yml` |
| `chat.yml` | `ChatConfig` / `ChatModule` | Globaler Chat und LuckPerms-Metaformat | Ja | Durch Reload | `src/main/resources/chat.yml` |
| `private-messages.yml` | `PrivateMessageConfig` / `PrivateMessageModule` | Aktivierung und PM-Formate | Ja | Durch Reload | `src/main/resources/private-messages.yml` |
| `presentation.yml` | `PresentationConfig` / `PresentationModule` | Sidebar, Tablist, Updateintervall | Ja | Durch Reload | `src/main/resources/presentation.yml` |
| `blackjack.yml` | `BlackjackTableConfig` / `BlackjackModule` | Physische Blackjack-Table-Drafts | Nein | Ja, atomar über `/blackjack setup …` | `src/main/resources/blackjack.yml` |
| `warps.yml` | `WarpConfig` / `WarpModule` | Dynamische Warps | Nein | Ja, atomar über `/warp …` | `src/main/resources/warps.yml` |

Die fünf Reload-Teilnehmer bleiben exakt `config.yml`, `lobby.yml`, `chat.yml`, `private-messages.yml` und `presentation.yml`. `ranks.track` und `online-rewards` gehören zum vorhandenen `ConfigService`; Rank, Reward, OnlineReward und Quest fügen keinen sechsten Teilnehmer hinzu. Reward und Quest besitzen keine eigene Config- oder Datendatei. Der Reload wird zweiphasig vorbereitet und angewendet. `OnlineRewardService` liest die immutable Config-View bei jedem Processing neu, sodass Enabled-, Intervall-, Coin- und Nachrichtenänderungen nach Apply ohne Neustart gelten. Bei Rollback setzt `LobbyModule` neben Config und Spawn auch die Gamemodes aller normalen Lobby-Spieler auf den vorherigen Wert zurück. BUILD-Spieler bleiben bis zum Build-Ende in `CREATIVE`.

## Module map und Reihenfolge

Die registrierte Reihenfolge ist eine Dependency-Reihenfolge und muss bei neuen Modulen bewusst gepflegt werden:

1. **Permission** – lesender LuckPerms-Zugriff.
2. **Rank** – cachefreie Rank-Domain, öffentlicher Track und `/rank`-/`/ranks`-Commands.
3. **Player** – `CorePlayer`, Cache, YAML-Persistence, Settings und Player-Join-/Quit-Lifecycle.
4. **Social** – Ignore-State und Commands.
5. **Economy** – Coin-Wallet und `/coins`.
6. **Reward** – zentrale Gameplay-Reward-API und gebündelte Player-Persistence.
7. **OnlineReward** – kumulative Minecraft-Spielzeit-Rewards und Player-Fortschritt.
8. **Quest** – generische Definitionen, Assignments, Fortschritt, Completion und gebündelte Player-Persistence.
9. **Lobby** – Config, Spawn, Protection, Player-State, Items, Messages, `/spawn`, `/setspawn`.
10. **Chat** – globaler Chat und lesende Rank-Placeholder.
11. **PrivateMessage** – `/msg`, `/reply` und Session-Konversationen.
12. **Presentation** – Sidebar, Tablist und lesende Rank-Placeholder.
13. **Settings** – Settings-Inventar und `/settings`.
14. **Activity** – generische Runtime-Typen, Venues, Sessions und Memberships.
15. **Utility** – `/build`, grundlegende Player-Utilities und transienter Movement-Cleanup.
16. **Seat** – generische CASUAL-/MANAGED-Sitze, Seat-Entities und Event-Cleanup.
17. **WorldDisplay** – native keyed TextDisplay-/ItemDisplay-Lifecycles.
18. **Blackjack** – physische Tische, Seat-Allocation, Activity-Hotbar und native Weltanzeigen.
19. **Warp** – dynamische Warp-Persistence und Admin-Command.
20. **LobbyExperience** – Visibility, Item-Interaktionen und Navigator-UI.

Shutdown läuft exakt rückwärts: LobbyExperience → Warp → Blackjack → WorldDisplay → Seat → Utility → Activity → Settings → Presentation → PrivateMessage → Chat → Lobby → Quest → OnlineReward → Reward → Economy → Social → Player → Rank → Permission. Quest stoppt zuerst seinen Flush-Task und speichert dirty Quest-State, während Reward und Player noch verfügbar sind. OnlineReward stoppt danach seinen Processing-Task; Reward flusht anschließend dirty Coins und jeweils den gesamten aktuellen `CorePlayer`, solange Economy und Player noch verfügbar sind. Rank besitzt keinen persistenten Player-State und benötigt keine zusätzliche Cleanup-Logik. Blackjack gibt seine MANAGED-Sitze frei, bevor Seat den globalen Rest bereinigt.

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

`RankModule` hängt ausschließlich von Plugin, Config, Permission und Message ab. Es hängt insbesondere nicht von Player, Economy, Lobby, Chat, Presentation, Activity oder Blackjack ab. `RewardModule` hängt nur von Plugin, Player und Economy ab; es besitzt insbesondere keine Abhängigkeit von OnlineReward, Activity, Mine, Quest, Rank, Chat oder Presentation. `OnlineRewardModule` konsumiert Config, Player, Reward und Message, aber nie Economy direkt, Activity, Rank, LuckPerms, Mine, Quest, Blackjack, Warp oder Lobby. `QuestModule` konsumiert ausschließlich Plugin, Player und Reward; es kennt Economy, OnlineReward, Activity, Blackjack, Mine, Rank, Permission und LuckPerms nicht. Features hängen in Richtung `Gameplay-Producer → Quest → Reward → Economy → Player`, nie umgekehrt. `Presentation` bezieht Rank, Permission, Player, Economy und Lobby. Chat bezieht Rank, Permission und Social; PrivateMessage bezieht Player und Social. `MessageService` sowie bei Bedarf `ConfigService` werden explizit injiziert. Utility besitzt keine Blackjack-Abhängigkeit. SeatService kennt weder Lobby noch Activity noch Blackjack; nur SeatListener erhält die Lobby-/Activity-Policy. WorldDisplay hängt nur vom Plugin ab.

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

`QuestDefinitionRegistry` hält den zur Laufzeit bekannten Katalog. `findById`, `snapshot` und `snapshotById` liefern immutable Sichten in deterministischer ID-Reihenfolge. `replaceAll` baut zuerst einen vollständigen Kandidaten auf und lehnt Nullwerte oder Duplicate-IDs ab, bevor es den Runtime-Katalog austauscht. Ein Fehler lässt den alten Katalog vollständig erhalten. Phase 17A erzeugt beim Modulstart absichtlich ein leeres Registry; YAML-Loading und konkrete Production-Definitionen folgen erst mit dem Daily-System.

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

Phase 17B kann später Definitionen per `QuestDefinitionRegistry#replaceAll` laden, mit `QuestService#replaceAssignments` drei bis vier Daily Quests setzen und Producer für `playtime:minute`, `mine:block:any`, `blackjack:win`, `activity:complete` oder `location:visit:mine` anbinden. Phase 17A implementiert ausdrücklich noch keine Daily-Rotation, Cycle-ID, Reset-Zeit, Auswahl, konkreten Quests, Gameplay-Hooks, Mine, `/quests`, GUI, Claim-Button, Feedback, Kategorien, Voraussetzungen, Ränge oder Multiplikatoren.

## Ranks & Server Identity

LuckPerms besitzt Gruppen, Mitgliedschaften, Primary Group, Display Name, Prefix, Suffix, Weight, Tracks und Permissions vollständig. VapeeCore liest und präsentiert diese Informationen ausschließlich; es existieren weder `Player.rank`, eigene Rank-Mitgliedschaften, eine Rank-Datenbank noch Rank-Zuweisungen in `plugins/VapeeCore/players/`. Administratoren ändern Ränge weiterhin mit LuckPerms. `/rank` und `/ranks` sind reine Lese-Commands im VapeeCore-Namespace und besitzen bewusst keine Alias-Flut.

`PermissionModule` stellt die einzige LuckPerms-Provider-Verbindung her. `LuckPermsService` kapselt geladene User, Group Information und Track Groups, ohne LuckPerms-API-Typen in andere Features zu leaken. Eine Group-Auflösung liest Display Name, Description, rohe Color Meta und Weight gemeinsam. `RankService` bildet diese Werte auf das immutable `RankInfo(id, displayName, description, color, weight)` ab. Normale Online-Abfragen verwenden ausschließlich `UserManager#getUser`; es gibt kein `loadUser`, kein `join()` und keinen dauerhaften Rank-Cache. Ist der User nicht geladen, liefert `getPrimaryRank` `Optional.empty()`. Fehlt die Group unerwartet, bleibt die bekannte ID erhalten und Display Name, Description, Color und Weight fallen kontrolliert zurück.

Der öffentliche Track steht unter `config.yml` → `ranks.track`; Default und Fallback sind `ranks`. Der Wert muss ein non-blank String sein, sonst warnt `ConfigService`, verwendet `ranks` und verändert die Datei nicht. Nur die in diesem LuckPerms-Track enthaltenen Gruppen erscheinen in `/ranks`; interne Gruppen bleiben unsichtbar. `Track#getGroups()` bestimmt die Reihenfolge, nicht Alphabet oder Weight. Da `RankService` den aktuellen Configwert bei jeder Listenabfrage liest, wirkt ein geänderter Track nach `/core reload` sofort, ohne sechsten Reload-Teilnehmer.

Jeder öffentliche Rank sollte in LuckPerms einen Group Display Name besitzen. Fehlt er, erzeugt VapeeCore ohne hartcodiertes Mapping einen neutralen Namen aus der Group-ID, zum Beispiel `senior_builder` → `Senior Builder`. Die optionale Beschreibung kommt aus `vapeecore.rank.description`; die Rank-Farbe kommt aus `vapeecore.rank.color`; Weight bleibt reine Metadateninformation. `RankService` akzeptiert Adventure Named Colors wie `gray`, `gold`, `aqua`, `green`, `red`, `dark_red` und `light_purple` sowie sechsstellige Hexwerte wie `#55ffaa`. Fehlende oder ungültige Werte bleiben `Optional.empty()` und werden bei der Ausgabe neutral weiß dargestellt. Prefix und Suffix bleiben unabhängig davon kompatibel und sind für Rank-Farbe nicht erforderlich.

Neue Ränge benötigen keine VapeeCore-Codeänderung. Der empfohlene LuckPerms-Ablauf ist: Gruppe erstellen, Group Display Name setzen, `vapeecore.rank.color` setzen, optional `vapeecore.rank.description` setzen und die Gruppe an den konfigurierten `ranks`-Track anhängen. Weder Gruppen-IDs noch deren Farben werden in Production Code abgebildet.

Feature-Zugriff basiert ausschließlich auf Permissions, nie auf Rank-Namen. Builder-Funktionen prüfen `vapeecore.utility.build`. Spätere Mine-Zugriffe verwenden `vapeecore.mine.<mine>`; spätere Reward-Multiplikatoren könnten über Permissions oder optionales LuckPerms-Meta modelliert werden. Phase 16C implementiert weder Mine noch `coin-multiplier`; Online Rewards sind für alle Ränge identisch und besitzen keine LuckPerms-Abhängigkeit.

Presentation und Chat unterstützen `<rank>` als farbiges Component aus dem freundlichen Primary-Rank-Namen, `<rank_name>` als normalen Player Display Name in der Primary-Rank-Farbe sowie `<rank_id>` als rohe Primary Group. `<name>` bleibt der unveränderte Player Display Name; `<group>` bleibt als Compatibility-Alias identisch zu `<rank_id>`; `<prefix>` und `<suffix>` bleiben die effektiven LuckPerms-Metawerte. Der Chat-Renderer wird pro `AsyncChatEvent` neu erzeugt, damit Papers viewer-unaware Cache nicht die erste Nachricht für Folge-Events wiederverwendet. Der Async-Pfad liest nur bereits geladene LuckPerms-Cached-Data, verwendet keine Player-Statistik und setzt Playertext weiterhin als sichere Adventure Component ein.

Presentation besitzt vollständig `<server>`, `<name>`, `<rank_name>`, `<prefix>`, `<suffix>`, `<rank>`, `<rank_id>`, `<group>`, `<playtime>`, `<coins>`, `<online>` und `<max_players>`; dieselben Placeholder gelten für Scoreboard, `tablist.name-format`, Header und Footer. `<playtime>` stammt ausschließlich aus `Player#getStatistic(Statistic.PLAY_ONE_MINUTE)`. Der historische Statistikname ist irreführend: Der Wert sind Ticks, also 20 Ticks pro Sekunde. `PlaytimeFormatter` rechnet mit `long`, klemmt negative Werte auf null und zeigt unter einer Stunde Minuten, unter einem Tag Stunden und Minuten sowie ab einem Tag Tage und Stunden. Es existieren weder eigene Persistence noch zusätzliche Scheduler- oder Polling-Tasks.

Das Default-Scoreboard zeigt Rank, Playtime, Coins und Onlinezahl im kompakten Label/Wert-Layout; `<rank>` darf dabei nicht von einer äußeren weißen Farbe überschrieben werden. Das Default-Tablistformat und Chat-Defaultformat bleiben `<prefix><name><suffix>`-basiert und ändern bestehende Darstellung nicht automatisch.

Der neue Resource-Default überschreibt keine bestehende `plugins/VapeeCore/presentation.yml`. Ein vorhandenes `<group>` funktioniert weiterhin; Administratoren müssen das neue Label/Wert-Layout und `<playtime>` auf einem bestehenden Dev- oder Live-Server bei Bedarf manuell in dessen Datei übernehmen und mit `/core reload` aktivieren.

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
| Quest Progress Engine | `QuestService` |
| Quest Definition Domain und Katalog | `QuestDefinition` / `QuestDefinitionRegistry` |
| Technische Quest Progress Keys | `QuestProgressKey` und später die Konstanten des produzierenden Features |
| Player Quest Persistence | `PlayerQuestState`, `PlayerQuestProgress` und `FilePlayerRepository` |
| Daily-Quest-Auswahl | Noch nicht implementiert; Phase 17B |

## Utility ownership und Lifecycle

`UtilityModule` bleibt genau ein `CoreModule`. Beim Enable bezieht es Lobby- und Activity-Services, erstellt einen `UtilityService` und den ausschließlich für Cleanup zuständigen `UtilityListener`, registriert acht Commands und anschließend den Listener. Es ist kein `ReloadParticipant` und besitzt keine Configdatei. Beim Disable werden verwaltetes Flight und beide Speed-Kanäle online normalisiert, UUID-Mengen geleert, Listener abgemeldet und Executor sowie TabCompleter entfernt. Der Gamemode wird dabei bewusst nicht zurückgesetzt.

`UtilityService` ist der einzige Owner der eigentlichen Bukkit-Mutationen für Flight, Speed, Gamemode, Utility-Teleports, Heal und Feed. Alle Mutationen sind Main-Thread-only. Der Service hält niemals dauerhafte `Player`-Referenzen, sondern ausschließlich UUID-Mengen für command-managed Flight und Speed. Diese Daten werden weder in `CorePlayer` noch in `PlayerSettings` oder `players/<uuid>.yml` persistiert. Teleportziele und letzte Positionen werden ebenfalls nicht gespeichert; `/back` gehört nicht zu dieser Phase.

`UtilityListener` entfernt beim Quit den UUID-State und normalisiert verwaltete Bewegung soweit noch sicher möglich. Beim Join vergisst er zunächst möglichen stale Runtime-State und plant die eigentliche Normalisierung einen Tick später. Dadurch läuft zuerst der bereits geplante Lobby-Join-State; anschließend setzt Utility Walk Speed auf `0.2F`, Fly Speed auf `0.1F` und entfernt in `SURVIVAL`/`ADVENTURE` unerwartetes Flight. Native Flight-Semantik in `CREATIVE`/`SPECTATOR` bleibt erhalten. Diese Join-Normalisierung deckt auch Prozessabbrüche ab, bei denen reguläres Disable-Cleanup nicht lief.

Die Commands lösen Targets ausschließlich über `Server#getPlayerExact` auf. Es gibt weder fuzzy Namen noch `OfflinePlayer`. Bei optionalem Target reicht für Self die Basispermission; ein anderes Target und Console-mit-Target benötigen `.others`. Player-Completion wird ohne `.others` nicht offengelegt und sonst case-insensitive stabil sortiert. Gruppen oder Ränge sind keine Business-Logik. Eine mögliche, ausschließlich externe LuckPerms-Konfiguration wäre beispielsweise:

- Builder: `vapeecore.utility.build`, `vapeecore.utility.fly`, `vapeecore.utility.speed`
- Moderator: `vapeecore.utility.teleport`, `vapeecore.utility.teleport.here`, `vapeecore.utility.heal`, `vapeecore.utility.feed`
- Admin: alle gewünschten `vapeecore.utility.*`-Permissions

VapeeCore kennt die Gruppennamen Builder, Moderator und Admin ausdrücklich nicht; sie sind nur Beispiele für eine Serverkonfiguration.

`/fly` verwaltet ausschließlich Flight in `SURVIVAL` und `ADVENTURE`. BUILD sowie `CREATIVE`/`SPECTATOR` behalten ihren jeweiligen nativen Owner. `/speed` akzeptiert Level 1–10; Level 1 entspricht Walk `0.2F` beziehungsweise Fly `0.1F`, Level 10 jeweils `1.0F`. Der Fly-Kanal gilt bei aktivem Fliegen sowie in `CREATIVE`/`SPECTATOR`, sonst der Walk-Kanal. `/gamemode` akzeptiert vollständige Namen, `s/c/a/sp` und `0/1/2/3`; Completion zeigt nur vollständige Namen. Ein BUILD-Target wird abgewiesen. Ein späterer Lobby-Resync darf einen temporär gesetzten Gamemode wieder auf `lobby.yml` normalisieren; Creative allein schaltet nie BUILD oder Protection-Bypass ein.

`/tp` teleportiert nur den ausführenden Spieler zu einem exakten Online-Target, `/tphere` nur ein Online-Target zum ausführenden Spieler. Es gibt keine Zwei-Target- oder Console-Form. BUILD-Teleports werden nicht doppelt behandelt: Ein tatsächlicher Weltwechsel löst den bestehenden `LobbyListener`-Cleanup aus, ein Teleport innerhalb derselben Lobby-Welt behält BUILD. Die Plugin-Commands `/gamemode` und `/tp` sind bewusst vereinfachte VapeeCore-Varianten; die Vanilla-Kommandos bleiben über `/minecraft:gamemode` und `/minecraft:tp` erreichbar.

Alle Utility-Mutationen, die laufenden Gameplay-State stören würden, fragen direkt und schmal `ActivityService.isParticipating(UUID)` ab. Das generische Activity-Framework erhält keine Utility-Regeln. `/tphere` prüft Sender und Target. `/heal` setzt aktuelle Max-Health sowie Fire-/Freeze-Ticks zurück, verändert aber weder Hunger, Inventory, Gamemode noch Potion Effects. `/feed` setzt Food 20, Saturation 20 und Exhaustion 0, verändert aber Health nicht.

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
| `/tp <player>` | Zum Online-Spieler teleportieren | `TeleportCommand` | `vapeecore.utility.teleport` |
| `/tphere <player>` | Online-Spieler zum Sender teleportieren | `TeleportHereCommand` | `vapeecore.utility.teleport.here` |
| `/heal [player]` | Aktuelle Max-Health wiederherstellen | `HealCommand` | `vapeecore.utility.heal`, fremde Targets: `.heal.others` |
| `/feed [player]` | Hunger, Saturation und Exhaustion normalisieren | `FeedCommand` | `vapeecore.utility.feed`, fremde Targets: `.feed.others` |
| `/coins`, `/coins help`, `/coins …` | Eigene Coins anzeigen / permission-aware Hilfe / Online-Balances administrieren | `CoinsCommand` | Basis `vapeecore.economy.coins`, Mutationen zusätzlich `vapeecore.economy.admin` |
| `/msg`, `/reply`, `/r` | Private Online-Nachrichten | `MessageCommand`, `ReplyCommand` | `vapeecore.message.use` |
| `/settings` | Settings-Menü öffnen | `SettingsCommand` | `vapeecore.settings.use` |
| `/ignore`, `/unignore`, `/ignorelist` | Ignore-State verwalten | `IgnoreCommand`, `UnignoreCommand`, `IgnoreListCommand` | `vapeecore.social.ignore` |
| `/rank [player]` | Eigenen oder den Rank eines Online-Spielers anzeigen | `RankCommand` | `vapeecore.rank.view` (Default `true`) |
| `/ranks` | Öffentliche LuckPerms-Track-Reihenfolge anzeigen | `RanksCommand` | `vapeecore.ranks.view` (Default `true`) |
| `/blackjack`, `/blackjack help`, `/blackjack setup …` | Strukturierte Hilfe und Verwaltung physischer Blackjack-Tische | `BlackjackCommand` | `vapeecore.blackjack.admin` |
| `/warp`, `/warp help`, `/warp …` | Strukturierte Hilfe und Verwaltung dynamischer Warps | `WarpCommand` | `vapeecore.warp.admin` |

Ränge sind nicht in Java hardcodiert. LuckPerms vergibt Permissions, etwa `vapeecore.utility.build` an eine frei benannte Gruppe; VapeeCore prüft nur die Permission und kennt den Gruppennamen nicht. `/rank` und `/ranks` liegen im VapeeCore-Namespace und mutieren LuckPerms nicht. Die `.others`-Nodes für Flight, Speed, Gamemode, Heal und Feed besitzen die jeweilige Basispermission als Child. `vapeecore.lobby.build` ist eine deprecated Compatibility-Permission in `plugin.yml`, deren Child die neue Permission gewährt. Produktionscode prüft den alten Namen nicht mehr. Der alte Name umgeht insbesondere niemals direkt die Lobby-Protection.

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
- `dev.vapee.core.reward.RewardServiceHarness`
- `dev.vapee.core.reward.RewardLifecycleHarness`
- `dev.vapee.core.onlinereward.OnlineRewardServiceHarness`
- `dev.vapee.core.onlinereward.OnlineRewardLifecycleHarness`
- `dev.vapee.core.player.repository.PlayerRewardPersistenceHarness`
- `dev.vapee.core.quest.QuestDefinitionHarness`
- `dev.vapee.core.quest.QuestServiceHarness`
- `dev.vapee.core.player.repository.PlayerQuestPersistenceHarness`
- `dev.vapee.core.quest.QuestLifecycleHarness`

Nach relevanten Änderungen folgen ein Paper-1.21.11-Smoke-Test mit Java 21 und LuckPerms 5.5.x, allen 20 Modulen, `/core`, `/core reload`, Command-Registrierung und sauberem Shutdown. Ein „Live Client Test“ darf nur dokumentiert werden, wenn wirklich ein Minecraft-Client verbunden war und die Schritte ausgeführt wurden; Serverstart oder Harness allein zählen nicht als Live-Client-Test.

## Documentation maintenance rule

Diese Datei ist Teil der Codebase. Wenn eine zukünftige Änderung eine Klasse verschiebt, einen Config-Key ändert, einen Command oder ein Modul hinzufügt, Ownership verschiebt, Permissions verändert oder Join-/Player-State-Flows anpasst, muss `docs/DEVELOPER_GUIDE.md` im selben Arbeitsschritt aktualisiert werden. Das README bleibt die Projektübersicht; die praktische Detaildokumentation bleibt hier.
