# Navigator — Regionshöhe und inklusive Blockzählung

## Auftrag und Ausgangspunkt

Begrenztes Follow-up auf `codex/navigator-ux-editor`, HEAD `929ccbb5e14277de4f0c3a893de793bf701a862a`. Bestehende Commits bleiben unverändert. Vor Änderungen: vollständiger kanonischer Verifier **215/215 Harnesses, 31.966 Checks, Failed=0, Missing/Invalid=0, Maven clean package und Spotless PASS**. Die bekannte lokale Run-Änderung wird separat erhalten.

Der erste Sandbox-Testversuch wurde nach `AccessDeniedException` bei atomaren Umbenennungen eigener temporärer Persistenz-Fixtures beendet. Dieselbe unveränderte Basis bestand anschließend mit den nötigen Ausführungsrechten vollständig. Die Diagnose wird getrennt vom erfolgreichen Basislauf aufbewahrt; kein Produktcode wurde dafür angepasst.

## Bedienung und bestehende Regionen

- Im bestehenden Regionsscreen zeigt Slot 6 **Höhe: Gesamte Welthöhe** oder **Höhe: Manuelle Höhe**; die Lore erklärt Auswahl, Wechsel und Draft/Save.
- Neue Regionen: standardmäßig gesamte Höhe der tatsächlich markierten Welt, `getMinHeight()` bis einschließlich `getMaxHeight() - 1`. Keine fest codierten Overworld-/Nether-Grenzen.
- Linksklick markiert weiterhin Pos1, Rechtsklick Pos2. X/Z entsprechen exakt den beiden markierten Blockkoordinaten. Manuelle Höhe übernimmt auch die Y-Blockkoordinaten, unabhängig von der Reihenfolge der Ecken. Die vorhandenen Positionscommands benutzen dieselbe Logik und den Block der Fußposition.
- Umschalten einer vorhandenen Draft-Region verändert ausschließlich Y. Zurück auf manuell stellt innerhalb derselben Sitzung die vorherigen manuellen Y-Grenzen wieder her. Die Mode-Auswahl ist an Menü/Entry gebunden, folgt einem erfolgreichen Verschieben und wird beim Clear/Entfernen freigegeben. Eine offene Pos1 behält ihre bisherige Ownership.
- Bestehende Regionen werden weder beim Öffnen noch durch Reload oder gewöhnliches Save automatisch erweitert. Bei einer ausdrücklich gewünschten Erweiterung wird die **Regionswelt** nachgeschlagen, nicht die momentane Welt des Administrators. Eine nicht geladene Regionswelt verweigert die Erweiterung ohne Mutation oder automatisches Laden.
- Weiterhin nur Draft bis explizites Save; bestehende Backups/Konfliktprüfung, Permissions, Preview und GUI-Identitäts-/Revisionsschutz gelten unverändert. Stack-Opt-out, Name, Lore und Eintragsdaten bleiben erhalten.

Kein zusätzlicher YAML-Key, keine Migration, Konfigurationsquelle oder persistenter Cache. Gespeichert werden weiterhin die konkreten bestehenden min/max-Koordinaten. Beim erneuten Öffnen wird ein exakt weltweiter gespeicherter Y-Bereich als volle Höhe erkannt, sofern diese Welt geladen ist. Ändern sich später Weltgrenzen, werden alte gespeicherte Grenzen nicht automatisch angepasst. Frühere manuelle Eckhöhen werden nach Save/Neustart nicht zusätzlich gespeichert; nach Umschalten auf manuell bei Bedarf erneut markieren.

## Ursache und Korrektur der Zählgrenze

Vorher verglich `contains` rohe Spielerkoordinaten direkt mit dem maximalen Eckpunkt. Bei einer markierten maximalen Ecke X=2 fehlte deshalb der größte Teil des Blocks (z. B. Spieler X=2,5). Entsprechend fehlte bei Y=`maxHeight - 1` der obere Anteil des letzten Blocks.

Nun gilt auf jeder Achse **`floor(min) <= floor(playerPosition) <= floor(max)`**. Beide ausgewählten Eckblöcke sind vollständig enthalten; der Beginn des nächsten Blocks ist ausgeschlossen. Abrunden statt Abschneiden Richtung null ist insbesondere bei negativen Koordinaten notwendig. Bezugspunkt bleibt die Fußposition des Spielers, keine neue Hitbox-/Volumenüberlappung. Vorhandene gebrochene Grenzen bezeichnen ihren jeweiligen Block; ihre gespeicherten Werte werden nicht umgeschrieben. Null/ungültige Locations und fremde Welten bleiben ausgeschlossen. Online-, Sichtbarkeits-, Vanish-/Invisible- und UUID-Deduplizierungsregeln bleiben erhalten.

