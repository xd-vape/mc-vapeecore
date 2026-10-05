# VapeeCore Permissions

Diese Datei ist die kanonische Übersicht der von VapeeCore registrierten Permission-Nodes. Die Rangnamen in den Empfehlungen sind Beispiele für LuckPerms-Gruppen. Feature-Fähigkeiten bleiben Permission-basiert; seit Phase 26A wertet die separate Staff-Target-Hierarchie für Moderation und seit 26B sensible administrative Online-Ziele explizit konfigurierte primäre Group-IDs aus. `default: op` ist nur der Bukkit/Paper-Default und kein Ersatz für eine gezielte LuckPerms-Konfiguration.

## Allgemein, Lobby und Economy

| Permission | Default | Command/Funktion | Hinweis |
|---|---:|---|---|
| `vapeecore.admin` | `op` | `/core reload` (technische Administration) | Kritisches Owner-Recht; kein Wildcard-Parent für alle VapeeCore-Rechte. |
| `vapeecore.lobby.spawn` | `true` | `/spawn` | Teleport zum konfigurierten Lobby-Spawn. |
| `vapeecore.lobby.setspawn` | `op` | `/setspawn` | Verändert den persistenten Lobby-Spawn. |
| `vapeecore.lobby.build` | `op` | Deprecated Compatibility-Parent | Gewährt als Child `vapeecore.utility.build`; neuer Code prüft diesen alten Namen nicht direkt. |
| `vapeecore.economy.coins` | `true` | `/coins` | Eigene Coin-Balance anzeigen. |
| `vapeecore.economy.admin` | `op` | `/coins get\|add\|remove\|set` | Liest bekannte Online-/Offline-Wallets; verändert nur geladene Online-Wallets. Child: `vapeecore.economy.coins`. |
| `vapeecore.blackjack.admin` | `op` | `/blackjack …` | Verändert Blackjack-Tische und deren Konfiguration. |
| `vapeecore.warp.admin` | `op` | `/warp …` | Verändert persistente Warp-Ziele. |

### Economy Commands (Phase 22)

Alle `/coins`-Formen prüfen explizit `vapeecore.economy.coins`; `get/add/remove/set` zusätzlich `vapeecore.economy.admin`. Das Admin-Child gewährt den Basiszugang, nicht umgekehrt.

- `/coins`: eigene Balance, Player-only.
- `/coins help`: permission-aware Hilfe.
- `/coins get <player|uuid>`: bekannte Online- oder Offline-Balance lesen.
- `/coins add <player|uuid> <amount>`: positive ganze Coins hinzufügen.
- `/coins remove <player|uuid> <amount>`: positive ganze Coins entfernen.
- `/coins set <player|uuid> <amount>`: nichtnegative ganze Balance setzen.

Console mit Adminzugang darf alle expliziten Target-Formen verwenden. Mutationen verlangen echte Online-Präsenz und geladene Playerdaten. Vollständige bekannte Namen sind case-insensitive; mehrdeutige Namen verlangen eine UUID, unbekannte Ziele bleiben unbekannt. Completion zeigt primär Online-Targets (bei Mehrdeutigkeit UUID), begrenzt aber nicht manuelles Offline-GET. Es gibt weder Offline-Mutationen noch Player-Transfers oder `/pay`.

## Community, Settings, Rank und Profile

| Permission | Default | Command/Funktion | Hinweis |
|---|---:|---|---|
| `vapeecore.message.use` | `true` | `/msg`, `/reply`, `/r` | Private Online-Nachrichten. |
| `vapeecore.settings.use` | `true` | `/settings`, `/settings visibility …` | Eigenes Settings-Menü, Visibility-Filter und Added-Users-Liste verwalten. |
| `vapeecore.quest.use` | `true` | `/quests`, `/quest` | Eigene Daily-Quests read-only anzeigen; Player-only, keine Children und keine Staff-/Rank-Anforderung. Rewards sind automatisch. |
| `vapeecore.social.ignore` | `true` | `/ignore`, `/unignore`, `/ignorelist` | Eigene Ignore-Liste verwalten. |
| `vapeecore.friend.use` | `true` | `/friend`, `/friends`, Friends-GUI | Eigene Freundschaften und Anfragen anzeigen und verwalten; GUI und alle Subcommands, ohne separate GUI-Permission. |
| `vapeecore.clan.use` | `true` | `/clan`, `/clans`, Clan-GUI | Eigene Clans und Einladungen verwalten; Owner-Aktionen bleiben auf den tatsächlichen Clan-Owner beschränkt. Keine separate GUI- oder Staff-Permission. |
| `vapeecore.visibility.staff` | `op` | Visibility-Klassifizierungsmarker | Markiert das Online-Target ausschließlich für den Staff-Visibility-Filter. Keine Command-Permission und keine Admin-Fähigkeit. |
| `vapeecore.rank.view` | `true` | `/rank [player]` | Eigenen oder einen Online-Rank anzeigen. |
| `vapeecore.ranks.view` | `true` | `/ranks` | Öffentlichen LuckPerms-Track anzeigen. |
| `vapeecore.profile.view` | `true` | `/profile [player\|uuid]` | Eigene, online oder bereits bekannte Profile anzeigen. |

