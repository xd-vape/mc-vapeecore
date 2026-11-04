# Phase 01 — Architecture & Validation Foundation

## Ausgangspunkt und Umfang

- Ausgangsbranch: `master`, sauber; Baseline `8fd0ef6c4911400a6472bb89f8722e80f35ed44f` (`GUI Changes`).
- Daily Quest Reset UI und i18n Catalog Organization sind über die vorhandenen Merge-Commits integriert; die anschließend eingecheckten GUI-Anpassungen bleiben erhalten.
- Arbeitsbranch: `phase/architecture-validation-foundation`.
- Keine Änderung unter `src/main/`, keine Gameplay-/Permission-/Command-/Schema-Änderung, kein Deployment, Push oder Merge.

## Gemessene Baseline

Unveränderter kanonischer Runner, offline mit dem vorhandenen lokalen Maven-Repository:

| Ergebnis | Baseline |
|---|---:|
| Discovered / Executed | 180 / 180 |
| Passed / Failed | 179 / 1 |
| Missing/Invalid | 0 |
| Checks erfolgreicher Harnesses | 27.709 |
| Runner Exit-Code | 1 |
| Separates Maven `clean package` | erfolgreich, Exit 0 |

Die Baseline ist ausdrücklich **nicht grün**: `VisibilityCompositionHarness` verlangt „Manage Players chest is at the lower right corner“, obwohl das eingecheckte `VisibilitySettingsMenu.DEFAULT_LAYOUT` Manage Players an einer anderen Position und Close unten rechts platziert. Das ist ein veralteter Geometrievertrag im Test. Die Produktionsanordnung wurde nicht zurückgesetzt. Der neue Test prüft das gerenderte CHEST-Item und die tatsächliche Navigation an der owning Layout-Position.

## Alter und neuer Arbeitsablauf

Vorher: Maven baute nur; der vollständige Runner war separat, Nachweise lagen im Dev-Server-Verzeichnis. Build & Deploy enthielt eine zweite Build-Definition und stoppte den Server bereits vor der Validierung. Maven-/Java-Erkennung war mehrfach und auf `.cmd`/`.exe` zugeschnitten.

Jetzt:

```powershell
# Nur bauen / paketieren:
mvn clean package
# Vollständige eigenständige Behavioral Regression:
pwsh -File ./scripts/run-harnesses.ps1
# Offizieller, nicht deployender Abschluss einer Änderung:
pwsh -File ./scripts/verify.ps1
# Optional für jeden der Validierungs-/Deploy-Einstiege:
pwsh -File ./scripts/verify.ps1 -Offline -MavenRepository '<lokales Repository>'
# Nur bei gewünschtem lokalem Deployment:
pwsh -File ./scripts/build-and-deploy.ps1
```

Verify prüft JDK/Maven, ruft Maven `clean package` und danach den **bestehenden** kanonischen Runner in einem separaten PowerShell-Prozess auf. Es verwirft alte Nachweise vor dem Lauf und akzeptiert nur einen frischen, vollständigen Erfolgsnachweis. Der Runner bleibt die einzige Discovery-/Execution-/Output-Validierungsengine; keine zweite Harness-Liste und kein Skip-Schalter. Inventory, Maven- und Einzelprozess-Logs sowie `results.json` liegen unter `target/verification/harnesses/`.

Build & Deploy ruft Verify auf, stoppt **nach erfolgreichem Gate** Paper sauber und verwendet danach das bestehende temporäre JAR-/Replace-/Copy-Fallback mit Größen- und SHA-256-Ausgabe. Kein automatischer Serverstart. Fehler bei Build, Regression, Nachweis oder Stop verhindern die Ersetzung. Start/Stop bleiben getrennte Entwickleraktionen. Fehlende JDK-Tools und fehlgeschlagene jps-Inspektion führen beim Stop zu einem Fehler statt einer unbegründeten „kein Server“-Annahme. Der vorhandene verwaltete PID-/Stop-Request-Vertrag bleibt erhalten.

## Voraussetzungen und Plattformen

`development-environment.ps1` enthält ausschließlich gemeinsame Discovery-Funktionen. Java kommt aus explizitem `JAVA_HOME` oder PATH; `java.home` löst auch Launcher/Symlinks zur tatsächlichen Installation auf. `java`, `javac`, `javap` müssen JDK 21 entsprechen. Maven kommt aus explizitem `MAVEN_HOME`, PATH oder optional dem bestehenden Windows-/IntelliJ-Fallback; Mindestversion 3.6.3. Fehlerhafte explizite Home-Werte werden nicht still durch andere Installationen ersetzt. Ohne eigenen Repository-Parameter gelten die normalen Maven-Einstellungen.

