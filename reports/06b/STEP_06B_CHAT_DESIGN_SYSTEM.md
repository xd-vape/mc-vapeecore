# Step 06B – Global Chat Design System & Readability Polish

## Ausgangszustand und Arbeitsort

03.11.2026, Hauptprojekt `E:\NEXTCLOUD\Programmierung\JAVA\MINECRAFT\VapeeCore`. Ausgangscommit: `f4970a9d8470e25a1a31bcbb756c048815384f2e`; darin sind Chat Polish, konfigurierbarer Akzent, die genehmigte Run-Konfiguration und die manuell angelegte AGENTS.md bereits eingecheckt.

Die Implementierung findet direkt im Hauptprojekt auf `codex/step06b-chat-design-system` statt. Es wurde kein weiterer Entwicklungs-Worktree angelegt. Der frühere Chat-Worktree wurde nicht verändert. Vorhandene Berichte wurden während der Bestandsaufnahme vom Benutzer nach `reports/OLD/` verschoben; der Benutzer bestätigte diese beabsichtigte Archivierung. Diese Umordnung bleibt vollständig außerhalb des Step-06B-Commits.

Die vollständige Basisprüfung vor Änderungen ergab Maven/Spotless PASS, 226 entdeckte Harnesses, 225 PASS, 1 FAIL, Missing/Invalid=0, 33.271 gezählte Checks. Der einzige Fehler ist RepoLinkHarness: README verweist noch auf `reports/architecture-modernization/PHASE_05B_MODULE_LIFECYCLE_AUDIT.md`, das nun unter OLD liegt. Der Link-Test wird nicht abgeschwächt und die Archivierung nicht zurückgesetzt.

## Architekturentscheidungen und zentrale API

Die vorhandenen Klassen werden erweitert, ohne neues Modul, Theme-Service, zusätzliche Abhängigkeit oder Scheduler:

- `MessageStyle`: zentrale semantische Rollen PREFIX, TEXT, MUTED, ACCENT, SUCCESS, ERROR, WARNING, ONLINE, OFFLINE. BODY/HIGHLIGHT bleiben kompatible bestehende Namen; `accent()` liest denselben angewendeten Zustand. `text`, `success`, `error`, `warning`, `status`, `prefix`, `feature`, `message` und `block` sind die kleine gemeinsame Kompositions-API.
- `SocialChannel`: Friends, Clan und Staff delegieren an dieselbe dunkelgraue Prefix-Komposition. Bestehende Kanalnamen und Delivery-Verträge bleiben erhalten.
- `MessageService.sendRawBlock`: lokalisiert zuerst, formatiert danach genau einen Component mit Leerzeile davor/danach und zwei Leerzeichen Einrückung je Inhaltszeile. Interne Übersetzungs-Newlines werden berücksichtigt. Hover-Texte werden nicht eingerückt; keine String-Serialisierung oder erneute MiniMessage-Auswertung dynamischer Daten.
- `LocalizationService`, `Translations`, `TranslationCatalog`: erkennen zusätzlich die kollisionsarm benannten semantischen `vc_*`-Tags aus MessageStyle. `<accent>` bleibt erhalten und liest die bestehende Config-Palette beim Rendern. Bestehende dynamische `<prefix>`-Platzhalter werden nicht mit einem neuen globalen Farbtag überschrieben.

Die Palette verwendet den bestehenden Minecraft-Stil: Prefix/Muted DARK_GRAY, Text/Offline GRAY, Success/Online GREEN, Error RED, Warning YELLOW und den konfigurierbaren Standardakzent `#E5BF75`. Die ConfigService-Implementierung, sechsstelligen Hex-/Minecraft-Namen, Fallback, Prepare/Apply/Rollback und der siebenstufige Reload-Vertrag bleiben unverändert. Kein zusätzlicher persistenter Zustand.

Die dauerhafte [Entwicklerdokumentation](../../docs/development/chat-design-system.md) enthält API, Farbwerte, Delivery, MiniMessage-Tags, EN/DE und ein konkretes zukünftiges Party-Beispiel. AGENTS.md wurde nur gezielt ergänzt; bestehende manuelle Projekt- und Sicherheitsregeln bleiben erhalten.

