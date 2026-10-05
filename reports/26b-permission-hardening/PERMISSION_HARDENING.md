# Phase 26B – Administrative Target and Permission Hardening

## Ergebnis und bestätigte Ausgangsbasis (Abschluss 4–8, 85–96, 111–113)

Stand: 2026-10-01. Genau ein Worker; keine Delegation. Ausgangsbranch:
`phase/26b-administrative-target-permission-hardening`.
Bestätigte, saubere Baseline:
`fee935ed2117f8c42295c40c43f8d01feb92d8ca` – Merge Phase 26A - Staff Hierarchy and Moderation Protection.
88 ausführbare Harnesses / 8.620 Checks als Ausgangsbasis.

Phase 26B ist implementiert und verifiziert: administrative Online-Utility-Ziele und Coins-Mutationen verwenden die vorhandene Staff-Hierarchie. Capability, Self, Console, fail-closed und Completion wurden auditiert. TP schützt die bewegte SOURCE, nicht das Reiseziel. Kein Descriptor-Defekt gefunden.

| Ergebnis | Tatsächlich geprüft |
|---|---:|
| Core Modules | 26 |
| ReloadParticipants | 6 |
| Root Commands | 36 |
| Permission-Nodes | 49 |
| Positive Child-Kanten | 15 |
| Ausführbare Harnesses, jeweils eigener Java-Prozess | 90 |
| Checks | 14.037 |
| Fehler / fehlgeschlagene Harnesses | 0 |
| Maven clean package | BUILD SUCCESS |
| Paper Enable / Disable | 26 / 26 |
| Erfolgreiche Config-Reloads | 2 |
| VapeeCore ERROR / SEVERE im Smoke | 0 |

Merge readiness: Repository-Änderung bereit; keine bekannten 26B-Produktionscode-Defekte. Bestehende externe LuckPerms-Konfigurationsabweichungen sind unten ausdrücklich dokumentiert, nicht automatisch korrigiert. Vor produktivem Staff-Einsatz sollte der Betreiber seine Capability-Zuweisungen prüfen. Kein Phase-27-Feature wurde vorweggenommen.

Live client test: not performed.

## Datei-Inventar (Abschluss 1–3)

5 neue Dateien:

- `docs/PERMISSION_HARDENING.md`
- `src/main/java/dev/vapee/core/command/OnlineStaffTargetGuard.java`
- `src/test/java/dev/vapee/core/command/OnlineStaffTargetGuardHarness.java`
- `src/test/java/dev/vapee/core/command/StaffTargetTestFixture.java`
- `src/test/java/dev/vapee/core/permission/PermissionDescriptorHarness.java`

22 geänderte Dateien:

- `README.md`
- `docs/DEVELOPER_GUIDE.md`
- `docs/PERMISSIONS.md`
- `src/main/java/dev/vapee/core/VapeeCore.java`
- `src/main/java/dev/vapee/core/economy/EconomyModule.java`
- `src/main/java/dev/vapee/core/economy/command/CoinsCommand.java`
- `src/main/java/dev/vapee/core/utility/UtilityModule.java`
- `src/main/java/dev/vapee/core/utility/command/ClearCommand.java`
- `src/main/java/dev/vapee/core/utility/command/EnderChestCommand.java`
- `src/main/java/dev/vapee/core/utility/command/FeedCommand.java`
- `src/main/java/dev/vapee/core/utility/command/FlyCommand.java`
- `src/main/java/dev/vapee/core/utility/command/GameModeCommand.java`
- `src/main/java/dev/vapee/core/utility/command/HealCommand.java`
- `src/main/java/dev/vapee/core/utility/command/InvseeCommand.java`
- `src/main/java/dev/vapee/core/utility/command/SpeedCommand.java`
- `src/main/java/dev/vapee/core/utility/command/TeleportCommand.java`
- `src/main/java/dev/vapee/core/utility/command/TeleportHereCommand.java`
- `src/test/java/dev/vapee/core/economy/EconomyIntegrationHarness.java`
- `src/test/java/dev/vapee/core/economy/command/CoinsCommandHarness.java`
- `src/test/java/dev/vapee/core/utility/UtilityInventoryHarness.java`
- `src/test/java/dev/vapee/core/utility/command/TeleportCommandHarness.java`
- `src/test/java/dev/vapee/core/utility/command/UtilityCommandHarness.java`

