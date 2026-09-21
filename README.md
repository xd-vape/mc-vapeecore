# VapeeCore

VapeeCore ist das zentrale Basis-Plugin für einen Minecraft-Community-Server. Das Projekt ist als modularer Monolith aufgebaut und stellt aktuell eine zentrale Konfiguration, MiniMessage-/Adventure-Nachrichten, eine gemeinsame Command-Help-Präsentation, interne CoreModule, eine lokale Player Foundation, eine persistente Social-/Ignore-Grundlage, eine Coin-Economy, eine zentrale Reward Foundation, kumulative Online-/Playtime-Rewards, globalen Chat, private Nachrichten, Player-Presentation, eine Ingame-Settings-Oberfläche, grundlegende Utility-Commands, ein leichtgewichtiges Activity-Fundament, Community-Sitze, native World Displays, physisches Blackjack, ein generisches Warp-System, eine spielerfreundliche Lobby Experience und eine lesende LuckPerms-Rank-Integration bereit. Der aktuelle Stand ist Phase 16C „Online / Playtime Rewards“.

## Developer Documentation

Die praktische Architektur-, Ownership-, Config-, Command- und Erweiterungsdokumentation liegt in [docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md). Dort sind auch der verbindliche Command-UX-Standard und die Checkliste für neue Commands dokumentiert.

## Voraussetzungen

- Java 21
- Paper 1.21.11
- Maven 3
- LuckPerms 5.5 als Server-Plugin

LuckPerms muss als eigene Plugin-JAR im `plugins`-Verzeichnis des Paper-Servers vorhanden sein. Ohne LuckPerms wird VapeeCore nicht geladen.

## Build

```bash
mvn clean package
```

Die fertige Plugin-JAR wird unter `target/vapeecore-1.0-SNAPSHOT.jar` erzeugt.

### IntelliJ Dev-Server

Das Projekt enthält die geteilten IntelliJ-Run-Konfigurationen `Start VapeeCore Dev Server` und `Build & Deploy VapeeCore`. Der Server sollte über die erste Konfiguration oder über `dev-server/start.bat` gestartet werden. Beide Wege verwenden denselben verwalteten Startprozess und stellen einen sauberen Stop-Kanal bereit.

`Build & Deploy VapeeCore` sendet einem laufenden verwalteten Paper-Server zunächst den regulären Konsolenbefehl `stop` und wartet, bis Plugins, Spieler und Welten vollständig gespeichert wurden. Anschließend führt die Konfiguration `mvn clean package` aus und ersetzt `dev-server/plugins/vapeecore-1.0-SNAPSHOT.jar` über eine temporäre Deployment-Datei. Der Server bleibt danach absichtlich gestoppt und kann über `Start VapeeCore Dev Server` erneut gestartet werden. Ein fremder oder manuell gestarteter Paper-Prozess wird nicht hart beendet; in diesem Fall bricht das Deployment mit einer verständlichen Meldung ab.

## Architektur

- `command`: Commands und deren Subcommands
- `command.help`: immutable Help-Modelle und gemeinsamer Adventure-Renderer für komplexe Commands
- `activity`: runtimebasiertes Framework für kleine, direkt erreichbare Server-/Lobby-Aktivitäten
- `activity.blackjack`: physische, administrativ konfigurierte Blackjack-Tische, Karten-Domain, Activity-Hotbar und native Weltanzeigen
- `activity.location`: immutable Positionen, Bereiche und physische Activity-Venues
- `activity.player`: immutable Activity-Teilnehmer ohne dauerhafte Bukkit-Player-Referenzen
- `chat`: globaler Adventure-Chat mit LuckPerms-Prefix und -Suffix
- `config`: zentraler Zugriff auf die Bukkit-Konfiguration
- `economy`: internes Coin-Wallet, EconomyService und Coin-Commands
- `reward`: zentrale Gameplay-Reward-API mit gebündelter Player-Persistence
- `onlinereward`: kumulative, konfigurierbare Rewards auf Basis der Minecraft-Spielzeit
- `lobby`: Lobby-Spawn, Teleports und auf die Lobby-Welt begrenzter Schutz
- `lobby.item`: Definition und Erzeugung der drei Lobby-Hotbar-Items
- `lobby.message`: Rendering der konfigurierbaren Join-/Quit-Nachrichten
- `lobby.player`: zentraler Runtime-Zustand `NORMAL`/`BUILD`, Gamemode und Inventory-Ownership
- `lobby.warp`: generische persistente Warps ohne vordefinierte oder reservierte Ziele
- `lobby.experience`: Interaktionen der Lobby-Hotbar, dynamischer Warp Navigator, Settings-Shortcut und weltgebundene Player-Visibility
- `message`: Adventure- und MiniMessage-Ausgabe
- `module`: kleiner Lifecycle-Extension-Point für zukünftige Systeme
- `permission`: lesender Zugriff auf LuckPerms-Gruppen und Meta-Daten
- `rank`: cachefreie Rank-Domain, öffentlicher LuckPerms-Track und `/rank`-/`/ranks`-Commands
- `player`: Player-Domainmodell, aktiver Cache und Join-/Quit-Lifecycle
- `player.repository`: austauschbare Persistence mit lokaler YAML-Implementierung
- `player.social`: persistenter, UUID-basierter Social-State eines Players
- `player.settings`: persistente Player-Settings und interne Zugriffsschicht für andere Module
- `privatemessage`: sichere, sessionbasierte Nachrichten zwischen Online-Spielern
- `presentation`: Lobby-Sidebar und serverweite Adventure-Tablist
- `reload`: koordinierter zweiphasiger Config-Reload mit Runtime-Rollback
- `settings`: sichere Ingame-Oberfläche für die bereits persistenten Player-Settings
- `seat`: runtimebasierte CASUAL-/MANAGED-Sitze, technische Seat-Entities und Lobby-Interaktion
- `social`: Ignore-Service, threadsichere Runtime-Projektion, Lifecycle und Commands
- `utility`: zustandsbewusste Builder-/Admin-Utilities, Laufzeit-Cleanup und Commands
- `worlddisplay`: keyed native TextDisplay-/ItemDisplay-Lifecycles für dynamische Core-Features