## Umgestellte Features und Beispiele

### Prefixe

Vorher: Friends GOLD, Clan DARK_PURPLE, Staff DARK_GRAY. Jetzt: alle drei Prefixe beziehen ihre dunkelgraue Farbe aus MessageStyle.PREFIX. Rang-/Spielernamen, Clan-Namen/Tags und Aktionen behalten ihre fachlichen Farben. Friends/Clan behalten den ausdrücklich konfigurierten Operator-Wrapper, Staff bleibt raw mit einem eigenen Prefix.

### Friends Presence

Der Wortlaut bleibt erhalten. Vorher war in „Friend Alex is now online.“ der gesamte Nachsatz grün. Jetzt bleibt der Satz neutral und ausschließlich „online“ ist grün. Offline ist dezent grau. Spielername und Rang behalten die bestehenden live aufgelösten Farben. Einstellungen, beidseitige Ignore-Prüfungen, Online-Prüfung und Fehlerisolation sind unverändert.

EN: `[Friends] Friend Alex is now online.`

DE: `[Freunde] Dein Freund Alex ist jetzt online.`

Die beiden Statuswörter werden lokalisiert als Component eingesetzt. Die bestehende eigene Join-Zusammenfassung bleibt unverändert: präfixfrei, eine kompakte Zeile, bestehender Opt-out, grüne Online-Anzahl und weiße Gesamtzahl.

### Warnung und Mute

Vorher zwei direkt aneinander anschließende Zeilen. Jetzt ein zusammenhängender Block, hier ohne Darstellung der Farben:

```text

  [Team] Alex verwarnt.
  Grund: Wiederholte Beleidigungen

```

Betroffene Spieler erhalten dieselben Abstände und Einrückung, aber keinen Staff-Prefix. Mute-Bestätigungen zeigen weiterhin Dauer beziehungsweise Dauerhaftigkeit; Betroffene sehen weiterhin Ablaufzeit in UTC beziehungsweise dauerhaft. Gründe und relevante Werte verwenden den konfigurierten Akzent, neutrale Erklärung und kleine Statuslabels bleiben getrennt. Der gemeinsame Reason-Renderer verwendet den Akzent auch in bestehenden Historien-/Disconnect-Kompositionen; deren Fakten, Hover-IDs und sonstiges Layout bleiben erhalten.

Die gebündelten Warn-/Mute-Übersetzungen verwenden zentrale semantische Tags; ihre bisherigen fachlichen Platzhalter bleiben erhalten. Bereits vorhandene Operator-Sprach-Overrides werden nicht umgeschrieben. Die wiederholte Chat-Sperr-Erinnerung bleibt eine kompakte Zeile; Drosselung, Async-/Main-Thread-Grenzen, Persistenz und Moderations-Authority bleiben unverändert.

## Staff PUBLIC/HIDDEN

Die bestehende Implementierung ist bereits konsistent. HIDDEN wird nur von der öffentlichen Roster-Filterung und der Panel-Statusdarstellung verwendet. PUBLIC erlaubt die öffentliche Listung unter den vorhandenen Staff-, Session-, Online- und canSee-Prüfungen. Berechtigte private Panels können Hidden-Staff weiterhin sehen. Kein automatischer Disguise, keine Änderung von Chat-Rängen/-Prefixen, Nametags, Scoreboard oder Tablist; Status bleibt sessiongebunden.

Es wurde nur die API-Dokumentation präzisiert. Der bestehende echte Panel-Test erfasst nun zusätzlich Appearance-Schreibaufrufe und prüft unveränderte Rangdaten nach HIDDEN und PUBLIC. Bestehende Permissions-, Revoke-, Foreign-/Stale-Inventory-, Reconnect- und Shutdown-Prüfungen bleiben erhalten.

## Tests und Qualitätssicherung

