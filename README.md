# VapeeCore

VapeeCore ist das zentrale Basis-Plugin für einen Minecraft-Community-Server. Das Projekt ist als modularer Monolith aufgebaut und stellt aktuell eine zentrale Konfiguration, MiniMessage-/Adventure-Nachrichten, eine gemeinsame Command-Help-Präsentation, interne CoreModule, eine lokale Player Foundation, eine persistente Social-/Ignore-Grundlage, eine Coin-Economy, globalen Chat, private Nachrichten, Player-Presentation, eine Ingame-Settings-Oberfläche, ein leichtgewichtiges Activity-Fundament, physisches Blackjack, ein generisches Warp-System, eine spielerfreundliche Lobby Experience und eine lesende LuckPerms-Integration bereit. Der aktuelle Stand ist Phase 15A.2 „Command UX Foundation“.

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
- `activity.blackjack`: physische, administrativ konfigurierte Blackjack-Tische, Karten-Domain und GUI
- `activity.location`: immutable Positionen, Bereiche und physische Activity-Venues
- `activity.player`: immutable Activity-Teilnehmer ohne dauerhafte Bukkit-Player-Referenzen
- `chat`: globaler Adventure-Chat mit LuckPerms-Prefix und -Suffix
- `config`: zentraler Zugriff auf die Bukkit-Konfiguration
- `economy`: internes Coin-Wallet, EconomyService und Coin-Commands
- `lobby`: Lobby-Spawn, Teleports und auf die Lobby-Welt begrenzter Schutz
- `lobby.item`: Definition und Erzeugung der drei Lobby-Hotbar-Items
- `lobby.message`: Rendering der konfigurierbaren Join-/Quit-Nachrichten
- `lobby.player`: zentraler Runtime-Zustand `NORMAL`/`BUILD`, Gamemode und Inventory-Ownership
- `lobby.warp`: generische persistente Warps ohne vordefinierte oder reservierte Ziele
- `lobby.experience`: Interaktionen der Lobby-Hotbar, dynamischer Warp Navigator, Settings-Shortcut und weltgebundene Player-Visibility
- `message`: Adventure- und MiniMessage-Ausgabe
- `module`: kleiner Lifecycle-Extension-Point für zukünftige Systeme
- `permission`: lesender Zugriff auf LuckPerms-Gruppen und Meta-Daten
- `player`: Player-Domainmodell, aktiver Cache und Join-/Quit-Lifecycle
- `player.repository`: austauschbare Persistence mit lokaler YAML-Implementierung
- `player.social`: persistenter, UUID-basierter Social-State eines Players
- `player.settings`: persistente Player-Settings und interne Zugriffsschicht für andere Module
- `privatemessage`: sichere, sessionbasierte Nachrichten zwischen Online-Spielern
- `presentation`: Lobby-Sidebar und serverweite Adventure-Tablist
- `reload`: koordinierter zweiphasiger Config-Reload mit Runtime-Rollback
- `settings`: sichere Ingame-Oberfläche für die bereits persistenten Player-Settings
- `social`: Ignore-Service, threadsichere Runtime-Projektion, Lifecycle und Commands
- `utility`: administrative Utility-Einstiegspunkte; aktuell ausschließlich `/build`

Die 14 Module starten in der gerichteten Reihenfolge `Permission → Player → Social → Economy → Lobby → Chat → PrivateMessage → Presentation → Settings → Activity → Utility → Blackjack → Warp → LobbyExperience` und werden beim Shutdown vollständig rückwärts deaktiviert. Dadurch sind Lobby und Activity vor `/build`, Blackjack nach Activity sowie Warp vor LobbyExperience verfügbar. Chat und PrivateMessage nutzen weiterhin den Social-Snapshot und Player-Persistence wird beim Shutdown erst nach allen konsumierenden Modulen gespeichert.

Aktive Spieler werden als `CorePlayer` im Speicher gehalten. Zum Profil gehören die Einstellungen `scoreboard`, `sounds`, `private-messages` und `lobby-players-visible`, die standardmäßig aktiviert sind, das Coin-Wallet und `PlayerSocial`. Andere Module greifen über klar abgegrenzte Services darauf zu. Die lokale Persistence legt pro UUID eine Datei unter `plugins/VapeeCore/players/<uuid>.yml` an. Alte Player-Dateien ohne einzelne Settings oder ohne `settings`, `economy` beziehungsweise `social` werden mit den jeweiligen Default-Werten geladen und beim nächsten regulären Save automatisch erweitert. Bukkit-`Player`-Instanzen werden nicht im Domainmodell gespeichert.

