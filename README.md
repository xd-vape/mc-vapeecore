# VapeeCore

VapeeCore ist ein modular aufgebautes Minecraft-Plugin für Paper. Es verbindet Lobby, soziale Funktionen, Spielerprofile, Moderation, Darstellung und gemeinsame Activities in einer JAR. LuckPerms liefert Gruppen, Ranks und Berechtigungen.

## Funktionen

- **Lobby:** Spawn, konfigurierbare Teleports und Schutzregeln, NORMAL-/BUILD-Verhalten, Warp-Navigator, Sichtbarkeitsfilter, Player Menu per eigenem Spielerkopf mit Freunde-/Settings-Zugang und anpassbare Hotbar-Items.
- **Spieler:** bekannte Namen/UUIDs, Online-/Offline-Profile, gespeicherte Einstellungen, Ignore-Liste und Coins.
- **Community:** Freundschaften und Anfragen, gefilterte Friend-Presence-Hinweise, Clans mit Einladungen und Mitgliederverwaltung, private Nachrichten und Reply.
- **Moderation und Utility:** Mutes, Bans, Warnungen und Historie; berechtigungsgesteuerte Bewegungs-, Teleport- und Spielerwerkzeuge.
- **Darstellung:** formatierbarer Chat, Sidebar und Tablist; Rankfarben aus LuckPerms und optionale Clan-Tags in Tablist/Nametags. Die Nametag-Farbe verwendet die 16 von Minecraft unterstützten Teamfarben.
- **Rewards und Quests:** Gameplay-Reward-API, kumulative Playtime-Rewards, Quest-Fortschritt und konfigurierbare Daily-Auswahl. Daily Quests sind im Default deaktiviert und besitzen keinen vorinstallierten Quest-Katalog.
- **Activities:** gemeinsame Session-/Teilnehmerverwaltung, physische Blackjack-Tische, Sitze und World Displays. Blackjack besitzt keine Coin-Einsätze.

`/core help` zeigt die verfügbaren Befehle. Feature-Einstiege sind beispielsweise `/spawn`, `/settings`, `/friend`, `/clan`, `/profile`, `/quests`, `/msg`, `/rank` und `/ranks`. Vollständige Berechtigungen und öffentliche Command-Verträge stehen in [PERMISSIONS.md](docs/PERMISSIONS.md).

## Sprache: Deutsch und Englisch

VapeeCore liest die aktuelle Minecraft-Clientsprache: Sprachcode `de` (einschließlich Deutschland, Österreich und Schweiz) zeigt Deutsch, alle anderen Sprachen Englisch. Console-Ausgaben bleiben Englisch. Es gibt keinen Sprach-Command und keinen gespeicherten Sprachwert im Spielerprofil.

`plugins/VapeeCore/lang/en.yml` und `lang/de.yml` sind UTF-8-MiniMessage-Kataloge. Beim ersten Start werden nur fehlende Dateien kopiert; vorhandene Dateien werden nie überschrieben oder durch ConfigEvolution ergänzt. Eigene Einträge sind optionale Overrides. Deutsch verwendet Live-DE → gebündeltes DE → Live-EN → gebündeltes EN; Englisch Live-EN → gebündeltes EN. `/core reload` validiert beide Kataloge und übernimmt sie gemeinsam. Bei Fehlern bleibt der vorherige Stand aktiv.

Eigene Config-Texte bleiben erhalten: ein bisheriger String oder eine Lore-Liste gilt weiterhin für alle Spieler. Lobby-Namen/Lore, Join-/Quit-Formate, PM-Formate und Quest-Namen/Beschreibungen können zusätzlich `en`/`de`-Maps mit englischem Fallback verwenden. Darstellung und sichere Platzhalter sind in [FORMATTING.md](docs/FORMATTING.md) beschrieben.

