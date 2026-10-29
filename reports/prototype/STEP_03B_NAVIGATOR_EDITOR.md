# Prototype Step 03B — Navigator UX und Ingame-Editor

## Stand und Umfang

Branch `codex/navigator-ux-editor`, Basis `15d59b0741fbb9ed9f662ae5b4fd1b7106eba619` auf dem ungemergten Step-03-Branch. `master` steht beim Preflight auf `ec7d163a1b8879f2618b120726f23fd181a03981`; Step 03 ist dort noch nicht enthalten. Frühere Commits bleiben unverändert. 27 Module, sieben Reload-Teilnehmer. Kein Push, Merge, Deployment oder Operator-Server-Test.

Basisprüfung vor Änderungen: Maven clean package PASS, Spotless PASS, **209/209 Harnesses, 31.554 Checks, Failed=0, Missing/Invalid=0**. Alle ursprünglichen Harness-Identitäten bleiben erhalten; `NavigatorSecurityHarness.java` bleibt unverändert. Final: **212/212 Harnesses, 31.734 Checks, Failed=0, Missing/Invalid=0, Maven clean package und Spotless PASS**. Drei neue Harnesses ergänzen die 209 bisherigen.

## Spieleransicht

- Default 27 Slots, optional je Menü 36/45/54. Alte explizite Slots bleiben unverändert; ohne `size` wird die kleinste passende Größe gewählt. Mehrere Seitengruppen reservieren links/rechts in der unteren Reihe (`size-9`, `size-1`) für Pfeile. Explizite Kollisionen sind Fehler; automatische Größenwahl kann wachsen.
- Neue Einträge schlagen 11, 13, 15 vor, danach freie Plätze. Keine Füllscheiben, keine erzwungene Kategorie, keine automatischen Ziele. Neue Konfigurationen enthalten nur das leere Root-Menü, `enabled: false` erhält Legacy.
- ESC schließt normal. Rechtsklick auf einen leeren Untermenü-Platz kehrt zum tatsächlichen Elternmenü und dessen Seite zurück. Rechtsklick auf einen belegten Eintrag aktiviert weiterhin dessen normale Aktion. Kein Close-Button, kein obligatorischer Back-Button. Optionaler `back-slot` ist standardmäßig aus. Ein dezenter lokalisierter Lore-Hinweis erklärt den Rückweg, ohne Chat-Spam.
- Alle Verschachtelungen, Permission-Pfade, Literaltexte, Zähler, Vanish/Visibility-Filter, UUID-Deduplizierung, Materiallimits, Compass-Integration und Legacy-Warp-Liste bleiben erhalten. Attribute, Verzauberungs-/Zusatz-Tooltips, Unbreakable, Farbstoff- und Rüstungsverzierungsdetails werden nur auf Navigator-/Editor-Icons verborgen.
- UNAVAILABLE zeigt klare Lore und führt keinen Teleport/Command aus; Klicks senden keine wiederholten Chatfehler. Echte fehlende/versteckte Warps und Teleportfehler behalten ihre Fehlerbehandlung.

## Praktische Administration ohne YAML

Benötigt `vapeecore.navigator.admin` (Default `op`); Warp-Verwaltung benötigt weiterhin separat `vapeecore.warp.admin`. Editorcommands sind nur für echte Spieler. Vorschau erfordert zusätzlich die normale Lobby-/Profil-/NORMAL-/Activity-Eligibility der Spieleransicht.