Jedes Player-Profil besitzt außerdem ein Coin-Wallet mit einer nicht negativen ganzzahligen `long`-Balance und dem Defaultwert `0`. Die Coins werden als `economy.coins` in derselben Player-YAML gespeichert; alte Dateien werden beim nächsten regulären Save automatisch ergänzt. Andere Module greifen ausschließlich über den `EconomyService` darauf zu. `/coins` zeigt den eigenen Kontostand, während `/coins get|add|remove|set` ausschließlich online befindliche, geladene Spieler administriert. Es gibt bewusst keine Vault-Anbindung, Offline-Mutationen, weiteren Währungen oder Spieler-zu-Spieler-Transfers.

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
```

Das `SocialModule` wird unmittelbar nach dem `PlayerModule` aktiviert und stellt `/ignore <player>`, `/unignore <player|uuid>` und `/ignorelist` mit der gemeinsamen Permission `vapeecore.social.ignore` bereit. Neu ignoriert werden ausschließlich exakt benannte Online-Spieler; dabei werden weder partielle Namen noch Bukkit-`OfflinePlayer`-Lookups verwendet. Unignore arbeitet ausschließlich gegen die eigene gespeicherte Ignore-Liste und kann bekannte Offline-Spieler über deren VapeeCore-Playerdatei oder als UUID auflösen. Namen dienen nur der Anzeige und Eingabeauflösung, persistente Wahrheit bleiben die UUIDs unter `social.ignored`.

Die Ignore-Beziehung ist einseitig. Wenn A B ignoriert, sieht A keine globalen Chatnachrichten von B; B sieht A weiterhin, solange B A nicht ebenfalls ignoriert. Papers veränderbare `AsyncChatEvent`-Viewer-Menge wird pro Player-Audience gefiltert, während Console und andere Audience-Typen erhalten bleiben. Dafür verwendet `SocialService` eine `ConcurrentHashMap<UUID, Set<UUID>>` mit unveränderlichen Sets. Der Async-Chatpfad liest weder den nicht threadsicheren Player-Cache noch Dateien. Die Player-YAML in `CorePlayer.getSocial()` bleibt alleinige persistente Wahrheit; der Snapshot wird nur nach erfolgreichem Save vollständig ersetzt.

Ignore blockiert außerdem private Nachrichten in beiden Richtungen und gilt automatisch auch für `/reply` beziehungsweise `/r`, weil Replies denselben Sendepfad verwenden. Wer selbst ein Ziel ignoriert, erhält die klare Meldung, dass dieses Ziel ignoriert wird. Ignoriert dagegen der Empfänger den Sender, wird aus Datenschutzgründen dieselbe allgemeine Ablehnung wie bei deaktiviertem PM-Empfang ausgegeben. Ignore verändert `settings.private-messages` nicht.

Phase 12 enthält bewusst kein Friends- oder Party-System, kein Social GUI und keine `social.yml`. Ignore versteckt weder Entities noch Tablist, Scoreboard, Nametags oder Join-/Quit-Meldungen. `SocialModule` ist ein normales `CoreModule` und kein `ReloadParticipant`; Social-Playerdaten werden durch `/core reload` nicht von Platte neu geladen.

Permissions, Gruppen, Primary Groups, Prefixe, Suffixe, Meta-Daten, Contexts und Vererbung werden ausschließlich von LuckPerms verwaltet. VapeeCore liest die bereits von LuckPerms aufgelösten Daten und speichert sie weder im `CorePlayer` noch in den Player-YAML-Dateien. Normale Permission-Checks erfolgen weiterhin über Bukkit/Paper.

Das `LobbyModule` verwendet die separate Datei `plugins/VapeeCore/lobby.yml`. `/setspawn` speichert dort den Lobby-Spawn mit Weltname, Position und Blickrichtung; `/spawn` teleportiert Spieler dorthin. `player.gamemode` bestimmt den normalen Lobby-Gamemode und verwendet bei ungültigen Werten sicher `ADVENTURE`. `LobbyPlayerStateService` besitzt den nicht persistenten Zustand `NORMAL`/`BUILD`, normalisiert Gamemode und Inventory und erzeugt in `NORMAL` die Lobby-Hotbar. `/build` liegt im `UtilityModule`, ist auf die Lobby beschränkt, mit Activities gegenseitig exklusiv und verwendet immer `CREATIVE`. Nur der aktive BUILD-Zustand umgeht Block- und Item-Schutz; die Permission `vapeecore.utility.build` erlaubt ausschließlich das Command. `vapeecore.lobby.build` bleibt nur als deprecated Permission-Parent zur Migration erhalten.

### Activity Foundation

Das nach `SettingsModule` gestartete `ActivityModule` stellt mit dem `ActivityService` vier ausschließlich zur Laufzeit geführte Registries bereit: Activity-Typen, Venues, Sessions und die globale Zuordnung `Player-UUID → Session-UUID`. Direkt beim Enable des ActivityModule bleiben alle Registries leer; das später gestartete BlackjackModule registriert seinen Typ und die aus `blackjack.yml` aktivierten Venues und Sessions. Der frühere, ausschließlich für die Compass-Navigation verwendete `ActivityCatalog` samt `ActivityEntryPoint` wurde in Phase 15A entfernt. Mutierende Service-Operationen sind auf den primären Server-Thread begrenzt. Es gibt weder Reflection-Scans noch statische Registries oder Activity-, Venue- beziehungsweise Session-Persistence und keine neuen Keys in den Player-YAML-Dateien.

Ein `ActivityType` besitzt einen stabilen Key nach `[a-z0-9_-]+`, eine minimale und maximale Teilnehmerzahl und erzeugt eine konkrete `ActivitySession` für eine UUID und ein bereits registriertes `ActivityVenue`. Typen müssen explizit vor ihren Venues registriert werden. Ein Typ kann erst entfernt werden, wenn keine zugehörigen Venues oder Sessions mehr existieren. Ein Venue ist über `activityKey + id` eindeutig, gehört genau einem Typ und kann gleichzeitig höchstens eine Session reservieren. Es kann erst entfernt werden, nachdem diese Session geschlossen wurde.

`ActivityPosition` speichert Weltname, Koordinaten und Blickrichtung als finite Werte und löst eine Bukkit-`Location` nur auf, wenn die Welt bereits geladen ist. `ActivityArea` ist ein normalisierter, achsenparalleler und weltgebundener Quader. `ActivityVenue` verbindet eine solche Area mit einer repräsentativen Anchor-Position; Anchor und Area müssen dieselbe Welt verwenden und der Anchor muss innerhalb der Area liegen. Das Framework lädt oder erzeugt keine Welten automatisch.

`ActivitySession` ist die abstrakte Basis konkreter Activities. Sie speichert Session-UUID, Activity-Key, Venue, Erstellungszeitpunkt, Lifecycle-State und immutable Teilnehmer-Snapshots. `ActivityParticipant` enthält ausschließlich UUID und Beitrittszeitpunkt; dauerhafte Bukkit-`Player`-Referenzen werden nicht gehalten. Ein Spieler kann serverweit höchstens einer Activity-Session angehören. `ActivityService.joinSession` prüft Online-Status, geladenen `CorePlayer`, Membership, Session-State, Kapazität und Venue-Welt. Das Framework teleportiert nicht, ändert keinen GameMode und manipuliert weder Inventar, Rüstung, Hotbar noch Lobby-Items.

Der absichtlich kleine Lifecycle lautet `AVAILABLE → ACTIVE → RESETTING → AVAILABLE`; aus `AVAILABLE`, `ACTIVE` und `RESETTING` ist zusätzlich der terminale Übergang nach `CLOSED` erlaubt. Alle anderen Übergänge werden abgelehnt. Der Service besitzt die State-Transitions; konkrete Sessions können den State nicht frei setzen. Aktivierung verlangt mindestens die konfigurierte Teilnehmerzahl. Verlässt während `ACTIVE` ein Teilnehmer die Session und fällt sie dadurch unter dieses Minimum, führt der Service kontrolliert `ACTIVE → RESETTING → AVAILABLE` aus. Ein normaler Reset entfernt die verbleibenden Teilnehmer nicht.

Konkrete Sessions erhalten nur die kleinen Hooks `onParticipantJoined`, `onParticipantLeft`, `onActivated`, `onReset` und `onClosed`. Über `trackTask` und `untrackTask` gehören Activity-eigene Bukkit-Tasks direkt ihrer Session. Reset und Close canceln alle getrackten Tasks und leeren die Task-Sammlung. Schlägt Aktivierung, Reset oder Task-Cleanup fehl, wird die betroffene Session sicher geschlossen; ein Join-Hook-Fehler rollt Participant und Membership zurück. Leave- und Close-Hooks laufen best-effort, sodass ein fehlerhafter Hook die Bereinigung anderer Teilnehmer, Memberships, Registries und der Venue-Reservierung nicht verhindert.

Der kleine `ActivityListener` behandelt ausschließlich Quit und World Change. Quit entfernt eine vorhandene Membership mit `DISCONNECT`; ein Wechsel aus der Venue-Welt entfernt sie mit `WORLD_CHANGE`. Ein Wechsel innerhalb derselben Welt bleibt unangetastet. Es gibt bewusst keinen globalen `PlayerMoveEvent`-Listener und keinen globalen Activity-Tick-Task. Beim Modul-Shutdown werden alle Sessions über denselben zentralen Safe-Close-Pfad geschlossen, danach Memberships, Venue-Reservierungen, Venues und Typen geleert.

Activity und Game bleiben getrennte Konzepte. Das Activity-Framework besitzt keine Economy-, Social-, Settings- oder Presentation-Abhängigkeit, kein Inventory- oder Player-Snapshot-System, kein universelles Countdown-, Winner-, Team-, Spectator-, Arena-Reset- oder Matchmaking-System. Konkrete Activities injizieren nur ihre tatsächlich benötigten Module. Das matchbasierte Game-Framework folgt separat in Phase 16.

### Blackjack Activity

Das `BlackjackModule` ist die erste konkrete Activity und wird nach Utility sowie vor Warp und LobbyExperience aktiviert. Es nutzt `JavaPlugin`, `ActivityModule`, `MessageService` und eine schmale, injizierte BUILD-Abfrage aus dem Lobby-State. Sein `BlackjackActivityType` verwendet den Key `blackjack`, `minParticipants = 1` und `maxParticipants = 5`. Spieler im BUILD-Modus werden vor jeder Sitzreservierung abgewiesen. Spieler starten Blackjack ausschließlich durch einen Main-Hand-Rechtsklick auf den konfigurierten Interaktionsblock eines physischen Tisches; der Compass und das Warp-System kennen Blackjack nicht.

Eine Solo-/Public-Auswahl gibt es nicht mehr: Ein Teilnehmer spielt automatisch allein, mehrere Teilnehmer spielen gemeinsam gegen denselben Dealer. Ein Spieler kann sofort `Deal` drücken; Queue, Countdown oder zweite erforderliche Person existieren nicht. Solange eine Session `AVAILABLE` ist, werden bis zur tatsächlichen Anzahl konfigurierter Sitze weitere Spieler aufgenommen. Sobald jemand `Deal` drückt und der Tisch `ACTIVE` ist, sind Mid-Round-Joins kontrolliert gesperrt. Die globale Obergrenze bleibt fünf, ein Tisch mit drei Sitzen besitzt praktisch aber Kapazität drei.

Physische Tische werden ausschließlich in `plugins/VapeeCore/blackjack.yml` gespeichert; der Default ist `tables: {}` und es werden keine Beispieltische oder virtuellen `table-1`-Venues erzeugt. Ein persistierter `BlackjackTableDraft` darf während des Setups unvollständig sein. Erst eine vollständige, immutable `BlackjackTableDefinition` mit gemeinsamer Welt, Area (`pos1`/`pos2`), Dealer-Position, Interaktionsblock und ein bis fünf innerhalb der Area liegenden Sitzen wird aktiviert. Jeder gültige, aktivierte Tisch besitzt genau ein `ActivityVenue`, eine langlebige `BlackjackSession` und einen direkten `BlackjackBlockPosition → tableId`-Lookup. Ungültige einzelne Einträge oder nicht geladene Welten werden mit Warnung übersprungen, ohne die Datei zu überschreiben oder andere Tische zu blockieren.

Das Admin-Setup erfolgt mit `vapeecore.blackjack.admin`; `/blackjack help` und `/blackjack setup help` zeigen die verfügbaren Aktionen einzeln und in Workflow-Gruppen. Neue Drafts sind disabled. Positionen und Sitze können nur im disabled Zustand bearbeitet werden; `enable` validiert und aktiviert atomar, `disable` ist nur bei `AVAILABLE` und null Teilnehmern erlaubt, und Löschen setzt ein vorheriges Disable voraus. Änderungen werden sofort per temporärer Datei und Atomic-Move-Fallback gespeichert.

Beim Beitritt wird die niedrigste freie konfigurierte Sitznummer reserviert, die Activity-Membership eingetragen und anschließend ein unsichtbarer, unbeweglicher, nicht persistenter ArmorStand mit den PDC-Keys `blackjack_seat`, `blackjack_table` und `blackjack_seat_number` erzeugt. Der Spieler wird über die offizielle Passenger-API aufgesetzt. Schlägt Join, Entity-Erzeugung oder Mount fehl, werden Membership, Entity und Reservierung vollständig zurückgerollt. Manuelles Absteigen verlässt die Activity; Quit und World Change werden weiterhin zentral vom Activity-Framework behandelt. Programmatische Dismounts besitzen einen Rekursions-Guard. Beim Start werden nur eindeutig PDC-markierte alte Sitz-Entities entfernt, beim Disable/Shutdown alle eigenen Tisch-Sitze bereinigt.

Eine Runde teilt in deterministischer Join-Reihenfolge aus: je eine Karte an alle Teilnehmer, eine an den Dealer, je eine zweite an alle Teilnehmer und die verdeckte Hole Card des Dealers. Unterstützt werden Hit, Stand, Natural Blackjack, Bust, Win, Loss und Push. Asse zählen automatisch als 11 oder 1. Der Dealer zieht unter 17 und steht auch auf Soft 17. Split, Double Down, Insurance, Surrender und Side Bets sind nicht enthalten. Jeder Zug besitzt einen session-eigenen Timeout von 20 Sekunden; bei Ablauf wird automatisch gestanden. Runden- und Zug-Generationen verhindern, dass alte Tasks eine spätere Aktion oder Runde beeinflussen.

Nach dem letzten Spielerzug wird die Dealer-Karte aufgedeckt, das Ergebnis etwa drei Sekunden angezeigt und die Session über `ACTIVE → RESETTING → AVAILABLE` zurückgesetzt. Teilnehmer bleiben für eine direkte Revanche am Tisch. Verlassen oder Disconnect des aktuellen Spielers schaltet ohne Timeout-Verzögerung weiter; verlässt der letzte Rundenteilnehmer den Tisch, wird die Runde sofort sauber zurückgesetzt. Alle GUIs verwenden ownergebundene, inventaridentische Holder, sichere Slot-Zuordnungen und blockieren Click-, Shift-, Number-Key-, Hotbar-, Double-Click- und Drag-Itemdiebstahl. Das Schließen eines Inventars verlässt die Activity nicht.

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

Das `ChatModule` formatiert den globalen Chat über Papers `AsyncChatEvent` und einen viewer-unabhängigen `ChatRenderer`. Das Format liegt in `plugins/VapeeCore/chat.yml`; LuckPerms-Prefix und -Suffix können dort als `legacy-ampersand`, `mini-message` oder `plain` interpretiert werden. Das Serverformat ist MiniMessage, die originale Playernachricht wird jedoch als Adventure Component eingesetzt und niemals als MiniMessage ausgewertet. Chat-Channels sind nicht Bestandteil dieser Phase.

Das `PrivateMessageModule` stellt `/msg <player> <message>` sowie `/reply <message>` mit dem Alias `/r` für ausschließlich online befindliche Spieler bereit. Darstellung und globaler Aktivierungsstatus liegen in `plugins/VapeeCore/private-messages.yml`. Spielertext wird als sichere Adventure Component eingesetzt und weder als MiniMessage noch als Legacy-Farbcode ausgewertet. `PlayerSettings.privateMessagesEnabled` bedeutet ausschließlich „private Nachrichten empfangen“: Ein Spieler mit deaktiviertem Empfang darf weiterhin selbst schreiben. Der letzte erfolgreiche Gesprächspartner wird nur für die aktuelle Session im Speicher gehalten; ein Quit entfernt alle zugehörigen Reply-Verweise. Es gibt bewusst keine Offline-Nachrichten, Mailbox oder SocialSpy.

Das `PresentationModule` aktualisiert über einen gemeinsamen synchronen Task standardmäßig einmal pro Sekunde die persönliche Sidebar und Tablist. Die Templates liegen in `plugins/VapeeCore/presentation.yml` und verwenden Adventure/MiniMessage mit sicheren Component-Platzhaltern für Servername, Displayname, LuckPerms-Prefix, -Suffix und Primary Group, Coins sowie Onlinezahlen. Die Sidebar nutzt Papers moderne Component- und NumberFormat-Scoreboard-APIs, respektiert `PlayerSettings.scoreboardEnabled` und ist standardmäßig ausschließlich in der konfigurierten Lobby-Welt aktiv. Die Tablist ist serverweit; Rank-Sortierung und Minigame-Scoreboards sind bewusst nicht enthalten.

Das `SettingsModule` stellt `/settings` für Spieler mit `vapeecore.settings.use` bereit. Das unveränderte 27-Slot-Inventar schaltet Scoreboard, VapeeCore-eigene UI-Feedback-Sounds und den Empfang privater Nachrichten um. Visibility wird bewusst nur über das Lobby-Hotbar-Item bedient, damit keine zyklische Abhängigkeit entsteht. Die Werte werden unmittelbar über den bestehenden `PlayerSettingsService` in derselben Player-YAML gespeichert; es gibt weder eine zweite Settings-Persistence noch eine `settings.yml`. Ein Scoreboard-Toggle lässt die Presentation sofort den gültigen Scope neu bewerten. Das PM-Setting wirkt ebenfalls sofort und steuert ausschließlich den Empfang—das eigene Senden bleibt möglich. `SettingsModule` ist kein `ReloadParticipant`.

`/core reload` liest `config.yml`, `lobby.yml`, `chat.yml`, `private-messages.yml` und `presentation.yml` in einer gemeinsamen zweiphasigen Transaktion neu. Zuerst werden alle fünf Dateien in dieser Reihenfolge vollständig vorbereitet und validiert; erst danach werden ihre Runtime-Zustände in derselben Reihenfolge angewendet. Ein Prepare-Fehler verändert daher keinen aktiven Zustand. Bei einem unerwarteten Apply-Fehler werden bereits übernommene Zustände rückwärts zurückgerollt. Message-Prefix, Servername, Debug-Modus, Lobby-Regeln, Chat- und PM-Format sowie Scoreboard- und Tablist-Templates werden ohne Serverneustart aktiv; auch der Presentation-Task passt sich sicher an `enabled` und `update-interval-ticks` an. Ein PM-Reload behält bestehende Conversation-Beziehungen. Der Reload schreibt keine Configdateien, lädt keine Playerdaten und verändert weder Social-State, Economy noch LuckPerms.

Die 14 Module starten exakt in der Reihenfolge Permission → Player → Social → Economy → Lobby → Chat → PrivateMessage → Presentation → Settings → Activity → Utility → Blackjack → Warp → LobbyExperience und stoppen in umgekehrter Reihenfolge. `/core reload` besitzt weiterhin genau fünf Teilnehmer (`config.yml`, `lobby.yml`, `chat.yml`, `private-messages.yml`, `presentation.yml`); Utility ist kein Reload-Teilnehmer, Blackjack und Warp werden bewusst direkt über ihre Admin-Commands gepflegt.

Weitere Activities, Voice-System, Community-Funktionen und Minigames werden in späteren Phasen als klar abgegrenzte interne `CoreModule` innerhalb derselben VapeeCore-JAR ergänzt. Phase 15A liefert physisches Blackjack und generische Warps, baut aber weiterhin weder ein Game- noch ein Minigame-Framework auf.
