# Prototype Step 03C — Navigator Editor UX und Regionsdiagnose

## Ausgangspunkt und Schutz

Fortsetzung auf `codex/navigator-ux-editor`, HEAD vor Änderung `3331d8f185f147cecbd02417e40d554ede581f3c`. Step 03 `15d59b0741fbb9ed9f662ae5b4fd1b7106eba619` und Step 03B bleiben unverändert. Der Arbeitsbaum enthielt ausschließlich die ausdrücklich genehmigte Änderung `.run/Start VapeeCore Dev Server.run.xml`. Keine neuen Module, Commands, Permission-Nodes oder Reload-Teilnehmer: weiterhin 27 Module, sieben Reload-Teilnehmer, 37 Commands und 51 Permission-Nodes.

Vollständige Basisprüfung vor Produktionsänderungen: **212/212 Harnesses, 31.734 Checks, Failed=0, Missing/Invalid=0, Maven clean package PASS, Spotless PASS**. Alle 212 bisherigen Identitäten bleiben erhalten. Operator-Dateien werden nur lesend inventarisiert. Tests verwenden eigene Dateien/Spieler und eine gesonderte Paper-Installation, niemals den laufenden IntelliJ-Server. Kein Push, Merge oder Deployment.

## Bestätigte Ursache und Grenze der Diagnose

In Step 03B entstand die Erfolgsmeldung `Position 1 selected; use pos2 in the same world. Save is blocked until complete or clear.` in `NavigatorEditor.region`, Fall `pos1`: Die aktuelle Location wurde korrekt in `Session.first` gespeichert.

Die nachfolgende Fehlermeldung `Editor: Select pos1 and pos2 in the same world` kam aus **derselben Pos2-Bedingung für zwei verschiedene Fehler**:

```java
if (s.first == null || !s.first.getWorld().getUID().equals(at.getWorld().getUID()))
    throw new IllegalArgumentException("Select pos1 and pos2 in the same world");
```

`select(Session, id)` setzte bislang `first = null`, sobald die Auswahl-ID wechselte. Selbst `/navigator menu select root` im bereits gewählten Root-Menü setzte zunächst die Eintragsauswahl auf null. Ebenso tat es der Klick auf einen leeren Editor-Slot. Anschließendes erneutes Auswählen desselben Warps konnte den verlorenen Punkt nicht restaurieren. Die Fehlermeldung unterschied dieses verlorene Pos1 nicht vom tatsächlichen Weltwechsel.

Die Diagnose wurde **vor jeder Quellcodeänderung gegen den unveränderten Step-03B-Bytecode kompiliert und ausgeführt** (`RegionBefore.java`, `region-before-diagnosis.log` außerhalb des Repositorys):

| Ablauf nach erfolgreichem Pos1 | Pos1 erhalten? | Pos2 in derselben Welt |
|---|---|---|
| Nur Bewegung | ja | Region erzeugt, info und Save erfolgreich |
| `/navigator edit` erneut öffnen | ja | Region erzeugt, info und Save erfolgreich |
| Dasselbe Menü erneut auswählen, dann denselben Warp | nein | irreführende Welt-Meldung |
| Leeren Editor-Slot anklicken, dann denselben Warp auswählen | nein | identische irreführende Welt-Meldung |

In allen vier Fällen war die Welt-UUID gleich. Es wurde kein Fehler der normalen Bukkit-Welt-UUID- oder Location-Abfrage nachgewiesen. Aus den ursprünglichen Meldungen allein lässt sich nicht rekonstruieren, welcher GUI-/Auswahlwechsel beim Nutzer tatsächlich dazwischenlag; dafür fehlen protokollierte Auswahlschritte und Positions-/Welt-Snapshots. Belegt und behoben sind die reproduzierbare Zustandslöschung und die zusammengelegte, irreführende Fehlerdiagnose. Der Nutzer wird nicht pauschal einer falschen Welt zugeordnet.

## Umsetzung und neuer Regionsablauf

