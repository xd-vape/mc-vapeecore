# Phase 28 – Lobby and Navigation Completion

Phase 28 ergänzt den bestehenden Compass-Warp-Navigator um Sichtbarkeit, Reihenfolge, aktuelle Zugriffsprüfung und sichere Inventory-Lifecycle-Grenzen. Die vorhandene Lobby- und Warp-Architektur bleibt erhalten. Dieser Bericht dokumentiert Implementierung, Regression, Build, Paper-Smoke und die Grenzen der Verifikation.

## Baseline und Umfang

| Merkmal | Ergebnis |
| --- | --- |
| Branch | `phase/28-lobby-navigation-completion` |
| Start-Commit | `ded66f1d8f0983110bd23047ba335117e7cc8047` |
| Start-Worktree | sauber |
| Baseline-Regression | 93 ausführbare Harnesses, 14.098 Checks |
| Module | 27, bestehende Reihenfolge unverändert |
| ReloadParticipant | 6; Warp und LobbyExperience bleiben keine Teilnehmer |
| Root-Commands | 36 |
| Permission-Nodes | 49; 15 bestehende Child-Beziehungen |
| Umsetzung | ein Worker, ein lokaler Abschluss-Commit, kein Push |

`LobbyModule` besitzt Welt, Spawn, Protection und Lobby-Konfiguration. `LobbyExperienceModule` besitzt die normale Lobby-Ausstattung, globale Join-/Quit-Ausgabe, Compass-Interaktion und Navigator-Inventare. `WarpModule` besitzt die generischen Warp-Daten, Persistence, Service und Admin-Commands. Die kleine `NavigatorAccessPolicy` verbindet ausschließlich in der Anwendungsschicht Player-, Lobby-, Mode- und Activity-Abfragen. `ActivityModule` wird ausdrücklich in LobbyExperience injiziert; WarpService kennt weder Lobby-State noch ActivityService.

Der Hotbar-Vertrag bleibt Slot 0 `COMPASS` (Navigator), Slot 4 `LIME_DYE`/`GRAY_DYE` (Visibility), Slot 8 `COMPARATOR` (Settings). Es gibt kein Server Menu, Main Menu, Game Menu, zusätzliches Item, Categories, fest verdrahtete Destination, reservierte Warp-ID oder automatisches Spawn-Ziel. FAWE-/Build-Ausstattung, Inventory Ownership und Restore-Verhalten bleiben bei ihren bisherigen Services. Ein allgemeines GUI-Framework oder Shared-Item-Factory-Refactoring entsteht nicht.

## Datei-Inventar

Neue Dateien:

- `docs/LOBBY_NAVIGATION.md`
- `src/main/java/dev/vapee/core/lobby/warp/WarpNavigation.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorAccessPolicy.java`
- `src/test/java/dev/vapee/core/lobby/warp/command/WarpCommandHarness.java`
- `src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorFixture.java`
- `src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenuHarness.java`
- `src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorSecurityHarness.java`

Geänderte Dateien:

- `README.md`
- `docs/DEVELOPER_GUIDE.md`
- `src/main/java/dev/vapee/core/VapeeCore.java`
- `src/main/java/dev/vapee/core/lobby/experience/LobbyExperienceModule.java`
- `src/main/java/dev/vapee/core/lobby/experience/LobbyItemListener.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorInventoryHolder.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorListener.java`
- `src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenu.java`
- `src/main/java/dev/vapee/core/lobby/warp/WarpConfig.java`
- `src/main/java/dev/vapee/core/lobby/warp/WarpPoint.java`
- `src/main/java/dev/vapee/core/lobby/warp/WarpResult.java`
- `src/main/java/dev/vapee/core/lobby/warp/WarpService.java`
- `src/main/java/dev/vapee/core/lobby/warp/command/WarpCommand.java`
- `src/test/java/dev/vapee/core/lobby/warp/WarpHarness.java`

