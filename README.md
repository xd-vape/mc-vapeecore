# VapeeCore

VapeeCore ist das zentrale Basis-Plugin für einen Minecraft-Community-Server. Das Projekt ist als modularer Monolith aufgebaut und stellt aktuell eine zentrale Konfiguration, MiniMessage-/Adventure-Nachrichten, eine gemeinsame Command-Help-Präsentation, interne CoreModule, eine lokale Player Foundation, persistente Social-/Ignore-, Friends- und Clan-Systeme mit fokussierten GUIs, runtimebasierte Presence mit optionalen Friend-Join-/Leave-Benachrichtigungen, eine Coin-Economy, eine zentrale Reward Foundation, kumulative Online-/Playtime-Rewards, eine generische Quest Foundation mit Daily-Cycle und konfigurierbarem Katalog, globalen Chat, private Nachrichten, Player-Presentation, eine Ingame-Settings-Oberfläche mit Player-Visibility-Verwaltung, sichere Staff-Utilities, ein leichtgewichtiges Activity-Fundament, Community-Sitze, native World Displays, physisches Blackjack, ein generisches Warp-System, eine spielerfreundliche Lobby Experience, eine gefilterte Visibility-/Privacy-Foundation und eine lesende LuckPerms-Rank-Integration bereit. Der aktuelle Stand ist Phase 29 „Quest Completion“.

## Phase 29 – Quest Completion

`/quests` (Alias `/quest`, `vapeecore.quest.use`, Default `true`) synchronisiert den Daily-Cycle und öffnet eine geschützte, lesende 54-Slot-Ansicht mit 45 Quests pro Seite, Navigation, Refresh und Close. Sie zeigt Namen, Beschreibung, Fortschritt, Coins und Status. Rewards werden automatisch ausgezahlt; es gibt keinen Claim-Button oder Claim-Command.

Die generische Quest-/Daily-/Reward-Foundation bleibt erhalten. Genau zwei echte Quellen melden über `QuestProgressReporter`: `playtime:minute` zählt neu überschrittene volle Minuten der kumulativen Minecraft-Spielzeit, `blackjack:win` zählt abgeschlossene `WIN`- und `BLACKJACK`-Ergebnisse. Completion meldet den literal konfigurierten Namen und Coins einmal; fehlgeschlagene Rewards bleiben pending und können die Rotation blockieren. Wiederholte erfolglose Retries erzeugen keine Completion-Nachrichten.

`daily-quests.yml` bleibt byte-identisch mit `enabled: false` und `quests: {}`. Betreiber bestimmen Inhalte und Balance selbst; Beispiele stehen im [Quest-Bericht](reports/29-quest-completion/QUEST_COMPLETION.md). Andere valide Progress Keys dürfen laden und bleiben ohne Producer inert. Es gibt keinen Mine- oder generischen Activity-Producer, neuen Hotbar-Eintrag, zentrales Menü oder Quest-Adminbefehl.

Aktueller Vertrag: **27 Module, sechs Reload-Teilnehmer, 37 Root-Commands, 50 Permission-Nodes, 15 Child-Kanten**. Verifikation: **101 ausführbare Harnesses, 14.705 Checks, null Fehler**. Build, Paper-Smoke und Merge-Bereitschaft: [reports/29-quest-completion/QUEST_COMPLETION.md](reports/29-quest-completion/QUEST_COMPLETION.md). Live client test: not performed.

## Phase 28 – Lobby & Navigation Completion (historisch)

Der Compass bleibt der **Warp Navigator**. Die Hotbar bleibt bei Slot 0 Navigator, 4 Player Visibility und 8 Settings; es gibt kein Server Menu und keine zusätzlichen Items oder Kategorien. Ziele bleiben vollständig generische Warp-IDs nach `[a-z0-9_-]+`.

Warps besitzen jetzt `navigator.visible` (Default `true`) und `navigator.order` (Default `0`, nichtnegative ganze Zahl). Der Navigator zeigt ausschließlich sichtbare Ziele, sortiert nach Order und anschließend ID, mit 45 Zielen pro Seite. Legacy-Dateien laden mit denselben Defaults ohne Eager-Rewrite; ungültige optionale Metadaten warnen und fallen sicher zurück. `/warp show <id>`, `/warp hide <id>` und `/warp order <id> <order>` verwenden dieselbe `vapeecore.warp.admin`-Permission. Hidden-Warps bleiben gespeichert und administrativ editierbar; Help, Info, List und Completion zeigen die passende Navigator-Semantik.

Owner, Holder, exakt gebundenes Inventar, aktive Registrierung und tatsächlich geöffnete Ansicht müssen bei Menüaktionen übereinstimmen. Öffnen und Klicken verlangen online, geladen, Lobby-Welt, `NORMAL` und keine Activity. Stale Menüs können BUILD-/Activity-State oder inzwischen versteckte/entfernte Ziele nicht umgehen. Die Ausführung prüft aktuellen Warp-State; Weltwechsel, Quit und Disable räumen aktive Inventare auf.

Stand von Phase 28: **27 Module, sechs Reload-Teilnehmer, 36 Root-Commands, 49 Permission-Nodes**. Verifikation: **96 ausführbare Harnesses, 14.454 Checks, null Fehler**, erfolgreicher Maven-Clean-Build und realer Paper-1.21.11-/LuckPerms-5.5.84-Smoke einschließlich Reload und Stop. Vollständiger Bericht: [reports/28-lobby-navigation/LOBBY_NAVIGATION.md](reports/28-lobby-navigation/LOBBY_NAVIGATION.md). Live client test: not performed.

## Phase 27 – Notifications & Presence

Verifiziert: **93 ausführbare Harnesses, 14.098 Checks, 0 Fehler; Maven-Clean-Build und echter Paper-1.21.11-Start/Reload/Shutdown erfolgreich.** Live client test: not performed.

`PresenceModule` hält ausschließlich zur Laufzeit die UUIDs aktuell online befindlicher und vollständig geladener Spieler. Join wird auf `MONITOR` erst nach erfolgreichem Player-Load veröffentlicht, Quit auf `LOWEST` noch vor Player-/Social-Cleanup; echte Zustandswechsel werden dedupliziert. Der Enable-Seed übernimmt bereits online und geladen vorgefundene Spieler ohne Benachrichtigung. Presence besitzt keine Datei, keinen Scheduler, keine Bukkit-Referenzen im Domain-State und ist kein Reload-Teilnehmer.

Akzeptierte Freunde können über den opt-in Player-Key `settings.friend-presence-notifications` unmittelbare Online-/Offline-Hinweise erhalten. Default und Legacy-Fallback sind `false`. Nur aktuell online befindliche, geladene Empfänger mit aktivierter Preference werden berücksichtigt; Ignore in beliebiger Richtung blockiert. Freundesreihenfolge bleibt deterministisch, Fehler eines Empfängers verhindern keine weiteren Zustellungen und es gibt keine Offline-Queue. Das 54-Slot-Settings-Menü schaltet dieselbe Preference über die `BELL` in Slot 33 und den Status in Slot 42.

Stand von Phase 27: **27 Module, sechs Reload-Teilnehmer, 36 Root-Commands**. Es kamen keine Commands, Permissions, Configdateien, Schema-Versionen oder globalen Join-/Quit-Nachrichten hinzu. Technischer Bericht: [reports/27-notifications-presence/NOTIFICATIONS_PRESENCE.md](reports/27-notifications-presence/NOTIFICATIONS_PRESENCE.md).

## Phase 26B – Administrative Target and Permission Hardening

Verifiziert: **90 ausführbare Harnesses, 14.037 Checks, 0 Fehler; Maven-Clean-Build und echter Paper-Start/Reload erfolgreich.**

Abgeschlossen: Staff-Schutz für die zehn sensiblen Utility-Target-Aktionen und Coins add/remove/set. Self bleibt unverändert; bei TP wird die bewegte Source geschützt, nicht das Reiseziel. Invsee bleibt read-only, Enderchest ein echtes mutierbares GUI. Permission zuerst, geladene Hierarchie vor jedem Eingriff, Unknown fail closed; weder OP noch Teleport-State-Bypass umgehen Staff-Schutz. Ping, Build und Coins GET sind absichtlich nicht zusätzlich hierarchiegeschützt.

Stand von Phase 26B: **26 Module, sechs Reload-Teilnehmer, 36 Root-Commands**. Descriptor-Audit: 49 Nodes, 15 Child-Kanten, kein Defekt und keine Änderung an plugin.yml/config.yml. Keine neue Permission, Offline-Mutation, LP-Änderung oder zweite Hierarchie. Bericht: [reports/26b-permission-hardening/PERMISSION_HARDENING.md](reports/26b-permission-hardening/PERMISSION_HARDENING.md); kanonischer Permission-/Inheritance-Audit: [docs/PERMISSIONS.md](docs/PERMISSIONS.md). Phase 27 ergänzt anschließend ausschließlich Notifications & Presence.

## Phase 26A – Staff Hierarchy and Moderation Protection

Alle sieben Moderationsbefehle prüfen nach Permission, Known-Identity und Self-Regel eine eigene Staff-Hierarchie. Die Standardreihenfolge in `config.yml` unter `staff.hierarchy.protected-groups` ist **builder → moderator → admin → owner (LOW→HIGH)**. Ausschließlich die primäre LuckPerms-Gruppe zählt, nicht Weight, Prefix, Farbe, geerbte Gruppen oder der öffentliche `ranks`-Track. Normal/VIP/Custom-Ziele außerhalb der Liste bleiben mit Command-Permission moderierbar. Geschützte Ziele verlangen einen geschützten Actor mit **strikt höherem** Level; Owner→Owner bleibt verboten. Builder ist geschützt, erhält dadurch aber keine Moderationsrechte. OP ist kein Hierarchie-Bypass. Console umgeht nur die Hierarchie, nicht die Permission.

