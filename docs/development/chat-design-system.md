# Chat-Designsystem

VapeeCore verwendet die bestehende Adventure-/MiniMessage-Architektur. `MessageStyle` definiert Palette und Komposition, `SocialChannel` die drei vorhandenen lokalisierten Feature-Wrapper, `MessageService` die bestehende Delivery und `LocalizationService` die Empfängersprache. Es gibt keinen zweiten Theme-Service, kein neues Modul und keinen zusätzlichen Scheduler.

## Semantische Palette

| Rolle | API | Standard | Verwendung |
|---|---|---|---|
| BRACKET | `MessageStyle.BRACKET` / kompatibles `PREFIX` | Dark Gray `#555555` | nur `[` und `]` |
| FRIENDS_LABEL | `MessageStyle.FRIENDS_LABEL` | Gold `#FFAA00` | Friends / Freunde |
| CLAN_LABEL | `MessageStyle.CLAN_LABEL` | Dark Purple `#AA00AA` | Clan |
| STAFF_LABEL | `MessageStyle.STAFF_LABEL` | Dark Gray `#555555` | Staff / Team; unabhängig von BRACKET |
| DEFAULT_LABEL | `MessageStyle.DEFAULT_LABEL` | Gray `#AAAAAA` | neutrale Rückfallfarbe alter API-Overloads |
| TEXT | `MessageStyle.TEXT` / bestehender Alias `BODY` | Gray `#AAAAAA` | neutrale Erklärungen |
| MUTED | `MessageStyle.MUTED` | Dark Gray `#555555` | nachrangige Hinweise |
| ACCENT | `MessageStyle.accent()` / bestehendes `HIGHLIGHT` | `#E5BF75` | Werte, Gründe, Command-Roots |
| SUCCESS | `MessageStyle.SUCCESS` | Green `#55FF55` | kleines Erfolgslabel |
| ERROR | `MessageStyle.ERROR` | Red `#FF5555` | Fehlerlabel |
| WARNING | `MessageStyle.WARNING` | Yellow `#FFFF55` | Warnung/Ersthinweis |
| ONLINE | `MessageStyle.ONLINE` | Green `#55FF55` | nur das Statuswort |
| OFFLINE | `MessageStyle.OFFLINE` | Gray `#AAAAAA` | dezenter Offline-Status |

`text(Component)` setzt eine neutrale Farbe nur bei fehlender eigener Farbe. `success`, `error`, `warning` färben Statuslabels; `status(label, boolean)` färbt Online-/Offline-Labels. Keine dieser Funktionen ist für das Umfärben ganzer Sätze mit Spielernamen bestimmt. Namen weiter über den vorhandenen UUID-/Rank-Formatter einsetzen. Die Farbwerte eines LuckPerms-Rangs und fachliche Clan-Namen/Tags bleiben eigenständig.

## Prefix und Delivery

Neue Features verwenden `MessageStyle.feature(bareLocalizedLabel, labelColor, body)` beziehungsweise `prefix(label, labelColor)`. Die zentrale Komposition erzeugt drei getrennte Components: öffnende Klammer, Label und schließende Klammer. Beide Klammern sind explizit BRACKET; das Label erhält die unabhängig übergebene zentrale Feature-Farbe. Der Nachrichteninhalt wird separat neutral formatiert. Die bisherigen Overloads ohne Farbangabe bleiben erhalten und verwenden DEFAULT_LABEL. Keine manuellen `[Feature]`-Strings in Feature-Klassen zusammensetzen. Neue Feature-Farben werden erst mit dem tatsächlichen Feature als Konstante in MessageStyle ergänzt, nicht vorsorglich registriert.

`message(formattedPrefix, labelColor, body)` ist der kompatible Adapter für vollständige bereits eingeklammerte Prefixe. Übersetzbare Prefixe werden durch den vorhandenen LocalizationService aufgelöst, bevor nur ihre Klammerzeichen auf Component-Ebene formatiert werden. Der interne Format-Component ist kein neuer Übersetzungsschlüssel und wird nicht an den Client durchgereicht. Die Sprachkataloge und ihre bestehenden Platzhalter werden nicht geändert. Vollständige Operator-Overrides werden nicht nochmals eingeklammert; Wortlaut, Abstände, Dekorationen und Click-/HoverEvents bleiben erhalten. Explizite Operator-Label-Farben behalten ihren Vorrang, die Klammern erhalten die zentrale Farbe. Hover-Inhalte und Aktionen im Body werden nicht durch die Prefix-Formatierung bearbeitet.

