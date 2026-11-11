# Step 08F.4 · Questabschluss-Lautstärke und Entfernung von /ranks

Basis ist `master` auf `64ab4e781a00f8bf02c0bd2874871fd48e7efec5`.

## Questabschluss

`feedback.sound-volume` akzeptiert endliche YAML-Zahlen von `0.0` bis `1.0` einschließlich. Ältere Dateien ohne den Schlüssel verwenden `0.6`. Der Standard-Sound bleibt `minecraft:ui.toast.challenge_complete`, der Pitch bleibt `1.0`. Bei ungültiger Lautstärke schlägt die Reload-Vorbereitung fehl. Die aktive Konfiguration bleibt dabei erhalten. Die Lautstärke gilt für den bestehenden Sound pro erfolgreich abgeschlossenem Batch, einschließlich erfolgreicher Pending-Reward-Retries. Menüöffnung, Retry und Coin-Auszahlung nutzen ihre bisherigen Abläufe.

## Befehl und bestehende Tagesquests

Mit dem öffentlichen Befehl `/ranks` entfallen seine Registrierung, der namespaced Befehl `vapeecore:ranks`, das Permission-Node `vapeecore.ranks.view` und die Hilfe- und Sprachtexte. `/rank`, sein Permission-Node, `RankService`, LuckPerms-Integration, Staff-Hierarchie und Rangdarstellung bleiben erhalten. Die Quest-Lobby-Integration bindet weiterhin den erfolgreichen Selbstabruf von `/rank`.

Die Tagesauswahl überspringt `lobby:ranks_check` anhand des Progress-Keys. Das gilt auch bei `enabled: true` und bei anderen IDs als `check_ranks`. Die Definition bleibt im Registry-Katalog. Bereits zugewiesene Quests können daher durch einen erfolgreichen Selbstabruf mit `/rank` ohne Argumente abgeschlossen werden. Ein Abruf kann beide zugewiesenen Rangquests fortschreiben. Die vorhandene Questlogik zahlt jede Belohnung höchstens einmal.

Ein Reload würfelt aktuelle Zuweisungen nicht neu aus. Force im selben Cycle erhält vorhandene Einträge mit dem alten Key und rechnet sie auf Slot-, Kategorie- und Gruppenlimits an. Pending Rewards blockieren Force und die normale Rotation, solange ihre Auszahlung fehlschlägt. Nach erfolgreichem Retry greift die normale Rotation ohne den alten Key. Wenn keine Ersatzquest auswählbar ist, bleiben vorhandene Legacy-Einträge auch bei einem Cycle-Wechsel erhalten.

## Betreiber

Die neue Auswahl funktioniert mit der vorhandenen Datei. Für noch zugewiesene Spieler unter `quests.check_ranks` die deutschen und englischen Beschreibungen auf `/rank` ohne Argumente aktualisieren. `enabled: false` kennzeichnet die Quest im Katalog als auslaufend und unterdrückt den Hinweis beim Laden. Definition, ID, Key, Ziel und Belohnung bis zur Erledigung aller Altzuweisungen und Pending Rewards beibehalten. Die vorhandene Quest `check_rank` mit `lobby:rank_check` kann unverändert weiterlaufen.

Die Lautstärke kann bei Bedarf mit `feedback.sound-volume` gesetzt werden. Ohne Eintrag gilt `0.6`. Die Operator-Konfiguration wurde für diesen Step lediglich gelesen.

## Verifikation

`pwsh -NoProfile -File .\scripts\verify.ps1` bestand mit Java 21 alle 247 Harnesses und 40.969 Checks. Es gab null Fehler und null fehlende oder ungültige Harnesses. Maven `clean package` und Spotless bestanden. Die JAR enthält `RankCommand`. Der entfernte `RanksCommand` fehlt im Artefakt. Der bisherige Test des entfernten Befehls wurde durch einen Rank-Modul-Lifecycle-Test mit ausschließlich registriertem `rank` ersetzt.

Der native Test auf einer isolierten Paper-1.21.11-Instanz mit LuckPerms bestand 18 Checks. Die native Command-Map und der Brigadier-Baum enthielten `rank` und keine der beiden Varianten von `ranks`. Ein erfolgreicher `/rank`-Abruf mit einem kontrollierten Spielersitzungsobjekt schloss eine bestehende Altquest ab und zahlte ihre Coins einmal. Das nächste Abschluss-Batch nach `/core reload` lieferte zwei reguläre Rewards und ein Soundpaket. Die geprüften Pakete verwendeten `0.6` vor und `0.25` nach Reload, jeweils mit Pitch `1.0`. Die Probe hat die vom Server versendeten Werte geprüft.

Die Schutzprüfung verglich 31 Dateien per SHA-256, einschließlich der Operator-Konfiguration, der Operator-JAR, beider Run-Konfigurationen und der geschützten Receipts. Der geschützte Worktree und seine ungetrackte Berichtsdatei wurden separat abgeglichen. Die native Testwelt und ihre Spielerdateien lagen außerhalb des Operator-Servers.
