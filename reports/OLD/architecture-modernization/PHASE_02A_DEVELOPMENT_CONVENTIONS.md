# Phase 2A — Development Conventions & Code Consistency

## Baseline

Sauberer lokaler `master`: `2dea61e1bbaf29e0de826d2abaf9a310879e43c5` (`Merge Architecture Validation Foundation`). `git merge-base --is-ancestor 8fa3d7bcfc55e45c43f6f427fb0818e926e21dd4 master` ergab Exit 0. Verify, gemeinsamer Environment-Helper und Architekturreferenz waren vorhanden. Keine vorherige Branch-Arbeit wurde automatisch gemergt oder verworfen.

Arbeitsbranch: `phase/development-conventions`. Gemessene unveränderte Baseline über `scripts/verify.ps1 -Offline -MavenRepository <vorhandenes Repository>`: Build PASS, 181 entdeckt/ausgeführt/bestanden, 27.849 Checks, Failed 0, MissingInvalid 0, Exit 0. Die historischen Zahlen waren kein festgelegter Sollwert.

## Vorhandene Konventionen und Abweichungen

Gelesen wurden `module/CoreModule` und `ModuleManager`, `settings/SettingsModule`, `quest/QuestService`, Daily-Quest-Holder/Countdown, `friend/FriendPair`, `clan/ClanService`, `activity/ActivityModule`, `player/settings/PlayerSettings`, `config/ConfigService`, `command/help/CommandHelpEntry`, relevante Tests, POM, README, beide Entwicklerreferenzen und Verify.

Vorherrschend sind vier Leerzeichen, Klammern auf derselben Zeile, UpperCamelCase-Typen, lowerCamelCase-Mitglieder, UPPER_SNAKE_CASE-Konstanten, private/final Abhängigkeiten und explizite `Objects.requireNonNull`-Konstruktoren. Abweichungen: einzeilige Methoden/mehrere Statements, uneinheitliche Fortsetzungen und Importgruppen, unterschiedlich platzierte Konstanten/Observer-Felder und zusätzliche Leerzeilen. JavaDoc reicht von gezielten Ownership-Verträgen bis knappen Typkommentaren. Logging, fachliche Results und RuntimeException-Cleanup sind featureabhängig; es gibt keine globale identische Fehlerbehandlung. Harness-Namen und Ergebniszeilen sind funktionale Runner-Verträge.

Diese Befunde unterscheiden **mechanische Lesbarkeit** von **Designarbeit**: Modul-zu-Modul-Abhängigkeiten, späte Enable-Fehler oder große Repositories werden nicht durch einen Formatter gelöst und hier nicht umgebaut.

## Werkzeug und EditorConfig

- Spotless Maven Plugin **2.43.0**, Google Java Format **1.22.0**, Stil **AOSP**; beide Versionen ausdrücklich in `pom.xml`.
- Auswahl: Maven-Integration ohne IDE-Pflicht, vier Leerzeichen, echte Java-21-Prüfung und Offline-Betrieb nach Bereitstellung der Artefakte. Kein zweiter Java-Formatter, kein Refactoring-Schritt.
- `reflowLongStrings=false`, `formatJavadoc=false`; keine automatische Literalzerlegung oder JavaDoc-Neufassung. `lineEndings=UNIX`, UTF-8. Der kleine Pilot wird ohne timestampbasierte Skip-Optimierung geprüft, damit auch die Idempotenz-Gegenprobe wirklich formatiert.
- `.editorconfig`: LF, UTF-8, abschließender Newline, Java/XML/PowerShell vier und YAML zwei Leerzeichen. Markdown-Hardbreaks und bedeutende YAML-Blocktext-Leerzeichen sind vom Trailing-Whitespace-Trim ausgenommen.
- Auf diesem Rechner ist `core.autocrlf=true`. Daher definiert `.gitattributes` exakt für die sechs Pilotdateien `text eol=lf`. Keine repo-weite EOL-Regel oder Renormalisierung.

