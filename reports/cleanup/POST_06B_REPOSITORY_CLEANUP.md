# Post-06B Repository Cleanup

Stand: 04.11.2026. Repository `E:\NEXTCLOUD\Programmierung\JAVA\MINECRAFT\VapeeCore`.

## Ausgangslage und Sicherung

Der ursprüngliche Branch `codex/step06b-chat-design-system`, lokaler `master`,
`origin/master` und der direkt abgefragte GitHub-`master` standen auf
`435ff92404ff785e4da8c03386378637ca406c22`. Dessen direkter Vorgänger ist
`0e4f2467fec56795ace0df1b3d4463eb260ea728`. Beide veröffentlichten Commits bleiben
unverändert. Der Index war leer; es gab keine laufende Merge-Operation.

Vor dem Branchwechsel wurden alle 122 einzelnen Porcelain-Status-Einträge mit
Pfad, Status, fachlicher Gruppe, Arbeitskopie-SHA-256 und ursprünglicher
HEAD-SHA-256 erfasst. Bei gelöschten Dateien ist die Arbeitskopie-Prüfsumme null;
bei neuen Dateien die ursprüngliche HEAD-Prüfsumme. Das vollständige
[Ausgangsmanifest](POST_06B_INITIAL_MANIFEST.json) enthält auch die 55
Original-/Archivpaare samt Git-Blob- und SHA-256-Nachweisen.

Zusätzlich wurden außerhalb des Repositories die vorhandenen Inhalte aller 794
getrackten beziehungsweise nicht ignorierten Dateipfade, vollständige Diffs,
Index-Diff, Status und Referenzen gesichert. Operator-Daten unter `dev-server/`
sowie ignorierte Entwicklungs- und Build-Dateien wurden nicht als Cleanup-
Kandidaten behandelt. Die Sicherung und Testlogs liegen unter
`C:\Users\vapee\.codex\visualizations\2026\10\25\01a17432-7597-7ff1-bc9d-34201a027523\cleanup-post06b-20261104`.

Nach der Sicherung wurde im bestehenden Hauptarbeitsbaum der lokale Branch
`codex/post06b-repository-cleanup` angelegt. Der unmittelbar anschließende
Vergleich bestätigte identische Inhalte und dieselben 122 Status-Einträge. Kein
zusätzlicher Entwicklungs-Worktree wurde angelegt; der bereits vorhandene
`chat-polish-wt` blieb unangetastet.

## Zuordnung sämtlicher ursprünglicher Änderungen

| Arbeitspaket | ursprüngliche Status-Einträge | Inhalt |
| --- | ---: | --- |
| Lokalisierung | 5 | zwei Produktionsklassen, Compatibility-Harness, Chat-Guide und bestehender Fehlerbericht |
| Berichtsarchiv | 114 | 55 alte Pfade als gelöscht, 55 Archivziele als untracked, vier aktive Dokumentationen mit 17 Linkkorrekturen |
| Projektanweisungen | 1 | ausschließlich drei Formatierungsanpassungen in AGENTS.md |
| Lokale Übergabenachweise | 2 | zwei geprüfte Commit-Receipts; bleiben unverändert untracked |
| **Gesamt** | **122** | acht modifizierte, 55 gelöschte und 59 untracked Dateien |

Die Archivverschiebungen sind beabsichtigte Benutzerarbeit. Weder alte Pfade
wiederherzustellen noch Archivdateien zu entfernen war erforderlich. Weitere
unbekannte oder angefangene Feature-Dateien wurden unter den 122 Einträgen nicht
gefunden. Neu durch dieses Cleanup entstehen der erweiterte Link-Harness, dieses
Dokument und das Ausgangsmanifest; sie sind keine ursprünglichen Status-Einträge.

## Geprüfte Lokalisierungskorrektur

Die vorhandenen Änderungen bleiben inhaltlich erhalten:

- `LocalizationService.prepare` verwendet für Operator-Dateien
  `TranslationCatalog.validateOverrides`.
- Ausschließlich `friends.presence.online` und `friends.presence.offline` dürfen
  den alten Vertrag `{player}` behalten, wenn der kanonische Vertrag exakt
  `{player, status}` ist. Ähnliche Schlüssel, fehlende Spieler, zusätzliche oder
  umbenannte Parameter und zukünftige andere kanonische Schemata bleiben Fehler.
