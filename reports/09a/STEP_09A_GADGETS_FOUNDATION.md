# Step 09A · Gadgets Foundation

Basis ist `master` auf `457d4fa1894c27e264ab5623c5a09419a534a711`.

## Umsetzung

`GadgetsModule` besitzt das Menü und verwendet den vorhandenen Lobby-Zugang für eingeloggte Spieler mit geladenem Profil im NORMAL-Modus außerhalb von Aktivitäten. Es gibt keine zusätzliche Permission. Die Komposition registriert die Aktion vor dem Versiegeln der Lobby-Item-Registry und startet das Modul nach LobbyExperience. Die vorhandene PDC-Kennung und der zentrale Item-Service übernehmen Klickrouting, Drop-/Swap-Schutz, Duplikatbereinigung und Slotkonflikte. Bei Konflikten kommen die bereits vorhandenen Items zuerst.

Rechtsklick in der Haupthand öffnet das lokalisierte 27-Slot-Menü. Player Riding, Size Changer, Glow, Trails, Movement Extras und Fun Gadgets erscheinen als noch nicht verfügbar. Das Menü sperrt sämtliche Click- und Drag-Transaktionen in seiner Ansicht. Besitzer und gebundenes Inventar bestimmen die Zuordnung beim Schließen. Weltwechsel, Logout, Reload und Disable räumen offene Ansichten auf. Der Reload nutzt die vorhandene Lobby-Transaktion einschließlich Rollback. Das Modul legt keine Aufgaben, Spielerzustände oder Effekte an.

## Aktivierung auf bestehenden Installationen

Neue Serverdateien enthalten das aktive Standarditem `CHEST` mit goldenem Namen und Slot 4 (nullbasiert). Bei einer bestehenden Datei den Eintrag unter dem bereits vorhandenen `items:` ergänzen und `/core reload` ausführen. Die folgenden Felder können angepasst werden. Bei einer Legacy-Datei ohne `items:` sollten beim erstmaligen Anlegen des Abschnitts die gewünschten bisherigen Item-IDs ebenfalls eingetragen werden, da der Abschnitt die gesamte Zusammensetzung bestimmt.

```yaml
items:
  gadgets:
    enabled: true
    slot: 4
    material: CHEST
    name: "<gold>Gadgets"
    lore:
      - "<i18n:lobby.gadgets.lore>"
```

Die bestehende Schema-Version bleibt gleich. Bereits vorhandene Dateien erhalten durch Step 09A keinen automatischen Gadgets-Eintrag, auch bei der bestehenden Legacy-Migration. Ein ausdrücklich deaktivierter Eintrag bleibt deaktiviert.

## Verifikation

Der Gadgets-Harness prüft Hotbar-Registrierung und -Schutz, zwei Sprachen, alle sechs inaktiven Einträge, Drag und Transfers, Slotkonflikte, Opt-in, Reload und Rollback, Logout, Weltwechsel sowie Enable-Fehler und erneutes Aktivieren. Die Step-08F.6-Harnesses prüfen weiter Spielerkopf und Sichtbarkeitswechsel. Die visuelle Abnahme im echten Minecraft-Client steht aus.

Der Gadgets-Harness bestand mit 50 Checks. Im ersten vollständigen Lauf erwarteten zwei ältere Harnesses noch die frühere Item- beziehungsweise Modulzusammensetzung. Die aktualisierten Erwartungen prüfen die sechs registrierten Hotbar-IDs und die vollständige Modulreihenfolge einschließlich Gadgets. Der vollständige Offline-Verifier bestand anschließend mit 247 von 247 Harnesses und 40.970 Checks. Maven und Spotless bestanden. Es gab keine fehlenden oder ungültigen Harnesses.
