# Step 09B.1 · Gadget Hotbar, Compact Menu & Permissions

Basis ist `master` auf `b4a7333393e7fca0312aaa7325dd992649c9a0ec`.

## Verhalten

Die Gadgets-Kiste bleibt im konfigurierten Slot und öffnet das 27-Slot-Menü per Rechtsklick. Player Riding rüstet den eigenen Sattel auf einem freien, unreservierten Hotbar-Platz aus. Die bevorzugte Reihenfolge ist 2, 3, 5, 6, 7, danach 0, 1, 4, 8. Belegte Slots bleiben erhalten. Ein weiterer Klick entfernt nur das Player-Riding-Item und löst die eigene Mount-Verbindung. Zum Menü zurück führt die Kiste. Der frühere Sneak-Rechtsklick-Shortcut am Sattel entfällt.

Das Menü behält alle sechs bisherigen Positionen. Die Beschreibungen sind kürzer. Slot 22 zeigt den Ausrüstungsstand. Die fünf inaktiven Kategorien beschreiben kurz ihren vorgesehenen Zweck. Die Bedienung für Aufnehmen, Mitreiten, Absteigen und Schleudern steht in der Sattel-Lore.

`vapeecore.gadgets.use` und `vapeecore.gadgets.player-riding` haben beide Default `true`. Die erste Berechtigung schützt den Menüzugang, beide zusammen schützen Ausrüsten und Nutzen. Ein gesperrter Player-Riding-Eintrag zeigt den Status und begründet eine abgelehnte Aktion. Bereits ausgerüstete Spieler verlieren ihre temporäre Sitzung am nächsten Abgleich über Menü, Bewegung, Slotwechsel, Interaktion oder Hotbar-Refresh.

## Betrieb

Bestandskonfigurationen können unverändert weiterverwendet werden. Falls bereits ein `gadgets`-Lobby-Item konfiguriert ist, übernimmt es seine bisherigen Werte. Das folgende optionale Top-Level-Feld kann per `/core reload` übernommen werden. Zulässig sind ganze Werte von 1 bis 3. Fehlende und ungültige Werte verwenden 3.

```yaml
gadgets:
  max-equipped-items: 3
```

Die Ausrüstung verwendet den vorhandenen Lobby-Item-Schutz sowie eine Gadget-ID und eine temporäre Sitzungskennung. Hotbar-Refresh, Logout, Lobby-Zustandswechsel, Activities, Reload und Disable gleichen den Session-Zustand mit dem Inventar ab. Slots werden ausschließlich für Item-Gadgets verwaltet. Es gibt keine persistente Ausrüstung. Die Reitmechanik aus 09B bleibt erhalten.

## Verifikation

Der vollständige Offline-Verifier bestand mit 248 von 248 Harnesses und 41.150 Checks. Maven und Spotless bestanden. Der Equipment-Harness prüfte mit 25 Checks die Limits 1, 2 und 3 sowie reservierte oder belegte Plätze und alte Session-Kennungen. Der Gadgets-Harness prüfte mit 93 Checks unter anderem Menü und Kiste, Schutz des Sattels, Rechteentzug während eines Mounts, Reload/Rollback und die Übergänge zu BUILD und Activity. Der Permission-Descriptor prüfte beide neuen Nodes mit Default `true`. Die bestehenden Harnesses für Spielerkopf, Sichtbarkeitswechsel, Navigator und LobbyGlide bestanden.

Die isolierte Paper-1.21.11-Instanz bestand 13 Checks mit dem Build-Artefakt. Die Probe verwendete kontrollierte CraftPlayer-Sitzungen. Sie bestätigte den separaten Sattel, die gleichzeitig vorhandene Kiste, das Wiederöffnen über die Kiste und das gezielte Entfernen des Sattels. Die bestehende Reitmechanik wurde über Bukkit-Ereignisse, den nativen Angriffspfad, den Ride-Tick und die Sneak-Paketverarbeitung geprüft.

Die echte Minecraft-Client-Abnahme für Menübild, Klickgesten und Reiterdarstellung steht aus. Die Operator-JAR wich schon vor Beginn von der vorherigen Schutzbaseline ab. Sie stimmte bytegenau mit dem vorhandenen lokalen Build überein. Der neue Schutzvergleich verwendet den vor dieser Arbeit gemessenen Hash und lässt den historischen Beleg unverändert.