Bekannte Offline-Ziele werden bei fehlendem LuckPerms-Cache asynchron aufgelöst, ohne den Serverthread zu blockieren. Erst die Hauptthread-Fortsetzung prüft aktuelle Permission, ursprüngliche Online-Session, Actor-Gruppe und einen gemeinsamen unveränderlichen Config-Snapshot. Fehler oder fehlende Gruppen brechen ohne Save/Audit/Benachrichtigung/Kick ab. Self-Mutationen bleiben verboten; Self-History bleibt mit Permission erlaubt. Online-Completion filtert synchron geschützte/unsichere Ziele; aktive Offline-Unban/Unmute-Kandidaten bleiben ohne Massen-Loads sichtbar, die Ausführung prüft sie vollständig.

`/core reload` aktiviert die Hierarchie über den bestehenden ConfigService-Teilnehmer einschließlich Rollback. Fehlende Legacy-Keys verwenden sichere Defaults; ungültige oder leere Listen warnen und fallen auf diese zurück, ohne die Datei zu überschreiben. **26 Module, sechs Reload-Teilnehmer, 36 Root-Commands; keine neuen Permissions oder Datenmigrationen.** Utility-/Teleport-/Inventory- und Economy-Target-Schutz sowie der abschließende Permission-/Inheritance-Audit wurden anschließend in **Phase 26B** abgeschlossen. Bericht und vollständige Verifikation: [reports/26a-staff-hierarchy/STAFF_HIERARCHY.md](reports/26a-staff-hierarchy/STAFF_HIERARCHY.md).

## Developer Documentation

Die praktische Architektur-, Ownership-, Config-, Command- und Erweiterungsdokumentation liegt in [docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md). Die vollständige kanonische Permission- und Rangübersicht liegt in [docs/PERMISSIONS.md](docs/PERMISSIONS.md).

## Phase 25B – Mute and Communication Enforcement (historisch)

`/mute <player|uuid> <duration|permanent> <reason...>` und `/unmute <player|uuid> [reason...]` funktionieren für bekannte Online-/Offline-Spieler und UUIDs. Self-Moderation bleibt gesperrt. Die unabhängigen Permissions `vapeecore.moderation.mute` und `.unmute` sind standardmäßig `op`, ohne Children, Aliases oder Bypass.

Aktive Mutes blockieren öffentlichen Chat und ausschließlich ausgehende private Nachrichten (`/msg`, `/reply`, `/r`). Ein gemuteter Empfänger darf weiterhin Nachrichten empfangen; Settings und Ignore gelten unverändert. Erfolgreiches Unmute wirkt sofort, exakt am Ablaufzeitpunkt wird Kommunikation ohne Save/Restart/Reload wieder erlaubt. Der async Chat liest nur die immutable, nach erfolgreichem Save veröffentlichte Mute-Projektion; Feedback erfolgt einmalig auf dem Hauptthread. Save-Fehler behalten den bisherigen Zustand. Gründe und UTC-/Permanent-Notices sind literal Adventure-Components.

Damals: **36 Root-Commands, 26 Module, sechs Reload-Teilnehmer**; aktuell sind es durch Presence 27 Module. Phase 25B ergänzte keine Ranghierarchie, globale Command-Sperre, Schemaänderung oder periodischen Task. Seit Phase 26A ergänzt die explizite Staff-Hierarchie die weiterhin notwendigen Moderations-Permissions. Vollständige Verifikation: [reports/25b-moderation-mutes/MODERATION_MUTES.md](reports/25b-moderation-mutes/MODERATION_MUTES.md).

## Phase 25A – Moderation Commands + Ban Enforcement (historisch)

`/warn`, `/ban`, `/unban`, `/kick` und `/history` sind für berechtigte Player und Console verfügbar. Bekannte Offline-Namen und UUIDs funktionieren für Warn/Ban/Unban/History; Kick verlangt echte Online-Präsenz. Ban-Dauer ist explizit: positive einzelne `s/m/h/d/w`-Einheit oder `permanent`/`perm`. Aktive Bans verhindern den synchronen Login; Online-Ban und Kick disconnecten erst nach erfolgreichem Save. Gründe bleiben literal, History hat fünf Records pro Seite und sichere UUID-basierte Suggest-Navigation. Self-Moderation ist gesperrt, Self-History mit Permission erlaubt. `/core help` enthält die unabhängig permission-gefilterte Moderation-Section; damals insgesamt 34 Root-Commands, 26 Module und sechs Reload-Teilnehmer.

Die fünf `vapeecore.moderation.<command>`-Permissions sind unabhängig und standardmäßig `op`; Empfehlungen stehen in PERMISSIONS. Damals noch keine Target-Hierarchie; diese wurde anschließend gezielt für alle sieben Moderationsbefehle in Phase 26A ergänzt. Mute/Unmute und Chat-/PM-Enforcement wurden anschließend in Phase 25B ergänzt. `minecraft:ban`/`minecraft:kick` bleiben unveränderte Vanilla-Commands, keine Bukkit-Banlist-Synchronisierung. Bericht: [reports/25a-moderation-tools/MODERATION_TOOLS.md](reports/25a-moderation-tools/MODERATION_TOOLS.md).

## Phase 24 – Moderation Foundation

`ModerationModule` besitzt die immutable Historie für `WARNING`, `MUTE`, `BAN` und `KICK` sowie den `ModerationService` und das separate `plugins/VapeeCore/moderation.yml` (Schema 1). Warning und Kick sind ausschließlich gespeicherte Ereignisse; Mute und Ban können permanent oder zeitlich begrenzt sein und widerrufen werden. Exakt am Ablaufzeitpunkt ist die Sanktion nicht mehr aktiv. Abgelaufene und widerrufene Einträge bleiben erhalten. Änderungen speichern einen vollständig validierten Snapshot vor dem Austausch des Servicezustands; Speicherfehler lassen den alten Zustand unverändert.

Historischer Phase-24-Stand, vor Phase 25A: keine Moderationsbefehle, Permissions, GUI, Join-/Ban-Durchsetzung, Chat-/PM-Mute-Durchsetzung oder Ablauf-Tasks. Keine Player-YAML-Änderung oder Bukkit-Banliste. Eine fehlende Datei bleibt bei Start, `/core reload` und Shutdown fehlend; erst die erste Service-Mutation schreibt sie. Moderation ist kein Reload-Teilnehmer. Damals waren es 26 Module; aktuell sind es 27 und unverändert sechs Reload-Teilnehmer. Schema und Main-Thread-Grenze stehen im Developer Guide; Verifikation in [reports/24-moderation-foundation/MODERATION_FOUNDATION.md](reports/24-moderation-foundation/MODERATION_FOUNDATION.md). Die Erweiterungen wurden in Phase 25A/25B ergänzt; der Phase-24-Bericht bleibt historisch unverändert.

## Phase 23 – Core Commands Completion

Alle eigenen Commands wurden vor Änderungen auditiert; Matrix, begründete Änderungen und Verifikation stehen in [reports/23-core-commands-completion/COMMAND_AUDIT.md](reports/23-core-commands-completion/COMMAND_AUDIT.md). `/core help` deckt General, Social, Lobby & Settings, Economy, Utilities und Administration permission-aware ab. Executor-Checks ergänzen die Registrierung in `plugin.yml`; Completion verrät ohne Berechtigung keine Targets. Friend-Aktionen ohne Ziel zeigen tatsächliche Anfragen oder Freunde mit ausschließlich vorgeschlagenen Chat-Befehlen. Mehrdeutige oder unbekannte Identitäten erhalten UUID-Fallbacks; eingehende Requests enthalten sichere Accept-/Deny-Aktionen.

Utilities, `/msg`, `/ignore` und `/rank` akzeptieren ausschließlich vollständige eindeutige Online-Namen (case-insensitive), keine Teilnamen, Selector oder Offline-Fallbacks. Bestehende Known-Player-/UUID-Domains wie Friend, Clan, Profile, Coins, Unignore und Settings Visibility bleiben unverändert. Lobby-Commands melden abgebrochene Teleports und Speicherfehler kontrolliert. Phase 23 ergänzte keine Permission-Nodes, Module, Reload-Teilnehmer oder Datenmigration: damals 25 Module und sechs Reload-Teilnehmer. Der historische Phase-23-Audit bleibt unverändert; Moderationsbefehle, `/pay`, Offline-Utilities und Invsee-Modify gehören nicht zu Phase 23.

## Voraussetzungen

- Java 21
- Paper 1.21.11
- Maven 3
- LuckPerms 5.5 als Server-Plugin

LuckPerms muss als eigene Plugin-JAR im `plugins`-Verzeichnis des Paper-Servers vorhanden sein. Ohne LuckPerms wird VapeeCore nicht geladen.

## Build

```bash
mvn clean package
```

Die fertige Plugin-JAR wird unter `target/vapeecore-1.0-SNAPSHOT.jar` erzeugt.

### IntelliJ Dev-Server

Das Projekt enthält die geteilten IntelliJ-Run-Konfigurationen `Start VapeeCore Dev Server` und `Build & Deploy VapeeCore`. Der Server sollte über die erste Konfiguration oder über `dev-server/start.bat` gestartet werden. Beide Wege verwenden denselben verwalteten Startprozess und stellen einen sauberen Stop-Kanal bereit.