Entfernte Dateien: keine. Build-JAR, Deploy-JAR, Testprotokoll, Serverlogs und LP-Daten sind ignorierte Laufzeit-/Buildartefakte und nicht Teil des Commits.

## Guard und Ownership (Abschluss 9–18, 46, 108–110)

`command.OnlineStaffTargetGuard` ist ein kleiner konkreter Bukkit-Application-Adapter, kein Command-Framework, Dispatcher oder zweiter Staff-Service. RankModule bleibt Owner des unveränderten, Bukkit-freien StaffHierarchyService. VapeeCore injiziert RankModule ausdrücklich in UtilityModule und EconomyModule; beide beziehen beim Enable genau diesen Service. Rank hängt nicht von Utility/Economy ab. Enable-Reihenfolge und bestehende Hook-Cleanup-Grenzen bleiben erhalten.

Der Adapter besitzt ausschließlich finale Service-/Logger-Referenzen. Keine Group-/Level-/Player-Caches oder zweite Level-Quelle. Eine fremde Player-UUID geht ausschließlich durch `decideLoaded(actorUUID, targetUUID)`. Keine LP-User-Loads, Futures, Waits, Scheduler oder asynchrone Target-Pipeline. Der bestehende immutable Config-Supplier wird bei jeder Service-Entscheidung frisch erfasst; Reload und Rollback wirken ohne neuen Teilnehmer.

`check` liefert StaffTargetDecision, einschließlich UNAVAILABLE. Die kleine Feedback-Methode `authorize` behandelt DENY und UNAVAILABLE ausdrücklich getrennt. Normales Policy-Denial: generische Antwort ohne Group-/Level-Details und ohne Logspam. UNAVAILABLE, ungültige/missing geladene Gruppe oder unerwartete RuntimeException: keine Aktion; kontrollierte Antwort und ein WARNING mit Actor-UUID, Target-UUID und Command/Action. Exceptions stehen nur im Serverlog. Completion verweigert unsichere Kandidaten still und lädt keine User.

Nach vorhandenen Syntax-/Capability-Gates, exakter Target-Auflösung und UUID-Self-Klassifikation wird Hierarchie vor State-Guards und vor jeder Mutation/externer Aktion geprüft. Bei Utility-Formen, deren Capability von der Self-UUID abhängt, bleibt die bestehende exakte Online-Auflösung zur Klassifikation erhalten; es erfolgt niemals ein Hierarchie-Lookup vor der konkreten Capability-Prüfung.

| Actor / Ziel | Hierarchie-Ergebnis; Capability bleibt Pflicht |
|---|---|
| Beliebige geladene primäre Gruppe → ungeschütztes Ziel | ALLOW |
| Geschützter Actor → niedrigeres geschütztes Ziel | ALLOW |
| Geschützter Actor → gleiches/höheres Ziel | DENY_SAME_OR_HIGHER |
| Ungeschützter Actor, auch VIP/OP → geschütztes Ziel | DENY_ACTOR_NOT_PROTECTED |
| Fehlende Target-Gruppe / fehlender Actor bei geschütztem Target | UNAVAILABLE |
| Explizit oder implizit gleiche Player-UUID | Self; keine LP-Abfrage |
| ConsoleCommandSender | Hierarchie-ALLOW, nur wo Command Console schon unterstützt |
| Andere Sender, z. B. CommandBlock | Fail closed, kein Console-Ersatz |

Ein unbekannter Actor wird nicht unnötig strenger behandelt, wenn die bestehende Service-Policy ein bekanntes ungeschütztes Target erlaubt. Primary Group ist die einzige Level-Quelle; kein Weight, Prefix, Track, OP, maximal geerbter Rang oder Visibility-Marker. Owner besitzt keinen Bypass gegen andere Owner. Self-Ausnahmen verändern keine Capability.

## Utility-Aktionen und Side-Effect-Grenzen (Abschluss 19–35, 44)