`PendingRegion` ist ein kleiner Record innerhalb des bestehenden Editors, mit Menü-ID, Entry-ID, Bukkit-Welt-UUID, Weltname und endlichen XYZ-Werten. Kein neuer Cache, Scheduler oder Persistenztyp. Die Auswahl bleibt beim Bewegen, Schließen, erneuten Öffnen und Navigieren erhalten. Ein Wechsel des Menüs/Eintrags erklärt einmalig, zu welchem Eintrag Pos1 gehört. Pos2, neues Pos1 und clear auf einem anderen Eintrag können diese Auswahl nicht verbrauchen oder überschreiben; sie nennen den tatsächlichen Eigentümer. Zur ursprünglichen Auswahl zurückkehren und pos2/clear ausführen. Ein erfolgreicher Entry-Move aktualisiert den Menübezug der Pending-Auswahl. Entfernen des betroffenen Eintrags/Menüs wird bis zur ausdrücklichen Auflösung gesperrt.

Beispiel ohne neue YAML-Arbeit:

1. `/navigator edit`, Menü auswählen und Warp anklicken; alternativ `/navigator menu select welten`, `/navigator select parkour`.
2. Zur ersten Ecke gehen und `/navigator region pos1` ausführen. Es folgt genau eine kurze Bestätigung. Ein Regionscommand aus der Spielansicht öffnet keine GUI über dem Spieler.
3. Zur zweiten Ecke derselben Welt gehen und `/navigator region pos2` ausführen. Beide Punkte werden normalisiert, Grenzen bleiben inklusiv. Der Vergleich verlangt weiterhin gleiche Welt-UUIDs; gleicher Name bei anderer UUID reicht nicht. Unterschiedliche World-Objektinstanzen mit derselben UUID sind dagegen gültig.
4. `/navigator region info` zeigt Welt, Grenzen, Zählstatus und gegebenenfalls die noch offene Pos1-Auswahl. Info ist lesend und öffnet/ändert keine Ansicht.
5. `/navigator save` übernimmt den vollständigen Entwurf wie bisher. Ein offenes Pos1 blockiert Save mit dem konkreten Menü-/Eintragsbezug; alternativ am richtigen Eintrag `/navigator region clear`.

Die GUI bietet denselben Ablauf über **Warp-Eintrag → Spielerzähler-Region → Position 1 / Position 2 / Region entfernen**. Welt-/Koordinaten-/Kontextprüfungen sind identisch. Der Warp-Teleportpunkt bleibt unverändert; kein neuer Warp wird automatisch eingetragen.

Getrennte EN/DE-Rückmeldungen unterscheiden: keine Sitzung, kein ausgewählter Warp, veralteter Eintrag/Editorzustand, fehlendes Pos1, tatsächliche unterschiedliche Welten (mit beiden Namen), ungültige Koordinaten, falscher Pending-Eintrag und unvollständige Region. Ein tatsächlicher Weltfehler wird erst nach vorhandenem Pos1, passendem Entry-Kontext und gültiger aktueller Location gemeldet.

## Aktionsauswahl und Chat

- **Menü-Aktionen** öffnet ein 27-Slot-Inventar mit Untermenü erstellen, vorhandenes Menü auswählen, aktuelles Menü umbenennen, Icon ändern, Größe ändern und optionalem Zurück-Icon. Der Icon-Picker öffnet ausschließlich über **Menü-Icon ändern**.
- Größenwahl bietet 27/36/45/54 über die bestehenden Menümutationen und Slot-/Kollisionsvalidierungen. Zurück-Icon bietet Deaktivieren oder den vorhandenen engen Slot-Command. Kein anderes Layout-/Editing-Framework.
- Ein Warp-Eintrag öffnet die kompakte Auswahl für Icon, Name/Lore, Permission, Verschieben, Region und Entfernen. Name und Lore haben zwei getrennte Textaktionen; Region hat eine eigene kleine Ansicht für Info, Pos1, Pos2, clear und Stack-Anzeige.
- Textaktionen schließen nur die eigene GUI und senden genau einen lokalisierten, anklickbaren **SUGGEST_COMMAND**. Sie führen keinen Textbefehl ungeprüft aus. Die Sitzung und eine offene Region bleiben bestehen. Bestehende Commands und ihre Schreibweise bleiben nutzbar.
- `/navigator help` bleibt die explizite vollständige Referenz. Fehlende/ungültige Argumente liefern nur die relevante kurze Syntax; Auswahl-/Regionsfehler liefern konkrete Anweisungen. Gewöhnliche Navigation öffnet keine Hilfebäume und erzeugt keine wiederholten Nachrichten. Erfolgs-/Vorschaumeldungen bleiben kurz.
- UUID, Session, Revision, exakter Holder, gebundene und aktuell geöffnete Inventory-Instanz bleiben maßgeblich. Stale/forged/foreign Clicks können keine Aktionen auslösen; erlaubte Nutzer erhalten für eine tatsächlich abgelaufene eigene aktive Ansicht genau eine kurze Meldung. Permission-Entzug und Click/Drag-Schutz bleiben bestehen.