1. Am Ziel `/warp set parkour` ausführen. Dies erstellt nur den Warp, keinen Navigator-Eintrag.
2. `/navigator edit` öffnet den Root-Entwurf. Einen leeren Platz anklicken → **Warp hinzufügen** → vorhandenen Warp auswählen. Alle Warps werden aus WarpService angeboten; global versteckte sind entsprechend markiert.
3. Für ein Untermenü einen leeren Platz wählen → **Untermenü erstellen** → `/navigator menu create welten Welten`. Das erstellt und platziert den Link an diesem Platz. Ohne ausgewählten Platz wird ein freier, ausgewogener Platz vorgeschlagen.
4. Eintrag anklicken → **Icon** wählen, oder `/navigator entry icon GRASS_BLOCK`. `/navigator entry name de Welten` ändert nur Deutsch, `en` nur Englisch, `all`/kein Sprachargument setzt den Literaltext für alle Sprachen.
5. Eintrag anklicken → **Untermenü bearbeiten** oder `/navigator menu select welten`. Leeren Platz wählen → **Warp hinzufügen** → `parkour`. Weitere Untermenüs funktionieren gleich; Zyklen werden abgelehnt.
6. Eintrag auswählen: `/navigator entry name all Parkour`, `/navigator entry lore de Springe zum Ziel | Viel Spaß`, `/navigator entry lore en Reach the finish | Have fun`. `lore none` entfernt optionale Lore. Die Trennung `|` erzeugt mehrere Zeilen; keine MiniMessage-Ausführung. Locale-spezifische Änderungen behalten andere Sprachwerte.
7. `/navigator entry permission example.navigator.parkour` beschränkt genau diesen Link. `/navigator entry permission none` entfernt den Gate. Nicht berechtigte Spieler sehen den Eintrag nicht und können ihn auch aus alten Inventaren nicht aktivieren. Parent-Links werden ebenfalls geprüft. Rechte über Bukkit/LuckPerms vergeben, keine Rangnamen hardcodiert.
8. `/navigator entry move root 15 0` verschiebt den ausgewählten Eintrag in Menü `root`, Slot 15, Seitengruppe 0; belegte Positionen, doppelte IDs im Ziel und Zyklen werden abgelehnt. `/navigator entry remove` entfernt den Eintrag, niemals den Warp.
9. `/navigator menu rename de Spielwelten` ändert den ausgewählten Menütitel. `/navigator menu icon FEATHER` setzt das Icon für die Menüauswahl und zukünftige Links; bestehende Links behalten ihre individuell editierten Icons/Namen. `/navigator menu size 36` vergrößert dieses Menü. `/navigator menu back 18` aktiviert optional den kleinen Back-Pfeil, `back -1` deaktiviert ihn. Kollisionen mit vorhandenen Einträgen/Pfeilen werden abgelehnt.
10. `/navigator menu delete welten` entfernt dieses Menü und alle eingehenden MENU-Links, ohne untergeordnete Menüs/Warps zu löschen. Root kann nicht gelöscht werden. Über **Menü auswählen** bleiben unverknüpfte Menüs erreichbar und können erneut platziert werden.
11. `/navigator page 1` wählt die Seitengruppe für weitere Einträge; GUI-Pfeile blättern ebenfalls. Bei 54 Slots zeigt **Weitere Plätze** die Slots 45–53 im Editor. `/navigator select <entry-id>` wählt einen Eintrag eindeutig aus. `/navigator add warp parkour 13`, `add menu welten 11`, `add unavailable future 15` sind gleichwertige enge Command-Alternativen. `/navigator help` und Tab-Vervollständigung zeigen den verfügbaren Umfang.
12. **Vorschau** oder `/navigator preview` rendert den Entwurf mit dem öffentlichen Navigator. Menü-/Seiten-/Zurücknavigation funktioniert; Warp-Klicks teleportieren nicht. `/navigator edit` kehrt zur Sitzung zurück. **Speichern und V2 aktivieren** oder `/navigator save` übernimmt den vollständigen Entwurf unmittelbar; neu geöffnete Compasses zeigen ihn ohne Neustart. `/navigator discard` verwirft ausschließlich den Entwurf.

### Optionale Spielerzählung

Ein Warp ist ein exakter Teleportpunkt. Eine Region ist ein davon unabhängiger inklusiver Quader, in dem sichtbare Online-Spieler gezählt werden. Ohne Region funktioniert der Warp normal und zeigt keinen Zähler. Es gibt keine WorldGuard-Abhängigkeit.

Im Editor den konkreten Warp-Eintrag wählen, z. B. `/navigator menu select welten`, `/navigator select parkour`:

1. Zur ersten Ecke gehen, `/navigator region pos1`.
2. Zur gegenüberliegenden Ecke **derselben Welt** gehen, `/navigator region pos2`.
3. `/navigator region info` zeigt Welt, normalisierte Grenzen, ON/OFF und Stack-Anzeige. Der Eintragsinspektor zeigt dasselbe. Positionen müssen endlich und vollständig sein; eine offene pos1-Auswahl blockiert Save bis pos2 oder clear. Wechsel des ausgewählten Eintrags verwirft die unvollständige Auswahl.
4. Optional `/navigator entry stack true`; die Lore zeigt die volle Zahl, der Stack wird auf das Materiallimit und maximal 64 begrenzt. `stack false` lässt nur die Lore-Zahl.
5. `/navigator region clear` entfernt Region und Stack-Anzeige. Danach optional speichern. Warp-Koordinaten bleiben immer unverändert.

