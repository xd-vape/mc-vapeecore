# Step 06C – Unified Command Usage Design

04.11.2026, Branch `codex/step06c-command-usage-design`, Basis
`599aa13482b1a019500ece8aa32796fa8f4ae2c5` auf dem ausgecheckten, sauberen master.
Die beiden lokalen Step-06B-Receipts waren bereits über `.git/info/exclude`
ausgeblendet und bleiben unverändert. Der vorhandene `chat-polish-wt` mit seinem
untracked historischen Bericht wurde separat gesichert und nicht bearbeitet.
Es wurde kein weiterer Entwicklungs-Worktree angelegt.

## Ursache und vollständige Bestandsaufnahme

Der Descriptor registriert weiterhin dieselben 43 Root-Commands und Aliases.
Vorher existierten direkte Yellow-/Aqua-Components, reine lokalisierte
Gesamttexte sowie Usage über die Help-orientierte `MessageStyle.syntax`-Methode.
Friends hatte beim fehlenden Add-Ziel zwei Meldungen, Clan teilweise drei.
Einzelne klickbare Clan-Hinweise schlugen Platzhalter als Eingabe vor.

| Bestehende Commands | Umsetzung / bewahrte Grenze |
| --- | --- |
| `/vapeecore` mit bestehendem `/core`-Alias | einfache Usage zentral; öffentliche Übersicht, Help und Reload unverändert |
| `/friend`, `/clan` | gemeinsame Usage mit ihren SocialChannel-Prefixen; kontextabhängige Auswahl und UUID-Aktionen bleiben erhalten |
| `/profile`, `/coins` | zentrale Usage; bestehende Ziel-/Amount-Fehler und Known-Identity-/Staff-Policies unverändert |
| `/spawn`, `/setspawn` | zentrale Syntax; bestehende rote Fehlereinleitung und Ausführungs-/Senderregeln erhalten |
| `/warp` | vorhandene Warp-Metadaten liefern getrennte command()/arguments(); Help syntax() und alle zehn Subcommands unverändert |
| `/warn`, `/kick`, `/ban`, `/unban`, `/mute`, `/unmute`, `/history` | gemeinsame Moderation-Usage; fachliche Fehler, Actor-/Target-Policy und spätere asynchrone Autorisierung unverändert |
| `/build`, `/fly`, `/speed`, `/gamemode`, `/tp`, `/tphere`, `/heal`, `/feed`, `/ping`, `/clear`, `/invsee`, `/enderchest` | zentrale Usage; sämtliche Berechtigungen, Teleport-Grammatik, Zielauflösung und Auswirkungen unverändert |
| `/msg`, `/reply` | zentrale Syntax; bestehende PM-Delivery und Literalwerte unverändert |
| `/ignore`, `/unignore`, `/ignorelist` | zentrale Syntax; bestehende Ignore-/Privacy-Regeln unverändert |
| `/rank`, `/ranks` | zentrale Syntax; Rangabfragen und LuckPerms-Farben bleiben fachlich eigenständig |
| `/settings` | konkrete Visibility-Unterbefehle verwenden explizit getrennte Tokens; UUID-Mehrdeutigkeitsfehler bleiben eigene Hinweise |
| `/quests`, `/core quest force` | zentrale gebündelte Usage; vollständige vorhandene Sprach-Overrides bleiben erhalten |
| `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tpcancel` | konkrete zentrale Usage des aufgerufenen Commands mit Raw-Delivery; UUID-/Request-ID-, Session- und 60-Sekunden-Verträge unverändert |
| `/staff` | zentrale Usage mit bestehendem Staff-Wrapper; Panel, öffentliches list und HIDDEN-Vertrag unverändert |
| `/navigator` | Editor-Syntaxfälle verwenden zentrale Components; Help-Seiten, Draft, Regionen, GUI und Permissions unverändert |

