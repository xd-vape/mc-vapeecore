# Step 07A – Vollständiger GUI- und UX-Audit

## 1. Executive Summary

Quellcodebasis ist `e1c069fb1e7046c93068d399bbaebf19dd4f60f4` nach Step 06D.1. Untersucht wurden **elf eigene GUI-Systeme**: PlayerMenu, Settings, VisibilitySettings, VisiblePlayers, Friends, Clan, Navigator zur Laufzeit, NavigatorEditor, StaffPanel, DailyQuests und Invsee. Mehrere Ansichten desselben Systems zählen dabei zusammen. Zusätzlich wurde das native EnderChest-Inventar als andersartige, tatsächlich beschreibbare Inventaroberfläche geprüft. `/profile`, Rangabfragen, Moderationshistorie und TPA erzeugen keine weiteren eigenen Inventarmenüs.

Die Grundlage ist bereits tragfähig: explizite Layouts, gebundene Holder, autoritative Slot-Ziele, geklemmte Pagination, literale Nutzernamen und ereignisbasierte Aktualisierung nach Service-Commits. Eine neue UI-Engine ist nicht erforderlich. Staff besitzt besonders gründliche Session-/Callback-Prüfungen; DailyQuests hat bereits einen gezielt begrenzten Reset-Countdown.

**Kein P0-Befund wird in diesem Audit behauptet.** Das ist keine umfassende Exploit-Freiheitsgarantie. Die wichtigsten Maßnahmen betreffen nachvollziehbare Bedienrisiken und Konsistenz:

1. Clan-Eigentumsübergabe vor dem tatsächlichen Transfer ausdrücklich bestätigen.
2. Rückweg aus dem Friends-Menü, Kontext beim Chat-Handoff und Rücksprünge innerhalb des Editors verständlicher machen.
3. Editor-Einstieg, leere Picker und gemischte EN-/DE-/Techniktexte verbessern.
4. Reale Spielerprofile für personenbezogene Clan-Köpfe ergänzen.
5. Online-/Offline-, Gefahren- und Steuerfarben in ausgewählten GUIs konsistent anwenden.

Zuerst empfehlen sich kleine Clan- und Navigationskorrekturen; danach Editor-Texte/Leerzustände und die kosmetische Angleichung. Staff und DailyQuests benötigen keinen grundlegenden Umbau. Tatsächliche Lesbarkeit, Tooltip-Umbrüche, Klickgefühl und Client-Aktualisierung sind **visuell nicht bestätigt**.

## 2. Ausgangszustand, Scope und Methode

- Repository: `E:\NEXTCLOUD\Programmierung\JAVA\MINECRAFT\VapeeCore`.
- Zu Beginn: sauberer Hauptarbeitsbaum auf `master`; `master` und `origin/master` auf dem erwarteten Quellcommit. Sicherer `git fetch origin` ohne Divergenz.
- Audit-Branch: `codex/step07a-gui-ux-audit`, unmittelbar von diesem `master` angelegt.
- Optionaler Cleanup ausgeführt: ausschließlich der lokale `codex/step06d1-remove-public-staff-roster` wurde mit `git branch -d` entfernt. Sein HEAD entsprach `master`, `merge-base --is-ancestor` war erfolgreich, `master..branch` enthielt keine Commits, kein Worktree hatte ihn ausgecheckt und der Hauptarbeitsbaum war sauber.
- Recovery-Branches und `codex/chat-presentation-polish` bleiben erhalten. Im fremden `chat-polish-wt` bleibt der bekannte ungetrackte Bericht `reports/CHAT_PRESENTATION_POLISH.md` unangetastet.
- Vor Änderungen wurden 1.481 Dateien beider Worktrees einschließlich der zwei geschützten ignorierten Step-06B-Receipts per SHA-256 erfasst. Operator-Welten, Logs, LuckPerms- und Spielerdaten waren keine Testfixtures.
- Einzige erlaubte Repository-Dateiänderung ist dieser Bericht. Keine Java-, Test-, Sprach-, Config-, Maven- oder Run-Datei wurde verändert. Kein Formatter, keine neuen Abhängigkeiten und keine laufende Serverinstanz verwendet.

Die Suche umfasste `createInventory`, Methodenreferenzen auf `createInventory`, `openInventory`, Holder, Click-/Drag-Listener, Modulverdrahtung, Service-Observer und vorhandene Harnesses. Nachfolgende Slotnummern sind **nullbasiert**. Standardlayouts aus Java sind von operatorseitig konfigurierten Navigator-Menüs und Hotbar-Items zu unterscheiden.

Evidenzklassen:

- **Q:** direkt durch den referenzierten Quellcode belegt.
- **B:** vorhandener Behavior-/Lifecycle-Harness; seine Existenz ist keine neue Testausführung.
- **V:** eine tatsächliche Client-Auswirkung bleibt visuell oder mit verbundenem Client zu prüfen.
- **Empfehlung:** zukünftige, in diesem Schritt nicht implementierte Änderung.

Historische Reports wurden als Kontext gelesen, insbesondere die GUI-Authoring-/Pagination-Berichte, Navigator-Prototypberichte, Sicherheitsremediation und Step 06D.1. Bei Abweichungen ist der aktuelle Quellcode maßgeblich. Die öffentliche Staff-Liste und ihr PUBLIC/HIDDEN-Zustand sind **entfernt**. `VisibilityMode.HIDDEN` in den persönlichen Sichtbarkeitseinstellungen ist ein anderes, weiterhin vorhandenes Feature.

## 3. Vollständige Bestandsaufnahme

### 3.1 PlayerMenu – persönlicher Lobby-Einstieg