API-Grundlagen: [Paper WorldInfo](https://jd.papermc.io/paper/1.21.11/org/bukkit/generator/WorldInfo.html) dokumentiert das exklusive Weltmaximum; [Paper Location](https://jd.papermc.io/paper/1.21.11/org/bukkit/Location.html#getBlockX()) dokumentiert das Abrunden der Blockkoordinaten.

## Verifikation

Gezielte ausführbare Checks decken vier unterschiedliche Höhenbereiche (-64..319, 0..255, -128..511, 64..191), beide Modi per tatsächlicher Tool-Eventlogik, unveränderte X/Z, manuelle Rückkehr, EN/DE-GUI, neue/bestehende Regionen, Save/Reopen, nicht geladene Welten, Entry-Kontext/Move, stale/foreign GUI und Permission-Entzug ab. Auf allen drei Achsen werden minimale/maximale Randblöcke, negative gebrochene Positionen und der exakt nächste ausgeschlossene Block geprüft; `count` wird zusätzlich zu `contains` ausgeführt. Die bisherigen Tool-Sicherheits-/Lifecycle-, Editor-, Store- und Sichtbarkeitstests bleiben erhalten.

| Prüfung | Ergebnis |
|---|---|
| Kanonischer Verifier, vollständig | **216/216 Harnesses, 32.086 Checks, Failed=0, Missing/Invalid=0** |
| Maven clean package / Spotless | PASS / PASS, unveränderte sechs Spotless-Pilotdateien |
| NavigatorRegionHeightHarness | 98 Checks |
| NavigatorRegionToolHarness | 79 Checks, einschließlich aller bisherigen 59 |
| Region / Editor / Editor UX / Final Presentation / Security / Store | 28 / 59 / 127 / 32 / 78 / 58 Checks |
| Native JVM 1 | 688 Laufzeit + 12 Shutdown = 700 Checks |
| Native Kaltstart-JVM 2 | 507 Laufzeit + 12 Shutdown = 519 Checks |
| Native gesamt | **1.219 Checks, beide Exit=0, ERROR/SEVERE=0** |

Alle bisherigen 215 Harness-Identitäten bleiben erhalten; kein bestehender Harness meldet weniger Checks. Hinzu kommen 98 Höhen-Checks, 20 Tool-Checks, ein GUI-Audit-Check für den neuen Button und ein Dokumentationslink-Check. Der vollständige finale Verifier bestand im ersten Gesamtlauf nach der Implementierung.

Native Acceptance: Paper **1.21.11-132**, LuckPerms **5.5.84**, eigene Installation auf **127.0.0.1:25607**. Echte World-/Block-/Location-, Paper-ItemStack-/ItemMeta-/PDC- und Eventobjekte, CraftInventory-Storage mit kontrolliertem PlayerInventory-/Player-Transport, keine Netzwerkclients. Beide Modi werden über den echten GUI-Button und die Tool-Eventlogik in Overworld, Nether und End geprüft. Enthalten sind Weltminimum/oberster Block/nächster ausgeschlossener Block, negative gebrochene X/Z, manuelle Rückkehr und Neumarkierung, Save/Reopen und unveränderte gespeicherte manuelle Grenzen im kalten zweiten JVM-Prozess. Die bisherigen Permissions-, Sichtbarkeits-, Inventory-/Ownership-, Stale-/Foreign-PDC-, Restart-Orphan-, Join-, Save-Konflikt- und Shutdown-Regressionen bleiben enthalten, ebenso die übrigen Social-/Rank-/Quest-/Reward-/Reload-Checks des bisherigen nativen Helfers.

Native getestete JAR und vollständiges kanonisches Build sind byteidentisch: SHA-256 **`19ABB06446D2809092A230A2A3361C9DF870E1B7517325633DD656F2F74DC748`**.

Drei diagnostische native Versuche bleiben getrennt dokumentiert: Der alte Helfer erwartete X=10,01 bereits außerhalb des maximalen Blocks X=10. Er prüft jetzt sowohl dessen Einschluss als auch X=11 als nächsten ausgeschlossenen Block. Anschließend kollidierte der zusätzliche gespeicherte Test-Menülink im Kaltstart mit einem vom alten Helfer bewusst leer erwarteten Root-Slot; eine erste Ausweichposition außerhalb der bisherigen Root-Größe änderte zudem die automatische Größe. Der zusätzliche Fixture-Link verwendet nun eigene freie Slots 15/17 innerhalb der bisherigen Root-Größe. Die bisherigen Assertions bleiben bestehen. Alle diese Korrekturen betrafen ausschließlich den nativen Testhelfer, nicht den Produktcode oder die getestete JAR. Der abschließende Lauf `native-height-acceptance` ist über beide JVMs vollständig fehlerfrei.

Evidence-Verzeichnis: `C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/navigator-region-height-20261030/`. Enthalten sind Basis-/Final-Receipts und vollständige Verifier-Logs, `harness-check-ledger.json`, gezielte Harness-Logs, der native Helfer, die drei Diagnoseläufe sowie `native-height-acceptance/summary.json`, JVM-/Shutdown-Receipts, Console-Logs, getestete JARs, Plugin-Daten/Backups und Tool-NBT. Alle vier eigenen Serverkopien wurden nach Evidence-Sicherung, bestätigten JVM-Exits und freigegebenen Session-Locks entfernt (`native-cleanup.json`).

## Schutz und verbleibende Clientprüfung

Kein Einsatz des Operator-Servers als Fixture, kein Start/Stop, Deployment, Push oder Merge. Native Tests verwenden eine eigene Installation, eigene Welten/Playerdaten/Configs und ausschließlich Kopien von Paper/Libraries/LuckPerms. Echte Client-Optik, tatsächliche Adventure-Klickpakete und Bedienung mit zwei physischen Clients bleiben manuell zu prüfen; synthetische Player-Transporte werden nicht als Client-Bestätigung ausgegeben.

Die Run-Datei ist per vollständigem Git-Diff und SHA-256 vor/nachher **identisch**: `EEDE5A220EEC6A8D154D82F2BD1BF29CFEB59215D6B546D83CDB38A73EAF596F`. Sie bleibt als genehmigte lokale Änderung ungestaged/uncommitted. Alle 24 Skripte und beide Run-Dateien sind im Schutzvergleich identisch.

Operator-Snapshot: **950 Pfade vorher, 949 nachher; 927 hash-identisch, 13 lesbare Dateien extern verändert, neun wegen anfänglicher Locks nicht per Hash vergleichbar, null neu, ein externer Pfad entfernt**. Verändert wurden Player-/World-/Stats-/Raid-/level.dat-Dateien; entfernt wurde `dev-server/.vapeecore-dev/server.pid`. Zu Beginn waren u. a. latest.log, die LuckPerms-Datenbank und Welt-/Sessiondateien gesperrt, danach lesbar. Dies passt zu externem Betrieb/Beenden des Operator-Servers während des Auftrags; der Agent hat ihn weder gestartet noch gestoppt und keine seiner Daten geschrieben. Für diese Dateien wird ausdrücklich keine Bytegleichheit behauptet. Exakte Pfade und Leseergebnisse stehen in `protection-before.json`, `protection-after.json` und `protection-comparison.json`.

Es gibt keine unerwarteten getrackten Arbeitsbaumänderungen. Außer der bekannten Run-Ausnahme betreffen die lokalen Änderungen ausschließlich die zehn unten genannten Pfade. Alle Änderungen bleiben lokal und ungestaged; für diesen Folgeauftrag wurde kein zusätzlicher Commit erzeugt. Bestehender HEAD und Branch bleiben erhalten.

## Genaue Änderungspfade

```text
docs/DEVELOPER_GUIDE.md
reports/prototype/STEP_03D_REGION_HEIGHT_FOLLOWUP.md
src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditor.java
src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorRegion.java
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditorHarness.java
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditorUxHarness.java
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorFixture.java
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorRegionHarness.java
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorRegionHeightHarness.java
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorRegionToolHarness.java
```

`NavigatorRegionTool`, Konfigurationsparser/-Serializer, Player-Daten, Runtime-Module, Scheduler und Reload-Reihenfolge selbst bleiben unverändert: 27 Module, sieben Reload-Teilnehmer.