Die 19 Module starten in der gerichteten Reihenfolge `Permission → Rank → Player → Social → Economy → Reward → OnlineReward → Lobby → Chat → PrivateMessage → Presentation → Settings → Activity → Utility → Seat → WorldDisplay → Blackjack → Warp → LobbyExperience` und werden beim Shutdown vollständig rückwärts deaktiviert. OnlineReward stoppt dadurch vor Reward, dessen Batch-Flush wiederum vor Economy und Player läuft. Die fünf Reload-Teilnehmer bleiben unverändert.

Aktive Spieler werden als `CorePlayer` im Speicher gehalten. Zum Profil gehören die Einstellungen `scoreboard`, `sounds`, `private-messages` und `lobby-players-visible`, die standardmäßig aktiviert sind, das Coin-Wallet, `PlayerSocial` und der kleine `OnlineRewardProgress`. Andere Module greifen über klar abgegrenzte Services darauf zu. Die lokale Persistence legt pro UUID eine Datei unter `plugins/VapeeCore/players/<uuid>.yml` an. Alte Player-Dateien ohne einzelne Settings oder ohne `settings`, `economy`, `social` beziehungsweise Online-Reward-State bleiben gültig. Ein fehlender Reward-State wird beim ersten Processing auf die aktuelle Minecraft-Spielzeit baselined, ohne vergangene Zeit auszuzahlen. Bukkit-`Player`-Instanzen werden nicht im Domainmodell gespeichert.

Jedes Player-Profil besitzt außerdem ein Coin-Wallet mit einer nicht negativen ganzzahligen `long`-Balance und dem Defaultwert `0`. Die Coins werden als `economy.coins` in derselben Player-YAML gespeichert; alte Dateien werden beim nächsten regulären Save automatisch ergänzt. Direkte beziehungsweise administrative Balance-Änderungen verwenden den `EconomyService`; Gameplay-Rewards laufen ausschließlich über den `RewardService`. `/coins` zeigt den eigenen Kontostand, während `/coins get|add|remove|set` ausschließlich online befindliche, geladene Spieler administriert. Es gibt bewusst keine Vault-Anbindung, Offline-Mutationen, weiteren Währungen oder Spieler-zu-Spieler-Transfers.

```yaml
name: Vapee
first-join: 123456
last-join: 123456
settings:
  scoreboard: true
  sounds: true
  private-messages: true
  lobby-players-visible: true
economy:
  coins: 2500
social:
  ignored:
    - "550e8400-e29b-41d4-a716-446655440000"
rewards:
  online:
    processed-playtime-ticks: 123456
```

Das `SocialModule` wird unmittelbar nach dem `PlayerModule` aktiviert und stellt `/ignore <player>`, `/unignore <player|uuid>` und `/ignorelist` mit der gemeinsamen Permission `vapeecore.social.ignore` bereit. Neu ignoriert werden ausschließlich exakt benannte Online-Spieler; dabei werden weder partielle Namen noch Bukkit-`OfflinePlayer`-Lookups verwendet. Unignore arbeitet ausschließlich gegen die eigene gespeicherte Ignore-Liste und kann bekannte Offline-Spieler über deren VapeeCore-Playerdatei oder als UUID auflösen. Namen dienen nur der Anzeige und Eingabeauflösung, persistente Wahrheit bleiben die UUIDs unter `social.ignored`.

Die Ignore-Beziehung ist einseitig. Wenn A B ignoriert, sieht A keine globalen Chatnachrichten von B; B sieht A weiterhin, solange B A nicht ebenfalls ignoriert. Papers veränderbare `AsyncChatEvent`-Viewer-Menge wird pro Player-Audience gefiltert, während Console und andere Audience-Typen erhalten bleiben. Dafür verwendet `SocialService` eine `ConcurrentHashMap<UUID, Set<UUID>>` mit unveränderlichen Sets. Der Async-Chatpfad liest weder den nicht threadsicheren Player-Cache noch Dateien. Die Player-YAML in `CorePlayer.getSocial()` bleibt alleinige persistente Wahrheit; der Snapshot wird nur nach erfolgreichem Save vollständig ersetzt.

Ignore blockiert außerdem private Nachrichten in beiden Richtungen und gilt automatisch auch für `/reply` beziehungsweise `/r`, weil Replies denselben Sendepfad verwenden. Wer selbst ein Ziel ignoriert, erhält die klare Meldung, dass dieses Ziel ignoriert wird. Ignoriert dagegen der Empfänger den Sender, wird aus Datenschutzgründen dieselbe allgemeine Ablehnung wie bei deaktiviertem PM-Empfang ausgegeben. Ignore verändert `settings.private-messages` nicht.

Phase 12 enthält bewusst kein Friends- oder Party-System, kein Social GUI und keine `social.yml`. Ignore versteckt weder Entities noch Tablist, Scoreboard, Nametags oder Join-/Quit-Meldungen. `SocialModule` ist ein normales `CoreModule` und kein `ReloadParticipant`; Social-Playerdaten werden durch `/core reload` nicht von Platte neu geladen.

