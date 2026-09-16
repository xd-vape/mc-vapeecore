# VapeeCore

VapeeCore ist das zentrale Basis-Plugin für einen Minecraft-Community-Server. Das Projekt ist als modularer Monolith aufgebaut und stellt aktuell eine zentrale Konfiguration, MiniMessage-Nachrichten, interne CoreModule, eine lokale Player Foundation, eine persistente Social-/Ignore-Grundlage, eine Coin-Economy, globalen Chat, private Nachrichten, Player-Presentation, eine Ingame-Settings-Oberfläche, ein leichtgewichtiges Activity-Fundament, Blackjack als erste konkrete Activity, eine spielerfreundliche Lobby Experience und eine lesende LuckPerms-Integration bereit.

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

## Architektur

- `command`: Commands und deren Subcommands
- `activity`: runtimebasiertes Framework für kleine, direkt erreichbare Server-/Lobby-Aktivitäten
- `activity.blackjack`: erste konkrete Activity mit Solo-/Public-Tischen, Karten-Domain und GUI
- `activity.location`: immutable Positionen, Bereiche und physische Activity-Venues
- `activity.navigation`: generischer Runtime-Katalog für benutzerseitige Activity-Einstiegspunkte
- `activity.player`: immutable Activity-Teilnehmer ohne dauerhafte Bukkit-Player-Referenzen
- `chat`: globaler Adventure-Chat mit LuckPerms-Prefix und -Suffix
- `config`: zentraler Zugriff auf die Bukkit-Konfiguration
- `economy`: internes Coin-Wallet, EconomyService und Coin-Commands
- `lobby`: Lobby-Spawn, Teleports und auf die Lobby-Welt begrenzter Schutz
- `lobby.experience`: Lobby-Hotbar, Navigator, Settings-Shortcut, Join-/Quit-UX und weltgebundene Player-Visibility
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

Die zwölf Module starten in der gerichteten Reihenfolge `Permission → Player → Social → Economy → Lobby → Chat → PrivateMessage → Presentation → Settings → Activity → Blackjack → LobbyExperience` und werden beim Shutdown vollständig rückwärts deaktiviert. Dadurch ist Blackjack bereits im Activity-Katalog registriert, bevor LobbyExperience seine Navigation initialisiert. LobbyExperience hängt ausschließlich vom generischen ActivityModule ab und kennt das BlackjackModule nicht. Chat und PrivateMessage nutzen weiterhin den Social-Snapshot und Player-Persistence wird beim Shutdown erst nach allen konsumierenden Modulen gespeichert.

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

Das `LobbyModule` verwendet die separate Datei `plugins/VapeeCore/lobby.yml`. `/setspawn` speichert dort den Lobby-Spawn mit Weltname, Position und Blickrichtung; `/spawn` teleportiert Spieler dorthin. Join-Teleport, Lobby-Respawn, Void Rescue sowie Damage-, Hunger-, Block- und Item-Schutz gelten ausschließlich in der Welt aus `spawn.world`. Spieler mit `vapeecore.lobby.build` dürfen dort bauen sowie Items droppen und aufnehmen.

### Activity Foundation

Das nach `SettingsModule` gestartete `ActivityModule` stellt mit dem `ActivityService` vier ausschließlich zur Laufzeit geführte Registries bereit: Activity-Typen, Venues, Sessions und die globale Zuordnung `Player-UUID → Session-UUID`. Zusätzlich führt der `ActivityCatalog` explizit registrierte, benutzerseitige `ActivityEntryPoint`s. Der Katalog akzeptiert einen Einstiegspunkt erst, nachdem sein Activity-Typ registriert wurde, liefert immutable Snapshots und wird beim Shutdown geleert. Direkt beim Enable des ActivityModule bleiben alle Registries weiterhin leer; das danach gestartete BlackjackModule registriert seinen Typ und Katalogeintrag. Mutierende Service-Operationen sind auf den primären Server-Thread begrenzt. Es gibt weder Reflection-Scans noch statische Registries oder Activity-, Venue- beziehungsweise Session-Persistence und keine neuen Keys in den Player-YAML-Dateien.

