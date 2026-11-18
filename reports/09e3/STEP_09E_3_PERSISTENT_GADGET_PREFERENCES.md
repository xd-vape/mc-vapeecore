# Step 09E.3 · Persistente Gadget-Einstellungen

Basis ist `master` auf `39fa0a2c2955427235b527ca20a315ca269dcf77`.

## Profil und Speichergrenze

`PlayerSettings` enthält die unveränderlichen Wunschwerte für Größe, Glow und Trails. `PlayerSettingsYamlCodec` liest und schreibt die drei Blätter unter `settings.gadgets`. Bestehende Profile ohne die Blätter verwenden `NORMAL`, `false` und `NONE`. Ungültige Werte erhalten einzeln ihre Standards und erzeugen eine Diagnose mit Feld und Profil. Die bestehende vollständige Profiltransaktion und ihr Schema bleiben maßgeblich. Unbekannte Nachbarfelder bleiben erhalten.

Das geschützte Menü speichert eine erfolgreiche manuelle Änderung über `PlayerSettingsService`. Identische Wünsche benötigen keinen neuen Save. Bei einem Schreibfehler bleibt der vorherige Profilwert gültig. Das Menü meldet den Speicherfehler auf EN/DE ohne automatisches Feature-Präfix und stellt den vorherigen Effekt wieder her, soweit die aktuellen Sicherheitsbedingungen dies erlauben. Mounts und Riding-Ausrüstung bleiben temporär.

## Aktivierung und Cleanup

Ein gebündelter One-Shot prüft nach Profil- und Lobby-Synchronisierung das aktuelle Bukkit-Spielerobjekt, das geladene Profil, Lobby NORMAL, Aktivitätszustand und die Gadget-Permissions. Er läuft zum Join, nach Respawn und Weltwechsel, nach Rückkehr aus BUILD oder einer Activity und nach Lobby-Reload beziehungsweise Rollback. Ein erfolgreicher Teleport innerhalb derselben Lobby behält den bisherigen Runtime-Cleanup bei. Der gespeicherte Wunsch bleibt für die nächste manuelle Auswahl oder den nächsten Restore-Anlass verfügbar.

Die bestehenden Gadget-Engines übernehmen die Anwendung. Zusatzgrößen prüfen Hitbox, Platz, geladene Chunks und kompatiblen Zustand erneut. Ein blockierter Wunsch bleibt gespeichert. Das Größenmenü markiert die aktive Größe und kennzeichnet eine gespeicherte inaktive Option separat. Für gespeichertes, inaktives Glow versucht ein Linksklick die Aktivierung erneut. Ein Rechtsklick schaltet den gespeicherten Wunsch aus.

Glow übernimmt keinen vorhandenen Potion-Effekt oder fremdes Flag. Bei einem passenden endlichen Rest ist höchstens ein erneuter Versuch nach der beobachteten Laufzeit möglich. Auch dieser versucht die Aktivierung nur bei freiem Glow-Zustand und gültiger Sitzung. Trails beginnen mit einer neuen Emissionssitzung und verwenden weiterhin die vorhandene Sichtbarkeits-, Ignore-, Abstands- und Tickprüfung. Bewegung, Glow-Erneuerung, automatischer Restore und Cleanup speichern keine Wunschwerte.

## Verifikation

- `pwsh -NoProfile -File .\scripts\verify.ps1 -Offline` auf dem endgültigen Stand. Maven `clean package` und Spotless bestanden. 248/248 Harnesses, 41.930 Checks, 0 Fehler und 0 fehlende oder ungültige Harnesses.
- `GadgetsFoundationHarness` mit 355 Checks, `PlayerSettingsYamlCodecHarness` mit 971 und `SettingsPersistenceContractHarness` mit 77. Der Profilvertrag prüft die exakte Feldliste und die Speicherung unabhängig von anderen Einstellungen. Der Gadget-Harness prüft unter anderem Rejoin von 2×, 3× und 5×, gespeicherte blockierte Größen, Rechteentzug, fremdes Glow, den einzelnen Rest-Glow-Versuch, Ignore, BUILD, Rollback, Duplikate und Schreibfehler.
- Der erste Lauf wurde durch verweigerte atomare Dateivorgänge im Sandbox-Tempverzeichnis beeinträchtigt. Im anschließenden Lauf musste der bestehende Vertragstest um die drei vorgesehenen YAML-Pfade erweitert werden. Der abschließende vollständige Lauf bestand mit isolierten Testprofilen.

- Isoliertes Paper 1.21.11 Build 132 mit LuckPerms 5.5.84. 21 bestandene Checks, davon acht nach vollständigem Neustart. Kontrollierte CraftPlayer-Sitzungen durchliefen die echten Bukkit-Join-/Quit-Listener und schrieben ein echtes Testprofil. Der Neustart bestätigte 3× SCALE, Glow, die Cloud-Auswahl im Menü und ein von Paper geliefertes Partikelpaket. Riding-Ausrüstung wurde nicht wiederhergestellt. Beide Serverprozesse beendeten sich mit Exitcode 0.
- Das native Testartefakt und die lokale Build-JAR waren bytegleich. SHA-256 `c268510f0202e28ad0b772673d21295cdd2fb17ea13f37081eb9db6e3ecd67ed`.
- Die 31 Schutzdateien wurden vor und nach der Arbeit anhand ihrer SHA-256-Werte verglichen. Der geschützte Worktree einschließlich seiner lokalen Datei und die bestehenden Git-Referenzen wurden verglichen. Tests liefen mit isolierten Profilen und Welten.

## Offene Client-Abnahme

Die echte Minecraft-Client-Abnahme bleibt offen. Die Prüfschritte aus [Step 09E.2](../09e2/GADGET_CLIENT_QA.md) bleiben auf `PENDING`. Für die Persistenz im Client zusätzlich Logout/Rejoin und Neustart mit Größe, Glow und Trails prüfen. Dazu gehören die Markierung einer blockierten gespeicherten Größe, das Auslaufen von Rest-Glow und eine zweite Spielerperspektive mit Hidden oder Ignore. Die historischen Berichte und ihre QA-Status wurden erhalten.