Permissions, Gruppen, Primary Groups, Prefixe, Suffixe, Meta-Daten, Contexts und Vererbung werden ausschließlich von LuckPerms verwaltet. VapeeCore liest die bereits von LuckPerms aufgelösten Daten und speichert sie weder im `CorePlayer` noch in den Player-YAML-Dateien. Normale Permission-Checks erfolgen weiterhin über Bukkit/Paper.

### Ranks & Server Identity

LuckPerms bleibt die einzige Source of Truth für Rang-Mitgliedschaften, Primary Group, Display Name, Prefix, Suffix, Weight, Tracks und Permissions. VapeeCore erzeugt keine eigene Rank-Persistence und bietet keine Promote-/Demote- oder Rank-Mutationscommands. Das `RankModule` liest bereits geladene LuckPerms-User und aktuelle Group-/Trackdaten ohne blockierende User-Loads oder dauerhaften Rank-Cache.

Der öffentliche Rank-Track wird in `plugins/VapeeCore/config.yml` über `ranks.track` konfiguriert; Default ist `ranks`. `/ranks` zeigt ausschließlich dessen Gruppen und bewahrt die LuckPerms-Track-Reihenfolge. `/rank [player]` zeigt den Primary Rank eines Online-Spielers. Ein öffentlicher Rank sollte in LuckPerms einen Display Name besitzen; andernfalls erzeugt VapeeCore einen neutralen Fallback aus der Group-ID. Die optionale Beschreibung kommt aus `vapeecore.rank.description`, die dynamische Adventure-Farbe aus `vapeecore.rank.color`. Named Colors und sechsstellige Hexwerte wie `#c35cff` werden unterstützt; fehlende oder ungültige Farben fallen neutral auf Weiß zurück. Es gibt keine Rank-Namen oder Rank-Farben im Java-Code—auch ein später ergänzter Rank funktioniert allein über LuckPerms Display Name, Meta und Track.

Feature-Zugriff richtet sich immer nach Permissions, nie nach einem Rank-Namen. Builder-Funktionen bleiben beispielsweise an `vapeecore.utility.build` gebunden. Spätere Mine-Rechte verwenden `vapeecore.mine.<mine>`; Reward-Multiplikatoren könnten in einer späteren Phase über Permissions oder LuckPerms-Meta ergänzt werden, ohne Namen wie VIP oder Admin im Java-Code abzufragen. Phase 16C implementiert weder Mine noch Coin-Multiplikator.

### Reward Foundation

Das `RewardModule` beantwortet zentral, **wie** positive Gameplay-Coin-Rewards angewendet und gespeichert werden. Das aufrufende Feature entscheidet weiterhin, **wann** und **warum** ein Reward entsteht. Dadurch bleiben spätere Mine-, Quest-, Onlinezeit-, Activity-, Event- und Achievement-Systeme voneinander unabhängig und reichen nur UUID, exakten Coin-Betrag, `RewardSource` und einen internen Grund an den `RewardService` weiter.

Ein erfolgreicher Grant aktualisiert das geladene Wallet sofort, markiert aber lediglich die Player-UUID als dirty. Genau ein gemeinsamer synchroner Task persistiert diese Player höchstens einmal pro Sekunde; Quit wird vor dem regulären Player-Unload geflusht, und beim Shutdown läuft der Reward-Flush vor Economy und Player. Ein Save-Fehler bleibt isoliert, lässt die UUID für einen späteren Retry dirty und rollt den bereits sichtbaren Reward nicht zurück. Bei einem harten Prozessabbruch umfasst das absichtliche Verlustfenster daher ungefähr eine Sekunde.

Direkte Admin-Mutationen über `EconomyService#setCoins`, `addCoins` und `removeCoins` speichern weiterhin sofort und rollen bei Save-Fehlern zurück. Feature-Code darf den internen Deferred-Pfad des EconomyService nicht direkt verwenden, sondern muss Gameplay-Rewards über `RewardService` vergeben. Die Foundation besitzt weiterhin keine eigene Config, Datei, History, Ledger, Offline-Queue oder Multiplikatoren; Phase 16C verwendet sie nun als erster unabhängiger Consumer.

### Online / Playtime Rewards

`OnlineRewardModule` liest einmal pro Sekunde für alle Online-Spieler `Statistic.PLAY_ONE_MINUTE`. Der historische Statistikname liefert Ticks; dieselbe Quelle bleibt unverändert hinter Presentation-`<playtime>`. Standardmäßig werden pro kumulierten 60 Minuten exakt 250 Coins über `RewardService` mit `RewardSource.PLAYTIME` vergeben. Die Zeit muss nicht in einer Session gesammelt werden und gilt unabhängig von Welt, Lobby, Activity, Blackjack, Rank, Mine oder Quest.

Persistiert wird ausschließlich `rewards.online.processed-playtime-ticks`. Der unverarbeitete Fortschritt ergibt sich aus aktueller Minecraft-Spielzeit minus diesem Wert. Beim ersten Lauf eines Legacy-Spielers wird die aktuelle Statistik nur als Baseline gesetzt; auch hunderte vorhandene Stunden erzeugen daher keine rückwirkende Auszahlung. Nach einem erfolgreichen Reward wird der verarbeitete Wert nur um vollständige Intervalle erhöht, sodass Restzeit Sessions und Serverneustarts überlebt. Mehrere gleichzeitig fällige Intervalle werden in einem einzigen Reward-Grant zusammengefasst.

