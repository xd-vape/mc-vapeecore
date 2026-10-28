# Prototype Step 01B – Social Chat Styling

Branch: `prototype/friend-presence-chat-actions`. Unveränderter Vorgänger: `b66eaabe1b172311115a7087eed0eb4027d8f9d3`.
Ein zusätzlicher lokaler Commit: `Polish social chat prefixes and rank-colored player names`.
Die SHA dieses Commits wird nach Erstellung im Abschluss genannt; dieser Bericht gehört selbst zu diesem Commit.

## Darstellung und Grenzen

- Englisch `[Friends]`, Deutsch `[Freunde]`: Adventure Gold. `[Clan]` in beiden Sprachen: Dark Purple.
- Grauer Body, rotes lokalisiertes `Error: ` / `Fehler: `, grüne Accept- und rote Deny-Buttons. Presence behält einen begrenzten grünen Online-Status und grauen Offline-Status. Clan-Namen Gold, bestehende Tags Aqua.
- Name: reale Actor-/Target-/Subject-UUID → aktueller geladener LuckPerms-Primary-Rank → `vapeecore.rank.color` → `RankInfo.effectiveColor()`. Named- und RGB-Farben verwenden dieselbe etablierte RankService-Auswertung. Fehlende User/Gruppen/Farbe sowie ungültige Farben: Weiß.
- Kein User-Load, Storage-Wait, Rank-Cache, Scheduler, Rank-Mutation oder Parsing von LP-Präfixen/Namen. Literalnamen bleiben `Component.text`; Farben gelten ausschließlich im Namens-Component.
- Ein gemeinsamer read-only `PlayerNameFormatter` wird im Composition Root über den typisierten aktuellen RankService-Provider verdrahtet. Alte Konstruktoren bleiben mit neutralem Fallback verfügbar. Friend/Clan-Kommandos und Menüs reichen bereits bekannte UUIDs bis zu den Benachrichtigungen weiter. Presence verwendet die Subject-UUID. Die vier bisherigen Presence-Service-Provider behalten ihre Enable-Bindung.
- Kanalpräfix einmal pro Nachricht beziehungsweise Listenüberschrift, nicht pro Folgeline. Die Übersetzung erfolgt weiter beim Empfänger, einschließlich Buttons/Hover. Der globale Operator-Präfix bleibt unverändert und wird durch `MessageService.send()` davor geliefert. Keine Änderung an `send()` oder `sendRaw()`.
- Accept/Deny bleiben `RUN_COMMAND` mit kanonischen Spieler-/stabilen Clan-UUIDs. Cancel/Remove und destruktive Bestätigungen, Help und editierbare Beispiele behalten ihren bestehenden Vertrag. Keine Änderung an Domain-Services, Permissions, Gameplay, Player-Persistenz oder Konfigurationsschema; weiterhin 28 Module und sieben Reload-Teilnehmer.

## Verifikation

Baseline: vollständiges `scripts/verify.ps1`, Maven und Spotless erfolgreich; 207 Harnesses / 33.422 Checks, Failed=0, Missing/Invalid=0. Die genehmigte Run-Ausnahme war die einzige lokale Änderung.

Fokussiert: `SocialChatStylingHarness` ergänzt 49 ausführbare Component-/Command-/Repository-Checks mit echtem RankService vor einer geladenen LP-API-Seam. Named/RGB, fehlende/ungültige Farben, ungeladene User, gleichnamige Spieler verschiedener UUIDs, Rangänderung, Actor/Target/Subject-Zuordnung, lokalisierte Präfixe, tatsächliche Farben einschließlich Vererbung, Body-Grenzen, Literaltext/Injection, RUN_COMMAND, exakte Domain-Resultate und stale Klicks, Ignore in beiden Richtungen, Opt-out und Offline-Empfänger sowie unveränderte Persistenzgrenzen sind abgedeckt. Die bestehenden Sicherheitsprüfungen bleiben erhalten; aktualisiert wurden nur konkrete Text-/Farberwartungen.

Native Acceptance: Paper 1.21.11 Build 132 + LuckPerms 5.5.84 in einem neu angelegten externen Testserver; 71 Checks (62 + 9) in zwei getrennten JVMs, beide Exit 0, keine ERROR/SEVERE im erfolgreichen Lauf und normaler Root-Shutdown. Echte PluginCommand-Executor, Adventure-Components, Localization, RankService und LP-API; synthetische Player-Transport-Proxies ohne Netzwerk-Clients. Nur die separate Testdatenbank erhält synthetische LP-Gruppen/User mit eindeutigen Gewichten und Farbmetadaten. Der installierte LP-Bestand bleibt unangetastet.