Während `Start VapeeCore Dev Server` läuft, können Paper-Konsolenbefehle direkt in das zugehörige IntelliJ-Ausgabefenster eingegeben werden. Beim Start über `dev-server/start.bat` werden die Befehle entsprechend im geöffneten Terminalfenster eingegeben.

`Build & Deploy VapeeCore` sendet einem laufenden verwalteten Paper-Server zunächst den regulären Konsolenbefehl `stop` und wartet, bis Plugins, Spieler und Welten vollständig gespeichert wurden. Anschließend führt die Konfiguration `mvn clean package` aus und ersetzt `dev-server/plugins/vapeecore-1.0-SNAPSHOT.jar` über eine temporäre Deployment-Datei. Der Server bleibt danach absichtlich gestoppt und kann über `Start VapeeCore Dev Server` erneut gestartet werden. Ein fremder oder manuell gestarteter Paper-Prozess wird nicht hart beendet; in diesem Fall bricht das Deployment mit einer verständlichen Meldung ab.

## Architektur

- `command`: Commands und deren Subcommands
- `command.help`: immutable Help-Modelle und gemeinsamer Adventure-Renderer für komplexe Commands
- `activity`: runtimebasiertes Framework für kleine, direkt erreichbare Server-/Lobby-Aktivitäten
- `activity.blackjack`: physische, administrativ konfigurierte Blackjack-Tische, Karten-Domain, Activity-Hotbar und native Weltanzeigen
- `activity.location`: immutable Positionen, Bereiche und physische Activity-Venues
- `activity.player`: immutable Activity-Teilnehmer ohne dauerhafte Bukkit-Player-Referenzen
- `chat`: globaler Adventure-Chat mit LuckPerms-Prefix und -Suffix
- `config`: zentraler Zugriff auf die Bukkit-Konfiguration
- `economy`: internes Coin-Wallet, EconomyService und Coin-Commands
- `identity`: immutable Identitäts-/Profilansichten, Known-Player-Lookup und `/profile`
- `moderation`: immutable Moderationshistorie, separate atomare Persistence, sieben Staff-Commands, synchrones Ban-Login-Enforcement und immutable Mute-Projektion für Chat/PM
- `friend`: persistente UUID-basierte Freundschaften, Anfragen, `/friend` und die fokussierte Friends-GUI
- `presence`: runtimebasierter Online-State und gefilterte Friend-Join-/Leave-Benachrichtigungen
- `clan`: persistente UUID-basierte Clans, `/clan` und die geschützte Clan-GUI
- `format`: gemeinsam genutztes Playtime-Format
- `reward`: zentrale Gameplay-Reward-API mit gebündelter Player-Persistence
- `onlinereward`: kumulative, konfigurierbare Rewards auf Basis der Minecraft-Spielzeit
- `quest`: generische Definitionen, Assignments, Progress, Completion und persistenter Quest-State
- `quest.daily`: Daily-Config, Cycle-Berechnung, deterministische Auswahl und Rotation
- `lobby`: Lobby-Spawn, Teleports und auf die Lobby-Welt begrenzter Schutz
- `lobby.item`: Definition und Erzeugung der drei Lobby-Hotbar-Items
- `lobby.message`: Rendering der konfigurierbaren Join-/Quit-Nachrichten
- `lobby.player`: zentraler Runtime-Zustand `NORMAL`/`BUILD`, Gamemode und Inventory-Ownership
- `lobby.warp`: generische persistente Warps ohne vordefinierte oder reservierte Ziele
- `lobby.experience`: Event-Einstieg für Lobby-Hotbar, dynamischen Warp Navigator, Settings-Shortcut und Visibility-Synchronisierung
- `message`: Adventure- und MiniMessage-Ausgabe
- `module`: kleiner Lifecycle-Extension-Point für zukünftige Systeme
- `permission`: lesender Zugriff auf LuckPerms-Gruppen und Meta-Daten
- `rank`: cachefreie Rank-Domain, öffentlicher LuckPerms-Track und `/rank`-/`/ranks`-Commands
- `player`: Player-Domainmodell, aktiver Cache und Join-/Quit-Lifecycle
- `player.repository`: austauschbare Persistence mit lokaler YAML-Implementierung
- `player.social`: persistenter, UUID-basierter Social-State eines Players
- `player.settings`: persistente Player-Settings und interne Zugriffsschicht für andere Module
- `privatemessage`: sichere, sessionbasierte Nachrichten zwischen Online-Spielern
- `presentation`: Lobby-Sidebar und serverweite Adventure-Tablist
- `reload`: koordinierter zweiphasiger Config-Reload mit Runtime-Rollback
- `settings`: sichere Ingame-Oberfläche einschließlich Visibility-Filter und verwalteter sichtbarer Spieler
- `seat`: runtimebasierte CASUAL-/MANAGED-Sitze, technische Seat-Entities und Lobby-Interaktion
- `social`: Ignore-Service, threadsichere Runtime-Projektion, Lifecycle und Commands
- `utility`: zustandsbewusste Builder-/Admin-Utilities, Laufzeit-Cleanup und Commands
- `visibility`: zentrale Lobby-Visibility-Policy, Paper-Show/Hide-Anwendung und Cleanup eigener Hide-Zustände
- `worlddisplay`: keyed native TextDisplay-/ItemDisplay-Lifecycles für dynamische Core-Features

Die 27 Module starten in der gerichteten Reihenfolge `Permission → Rank → Player → Social → Economy → Identity → Moderation → Friend → Presence → Clan → Reward → OnlineReward → Quest → DailyQuest → Lobby → Visibility → Chat → PrivateMessage → Presentation → Settings → Activity → Utility → Seat → WorldDisplay → Blackjack → Warp → LobbyExperience` und werden beim Shutdown vollständig rückwärts deaktiviert. Presence leert dabei seinen Runtime-State, solange Friend, Social und Player noch verfügbar sind. DailyQuest stoppt seinen Sync vor dem Quest-Flush; Quest flusht anschließend vor OnlineReward und Reward. Visibility gibt beim Shutdown alle von VapeeCore gesetzten Hide-Zustände frei. `/core reload` besitzt weiterhin sechs Teilnehmer einschließlich `daily-quests.yml`.

Aktive Spieler werden als `CorePlayer` im Speicher gehalten. Zum Profil gehören die Einstellungen `scoreboard`, `sounds`, `private-messages`, `friend-requests`, das standardmäßig deaktivierte `friend-presence-notifications` und die gebündelten `PlayerVisibilitySettings`. Deren kompatibler Master-Key `lobby-players-visible` ist standardmäßig aktiv; Friends-, Staff-, Added-User- und Game-Participant-Filter starten deaktiviert. Außerdem gehören Coin-Wallet, `PlayerSocial`, der kleine `OnlineRewardProgress`, `PlayerQuestState` und `PlayerDailyQuestState` zum Profil. Andere Module greifen über klar abgegrenzte Services darauf zu. Die lokale Persistence legt pro UUID eine Datei unter `plugins/VapeeCore/players/<uuid>.yml` an. Alte Player-Dateien ohne einzelne Settings oder ohne `settings`, `settings.visibility`, `economy`, `social`, Online-Reward-, Quest- beziehungsweise Daily-Cycle-State bleiben gültig. Ein fehlender Reward-State wird beim ersten Processing auf die aktuelle Minecraft-Spielzeit baselined; ein fehlender Quest-State ist leer und ein fehlender Daily-Cycle uninitialisiert. Bukkit-`Player`-Instanzen werden nicht im Domainmodell gespeichert.

Jedes Player-Profil besitzt außerdem ein Coin-Wallet mit einer nicht negativen ganzzahligen `long`-Balance und dem Defaultwert `0`. Die Coins werden als `economy.coins` in derselben Player-YAML gespeichert; alte Dateien werden beim nächsten regulären Save automatisch ergänzt. Direkte beziehungsweise administrative Balance-Änderungen verwenden den `EconomyService`; Gameplay-Rewards laufen ausschließlich über den `RewardService`. `/coins` zeigt den eigenen Kontostand, während `/coins get <player|uuid>` bekannte Online- und Offline-Wallets liest. `/coins add|remove|set <player|uuid> <amount>` verändert ausschließlich tatsächlich online befindliche, geladene Spieler. Ziele werden über bekannte UUIDs oder vollständige case-insensitive Namen aufgelöst; Mehrdeutigkeit verlangt eine UUID. Es gibt bewusst keine Vault-Anbindung, Offline-Mutationen, weiteren Währungen oder Spieler-zu-Spieler-Transfers.

Phase 22 vervollständigt die Command-UX: explizite Basispermission `vapeecore.economy.coins`, zusätzliche Adminpermission `vapeecore.economy.admin` mit Basis-Child, konkrete Subcommand-Usage und permission-aware Hilfe. Positive ganze `long`-Amounts gelten für Add/Remove, Set erlaubt auch null; Overflow und fehlende Coins werden ohne Mutation abgewiesen. Nach erfolgreichem Save erhalten Admin und Online-Target Feedback mit neuer Balance, ohne doppelte Self-Nachricht; INFO protokolliert Actor, Target, Aktion und alte/neue Balance. Save-Fehler erzeugen kontrolliertes Feedback und SEVERE. Tab Completion schlägt primär Online-Targets vor, bei mehrdeutigen Namen deren UUID; bekannte Offline-Ziele funktionieren manuell bei GET. Gameplay-Rewards, Offline-Profile und Presentation-`<coins>` behalten ihre bestehenden Servicegrenzen. `/pay` bleibt ohne sichere Zwei-Wallet-Transaktion ausdrücklich nicht implementiert.