Vollständige Help-Seiten, Root-Aktionen ohne Fehler, echte fachliche Fehler,
kontextabhängige Freundes-/Clan-Auswahl und sicherheitsrelevante Bestätigungen
sind ausdrücklich keine einfachen Usage-Zeilen. Insbesondere bleiben
`/friend` ohne Argumente, die echten Friend-/Clan-RUN_COMMAND-Buttons und
`/clan disband confirm` erhalten. Der komplette Descriptor bleibt byte-identisch.

## Kleine zentrale API und tatsächliche Rollen

`MessageStyle.usage(command, arguments)` komponiert die lokalisierte
`common.usage`-Bezeichnung und `usageSyntax(command, arguments)`. Command und
Argumente sind beim Aufruf explizit getrennt; kein Laufzeit-Parser errät den
Subcommand-Anteil. Die bestehende `syntax(String)`-Methode und der gesamte
CommandHelpRenderer bleiben unverändert.

| Bestandteil | bestehende Rolle |
| --- | --- |
| Prefix-Klammern | BRACKET, Dark Gray |
| Friends-/Clan-/Staff-Label | bestehendes GOLD / DARK_PURPLE / DARK_GRAY |
| Usage-Bezeichnung | TEXT, Gray |
| ausführbarer Root plus Subcommand | aktuell angewendetes `MessageStyle.accent()` |
| Pflichtargumente / sonstige Syntax | TEXT, Gray |
| eckig geklammerte optionale Gruppen | MUTED, Dark Gray |

`messages.accent-color` bleibt die einzige Akzenteinstellung. Sie wird beim
Erzeugen des Components gelesen; es gibt keine statische Standardkopie, zweite
Palette, Dependency, neues Modul oder neuen Cache. Config-Prepare veröffentlicht
nichts, Apply färbt neue Ausgaben und Rollback stellt den vorherigen Akzent her.
Bereits erzeugte immutable Components behalten ihren ursprünglichen Stil.
Echte Fehlermeldungen, Rangfarben, Clan-Tags und Aktionsfarben bleiben erhalten.

## Kompakte Social-Usage und Interaktionen

`/friend add` ohne Ziel erzeugt genau eine Zeile, etwa
`[Friends] Usage: /friend add <player|uuid>`. Andere falsche Friend-Argumentzahlen
nennen ebenfalls den konkreten Unterbefehl. Fehlende accept/deny/cancel/remove-
Ziele behalten ihre bisherigen kontextabhängigen Auswahl-/Leerzustände.

Clan tag/invite/kick/transfer liefern beim einfachen fehlenden Pflichtargument
eine konkrete Usage. create/rename behalten ihre nützlichen Beispiele für
mehrteilige Namen, ohne zusätzliche Missing-Meldung. Eingehende Einladungen,
ausgehende Cancel-Auswahl und die Disband-Bestätigung bleiben eigenständige
Fälle. Die vorhandenen Beispiele sind weiterhin vollständig editierbar.

Usage besitzt ausschließlich SUGGEST_COMMAND und den vorhandenen lokalisierten
`common.command-insert`-Hover. Mit Argumenten lautet der Vorschlag beispielsweise
`/friend add `, ohne Argumente `/clan help`. Platzhalter werden nicht in die
ausführbare Eingabe übernommen. Command-Tokens mit Platzhaltern oder
Steuerzeichen werden zurückgewiesen. Argumente bleiben Literal-Components;
MiniMessage-artiger Text kann keinen RUN_COMMAND erzeugen.

## EN/DE und bestehende Operator-Overrides

`common.usage` bleibt unverändert: EN `Usage: `, DE `Verwendung: `.
Command-Tokens und Platzhalter bleiben sprachunabhängig. Es werden keine neuen
Übersetzungsschlüssel oder Placeholder-Verträge eingeführt; beide gebündelten
Kataloge und vorhandene Operator-Dateien bleiben unverändert.

