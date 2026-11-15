# Step 09C · Size Changer

Basis ist `master` auf `92f5f1137bd13438d0d6a59548f20e9196b7bd80`.

## Bedienung

Der Size-Changer-Eintrag im Gadgets-Menü öffnet ein geschütztes 27-Slot-Untermenü. Ein Linksklick übernimmt Tiny (0,5×), Small (0,75×), Normal (1×), Tall (1,25×) oder Giant (1,5×). Der ausgewählte Eintrag ist grün markiert und beschriftet. Der Zurück-Pfeil öffnet das Gadgets-Menü und behält die Auswahl bei. EN/DE-Texte nutzen das zentrale Nachrichtendesign. Die Standardnachrichten sind präfixfrei. Konfigurierte Betreiberpräfixe und Sprach-Overrides bleiben wirksam.

Das direkte Gadget benötigt `vapeecore.gadgets.use` und `vapeecore.gadgets.size-changer`. Beide sind standardmäßig erlaubt. Menü und Auswahl prüfen die Rechte separat. Size Changer belegt keinen Hotbar-Slot und zählt nicht zum Ausrüstungslimit. Gadgets-Kiste und Riding-Sattel behalten ihre eigenen Slots. Bestehende Installationen brauchen keinen neuen Konfigurationseintrag, wenn die Gadgets-Kiste bereits aktiv ist.

## Laufzeit

Der transiente Paper-Modifier `vapeecore:gadgets_size_changer` multipliziert `Attribute.SCALE` auf dem Serverthread. Die fünf Werte entsprechen den absoluten Größen beim unveränderten Standardattribut. Bei anderen Basiswerten oder Modifiern wirkt die Auswahl als Multiplikator auf die vorhandene Größe innerhalb der Paper-Attributgrenzen. Normal entfernt nur den eigenen Modifier. Fehlt das Attribut, lehnt das Menü eine Größenänderung mit einem kurzen Hinweis ab.

Angenommene Mounts setzen die Größe der beteiligten Spieler zurück. Während eines Mounts, im Flug, beim Gleiten oder im Tod kann kein neuer Größeneffekt gesetzt werden. Teleports, Weltwechsel, Logout, Kick, BUILD, Activities, Tod, Respawn, Lobby-Reload, Rollback und Disable entfernen den eigenen Modifier. Bewegungs-, Menü- und Hotbar-Abgleich erkennen Rechteentzug. Join und Enable entfernen einen eventuell vorhandenen Altmodifier mit dem eigenen Schlüssel. Der transiente Modifier wird nicht als permanentes Attribut gespeichert.

Navigation per Inventarklick erfolgt einmalig im nächsten Tick. Die bisherige Ansicht, ihre Zuordnung zum Spieler und die Berechtigungen werden dabei erneut geprüft. Schließen und Lifecycle-Wechsel verwerfen ausstehende Übergänge. Das Gadget verwendet keinen periodischen Task.

## Verifikation

- `pwsh -NoProfile -File .\scripts\verify.ps1 -Offline` — Maven `clean package` und Spotless PASS. 248/248 Harnesses, 41.284 Checks, 0 Fehler und 0 fehlende oder ungültige Harnesses.
- GadgetsFoundationHarness — 171 Checks. Abgedeckt sind die fünf Auswahlen, Normal und fremde Attributwerte, beide Sprachen, Rechteentzug, ungültige und doppelte Klicks, Hotbar und Ausrüstungslimit, Kick, Respawn, Join, Weltwechsel, BUILD, Activity, Reload, Rollback und Disable. Die bestehenden Riding- und Lobby-Regressionen liefen mit.
- Isoliertes Paper 1.21.11 Build 132 mit LuckPerms — 25 Checks mit bytegleicher Kopie des finalen Build-Artefakts. Geprüft wurden echtes CraftInventory, alle fünf SCALE-Werte und die serverseitige Breite der Hitbox nach dem Paper-Spielertick, die Abwesenheit des transienten Modifiers in den gepackten persistenten Attributdaten, fremde Skalierung sowie tatsächliche Paper-Mounts und Teleports. Die Probe verwendete kontrollierte CraftPlayer-Sitzungen auf Loopback.
- 31 vorhandene Schutzdateien wurden per SHA-256 verglichen. Geschützter Worktree und bestehende Git-Referenzen wurden ebenfalls abgeglichen. Die Tests nutzten eine getrennte Welt und getrennte Testprofile.

Die echte Minecraft-Client-Abnahme bleibt offen. Zu prüfen sind Menübild und Auswahlfeedback, sichtbare Größe aus Eigen- und Fremdperspektive sowie Hitbox und Bewegung in engen Bereichen und nach Mount oder Teleport.