Entfernte Dateien: keine. Insgesamt sieben neue und 14 geänderte Dateien. Lokale Build-/Harness-/Server-Artefakte liegen ausschließlich in den bereits ignorierten `target/`- und `dev-server/`-Verzeichnissen.

## Warp-Metadaten und Persistence

`WarpNavigation` ist ein immutable Record mit `boolean visible` und nichtnegativem `int order`. `WarpPoint` besitzt diesen Wert zusätzlich zu seinen bisherigen vier Feldern; der alte Vier-Argument-Konstruktor bleibt verfügbar.

```yaml
warps:
  example:
    display-name: Example
    icon: ENDER_PEARL
    navigator:
      visible: true
      order: 0
    location:
      world: world
      x: 0.5
      y: 80.0
      z: 0.5
      yaw: 0.0
      pitch: 0.0
```

Die ID ist nur ein frei gewähltes Beispiel. Es gibt keine konfigurierten GUI-Slots. Fehlende Navigator-Metadaten bei Legacy-Warps und neu erzeugte Warps bedeuten `visible=true`, `order=0`. Hidden entfernt einen Warp ausschließlich aus der Navigator-Sicht; Admin-Verwaltung und generischer Teleport bleiben erlaubt.

`visible` akzeptiert ausschließlich echte YAML-Booleans. `order` akzeptiert ganzzahlige YAML-Zahlen im Bereich `0..2147483647`; negative Werte, Strings, Dezimalzahlen, Exponenten als Gleitkommazahlen und Overflow werden abgewiesen. Ein falscher Einzelwert erzeugt WARNING mit Datei, Warp-ID, Key und Fallback und setzt nur diesen Wert auf seinen Default. Eine skalare, Listen- oder explizit null gesetzte Navigator-Section fällt vollständig auf `true/0` zurück. Explizite Null-Felder werden trotz Bukkit-YAML-Null-Entfernung erkannt und gewarnt. Fehlende optionale Felder sind kompatibel und benötigen keine Warnung. Die bisherigen Pflichtdaten ID, Display Name, Icon und Position behalten ihre strikte Validierung.

Laden verändert die Quelldatei nicht. Es gibt keine Startup-Migration oder Reparatur-Schreiboperation. Eine erfolgreiche normale Mutation schreibt beide Navigator-Felder zusammen mit den vorhandenen Warp-Daten über denselben bestehenden Tempdatei-/Atomic-Move-/Replace-Fallback-Pfad. Der Service baut einen Kandidaten, speichert zuerst und tauscht erst anschließend den Runtime-State. Bei Save-Failure bleiben State und bisherige Warp-Objekte erhalten. Position, Name und Icon bewahren die aktuellen Navigator-Metadaten; Visibility und Order bewahren jeweils alle anderen Felder. Identische Visibility-/Order-Werte liefern `SUCCESS` ohne Save, auch wenn das Dateisystem momentan keine Änderung zulässt.

`getWarps()` bleibt eine immutable, nach ID sortierte vollständige Admin-Sicht. `getNavigatorWarps()` liefert eine separate immutable Liste ausschließlich sichtbarer Warps, aufsteigend nach Order und dann ID. Es gibt keine zweite Registry oder Dateiabfrage beim Klicken.

## Admin-UX

Alle Pfade prüfen weiterhin zuerst `vapeecore.warp.admin` (Default `op`). Es gibt keinen neuen Root, Permission-Node, Player-Teleport-Command, `/navigator`-Command oder Staff-Hierarchie-Zwang.

| Command | Verhalten |
| --- | --- |
| `/warp show <id>` | setzt Navigator-Sichtbarkeit auf true |
| `/warp hide <id>` | setzt Navigator-Sichtbarkeit auf false |
| `/warp order <id> <number>` | setzt nichtnegative Navigator-Reihenfolge |
| `/warp help` | enthält die neuen Einträge in der bestehenden Management-Section |
| `/warp info <id>` | zeigt zusätzlich Visible/Hidden und Order |
| `/warp list` | zeigt alle IDs einschließlich Hidden samt kompakter Sichtbarkeit und Order |

