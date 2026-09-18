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
| Coins | `dev.vapee.core.economy` |
| Player settings persistence | `dev.vapee.core.player.settings` und `FilePlayerRepository` |
| Ignore/social | `dev.vapee.core.social` und `dev.vapee.core.player.social` |
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
| `config.yml` | `ConfigService` | Servername, globaler Message-Prefix, Debug | Ja | Durch Reload | `src/main/resources/config.yml` |
| `lobby.yml` | `LobbyConfig` / `LobbyModule` | Spawn, Teleport, Protection, `player.gamemode`, Join/Quit-Texte | Ja | Spawn durch `/setspawn`, übrige Werte durch Reload | `src/main/resources/lobby.yml` |
| `chat.yml` | `ChatConfig` / `ChatModule` | Globaler Chat und LuckPerms-Metaformat | Ja | Durch Reload | `src/main/resources/chat.yml` |
| `private-messages.yml` | `PrivateMessageConfig` / `PrivateMessageModule` | Aktivierung und PM-Formate | Ja | Durch Reload | `src/main/resources/private-messages.yml` |
| `presentation.yml` | `PresentationConfig` / `PresentationModule` | Sidebar, Tablist, Updateintervall | Ja | Durch Reload | `src/main/resources/presentation.yml` |
| `blackjack.yml` | `BlackjackTableConfig` / `BlackjackModule` | Physische Blackjack-Table-Drafts | Nein | Ja, atomar über `/blackjack setup …` | `src/main/resources/blackjack.yml` |
| `warps.yml` | `WarpConfig` / `WarpModule` | Dynamische Warps | Nein | Ja, atomar über `/warp …` | `src/main/resources/warps.yml` |

Die fünf Reload-Teilnehmer bleiben exakt `config.yml`, `lobby.yml`, `chat.yml`, `private-messages.yml` und `presentation.yml`. Der Reload wird zweiphasig vorbereitet und angewendet. Bei Rollback setzt `LobbyModule` neben Config und Spawn auch die Gamemodes aller normalen Lobby-Spieler auf den vorherigen Wert zurück. BUILD-Spieler bleiben bis zum Build-Ende in `CREATIVE`.

## Module map und Reihenfolge

Die registrierte Reihenfolge ist eine Dependency-Reihenfolge und muss bei neuen Modulen bewusst gepflegt werden:

1. **Permission** – lesender LuckPerms-Zugriff.
2. **Player** – `CorePlayer`, Cache, YAML-Persistence, Settings und Player-Join-/Quit-Lifecycle.
3. **Social** – Ignore-State und Commands.
4. **Economy** – Coin-Wallet und `/coins`.
5. **Lobby** – Config, Spawn, Protection, Player-State, Items, Messages, `/spawn`, `/setspawn`.
6. **Chat** – globaler Chat.
7. **PrivateMessage** – `/msg`, `/reply` und Session-Konversationen.
8. **Presentation** – Sidebar und Tablist.
9. **Settings** – Settings-Inventar und `/settings`.
10. **Activity** – generische Runtime-Typen, Venues, Sessions und Memberships.
11. **Utility** – Utility-Commands; derzeit ausschließlich `/build`.
12. **Blackjack** – physische Tische, Sitze, Kartenrunde und UI.
13. **Warp** – dynamische Warp-Persistence und Admin-Command.
14. **LobbyExperience** – Visibility, Item-Interaktionen und Navigator-UI.

Shutdown läuft exakt rückwärts: LobbyExperience → Warp → Blackjack → Utility → Activity → Settings → Presentation → PrivateMessage → Chat → Lobby → Economy → Social → Player → Permission. Das ist relevant, weil Lobby beim eigenen Cleanup aktive BUILD-Inventare entfernt, nachdem Experience-UI und Interaktionen bereits deaktiviert wurden; die Lobby-Hotbar wird beim Shutdown nicht neu erzeugt.

Die wichtigsten Dependency-Richtungen sind:

```text
Player
  ↑
Lobby

Lobby + Activity
  ↑
Utility

Activity + schmale Lobby-BUILD-Abfrage
  ↑
Blackjack

Lobby + Player + Settings + Warp
  ↑
LobbyExperience
```

`Presentation` bezieht außerdem Permission, Player, Economy und Lobby. Chat bezieht Permission und Social; PrivateMessage bezieht Player und Social. `MessageService` sowie bei Bedarf `ConfigService` werden explizit injiziert. Utility besitzt keine Blackjack-Abhängigkeit. Das generische Activity-Framework kennt weder Lobby noch Blackjack.

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