Die Einstellungen `online-rewards.enabled`, `interval-minutes`, `coins` sowie `message.enabled` und `message.format` liegen in `config.yml` und wirken nach `/core reload`, ohne einen sechsten Reload-Teilnehmer. Defaults sind 60 Minuten, 250 Coins und eine MiniMessage-Benachrichtigung mit `<coins>`, `<minutes>`, `<intervals>` und `<balance>`. Bestehende Live-Dateien benötigen den Block nicht; fehlende Keys verwenden interne Defaults und werden nicht automatisch in die Datei geschrieben.

Bei `enabled: false` wird die Baseline im Speicher regelmäßig auf die aktuelle Statistik synchronisiert, damit deaktivierte Zeit später nicht nachgezahlt wird. Ein Statistik-Reset rebased ebenfalls ohne Coins. Phase 16C besitzt bewusst kein AFK-System: Minecraft-Spielzeit zählt aktuell auch verbundene AFK-Zeit. Es gibt keine Permission, keinen Command, Claim-Button, Daily-/Session-Limit, Zufall, Rank-Multiplikator oder Kopplung an Mine und Quest.

Das `LobbyModule` verwendet die separate Datei `plugins/VapeeCore/lobby.yml`. `/setspawn` speichert dort den Lobby-Spawn mit Weltname, Position und Blickrichtung; `/spawn` teleportiert Spieler dorthin. `player.gamemode` bestimmt den normalen Lobby-Gamemode und verwendet bei ungültigen Werten sicher `ADVENTURE`. `LobbyPlayerStateService` besitzt den nicht persistenten Zustand `NORMAL`/`BUILD`, normalisiert Gamemode und Inventory und erzeugt in `NORMAL` die Lobby-Hotbar. `/build` liegt im `UtilityModule`, ist auf die Lobby beschränkt, mit Activities gegenseitig exklusiv und verwendet immer `CREATIVE`. Nur der aktive BUILD-Zustand umgeht Block- und Item-Schutz; die Permission `vapeecore.utility.build` erlaubt ausschließlich das Command. `vapeecore.lobby.build` bleibt nur als deprecated Permission-Parent zur Migration erhalten.

### Utility Commands

Phase 15A.3 erweitert dasselbe `UtilityModule` um `/fly`, `/speed`, `/gamemode` (Alias `/gm`), `/tp`, `/tphere`, `/heal` und `/feed`. Alle Player-Ziele werden exakt und ausschließlich online aufgelöst; Self- und Others-Rechte bleiben getrennt. Flight und Speed sind reine Laufzeit-Zustände: Join, Quit und Modul-Shutdown bereinigen sie, ohne Player-YAMLs oder neue Konfigurationen zu verändern. Gamemode, Movement, Teleport, Heal und Feed sind während einer Activity gesperrt, soweit sie deren Gameplay-State verändern würden. BUILD bleibt vollständig im `LobbyPlayerStateService`; `/fly` greift dort nicht ein, `/gamemode` wahrt dessen CREATIVE-Invariante und Teleports verlassen BUILD über den bestehenden World-Change-Flow. Die konkreten Permissions, Zustandsregeln und Erweiterungspunkte stehen im Developer Guide.

### Activity Foundation

Das nach `SettingsModule` gestartete `ActivityModule` stellt mit dem `ActivityService` vier ausschließlich zur Laufzeit geführte Registries bereit: Activity-Typen, Venues, Sessions und die globale Zuordnung `Player-UUID → Session-UUID`. Direkt beim Enable des ActivityModule bleiben alle Registries leer; das später gestartete BlackjackModule registriert seinen Typ und die aus `blackjack.yml` aktivierten Venues und Sessions. Der frühere, ausschließlich für die Compass-Navigation verwendete `ActivityCatalog` samt `ActivityEntryPoint` wurde in Phase 15A entfernt. Mutierende Service-Operationen sind auf den primären Server-Thread begrenzt. Es gibt weder Reflection-Scans noch statische Registries oder Activity-, Venue- beziehungsweise Session-Persistence und keine neuen Keys in den Player-YAML-Dateien.

Ein `ActivityType` besitzt einen stabilen Key nach `[a-z0-9_-]+`, eine minimale und maximale Teilnehmerzahl und erzeugt eine konkrete `ActivitySession` für eine UUID und ein bereits registriertes `ActivityVenue`. Typen müssen explizit vor ihren Venues registriert werden. Ein Typ kann erst entfernt werden, wenn keine zugehörigen Venues oder Sessions mehr existieren. Ein Venue ist über `activityKey + id` eindeutig, gehört genau einem Typ und kann gleichzeitig höchstens eine Session reservieren. Es kann erst entfernt werden, nachdem diese Session geschlossen wurde.

`ActivityPosition` speichert Weltname, Koordinaten und Blickrichtung als finite Werte und löst eine Bukkit-`Location` nur auf, wenn die Welt bereits geladen ist. `ActivityArea` ist ein normalisierter, achsenparalleler und weltgebundener Quader. `ActivityVenue` verbindet eine solche Area mit einer repräsentativen Anchor-Position; Anchor und Area müssen dieselbe Welt verwenden und der Anchor muss innerhalb der Area liegen. Das Framework lädt oder erzeugt keine Welten automatisch.

`ActivitySession` ist die abstrakte Basis konkreter Activities. Sie speichert Session-UUID, Activity-Key, Venue, Erstellungszeitpunkt, Lifecycle-State und immutable Teilnehmer-Snapshots. `ActivityParticipant` enthält ausschließlich UUID und Beitrittszeitpunkt; dauerhafte Bukkit-`Player`-Referenzen werden nicht gehalten. Ein Spieler kann serverweit höchstens einer Activity-Session angehören. `ActivityService.joinSession` prüft Online-Status, geladenen `CorePlayer`, Membership, Session-State, Kapazität und Venue-Welt. Das Framework teleportiert nicht, ändert keinen GameMode und manipuliert weder Inventar, Rüstung, Hotbar noch Lobby-Items.

