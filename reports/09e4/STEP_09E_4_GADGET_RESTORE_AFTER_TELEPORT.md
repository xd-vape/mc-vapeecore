# Step 09E.4 · Gadget-Wiederherstellung nach Teleport

Basis ist `master` auf `57a4070e2cc56670c741330c4d9e41813e8699e0`.

## Teleport und Wiederherstellung

Die bestehenden Teleport-Listener räumen Größe, Glow, Trails und unzulässige Riding-Verbindungen auf. `GadgetPreferenceLifecycle` beobachtet den unabgebrochenen `PlayerTeleportEvent` auf `MONITOR`. Paper ruft das Ereignis vor der Positionsänderung auf. Für denselben Lobby-Kontext prüft der One-Shot im nächsten Servertick den endgültigen Abbruchzustand, die tatsächliche Zielwelt und die exakten XYZ-Koordinaten. Wenn keine Positionsänderung nachweisbar ist oder der Spieler das Ziel bereits verlassen hat, bleibt dieser Teleport ohne Restore. Weltwechsel laufen über die bestehenden Lobby- und Weltwechsel-Listener. [Paper 1.21.11 · Teleport-Event](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerTeleportEvent.html) · [Paper 1.21.11 · Reihenfolge im Server](https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-server/patches/sources/net/minecraft/server/level/ServerPlayer.java.patch)

Pro Spieler bleibt eine ausstehende Aufgabe im bestehenden Lifecycle. Eine neuere Teleport-Anfrage ersetzt sie. Falls diese Anfrage scheitert, kann die unmittelbar vorher bestätigte Ankunft am unveränderten Standort weiterhin gelten. Beim Ausführen müssen das Bukkit-Spielerobjekt, die geladene Profilinstanz und der Lobby-Zustand noch passen. Logout, Reload und Disable machen frühere Aufgaben unwirksam. Der Restore liest aktuelle Wünsche aus dem Profil, damit eine inzwischen gespeicherte Auswahl maßgeblich bleibt. Offene, gültige Gadget-Menüs aktualisieren ihre Anzeige.

## Effekte und Berechtigungen

Size Changer prüft vorhandene Berechtigungen, skalierbare Attribute, geladene Chunks und die Stand-Hitbox am aktuellen Ziel. Beim Teleport gilt die Kollisionsprüfung auch für Standardgrößen. Eine blockierte Größe bleibt als Wunsch gespeichert und wird im Menü als inaktiv gekennzeichnet. Fremde SCALE-Modifier bleiben erhalten.

Glow verwendet weiterhin dieselben Ownership- und Erneuerungsregeln. Ein passender kurzlebiger Rest erlaubt höchstens einen weiteren Versuch, solange derselbe Spieler am bestätigten Ziel in derselben berechtigten Lobby-Sitzung steht. Ein weiterer Teleport macht einen alten Retry unwirksam. Trails starten am Ziel mit einer neuen Laufzeitsitzung und dem bestehenden Vier-Tick-Limit sowie den Reichweiten-, Sichtbarkeits- und Ignore-Filtern.

Die Wiederherstellung schreibt keine Profilwerte, sendet keine Chatmeldungen und stattet niemanden automatisch mit einem Riding-Item aus. Eine durch den Teleport beendete Riding-Verbindung bleibt beendet.

## Verifikation

- Vollständiger Offline-Verifier auf dem finalen Stand mit `pwsh -NoProfile -File .\scripts\verify.ps1 -Offline`. Maven `clean package` und Spotless bestanden. 248/248 Harnesses, 41.972 Checks, 0 Fehler und 0 fehlende oder ungültige Harnesses.

- Gezielter `GadgetsFoundationHarness` mit 396 bestandenen Checks. Die neuen Szenarien prüfen Ankunft und Abbruch, schnelle Teleports, fremde Welten, BUILD und Activities, Sitzungs- und Profilwechsel, aktuelle manuelle Auswahl, Permissions, Kollisionen, Glow-Ownership, Trails, Riding und Cleanup von Aufgaben.
- Isoliertes Paper 1.21.11 Build 132 mit LuckPerms 5.5.84. 19 bestandene Checks mit kontrollierter CraftPlayer-Sitzung, echten Paper-Teleports und Serverticks. Geprüft wurden Abbruch, schnelle Teleports, 3×, Glow, ein Cloud-Partikelpaket nach Bewegung, 5× an freien und blockierten Zielen sowie unveränderte Testprofildateien. Der Testprozess endete mit Exitcode 0.
- SHA-256 der lokalen Build-JAR und ihrer bytegleichen nativen Testkopie `e72f12627d82c8642a0a9074aade36ae9c45184a663f7273ea513169957aabdc`.
- Die 31 Schutzdateien wurden mit dem Ausgangsstand dieses Schritts per SHA-256 verglichen. Die Operator-JAR, `daily-quests.yml`, `lobby.yml` und `server.properties` wichen bereits vor Arbeitsbeginn vom früheren Beleg ab. Der Operator-Server und der geschützte Worktree blieben unangetastet.

## Manuelle Client-Abnahme

Sechs echte Persistenz-Beobachtungen hat der Nutzer bereits bestätigt. Die vier neuen Teleport-Fälle stehen auf `PENDING`. Player Riding bleibt nach verlorenem Sneak beim Fensterwechsel des zweiten Clients offen. [Manuelle Gadget-Client-Abnahme](../09e2/GADGET_CLIENT_QA.md)
