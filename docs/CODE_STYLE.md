# Code-Konventionen und Formatter-Pilot

Diese Regeln gelten als Entwicklungsrichtung. **Automatisch geprüft werden vorerst nur die sechs Java-Dateien der Spotless-Allowlist in `pom.xml`.** Bestehende andere Dateien werden weder durch die Einführung dieser Regeln normalisiert noch als bereits konform bezeichnet. Zuständigkeiten und fachliche Grenzen stehen in [ARCHITECTURE.md](ARCHITECTURE.md).

## Ein Werkzeug für mechanische Java-Formatierung

Fest gewählt sind **Spotless Maven Plugin 2.43.0** und **Google Java Format 1.22.0 im AOSP-Stil**. AOSP passt zu vier Leerzeichen. Beide Versionen sind direkt in `pom.xml` festgeschrieben; es gibt keinen zweiten Java-Formatter. Die Kombination wurde mit JDK 21 einschließlich Records und guarded Pattern-Switch sowie offline nach dem Download geprüft. Versionierte Referenzen: [Spotless Maven](https://github.com/diffplug/spotless/blob/maven/2.43.0/plugin-maven/README.md), [Google Java Format](https://github.com/google/google-java-format/tree/v1.22.0).

Spotless ist für mechanische Ausgabe maßgeblich, `.editorconfig` liefert passende Editor-Defaults. Java wird als UTF-8 ohne BOM mit LF und abschließendem Newline gespeichert. `.gitattributes` erzwingt LF **nur für den Piloten**, auch bei Windows-Checkouts mit `core.autocrlf=true`. Es gibt keine automatische Konvertierung des gesamten Projekts.

- Vier Leerzeichen pro Block; Einrückung von Fortsetzungen und Zeilenumbrüche bestimmt der Formatter. Keine manuellen Spaltenausrichtungen gegen das Werkzeug erzwingen.
- Öffnende Klammer auf derselben Zeile; bei neuem Kontrollfluss vorzugsweise explizite Blöcke. Der Formatter ergänzt keine fachlich fehlenden Blöcke und schreibt vorhandene Schleifen/Switches nicht um.
- Methoden und zusammengehörige Abschnitte durch Leerzeilen trennen; keine vertikale Ausrichtung durch viele Leerzeichen. Die genaue mechanische Ausgabe übernimmt Spotless.
- Imports lexikografisch, statische Imports als separate Gruppe davor. Explizite Imports bevorzugen, keine neuen Wildcard-Imports. Bestehende vollqualifizierte Namen nicht im Rahmen einer Formatierung manuell umbauen. Der Pilot erfordert keine Import- oder Symbolumbenennung.
- Lange Stringliterale werden nicht automatisch zerlegt (`reflowLongStrings=false`); JavaDoc wird nicht neu umflossen (`formatJavadoc=false`). Kommentare sollen Verträge, Gründe oder Grenzen erklären, nicht offensichtliche Getter nacherzählen.
- `.editorconfig`: Java/XML/PowerShell vier, YAML zwei Leerzeichen. Markdown-Hardbreaks und bedeutende YAML-Blocktext-Leerzeichen bleiben vom automatischen Trailing-Whitespace-Trim ausgenommen. Das ist keine zusätzliche YAML-/Markdown-Formatter-Pipeline.

## Aktuelle Allowlist

| Datei | Grund |
|---|---|
| `src/main/java/dev/vapee/core/quest/daily/menu/ResetCountdownFormatter.java` | Kleine reine Utility; kompakte Methoden werden lesbar |
| `src/main/java/dev/vapee/core/friend/FriendPair.java` | Kleine Domain-Record mit unverändertem UUID-/Persistenzvertrag |
| `src/main/java/dev/vapee/core/presence/PresenceModule.java` | Überschaubarer realer Lifecycle und explizite Abhängigkeiten |
| `src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestInventoryHolder.java` | GUI-Bindung und veröffentlichter Render-State ohne Layoutänderung |
| `src/test/java/dev/vapee/core/presence/PresenceLifecycleHarness.java` | In Phase 1 verhaltensbasiert verbesserter Lifecycle-Test |
| `src/test/java/dev/vapee/core/settings/visibility/SettingsModuleLifecycleHarness.java` | Reale Registrierung, Teilfehler und Menü-Cleanup aus Phase 1 |

Neue Dateien außerhalb dieser Liste werden nicht automatisch aufgenommen. Eine spätere Erweiterung benötigt bewusstes Review der Allowlist, Git-LF-Regeln und Source-Test-Auswirkungen. `VapeeCore` und `FilePlayerRepository` gehören nicht zum Piloten.

## Bearbeiten → Formatieren → Prüfen → Verifizieren → Commit

Aus dem Projektordner mit JDK 21 und Maven auf PATH. Für direkte `mvn`-Befehle muss das Maven-`bin`-Verzeichnis auf PATH liegen; `MAVEN_HOME` allein macht keinen Shell-Befehl verfügbar. Verify kann Maven zusätzlich über `MAVEN_HOME` oder den optionalen Windows-/IntelliJ-Fallback finden. Alternativ die Goals im Maven-Toolwindow der IDE ausführen:

```powershell
# Explizit anwenden; verändert ausschließlich die Pilotdateien:
mvn spotless:apply
# Nur prüfen; verändert keine Java-Quelle:
mvn spotless:check
# Danach Build, Formatprüfung und vollständige Behavioral Regression:
pwsh -File ./scripts/verify.ps1
```

Nach einmaligem Laden der Formatter-Abhängigkeiten funktioniert beispielsweise `mvn -o spotless:check`. Ein eigenes Repository wird mit `'-Dmaven.repo.local=<lokales Repository>'` übergeben; Verify besitzt weiterhin `-Offline` und `-MavenRepository`. Ein leerer Offline-Cache kann keine fehlenden Artefakte ersetzen. Beim ersten Einrichten `mvn spotless:check` bzw. `apply` online ausführen, danach offline prüfen.

Nur **eine bereits erlaubte** Datei anwenden:

```powershell
mvn spotless:apply '-DspotlessFiles=.*ResetCountdownFormatter[.]java'
```

`spotlessFiles` ist ein Regex gegen den absoluten Dateipfad, kein Shell-Glob. Der selektierte Dateiname muss eindeutig sein. Der Filter verkleinert die Allowlist, erweitert sie nicht. Vor dem Commit immer die vollständige Pilotprüfung ohne Filter verwenden und den Diff ansehen.

`verify.ps1` führt `clean package`, danach `spotless:check` und erst nach dessen Erfolg den unveränderten vollständigen Runner aus. Ein Formatfehler ist ein Fehlercode und verhindert auch ein anschließendes Build-&-Deploy. Verify führt niemals `apply` aus. `mvn clean package` bleibt Build/Paketierung; die vollständige Prüfung inklusive aller eigenständigen Harnesses erfolgt über Verify.

Die wiederholbaren Gegenproben laufen separat mit `pwsh -File ./scripts/tests/formatter.Tests.ps1 -Offline` (optional `-MavenRepository`). Sie benutzen temporäre Projektkopien für echten Formatter, Java-21-Probe, AST-/Literalvergleich, Idempotenz, Scope und Fehlerdurchgabe. Die Wrapper-Fehlerprüfungen bleiben zusätzlich im kanonischen `VerificationScriptHarness` integriert.

## IntelliJ IDEA

EditorConfig-Unterstützung aktivieren bzw. aktiviert lassen; `.editorconfig` stellt Einrückung, Encoding und Zeilenende beim Bearbeiten bereit. Das ist keine Aufforderung zu „Reformat project“ oder „Optimize imports“ auf dem ganzen Projekt.

Die Maven-Toolwindow-Goals `spotless:apply` und `spotless:check` oder dieselben Terminalbefehle verwenden. Für eine einzelne erlaubte Datei den oben gezeigten Filter verwenden. Falls IntelliJs eingebautes **Reformat Code** eine andere Ausgabe erzeugt, entscheidet der gemeinsame Maven-Formatter. Keine persönliche IDE-Profildatei oder zusätzliche Formatter-Erweiterung ist erforderlich. Maven muss mit JDK 21 laufen.

Bei einem Check-Fehler die genannten Dateien prüfen, bewusst `apply` ausführen, den kleinen Diff kontrollieren und Check + Verify wiederholen. Ein unerwarteter Diff außerhalb des Piloten muss vor dem Commit geklärt werden; keine automatische Rücknahme fremder Arbeit. Bestehende andere Dateien wegen EditorConfig nicht gesammelt neu speichern.

## Namen und Klassenaufbau

| Element | Konvention |
|---|---|
| Package | Kleinbuchstaben unter `dev.vapee.core`, nach Feature und Verantwortung |
| Klasse / Interface / Record / Enum | `UpperCamelCase`, fachlicher Name; vorhandene Rollen wie `Module`, `Service`, `Repository`, `Menu`, `Listener` erkennbar halten |
| Methode / Variable / Feld / Parameter | `lowerCamelCase`, konkrete Bedeutung; boolesche Abfragen z. B. `isActive`, `hasNext`; keine unnötigen Abkürzungen |
| Konstante / Enum-Wert | `UPPER_SNAKE_CASE`; nur echte konstante Werte so benennen |
| Ausführbarer Verhaltenstest | `*Harness.java`, gleichnamige Top-Level-Klasse und vorhandene `main`-/Ergebniszeilen-Konvention |
| Testunterstützung | `*Fixture` bzw. bestehende eindeutige Hilfsnamen; keine irreführende ausführbare Harness-Klasse |

Felder gewöhnlich `private`; stabile Abhängigkeiten `final`. Sichtbarkeit nur für einen begründeten Vertrag erweitern. Konstruktorabhängigkeiten ausdrücklich übergeben, mit dem bestehenden `Objects.requireNonNull(value, "value")`-Muster prüfen. Erwartete Abwesenheit mit dem vorhandenen Optional-/Result-Vertrag ausdrücken; keinen neuen gemischten Null-/Optional-Vertrag nebenher schaffen. Bestehende Verträge werden durch diese Phase nicht geändert.

Gut lesbarer Aufbau: Konstanten, Felder, Konstruktoren, öffentlicher API-/Lifecycle-Bereich, private Helfer. `enable`/`disable` sinnvoll zusammenhalten; Records, verschachtelte Typen und lokale Featuregruppen dürfen eine passendere Reihenfolge haben. Der Formatter sortiert keine Methoden/Felder um. Eine solche Umordnung ist eine separate bewusste Änderung.

## Methoden, Fehler und Zuständigkeiten

Eine Methode extrahieren, wenn sie einen eigenständig verständlichen Schritt benennt, Wiederholung entfernt oder einen klaren Fehler-/Transaktionsbereich abgrenzt. Keine starre Zeilenzahl. Eine Hilfsklasse braucht eine zusammenhängende Verantwortung oder eine reale externe Grenze; nicht jede längere Methode benötigt eine neue Abstraktion. Kleine Regeln bleiben beim owning Feature. Vor Duplizieren vorhandene Services und kleine Interfaces suchen; keine allgemeine Utility als Ablage für fachliche Regeln.

Erwartete Validierungsfehler über den vorhandenen fachlichen Result-/Meldungspfad beantworten. Ungültige Programmierargumente früh ablehnen. Exceptions nicht verschlucken und nicht als scheinbaren Erfolg ausgeben. Unerwartete Runtime-Fehler an der zuständigen Grenze mit hilfreichem Feature-/UUID-/Action-Kontext und Exception protokollieren; normale Permission-Denials sind kein Stacktrace-Ereignis. Keine Secrets oder vollständigen privaten Inhalte ins Log schreiben.

Ressourcen in `finally`/try-with-resources bzw. dem bestehenden Lifecycle freigeben. Teilweises Enable muss eigene Registrierungen zurücknehmen; Disable entfernt nur eigene Ressourcen. Persistenz-Rollback/Dirty-Retry und den Zeitpunkt der Erfolgsmeldung beim zuständigen Service belassen. SettingsModule und LobbyExperienceModule zeigen modul-lokales Cleanup: Observer-Start gehört in denselben geschützten Bereich wie die Registrierung; Back-Callbacks haben denselben Owner wie das Parent-Menü. Unabhängige Freigaben fortsetzen, die ursprüngliche Startup-Exception erhalten und Freigabefehler mit Kontext anhängen. Disable muss nach Teilinitialisierung und bei Wiederholung sicher sein. Das ist keine Behauptung, dass sämtliche Module bereits jeden späten Fehler abdecken. Formatierung verändert keine Fehlerbehandlung.

Lifecycle-Harnesses treiben die echten `enable`-/`disable`-Pfade mit schmalen kontrollierten Außenstellen. Ein Fixture darf eine Registrierung nach ihrer Wirkung scheitern lassen; es darf das zu prüfende Cleanup nicht selbst erledigen. Prüfe auch Retry, Eigentumsgrenzen und absichtliche Defekte. Eine Source-Assertion nur durch Verhalten ersetzen, wenn der bisherige Architektur-/Sicherheitsvertrag dabei erhalten bleibt; reine Formatierungsvarianten müssen weiter bestehen.

## Package-Konvention

Feature-Ownership vor gleichförmiger Verzeichnisstruktur. Ein größeres Feature **kann** `FeatureModule`, `FeatureService` und Unterpackages `command`, `menu`, `listener`, `config`, `persistence` besitzen; kleine Features benötigen diese Verzeichnisse nicht. Bestehende Namen wie `friend/gui`, `settings/visibility` oder `player/repository` werden nicht umbenannt.

Zusammengehörige Regeln zusammenhalten, Unterpackages nur für bessere Navigation schaffen und für gemeinsame Infrastruktur einen klaren Owner bestimmen. Zirkuläre Abhängigkeiten vermeiden; die kleinste stabile Schnittstelle oder vorhandene Callback-/Observer-Grenze bevorzugen. Keine Klassen verschieben oder Interfaces erfinden, nur damit ein Verzeichnisbaum einheitlich aussieht. Geschäftsregeln, State und Commit-Grenzen bleiben bei ihren bisherigen Eigentümern.