Ein Client-Sprachwechsel aktualisiert zukünftige Nachrichten und die Items eigener aktiver Menüs. Native Inventartitel ändern sich beim nächsten Öffnen. Spielertexte, Clan-/Warp-Namen, Staff-Gründe, Servermarke und LuckPerms-Metadaten werden unverändert angezeigt. Gemeinsam sichtbare Weltanzeigen bleiben Englisch; die bestehende gemeinsame Chat-/Nametag-Darstellung wird nicht in eine neue Architektur umgebaut.

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
| `lang/en.yml`, `lang/de.yml` | Optionale englische/deutsche Übersetzungs-Overrides; keine ConfigEvolution |

`lobby.yml` besitzt `config-version: 2`, die anderen fünf Dateien `config-version: 1` und werden beim Startup auf der festen Operator-Allowlist weiterentwickelt. Die Version nicht manuell erhöhen, um Defaults anzufordern: neue Keys werden mit einem entsprechenden Schema-Update ergänzt. Typkonflikte bewahren den Betreiberwert und erzeugen eine Warnung; der jeweilige Loader verwendet seine sicheren Fallbacks.

Nach normalen Änderungen liest **`/core reload`** die Operator-Configs und beide Sprachdateien über die in `VapeeCore` verdrahteten Teilnehmer in einer gemeinsamen Prepare-/Apply-Transaktion neu. Der Reload erstellt keine Migrationsbackups und schreibt keine Operator-Configs. Die heutige Daily-Auswahl bleibt im selben Cycle erhalten; neue Katalogeinträge werden beim nächsten Cycle ausgewählt. Für ausdrückliche Tests übernimmt `/core quest force [player]` die aktuelle Auswahl bis zur konfigurierten Anzahl, erhält Fortschritt weiterhin ausgewählter IDs und vergibt selbst keine Coins. Ausstehende Belohnungen blockieren Force; es verlangt `vapeecore.admin` und den bestehenden Online-Zielschutz.

`warps.yml`, `blackjack.yml`, `players/*.yml`, `friends.yml`, `clans.yml` und `moderation.yml` sind veränderliche **Persistence**, keine Operator-Config-Evolution. Sie besitzen ihre eigenen Datei-/Schema-Verträge. Verwende für Warps und Blackjack die Admin-Commands.

### Lobby-Items anpassen

Die Java-Registrierungen heißen `player-menu`, `navigator`, `visibility`, `settings` und `friends`. Aktuelles Default-Beispiel: Player Menu auf Slot 0, Visibility auf 1, Navigator auf 8; die separaten Settings-/Friends-Items sind standardmäßig deaktiviert. Maßgeblich ist immer die Live-`lobby.yml`, keine funktionale Bindung an diese Positionen. Der optionale Friends-Kopf öffnet das bestehende Freunde-Menü mit der aktuellen Friend-Berechtigung; `/friend` bleibt verfügbar. Material, Slot, Name, Lore, Aktivierung und optionaler eigener Spielerkopf stehen in `lobby.yml`. Beispiel eines eigenen Navigator-Eintrags (aktive Slots müssen kollisionsfrei bleiben):

```yaml
items:
  navigator:
    enabled: true
    slot: 8
    material: PLAYER_HEAD
    head-owner: self
    name: "<aqua>Mein Navigator</aqua>"
    lore:
      - "<gray>Öffne die Warps.</gray>"
```

`head-owner: self` verwendet bei `PLAYER_HEAD` das bereits vorhandene Online-Profil ohne zusätzlichen Lookup; bei anderen Materialien wird es ignoriert. Slots sind **0 bis 8**, aktivierte Items benötigen unterschiedliche Slots. Bei ungültigen Materialien oder Slots warnen sichere Fallbacks. Fremde Items werden nur in freie, nicht reservierte Storage-Slots verschoben; ohne freien Platz bleibt das fremde Item erhalten. Name/Lore verwenden MiniMessage; Farbtags dürfen implizit geschlossen werden. PDC-Identität und Click-Aktion bleiben in Java, unabhängig von Text und Material. Nicht registrierte YAML-IDs werden mit Warnung ignoriert. Ein aus dem vorhandenen `items`-Abschnitt entfernter Eintrag wird nach Reload entfernt; `enabled: false` deaktiviert ihn ebenfalls. Die ID `friends` beibehalten und für einen anderen Titel nur `name` ändern.