Phase 17C ergänzt eine rein lesende Identity-/Profile-Schicht. `/profile` zeigt das eigene Profil; `/profile <name|uuid>` findet online befindliche oder bereits in VapeeCore bekannte Offline-Spieler. Namen werden ohne Beachtung der Großschreibung über einen beim Start aus den bestehenden Player-Dateien aufgebauten Runtime-Index aufgelöst; mehrere UUIDs mit gleichem Namen verlangen eine eindeutige UUID. Die Profilansicht zeigt Name, Online-Status, Coins, First Join und Last Join. Nur online sind aktueller LuckPerms-Rank samt Farbe und Minecraft-Playtime verfügbar; offline werden weder Rank noch Playtime künstlich gespeichert oder blockierend geladen. `EconomyService#getKnownCoins` liest bekannte Offline-Wallets, während bestehende Coin-Mutationen weiterhin nur geladene Spieler betreffen. Diese Identity-Phase brachte keine neue Config, kein neues Player-YAML-Feld und keine Profile-GUI. Friends und Clans verwenden dieselbe UUID-/Namensauflösung. Bukkit-`OfflinePlayer` und Netzwerk-Lookups dienen nicht als Identitätsdatenbank.

Phase 18A.1/18A.2 ergänzt die Friends Foundation und ihre Integration. `/friend` (Alias `/friends`) bietet `help`, `add`, `accept`, `deny`, `cancel`, `remove`, `list` und `requests` für Spieler mit `vapeecore.friend.use`. Ziele werden als bereits bekannte Online- oder Offline-Spieler über Name oder UUID aufgelöst; bei mehrdeutigen Namen ist eine UUID nötig. Freundschaften und gerichtete Anfragen liegen UUID-basiert mit `schema-version: 1` zentral in `plugins/VapeeCore/friends.yml`, nicht in Player-YAML. Die Standardlimits für Freunde, eingehende und ausgehende Anfragen sind jeweils 100/25/25 und stehen unter `friends.limits` in `config.yml`; Änderungen gelten nach `/core reload`. Der neue, standardmäßig aktive Player-Key `settings.friend-requests` wird in `/settings` über Feature-Slot 16 oder Status-Slot 25 geschaltet: Deaktivieren verhindert neue Anfragen, nicht das Annehmen bereits vorhandener. Ignore in beiden Richtungen blockiert neue Anfragen und Annahmen auch für bekannte Offline-Spieler. Friend besitzt keinen Scheduler und keinen zusätzlichen Reload-Teilnehmer.

Phase 18B ergänzt eine schlanke Friends-GUI: `/friend` und `/friends` ohne Argumente öffnen Freunde, eingehende und ausgehende Anfragen in drei Tabs; `/friend help` und alle Text-Subcommands bleiben erhalten. Die GUI zeigt den aktuellen Online-/Offline-Status weiterhin direkt über Paper und speichert ihn nicht; sie konsumiert den späteren PresenceService bewusst nicht. Sie blättert in 45er-Seiten und bietet einen anklickbaren Vorschlag für `/friend add `. Eingehende Anfragen können angenommen oder abgelehnt, ausgehende abgebrochen werden; Freundschaften werden ausschließlich mit Shift + Rechtsklick entfernt. Es gibt keine Friend-Teleports oder ein größeres Social-Hub-Menü.

```yaml
name: Vapee
first-join: 123456
last-join: 123456
settings:
  scoreboard: true
  sounds: true
  private-messages: true
  friend-requests: true
  friend-presence-notifications: false
  lobby-players-visible: true
  visibility:
    show-friends: false
    show-staff: false
    show-added-users: false
    show-game-participants: false
    added-players: []
economy:
  coins: 2500
social:
  ignored:
    - "550e8400-e29b-41d4-a716-446655440000"
rewards:
  online:
    processed-playtime-ticks: 123456
quests:
  daily:
    cycle-id: "2026-09-22"
  active:
    example_quest:
      progress: 4
      status: ACTIVE
```

Das `SocialModule` wird unmittelbar nach dem `PlayerModule` aktiviert und stellt `/ignore <player>`, `/unignore <player|uuid>` und `/ignorelist` mit der gemeinsamen Permission `vapeecore.social.ignore` bereit. Neu ignoriert werden ausschließlich exakt benannte Online-Spieler; dabei werden weder partielle Namen noch Bukkit-`OfflinePlayer`-Lookups verwendet. Unignore arbeitet ausschließlich gegen die eigene gespeicherte Ignore-Liste und kann bekannte Offline-Spieler über deren VapeeCore-Playerdatei oder als UUID auflösen. Namen dienen nur der Anzeige und Eingabeauflösung, persistente Wahrheit bleiben die UUIDs unter `social.ignored`.

Die Ignore-Beziehung ist einseitig. Wenn A B ignoriert, sieht A keine globalen Chatnachrichten von B; B sieht A weiterhin, solange B A nicht ebenfalls ignoriert. Papers veränderbare `AsyncChatEvent`-Viewer-Menge wird pro Player-Audience gefiltert, während Console und andere Audience-Typen erhalten bleiben. Dafür verwendet `SocialService` eine `ConcurrentHashMap<UUID, Set<UUID>>` mit unveränderlichen Sets. Der Async-Chatpfad liest weder den nicht threadsicheren Player-Cache noch Dateien. Die Player-YAML in `CorePlayer.getSocial()` bleibt alleinige persistente Wahrheit; der Snapshot wird nur nach erfolgreichem Save vollständig ersetzt.

Ignore blockiert außerdem private Nachrichten in beiden Richtungen und gilt automatisch auch für `/reply` beziehungsweise `/r`, weil Replies denselben Sendepfad verwenden. Wer selbst ein Ziel ignoriert, erhält die klare Meldung, dass dieses Ziel ignoriert wird. Ignoriert dagegen der Empfänger den Sender, wird aus Datenschutzgründen dieselbe allgemeine Ablehnung wie bei deaktiviertem PM-Empfang ausgegeben. Ignore verändert `settings.private-messages` nicht.

Die damalige Phase 12 enthielt noch kein Friends- oder Party-System, kein Social GUI und keine `social.yml`. Außerhalb der Lobby-Visibility verändert Ignore weder Entities noch Tablist, Scoreboard, Nametags oder Join-/Quit-Meldungen. In der zentralen Visibility-Policy ist eine Ignore-Beziehung in beliebiger Richtung dagegen ein harter beidseitiger Entity-Privacy-Block. `SocialModule` ist ein normales `CoreModule` und kein `ReloadParticipant`; Social-Playerdaten werden durch `/core reload` nicht von Platte neu geladen.

Permissions, Gruppen, Primary Groups, Prefixe, Suffixe, Meta-Daten, Contexts und Vererbung werden ausschließlich von LuckPerms verwaltet. VapeeCore liest die bereits von LuckPerms aufgelösten Daten und speichert sie weder im `CorePlayer` noch in den Player-YAML-Dateien. Normale Permission-Checks erfolgen weiterhin über Bukkit/Paper.

### Ranks & Server Identity

LuckPerms bleibt die einzige Source of Truth für Rang-Mitgliedschaften, Primary Group, Display Name, Prefix, Suffix, Weight, Tracks und Permissions. VapeeCore erzeugt keine eigene Rank-Persistence und bietet keine Promote-/Demote- oder Rank-Mutationscommands. Das `RankModule` liest bereits geladene LuckPerms-User und aktuelle Group-/Trackdaten ohne blockierende User-Loads oder dauerhaften Rank-Cache.

Der öffentliche Rank-Track wird in `plugins/VapeeCore/config.yml` über `ranks.track` konfiguriert; Default ist `ranks`. `/ranks` zeigt ausschließlich dessen Gruppen und bewahrt die LuckPerms-Track-Reihenfolge. `/rank [player]` zeigt den Primary Rank eines Online-Spielers. Ein öffentlicher Rank sollte in LuckPerms einen Display Name besitzen; andernfalls erzeugt VapeeCore einen neutralen Fallback aus der Group-ID. Die optionale Beschreibung kommt aus `vapeecore.rank.description`, die dynamische Adventure-Farbe aus `vapeecore.rank.color`. Named Colors und sechsstellige Hexwerte wie `#c35cff` werden unterstützt; fehlende oder ungültige Farben fallen neutral auf Weiß zurück. Die Rank-Darstellung enthält kein festes Mapping von Rank-Namen oder Farben—auch ein später ergänzter Rank funktioniert allein über LuckPerms Display Name, Meta und Track. Die vier Staff-Schutzdefaults sind separat in StaffHierarchyConfig definiert.

Feature-Zugriff richtet sich weiterhin nach Permissions, nicht nach einem Rank-Namen. Zusätzlich schützt Phase 26A Moderations-Targets und Phase 26B sensible administrative Online-Ziele anhand der separat konfigurierten primären Staff-Gruppe; ein höherer Rang gewährt niemals eine Command-Permission. Builder-Funktionen bleiben beispielsweise an `vapeecore.utility.build` gebunden. Spätere Mine-Rechte verwenden `vapeecore.mine.<mine>`; Reward-Multiplikatoren könnten in einer späteren Phase über Permissions oder LuckPerms-Meta ergänzt werden, ohne Namen wie VIP oder Admin im Java-Code abzufragen. Phase 16C implementiert weder Mine noch Coin-Multiplikator.

### Reward Foundation

Das `RewardModule` beantwortet zentral, **wie** positive Gameplay-Coin-Rewards angewendet und gespeichert werden. Das aufrufende Feature entscheidet weiterhin, **wann** und **warum** ein Reward entsteht. Dadurch bleiben spätere Mine-, Quest-, Onlinezeit-, Activity-, Event- und Achievement-Systeme voneinander unabhängig und reichen nur UUID, exakten Coin-Betrag, `RewardSource` und einen internen Grund an den `RewardService` weiter.

