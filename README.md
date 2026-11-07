# Pikachu Styles (Fabric, Minecraft 1.21.11)

Client-Mod: erkennt Texturepacks im `resourcepacks`-Ordner und lässt dich pro Kategorie
(Schwerter, Äxte, Spitzhacken, Schaufeln, Rüstungen, Obsidian, End Crystal (3D), Schild, Elytra, Bogen, Armbrust, Totem, Tränke, XP-Flasche, Raketen, ...) wählen, aus welchem Pack die Textur kommt.

## Bauen
1. Ordner in IntelliJ IDEA öffnen (JDK 21 einstellen), Gradle importieren lassen.
2. Zum Testen: Gradle-Task `Tasks > fabric > runClient`.
3. Fertige Mod: Task `build`, die Datei liegt in `build/libs/texturemixer-1.0.0.jar`.
   Zusammen mit Fabric API und Mod Menu in den `mods`-Ordner legen.

## Benutzen
- Menü öffnen: Mod Menu > "Pikachu Styles" > Konfigurieren-Symbol.
- Oben Reiter pro Kategorie, darunter ein Raster mit Vorschaubild pro Pack (Animationen laufen live). Klick = auswählen, "Standard" = nichts überschreiben.
- Unten: Taste zum Öffnen festlegen (standardmäßig nicht belegt). ESC = Abbruch, Entf = Taste löschen.
- Beim Schließen ("Fertig" oder ESC) wird `resourcepacks/texturemixer_output` erzeugt, ganz oben aktiviert und die Ressourcen neu geladen.
- Auswahl und Taste werden in `config/texturemixer.json` gespeichert.

## Neue Kategorie hinzufügen
In `Category.java` in `build()` eine Zeile ergänzen, z. B.
`l.add(simple("gapple", "Goldener Apfel", "item/golden_apple.png", "item/enchanted_golden_apple.png"));`

## Hinweise
- Es werden nur Texturen kopiert (assets/minecraft/textures). Packs mit eigenen 3D-Modellen
  für Items brauchen zusätzlich deren Modelle, das ist hier nicht enthalten.
- Alte Rüstungstexturen (`models/armor/*_layer_1/2.png`) werden automatisch ins neue Format umgeleitet.
- Falls Gradle eine Version nicht findet: aktuelle Werte auf https://fabricmc.net/develop eintragen
  (`gradle.properties`).

## Tränke
Feuerresistenz, Stärke und Schnelligkeit, jeweils Normal und Splash (Umschalter oben rechts).
Die Mod liest dafür `items/potion.json` und `items/splash_potion.json` der Packs (ab 1.21.4) und baut daraus
eine neue Weiche. Alle anderen Tränke bleiben unverändert. Lange und starke Stufen nutzen denselben Look.

## Rüstung: Item / Body
Diamant- und Netherite-Rüstung haben oben rechts einen Umschalter **Item / Body** (Auswahl wird getrennt gespeichert):
- **Item**: die Icons (Helm, Brustplatte, Hose, Stiefel), Vorschau als 2x2-Bild.
- **Body**: die getragene Rüstung. Vorschau ist dein eigener Skin (mit Hut/Jacke-Ebene) mit der Rüstung des Packs,
  als sich langsam drehende 3D-Figur (eine Umdrehung in 10 Sekunden).
  Ohne Welt (z. B. im Titelbildschirm) wird Steve gezeigt. Die Karte "Standard" zeigt die Vanilla-Rüstung.
- Alte Config-Einträge (`diamond_armor`) gelten nach dem Update automatisch für Item und Body.
- Die Drehung steckt in `Model3D.java` (Geometrie) und `BodyView.java` (Zeichnen); Dauer der Umdrehung: `PERIOD_MS`.
- Skin-Zugriff: `BodyView.skin()` (ein einziger Aufruf, `getSkin().body().texturePath()`).


## Elytra: Item / Body
Die Elytra hat denselben Umschalter **Item / Body** (getrennt gespeichert):
- **Item**: das Icon (auch die kaputte Elytra).
- **Body**: die getragenen Flügel. Vorschau ist dein eigener Skin als sich langsam drehende 3D-Figur
  mit den Flügeln des Packs (ohne Welt Steve). Die Karte "Standard" zeigt die Vanilla-Elytra.
- Alte Config-Einträge (`elytra`) gelten nach dem Update automatisch für Item und Body.