Für die acht früheren vollständigen Usage-Schlüssel (`profile.usage`,
`core.quest.usage`, `daily-quests.command-usage`, `utility.build.usage`,
`staff.usage` und die drei `tpa.usage-*`) erhält
`MessageService.usage(recipient, legacyKey, command, arguments)` einen wirklich
abweichenden Override der Empfängersprache als vollständigen Text. Ohne solchen
Override verwendet der gebündelte Default die zentrale Komposition. Da das
LocalizationModule beim Start selbst vollständige Kataloge kopiert, werden auch
exakt dem aktuell deklarierten gebündelten Wert entsprechende Live-Einträge als
Defaults zentral dargestellt. Alle acht Schlüssel werden in beiden vollständig
kopierten Katalogen auf identische Components einschließlich Events geprüft.
Es gibt keinen heuristischen Vergleich mit früheren historischen Standardtexten.
Explizite Operator-Gestaltung kann daher weiterhin vom neuen Standard abweichen.
Auch ein `common.usage`-Override bleibt maßgeblich. Der bestehende
Snapshot-/Reload-/Rollback-Vertrag und die strikte Katalogvalidierung bleiben
unverändert; die Presence-Kompatibilität aus dem Cleanup bleibt erhalten.

## Verhaltenstests und Qualitätssicherung

`CommandUsageHarness` prüft echte registrierbare Friend-/Clan-Executor-Pfade,
tatsächliche Adventure-Farbspannen einschließlich Vererbung, beide Sprachen,
zwei Akzentfarben, genau eine Friend-Add-Meldung, Prefix-Rollen, Pflicht-/Option-
Argumente, sichere Click-/Hover-Events, unveränderte Help-Darstellung,
Permissions, Menü-/Auswahlpfade, echte Config-Reload-/Rollback-Pfade und
Operator-Overrides ohne Dateischreibzugriffe. Der neue Harness wurde mit dem
bestehenden Google Java Format 1.22.0/AOSP formatiert.

Die bestehenden Harnesses bleiben erhalten. Ihre eingefrorenen alten Usage-
Wortlaute wurden auf die neue Bezeichnung beziehungsweise den Feature-Prefix
angepasst. Die früheren Missing-Meldungsprüfungen sichern jetzt die exakte
Paketanzahl und konkrete Syntax; TPA prüft die einzelne Raw-Zeile und den sicheren
Vorschlag. Autorisierungs-, Persistenz-, Request-Replay- und Completion-Prüfungen
wurden nicht entfernt oder abgeschwächt.

Der abschließende vollständige Lauf
`pwsh -NoProfile -File .\scripts\verify.ps1` wurde tatsächlich erfolgreich beendet:

| Prüfung | Ergebnis |
| --- | --- |
| Maven clean package | PASS |
| Spotless | PASS, unveränderte Pilot-Allowlist mit sechs Java-Dateien |
| Kanonische Harnesses | 230 entdeckt / 230 ausgeführt / 230 bestanden |
| Checks | 34.069 |
| Failed / MissingInvalid | 0 / 0 |
| Neuer CommandUsageHarness | 454 Checks |
| FriendCommandHarness / ClanCommandHarness | 133 / 130 Checks |
| TeleportRequestsHarness | 161 Checks |
| CommandHelpHarness | 56 Checks |
| RepoLinkHarness | 120 Checks |
| Isolierter nativer Paper-Smoke | 279 Checks, Exitcode 0 |

Die vorhandenen 229 Harnesses bleiben vollständig erhalten; der Usage-Harness
ist die einzige Ergänzung. Gegenüber dem Ausgangsstand mit 33.654 Checks setzt
sich die Differenz exakt aus +454 neuen Usage-Checks, -43 automatisch gezählten
wiederholten Katalogreferenzen nach Entfernung der alten direkten Usage-Aufrufe,
+3 Dokumentationslink-Checks und +1 automatisch erfasstem GUI-Authoring-Audit-
Check zusammen. CatalogUsageHarness bleibt unverändert und besteht mit 2.872
Checks. Es wurde kein Harness entfernt oder eine Testbedingung deaktiviert.