## Moderation (Phase 26A)

| Permission | Default | Command | Scope |
|---|---:|---|---|
| `vapeecore.moderation.mute` | `op` | `/mute <player\|uuid> <duration\|permanent> <reason...>` | Known online/offline; Chat und ausgehende PM gesperrt, Empfangen bleibt erlaubt. |
| `vapeecore.moderation.unmute` | `op` | `/unmute <player\|uuid> [reason...]` | Aktiven Mute widerrufen; nach Save sofortige Freigabe. |
| `vapeecore.moderation.warn` | `op` | `/warn <player\|uuid> <reason...>` | Known online/offline, gespeicherte Warnung; online Benachrichtigung nach Save. |
| `vapeecore.moderation.ban` | `op` | `/ban <player\|uuid> <duration\|permanent> <reason...>` | Known online/offline, temporär/permanent; Login-Sperre und Online-Disconnect nach Save. |
| `vapeecore.moderation.unban` | `op` | `/unban <player\|uuid> [reason...]` | Widerruft ausschließlich einen aktuell aktiven Ban. |
| `vapeecore.moderation.kick` | `op` | `/kick <player\|uuid> <reason...>` | Known online; Record vor tatsächlichem Disconnect. |
| `vapeecore.moderation.history` | `op` | `/history <player\|uuid> [page]` | Known online/offline, read-only; fünf Records pro Seite. |

Alle sieben Nodes werden direkt im Executor und in der Completion geprüft. Keine Children, kein `vapeecore.moderation.*`-Parent und keine automatische Ban→Unban- oder History→Mutation-Berechtigung. Player und Console sind erlaubt; andere Sender werden kontrolliert abgelehnt. Self-Mute/Unmute/Warn/Ban/Unban/Kick ist gesperrt; eigene History mit Permission erlaubt. Zusätzlich gilt die unten beschriebene explizite Staff-Target-Hierarchie. Gezielte Permission-Vergabe bleibt erforderlich; Weight, Prefix und öffentlicher Rank-Track sind keine Autorisierungsquellen. Mute und Unmute gewähren einander keine Rechte. Kein Mute-Bypass, keine Rang-/OP-Ausnahme für Kommunikation. Fremde Vanilla-Commands und externe Kommunikation liegen außerhalb des Enforcement-Scope.

## Staff-Target-Hierarchie (26A)

```yaml
staff:
  hierarchy:
    protected-groups:
      - builder
      - moderator
      - admin
      - owner
```

Die Reihenfolge ist LOW→HIGH und sicherheitsrelevant. Fehlende Legacy-Keys verwenden dieselben vier Defaults; ungültige Typen, null, leere/blanke IDs, Whitespace/Controls, leere Listen oder case-insensitive Duplikate warnen und verwenden die vollständigen Defaults. IDs werden getrimmt und mit `Locale.ROOT` normalisiert. `/core reload` tauscht den unveränderlichen Snapshot atomar und rollbackfähig; jede Entscheidung verwendet genau einen Snapshot für beide Levels.

| Actor (mit Command-Permission) | Ziel | Ergebnis |
|---|---|---|
| Beliebige primäre Gruppe | Nicht in Schutzliste (z. B. default/vip/custom) | Erlaubt |
| Nicht geschützter Actor, auch VIP/OP | Geschütztes Staff-Ziel | Verboten |
| Geschützter Actor | Niedrigeres geschütztes Staff-Ziel | Erlaubt |
| Geschützter Actor | Gleiches oder höheres Staff-Level | Verboten |
| Console | Bekanntes Ziel | Hierarchie erlaubt; Permission bleibt Pflicht |

