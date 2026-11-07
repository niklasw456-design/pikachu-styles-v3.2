package de.texturemixer;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Reiter im Menü. Hat er zwei Varianten (z. B. Platziert / In der Hand oder Normal / Splash),
 * erscheint oben rechts der Umschalter.
 *
 * Rule:       kopiert alle Dateien, deren Pfad (relativ zu assets/minecraft/textures/) mit "src" beginnt,
 *             nach "dst" + Rest des Pfads. Ein Prefix wie "item/mace.png" greift auch für die .mcmeta.
 *             Regeln weiter hinten überschreiben frühere (neues Format gewinnt vor altem).
 * HandSpec:   für Blöcke/Netze: In der Hand wird ein eigenes Item-Modell erzeugt, das die gewählte Textur nutzt.
 * PotionSpec: Tränke: der Look steht in items/potion.json bzw. items/splash_potion.json des Packs.
 * render:     "crystal" = die Vorschau wird als drehendes 3D-Modell berechnet,
 *             "armor_items:<material>" = 2x2-Bild aus den vier Rüstungs-Items,
 *             "body:<material>" = Spieler-Skin mit der getragenen Rüstung.
 */
public record Category(String id, String name, String icon, String potionIcon, List<Variant> variants) {

    public record Rule(String src, String dst) {}

    public record HandSpec(String itemId, boolean cube, List<String> sources) {}

    public record PotionSpec(String itemDef, List<String> ids, int color) {}

    public record Variant(String key, String label, List<Rule> rules, List<String> previews,
                          HandSpec hand, String render, PotionSpec potion) {
        public boolean matches(String rel) {
            for (Rule r : rules) {
                if (rel.startsWith(r.src())) return true;
            }
            return false;
        }

        /** Rüstung, Reiter "Item": Vorschau = Helm, Brustplatte, Hose, Stiefel als 2x2-Bild. */
        public boolean isArmorItems() {
            return render != null && render.startsWith("armor_items:");
        }

        /** Rüstung, Reiter "Body": Vorschau = Spieler-Skin mit angezogener Rüstung. */
        public boolean isBody() {
            return render != null && render.startsWith("body:");
        }

        /** Elytra, Reiter "Body": Vorschau = Spieler-Skin von hinten mit den Flügeln des Packs. */
        public boolean isElytraBody() {
            return isBody() && "elytra".equals(material());
        }

        /** Material hinter dem Doppelpunkt im render-Feld (z. B. "diamond"), sonst null. */
        public String material() {
            int i = render == null ? -1 : render.indexOf(':');
            return i < 0 ? null : render.substring(i + 1);
        }
    }

    public static final List<Category> ALL = build();

    public boolean dual() {
        return variants.size() > 1;
    }

    /** Eindeutiger Schlüssel für Auswahl und Config. */
    public String fullId(Variant v) {
        return dual() ? id + "#" + v.key() : id;
    }

    // ---------- Aufbau ----------

    private static Rule same(String path) {
        return new Rule(path, path);
    }

    private static Variant plain(List<Rule> rules, String... previews) {
        return new Variant("main", "", List.copyOf(rules), List.of(previews), null, null, null);
    }

    private static Category single(String id, String name, String icon, Variant v) {
        return new Category(id, name, icon, null, List.of(v));
    }

    private static Category tool(String id, String name, String item) {
        return single(id, name, item, plain(List.of(same("item/" + item + ".png")), "item/" + item + ".png"));
    }

    /**
     * Rüstung mit Umschalter: "Item" = die Item-Icons (Helm, Brustplatte, Hose, Stiefel),
     * "Body" = die getragene Rüstung (Entity-Texturen), Vorschau am Spieler-Skin.
     */
    private static Category armor(String id, String name, String material, String equip) {
        List<Rule> items = new ArrayList<>();
        for (String part : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
            items.add(same("item/" + material + "_" + part));
        }
        Variant item = new Variant("item", "Item", List.copyOf(items), List.of(), null,
                "armor_items:" + material, null);

        List<Rule> body = new ArrayList<>();
        // Altes Format (vor 1.21.2) wird automatisch ins neue Format umgeleitet
        body.add(new Rule("models/armor/" + equip + "_layer_1", "entity/equipment/humanoid/" + equip));
        body.add(new Rule("models/armor/" + equip + "_layer_2", "entity/equipment/humanoid_leggings/" + equip));
        body.add(same("entity/equipment/humanoid/" + equip));
        body.add(same("entity/equipment/humanoid_leggings/" + equip));
        Variant bodyV = new Variant("body", "Body", List.copyOf(body), List.of(), null,
                "body:" + material, null);

        return new Category(id, name, material + "_chestplate", null, List.of(item, bodyV));
    }

    /**
     * Elytra mit Umschalter: "Item" = das Icon (auch die kaputte Elytra),
     * "Body" = die getragenen Flügel (Entity-Textur), Vorschau am Spieler-Skin von hinten.
     */
    private static Category elytra() {
        Variant item = new Variant("item", "Item",
                List.of(same("item/elytra.png"), same("item/broken_elytra.png")),
                List.of("item/elytra.png"), null, null, null);
        Variant body = new Variant("body", "Body", List.of(
                // Altes Format (vor 1.21.4) wird automatisch ins neue Format umgeleitet
                new Rule("entity/elytra", "entity/equipment/wings/elytra"),
                same("entity/equipment/wings/elytra")),
                List.of(), null, "body:elytra", null);
        return new Category("elytra", "Elytra", "elytra", null, List.of(item, body));
    }

