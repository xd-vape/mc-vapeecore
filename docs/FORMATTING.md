# VapeeCore – Formatting, Colors & Placeholders

Diese Dokumentation beschreibt, wie Farben, Textformatierungen, LuckPerms-Ränge und Placeholder in VapeeCore verwendet werden.

Sie dient als zentrale Referenz für:

- Chat
- Scoreboard
- Tablist
- Server-Messages
- LuckPerms-Ränge
- Rank-Farben
- Prefix und Suffix
- Adventure Components
- MiniMessage
- Playtime
- Online-Reward-Nachrichten
- zukünftige neue Ränge

## Sprache und lokalisierte Texte

Die Minecraft-Clientsprache entscheidet über eigene Plugin-Texte: `de` → Deutsch, sonst Englisch; Console → Englisch. Die UTF-8-Dateien `plugins/VapeeCore/lang/en.yml` und `de.yml` enthalten optionale MiniMessage-Overrides. Bestehende Dateien werden nicht überschrieben oder migriert. `/core reload` prüft beide Dateien gemeinsam; fehlerhaftes YAML, doppelte Keys oder inkompatible dynamische Placeholder verhindern die Veröffentlichung des Kandidaten. Fallback Deutsch: Live-DE → gebündeltes DE → Live-EN → gebündeltes EN; Englisch: Live-EN → gebündeltes EN.

Kataloge dürfen flache semantische Keys (`common.close`) oder gleichwertige verschachtelte Sections verwenden. EN/DE müssen dieselben dynamischen Placeholder besitzen; Farbtags gehören nicht zu dieser Menge. Namen, Staff-Gründe und PM-/Chat-Body werden als literal Components eingesetzt und können keine MiniMessage-Tags injizieren.

Bestehende operator-authored Strings und Lore-Listen gelten unverändert für alle Spieler. Für Lobby-Item-Namen/Lore und PM-/Join-/Quit-Formate sind Sprachmaps mit `en` als Fallback möglich:

```yaml
items:
  friends:
    enabled: true
    slot: 6 # Beispiel; Live lobby.yml bestimmt den Slot
    material: PLAYER_HEAD
    head-owner: self
    name:
      en: "<aqua>Friends"
      de: "<aqua>Freunde"
    lore:
      en: ["<gray>Manage your friends", "", "<yellow>Click to open"]
      de: ["<gray>Verwalte deine Freunde", "", "<yellow>Klicke zum Öffnen"]
```

Bei Quest-Namen/Beschreibungen gilt dieselbe `en/de`-Map, aber deren Werte sind **Plain Text**, keine MiniMessage-Vorlagen. Technische Quest-/Feature-IDs werden nicht übersetzt. Lokalisierte Join-/Quit-Formate enthalten weiterhin den sicheren `<name>`-Placeholder und werden pro Empfänger ausgegeben. PM-Formate enthalten die bisherigen `<sender>`, `<recipient>`, `<message>`-Placeholder. Die neuen Default-Aliase `<outgoing>`, `<incoming>` liefern den ganzen katalogbasierten PM-Wrapper. `<reward>` liefert den katalogbasierten Playtime-Reward-Hinweis; bisherige Reward-Templates bleiben universal gültig.

In Lobby-/Presentation-/PM-MiniMessage-Vorlagen kann `<i18n:common.close>` ausdrücklich einen parameterlosen Katalogtext referenzieren. Diese Referenz definiert ausschließlich Text, nie einen Command oder eine Java-Aktion. Dynamische Katalogvorlagen benötigen die vom jeweiligen Feature gelieferten sicheren Argumente; füge sie nicht als parameterlose Referenz ein. Für neue eigene Sidebar-/Header-/Footer-Texte sind etwa `<i18n:presentation.sidebar.coins>`, `<i18n:presentation.sidebar.rank>` und `<i18n:presentation.tablist.tagline>` vorgesehen.

### Bestehende Installation auf übersetzte Standardtexte umstellen

Ein neues Plugin-JAR und `/core reload` ersetzen keine vorhandenen Texte in `lobby.yml`, `presentation.yml`, `private-messages.yml` oder `config.yml`. Ein alter englischer Header oder ein deutscher Friends-Name bleibt deshalb für beide Clientsprachen gleich. Das Anpassen von `lang/de.yml` wirkt nur, wenn die jeweilige Vorlage diesen Sprachschlüssel verwendet. Vor Änderungen die Live-Dateien sichern und gezielt die gewünschten Textfelder ersetzen; Slots, Materialien, Spawn, Rang-/Clanformate und eigene Texte beibehalten.

| Live-Textfeld | Übersetzte Standardvorlage |
|---|---|
| `presentation.yml`: `tablist.header` (Textzeile nach dem Servernamen) | `<i18n:presentation.tablist.tagline>` |
| `presentation.yml`: `tablist.footer` | `<i18n:presentation.tablist.footer>` |
| `presentation.yml`: Labels in `scoreboard.lines` | `<i18n:presentation.sidebar.coins>`, `<i18n:presentation.sidebar.rank>`, `<i18n:presentation.sidebar.playtime>` |
| `lobby.yml`: `items.navigator.name` / `lore` | `<i18n:lobby.navigator.name>` / `<i18n:lobby.navigator.lore>` |
| `lobby.yml`: `items.visibility.name` / `lore` | `<i18n:lobby.visibility.name>` / `<i18n:lobby.visibility.lore>` |
| `lobby.yml`: `items.visibility.filtered.name` / `lore` | `<i18n:lobby.visibility.filtered-name>` / `<i18n:lobby.visibility.filtered-lore>` |
| `lobby.yml`: `items.settings.name` / `lore` | `<i18n:lobby.settings.name>` / `<i18n:lobby.settings.lore>` |
| `lobby.yml`: `items.friends.name` / `lore` | `<i18n:lobby.friends.name>` / Liste aus `<i18n:lobby.friends.lore>`, Leerzeile, `<i18n:lobby.friends.open>` |
| `private-messages.yml`: `format.outgoing` / `format.incoming` | `<outgoing>` / `<incoming>` |
| `config.yml`: `online-rewards.message.format` | `<reward>` |

Header/Footer und Lore bleiben YAML-Listen; die Referenzen sind deren einzelne String-Einträge. Nach `/core reload` folgen diese Texte der aktuellen Minecraft-Clientsprache. Für eigene zweisprachige Header/Footer einen eigenen parameterlosen Key in beiden Sprachdateien anlegen und ausdrücklich referenzieren. Für eigene Lobby-Namen/Lore kann alternativ die oben gezeigte `en/de`-Map verwendet werden. Feste Strings werden absichtlich nicht anhand ihres Inhalts automatisch übersetzt.