Der absichtlich kleine Lifecycle lautet `AVAILABLE → ACTIVE → RESETTING → AVAILABLE`; aus `AVAILABLE`, `ACTIVE` und `RESETTING` ist zusätzlich der terminale Übergang nach `CLOSED` erlaubt. Alle anderen Übergänge werden abgelehnt. Der Service besitzt die State-Transitions; konkrete Sessions können den State nicht frei setzen. Aktivierung verlangt mindestens die konfigurierte Teilnehmerzahl. Verlässt während `ACTIVE` ein Teilnehmer die Session und fällt sie dadurch unter dieses Minimum, führt der Service kontrolliert `ACTIVE → RESETTING → AVAILABLE` aus. Ein normaler Reset entfernt die verbleibenden Teilnehmer nicht.

Konkrete Sessions erhalten nur die kleinen Hooks `onParticipantJoined`, `onParticipantLeft`, `onActivated`, `onReset` und `onClosed`. Über `trackTask` und `untrackTask` gehören Activity-eigene Bukkit-Tasks direkt ihrer Session. Reset und Close canceln alle getrackten Tasks und leeren die Task-Sammlung. Schlägt Aktivierung, Reset oder Task-Cleanup fehl, wird die betroffene Session sicher geschlossen; ein Join-Hook-Fehler rollt Participant und Membership zurück. Leave- und Close-Hooks laufen best-effort, sodass ein fehlerhafter Hook die Bereinigung anderer Teilnehmer, Memberships, Registries und der Venue-Reservierung nicht verhindert.

Der kleine `ActivityListener` behandelt ausschließlich Quit und World Change. Quit entfernt eine vorhandene Membership mit `DISCONNECT`; ein Wechsel aus der Venue-Welt entfernt sie mit `WORLD_CHANGE`. Ein Wechsel innerhalb derselben Welt bleibt unangetastet. Es gibt bewusst keinen globalen `PlayerMoveEvent`-Listener und keinen globalen Activity-Tick-Task. Beim Modul-Shutdown werden alle Sessions über denselben zentralen Safe-Close-Pfad geschlossen, danach Memberships, Venue-Reservierungen, Venues und Typen geleert.

Activity und Game bleiben getrennte Konzepte. Das Activity-Framework besitzt keine Economy-, Social-, Settings- oder Presentation-Abhängigkeit, kein Inventory- oder Player-Snapshot-System, kein universelles Countdown-, Winner-, Team-, Spectator-, Arena-Reset- oder Matchmaking-System. Konkrete Activities injizieren nur ihre tatsächlich benötigten Module. Das matchbasierte Game-Framework folgt separat in Phase 16.

### Community Seating & World Display Foundation

`SeatModule` besitzt die gemeinsame physische Seat-Infrastruktur. Ein leerer Main-Hand-Rechtsklick setzt normale Lobby-Spieler auf freie Bottom-Stairs sowie Bottom-/Top-Slabs; Sneaken, BUILD, laufende Activities, registrierte Activity-Areas, bereits belegte Sitze, bestehende Vehicles und blockierter Kopfraum werden ignoriert. Top-Stairs und Double-Slabs sind bewusst keine Casual-Sitze. Die Laufzeitdaten sind weder persistent noch konfigurierbar und benötigen keine Permission oder `/sit`-Command.

`SeatService` unterscheidet `CASUAL` und `MANAGED`, erzwingt genau einen Seat pro Spieler und einen Spieler pro Key und besitzt Reservation, Mount, Dismount-Guard, PDC-Markierung sowie Startup-/Shutdown-Cleanup. `BlackjackSeatService` reserviert bei modernen Tischen exakt den angeklickten Sitz; nur Legacy-Tische verwenden weiterhin die niedrigste freie Sitznummer. Alte `blackjack_seat`-Entities werden während der Migration weiterhin entfernt.

`WorldDisplayModule` stellt einen runtimebasierten, keyed Lifecycle für native `TextDisplay`- und `ItemDisplay`-Entities bereit: Erzeugen, typisierte Updates, Teleport, Einzel-/Owner-Entfernung und stale Cleanup. Es gibt keine Demo-Displays, Persistenz, Commands, Tick-Abfrage, Packet-Library oder harte Abhängigkeit von einem Hologramm-Plugin. Externe Hologramm-Plugins können unabhängig für statische Admin-Hologramme verwendet werden; dynamische VapeeCore-Features nutzen diese native Foundation.

### Blackjack Activity

Das `BlackjackModule` ist die erste konkrete Activity und wird nach Seat und WorldDisplay sowie vor Warp und LobbyExperience aktiviert. Es bezieht `JavaPlugin`, `ActivityModule`, `SeatModule`, `WorldDisplayModule`, `LobbyModule`, `MessageService` und `CommandHelpRenderer`. Sein `BlackjackActivityType` verwendet den Key `blackjack`, `minParticipants = 1` und `maxParticipants = 5`. Spieler im BUILD-Modus werden vor jeder Sitzreservierung abgewiesen. Moderne Tische werden mit leerer Main Hand direkt über den konkreten Sitzblock betreten; alte Konfigurationen bleiben über ihren Interaktionsblock und die niedrigste freie Sitznummer kompatibel.