## Save-Labels und unveränderte Persistenz

| Runtime | Englisch | Deutsch |
|---|---|---|
| Inaktiv | Save & Activate Navigator | Speichern & Navigator aktivieren |
| Aktiv | Save Changes | Änderungen speichern |
| Entwurf gesperrt | Cannot save + konkreter Grund | Speichern gesperrt + konkreter Grund |

Der Status wird aus dem aktiven Runtime-Snapshot gelesen, nicht aus dem bei Sitzungsbeginn eingefrorenen `enabled`-Wert. `NavigatorStore.isActive()` ist lediglich dieser Lesezugriff; alle bisherigen File-/Save-/Revisions-/Rollback-Methoden bleiben unverändert. Der gesamte Entwurf wird für Save-/Button-Validierung weiterhin vom produktiven Parser geprüft. Ein Klick auf einen gesperrten Save meldet den Grund und schreibt nichts.

Bytekonflikte, konkurrierende Administratoren, genaue Backups, Atomic-Move/Fallback, Rücknahme, Recovery-Sperre und Live-Apply bleiben unverändert. Die bisherigen `NavigatorStoreHarness`-Fehlerkontrollen und `NavigatorEditorHarness` werden vollständig weiter ausgeführt. Auch 27-Slot-Default, größere Menüs, Untermenüs, Builder-/Entry-Permissions, Parent-Pfade, Back/ESC, WarpService, Zählregeln, Visibility/Vanish, Deduplizierung, Stacklimits und Compass bleiben erhalten.

## Verifikation

Neu: `NavigatorEditorUxHarness` mit fokussierten ausführbaren Szenarien für alle vier vorher/nachher-Reproduktionen, Welt-UUIDs, Bewegung, Reopen, Pending-Eigentümerschaft, gleichen Warp in anderem Menü, Move/Delete, Fehlerspezifität EN/DE, kurze Syntax statt Help-Spam, Menü-/Warp-Aktionsauswahl, getrennten Icon-Pfad, Vorschlagscommands, vier Save-Labels, ungültigen Entwurf, Autorisierung, Revocation, stale/forged Holder und Drag-/Click-Schutz. Die bestehende Testfixture wird lediglich paketlokal für Wiederverwendung zugänglich; kein bestehender Harness wird entfernt oder abgeschwächt.

**Final: 213/213 Harnesses, 31.863 Checks, Failed=0, Missing/Invalid=0; Maven clean package und Spotless PASS. Native Paper/LuckPerms: 793 Checks in zwei getrennten JVMs, beide Exit=0, keine ERROR/SEVERE.** Evidence: `C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/prototype-step03c-navigator-ux-fixes-20261030/`.

Native Acceptance verwendet eine eigene Wegwerf-Installation Paper 1.21.11-132 + LuckPerms 5.5.84, echte Server-World-/Location-/Inventory-/ItemMeta-Objekte und den tatsächlich registrierten `/navigator`-Command. Synthetische Spieler transportieren Commands/Inventories; sie sind kein physischer Minecraft-Client. Der Operator-Server und seine Daten sind keine Fixtures.

## Noch im echten Minecraft-Client prüfen