    /** Block bzw. Netz: platziert = Blocktextur, in der Hand = eigenes Item-Modell mit frei wählbarer Textur. */
    private static Category blockLike(String id, String name, String item, boolean cube) {
        String block = "block/" + item + ".png";
        Variant placed = new Variant("placed", "Platziert", List.of(same(block)), List.of(block), null, null, null);
        List<String> sources = List.of("item/" + item + ".png", block);
        Variant hand = new Variant("hand", "In der Hand", List.of(), sources,
                new HandSpec(item, cube, sources), null, null);
        return new Category(id, name, item, null, List.of(placed, hand));
    }

    /** Trank: Normal / Splash, jeweils mit den Stufen "lang" und "stark" (gleicher Look). */
    private static Category potion(String id, String name, String potion, int color) {
        List<String> ids = List.of("minecraft:" + potion, "minecraft:long_" + potion, "minecraft:strong_" + potion);
        Variant normal = new Variant("normal", "Normal", List.of(), List.of(), null, null,
                new PotionSpec("potion", ids, color));
        Variant splash = new Variant("splash", "Splash", List.of(), List.of(), null, null,
                new PotionSpec("splash_potion", ids, color));
        return new Category(id, name, "potion", potion, List.of(normal, splash));
    }

    private static List<Category> build() {
        List<Category> l = new ArrayList<>();

        l.add(tool("netherite_sword", "Netherite-Schwert", "netherite_sword"));
        l.add(tool("netherite_axe", "Netherite-Axt", "netherite_axe"));
        l.add(tool("netherite_pickaxe", "Netherite-Spitzhacke", "netherite_pickaxe"));
        l.add(tool("netherite_shovel", "Netherite-Schaufel", "netherite_shovel"));
        l.add(armor("netherite_armor", "Netherite-Rüstung", "netherite", "netherite"));

        l.add(tool("diamond_sword", "Diamant-Schwert", "diamond_sword"));
        l.add(tool("diamond_axe", "Diamant-Axt", "diamond_axe"));
        l.add(tool("diamond_pickaxe", "Diamant-Spitzhacke", "diamond_pickaxe"));
        l.add(tool("diamond_shovel", "Diamant-Schaufel", "diamond_shovel"));
        l.add(armor("diamond_armor", "Diamant-Rüstung", "diamond", "diamond"));

        l.add(blockLike("obsidian", "Obsidian", "obsidian", true));
        l.add(blockLike("crying_obsidian", "Crying Obsidian", "crying_obsidian", true));

        // End Crystal: platziert = Entity-Textur (als drehendes 3D-Modell), in der Hand = Item-Icon
        Variant crystalPlaced = new Variant("placed", "Platziert",
                List.of(same("entity/end_crystal/")), List.of("entity/end_crystal/end_crystal.png"),
                null, "crystal", null);
        Variant crystalHand = new Variant("hand", "In der Hand",
                List.of(same("item/end_crystal.png")), List.of("item/end_crystal.png"), null, null, null);
        l.add(new Category("end_crystal", "End Crystal", "end_crystal", null, List.of(crystalPlaced, crystalHand)));

        l.add(single("shield", "Schild", "shield", plain(
                List.of(same("entity/shield"), same("item/shield")),
                "entity/shield_base_nopattern.png", "entity/shield/shield_base_nopattern.png",
                "entity/shield_base.png", "entity/shield/shield_base.png")));

        l.add(blockLike("cobweb", "Spinnennetz", "cobweb", false));

        l.add(tool("mace", "Streitkolben", "mace"));

        l.add(single("trident", "Dreizack", "trident", plain(
                List.of(same("item/trident"), same("entity/trident")), "item/trident.png")));

        l.add(elytra());

        l.add(single("bow", "Bogen", "bow", plain(
                List.of(same("item/bow.png"), same("item/bow_pulling_")), "item/bow.png")));
        l.add(single("crossbow", "Armbrust", "crossbow", plain(
                List.of(same("item/crossbow")), "item/crossbow_standby.png")));

        l.add(tool("totem", "Totem", "totem_of_undying"));
        l.add(tool("ender_pearl", "Enderperle", "ender_pearl"));

        l.add(single("golden_apple", "Goldener Apfel", "golden_apple", plain(
                List.of(same("item/golden_apple.png"), same("item/enchanted_golden_apple.png")),
                "item/golden_apple.png")));

        l.add(single("wind_charge", "Windkugel", "wind_charge", plain(
                List.of(same("item/wind_charge.png"), same("entity/projectiles/wind_charge")),
                "item/wind_charge.png")));

        l.add(tool("xp_bottle", "XP-Flasche", "experience_bottle"));
        l.add(tool("firework_rocket", "Rakete", "firework_rocket"));

        l.add(potion("potion_fire_resistance", "Feuerresistenz-Trank", "fire_resistance", 0xE49A3A));
        l.add(potion("potion_strength", "Stärke-Trank", "strength", 0x932423));
        l.add(potion("potion_swiftness", "Schnelligkeits-Trank", "swiftness", 0x33EBFF));

        return List.copyOf(l);
    }
}
