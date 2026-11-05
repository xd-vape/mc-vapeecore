# Step 06D.1 – Remove Public Staff Roster

## Stand und Umfang

- Datum: 2026-11-06.
- Branch: `codex/step06d1-remove-public-staff-roster`.
- Ausgangscommit: `36b9208f4f34000abb7e0c3b6ce996c223a07c32`; lokaler `master` und `origin/master` waren vor Beginn identisch. Hauptarbeitsbaum war sauber. Der vorherige Branch-Cleanup war abgeschlossen.
- Genau ein lokaler Implementierungscommit ist vorgesehen. Seine vollständige SHA wird nach Erstellung in der Codex-Abschlussantwort und im externen `delivery-receipt.json` festgehalten. Eine eigene Commit-SHA kann nicht Bestandteil ihres eigenen Commit-Inhalts sein. Im Repository lässt sie sich mit `git log -1 --format=%H -- reports/06d/STEP_06D_1_REMOVE_PUBLIC_STAFF_ROSTER.md` ermitteln.
- Kein Push, Merge, PR, Deployment oder Eingriff am Operator-Server.

## Entfernte Funktion

`/staff list` besitzt keine Sonderroute mehr. Jede nichtleere Argumentfolge, einschließlich `list`, `LIST` und unbekannter Argumente, liefert ausschließlich die bestehende kompakte Usage. Es werden dabei weder Online-Spieler noch Ränge abgefragt, Namen ausgegeben oder Inventare geöffnet. Die Tab-Completion liefert keine Subcommands.

Standardausgabe in genau einer Zeile:

- EN: `[Staff] Usage: /staff`
- DE: `[Team] Verwendung: /staff`

Der PUBLIC/HIDDEN-Zustand hatte ausschließlich die öffentliche Liste gefiltert. Entfernt wurden Map, `isPublic`, Toggle-Action, Status-Item, Status-Kopf-Lore und die dazugehörigen Join-/Quit-/Disable-Aufräumarbeiten. Slot 22 bleibt in beiden privaten Ansichten frei und hat keine Action-Bindung. Auch ein dort eingeschleustes altes Item erhält keine Funktion.

## Erhaltene Grenzen und GUI

`/staff` ohne Argumente öffnet weiterhin ausschließlich das private Panel. Dazu müssen effektiver `vapeecore.staff.panel`-Node und eine aktuell geladene primäre Gruppe aus `staff.hierarchy.protected-groups` gemeinsam vorliegen. OP, Gruppenname ohne Node und Node ohne geeignete primäre Gruppe reichen nicht aus. Runtime, aktuelle identische Player-Session und bestehende Inventory-Bindungen werden weiterhin geprüft.

Die interne Staff-Übersicht zeigt alle für den Betrachter sichtbaren, aktuell geeigneten Staff-Mitglieder. Spielerzielansicht, 18 Content-Slots, Pagination, Filter, Refresh, Schließen und die festen Teleport-/History-/Warn-/Mute-/Kick-Positionen bleiben bestehen. Kopfidentität, vorhandene Skins, Rankfarben und Lokalisierung bleiben erhalten. Externes `canSee` wird weiterhin beachtet.

Target-Hierarchie, effektive Aktionsrechte, aktuelle Zielsession und finale Executor-Prüfungen bleiben bestehen. Die weiterhin relevanten Sicherheits-/Lifecycle-Tests verwenden den erhaltenen MODE-Button statt des entfernten STATUS-Buttons: Creative, Drag, Shift-/Number-/Offhand-/Drop-/Bottom-Interaktionen, fremde Viewer, gefälschte Holder und Item-Profile, gecancelte Events, doppelte Klicks, Rechte-/Gruppen-/Kontextentzug, Offline-Ziele, stale Seiten/Close/Quit, Reconnect, Locale-Rerender, Disable und Re-enable bleiben ausführbar geprüft. Moderationsexecutor und Modul-Acquisition/Rollback wurden nicht geändert.