Dies gilt für `warn/mute/unmute/ban/unban/kick/history`. Self-Mutationen sind vorher gesperrt; Self-History benötigt nur die History-Permission. Fehlende/fehlerhafte Gruppenauflösung bedeutet sicheren Abbruch; Gründe enthalten keine Gruppen-/Level-Details. Unbekannte Identitäten bleiben unbekannt. Offline-LuckPerms-Loads blockieren nicht; vor Fortsetzung werden Permission und ursprüngliche Online-Session erneut geprüft. Tab-Completion ist nur UX: unsichere Online-Targets werden verborgen, aktive Offline-Unban/Unmute-Kandidaten werden nicht massenhaft nachgeladen und bei Ausführung verbindlich geprüft.

Nur die **primäre LuckPerms-Gruppe** ist Level-Quelle. Primär `admin` mit geerbtem `moderator` bleibt Admin; ein Mitglied mit primär `vip` wird nicht über geerbte Staff-Gruppen hochgestuft. Die LP-Primary-Group-Konfiguration ist daher eine Betreiberverantwortung. Weight, Prefix/Suffix, Display Name/Color, Rank-Track, maximale geerbte Gruppe und OP sind keine Hierarchie. Custom-Gruppen werden nur durch explizite Aufnahme geschützt. VapeeCore verändert weder LP-User noch Gruppen.

Seit **26B** gilt dieselbe Hierarchie zusätzlich für sensible administrative Online-Utility-Ziele und Coins add/remove/set. StaffHierarchyService bleibt die einzige Level-Quelle. Self-Utility und Self-Economy bleiben mit bestehenden Permissions erlaubt; Moderations-Self-Mutationen bleiben verboten. Console ist nur in unterstützten Formen erlaubt; sonstige Sender fail closed. Unknown loaded State wird nicht nachgeladen. plugin.yml und config.yml bleiben unverändert.

## Staff Utilities

Self- und Others-Rechte werden im Command-Code getrennt geprüft. Eine Basispermission erlaubt niemals die Mutation eines anderen Spielers. Die in `plugin.yml` deklarierten `.others`-Nodes besitzen bei Commands mit Self-Form die jeweilige Basispermission als Child, damit ein Staff-Mitglied mit dem stärkeren Recht auch die Self-Form verwenden und den Bukkit-Command-Gate passieren kann.