Tatsächlich ausgeführt: Windows, PowerShell 7, JDK 21.0.12.1, IntelliJ-gebündeltes Maven, Offline-Modus und das vorhandene Maven-Repository. Zusätzlich isolierte Pfade mit Leerzeichen für Argumentweitergabe. Linux/macOS: nur Codeprüfung der plattformneutralen Pfade, Toolnamen und Classpath-Trennung, **kein Laufzeittest**. Die eingecheckten IntelliJ-Run-Konfigurationen bleiben konfigurierbare Windows-Vorlagen mit lokal anzupassenden Interpreter-/JDK-Pfaden. Es gibt keine neue Pflichtabhängigkeit.

## Wrapper- und Deployment-Fehlerprüfungen

`VerificationScriptHarness` ist ein neu entdeckter Einstieg im kanonischen Runner. Er startet `scripts/tests/verification.Tests.ps1`. Die Tests kopieren die **echten** Verify-/Deploy-Skripte in zufällige temporäre Projektverzeichnisse und ersetzen dort ausschließlich externe Tool-/Runner-/Servergrenzen. Keine Live-Konfiguration, Produktionsquelle oder echten Spielerdaten werden für Fehlerfälle verändert.

Abgedeckt:

- erfolgreicher Build + vollständiger Nachweis → Exit 0 und eindeutige Erfolgsmeldung;
- Maven Exit 17 und Harness Exit 23 → Fehler, keine falsche Erfolgsmeldung, kein Deployment;
- fehlende Voraussetzung mit Handlungsanweisung;
- fehlender, unvollständiger, ungültiger und nicht als JSON lesbarer Nachweis; ein alter grüner Nachweis genügt ausdrücklich nicht;
- `-Offline` und exakter eigener Repository-Pfad mit Leerzeichen bei beiden Stufen;
- Verify verändert weder bestehende Dev-JAR/Operator-YAML/PID-Sentinels noch ruft es Stop/Start auf;
- fehlgeschlagenes Gate lässt Deploy-JAR und Server unangetastet;
- Stop Exit 29 verhindert Ersetzen; erfolgreicher Fixture-Deploy ersetzt den Kandidaten und meldet SHA-256;
- echte Discovery-Funktionen melden fehlendes JAVA_HOME/MAVEN_HOME sowie Found Java 17 / Expected Java 21 verständlich.

## Selektiv verbesserte Tests

| Harness | Alte Fragilität | Neue Prüfung |
|---|---|---|
| `SettingsModuleLifecycleHarness` | Literale `new`, `registerEvents`, Command-Setter, Cleanup-Zeilen und deren Source-Reihenfolge | Echtes `SettingsModule.enable/disable`, reale Command-Objekte und HandlerLists, exakte Listener-Typen, Observer-Abmeldung, Schließen aller aktiven Menütypen vor Handler-Abmeldung, Runtime-Freigabe, Getter-Sperre, Re-enable; injizierte Teilregistrierungsfehler an allen drei Listenern |
| `PresenceLifecycleHarness` | Wortlaut von Registrierung, Online-Seeding, Clear/Null und Getter | Echtes Modul mit echten Player-/Settings-/Social-/Friend-Services: ausschließlich geladene Online-Spieler seeden, keine Startbenachrichtigung, reale Join/Quit-Handler, Cleanup/Fresh-State/Re-enable, Fehler nach Registrierung und beim Seeding |
| `VisibilityCompositionHarness` | Permanente untere rechte Position trotz aktueller owning Layout-Änderung | CHEST-Rendering und tatsächlicher Klick→Players-Halter an der aktuellen `DEFAULT_LAYOUT`-Position; alle übrigen Pane-/Mutation-/Ghost-Control-Prüfungen bleiben |

`ModuleRuntimeFixture` ersetzt die äußere Paper-Grenze, nicht die geprüften Module. Wie vorhandene Repository-Fixtures verwendet es Reflection/Unsafe für dependency-module shells und den JavaPlugin-Testkörper. Private Runtime-Maps werden für Cleanup mit kontrollierten Inventaren befüllt; bestehende konkrete Holder-/stale-inventory-Verhaltenstests bleiben zusätzlich erhalten. Das ist kein Ersatz für einen nativen Paper-Start und kein neues Produktionsframework.

### Tatsächlich ausgeführte Formatierungs- und Negativkontrollen