Der Order-Parser akzeptiert nur ASCII-Dezimalziffern im `int`-Bereich: `0`, führende Nullen und `2147483647` sind zulässig; Vorzeichen, Whitespace, Dezimalpunkt/-komma, Exponenten, leere Werte und Overflow nicht. Arity, fehlende IDs, ungültige Eingaben und Save-Fehler erhalten kontrolliertes Feedback. Save-Fehler loggen den Fehler und melden keinen Erfolg. IDs und Display Names bleiben literale Adventure-Components.

Completion bietet Show nur für Hidden, Hide nur für Visible und Order für alle Warps an, jeweils mit deterministischem Prefix-Filter. Ohne Permission bleiben Help, Info, List, Mutation und Completion gesperrt; keine ID- oder Metadaten-Leaks. Die bestehenden Set/Remove/Name/Icon-Commands behalten ihre Aufgabe und Console-/Player-Grenzen.

## Navigator-Darstellung

Der Titel bleibt `Warp Navigator`; 54 Inventory-Slots und 45 Content-Slots bleiben bestehen. Die untere Zeile behält Previous 45, Page Info 49, Close 50 und Next 53. Die sichtbare Service-Liste bestimmt Reihenfolge und Seitenanzahl. Seiten werden auf `0..lastPage` geklemmt; eine leere Sicht besitzt genau eine gültige Seite. Page Info zählt ausschließlich sichtbare Destinations.

Einträge zeigen Icon, freundlichen literal Display Name und `Click to teleport.`. Technische IDs, Admin-Syntax und rohe MiniMessage-Auswertung entfallen aus der Player-Ansicht. Eine leere Registry und eine ausschließlich versteckte Registry zeigen denselben neutralen `No Destinations Available`-Zustand ohne Admin-Hinweis. Der serverseitige Content-Slot→ID-Snapshot im Holder bleibt die Target-Quelle, niemals Material, Titel, Name, Lore oder NBT.

## Inventory- und Zugriffsgrenzen

`NavigatorMenu` hält pro Instanz `Map<UUID, Inventory>`. Eine Aktion benötigt zugleich passende Owner-UUID, den echten Inventory-Holder, dessen exakte Inventory-Bindung, dieselbe registrierte aktive Inventory-Instanz und dieselbe tatsächlich geöffnete Top-Inventory-Instanz. Fremde Owner, ungebundene oder nachgebildete Holder, alte Seiten und bereits geschlossene Menüs bleiben inert. Ein späteres Close-Event einer alten Seite entfernt keine neue aktive Seite. Ein abgelehnter Inventory-Open publiziert kein neues aktives Inventory.

Jeder erkannte Navigator-Click wird vor Validierung gecancelt, einschließlich Bottom-Transfers und Outside Clicks. Nur LEFT/RIGHT im Top Inventory dürfen definierte Ziele oder Controls ausführen. Shift, Number Key, Double Click, Drop, Offhand Swap, Collect und Creative-Aktionen bleiben ohne Navigator-Aktion. Top-Drags sind gecancelt; reine Bottom-Drags sind nur bei gültigem aktivem und autorisiertem Navigator erlaubt. Ungültige oder veraltete Navigator-Drag-Events werden vollständig gecancelt.

Öffnen und jedes Click-/Drag-Event prüfen erneut: Player online, `PlayerService.isLoaded`, aktuelle Lobby-Welt, `LobbyPlayerMode.NORMAL`, keine Activity-Teilnahme. Nach tatsächlichem Inventory-Open wird die Policy erneut geprüft, sodass ein Open-Event mit zwischenzeitlichem State-Wechsel keinen Zugang publiziert. BUILD, ungeladene Spieler, Activity-Teilnehmer und Spieler außerhalb der Lobby werden abgewiesen. Ein stale Compass kann diese Grenzen nicht umgehen. Bei verlorenem Zugriff wird der eigene Navigator geschlossen und die aktive Referenz entfernt.