| Permission | Default | Command/Funktion | Scope und Risiko |
|---|---:|---|---|
| `vapeecore.utility.build` | `op` | `/build` | Nur Self; wechselt den kontrollierten Lobby-Inventar-/BUILD-State. |
| `vapeecore.utility.fly` | `op` | `/fly` | Nur Self; command-managed Flight. |
| `vapeecore.utility.fly.others` | `op` | `/fly <player>` | Ändert Flight eines Online-Spielers; Child: `.fly`. |
| `vapeecore.utility.speed` | `op` | `/speed <1-10>` | Nur Self; Walk-/Fly-Speed. |
| `vapeecore.utility.speed.others` | `op` | `/speed <1-10> <player>` | Ändert Speed eines Online-Spielers; Child: `.speed`. |
| `vapeecore.utility.gamemode` | `op` | `/gamemode <mode>`, `/gm` | Nur Self; Gamemode-Mutation. |
| `vapeecore.utility.gamemode.others` | `op` | `/gamemode <mode> <player>` | Ändert Gamemode eines Online-Spielers; Child: `.gamemode`. |
| `vapeecore.utility.teleport` | `op` | `/tp <target>` oder `/tp <x> <y> <z> [yaw pitch]` | Teleportiert nur den ausführenden Spieler zu einem Online-Spieler oder Koordinaten in seiner aktuellen Welt. Gewährt keine Others- oder expliziten World-Ziele. |
| `vapeecore.utility.teleport.others` | `op` | `/tp <source> <target>` oder `/tp <source> <x> <y> <z> [yaw pitch]` | Teleportiert einen Online-Spieler; Child: `.teleport`. Auch Console mit benannter Quelle. |
| `vapeecore.utility.teleport.world` | `op` | `/tp world <world> <x> <y> <z> [yaw pitch]` | Self-Ziel in einer explizit benannten, geladenen Welt; Child: `.teleport`. |
| `vapeecore.utility.teleport.others.world` | `op` | `/tp <source> world <world> <x> <y> <z> [yaw pitch]` | Benannte Online-Quelle in explizite Welt; Children: `.teleport.others` und `.teleport.world`. Auch Console. |
| `vapeecore.utility.teleport.here` | `op` | `/tphere <player>` | Teleportiert ein Online-Ziel zum ausführenden Spieler. |
| `vapeecore.utility.teleport.bypass` | `op` | `/tp`, `/tphere` | Umgeht ausschließlich interne VapeeCore-Teleport-State-Guards, etwa Activity Participation. Umgeht niemals Events anderer Plugins, Event-Cancellation, Offline-Checks oder ein `false` von Bukkit/Paper. Kritisches Admin-Recht. |
| `vapeecore.utility.heal` | `op` | `/heal` | Nur Self; Max-Health, Fire und Freeze. |
| `vapeecore.utility.heal.others` | `op` | `/heal <player>` | Heilt einen Online-Spieler; Child: `.heal`. |
| `vapeecore.utility.feed` | `op` | `/feed` | Nur Self; Food, Saturation und Exhaustion. |
| `vapeecore.utility.feed.others` | `op` | `/feed <player>` | Sättigt einen Online-Spieler; Child: `.feed`. |
| `vapeecore.utility.ping` | `op` | `/ping` | Nur Self; zeigt den eigenen Ping. |
| `vapeecore.utility.ping.others` | `op` | `/ping <player>` | Zeigt den Ping eines Online-Spielers; Child: `.ping`. |
| `vapeecore.utility.clear` | `op` | `/clear` | Nur Self; löscht nur ein nicht durch Activity oder BUILD kontrolliertes Inventar. |
| `vapeecore.utility.clear.others` | `op` | `/clear <player>` | Löscht ein sicheres Online-Spieler-Inventar; Child: `.clear`. Destruktives Admin-Recht. |
| `vapeecore.utility.invsee` | `op` | `/invsee <player>` | Öffnet nur einen read-only Snapshot eines Online-Spieler-Inventars. |
| `vapeecore.utility.invsee.modify` | `op` | Reserviert | **Reserved, not active.** Phase 17C.1 besitzt keine Invsee-Mutation; Child: `.invsee`. |
| `vapeecore.utility.enderchest` | `op` | `/enderchest` | Öffnet das eigene echte Enderchest. |
| `vapeecore.utility.enderchest.others` | `op` | `/enderchest <player>` | Öffnet das echte, mutierbare Enderchest eines Online-Spielers; Child: `.enderchest`. Nur Admin/Owner empfohlen. |

Utility-Targets werden ausschließlich über vollständige, case-insensitive Namen aus der aktuellen Online-Spielermenge aufgelöst. Partielle Namen, `OfflinePlayer`, Mojang-API- oder Netzwerk-Lookups werden nicht verwendet. `/tp` und `/teleport` verwenden dieselbe Grammatik: XYZ darf absolut, relativ (`~`) oder vollständig lokal (`^`) sein; optionales Yaw/Pitch erlaubt absolute und `~`-relative Rotation. `^` darf nicht mit absoluten/`~`-XYZ gemischt werden. Relative und lokale Werte beziehen sich immer auf die bewegte Quelle, auch bei Console- und expliziten Weltformen. Eine Player→Player-Teleportation darf ohne `.world` zwischen Welten wechseln. Die World-Rechte gelten nur für frei benannte Welt+Koordinaten-Ziele. Bypass umgeht keine Berechtigungen, Eingabefehler, Staff-Hierarchie oder externe Event-Cancellation.

## Empfohlene Rangmatrix

Die Matrix ist ein bewusst konservativer Ausgangspunkt. Elternvererbung sollte in LuckPerms abgebildet werden; es wird kein blindes `vapeecore.*` empfohlen.

### Empfohlene LuckPerms-Eltern

| Gruppe | Empfohlener Parent |
|---|---|
| vip | default |
| builder | default |
| moderator | default |
| admin | moderator |
| owner | admin |

VIP darf nicht über Builder/Moderator Staff-Fähigkeiten erben; Staff-Gruppen erben ebenso nicht VIP. Builder ist eine getrennte Build-Rolle, nicht der Parent von Moderator. Diese Permission-Vererbung ist vom LOW→HIGH-Target-Schutz unabhängig. Primäre Gruppen müssen mit der tatsächlichen Rolle konsistent sein. Die Beispiele sind ausschließlich Betreiberempfehlungen; VapeeCore führt keine LP-Gruppen-/Parent-/Track-/Permission-Mutationen aus.

### User

