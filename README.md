# VapeeCore

VapeeCore ist das zentrale Basis-Plugin für einen Minecraft-Community-Server. Das Projekt ist als modularer Monolith aufgebaut und stellt aktuell eine zentrale Konfiguration, MiniMessage-Nachrichten, interne CoreModule, eine lokale Player Foundation, eine persistente Social-/Ignore-Grundlage, eine Coin-Economy, globalen Chat, private Nachrichten, Player-Presentation, eine Ingame-Settings-Oberfläche und eine lesende LuckPerms-Integration bereit.

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

## Architektur

- `command`: Commands und deren Subcommands
- `chat`: globaler Adventure-Chat mit LuckPerms-Prefix und -Suffix
- `config`: zentraler Zugriff auf die Bukkit-Konfiguration
- `economy`: internes Coin-Wallet, EconomyService und Coin-Commands
- `lobby`: Lobby-Spawn, Teleports und auf die Lobby-Welt begrenzter Schutz
- `message`: Adventure- und MiniMessage-Ausgabe
- `module`: kleiner Lifecycle-Extension-Point für zukünftige Systeme
- `permission`: lesender Zugriff auf LuckPerms-Gruppen und Meta-Daten
- `player`: Player-Domainmodell, aktiver Cache und Join-/Quit-Lifecycle
- `player.repository`: austauschbare Persistence mit lokaler YAML-Implementierung
- `player.social`: persistenter, UUID-basierter Social-State eines Players
- `player.settings`: persistente Player-Settings und interne Zugriffsschicht für andere Module
- `privatemessage`: sichere, sessionbasierte Nachrichten zwischen Online-Spielern
- `presentation`: Lobby-Sidebar und serverweite Adventure-Tablist
- `reload`: koordinierter zweiphasiger Config-Reload mit Runtime-Rollback
- `settings`: sichere Ingame-Oberfläche für die bereits persistenten Player-Settings
- `social`: Ignore-Service, threadsichere Runtime-Projektion, Lifecycle und Commands

Die neun Module starten in der gerichteten Reihenfolge `Permission → Player → Social → Economy → Lobby → Chat → PrivateMessage → Presentation → Settings` und werden beim Shutdown vollständig rückwärts deaktiviert. Dadurch können Chat und PrivateMessage den bereits aktiven Social-Snapshot nutzen, während Player-Persistence beim Shutdown erst nach Social gespeichert wird.

Aktive Spieler werden als `CorePlayer` im Speicher gehalten. Zum Profil gehören die Einstellungen `scoreboard`, `sounds` und `private-messages`, die standardmäßig aktiviert sind, das Coin-Wallet und `PlayerSocial`. Andere Module greifen über klar abgegrenzte Services darauf zu. Die lokale Persistence legt pro UUID eine Datei unter `plugins/VapeeCore/players/<uuid>.yml` an. Alte Player-Dateien ohne `settings`, `economy` oder `social` werden mit den jeweiligen Default-Werten geladen und beim nächsten regulären Save automatisch erweitert. Bukkit-`Player`-Instanzen werden nicht im Domainmodell gespeichert.

Jedes Player-Profil besitzt außerdem ein Coin-Wallet mit einer nicht negativen ganzzahligen `long`-Balance und dem Defaultwert `0`. Die Coins werden als `economy.coins` in derselben Player-YAML gespeichert; alte Dateien werden beim nächsten regulären Save automatisch ergänzt. Andere Module greifen ausschließlich über den `EconomyService` darauf zu. `/coins` zeigt den eigenen Kontostand, während `/coins get|add|remove|set` ausschließlich online befindliche, geladene Spieler administriert. Es gibt bewusst keine Vault-Anbindung, Offline-Mutationen, weiteren Währungen oder Spieler-zu-Spieler-Transfers.