- Pos1 → normales Gehen/ESC → Pos2 → info → Save in der tatsächlichen Welt ausführen; dabei Auswahl bewusst wechseln und zurückkehren.
- Alle sechs Menü-Aktionen und alle Warp-Aktionen durchklicken; Textvorschläge im Minecraft-Chat verwenden, ohne vollständige Help-Bäume.
- EN/DE-Darstellung, lesbare Status-/Fehlermeldungen und aktive/inaktive/gesperrte Save-Labels vergleichen.
- Mit zwei echten Admins und einem normalen Spieler Permission-Entzug, offene Ansichten und Save-Konflikte prüfen.
- Sichtbare/vanished Spieler an den Regionsgrenzen und den Compass nach Save prüfen.

Die reale Clientprüfung von Step 03B wird durch den Nutzerbericht bestätigt; eine physische Clientprüfung dieses neuen Step-03C-Builds wird nicht behauptet.

## Git- und Operator-Nachweis

Die Run-Datei bleibt als ausdrücklich genehmigte lokale Änderung erhalten und außerhalb des Commits. Vorher-/Nachher-Diff und SHA-256 werden verglichen; erwarteter Hash `EEDE5A220EEC6A8D154D82F2BD1BF29CFEB59215D6B546D83CDB38A73EAF596F`. Gesperrte und durch den laufenden Operator-Server extern veränderte Dateien werden separat erfasst; keine pauschale Behauptung vollständiger Byte-Identität.

Genau ein zusätzlicher lokaler Commit: `Improve Navigator editor actions and region feedback`. Exakte Pfade und Schlussmesswerte stehen unten. Es gibt keine zusätzlichen unerwarteten Git-Änderungen; die Run-Ausnahme bleibt ausdrücklich lokal erhalten. Keine Änderungen an Security Hardening, Staff Panel oder anderen Featurephasen.

## Schlussmesswerte und genaue Pfade

| Prüfung | Ergebnis |
|---|---|
| Unveränderte Basis | 212 Harnesses / 31.734 Checks / 0 Fehler |
| Vollständiger kanonischer Verifier | 213 Harnesses / 31.863 Checks / 0 Fehler / 0 Missing/Invalid |
| Maven clean package / Spotless | PASS / PASS (unveränderte sechs Pilotdateien) |
| Neue NavigatorEditorUxHarness | 127 Checks |
| Bisherige Editor-/Store-/Compact-/Security-Harnesses | 59 / 58 / 44 / 78 Checks |
| Native JVM 1 | 480 Laufzeit + 8 Shutdown = 488 Checks |
| Native Kaltstart-JVM 2 | 297 Laufzeit + 8 Shutdown = 305 Checks |
| Native gesamt | 793 Checks, zwei JVMs, Exit=0, ERROR/SEVERE=0 |
| Module / Reload-Teilnehmer | 27 / 7 |

Die native geprüfte JAR ist byteidentisch mit dem Artefakt des abschließenden kanonischen Builds: SHA-256 `D237FFB19EF361D53A9BB4855070F2CB95E33CDA6C5289C99B0237BF4ED2F69E`. Der vollständige Verifier und die Native Acceptance sind im ersten jeweiligen Gesamtlauf erfolgreich. Während der fokussierten Entwicklung wurde die gezielte Syntaxmeldung für fehlende Permission-Argumente nach einem Regressionstreffer korrigiert und erneut geprüft.

Die native Annahme führt beide vormals scheiternden Auswahlpfade mit tatsächlichen Bukkit-Weltobjekten aus, zusätzlich Pos1 → Bewegung → Reopen → Pos2 → info → Save, echte Weltwechsel, ungültige Koordinaten, fehlendes Pos1/Sitzung, gleicher Warp in anderem Menü, einmalige kurze Syntax, alle vier Save-Labels über den Runtime-Zustand, blockierten Save, die Menü-/Warp-/Text-/Regionsauswahl, SUGGEST_COMMAND, expliziten Icon-Pfad, forged/stale Views und Revocation. Die 527 bisherigen nativen Step-03B-Prüfungen einschließlich Friends-/Clan-RUN_COMMAND, Rangfarben, Join-Summary/Opt-out, Profile, Settings, Warp, Activity/Reward/Quest und Reload bleiben enthalten; Step 03C ergänzt 266 native Checks.

