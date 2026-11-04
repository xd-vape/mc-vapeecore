# Step 06B – Kompatibilität bestehender Sprachdateien

03.11.2026, Hauptprojekt `E:\NEXTCLOUD\Programmierung\JAVA\MINECRAFT\VapeeCore`, Branch `codex/step06b-chat-design-system`. Ausgangs-HEAD `0e4f2467fec56795ace0df1b3d4463eb260ea728`; diese Korrektur liegt als ungestagter Follow-up vor. Frühere Commits werden nicht geändert.

## Nachgewiesene Ursache

Step 06B ergänzte für `friends.presence.online` und `friends.presence.offline` den Platzhalter `<status>`. Die neuen gebündelten Texte benötigen damit `{player, status}`, während bestehende EN-/DE-Operator-Dateien weiterhin den vorherigen gültigen Vertrag `{player}` verwenden.

Die bisherige Vorbereitung verglich die Operator-Overrides mit den neuen gebündelten Platzhaltern auf exakte Gleichheit. Das führte bereits beim ersten Modul Localization zum Abbruch und zur Deaktivierung von VapeeCore. Die spätere `/setspawn`-Exception ist eine Folge dieses fehlgeschlagenen Starts, kein separater Permission- oder Command-Fehler. Die Compatibility-Lücke wurde in Step 06B nicht durch einen Upgrade-Test mit vorhandenen Sprachdateien abgedeckt.

Die vorhandenen Operator-Sprachdateien wurden ausschließlich gelesen. Für beide Schlüssel und beide Sprachen wurde tatsächlich `expected=[player, status]`, `actual=[player]` festgestellt. Vor der Korrektur scheiterte die Vorbereitung mit genau der gemeldeten Exception; danach akzeptiert sie dieselben Dateien ohne Änderungen.

## Gezielte Korrektur

- `TranslationCatalog.validateParity` bleibt für gebündelte Kataloge und allgemeine Invarianten strikt.
- `validateOverrides` erlaubt ausschließlich bei den zwei exakt benannten Presence-Schlüsseln den bisherigen `{player}`-Vertrag, und nur wenn der aktuelle kanonische Vertrag exakt `{player, status}` ist.
- Fehlender Spieler, umbenannte/zusätzliche Parameter, ähnliche andere Schlüssel und zukünftige andere kanonische Schemata bleiben Fehler. Alle übrigen Operator-Overrides werden unverändert strikt geprüft.
- `LocalizationService.prepare` verwendet die Operator-Validierung. Die vorhandene Snapshot-/Apply-/Rollback-Logik bleibt unverändert.
- Fehlermeldungen nennen künftig erwartete und tatsächliche Platzhalter.

Keine Sprachdatei wird migriert, umgeschrieben oder gelöscht. Alte explizite Operator-Texte und ihre bisherigen Farben bleiben maßgeblich; die neuen gebündelten Texte verwenden weiterhin die zentrale semantische Palette und das grüne Online-Statuswort. Keine neuen Module, Config-Versionen oder Scheduler; keine Änderungen an Commands, Permissions, Spieler- oder Freundesdaten.

## Ausgeführte Prüfungen

`LocalizationCompatibilityHarness`: **98 Checks**. Echte LocalizationModule-Starts mit vorhandenen alten/neuen EN-/DE-Overrides und gemischten Versionen; genaue Dateibytes bleiben erhalten; Literalwerte erzeugen keine ClickEvents. Reload-Prepare, Apply, exakter Rollback, Fehler eines späteren Reload-Teilnehmers, ungültiger deutscher Kandidat und erneuter erfolgreicher Reload werden über die bestehenden Module/den ReloadService geprüft. Negative Kontrollen sichern strikte gebündelte Parität, andere Schlüssel, zusätzliche/falsch benannte Parameter, fehlenden Spieler und zukünftige Schemata ab.