Ein `ActivityType` besitzt einen stabilen Key nach `[a-z0-9_-]+`, eine minimale und maximale Teilnehmerzahl und erzeugt eine konkrete `ActivitySession` für eine UUID und ein bereits registriertes `ActivityVenue`. Typen müssen explizit vor ihren Venues registriert werden. Ein Typ kann erst entfernt werden, wenn keine zugehörigen Venues oder Sessions mehr existieren. Ein Venue ist über `activityKey + id` eindeutig, gehört genau einem Typ und kann gleichzeitig höchstens eine Session reservieren. Es kann erst entfernt werden, nachdem diese Session geschlossen wurde.

`ActivityPosition` speichert Weltname, Koordinaten und Blickrichtung als finite Werte und löst eine Bukkit-`Location` nur auf, wenn die Welt bereits geladen ist. `ActivityArea` ist ein normalisierter, achsenparalleler und weltgebundener Quader. `ActivityVenue` verbindet eine solche Area mit einer repräsentativen Anchor-Position; Anchor und Area müssen dieselbe Welt verwenden und der Anchor muss innerhalb der Area liegen. Das Framework lädt oder erzeugt keine Welten automatisch.

`ActivitySession` ist die abstrakte Basis konkreter Activities. Sie speichert Session-UUID, Activity-Key, Venue, Erstellungszeitpunkt, Lifecycle-State und immutable Teilnehmer-Snapshots. `ActivityParticipant` enthält ausschließlich UUID und Beitrittszeitpunkt; dauerhafte Bukkit-`Player`-Referenzen werden nicht gehalten. Ein Spieler kann serverweit höchstens einer Activity-Session angehören. `ActivityService.joinSession` prüft Online-Status, geladenen `CorePlayer`, Membership, Session-State, Kapazität und Venue-Welt. Das Framework teleportiert nicht, ändert keinen GameMode und manipuliert weder Inventar, Rüstung, Hotbar noch Lobby-Items.

Der absichtlich kleine Lifecycle lautet `AVAILABLE → ACTIVE → RESETTING → AVAILABLE`; aus `AVAILABLE`, `ACTIVE` und `RESETTING` ist zusätzlich der terminale Übergang nach `CLOSED` erlaubt. Alle anderen Übergänge werden abgelehnt. Der Service besitzt die State-Transitions; konkrete Sessions können den State nicht frei setzen. Aktivierung verlangt mindestens die konfigurierte Teilnehmerzahl. Verlässt während `ACTIVE` ein Teilnehmer die Session und fällt sie dadurch unter dieses Minimum, führt der Service kontrolliert `ACTIVE → RESETTING → AVAILABLE` aus. Ein normaler Reset entfernt die verbleibenden Teilnehmer nicht.

Konkrete Sessions erhalten nur die kleinen Hooks `onParticipantJoined`, `onParticipantLeft`, `onActivated`, `onReset` und `onClosed`. Über `trackTask` und `untrackTask` gehören Activity-eigene Bukkit-Tasks direkt ihrer Session. Reset und Close canceln alle getrackten Tasks und leeren die Task-Sammlung. Schlägt Aktivierung, Reset oder Task-Cleanup fehl, wird die betroffene Session sicher geschlossen; ein Join-Hook-Fehler rollt Participant und Membership zurück. Leave- und Close-Hooks laufen best-effort, sodass ein fehlerhafter Hook die Bereinigung anderer Teilnehmer, Memberships, Registries und der Venue-Reservierung nicht verhindert.

Der kleine `ActivityListener` behandelt ausschließlich Quit und World Change. Quit entfernt eine vorhandene Membership mit `DISCONNECT`; ein Wechsel aus der Venue-Welt entfernt sie mit `WORLD_CHANGE`. Ein Wechsel innerhalb derselben Welt bleibt unangetastet. Es gibt bewusst keinen globalen `PlayerMoveEvent`-Listener und keinen globalen Activity-Tick-Task. Beim Modul-Shutdown werden alle Sessions über denselben zentralen Safe-Close-Pfad geschlossen, danach Memberships, Venue-Reservierungen, Venues und Typen geleert.