Eine Solo-/Public-Auswahl gibt es nicht mehr: Ein Teilnehmer spielt automatisch allein, mehrere Teilnehmer spielen gemeinsam gegen denselben Dealer. Ein Spieler kann sofort `Deal` drücken; Queue, Countdown oder zweite erforderliche Person existieren nicht. Solange eine Session `AVAILABLE` ist, werden bis zur tatsächlichen Anzahl konfigurierter Sitze weitere Spieler aufgenommen. Sobald jemand `Deal` drückt und der Tisch `ACTIVE` ist, sind Mid-Round-Joins kontrolliert gesperrt. Die globale Obergrenze bleibt fünf, ein Tisch mit drei Sitzen besitzt praktisch aber Kapazität drei.

Die physische Blockstruktur eines Tisches gehört dem Map-Builder und wird manuell gebaut. VapeeCore speichert weder Tischblöcke noch Schematics, repariert oder regeneriert keine Struktur und benötigt dafür weder Resource Pack noch Furniture-System. `plugins/VapeeCore/blackjack.yml` enthält nur den Gameplay-Bereich, Dealer, Sitze und den optionalen `display`-Anchor als Mittelpunkt, Oberflächenhöhe und Hauptausrichtung der echten Spielfläche. `/blackjack setup display <id>` ermittelt die Oberkante des anvisierten Blocks aus dessen Collision Shape; beim Dealer-Setup steht der Admin an der Dealerposition und schaut zum Tisch. `/blackjack setup preview <id>` zeigt für zwölf Sekunden Center, Floating Dealer Hand, Status- und vorhandene Sitz-Kartenpositionen, auch bei disabled oder teilweise konfigurierten Drafts.

Das Admin-Setup erfolgt mit `vapeecore.blackjack.admin`; `/blackjack help` und `/blackjack setup help` zeigen die verfügbaren Aktionen einzeln und in Workflow-Gruppen. Neue Drafts sind disabled. Positionen und Sitze können nur im disabled Zustand bearbeitet werden; `enable` validiert und aktiviert atomar, `disable` ist nur bei `AVAILABLE` und null Teilnehmern erlaubt, und Löschen setzt ein vorheriges Disable voraus. Änderungen werden sofort per temporärer Datei und Atomic-Move-Fallback gespeichert.

Beim Beitritt werden Sitz, Activity-Membership, Mount und Inventory-Ownership transaktional übernommen. `LobbyPlayerStateService` gibt das NORMAL-Inventar an `BlackjackInventoryService` ab; BUILD bleibt ausgeschlossen. Die Activity-Hotbar zeigt abhängig vom Rundenzustand Deal, Hit, Stand, Double, Status und Leave und verwendet den PDC-Key `blackjack_action`. Nach der dreisekündigen Ergebnisansicht wechselt die Session sauber zu `AVAILABLE`; erst der neue Post-Reset-Hook aktualisiert Hotbar und Weltanzeige, sodass derselbe sitzende Teilnehmer ohne Dismount sofort erneut Deal drücken kann. Drop, Inventory-Move, Number-Key, Drag, Offhand-Swap und Pickup sind ausschließlich während der Teilnahme geschützt. Leave stellt NORMAL in der Lobby wieder her; Quit, World Change, Tod und Plugin-Shutdown bereinigen ohne unerwünschtes Reapply. Tod ist ein generischer `ActivityLeaveReason.DEATH`.

Eine Runde teilt in deterministischer Join-Reihenfolge aus: je eine Karte an alle Teilnehmer, eine an den Dealer, je eine zweite an alle Teilnehmer und die verdeckte Hole Card des Dealers. Unterstützt werden Hit, Stand und Double Down sowie Natural Blackjack, Bust, Win, Loss und Push. Double ist nur im eigenen ersten Zug mit exakt zwei Karten und ohne Natural möglich, zieht genau eine Karte und steht automatisch. Der Dealer zieht unter 17 und steht auch auf Soft 17; sind alle Spieler bereits Bust oder sichere Naturals, wird ohne bedeutungsloses Dealer-Ziehen direkt abgerechnet. Der Six-Deck-Shoe bleibt ein unveränderter Fisher-Yates-Shuffle mit `Random`, ohne Pity-, Seed- oder Win-Chance-Manipulation. Split, Insurance, Surrender und Side Bets sind nicht enthalten.

`BlackjackWorldViewService` rendert Status, Werte, Ergebnisse und Karten ausschließlich als native `TextDisplay`-Entities. Playerkarten bleiben unverändert als einzelne horizontale Karten auf der Table Surface. Der Dealer verwendet dagegen genau ein aufrechtes `dealer-hand`-Display vor seiner gespeicherten Position: Während des Spielerzugs bleiben Hole Card und Gesamtwert verborgen, Reveal und weitere Dealer-Karten aktualisieren dieselbe Entity. Abstand, Höhe und Größe liegen zentral in `BlackjackDisplayGeometry`; Production und Preview verwenden dieselbe Positionsberechnung. Produktions-Owner `blackjack:<tableId>` und Preview-Owner `blackjack-preview:<adminUuid>:<tableId>` sind strikt getrennt.

Phase 15A ist ausdrücklich `Free Play`: Blackjack importiert weder `EconomyModule` noch `EconomyService`, verändert keine Coins und besitzt keine Einsätze, Payouts oder Escrow-Logik. `blackjack.yml` persistiert ausschließlich den physischen Tischaufbau; Player, Sitz, Session, Runde, Hände und Shoe bleiben reine Runtime-Daten. Das bestehende Player-YAML-Schema bleibt unverändert. Echte Coin-Wetten benötigen später eine transaktionale Escrow- und Crash-Recovery-Strategie, damit ein Serverausfall keinen Einsatz verlieren oder verdoppeln kann.