Ein erfolgreicher Grant aktualisiert das geladene Wallet sofort, markiert aber lediglich die Player-UUID als dirty. Genau ein gemeinsamer synchroner Task persistiert diese Player höchstens einmal pro Sekunde; Quit wird vor dem regulären Player-Unload geflusht, und beim Shutdown läuft der Reward-Flush vor Economy und Player. Ein Save-Fehler bleibt isoliert, lässt die UUID für einen späteren Retry dirty und rollt den bereits sichtbaren Reward nicht zurück. Bei einem harten Prozessabbruch umfasst das absichtliche Verlustfenster daher ungefähr eine Sekunde.

Direkte Admin-Mutationen über `EconomyService#setCoins`, `addCoins` und `removeCoins` speichern weiterhin sofort und rollen bei Save-Fehlern zurück. Feature-Code darf den internen Deferred-Pfad des EconomyService nicht direkt verwenden, sondern muss Gameplay-Rewards über `RewardService` vergeben. Die Foundation besitzt weiterhin keine eigene Config, Datei, History, Ledger, Offline-Queue oder Multiplikatoren; OnlineReward und Quest verwenden sie als voneinander unabhängige Consumer.

### Online / Playtime Rewards

`OnlineRewardModule` liest einmal pro Sekunde für alle Online-Spieler `Statistic.PLAY_ONE_MINUTE`. Der historische Statistikname liefert Ticks; dieselbe Quelle bleibt unverändert hinter Presentation-`<playtime>`. Standardmäßig werden pro kumulierten 60 Minuten exakt 250 Coins über `RewardService` mit `RewardSource.PLAYTIME` vergeben. Die Zeit muss nicht in einer Session gesammelt werden und gilt unabhängig von Welt, Lobby, Activity, Blackjack, Rank, Mine oder Quest.

Persistiert wird ausschließlich `rewards.online.processed-playtime-ticks`. Der unverarbeitete Fortschritt ergibt sich aus aktueller Minecraft-Spielzeit minus diesem Wert. Beim ersten Lauf eines Legacy-Spielers wird die aktuelle Statistik nur als Baseline gesetzt; auch hunderte vorhandene Stunden erzeugen daher keine rückwirkende Auszahlung. Nach einem erfolgreichen Reward wird der verarbeitete Wert nur um vollständige Intervalle erhöht, sodass Restzeit Sessions und Serverneustarts überlebt. Mehrere gleichzeitig fällige Intervalle werden in einem einzigen Reward-Grant zusammengefasst.

Die Einstellungen `online-rewards.enabled`, `interval-minutes`, `coins` sowie `message.enabled` und `message.format` liegen in `config.yml` und wirken nach `/core reload`, ohne einen sechsten Reload-Teilnehmer. Defaults sind 60 Minuten, 250 Coins und eine MiniMessage-Benachrichtigung mit `<coins>`, `<minutes>`, `<intervals>` und `<balance>`. Bestehende Live-Dateien benötigen den Block nicht; fehlende Keys verwenden interne Defaults und werden nicht automatisch in die Datei geschrieben.

Bei `enabled: false` wird die Baseline im Speicher regelmäßig auf die aktuelle Statistik synchronisiert, damit deaktivierte Zeit später nicht nachgezahlt wird. Ein Statistik-Reset rebased ebenfalls ohne Coins. Phase 16C besitzt bewusst kein AFK-System: Minecraft-Spielzeit zählt aktuell auch verbundene AFK-Zeit. Es gibt keine Permission, keinen Command, Claim-Button, Daily-/Session-Limit, Zufall, Rank-Multiplikator oder Kopplung an Mine und Quest.

### Quest Foundation

Phase 17A trennt die Frage „Ist eine Quest erfüllt?“ konsequent von der Reward Foundation. Immutable `QuestDefinition`s beschreiben technische ID, sichtbaren Namen und Beschreibung, einen generischen `QuestProgressKey`, `long`-Target und einen positiven Coin-Reward. Seit Phase 29 melden Playtime und Blackjack positive Fortschrittsmengen für `playtime:minute` und `blackjack:win` über `QuestProgressReporter`; der Domain-Service kennt diese Quellen nicht. Wildcards und hardcodierte Quest-Typ-Enums gibt es nicht.

`QuestDefinitionRegistry` hält immutable, deterministische Snapshots und ersetzt einen Katalog erst nach vollständiger Duplicate-Validierung. `DailyQuestModule` lädt den Produktionskatalog aus `daily-quests.yml`; die Datei enthält standardmäßig noch keine Definitionen. `QuestService` kann geladene Spieler-Quests zuweisen, atomisch ersetzen oder leeren, mehrere passende Quests mit einem Signal aktualisieren und Fortschritt overflow-sicher am Target cappen. Eine Completion zahlt automatisch exakt den definierten Betrag über `RewardService` mit `RewardSource.QUEST` und `quest:<id>` aus. Erfolg führt zu `COMPLETED`; ein normaler Fehler oder eine unerwartete Reward-Ausnahme erhält Target-Fortschritt als `REWARD_PENDING`, der kontrolliert erneut versucht werden kann. Completed Quests ignorieren weitere Signale und werden nicht doppelt belohnt.

Aktuelle Assignments werden ohne Definition-Snapshot unter `quests.active.<id>.progress|status` im bestehenden Player-Profil gespeichert. Fehlende Legacy-Sections sind leer; beschädigte einzelne optionale Einträge werden mit Warning übersprungen, und unbekannte Definitionen bleiben gespeichert, werden aber weder fortgeschrieben noch belohnt. Quest-State ist sofort im `CorePlayer` sichtbar, während ein gemeinsamer 100-Tick-Task dirty UUIDs bündelt. Quit und Shutdown flushen kontrolliert; ein harter JVM-/OS-Abbruch kann höchstens ungefähr fünf Sekunden ungeflushten Fortschritt verlieren. Da Reward- und Quest-Flush immer den gesamten aktuellen `CorePlayer` speichern, schreiben sie keine veralteten Teil-Snapshots zurück.

Phase 17B ergänzt `plugins/VapeeCore/daily-quests.yml` mit standardmäßig deaktiviertem Daily-System, vier Slots pro Tag, konfigurierbarer Reset-Zeit und Zeitzone sowie frei definierbaren Quests. Die Auswahl nutzt einen stabilen SHA-256-Wert aus Spieler-UUID, Cycle-Datum und Quest-ID: Ein Restart oder Hard Crash erzeugt für denselben Katalog dieselbe Auswahl. `quests.daily.cycle-id` im Player-Profil verhindert einen Reroll im selben Cycle; verpasste Tage werden nicht nachgeholt. Vor der Rotation werden offene Rewards erneut versucht; ungelöste oder unbekannte `REWARD_PENDING`-Quests blockieren den Reset, statt einen Reward zu verlieren. Ein gemeinsamer 1200-Tick-Task und Join-Sync erledigen die Zuweisung; der vorhandene Quest-Flush speichert Cycle und Assignments zusammen. Phase 29 ergänzt `/quests`, ein geschütztes read-only Menü, Completion-Nachrichten und genau die Producer `playtime:minute` und `blackjack:win`. Inhalte und Balance bleiben Betreiberkonfiguration; Default-Definitionen und Mine-Producer bleiben aus.

Das `LobbyModule` verwendet die separate Datei `plugins/VapeeCore/lobby.yml`. `/setspawn` speichert dort den Lobby-Spawn mit Weltname, Position und Blickrichtung; `/spawn` teleportiert Spieler dorthin. `player.gamemode` bestimmt den normalen Lobby-Gamemode und verwendet bei ungültigen Werten sicher `ADVENTURE`. `LobbyPlayerStateService` besitzt den nicht persistenten Zustand `NORMAL`/`BUILD`, normalisiert Gamemode und Inventory und erzeugt in `NORMAL` die Lobby-Hotbar. `/build` liegt im `UtilityModule`, ist auf die Lobby beschränkt, mit Activities gegenseitig exklusiv und verwendet immer `CREATIVE`. Nur der aktive BUILD-Zustand umgeht Block- und Item-Schutz; die Permission `vapeecore.utility.build` erlaubt ausschließlich das Command. `vapeecore.lobby.build` bleibt nur als deprecated Permission-Parent zur Migration erhalten.

### Utility Commands

Das `UtilityModule` stellt zwölf granulare Staff-/Builder-Commands bereit: `/build`, `/fly`, `/speed`, `/gamemode` (`/gm`), `/tp` (`/teleport`), `/tphere`, `/heal`, `/feed`, `/ping`, `/clear`, `/invsee` und `/enderchest`. `/tp` teleportiert zu Online-Spielern oder XYZ-Koordinaten, unterstützt absolute Werte, relative `~`- und lokale `^`-Koordinaten, optionale Rotation und explizite geladene Welten; `/tphere` holt einen Online-Spieler zum Sender. Player-Ziele werden case-insensitive, aber ausschließlich als vollständige Namen aus der aktuellen Online-Spielermenge aufgelöst. Self-, Others- und World-Rechte bleiben strikt getrennt; `teleport.bypass` umgeht nur interne VapeeCore-State-Guards. Activity-/BUILD-Inventare werden nicht blind gelöscht, und `/invsee` verwendet einen vollständig geschützten read-only Snapshot statt des fremden Live-Inventars. Die vollständige Permission- und Rangmatrix steht in [docs/PERMISSIONS.md](docs/PERMISSIONS.md), Ownership und Lifecycle im Developer Guide.