Activity und Game bleiben getrennte Konzepte. Das Activity-Framework besitzt keine Economy-, Social-, Settings- oder Presentation-Abhängigkeit, kein Inventory- oder Player-Snapshot-System, kein universelles Countdown-, Winner-, Team-, Spectator-, Arena-Reset- oder Matchmaking-System. Konkrete Activities injizieren nur ihre tatsächlich benötigten Module. Das matchbasierte Game-Framework folgt separat in Phase 16.

### Blackjack Activity

Das `BlackjackModule` ist die erste konkrete Activity und wird zwischen Activity und LobbyExperience aktiviert. Sein `BlackjackActivityType` verwendet den Key `blackjack`, `minParticipants = 1` und `maxParticipants = 5`. Die komplette Spieler-UX läuft ohne neuen Command über `Navigator → Activities → Blackjack`. Das generische 27-Slot-Activities-Menü liest ausschließlich den Activity-Katalog; weder Navigator noch LobbyExperience enthalten einen Blackjack-Sonderfall.

Blackjack bietet `Solo Play` und `Public Table`. Solo reserviert sofort einen privaten Tisch für genau einen Spieler. Public versucht zuerst, einem `AVAILABLE`-Tisch in der aktuellen Lobby-Welt beizutreten, und erzeugt andernfalls automatisch einen neuen. Ein Public-Tisch darf bereits mit einem Spieler sofort starten. Sobald jemand `Deal` drückt, wechselt dieser Tisch nach `ACTIVE` und nimmt keine weiteren Spieler auf; spätere Spieler erhalten ohne Queue, Countdown oder Mindestgruppe einen anderen verfügbaren oder neuen Tisch.

Die Tische sind virtuelle `ActivityVenue`s mit Runtime-IDs `table-1`, `table-2`, … und einem kleinen Bereich um den jeweils aktuellen Lobby-Spawn. Sie werden erst beim ersten Launch erzeugt und teleportieren niemanden. Fehlt der Lobby-Spawn oder ist seine Welt nicht geladen, bleibt der Serverstart erfolgreich und der Spieler erhält eine kontrollierte Unavailable-Meldung. Ein späteres `/setspawn` reicht aus, damit der nächste Launch funktioniert. Leere `AVAILABLE`-Sessions werden `UNCLAIMED` und können zwischen Solo und Public wiederverwendet werden; leere Tische aus einer früheren Lobby-Welt werden beim nächsten Launch sicher entfernt.

Eine Runde teilt in deterministischer Join-Reihenfolge aus: je eine Karte an alle Teilnehmer, eine an den Dealer, je eine zweite an alle Teilnehmer und die verdeckte Hole Card des Dealers. Unterstützt werden Hit, Stand, Natural Blackjack, Bust, Win, Loss und Push. Asse zählen automatisch als 11 oder 1. Der Dealer zieht unter 17 und steht auch auf Soft 17. Split, Double Down, Insurance, Surrender und Side Bets sind nicht enthalten. Jeder Zug besitzt einen session-eigenen Timeout von 20 Sekunden; bei Ablauf wird automatisch gestanden. Runden- und Zug-Generationen verhindern, dass alte Tasks eine spätere Aktion oder Runde beeinflussen.

Nach dem letzten Spielerzug wird die Dealer-Karte aufgedeckt, das Ergebnis etwa drei Sekunden angezeigt und die Session über `ACTIVE → RESETTING → AVAILABLE` zurückgesetzt. Teilnehmer bleiben für eine direkte Revanche am Tisch. Verlassen oder Disconnect des aktuellen Spielers schaltet ohne Timeout-Verzögerung weiter; verlässt der letzte Rundenteilnehmer den Tisch, wird die Runde sofort sauber zurückgesetzt. Alle GUIs verwenden ownergebundene, inventaridentische Holder, sichere Slot-Zuordnungen und blockieren Click-, Shift-, Number-Key-, Hotbar-, Double-Click- und Drag-Itemdiebstahl. Das Schließen eines Inventars verlässt die Activity nicht.

