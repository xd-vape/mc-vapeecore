# VapeeCore

VapeeCore ist das zentrale Basis-Plugin für einen Minecraft-Community-Server. Das Projekt ist als modularer Monolith aufgebaut und stellt aktuell eine zentrale Konfiguration, MiniMessage-Nachrichten, interne CoreModule, eine lokale Player Foundation, eine Coin-Economy und eine lesende LuckPerms-Integration bereit.

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
- `config`: zentraler Zugriff auf die Bukkit-Konfiguration
- `economy`: internes Coin-Wallet, EconomyService und Coin-Commands
- `lobby`: Lobby-Spawn, Teleports und auf die Lobby-Welt begrenzter Schutz
- `message`: Adventure- und MiniMessage-Ausgabe
- `module`: kleiner Lifecycle-Extension-Point für zukünftige Systeme
- `permission`: lesender Zugriff auf LuckPerms-Gruppen und Meta-Daten
- `player`: Player-Domainmodell, aktiver Cache und Join-/Quit-Lifecycle
- `player.repository`: austauschbare Persistence mit lokaler YAML-Implementierung
- `player.settings`: persistente Player-Settings und interne Zugriffsschicht für andere Module

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

Das `LobbyModule` verwendet die separate Datei `plugins/VapeeCore/lobby.yml`. `/setspawn` speichert dort den Lobby-Spawn mit Weltname, Position und Blickrichtung; `/spawn` teleportiert Spieler dorthin. Join-Teleport, Lobby-Respawn, Void Rescue sowie Damage-, Hunger-, Block- und Item-Schutz gelten ausschließlich in der Welt aus `spawn.world`. Spieler mit `vapeecore.lobby.build` dürfen dort bauen sowie Items droppen und aufnehmen. `/core reload` lädt weiterhin nur `config.yml` neu.

Chat, Scoreboard, Tablist, Voice-System, Community-Funktionen und Minigames werden bei Bedarf als klar abgegrenzte interne `CoreModule` innerhalb derselben VapeeCore-JAR ergänzt.