- Die gebündelten EN-/DE-Kataloge verwenden weiterhin strikte Parität und die
  Step-06B-Platzhalter. Die semantischen `vc_*`-Tags bleiben Styling-Tags und
  werden nicht als fachliche Platzhalter behandelt.
- Weder Sprach-Overrides noch gebündelte Kataloge werden umgeschrieben.
  Literal-Components, Click-/Hover-Verhalten, Snapshot-Publikation und der
  bestehende transaktionale Reload-Vertrag bleiben erhalten.

`LocalizationCompatibilityHarness` prüft echte Modulstarts mit allen vier
EN-/DE-Kombinationen alter/neuer Overrides, unveränderte Dateibytes, Literalnamen,
strikte Negativfälle, erfolgreichen Reload, Prepare-Fehler und exakten Rollback
auch nach dem Fehler eines späteren Teilnehmers. Der
[vorhandene Fehlerbericht](../06b/LOCALIZATION_UPGRADE_COMPATIBILITY_FIX.md) und
der [Chat-Guide](../../docs/development/chat-design-system.md) gehören zum Paket.
Es war keine zusätzliche Produktionskorrektur erforderlich.

Die native Paper-Akzeptanz im Fehlerbericht beschreibt die damaligen isolierten
Läufe vom 03.11.2026. Dieses Cleanup führt keine neue native Paper-Instanz aus
und behauptet keine neue visuelle Minecraft-Client-Abnahme. Der laufende
Operator-Server wurde nicht als Fixture verwendet.

## Archivierung und tatsächliche Linkfehler

Alle 55 Archivziele waren vollständig vorhanden. Ihre Git-gefilterten Inhalte
stimmten vor dem Cleanup exakt mit den ursprünglichen HEAD-Blobs überein.
Nur zwei Arbeitskopien waren auch raw byte-identisch mit den Git-Blobs;
53 hatten die durch `core.autocrlf=true` erklärbaren CRLF-Zeilenenden. Beide
SHA-256-Werte sind im Manifest ausdrücklich getrennt, eine rohe Byte-Gleichheit
aller 55 Dateien wird nicht behauptet.

Die vorhandenen vier Dokumentationsänderungen korrigieren 17 Links in README,
DEVELOPER_GUIDE, MODULE_DEVELOPMENT und PERSISTENCE_DEVELOPMENT auf `reports/OLD/`.
Zusätzlich ergab die Inhaltsprüfung vier defekte Markdown-Verweise in drei
historischen Berichten:

| Archivbericht | Korrektur |
| --- | --- |
| `26b-permission-hardening/PERMISSION_HARDENING.md` | zwei Links auf `../../../docs/PERMISSIONS.md`; Audit-Fragment auf die vorhandene Überschrift zum aktuellen Phase-29-Vertrag aktualisiert |
| `30a1-moderation-authorization-hotfix/MODERATION_AUTHORIZATION_HOTFIX.md` | Audit-Link auf `../30a-full-codebase-audit/FULL_CODEBASE_AUDIT.md`; Fragment erhalten |
| `architecture-modernization/PHASE_04A_MODULE_LIFECYCLE.md` | Entwicklerguide auf `../../../docs/MODULE_DEVELOPMENT.md` |

Die ersten drei Verweise waren bereits an den bisherigen Berichtspfaden defekt;
der vierte benötigte wegen der zusätzlichen Archiv-Verzeichnisebene einen
weiteren Elternpfad. Die ergänzende Fragmentprüfung fand außerdem beim ersten
Permissions-Link einen veralteten Phase-26B-Anker; die vorhandene Überschrift
heißt inzwischen `Vollständiger Command-/Descriptor-Audit (aktueller Phase-29-Vertrag)`.
Dieser Fragmentnachtrag erhält einen weiteren kleinen lokalen Archiv-Commit.
45 Überschriftenfragmente wurden gegen die vorhandenen Markdown-Überschriften
geprüft; der historische Zeilenverweis `#L282` im Security-Audit ist ausdrücklich
ein Zeilenbeleg und bleibt erhalten. Ausschließlich die vier Zielpfade und der
eine veraltete Überschriftenanker wurden ersetzt; alle
übrigen Bytes einschließlich Zeilenenden dieser drei Berichte bleiben erhalten.
Die anderen 52 Archivdateien bleiben vollständig byte-identisch zur gesicherten
Arbeitskopie. Git erkennt sämtliche 55 Verschiebungen weiterhin als Renames.