Es gibt keine neuen Schreibzugriffe auf LuckPerms-Rechte, Spieler-Visibility, Vanish, Namen, Chat-Ränge, Nametags, Scoreboard, Tablist oder persistierte Player-Daten. Module, Reload-Teilnehmer, Dependencies und Scheduler bleiben unverändert.

## Command-Sichtbarkeit

Der bestehende `CommandVisibilityListener` entfernt die tatsächlich eigenen Staff-Root-/Namespaced-Vorschläge ohne effektiven Panel-Node. Ein Node-Inhaber darf die Root sehen; die primäre Gruppe prüft weiterhin ausschließlich der Zugriffspfad. Es gibt keine neue Gruppenabfrage, Permission-Engine, Registrierung oder globale Whitelist.

Die Staff-Root bleibt absichtlich ohne Descriptor-Permission, damit manuell eingegebenes `/staff` die lokalisierte bestehende Staff-/Team-Ablehnung aus dem Executor erhält. Alle Permission-Nodes, Defaults und Children bleiben unverändert; nur die Descriptor-Beschreibung und Usage wurden korrigiert. Ausblenden ersetzt keine Autorisierung. Bei deaktiviertem Visibility-Filter bleibt dessen bestehender No-op-Vertrag erhalten; der Executor schützt das Panel weiterhin.

## Sprache und Operator-Kompatibilität

Die gebündelte Staff-Usage enthält nur `/staff`. Roster-/Status-Legacy-Schlüssel bleiben ungenutzt zur Katalog-/Platzhalterkompatibilität erhalten. Es existiert kein Produktionsaufruf dieser Schlüssel mehr.

`MessageService.usage` erkennt zusätzlich ausschließlich diese zwei exakten alten `staff.usage`-Defaults aus vollständig kopierten Katalogen:

- EN: `Use /staff to open the panel or /staff list for the public team list.`
- DE: `Mit /staff öffnest du das Panel, mit /staff list die öffentliche Teamliste.`

Diese Kopien verwenden zur Laufzeit die neue gemeinsame Usage-Komposition. Abweichende Texte, explizite Formatierungen und eigene `common.usage`-Texte behalten ihren Vorrang. Absichtlich identisch belassene alte Defaults sind technisch nicht von unveränderten Kopien unterscheidbar. Ein eigener Operator-Text kann weiterhin veraltete Worte enthalten; daraus entsteht keine ausführbare Listenfunktion. Roster-/Status-Overrides können ebenfalls keine entfernte Funktion aktivieren.

Kataloge werden über die bestehende Validierung und Prepare/Apply/Rollback geladen. Keine installierte Sprachdatei wird migriert oder überschrieben. Historische Phasenberichte bleiben historisch; aktuelle Entwicklerdokumentation und AGENTS wurden korrigiert.

## Verifikation

Die frühere Step-06D-Abnahme mit 233 Harnesses / 34.615 Checks, Maven/Spotless PASS und 483 nativen Checks ist ausschließlich die Baseline, kein neuer Testlauf.

Neuer vollständiger Lauf mit `pwsh -NoProfile -File .\scripts\verify.ps1`: **Maven clean package PASS, Spotless PASS, Discovered=234, Executed=234, Passed=234, Failed=0, Missing/Invalid=0, Checks=34.791**, Prozess-Exitcode 0. Die Ergebnisliste enthält nachweislich alle 233 ursprünglichen Harnesses und zusätzlich den neuen Removal-Harness. Es wurde kein Harness gelöscht, deaktiviert oder umgangen.

Die Checkzahl ist datenabhängig. Der Vergleich der beiden vollständigen Ergebnisdateien erklärt den Nettozuwachs von 176:

| Harness | Baseline | Neuer Lauf | Ursache |
| --- | ---: | ---: | --- |
| `CatalogUsageHarness` | 2.847 | 2.844 | Drei Produktions-Katalogreferenzen der entfernten öffentlichen Darstellung entfallen; alle Katalogwerte werden weiterhin geprüft. |
| `CommandVisibilityHarness` | 102 | 105 | Drei zusätzliche explizite Staff-Node-/OP-/Revoke-Prüfungen. |
| `StaffPanelHarness` | 126 | 125 | Bewusst entfernte Roster-/Statusfälle ersetzt; alle weiterhin relevanten Sicherheits-/Lifecycle-Fälle verwenden erhaltene Controls. |
| `StaffRosterRemovalHarness` | 0 | 176 | Neuer ausführbarer Verhaltenstest. |
| `GuiAuthoringAuditHarness` | 272 | 273 | Automatischer Testsource-Audit erfasst die zusätzliche Java-Datei. |

Alle anderen Harness-Checkzahlen bleiben gleich. Fokussierte Vorprüfungen von StaffPanel, StaffPanelLifecycle, CommandVisibility, PermissionDenial, CommandUsage, ChatPresentation und PermissionDescriptor bestanden ebenfalls; der neue Removal-Harness bestand separat 176 Checks. Nach der abschließenden Berichtsergänzung bestand der separat ausgeführte Repository-Link-Harness nochmals 120 Checks; dies ist kein zweiter vollständiger Verifier-Lauf.

Der neue `StaffRosterRemovalHarness` prüft ausgeführte Commandpfade, alle Node-/Gruppenkombinationen in EN/DE, fehlende Spielerenumeration, unveränderte Autorität/Appearance, entfernte Controls, wirkungslose Item-Injektion, Kopfidentität/Rankfarbe, alte vollständige Katalogkopien, eigene Overrides, echte Katalogvalidierung und Snapshot-Wiederherstellung. Die vorhandenen Harnesses bleiben erhalten.

## Native Abnahme und Client-Grenze

Isolierte Laufzeit: Paper 1.21.11 Build 132 (`c5eb079`), Java 21.0.12.1, LuckPerms 5.5.84. Eigener Wegwerfserver unter dem externen Evidenzordner, Loopback-Port 25659. Bibliotheken und Runtime stammen aus einer früheren isolierten Testumgebung; die Operator-Installation wird nicht als Fixture verwendet.

Neuer Lauf `native-01`: **522 Checks bestanden, keine Fehler**, eigener Prozess PID 4868 regulär beendet, Exitcode 0. Alle 27 realen Module waren aktiviert. Native Commandbaum-/Visibility-Prüfungen decken Root- und Namespaced-Staff ohne Node, Node allein, geeignete primäre Gruppe allein und beide gemeinsam ab. Reale Inventory-/SkullMeta-Prüfungen bestätigen das erhaltene interne Panel und den entfernten Status; ein eingesetzter Kopf auf Slot 22 bleibt ohne Action. EN/DE werden über registrierte Commands geprüft, einschließlich der weiterhin lokalisierten manuellen Denials ohne Descriptor-Gate. Vollständig kopierte alte Sprachkataloge, individuelle farbige Usage-Overrides und Wiederherstellung wurden über echte sieben-Teilnehmer-Reloads geprüft. Native Namespace-, Fremdcommand-, Permission-Grant/Revoke- und Lifecycle-Kontrollen aus Step 06D bleiben Teil dieser neuen Abnahme.

SHA-256 von gebautem und in die Wegwerf-Fixture kopiertem Plugin-JAR identisch: `721E09C08C41FD690A6EE10A2855BE8E0451C03F0408DBC6E7EE3EB0ECFDADDA`. Es wurde kein Artifact auf den Operator-Server kopiert.

Die Abnahme verwendet echte Bukkit-Registrierung, Paper-Inventare/ItemMeta/SkullMeta, geladene LuckPerms-Nutzer und effektive Permission-Abfragen, echte Reloads und PlayerCommandSendEvents. Player/InventoryView und Brigadier-CommandSource sind kontrollierte synthetische Grenzen, keine verbundenen Minecraft-Clients. Paper bereitet den nutzbaren Commandbaum nativ vor; die Prüfung behauptet keine echten Client-Pakete.