### Activity Foundation

Das nach `SettingsModule` gestartete `ActivityModule` stellt mit dem `ActivityService` vier ausschließlich zur Laufzeit geführte Registries bereit: Activity-Typen, Venues, Sessions und die globale Zuordnung `Player-UUID → Session-UUID`. Direkt beim Enable des ActivityModule bleiben alle Registries leer; das später gestartete BlackjackModule registriert seinen Typ und die aus `blackjack.yml` aktivierten Venues und Sessions. Der frühere, ausschließlich für die Compass-Navigation verwendete `ActivityCatalog` samt `ActivityEntryPoint` wurde in Phase 15A entfernt. Mutierende Service-Operationen sind auf den primären Server-Thread begrenzt. Es gibt weder Reflection-Scans noch statische Registries oder Activity-, Venue- beziehungsweise Session-Persistence und keine neuen Keys in den Player-YAML-Dateien.

Ein `ActivityType` besitzt einen stabilen Key nach `[a-z0-9_-]+`, eine minimale und maximale Teilnehmerzahl und erzeugt eine konkrete `ActivitySession` für eine UUID und ein bereits registriertes `ActivityVenue`. Typen müssen explizit vor ihren Venues registriert werden. Ein Typ kann erst entfernt werden, wenn keine zugehörigen Venues oder Sessions mehr existieren. Ein Venue ist über `activityKey + id` eindeutig, gehört genau einem Typ und kann gleichzeitig höchstens eine Session reservieren. Es kann erst entfernt werden, nachdem diese Session geschlossen wurde.

`ActivityPosition` speichert Weltname, Koordinaten und Blickrichtung als finite Werte und löst eine Bukkit-`Location` nur auf, wenn die Welt bereits geladen ist. `ActivityArea` ist ein normalisierter, achsenparalleler und weltgebundener Quader. `ActivityVenue` verbindet eine solche Area mit einer repräsentativen Anchor-Position; Anchor und Area müssen dieselbe Welt verwenden und der Anchor muss innerhalb der Area liegen. Das Framework lädt oder erzeugt keine Welten automatisch.

`ActivitySession` ist die abstrakte Basis konkreter Activities. Sie speichert Session-UUID, Activity-Key, Venue, Erstellungszeitpunkt, Lifecycle-State und immutable Teilnehmer-Snapshots. `ActivityParticipant` enthält ausschließlich UUID und Beitrittszeitpunkt; dauerhafte Bukkit-`Player`-Referenzen werden nicht gehalten. Ein Spieler kann serverweit höchstens einer Activity-Session angehören. `ActivityService.joinSession` prüft Online-Status, geladenen `CorePlayer`, Membership, Session-State, Kapazität und Venue-Welt. Das Framework teleportiert nicht, ändert keinen GameMode und manipuliert weder Inventar, Rüstung, Hotbar noch Lobby-Items.

Der absichtlich kleine Lifecycle lautet `AVAILABLE → ACTIVE → RESETTING → AVAILABLE`; aus `AVAILABLE`, `ACTIVE` und `RESETTING` ist zusätzlich der terminale Übergang nach `CLOSED` erlaubt. Alle anderen Übergänge werden abgelehnt. Der Service besitzt die State-Transitions; konkrete Sessions können den State nicht frei setzen. Aktivierung verlangt mindestens die konfigurierte Teilnehmerzahl. Verlässt während `ACTIVE` ein Teilnehmer die Session und fällt sie dadurch unter dieses Minimum, führt der Service kontrolliert `ACTIVE → RESETTING → AVAILABLE` aus. Ein normaler Reset entfernt die verbleibenden Teilnehmer nicht.

Konkrete Sessions erhalten nur die kleinen Hooks `onParticipantJoined`, `onParticipantLeft`, `onActivated`, `onReset` und `onClosed`. Über `trackTask` und `untrackTask` gehören Activity-eigene Bukkit-Tasks direkt ihrer Session. Reset und Close canceln alle getrackten Tasks und leeren die Task-Sammlung. Schlägt Aktivierung, Reset oder Task-Cleanup fehl, wird die betroffene Session sicher geschlossen; ein Join-Hook-Fehler rollt Participant und Membership zurück. Leave- und Close-Hooks laufen best-effort, sodass ein fehlerhafter Hook die Bereinigung anderer Teilnehmer, Memberships, Registries und der Venue-Reservierung nicht verhindert.

Der kleine `ActivityListener` behandelt ausschließlich Quit und World Change. Quit entfernt eine vorhandene Membership mit `DISCONNECT`; ein Wechsel aus der Venue-Welt entfernt sie mit `WORLD_CHANGE`. Ein Wechsel innerhalb derselben Welt bleibt unangetastet. Es gibt bewusst keinen globalen `PlayerMoveEvent`-Listener und keinen globalen Activity-Tick-Task. Beim Modul-Shutdown werden alle Sessions über denselben zentralen Safe-Close-Pfad geschlossen, danach Memberships, Venue-Reservierungen, Venues und Typen geleert.

Activity und Game bleiben getrennte Konzepte. Das Activity-Framework besitzt keine Economy-, Social-, Settings- oder Presentation-Abhängigkeit, kein Inventory- oder Player-Snapshot-System, kein universelles Countdown-, Winner-, Team-, Spectator-, Arena-Reset- oder Matchmaking-System. Konkrete Activities injizieren nur ihre tatsächlich benötigten Module. Das matchbasierte Game-Framework folgt separat in Phase 16.

### Community Seating & World Display Foundation

`SeatModule` besitzt die gemeinsame physische Seat-Infrastruktur. Ein leerer Main-Hand-Rechtsklick setzt normale Lobby-Spieler auf freie Bottom-Stairs sowie Bottom-/Top-Slabs; Sneaken, BUILD, laufende Activities, registrierte Activity-Areas, bereits belegte Sitze, bestehende Vehicles und blockierter Kopfraum werden ignoriert. Top-Stairs und Double-Slabs sind bewusst keine Casual-Sitze. Die Laufzeitdaten sind weder persistent noch konfigurierbar und benötigen keine Permission oder `/sit`-Command.

`SeatService` unterscheidet `CASUAL` und `MANAGED`, erzwingt genau einen Seat pro Spieler und einen Spieler pro Key und besitzt Reservation, Mount, Dismount-Guard, PDC-Markierung sowie Startup-/Shutdown-Cleanup. `BlackjackSeatService` reserviert bei modernen Tischen exakt den angeklickten Sitz; nur Legacy-Tische verwenden weiterhin die niedrigste freie Sitznummer. Alte `blackjack_seat`-Entities werden während der Migration weiterhin entfernt.

`WorldDisplayModule` stellt einen runtimebasierten, keyed Lifecycle für native `TextDisplay`- und `ItemDisplay`-Entities bereit: Erzeugen, typisierte Updates, Teleport, Einzel-/Owner-Entfernung und stale Cleanup. Es gibt keine Demo-Displays, Persistenz, Commands, Tick-Abfrage, Packet-Library oder harte Abhängigkeit von einem Hologramm-Plugin. Externe Hologramm-Plugins können unabhängig für statische Admin-Hologramme verwendet werden; dynamische VapeeCore-Features nutzen diese native Foundation.

### Blackjack Activity

Das `BlackjackModule` ist die erste konkrete Activity und wird nach Seat und WorldDisplay sowie vor Warp und LobbyExperience aktiviert. Es bezieht `JavaPlugin`, `ActivityModule`, `SeatModule`, `WorldDisplayModule`, `LobbyModule`, `MessageService` und `CommandHelpRenderer`. Sein `BlackjackActivityType` verwendet den Key `blackjack`, `minParticipants = 1` und `maxParticipants = 5`. Spieler im BUILD-Modus werden vor jeder Sitzreservierung abgewiesen. Moderne Tische werden mit leerer Main Hand direkt über den konkreten Sitzblock betreten; alte Konfigurationen bleiben über ihren Interaktionsblock und die niedrigste freie Sitznummer kompatibel.

Eine Solo-/Public-Auswahl gibt es nicht mehr: Ein Teilnehmer spielt automatisch allein, mehrere Teilnehmer spielen gemeinsam gegen denselben Dealer. Ein Spieler kann sofort `Deal` drücken; Queue, Countdown oder zweite erforderliche Person existieren nicht. Solange eine Session `AVAILABLE` ist, werden bis zur tatsächlichen Anzahl konfigurierter Sitze weitere Spieler aufgenommen. Sobald jemand `Deal` drückt und der Tisch `ACTIVE` ist, sind Mid-Round-Joins kontrolliert gesperrt. Die globale Obergrenze bleibt fünf, ein Tisch mit drei Sitzen besitzt praktisch aber Kapazität drei.

Die physische Blockstruktur eines Tisches gehört dem Map-Builder und wird manuell gebaut. VapeeCore speichert weder Tischblöcke noch Schematics, repariert oder regeneriert keine Struktur und benötigt dafür weder Resource Pack noch Furniture-System. `plugins/VapeeCore/blackjack.yml` enthält nur den Gameplay-Bereich, Dealer, Sitze und den optionalen `display`-Anchor als Mittelpunkt, Oberflächenhöhe und Hauptausrichtung der echten Spielfläche. `/blackjack setup display <id>` ermittelt die Oberkante des anvisierten Blocks aus dessen Collision Shape; beim Dealer-Setup steht der Admin an der Dealerposition und schaut zum Tisch. `/blackjack setup preview <id>` zeigt für zwölf Sekunden Center, Floating Dealer Hand, Status- und vorhandene Sitz-Kartenpositionen, auch bei disabled oder teilweise konfigurierten Drafts.