`/warp hide parkour` macht den Warp global für Navigator-Teleports unnavigierbar. Eine Entry-Permission beschränkt dagegen nur den Navigator-Link. Für einen Staff-Warp deshalb eine Entry-Permission verwenden und den Warp bei Bedarf mit `/warp show parkour` navigierbar lassen. Keine Änderung der generischen Warp-/Sichtbarkeitssemantik.

## Speicherung, Konflikte und Schutz

Jede echte Admin-UUID besitzt einen eigenen unveröffentlichten Entwurf. GUI-Aktionen benötigen Permission, identische Sitzung/Revision, den gebundenen Holder, die aktive Inventory-Instanz und das tatsächlich geöffnete Top-Inventar. Itemtexte sind keine Action-IDs. Shift, Number-Key, Doubleclick, Drop und Offhand-Swap können nichts editieren oder speichern. Top-Drags werden blockiert. Permission-Entzug invalidiert die nächste Aktion; Quit/Shutdown lösen Sitzungen und eigene Ansichten auf. Die Moduleigentümerschaft räumt Command/Completer nur auf, wenn sie noch zum Editor gehören.

`navigator.yml` bleibt alleinige Konfigurationsquelle. Startup schreibt bestehende Dateien nicht um. Inaktive gültige V2-Komposition bleibt beim Öffnen des Editors erhalten; fehlerhafte YAML wird nicht still ersetzt. Vollständige Validierung erfolgt vor jeder Draft-Publikation und erneut bei Save. Externe manuelle Änderungen benötigen weiterhin Neustart und werden beim Save als Bytekonflikt erkannt, auch bei reinen Kommentar-/Whitespace-Änderungen. Eine zusätzliche Laufzeitrevision blockiert konkurrierende Admin-Saves selbst bei wiederhergestellten alten Bytes.

Vor Ersetzung wird ein neues exaktes Backup `navigator-backups/navigator-<UUID>.yml` mit CREATE_NEW angelegt und geprüft. `SafeFileWriter` schreibt einen validierten temporären Kandidaten und verwendet Atomic-Move, sofern das Dateisystem ihn unterstützt; andernfalls seinen geschützten Wiederherstellungsmechanismus. Der Bytevergleich wird direkt vor Ersetzung wiederholt. Unkoordinierte externe Programme können zwischen Vergleich und OS-Move schreiben; dies ist keine systemweite Compare-and-Swap-Sperre. Betreiber sollten externe Schreibvorgänge und Ingame-Saves nicht gleichzeitig starten.

Live-Publikation und Invalidation alter öffentlicher Inventare erfolgen auf dem Main Thread nach erfolgreicher Dateiersetzung. Bei fehlgeschlagener Anwendung wird der alte Snapshot restauriert und die Datei bytegenau zurückgeschrieben. Verweigert das Betriebssystem auch diese Rücknahme, bleibt das Original im geschützten Backup und zusätzlich in `navigator.yml.vapeecore-recovery.bak`, soweit schreibbar. Die Runtime fällt sicher auf die leere Ansicht zurück; weitere Sitzungen und Startup bleiben gesperrt, bis der Betreiber den Konflikt anhand des Backups ausdrücklich reconciliert. Keine automatische Löschung/Überschreibung einer bestehenden Recovery-Datei. Dieser doppelte I/O-Fehler wird ausdrücklich als Fehler gemeldet, niemals als erfolgreicher Save.

Nur ein ausdrücklich ausgelöster Save normalisiert YAML-Format/Kommentare. Discard, Vorschau, fehlerhafte Eingaben und bloßes Öffnen ändern weder Datei noch aktive Konfiguration. Keine zweite persistente Konfigurationsquelle, kein neuer Reload-Teilnehmer, keine Änderung an Spieler-/Friend-/Warp-Daten.

## Verifikation und native Annahme

Neue ausführbare Harnesses: `NavigatorCompactHarness`, `NavigatorEditorHarness`, `NavigatorStoreHarness`. Vorhandene V2-/Locale-Erwartungen wurden ausschließlich an die beabsichtigte UX angepasst. Die bisherigen Sicherheitsprüfungen bleiben vollständig; insbesondere `NavigatorSecurityHarness.java` ist unverändert. Kontrollierte echte Dateifehler prüfen Schreiben, Atomic-Move, Fallback, Anwendungsrollback, doppelten Rollbackfehler und externe Änderungen unmittelbar vor Replace. Diese negativen Kontrollen müssen den Fehler tatsächlich auslösen und die jeweilige Schutzwirkung nachweisen.