`SocialChannel.FRIENDS`, `CLAN` und `STAFF` delegieren an diese Komposition. Ihre bestehenden EN-/DE-Bezeichnungen bleiben `[Friends]`/`[Freunde]`, `[Clan]`, `[Staff]`/`[Team]`; nur ihre Klammern sind dunkelgrau. Friends/Freunde verwendet GOLD, Clan DARK_PURPLE und Staff/Team weiterhin DARK_GRAY. SocialChannel wählt die Label-Farbe über seine stabile Enum-Identität und die MessageStyle-Konstanten, niemals anhand eines übersetzten Namens. Eine spätere andere STAFF_LABEL-Farbe ändert BRACKET nicht. Die Operator-Wrapper-Regel bleibt erhalten: Friends/Clan verwenden normale Delivery, Staff besitzt seinen vollständigen Wrapper und verwendet Raw-Delivery. Bereits existierende explizite Operator-Templates und Sprach-Overrides werden nicht überschrieben.

TPA/TPAHere bleiben ohne Feature-/Operator-Präfix und zeigen weiterhin nur die Accept-Aktion. `/tpdeny` bleibt manuell verfügbar. Die eigene Friends-Join-Zusammenfassung bleibt eine einzelne präfixfreie Zeile. Ban-/Kick-Screens und Hinweise an betroffene Spieler erhalten keinen Staff-Präfix.

## Akzentfarbe und Lokalisierung

```yaml
messages:
  accent-color: "#E5BF75"
```

Die 16 Minecraft-Farbnamen und sechsstellige Hex-Werte werden weiterhin durch ConfigService validiert. Fehlende Werte verwenden den Standard; ungültige Werte warnen und verwenden den sicheren Fallback. `prepareReload` veröffentlicht nichts, Apply aktiviert die Farbe und Rollback stellt sie wieder her. Keine neue Config-Struktur, kein zweiter Farbzustand und keine Änderung des siebenstufigen Reload-Vertrags.

Gebündelte Sprachtexte können `<accent>` sowie `<vc_prefix>` (Klammerfarbe), `<vc_text>`, `<vc_muted>`, `<vc_success>`, `<vc_error>`, `<vc_warning>`, `<vc_online>` und `<vc_offline>` verwenden. Diese Tags beziehen ihre Farben aus MessageStyle. Die `vc_`-Namen vermeiden eine Kollision mit bestehenden dynamischen Platzhaltern wie `<prefix>`. Normales `<aqua>` bleibt Aqua, einschließlich Operator-Templates und Rangdaten. Die Tag-Resolver lesen den Akzent beim Rendern; bereits gesendete immutable Components werden nicht rückwirkend verändert.

EN/DE behalten dieselbe Struktur und dieselben fachlichen Platzhalter. Werte als `Component.text` beziehungsweise `MessageStyle.value`, Namen über den bestehenden Rank-Formatter übergeben. Niemals Spielernamen, Gründe oder Texte aus Commands in MiniMessage-Strings konkatenieren.

Bestehende Presence-Sprach-Overrides aus der Version vor Step 06B dürfen für `friends.presence.online` und `friends.presence.offline` weiterhin ausschließlich `<player>` enthalten. Die neuen gebündelten Texte verwenden zusätzlich `<status>`. Nur diese zwei bekannten alten Override-Verträge werden beim Laden und Reload akzeptiert; die gebündelte EN-/DE-Parität sowie fehlende, umbenannte oder zusätzliche Parameter anderer Texte bleiben strikt. Vorhandene Dateien und ihre Gestaltung werden dabei nicht umgeschrieben. Alte Overrides behalten deshalb auch ihre bisherigen Statusfarben, bis der Operator sie bewusst anpasst.

## Mehrzeilige Blöcke und Interaktionen

`messages.sendRawBlock(recipient, lines...)` lokalisiert zuerst und verwendet danach `MessageStyle.block`. Ein Block ist ein einzelner ausgelieferter Component mit genau einer leeren Zeile davor/danach und zwei Leerzeichen Einrückung je Inhaltszeile. Auch Newlines innerhalb einer Übersetzung werden eingerückt. Bei einem Feature-Block den Prefix einmal am ersten Inhalt einsetzen. Die Methode ergänzt keinen globalen Operator-Wrapper.

`MessageStyle.block` direkt nur mit bereits lokalisierten Components verwenden. Leere Blöcke werden zurückgewiesen. Die Formatierung ersetzt Text-Newlines auf Component-Ebene; sie serialisiert oder parst keine dynamischen Werte. Hover-Inhalte bleiben von der Einrückung ausgeschlossen. ClickEvents, HoverEvents und farbige Namen bleiben an ihren Components.