Separate Kopien ausgewählter **Produktionsklassen** wurden mit `javac --release 21` in ein isoliertes Klassenverzeichnis kompiliert und vor den unveränderten Klassen auf die Test-Classpath gesetzt. Die echten Projektquellen blieben unverändert; kein Restore war nötig.

| Kontrolle | Erwartet / tatsächlich |
|---|---|
| Settings: Whitespace/Zeilenumbruch an `registerEvents`, anders formatierte Null-Zuweisungen | PASS, Exit 0, 44 Checks |
| Settings: Registrierung des VisibilitySettings-Listeners entfernt | FAIL, Exit 1, „real enable registers exactly the three owning listeners“ |
| Presence: Whitespace/Zeilenumbruch an Registrierung und Null-Zuweisungen | PASS, Exit 0, 24 Checks |
| Presence: Disable-Handler-Abmeldung entfernt | FAIL, Exit 1, „disable clears owned state and actual handlers“ |
| Visibility: umgebrochenes `.button(...)` | PASS, Exit 0, 54 Checks |
| Visibility: Verwaltungskiste als STONE gerendert | FAIL, Exit 1, „Manage Players chest renders at its owning layout placement“ |
| Visibility: Verwaltungskiste auf einen anderen freien Slot bewegt | PASS, Exit 0, 54 Checks |

Die Settings-Prüfung verlangt dabei Gleichheit des vollständigen Handler-Bestands während des Schließens. Die Formatierungs-/Negativkontrollen wurden auch gegen diese endgültige Fassung wiederholt.

Beibehalten: strikte Descriptor-/Permission-/Hierarchy-Verträge, Source-Verbote für unzulässige Abhängigkeiten, GUI-Owner/Inventory/Security-Prüfungen, Persistence-/Rollback-/Reload-Harnesses. Nicht pauschal gelöscht: verbleibende Bootstrap-Reihenfolge und Reload-Listen-Textprüfungen in Permission/DailyQuest/Presence und weitere ähnliche Lifecycle-Harnesses. Ihre vollständige Ablösung würde mehr vom realen Composition Root und seinen externen Abhängigkeiten isolierbar machen müssen; bloße Existenzprüfungen wären schwächer.

## Architektur und Dokumentation

`docs/ARCHITECTURE.md` erklärt Composition Root, Modulstart/rückwärtiges Cleanup, Rollen, State-/Dirty-/Commit-Owner, bestehende Observer/Callbacks, Reload und ConfigEvolution. Sechs konkrete Wege führen durch neue Settings, Commands, Config-Werte, Module, GUI-Actions und persistente Daten. Aktuelle Implementierung, bevorzugte Konvention und tatsächlich automatisch geprüfte Grenzen sind getrennt.

README und Developer Guide führen zu Verify und dieser Referenz. Korrigiert: veraltete Modul-/Reload-Zahlen im README durch maßgebliche Verdrahtungsreferenz, fehlendes Player-Menu in der Lobby-Registry-Beschreibung, Friends als angeblich stets aktiv auf Slot 1, Hotbar-Beispiel/Kollisionshinweis und Hidden-Variante, Daily-Same-Cycle/Force-Verhalten, angeblich eingecheckte Stop-Run-Konfiguration, persönliche Maven-Pfade im Einstieg und alter Ergebnisordner. Historische Reports wurden nicht umgeschrieben.

## Abschließende Validierung

Offizieller vollständiger Lauf unter Windows, offline mit dem vorhandenen lokalen Maven-Repository:

| Ergebnis | Final |
|---|---:|
| Maven `clean package` | erfolgreich, Exit 0 |
| Kanonische Compilation / Classpath | erfolgreich, Exit 0 |
| Discovered / Executed / Passed | 181 / 181 / 181 |
| Failed / MissingInvalid | 0 / 0 |
| TotalChecks | 27.849 |
| Verify Exit-Code | 0 |
| Baseline-Einstiege nicht mehr vorhanden | 0 |
| Integrierte Wrapper-/Deploy-Prüfungen | 46 bestanden |

Alle 180 Baseline-Einstiege sind erhalten; `VerificationScriptHarness` ist der eine neue ausführbare Einstieg. Fokussiert bestanden Settings-Lifecycle (44), Presence-Lifecycle (24), Visibility-Komposition (54), Skriptprüfungen (46) und Dokumentlinks (29). Die Gesamtzahl enthält außerdem die automatisch zusätzlichen Source-Audit- und Dokumentlink-Prüfungen. Sie ist kein aus historischen Counts abgeleiteter Sollwert.