Vor der Gesamtprüfung liefen 26 betroffene Harnesses separat. Die anschließend
korrigierten alten Text-Oracles sind im abschließenden vollständigen Lauf
ebenfalls erfolgreich. Ein Zwischenlauf fand noch die frühere deutsche
Navigator-Bezeichnung `Syntax:` und die generische Quest-Fehlereinleitung;
beide prüfen jetzt die exakte neue Usage bei beibehaltener Paketanzahl und
unveränderten Autorisierungs-/Ausführungsgrenzen. Die kopierten Standardkataloge
sind als zusätzliche Regression im neuen Harness in beiden Sprachen enthalten.

Der maßgebliche Receipt ist `target/verification/harnesses/results.json`, extern
gesichert als `canonical-final-results.json`. Vollständiges Verifier-Log,
fokussierte Ausgaben und diagnostische Zwischenläufe liegen im externen
Step-06C-Evidenzverzeichnis. Nach dem abschließenden Berichtseintrag wird der
Link-Harness gezielt erneut geprüft; Produktions- und Testcode bleiben dabei
gegenüber dem bestandenen Gesamtstand unverändert.

Die ersten unprivilegierten Persistenz-Fixtures konnten temporäre Dateien wegen
Sandbox-AccessDenied nicht atomar ersetzen. Die Tests wurden mit geeigneten
Dateirechten erneut ausgeführt; das war keine Produktionskorrektur. Ein erster
Compile-Lauf fand noch einen nicht umgestellten Clan-Invite-Aufruf; dieser wurde
innerhalb des Usage-Scopes korrigiert. Der alte TPA-Wortlaut und anschließend
ein im Test versehentlich über die komplette Nachrichtenhistorie verglichener
ClickEvent wurden gezielt auf den neuen einzelnen Usage-Component ausgerichtet.
Fehlgeschlagene Zwischenläufe werden nicht als bestandene Abnahme gezählt.

## Isolierter nativer Paper-Smoke

Frische isolierte Paper-1.21.11-Instanz (Build 132) mit Java 21.0.12.1 und
LuckPerms 5.5.84 auf Loopback-Port 25651: **279 Checks, keine Fehler**.
Alle 27 tatsächlichen Module waren vor und nach dem echten `/core reload`
aktiv. Die neu gebaute JAR wurde per SHA-256 mit der Testkopie verglichen.
Der ausschließlich für diese Instanz gestartete Prozess PID 17832 wurde vom
guarded Testhelfer regulär beendet; Exitcode 0. Keine ERROR-/SEVERE-/Exception-
Zeilen in ihrem Serverlog.

Der Helfer verwendet tatsächliche registrierte `PluginCommand.execute`-Pfade
mit synthetischen Player-/Console-Sendern. EN/DE, konkrete Friends-/Clan-Usage,
Prefix-/Label-/Argumentfarben, Beispiele, Vorschläge und Hover sowie Profile,
Spawn, Ranks, Build, Quests, Staff, Warp, Coins, Core-Quest und Moderation werden
auf Server-API-Ebene geprüft. Frisch kopierte Standardkataloge verwenden die
gemeinsame Usage. Ein reales Config-Reload ändert den Akzent; ein aktivierter
Test-Operator-Prefix beweist daneben die weiterhin rohe TPA-Console-Delivery.
Der Paper-Permission-Gate blockiert einen kontrolliert unberechtigten Sender.
Die bestehende Bindung der fünf TPA-Roots und weiterer Roots bleibt erhalten.

