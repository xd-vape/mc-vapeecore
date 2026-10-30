# Prototype Step 03D — Navigator Final UX und Regionswerkzeug

## Ausgangspunkt

Branch `codex/navigator-ux-editor`, Ausgangs-HEAD `5de969dbd69ab03a72ff7c793ae64a8bfc80e433`. Vor Änderungen wurde der gesamte kanonische Verifier ausgeführt: **213/213 Harnesses, 31.863 Checks, Failed=0, Missing/Invalid=0; Maven clean package und Spotless PASS**. Frühere Commits bleiben unverändert. Nur `.run/Start VapeeCore Dev Server.run.xml` war als genehmigte lokale Änderung vorhanden.

Die Änderung bleibt im vorhandenen Navigator/LobbyExperience: weiterhin 27 Module und sieben Reload-Teilnehmer, keine neue Konfigurationsquelle oder allgemeine GUI-/Itemverwaltung. Tests nutzen ausschließlich eigene Dateien und eine Wegwerf-Paper-Installation. Operator-Welten, Player-/LuckPerms-Daten, installierte Konfigurationen und der reale Server werden nicht als Fixtures verwendet. Kein Push, Merge oder Deployment.

## Schlanke Spieleransicht

- Warpname und optionale selbst verfasste EN/DE-Lore bleiben erhalten. Automatische Teleportinstruktionen entfallen auch im Legacy-Warp-Navigator.
- Gezählt wird nur bei tatsächlich verfügbarer Warp-Destination mit expliziter Region und nach denselben bestehenden Online-/World-/Visibility-/Vanish-/UUID-Deduplizierungsregeln. Ohne Region entsteht kein Bevölkerungsindikator.
- Gezählte Warps verwenden standardmäßig die ItemStack-Menge. Fehlendes `count.stack-amount` bedeutet jetzt true; ein ausdrücklich gesetztes false wird unverändert respektiert und zeigt weiterhin die bisherige lokalisierte Lore-Zahl. Neue Regionszuweisungen aktivieren Stackdarstellung; beim Ersetzen einer bestehenden Region bleibt deren explizites false erhalten.
- 1..Materiallimit, maximal 64: tatsächliche Zahl als legale Menge, keine zusätzliche automatische Count-Lore. Menge 1 hat im Vanilla-Client keinen sichtbaren Zahlenbadge; dies ist bewusst die normale Stackdarstellung.
- Null Spieler: Menge **1**, damit das Icon sichtbar/anklickbar bleibt, plus exakt **0 players / 0 Spieler** in der Lore. Dies ist der dokumentierte Null-Fallback und keine vorgetäuschte Spielerzahl.
- Oberhalb des zulässigen Materiallimits: Menge wird sicher geklemmt und die Lore ergänzt lediglich die vollständige Zahl, z. B. **74 players / 74 Spieler**. Auch bei nicht stapelbaren Icons bleibt Menge 1 legal; die exakte Zahl steht dann beim Überlauf in dieser kurzen Lore.
- Unavailable-/fehlende Destinationen behalten sinnvolle Lore bzw. Teleportfehler und zeigen keine Regionszahlen. Custom Lore wird weder ersetzt noch als Befehlsquelle interpretiert.
- Der Rückweg bleibt Rechtsklick auf leeren Untermenü-Slot mit echter Elternseite. Statt wiederholter Warp-Lore erscheint einmal beim ersten erfolgreichen Untermenü-Open ein lokalisierter ActionBar-Hinweis. Paging/In-place-Refresh wiederholen ihn nicht. Es entstehen keine Chatmeldungen bei normaler Navigation. ESC und optionale Back-Icons bleiben unverändert.

## Regionswerkzeug verwenden