Neue kompilierte `ChatDesignSystemHarness`: 81 Checks für die tatsächliche Ausgabe aller SocialChannels in EN/DE, ein neues Feature ohne eigene Prefixlogik, neutrale Sätze und Rangfarben, semantische Tags, Akzent-Reload/Rollback, Warn-/Mute-Commands, Blöcke, Literalgründe und unveränderte Click-/Hover-Events einschließlich mehrzeiliger Hover-Texte.

Die fokussierten bestehenden ChatPresentation-, SocialChatStyling-, MessageAccent-, FriendPresenceNotifier-, StaffPanel-, ModerationMuteFeedback-, ModerationCommand- und CatalogUsage-Harnesses wurden ausgeführt. Erwartungsänderungen sind auf die beauftragten Prefixfarben und Blockstruktur begrenzt; kein Harness wurde deaktiviert oder entfernt.

Der abschließende vollständige kanonische Verifier wird auf einer isolierten Quellenkopie des vorgesehenen Commit-Inhalts ausgeführt. Sie exportiert den eingecheckten Ausgangsbaum einschließlich historischer Berichtspfade und überlagert ausschließlich die vorgesehenen Step-06B-Dateien. Dadurch wird die unabhängig noch uncommittete Benutzerarchivierung nicht versehentlich Bestandteil des Commits oder durch die Tests zurückgesetzt. RepoLinkHarness läuft dort regulär mit; es gibt keine Test-Ausnahme. Alle Implementierungsänderungen und der Feature-Branch bleiben im Hauptprojekt.

Abschließender vollständiger Lauf: **227 entdeckt / 227 ausgeführt / 227 PASS, 33.468 Checks, Failed=0, Missing/Invalid=0, Maven clean package PASS, Spotless PASS**. Alle 226 bisherigen Harness-Identitäten sind erhalten. RepoLinkHarness läuft unverändert mit und besteht 102 Checks. Der zusätzliche ChatDesignSystemHarness besteht 81 Checks. Die Quell-/Test-/Ressourcendateien dieser Prüfung entsprechen bytegenau den vorgesehenen Dateien im Hauptprojekt.

Im tatsächlichen Hauptprojekt bestehen ebenfalls **Maven clean package, Spotless und alle neun gezielt betroffenen Harnesses**: ChatDesignSystem 81, ChatPresentation 68, SocialChatStyling 49, MessageAccent 79, FriendPresenceNotifier 8, StaffPanel 126, ModerationMuteFeedback 80, ModerationCommand 4.426 und CatalogUsage 2.915 Checks. Das sind überlappende fokussierte Prüfungen, keine zusätzlich zum kanonischen Zähler aufzuaddierenden Checks.

Der lokale Arbeitsbaum mit der separat noch uncommitteten Benutzerarchivierung hat weiterhin den bereits in der Basisprüfung gefundenen README-Linkfehler. Dieser Unterschied wird nicht als grüner vollständiger Hauptprojekt-Verifier ausgegeben. Der erfolgreiche vollständige Lauf prüft den vorgesehenen Commit-Inhalt mit den weiterhin eingecheckten historischen Berichtspfaden. Die Archivierung selbst und ihre Linkanpassungen benötigen einen eigenen konsistenten Dokumentationsschritt.

Ein pauschaler repoweiter Regex-Verbotscheck für NamedTextColor wurde verworfen: GUI-, Rang- und fachliche Farben sind legitime Ausnahmen. Die schlanke dauerhafte Regel sind AGENTS, die zentrale API und die ausführbaren Verhaltenstests über alle SocialChannel-Werte. Neue Features sollen diese Ausgabeprüfungen erweitern. Kein fragiler Source-String-Test und keine zweite Formatierungsarchitektur.

## Visuelle Prüfung und Grenzen

Keine echte Minecraft-Client-Sichtprüfung und kein neuer nativer Paper-Lauf werden in dieser Phase behauptet. Offen bleiben Kontrast auf realen Chat-Hintergründen, Zeilenumbruch, Schrift-/Resource-Pack-Effekte, die Abstände der Warn-/Mute-Blöcke und das gerenderte Hover-/Klick-Erlebnis. Frühere native Testresultate werden nicht als neuer Step-06B-Test ausgegeben.