Der Editor bleibt für zwei synthetische Admin-Transporte absichtlich bis zum tatsächlichen Module-Shutdown offen. Danach werden leere Session-/Active-Maps, geschlossene Inventare, entfernte Listener, freigegebene Command-Eigentümerschaft und normaler Root-Shutdown geprüft. Die JVMs sind beendet; die Wegwerf-Serverkopie wurde nach Prüfung des absoluten Pfads, der Exit-Receipts und freier Locks entfernt. Logs, Helper/Plugin-JAR, Konfigurationen und exakte Backups bleiben als Evidence erhalten. Keine physische Clientprüfung dieses Builds.

Der unveränderte Vorher-Reproduzierer wurde gegen den neuen Bytecode erneut kompiliert: jetzt erzeugen **alle vier Varianten** eine Region und können diese erfolgreich speichern (`region-after-diagnosis.log`). Die gezielten Canonical-Tests unterscheiden außerdem identischen Welt-Namen mit verschiedener UUID (Ablehnung) von unterschiedlichen World-Instanzen derselben UUID (Annahme); die Cross-World-Prüfung wurde nicht abgeschwächt.

Evidence im oben angegebenen Verzeichnis: `baseline-results.json`, `baseline-verification.log`, `RegionBefore.java`, `region-before-diagnosis.log`, `region-after-diagnosis.log`, `final-results.json`, `canonical-verification.log`, `harness-check-ledger.json`, `native-acceptance/summary.json`, beide Main-/Shutdown-Receipts und Console-Logs, getestete JAR/Helper/Plugin-Daten/Backups, `native-cleanup.json`, Vorher-/Nachher-Dateiinventare, `protection-comparison.json`, Run-Diffs/-Hashes und `committed-paths.txt`.

Operator-Snapshots: **953 Dateien vorher, 952 nachher**; **928 beidseitig lesbare Dateien identisch**, **15 beidseitig lesbare Dateien extern verändert**, **9 ohne vollständigen Vorher-Hash wegen damaliger Prozesssperren**. Beim Nachher-Snapshot waren diese neun Dateien wieder lesbar. **0 hinzugefügte, 1 extern entfernte Pfade**. Die PID-Datei verschwand und die Locks wurden extern freigegeben. Dieser beobachtete Serverzustandswechsel wird nicht als Agentenaktion ausgegeben; der Operator-Server wurde nicht von uns gestartet/gestoppt, seine Daten wurden nicht beschrieben und keine verschwundene Datei wurde wiederhergestellt.

Alle Skripte, Run-Konfigurationen und beidseitig lesbaren Navigator-/Warp-/Operator-Konfigurationen blieben unverändert. Für anfangs gesperrte Logs, LuckPerms-DB und Weltdateien ist keine vollständige Byte-Identitätsaussage möglich. Die einzige lokale Git-Änderung nach dem Commit soll weiterhin die genehmigte Run-Datei sein.

Extern veränderte beidseitig lesbare Operator-Dateien:

```text
dev-server\plugins\VapeeCore\players\7cf63c54-1791-41fa-9aed-ef91ce90af06.yml
dev-server\world\data\raids.dat
dev-server\world\level.dat
dev-server\world\level.dat_old
dev-server\world\playerdata\7cf63c54-1791-41fa-9aed-ef91ce90af06.dat
dev-server\world\playerdata\7cf63c54-1791-41fa-9aed-ef91ce90af06.dat_old
dev-server\world\region\r.-1.-1.mca
dev-server\world\region\r.0.-1.mca
dev-server\world\stats\7cf63c54-1791-41fa-9aed-ef91ce90af06.json
dev-server\world_nether\DIM-1\data\raids.dat
dev-server\world_nether\level.dat
dev-server\world_nether\level.dat_old
dev-server\world_the_end\DIM1\data\raids_end.dat
dev-server\world_the_end\level.dat
dev-server\world_the_end\level.dat_old
```

Anfangs gesperrt, nachher lesbar:

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

Extern entfernter Pfad (nicht restauriert):

```text
dev-server\.vapeecore-dev\server.pid
```

### Genau sechs Commit-Pfade

```text
docs/DEVELOPER_GUIDE.md
reports/prototype/STEP_03C_NAVIGATOR_UX_FIXES.md
src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditor.java
src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorStore.java
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditorHarness.java
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditorUxHarness.java
```
