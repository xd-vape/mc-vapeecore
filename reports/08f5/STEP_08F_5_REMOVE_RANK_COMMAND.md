# Step 08F.5 · Öffentlichen Rank-Befehl entfernen

Basis ist `master` auf `6cb970c325318265ba4811008ab8780868d976f0`.

## Befehle und Rangsystem

Der Plugin-Descriptor und die Hilfe enthalten weder `/rank` noch `/ranks`. Die alte Permission `vapeecore.rank.view`, der Executor und seine Sprachtexte entfallen. Paper registriert dadurch auch keinen der beiden Namen im VapeeCore-Namespace. Die Command-Visibility-Logik bleibt unverändert und greift weiterhin nur bei tatsächlich registrierten Befehlen.

`RankModule` besitzt weiterhin `RankService` und `StaffHierarchyService`. LuckPerms liefert weiterhin Rangdaten, Farben, Metadaten und den Track für die TAB-Sortierung. Chat, Nametags, TAB und Staff-Autorisierung behalten ihre vorhandenen Rangdienste.

## Tagesquests

Der Selector filtert `lobby:rank_check` und `lobby:ranks_check` vor der Auswahl nach Progress-Key. Das gilt unabhängig von Quest-ID und `enabled`-Einstellung. Die Definitionen bleiben für gespeicherte Zuweisungen und ausstehende Belohnungen im Registry-Katalog.

Ein erfolgreiches Öffnen des gebundenen Daily-Quest-Menüs über `/quests` oder den Quest-NPC meldet je einen Fortschritt für die passenden alten Keys. `QuestLobbyProducer` prüft die aktive Spielersitzung, den geladenen Spieler und die passende Zuweisung. `QuestService` wendet seine bisherigen Fortschritts- und Belohnungsregeln an. Jeder einzelne Abschluss kann nur einmal ausgezahlt werden. Beim Login, bei automatischen Menüöffnungen, beim Blättern, bei Updates und bei abgebrochenen Öffnungen entsteht kein neuer Fortschritt aus dieser Übergangsregel. Andere reguläre Menü-Einstiege behalten ihren bisherigen `lobby:quests_open`-Fortschritt.

Ein Reload ändert die gespeicherte Auswahl nicht. Force im selben Zyklus behält gespeicherte Altquests und berücksichtigt sie in den Auswahlgrenzen. Die normale Rotation verwendet weiter die regulären Ersatzquests und schützt ausstehende Rewards. Die bestehenden Retries für bereits ausstehende Auszahlungen bleiben aktiv. Wenn keine Ersatzquest auswählbar ist, bleiben die Altquests und der bisherige Zyklus erhalten. In diesem Fall bleibt das Menü für die betroffenen Spieler erreichbar.

## Betreiber

Die vorhandene Konfiguration funktioniert ohne Änderung. Solange Altquests zugewiesen sind, können die englischen und deutschen Beschreibungen von `check_rank` und `check_ranks` auf `/quests` oder den Quest-NPC umgestellt werden. `enabled: false` kann die Einträge als auslaufend kennzeichnen.

IDs, Keys, Ziele und Belohnungen beibehalten, solange eine der Definitionen noch in einer aktiven Tagesauswahl eines Spielerprofils steht oder ein Reward aussteht. Das schließt Offline-Profile und bereits abgeschlossene Einträge im alten Zyklus ein. Die Definitionen können nach erfolgreicher Auszahlung ausstehender Rewards und der Rotation sämtlicher verbleibender Altzuweisungen entfernt werden. Falls kein Ersatz auswählbar ist, rotiert die alte Auswahl erst wieder, sobald der Katalog eine auswählbare Quest enthält.

## Verifikation

Der abschließende Lauf von `pwsh -NoProfile -File .\scripts\verify.ps1` bestand alle 246 Harnesses mit 40.868 Checks, ohne Fehler und ohne fehlende oder ungültige Harnesses. Maven `clean package` und Spotless bestanden. Der frühere Harness für den entfernten `/rank`-Befehl entfiel. Der Rank-Lifecycle-Harness prüft Start und Stop ohne Command-Zugriff. RankService-, Staff-, Chat-, Nametag- und TAB-Regressionstests bestanden.

Der erste Verifier-Lauf fand im neuen Reward-Test eine fehlerhafte Kontostandserwartung. Der Test verglich den Gesamtstand mit der Summe der Menübelohnungen, obwohl der Spieler bereits Coins für Warp-Quests erhalten hatte. Die korrigierte Prüfung verwendet die Differenz ab dem ersten Menüaufruf. Die vier gezielten Harnesses bestanden danach separat.

24 native Checks auf einer isolierten Paper-1.21.11-Instanz mit LuckPerms bestanden. Die Befehlsnamen fehlten mit und ohne Namespace in Command-Map und Brigadier. `RankService` löste weiterhin einen LuckPerms-Benutzer auf. Die Probe bestätigte die Filterung beider alten Keys bei neuen Tageszuweisungen, den Altquest-Fortschritt über den echten `/quests`-Command und den NPC-Interaktionshandler sowie den unveränderten automatischen Menüpfad. Wiederholte Öffnungen und Retries zahlten keine Coins doppelt. Ein Vergleich der JAR-Einträge bestätigte inhaltsgleiche Pakete zwischen der nativen Probe und dem finalen Maven-Build.

Die Schutzprüfung erfasste 31 Dateien per SHA-256 und den separaten Worktree. Die Operator-Konfiguration, Run-Konfigurationen, Receipts und Operator-JAR blieben unverändert. Der native Test verwendete ausschließlich seine eigene Welt und eigene Spielerprofile.
