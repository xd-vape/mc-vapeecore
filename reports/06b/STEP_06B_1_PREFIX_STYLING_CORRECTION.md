# Step 06B.1 – Prefix Styling Correction

## Ausgangszustand und technische Ursache

03.11.2026, Hauptprojekt `E:\NEXTCLOUD\Programmierung\JAVA\MINECRAFT\VapeeCore`, bestehender Branch `codex/step06b-chat-design-system`, Ausgangs-HEAD `0e4f2467fec56795ace0df1b3d4463eb260ea728`.

Step 06B setzte die dunkelgraue Prefixfarbe sowohl am gesamten formatierten Prefix als auch am Label der neuen Prefix-Komposition. Dadurch verloren Friends und Clan ihre ursprünglichen Identitätsfarben. Die Klammerfarbe war nicht als eigenständige Rolle modelliert.

Vor Beginn bestanden bereits die manuell korrigierte AGENTS.md, vier Linkkorrekturen zur Berichtsarchivierung, die uncommittete Lokalisierungs-Upgrade-Korrektur einschließlich Tests/Dokumentation, ein Commitnachweis und 55 nach OLD verschobene Berichte. Diese Ausgangslage ist separat per Status-/Hash-Manifest erfasst. Die Präfixarbeit erfolgt direkt im Hauptprojekt, nicht in einem neuen Entwicklungs-Worktree.

## Palette und API

| Element | Zentrale Definition | Standard |
|---|---|---|
| `[` und `]` | `MessageStyle.BRACKET` | DARK_GRAY `#555555` |
| Friends / Freunde | `MessageStyle.FRIENDS_LABEL` | GOLD `#FFAA00` |
| Clan | `MessageStyle.CLAN_LABEL` | DARK_PURPLE `#AA00AA` |
| Staff / Team | `MessageStyle.STAFF_LABEL` | DARK_GRAY `#555555`, unabhängig von BRACKET |
| Unbenanntes neues Feature / bisheriger Overload | `MessageStyle.DEFAULT_LABEL` | neutrales GRAY `#AAAAAA` |

Das bisherige `PREFIX` bleibt als kompatibler Alias der Klammerfarbe erhalten. Andere semantische Farben, Akzentkonfiguration, Rangfarben, Clan-Namen/-Tags und interaktive Aktionsfarben werden nicht verändert.

- `prefix(label, labelColor)` erzeugt drei getrennte Components mit unabhängig gesetzten Farben.
- `feature(label, labelColor, body)` kombiniert einen nackten lokalisierten Feature-Namen mit separatem neutralem Body.
- `message(formattedPrefix, labelColor, body)` akzeptiert weiterhin vollständige bereits eingeklammerte Prefixe. Die bisherigen Overloads bleiben verfügbar und verwenden die zentrale neutrale Label-Rückfallfarbe.
- `SocialChannel` ordnet seine bestehenden stabilen Enum-Identitäten den zentralen Label-Farben zu. Es gibt keinen Vergleich mit lokalisierten Namen und keine vorsorglichen Party-/Guild-/Support-Registrierungen.

Vollständige übersetzbare Prefixe werden über einen internen Format-Component durch den bestehenden LocalizationService aufgelöst. Erst danach werden ausschließlich die Klammerzeichen innerhalb dieses Prefix-Components gefärbt. Das ist dieselbe zentrale Komposition, kein zweiter Formatter-Service. Übersetzungsschlüssel, Katalogdateien und Platzhalter bleiben unverändert; keine neue Datenmigration.

Neue Features können dieselbe API mit einer zentral definierten Label-Farbe verwenden, ohne eigene Klammern zusammenzusetzen. Eine spätere Änderung von STAFF_LABEL verändert BRACKET nicht.

## Delivery, Operator-Overrides und erhaltenes Verhalten

Die sichtbaren Standardtexte bleiben `[Friends]` / `[Freunde]`, `[Clan]`, `[Staff]` / `[Team]`, jeweils mit einem Leerzeichen vor dem Body. Friends/Clan behalten den vorhandenen Operator-Wrapper; Staff bleibt raw. Keine doppelten Feature-Prefixe.