Das Admin-Setup erfolgt mit `vapeecore.blackjack.admin`; `/blackjack help` und `/blackjack setup help` zeigen die verfügbaren Aktionen einzeln und in Workflow-Gruppen. Neue Drafts sind disabled. Positionen und Sitze können nur im disabled Zustand bearbeitet werden; `enable` validiert und aktiviert atomar, `disable` ist nur bei `AVAILABLE` und null Teilnehmern erlaubt, und Löschen setzt ein vorheriges Disable voraus. Änderungen werden sofort per temporärer Datei und Atomic-Move-Fallback gespeichert.

Beim Beitritt werden Sitz, Activity-Membership, Mount und Inventory-Ownership transaktional übernommen. `LobbyPlayerStateService` gibt das NORMAL-Inventar an `BlackjackInventoryService` ab; BUILD bleibt ausgeschlossen. Die Activity-Hotbar zeigt abhängig vom Rundenzustand Deal, Hit, Stand, Double, Status und Leave und verwendet den PDC-Key `blackjack_action`. Nach der dreisekündigen Ergebnisansicht wechselt die Session sauber zu `AVAILABLE`; erst der neue Post-Reset-Hook aktualisiert Hotbar und Weltanzeige, sodass derselbe sitzende Teilnehmer ohne Dismount sofort erneut Deal drücken kann. Drop, Inventory-Move, Number-Key, Drag, Offhand-Swap und Pickup sind ausschließlich während der Teilnahme geschützt. Leave stellt NORMAL in der Lobby wieder her; Quit, World Change, Tod und Plugin-Shutdown bereinigen ohne unerwünschtes Reapply. Tod ist ein generischer `ActivityLeaveReason.DEATH`.

Eine Runde teilt in deterministischer Join-Reihenfolge aus: je eine Karte an alle Teilnehmer, eine an den Dealer, je eine zweite an alle Teilnehmer und die verdeckte Hole Card des Dealers. Unterstützt werden Hit, Stand und Double Down sowie Natural Blackjack, Bust, Win, Loss und Push. Double ist nur im eigenen ersten Zug mit exakt zwei Karten und ohne Natural möglich, zieht genau eine Karte und steht automatisch. Der Dealer zieht unter 17 und steht auch auf Soft 17; sind alle Spieler bereits Bust oder sichere Naturals, wird ohne bedeutungsloses Dealer-Ziehen direkt abgerechnet. Der Six-Deck-Shoe bleibt ein unveränderter Fisher-Yates-Shuffle mit `Random`, ohne Pity-, Seed- oder Win-Chance-Manipulation. Split, Insurance, Surrender und Side Bets sind nicht enthalten.

`BlackjackWorldViewService` rendert Status, Werte, Ergebnisse und Karten ausschließlich als native `TextDisplay`-Entities. Playerkarten bleiben unverändert als einzelne horizontale Karten auf der Table Surface. Der Dealer verwendet dagegen genau ein aufrechtes `dealer-hand`-Display vor seiner gespeicherten Position: Während des Spielerzugs bleiben Hole Card und Gesamtwert verborgen, Reveal und weitere Dealer-Karten aktualisieren dieselbe Entity. Abstand, Höhe und Größe liegen zentral in `BlackjackDisplayGeometry`; Production und Preview verwenden dieselbe Positionsberechnung. Produktions-Owner `blackjack:<tableId>` und Preview-Owner `blackjack-preview:<adminUuid>:<tableId>` sind strikt getrennt.

Phase 15A ist ausdrücklich `Free Play`: Blackjack importiert weder `EconomyModule` noch `EconomyService`, verändert keine Coins und besitzt keine Einsätze, Payouts oder Escrow-Logik. `blackjack.yml` persistiert ausschließlich den physischen Tischaufbau; Player, Sitz, Session, Runde, Hände und Shoe bleiben reine Runtime-Daten. Das bestehende Player-YAML-Schema bleibt unverändert. Echte Coin-Wetten benötigen später eine transaktionale Escrow- und Crash-Recovery-Strategie, damit ein Serverausfall keinen Einsatz verlieren oder verdoppeln kann.

Das als letztes gestartete `LobbyExperienceModule` bezieht den vom `LobbyModule` besessenen `LobbyItemService` und den zentralen Service des `VisibilityModule`. Es verarbeitet die Interaktionen der drei PDC-markierten Hotbar-Items: Warp Navigator in Slot 0 (`COMPASS`), Player-Visibility in Slot 4 (`LIME_DYE` oder `GRAY_DYE`) und Settings in Slot 8 (`COMPARATOR`). Das Setzen und Entfernen der normalen Hotbar gehört dagegen dem `LobbyPlayerStateService`: `NORMAL` bereinigt das vollständige Player-Inventory und erzeugt die drei Items deterministisch, `BUILD` entfernt sie und stellt ein temporäres Creative-Inventory bereit. Drop, Offhand-Swap, Click-, Shift-, Number-Key-, Double-Click- und Drag-Manipulationen der PDC-markierten Items werden verhindert. Der Visibility-Schalter besitzt einen kurzen Server-Cooldown; Block-Rechtsklicks werden für die Item-Aktualisierung um einen Tick verschoben, damit Boden-Interaktionen nicht doppelt oder verloren verarbeitet werden.

Das eigenständige `WarpModule` hängt nur von `JavaPlugin` und `MessageService` ab. `plugins/VapeeCore/warps.yml` beginnt mit `warps: {}`; es gibt keine vordefinierten, reservierten oder besonders behandelten Lobby-, Spawn-, Casino- oder Blackjack-Ziele. Administratoren pflegen beliebige IDs nach `[a-z0-9_-]+`; `/warp` und `/warp help` zeigen die verfügbaren Aktionen strukturiert. Neue Warps erhalten generisch einen Namen aus der ID und `ENDER_PEARL`; erneutes Setzen ändert nur die Position. Persistente Änderungen werden vor dem Runtime-Austausch atomar geschrieben, und Teleports verwenden `TeleportCause.PLUGIN`, ohne Welten zu laden.

Der Compass öffnet ein geschütztes dynamisches 54-Slot-Inventar, das ausschließlich `WarpService.getNavigatorWarps()` plus technische Controls rendert. Bis zu 45 sichtbare, nach Order und ID sortierte Ziele erscheinen pro Seite; Previous (45), Page Info (49), Close (50) und Next (53) behalten ihre Slots. Null sichtbare Warps zeigen `No Destinations Available`, auch wenn hidden Warps gespeichert sind. Der ownergebundene `NavigatorInventoryHolder` speichert Seite, exaktes Inventar und die konkrete `slot → warpId`-Zuordnung; Active-Tracking und aktuelle Lobby-/Activity-Prüfungen sichern jeden Zugriff. Die normale Player-Lore enthält nur `Click to teleport.`, keine technische Warp-ID. Titel- oder Material-Erkennung und feste Kategorien existieren nicht. Das Settings-Hotbar-Item öffnet weiterhin das vorhandene `SettingsMenu`; `/settings` und alle übrigen bestehenden Commands bleiben unverändert verfügbar.

Player-Visibility ist pro Viewer und ausschließlich auf andere Spieler in der Lobby-Welt begrenzt. Der bestehende Master-Key `settings.lobby-players-visible` zeigt im Zustand `true` grundsätzlich alle zulässigen Lobby-Spieler; im Zustand `false` greifen die aktivierten Filter für Friends, Staff und explizit hinzugefügte UUIDs. Eine Ignore-Beziehung in beliebiger Richtung blockiert beide Sicht-Richtungen immer, auch wenn Master oder Filter passen. Friends stammen direkt aus `FriendService`; Staff wird ausschließlich mit der Marker-Permission `vapeecore.visibility.staff` klassifiziert. Game Participants erscheinen transparent als `Unavailable`, weil der Production-Provider bis zur späteren Game-Integration ausdrücklich `false` bleibt.

Das `VisibilityModule` besitzt Policy, Paper-Anwendung und die von VapeeCore gesetzten Hide-Zustände. Join, Lobby-Eintritt und Respawn synchronisieren eventgetrieben; der Hotbar-Master-Toggle und das Settings-Menü persistieren sofort, wenden die neue Viewer-Präferenz direkt an und halten das Item zwischen `Players: Visible` und `Players: Filtered` synchron. Erfolgreiches Accept/Auto-Accept/Remove einer Freundschaft sowie Ignore/Unignore berechnen das betroffene Online-Lobby-Paar sofort in beide Richtungen neu; kleine domain-neutrale Listener vermeiden dabei eine Friend-/Social-Abhängigkeit auf Visibility. Beim Verlassen, Quit und Plugin-Disable werden nur VapeeCore-eigene Zustände mit `showPlayer(plugin, target)` restauriert. Es gibt weder globales World-Visibility noch einen periodischen Scheduler. Alte Player-Dateien ohne `settings.visibility` bleiben gültig; fehlerhafte optionale Filterwerte warnen und fallen sicher zurück. Added-Player-UUIDs werden ohne Namen deterministisch sortiert gespeichert, Duplikate zusammengeführt und ungültige oder eigene UUIDs beim Laden ignoriert. Jede Mutation speichert sofort und rollt bei einem Save-Fehler den vorherigen Domainzustand zurück.

Join- und Quit-Nachrichten werden ebenfalls aus der bestehenden `lobby.yml` gelesen:

```yaml
messages:
  join:
    enabled: true
    format: "<dark_gray>[<green>+<dark_gray>] <white><name>"
  quit:
    enabled: true
    format: "<dark_gray>[<red>-<dark_gray>] <white><name>"
```