Es gibt keinen globalen Menu-Cache, Poller, Scheduler, Async-Pfad oder Datenbank. Darstellung und Events verwenden den vorhandenen synchronen Serverthread und kleine immutable Snapshots.

## Stale Ziele und Teleport

`teleportFromNavigator` prüft den aktuellen Warp-State auf Existenz und Sichtbarkeit. Hidden ergibt `NOT_NAVIGABLE`, entfernt ergibt `NOT_FOUND`; beide führen zu `That destination is no longer available.` und einem frischen, auf die noch vorhandenen Seiten geklemmten View. Die generische `teleport`-API bleibt unabhängig von Navigator-Visibility.

Eine geänderte Order interpretiert den alten Slot nicht neu: der Holder behält seine ursprüngliche ID. Die aktuelle Position dieser ID wird beim Ausführen gelesen. Eine ungeladene Zielwelt liefert `WORLD_NOT_LOADED`, ohne World-Erzeugung oder Ladeversuch. Eine Paper-Cancellation beziehungsweise `teleport(...) == false` liefert `TELEPORT_FAILED`; Fall Distance und Velocity werden dabei nicht verändert.

Ein erfolgreicher Teleport nutzt weiterhin `TeleportCause.PLUGIN`, setzt Fall Distance auf 0 und Velocity auf den Nullvektor und schließt den Navigator. Sichtbare Ziele innerhalb und außerhalb der Lobby sind zulässig. Nach Erfolg sind wiederholte alte Click-Events inert. Ein durch Teleport ausgelöstes World-Leave-Cleanup und anschließendes Success-Close sind idempotent. Utility-/Team-Teleport-Logik, Consent, Staff-Level und Spawn werden nicht als Warp-Policy verwendet.

## Lifecycle und bestehende Features

Quit vergisst die UUID auch nach bereits gewechseltem View. Ein World Change aus der Lobby schließt den eigenen Navigator und entfernt Tracking; Lobby-Eintritt öffnet ihn nicht automatisch. Disable schließt noch tatsächlich geöffnete eigene aktive Navigator-Inventare vor Listener-Deregistrierung. Fehler beim Schließen eines Spielers werden isoliert, weitere Spieler werden verarbeitet, und die Registry wird im `finally` vollständig geleert. Fremde aktuelle Inventare bleiben erhalten.

Partielles LobbyExperience-Enable deregistriert alle bereits angelegten Listener, deaktiviert den Experience-Kandidaten und bereinigt den Navigator-Kandidaten vor dem Verwerfen der Runtime-Referenzen. Kein neues Modul und kein anderer Enable-/Disable-Reihenfolgevertrag wird eingeführt. Die Harnesses prüfen Cleanup-Verhalten mit echten Menüinstanzen und die Module-Wiring-/Rollback-Grenzen anhand des Quellcodes; der normale JavaPlugin-Lifecycle wird zusätzlich auf Paper geprüft.

| Bestehender Bereich | Regression und Grenze |
| --- | --- |
| Spawn / SetSpawn | Lobby-Spawn bleibt eigene Position; Console bleibt player-only, kein reservierter Warp |
| Lobby Protection / BUILD | bestehende Protection, Managed Items, Ownership und Restore unverändert; BUILD-Navigator zusätzlich gesperrt |
| Activity | ActivityService bleibt Besitzer der Teilnahme; Inventory-Reservierung unverändert |
| Visibility | Master-/Filter-Policy, Hotbar-Item, Cooldown und Settings-Persistence unverändert |
| Settings | Comparator, Menü-Features, persönliche Toggles und Ownership unverändert |
| Presence (Phase 27) | opt-in Friend Presence, Ignore-Gates und Notifications unverändert; globale Join-/Quit-Ausgabe weiter bei LobbyExperience |
| Warp | vorhandene CRUD-/Teleport-APIs und Required-Field-Regeln erhalten |
| Phase 26A/26B | Staff-Hierarchie, Target Guard, Permission-First und Utility-/Economy-Hardening unverändert |
| Moderation | Ban, Mute, Chat-/PM-Enforcement, Persistence und Lifecycle unverändert |
| Übrige Features | alle vorhandenen Harnesses separat ausgeführt, Ergebnisse unten |