Geprüft: Named/RGB, weiße ungeladene LP-Targets, Empfängersprache, genau ein Kanalpräfix, begrenzte Body-Farbe, nächste Nachricht nach Änderung der Gruppenfarbmetadaten, Friend-/Clan-RUN_COMMAND, stale/copied/permission checks, Presence-Ignore/Opt-out und echte Persistenz über Kaltstart. Die ersten beiden Native-Versuche deckten fehlende eindeutige Gewichte in den synthetischen Fixturegruppen beziehungsweise eine falsche deutsche Texterwartung im Testhelfer auf; nach reinen Fixturekorrekturen bestand der komplette Test. Logs/Helper bleiben als Nachweise erhalten. Keine Produktänderung für diese Fixturekorrekturen. Der identische Test bestand auch mit dem finalen Maven-JAR: SHA-256 `60C81926CADBB9FE543136CE446F630FDACE487296927B0B9CC2020976C9CD7A`. Beide finalen JVMs beendeten sich normal; alle separaten Testserver-Verzeichnisse wurden erst nach bestätigtem Prozessende und Sicherung der Nachweise entfernt.

Finale kanonische Verifikation: vollständiges `scripts/verify.ps1` erfolgreich, Maven `clean package` und Spotless erfolgreich. Discovered=Executed=Passed=208, TotalChecks=33.485, Failed=0, Missing/Invalid=0. Alle ursprünglichen 207 Harnesses sind erhalten; ein neuer Harness ergänzt 49 Verhaltenschecks. Die weiteren 14 zusätzlichen Checks entstehen aus den bestehenden dynamischen Katalog-/Lokalisierungs-, Permission-Descriptor-, Repo-Link- und GUI-Audit-Inventaren. FriendCommand 133, ClanCommand 130, FriendPresenceNotifier 8 und PresenceDependencyBoundary 292 Checks bleiben erhalten. Eine alte allgemeine MessageStyle-Farberwartung wurde auf den geforderten weißen Fallback ohne Rank-Provider aktualisiert; MessageStyle besteht weiterhin mit 18 Checks. Der vollständige Abschlusslauf wurde nach dieser Korrektur wiederholt. `git diff --check` ist erfolgreich.

Nachweise außerhalb des Repositories: `C:/Users/vapee/.codex/visualizations/2026/10/25/01a17432-7597-7ff1-bc9d-34201a027523/prototype-step01b-evidence-20261028/` enthält Baseline-/Final-Receipts und Logs, Inventare, unveränderte Run-Diffs/Hashes, die exakte Stage-Pfadliste sowie unter `native-final/` den Native-Helper, beide JVM-Receipts/Logs, JAR-Hash und synthetische persistierte Testdaten. Die finale JAR-Prüfung ist byteidentisch zum Maven-Artefakt.

## Schutz vorhandener Dateien

Die ausdrücklich genehmigte lokale Änderung an `.run/Start VapeeCore Dev Server.run.xml` wird weder bearbeitet noch gestagt oder committet. Ausgangs- und Vor-Commit-SHA-256 sind identisch: `EEDE5A220EEC6A8D154D82F2BD1BF29CFEB59215D6B546D83CDB38A73EAF596F`. Auch der vollständige Git-Diff ist bytegleich zum Ausgangs-Diff. Derselbe Vergleich wird nach dem Commit erneut durchgeführt und extern im Abschlussnachweis festgehalten.

Vorher-Inventar: 712 bestehende getrackte Dateien, 26 Run-/Scriptdateien und 917 Operator-Dateien. Der installierte Operator-Server lief bereits: 908 Dateien waren hashbar, neun aktive Log-/LP-/Weltdateien gesperrt. Kein Stop, Start, Deployment oder Schreibzugriff durch diese Arbeit; ein vollständiger bytegenauer Vergleich der aktiven gesperrten Dateien ist daher nicht möglich. Laufende externe Serveraktivität wird getrennt von unerwarteten getrackten Änderungen behandelt.

Lesender Operator-Vergleich während des laufenden Servers: weiterhin 917 Dateien, keine hinzugefügten/entfernten Pfade. 902 Dateien waren in beiden Inventaren hashbar: 884 identisch, 18 verändert (zwei laufend gespeicherte Playerprofile sowie Welt-/Spielerstände); 15 Dateien waren beim zweiten Inventar gesperrt. Das entspricht externer laufender Serveraktivität und wird nicht als unveränderter Bytebestand ausgegeben. Diese Arbeit hat ausschließlich lesend auf `dev-server` zugegriffen. Alle 26 Run-/Scriptdateien sind unverändert. Weitere unerwartete getrackte Änderungen wurden nicht festgestellt und keine fremden lokalen Änderungen bereinigt.