- `vapeecore.lobby.spawn`
- `vapeecore.economy.coins`
- `vapeecore.message.use`
- `vapeecore.settings.use`
- `vapeecore.quest.use`
- `vapeecore.social.ignore`
- `vapeecore.friend.use`
- `vapeecore.clan.use`
- `vapeecore.rank.view`
- `vapeecore.ranks.view`
- `vapeecore.profile.view`

Keine Staff-Utilities.

### VIP

Alles von User. Keine zusätzlichen Staff-Permissions; nicht existierende Cosmetic-Rechte werden nicht erfunden.

### Builder

Alles aus der gewünschten Player-Basis, zusätzlich:

- `vapeecore.utility.build`
- `vapeecore.utility.fly`
- `vapeecore.utility.speed`
- `vapeecore.utility.gamemode`
- `vapeecore.visibility.staff`
- optional `vapeecore.utility.teleport`

Keine `.others`-, Bypass- oder Moderationsrechte. Die primäre Gruppe `builder` ist in der Default-Hierarchie geschützt, ohne dadurch eine Fähigkeit zu erhalten.

### Moderator

Alles aus der gewünschten Player-Basis, zusätzlich:

- `vapeecore.moderation.mute`
- `vapeecore.moderation.unmute`
- `vapeecore.moderation.warn`
- `vapeecore.moderation.kick`
- `vapeecore.moderation.history`
- `vapeecore.utility.fly`
- `vapeecore.utility.teleport`
- `vapeecore.utility.teleport.here`
- `vapeecore.utility.heal`
- `vapeecore.utility.feed`
- `vapeecore.utility.ping.others`
- `vapeecore.visibility.staff`
- optional `vapeecore.utility.invsee`

Nicht empfohlen: `teleport.others`, `teleport.bypass`, `vapeecore.economy.admin` oder `vapeecore.admin`.

### Admin

Alles von Moderator, zusätzlich:

- `vapeecore.moderation.ban`
- `vapeecore.moderation.unban`
- `vapeecore.utility.build`
- `vapeecore.utility.fly.others`
- `vapeecore.utility.speed`
- `vapeecore.utility.speed.others`
- `vapeecore.utility.gamemode`
- `vapeecore.utility.gamemode.others`
- `vapeecore.utility.teleport`
- `vapeecore.utility.teleport.others`
- `vapeecore.utility.teleport.world`
- `vapeecore.utility.teleport.others.world`
- `vapeecore.utility.teleport.here`
- `vapeecore.utility.teleport.bypass`
- `vapeecore.utility.heal.others`
- `vapeecore.utility.feed.others`
- `vapeecore.utility.clear`
- `vapeecore.utility.clear.others`
- `vapeecore.utility.invsee`
- `vapeecore.utility.enderchest`
- `vapeecore.utility.enderchest.others`
- `vapeecore.economy.admin`
- `vapeecore.lobby.setspawn`
- `vapeecore.warp.admin`
- `vapeecore.blackjack.admin`
- `vapeecore.visibility.staff`

`vapeecore.admin` bleibt optional, wenn `/core reload` Owner-only sein soll.

### Owner

Alles von Admin einschließlich `vapeecore.visibility.staff`, zusätzlich `vapeecore.admin` und nur die tatsächlich benötigten externen Paper-/Bukkit-Rechte. Kein blindes `*` nötig. Die primäre Gruppe `owner` gewährt keinen globalen Bypass: Owner-Spieler dürfen andere Owner nicht moderieren, deren History lesen oder deren geschützte administrative Online-Zustände verändern; eigene Utilities/Economy bleiben erlaubt.

## Bukkit-/Vanilla-Lockdown

Für einen Community-Server kann es sinnvoll sein, unter anderem folgende fremden Nodes über LuckPerms explizit auf `false` zu setzen:

- `bukkit.command.plugins`
- `bukkit.command.version`
- `bukkit.command.help`
- `minecraft.command.help`
- `minecraft.command.msg`
- `minecraft.command.me`
- `minecraft.command.teammsg`

Diese Nodes gehören **nicht** zu VapeeCore. Sie werden von Paper/Bukkit beziehungsweise Minecraft ausgewertet und müssen dort oder über LuckPerms konfiguriert werden. VapeeCore blockiert sie absichtlich nicht über `PlayerCommandPreprocessEvent`.


## Administrative Online-Targets (26B)