## Verifikation

| Prüfung | Tatsächliches Ergebnis |
| --- | --- |
| Finaler Maven-Build | `clean package`: BUILD SUCCESS, 18,203 s |
| Build-Umgebung | Java 21; vorhandener IntelliJ-Maven, Offline-Modus mit lokalem Dependency-Cache |
| Finale Regression | **96 Harnesses, 14.454 Checks, 0 Fehler** |
| Prozessgrenze | jeder ausführbare Harness als separater Java-Prozess, alle Exit 0 |
| Release-JAR | `target/vapeecore-1.0-SNAPSHOT.jar` |
| JAR-Größe | **1.025.059 Bytes** |
| SHA-256 | `61672887073F83076279ECD65EA524D86E7ACAEF9B98507A23FE10E0D6E168CE` |
| Deploy | lokale Dev-Server-JAR; vorherige JAR als `dev-server/backups/vapeecore-before-phase28.jar` gesichert |
| Deploy-SHA-256 | `61672887073F83076279ECD65EA524D86E7ACAEF9B98507A23FE10E0D6E168CE` |

Der finale Clean-Build wurde am 02.10.2026 um 10:57:44 CEST abgeschlossen; danach lief die vollständige Regression mit den frisch kompilierten Production- und Testklassen. Der vorherige vollständige Regression-Durchlauf lieferte dieselben 96/14.454/0. Bestehende javac-Hinweise auf Annotation Processing und bereits vorher deprecated API-Nutzung bleiben unverändert.

### Realer Paper-Smoke

| Umgebung / Probe | Tatsächliches Ergebnis |
| --- | --- |
| Paper | 1.21.11-132, Commit `c5eb079`, API 1.21.11-R0.1-SNAPSHOT |
| Java | Oracle Java 21.0.12.1+1-LTS-4 auf Windows 11 |
| LuckPerms | 5.5.84, Provider erfolgreich verbunden |
| Startup | Done nach 15,131 s; 27 Module erfolgreich aktiviert |
| `core` | Running, Active Modules 27, Loaded Players 0, LuckPerms Connected |
| `core reload` | sechs Dateien vorbereitet; erfolgreicher Apply in 13 ms |
| Reload-Dateien | config.yml, lobby.yml, chat.yml, private-messages.yml, presentation.yml, daily-quests.yml |
| `warp help` | bestehende Sections plus Show/Hide/Order und Navigator-only-Semantik |
| `warp list` | kontrollierter Empty State: No warps are configured |
| `warp info definitely-not-existing` | kontrolliert: That warp does not exist |
| `spawn`, `setspawn` von Console | beide kontrolliert: Only players can use this command |
| `bukkit:help VapeeCore` | exakt 36 registrierte Root-Commands |
| `version`, `lp info`, `list` | erwartete Paper-/LP-Version; null Online-Spieler |
| `stop` | 27 Module rückwärts deaktiviert, Worlds gespeichert, Prozess Exit 0, Port anschließend geschlossen |
| Log-Audit | keine ERROR-, SEVERE- oder Exception-Zeile |
| Live client test | **not performed** |

Start, Probes und Stop fanden am 02.10.2026 zwischen 11:00:10 und 11:01:24 CEST statt. `target/phase28-paper-smoke.log` enthält den lokalen vollständigen Log. Die vorhandene Runtime-Warpdatei war leer. Es wurden ausschließlich die genannten Lese-/Help-Probes und Core-Reload ausgeführt, keine Warp- oder Spawn-Mutation. SHA-256 vor/nach Start, Reload und Stop bestätigt unveränderte Runtime-Dateien `warps.yml`, `lobby.yml` und `config.yml`.