Vollständige Operator-Prefixe bleiben vollständig; sie werden nicht nochmals eingeklammert oder als MiniMessage neu interpretiert. Wortlaut, Abstände, Dekorationen und Click-/HoverEvents bleiben erhalten. Explizite Operator-Label-Farben behalten ihren Vorrang, die Klammern werden zentral gefärbt. Die Formatierung bearbeitet weder Hover-Inhalte noch Aktionsbuttons im Nachrichtenbody. `[Accept]` / `[Deny]` bleiben Aktionen mit ihren bisherigen Farben und Befehlen.

Warn-/Mute-Blöcke, Statusdarstellung, Spielernamen/Ränge, Accent-Reload/Rollback, TPA ohne Prefix, präfixfreie Friends-Join-Zusammenfassung und Staff PUBLIC/HIDDEN werden fachlich nicht geändert. Die vorhandene separate Lokalisierungs-Upgrade-Korrektur bleibt im Arbeitsbaum erhalten und wird nicht in diesen Präfix-Commit aufgenommen.

## Regeln und Entwicklerdokumentation

Die manuell aktualisierte Arbeitsdatei AGENTS.md war bereits korrekt. Ihre Bytes werden nicht geändert. Ihre auftragsbezogenen Chat- und Qualitätsregeln werden gezielt übernommen; sonstige vorhandene manuelle Anpassungen bleiben außerhalb des Commits. Die Regeln verlangen getrennte Klammer-/Label-Farben, zentrale Feature-Farben, sprachunabhängige Identität und die bestehende API.

`docs/development/chat-design-system.md` beschreibt die tatsächlichen Overloads, Farben, kompatiblen vollständigen Prefixe und zukünftige Verwendung. Ausschließlich widersprüchliche Aussagen in DEVELOPER_GUIDE und SERVICE_DEPENDENCIES werden korrigiert. Die bereits vorhandene Kompatibilitätsnotiz und die separaten Archivlink-Korrekturen bleiben ungestagt.

## Tests und Qualität

`PrefixStylingHarness` besteht fokussiert **71 Checks** für tatsächlich gerenderte Adventure-Components: getrennte Klammer-/Label-Nodes und Farbspannen, EN/DE, neue Feature-Farbe ohne Registrierung, identischer Override-Text für unterschiedliche Identitäten, neutraler Body, unveränderte Rangfarbe, Operator-Wrapper, ClickEvents, HoverEvents und Dekorationen. Die Farbprüfung beschränkt sich auf den jeweiligen Systemprefix und verwechselt grüne Aktionsklammern nicht mit Prefixklammern.

Vorhandene ChatDesignSystem-, SocialChatStyling- und MessageAccent-Erwartungen werden nur für die beauftragte Farbaufteilung angepasst. Keine Harnesses werden entfernt oder deaktiviert. Staff-, TPA-, Moderations-, Presence-, Join-Summary- und Reload-Prüfungen bleiben bestehen.

Beide vollständigen kanonischen Verifier bestehen mit Maven clean package PASS, Spotless PASS, Failed=0 und Missing/Invalid=0:

| Geprüfter Stand | Entdeckt / ausgeführt / bestanden | Checks |
|---|---|---|
| Tatsächliches Hauptprojekt, einschließlich der erhaltenen uncommitteten Korrekturen | 229 / 229 / 229 | 33.639 |
| Reine Quellenkopie ausschließlich des vorgesehenen Präfix-Commit-Inhalts | 228 / 228 / 228 | 33.540 |

Alle 227 ursprünglichen Step-06B-Harnesses bleiben erhalten; der Präfix-Commit ergänzt PrefixStylingHarness mit 71 Checks. Der Arbeitsbaum enthält zusätzlich den schon zuvor vorhandenen, weiterhin separat uncommitteten LocalizationCompatibilityHarness mit 98 Checks. Die abweichende Source-Inventur erklärt den zusätzlichen einen Check zwischen den beiden Layouts. Die unterschiedlichen Gesamtzahlen sind damit beabsichtigt, kein ausgelassener Testlauf. Die betroffenen bestehenden Chat-Harnesses bestehen auch fokussiert.