Warn-/Mute-Bestätigungen an Moderatoren verwenden einen solchen Block mit genau einem Staff-Präfix. Die Erstmitteilung an Betroffene verwendet denselben Abstand ohne Staff-Präfix. Grund und relevante Werte verwenden den Akzent; Mute-Ablauf/Dauerhaftigkeit bleiben vollständig sichtbar. Die zehn Sekunden gedrosselte Chat-Sperr-Erinnerung bleibt eine kompakte Zeile. Moderationsspeicherung, Permissions, Parser und Drossellogik sind unverändert.

Klickbare `[Accept]`, `[Deny]` oder ähnliche Labels sind Aktionen, keine System-Präfixe. Nicht mit dem Prefix-Formatter färben oder ihre RUN_COMMAND-/SUGGEST_COMMAND-Semantik verändern. Aktionen weiterhin serverseitig anhand UUID, Request-ID, Session und effektiven Permissions autorisieren.

## Beispiel für ein neues Feature

Nur ein Verwendungsbeispiel, kein implementiertes Party-System. Die betreffenden Übersetzungsschlüssel würde das Feature parallel in beiden Katalogen ergänzen:

```yaml
# EN
party:
  label: "Party"
  created: "<vc_success>Created</vc_success> a party with <player>."
# DE (im deutschen Katalog)
party:
  label: "Gruppe"
  created: "Gruppe mit <player> <vc_success>erstellt</vc_success>."
```

```java
Component body = tr("party.created", Map.of("player", names.format(targetId, targetName)));
messages.sendRaw(viewer, MessageStyle.feature(tr("party.label"), MessageStyle.DEFAULT_LABEL, body));

// Ein umfangreicherer Hinweis mit optionaler existierender Aktion:
messages.sendRawBlock(viewer,
        MessageStyle.feature(tr("party.label"), MessageStyle.DEFAULT_LABEL, body),
        MessageStyle.text(actionComponent));
```

Kein eigenes NamedTextColor für gemeinsam genutzte Systemfarben und keine zusätzliche Prefix-Implementierung notwendig. Die Delivery-Regel des Features bewusst wählen: normale `send` für den ausdrücklich gewünschten Operator-Wrapper, `sendRaw`/`sendRawBlock` für einen vollständig selbst besessenen Wrapper.

## Staff PUBLIC/HIDDEN

PUBLIC erlaubt die Aufnahme in die öffentliche Staff-Liste, vorbehaltlich aktueller Staff-Eignung, Online-Session und vorhandener `canSee`-Prüfung. HIDDEN schließt den Spieler aus dieser Liste aus. Der Status ist sessiongebunden; ein berechtigtes privates Panel kann Hidden-Staff weiterhin anzeigen. Es handelt sich nicht um einen Disguise und nicht um einen Unsichtbarkeits-/Moderations-Bypass. Rang, Prefix, Chat, Name, Nametag, Scoreboard und Tablist dürfen davon nicht verändert werden.

## Regression und Architekturregel

`PrefixStylingHarness` prüft getrennte Klammer-/Label-Components, beide Sprachen, identischen Override-Text mit unterschiedlichen Feature-Identitäten und Click-/Hover-Erhalt. `ChatDesignSystemHarness` prüft tatsächliche Prefix-/Farbspannen, ein neues Feature ohne Enum-Erweiterung, semantische Tags, Akzent-Reload/Rollback, EN/DE, Blockstruktur und interaktive Events. Bestehende Social-, Presence-, Staff-, TPA-, Moderations- und Config-Harnesses sichern die fachlichen Grenzen ab. Der Staff-Fixture erfasst zusätzlich Appearance-Schreibzugriffe.

Ein pauschaler repositoryweiter Regex-Verbotscheck für NamedTextColor ist ungeeignet: GUI-Items, Rangfarben und fachliche Farben bleiben legitim. Die schlanke Regel besteht deshalb aus den verbindlichen AGENTS-Anweisungen, dieser API und ausführbaren Verhaltenstests für alle vorhandenen SocialChannels. Neue Features müssen dieselben Tests um ihre beobachtbare Ausgabe erweitern. Es gibt keine Behauptung, dass ein Java-Typsystem beliebige zukünftige Direktformatierung automatisch verhindern könnte.

Operator-Sprach-Overrides behalten Vorrang und können bewusst vom gebündelten Stil abweichen. Eine Umstellung solcher Texte ist eine separate Operator-Entscheidung. Rendered Minecraft-Client-Prüfungen von Kontrast, Umbruch, Hover und Abständen bleiben nach jeder Änderung an der Präsentation sinnvoll.
