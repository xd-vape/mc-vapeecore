# VapeeCore

VapeeCore ist ein modular aufgebautes Minecraft-Plugin für Paper. Es verbindet Lobby, soziale Funktionen, Spielerprofile, Moderation, Darstellung und gemeinsame Activities in einer JAR. LuckPerms liefert Gruppen, Ranks und Berechtigungen.

## Funktionen

- **Lobby:** Spawn, konfigurierbare Teleports und Schutzregeln, NORMAL-/BUILD-Verhalten, Warp-Navigator, Sichtbarkeitsfilter und anpassbare Hotbar-Items.
- **Spieler:** bekannte Namen/UUIDs, Online-/Offline-Profile, gespeicherte Einstellungen, Ignore-Liste und Coins.
- **Community:** Freundschaften und Anfragen, gefilterte Friend-Presence-Hinweise, Clans mit Einladungen und Mitgliederverwaltung, private Nachrichten und Reply.
- **Moderation und Utility:** Mutes, Bans, Warnungen und Historie; berechtigungsgesteuerte Bewegungs-, Teleport- und Spielerwerkzeuge.
- **Darstellung:** formatierbarer Chat, Sidebar und Tablist; Rankfarben aus LuckPerms und optionale Clan-Tags in Tablist/Nametags. Die Nametag-Farbe verwendet die 16 von Minecraft unterstützten Teamfarben.
- **Rewards und Quests:** Gameplay-Reward-API, kumulative Playtime-Rewards, Quest-Fortschritt und konfigurierbare Daily-Auswahl. Daily Quests sind im Default deaktiviert und besitzen keinen vorinstallierten Quest-Katalog.
- **Activities:** gemeinsame Session-/Teilnehmerverwaltung, physische Blackjack-Tische, Sitze und World Displays. Blackjack besitzt keine Coin-Einsätze.

`/core help` zeigt die verfügbaren Befehle. Feature-Einstiege sind beispielsweise `/spawn`, `/settings`, `/friend`, `/clan`, `/profile`, `/quests`, `/msg`, `/rank` und `/ranks`. Vollständige Berechtigungen und öffentliche Command-Verträge stehen in [PERMISSIONS.md](docs/PERMISSIONS.md).

## Voraussetzungen

- Paper **1.21.11**
- Java **21**; zum Entwickeln ein vollständiges JDK 21
- LuckPerms **5.5.x** auf demselben Server (Pflichtabhängigkeit)
- Maven für einen eigenen Build

Die Zielversionen werden in [pom.xml](pom.xml) und [plugin.yml](src/main/resources/plugin.yml) festgelegt.

## Installation und Update

1. Mit `mvn clean package` die JAR erstellen oder die bereitgestellte Build-JAR verwenden.
2. Paper sauber stoppen und LuckPerms installieren.
3. `target/vapeecore-1.0-SNAPSHOT.jar` in den Serverordner `plugins/` kopieren.
4. Paper starten. VapeeCore erstellt fehlende Operator-Konfigurationen unter `plugins/VapeeCore/`.
5. Berechtigungen in LuckPerms vergeben, den Lobby-Spawn mit `/setspawn` setzen und die gewünschten Konfigurationen anpassen.

Bei Updates den vorhandenen `plugins/VapeeCore/`-Ordner behalten. Beim Startup ergänzt VapeeCore fehlende Defaults älterer Operator-Configs nach einem exakten Backup unter `plugins/VapeeCore/backups/config/`. Eigene Werte, unbekannte Keys, Kommentare und vorhandene Listen bleiben erhalten. Bestehende Texte werden nicht durch neue Default-Texte ersetzt. Ein weiterer Start mit derselben Config-Version schreibt die Dateien nicht erneut. Eine neuere, nicht unterstützte Version wird mit Warnung unangetastet gelassen; die vorhandenen Loader lesen die ihnen bekannten Werte.

## Konfiguration

**Live-Dateien** unter `plugins/VapeeCore/` steuern den Server. **Resource-Dateien** unter `src/main/resources/` sind die Vorlagen in der JAR. Änderungen an Ressourcen werden erst mit einem neuen Build wirksam und ersetzen keine bereits gesetzten Betreiberwerte.

| Live-Datei | Zweck |
|---|---|
| `config.yml` | Servername, Message-Prefix, öffentlicher Rank-Track, Staff-Hierarchie, Friend-/Clan-Limits, Playtime-Rewards |
| `lobby.yml` | Spawn, Gamemode, Teleports, Protection, Join-/Quit-Nachrichten und Hotbar-Items |
| `chat.yml` | Chat-Format und Interpretation von LuckPerms-Meta |
| `private-messages.yml` | Aktivierung und Nachrichtentemplates für private Nachrichten |
| `presentation.yml` | Sidebar, Tablist, Nametags, Rank-/Clan-Darstellung und Updateintervall |
| `daily-quests.yml` | Aktivierung, Auswahl, Reset, Zeitzone und Quest-Katalog |