| Aktion | Verbindlicher Schutzpunkt / Erhaltenes Verhalten |
|---|---|
| fly anderer Spieler | Vor Activity/BUILD und vor clearManagedFlight bei Creative/Spectator sowie toggleFlight. Denial behält Managed-Ownership und native Flight. |
| speed anderer Spieler | Vor Activity und Walk-/Fly-Speed-Änderung. Levels/kanalabhängige Berechnung unverändert. |
| gamemode anderer Spieler | Vor Activity/BUILD, Mode-Änderung, Flight-Cleanup und Lobby-Warnung. |
| heal anderer Spieler | Vor Activity sowie Health/Fire/Freeze-Änderung. |
| feed anderer Spieler | Vor Activity sowie Food/Saturation/Exhaustion-Änderung. |
| clear anderer Spieler | Vor Activity/BUILD, Storage-/Armor-/Offhand-Clear. Destruktive Aktion bleibt bestehend geschützt. |
| invsee anderer Spieler | Sensitiver Read: vor Snapshot-Erstellung, Runtime-View und openInventory. Weiterhin nur unveränderlicher read-only Snapshot; modify-Node inaktiv. |
| enderchest anderer Owner | Vor Öffnen des realen mutierbaren Owner-Inventars. Kein Snapshot-Ersatz; Self öffnet weiter das eigene echte Chest. |
| tphere | Bewegtes Target vor Activity, State-Bypass und teleport. Self bleibt bestehender No-op. Console ohne eigene Destination bleibt abgelehnt. |
| tp | Bewegte SOURCE vor weiteren State-/Teleport-Aktionen; DESTINATION hat keinen zusätzlichen Staff-Gate. |

UtilityService, InvseeService, OnlinePlayerResolver, TeleportParser und UtilityListener bleiben unverändert und frei von neuer Staff-Policy.

TP-Formen: Player→Player, absolute/relative/lokale XYZ, Yaw/Pitch und explizite World-Formen verwenden denselben Parser. Andere SOURCE ist geschützt, unabhängig von der Form. Implicit Self → Owner-Destination ist erlaubt. Explicit eigene SOURCE ist hierarchie-exempt, benötigt aber weiterhin Others bzw. Others.World gemäß bestehender Grammatik. Auch erlaubte fremde SOURCE darf zu höherem Staff reisen. Source- und Destination-Activity bleiben wirksam; teleport.bypass umgeht ausschließlich bestehende interne State-Guards, niemals Staff-Hierarchie, Permission, Online-/World-Lookup oder externes Cancel/false.

TP-Completion hat eine bestehende Mehrdeutigkeit: erster Player-Token gehört zunächst zur Self-DESTINATION-Form. Dessen Namen sind deshalb nicht nach Staff-Level gefiltert; der Parser wurde nicht neu entworfen. Der erste Console-Token ist eindeutig SOURCE und geht durch den Adapter (Console-Hierarchieautorität). Nach eingegebener SOURCE bleiben DESTINATION-, Koordinaten- und World-Suggestions ungefiltert durch Staff-Level. Die Ausführung autorisiert SOURCE verbindlich. Bei allen anderen scoped Utility-Targets werden geladene Kandidaten nach derselben Policy gefiltert; bestehende Self-Suggestions bleiben erhalten. Player-only GUIs/tphere bieten Console keine Targets.

Bewusste Ausschlüsse: Ping ist nur Latenzinformation, kein sensibles Inventory-/Moderation-Read und keine Player-Mutation; bestehende Permissions reichen. Build ist Self-only. Beide Commands und ihre Tests/Domain wurden nicht um neue Target-Policy erweitert.

## Economy-Mutationen und Reads (Abschluss 36–43, 45–46)

ADD, REMOVE und SET: bestehende Base- und Admin-Capability → bekannte Name/UUID-Identity → echte Online-Präsenz → geladene Staff-Entscheidung → vorhandene Loaded-Wallet-/Amount-Validierung → Service-Mutation/Save → INFO-Audit → Feedback/Target-Notice. Keine Save-/Wallet-/Notice-/Success-Audit-Effekte bei Hierarchie-Denial oder unavailable.

Self-Mutation mit economy.admin bleibt möglich, auch bei unbekannter eigener Gruppe und ohne doppelte Self-Notice. Console behält ihre bestehenden expliziten Online-Target-Formen; andere Sender erhalten keine Console-Autorität. Mutation-Completion filtert Online-Targets einschließlich sicherer Name/UUID-Auswahl; Self und Console bleiben korrekt.

COINS GET ist ausdrücklich nicht hierarchiegeschützt. Die vorhandenen economy.coins/economy.admin-Gates bleiben Pflicht. Bekannte Online- und Offline-Balances bleiben lesbar; GET-Completion ist unverändert capability-only. Keine Offline-Player-/LP-Loads und keine Offline-Mutationen. Eigene Balance, Zahlengrammatik, Overflow, insufficient funds, Save-Rollback, Literal-Components und Erfolgsaudit sind unverändert.