1. Mit `vapeecore.navigator.admin` `/navigator edit` öffnen, vorhandenen Warp auswählen und **Spielerzähler-Region** öffnen.
2. **Region markieren** anklicken, alternativ `/navigator region mark`.
3. Ein freier, nicht durch aktive Lobby-Items reservierter Hotbar-Platz erhält das klar benannte temporäre Werkzeug. Ist keiner frei, wird ausschließlich eine kurze Meldung gezeigt; kein Item wird verschoben, ersetzt oder gespeichert.
4. Linksklick auf einen echten Block setzt Pos1. Rechtsklick auf einen zweiten Block derselben Welt setzt Pos2. Es gelten die tatsächlichen ganzzahligen Blockkoordinaten, nicht die aktuelle Fußposition des Administrators.
5. Die bestehenden inklusiven NavigatorRegion-Grenzen werden normalisiert. Es werden exakte Eckkoordinaten verwendet, keine zusätzliche Max+1-Fläche oder neue Zählsemantik.
6. Abschluss schreibt die Region nur in den bereits vorhandenen unsaved Draft und entfernt das Werkzeug. **Erst normales explizites Save** schreibt `navigator.yml` und publiziert die Runtime.

`/navigator region cancel` beendet die Werkzeugvergabe. Eine offene Pos1 bleibt ihrem ursprünglichen Menü/Eintrag zugeordnet; mit den bestehenden `pos2`-/`clear`-Commands kann sie fortgesetzt/entfernt werden. `clear` entfernt wie bisher Region und offene Auswahl. Ein Weltfehler verwirft weder Werkzeug noch Pos1. Ein absichtlicher Wechsel des Menü-/Entry-Kontexts beendet die Tool-Lease, behält jedoch den Step-03C-Pending-Kontext mit dessen eigener Rückmeldung.

Die bisherigen `/navigator region pos1|pos2|info|clear` bleiben Fallbacks. Ein solcher schreibender Command beendet eine eventuell aktive Werkzeugvergabe, damit keine alte Item-Auswahl später hineinwirkt. `info` ist rein lesend. `/navigator edit` beendet die temporäre Vergabe und öffnet ausdrücklich die GUI.

## Authority, Inventar- und Lifecycle-Schutz

Das Werkzeug verwendet einen eigenen PDC-Key `vapeecore:navigator_region_tool`. Der Wert enthält Owner-UUID, Sitzungs-Nonce, einmalige Tool-Nonce, Menü-ID und Entry-ID. Zur tatsächlichen Aktion werden zusätzlich die aktive serverseitige Lease, identische ausgegebene Item-Metadaten/Menge, der reservierte Slot und die aktuell autorisierte Editor-Sitzung geprüft. Material, sichtbarer Name, Lore und PDC allein sind keine Berechtigung. Foreign/stale/forged Kopien können keine Region ändern.

Die Vergabe folgt der vorhandenen Lobby-Ownership-Konvention, reale belegte Slots zu erhalten. Verwendet wird nur ein leerer, nicht für konfigurierte Lobby-Items reservierter Hotbar-Slot. Es gibt bewusst kein Item-Escrow und keine neue persistente Wiederherstellungsdatei. Beim Abschluss wird nur das eigene temporäre Item entfernt; ein extern ersetztes echtes Item wird nicht gelöscht. Die bisherige Held-Auswahl wird nur restauriert, wenn die Tool-Lease sie weiterhin besitzt. Eigenständiges Wechseln des Held-Slots beendet die Auswahl und wird respektiert.

Clicks/Drags während der aktiven Auswahl, Number-Key-/Offhand-/Creative-Transfers, Drop, Hopper-/Inventory-Moves, Pickup, Armorstand-/Entity-/Hanging-Aktionen sowie BlockDamage/Break/Place werden gegen unbeabsichtigte Änderungen geschützt. Der Selector ruft keine Blocksetter und keinen Teleport auf. Main-Hand wählt; Offhand-Events bleiben inert und gecancelt. Eine kurze Ein-Tick-Sperre deckt nach Completion nachfolgende Offhand-Aktivierung ab.

Bei einem gecancelten Drop/Cursor-Vorgang kann Vanilla das Item erst nach dem Callback zurückgeben. Deshalb gibt es eine deduplizierte, featurelokale Next-Tick-Bereinigung: gültige einzelne Ausgabe bleibt erhalten, stale/forged/duplizierte Rückgaben werden entfernt. Kein wiederkehrender Scheduler. Pending-Aufgaben werden beim Module-Shutdown gecancelt und ihre betroffenen Inventare bereinigt.

