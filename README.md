# VapeeCore

VapeeCore ist das zentrale Basis-Plugin für einen Minecraft-Community-Server. Das Projekt ist als modularer Monolith aufgebaut und stellt aktuell eine zentrale Konfiguration, MiniMessage-Nachrichten, interne CoreModule, eine lokale Player Foundation, eine Coin-Economy, globalen Chat, private Nachrichten, Player-Presentation und eine lesende LuckPerms-Integration bereit.

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
- `chat`: globaler Adventure-Chat mit LuckPerms-Prefix und -Suffix
- `config`: zentraler Zugriff auf die Bukkit-Konfiguration
- `economy`: internes Coin-Wallet, EconomyService und Coin-Commands
- `lobby`: Lobby-Spawn, Teleports und auf die Lobby-Welt begrenzter Schutz
- `message`: Adventure- und MiniMessage-Ausgabe
- `module`: kleiner Lifecycle-Extension-Point für zukünftige Systeme
- `permission`: lesender Zugriff auf LuckPerms-Gruppen und Meta-Daten
- `player`: Player-Domainmodell, aktiver Cache und Join-/Quit-Lifecycle
- `player.repository`: austauschbare Persistence mit lokaler YAML-Implementierung
- `player.settings`: persistente Player-Settings und interne Zugriffsschicht für andere Module
- `privatemessage`: sichere, sessionbasierte Nachrichten zwischen Online-Spielern
- `presentation`: Lobby-Sidebar und serverweite Adventure-Tablist
- `reload`: koordinierter zweiphasiger Config-Reload mit Runtime-Rollback

Aktive Spieler werden als `CorePlayer` im Speicher gehalten. Zum Profil gehören die Einstellungen `scoreboard`, `sounds` und `private-messages`, die standardmäßig aktiviert sind. Andere Module greifen über den `PlayerSettingsService` darauf zu. Die lokale Persistence legt pro UUID eine Datei unter `plugins/VapeeCore/players/<uuid>.yml` an und speichert die Settings dort im Abschnitt `settings`. Alte Player-Dateien ohne diesen Abschnitt werden mit den Default-Werten geladen und beim nächsten regulären Save automatisch erweitert. Bukkit-`Player`-Instanzen werden nicht im Domainmodell gespeichert.

Jedes Player-Profil besitzt außerdem ein Coin-Wallet mit einer nicht negativen ganzzahligen `long`-Balance und dem Defaultwert `0`. Die Coins werden als `economy.coins` in derselben Player-YAML gespeichert; alte Dateien werden beim nächsten regulären Save automatisch ergänzt. Andere Module greifen ausschließlich über den `EconomyService` darauf zu. `/coins` zeigt den eigenen Kontostand, während `/coins get|add|remove|set` ausschließlich online befindliche, geladene Spieler administriert. Es gibt bewusst keine Vault-Anbindung, Offline-Mutationen, weiteren Währungen oder Spieler-zu-Spieler-Transfers.

```yaml
name: Vapee
first-join: 123456
last-join: 123456
settings:
  scoreboard: true
  sounds: true
  private-messages: true
economy:
  coins: 2500
```

Permissions, Gruppen, Primary Groups, Prefixe, Suffixe, Meta-Daten, Contexts und Vererbung werden ausschließlich von LuckPerms verwaltet. VapeeCore liest die bereits von LuckPerms aufgelösten Daten und speichert sie weder im `CorePlayer` noch in den Player-YAML-Dateien. Normale Permission-Checks erfolgen weiterhin über Bukkit/Paper.

Das `LobbyModule` verwendet die separate Datei `plugins/VapeeCore/lobby.yml`. `/setspawn` speichert dort den Lobby-Spawn mit Weltname, Position und Blickrichtung; `/spawn` teleportiert Spieler dorthin. Join-Teleport, Lobby-Respawn, Void Rescue sowie Damage-, Hunger-, Block- und Item-Schutz gelten ausschließlich in der Welt aus `spawn.world`. Spieler mit `vapeecore.lobby.build` dürfen dort bauen sowie Items droppen und aufnehmen.

Das `ChatModule` formatiert den globalen Chat über Papers `AsyncChatEvent` und einen viewer-unabhängigen `ChatRenderer`. Das Format liegt in `plugins/VapeeCore/chat.yml`; LuckPerms-Prefix und -Suffix können dort als `legacy-ampersand`, `mini-message` oder `plain` interpretiert werden. Das Serverformat ist MiniMessage, die originale Playernachricht wird jedoch als Adventure Component eingesetzt und niemals als MiniMessage ausgewertet. Chat-Channels sind nicht Bestandteil dieser Phase.

Das `PrivateMessageModule` stellt `/msg <player> <message>` sowie `/reply <message>` mit dem Alias `/r` für ausschließlich online befindliche Spieler bereit. Darstellung und globaler Aktivierungsstatus liegen in `plugins/VapeeCore/private-messages.yml`. Spielertext wird als sichere Adventure Component eingesetzt und weder als MiniMessage noch als Legacy-Farbcode ausgewertet. `PlayerSettings.privateMessagesEnabled` bedeutet ausschließlich „private Nachrichten empfangen“: Ein Spieler mit deaktiviertem Empfang darf weiterhin selbst schreiben. Der letzte erfolgreiche Gesprächspartner wird nur für die aktuelle Session im Speicher gehalten; ein Quit entfernt alle zugehörigen Reply-Verweise. Es gibt bewusst keine Offline-Nachrichten, Mailbox, Ignore-Funktion oder SocialSpy.

Das `PresentationModule` aktualisiert über einen gemeinsamen synchronen Task standardmäßig einmal pro Sekunde die persönliche Sidebar und Tablist. Die Templates liegen in `plugins/VapeeCore/presentation.yml` und verwenden Adventure/MiniMessage mit sicheren Component-Platzhaltern für Servername, Displayname, LuckPerms-Prefix, -Suffix und Primary Group, Coins sowie Onlinezahlen. Die Sidebar nutzt Papers moderne Component- und NumberFormat-Scoreboard-APIs, respektiert `PlayerSettings.scoreboardEnabled` und ist standardmäßig ausschließlich in der konfigurierten Lobby-Welt aktiv. Die Tablist ist serverweit; Rank-Sortierung und Minigame-Scoreboards sind bewusst nicht enthalten.

`/core reload` liest `config.yml`, `lobby.yml`, `chat.yml`, `private-messages.yml` und `presentation.yml` in einer gemeinsamen zweiphasigen Transaktion neu. Zuerst werden alle fünf Dateien in dieser Reihenfolge vollständig vorbereitet und validiert; erst danach werden ihre Runtime-Zustände in derselben Reihenfolge angewendet. Ein Prepare-Fehler verändert daher keinen aktiven Zustand. Bei einem unerwarteten Apply-Fehler werden bereits übernommene Zustände rückwärts zurückgerollt. Message-Prefix, Servername, Debug-Modus, Lobby-Regeln, Chat- und PM-Format sowie Scoreboard- und Tablist-Templates werden ohne Serverneustart aktiv; auch der Presentation-Task passt sich sicher an `enabled` und `update-interval-ticks` an. Ein PM-Reload behält bestehende Conversation-Beziehungen. Der Reload schreibt keine Configdateien, lädt keine Playerdaten und verändert weder Economy noch LuckPerms.

Voice-System, Community-Funktionen und Minigames werden bei Bedarf als klar abgegrenzte interne `CoreModule` innerhalb derselben VapeeCore-JAR ergänzt.