```yaml
name: Vapee
first-join: 123456
last-join: 123456
settings:
  scoreboard: true
  sounds: true
  private-messages: true
economy:
  coins: 2500
social:
  ignored:
    - "550e8400-e29b-41d4-a716-446655440000"
```

Das `SocialModule` wird unmittelbar nach dem `PlayerModule` aktiviert und stellt `/ignore <player>`, `/unignore <player|uuid>` und `/ignorelist` mit der gemeinsamen Permission `vapeecore.social.ignore` bereit. Neu ignoriert werden ausschließlich exakt benannte Online-Spieler; dabei werden weder partielle Namen noch Bukkit-`OfflinePlayer`-Lookups verwendet. Unignore arbeitet ausschließlich gegen die eigene gespeicherte Ignore-Liste und kann bekannte Offline-Spieler über deren VapeeCore-Playerdatei oder als UUID auflösen. Namen dienen nur der Anzeige und Eingabeauflösung, persistente Wahrheit bleiben die UUIDs unter `social.ignored`.

Die Ignore-Beziehung ist einseitig. Wenn A B ignoriert, sieht A keine globalen Chatnachrichten von B; B sieht A weiterhin, solange B A nicht ebenfalls ignoriert. Papers veränderbare `AsyncChatEvent`-Viewer-Menge wird pro Player-Audience gefiltert, während Console und andere Audience-Typen erhalten bleiben. Dafür verwendet `SocialService` eine `ConcurrentHashMap<UUID, Set<UUID>>` mit unveränderlichen Sets. Der Async-Chatpfad liest weder den nicht threadsicheren Player-Cache noch Dateien. Die Player-YAML in `CorePlayer.getSocial()` bleibt alleinige persistente Wahrheit; der Snapshot wird nur nach erfolgreichem Save vollständig ersetzt.

Ignore blockiert außerdem private Nachrichten in beiden Richtungen und gilt automatisch auch für `/reply` beziehungsweise `/r`, weil Replies denselben Sendepfad verwenden. Wer selbst ein Ziel ignoriert, erhält die klare Meldung, dass dieses Ziel ignoriert wird. Ignoriert dagegen der Empfänger den Sender, wird aus Datenschutzgründen dieselbe allgemeine Ablehnung wie bei deaktiviertem PM-Empfang ausgegeben. Ignore verändert `settings.private-messages` nicht.

Phase 12 enthält bewusst kein Friends- oder Party-System, kein Social GUI und keine `social.yml`. Ignore versteckt weder Entities noch Tablist, Scoreboard, Nametags oder Join-/Quit-Meldungen. `SocialModule` ist ein normales `CoreModule` und kein `ReloadParticipant`; Social-Playerdaten werden durch `/core reload` nicht von Platte neu geladen.

Permissions, Gruppen, Primary Groups, Prefixe, Suffixe, Meta-Daten, Contexts und Vererbung werden ausschließlich von LuckPerms verwaltet. VapeeCore liest die bereits von LuckPerms aufgelösten Daten und speichert sie weder im `CorePlayer` noch in den Player-YAML-Dateien. Normale Permission-Checks erfolgen weiterhin über Bukkit/Paper.

Das `LobbyModule` verwendet die separate Datei `plugins/VapeeCore/lobby.yml`. `/setspawn` speichert dort den Lobby-Spawn mit Weltname, Position und Blickrichtung; `/spawn` teleportiert Spieler dorthin. Join-Teleport, Lobby-Respawn, Void Rescue sowie Damage-, Hunger-, Block- und Item-Schutz gelten ausschließlich in der Welt aus `spawn.world`. Spieler mit `vapeecore.lobby.build` dürfen dort bauen sowie Items droppen und aufnehmen.

Das `ChatModule` formatiert den globalen Chat über Papers `AsyncChatEvent` und einen viewer-unabhängigen `ChatRenderer`. Das Format liegt in `plugins/VapeeCore/chat.yml`; LuckPerms-Prefix und -Suffix können dort als `legacy-ampersand`, `mini-message` oder `plain` interpretiert werden. Das Serverformat ist MiniMessage, die originale Playernachricht wird jedoch als Adventure Component eingesetzt und niemals als MiniMessage ausgewertet. Chat-Channels sind nicht Bestandteil dieser Phase.