Paper meldete lediglich seinen Versionshinweis, dass die ausdrücklich angeforderte Minecraft-Version 1.21.11 älter als die aktuelle stabile Minecraft-Linie ist. Der Build 132 ist laut eigener Versionsabfrage aktuell für 1.21.11. Dieser Serverhinweis ist kein VapeeCore-Startup- oder Reload-Fehler.

Die neuen Harnesses sind `WarpCommandHarness`, `NavigatorMenuHarness` und `NavigatorSecurityHarness`; `NavigatorFixture` ist ausschließlich Test-Support und besitzt keinen ausführbaren Harness-Einstieg. Der vorhandene `WarpHarness` wurde erweitert. Die Verifikation deckt Legacy-/Null-/Wrong-Type-Fallbacks ohne Rewrite, Max-Order-Roundtrip, Mutation-Preservation, echte Save-Failure-Grenzen, No-op-Persistence, Permission-First, Completion, sichtbare Sortierung, Pagination, literal Namen, leere/Hidden-Sichten, exakte aktive Bindung, erlaubte Klicktypen, Drags, alle Access-Gates, Stale-Hide/Remove/Reorder, aktuellen Zielort, World-Unavailability, Teleport-Cancellation, Erfolg und doppelte alte Events sowie Quit/World/Disable-Cleanup ab.

Alle ausführbaren `*Harness.java` mit `public static void main` wurden nach dem finalen Clean-Build einzeln in eigenen Java-Prozessen gestartet. Maven Package allein wird nicht als Harness-Ausführung gezählt. Ergebnis-JSON und Einzelprozess-Logs liegen lokal in `target/phase28-harness-results.json` und `target/phase28-harness-logs/`.

### Vollständige Harness-Ergebnisse