`RepoLinkHarness` prüft jetzt Markdown-Dateilinks in README, allen Entwickler-
dokumentationen und allen Berichten einschließlich `reports/OLD/`. Historische
Pfadangaben als normaler Prosa- oder Code-Text werden weiterhin nicht als Links
interpretiert. Alle bisherigen Prüfungen bleiben erhalten; zwei zusätzliche
Negativfälle prüfen entfernte Vor-Archiv-Pfade und die fehlende Elternpfadebene.
Es wurde keine Prüfung deaktiviert oder abgeschwächt.

## AGENTS.md und verbleibende lokale Dateien

Die offenen Änderungen an [AGENTS.md](../../AGENTS.md) entfernen eine führende
Leerzeile, verbinden den umgebrochenen Projektbeschreibungssatz und setzen
`reports/<phase>/` in Inline-Code. Die Chat-Design-Regeln, getrennten Klammer- und
Label-Farben, Zukunftsregeln, QA-, Sicherheits- und Dokumentationsregeln waren
bereits veröffentlicht und bleiben vollständig erhalten. Die bestehende
Benutzerdatei wurde dafür nicht bearbeitet; nur ihr bereits vorhandener Diff
wird separat übernommen.

Zwei geprüfte lokale Receipts bleiben absichtlich untracked und unverändert:

- `reports/06b/STEP_06B_CHAT_DESIGN_SYSTEM_COMMIT.txt`
- `reports/06b/STEP_06B_1_PREFIX_STYLING_CORRECTION_COMMIT.txt`

Sie referenzieren korrekt die beiden vorhandenen Commits und dokumentieren den
damaligen lokalen Übergabestand. Aussagen wie `Push: no` sind historisch, keine
Beschreibung des inzwischen veröffentlichten Repository-Zustands. Es sind keine
normalen technischen Projektberichte; sie werden weder gelöscht noch ignoriert
noch ungeprüft eingecheckt. Somit ist der verbleibende untracked Zustand bewusst.

## Qualitätssicherung

Der vollständige Lauf `pwsh -NoProfile -File .\scripts\verify.ps1` ist tatsächlich
ausgeführt und erfolgreich beendet worden:

| Prüfung | Ergebnis |
| --- | --- |
| Maven clean package | PASS |
| Spotless | PASS, bestehende Allowlist mit sechs Java-Dateien |
| Kanonischer Verifier | 229 entdeckt / 229 ausgeführt / 229 bestanden |
| Checks | 33.654 |
| Failed / MissingInvalid | 0 / 0 |
| LocalizationCompatibilityHarness | 98 Checks |
| LocalizationHarness | 845 Checks |
| LocalizationReloadHarness | 11 Checks |
| CatalogUsageHarness | 2.915 Checks |
| ChatDesignSystemHarness | 81 Checks |
| PrefixStylingHarness | 71 Checks |
| MessageStyleHarness | 18 Checks |
| RepoLinkHarness | 117 Checks |
| Ergänzende Dateilinkprüfung | 190 lokale Verweise, keine fehlenden Ziele |
| Ergänzende Überschriftenprüfung | 45 Fragmente, keine fehlenden Überschriften |
| Archivprüfung | alle 55 Renames; nur die beschriebenen Link-/Ankerkorrekturen |

Die acht aufgeführten fokussierten Harnesses wurden zusätzlich separat
ausgeführt. Der frühere fokussierte Linklauf hatte vor Anlage dieses Berichts
114 Checks; mit dessen drei Markdown-Dateilinks sind es 117. Die 15 zusätzlichen
kanonischen Checks gegenüber 33.639 ergeben sich aus der erweiterten Linkprüfung;
alle bereits vorhandenen 229 Harnesses bleiben erhalten. Der finale Receipt
liegt unter `target/verification/harnesses/results.json` und zusätzlich gesichert
im externen Evidenzverzeichnis als `canonical-results.json`.
Nach dem Eintragen der Ergebnisse und Commit-Nachweise bestand RepoLinkHarness
erneut mit 117 Checks. Produktions- und Testcode blieben seit dem vollständigen
Verifikationslauf unverändert.

Ein erster vollständiger Versuch scheiterte am Dateisystemschutz beim Schreiben
von Maven-Resolver-Metadaten unter `.m2/repository`; das war kein bestandener
Testlauf. Der danach mit passendem Cache-Zugriff ausgeführte vollständige Lauf
bestand. Es wurden dafür weder Projektdateien noch Maven-Konfiguration geändert.