VapeeCore injiziert RankModule ausdrücklich in UtilityModule und EconomyModule. Rank besitzt unverändert den einzigen StaffHierarchyService; es hängt nicht von Utility oder Economy ab. Beide Module erstellen einen kleinen Bukkit-Application-Guard `command.OnlineStaffTargetGuard`, ohne zweiten Hierarchie-Algorithmus, Group-/Level-Cache, Scheduler oder neuen ReloadParticipant. Enable-Reihenfolge, 26 Module und sechs Reload-Teilnehmer bleiben unverändert; Executor/Completer-Hooks werden wie bisher entfernt.

Command-Capability und Syntax bleiben Pflicht. Nach bestehender exakter Online-Zielauflösung und UUID-Self-Klassifikation folgt die geladene Entscheidung vor Activity-/BUILD-Guards, Flight-Cleanup, Player-/Wallet-Mutation, Snapshot-Erstellung oder GUI-Open. `check` erhält StaffTargetDecision einschließlich UNAVAILABLE; `authorize` unterscheidet kontrolliertes Denial und unavailable Feedback. Fehlende geladene Gruppen oder unerwartete RuntimeException: fail closed, ein contextual WARNING mit Actor-UUID, Target-UUID und Action; gewöhnliche Hierarchie-Denials erzeugen keinen Logspam. Completion prüft dieselben loaded-only Regeln still und lädt keine LP-User.

Self (implizit oder ausdrücklich gleiche UUID) bleibt ohne LP-Abfrage erlaubt, sofern vorhandene Permissions und State-Guards erfüllt sind. Console erhält Hierarchie-Autorität nur in bereits unterstützten expliziten Target-Formen; tphere, invsee und enderchest bleiben Player-only. Andere Sender sind kein Console-Ersatz. OP und teleport.bypass umgehen niemals Hierarchie.

Geschützt sind fly, speed, gamemode, heal, feed, clear, invsee (sensitives Lesen), enderchest (reales mutierbares Inventar), tphere und tp mit anderer bewegter SOURCE. Bei tp ist DESTINATION keine administrative Mutation: Self zu höherem Staff oder fremde erlaubte Source zu höherem Staff bleiben möglich. Player→Player, Koordinaten, relative/lokale Rotation und explizite Weltformen behalten Parser und Permissions. Auch benannte eigene SOURCE in Others-Grammatik benötigt weiterhin das Others-Recht, ist aber hierarchie-exempt. Destination-Activity und externes false/Cancel bleiben verbindlich.

TP-Completion: erster Player-Token ist die bestehende Self-DESTINATION-Grammatik und wird nicht als Staff-Ziel gefiltert. Erster Console-Token ist SOURCE und wird entsprechend autorisiert. Nach eingegebener SOURCE bleiben DESTINATION, Koordinaten und Weltvorschläge von der Staff-Hierarchie ungefiltert; die tatsächliche Ausführung prüft die bewegte SOURCE verbindlich. Kein Parser- oder UX-Grammatik-Umbau.

Coins add/remove/set prüfen nach Known-Identity und echter Online-Präsenz die Hierarchie, vor Wallet-Zugriff, Save, INFO-Audit und Notice. Self-/Console-Capability bleibt erhalten. Mutation-Completion filtert online, GET bleibt capability-only, einschließlich bekannter Offline-Reads ohne LP-Load. EconomyService, RewardService, UtilityService und InvseeService bleiben frei von Staff-Policy.

Ping bleibt absichtlich ein nicht-sensitives, permission-geschütztes Latenz-Read. Build bleibt Self-only. Moderation einschließlich Schema, Mute-Projektion und Chat/PM-Enforcement ist unverändert. Historische Berichte und FORMATTING bleiben unverändert. plugin.yml, config.yml und StaffHierarchyService wurden nicht geändert.

Kanonischer Audit: `docs/PERMISSIONS.md` (aktuell 37 Roots, 50 Nodes, 15 Child-Kanten und konservative Parent-Empfehlung). Vollständige Verification/Datei-Inventare: `reports/26b-permission-hardening/PERMISSION_HARDENING.md`. Phase 27 Notifications & Presence bleibt außerhalb dieser Änderung: keine AFK-/Presence-/Friend-Alert-/Join-Quit-Neugestaltung.

## Permission-Children und Default-Audit (aktueller Phase-29-Vertrag)

50 explizite Nodes: 11 Player-Basisrechte mit `default: true`, 39 mit `default: op`. OP-Defaults gewähren Fähigkeiten, niemals einen Staff-Hierarchie-Bypass. Die folgenden 15 positiven Child-Kanten sind vollständig; keine Rückrichtung, unbekannten Children oder Zyklen. `vapeecore.admin` und `vapeecore.visibility.staff` besitzen keine Children; die sieben Moderation-Nodes sind unabhängig. Es gibt keine Wildcard-Nodes und keine Wildcard-Empfehlung.