Dies ist kein Test echter Minecraft-Accounts oder Netzwerk-Logins: Die
Sender-Permissions sind kontrollierte Probe-Werte. LuckPerms-Start und Modul-
Verdrahtung sind nativ; vollständige Permission-/Descriptor-/Consent-Verträge
werden zusätzlich durch die kanonischen Verhaltenstests geprüft. Insbesondere
die TPA-Usage mit echter Online-Session wird im bestehenden TPA-Harness geprüft,
nicht durch einen behaupteten synthetischen Player-Login im Paper-Test.

Runtime-Binaries und Lobby-Testkonfiguration stammen aus einer früheren
isolierten, gestoppten Testumgebung. Es wurden keine Operator-Welten, Profile,
Sprachdateien oder Serverkonfigurationen als veränderbare Fixture verwendet.
Helper-Quellen, Launcher, JAR-Prüfsummen, Prozess-Receipt und vollständige Logs
liegen außerhalb des Repositories unter
`C:\Users\vapee\.codex\visualizations\2026\10\25\01a17432-7597-7ff1-bc9d-34201a027523\step06c-command-usage-design-20261104`
(Runtime und Ergebnisse im Unterordner `native`).
Die visuellen Minecraft-Client-Prüfungen bleiben ausdrücklich offen.

## Geänderte Dateien und Git-Nachweis

Genau 51 beabsichtigte Commit-Pfade:

```text
AGENTS.md
docs/COMMAND_DEVELOPMENT.md
docs/development/chat-design-system.md
reports/06c/STEP_06C_COMMAND_USAGE_DESIGN.md
src/main/java/dev/vapee/core/clan/command/ClanCommand.java
src/main/java/dev/vapee/core/command/CoreCommand.java
src/main/java/dev/vapee/core/economy/command/CoinsCommand.java
src/main/java/dev/vapee/core/friend/command/FriendCommand.java
src/main/java/dev/vapee/core/identity/command/ProfileCommand.java
src/main/java/dev/vapee/core/lobby/command/SetSpawnCommand.java
src/main/java/dev/vapee/core/lobby/command/SpawnCommand.java
src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditor.java
src/main/java/dev/vapee/core/lobby/warp/command/WarpCommand.java
src/main/java/dev/vapee/core/lobby/warp/command/WarpSubcommand.java
src/main/java/dev/vapee/core/message/MessageService.java
src/main/java/dev/vapee/core/message/MessageStyle.java
src/main/java/dev/vapee/core/moderation/command/AbstractModerationCommand.java
src/main/java/dev/vapee/core/privatemessage/command/MessageCommand.java
src/main/java/dev/vapee/core/privatemessage/command/ReplyCommand.java
src/main/java/dev/vapee/core/quest/daily/command/CoreQuestCommand.java
src/main/java/dev/vapee/core/quest/daily/command/DailyQuestCommand.java
src/main/java/dev/vapee/core/rank/command/RankCommand.java
src/main/java/dev/vapee/core/rank/command/RanksCommand.java
src/main/java/dev/vapee/core/settings/command/SettingsCommand.java
src/main/java/dev/vapee/core/social/command/IgnoreCommand.java
src/main/java/dev/vapee/core/social/command/IgnoreListCommand.java
src/main/java/dev/vapee/core/social/command/UnignoreCommand.java
src/main/java/dev/vapee/core/staff/StaffPanel.java
src/main/java/dev/vapee/core/utility/command/BuildCommand.java
src/main/java/dev/vapee/core/utility/command/ClearCommand.java
src/main/java/dev/vapee/core/utility/command/EnderChestCommand.java
src/main/java/dev/vapee/core/utility/command/FeedCommand.java
src/main/java/dev/vapee/core/utility/command/FlyCommand.java
src/main/java/dev/vapee/core/utility/command/GameModeCommand.java
src/main/java/dev/vapee/core/utility/command/HealCommand.java
src/main/java/dev/vapee/core/utility/command/InvseeCommand.java
src/main/java/dev/vapee/core/utility/command/PingCommand.java
src/main/java/dev/vapee/core/utility/command/SpeedCommand.java
src/main/java/dev/vapee/core/utility/command/TeleportCommand.java
src/main/java/dev/vapee/core/utility/command/TeleportHereCommand.java
src/main/java/dev/vapee/core/utility/tpa/TeleportRequests.java
src/test/java/dev/vapee/core/clan/gui/ClanCommandHarness.java
src/test/java/dev/vapee/core/clan/gui/CommandUsageHarness.java
src/test/java/dev/vapee/core/command/CoreCommandHarness.java
src/test/java/dev/vapee/core/friend/FriendCommandHarness.java
src/test/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditorUxHarness.java
src/test/java/dev/vapee/core/quest/daily/command/QuestCommandHarness.java
src/test/java/dev/vapee/core/rank/RankCommandHarness.java
src/test/java/dev/vapee/core/rank/RanksCommandHarness.java
src/test/java/dev/vapee/core/utility/command/UtilityCommandHarness.java
src/test/java/dev/vapee/core/utility/tpa/TeleportRequestsHarness.java
```