Das `PrivateMessageModule` stellt `/msg <player> <message>` sowie `/reply <message>` mit dem Alias `/r` für ausschließlich online befindliche Spieler bereit. Darstellung und globaler Aktivierungsstatus liegen in `plugins/VapeeCore/private-messages.yml`. Spielertext wird als sichere Adventure Component eingesetzt und weder als MiniMessage noch als Legacy-Farbcode ausgewertet. `PlayerSettings.privateMessagesEnabled` bedeutet ausschließlich „private Nachrichten empfangen“: Ein Spieler mit deaktiviertem Empfang darf weiterhin selbst schreiben. Der letzte erfolgreiche Gesprächspartner wird nur für die aktuelle Session im Speicher gehalten; ein Quit entfernt alle zugehörigen Reply-Verweise. Es gibt bewusst keine Offline-Nachrichten, Mailbox oder SocialSpy.

Das `PresentationModule` aktualisiert über einen gemeinsamen synchronen Task standardmäßig einmal pro Sekunde die persönliche Sidebar und Tablist. Die Templates liegen in `plugins/VapeeCore/presentation.yml` und verwenden Adventure/MiniMessage mit sicheren Component-Platzhaltern für Servername, Displayname, LuckPerms-Prefix, -Suffix und Primary Group, Coins sowie Onlinezahlen. Die Sidebar nutzt Papers moderne Component- und NumberFormat-Scoreboard-APIs, respektiert `PlayerSettings.scoreboardEnabled` und ist standardmäßig ausschließlich in der konfigurierten Lobby-Welt aktiv. Die Tablist ist serverweit; Rank-Sortierung und Minigame-Scoreboards sind bewusst nicht enthalten.

Das `SettingsModule` stellt `/settings` für Spieler mit `vapeecore.settings.use` bereit. Das 27-Slot-Inventar schaltet Scoreboard, VapeeCore-eigene UI-Feedback-Sounds und den Empfang privater Nachrichten um. Die Werte werden unmittelbar über den bestehenden `PlayerSettingsService` in derselben Player-YAML gespeichert; es gibt weder eine zweite Settings-Persistence noch eine `settings.yml`. Ein Scoreboard-Toggle lässt die Presentation sofort den gültigen Scope neu bewerten. Das PM-Setting wirkt ebenfalls sofort und steuert ausschließlich den Empfang—das eigene Senden bleibt möglich. `SettingsModule` ist kein `ReloadParticipant`.

`/core reload` liest `config.yml`, `lobby.yml`, `chat.yml`, `private-messages.yml` und `presentation.yml` in einer gemeinsamen zweiphasigen Transaktion neu. Zuerst werden alle fünf Dateien in dieser Reihenfolge vollständig vorbereitet und validiert; erst danach werden ihre Runtime-Zustände in derselben Reihenfolge angewendet. Ein Prepare-Fehler verändert daher keinen aktiven Zustand. Bei einem unerwarteten Apply-Fehler werden bereits übernommene Zustände rückwärts zurückgerollt. Message-Prefix, Servername, Debug-Modus, Lobby-Regeln, Chat- und PM-Format sowie Scoreboard- und Tablist-Templates werden ohne Serverneustart aktiv; auch der Presentation-Task passt sich sicher an `enabled` und `update-interval-ticks` an. Ein PM-Reload behält bestehende Conversation-Beziehungen. Der Reload schreibt keine Configdateien, lädt keine Playerdaten und verändert weder Social-State, Economy noch LuckPerms.

Voice-System, Community-Funktionen und Minigames werden bei Bedarf als klar abgegrenzte interne `CoreModule` innerhalb derselben VapeeCore-JAR ergänzt.