| Harness | Checks | Exit |
| --- | ---: | ---: |
| `ActivityHarness` | 153 | 0 |
| `BlackjackFairnessHarness` | 393 | 0 |
| `BlackjackHarness` | 218 | 0 |
| `BlackjackPresentationHarness` | 43 | 0 |
| `BlackjackPreviewHarness` | 16 | 0 |
| `BlackjackTableHarness` | 90 | 0 |
| `ChatHarness` | 17 | 0 |
| `ClanDomainHarness` | 52 | 0 |
| `ClanIntegrationHarness` | 15 | 0 |
| `ClanPersistenceHarness` | 39 | 0 |
| `ClanServiceHarness` | 136 | 0 |
| `ClanCommandHarness` | 83 | 0 |
| `ClanMenuHarness` | 84 | 0 |
| `ClanMenuSecurityHarness` | 37 | 0 |
| `CoreCommandHarness` | 72 | 0 |
| `OnlineStaffTargetGuardHarness` | 268 | 0 |
| `ConfigServiceHarness` | 74 | 0 |
| `DefaultConsistencyHarness` | 12 | 0 |
| `CoinsCommandHarness` | 1.942 | 0 |
| `EconomyIntegrationHarness` | 27 | 0 |
| `EconomyServiceHarness` | 44 | 0 |
| `FriendCommandHarness` | 80 | 0 |
| `FriendDomainHarness` | 29 | 0 |
| `FriendIntegrationHarness` | 28 | 0 |
| `FriendLifecycleHarness` | 12 | 0 |
| `FriendPersistenceHarness` | 28 | 0 |
| `FriendServiceHarness` | 79 | 0 |
| `FriendMenuHarness` | 159 | 0 |
| `FriendMenuSecurityHarness` | 37 | 0 |
| `IdentityHarness` | 26 | 0 |
| `ProfileHarness` | 19 | 0 |
| `LobbyCommandHarness` | 26 | 0 |
| `NavigatorMenuHarness` | 113 | 0 |
| `NavigatorSecurityHarness` | 82 | 0 |
| `LobbyHarness` | 53 | 0 |
| `WarpCommandHarness` | 86 | 0 |
| `WarpHarness` | 118 | 0 |
| `CommandHelpHarness` | 85 | 0 |
| `ModerationCommandHarness` | 3.381 | 0 |
| `ModerationDurationHarness` | 53 | 0 |
| `ModerationBanEnforcementHarness` | 31 | 0 |
| `ModerationDomainHarness` | 112 | 0 |
| `ModerationLifecycleHarness` | 149 | 0 |
| `ModerationMuteEnforcementHarness` | 52 | 0 |
| `ModerationMuteProjectionHarness` | 20 | 0 |
| `ModerationPersistenceHarness` | 523 | 0 |
| `ModerationServiceHarness` | 141 | 0 |
| `OnlineRewardLifecycleHarness` | 42 | 0 |
| `OnlineRewardServiceHarness` | 42 | 0 |
| `LuckPermsAsyncHarness` | 16 | 0 |
| `PermissionDescriptorHarness` | 812 | 0 |
| `PlayerQuestPersistenceHarness` | 21 | 0 |
| `PlayerRewardPersistenceHarness` | 11 | 0 |
| `PlayerVisibilityPersistenceHarness` | 17 | 0 |
| `PlayerSettingsServiceHarness` | 28 | 0 |
| `PlayerVisibilitySettingsHarness` | 11 | 0 |
| `FriendPresenceNotifierHarness` | 8 | 0 |
| `PresenceLifecycleHarness` | 14 | 0 |
| `PresenceServiceHarness` | 15 | 0 |
| `PlaytimeFormatterHarness` | 14 | 0 |
| `PresentationHarness` | 10 | 0 |
| `PrivateMessageSocialHarness` | 117 | 0 |
| `DailyQuestConfigHarness` | 13 | 0 |
| `DailyQuestCycleSelectorHarness` | 18 | 0 |
| `DailyQuestLifecycleHarness` | 14 | 0 |
| `DailyQuestServiceHarness` | 20 | 0 |
| `QuestDefinitionHarness` | 29 | 0 |
| `QuestLifecycleHarness` | 42 | 0 |
| `QuestServiceHarness` | 44 | 0 |
| `RankCommandHarness` | 16 | 0 |
| `RanksCommandHarness` | 12 | 0 |
| `RankServiceHarness` | 23 | 0 |
| `StaffHierarchyServiceHarness` | 109 | 0 |
| `RewardLifecycleHarness` | 40 | 0 |
| `RewardServiceHarness` | 126 | 0 |
| `SeatHarness` | 52 | 0 |
| `SettingsCommandHarness` | 25 | 0 |
| `SettingsMenuHarness` | 66 | 0 |
| `SettingsMenuSecurityHarness` | 34 | 0 |
| `SettingsModuleLifecycleHarness` | 23 | 0 |
| `SettingsNavigationHarness` | 17 | 0 |
| `VisibilityMenuSecurityHarness` | 41 | 0 |
| `VisibilitySettingsMenuHarness` | 29 | 0 |
| `VisiblePlayersMenuHarness` | 74 | 0 |
| `BuildCommandHarness` | 17 | 0 |
| `TeleportCommandHarness` | 894 | 0 |
| `UtilityCommandHarness` | 1.221 | 0 |
| `TeleportParserHarness` | 58 | 0 |
| `UtilityInventoryHarness` | 742 | 0 |
| `UtilityServiceHarness` | 35 | 0 |
| `FriendVisibilityRefreshHarness` | 11 | 0 |
| `IgnoreVisibilityRefreshHarness` | 7 | 0 |
| `VisibilityModuleHarness` | 22 | 0 |
| `VisibilityPolicyHarness` | 28 | 0 |
| `VisibilityServiceHarness` | 10 | 0 |
| `WorldDisplayHarness` | 27 | 0 |

## Dokumentation und Resource-Audit