Die reine Quellenkopie prüft, dass die gezielte Teil-Staging-Auswahl eigenständig kompiliert und verifiziert werden kann. Das ist ausschließlich eine Testkopie; die Implementierung und der Branch bleiben im Hauptprojekt. Der frühere README-Linkfehler ist im Arbeitsbaum durch die vorhandenen separaten Linkkorrekturen bereits behoben. Im Commit-Testbaum bleiben die noch eingecheckten historischen Berichtspfade erhalten; der unveränderte RepoLinkHarness läuft regulär und besteht dort ebenfalls.

## Git-Abgrenzung und Schutz

Genau ein neuer lokaler Präfix-Commit ist vorgesehen, ohne Amend/Rebase und ohne Änderung von 0e4f246. Für die überlappenden Dateien AGENTS.md, LocalizationService, DEVELOPER_GUIDE und Chat-Design-Dokumentation werden explizite Indexversionen verwendet, die nur die auftragsbezogenen Änderungen enthalten. Die Arbeitsdateien werden dabei nicht zurückgesetzt. Damit gelangen die bereits offenen Lokalisierungs- und Archivlink-Korrekturen nicht versehentlich in diesen Commit.

Vorhandene Run-Dateien, archivierte Berichte, Operator-Daten und die sonstigen lokalen Änderungen bleiben erhalten. Kein Push, Merge oder Deployment; kein Stoppen oder Verändern des Operator-Servers.

Die endgültige Commit-ID wird nach dem Commit außerhalb seines Inhalts im benachbarten `STEP_06B_1_PREFIX_STYLING_CORRECTION_COMMIT.txt` protokolliert. Der ursprüngliche Commit wird dafür nicht verändert.

## Geplante Commit-Pfade

1. `AGENTS.md` – ausschließlich auftragsbezogene vorhandene Chat-/Qualitätsregeln
2. `docs/DEVELOPER_GUIDE.md` – nur Prefix-Aussagen
3. `docs/SERVICE_DEPENDENCIES.md`
4. `docs/development/chat-design-system.md` – nur Prefix-Dokumentation
5. `reports/06b/STEP_06B_1_PREFIX_STYLING_CORRECTION.md`
6. `src/main/java/dev/vapee/core/i18n/LocalizationService.java` – nur Prefix-Renderadapter
7. `src/main/java/dev/vapee/core/message/MessageStyle.java`
8. `src/main/java/dev/vapee/core/message/SocialChannel.java`
9. `src/test/java/dev/vapee/core/message/MessageColorAssertions.java`
10. `src/test/java/dev/vapee/core/message/ChatDesignSystemHarness.java`
11. `src/test/java/dev/vapee/core/message/SocialChatStylingHarness.java`
12. `src/test/java/dev/vapee/core/config/MessageAccentHarness.java`
13. `src/test/java/dev/vapee/core/message/PrefixStylingHarness.java`

## Grenzen

Keine echte Minecraft-Client-Sichtprüfung und kein neuer nativer Paper-Akzeptanzlauf werden behauptet. Offen bleibt die gerenderte visuelle Bestätigung von Kontrast und Präfixdarstellung im Client. Explizite Operator-Overrides können bewusst von den gebündelten Label-Farben abweichen. Die unabhängige noch uncommittete Lokalisierungs-Upgrade-Korrektur und Berichtsarchivierung bleiben eigene Änderungen; sie werden durch diesen Commit nicht mitgeliefert oder zurückgenommen.

Prüflogs, Ausgangssicherung, gezielte Indexversionen und Schutzvergleiche liegen außerhalb des Projekts unter `C:\Users\vapee\.codex\visualizations\2026\10\25\01a17432-7597-7ff1-bc9d-34201a027523\step06b1-prefix-20261103`.