Phase 15 ist ausdrücklich `Free Play`: Blackjack importiert weder `EconomyModule` noch `EconomyService`, verändert keine Coins und besitzt keine Einsätze, Payouts oder Escrow-Logik. Es gibt außerdem kein `blackjack.yml`, keine Player-Statistiken oder Round-Persistence, keine neue Permission, keinen Blackjack-Command und kein Game-/Minigame-Framework. Echte Coin-Wetten folgen erst mit einer transaktionalen Escrow- und Crash-Recovery-Strategie, damit ein Serverausfall keinen Einsatz verlieren oder verdoppeln kann.

Das nach `ActivityModule` gestartete `LobbyExperienceModule` ergänzt diese technische Lobby um drei PDC-markierte Hotbar-Items: Navigator in Slot 0 (`COMPASS`), Player-Visibility in Slot 4 (`LIME_DYE` oder `GRAY_DYE`) und Settings in Slot 8 (`COMPARATOR`). Die Items werden nur in der konfigurierten Lobby-Welt gesetzt. Vorhandene VapeeCore-Lobby-Items werden vorher dedupliziert; normale Items in reservierten Slots werden ausschließlich in einen freien, nicht reservierten Storage-Slot verschoben. Ist kein solcher Slot verfügbar, bleibt das normale Item unangetastet und das betreffende Lobby-Item wird nicht erzwungen. Es gibt ausdrücklich keinen Inventory-Wipe. Drop, Offhand-Swap, Click-, Shift-, Number-Key-, Double-Click- und Drag-Manipulationen der PDC-markierten Items werden verhindert, ohne normale Items pauschal zu sperren.

Der Navigator ist ein geschütztes 27-Slot-Inventar mit eigenem, ownergebundenem `NavigatorInventoryHolder`; Titel- oder Material-Erkennung wird nicht verwendet. Slot 11 teleportiert über den bestehenden `LobbyService` zum Lobby-Spawn, Slot 13 öffnet das generische Activities-Menü, Slot 15 zeigt weiterhin die kontrollierte Coming-Soon-Meldung für Minigames und Slot 22 schließt das Menü. Das Settings-Hotbar-Item öffnet direkt das vorhandene Phase-11-`SettingsMenu`; `/settings` und alle übrigen bestehenden Commands bleiben unverändert verfügbar. Ein Game-System oder eine Proxy-Integration wird nicht aufgebaut.

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

Bei `enabled: false` wird die jeweilige Nachricht vollständig unterdrückt; es gibt keinen Vanilla-Fallback. `<name>` wird als Adventure Component eingesetzt und nicht in einen MiniMessage-String eingebaut. Fehlende Keys verwenden interne Defaults. Ungültige Templates erzeugen eine Warnung und verwenden einen sicheren internen Default, ohne `lobby.yml` zu verändern. Da `LobbyModule` weiterhin alleiniger Reload-Teilnehmer für `lobby.yml` ist und LobbyExperience dessen aktuellen State liest, gelten Änderungen nach `/core reload` ohne einen sechsten Reload-Teilnehmer.

Das `ChatModule` formatiert den globalen Chat über Papers `AsyncChatEvent` und einen viewer-unabhängigen `ChatRenderer`. Das Format liegt in `plugins/VapeeCore/chat.yml`; LuckPerms-Prefix und -Suffix können dort als `legacy-ampersand`, `mini-message` oder `plain` interpretiert werden. Das Serverformat ist MiniMessage, die originale Playernachricht wird jedoch als Adventure Component eingesetzt und niemals als MiniMessage ausgewertet. Chat-Channels sind nicht Bestandteil dieser Phase.