Das als letztes gestartete `LobbyExperienceModule` bezieht den vom `LobbyModule` besessenen `LobbyItemService` und verarbeitet die Interaktionen der drei PDC-markierten Hotbar-Items: Warp Navigator in Slot 0 (`COMPASS`), Player-Visibility in Slot 4 (`LIME_DYE` oder `GRAY_DYE`) und Settings in Slot 8 (`COMPARATOR`). Das Setzen und Entfernen der normalen Hotbar gehört dagegen dem `LobbyPlayerStateService`: `NORMAL` bereinigt das vollständige Player-Inventory und erzeugt die drei Items deterministisch, `BUILD` entfernt sie und stellt ein temporäres Creative-Inventory bereit. Drop, Offhand-Swap, Click-, Shift-, Number-Key-, Double-Click- und Drag-Manipulationen der PDC-markierten Items werden verhindert. Der Visibility-Schalter besitzt einen kurzen Server-Cooldown; Block-Rechtsklicks werden für die Item-Aktualisierung um einen Tick verschoben, damit Boden-Interaktionen nicht doppelt oder verloren verarbeitet werden.

Das eigenständige `WarpModule` hängt nur von `JavaPlugin` und `MessageService` ab. `plugins/VapeeCore/warps.yml` beginnt mit `warps: {}`; es gibt keine vordefinierten, reservierten oder besonders behandelten Lobby-, Spawn-, Casino- oder Blackjack-Ziele. Administratoren pflegen beliebige IDs nach `[a-z0-9_-]+`; `/warp` und `/warp help` zeigen die verfügbaren Aktionen strukturiert. Neue Warps erhalten generisch einen Namen aus der ID und `ENDER_PEARL`; erneutes Setzen ändert nur die Position. Persistente Änderungen werden vor dem Runtime-Austausch atomar geschrieben, und Teleports verwenden `TeleportCause.PLUGIN`, ohne Welten zu laden.

Der Compass öffnet ein geschütztes dynamisches 54-Slot-Inventar, das ausschließlich `WarpService.getWarps()` plus technische Controls rendert. Bis zu 45 alphabetisch nach ID sortierte Ziele erscheinen pro Seite; Previous, Page Info, Close und Next liegen in der unteren Reihe. Null Warps sind ein gültiger Empty State. Der ownergebundene `NavigatorInventoryHolder` speichert Seite, exaktes Inventar und die konkrete `slot → warpId`-Zuordnung. Titel- oder Material-Erkennung und feste Kategorien existieren nicht. Das Settings-Hotbar-Item öffnet weiterhin das vorhandene `SettingsMenu`; `/settings` und alle übrigen bestehenden Commands bleiben unverändert verfügbar.

Player-Visibility ist einseitig und ausschließlich auf andere Spieler in der Lobby-Welt begrenzt. `false` versteckt für den jeweiligen Viewer andere Lobby-Spieler über Papers `hidePlayer(plugin, target)`, beeinflusst aber weder Chat, private Nachrichten, Ignore, Scoreboard noch andere Welten. Beim Betreten werden beide Sicht-Richtungen gegen die jeweiligen persistenten Präferenzen synchronisiert; beim Verlassen, Quit und Plugin-Disable werden die von VapeeCore gesetzten Hide-Zustände mit `showPlayer` restauriert. Die persistente Wahrheit bleibt `settings.lobby-players-visible` in der Player-YAML; es gibt keinen separaten Visibility-Cache. Ein fehlender Key wird rückwärtskompatibel als `true` geladen. Ein fehlgeschlagener Save wird durch den bestehenden `PlayerSettingsService` auf den vorherigen Domainwert zurückgerollt, bevor Sichtbarkeit oder Hotbar-Darstellung geändert werden. Lobby-Visibility ist ausdrücklich nicht mit dem Ignore-System gekoppelt.

Join- und Quit-Nachrichten werden ebenfalls aus der bestehenden `lobby.yml` gelesen:

```yaml
messages:
  join:
    enabled: true
    format: "<dark_gray>[<green>+<dark_gray>] <white><name>"
  quit:
    enabled: true
    format: "<dark_gray>[<red>-<dark_gray>] <white><name>"
```

Bei `enabled: false` wird die jeweilige Nachricht vollständig unterdrückt; es gibt keinen Vanilla-Fallback. `LobbyMessageService` rendert `<name>` als Adventure Component, protokolliert Laufzeitfehler und liefert einen sicheren Component-Fallback. Fehlende Keys verwenden interne Defaults. Ungültige Templates erzeugen eine Warnung und verwenden einen sicheren internen Default, ohne `lobby.yml` zu verändern. Da `LobbyModule` weiterhin alleiniger Reload-Teilnehmer für `lobby.yml` ist und LobbyExperience dessen Message-Service bezieht, gelten Änderungen nach `/core reload` ohne einen sechsten Reload-Teilnehmer.

Das `ChatModule` formatiert den globalen Chat über Papers `AsyncChatEvent` und einen pro Event neu erzeugten viewer-unabhängigen `ChatRenderer`. Das Format liegt in `plugins/VapeeCore/chat.yml`; LuckPerms-Prefix und -Suffix können dort als `legacy-ampersand`, `mini-message` oder `plain` interpretiert werden. Zusätzlich stehen `<rank>` für den farbigen freundlichen Display Name, `<rank_name>` für den Player Display Name in Rank-Farbe sowie `<rank_id>` und der Compatibility-Alias `<group>` für die rohe Primary Group bereit. `<name>` bleibt unverändert. Der Async-Pfad liest ausschließlich bereits geladene LuckPerms-Daten und startet keine blockierenden Loads oder Statistikabfragen. Das Serverformat ist MiniMessage, die originale Playernachricht wird jedoch als Adventure Component eingesetzt und niemals als MiniMessage ausgewertet. Das Defaultformat bleibt `<prefix><name><suffix> …` und dupliziert den Rank nicht neben dem Prefix.