Der erste fokussierte Teststart ohne explizites `JAVA_HOME` blieb bereits bei der
Werkzeugermittlung hängen; PATH zeigte auf Oracle-`javapath`. Ausschließlich der
eigene Testprozess wurde abgebrochen. Er zählt nicht als bestandener Test. Die tatsächlichen
Testläufe verwenden prozesslokal `JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1`,
entsprechend dem vorhandenen JDK; keine Run-Konfiguration wurde geändert.

## Lokale Commits und Endzustand

| Commit | Paket |
| --- | --- |
| `703b86f56febf880cb58d699d92e971ba70ae357` | `fix(i18n): preserve localization compatibility` — fünf eindeutig zugeordnete Dateien |
| `859b4da3515f8232772f7d39ebcb762c944a236d` | `docs(reports): archive historical phase reports` — 55 Renames, vier Dokumentationen und RepoLinkHarness |
| `793e02746ee440fe2a5e925b9ecc1e9fc1002fc7` | `docs: preserve project instruction formatting` — ausschließlich vorhandener AGENTS.md-Diff |
| `8323aaa4fad07e2c8ea4daed7c6e305612b3aee3` | `docs(reports): update archived permission audit anchor` — nachträglich geprüfter einzelner Überschriftenanker |

Branch: `codex/post06b-repository-cleanup`. HEAD vor dem abschließenden
Berichtscommit: `8323aaa4fad07e2c8ea4daed7c6e305612b3aee3`.

Die fachlichen Pakete wurden mit expliziten Dateipfaden gestagt und mit komplett
geprüften staged Diffs getrennt committet. Dieser Abschlussbericht und sein
Manifest bilden den fünften lokalen Commit mit dem Titel
`docs(cleanup): document post-06b repository cleanup`. Dessen
vollständiger SHA ist über `git log -1 --format=%H codex/post06b-repository-cleanup`
ermittelbar und zusätzlich im externen `delivery-receipt.json` sowie in der
abschließenden Chat-Antwort festgehalten; eine Selbstreferenz im eigenen Commit
wird dadurch vermieden.

Der abschließende Inhaltsvergleich aller 794 ursprünglichen Pfade bestätigt:
keine unerwartete Existenz- oder Inhaltsänderung. Die einzige neu bearbeitete
Java-Datei ist der strengere RepoLinkHarness; in drei Archivberichten wurden
ausschließlich die beschriebenen Links beziehungsweise der eine Anker geändert.
Alle anderen ursprünglichen Arbeitskopien bleiben byte-identisch zur Sicherung,
einschließlich AGENTS.md, der Lokalisierungskorrektur und der beiden Receipts.
Die beiden neuen Cleanup-Dokumente sind die einzigen zusätzlichen Projektpfade.

Beide Run-Konfigurationen haben weiterhin dieselben Diffs und SHA-256-Werte wie
vor der Bereinigung; sie wurden nicht gestagt oder committet:

| Run-Konfiguration | unveränderter SHA-256 |
| --- | --- |
| `.run/Start VapeeCore Dev Server.run.xml` | `EEDE5A220EEC6A8D154D82F2BD1BF29CFEB59215D6B546D83CDB38A73EAF596F` |
| `.run/Build and Deploy VapeeCore.run.xml` | `FFE3EDC5279855568BA0E906434D82A2901540D42C0976202C4F3DFCE06E8E56` |

Nach dem abschließenden Dokumentationscommit bleiben ausschließlich die beiden
oben benannten untracked Receipts übrig; der Index und sämtliche getrackten
Dateien sind sauber. Bestehende lokale Branches, Remote-Tracking-Referenzen und
Tags bleiben unverändert. Änderungen, die ein laufender Operator-Server selbst
an seinen ignorierten Daten vornehmen könnte, werden damit nicht als
byte-identisch behauptet; dieses Cleanup hat diese Dateien nicht bearbeitet.

Keine Benutzerdatei wurde gelöscht oder verworfen. Keine Stash-, Reset-, Rebase-
oder Clean-Operation, keine Historienumschreibung, kein Push, kein Merge nach
master, kein Deployment und kein Servereingriff. Die beiden alten Feature-
Commits sowie lokale und entfernte master-Referenzen bleiben unverändert.

Offen bleiben die bewusste Entscheidung über die beiden lokalen Receipts, die
separat zu autorisierende Veröffentlichung dieser Cleanup-Commits und bei Bedarf
eine visuelle Minecraft-Client-Prüfung. Alte Operator-Overrides behalten bewusst
ihre bisherigen expliziten Farben, bis der Operator sie selbst anpasst.