| Parent (vapeecore.) | Child (vapeecore.) | Bedeutung |
|---|---|---|
| lobby.build | utility.build | Legacy-Kompatibilität; Code prüft nur utility.build |
| utility.fly.others | utility.fly | Stärkerer Zugang gewährt Basis; kein Target-Schutz-Bypass |
| utility.speed.others | utility.speed | Stärkerer Zugang gewährt Basis; kein Target-Schutz-Bypass |
| utility.gamemode.others | utility.gamemode | Stärkerer Zugang gewährt Basis; kein Target-Schutz-Bypass |
| utility.teleport.others | utility.teleport | Stärkerer Zugang gewährt Basis; kein Target-Schutz-Bypass |
| utility.teleport.world | utility.teleport | Stärkerer Zugang gewährt Basis; kein Target-Schutz-Bypass |
| utility.teleport.others.world | utility.teleport.others | Stärkerer Zugang gewährt Basis; kein Target-Schutz-Bypass |
| utility.teleport.others.world | utility.teleport.world | Stärkerer Zugang gewährt Basis; kein Target-Schutz-Bypass |
| utility.heal.others | utility.heal | Stärkerer Zugang gewährt Basis; kein Target-Schutz-Bypass |
| utility.feed.others | utility.feed | Stärkerer Zugang gewährt Basis; kein Target-Schutz-Bypass |
| utility.ping.others | utility.ping | Stärkerer Zugang gewährt Basis; kein Target-Schutz-Bypass |
| utility.clear.others | utility.clear | Stärkerer Zugang gewährt Basis; kein Target-Schutz-Bypass |
| utility.invsee.modify | utility.invsee | Reserved, not active; kein Schreibmodus |
| utility.enderchest.others | utility.enderchest | Stärkerer Zugang gewährt Basis; kein Target-Schutz-Bypass |
| economy.admin | economy.coins | Admin gewährt Basis, niemals umgekehrt |

`default: true` bleibt ausschließlich bei lobby.spawn, economy.coins, profile.view, friend.use, clan.use, message.use, settings.use, quest.use, social.ignore, rank.view und ranks.view. `invsee.modify` bleibt ein reservierter Node, weder aktiver Executor-Gate noch Mutation. `vapeecore.admin` wird nur für die technische Core-Reload-Fähigkeit verwendet, nicht als Staff-Bundle.

## Vollständiger Command-/Descriptor-Audit (aktueller Phase-29-Vertrag)

Alle 37 Root-Commands wurden gegen Registrierung, Executor-Gates und den eingefrorenen Descriptor-Vertrag geprüft. `PermissionDescriptorHarness` vergleicht Aliases, vollständige Usage, Nodes, Defaults und Child-Graph programmatisch; die bestehenden und erweiterten Command-Harnesses prüfen das Verhalten. Paper bestätigt die tatsächlich registrierten Roots. Phase 29 ergänzt ausschließlich quests, den Alias quest und quest.use; bestehende Defaults und Children bleiben unverändert.

In der Executor-Spalte ist der Präfix `vapeecore.` außer bei Core zur Lesbarkeit weggelassen. Die Empfehlung ist keine automatische Rangfreigabe. Gezielte Fähigkeiten und die unabhängige Target-Hierarchie gelten gleichzeitig.