README nennt Phase 28 als aktuellen Stand und beschreibt den erhaltenen Compass, Metadaten, Admin-UX und aktive Inventory-Sicherheit. Der Developer Guide dokumentiert Ownership, Activity-Injektion, YAML-Regeln, Service-Grenzen, Access-/Teleport-/Lifecycle-Verhalten und neue Harnesses. Dieser Bericht ist die Phase-28-Abschlussreferenz.

`docs/PERMISSIONS.md` bleibt unverändert: Es listet den Warp-Root und seinen bestehenden Admin-Node, keine vollständige Warp-Subcommand-Syntax. Es entsteht deshalb kein veralteter expliziter Show/Hide/Order-Katalog. Folgende historischen Dokumente bleiben byte-identisch zur Baseline: `COMMAND_AUDIT.md`, `MODERATION_FOUNDATION.md`, `MODERATION_TOOLS.md`, `MODERATION_MUTES.md`, `STAFF_HIERARCHY.md`, `PERMISSION_HARDENING.md`, `NOTIFICATIONS_PRESENCE.md` und `FORMATTING.md`.

`src/main/resources/plugin.yml`, `config.yml`, `lobby.yml` und `warps.yml` bleiben byte-identisch. `warps.yml` wird weiterhin leer als `warps: {}` ausgeliefert. Es gibt keine neue Default-Destination und keine Resource-Migration. Das verschachtelte Navigator-Format wird erst bei einer erfolgreichen Warp-Mutation in die Runtime-Datei geschrieben. Manuelles Ändern im laufenden Betrieb wird weder per Watcher noch per `/core reload` eingelesen.

Der vollständige Diff einschließlich aller neuen Dateien wurde gelesen. `git diff --check` ist fehlerfrei. Der Dateikatalog beschränkt sich auf LobbyExperience/Navigator, Warp, deren fokussierte Tests und aktuelle Dokumentation. Die geschützten Resource-/historischen Dokumentpfade wurden nicht bearbeitet und ihr Git-Diff gegen die Baseline ist leer; vorhandene lokale Zeilenenden wurden nicht normalisiert. Keine unbeabsichtigte Root-/Permission-/Default-Änderung, kein fachfremdes Refactoring, keine feste Destination und keine neue Hotbar-Aktion.

## Grenzen und Folgeschritte

Live client test: not performed. Die Tests simulieren die Inventory-/Player-/Event-Grenzen und Paper prüft Start, Commands, Reload und Stop; tatsächliche Compass-Bedienung, Rendering und fremde Teleport-Plugins mit einem Minecraft-Client wurden nicht geprüft.

Phase 29 – Quest Completion kann auf der erhaltenen Architektur beginnen; keine Quest-UI, neuen Producer, Quest Types, Achievements, Battle Pass oder Quest Shop wurden vorweggenommen. Gemeinsamer GUI-/Inventory-/Item-Code bleibt für Phase 30C – GUI / Inventory / Item Cleanup reserviert. Target-controlled Team Teleport Consent bleibt Phase 30G – Staff Interaction & Consent Polish; Phase 28 erweitert keine Team-Teleport-Fähigkeit.

Known Issues: kein bekannter Produktionsdefekt nach den ausgeführten Prüfungen. Verifikationsgrenze bleibt die fehlende tatsächliche Minecraft-Client-Sitzung. Der vorhandene Persistence-Fallback bleibt unverändert und verspricht bei hartem Prozess-/Dateisystemabbruch dieselben bisherigen Grenzen; Phase 28 führt keine neue Transaktionsgarantie ein.

Merge Readiness: Implementierung, vollständige Regression, finaler Clean-Build, realer Paper-/Reload-/Stop-Smoke und Diff-Audit sind abgeschlossen. Phase 28 ist für die lokale Übernahme bereit. Der autorisierte Abschluss ist genau ein lokaler Commit `Phase 28 - Lobby and Navigation Completion`; es wird nicht gepusht.