**Zweck/Einstieg:** persönlicher Hub über Rechtsklick auf das registrierte `player-menu`-Hotbar-Item. Kein eigener `/playermenu`-Command. Die Registrierung steht in [LobbyItemRegistrations.java:48](../../src/main/java/dev/vapee/core/lobby/item/LobbyItemRegistrations.java#L48); Ziele und Zugang verdrahtet [LobbyExperienceModule.java:144](../../src/main/java/dev/vapee/core/lobby/experience/LobbyExperienceModule.java#L144).

**Größe/Layout/Navigation:** 27 Slots, `MenuLayout`; VISIBILITY 11, FRIENDS 13, SETTINGS 15, CLOSE 22. SPYGLASS, BOOK, COMPARATOR und BARRIER; keine Filler, Listen, Pagination oder Refresh-Schaltfläche. Die drei Hauptkacheln haben beschreibende Lore und Öffnungshinweis. Ein nicht erlaubtes beziehungsweise nicht verdrahtetes Ziel fehlt vollständig: [PlayerMenu.java:19](../../src/main/java/dev/vapee/core/lobby/experience/player/PlayerMenu.java#L19), `allowed` und `render` ab Zeile 74/91.

**Zustand/Rechte/Config:** Online-Spieler, geladene Settings, aktive Lobby-Item-Registry, Lobbyzugang/NORMAL-Modus, keine Activity und aktiviertes Hotbar-Feature. FRIENDS braucht `vapeecore.friend.use`; SETTINGS und VISIBILITY brauchen `vapeecore.settings.use`. Kein erfundener zusätzlicher Hub-Permission-Node. `lobby.yml` beeinflusst Hotbar-Aktivierung/Aussehen, nicht die drei GUI-Slots. Texte: `player-menu.*`, `common.*`; Namen AQUA, Beschreibungen GRAY, Close GRAY.

**Interaktion/Updates:** Links- oder Rechtsklick schließt den Hub und öffnet das erlaubte Ziel. Holder bindet Owner-UUID und exaktes Inventory; zusätzlich aktive Registry und geöffnetes Top-Inventory. Drag wird verworfen; Weltwechsel kann die Ansicht schließen. Locale-Observer rendert Items; kein Polling. Quellen: [PlayerMenuListener.java:11](../../src/main/java/dev/vapee/core/lobby/experience/player/PlayerMenuListener.java#L11), [PlayerMenuHolder.java:12](../../src/main/java/dev/vapee/core/lobby/experience/player/PlayerMenuHolder.java#L12).

**Tests/Auffälligkeit:** `PlayerMenuHarness`; enger Hub mit drei klaren Zielen. Friends hat keinen symmetrischen Rückweg zum Hub. Bei entfallenden Zielrechten können Kacheln bis zur nächsten Aktualisierung sichtbar bleiben, die Aktion prüft die Rechte erneut; kein belegter Autorisierungs-Bypass.

### 3.2 Settings – persönliche Einstellungen

**Zweck/Einstieg:** `/settings`, PlayerMenu und optional registrierter Settings-Hotbar-Shortcut. 45 Slots; fünf Icon-/Statuspaare: Scoreboard 11/20, Sounds 12/21, private Nachrichten 13/22, Freundschaftsanfragen 14/23, Freundesstatus 15/24. BACK 36, CLOSE 44. VISIBILITY und REFRESH sind unterstützte Actions, im **Standardlayout nicht platziert**. Die Sichtbarkeit ist regulär über PlayerMenu und `/settings visibility` erreichbar; ihr Fehlen in diesem Layout ist keine verlorene Implementierung. Quelle: [SettingsMenu.java:39](../../src/main/java/dev/vapee/core/settings/SettingsMenu.java#L39).

**Darstellung/Zustände:** MAP, NOTE_BLOCK, WRITABLE_BOOK, generischer PLAYER_HEAD und BELL; darunter grünes/rotes Glas mit aktiviert/deaktiviert. Beide Positionen eines Paars führen dieselbe reversible Einstellung aus. Aktueller Status, Beschreibung, Leerzeile und Klickhinweis sind vorhanden. Keine dynamische Liste, Pagination oder fachlicher Leerzustand; fehlendes geladenes Profil wird als Fehler behandelt. Der generische Kopf für „Freundschaftsanfragen“ bezeichnet eine Funktion und benötigt keinen Spieler-Skin.

**Rechte/Lokalisierung/Config:** `vapeecore.settings.use` beim Öffnen und beim Klick; `settings.*` und `common.*`. PlayerSettingsService ist Eigentümer der Werte; Die gespeicherten Werte und bestehenden Java-Domain-Defaults bestimmen den Zustand; keine frei konfigurierbaren GUI-Slots. [SettingsMenuEntries.java:26](../../src/main/java/dev/vapee/core/settings/SettingsMenuEntries.java#L26) definiert die fachlichen Tiles, `toggle` ab Zeile 76 Speichern, Präsentationsrefresh und Statusfarben.

**Navigation/Interaktion:** einfache Links-/Rechtsklick-Toggles, kein Bestätigungsdialog nötig. BACK wird von LobbyExperience zum PlayerMenu verdrahtet; ist dieses dort nicht zugänglich, schließt die Ansicht. Auch ein direkter `/settings`-Aufruf bekommt diesen Rückweg. Persistenzfehler liefern Rückmeldung und erneutes Rendern statt vorgetäuschtem Erfolg: [SettingsListener.java:74](../../src/main/java/dev/vapee/core/settings/SettingsListener.java#L74).

**Updates/Sicherheit/Tests:** Settings-Commit- und Locale-Observer; exakte Holder-/Owner-/Inventory-/Active-Bindung, Click-/Drag-Sperren und Shutdown-Cleanup. `SettingsInventoryHolder`, `SettingsMenuHarness`, `SettingsMenuSecurityHarness`, `SettingsLayoutHarness`, `SettingsLayoutAlignmentHarness`, `SettingsAutomaticUpdateHarness`, `SettingsLocalizationHarness`. Kleine technische Kopplung: PM benutzt `settings.friend-requests.hint-one`, obwohl ein eigener PM-Key existiert. Die gebündelten Texte sind aktuell identisch; erst individuelle Overrides können die Unterschiede sichtbar machen.

### 3.3 VisibilitySettings – persönliche Sichtbarkeitsfilter

**Zweck/Einstieg:** PlayerMenu/VISIBILITY oder `/settings visibility`; ebenfalls aus der hinzugefügten Spielerliste zurück erreichbar. 45 Slots; MASTER 11/20, FRIENDS 12/21, STAFF 13/22, ADDED_USERS 14/23, GAME_PARTICIPANTS 15/24, MANAGE_PLAYERS 40, BACK 36, CLOSE 44. REFRESH existiert als optionale Action, im Standardlayout ohne Slot. [VisibilitySettingsMenu.java:34](../../src/main/java/dev/vapee/core/settings/visibility/VisibilitySettingsMenu.java#L34).

**Status/Dynamik:** MASTER wechselt ALL/FILTERED/HIDDEN. Modus wird mit grünem/gelbem/rotem Glas gezeigt; Filter zeigen gespeichert aktiv/inaktiv und haben ausdrücklich den Hinweis, dass sie im gefilterten Modus gelten. ADDED_USERS und MANAGE_PLAYERS zeigen die Zahl konfigurierter Nutzer. GAME_PARTICIPANTS ist sichtbar als graue, **nicht verfügbare** Integration; ein Klick erklärt die zukünftige Verfügbarkeit und schaltet nichts frei. Quellen: `render` ab Zeile 142, [VisibilitySettingsListener.java:89](../../src/main/java/dev/vapee/core/settings/visibility/VisibilitySettingsListener.java#L89).

**Aktionen/Navigation:** normale Links-/Rechtsklicks speichern reversible Filter. Erst nach erfolgreichem Save wird die Sichtbarkeit angewendet; Anwendung und Speicherung haben getrennte Fehlertexte. BACK führt mit aktiver LobbyExperience zum PlayerMenu; MANAGE_PLAYERS öffnet die separate Liste. Keine Pagination und kein klassischer Leerzustand in diesem festen Formular.

**Rechte/Texte/Config:** `vapeecore.settings.use`, geladenes Settings-Profil; `visibility.*`, `settings.visibility.*`, `common.*`. Werte gehören zu PlayerSettings und bestehender VisibilityService-Policy, nicht zu einer neuen GUI-Konfiguration. `vapeecore.visibility.staff` ist die fachliche Staff-Erkennung der Sichtbarkeit und kein zusätzlicher Öffnungs-Node für dieses Formular.

**Updates/Bindings/Tests:** Settings-Commit- und Locale-Observer, gebundener `VisibilitySettingsHolder`, Owner/aktives/geöffnetes Inventory, Close/Quit/Shutdown. `VisibilitySettingsMenuHarness`, `VisibilityMenuSecurityHarness`, `VisibilityLayoutHarness`, `VisibilityAutomaticUpdateHarness`. Verbesserungspotenzial ist die Unterscheidung „Filter gespeichert“ gegenüber „momentan wirksam“, obwohl die Lore dies bereits erklärt; keine Behauptung einer kaputten Filterlogik.

### 3.4 VisiblePlayers – manuell hinzugefügte sichtbare Spieler

**Zweck/Einstieg:** MANAGE_PLAYERS aus VisibilitySettings; `/settings visibility add/remove` sind die ergänzenden Texteingaben. 54 Slots, 45 Content-Slots 0–44, leerer Zustand auf 22. PREVIOUS 45, ADD 46, BACK 48, CLOSE 49, PAGE_INFO 52, NEXT 53; REFRESH im Default nicht platziert. [VisiblePlayersMenu.java:47](../../src/main/java/dev/vapee/core/settings/visibility/VisiblePlayersMenu.java#L47).

**Daten/Darstellung:** autoritative gespeicherte UUIDs; bekannte Namen vor unbekannten Identitäten, dann alphabetisch/UUID. Echte Köpfe über `CachedPlayerHeads`; Online-Status grün, Offline grau; Name AQUA, volle UUID und Rechtsklick-Entfernen in Lore. Unbekannte Identität fällt auf UUID zurück. Pagination wird geklemmt; unbenötigte Pfeile/Seiteninfo fehlen. Leere Liste hat PAPER mit Einstiegshinweis. [VisiblePlayersMenu.java:243](../../src/main/java/dev/vapee/core/settings/visibility/VisiblePlayersMenu.java#L243).

**Interaktion/Navigation:** ADD schließt das Inventar und sendet einen SUGGEST_COMMAND `/settings visibility add `; keine Chat-Eingabe wird abgefangen. Ein Rechtsklick auf einen Zielkopf entfernt direkt, ohne Bestätigung, über den Settings-Service; reversibel durch erneutes Hinzufügen. BACK kehrt zum Visibility-Formular zurück. Nach Hinzufügen per Command gibt es keinen gespeicherten Rücksprung zur vorherigen Listenseite.

**Rechte/Texte/Config:** `vapeecore.settings.use`; geladenes Settings-Profil, bekannte Identitäten für das Hinzufügen und Service-Selbstzielprüfung. `visibility.*`, `visible-players.*`, `common.*`; persistierte persönliche Liste, keine eigene Skin-/Layout-Konfiguration. Volle UUID bei jedem bekannten Namen kann Tooltips verlängern; tatsächlicher Umbruch ist V.

**Updates/Sicherheit/Tests:** Settings-Commit, Locale und relevante Join-/Quit-Ereignisse; kein eigener Skin-Download oder zusätzlicher Cache. `VisiblePlayersHolder` hält aktuelle sichtbare Aktionen und UUID-Ziele statt Itemnamen als Autorität. [VisiblePlayersListener.java:63](../../src/main/java/dev/vapee/core/settings/visibility/VisiblePlayersListener.java#L63). `VisiblePlayersMenuHarness`, `VisiblePlayerHeadsHarness`, `VisiblePlayersPaginationUpdateHarness`, `VisibilityMenuSecurityHarness`.

### 3.5 Friends – Freunde und Anfragen

**Zweck/Einstieg:** `/friend` ohne Argumente, PlayerMenu/FRIENDS und optionaler Friends-Hotbar-Shortcut. Ansichten `FRIENDS`, `INCOMING`, `OUTGOING`. 54 Slots mit 45 Einträgen 0–44, EMPTY 22; PREVIOUS 45, ADD 46, FRIENDS 47, INCOMING 48, CLOSE 49, OUTGOING 50, PAGE_INFO 52, NEXT 53. Kein Default-Refresh, kein BACK. [FriendMenu.java:50](../../src/main/java/dev/vapee/core/friend/gui/FriendMenu.java#L50).

**Daten/Status/Leerzustände:** echte UUID-bezogene Köpfe in allen drei Listen, Namens-/UUID-Sortierung unabhängig von Online-Status. Gewählter Tab grün/LIME_DYE, andere weiß/GRAY_DYE; Tabs enthalten Zählung und Auswahlhinweis. Alle drei leeren Listen besitzen eigene Texte. Pfeile/Seiteninfo erscheinen nur, wenn nötig. Online/Offline steht in der Lore; da beide gebündelten Keys unformatiert und `UiItems.literal` die Lore grau rendert, fehlt hier die grüne Online-Farbrolle anderer Listen. [FriendMenu.java:256](../../src/main/java/dev/vapee/core/friend/gui/FriendMenu.java#L256).

**Aktionen:** Freunde entfernen mit SHIFT_RIGHT; eingehend LEFT akzeptieren/RIGHT ablehnen; ausgehend LEFT oder RIGHT abbrechen. Die unterschiedlichen Gesten sind in Lore beschrieben. Service-Rückgaben bestimmen die Rückmeldung und erst echte Commits aktualisieren andere Ansichten. ADD schließt und bietet `/friend add ` als SUGGEST_COMMAND. Kein automatisches Ausführen und keine GUI-eigene Texteingabesession. Tabwechsel beginnt auf Seite 0; In-place-Refresh erhält aktuelle Ansicht/Seite, soweit noch gültig. [FriendMenuListener.java:43](../../src/main/java/dev/vapee/core/friend/gui/FriendMenuListener.java#L43).

**Zugang/Config/Sprache:** `vapeecore.friend.use`, verfügbare Friend-/Identity-Services; `config.yml` `friends.limits` begrenzt Beziehungen/Anfragen, nicht Slotgeometrie. Texte `friends.*`, `common.*`; Spielernamen werden als literale Components eingebunden. FriendMenu selbst liest keinen RankService und färbt Namen AQUA; Rangfarben aus dem Chat werden hier nicht nachträglich übernommen. Das ist der Ist-Zustand, kein nachgewiesener Verlust eines zugesagten GUI-Rangvertrags.

**Updates/Bindings/Tests:** Friend-Commit-/Locale-Observer und relevante Presence-Events; während Quit gerenderter Presence-Zustand wird nur lokal übersteuert. Holder Owner/bound Inventory/aktive und tatsächlich offene Ansicht, sichtbare Actions und Slot-UUIDs; Drag/ungültige Inventaroperationen gesperrt. `FriendMenuHarness`, `FriendMenuSecurityHarness`, `FriendHeadRenderingHarness`, `FriendPaginationUpdateHarness`, `FriendMenuLifecycleHarness`, `FriendLayoutHarness`. Haupt-UX-Themen: kein Rückweg zum Hub und kein Kontext nach dem Chat-Handoff.

### 3.6 Clan – Übersicht, Mitglieder, Einladungen

**Zweck/Einstieg:** `/clan` ohne Argumente. `OVERVIEW`, `MEMBERS`, `INVITES`; keine eigene zusätzliche Spielerprofil-GUI. 54 Slots, Content 0–44, EMPTY 22; PREVIOUS 45, OVERVIEW 46, MEMBERS 47, INVITES 48, CLOSE 49, kontextuelle ACTION 50, PAGE_INFO 52, NEXT 53. Overview-Namen/Anzahl/Rolle auf 20/22/24; diese Displays sind exklusiv zur Liste. [ClanMenu.java:40](../../src/main/java/dev/vapee/core/clan/gui/ClanMenu.java#L40).

**Dynamik/Status/Leerfälle:** ohne Clan Overview-Hinweis mit Erstellen/Einladungen; MEMBERS wird auf Overview zurückgeführt. Owner steht in Mitgliederlisten zuerst, dann alphabetisch/UUID; vorhandener Clan enthält seinen Owner, daher ist „nur ein Mitglied“ eine gültige Eintragsliste, kein fehlender Leerzustand. Incoming-Clan-Einladungen sind PAPER mit Clanname/Tag; Owner sieht ausgehende Spielereinladungen, gewöhnliche Mitglieder keine fremde Verwaltungsliste. Eigene leere Mitglieder-/Einladungstexte sind vorhanden. Besitzer-/Mitgliederrolle und Online-Status stehen in Lore.

**Köpfe/Farben:** Mitglieder und ausgehende Spielereinladungen verwenden zwar PLAYER_HEAD, aber `item(Entry)` rendert nur `UiItems.literal`; es gibt hier keine UUID-/Profil-Dekoration mit `CachedPlayerHeads`. Der Übersichtskopf für die Mitgliederanzahl ist dagegen bewusst ein generisches Symbol. Namen und Steuertexte sind standardmäßig AQUA, Lore einschließlich Online/Offline grau. [ClanMenu.java:181](../../src/main/java/dev/vapee/core/clan/gui/ClanMenu.java#L181), `item` ab Zeile 282.

**Aktionen/Bestätigung:** Mitglieder: SHIFT_RIGHT kickt, SHIFT_LEFT überträgt Eigentum **direkt**. Incoming: LEFT akzeptiert/RIGHT lehnt ab; outgoing: RIGHT bricht ab. Erstellen schlägt einen Command mit Beispieltext vor. Auflösen braucht SHIFT_RIGHT auf ACTION und danach den vorgeschlagenen `/clan disband confirm`; der Klick selbst löst nicht auf. Der direkte Eigentumstransfer ist somit eine belegte Bestätigungs-Asymmetrie, kein Beleg für fremde Autorität. [ClanMenuListener.java:80](../../src/main/java/dev/vapee/core/clan/gui/ClanMenuListener.java#L80), [ClanService.java:158](../../src/main/java/dev/vapee/core/clan/ClanService.java#L158).

**Rechte/Config:** `vapeecore.clan.use` an Command und Click-Listener; `ClanMenu.open` selbst besitzt keinen zusätzlichen Permission-Gate. Aktuelle Clan-ID, aktuelle Rolle und Service-Ownership werden erneut geprüft; Item-Lore verleiht keine Rechte. Config `clans.limits`, Namen-/Taggrenzen; Katalog `clan.*`, `clans.*`, `common.*`. Zukünftige neue Einstiegspunkte müssen die vorhandene Command-/UI-Grenze respektieren.

**Updates/Tests:** erfolgreiche Clan-Service-Commits veröffentlichen betroffene Spieler, Locale/Presence aktualisieren bestehende Inventare und klemmen Seiten. Source: [ClanService.java:281](../../src/main/java/dev/vapee/core/clan/ClanService.java#L281), [ClanMenu.java:310](../../src/main/java/dev/vapee/core/clan/gui/ClanMenu.java#L310). `ClanMenuHarness`, `ClanMenuSecurityHarness`, `ClanPaginationUpdateHarness`, `ClanMenuLifecycleHarness`, `ClanLayoutHarness`; Domain-Tests ergänzen Owner-/Ziel-/Save-Fehler. Keine neue Confirmation-/Skin-Funktion wurde implementiert.

### 3.7 Navigator – Legacy und konfigurierter V2-Betrieb

**Einstieg/Zugang:** registriertes Navigator-Hotbar-Item; es öffnet in der Lobby für online/geladen/NORMAL/ohne Activity. `/navigator` ist der Admin-Editor, kein erfundener öffentlicher GUI-Root. Die Policy hat keinen eigenen allgemeinen `navigator.use`-Node. V2-Einträge besitzen eigene optionale Permissions, die bei Navigation und Aktionen erneut gelten: [NavigatorAccessPolicy.java:39](../../src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorAccessPolicy.java#L39), [NavigatorMenu.java:436](../../src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenu.java#L436).

**Legacy:** bei gebündeltem `navigator.yml enabled: false` bleibt die flache Warp-Liste. 27 Slots, 25 Content-Plätze 0–17/19–25; PREVIOUS 18, NEXT 26, EMPTY 13. PAGE_INFO/CLOSE sind als mögliche Actions vorhanden, im Defaultlayout jedoch **nicht platziert**. Kein sichtbarer Standard-Close-Button; ESC ist der normale Ausstieg. Navigierbare Warps bestimmen Icons/Namen/Sortierung. [NavigatorMenu.java:53](../../src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenu.java#L53).

**V2:** Menüs haben 27/36/45/54 Slots oder kompatibel ermittelte Altdatei-Größe. Einträge haben explizite Seite/Slot/Icon/Name/Lore/Typ/Ziel/Permission/optionale Region. MENU öffnet Untermenü, WARP teleportiert über WarpService, UNAVAILABLE führt keine Aktion aus. Sichtbare konfigurierte Seitennummern werden zu tatsächlich erreichbaren Seiten verdichtet. Previous ist `size-9`, Next `size-1`; optionaler Back-Slot ist konfigurierbar und kollisionsgeprüft. Sonst Rechtsklick auf einen **leeren** Slot als Rückweg, mit einmaligem Actionbar-Hinweis. Breadcrumbs erhalten Elternseite und Route; veränderte/entzogene Elternberechtigungen werden erneut geprüft. [NavigatorConfig.java:152](../../src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorConfig.java#L152), [NavigatorMenu.java:394](../../src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenu.java#L394).

**Darstellung/Zustände:** fehlende Entries ergeben GRAY_DYE/Leertext; global ausgeblendete oder fehlende Warpziele werden als nicht verfügbar beschrieben. Ein tatsächlich nicht geladener Warp-Welt-/Teleportfehler wird beim Ausführen gemeldet. Konfigurierte Namen/Lore sind `LocalizedText` und werden als literale Texte eingebunden; kein freier Command-Executor. Regionzähler stehen in Lore oder begrenzter Stack-Anzahl, inklusive Null-/Overflow-Lore. Zählung erfolgt beim Rendern; es gibt keinen belegten Live-Movement-Zähler. [NavigatorMenu.java:505](../../src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenu.java#L505).

**Config/Updates/Tests:** `navigator.yml`, `warps.yml`, Lobby-/Hotbar-Zugang; Warp-Commit- und Locale-Observer, zusätzlich Neuöffnung und Editor-Apply. Manuelle Navigator-YAML-Änderungen benötigen nach dokumentiertem Start-/Store-Vertrag einen Neustart; `/core reload` ist keine pauschale Neuinitialisierung aller Navigator-Konfigurationen. Drag in das Top-Inventar wird blockiert; ein reiner Bottom-Drag ist unter gültiger Ansicht bewusst anders behandelt als in Social-Menüs. `NavigatorMenuHarness`, `NavigatorV2Harness`, `NavigatorConfigHarness`, `NavigatorSecurityHarness`, `NavigatorLayoutHarness`, `NavigatorPaginationUpdateHarness`, `NavigatorLocalizationHarness`, `NavigatorCompactHarness`, `NavigatorRegionHarness` und `NavigatorRegionHeightHarness`.

### 3.8 NavigatorEditor – Admin-Entwurf, Vorschau und Regionwerkzeug

**Zweck/Eintritt:** `/navigator edit`, `vapeecore.navigator.admin`, online und aktive Editor-Runtime. Eigener Session-Token, Base-Snapshot, Draft, Auswahl, Seite, Slotbank und Revision. Lokaler `Holder` bindet Session/Revision/Owner/Inventory; Click-Gültigkeit prüft zusätzlich aktive und tatsächlich offene Ansicht. Das ist eine begründete Feature-Besonderheit, kein zweites allgemeines UI-System. [NavigatorEditor.java:66](../../src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditor.java#L66), `valid`/`click` ab Zeile 733.

| Editor-Ansicht | Größe und relevante Plätze | Verhalten |
| --- | --- | --- |
| `showMenu` | 54; Projektionsfläche 0–44; Menüwahl 45, Menüaktionen 46, technische Seiteninfo 47, Preview 48, Save 49, Discard 50, Seite zurück/vor 51/52, Slotbank 53 bei großen Menüs | Freier dargestellter Slot öffnet Add, belegter Slot Entry-Editor. Bank bildet die übrigen echten Slots bis 53 ab. Seiten sind 0–999, kein automatischer Endpunkt an der letzten belegten Seite. |
| `showMenuActions` | 27; Create 10, Select 11, Rename 12, Icon 14, Größe 15, Back-Option 16, Rückweg 22 | Teilweise Command-Vorschläge für Texteingaben. |
| `showSizes` / `showBackOptions` | 27; Größen 10/12/14/16, Zurück 22; Back-Status 4, deaktivieren 11, Slot wählen 15 | Größe/Back-Slot betreffen den Entwurf, keine automatische Layout-Umsortierung. |
| `showAdd` | 27; Warp 11, bestehendes Untermenü 13, Platzhalter 15, neues Untermenü 22, Rückweg 18 | Zielarten sind explizit getrennt. |
| `showWarps` / `showMenus` | 54; 0–44, vorher 45, zurück 49, weiter 53 | 45er-Picker ohne explizite Empty-Kachel oder Seiteninfo. Picker-Zurück führt direkt zum Haupteditor. |
| `showIcons` | 27; Materialauswahl ab 0, Rückweg 22 | Kleine vordefinierte Auswahl; beliebige weitere Item-Materialien per Command. Rückweg zum Haupteditor. |
| `showEntry` / `showTextActions` | 27; Entry-Info 4, Text 10, Icon 11, Permission 12, Move 13, Remove 14, Untermenü 15 oder Region 18, zurück 26; Text-Unterdialog 11/15, zurück 22 | Remove ändert zunächst nur den Draft. Keine neue persistente Aktion ohne Save. |
| `showRegion` | 27; Info 4, Höhe 6, Pos1 10, Pos2 12, Clear 14, Stack 16, Markieren 20, zurück 22, Abbruch 24 | Ganze Welthöhe oder manuelle Höhe, Regionwerkzeug und zwei Blockpositionen. |

Quellen: [NavigatorEditor.java:572](../../src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditor.java#L572), [NavigatorEditor.java:642](../../src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditor.java#L642), [NavigatorEditor.java:696](../../src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditor.java#L696).

**Save/Preview/Sicherheit:** Save validiert Draft und vollständige Regionwahl, prüft Base-Revision/Dateibytes, erzeugt Backup und publiziert über vorhandenen Store. Externe Änderungen werden nicht blind überschrieben. Preview führt keinen Warp-Teleport aus; Rückkehr per `/navigator edit`. Schließen einer Ansicht ist nicht gleich Discard; Draft lebt bis Save, Discard, Quit, Permission-Verlust oder Shutdown. Entfernen/Verwerfen haben unterschiedliche Tragweite, obwohl beide als Ein-Klick-Aktion auftreten. [NavigatorStore.java:47](../../src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorStore.java#L47), [NavigatorEditor.java:547](../../src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditor.java#L547).

**Region-/Config-Grenze:** neues Markieren nutzt grundsätzlich volle Welthöhe; bestehende Regionen werden nicht automatisch umgeschrieben. X/Z bleiben markiert, Y ist Minimum bis Maximum minus eins; manuelle Höhe bleibt wählbar. Werkzeug behandelt Pending-Region, Targetwechsel, Rückgabe/Abbruch und Item-Sicherheit separat. Diese Funktionsregeln werden nicht als UX-Verbesserung erneut erfunden.

**Lokalisierung/Updates:** viele Texte werden über `text(player,en,de)` direkt im Java-Code gewählt statt aus Operator-Katalogen. Beispiele sind rein englisches `Page … / slots …`, `Type`, `Target`, `Permission`, `Navigable`, `Stack: true/false`, einzelne Symbolnamen `↩`. Bestehende sprachabhängige Hauptlabels sind vorhanden; „Editor ist vollständig unübersetzt“ wäre falsch. Ein eigener Locale-Observer für sofortiges Editor-Rerender fehlt; neue Dialoge wählen die aktuelle Sprache. `changed` invalidiert alte Revisionen/Previews. Keine neue Polling-Architektur erforderlich.

**Tests/UX:** `NavigatorEditorHarness`, `NavigatorEditorUxHarness`, `NavigatorFinalPresentationHarness`, `NavigatorStoreHarness`, `NavigatorRegionToolHarness`, `NavigatorRegionHeightHarness`, `NavigatorCompositionHarness`. Leere Hauptflächen sind absichtlich anklickbar, aber benötigen einen verständlichen Einstiegshinweis. Picker-Back und unterschiedliche `↩`-Plätze erschweren den Kontext. Graues „Speichern gesperrt“ hat erklärende Lore und bleibt mit serverseitiger Validierung abgesichert; kein nachgewiesenes Speichern ungültiger Daten.

### 3.9 StaffPanel – ausschließlich privates Staff- und Zielpanel

**Eintritt/Rechte:** `/staff`, ausschließlich ohne Argumente; `vapeecore.staff.panel` **und** aktuelle geeignete primäre LuckPerms-Gruppe, aktive Runtime und exakte aktuelle Player-Sitzung. Root-Vorschläge hängen vom Node ab; tatsächlicher Zugang zusätzlich von der Gruppe. `/staff list` liefert nur Usage und ist kein vorhandenes Listenfeature. [StaffPanel.java:93](../../src/main/java/dev/vapee/core/staff/StaffPanel.java#L93).

**Layout/Daten:** beide Ansichten 27 Slots. Liste 0–17, EMPTY 13, PREVIOUS 18, MODE 19, REFRESH 20, CLOSE 24, PAGE 25, NEXT 26. Ziele alphabetisch/UUID, Staff-Filter beziehungsweise aktuell erlaubte Spielerziele. Zielansicht Kopf 4, TELEPORT 10, HISTORY 11, WARN 12, MUTE 14, KICK 15, BACK 18, CLOSE 26. Slot 22 ist in beiden Ansichten leer/ungebunden. [StaffPanel.java:37](../../src/main/java/dev/vapee/core/staff/StaffPanel.java#L37).

**Status/Interaktion:** keine PUBLIC/HIDDEN-Map, kein Status-Toggle und keine entsprechende Kopf-Lore. Spielername behält RankInfo-Farbe, Köpfe nutzen vorhandene Profile/Cache; Lore enthält Rang und Aktionshinweis. Externes `canSee` gilt weiterhin. Aktionen ohne Rechte lassen feste Slots leer. Teleport und History verwenden typgeprüfte eigene Commandexecutor mit dem echten Player; Warn/Mute/Kick schlagen UUID-Commands vor und vollziehen keine Sanktion per Klick. [StaffPanel.java:181](../../src/main/java/dev/vapee/core/staff/StaffPanel.java#L181), `handle` ab Zeile 276.

**Navigation/Lifecycle:** nur einfacher linker Klick; deduplizierter Callback am nächsten Tick prüft Session, Content-Revision, Inventarbesitz, aktuelle Permissions und Zielhierarchie erneut. BACK erhält Listenmodus und Seite. Locale, Join/Quit und Weltwechsel können passende Ansichten aktualisieren; Refresh ist vorhanden. Keine Behauptung, dass jeder externe LP-Metadatenwechsel sofort ohne weiteres Ereignis repaintet. Modul-Disable schließt nur weiter eigene Ansichten. Texte `staff.*`, `common.*`, Hierarchie aus `staff.hierarchy.protected-groups`; keine neue Panel-Config.

**Tests/Auffälligkeit:** `StaffPanelHarness`, `StaffPanelLifecycleHarness`, `StaffRosterRemovalHarness`, `StaffHierarchyServiceHarness`, `CommandVisibilityHarness`. Sehr starke Autoritäts-/Stale-Absicherung. Steuertexte verwenden überwiegend den vorhandenen AQUA-Fallback, sodass BARRIER nicht wie im Settings-Menü rot beschriftet ist. Die abweichenden Close-Slots 24/26 sind durch unterschiedliche Ansichten begründet; nicht pauschal verschieben.

### 3.10 DailyQuests – Fortschritt und Reset

**Eintritt/Zustand:** `/quests`, `vapeecore.quest.use`, geladenes Player-Profil und verfügbare DailyQuest-Runtime. Vor Neuöffnung erfolgt Daily-Sync. DISABLED/NO_DEFINITIONS/fehlendes Profil/Assignment-Fehler geben gezielte Meldungen statt scheinbar funktionierender leerer Oberfläche. Bei blockierter Rotation wegen Pending-Reward gibt es Hinweis und vorhandenen Queststand. Eine formal leere aktive Liste besitzt zusätzlich einen eigenen Empty-Renderzustand. [DailyQuestMenu.java:165](../../src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestMenu.java#L165).

**Layout:** 36 Slots, pro Seite maximal fünf Quests; 1: `[13]`, 2: `[11,15]`, 3: `[11,13,15]`, 4: `[10,12,14,16]`, 5: `[9,11,13,15,17]`. EMPTY 22, PREVIOUS 27, PAGE_INFO 31, NEXT 33, RESET_INFO 34, CLOSE 35. REFRESH ist als Action vorhanden, im Standardlayout nicht platziert. [DailyQuestMenu.java:49](../../src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestMenu.java#L49).

**Items/Aktionen:** PAPERS für aktiv, GOLD_INGOT für ausstehende Belohnung, LIME_DYE plus Glint für abgeschlossen. Lore beschreibt Fortschritt, Ziel, Coin-Belohnung und aktuellen Status. Questklick löst **keinen Claim** aus; Belohnung ist automatisch und bei Pending erneut zustellbar. RESET_INFO ist Anzeige, kein Reset-Knopf. Pagination versteckt unnötige Controls und klemmt nach Datenänderungen. [DailyQuestMenu.java:297](../../src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestMenu.java#L297), [DailyQuestMenuListener.java:15](../../src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestMenuListener.java#L15).

**Config/Sprache/Updates:** `daily-quests.yml` `enabled`, `quests-per-day`, `reset.time`, `reset.timezone`, Questkatalog/LocalizedText; `daily-quests.*`, `common.*`. Quest-Commit-/Locale-Observer aktualisieren dieselbe Ansicht. Bereits vorhandener Task alle 20 Ticks aktualisiert **nur den Reset-Clock-Inhalt**, nur solange eigene Ansichten aktiv sind; unveränderte Specs vermeiden Item-Writes. Er synchronisiert nicht jede Sekunde sämtliche Quests neu. Activation-Tokens und Cleanup-Retry verhindern alte Callbacks nach Shutdown/Re-enable. [DailyQuestMenu.java:411](../../src/main/java/dev/vapee/core/quest/daily/menu/DailyQuestMenu.java#L411).

**Tests/Auffälligkeit:** `QuestMenuHarness`, `QuestMenuSecurityHarness`, `QuestLayoutHarness`, `DailyQuestMenuLifecycleHarness`, `DailyQuestCountdownHarness`. Rechter/Linker Klick nur auf Navigation; kein Drag. Bei Rechteentzug blockieren `canUse` und Registry-Prüfung die Aktionen; anders als Settings schließt der Clickpfad nicht unmittelbar mit eigener Denial-Meldung. Countdown kann die Active-Registrierung verwerfen, ohne die sichtbare passive Ansicht zu schließen. Ein verständlicherer Abschluss dieses Falls ist eine kleine UX-Maßnahme, kein Beleg für einen Reward-Bypass.

### 3.11 Invsee – unveränderlicher Inventar-Snapshot

**Eintritt/Rechte:** `/invsee <player>`, `vapeecore.utility.invsee`, exaktes Online-Ziel und bestehender `OnlineStaffTargetGuard`. `vapeecore.utility.invsee.modify` ist reserviert/inaktiv; dieses Menü hat keine Editierfunktion. Autorisierung findet am Command statt, nicht nochmals im rendernden `openSnapshot`. [InvseeCommand.java:90](../../src/main/java/dev/vapee/core/utility/command/InvseeCommand.java#L90).

**Layout/Daten:** 54 Slots, eigene Konstanten statt `MenuLayout`: geklonte Hotbar 0–8, Main 9–35, Rüstung 45–48, Offhand 50. Übrige Slots leer. Kein Back/Close/Refresh-Button, keine Pagination, kein Empty-Placeholder; ein leerer Slot zeigt fachlich ein leeres Zielfach. Original-Itemnamen/Lore/Metadaten bleiben durch Klonen erhalten. Titel wird lokalisiert mit literalem Zielnamen; Begleitnachricht sagt ausdrücklich „read-only snapshot“. [InvseeService.java:35](../../src/main/java/dev/vapee/core/utility/InvseeService.java#L35), `openSnapshot` ab Zeile 77 und `copySnapshot` ab 171.

**Interaktion/Lifecycle:** alle Click-/Drag-/Swap-/Drop-Manipulationen sind gesperrt, einschließlich Bottom-Inventory-Routen. Holder bindet Viewer-UUID, Ziel-UUID und Inventory; ActiveView ist eigene Runtime-Registry. Close, Viewer-Quit, Target-Quit und Disable beenden Views. Snapshot aktualisiert sich bewusst nicht bei Änderungen des Zielinventars; erneutes `/invsee` nimmt neu auf. Ein nachträglicher Rechteentzug entfernt bereits dargestellte Snapshotdaten nicht sofort, eröffnet jedoch keinen neuen Lese-/Schreibpfad durch Klicks. Keine zusätzliche Invsee-Config.

**Tests/UX:** `UtilityInventoryHarness`, `Phase04SecurityAuditHarness`, Utility-/Hierarchy-Tests. Snapshot statt Live-Ansicht ist eine bewusste Grenze. Eine optional deutlichere Anzeige im Menü ist P3; keine Empfehlung für Live-Editing oder eine Aktivierung des reservierten Modify-Nodes.

### 3.12 Zusätzlich geprüft: natives EnderChest-Inventar

`/enderchest [player]` öffnet über `UtilityService.openEnderChest` das **echte** 27-Slot-EnderChest-Inventar. Es ist beschreibbar und kein weiterer `UiItems`-/`MenuLayout`-Screen. Selbstzugriff verlangt `vapeecore.utility.enderchest`, Fremdzugriff zusätzlich die bestehende `.others`-Berechtigung, aktuelles Online-Ziel und Target-Hierarchie. Keine eigene Pagination/Leerzustandskachel/Farbpalette; Itemnamen und Lore sind echte gespeicherte Items.

Fremdzugriffe besitzen eine View-Lease: bei Interaktion werden aktuelle identische Viewer-/Owner-Sessions, Permission und Hierarchie erneut geprüft. Widerruf blockiert alle Slots/Routen sofort, schließt dedupliziert am nächsten Tick und bleibt bis Close gesperrt. Quit/Disable geben die Lease frei. [EnderChestCommand.java:154](../../src/main/java/dev/vapee/core/utility/command/EnderChestCommand.java#L154), [UtilityService.java:145](../../src/main/java/dev/vapee/core/utility/UtilityService.java#L145). `EnderChestRevocationHarness` und `UtilityInventoryHarness` decken diese Grenze ab. Diese Oberfläche darf bei einer visuellen GUI-Angleichung nicht versehentlich zu einem dekorierten oder read-only Menü werden.

## 4. Vergleich ohne erfundene Punktbewertungen

| System | Navigation | Konsistenz | Verständlichkeit | Interaktionen | Leerzustände | Lokalisierung | Vorhandene Testabdeckung | Wartbarkeit |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PlayerMenu | Drei Ziele, Close; Friends ohne Rückweg | Kompakter Sonderfall | Wenige klare Kacheln | LEFT/RIGHT öffnet | Rechteabhängig fehlende Ziele | Katalog; Locale-Items | Hub/Binding/Permissions | Kleine bestehende Klasse ausreichend |
| Settings | Back/Close | Gute Icon-/Statuspaare | Klarer Ist-Zustand | Reversible Toggles | Profilfehler statt leeres Formular | Katalog; PM-Key gekoppelt | Behavior/Security/Layout/Updates | Entries und Layout getrennt |
| VisibilitySettings | Hub-Back, separate Spielerliste | Wie Settings | Filterwirkung und gespeicherter Status unterscheiden | Toggles; zukünftige Option erklärt | Fester Screen | Katalog, nachvollziehbare Modusbegriffe | Security/States/Observer | Bestehende Definitionen ausreichend |
| VisiblePlayers | Back vorhanden; Chat-Eingabe unterbricht | Standard-54er-Liste | Volle UUID kann Lore verlängern | RIGHT entfernt, ADD schlägt vor | Kachel plus Einstieg | Katalog/literale Namen | Heads/Pagination/Security | Holder-/Refresh-Muster wiederverwendbar |
| Friends | Tabs, Close; kein Hub-Back | Standard-54er-Liste | Gesten in Lore, unterschiedliche Klickarten | Accept/Deny/Cancel/SHIFT_REMOVE | Alle drei Zustände erklärt | Katalog, graue Status-Lore | Relationship/Heads/Security/Refresh | Geometrie und Services getrennt |
| Clan | Tabs, Close, Chat-Prompts | Gleiche Grundliste; alle Textrollen AQUA | Transfer-Geste hat starke Wirkung | Direkter Transfer/Kick; Disband bestätigt | Clanlos/Einladungen sinnvoll | Katalog/literale Clan-Namen | Ownership/Security/Lifecycle | Profil-Dekoration fehlt; kein neuer Cache nötig |
| Navigator | Breadcrumb erhält Seite; optionales Back | Operator-Geometrie bewusst variabel | Empty-Back weniger auffindbar | MENU/WARP/UNAVAILABLE | Leeres Menü wird erklärt | Katalog plus LocalizedText | Config/Security/Compact/Region | Separate Config-Geometrie ist sinnvoll |
| Editor | Viele Dialoge; Picker zurück zum Haupteditor | 27/54, unterschiedliche Zurück-Slots | Rohtexte, leere Picker und Slotbanken | Draft/Save/Preview sauber getrennt | Picker überwiegend nur Steuerzeile | Teilweise inline EN/DE, teils Englisch | Editor/Store/Region/Revision | Große Featureklasse; kleine Helfer statt Framework |
| Staff | Listenseite bleibt beim Ziel-Back erhalten | Feste berechtigungsabhängige Plätze | Ohne Status-Toggle kompakter | Defer/revalidate; Sanktionen nur vorgeschlagen | Eigene leere Liste | Katalog plus Rang-Components | Besonders gründliche Session-/Ownership-Kontrollen | Grenzen erhalten, kein Umbau nötig |
| DailyQuests | Seiten/Close; kein Parent erforderlich | Adaptive kleine Questreihe | Status/Auto-Reward/Countdown getrennt | Nur Navigation, kein Claim | Sync-Fehler plus echter Empty-Render | Katalog plus Definitionstexte | Quest/Countdown/Lifecycle/Permissions | Bestehende Timer-/Observer-Grenze gut |
| Invsee | ESC/erneuter Command | Zweckbedingt andere Geometrie | Snapshot-Hinweis im Chat | Vollständig read-only | Leere Zielslots bleiben leer | Titel/Feedback lokalisiert, echte Itemtexte | Snapshot/Manipulation/Quit/Disable | Kein generischer Menu-Controller nötig |

## 5. Gemeinsame UI-Architektur: Stärken und tatsächliche Unterschiede

| Baustein | Bestehender Nutzen | Grenze / Folgerung |
| --- | --- | --- |
| [MenuLayout.java:9](../../src/main/java/dev/vapee/core/ui/MenuLayout.java#L9) | Prüft Größe, doppelte Slots, Content-/Control-/Empty-Kollisionen; optionale Actions und Statusplatz; explizite Content-Reihenfolge. | Geometrie, keine Autorisierung oder Navigation-Historie. Nicht in eine Permission-/Session-Engine umbauen. |
| [UiItemSpec.java:10](../../src/main/java/dev/vapee/core/ui/UiItemSpec.java#L10) und [UiItems.java:16](../../src/main/java/dev/vapee/core/ui/UiItems.java#L16) | Unveränderliche Darstellung, Literal-Components, Fallback-Farben, nicht kursiv, gemeinsame Material-/Meta-Erzeugung. | Nicht jede Feature-Farbe muss dem Chat-Akzent entsprechen. Kleine gemeinsame Standard-Control-/Status-Specs wären möglich; kein zweiter Renderer. |
| [Pagination.java:11](../../src/main/java/dev/vapee/core/ui/Pagination.java#L11) | Geklemmte Seite und sichere halb offene Bereiche; auch bei schrumpfenden Listen. | Editor-Draft-Seiten 0–999 sind freie Autorenseiten und nicht dieselbe Pagination. Nicht zwangsweise vereinheitlichen. |
| [CachedPlayerHeads.java:32](../../src/main/java/dev/vapee/core/ui/CachedPlayerHeads.java#L32) | UUID-Tag, Online-Profil/Paper-Cache, Texture-Fallback ohne eigene Downloads. | Item-UUID ist Darstellung, kein Klickrecht. Für personenbezogene Clan-Einträge direkt wiederverwenden; keine permanente zweite Skin-Cache-Schicht. |
| Holder/aktive Registry | Fachliche Slot-UUIDs und nur tatsächlich gerenderte Aktionen statt Material-/Titelvergleich. | Gemeinsames Muster, aber Staff bindet zusätzlich exakte Player-Sitzung/Revision, Navigator Route und Editor Draft. Diese Zusatzgrenzen nicht vereinheitlichend verlieren. |
| Commit-Observer | Settings/Friend/Clan/Quest/Warp veröffentlichen tatsächliche Änderungen; Menüs malen betroffene aktive Views neu. | Erfolgloser Save darf nicht wie Erfolg repainten; keine zusätzliche globale Polling-Schleife. |

**Tatsächliche Duplication:** Owner-/Top-Inventory-/Active-Prüfungen, Settings/Friend/Clan/VisiblePlayers-Refresh und einfache ARROW/BARRIER/CLOCK/PAPER-Specs wiederholen sich. Das sind Kandidaten für wenige konservative Hilfsmethoden nach eigenständigen Tests. Ein generischer Basisklassen-Controller würde derzeit verschiedene Sicherheits- und Lifecycle-Verträge verschlucken. Keine pauschale Extraktion empfohlen.

**Bewusste Featureunterschiede:** Staff nur LEFT und aufgeschobene Aktion; Friends spezielle Relationship-Gesten; EnderChest echtes Storage; Invsee Snapshot; DailyQuest-Timer nur Reset-Anzeige; Navigator exakte Operator-Slots; Editor eigene Session-/Draft-Revision. Unterschiedliche Größen, Close-Slots und Filler-freie Flächen sind deshalb allein noch keine Fehler.

## 6. Navigation, Aktualisierung und Grenzfälle

### Rückwege und Kontext

- PlayerMenu → Settings/Visibility → PlayerMenu ist verdrahtet; VisiblePlayers → Visibility ebenfalls. Friends besitzt nur Close. Diese Asymmetrie ist Q, nicht bloß ein optischer Eindruck.
- Friend-/Clan-Tabwechsel beginnt auf Seite 0, Commit-Refresh erhält aktuelle Ansicht/geklemmt gültige Seite. Keine separate Seite pro Tab gespeichert.
- Staff-Zielansicht und Navigator-Untermenüs erhalten ihren relevanten Elternkontext. Editor-Picker dagegen springen über `pickerButtons` zum Haupteditor; sein `↩` sagt nicht aus, dass ein Dialog übersprungen wird.
- Add-/Create-/Rename-/Lore-Prompts schließen die GUI und suggerieren einen Command. Das ist eine bestehende, sichere Texteingabeform. Es gibt keinen automatischen Rücksprung nach Texteingabe und kein allgemeines Chat-Capture.
- ESC/Close im Editor lässt den Draft leben, Discard verwirft ihn. Ohne Rückfrage kann Discard umfangreiche ungespeicherte Arbeit verlieren; erst anhand Clienttest und gewünschtem Betreibervertrag über eine kleine Bestätigung entscheiden.

### Aktualisierungsmatrix

| System | Auslöser | Kein belegter zusätzlicher Automatismus |
| --- | --- | --- |
| PlayerMenu | Öffnen, Locale | Keine allgemeine LP-Menü-Polling-Schleife |
| Settings / Visibility | Settings-Service-Commit, Locale, Öffnen | Optionaler REFRESH ist im Defaultlayout nicht sichtbar |
| VisiblePlayers | Settings-Commit, relevante Join/Quit, Locale | Chat-Add öffnet die vorherige Seite nicht automatisch |
| Friends / Clan | Beziehungs-/Clan-Commit, relevante Presence, Locale | Keine unabhängige Online-/Rang-Cache-Schicht |
| Navigator | Warp-Commit, Locale, Neuöffnung, Editor-Apply | Keine PlayerMove-Aktualisierung der Regionszählung belegt |
| Editor | Neue Dialoge, Command-/Draft-Änderung, Revision | Kein eigener Locale-Rerender-Observer |
| Staff | Locale, Join/Quit, Welt, explizites Refresh/Neuöffnung | Keine behauptete sofortige LP-Metadaten-Repaint-Garantie |
| DailyQuests | Quest-Commit/Locale; existierender Reset-Task alle 20 Ticks | Kein sekündliches vollständiges Quest-Sync |
| Invsee | Neuaufnahme bei Command, sonst Close/Quit/Disable | Absichtlich kein Live-Inventarspiegel |

**Titelgrenze:** Viele Locale-Observer rendern Inhalte im selben Inventory. Titel werden nur bei dessen Erzeugung lokalisiert. Ein neues sprachliches Itemset ist daher kein Beleg für einen aktualisierten Inventartitel. Titelwechsel im Client ist V und soll gezielt geprüft werden; nicht allein für diesen kosmetischen Fall pauschal alle Inventare neu öffnen.

### Leere und nicht verfügbare Zustände

Freunde und beide Anfragelisten haben eigene Empty-Texte; Clanlosigkeit bietet konkrete Folgeschritte. Sichtbare Spieler bieten Add; Staff erklärt fehlende Einträge; Navigator zeigt leeres Menü. DailyQuests unterscheidet deaktivierte/fehlende Konfiguration vom vorhandenen Empty-Renderzustand. Editor-Picker mit null Warps oder Menüs zeigen dagegen überwiegend nur Navigation. Invsee-Leerslots sind Inhalte des Snapshots und dürfen nicht mit dekorativen Empty-Items überdeckt werden.

## 7. Item-Design, Farbe und EN/DE

**Nachgewiesene Darstellungsunterschiede:**

- VisiblePlayers färbt Online grün, Offline grau. Friend-/Clan-Lore fällt über `UiItems.literal` bei unformatierten `common.online/offline` auf Grau zurück. Staff-Spielernamen benutzen RankInfo-Farben; Friend/Clan/VisiblePlayers-Namen sind überwiegend fest AQUA. Das beschreibt verschiedene vorhandene Verträge und rechtfertigt keine globale Rang-Neufärbung.
- Settings-/Visibility-Close ist rot, PlayerMenu-Close grau; Clan/Staff/DailyQuest-Controltexte überwiegend AQUA, selbst bei BARRIER/RED_DYE. Material vermittelt dort zusätzlich Bedeutung. Ob Farbe und Material im Client ausreichend klar sind, ist V.
- GUI-AQUA ist ein direkter Featurewert und folgt nicht generell `messages.accent-color`. Das Chat-Designsystem aus 06B–06C.1 ist kein Beleg für eine bereits vollzogene GUI-Theme-Migration. Akzent-Reload, Friends-Gold, Clan-Violett und Rangfarben dürfen nicht blind überschrieben werden.
- Freie Flächen sind oft absichtlich ohne Filler; insbesondere Navigator und Editor hängen vom freien Slot ab. Eine globale Glasumrandung könnte Entries verdrängen oder „Rechtsklick leer = zurück“ zerstören.
- UUID-Lore bei bekannten VisiblePlayers ist technisch korrekt, aber lang. Keine Behauptung eines gemessenen Client-Umbruchs; bekanntes Namenslabel und UUID-Fallback können später schlanker gestaltet werden.

**Sprachen:** `common.back/close/previous-page/next-page`, Empty- und Klickhinweise haben EN-/DE-Pendants; Sichtbarkeits-Back-Lore nennt korrekt das Spielermenü. Beispiele wie `friends.remove-hint` und `clan.transfer-hint` erklären die Modifier-Klicks in beiden Sprachen. Sie beseitigen jedoch keine versehentliche Bestätigung starker Aktionen. Editor-Rohtexte umgehen die Kataloge und deren Operator-Overrides. Die technische Kopplung der PM-Hint-Zeile ist aktuell textgleich, aber für Overrides relevant. Quellen: [en.yml:41](../../src/main/resources/lang/en.yml#L41), [de.yml:365](../../src/main/resources/lang/de.yml#L365), [SettingsMenuEntries.java:34](../../src/main/java/dev/vapee/core/settings/SettingsMenuEntries.java#L34).

**Platzhalter/Literalität:** vorhandene Renderpfade übergeben UUIDs, Namen, Clanname/Tag, Questwerte und Zähler als Components beziehungsweise `LocalizedText`; diese Daten werden nicht als beliebige MiniMessage-Quelle interpretiert. UiItems setzt Fallback-Farbe und deaktiviert Kursiv, ohne einen schon ausdrücklich formatierten Component pauschal umzuschreiben. Bestehende Katalog-Paritäts-/Usage-Harnesses sichern Keys und Platzhalter ab. Operator-Texte können bewusst vom Standard abweichen; dieser Audit bewertet den gebündelten Standard und überschreibt keinen Override.

## 8. Sicherheits- und Lifecycle-Bewertung

| Grenze | Befund | Einordnung |
| --- | --- | --- |
| Titel/Material als Autorität | Holder und aktive Inventaridentität sind maßgeblich; Slot-Ziele kommen aus fachlichem Snapshot. | Gute bestehende Grenze; gefälschte Itemnamen/Profile verleihen keine Action. |
| Permission-Änderung | Menüs prüfen beim Handeln erneut; Staff zusätzlich vor Callback/Executor. Clan- und Invsee-Renderer verlassen sich beim Öffnen auf ihre Command-Grenze. | Bestehende Architektur ausdrücklich unterscheiden; neue Einstiegspunkte benötigen eigenen Schutz. Kein aktueller Bypass nachgewiesen. |
| Rollen/Targets | ClanService verlangt aktuellen Owner und aktuelles Mitglied; Staff und Utility ihre Hierarchie-/Session-Grenzen. | Direkter Clan-Transfer ist ein UX-Risiko bei gültiger Autorität, keine festgestellte Privilegieneskalation. |
| Datenänderung/Commit | Observer übernehmen erfolgreiche Service-Commits; Seiten und aktuelle Slot-Zuordnung werden neu aufgebaut. | Nicht durch optimistische Itemverschiebung oder zweite Daten-Caches ersetzen. |
| Doppelklick | Staff dedupliziert wartende Aktionen; andere Menüs verarbeiten erlaubte Klicks synchron und verlassen sich auf aktuelle Domainzustände. | Ein schneller zweiter Settings-Klick kann bewusst zurückschalten. Keine pauschale Debounce-Schicht ohne konkrete Fehlermessung. |
| Stale/Close/Quit | Bound Inventory, Active-Registry und Close/Quit-Cleanup; Staff/Editor stärkere Revision-/Session-Guards. | Vorhandene Security-/Lifecycle-Harnesses weiter erhalten. Keine beobachtete Manipulation durch den Audit. |
| Bereits gecanceltes Event | Staff erfasst den ursprünglichen Cancel-Zustand; mehrere ältere Listener setzen Cancel und führen ihre eigene erlaubte UI-Action trotzdem aus. | Möglicher Integrations-/UX-Konflikt mit Fremdplugins. Native Gegenprobe nötig; hier weder Exploit noch allgemeiner Fehler bewiesen. |
| Inventory-Wechsel im Click | Viele ältere Menüs öffnen synchron, Staff verschiebt Aktionen. | Unterschied Q; sichtbare Desynchronisation/Packetfolge nicht bewiesen. Mit verbundenem Client testen, gegebenenfalls das vorhandene Staff-Muster eng übernehmen. |
| Reload/Disable | Modulbesitz, Observer-Abmeldung und owned-view Cleanup sind vorhanden; DailyQuest schützt Aktivierungen, Editor besitzt Base-Konfliktprüfung. | Nicht alle Module sind ReloadService-Teilnehmer. „Reload lädt jede GUI-Konfiguration neu“ wäre falsch. |
| Passive Invsee-/Questansicht nach Rechteverlust | Kein fortgesetzter mutierender Klickpfad; Ansicht kann bis Close bestehen bleiben. | P2/P3-UX-Abschluss prüfen, keine Behauptung von zusätzlichem Datenzugriff oder Reward-Claim. |

## 9. Priorisierte Maßnahmen

Die Prioritäten sind Auftragskategorien, keine CVSS-/Exploitbewertung. **P0: kein belegter Befund.** P1 betrifft hohen UX-Nutzen; P2 visuelle Konsistenz; P3 optionale Pflege. Aufwand und Risiko sind qualitative Schätzungen, keine Zeitversprechen.

### M1 – Clan-Transfer ausdrücklich bestätigen

- **Ist/Problem (Q):** SHIFT_LEFT auf Mitglied ruft sofort `transferOwnership` auf; im Gegensatz zu Disband keine zweite Bestätigung. Ein gültiger Owner kann dadurch versehentlich seine Autorität abgeben.
- **Vorschlag/Nutzen:** kleine zweistufige Bestätigung mit explizitem Zielnamen; erst bestätigen, dann aktuellen Owner, Clan, Mitglied und Permission erneut prüfen. Bestehenden Service unverändert verwenden. Keine pauschalen Bestätigungen für gewöhnliche Toggles.
- **Priorität/Aufwand/Risiko:** P1 / mittel / mittel, weil Klickvertrag und veraltete Bestätigungen betroffen sind.
- **Dateien:** `ClanMenuListener.java`, `ClanMenu.java`, `ClanMenuHolder.java`, betroffene `lang/en.yml`/`de.yml`; Service nur als vorhandene Autorität.
- **Erforderliche Tests:** Cancel/Confirm, gezielte UUID, Rollen-/Clan-/Target-Wechsel zwischen Schritten, Disconnect, Reload/Disable, Doppelklick, Owner-only, Save-Fehler; bisherige Shift-Security-Tests erhalten. Native und echte Clientprobe der Bestätigung.

### M2 – Rückwege statt pauschal gleicher Slots

- **Ist/Problem (Q):** Friends ist vom Hub erreichbar, kennt aber keinen BACK. Editor-Picker `↩` springen teils am vorherigen Dialog vorbei zum Haupteditor; die Beschriftung benennt das Ziel nicht.
- **Vorschlag/Nutzen:** kleiner, vom aufrufenden Feature gesetzter Back-Opener für den Hub-Kontext und expliziter Rücksprung-Consumer für einzelne Editor-Picker. Bei direktem `/friend` nicht eine fiktive Elternseite erfinden. Symbole mit lokalisiertem Zielnamen/Lore ergänzen. Kein globaler Menü-Stack.
- **Priorität/Aufwand/Risiko:** P1 / mittel / mittel, weil Session-/Lifecycle-Ownership und bestehende Slots betroffen sind.
- **Dateien:** `FriendMenu.java`, `FriendMenuListener.java`, `FriendMenuHolder.java`, `LobbyExperienceModule.java`, `NavigatorEditor.java`, EN/DE.
- **Erforderliche Tests:** Hub-/Command-Einstieg getrennt, Parent nicht verfügbar, Permission-/Weltwechsel, stale Back-Callback nach Disable, Seitenerhalt, Slotkollisionen, unabhängige Picker-Routen. Clientprobe Back gegenüber Close.

### M3 – Editor-Einstieg, Empty-Picker und Sprache vervollständigen

- **Ist/Problem (Q/V):** leerer Slot ist eine Action ohne sichtbares Item; leere Warp-/Menü-Picker haben keinen fachlichen Empty-Hinweis. `Page`, `Type`, `Target`, `Navigable`, rohe Booleans und `↩` umgehen die EN-/DE-Kataloge; technische 0-basierte Autorenpage ist nicht dasselbe wie eine spielerseitige Seite.
- **Vorschlag/Nutzen:** vorhandene Info-Kachel mit „Freien Platz anklicken“ ergänzen, spezifische Empty-Texte und Rückweg anbieten, echte Systemtexte konservativ ins bestehende LocalizationService übernehmen, Ja/Nein beziehungsweise erklärten Stack-Modus anzeigen. IDs/Commandtokens und numerische Autorenpage unverändert lassen oder einen klar getrennten Anzeigetext verwenden. Keine Filler auf freie Arbeitsplätze legen.
- **Priorität/Aufwand/Risiko:** P1 / mittel / klein bis mittel; Sprach-Key-/Override- und Klickzuordnungen testen.
- **Dateien:** `NavigatorEditor.java`, `lang/en.yml`, `lang/de.yml`; vorhandene Editor-Harnesses.
- **Erforderliche Tests:** DE/EN, Literalnamen mit Formatzeichen, Operator-Override, null Warps, keine gültigen Ziele, erster/neuer Draft, Slotbank, Preview/Save/Pending-Region, technische IDs unverändert. Visuelle Prüfung bei 27/54 Zielslots.

### M4 – Personengebundene Clan-Köpfe vervollständigen

- **Ist/Problem (Q):** Mitglieder und ausgehende Spielereinladungen haben PLAYER_HEAD ohne Player-Profil; andere Personenlisten benutzen bereits echte Köpfe.
- **Vorschlag/Nutzen:** `CachedPlayerHeads` auf frischen personenbezogenen Items mit autoritativer Entry-UUID anwenden. Aggregatkopf der Mitgliederanzahl bleibt generisch; Clan-Einladungen bleiben PAPER. Kein Downloader, persistenter Cache oder neuer Async-Rückkanal.
- **Priorität/Aufwand/Risiko:** P2 / klein / klein, rein kosmetisch bei unveränderter Slot-Autorität.
- **Dateien:** `ClanMenu.java`, passende vorhandene Clan-GUI-/Head-Harnesses; bestehendes `CachedPlayerHeads` wiederverwenden.
- **Erforderliche Tests:** echte UUID/SkullMeta/Profile, Online/Cache/missing/falsche Cache-UUID, Mitglieder/outgoing getrennt, Aggregatsymbol unverändert, Seiten-/Holder-Sicherheit und keine Mainthread-Netzwerkoperation. Client-Skin-Fallback prüfen.

### M5 – Kleine semantische GUI-Rollen statt globaler Farbtausch

- **Ist/Problem (Q/V):** Friends/Clan zeigen Online und Offline beide grau; BARRIER-Beschriftungen und ausgewählte/gefährliche Aktionen wechseln ohne gemeinsame GUI-Regel zwischen Rot, Grau und AQUA.
- **Vorschlag/Nutzen:** zunächst Online-/Offline-Lore und wenige Close/Back-/Gefahrenspecs mit vorhandener Status-/Semantikpalette angleichen; optional kleine stateless Spec-Helfer neben UiItems. Operator- und vorhandene innere Componentfarben behalten. Größe und feste Feature-Slots unverändert lassen.
- **Priorität/Aufwand/Risiko:** P2 / klein bis mittel / klein; gewünschte Farbidentität vor Umsetzung im Client abstimmen.
- **Dateien:** `FriendMenu.java`, `ClanMenu.java`, gegebenenfalls `StaffPanel.java`, `DailyQuestMenu.java`, `UiItems.java` und zentrale vorhandene Farbdefinitionen.
- **Erforderliche Tests:** Online/Offline-Farbspannen, Namen-/Rangfarben unverändert, EN/DE, eigener Override, semantisches Rot/Grün nicht durch Akzent ersetzen, `messages.accent-color`-Vertrag unangetastet. Client-Kontrast und Glint prüfen.

### M6 – Chat-Handoff erklären, keinen zweiten Eingabemechanismus schaffen

- **Ist/Problem (Q):** Friend/VisiblePlayers Add und Editor-Texteingaben schließen; Erfolg führt nicht automatisch zur bisherigen GUI-Seite zurück. Clan Create verwendet einen vorbereiteten Beispielcommand.
- **Vorschlag/Nutzen:** zuerst lokalisierten Rückkehrhinweis und gegebenenfalls zusätzliche bestehende Command-Suggestion zum Wiederöffnen anbieten. Automatisches Wiederöffnen nur als getrennte Betreiberentscheidung und nur mit begrenztem, sessiongebundenem Kontext; nie in fremde mittlerweile geöffnete Inventare eingreifen.
- **Priorität/Aufwand/Risiko:** P1 / klein für Erklärung, mittel für Rückkehr / mittel bei automatischer Navigation.
- **Dateien:** `FriendMenu.promptAdd`, `VisiblePlayersMenu.promptAdd`, `ClanMenu.promptCreate`, `NavigatorEditor.prompt`, EN/DE; Commandpfade erst bei ausdrücklich gewünschtem Rückkehrvertrag.
- **Erforderliche Tests:** SUGGEST statt RUN, nur eigene Session, Abbruch/Fehler/erfolgreicher Commit, neue fremde Ansicht, Logout/Reconnect, alte Seite verschwunden. Keine neue persistente Einstellung und kein globales Chat-Capture.

### M7 – Inaktive Filter, lange Lore und Rechteverlust klarer abschließen

- **Ist/Problem (Q/V):** gespeicherte Visibility-Unterfilter können in ALL/HIDDEN weiter „Enabled“ zeigen; die Lore erklärt bereits ihre bedingte Wirkung. VisiblePlayers zeigt stets UUID. DailyQuest blockiert entzogene Rechte, kann aber eine passive nicht mehr aktive Ansicht stehen lassen.
- **Vorschlag/Nutzen:** gespeicherten Status plus „wirksam nur in FILTERED“ optisch klar trennen; bekannte Namen nicht standardmäßig mit voller UUID überladen, UUID-Fallback für unbekannt beibehalten. Bei Quest-Rechteverlust nur noch eigenes Inventory verständlich schließen; keine Änderung am Rewardpfad.
- **Priorität/Aufwand/Risiko:** P2 / klein bis mittel / klein, sofern ausschließlich Darstellung/owned-close.
- **Dateien:** `VisibilitySettingsMenu.java`, `VisiblePlayersMenu.java`, `DailyQuestMenuListener.java`, `DailyQuestMenu.java`, EN/DE.
- **Erforderliche Tests:** alle drei Modi bei gleichbleibenden gespeicherten Filtern, unbekannte Namen, Permission-Verlust mit offenem und ersetzt fremdem Inventar, Countdown-Task-Cleanup und unveränderte automatische Rewards. Tooltip-Umbruch ist Clienttest.

### M8 – Integrationsunterschiede erst beweisen, dann eng vereinheitlichen

- **Ist/Problem (Q, Auswirkung offen):** synchrone Inventory-Übergänge und Behandlung vorher gecancelter Events unterscheiden sich von Staff. Das allein beweist keine Desynchronisation oder Privilegieneskalation.
- **Vorschlag/Nutzen:** isolierte Gegenprobe mit Fremdlistener und verbundenem Client; nur bei belegtem Konflikt jeweilige Transition analog bestehendem dedupliziertem Staff-/EnderChest-Callback absichern. Keine gemeinsame Scheduler-/Polling-Schicht vorsorglich einführen.
- **Priorität/Aufwand/Risiko:** P3 / mittel für Gegenprobe / mittel bei späterem Lifecycle-Eingriff.
- **Dateien:** nur tatsächlich betroffene Listener; Vorlagen `StaffPanel.defer` und `EnderChestCommand.revoke`.
- **Erforderliche Tests:** bereits gecancelter Click, Creative/Drag/Offhand, schnelle Seitenwechsel, Close-Hook, Permissionentzug im nächsten Tick, neuer Holder und neue Player-Session. Kein Test gegen Operator-Daten.

### M9 – Snapshot-/Titel-/Zählergrenzen sichtbar verifizieren

- **Ist/Problem (Q/V):** Invsee ist eine Momentaufnahme; Navigator zählt Regionen beim Rendern; Locale-Updates im selben Inventory aktualisieren nicht im Code dessen Titel. Es gibt keinen neuen Beleg für falsche Counts oder falsche Berechtigungen.
- **Vorschlag/Nutzen:** Clientprobe für Erwartungen und Titelwechsel. Bei Bedarf kleine erklärende Texte oder eng begrenzter Title-Refresh; keine Live-Inventare und kein neuer Regionszähler-Pollingdienst als Standardempfehlung.
- **Priorität/Aufwand/Risiko:** P3 / klein für Texte, mittel für Title-Transport / klein bis mittel.
- **Dateien:** nur bei belegtem Bedarf `InvseeService`, `NavigatorMenu`, betroffene Locale-Rerenderpfade und EN/DE.
- **Erforderliche Tests:** Snapshot bleibt geklont, erneut Öffnen nimmt neuen Stand; Regionswechsel/Renderereignisse, Untermenü-/Parent-Erhalt; Sprachwechsel ohne ungewollte Action oder Cursor-Verlust. V vor Umsetzung.

### M10 – Bestehenden PM-Lore-Key entkoppeln

- **Ist/Problem (Q):** PM-Icon benutzt `settings.friend-requests.hint-one`; beide gebündelten Texte sind aktuell identisch. Ein Operator kann jedoch nur den Freundes-Key ändern und damit unerwartet PM-Lore beeinflussen.
- **Vorschlag/Nutzen:** bereits vorhandenen `settings.private-messages.hint-one` verwenden, ohne neuen Key oder Katalogmigration.
- **Priorität/Aufwand/Risiko:** P3 / klein / klein; gewollte Override-Wirkung dokumentieren.
- **Dateien:** ausschließlich `SettingsMenuEntries.java` und gezielter vorhandener Lokalisierungstest in einer späteren Implementierung.
- **Erforderliche Tests:** getrennte EN-/DE-Overrides beider Keys, unveränderte Defaults und Toggle-Wirkung.

## 10. Kleine folgende Entwicklungspakete

| Paket | Ziel / konkrete Änderungen | Bestehende APIs | Risiken / erforderliche Tests | Abhängigkeiten |
| --- | --- | --- | --- | --- |
| 07B – Clan-Aktionssicherheit in der Bedienung | M1: Zielbezogene Transfer-Bestätigung, klare Abbruch-/Erfolgsführung; noch keine globalen GUI-Änderungen. | ClanService, Holder-/Action-Mapping, MessageService/Localization. | Stale Confirmation, neue Rollen, Save-Fehler, Doppelklick, Security-Harnesses, echte Client-Gesten. | Betreiberentscheidung über Bestätigungsablauf; unabhängig von Farben/Skins. |
| 07C – Rückwege und Eingabehinweise | M2 und zunächst textueller Teil M6; Friends-Hub-Rückweg, eindeutige Editor-Picker-Ziele, Rückkehr-Commandhinweise. | Bestehende Back-Opener in SettingsModule/LobbyExperience, Consumer, UiItems. | Callback-Ownership, direkter Command-Einstieg, Welt/Disable, feste Slots; Navigation-Harnesses und Client-Rückwege. | 07B nicht technisch erforderlich. Automatische Rückkehr separat freigeben. |
| 07D – Editor-Verständlichkeit | M3: Empty-/Arbeitsflächen-Hinweise, benannte Back-Aktionen, echte Katalogtexte statt Rohlabels. | LocalizationService, UiItemSpec, bestehender Editor-Holder/Store. | Operator-Overrides, IDs/Pages nicht umdeuten, Slotbanken/Pending-Region/Save erhalten; Editor-, Locale-, Storetests. | Rücksprungvertrag aus 07C vorab festlegen oder unabhängig nur Text/Empty umsetzen. |
| 07E – Begrenzte visuelle Angleichung | M4 zuerst separat Clan-Spielerköpfe; danach M5 und klar gewählter Teil M7. | CachedPlayerHeads, UiItems, vorhandene Semantikfarben, MenuLayout. | Profil-UUID, Fallback, Rank-/Overridefarben, keine Slotverschiebung; Head-/Layout-/Snapshottests plus Clientbilder. | Gewünschte Palette/Tooltipdichte durch Sichtprobe entscheiden; kein vollständiges Theme-Projekt. |
| 07F – Gezielte Restpflege | M10 separat; M8/M9 nur nach Reproduktion/Clientbeleg. | Vorhandene Locale-/Inventory-/Callback-Pfade. | Kleine reproduzierbare Negativkontrollen; keine breite Lifecycle-Umstellung aus Vermutung. | Evidenz für jedes einzelne Restthema. |

Jedes Paket sollte separat reviewbar und kanonisch prüfbar sein. Kein Paket ist Bestandteil dieses Audit-Commits. Keine Verpflichtung, alle optionalen P3-Vorschläge umzusetzen.

## 11. Bewusst nicht empfohlen

- Keine neue abstrakte UI-Engine, generische Menu-Basisklassenhierarchie, globale Permission-Whitelist oder zweites Design-/Message-Service-System.
- Keine einheitliche Inventory-Größe beziehungsweise globale Slotverschiebung für alle Features. Insbesondere Operator-Navigator-Slots, Staff-Aktionsplätze und native Storage-Slots bleiben ihren Verträgen zugeordnet.
- Keine permanente Skin-Datenbank, eigenen Netzwerkdownloads oder Async-Ergebnisse auf mittlerweile ersetzte Inventare.
- Keine globale Filler-Umrandung, kein flächendeckendes Akzent-Neufärben und keine pauschale Anwendung von Rangfarben auf alle Personennamen.
- Keine zusätzlichen Polling-Tasks für bereits beobachtete Services und kein zweiter Rank-/Online-Cache. Der vorhandene Reset-Countdown ist kein Anlass, alle Menüs zu pollen.
- Kein automatisches Ausführen der Eingabe-Suggestions, keine pauschalen Bestätigungen für harmlose reversible Toggles, kein Live-Editing für Invsee und keine read-only Umdeutung des EnderChest.
- Keine Wiederherstellung der öffentlichen Staff-Liste oder ihres entfernten Status. Keine Änderungen an Commands, Permissions, Player-Persistenz oder Operator-Dateien in diesem Schritt.

## 12. Echte Minecraft-Client-Abnahme vor Implementierung

1. EN/DE mit kleiner und großer GUI-Skalierung: Menütitel, lange Tooltips, UUIDs, Clanname/Tag und benutzerdefinierte Navigator-Texte. Mindestens ein zulässiger Name mit Formatzeichen muss literal bleiben.
2. PlayerMenu → Friends/Settings/Visibility, direkter Command-Einstieg, Back gegenüber Close und der Rückweg nach einer Chat-Suggestion. Erwartungen an automatische Rückkehr ausdrücklich festlegen.
3. Clan-Gesten mit eigenen Wegwerfaccounts: Eigentumsübergabe, Kick, Disband-Suggestion; bewerten, wo ein versehentlicher Modifier-Klick realistisch ist. Kein Test mit realem produktivem Clan-Eigentum.
4. Navigator leer und mehrseitig, Permission-gefilterte Route, ohne freien Rückkehrslot, mit explizitem Back, Elternseite nach Rückkehr, fehlender Warp/Welt, Regionzählung als Momentaufnahme.
5. Editor frisch/leer, volle 54-Slot-Definition, beide Slotbanken, Picker ohne Warps, Icon-/Text-/Region-Dialoge, Wechsel voller/manueller Höhe, Preview ohne Teleport, Schließen gegenüber Discard und Save-Konflikt. Arbeitssitzung nicht als allgemeine Settings-GUI behandeln.
6. Online-/Offline-Status bei Friends/Clan/VisiblePlayers, echte beziehungsweise fehlende Skins, Staff-Rangfarbe, alle Empty-States, zu lange Lore und Contrast von AQUA/Grau/Rot/Grün. Keiner dieser Punkte ist hier als visuell bestanden markiert.
7. Staff ohne Node, nur Node, nur geeignete Gruppe und beide gemeinsam; leere/mehrseitige Ziele, erlaubte/verbotene Aktionen, Rechteentzug bei offenem Menü. Slot 22 bleibt ungebunden.
8. DailyQuest active/pending/completed, Empty/disabled/no-definitions, Countdown über einen Reset, Permission-Verlust. Klarstellen, dass Questklick keinen Claim auslöst.
9. Schnelle Doppel-/Seitenklicks, ESC, Drag/Shift/Number/Offhand/Creative, bereits von Fremdplugin gecancelter Klick, Locale-Wechsel und Reload während offener Ansichten. Verbundener Client ist für Packet-/Titelwirkung erforderlich.
10. Invsee-Snapshot gegenüber echter EnderChest-Speicherung unterscheiden. Sichtprobe ausschließlich in isolierter Umgebung mit entbehrlichen Daten.

## 13. Qualitätssicherung und Grenzen

**Keine neue vollständige Verifikation, kein neuer Maven-/Spotless-Lauf, keine neue native Paper-/LuckPerms-Abnahme und kein Minecraft-Client-Test wurden für diesen reinen Audit ausgeführt.** Die bekannte Step-06D.1-Baseline bleibt: 234 Harnesses, 34.791 Checks, keine Fehler, Maven clean package/Spotless PASS und 522 native API-Checks. Diese Zahlen werden hier ausdrücklich nicht als neue Audit-Ergebnisse verwendet. Ein vorgefundener späterer lokaler Buildordner ist ebenfalls kein neuer Quellcode- oder nativer Abnahmebeweis dieses Audits.

Neue berichtsbezogene QA umfasst die Prüfung aller Markdown-Dateiziele und numerischen Quellzeilen, vorhandener referenzierter Harness-Dateien und ausgewählter Methodennamen, die Prüfung der Layout-Tabellen gegen ihre Definitionen, `git diff --check` und den abschließenden SHA-/Ref-/Worktree-Schutzvergleich. Neu ausgeführt: Der unveränderte Repository-Link-Harness wurde separat außerhalb des Projektausgabeordners kompiliert und bestand **120 Checks**. Die berichtsspezifische Referenzprüfung bestätigt **54 vorhandene Datei-/Zeilenverweise** und **59 existierende eindeutig benannte Harness-Klassen**. Zusätzlich wurden 55 Methodendeklarationen nachgeschlagen und die beschriebenen Layoutplätze gegen die aktuellen Implementierungen geprüft. Die ergänzende Holder-Suche bestätigt elf eigene InventoryHolder-Implementierungen einschließlich des Editor-Holders. Dies sind Dokumentationsprüfungen und kein neuer GUI-Verhaltenstest. Es wurden keine Testimplementierungen angepasst.

**Grenzen:** Quellcode und bestehende Tests belegen vorhandene Regeln, aber keine Client-Lesbarkeit, keine reale Packet-Reihenfolge und keinen vollständigen Schutz gegen beliebige Fremdplugins. Das Audit erfindet weder Exploits noch verspricht es Vollständigkeit für zukünftige Integrationseinstiege. Operator-Overrides, individuelle Navigator-Daten und laufende Spielerdaten wurden nicht als repräsentatives Client-Layout ausgelesen. Die Vergleiche beziehen sich auf den geprüften Repository-Stand, nicht auf sich legitim verändernde Operator-Serverdateien.

## 14. Untersuchte Dateien und Testzuordnung

Die konkreten Referenzen stehen an den jeweiligen Befunden. Ergänzend umfasst die geprüfte Struktur:

- `lobby/experience/player/PlayerMenu.java`, `PlayerMenuListener.java`, `PlayerMenuHolder.java`; `LobbyExperienceModule.java`, `LobbyExperienceListener.java`, `lobby/item/LobbyItemRegistrations.java` und Navigator-Zugang.
- `settings/SettingsMenu.java`, `SettingsListener.java`, `SettingsInventoryHolder.java`, `SettingsMenuEntry.java`, `SettingsMenuEntries.java`, `SettingsModule.java`, `settings/command/SettingsCommand.java`.
- `settings/visibility/VisibilitySettingsMenu.java`, `VisibilitySettingsListener.java`, `VisibilitySettingsHolder.java`, `VisiblePlayersMenu.java`, `VisiblePlayersListener.java`, `VisiblePlayersHolder.java`; vorhandener Settings-/Identity-/Visibility-Service-Zugriff.
- `friend/gui/FriendMenu.java`, `FriendMenuListener.java`, `FriendMenuHolder.java`, `FriendMenuView.java`; FriendCommand, FriendModule und Friend-Service-Observer.
- `clan/gui/ClanMenu.java`, `ClanMenuListener.java`, `ClanMenuHolder.java`, `ClanMenuView.java`; ClanCommand, ClanModule, ClanService-Transfer-/Kick-/Commit-Grenze.
- `lobby/experience/navigator/NavigatorMenu.java`, `NavigatorListener.java`, `NavigatorInventoryHolder.java`, `NavigatorEditor.java`, `NavigatorAccessPolicy.java`, `NavigatorConfig.java`, `NavigatorStore.java`, Region-/Tool-Anbindung.
- `staff/StaffPanel.java`, `StaffPanelHolder.java`; aktuelle Staff-Hierarchie und Step-06D.1-Tests/Report.
- `quest/daily/menu/DailyQuestMenu.java`, `DailyQuestMenuListener.java`, `DailyQuestInventoryHolder.java`, `ResetCountdownFormatter.java`; DailyQuest-Konfiguration und Runtimezugang.
- `utility/InvseeService.java`, `InvseeInventoryHolder.java`, `UtilityService.java`, `command/InvseeCommand.java`, `command/EnderChestCommand.java`.
- `ui/MenuLayout.java`, `UiItemSpec.java`, `UiItems.java`, `Pagination.java`, `CachedPlayerHeads.java`; `message/MessageStyle.java`/`MessageService.java` als bestehender Farb-/Delivery-Kontext und die Lokalisierungsgrenze.
- `src/main/resources/lang/en.yml`, `lang/de.yml`, `plugin.yml`, `lobby.yml`, `navigator.yml`, `config.yml`, `daily-quests.yml`; Warp-Konfiguration über ihren bestehenden Service, keine Koordinatenänderung.
- Die je Menü genannten vorhandenen Harnesses sowie `MenuLayoutHarness`, `PaginationHarness`, `CachedPlayerHeadsHarness`, `CatalogUsageHarness`, `RepoLinkHarness` und GUI-Authoring-/Sicherheitsdokumentation. Vorhandene Tests wurden für Vertragszuordnung gelesen, nicht als neue Gesamtabnahme ausgegeben.

## 15. Git-Abschluss und vollständige Änderungsliste

**Einzige geänderte und für den Commit vorgesehene Datei:**

`reports/07a/STEP_07A_GUI_UX_AUDIT.md`

Genau ein lokaler Audit-Commit; kein Push, Merge, PR oder Deployment. Der vollständige Commit-SHA wird nach Erstellung in der Codex-Abschlussantwort und einer externen Evidenz-Quittung angegeben. Eine eigene SHA kann nicht im Inhalt desselben Commits stehen; im Repository ist sie mit `git log -1 --format=%H -- reports/07a/STEP_07A_GUI_UX_AUDIT.md` ermittelbar.

Der abschließende Schutzvergleich vor dem Commit bestätigt alle 1.481 ursprünglichen Dateien bytegleich. `master`/`origin/master`, Recovery-Refs, fremder Worktree, beide Run-Konfigurationen und beide Receipts entsprechen dem Ausgangssnapshot; neu ist ausschließlich dieser Bericht. Nach dem Commit wird derselbe Schutzvergleich erneut geprüft. Externe Audit-Evidenz liegt unter `C:\Users\vapee\.codex\visualizations\2026\10\25\01a17432-7597-7ff1-bc9d-34201a027523\step07a-gui-ux-audit-20261106-014814`; sie ist kein zusätzlicher Repository-Report. Der einzige erlaubte Ref-Cleanup war die oben dokumentierte lokale Step-06D.1-Branchentfernung.