`ActivityService.isParticipating` blockiert NORMAL → BUILD. In Gegenrichtung erhält `BlackjackService` eine schmale `Predicate<UUID>`-Abfrage und lehnt BUILD-Spieler vor Definition-, Sitz- oder Membership-Änderungen ab. Es gibt keine `UtilityModule → BlackjackModule`-Dependency und keine Lobby-Abhängigkeit im generischen Activity-Framework.

Inventory-Ownership darf nie gleichzeitig bei zwei Systemen liegen: `NORMAL` gehört der Lobby, `BUILD` ist das temporäre Creative-Inventory. Eine spätere Activity-Hotbar muss Besitz explizit übernehmen und anschließend kontrolliert an den Lobby-State zurückgeben; sie darf nicht parallel dieselben Slots verwalten.

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
| Command-Help-Design und Rendering | `dev.vapee.core.command.help` |
| Hauptübersicht und `/core help` | `CoreCommand` |
| Coin-Command-UX | `CoinsCommand` |
| Blackjack-Command-UX | `BlackjackCommand` |
| Warp-Command-UX | `WarpCommand` |
| Zukünftige Utility Commands | `utility/command` |

## Commands und permissions

| Command | Zweck | Zuständige Klasse | Permission |
|---|---|---|---|
| `/vapeecore`, `/core help`, `/core version`, `/core reload` | Status, permission-aware Hilfe, Version, koordinierter Reload | `CoreCommand` | Nur Reload: `vapeecore.admin` |
| `/spawn` | Zum Lobby-Spawn teleportieren | `SpawnCommand` | `vapeecore.lobby.spawn` |
| `/setspawn` | Lobby-Spawn speichern | `SetSpawnCommand` | `vapeecore.lobby.setspawn` |
| `/build` | Temporären Lobby-BUILD-Modus umschalten | `BuildCommand` | `vapeecore.utility.build` |
| `/coins`, `/coins help`, `/coins …` | Eigene Coins anzeigen / permission-aware Hilfe / Online-Balances administrieren | `CoinsCommand` | Basis `vapeecore.economy.coins`, Mutationen zusätzlich `vapeecore.economy.admin` |
| `/msg`, `/reply`, `/r` | Private Online-Nachrichten | `MessageCommand`, `ReplyCommand` | `vapeecore.message.use` |
| `/settings` | Settings-Menü öffnen | `SettingsCommand` | `vapeecore.settings.use` |
| `/ignore`, `/unignore`, `/ignorelist` | Ignore-State verwalten | `IgnoreCommand`, `UnignoreCommand`, `IgnoreListCommand` | `vapeecore.social.ignore` |
| `/blackjack`, `/blackjack help`, `/blackjack setup …` | Strukturierte Hilfe und Verwaltung physischer Blackjack-Tische | `BlackjackCommand` | `vapeecore.blackjack.admin` |
| `/warp`, `/warp help`, `/warp …` | Strukturierte Hilfe und Verwaltung dynamischer Warps | `WarpCommand` | `vapeecore.warp.admin` |

Ränge sind nicht in Java hardcodiert. LuckPerms vergibt Permissions, etwa `vapeecore.utility.build` an eine Gruppe namens „Builder“; VapeeCore prüft nur die Permission und kennt den Gruppennamen nicht. `vapeecore.lobby.build` ist eine deprecated Compatibility-Permission in `plugin.yml`, deren Child die neue Permission gewährt. Produktionscode prüft den alten Namen nicht mehr. Der alte Name umgeht insbesondere niemals direkt die Lobby-Protection.

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
- `dev.vapee.core.activity.blackjack.table.BlackjackTableHarness`
- `dev.vapee.core.lobby.warp.WarpHarness`
- `dev.vapee.core.lobby.player.LobbyHarness`
- `dev.vapee.core.utility.command.BuildCommandHarness`
- `dev.vapee.core.message.CommandHelpHarness`

Nach relevanten Änderungen folgen ein Paper-1.21.11-Smoke-Test mit Java 21 und LuckPerms 5.5.x, `/core`, `/core reload`, Command-Registrierung und sauberem Shutdown. Ein „Live Client Test“ darf nur dokumentiert werden, wenn wirklich ein Minecraft-Client verbunden war und die Schritte ausgeführt wurden; Serverstart oder Harness allein zählen nicht als Live-Client-Test.

## Documentation maintenance rule

Diese Datei ist Teil der Codebase. Wenn eine zukünftige Änderung eine Klasse verschiebt, einen Config-Key ändert, einen Command oder ein Modul hinzufügt, Ownership verschiebt, Permissions verändert oder Join-/Player-State-Flows anpasst, muss `docs/DEVELOPER_GUIDE.md` im selben Arbeitsschritt aktualisiert werden. Das README bleibt die Projektübersicht; die praktische Detaildokumentation bleibt hier.