Alle normalen Menüs einschließlich Player Menu übersetzen Titel, Icons und Lore für ihren Besitzer. Sprachwechsel aktualisieren aktive Items; native Inventartitel werden beim nächsten Open neu erstellt. Eigene Sidebar/Tablist-Header/Footer sind persönlich. Native Nametag-/Tablist-Namen, Chat-Wrappers und global sichtbare Blackjack-/Preview-Anzeigen bleiben gemeinsame Texte; eigene Katalogtexte in den gemeinsamen Weltanzeigen sind Englisch. Servermarke, Clan-/Warp-Namen, LuckPerms-Prefix/Suffix und Benutzertexte bleiben unverändert. Frühzeitige Ban-Login-Ablehnung verwendet Englisch.

---

# Inhaltsverzeichnis

1. [Schnellübersicht](#1-schnellübersicht)
2. [Welche Formatierung wird wo verwendet?](#2-welche-formatierung-wird-wo-verwendet)
3. [Adventure / MiniMessage Farben](#3-adventure--minimessage-farben)
4. [Hex-Farben](#4-hex-farben)
5. [Textformatierungen](#5-textformatierungen)
6. [Gradients](#6-gradients)
7. [LuckPerms Rank-System](#7-luckperms-rank-system)
8. [Rank Display Names](#8-rank-display-names)
9. [Rank Colors](#9-rank-colors)
10. [Rank Descriptions](#10-rank-descriptions)
11. [Neue Ränge erstellen](#11-neue-ränge-erstellen)
12. [Rank Track](#12-rank-track)
13. [VapeeCore Rank Placeholder](#13-vapeecore-rank-placeholder)
14. [Chat Placeholder](#14-chat-placeholder)
15. [Presentation Placeholder](#15-presentation-placeholder)
16. [Chat konfigurieren](#16-chat-konfigurieren)
17. [Tablist konfigurieren](#17-tablist-konfigurieren)
18. [Scoreboard konfigurieren](#18-scoreboard-konfigurieren)
19. [Playtime](#19-playtime)
   - [Online-Reward-Nachricht](#online-reward-nachricht)
20. [LuckPerms Prefix und Suffix](#20-luckperms-prefix-und-suffix)
21. [Legacy `&` Farben](#21-legacy--farben)
22. [`§` Farbcodes](#22--farbcodes)
23. [LuckPerms Meta Format](#23-luckperms-meta-format)
24. [Live-Config vs Resource-Default](#24-live-config-vs-resource-default)
25. [Reload](#25-reload)
26. [Häufige Fehler](#26-häufige-fehler)
27. [Beispielkonfiguration](#27-beispielkonfiguration)
28. [Komplettes Rank-Beispiel](#28-komplettes-rank-beispiel)
29. [Kurzreferenz](#29-kurzreferenz)

---

# 1. Schnellübersicht

VapeeCore verwendet mehrere Formatierungsarten.

Die wichtigste Regel lautet:

| Ort | Format | Beispiel |
|---|---|---|
| `chat.yml` | MiniMessage | `<gold>Text</gold>` |
| `presentation.yml` | MiniMessage | `<aqua>Text</aqua>` |
| `config.yml -> messages.prefix` | MiniMessage | `<gray>[<aqua>VapeeCore</aqua>]</gray>` |
| `config.yml -> online-rewards.message.format` | MiniMessage | `<green>+<coins> Coins</green>` |
| `vapeecore.rank.color` | Adventure-Farbname oder Hex | `gold` |
| `vapeecore.rank.color` | Adventure-Hex | `#ffaa00` |
| LuckPerms Prefix/Suffix mit `legacy-ampersand` | Legacy `&` | `&6VIP &8| &f` |
| LuckPerms Prefix/Suffix mit `mini-message` | MiniMessage | `<gold>VIP</gold> ` |
| LuckPerms Prefix/Suffix mit `plain` | Plain Text | `VIP ` |

## Merksatz

### In VapeeCore YAML-Templates

```text
<gold>Text</gold>
```

### Bei `vapeecore.rank.color`

```text
gold
```

### Bei LuckPerms Legacy Prefix/Suffix

```text
&6VIP
```

### Nicht verwenden

```text
§6VIP
```

---

# 2. Welche Formatierung wird wo verwendet?

## `chat.yml`

Das eigentliche Chat-Template verwendet MiniMessage.

Beispiel:

```yaml
format: "<rank_name><dark_gray> » </dark_gray><white><message></white>"
```

Hier werden Farben mit `<...>` geschrieben.

---

## `presentation.yml`

Scoreboard, Tablist Header, Tablist Footer und Player-Name-Format verwenden MiniMessage.

Beispiel:

```yaml
scoreboard:
  title: "<aqua><bold><server></bold></aqua>"
```

---

## `config.yml`

Der globale Message-Prefix wird über `MessageService` als MiniMessage verarbeitet. Der Default ist `messages.prefix: ""`; normale Core-Nachrichten benötigen kein Pluginlabel. Ein expliziter eigener Prefix bleibt unterstützt.

Beispiel für einen bewusst gewählten eigenen Prefix:

```yaml
messages:
  prefix: "<gray>[<aqua>VapeeCore</aqua>]</gray> "
```

Auch `server.name` unterstützt MiniMessage-Farben und Formatierungen:

```yaml
server:
  name: "<red>Vapee Community</red>"
```

Alternativ sind Hex-Farben (`<#ff8800>Vapee Community`) und Gradients (`<gradient:red:yellow>Vapee Community</gradient>`) möglich. Explizite Farben im Namen bleiben auch innerhalb eines Aqua-Templates in `presentation.yml` erhalten. Ohne eigene Farbe übernimmt der Name weiterhin die Template-Farbe. `/rank` und `/ranks` zeigen den formatierten Namen ebenfalls. Nach dem Ändern `/core reload` ausführen; für die neue Unterstützung zuerst die aktualisierte Plugin-JAR installieren und den Server neu starten.

Auch `online-rewards.message.format` ist ein MiniMessage-Template. Es verwendet sichere numerische Placeholder; Details stehen im Abschnitt [Online-Reward-Nachricht](#online-reward-nachricht).

---

## LuckPerms Rank Color

Der Rank-Color-Meta-Wert verwendet **keine MiniMessage-Tags**.

Richtig:

```text
gold
```

oder:

```text
#ffaa00
```

Falsch:

```text
<gold>
```

Falsch:

```text
&6
```

Falsch:

```text
§6
```

---

# 3. Adventure / MiniMessage Farben

VapeeCore verwendet Adventure Components.

Die folgenden klassischen Minecraft-Farben können in MiniMessage verwendet werden.

| MiniMessage | Farbe | Hex | Legacy |
|---|---|---:|---:|
| `<black>` | Schwarz | `#000000` | `&0` |
| `<dark_blue>` | Dunkelblau | `#0000AA` | `&1` |
| `<dark_green>` | Dunkelgrün | `#00AA00` | `&2` |
| `<dark_aqua>` | Dunkelaqua | `#00AAAA` | `&3` |
| `<dark_red>` | Dunkelrot | `#AA0000` | `&4` |
| `<dark_purple>` | Dunkellila | `#AA00AA` | `&5` |
| `<gold>` | Gold / Orange | `#FFAA00` | `&6` |
| `<gray>` | Grau | `#AAAAAA` | `&7` |
| `<dark_gray>` | Dunkelgrau | `#555555` | `&8` |
| `<blue>` | Blau | `#5555FF` | `&9` |
| `<green>` | Hellgrün | `#55FF55` | `&a` |
| `<aqua>` | Aqua / Hellblau | `#55FFFF` | `&b` |
| `<red>` | Hellrot | `#FF5555` | `&c` |
| `<light_purple>` | Helllila / Pink | `#FF55FF` | `&d` |
| `<yellow>` | Gelb | `#FFFF55` | `&e` |
| `<white>` | Weiß | `#FFFFFF` | `&f` |

Beispiele:

```text
<gray>Normaler Text</gray>
```

```text
<gold>Coins</gold>
```

```text
<dark_red>Owner</dark_red>
```

```text
<aqua>Builder</aqua>
```

```text
<green>Moderator</green>
```

---

# 4. Hex-Farben

Minecraft unterstützt über Adventure RGB-Farben.

Dadurch können neben den 16 klassischen Farben beliebige RGB-Farben verwendet werden.

## MiniMessage

```text
<#ff5500>Orange</#ff5500>
```

```text
<#55ffaa>Mint</#55ffaa>
```

```text
<#c35cff>Lila</#c35cff>
```

Beispiel:

```yaml
title: "<#00d9ff><bold>Vapee Community</bold></#00d9ff>"
```

---

## Rank Color

Bei einem Rank wird nur der Hex-Wert gespeichert:

```text
/lp group developer meta set vapeecore.rank.color #c35cff
```

Nicht:

```text
/lp group developer meta set vapeecore.rank.color <#c35cff>
```

VapeeCore akzeptiert beim Rank-Color-Meta exakt das Format:

```text
#RRGGBB
```

Beispiel:

```text
#55ffaa
#c35cff
#ff5555
#ffaa00
```

---

# 5. Textformatierungen

MiniMessage unterstützt zusätzlich Text-Dekorationen.

## Fett

```text
<bold>Text</bold>
```

Kurzform:

```text
<b>Text</b>
```

---

## Kursiv

```text
<italic>Text</italic>
```

---

## Unterstrichen

```text
<underlined>Text</underlined>
```

---

## Durchgestrichen

```text
<strikethrough>Text</strikethrough>
```

---

## Kombinationen

```text
<gold><bold>VIP</bold></gold>
```

```text
<dark_red><bold>Owner</bold></dark_red>
```

```text
<#c35cff><bold>Developer</bold></#c35cff>
```

---

## Reset

MiniMessage unterstützt:

```text
<reset>
```

Beispiel:

```text
<red>Rot <reset>Normal
```

Normalerweise ist es übersichtlicher, Tags sauber zu schließen:

```text
<red>Rot</red> Normal
```

---

# 6. Gradients

MiniMessage unterstützt Farbverläufe.

Beispiel:

```text
<gradient:#00ffff:#0088ff>Vapee Community</gradient>
```

Mit mehreren Farben:

```text
<gradient:#00ffff:#8855ff:#ff55aa>Vapee Community</gradient>
```

Geeignet für:

- Servernamen
- Überschriften
- besondere Events
- besondere kosmetische Texte

Nicht empfohlen für:

- normale Chatnachrichten
- jeden Scoreboard-Eintrag
- lange Texte
- jede einzelne UI-Komponente

Beispiel:

```yaml
scoreboard:
  title: "<gradient:#00ffff:#0088ff><bold>Vapee Community</bold></gradient>"
```

---

# 7. LuckPerms Rank-System

LuckPerms ist die Source of Truth für Ränge.

VapeeCore besitzt keine eigene Rank-Mitgliedschaft.

Ein Rank besteht im Wesentlichen aus:

```text
LuckPerms Group ID
Display Name
Rank Color
optional Description
optional Weight
```

Beispiel:

```text
Group ID:
owner

Display Name:
Owner

Color:
dark_red

Description:
Serverleitung.
```

VapeeCore erkennt Ränge dynamisch.

Es existiert keine Java-Logik wie:

```java
if (rank.equals("owner")) {
    ...
}
```

Dadurch können später neue Ränge hinzugefügt werden, ohne VapeeCore neu programmieren zu müssen.

---

# 8. Rank Display Names

LuckPerms Group IDs sind technische Namen.

Beispiel:

```text
default
```

Das soll Spielern nicht unbedingt angezeigt werden.

Deshalb besitzt LuckPerms einen Display Name.

Beispiel:

```text
default
→ User
```

Command:

```text
/lp group default setdisplayname User
```

Weitere Beispiele:

```text
/lp group vip setdisplayname VIP
/lp group builder setdisplayname Builder
/lp group moderator setdisplayname Moderator
/lp group admin setdisplayname Admin
/lp group owner setdisplayname Owner
```

Dann gilt:

```text
<rank_id>
→ default
```

aber:

```text
<rank>
→ User
```

---

# 9. Rank Colors

VapeeCore verwendet folgenden LuckPerms Meta-Key:

```text
vapeecore.rank.color
```

Beispiel:

```text
/lp group owner meta set vapeecore.rank.color dark_red
```

## Unterstützt

Adventure Named Colors:

```text
black
dark_blue
dark_green
dark_aqua
dark_red
dark_purple
gold
gray
dark_gray
blue
green
aqua
red
light_purple
yellow
white
```

und RGB Hex:

```text
#RRGGBB
```

Beispiel:

```text
/lp group developer meta set vapeecore.rank.color #c35cff
```

---

## Empfohlenes aktuelles Setup

```text
/lp group default meta set vapeecore.rank.color gray
/lp group vip meta set vapeecore.rank.color gold
/lp group builder meta set vapeecore.rank.color aqua
/lp group moderator meta set vapeecore.rank.color green
/lp group admin meta set vapeecore.rank.color red
/lp group owner meta set vapeecore.rank.color dark_red
```

Diese Werte sind Server-Konfiguration und nicht im Java-Code hardcodiert.

---

## Fehlende Farbe

Wenn kein:

```text
vapeecore.rank.color
```

gesetzt wurde, funktioniert der Rank weiterhin.

VapeeCore verwendet einen neutralen weißen Fallback.

---

## Ungültige Farbe

Beispiel:

```text
vapeecore.rank.color = banana
```

wird nicht als gültige Farbe erkannt.

Der Rank bleibt funktionsfähig und fällt auf die neutrale Standardfarbe zurück.

---

# 10. Rank Descriptions

VapeeCore unterstützt Rank-Beschreibungen über:

```text
vapeecore.rank.description
```

Beispiel:

```text
/lp group owner meta set vapeecore.rank.description "Serverleitung."
```

Weitere Beispiele:

```text
/lp group default meta set vapeecore.rank.description "Standardrang für alle Spieler."
```

```text
/lp group vip meta set vapeecore.rank.description "Supporter-Rang für Unterstützer des Servers."
```

```text
/lp group builder meta set vapeecore.rank.description "Baut und gestaltet die Serverwelt."
```

```text
/lp group moderator meta set vapeecore.rank.description "Kümmert sich um Community und Moderation."
```

```text
/lp group admin meta set vapeecore.rank.description "Verwaltet den Server und dessen Systeme."
```

```text
/lp group owner meta set vapeecore.rank.description "Serverleitung."
```

---

# 11. Neue Ränge erstellen

Neue Ränge benötigen keine Java-Codeänderung.

Beispiel:

```text
/lp creategroup developer
```

Display Name:

```text
/lp group developer setdisplayname Developer
```

Farbe:

```text
/lp group developer meta set vapeecore.rank.color #c35cff
```

Beschreibung:

```text
/lp group developer meta set vapeecore.rank.description "Entwickelt technische Systeme für den Server."
```

Zum öffentlichen Rank-Track hinzufügen:

```text
/lp track ranks append developer
```

Danach kann VapeeCore automatisch darstellen:

```text
<rank>
→ Developer
```

in:

```text
#c35cff
```

und:

```text
<rank_name>
```

färbt automatisch den Spielernamen mit derselben Farbe.

---

# 12. Rank Track

Der von VapeeCore verwendete öffentliche Rank-Track wird in:

```text
config.yml
```

festgelegt.

Aktuell:

```yaml
ranks:
  track: "ranks"
```

Der entsprechende LuckPerms Track kann beispielsweise so erstellt werden:

```text
/lp createtrack ranks
```

Ranks hinzufügen:

```text
/lp track ranks append default
/lp track ranks append vip
/lp track ranks append builder
/lp track ranks append moderator
/lp track ranks append admin
/lp track ranks append owner
```

Überprüfen:

```text
/lp track ranks info
```

Die Reihenfolge des Tracks wird von `/ranks` übernommen.

VapeeCore sortiert die Ränge nicht anhand ihrer Farbe oder ihres Namens neu.

---

# 13. VapeeCore Rank Placeholder

## `<rank>`

Der freundliche Rank Display Name inklusive Rank-Farbe.

Beispiel:

```text
LuckPerms Group:
owner

Display Name:
Owner

Color:
dark_red
```

Ergebnis:

```text
<rank>
→ Owner
```

`Owner` wird dabei dunkelrot dargestellt.

---

## `<rank_id>`

Die technische LuckPerms Primary Group ID.

Beispiel:

```text
<rank_id>
→ owner
```

Dieser Placeholder ist hauptsächlich für technische oder Debug-Zwecke gedacht.

---

## `<group>`

Compatibility Alias für:

```text
<rank_id>
```

Beispiel:

```text
<group>
→ owner
```

Für sichtbare UI sollte normalerweise `<rank>` verwendet werden.

---

## `<rank_name>`

Der Spielername in der Farbe seines Primary Ranks.

Beispiel:

```text
Spieler:
rx29

Rank:
Owner

Rank Color:
dark_red
```

Ergebnis:

```text
<rank_name>
→ rx29
```

`rx29` wird dunkelrot dargestellt.

---

## `<name>`

Der normale Player Display Name.

```text
<name>
→ rx29
```

Es wird dabei keine Rank-Farbe durch VapeeCore erzwungen.

---

# 14. Chat Placeholder

In `chat.yml` stehen folgende Placeholder zur Verfügung:

| Placeholder | Bedeutung |
|---|---|
| `<name>` | normaler Player Display Name |
| `<rank_name>` | Player Display Name mit Rank-Farbe |
| `<prefix>` | LuckPerms Prefix |
| `<suffix>` | LuckPerms Suffix |
| `<rank>` | freundlicher Rank Display Name mit Rank-Farbe |
| `<rank_id>` | technische Primary Group ID |
| `<group>` | Alias für `<rank_id>` |
| `<message>` | originale Chatnachricht als Adventure Component |

## Empfohlen ohne Prefix

```yaml
format: "<rank_name><dark_gray> » </dark_gray><white><message></white>"
```

Beispiel:

```text
rx29 » Hallo
```

Der Name erhält automatisch die Rank-Farbe.

---

## Mit Rank davor

Beispiel:

```yaml
format: "<rank> <rank_name><dark_gray> » </dark_gray><white><message></white>"
```

Ergebnis ungefähr:

```text
Owner rx29 » Hallo
```

Rank und Name besitzen die konfigurierte Rank-Farbe.

---

## Mit LuckPerms Prefix

```yaml
format: "<prefix><name><suffix><dark_gray> » </dark_gray><white><message></white>"
```

Hier wird `<name>` verwendet.

Die Rank-Farbe von `vapeecore.rank.color` wird dadurch **nicht automatisch auf den Namen übertragen**.

Für Rank-Farbe sollte stattdessen:

```text
<rank_name>
```

verwendet werden.

---

# 15. Presentation Placeholder

In `presentation.yml` stehen zur Verfügung:

| Placeholder | Bedeutung |
|---|---|
| `<server>` | Servername |
| `<name>` | normaler Player Display Name |
| `<rank_name>` | Player Display Name mit Rank-Farbe |
| `<prefix>` | LuckPerms Prefix |
| `<suffix>` | LuckPerms Suffix |
| `<rank>` | Rank Display Name mit Rank-Farbe |
| `<rank_id>` | technische LuckPerms Group ID |
| `<group>` | Compatibility Alias für `<rank_id>` |
| `<clan_tag>` | kanonischer Clan-Tag als wörtlicher Text; ohne verfügbaren Clan leer |
| `<clan_tag_display>` | konfigurierter Clan-Wrapper mit literal Tag; ohne gültigen Tag vollständig leer |
| `<playtime>` | kompakte Minecraft-Spielzeit |
| `<coins>` | aktuelle Coins |
| `<online>` | aktuelle Online-Spieler |
| `<max_players>` | maximale Spieleranzahl |

`<clan_tag>` gilt für die Presentation-Templates
(Sidebar-Titel/-Zeilen, Tablist-Name, Header, Footer und Nametag-Prefix/-Suffix). Der Wert ist weder
Clan-Name noch Clan-ID. MiniMessage-artiger Clan-Text wie `<red>` wird wörtlich
angezeigt; Farben können im umgebenden Admin-Template gesetzt werden. Ohne Clan
oder bei fehlendem/ungültigem Read ist der Wert leer, ohne automatische Klammern
oder Abstände. `<clan_tag_display>` ergänzt bei vorhandenem Tag den in
`clan-tag-format` definierten MiniMessage-Wrapper. Dessen dynamischer Inhalt ist
`<clan_tag>`; andere Presentation-Placeholder gehören in die äußeren Templates.
Die neuen Resource-Defaults verwenden `<rank_name><clan_tag_display>`
für den Tablist-Namen sowie einen leeren Prefix und `<clan_tag_display>` für native
Overhead-Team-Prefix/-Suffix. Der Team-Entry bleibt der echte Spielername.
Seine native Team-Farbe stammt aus `RankInfo.effectiveColor()` beziehungsweise
LuckPerms-Meta `vapeecore.rank.color`, derselben Quelle wie Chat und Tablist.
Alle 16 benannten Minecraft-Farben werden exakt gesetzt, auch bei identischem
Hexwert (z. B. `blue` und `#5555ff`). Beliebiges RGB kann das Vanilla-Team nicht
darstellen: es erhält ausdrücklich Weiß mit einmaliger Logwarnung je RGB-Wert
und Renderer-Lifetime. Es gibt keine stille Näherungsfarbe. Tablist und Chat
behalten die volle RGB-Farbe. Fehlende/ungültige Rankfarbe verwendet weiterhin
den bestehenden neutralen weißen RankInfo-Fallback.
Sichtbare Ranklabels werden nicht automatisch eingefügt. Wer sie möchte,
kann `nametag.prefix: "<prefix>"` und
`tablist.name-format: "<prefix><rank_name><clan_tag_display>"` konfigurieren.
Eine manuelle Änderung der Live-Datei wird mit `/core reload` übernommen.
Bestehende Dateien werden nicht umgeschrieben. Fehlende Nametag-Keys ergeben
`enabled: true`, `lobby-only: true`, `prefix: ""`,
`suffix: "<clan_tag_display>"`. Der gemeinsame Wrapper ist standardmäßig
`" <dark_gray>[</dark_gray><gray><clan_tag></gray><dark_gray>]</dark_gray>"`.
Resource-Pack-Glyph-Assets bleiben ein späterer optionaler Ausbau.

---

# 16. Chat konfigurieren

Datei:

```text
plugins/VapeeCore/chat.yml
```

Empfohlene Konfiguration ohne sichtbaren Prefix:

```yaml
enabled: true

format: "<rank_name><dark_gray> » </dark_gray><white><message></white>"

luckperms-meta:
  format: "legacy-ampersand"
```

Beispiel:

```text
Owner-Farbe:
dark_red

Spieler:
rx29

Nachricht:
Hallo
```

Ergebnis:

```text
rx29 » Hallo
```

Dabei:

```text
rx29
```

in `dark_red`.

---

# 17. Tablist konfigurieren

Datei:

```text
plugins/VapeeCore/presentation.yml
```

Für Rank-Farbe beim Spielernamen:

```yaml
tablist:
  enabled: true
  name-format: "<rank_name>"
```

Nicht:

```yaml
name-format: "<name>"
```

wenn der Name anhand des Rangs gefärbt werden soll.

Nicht:

```yaml
name-format: "<prefix><name><suffix>"
```

wenn ausschließlich die neue Rank-Color-Funktion verwendet werden soll.

---

## Beispiel

```yaml
tablist:
  enabled: true
  name-format: "<rank_name>"

  header:
    - "<aqua><bold><server></bold></aqua>"
    - "<gray>Welcome <white><name></white></gray>"

  footer:
    - "<gray>Online: <white><online>/<max_players></white></gray>"
    - "<gray>Coins: <gold><coins></gold></gray>"
```

---

# 18. Scoreboard konfigurieren

Beispiel im aktuellen VapeeCore-Stil:

```yaml
scoreboard:
  enabled: true
  lobby-only: true

  title: "<aqua><bold><server></bold></aqua>"

  lines:
    - ""
    - "<i18n:presentation.sidebar.rank>"
    - "<rank>"
    - ""
    - "<i18n:presentation.sidebar.playtime>"
    - "<yellow><playtime></yellow>"
    - ""
    - "<i18n:presentation.sidebar.coins>"
    - "<gold><coins></gold>"
    - ""
    - "<gray>Online:</gray> <white><online>/<max_players></white>"
    - ""
```

Das ergibt für einen englischen Client ungefähr; ein deutscher Client sieht stattdessen etwa `Rang` und `Spielzeit`:

```text
Vapee Community

Rank:
Owner

Playtime:
6h 43m

Coins:
12,500

Online: 3/20
```

`Owner` übernimmt seine Rank-Farbe automatisch.

---

## Wichtig bei `<rank>`

Empfohlen:

```yaml
- "<rank>"
```

Nicht notwendig:

```yaml
- "<white><rank></white>"
```

`<rank>` besitzt bereits seine eigene Farbe.

---

# 19. Playtime

VapeeCore besitzt den Placeholder:

```text
<playtime>
```

Die Spielzeit wird nicht selbst in VapeeCore gespeichert.

Quelle ist die Minecraft/Paper Statistic:

```java
Statistic.PLAY_ONE_MINUTE
```

Trotz des historischen Namens repräsentiert der Wert gespielte Ticks.

```text
20 Ticks = 1 Sekunde
```

---

## Formatierung

### Unter einer Stunde

```text
43m
```

### Unter 24 Stunden

```text
6h 43m
```

### Ab 24 Stunden

```text
2d 6h
```

Maximal zwei sinnvolle Einheiten werden angezeigt.

---

## Farbe festlegen

`<playtime>` selbst liefert nur den Text.

Die Farbe wird im Template gewählt:

```yaml
- "<yellow><playtime></yellow>"
```

oder:

```yaml
- "<aqua><playtime></aqua>"
```

oder:

```yaml
- "<#55ffaa><playtime></#55ffaa>"
```

---

## Online-Reward-Nachricht

Die automatische Nachricht nach einem erfolgreichen Playtime-Reward steht in der Live-Datei:

```text
plugins/VapeeCore/config.yml
```

Beispiel:

```yaml
online-rewards:
  message:
    enabled: true
    format: "<reward>"
```

`format` ist MiniMessage und unterstützt diese Online-Reward-Placeholder:

| Placeholder | Bedeutung |
|---|---|
| `<coins>` | tatsächlich mit diesem Reward gewährte Coins |
| `<minutes>` | tatsächlich vergütete Minuten |
| `<intervals>` | Anzahl gleichzeitig vergüteter Intervalle |
| `<balance>` | neue Coin-Balance nach dem Reward |
| `<reward>` | Vollständiger EN-/DE-Kataloghinweis mit sicheren Coin-/Minutenwerten |

Bei drei gleichzeitig fälligen 60-Minuten-Intervallen mit je 250 Coins sind die Werte beispielsweise `750`, `180`, `3` und die danach aktuelle Balance. Die Werte werden als sichere unparsed Placeholder eingesetzt und nicht erneut als MiniMessage interpretiert. Ein ungültiges Template verwendet den internen Default und verändert die Live-Datei nicht.

`<playtime>` gehört weiterhin ausschließlich zu `presentation.yml` und zeigt die gesamte Minecraft-Spielzeit. Es ist kein Placeholder für `online-rewards.message.format`; umgekehrt stehen diese Reward-Placeholder nicht in Scoreboard oder Tablist zur Verfügung.

---

# 20. LuckPerms Prefix und Suffix

VapeeCore unterstützt weiterhin klassische LuckPerms Prefixes und Suffixes.

Placeholder:

```text
<prefix>
<suffix>
```

Die Art, wie diese Metadaten interpretiert werden, wird separat konfiguriert.

Beispiel:

```yaml
luckperms-meta:
  format: "legacy-ampersand"
```

---

## Beispiel Prefix

```text
/lp group vip meta setprefix 200 "&6VIP &8| &f"
```

Dann kann beispielsweise verwendet werden:

```yaml
format: "<prefix><name><suffix><dark_gray> » </dark_gray><white><message></white>"
```

---

## Wichtig

Ein Prefix wie:

```text
&6
```

ist **nicht** die empfohlene Methode, um `<name>` einzufärben.

Prefix und Spielername sind getrennte Adventure Components.

Wenn der Spielername anhand des Rangs gefärbt werden soll:

```text
<rank_name>
```

verwenden.

---

# 21. Legacy `&` Farben

Wenn:

```yaml
luckperms-meta:
  format: "legacy-ampersand"
```

aktiv ist, können LuckPerms Prefixes und Suffixes klassische `&`-Codes verwenden.

| Code | Farbe |
|---|---|
| `&0` | black |
| `&1` | dark_blue |
| `&2` | dark_green |
| `&3` | dark_aqua |
| `&4` | dark_red |
| `&5` | dark_purple |
| `&6` | gold |
| `&7` | gray |
| `&8` | dark_gray |
| `&9` | blue |
| `&a` | green |
| `&b` | aqua |
| `&c` | red |
| `&d` | light_purple |
| `&e` | yellow |
| `&f` | white |

---

## Legacy Textformatierung

| Code | Format |
|---|---|
| `&l` | Bold |
| `&o` | Italic |
| `&n` | Underlined |
| `&m` | Strikethrough |
| `&k` | Obfuscated |
| `&r` | Reset |

Beispiel:

```text
&6&lVIP &8| &f
```

---

# 22. `§` Farbcodes

`§`-Farbcodes sollen in VapeeCore nicht verwendet werden.

Nicht:

```text
§6VIP
```

Nicht:

```text
§cAdmin
```

Nicht:

```text
§4Owner
```

Der aktuelle Legacy-Serializer von VapeeCore ist für:

```text
&
```

konfiguriert.

Deshalb:

```text
&6VIP
```

statt:

```text
§6VIP
```

---

# 23. LuckPerms Meta Format

`chat.yml` und `presentation.yml` besitzen jeweils:

```yaml
luckperms-meta:
  format: "legacy-ampersand"
```

VapeeCore unterstützt drei Varianten.

---

## `legacy-ampersand`

```yaml
luckperms-meta:
  format: "legacy-ampersand"
```

LuckPerms Prefix/Suffix:

```text
&6VIP &8| &f
```

---

## `mini-message`

```yaml
luckperms-meta:
  format: "mini-message"
```

LuckPerms Prefix/Suffix könnten dann beispielsweise sein:

```text
<gold>VIP</gold> <dark_gray>|</dark_gray> 
```

---

## `plain`

```yaml
luckperms-meta:
  format: "plain"
```

Formatierung wird nicht interpretiert.

Beispiel:

```text
VIP |
```

wird als normaler Text dargestellt.

---

## Empfehlung

Für bestehende klassische LuckPerms-Prefixes:

```yaml
format: "legacy-ampersand"
```

beibehalten.

Für das aktuelle VapeeCore-Design ohne Prefix ist dieser Wert weniger wichtig, weil die Rank-Farbe über:

```text
vapeecore.rank.color
```

gesteuert wird.

---

# 24. Live-Config vs Resource-Default

VapeeCore besitzt zwei Arten von Config-Dateien.

## Resource Defaults

Im Source Code:

```text
src/main/resources/chat.yml
src/main/resources/presentation.yml
src/main/resources/config.yml
```

Diese Dateien dienen als Vorlage für neue Installationen.

---

## Live Config

Auf dem laufenden Server:

```text
plugins/VapeeCore/chat.yml
plugins/VapeeCore/presentation.yml
plugins/VapeeCore/config.yml
```

Diese Dateien werden tatsächlich vom Server verwendet.

---

## Wichtig

Wenn VapeeCore aktualisiert wird, werden bestehende Live-Configs nicht automatisch durch neue Resource Defaults überschrieben.

Das bedeutet:

Eine Änderung an:

```text
src/main/resources/presentation.yml
```

ändert nicht automatisch:

```text
plugins/VapeeCore/presentation.yml
```

Fehlende neue Einstellungen ergänzt die Startup-Evolution bei einer älteren `config-version` nach einem exakten Backup unter `plugins/VapeeCore/backups/config/`. Die feste Allowlist enthält `config.yml`, `lobby.yml`, `chat.yml`, `private-messages.yml`, `presentation.yml` und `daily-quests.yml` (derzeit jeweils Version 1). Vorhandene Texte und Werte, unbekannte Keys, Kommentare sowie vorhandene Listen bleiben erhalten. Zukünftige Versionen werden mit Warnung unverändert gelassen. Ein weiterer Startup derselben unterstützten Version und `/core reload` erzeugen keine Migrationswrites. Persistence-Dateien sind ausgeschlossen. Details: [Developer Guide](DEVELOPER_GUIDE.md#defaults-und-live-konfiguration).

Bereits gesetzte Formate müssen bei einer gewünschten Darstellungsänderung weiter manuell angepasst werden.

Für Rank-Farben in bestehenden Live-Configs konkret:

```yaml
# plugins/VapeeCore/chat.yml
format: "<rank_name><dark_gray> » </dark_gray><white><message></white>"
```

```yaml
# plugins/VapeeCore/presentation.yml
tablist:
  name-format: "<rank_name>"
```

Wer Prefix und Suffix anzeigen möchte, kann sie um `<rank_name>` ergänzen. Der bisherige `<prefix><name><suffix>`-Namenspfad färbt `<name>` nicht automatisch. Nach der manuellen Änderung `/core reload` ausführen; VapeeCore überschreibt die Live-Dateien nicht.

---

# 25. Reload

`/core reload` umfasst die sechs Operator-Configs und beide Sprachdateien (`lang/en.yml`, `lang/de.yml`) über sieben Teilnehmer. Der gemeinsame Prepare-/Apply-/Rollback-Vertrag bewahrt bei Fehlern den vorherigen aktiven Stand; vorhandene Dateien werden dabei nicht geschrieben.

Nach Änderungen an VapeeCore Config-Dateien:

```text
/core reload
```

verwenden.

Beispiele:

Nach Änderung von:

```text
chat.yml
```

oder:

```text
presentation.yml
```

ausführen:

```text
/core reload
```

Ein Bukkit-/Paper-`/reload` soll für VapeeCore nicht verwendet werden.

---

# 26. Häufige Fehler

## Problem: Scoreboard zeigt `default`

Falsch:

```yaml
- "<group>"
```

oder:

```yaml
- "<rank_id>"
```

Beide geben die technische Group-ID aus.

Verwende:

```yaml
- "<rank>"
```

Dann wird beispielsweise:

```text
default
```

als:

```text
User
```

angezeigt, sofern der LuckPerms Display Name entsprechend gesetzt wurde.

---

## Problem: Scoreboard zeigt `owner` statt `Owner`

Überprüfen, ob:

```text
<rank>
```

verwendet wird.

Danach LuckPerms Display Name prüfen:

```text
/lp group owner setdisplayname Owner
```

---

## Problem: Chatname bleibt weiß

Wenn aktuell:

```yaml
format: "<name><dark_gray> » </dark_gray><white><message></white>"
```

verwendet wird, erhält `<name>` keine Rank-Farbe.

Stattdessen:

```yaml
format: "<rank_name><dark_gray> » </dark_gray><white><message></white>"
```

---

## Problem: Tablist-Name bleibt weiß

Falsch:

```yaml
name-format: "<name>"
```

oder:

```yaml
name-format: "<prefix><name><suffix>"
```

Für Rank-Farbe:

```yaml
name-format: "<rank_name>"
```

---

## Problem: Rank bleibt weiß

Prüfen:

```text
/lp group owner meta info
```

Es sollte ein Meta-Wert existieren:

```text
vapeecore.rank.color = dark_red
```

Beispiel setzen:

```text
/lp group owner meta set vapeecore.rank.color dark_red
```

---

## Problem: `<gold>` funktioniert bei Rank Color nicht

Falsch:

```text
/lp group vip meta set vapeecore.rank.color <gold>
```

Richtig:

```text
/lp group vip meta set vapeecore.rank.color gold
```

---

## Problem: `&6` funktioniert bei Rank Color nicht

Falsch:

```text
/lp group vip meta set vapeecore.rank.color &6
```

Richtig:

```text
/lp group vip meta set vapeecore.rank.color gold
```

---

## Problem: Hex Rank Color funktioniert nicht

Format muss sein:

```text
#RRGGBB
```

Richtig:

```text
#c35cff
```

Falsch:

```text
c35cff
```

Falsch:

```text
<#c35cff>
```

---

## Problem: Prefixfarbe färbt den Namen nicht

Beispiel:

```text
/lp group vip meta setprefix 200 "&6"
```

Das ist nicht dafür gedacht, den darauffolgenden `<name>`-Placeholder zuverlässig zu färben.

VapeeCore arbeitet mit getrennten Adventure Components.

Verwende:

```text
<rank_name>
```

---

## Problem: Resource Config wurde geändert, Server sieht Änderung nicht

Du hast wahrscheinlich geändert:

```text
src/main/resources/chat.yml
```

der Server verwendet aber:

```text
plugins/VapeeCore/chat.yml
```

Passe die Live Config ebenfalls an.

---

# 27. Beispielkonfiguration

## `config.yml`

```yaml
server:
  name: "Vapee Community"

messages:
  prefix: ""

ranks:
  track: "ranks"

online-rewards:
  enabled: true
  interval-minutes: 60
  coins: 250
  message:
    enabled: true
    format: "<reward>"

settings:
  debug: false
```

---

## `chat.yml`

Empfohlenes Design ohne sichtbaren Prefix:

```yaml
enabled: true

format: "<rank_name><dark_gray> » </dark_gray><white><message></white>"

luckperms-meta:
  format: "legacy-ampersand"
```

---

## `presentation.yml`

```yaml
enabled: true

update-interval-ticks: 20

luckperms-meta:
  format: "legacy-ampersand"

scoreboard:
  enabled: true
  lobby-only: true
  title: "<aqua><bold><server></bold></aqua>"

  lines:
    - "<i18n:presentation.sidebar.coins>"
    - "<gold><coins></gold> "
    - ""
    - "<i18n:presentation.sidebar.rank>"
    - "<rank>"
    - ""
    - "<i18n:presentation.sidebar.playtime>"
    - "<blue><playtime></blue>"

tablist:
  enabled: true
  name-format: "<rank_name>"

  header:
    - "<aqua><bold><server></bold></aqua>"
    - "<i18n:presentation.tablist.tagline>"

  footer:
    - "<i18n:presentation.tablist.footer>"
```

---

# 28. Komplettes Rank-Beispiel

## User

Technische Gruppe:

```text
default
```

Display Name:

```text
User
```

Setup:

```text
/lp group default setdisplayname User
/lp group default meta set vapeecore.rank.color gray
/lp group default meta set vapeecore.rank.description "Standardrang für alle Spieler."
```

Ergebnis:

```text
<rank>
→ User
```

in Grau.

```text
<rank_id>
→ default
```

```text
<rank_name>
→ Spielername in Grau
```

---

## VIP

```text
/lp creategroup vip
/lp group vip setdisplayname VIP
/lp group vip meta set vapeecore.rank.color gold
/lp group vip meta set vapeecore.rank.description "Supporter-Rang für Unterstützer des Servers."
```

Ergebnis:

```text
<rank>
→ VIP
```

in Gold.

---

## Builder

```text
/lp creategroup builder
/lp group builder setdisplayname Builder
/lp group builder meta set vapeecore.rank.color aqua
/lp group builder meta set vapeecore.rank.description "Baut und gestaltet die Serverwelt."
```

---

## Moderator

```text
/lp creategroup moderator
/lp group moderator setdisplayname Moderator
/lp group moderator meta set vapeecore.rank.color green
/lp group moderator meta set vapeecore.rank.description "Kümmert sich um Community und Moderation."
```

---

## Admin

```text
/lp creategroup admin
/lp group admin setdisplayname Admin
/lp group admin meta set vapeecore.rank.color red
/lp group admin meta set vapeecore.rank.description "Verwaltet den Server und dessen Systeme."
```

---

## Owner

```text
/lp creategroup owner
/lp group owner setdisplayname Owner
/lp group owner meta set vapeecore.rank.color dark_red
/lp group owner meta set vapeecore.rank.description "Serverleitung."
```

---

## Developer als späterer neuer Rank

```text
/lp creategroup developer
/lp group developer setdisplayname Developer
/lp group developer meta set vapeecore.rank.color #c35cff
/lp group developer meta set vapeecore.rank.description "Entwickelt technische Systeme für den Server."
/lp track ranks append developer
```

VapeeCore benötigt dafür keine Java-Codeänderung.

---

# 29. Kurzreferenz

## Ich möchte normalen Text in VapeeCore färben

Verwende MiniMessage:

```text
<gold>Text</gold>
```

---

## Ich möchte eine eigene RGB-Farbe verwenden

```text
<#c35cff>Text</#c35cff>
```

---

## Ich möchte die Farbe eines Rangs festlegen

```text
/lp group owner meta set vapeecore.rank.color dark_red
```

oder:

```text
/lp group developer meta set vapeecore.rank.color #c35cff
```

Keine `< >`.

---

## Ich möchte den sichtbaren Namen eines Rangs ändern

```text
/lp group default setdisplayname User
```

---

## Ich möchte den Rank im Scoreboard anzeigen

```text
<rank>
```

---

## Ich möchte die technische Group-ID anzeigen

```text
<rank_id>
```

oder aus Kompatibilitätsgründen:

```text
<group>
```

---

## Ich möchte den Spielernamen in seiner Rank-Farbe anzeigen

```text
<rank_name>
```

---

## Ich möchte den normalen Spielernamen anzeigen

```text
<name>
```

---

## Ich möchte die Spielzeit anzeigen

```text
<playtime>
```

Beispiel:

```text
6h 43m
```

---

## Ich möchte Coins anzeigen

```text
<coins>
```

---

## Ich möchte Online-Spieler anzeigen

```text
<online>
```

und:

```text
<max_players>
```

Beispiel:

```yaml
"<online>/<max_players>"
```

---

## Ich verwende LuckPerms Legacy Prefix

```text
&6VIP &8| &f
```

Nur wenn:

```yaml
luckperms-meta:
  format: "legacy-ampersand"
```

---

## Ich möchte `§6` verwenden

Nicht verwenden.

Stattdessen:

### MiniMessage

```text
<gold>
```

### Rank Color

```text
gold
```

### Legacy LuckPerms Meta

```text
&6
```

---

# Format Cheat Sheet

```text
┌─────────────────────────────────────────────────────────────┐
│ VapeeCore YAML / MiniMessage                               │
├─────────────────────────────────────────────────────────────┤
│ Gold:              <gold>Text</gold>                       │
│ Dunkelrot:         <dark_red>Text</dark_red>               │
│ Hex:               <#c35cff>Text</#c35cff>                 │
│ Bold:              <bold>Text</bold>                       │
│ Gradient:          <gradient:#00ffff:#0088ff>Text</gradient>│
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ LuckPerms Rank Color                                       │
├─────────────────────────────────────────────────────────────┤
│ Gold:              gold                                    │
│ Dunkelrot:         dark_red                                │
│ Hex:               #c35cff                                 │
│ Kein <gold>                                                │
│ Kein &6                                                    │
│ Kein §6                                                    │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ LuckPerms Prefix/Suffix mit legacy-ampersand               │
├─────────────────────────────────────────────────────────────┤
│ Gold:              &6                                      │
│ Rot:               &c                                      │
│ Dunkelrot:         &4                                      │
│ Aqua:              &b                                      │
│ Kein §6                                                    │
└─────────────────────────────────────────────────────────────┘
```

---

# Empfohlene VapeeCore-Konvention

Für VapeeCore sollte langfristig folgende Konvention eingehalten werden:

## UI und Templates

MiniMessage:

```text
<gray>
<gold>
<aqua>
<#c35cff>
```

## Rank Styling

LuckPerms Meta:

```text
vapeecore.rank.color
```

Wert:

```text
gold
```

oder:

```text
#c35cff
```

## Rank Name

LuckPerms Group Display Name.

## Spielername mit Rank-Farbe

```text
<rank_name>
```

## Rank im UI

```text
<rank>
```

## Technische IDs

Nur verwenden, wenn technisch notwendig:

```text
<rank_id>
<group>
```

## Legacy Prefix/Suffix

Nur über:

```text
&
```

wenn `legacy-ampersand` aktiviert ist.

## `§`

Nicht verwenden.

---

# Entwicklerhinweis

Wenn neue Placeholder, Rank-Metadaten, Textformate oder Presentation-Systeme zu VapeeCore hinzugefügt werden, muss diese Datei zusammen mit:

```text
docs/DEVELOPER_GUIDE.md
```

aktualisiert werden.

Neue sichtbare Rank-Eigenschaften sollen möglichst weiterhin aus LuckPerms stammen und nicht anhand konkreter Gruppennamen im Java-Code hardcodiert werden.

Die Trennung bleibt:

```text
LuckPerms
    ↓
Rank-Metadaten
    ↓
RankService
    ↓
Adventure Components
    ↓
Chat / Scoreboard / Tablist / Commands
```

Dadurch bleiben neue Ränge dynamisch und benötigen keine Änderungen am VapeeCore-Code.