| Root | Aliases | Bukkit-Gate | Executor-Gate | Empfehlung | Usage | Descriptor-Defekt |
|---|---|---|---|---|---|---|
| /mute | — | vapeecore.moderation.mute | moderation.mute | Moderator | /mute <player\|uuid> <duration\|permanent> <reason...> | Nein |
| /unmute | — | vapeecore.moderation.unmute | moderation.unmute | Moderator | /unmute <player\|uuid> [reason...] | Nein |
| /warn | — | vapeecore.moderation.warn | moderation.warn | Moderator | /warn <player\|uuid> <reason...> | Nein |
| /ban | — | vapeecore.moderation.ban | moderation.ban | Admin/Owner | /ban <player\|uuid> <duration\|permanent> <reason...> | Nein |
| /unban | — | vapeecore.moderation.unban | moderation.unban | Admin/Owner | /unban <player\|uuid> [reason...] | Nein |
| /kick | — | vapeecore.moderation.kick | moderation.kick | Moderator | /kick <player\|uuid> <reason...> | Nein |
| /history | — | vapeecore.moderation.history | moderation.history | Moderator | /history <player\|uuid> [page] | Nein |
| /vapeecore | core | — | Root ohne Gate; reload: vapeecore.admin | User; Reload Owner | /core help | Nein |
| /spawn | — | vapeecore.lobby.spawn | lobby.spawn | User/VIP | /spawn | Nein |
| /setspawn | — | vapeecore.lobby.setspawn | lobby.setspawn | Admin/Owner | /setspawn | Nein |
| /coins | — | vapeecore.economy.coins | economy.coins; get/add/remove/set zusätzlich economy.admin | User/VIP | /coins help | Nein |
| /profile | — | vapeecore.profile.view | profile.view | User/VIP | /profile [player\|uuid] | Nein |
| /friend | friends | vapeecore.friend.use | friend.use | User/VIP | /friend help | Nein |
| /clan | clans | vapeecore.clan.use | clan.use | User/VIP | /clan help | Nein |
| /msg | — | vapeecore.message.use | message.use | User/VIP | /msg <player> <message> | Nein |
| /reply | r | vapeecore.message.use | message.use | User/VIP | /reply <message> | Nein |
| /settings | — | vapeecore.settings.use | settings.use | User/VIP | /settings [visibility ...] | Nein |
| /quests | quest | vapeecore.quest.use | quest.use; Player-only | User/VIP | /quests | Nein |
| /ignore | — | vapeecore.social.ignore | social.ignore | User/VIP | /ignore <player> | Nein |
| /unignore | — | vapeecore.social.ignore | social.ignore | User/VIP | /unignore <player\|uuid> | Nein |
| /ignorelist | — | vapeecore.social.ignore | social.ignore | User/VIP | /ignorelist | Nein |
| /blackjack | — | vapeecore.blackjack.admin | blackjack.admin | Admin/Owner | /blackjack help | Nein |
| /warp | — | vapeecore.warp.admin | warp.admin | Admin/Owner | /warp help | Nein |
| /build | — | vapeecore.utility.build | utility.build | Builder; zusätzliche gezielte Staff-Rechte | /build | Nein |
| /fly | — | vapeecore.utility.fly | utility.fly Self; utility.fly.others für fremde UUID | Builder; zusätzliche gezielte Staff-Rechte | /fly [player] | Nein |
| /speed | — | vapeecore.utility.speed | utility.speed Self; utility.speed.others für fremde UUID | Builder; zusätzliche gezielte Staff-Rechte | /speed <1-10> [player] | Nein |
| /gamemode | gm | vapeecore.utility.gamemode | utility.gamemode Self; utility.gamemode.others für fremde UUID | Builder; zusätzliche gezielte Staff-Rechte | /gamemode <mode> [player] | Nein |
| /tp | teleport | vapeecore.utility.teleport | teleport / teleport.others / teleport.world / teleport.others.world je Grammatik | Moderator Self; Admin Others/World | /tp <player> \| /tp <x> <y> <z> [yaw pitch] \| /tp <source> <target> \| /tp <source> <x> <y> <z> [yaw pitch] \| /tp world <world> <x> <y> <z> [yaw pitch] \| /tp <source> world <world> <x> <y> <z> [yaw pitch] | Nein |
| /tphere | — | vapeecore.utility.teleport.here | utility.teleport.here | Moderator | /tphere <player> | Nein |
| /heal | — | vapeecore.utility.heal | utility.heal Self; utility.heal.others für fremde UUID | Moderator | /heal [player] | Nein |
| /feed | — | vapeecore.utility.feed | utility.feed Self; utility.feed.others für fremde UUID | Moderator | /feed [player] | Nein |
| /ping | — | vapeecore.utility.ping | utility.ping Self; utility.ping.others für fremde UUID | Moderator | /ping [player] | Nein |
| /clear | — | vapeecore.utility.clear | utility.clear Self; utility.clear.others für fremde UUID | Admin/Owner | /clear [player] | Nein |
| /invsee | — | vapeecore.utility.invsee | utility.invsee | Moderator optional / Admin | /invsee <player> | Nein |
| /enderchest | — | vapeecore.utility.enderchest | utility.enderchest Self; utility.enderchest.others für fremde UUID | Admin/Owner | /enderchest [player] | Nein |
| /rank | — | vapeecore.rank.view | rank.view | User/VIP | /rank [player] | Nein |
| /ranks | — | vapeecore.ranks.view | ranks.view | User/VIP | /ranks | Nein |