Die dauerhaften Regeln stehen in [Chat-Designsystem](../../docs/development/chat-design-system.md#einfache-command-usage)
und [Command development](../../docs/COMMAND_DEVELOPMENT.md); AGENTS.md enthält
zusätzlich die kurze verbindliche Regel für zukünftige Commands. Alle bisherigen
Projektanweisungen bleiben erhalten.

Die Umsetzung einschließlich dieses Berichts bildet genau einen fokussierten
lokalen Commit `Unify command usage presentation` auf
`codex/step06c-command-usage-design`. Der vollständige SHA wird im
externen `delivery-receipt.json` und in der abschließenden Chat-Antwort festgehalten,
um eine Selbstreferenz im eigenen Bericht zu vermeiden. Keine Veröffentlichung,
kein Merge, kein PR, kein Deployment und keine Änderung am Operator-Server.

Der Schutzvergleich umfasst 741 ursprüngliche Dateipfade des Hauptprojekts
einschließlich der beiden lokal ausgeblendeten Receipts sowie 729 Pfade des
bestehenden fremden Worktrees. Außerhalb der explizit aufgeführten Step-06C-
Dateien ergab sich keine unerwartete Existenz- oder Inhaltsänderung. Der fremde
Worktree und sein Git-Status bleiben unverändert; alle bestehenden Branches,
Recovery-Referenzen, master, origin/master und Tags bleiben erhalten.
Der neue AGENTS-Abschnitt lässt sämtliche früheren Regeln erhalten.

Beide Run-Konfigurationen bleiben unangetastet und werden nicht gestagt:

| Datei | unveränderter SHA-256 |
| --- | --- |
| `.run/Start VapeeCore Dev Server.run.xml` | `EEDE5A220EEC6A8D154D82F2BD1BF29CFEB59215D6B546D83CDB38A73EAF596F` |
| `.run/Build and Deploy VapeeCore.run.xml` | `FFE3EDC5279855568BA0E906434D82A2901540D42C0976202C4F3DFCE06E8E56` |

Die beiden lokalen Commit-Receipts, beide gebündelten Sprachkataloge,
`plugin.yml`, Maven-Konfiguration, ConfigService, Help-Renderer und die
Friend-/Clan-/Player-Persistenz bleiben unverändert. Alle migrierten Permission-
Ausdrücke und vollständigen Tab-Completion-Methoden wurden außerdem direkt gegen
die gesicherten Quellen verglichen. Operator-Dateien wurden nicht bearbeitet;
Dateien, die ein laufender Operator-Server selbst verändern könnte, werden
dadurch nicht pauschal als byte-identisch behauptet.

Offen bleibt die visuelle Minecraft-Client-Abnahme von Kontrast, Zeilenumbruch,
Hover und den editierbaren Chatvorschlägen. Ein API-/Server-Smoke ersetzt keine
visuelle Prüfung mit einem verbundenen Minecraft-Client.