Das `PrivateMessageModule` stellt `/msg <player> <message>` sowie `/reply <message>` mit dem Alias `/r` für ausschließlich online befindliche Spieler bereit. Darstellung und globaler Aktivierungsstatus liegen in `plugins/VapeeCore/private-messages.yml`. Spielertext wird als sichere Adventure Component eingesetzt und weder als MiniMessage noch als Legacy-Farbcode ausgewertet. `PlayerSettings.privateMessagesEnabled` bedeutet ausschließlich „private Nachrichten empfangen“: Ein Spieler mit deaktiviertem Empfang darf weiterhin selbst schreiben. Der letzte erfolgreiche Gesprächspartner wird nur für die aktuelle Session im Speicher gehalten; ein Quit entfernt alle zugehörigen Reply-Verweise. Es gibt bewusst keine Offline-Nachrichten, Mailbox oder SocialSpy.

Das `PresentationModule` aktualisiert über einen gemeinsamen synchronen Task standardmäßig einmal pro Sekunde die persönliche Sidebar und Tablist. Die Templates liegen in `plugins/VapeeCore/presentation.yml` und verwenden Adventure/MiniMessage mit sicheren Component-Platzhaltern für `<server>`, `<name>`, `<rank_name>`, `<prefix>`, `<suffix>`, `<rank>`, `<rank_id>`, den rückwärtskompatiblen `<group>`-Alias, `<playtime>`, `<coins>`, `<online>` und `<max_players>`. `<rank>` ist der farbige freundliche Display Name; `<rank_name>` ist der Player Display Name in derselben Farbe; `<rank_id>` und `<group>` bleiben die rohe Primary Group. `<playtime>` liest synchron `Statistic.PLAY_ONE_MINUTE`, interpretiert den Minecraft-Wert korrekt als Ticks und formatiert ihn ohne eigene Persistence auf höchstens zwei Einheiten. Die Sidebar nutzt im Resource-Default ein Label/Wert-Layout für Rank, Playtime, Coins und Online; die Tablist behält `<prefix><name><suffix>`. Bereits vorhandene Live-Dateien werden durch den Resource-Default nicht überschrieben und müssen für das neue Layout bei Bedarf manuell angepasst werden.

Das `SettingsModule` stellt `/settings` für Spieler mit `vapeecore.settings.use` bereit. Das unveränderte 27-Slot-Inventar schaltet Scoreboard, VapeeCore-eigene UI-Feedback-Sounds und den Empfang privater Nachrichten um. Visibility wird bewusst nur über das Lobby-Hotbar-Item bedient, damit keine zyklische Abhängigkeit entsteht. Die Werte werden unmittelbar über den bestehenden `PlayerSettingsService` in derselben Player-YAML gespeichert; es gibt weder eine zweite Settings-Persistence noch eine `settings.yml`. Ein Scoreboard-Toggle lässt die Presentation sofort den gültigen Scope neu bewerten. Das PM-Setting wirkt ebenfalls sofort und steuert ausschließlich den Empfang—das eigene Senden bleibt möglich. `SettingsModule` ist kein `ReloadParticipant`.

`/core reload` liest `config.yml`, `lobby.yml`, `chat.yml`, `private-messages.yml` und `presentation.yml` in einer gemeinsamen zweiphasigen Transaktion neu. Zuerst werden alle fünf Dateien in dieser Reihenfolge vollständig vorbereitet und validiert; erst danach werden ihre Runtime-Zustände in derselben Reihenfolge angewendet. Ein Prepare-Fehler verändert daher keinen aktiven Zustand. Bei einem unerwarteten Apply-Fehler werden bereits übernommene Zustände rückwärts zurückgerollt. Message-Prefix, Servername, Debug-Modus, `ranks.track`, Lobby-Regeln, Chat- und PM-Format sowie Scoreboard- und Tablist-Templates werden ohne Serverneustart aktiv. `RankService` liest den Tracknamen bei jeder Anfrage neu und ist selbst kein Reload-Teilnehmer. Der Reload schreibt keine Configdateien, lädt keine Playerdaten und verändert weder Social-State, Economy noch LuckPerms.

Die 19 Module starten exakt in der Reihenfolge Permission → Rank → Player → Social → Economy → Reward → OnlineReward → Lobby → Chat → PrivateMessage → Presentation → Settings → Activity → Utility → Seat → WorldDisplay → Blackjack → Warp → LobbyExperience und stoppen in umgekehrter Reihenfolge. `/core reload` besitzt weiterhin genau fünf Teilnehmer (`config.yml`, `lobby.yml`, `chat.yml`, `private-messages.yml`, `presentation.yml`); Rank, Reward und OnlineReward sind keine eigenen Reload-Teilnehmer.

Weitere Activities, Voice-System, Community-Funktionen und Minigames werden in späteren Phasen als klar abgegrenzte interne `CoreModule` innerhalb derselben VapeeCore-JAR ergänzt. Phase 16C ergänzt ausschließlich kumulative Onlinezeit-Rewards. Quests, Mine, Shop, AFK-Erkennung, Reward-Multiplikatoren, Blackjack-Betting und weitere Blackjack-Visual-Reworks bleiben für spätere Phasen reserviert.