EconomyService, CoinWallet, EconomyResult, PlayerService/Repository und RewardService wurden nicht geändert.

## Descriptor-/Permission-Audit (Abschluss 47–58)

Vollständiger kanonischer Audit mit allen 36 Roots, Aliases, Usage, Bukkit-/Executor-Gates und Rangempfehlungen: [PERMISSIONS.md](PERMISSIONS.md#vollständiger-command-descriptor-audit-phase-26b). Frozen Descriptor-Harness prüft sämtliche 36 Verträge und alle 49 Nodes programmatisch; echte Executor-/Completion-Verhaltenstests und Paper-Registrierung ergänzen ihn. Keine rein textbasierte Ersatzverifikation.

plugin.yml war bereits korrekt: unverändert. Keine neue Permission, kein Wildcard, Alias als zusätzlicher Root oder unbeabsichtigtes Capability-Bundle. Zehn normale Player-Basisrechte bleiben default:true; alle 39 übrigen Nodes default:op. OP-Defaults sind keine Hierarchieausnahme.

### Vollständiger Child-Vertrag

49 explizite Nodes: 10 Player-Basisrechte mit `default: true`, 39 mit `default: op`. OP-Defaults gewähren Fähigkeiten, niemals einen Staff-Hierarchie-Bypass. Die folgenden 15 positiven Child-Kanten sind vollständig; keine Rückrichtung, unbekannten Children oder Zyklen. `vapeecore.admin` und `vapeecore.visibility.staff` besitzen keine Children; die sieben Moderation-Nodes sind unabhängig. Es gibt keine Wildcard-Nodes und keine Wildcard-Empfehlung.

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

`default: true` bleibt ausschließlich bei lobby.spawn, economy.coins, profile.view, friend.use, clan.use, message.use, settings.use, social.ignore, rank.view und ranks.view. `invsee.modify` bleibt ein reservierter Node, weder aktiver Executor-Gate noch Mutation. `vapeecore.admin` wird nur für die technische Core-Reload-Fähigkeit verwendet, nicht als Staff-Bundle.


## Rank-Inheritance-Audit (Abschluss 59–66)

Die vollständige konservative Node-Zuweisung für User, VIP, Builder, Moderator, Admin und Owner steht in [PERMISSIONS.md](PERMISSIONS.md#empfohlene-rangmatrix). Kurzfassung: User zehn Basisrechte; VIP dieselben ohne erfundene Cosmetics. Builder Build/Fly/Speed/Gamemode/Visibility plus optional Self-TP, keine Others/Moderation. Moderator Warn/Mute/Unmute/Kick/History, Self-Fly/TP, tphere, Self-Heal/Feed, Ping-Others, Visibility und optional Invsee. Admin gezielt Ban/Unban, starke Others/World-/Inventory-Rechte, Economy-Admin, Setspawn/Warp/Blackjack. Owner ergänzt technisch notwendiges Core-Reload und nur benötigte externe Rechte, ohne Wildcard.

Empfohlene Parents: vip→default, builder→default, moderator→default, admin→moderator, owner→admin. VIP erbt keine Staff-Rolle; Staff erbt nicht VIP. Builder ist keine Moderator-Elternrolle. Parent-Vererbung ist keine Staff-Level-Quelle und ersetzt keine explizite Primary Group. Plugin verändert weder Groups, Parents, Users, Tracks noch Permission-Zuweisungen.

### Tatsächlicher lesender Dev-Server-Audit

`lp listgroups`, `lp group … parent info` und `lp group … permission info` wurden ausschließlich lesend ausgeführt. Sieben bereits vorhandene Gruppen: default, supporter, vip, builder, moderator, admin, owner. Kein Group wurde erzeugt oder geändert. supporter bleibt eine vorhandene öffentliche Rolle, keine neue Phase-26B-Staff-Stufe.

| Gruppe | Tatsächliche direkte Parents | Konservative Empfehlung / Abweichung |
|---|---|---|
| vip | default | entspricht |
| builder | default | entspricht |
| moderator | default | entspricht |
| admin | builder, moderator | zusätzliches Builder-Erbe; bewusst vom Betreiber zu prüfen |
| owner | admin, owner | vorhandene Selbstvererbung; Betreiber sollte den direkten owner→owner-Parent prüfen/entfernen |

Die lesenden Permission-Listen der sechs Matrixgruppen zeigen jeweils nur bestehende weight/displayname-Metadaten, keine direkten VapeeCore-Capability-Grants und keine Wildcards. Die empfohlene Matrix ist daher keine bereits ausgeführte Vergabe: nicht-OP Staff benötigt gezielte Rechte, während bestehende Bukkit-op-Defaults nur OP-Fähigkeiten liefern. Diese Betreiberhinweise sind keine 26B-Codefehler und bleiben absichtlich unverändert. Primary-Group-/OP-Schutz funktioniert unabhängig davon.

## Verhaltenstests (Abschluss 67–72)

- OnlineStaffTargetGuardHarness: loaded Matrix einschließlich default/vip/custom/developer, vier Staff-Stufen und null; DENY vs UNAVAILABLE; Self ohne Lookup; echte Console vs unsupported; OP ohne Ausnahme; custom nach Reload geschützt und nach Rollback ungeschützt; keine Async-Loads; exception-fail-closed, genaue Warning-Kontexte und stille Completion.
- UtilityCommandHarness: acht Nicht-TP-/Nicht-Invsee-Aktionen über jede Kombination default/vip/builder/moderator/admin/owner/unknown. Tatsächliche Setter, Inventory-Clear, GUI-Open und Teleport-Aufrufe werden gezählt; Denial ändert keinen Zustand. Capability vor Hierarchie, Activity/BUILD-Reihenfolge, Console-Vertrag, unsupported, Self, lookup-exception, Creative-Fly-/Gamemode-Cleanup und bewusster Ping-Ausschluss.
- TeleportCommandHarness: SOURCE-Matrix für Player→Player, koordinatenbasierte und explizite World/Rotation-Form; Owner-DESTINATION bleibt erlaubt und unverändert. Implicit/explicit Self, unveränderte Others-Capability, Console, unsupported, Activity, false/cancel, State-Bypass ohne Staff-Bypass, SOURCE-vs-DESTINATION-Completion.
- UtilityInventoryHarness: echte Invsee-/EnderChest-Executor über lokale Konstruktor-Seams, vollständige Matrix, kein Snapshot/Open/Runtime-View bei Denial, unveränderte Item-Daten, echtes Live-Enderchest bei Allow, Self nur Base; bestehende Read-only Click/Drag/Creative/Swap/Drop- und Lifecycle-Regression.
- CoinsCommandHarness: drei Mutationen über die komplette Matrix; Wallet, persistierter Wert, exakte Saves/Audits/Notices, UUID-Targets, capability-first, Self/Console/unsupported, lookup-exception, gefilterte Mutations-Completion, ungeschütztes Online-/Offline-GET.
- PermissionDescriptorHarness: genaue Roots/Aliases/Usages, Dependencies, Nodes/Defaults/Children, unbekannte Children, Zyklen/Rückkanten, Moderations-Unabhängigkeit, kein Admin-/Visibility-Bundle oder Wildcard; invsee.modify inaktiv, Legacy-Build nicht direkt geprüft; Modulverdrahtung, Counts und keine Utility-/Economy-LP-Loads.
- EconomyIntegrationHarness: echte File-Persistence unverändert; zusätzliche ausdrückliche RankModule-Injektion und Enable-Reihenfolge.

## Vollständige Regression (Abschluss 73–87)

Nach dem finalen Clean-Build wurde jeder Harness als eigener Java-21-Prozess ausgeführt. Kein Surefire-Resultat wurde als Ersatz für executable Harnesses gezählt. Vollständiges lokales Laufprotokoll: `target/phase26b-final-harness-results.txt`.

Phase 26A: StaffHierarchyServiceHarness 109, LuckPermsAsyncHarness 16, ConfigServiceHarness 74, DefaultConsistencyHarness 12, ModerationCommandHarness 3.381, ModerationLifecycleHarness 149; alle grün. Phase 25B einschließlich Mute Projection/Chat-/PM-Enforcement ist vollständig grün. Alle übrigen Moderation-, Chat/PM-, Friends-, Clan-, Player/Persistence-, Settings/Visibility-, Reward/Quest-, Blackjack-, Rank- und verbleibenden Harnesses wurden erneut ausgeführt.

| Vollqualifizierter Harness | Checks | Ergebnis |
|---|---:|---|
| dev.vapee.core.activity.ActivityHarness | 153 | PASS |
| dev.vapee.core.activity.blackjack.BlackjackFairnessHarness | 393 | PASS |
| dev.vapee.core.activity.blackjack.BlackjackHarness | 218 | PASS |
| dev.vapee.core.activity.blackjack.BlackjackPresentationHarness | 43 | PASS |
| dev.vapee.core.activity.blackjack.presentation.BlackjackPreviewHarness | 16 | PASS |
| dev.vapee.core.activity.blackjack.table.BlackjackTableHarness | 90 | PASS |
| dev.vapee.core.chat.ChatHarness | 17 | PASS |
| dev.vapee.core.clan.ClanDomainHarness | 52 | PASS |
| dev.vapee.core.clan.ClanIntegrationHarness | 15 | PASS |
| dev.vapee.core.clan.ClanPersistenceHarness | 39 | PASS |
| dev.vapee.core.clan.ClanServiceHarness | 136 | PASS |
| dev.vapee.core.clan.gui.ClanCommandHarness | 83 | PASS |
| dev.vapee.core.clan.gui.ClanMenuHarness | 84 | PASS |
| dev.vapee.core.clan.gui.ClanMenuSecurityHarness | 37 | PASS |
| dev.vapee.core.command.CoreCommandHarness | 72 | PASS |
| dev.vapee.core.command.OnlineStaffTargetGuardHarness | 268 | PASS |
| dev.vapee.core.config.ConfigServiceHarness | 74 | PASS |
| dev.vapee.core.config.DefaultConsistencyHarness | 12 | PASS |
| dev.vapee.core.economy.command.CoinsCommandHarness | 1942 | PASS |
| dev.vapee.core.economy.EconomyIntegrationHarness | 27 | PASS |
| dev.vapee.core.economy.EconomyServiceHarness | 44 | PASS |
| dev.vapee.core.friend.FriendCommandHarness | 80 | PASS |
| dev.vapee.core.friend.FriendDomainHarness | 29 | PASS |
| dev.vapee.core.friend.FriendIntegrationHarness | 28 | PASS |
| dev.vapee.core.friend.FriendLifecycleHarness | 12 | PASS |
| dev.vapee.core.friend.FriendPersistenceHarness | 28 | PASS |
| dev.vapee.core.friend.FriendServiceHarness | 79 | PASS |
| dev.vapee.core.friend.gui.FriendMenuHarness | 159 | PASS |
| dev.vapee.core.friend.gui.FriendMenuSecurityHarness | 37 | PASS |
| dev.vapee.core.identity.IdentityHarness | 26 | PASS |
| dev.vapee.core.identity.ProfileHarness | 19 | PASS |
| dev.vapee.core.lobby.command.LobbyCommandHarness | 26 | PASS |
| dev.vapee.core.lobby.player.LobbyHarness | 53 | PASS |
| dev.vapee.core.lobby.warp.WarpHarness | 45 | PASS |
| dev.vapee.core.message.CommandHelpHarness | 85 | PASS |
| dev.vapee.core.moderation.command.ModerationCommandHarness | 3381 | PASS |
| dev.vapee.core.moderation.command.ModerationDurationHarness | 53 | PASS |
| dev.vapee.core.moderation.ModerationBanEnforcementHarness | 31 | PASS |
| dev.vapee.core.moderation.ModerationDomainHarness | 112 | PASS |
| dev.vapee.core.moderation.ModerationLifecycleHarness | 149 | PASS |
| dev.vapee.core.moderation.ModerationMuteEnforcementHarness | 52 | PASS |
| dev.vapee.core.moderation.ModerationMuteProjectionHarness | 20 | PASS |
| dev.vapee.core.moderation.ModerationPersistenceHarness | 523 | PASS |
| dev.vapee.core.moderation.ModerationServiceHarness | 141 | PASS |
| dev.vapee.core.onlinereward.OnlineRewardLifecycleHarness | 42 | PASS |
| dev.vapee.core.onlinereward.OnlineRewardServiceHarness | 42 | PASS |
| dev.vapee.core.permission.LuckPermsAsyncHarness | 16 | PASS |
| dev.vapee.core.permission.PermissionDescriptorHarness | 805 | PASS |
| dev.vapee.core.player.repository.PlayerQuestPersistenceHarness | 21 | PASS |
| dev.vapee.core.player.repository.PlayerRewardPersistenceHarness | 11 | PASS |
| dev.vapee.core.player.repository.PlayerVisibilityPersistenceHarness | 12 | PASS |
| dev.vapee.core.player.settings.PlayerSettingsServiceHarness | 24 | PASS |
| dev.vapee.core.player.settings.PlayerVisibilitySettingsHarness | 11 | PASS |
| dev.vapee.core.presentation.PlaytimeFormatterHarness | 14 | PASS |
| dev.vapee.core.presentation.PresentationHarness | 10 | PASS |
| dev.vapee.core.privatemessage.PrivateMessageSocialHarness | 117 | PASS |
| dev.vapee.core.quest.daily.DailyQuestConfigHarness | 13 | PASS |
| dev.vapee.core.quest.daily.DailyQuestCycleSelectorHarness | 18 | PASS |
| dev.vapee.core.quest.daily.DailyQuestLifecycleHarness | 14 | PASS |
| dev.vapee.core.quest.DailyQuestServiceHarness | 20 | PASS |
| dev.vapee.core.quest.QuestDefinitionHarness | 29 | PASS |
| dev.vapee.core.quest.QuestLifecycleHarness | 42 | PASS |
| dev.vapee.core.quest.QuestServiceHarness | 44 | PASS |
| dev.vapee.core.rank.RankCommandHarness | 16 | PASS |
| dev.vapee.core.rank.RanksCommandHarness | 12 | PASS |
| dev.vapee.core.rank.RankServiceHarness | 23 | PASS |
| dev.vapee.core.rank.staff.StaffHierarchyServiceHarness | 109 | PASS |
| dev.vapee.core.reward.RewardLifecycleHarness | 40 | PASS |
| dev.vapee.core.reward.RewardServiceHarness | 126 | PASS |
| dev.vapee.core.seat.SeatHarness | 52 | PASS |
| dev.vapee.core.settings.command.SettingsCommandHarness | 25 | PASS |
| dev.vapee.core.settings.SettingsMenuHarness | 56 | PASS |
| dev.vapee.core.settings.SettingsMenuSecurityHarness | 34 | PASS |
| dev.vapee.core.settings.visibility.SettingsModuleLifecycleHarness | 23 | PASS |
| dev.vapee.core.settings.visibility.SettingsNavigationHarness | 17 | PASS |
| dev.vapee.core.settings.visibility.VisibilityMenuSecurityHarness | 41 | PASS |
| dev.vapee.core.settings.visibility.VisibilitySettingsMenuHarness | 29 | PASS |
| dev.vapee.core.settings.visibility.VisiblePlayersMenuHarness | 74 | PASS |
| dev.vapee.core.utility.command.BuildCommandHarness | 17 | PASS |
| dev.vapee.core.utility.command.TeleportCommandHarness | 894 | PASS |
| dev.vapee.core.utility.command.UtilityCommandHarness | 1221 | PASS |
| dev.vapee.core.utility.TeleportParserHarness | 58 | PASS |
| dev.vapee.core.utility.UtilityInventoryHarness | 742 | PASS |
| dev.vapee.core.utility.UtilityServiceHarness | 35 | PASS |
| dev.vapee.core.visibility.FriendVisibilityRefreshHarness | 11 | PASS |
| dev.vapee.core.visibility.IgnoreVisibilityRefreshHarness | 7 | PASS |
| dev.vapee.core.visibility.VisibilityModuleHarness | 22 | PASS |
| dev.vapee.core.visibility.VisibilityPolicyHarness | 28 | PASS |
| dev.vapee.core.visibility.VisibilityServiceHarness | 10 | PASS |
| dev.vapee.core.worlddisplay.WorldDisplayHarness | 27 | PASS |
| **Gesamt: 90** | **14.037** | **0 Fehler** |

## Maven, Artefakte und Paper-Smoke (Abschluss 88–96)

Finaler normaler `scripts/build-and-deploy.ps1`-Lauf: `mvn clean package`, BUILD SUCCESS. Surefire hat keine JUnit-Harness-Ausführung behauptet; die 90 separaten Prozesse oben sind die tatsächliche Regression. Nur bestehende Annotation-Processor-/deprecated-API-Compile-Hinweise, keine Kompilierfehler.

| Artefakt | Bytes | SHA-256 |
|---|---:|---|
| target/vapeecore-1.0-SNAPSHOT.jar | 999008 | DAD83CFFA80EC9EA8720B710760D23DCF017376AC23364B508B6B4D7723DB559 |
| dev-server/plugins/vapeecore-1.0-SNAPSHOT.jar | 999008 | DAD83CFFA80EC9EA8720B710760D23DCF017376AC23364B508B6B4D7723DB559 |

Echter Serverlauf am 2026-10-01: Paper 1.21.11-132/c5eb079, Java 21.0.12.1 (Oracle), LuckPerms 5.5.84. Finales Deploy-JAR remapped/geladen; `Done` nach 18,154 s. Alle 26 Module enabled, Utility 12 Commands, Economy und Moderation erfolgreich; LuckPerms Connected. `bukkit:help VapeeCore` bestätigt genau 36 Roots.

Console-Probes: core/status/help/version, zwei core reloads, alle zwölf Utility-Roots ohne mutierende Targets, gm/teleport-Aliases, Coins help/add/remove/set mit fehlenden Argumenten sowie read-only GET rxaq, sechs mutierende Moderation-Roots ohne Targets sowie read-only History rxaq, ranks und lesender LP-Audit. Player-only invsee/enderchest/tphere bleiben ausdrücklich Console-abgelehnt. Keine künstlichen Warnungen/Bans/Mutes/Kicks oder Wallet-/Inventory-Änderungen. 0 Online-Spieler.

Beide Reloads bereiten genau config.yml, lobby.yml, chat.yml, private-messages.yml, presentation.yml und daily-quests.yml vor und schließen erfolgreich ab (12 ms / 7 ms). Kein neuer ReloadParticipant.

Reguläres stop, 26 Module rückwärts disabled, Player-/World-Saves abgeschlossen, Exit 0 und kein verwaltetes PID-File zurückgeblieben. moderation.yml wurde nicht erzeugt. latest.log enthält 0 ERROR/SEVERE und 0 VapeeCore-Hierarchie-Warnings. Paper selbst meldet einen allgemeinen Versionszweig-Hinweis zu neueren Minecraft-Releases; dies ist kein VapeeCore-Fehler und führt hier zu keinem unangeforderten Upgrade.

Live client test: not performed.

## Docs, unveränderte Grenzen und Abschluss (Abschluss 97–113)

README: Phase 26B abgeschlossen. Developer Guide: Ownership, loaded-only Policy, Side-Effect-Reihenfolge, Completion und bewusste Ausschlüsse. PERMISSIONS: kanonischer 36-Root-/49-Node-/15-Kanten-Audit, Self/Console/OP/BYPASS-Semantik, vollständige User-/VIP-/Builder-/Moderator-/Admin-/Owner-Empfehlung. Dieser neue Bericht enthält Inventory und jedes tatsächliche Harness-Ergebnis.

Historische COMMAND_AUDIT, MODERATION_FOUNDATION, MODERATION_TOOLS, MODERATION_MUTES, STAFF_HIERARCHY und FORMATTING unverändert. config.yml, plugin.yml, StaffHierarchyConfig/Service/Decision, RankService/Presentation, LuckPermsService, Moderation-Domain/-Schema/-Commands/-Persistence, Utility-/Inventory-/Teleport-Domain, Ping, Build und Economy-/Reward-Domain unverändert. Keine Schema-/Datenmigration.

Keine neuen Commands, Permissions, Staff-Ränge, Hierarchie-Bypass-Permissions oder OP-Bypasses. Keine Offline-Utility-/Economy-Mutation, LP-Gruppen-/Parent-/Track-/Permission-Mutation, Invsee-Mutation, Staff-GUI, freeze, pay, Clan-/Friend-Stafftools oder generic Command-Framework. Keine Notifications-/Presence-/AFK-/Join-Quit-/Friend-Alert-Implementierung für Phase 27.

Bekannte Hinweise: kein Live-Client-Test; bestehende externe LP-Parent-/Capability-Konfiguration oben prüfen. Keine bekannten neuen Produktionscode-Defekte. Code ist merge-ready; Betreiber-Konfiguration wurde nicht still als passend oder bereits umgesetzt ausgegeben.

Git-Abschlussvertrag: genau ein lokaler Commit mit Titel
`Phase 26B - Administrative Target and Permission Hardening`.
Kein Push, Rebase, Reset oder History-Rewrite. Abschließender Commit-Hash, sauberer Status und vollständiger Baseline-Diff werden nach Commit geprüft und im Chat gemeldet.