Die zusätzlichen Sichtbarkeitsdarstellungen liegen unter `items.visibility.filtered` und `items.visibility.variants.hidden`. Rechtsklick wechselt ALL → FILTERED → HIDDEN. Nach `/core reload` werden die Items geladener NORMAL-Spieler in der Lobby aktualisiert; BUILD- und Activity-Inventare behalten ihre Zuständigkeit.

Für Chat- und Darstellungs-Platzhalter sowie LuckPerms-Meta siehe [FORMATTING.md](docs/FORMATTING.md).

## Entwicklung

See [Command development](docs/COMMAND_DEVELOPMENT.md) for root/subcommand ownership, the Warp extension pilot, authorization, help/completion and lifecycle tests.

Benötigt werden ein vollständiges **JDK 21**, **Maven 3.6.3+** und **PowerShell 7+**. `JAVA_HOME` bzw. der Java-Launcher auf `PATH` bestimmt das JDK; Maven wird über `MAVEN_HOME` oder `PATH` gefunden. Auf Windows bleibt die vorhandene IntelliJ-Maven-Installation ein optionaler Fallback. Ein explizit falsches Home-Verzeichnis erzeugt eine Diagnose. Ohne `-MavenRepository` gilt die normale Maven-Konfiguration einschließlich `settings.xml`; `-Offline` setzt bereits vorhandene Abhängigkeiten voraus.

1. Repository klonen und den Projektordner bzw. `pom.xml` in IntelliJ öffnen; Project SDK und Maven Runner auf JDK 21 stellen.
2. Für die reine JAR-Erstellung `mvn clean package` verwenden.
3. Vor einem Commit die vollständige, **nicht deployende** Validierung ausführen:

```powershell
pwsh -File .\scripts\verify.ps1
# Optional: vorhandenes lokales Repository und Offline-Modus
pwsh -File .\scripts\verify.ps1 -Offline -MavenRepository 'C:\path with spaces\maven-repository'
```

`verify.ps1` prüft die Voraussetzungen, baut mit `clean package`, prüft die Java-Formatierung und ruft danach den bestehenden vollständigen Harness-Runner auf. Erfolg verlangt eine bestandene Formatprüfung, alle entdeckten Einstiege, positive Check-Zahlen und null fehlende/ungültige Ergebnisse. Logs und das aktuelle `results.json` liegen unter `target/verification/harnesses/`. Verify stoppt/startet keinen Server, deployed keine JAR und verändert keine Betreiberkonfiguration.

Der Arbeitsablauf ist **Bearbeiten → Formatieren → Formatprüfung → vollständige Verifikation → Commit**. `mvn spotless:apply` formatiert ausdrücklich, `mvn spotless:check` prüft ohne Quelländerung. Die fest versionierte Kombination Spotless 2.43.0 / Google Java Format 1.22.0 (AOSP) gilt vorerst ausschließlich für die sechs Dateien der Allowlist in `pom.xml`. `.editorconfig` liefert kompatible Editor-Defaults; andere Java-Dateien werden nicht automatisch umformatiert. Einrichtung, Einzeldatei-Auswahl, Offline-Cache und IntelliJ stehen in [CODE_STYLE.md](docs/CODE_STYLE.md). Verify selbst formatiert niemals automatisch.

`scripts/run-harnesses.ps1` bleibt der direkte Einstieg für die vollständige eigenständige Behavioral Regression: Bytecode-Discovery statt gepflegter Klassenliste, ein Prozess pro Harness. **Maven allein führt diese Verhaltenstests nicht aus.** Bei kleinen Änderungen zunächst die betroffenen Harnesses mit der vom Runner erzeugten Classpath ausführen; vor dem Commit immer die komplette Validierung. Die integrierten Skript-Fehlertests können separat mit `pwsh -File ./scripts/tests/verification.Tests.ps1` gestartet werden.