Das `PrivateMessageModule` stellt `/msg <player> <message>` sowie `/reply <message>` mit dem Alias `/r` für ausschließlich online befindliche Spieler bereit. Darstellung und globaler Aktivierungsstatus liegen in `plugins/VapeeCore/private-messages.yml`. Spielertext wird als sichere Adventure Component eingesetzt und weder als MiniMessage noch als Legacy-Farbcode ausgewertet. `PlayerSettings.privateMessagesEnabled` bedeutet ausschließlich „private Nachrichten empfangen“: Ein Spieler mit deaktiviertem Empfang darf weiterhin selbst schreiben. Der letzte erfolgreiche Gesprächspartner wird nur für die aktuelle Session im Speicher gehalten; ein Quit entfernt alle zugehörigen Reply-Verweise. Es gibt bewusst keine Offline-Nachrichten, Mailbox oder SocialSpy.

Das `PresentationModule` aktualisiert über einen gemeinsamen synchronen Task standardmäßig einmal pro Sekunde die persönliche Sidebar und Tablist. Die Templates liegen in `plugins/VapeeCore/presentation.yml` und verwenden Adventure/MiniMessage mit sicheren Component-Platzhaltern für Servername, Displayname, LuckPerms-Prefix, -Suffix und Primary Group, Coins sowie Onlinezahlen. Die Sidebar nutzt Papers moderne Component- und NumberFormat-Scoreboard-APIs, respektiert `PlayerSettings.scoreboardEnabled` und ist standardmäßig ausschließlich in der konfigurierten Lobby-Welt aktiv. Die Tablist ist serverweit; Rank-Sortierung und Minigame-Scoreboards sind bewusst nicht enthalten.

Das `SettingsModule` stellt `/settings` für Spieler mit `vapeecore.settings.use` bereit. Das unveränderte 27-Slot-Inventar schaltet Scoreboard, VapeeCore-eigene UI-Feedback-Sounds und den Empfang privater Nachrichten um. Visibility wird bewusst nur über das Lobby-Hotbar-Item bedient, damit keine zyklische Abhängigkeit entsteht. Die Werte werden unmittelbar über den bestehenden `PlayerSettingsService` in derselben Player-YAML gespeichert; es gibt weder eine zweite Settings-Persistence noch eine `settings.yml`. Ein Scoreboard-Toggle lässt die Presentation sofort den gültigen Scope neu bewerten. Das PM-Setting wirkt ebenfalls sofort und steuert ausschließlich den Empfang—das eigene Senden bleibt möglich. `SettingsModule` ist kein `ReloadParticipant`.

`/core reload` liest `config.yml`, `lobby.yml`, `chat.yml`, `private-messages.yml` und `presentation.yml` in einer gemeinsamen zweiphasigen Transaktion neu. Zuerst werden alle fünf Dateien in dieser Reihenfolge vollständig vorbereitet und validiert; erst danach werden ihre Runtime-Zustände in derselben Reihenfolge angewendet. Ein Prepare-Fehler verändert daher keinen aktiven Zustand. Bei einem unerwarteten Apply-Fehler werden bereits übernommene Zustände rückwärts zurückgerollt. Message-Prefix, Servername, Debug-Modus, Lobby-Regeln, Chat- und PM-Format sowie Scoreboard- und Tablist-Templates werden ohne Serverneustart aktiv; auch der Presentation-Task passt sich sicher an `enabled` und `update-interval-ticks` an. Ein PM-Reload behält bestehende Conversation-Beziehungen. Der Reload schreibt keine Configdateien, lädt keine Playerdaten und verändert weder Social-State, Economy noch LuckPerms.

Weitere Activities, Voice-System, Community-Funktionen und Minigames werden in späteren Phasen als klar abgegrenzte interne `CoreModule` innerhalb derselben VapeeCore-JAR ergänzt. Phase 15 liefert Blackjack als erste konkrete Activity, baut aber weiterhin weder ein Game- noch ein Minigame-Framework auf.