Versionierte Referenzen: [Spotless Maven 2.43.0](https://github.com/diffplug/spotless/blob/maven/2.43.0/plugin-maven/README.md), [Google Java Format 1.22.0](https://github.com/google/google-java-format/tree/v1.22.0). Ein isoliertes Projekt mit Record und guarded Pattern-Switch wurde tatsächlich formatiert und mit `javac --release 21` kompiliert. Die erste Abhängigkeitsbereitstellung lief online; die folgenden Formatter-/Validierungsläufe ausdrücklich offline. Windows/PowerShell 7/JDK 21 wurden ausgeführt; Linux/macOS nicht.

## Exakte Pilot-Allowlist

| Datei | Verantwortung / Begründung |
|---|---|
| `src/main/java/dev/vapee/core/quest/daily/menu/ResetCountdownFormatter.java` | Kleine reine Utility, kompakte Darstellung sichtbar verbessern |
| `src/main/java/dev/vapee/core/friend/FriendPair.java` | Kleine Domain-Record, UUID-Sortierung und Persistenzvertrag bewahren |
| `src/main/java/dev/vapee/core/presence/PresenceModule.java` | Überschaubarer echter Modul-Lifecycle |
| `src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestInventoryHolder.java` | GUI-Owner-/Inventory-/Render-Bindung, keine Layoutänderung |
| `src/test/java/dev/vapee/core/presence/PresenceLifecycleHarness.java` | Verhaltenstest aus Phase 1, Source-Wiring-Grenzen bleiben erhalten |
| `src/test/java/dev/vapee/core/settings/visibility/SettingsModuleLifecycleHarness.java` | Registrierung, Teilfehler und Cleanup aus Phase 1 |

Vier Produktions- und zwei Testdateien wurden durch den Formatter geführt. `FriendPair` hatte bereits dieselbe sichtbare Formatierung und benötigt nur CRLF→LF; sein normalisierter Git-Inhalt ändert sich nicht. Der Java-Textdiff betrifft deshalb drei Produktions- und zwei Testdateien. Sämtliche sechs Pilotdateien sind LF und künftig explizit geprüft. `VapeeCore`, `BlackjackCommand`, `FilePlayerRepository` sowie alle übrigen Java-Quellen bleiben außerhalb des Piloten.

Alle sechs tatsächlichen Vorher-/Nachher-Dateien wurden über den JDK-Parser verglichen: gleicher Package-Name, gleiche sortierte Importmenge und gleiche Typ-ASTs einschließlich Namen, Signaturen, Feldern, Anweisungen und Literalwerten. Imports werden ausschließlich sortiert, keine API wird umbenannt oder extrahiert. Die geänderten Klassen wurden neu kompiliert. Zusätzlich wurde der vollständige Textdiff geprüft. Debug-Zeilennummern sind kein fachlicher Vertrag.

## Arbeitsablauf und Integration

```powershell
mvn spotless:apply
mvn spotless:check
pwsh -File ./scripts/verify.ps1
# Bereits bereitgestellte Abhängigkeiten:
mvn -o spotless:check
pwsh -File ./scripts/verify.ps1 -Offline -MavenRepository '<lokales Repository>'
# Genau eine bereits erlaubte Datei, nicht als Ersatz für den vollständigen Abschluss:
mvn spotless:apply '-DspotlessFiles=.*ResetCountdownFormatter[.]java'
```

Verify bleibt der eine offizielle Einstieg: Voraussetzungen → `clean package` → `spotless:check` → vollständiger kanonischer Runner → geprüfter Erfolgsnachweis. Formatfehler stoppen vor der Regression, liefern einen Fehlercode und verhindern damit das bestehende Deployment-Gate. Kein automatisches Apply und kein Deployment in Verify. Maven `clean package` bleibt Build/Paketierung; die eigenständigen Harnesses werden dadurch weiterhin nicht ausgeführt.

Die vorhandenen isolierten Wrapper-Tests behandeln den Formatter als zusätzlichen externen Schritt: Check statt Apply, Offline-/Repository-Weitergabe und Formatfehler inklusive blockiertem Deploy. Der neue echte Formatter-Test ist ein separates reproduzierbares Entwicklerwerkzeug, keine konkurrierende Format-Pipeline und keine zweite Harness-Engine.

## Tatsächliche Gegenproben

`scripts/tests/formatter.Tests.ps1` liest die echte Spotless-Konfiguration und kopiert nur den Piloten in zufällige temporäre Projektordner. Versions-/Stilkonfiguration, feste Include-Pfade, Größenbegrenzung und passende Git-LF-Regeln werden geprüft. Projekt-/Operator-Quellen werden für Fehlerfälle nicht verändert.

| Prüfung | Ergebnis |
|---|---|
| Apply zweimal auf den echten Projektpiloten | Zweiter uncached Lauf: null Byte-Änderungen |
| Check-only auf dem Projektpiloten | PASS, null Java-Byte-Änderungen |
| Scope über alle Java-Quellen | 570 Quellen erfasst, alle 564 Nicht-Pilotdateien byte-identisch |
| Reale isolierte Apply-/Check-/Restoration-Tests | 26 Checks bestanden, offline, einschließlich Vergleich mit den sechs tatsächlichen Originaldateien |
| Java 21 | Formatter + Kompilierung von Record/guarded Pattern-Switch bestanden |
| Fehlerdetektion | Zusätzliche Leerzeichen in einer isolierten Pilotklasse: echter Check Exit ungleich 0, Datei unverändert |
| Echte Verify-Integration | Kontrollierter erfolgreicher Build, echter fehlschlagender Spotless-Check: Verify scheitert, kein Runner-/Dev-Server-Aufruf und keine automatische Korrektur |
| Wiederherstellung | Originalbytes zurück, Check wieder erfolgreich |
| Einzelfilter | Ausgewählte Pilotdatei wird formatiert; ein selektierter Nicht-Pilot erweitert den Scope nicht |
| AST-Negativkontrolle | Geändertes Countdown-Formatliteral wird als Struktur-/Literalabweichung erkannt |
| Offline | Echte Apply-, Check-, Gegenproben und abschließende Verify-Ausführung mit `-o` bzw. `-Offline` |

Die erste Test-Fixture hatte ein zusätzliches XML-Namespace-Attribut am kopierten Plugin-Knoten; Maven lehnte nur diese temporäre POM ab. Die Fixture wurde korrigiert und danach vollständig neu geprüft. Kein Produktionsfehler und keine Quell-Reparatur war erforderlich.

Der Formatter-Test wurde mit `-OriginalDirectory <temporäre Vorher-Kopien>` ausgeführt. Ohne diesen optionalen Vergleich gegen die ursprünglichen Projektbytes enthält derselbe eigenständig wiederholbare Test 25 Checks. Die vorhandenen Wrapper-Tests bestehen mit 55 Checks und werden vom vollständigen Runner automatisch ausgeführt.

## Source-sensitive Tests

Für alle Pilotdateien wurde nach Source-Lesern gesucht. Die frühere Modul-Textprüfung in `PresenceLifecycleHarness` ist seit Phase 1 durch reale Registrierung/State/Cleanup ersetzt; diese Prüfungen bestehen nach Formatierung unverändert. Seine weiterhin vorhandenen Checks lesen `VapeeCore.java` und `PresenceListener.java`, die nicht formatiert werden. `SettingsModuleLifecycleHarness` behält alle Verhaltenstests. `VisibilityModuleHarness` liest weiterhin `SettingsModule.java`; dieses Modul bleibt außerhalb des Piloten. Keine Assertion wurde entfernt, abgeschwächt oder nur für den Formatter umgeschrieben. Es war kein Source-Test-Umbau erforderlich.

Fokussiert bestanden: Presence-Lifecycle 24, Settings-Lifecycle 44, DailyQuestReset 55, QuestMenuSecurity 45, FriendService 79 und VisibilityModule 22 Checks. Dokumentlinks waren ebenfalls erfolgreich. Die vollständige Regression bleibt der Abschluss, nicht nur diese Auswahl.

## Dokumentation und Konventionen

`docs/CODE_STYLE.md` beschreibt Formatierung, Namen, statische/Wildcard-Imports, JavaDoc, Klassen-/Methodenaufbau, Null-/Fehlerbehandlung, Logging und Lifecycle. Empfohlene Reihenfolgen sind keine automatische Umordnung. Feature-Ownership, sinnvolle Unterpackages und kleine vorhandene Service-Grenzen haben Vorrang vor einer allgemeinen Verzeichnisvorlage. Keine Package-Migration.

README, Architekturreferenz und Developer Guide verlinken diese Regeln und den Ablauf Bearbeiten → Formatieren → Check → Verify → Commit. IntelliJ liest `.editorconfig`; bei abweichendem IDE-Reformat gewinnt der gemeinsame Maven-Formatter. Keine neuen absoluten IDE-Pfade oder persönliche Profilpflicht. Die Dokumentation behauptet ausdrücklich keine projektweite Formatter-Konformität. Historische Reports bleiben unverändert.

## Abschlussvalidierung und Diff

Offizieller Abschlusslauf über `scripts/verify.ps1 -Offline -MavenRepository <vorhandenes Repository>`:

| Ergebnis | Final |
|---|---:|
| Maven `clean package` | PASS, Exit 0 |
| `spotless:check` | PASS, sechs Dateien sauber, keine Cache-Skips |
| Discovered / Executed / Passed | 181 / 181 / 181 |
| TotalChecks | 27.863 |
| Failed / MissingInvalid | 0 / 0 |
| Verify Exit-Code | 0 |
| Baseline-Einstiege nicht erhalten | 0 |
| Separate echte Formatter-Gegenproben | 26 bestanden |
| Integrierte Wrapper-Prüfungen | 55 bestanden |

Alle 181 Baseline-Einstiege bleiben verfügbar. Der Unterschied zum Baseline-Checkcount ergibt sich aus neun zusätzlichen Wrapper- und fünf zusätzlichen Dokumentlink-Prüfungen; keine Verhaltenstests wurden für die Formatierung entfernt. `results.json`, Inventar und Prozesslogs liegen unter `target/verification/harnesses/`. Die beobachteten 15 Dev-Pfade (JAR, PID/Stop-Request, Feature-YAMLs, Sprachdateien) waren nach Verify unverändert. Es wurde kein echter Server gestartet/gestoppt oder deployed. PowerShell-Parserprüfung und `git diff --check` sind erfolgreich.

Java-Textdiff: fünf Dateien, 343 hinzugefügte und 139 entfernte Zeilen, ausschließlich mechanische Formatierung/Imports. Dazu kommt `FriendPair` mit LF-Normalisierung ohne normalisierten Inhaltsdiff. Nicht-Pilot-Java-Diffs: null.

Gesamtdiff: 16 Dateien, 826 hinzugefügte und 147 entfernte Zeilen, ausschließlich die unten aufgeführten Phase-Dateien. Vor Commit ist der Arbeitsbaum nur durch diese Phase belegt; der Post-Commit-Status wird separat geprüft.

Paper-Smoke/Live-Client: nicht durchgeführt. Die Produktionsänderungen sind durch Parservergleich und Diffprüfung als mechanisch bestätigt; Build-/Formatter-/Behavior-Gates prüfen den Abschluss. Keine geänderten Gameplay-, GUI-Layout-, Klick-, Persistenz-, Permission-, Reload- oder Initialisierungsregeln.

## Geänderte Dateien

- `.editorconfig`, `.gitattributes`, `pom.xml`
- `scripts/verify.ps1`, `scripts/tests/verification.Tests.ps1`, `scripts/tests/formatter.Tests.ps1`
- `README.md`, `docs/CODE_STYLE.md`, `docs/ARCHITECTURE.md`, `docs/DEVELOPER_GUIDE.md`
- Drei Produktionsdateien mit Textdiff: `PresenceModule`, `DailyQuestInventoryHolder`, `ResetCountdownFormatter` (vollständige Pfade in der Allowlist oben)
- Zwei Testdateien mit Textdiff: `PresenceLifecycleHarness`, `SettingsModuleLifecycleHarness`
- Dieser Report: `reports/architecture-modernization/PHASE_02A_DEVELOPMENT_CONVENTIONS.md`

`FriendPair` ist im Piloten und wird über die neue Git-Regel auf LF gehalten; es entsteht kein separater normalisierter Inhaltsdiff. Generierte Ergebnisse, Maven-Artefakte und Operator-Dateien werden nicht eingecheckt.

## Priorisierter Handoff für Phase 2B

1. **LobbyExperience-Teilstart:** Den Settings-Back-Opener bei fehlgeschlagenem Enable genauso ausdrücklich zurücksetzen wie den Visibility-Back-Opener. Echte Teilfehler nach dem Wiring injizieren; stale Callback und erneutes Enable prüfen. Hier nicht behoben.
2. **Settings spätes Enable:** Observer-Starts und veröffentlichte Referenzen nach dem bisherigen Registration-try in die eigene Fehler-/Cleanup-Verantwortung aufnehmen. Teilfehler während später Observer-Registrierung nachweisen; Handler, Commands, Views und Observer dürfen nicht übrig bleiben. Hier nicht behoben.
3. **Weitere Bootstrap-/Lifecycle-Textprüfungen:** Reale Verdrahtung bzw. stabile Metadaten für verbleibende Permission-/DailyQuest-/Visibility-/Presence-Checks schaffen. Security-/Forbidden-Dependency-/Descriptor-Verträge erhalten. Bei größerem Produktionsbedarf einen getrennten Schritt planen, keine breite Umformatierung zur Test-Reparatur.
4. **Formatter-Scope erst danach gezielt erweitern:** Kleine zusammengehörige Dateien mit geprüften Source-Abhängigkeiten aufnehmen; Allowlist und LF-Regeln gemeinsam ändern. Keine Paketverschiebungen und keine großflächige Klassen-/Command-/Repository-Zerlegung in einem Format-Commit.

Geplanter einzelner lokaler Commit: `Establish Java development conventions and formatter pilot`. Der vollständige enthaltene Commit-SHA und der Post-Commit-Status werden in der Abschlussantwort ermittelt; ein Commit kann seinen eigenen SHA nicht vorab enthalten. Push: nein. Merge: nein.
