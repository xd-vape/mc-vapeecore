# VapeeCore Permissions

Diese Datei ist die kanonische Übersicht der von VapeeCore registrierten Permission-Nodes. Die Rangnamen in den Empfehlungen sind Beispiele für LuckPerms-Gruppen. Feature-Fähigkeiten bleiben Permission-basiert; seit Phase 26A wertet ausschließlich die separate Moderations-Target-Hierarchie explizit konfigurierte primäre Group-IDs aus. `default: op` ist nur der Bukkit/Paper-Default und kein Ersatz für eine gezielte LuckPerms-Konfiguration.

## Allgemein, Lobby und Economy

| Permission | Default | Command/Funktion | Hinweis |
|---|---:|---|---|
| `vapeecore.admin` | `op` | `/core reload` und administrative Core-Funktionen | Kritisches Owner-Recht; kein Wildcard-Parent für alle VapeeCore-Rechte. |
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

Utility-/Teleport-/Inventory- und Economy-Target-Autorisierung sowie der endgültige Permission-/Inheritance-Audit gehören zu **26B**, nicht 26A. `plugin.yml` und seine OP-Defaults/Children bleiben in 26A unverändert.

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

Utility-Targets werden ausschließlich über vollständige, case-insensitive Namen aus der aktuellen Online-Spielermenge aufgelöst. Partielle Namen, `OfflinePlayer`, Mojang-API- oder Netzwerk-Lookups werden nicht verwendet. `/tp` und `/teleport` verwenden dieselbe Grammatik: XYZ darf absolut, relativ (`~`) oder vollständig lokal (`^`) sein; optionales Yaw/Pitch erlaubt absolute und `~`-relative Rotation. `^` darf nicht mit absoluten/`~`-XYZ gemischt werden. Relative und lokale Werte beziehen sich immer auf die bewegte Quelle, auch bei Console- und expliziten Weltformen. Eine Player→Player-Teleportation darf ohne `.world` zwischen Welten wechseln. Die World-Rechte gelten nur für frei benannte Welt+Koordinaten-Ziele. Bypass umgeht keine Berechtigungen, Eingabefehler oder externe Event-Cancellation.

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

VIP darf nicht über Builder/Moderator Staff-Fähigkeiten erben. Builder ist eine getrennte Build-Rolle, nicht der Parent von Moderator. Diese Permission-Vererbung ist vom LOW→HIGH-Target-Schutz unabhängig. Primäre Gruppen müssen mit der tatsächlichen Rolle konsistent sein. Die Beispiele sind ausschließlich Betreiberempfehlungen; VapeeCore führt keine LP-Gruppen-/Parent-/Track-/Permission-Mutationen aus.

### User

- `vapeecore.lobby.spawn`
- `vapeecore.economy.coins`
- `vapeecore.message.use`
- `vapeecore.settings.use`
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

Alles von Admin einschließlich `vapeecore.visibility.staff`, zusätzlich `vapeecore.admin` und nur die tatsächlich benötigten externen Paper-/Bukkit-Rechte. Kein blindes `*` nötig. Die primäre Gruppe `owner` gewährt keinen globalen Bypass: Owner-Spieler dürfen andere Owner nicht moderieren oder deren History lesen.

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