Bei `enabled: false` wird die jeweilige Nachricht vollständig unterdrückt; es gibt keinen Vanilla-Fallback. `LobbyMessageService` rendert `<name>` als Adventure Component, protokolliert Laufzeitfehler und liefert einen sicheren Component-Fallback. Fehlende Keys verwenden interne Defaults. Ungültige Templates erzeugen eine Warnung und verwenden einen sicheren internen Default, ohne `lobby.yml` zu verändern. Da `LobbyModule` weiterhin alleiniger Reload-Teilnehmer für `lobby.yml` ist und LobbyExperience dessen Message-Service bezieht, gelten Änderungen nach `/core reload` ohne einen sechsten Reload-Teilnehmer.

Das `ChatModule` formatiert den globalen Chat über Papers `AsyncChatEvent` und einen pro Event neu erzeugten viewer-unabhängigen `ChatRenderer`. Das Format liegt in `plugins/VapeeCore/chat.yml`; LuckPerms-Prefix und -Suffix können dort als `legacy-ampersand`, `mini-message` oder `plain` interpretiert werden. Zusätzlich stehen `<rank>` für den farbigen freundlichen Display Name, `<rank_name>` für den Player Display Name in Rank-Farbe sowie `<rank_id>` und der Compatibility-Alias `<group>` für die rohe Primary Group bereit. `<name>` bleibt unverändert. Der Async-Pfad liest ausschließlich bereits geladene LuckPerms-Daten und startet keine blockierenden Loads oder Statistikabfragen. Das Serverformat ist MiniMessage, die originale Playernachricht wird jedoch als Adventure Component eingesetzt und niemals als MiniMessage ausgewertet. Das Resource- und Java-Defaultformat ist `<rank_name><dark_gray> » </dark_gray><white><message></white>`; Prefix und Suffix bleiben optional nutzbar.

Das `PrivateMessageModule` stellt `/msg <player> <message>` sowie `/reply <message>` mit dem Alias `/r` für ausschließlich online befindliche Spieler bereit. Darstellung und globaler Aktivierungsstatus liegen in `plugins/VapeeCore/private-messages.yml`. Spielertext wird als sichere Adventure Component eingesetzt und weder als MiniMessage noch als Legacy-Farbcode ausgewertet. `PlayerSettings.privateMessagesEnabled` bedeutet ausschließlich „private Nachrichten empfangen“: Ein Spieler mit deaktiviertem Empfang darf weiterhin selbst schreiben. Der letzte erfolgreiche Gesprächspartner wird nur für die aktuelle Session im Speicher gehalten; ein Quit entfernt alle zugehörigen Reply-Verweise. Es gibt bewusst keine Offline-Nachrichten, Mailbox oder SocialSpy.

Das `PresentationModule` aktualisiert über einen gemeinsamen synchronen Task standardmäßig einmal pro Sekunde die persönliche Sidebar und Tablist. Die Templates liegen in `plugins/VapeeCore/presentation.yml` und verwenden Adventure/MiniMessage mit sicheren Component-Platzhaltern für `<server>`, `<name>`, `<rank_name>`, `<prefix>`, `<suffix>`, `<rank>`, `<rank_id>`, den rückwärtskompatiblen `<group>`-Alias, `<playtime>`, `<coins>`, `<online>` und `<max_players>`. `<rank>` ist der farbige freundliche Display Name; `<rank_name>` ist der Player Display Name in derselben Farbe; `<rank_id>` und `<group>` bleiben die rohe Primary Group. `<playtime>` liest synchron `Statistic.PLAY_ONE_MINUTE`, interpretiert den Minecraft-Wert korrekt als Ticks und formatiert ihn ohne eigene Persistence auf höchstens zwei Einheiten. Die Sidebar nutzt im Resource-Default ein Label/Wert-Layout für Coins, Rank und Playtime; das Tablist-Namensformat ist `<rank_name>`. Bereits vorhandene Live-Dateien werden durch den Resource-Default nicht überschrieben und müssen für die Rank-Farben bei Bedarf manuell angepasst werden.

Das `SettingsModule` stellt `/settings` für Spieler mit `vapeecore.settings.use` bereit. Die überarbeitete 54-Slot-Übersicht zeigt Scoreboard, VapeeCore-eigene Interface-Sounds, private Nachrichten, Freundschaftsanfragen und Friend Presence als Feature-Icons mit direkt darunterliegenden grünen Enabled- oder roten Disabled-Panes. Friend Presence liegt als `BELL` in Slot 33 mit Status in Slot 42 und startet opt-in deaktiviert. Icon und Status schalten dieselbe Einstellung um; Toggles und Refresh aktualisieren das vorhandene Inventar. Die separate Player-Visibility-Karte zeigt grün `All Players` oder gelb `Filtered` und öffnet die Visibility-Unterseite. Dort werden Master, Friends, Staff und Added Users im gleichen Feature-/Status-Muster eingestellt; Game Participants bleibt bis zur echten Integration grau `Unavailable`. `Manage Visible Players` zeigt bekannte Namen, farbigen aktuellen Online-Status, UUID-Fallback und 45 Einträge pro Seite; Rechtsklick entfernt über die serverseitige Slot→UUID-Zuordnung. Der Add-Button schlägt `/settings visibility add ` vor. Alle drei Ansichten besitzen exaktes Owner-/Holder-/Inventory-Binding, Active-Inventory-Tracking und Click-/Drag-Schutz; Disable schließt eigene offene Menüs. Dieselbe Permission schützt `/settings visibility`, `add <player|uuid>` und `remove <player|uuid>`; Add akzeptiert nur bekannte Identitäten, gespeicherte stale UUIDs bleiben entfernbar. Die bestehende Persistence und `/profile [player|uuid]` bleiben erhalten. Chat Range und Game Auto-Join werden nicht angeboten; `SettingsModule` ist kein `ReloadParticipant`.

`/core reload` liest `config.yml`, `lobby.yml`, `chat.yml`, `private-messages.yml`, `presentation.yml` und `daily-quests.yml` in einer gemeinsamen zweiphasigen Transaktion neu. Zuerst werden alle sechs Dateien in dieser Reihenfolge vollständig vorbereitet und validiert; erst danach werden ihre Runtime-Zustände in derselben Reihenfolge angewendet. Ein Prepare-Fehler verändert daher keinen aktiven Zustand. Bei einem unerwarteten Apply-Fehler werden bereits übernommene Zustände rückwärts zurückgerollt. Message-Prefix, Servername, Debug-Modus, `ranks.track`, Lobby-Regeln, Chat- und PM-Format, Scoreboard-/Tablist-Templates sowie Daily-Config und Katalog werden ohne Serverneustart aktiv. Daily-Assignments ändern sich erst beim nächsten normalen Sync. Der Reload schreibt keine Configdateien, lädt keine Playerdaten und verändert weder Social-State, Economy noch LuckPerms.

Die 27 Module starten exakt in der Reihenfolge Permission → Rank → Player → Social → Economy → Identity → Moderation → Friend → Presence → Clan → Reward → OnlineReward → Quest → DailyQuest → Lobby → Visibility → Chat → PrivateMessage → Presentation → Settings → Activity → Utility → Seat → WorldDisplay → Blackjack → Warp → LobbyExperience und stoppen in umgekehrter Reihenfolge. `/core reload` besitzt genau sechs Teilnehmer (`config.yml`, `lobby.yml`, `chat.yml`, `private-messages.yml`, `presentation.yml`, `daily-quests.yml`); Presence, Moderation, Visibility, Clan, Friend, Identity, Rank, Reward, OnlineReward und Quest sind keine eigenen Reload-Teilnehmer.

### Clans (Phase 19B)

`/clan` und `/clans` öffnen die Clan-GUI; `/clan help` zeigt alle Befehle. Ein Clan wird mit `/clan create <tag> <name...>` gegründet, etwa `/clan create VAPE Vapee Community`. Mehrteilige Namen bleiben erhalten. `/clan info [tag|uuid]` zeigt Clan-Informationen, `/clan invites` die Einladungen. Clanlose Spieler können mit `/clan accept <tag|uuid>` beitreten oder mit `/clan deny <tag|uuid>` ablehnen. Owner laden über `/clan invite <player|uuid>` auch bereits bekannte Offline-Spieler ein und verwalten Einladungen mit `/clan cancel <player|uuid>`; Mitglieder nutzen `/clan leave`. Owner können `/clan kick <player|uuid>`, `/clan transfer <player|uuid>`, `/clan rename <name...>` und `/clan tag <tag>` verwenden. Das Löschen erfordert ausdrücklich `/clan disband confirm`.

Die 54-Slot-GUI zeigt Clan-Übersicht, Mitglieder und Einladungen mit 45 Einträgen pro Seite. Owner entfernen Mitglieder nur mit Shift + Rechtsklick oder übertragen Ownership mit Shift + Linksklick. Clanlose Spieler nehmen Einladungen mit Linksklick an oder lehnen sie mit Rechtsklick ab. Clan-Tags in Chat, Tablist oder Nametag gehören noch **nicht** zu dieser Phase.

Weitere Activities, Voice-System, Community-Funktionen und Minigames werden in späteren Phasen als klar abgegrenzte interne `CoreModule` innerhalb derselben VapeeCore-JAR ergänzt. Phase 17B ergänzt Daily-Quest-Auswahl und -Rotation, aber noch keine konkreten Quest-Produzenten. Mine, Shop, AFK-Erkennung, Reward-Multiplikatoren, Blackjack-Betting und weitere Blackjack-Visual-Reworks bleiben für spätere Phasen reserviert.