Finaler kanonischer Lauf: **212/212 Harnesses, 31734 Checks, null Fehler oder fehlende/ungültige Harnesses; Maven clean package PASS, Spotless PASS**. Alle 209 ursprünglichen Identitäten sind enthalten; kein bestehender Harness meldet weniger Checks. Der erste Gesamtlauf fand noch den festen 36-Command-Erwartungswert im Moderationstest; nur diese Zahl wurde auf 37 erhöht, danach wurde der komplette Verifier erneut ausgeführt. Kein Moderationscode wurde geändert. Evidence außerhalb des Repositorys: `C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/prototype-step03b-navigator-editor-20261029/`.

Native Acceptance verwendet eine eigene Wegwerf-Installation Paper 1.21.11 + LuckPerms, separate Worlds/Profiles/Ports und die gebaute Plugin-JAR. Echte Bukkit-Inventories, ItemMeta, Command-Registrierung, Speicherung und Module-Lifecycle werden geprüft; synthetische Player-Transportobjekte sind **keine Minecraft-Clients**. Der Operator-Server wird weder gestartet, gestoppt noch als Fixture verändert.

### Noch manuell im Minecraft-Client prüfen

- Drei-Reihen-Anordnung, explizite größere Untermenüs, Vanilla-Tooltips ohne Attribute und lesbare EN/DE-Lore.
- ESC schließt ohne Wiederöffnung; Rechtsklick auf leeren Platz stellt Elternseite wieder her; belegter Rechtsklick behält Aktion.
- Gesamten Warp-/Untermenü-/Icon-/Text-/Region-/Vorschau-/Save-Workflow mit tatsächlichen Clientklicks ausführen.
- Zwei reale Admins mit konkurrierenden Sitzungen, Permission-Entzug und Staff-/normalem Spieler vergleichen.
- Echte gleichzeitige sichtbare/vanished Spieler an Regionsgrenzen und live gespeicherte Änderungen prüfen.

## Git und Operator-Dateien

Die genehmigte lokale Änderung `.run/Start VapeeCore Dev Server.run.xml` wird weder verändert noch gestaged/committed. Vorher-/Nachher-Diff und SHA-256 müssen übereinstimmen: `EEDE5A220EEC6A8D154D82F2BD1BF29CFEB59215D6B546D83CDB38A73EAF596F`. Operator-Dateiinventar und Hash-Vergleich werden abschließend erfasst; gesperrte oder extern veränderte Dateien werden als solche gemeldet, nicht pauschal als identisch bezeichnet.

Genau ein zusätzlicher lokaler Commit: `Refine Navigator V2 UX and add in-game editor`. Das folgende Inventar wird explizit gestaged; die Run-Datei gehört nicht dazu. Es gibt keine weiteren unerwarteten Git-Änderungen. Historischer Step-03-Bericht bleibt bestehen.

## Abschließende Messwerte und Evidence

| Prüfung | Ergebnis |
|---|---|
| Basis | 209 Harnesses / 31.554 Checks / 0 Fehler |
| Finaler Verifier | 212 Harnesses / 31734 Checks / Failed=0 / Missing/Invalid=0 |
| Maven / Spotless | PASS / PASS (bestehende sechs Pilotdateien) |
| NavigatorCompactHarness | 44 Checks |
| NavigatorEditorHarness | 59 Checks |
| NavigatorStoreHarness | 58 Checks |
| Unveränderte NavigatorSecurityHarness | 78 Checks |
| Vorhandene NavigatorV2Harness / Lifecycle | 62 / 548 Checks |
| Native Paper 1.21.11-132 + LuckPerms 5.5.84 | 527 Checks, zwei getrennte JVMs, beide Exit=0, keine ERROR/SEVERE |
| Native JVM 1 | 347 Laufzeit + 8 tatsächliche Shutdown-Checks = 355 |
| Native Kaltstart-JVM 2 | 164 Laufzeit + 8 tatsächliche Shutdown-Checks = 172 |
| Module / Reload / Commands / Permission-Nodes | 27 / 7 / 37 / 51 |

SHA-256 der nativen getesteten JAR und des finalen kanonischen Build-Artefakts: `CB90FEBBB8DA0F07C97023BA522FBC8159B3102854D3B4A533BFF1F5B77610F3`. Identisch; die native Annahme wurde nach dem letzten Build erneut durchgeführt.