PowerShell-Parserprüfung aller Skripte und `git diff --check`: erfolgreich. Finale Ergebnisnachweise: `target/verification/harnesses/results.json`, `inventory.txt` und Einzelprozess-Logs. Der normale Verify-Lauf hat die alte Dev-Server-Receipt nicht überschrieben; deployte JAR und Serversteuerungsdateien blieben unverändert. Ein zusätzlicher Hashvergleich von 16 korrekt aufgelösten Dev-Pfaden während der Regression (JAR, PID/Stop-Request, alte Runner-Receipt, vorhandene Feature-YAMLs und beide Sprachen) ergab null Änderungen. Der tatsächlich ausgeführte Verify-Lauf startete/stoppte keinen Server und führte kein Deployment aus.

Kein nativer Paper- oder Minecraft-Client-Test wurde durchgeführt: keine Produktions-/Plugin-Packaging-Änderung; Skript-/Deploy-Gates wurden isoliert geprüft, ohne den echten Dev-Server zu stoppen oder zu deployen.

## Geänderte Dateien

1. `README.md`
2. `docs/ARCHITECTURE.md`
3. `docs/DEVELOPER_GUIDE.md`
4. `reports/architecture-modernization/PHASE_01_ARCHITECTURE_VALIDATION.md`
5. `scripts/development-environment.ps1`
6. `scripts/verify.ps1`
7. `scripts/run-harnesses.ps1`
8. `scripts/build-and-deploy.ps1`
9. `scripts/start-dev-server.ps1`
10. `scripts/stop-dev-server.ps1`
11. `scripts/tests/verification.Tests.ps1`
12. `src/test/java/dev/vapee/core/module/ModuleRuntimeFixture.java`
13. `src/test/java/dev/vapee/core/module/VerificationScriptHarness.java`
14. `src/test/java/dev/vapee/core/settings/visibility/SettingsModuleLifecycleHarness.java`
15. `src/test/java/dev/vapee/core/presence/PresenceLifecycleHarness.java`
16. `src/test/java/dev/vapee/core/settings/visibility/VisibilityCompositionHarness.java`

## Priorisierter Handoff für Phase 2

1. **Gezielter Lifecycle-/Bootstrap-Testpfad:** Nach dem gezeigten Muster echte Verdrahtung statt literaler Reload-Listen/Reihenfolgen prüfen. Mit DailyQuest-/Core-Wiring beginnen; nicht Descriptor-/Security-Verträge abschwächen. Späte Enable-Fehler separat behandeln: etwa liegen Settings-Observer-Starts nach dem bisherigen Registration-try; die jetzigen Teilfehlerprüfungen decken Registrierung, nicht beliebige spätere Fehler ab.
2. **Kleine, begründete Ownership-Korrekturen:** Bestehende Modul-zu-Modul-Konstruktoren und den von mehreren Features importierten `utility/OnlinePlayerResolver` einzeln prüfen. Keine pauschale Package-Verschiebung. Im LobbyExperience-Enable-Fehlerpfad wird der neu hinzugefügte Settings-Back-Opener noch nicht wie Visibility zurückgesetzt; das bleibt ein konkret zu prüfender Cleanup-Fall außerhalb dieser Produktionscode-freien Phase.
3. **Formatierung planbar machen:** Aktuell fehlt ein verbindlicher automatischer Java-Formatter. Ein späterer begrenzter Pilot sollte eine festgelegte Formatierung plus Änderungskontrolle einführen; keine große Formatierungswelle mit fachlichen Änderungen mischen. Die zwei jetzt verbesserten Source-Tests beseitigen erste falsche Formatierungsabhängigkeiten, nicht alle.
4. **Große Adapter/Repositories erst mit gezielten Verhaltensverträgen zerlegen:** `BlackjackCommand` und `FilePlayerRepository` bleiben unverändert. Ihre Aufteilung darf erst nach klaren Feature-/Save-Grenzen erfolgen, ohne zweite State-Authority oder neues allgemeines Framework.

Lokaler Commit nach erfolgreicher Validierung: `Establish architecture and validation foundation`. Der vollständige SHA wird in der Abschlussantwort ausgegeben und über `git log -1 --format=%H` ermittelt; der enthaltende Commit kann seinen eigenen Hash nicht vorab im Report speichern. Vor Commit enthält der Arbeitsbaum ausschließlich die 16 oben aufgeführten Phase-Dateien. Der abschließende Post-Commit-Status wird separat geprüft und in der Abschlussantwort gemeldet. Push: nein. Merge: nein.