Operator-Templates und Sprach-Overrides behalten Vorrang und können absichtlich vom gebündelten Stil abweichen. Bereits gesendete immutable Components ändern ihre Farben nicht rückwirkend. Neue Codeverwendung wird über die Projektanweisungen und API abgesichert, nicht durch die Behauptung, beliebige Direktformatierung könne vom Java-Typsystem verhindert werden.

## Git, Commit und Betriebssicherheit

Branch: `codex/step06b-chat-design-system`. Basis: `f4970a9d8470e25a1a31bcbb756c048815384f2e`.

Die endgültige lokale Commit-ID wird unmittelbar nach Erstellung in der benachbarten Datei `STEP_06B_CHAT_DESIGN_SYSTEM_COMMIT.txt` protokolliert und im Abschluss genannt. Der eigene Commit-Hash kann nicht selbst Bestandteil desselben gehashten Commit-Inhalts sein. Der Nachweis bleibt bewusst ein ungestagtes lokales Übergabeartefakt.

Kein Push, Merge oder Deployment. Kein Stoppen oder Verändern des Operator-Servers. Die vorhandene Run-Konfiguration und die nach OLD verschobenen Benutzerberichte bleiben unverändert und werden nicht gestagt. Vorher-/Nachher-Hashes und die genaue Commit-Dateiliste werden separat erfasst.

Die Schutzprüfung vergleicht 55 archivierte Benutzerberichte sowie beide Run-Dateien per SHA-256. Vor dem Commit sind alle unverändert; die 55 entfernten alten Berichtspfade bleiben ebenfalls außerhalb des Index. Der SHA-256 der genehmigten Start-Konfiguration lautet weiterhin `EEDE5A220EEC6A8D154D82F2BD1BF29CFEB59215D6B546D83CDB38A73EAF596F`.

## Genau vorgesehene Commit-Dateien

Nur diese 26 Pfade werden explizit gestagt:

1. `AGENTS.md`
2. `docs/DEVELOPER_GUIDE.md`
3. `docs/SERVICE_DEPENDENCIES.md`
4. `docs/development/chat-design-system.md`
5. `reports/06b/STEP_06B_CHAT_DESIGN_SYSTEM.md`
6. `src/main/java/dev/vapee/core/i18n/LocalizationService.java`
7. `src/main/java/dev/vapee/core/i18n/TranslationCatalog.java`
8. `src/main/java/dev/vapee/core/i18n/Translations.java`
9. `src/main/java/dev/vapee/core/message/MessageService.java`
10. `src/main/java/dev/vapee/core/message/MessageStyle.java`
11. `src/main/java/dev/vapee/core/message/SocialChannel.java`
12. `src/main/java/dev/vapee/core/moderation/ModerationComponents.java`
13. `src/main/java/dev/vapee/core/moderation/command/ModerationCommandContext.java`
14. `src/main/java/dev/vapee/core/moderation/command/MuteCommand.java`
15. `src/main/java/dev/vapee/core/moderation/command/WarnCommand.java`
16. `src/main/java/dev/vapee/core/presence/FriendPresenceNotifier.java`
17. `src/main/java/dev/vapee/core/staff/StaffPanel.java`
18. `src/main/resources/lang/de.yml`
19. `src/main/resources/lang/en.yml`
20. `src/test/java/dev/vapee/core/config/MessageAccentHarness.java`
21. `src/test/java/dev/vapee/core/message/ChatDesignSystemHarness.java`
22. `src/test/java/dev/vapee/core/message/ChatPresentationHarness.java`
23. `src/test/java/dev/vapee/core/message/SocialChatStylingHarness.java`
24. `src/test/java/dev/vapee/core/presence/FriendPresenceNotifierHarness.java`
25. `src/test/java/dev/vapee/core/staff/StaffPanelHarness.java`
26. `src/test/java/dev/vapee/core/staff/StaffPanelTestSupport.java`

Prüflogs und maschinenlesbare Nachweise liegen außerhalb des Projekts unter `C:\Users\vapee\.codex\visualizations\2026\10\25\01a17432-7597-7ff1-bc9d-34201a027523\step06b-chat-design-system-20261103`.