Für lokale Entwicklung `paper-*.jar` unter `dev-server/` und LuckPerms unter `dev-server/plugins/` bereitstellen sowie die Paper-EULA bearbeiten. Die eingecheckten IntelliJ-Konfigurationen heißen **Build & Deploy VapeeCore** und **Start VapeeCore Dev Server**; deren Interpreter-/JDK-Pfade gegebenenfalls auf die eigene Installation stellen. Stop ist ein separates Skript. Build & Deploy verwendet dieselbe vollständige Validierung, stoppt danach Paper sauber und ersetzt erst dann die Dev-JAR mit Größen-/SHA-256-Nachweis. Ein fehlgeschlagenes Gate lässt die bisher deployte JAR unverändert; der Server startet nicht automatisch:

```powershell
pwsh -File .\scripts\build-and-deploy.ps1
pwsh -File .\scripts\start-dev-server.ps1
pwsh -File .\scripts\stop-dev-server.ps1
```

Alle Validierungs-/Deploy-Skripte unterstützen `-Offline` und `-MavenRepository`. `dev-server/` enthält lokale Welten, Plugins, Betreiber-YAML und Spielerdaten; `dev-server/.vapeecore-dev/` enthält die Serversteuerung. `target/` enthält nur generierte Build-/Validierungsdaten. Beide Verzeichnisse sind ignoriert. Bestehende Plugin-Daten bei Updates behalten.

Die Entwicklungsskripte wurden unter Windows/PowerShell 7 ausgeführt. Plattformneutrale Pfade und `mvn`/JDK-Erkennung für Linux/macOS sind implementiert, dort aber nicht zur Laufzeit getestet. Die IntelliJ-Run-Konfigurationen bleiben Windows-Vorlagen.

Der Source ist nach Features unter `src/main/java/dev/vapee/core/` gegliedert. `VapeeCore.java` ist die maßgebliche Modul-/Reload-Verdrahtung; Disable läuft rückwärts. Die [Architekturreferenz](docs/ARCHITECTURE.md) erklärt Owner, Commit-Grenzen und Erweiterungspfade für Settings, Commands, Config, Module, GUIs und Persistenz. **Common Changes / Where do I change this?** im [Developer Guide](docs/DEVELOPER_GUIDE.md#common-changes--where-do-i-change-this) führt zu konkreten Feature-Dateien.

## Roadmap

Geplante Weiterentwicklungen sind Staff-Interaction-/Teleport-Consent-UX und optionale Rank-Badges. Diese Funktionen sind noch nicht implementiert; die vorhandenen Teleport-Befehle unterliegen den aktuellen Permissions und Schutzregeln.

## Dokumentation

- [Architecture](docs/ARCHITECTURE.md): Zuständigkeiten, Lifecycle und Standard-Erweiterungspfade
- [Persistence development](docs/PERSISTENCE_DEVELOPMENT.md): Profil-Felder, Settings-YAML, Schema, Migration, sichere Speicherung und Fehlerprüfungen
- [Code Style](docs/CODE_STYLE.md): Java-/Package-Konventionen und begrenzter Formatter-Pilot
- [Settings Development](docs/SETTINGS_DEVELOPMENT.md): Preferences, GUI-Einträge, Persistenz und Erweiterungstests
- [Configuration Development](docs/CONFIG_DEVELOPMENT.md): Betreiberwerte, Versionierung, sichere Evolution und Reload
- [Module Development](docs/MODULE_DEVELOPMENT.md): Abhängigkeiten, Ownership, Teilstart-Rollback und Lifecycle-Tests am Friend-Piloten
- [Service Dependencies](docs/SERVICE_DEPENDENCIES.md): Typisierte Service-Grenzen, später Erwerb und frische Abhängigkeiten beim Presence-Neustart
- [Developer Guide](docs/DEVELOPER_GUIDE.md): Feature-Details, Änderungen und lokale Entwicklung
- [Permissions](docs/PERMISSIONS.md): Commands, Berechtigungen und Staff-Verträge
- [Formatting](docs/FORMATTING.md): Chat, LuckPerms-Meta, Rankfarben und Clan-Templates
- [Reports](reports/): historische technische Audits und Validierungsnachweise