Offen bleibt die visuelle Minecraft-Client-Abnahme dieser Änderung: private Staff-/Spieleransicht, Seiten-/Zielnavigation, leere ehemalige Statusposition, Rankfarben/Skins, Hover und EN-/DE-Usage sowie Root-Vorschläge nach Permission-Wechsel. Frühere visuelle Step-06D-Rückmeldungen zählen hierfür nicht als neue Abnahme.

## Dateischutz und Evidenz

Vor Beginn wurden SHA-256-Snapshots von 1.479 getrackten/nichtignorierten Dateien beider Worktrees einschließlich der zwei ausdrücklich geschützten ignorierten Receipts erstellt. Der Hauptarbeitsbaum wird nur für die unten genannten Dateien verändert. Der bestehende `chat-polish-wt` samt ungetracktem `reports/CHAT_PRESENTATION_POLISH.md`, Recovery-Branches, `master`, Remote-Refs und beide Receipts bleiben erhalten. Operator-Welten/Logs/Player-Dateien werden weder gelesen noch als bytegleich behauptet.

Der Schutzvergleich bestätigt 1.462 unveränderte ursprüngliche Dateien und ausschließlich die 17 vorgesehenen geänderten ursprünglichen Dateien; neu sind nur Report und fokussierter Harness. Keine unbekannten lokalen Änderungen. Beide Worktree-Pfade bleiben bestehen, die geschützte fremde Worktree-Datei und dessen ursprünglicher ungetrackter Status bleiben unverändert. Bestehende Branch-/Remote-/Tag-Refs sind unverändert. Die Run-Diffs bleiben leer und ihre SHA-256-Werte entsprechen dem Ausgangszustand; beide Receipt-Hashes entsprechen ebenfalls exakt der Baseline.

Geschützte Run-Konfigurationen, Ausgangs-SHA-256:

- `.run/Start VapeeCore Dev Server.run.xml`: `EEDE5A220EEC6A8D154D82F2BD1BF29CFEB59215D6B546D83CDB38A73EAF596F`
- `.run/Build and Deploy VapeeCore.run.xml`: `FFE3EDC5279855568BA0E906434D82A2901540D42C0976202C4F3DFCE06E8E56`

Externes Evidenzverzeichnis: `C:\Users\vapee\.codex\visualizations\2026\10\25\01a17432-7597-7ff1-bc9d-34201a027523\step06d1-remove-public-staff-roster-20261106`.

Dort liegen Ausgangssnapshots, fokussierte Build-Logs, kanonisches Log/Ergebnis, nativer Helper und Startskript, Artifact-Hashes, Paper-Logs, native Ergebnisse, abschließender Schutzvergleich und Commit-Receipt. Diese Testartefakte werden nicht ins Projekt eingecheckt.

## Exakte beabsichtigte Commit-Pfade

1. `AGENTS.md`
2. `docs/COMMAND_DEVELOPMENT.md`
3. `docs/DEVELOPER_GUIDE.md`
4. `docs/PERMISSIONS.md`
5. `docs/development/chat-design-system.md`
6. `reports/06d/STEP_06D_1_REMOVE_PUBLIC_STAFF_ROSTER.md`
7. `src/main/java/dev/vapee/core/message/MessageService.java`
8. `src/main/java/dev/vapee/core/permission/CommandVisibilityListener.java`
9. `src/main/java/dev/vapee/core/staff/StaffPanel.java`
10. `src/main/resources/lang/de.yml`
11. `src/main/resources/lang/en.yml`
12. `src/main/resources/plugin.yml`
13. `src/test/java/dev/vapee/core/clan/gui/CommandUsageHarness.java`
14. `src/test/java/dev/vapee/core/clan/gui/PermissionDenialHarness.java`
15. `src/test/java/dev/vapee/core/message/ChatPresentationHarness.java`
16. `src/test/java/dev/vapee/core/permission/CommandVisibilityHarness.java`
17. `src/test/java/dev/vapee/core/permission/PermissionDescriptorHarness.java`
18. `src/test/java/dev/vapee/core/staff/StaffPanelHarness.java`
19. `src/test/java/dev/vapee/core/staff/StaffRosterRemovalHarness.java`