Der Git-/Run-Vergleich und die Abschluss-SHA werden zusätzlich außerhalb des Repositories abgelegt. Genau 25 Step-01B-Pfade sind explizit festgelegt: 14 Production, sechs Tests, zwei Resources und drei Dokumentationsdateien. Von 712 vorher vorhandenen getrackten Dateien wurden 21 bewusst geändert; 691 sind zum Vorher-Inventar bytegleich, einschließlich der bereits lokal geänderten Run-Datei. Vier neue Dateien gehören zum Step. Die Run-Konfiguration bleibt lokal geändert; der vorherige Commit wird nicht verändert. Keine pauschalen Stage-, Reset-, Stash- oder Clean-Befehle.

## Geänderte Pfade

Production:

- `src/main/java/dev/vapee/core/VapeeCore.java`
- `src/main/java/dev/vapee/core/message/PlayerNameFormatter.java` (neu)
- `src/main/java/dev/vapee/core/message/SocialChannel.java` (neu)
- `src/main/java/dev/vapee/core/friend/FriendMessages.java`
- `src/main/java/dev/vapee/core/friend/FriendModule.java`
- `src/main/java/dev/vapee/core/friend/command/FriendCommand.java`
- `src/main/java/dev/vapee/core/friend/gui/FriendMenu.java`
- `src/main/java/dev/vapee/core/friend/gui/FriendMenuListener.java`
- `src/main/java/dev/vapee/core/clan/ClanMessages.java`
- `src/main/java/dev/vapee/core/clan/ClanModule.java`
- `src/main/java/dev/vapee/core/clan/command/ClanCommand.java`
- `src/main/java/dev/vapee/core/clan/gui/ClanMenuListener.java`
- `src/main/java/dev/vapee/core/presence/FriendPresenceNotifier.java`
- `src/main/java/dev/vapee/core/presence/PresenceModule.java`

Tests:

- `src/test/java/dev/vapee/core/message/SocialChatStylingHarness.java` (neu)
- `src/test/java/dev/vapee/core/message/MessageStyleHarness.java`
- `src/test/java/dev/vapee/core/friend/FriendCommandHarness.java`
- `src/test/java/dev/vapee/core/clan/gui/ClanCommandHarness.java`
- `src/test/java/dev/vapee/core/presence/FriendPresenceNotifierHarness.java`
- `src/test/java/dev/vapee/core/presence/PresenceDependencyBoundaryHarness.java`

Resources: `src/main/resources/lang/en.yml`, `src/main/resources/lang/de.yml` (drei semantische Keys je Katalog; zwei vorhandene Friend-Menüfehler ohne vollflächiges Rot).

Dokumentation: `docs/SERVICE_DEPENDENCIES.md`, `docs/DEVELOPER_GUIDE.md` (korrigiert zusätzlich den veralteten Friend-Suggest-Vertrag), dieser Bericht.
Die vorhandene Run-Änderung gehört ausdrücklich nicht zum neuen Commit.

## Noch notwendiger Minecraft-Client-Test

Der Benutzer hat die bisherigen Step-01-Interaktionen im Client geprüft. Das neue Styling wurde bisher nicht visuell in einem Minecraft-Client bestätigt. Native API-/Adventure-Assertions ersetzen diese Prüfung nicht.

1. Zwei Testspieler mit verschiedenen LuckPerms-Rangfarben verbinden.
2. Eine Freundschaftsanfrage senden.
3. Den sichtbaren Friends-/Freunde-Präfix prüfen.
4. Die Rangfarbe des Absendernamens prüfen.
5. Accept ohne Enter anklicken.
6. Ergebnis und Statusmeldungen auf konsistente Darstellung prüfen.
7. Friend-Offline-/Online-Meldungen prüfen.
8. Clan-Einladung und direkte Accept-/Deny-Aktionen prüfen.
9. Rangfarbe ändern und die nächste Nachricht prüfen.
10. Empfängersprache wechseln und übersetzte Präfixe prüfen.
11. Lesbarkeit mit einem normalen Client ohne Resource-Pack oder Mods prüfen.

Kein Push, Merge oder Deployment. Nächster Schritt: finale Ingame-Darstellung vor dem Merge von Prototype Step 01 nach master prüfen.