Diese sechs Dateien besitzen `config-version: 1` und werden beim Startup auf der festen Operator-Allowlist weiterentwickelt. Die Version nicht manuell erhöhen, um Defaults anzufordern: neue Keys werden mit einem entsprechenden Schema-Update ergänzt. Typkonflikte bewahren den Betreiberwert und erzeugen eine Warnung; der jeweilige Loader verwendet seine sicheren Fallbacks.

Nach normalen Änderungen liest **`/core reload`** alle sechs Dateien in einer gemeinsamen Prepare-/Apply-Transaktion neu. Der Reload erstellt keine Migrationsbackups und schreibt keine Operator-Configs. Neue Daily-Assignments entstehen erst beim nächsten regulären Sync.

`warps.yml`, `blackjack.yml`, `players/*.yml`, `friends.yml`, `clans.yml` und `moderation.yml` sind veränderliche **Persistence**, keine Operator-Config-Evolution. Sie besitzen ihre eigenen Datei-/Schema-Verträge. Verwende für Warps und Blackjack die Admin-Commands.

### Lobby-Items anpassen

Die vorhandenen Typen heißen `navigator`, `visibility` und `settings`. Ein eigenes Friends-Hotbar-Item ist derzeit nicht registriert; Freunde sind über `/friend` erreichbar. Material, Slot, Name, Lore, Aktivierung und optionaler eigener Spielerkopf stehen in `lobby.yml`:

```yaml
items:
  navigator:
    enabled: true
    slot: 0
    material: PLAYER_HEAD
    head-owner: self
    name: "<aqua>Mein Navigator</aqua>"
    lore:
      - "<gray>Öffne die Warps.</gray>"
```

`head-owner: self` verwendet bei `PLAYER_HEAD` das bereits vorhandene Online-Profil ohne zusätzlichen Lookup; bei anderen Materialien wird es ignoriert. Slots sind **0 bis 8**, aktivierte Items benötigen unterschiedliche Slots. Bei ungültigen Materialien oder Slots warnen sichere Fallbacks. Fremde Items werden nur in freie, nicht reservierte Storage-Slots verschoben; ohne freien Platz bleibt das fremde Item erhalten. PDC-Identität und Click-Aktion bleiben in Java, unabhängig von Text und Material.

Die zweite Sichtbarkeitsdarstellung liegt unter `items.visibility.filtered`. Nach `/core reload` werden die Items geladener NORMAL-Spieler in der Lobby aktualisiert; BUILD- und Activity-Inventare behalten ihre Zuständigkeit.

Für Chat- und Darstellungs-Platzhalter sowie LuckPerms-Meta siehe [FORMATTING.md](docs/FORMATTING.md).

## Entwicklung

Der Source ist nach Features unter `src/main/java/dev/vapee/core/` gegliedert. Die Main Class verdrahtet 27 Module explizit; Disable läuft rückwärts. Es gibt sechs Reload-Teilnehmer.

```powershell
mvn clean package
pwsh -File .\scripts\run-harnesses.ps1
```

Der Harness-Runner entdeckt ausführbare `*Harness.java` selbst, prüft die Bytecode-Mains und führt die Regression mit vollständigem Inventar aus. Ein Maven-Build allein führt diese eigenständigen Harnesses nicht aus.

Für die lokale Windows-/IntelliJ-Umgebung sind die Run-Konfigurationen **Build & Deploy VapeeCore**, **Start VapeeCore Dev Server** und **Stop VapeeCore Dev Server** vorhanden. Der offizielle Build-/Deploy-Workflow stoppt Paper sauber, baut die JAR, führt alle Harnesses aus und ersetzt erst nach erfolgreichem Gate die Dev-JAR:

```powershell
pwsh -File .\scripts\build-and-deploy.ps1 -Offline -MavenRepository 'C:\Users\mehdi\.m2\repository'
pwsh -File .\scripts\start-dev-server.ps1
pwsh -File .\scripts\stop-dev-server.ps1
```

Für andere Rechner den Repository-Pfad passend wählen oder ohne Offline-Parameter arbeiten. `JAVA_HOME` muss auf das vollständige JDK zeigen. `dev-server/` und `target/` sind lokale, ignorierte Verzeichnisse.

Der [Developer Guide](docs/DEVELOPER_GUIDE.md) beginnt mit **Common Changes / Where do I change this?**: typische Änderungen, konkrete Dateien, GUI-Layouts, Package-Navigation und Config-/Persistence-Grenzen.

## Roadmap

Geplante Weiterentwicklungen sind Staff-Interaction-/Teleport-Consent-UX und optionale Rank-Badges. Diese Funktionen sind noch nicht implementiert; die vorhandenen Teleport-Befehle unterliegen den aktuellen Permissions und Schutzregeln.

## Dokumentation

- [Developer Guide](docs/DEVELOPER_GUIDE.md): Architektur, Änderungen und lokale Entwicklung
- [Permissions](docs/PERMISSIONS.md): Commands, Berechtigungen und Staff-Verträge
- [Formatting](docs/FORMATTING.md): Chat, LuckPerms-Meta, Rankfarben und Clan-Templates
- [Reports](reports/): historische technische Audits und Validierungsnachweise