Logout, Tod/KeepInventory-Drops, Discard, Save-Versuch einschließlich Konflikt, Permission-Entzug und Module-Shutdown lösen die Vergabe. Permission-Entzug wird auf der nächsten Use-/Move-/Held-/Inventory-/Command-Interaktion geprüft; auch ein unverändert aussehendes Item besitzt dann keine Action-Authority. Legitimes Gehen und Weltreisen erhalten die Auswahl, solange Sitzung/Entry und Slot weiterhin passen. Join und Module-Enable bereinigen verwaiste eigene PDC-Marker nach einem Restart; echte Items hatten den Slot nie verlassen. Ein Neustart setzt naturgemäß die serverseitigen Nonces/Leases zurück.

Für Adventure-Klicks setzt die Ausgabe die Paper-Komponente `CAN_BREAK` mit einem Wildcard-BlockPredicate; dies dient dem Adventure-Client-Hitpfad. Die Serverlistener lesen auch bereits untersagte Interaktionen, setzen jedoch niemals zuvor verweigerte Block-/Item-Aktionen auf ALLOW und canceln zusätzlich Damage/Break/Place. Bestehende Lobby-Schutzflags werden nicht geändert. API-Grundlagen: [Paper CAN_BREAK](https://jd.papermc.io/paper/1.21.11/io/papermc/paper/datacomponent/DataComponentTypes.html#CAN_BREAK), [ItemAdventurePredicate](https://jd.papermc.io/paper/1.21.11/io/papermc/paper/datacomponent/item/ItemAdventurePredicate.html). Die tatsächliche Client-Paketabgabe in Adventure muss zusätzlich im manuellen Test geprüft werden.

## Command-/GUI-Vertrag

Reine Daten-/Auswahlcommands öffnen grundsätzlich kein Inventar mehr. Dazu gehören `menu select`, `select`, `add`, `menu create/rename/icon/size/back`, `entry ...` und `region pos1/pos2/info/clear`. Eine eigene alte Editoransicht wird nötigenfalls geschlossen/invalidiert, ohne neu geöffnet zu werden. `edit` und `preview` sind die ausdrücklichen GUI-Öffner. GUI-Klickaktionen navigieren weiterhin gezielt zu Aktions-, Größen-, Icon- oder Regionsscreens. Save/Discard schließen und räumen auf. Vollständige Hilfe bleibt allein über `/navigator help` verfügbar; alltägliche Fehler liefern gezielte kurze Meldungen.

## Verifikation

Alle 213 bisherigen Harness-Identitäten bleiben erhalten. Beabsichtigte frühere Erwartungen an automatische Warp-Lore, wiederholte Back-Lore und implizites Öffnen wurden an den neuen Vertrag angepasst; reine Command-Tests verwenden keinen automatisch öffnenden Wrapper. Bestehende GUI-Szenarien öffnen mit explizitem `edit`. `NavigatorSecurityHarness` bleibt unverändert.

Neu: `NavigatorFinalPresentationHarness` für Null/1/Mehrfach/Overflow, Materiallimits, explizites false, keine Region, EN/DE, Author-Lore, versteckte Spieler, kontextuellen Hint und reine Commands. `NavigatorRegionToolHarness` führt die echte PDC-/Lease-/Event-/Draft-Logik aus, mit isolierten Item-/Player-/World-API-Adaptern. Native Acceptance prüft dieselben Grenzen zusätzlich mit echten Paper-ItemStacks, ItemMeta/PDC, Inventargrenzen, Events und World-/Blockobjekten.

**Final: 215/215 Harnesses, 31.966 Checks, Failed=0, Missing/Invalid=0; Maven clean package und Spotless PASS. Native Paper/LuckPerms: 976 Checks in zwei getrennten JVMs, beide Exit=0, ERROR/SEVERE=0.** Evidence: `C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/prototype-step03d-navigator-final-ux-20261030/`.

## Noch im echten Minecraft-Client prüfen

- Minimale Tooltips, Menge 1 ohne Zahlenbadge, Null- und Overflow-Fallback in EN/DE sowie nicht stapelbare Icons.
- Einmalige ActionBar für Back, Paging, verschachtelte Menüs und ESC.
- Mark Region → Linksklick/Rechtsklick in geschützter Lobby und Adventure, danach Weltfehler korrigieren und Save.
- Volle Hotbar, echte Compass-/Player-Menu-Items, Offhand, Drop, Inventar-/Container-/Creative-Transfer sowie Cancel ohne Itemverlust.
- Reine Commands aus dem Chat bleiben ohne unerwartete GUI; normale GUI-Aktionsmenüs funktionieren.
- Zwei echte Admins, Revocation, Save-Konflikt, Logout und Neustart mit offener Auswahl.

Ein physischer Minecraft-Client wurde für diesen neuen Build nicht verbunden; frühere vom Nutzer bestätigte Clienttests werden nicht als Step-03D-Annahme ausgegeben.

## Git und Operator-Dateien

Run-Konfiguration vor/nachher per vollständigem Git-Diff und SHA-256 geschützt; erwarteter Hash `EEDE5A220EEC6A8D154D82F2BD1BF29CFEB59215D6B546D83CDB38A73EAF596F`. Sie bleibt ungestaged und uncommitted. Operator-Snapshots unterscheiden identische, extern veränderte, fehlende und gesperrte Dateien ausdrücklich; keine pauschale Gleichheitsbehauptung bei Locks.

Genau ein neuer lokaler Commit: `Polish Navigator tooltips and region selection workflow`. Exakte Pfade und Schlussmesswerte stehen unten. Keine weiteren unerwarteten lokalen Git-Änderungen; die genehmigte Run-Ausnahme bleibt separat erhalten. Kein Push, Merge, Deployment oder Start der nächsten Phase.

## Schlussmesswerte und genaue Pfade

| Prüfung | Ergebnis |
|---|---|
| Unveränderte Basis | 213 Harnesses / 31.863 Checks / 0 Fehler |
| Vollständiger kanonischer Verifier | 215 Harnesses / 31.966 Checks / 0 Fehler / Missing/Invalid=0 |
| Maven clean package / Spotless | PASS / PASS (bestehende sechs Pilotdateien) |
| NavigatorFinalPresentationHarness | 32 Checks |
| NavigatorRegionToolHarness | 59 Checks |
| Bisherige Editor / Editor UX / V2 / Menu / Security / Store | 59 / 127 / 62 / 113 / 78 / 58 Checks |
| Native JVM 1 | 567 Laufzeit + 12 Shutdown = 579 Checks |
| Native Kaltstart-JVM 2 | 385 Laufzeit + 12 Shutdown = 397 Checks |
| Native gesamt | 976 Checks, zwei JVMs, Exit=0, ERROR/SEVERE=0 |
| Module / Reload | 27 / 7 |

Native getestete JAR und abschließendes kanonisches Build-Artefakt sind byteidentisch: SHA-256 `10F8EEA11A0E87313538CD7328B6AD493F2790E7200093335F7F43BA05D827DD`. Alle 213 vorherigen Harness-Identitäten bleiben enthalten; kein bisheriger Harness meldet weniger Checks. Der vollständige kanonische Lauf bestand im ersten Gesamtlauf.

Die native Annahme verwendet echte Paper-ItemStacks/ItemMeta/PDC und Adventure-Komponenten, echte Block-/World-/Location- und Eventobjekte sowie CraftInventory-Storage mit einer kontrollierten PlayerInventory-/Player-Transportgrenze (keine Netzwerkclients). Geprüft sind GUI Mark Region, tatsächliche geklickte Blockkoordinaten, Pos1/Pos2, Cross-World-Korrektur, Offhand, unveränderte BlockData, bestehender echter LobbyListener-Schutz, reine Fallbackcommands ohne Öffnen, Draft-only vor Save, normale Persistenz, freie/volle/reservierte Hotbar, reale vorherige Items, PDC-Owner/Session, gestohlene und stale Kopien, Slot-Ersatz, Transfers, Tod/ItemsToKeep, Quit, Discard, Revocation und Save-Konflikt.

Ein tatsächlich ausgegebenes Tool wird als Paper-NBT serialisiert, in einer zweiten JVM deserialisiert und über Join-Orphan-Cleanup entfernt; echte Items bleiben vorhanden. Für den echten Root-Shutdown bleibt ein gültiges Werkzeug absichtlich ausgegeben. Die zwölf Shutdown-Checks umfassen die bisherigen Menü-/Editor-/Command-Verträge sowie leere Tool-Leases, leere Pending-Aufgaben/Offhand-Fences, keine markierten Items/Cursor und entfernte Tool-Listener. Beide JVMs sind beendet; alle Wegwerf-Serverkopien wurden nach Evidence-Sicherung und Exit-/Lock-/Pfadprüfung entfernt.

Die bisherigen nativen Friends-/Clan-RUN_COMMAND-, Rangfarben-, Profile-, Join-Summary-/Opt-out-, Settings-, Activity-/Reward-/Quest-, Warp- und Reload-Regressionen sind weiterhin enthalten. Beabsichtigte alte Tooltip-/Command-Erwartungen wurden gezielt angepasst; es gibt keinen auto-openenden Command-Testwrapper. GUI-Schritte verlangen ausdrücklich edit. Die reale Adventure-Client-Paketabgabe und physische Client-Ansicht bleiben in der manuellen Liste offen.

Zwei vorgeschaltete native Diagnoseläufe scheiterten ausschließlich an alten Testhelfer-Annahmen: leere Lore wird von Paper als null statt als leere Liste repräsentiert; außerdem wurden Back-Hinweise fälschlich über mehrere durch Teleport getrennte Navigationsdurchläufe aufsummiert. Der Helfer normalisiert jetzt fehlende Lore und prüft die unveränderte Hinweisanzahl innerhalb desselben Durchlaufs. Dafür waren keine Produktionsänderungen nötig; der abschließende vollständige native Lauf ist fehlerfrei. Hinweisfreigabe gilt bis zum Navigator-Cleanup (z. B. Teleport/Quit); Seitenwechsel und Refresh allein erneuern sie nicht.

Evidence: `baseline-results.json`, `baseline-verification.log`, `final-results.json`, `canonical-verification.log`, `harness-check-ledger.json`, `native-verified/summary.json`, beide Main-/Shutdown-Receipts und Console-Logs, Helper/Plugin-JAR, Plugin-Daten/Backups und Tool-NBT, die beiden Diagnose-Logs, `native-cleanup.json`, `protection-before.json`, `protection-after.json`, `protection-comparison.json`, Run-Diffs/-Hashes und `committed-paths.txt` im oben genannten Verzeichnis.

Operator-Schutz: **954 Dateien vor/nachher, alle 954 lesbar und SHA-256-identisch** (928 Serverdateien, 24 Skripte, zwei Run-Konfigurationen). Keine externen Änderungen, Locks, hinzugefügten oder entfernten Pfade in diesem Vergleich. Dies ist ein gemessener Snapshotvergleich für diesen Auftrag, keine pauschale Aussage über sonstige Serveraktivität. Der Operator-Server wurde nicht gestartet, gestoppt, deployt oder als Fixture benutzt. Run-Diff und SHA stimmen exakt überein; die Datei bleibt ungestaged/uncommitted.

### Exakte Commit-Pfade (16)

```text
docs/DEVELOPER_GUIDE.md
reports/prototype/STEP_03D_NAVIGATOR_FINAL_UX.md
src/main/java/dev/vapee/core/lobby/experience/LobbyExperienceModule.java
src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorConfig.java
src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditor.java
src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenu.java
src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorRegionTool.java
src/main/resources/lang/de.yml
src/main/resources/lang/en.yml
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditorHarness.java
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditorUxHarness.java
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorFinalPresentationHarness.java
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorFixture.java
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenuHarness.java
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorRegionToolHarness.java
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorV2Harness.java
```