Der erste native Diagnoselauf erwartete am Slot eines vollständig verborgenen Eintrags `null`; tatsächlich zeigte die sichere leere Ansicht dort den allgemeinen Leerzustandsmarker. Ausschließlich die Acceptance-Erwartung wurde auf diesen Zustand und fehlende Namens-/Zähler-Leaks präzisiert. Der abschließende native Lauf prüft die tatsächliche Op-Commandregistrierung, echte InventoryHolder/ItemMeta inklusive HIDE_ATTRIBUTES/HIDE_ADDITIONAL_TOOLTIP, Warp-Picker, mehrstufige Bearbeitung, Locale-Lore, optionale Region, blockierte unvollständige/cross-world Auswahl, sichere Vorschau, GUI-Save, exaktes Backup, Live-Warp-Teleport, konkurrierende Admins, externe YAML-Konflikte, Permission-Entzug, Click/Drag-Schutz und echten Modul-Shutdown. Der Editor bleibt absichtlich bis zum Shutdown geöffnet; Sitzungen, Views, Listener und Command-Eigentümerschaft werden danach geprüft.

Die ursprünglichen nativen Friends-/Clan-RUN_COMMAND-, Rangfarben-, Join-Summary-/Opt-out-, Kopfprofil-, Settings-, Warp-, generischen Activity-/Reward-/Quest- und Reload-Regressionen sind weiterhin enthalten. API-Nachweise ersetzen keinen echten Minecraft-Client; die manuelle Liste oben bleibt offen. Alle Wegwerf-JVMs sind beendet, ihre Serverkopien wurden nach Evidence-Sicherung entfernt.

Evidence: `baseline-results.json`, `final-results.json`, `final-canonical-verification.log`, `harness-check-ledger.json`, `native-verified-final/summary.json`, beide native Main-/Shutdown-Receipts und Console-Logs, getestete JAR/Helper/Plugin-Daten einschließlich Backups, `native-cleanup.json`, `protection-comparison.json`, Run-Diff-/Hash-Receipts und `committed-paths.txt` im oben genannten Evidence-Verzeichnis.

Operator-Inventar: vorher/nachher jeweils **948 Dateien** (922 Serverdateien, 24 Skripte, zwei Run-Konfigurationen). **926 lesbare Dateien byte-identisch**, **13 extern laufend veränderte Serverdateien**, **9 gesperrte Dateien**, keine hinzugefügten/entfernten Pfade. Skripte, Run-Konfigurationen, Navigator-/Warp-Dateien, Plugin-JARs und lesbare Konfigurationen blieben unverändert. Keine Aussage zur Byte-Identität gesperrter Dateien. Der Operator-Server wurde nicht gestoppt oder verändert.

Extern veränderte Dateien des laufenden Operator-Servers:

```text
dev-server\plugins\VapeeCore\players\0d9efbe6-33bf-4cf7-ae67-61d6238b2561.yml
dev-server\world\data\raids.dat
dev-server\world\level.dat
dev-server\world\level.dat_old
dev-server\world\playerdata\0d9efbe6-33bf-4cf7-ae67-61d6238b2561.dat
dev-server\world\playerdata\0d9efbe6-33bf-4cf7-ae67-61d6238b2561.dat_old
dev-server\world\stats\0d9efbe6-33bf-4cf7-ae67-61d6238b2561.json
dev-server\world_nether\DIM-1\data\raids.dat
dev-server\world_nether\level.dat
dev-server\world_nether\level.dat_old
dev-server\world_the_end\DIM1\data\raids_end.dat
dev-server\world_the_end\level.dat
dev-server\world_the_end\level.dat_old
```

Vorher und nachher gesperrt, daher kein vollständiger Hashvergleich möglich:

```text
dev-server\logs\latest.log
dev-server\plugins\LuckPerms\luckperms-h2-v2.mv.db
dev-server\world\entities\r.-1.0.mca
dev-server\world\poi\r.-1.0.mca
dev-server\world\region\r.-1.0.mca
dev-server\world\region\r.0.0.mca
dev-server\world\session.lock
dev-server\world_nether\session.lock
dev-server\world_the_end\session.lock
```

## Exakte Commit-Pfade (22)

```text
docs/DEVELOPER_GUIDE.md
reports/prototype/STEP_03B_NAVIGATOR_EDITOR.md
src/main/java/dev/vapee/core/lobby/experience/LobbyExperienceModule.java
src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorConfig.java
src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorDocument.java
src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditor.java
src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorInventoryHolder.java
src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorListener.java
src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenu.java
src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorStore.java
src/main/resources/lang/de.yml
src/main/resources/lang/en.yml
src/main/resources/navigator.yml
src/main/resources/plugin.yml
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorCompactHarness.java
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditorHarness.java
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorFixture.java
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorLocalizationHarness.java
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorStoreHarness.java
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorV2Harness.java
src/test/java/dev/vapee/core/moderation/command/ModerationCommandHarness.java
src/test/java/dev/vapee/core/permission/PermissionDescriptorHarness.java
```