Vollständiger `pwsh -File .\scripts\verify.ps1` im tatsächlichen Hauptprojekt: **228 entdeckt / 228 ausgeführt / 228 bestanden, 33.567 Checks, Failed=0, Missing/Invalid=0**. Maven clean package und Spotless PASS. Kein bestehender Harness wurde entfernt oder abgeschwächt. Die vorherige separate Reparatur der 17 Archivlinks bleibt eine eigene ungestagte Änderung.

## Native Paper-Akzeptanz

Zwei bestätigte frische, automatisch beendete Testinstanzen auf Loopback-Port 25649, Paper 1.21.11 Build 132-c5eb079, Java 21.0.12.1 und LuckPerms 5.5.84. Vollständige alte EN-/DE-Kataloge stammen aus dem eingecheckten Stand f4970a9; Runtime-Binaries und eine Lobby-Testkonfiguration aus einer bereits gestoppten früheren isolierten Testumgebung. Keine Operator-Serverdaten werden als Fixture verwendet.

1. **Negativkontrolle:** archiviertes ungeändertes Step-06B-JAR reproduziert `Translation placeholder mismatch: friends.presence.offline`; VapeeCore bleibt deaktiviert, Rollback vor anderen Modulen. Der unabhängig vom deaktivierten Plugin-Classloader arbeitende Testhelfer bestätigt diesen erwarteten Zustand mit **3 Checks**.
2. **Korrigierter Build:** **14 Checks**, keine Fehler. Alle 27 Module sind aktiviert; alte EN-/DE-Texte sind geladen und rendern Literalnamen korrekt; der registrierte `/core reload` publiziert einen neuen Sprach-Snapshot und lässt alle Module aktiv; `/setspawn` besitzt wieder einen Executor eines aktivierten Plugins. Der Test ruft `/setspawn` nicht als echter Spieler auf und bestätigt keine Minecraft-Client-Darstellung.

Die alte und neue isolierte Sprachdatei bleiben jeweils byte-identisch vor/nach Start und Reload. Beide bestätigten Paper-Prozesse enden mit Exitcode 0; die Testverzeichnisse bleiben als Belege erhalten. Ein erster negativer Probe-Lauf scheiterte im Testhelfer an einer Klassenauflösung zum bereits deaktivierten Plugin. Der Helfer wurde auf reflektierten Zugriff umgestellt und die Negativkontrolle vollständig wiederholt; dieser erste Helferfehler wird nicht als bestandener Akzeptanztest gezählt.

## Schutz und Übergabe

Die beiden vorhandenen Operator-Sprachdateien und beide Run-Konfigurationen werden per SHA-256 vor/nach der Korrektur verglichen. Die bestehende Berichtsarchivierung, die vier vorherigen Dokumentations-Linkkorrekturen und der Step-06B-Commitnachweis bleiben unverändert und ungestagt. Es erfolgt kein Commit, Push, Merge, Deployment oder Eingriff in den laufenden Operator-Server.

Für den laufenden Server muss der korrigierte Build bewusst deployed und VapeeCore durch einen regulären Serverneustart geladen werden. Die bereits im Serverprozess geladene alte JAR-Version wird durch einen lokalen Build nicht ausgetauscht. Vorhandene Sprachdateien können erhalten bleiben.

## Pfade dieser Korrektur

- `src/main/java/dev/vapee/core/i18n/TranslationCatalog.java`
- `src/main/java/dev/vapee/core/i18n/LocalizationService.java`
- `src/test/java/dev/vapee/core/i18n/LocalizationCompatibilityHarness.java`
- `docs/development/chat-design-system.md`
- `reports/06b/LOCALIZATION_UPGRADE_COMPATIBILITY_FIX.md`

Diagnoseausgaben, native Fixture-Quellen, Katalogkopien aus Git, Verifier-Receipt und Logs liegen außerhalb des Projekts unter `C:\Users\vapee\.codex\visualizations\2026\10\25\01a17432-7597-7ff1-bc9d-34201a027523\localization-compatibility-fix-20261103`. Der abschließende maschinenlesbare Nachweis ist `delivery-receipt.json`.
