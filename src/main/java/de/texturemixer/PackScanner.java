package de.texturemixer;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** Findet Texturepacks im resourcepacks-Ordner und baut daraus das Ausgabe-Pack. */
public final class PackScanner {
    public static final String OUTPUT_NAME = "texturemixer_output";

    private static final String MCMETA = """
            {
              "pack": {
                "description": "Pikachu Styles (automatisch erzeugt)",
                "pack_format": 75,
                "supported_formats": { "min_inclusive": 34, "max_inclusive": 75 }
              }
            }
            """;

    /** categories enthält die Variant-IDs (siehe Category.fullId), die das Pack unterstützt. */
    public record PackInfo(String name, Path path, Set<String> categories) {}

    /**
     * Rohdaten für das Vorschaubild: entweder PNG (+ optional .mcmeta für Animation)
     * oder fertige ARGB-Pixel (argb != null), z. B. bei Tränken.
     */
    public record PreviewData(byte[] png, byte[] mcmeta, int[] argb, int w, int h) {}

    /** Rohdaten der getragenen Rüstung: outer = Helm/Brustplatte/Stiefel, legs = Hose (je PNG oder null). */
    public record BodyData(byte[] outer, byte[] legs) {}

    private interface RootAction<T> {
        T run(Path root) throws IOException;
    }

    private PackScanner() {}

    public static Path packsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
    }

    public static List<PackInfo> scan() {
        List<PackInfo> result = new ArrayList<>();
        Path dir = packsDir();
        if (!Files.isDirectory(dir)) return result;

        List<Path> entries;
        try (Stream<Path> s = Files.list(dir)) {
            entries = s.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase())).toList();
        } catch (IOException e) {
            TextureMixerClient.LOGGER.warn("resourcepacks-Ordner nicht lesbar", e);
            return result;
        }

        for (Path p : entries) {
            String name = p.getFileName().toString();
            if (name.equals(OUTPUT_NAME)) continue;
            boolean zip = Files.isRegularFile(p) && name.toLowerCase().endsWith(".zip");
            boolean folder = Files.isDirectory(p);
            if (!zip && !folder) continue;
            try {
                Set<String> cats = withRoot(p, PackScanner::detect);
                result.add(new PackInfo(name, p, cats));
            } catch (Exception e) {
                TextureMixerClient.LOGGER.warn("Pack {} konnte nicht gelesen werden", name, e);
            }
        }
        return result;
    }

    /** Baut das Ausgabe-Pack. Gibt die Anzahl erzeugter Einträge zurück (0 = Pack wurde entfernt). */
    public static int buildOutput(Map<String, String> selections, List<PackInfo> packs) throws IOException {
        Path out = packsDir().resolve(OUTPUT_NAME);
        deleteOutput(out);

        Path mc = out.resolve("assets").resolve("minecraft");
        int count = 0;
        Map<String, List<PotionSupport.Case>> potionCases = new LinkedHashMap<>();

        for (Category c : Category.ALL) {
            for (Category.Variant v : c.variants()) {
                String sel = selections.get(c.fullId(v));
                if (sel == null) continue;
                PackInfo info = packs.stream().filter(p -> p.name().equals(sel)).findFirst().orElse(null);
                if (info == null) continue;
                Files.createDirectories(mc);

                if (v.potion() != null) {
                    Category.PotionSpec ps = v.potion();
                    String key = (c.id() + "_" + v.key()).replaceAll("[^a-z0-9_]", "_");
                    PotionSupport.Case cs = withRoot(info.path(), root -> {
                        Map<String, Object> node = PotionSupport.resolveFor(root, ps.itemDef(), ps.ids());
                        if (node == null) return null;
                        return new PotionSupport.Case(ps.ids(), PotionSupport.export(root, node, mc, key));
                    });
                    if (cs != null) potionCases.computeIfAbsent(ps.itemDef(), k -> new ArrayList<>()).add(cs);
                    continue;
                }
                count += withRoot(info.path(), root -> copyVariant(root, v, mc));
            }
        }

        // Tränke: eine gemeinsame Weiche pro Item (potion / splash_potion), alle anderen Tränke bleiben Vanilla
        for (Map.Entry<String, List<PotionSupport.Case>> e : potionCases.entrySet()) {
            Object vanilla = vanillaModel(e.getKey());
            if (vanilla == null) {
                TextureMixerClient.LOGGER.warn("Vanilla-Definition für {} nicht gefunden, Trank-Texturen übersprungen", e.getKey());
                continue;
            }
            List<Object> cases = new ArrayList<>();
            for (PotionSupport.Case cs : e.getValue()) {
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("when", new ArrayList<Object>(cs.ids()));
                cm.put("model", cs.node());
                cases.add(cm);
            }
            Map<String, Object> sel = new LinkedHashMap<>();
            sel.put("type", "minecraft:select");
            sel.put("property", "minecraft:component");
            sel.put("component", "minecraft:potion_contents");
            sel.put("cases", cases);
            sel.put("fallback", vanilla);
            Map<String, Object> top = new LinkedHashMap<>();
            top.put("model", sel);
            Path items = mc.resolve("items").resolve(e.getKey() + ".json");
            Files.createDirectories(items.getParent());
            Files.writeString(items, Json.write(top));
            count++;
        }

        if (count == 0) {
            deleteOutput(out);
            return 0;
        }
        Files.writeString(out.resolve("pack.mcmeta"), MCMETA);
        return count;
    }

    /** Liest die originale Item-Definition aus dem Spiel selbst (unabhängig von Resource Packs). */
    @SuppressWarnings("unchecked")
    private static Object vanillaModel(String itemDef) {
        try {
            Optional<ModContainer> container = FabricLoader.getInstance().getModContainer("minecraft");
            if (container.isEmpty()) return null;
            Optional<Path> p = container.get().findPath("assets/minecraft/items/" + itemDef + ".json");
            if (p.isEmpty()) return null;
            Object parsed = Json.parse(Files.readString(p.get()));
            return parsed instanceof Map<?, ?> m ? ((Map<String, Object>) m).get("model") : null;
        } catch (Exception e) {
            TextureMixerClient.LOGGER.warn("Vanilla-Definition konnte nicht gelesen werden", e);
            return null;
        }
    }

    /** Liest das Vorschaubild einer Variante aus einem Pack. */
    public static PreviewData readPreview(PackInfo pack, Category.Variant v) throws IOException {
        if (v.isArmorItems()) {
            return armorItemsPreview(pack, v.material());
        }
        if (v.potion() != null) {
            Category.PotionSpec ps = v.potion();
            return withRoot(pack.path(), root -> {
                Map<String, Object> node = PotionSupport.resolveFor(root, ps.itemDef(), ps.ids());
                if (node == null) return null;
                PotionSupport.Image img = PotionSupport.preview(root, node, ps.color());
                if (img == null) return null;
                return new PreviewData(null, null, img.argb(), img.w(), img.h());
            });
        }
        return withRoot(pack.path(), root -> {
            Path tex = textures(root);
            if (!Files.isDirectory(tex)) return null;
            Path file = null;
            for (String cand : v.previews()) {
                Path p = tex.resolve(cand);
                if (Files.isRegularFile(p)) {
                    file = p;
                    break;
                }
            }
            if (file == null && v.hand() == null) {
                List<Path> files;
                try (Stream<Path> s = Files.walk(tex)) {
                    files = s.filter(Files::isRegularFile).sorted().toList();
                }
                for (Path f : files) {
                    String rel = rel(tex, f);
                    if (rel.endsWith(".png") && v.matches(rel)) {
                        file = f;
                        break;
                    }
                }
            }
            if (file == null) return null;
            byte[] png = Files.readAllBytes(file);
            Path meta = file.resolveSibling(file.getFileName().toString() + ".mcmeta");
            byte[] mcmeta = Files.isRegularFile(meta) ? Files.readAllBytes(meta) : null;
            return new PreviewData(png, mcmeta, null, 0, 0);
        });
    }

    /** Helm, Brustplatte, Hose und Stiefel (Item-Icons) als 2x2-Bild: oben Helm/Brustplatte, unten Hose/Stiefel. */
    private static PreviewData armorItemsPreview(PackInfo pack, String material) throws IOException {
        return withRoot(pack.path(), root -> {
            Path tex = textures(root);
            String[] parts = {"helmet", "chestplate", "leggings", "boots"};
            BufferedImage[] imgs = new BufferedImage[4];
            int tile = 0;
            for (int i = 0; i < 4; i++) {
                Path f = tex.resolve("item/" + material + "_" + parts[i] + ".png");
                if (!Files.isRegularFile(f)) continue;
                try (InputStream in = Files.newInputStream(f)) {
                    BufferedImage bi = ImageIO.read(in);
                    if (bi == null) continue;
                    imgs[i] = bi;
                    tile = Math.max(tile, bi.getWidth());
                }
            }
            if (tile <= 0) return null;

            int size = tile * 2;
            int[] out = new int[size * size];
            for (int i = 0; i < 4; i++) {
                BufferedImage bi = imgs[i];
                if (bi == null) continue;
                int src = bi.getWidth(); // animierte Texturen: nur das erste (quadratische) Bild
                int ox = (i % 2) * tile;
                int oy = (i / 2) * tile;
                for (int y = 0; y < tile; y++) {
                    for (int x = 0; x < tile; x++) {
                        int sx = x * src / tile;
                        int sy = y * src / tile;
                        if (sy >= bi.getHeight()) continue;
                        out[(oy + y) * size + ox + x] = bi.getRGB(sx, sy);
                    }
                }
            }
            return new PreviewData(null, null, out, size, size);
        });
    }

    /** Liest die getragene Rüstung eines Packs (neues Format zuerst, sonst altes Format vor 1.21.2). */
    public static BodyData readBody(PackInfo pack, String material) throws IOException {
        if ("elytra".equals(material)) {
            return withRoot(pack.path(), root -> {
                byte[] wings = firstBytes(textures(root),
                        "entity/equipment/wings/elytra.png",
                        "entity/elytra.png");
                return wings == null ? null : new BodyData(wings, null);
            });
        }
        return withRoot(pack.path(), root -> {
            Path tex = textures(root);
            byte[] outer = firstBytes(tex,
                    "entity/equipment/humanoid/" + material + ".png",
                    "models/armor/" + material + "_layer_1.png");
            byte[] legs = firstBytes(tex,
                    "entity/equipment/humanoid_leggings/" + material + ".png",
                    "models/armor/" + material + "_layer_2.png");
            if (outer == null && legs == null) return null;
            return new BodyData(outer, legs);
        });
    }

    /** Die originale Rüstung aus dem Spiel selbst (für die Karte "Standard"). */
    public static BodyData readVanillaBody(String material) {
        try {
            Optional<ModContainer> container = FabricLoader.getInstance().getModContainer("minecraft");
            if (container.isEmpty()) return null;
            String base = "assets/minecraft/textures/entity/equipment/";
            if ("elytra".equals(material)) {
                byte[] wings = readContainer(container.get(), base + "wings/elytra.png");
                return wings == null ? null : new BodyData(wings, null);
            }
            byte[] outer = readContainer(container.get(), base + "humanoid/" + material + ".png");
            byte[] legs = readContainer(container.get(), base + "humanoid_leggings/" + material + ".png");
            if (outer == null && legs == null) return null;
            return new BodyData(outer, legs);
        } catch (Exception e) {
            TextureMixerClient.LOGGER.warn("Vanilla-Rüstung konnte nicht gelesen werden", e);
            return null;
        }
    }

    private static byte[] readContainer(ModContainer container, String path) throws IOException {
        Optional<Path> p = container.findPath(path);
        return p.isPresent() && Files.isRegularFile(p.get()) ? Files.readAllBytes(p.get()) : null;
    }

    private static byte[] firstBytes(Path tex, String... candidates) throws IOException {
        for (String c : candidates) {
            Path p = tex.resolve(c);
            if (Files.isRegularFile(p)) return Files.readAllBytes(p);
        }
        return null;
    }

    private static Set<String> detect(Path root) throws IOException {
        Set<String> found = new LinkedHashSet<>();
        Path tex = textures(root);
        List<String> rels = new ArrayList<>();
        if (Files.isDirectory(tex)) {
            try (Stream<Path> s = Files.walk(tex)) {
                for (Path p : s.filter(Files::isRegularFile).toList()) rels.add(rel(tex, p));
            }
        }
        Set<String> relSet = new HashSet<>(rels);

        for (Category c : Category.ALL) {
            for (Category.Variant v : c.variants()) {
                boolean ok = false;
                if (v.potion() != null) {
                    ok = PotionSupport.resolveFor(root, v.potion().itemDef(), v.potion().ids()) != null;
                } else if (v.hand() != null) {
                    for (String s : v.hand().sources()) {
                        if (relSet.contains(s)) {
                            ok = true;
                            break;
                        }
                    }
                } else {
                    for (String rel : rels) {
                        if (v.matches(rel)) {
                            ok = true;
                            break;
                        }
                    }
                }
                if (ok) found.add(c.fullId(v));
            }
        }
        return found;
    }

    private static int copyVariant(Path root, Category.Variant v, Path mc) throws IOException {
        Path tex = textures(root);
        if (!Files.isDirectory(tex)) return 0;
        Path outTex = mc.resolve("textures");

        // In der Hand bei Blöcken/Netzen: Textur + eigenes Item-Modell + Item-Definition erzeugen
        if (v.hand() != null) {
            Category.HandSpec h = v.hand();
            for (String s : h.sources()) {
                Path src = tex.resolve(s);
                if (!Files.isRegularFile(src)) continue;

                String texName = "item/texturemixer/" + h.itemId() + "_hand";
                Path dstPng = outTex.resolve(texName + ".png");
                Files.createDirectories(dstPng.getParent());
                Files.copy(src, dstPng, StandardCopyOption.REPLACE_EXISTING);
                Path meta = src.resolveSibling(src.getFileName().toString() + ".mcmeta");
                if (Files.isRegularFile(meta)) {
                    Files.copy(meta, outTex.resolve(texName + ".png.mcmeta"), StandardCopyOption.REPLACE_EXISTING);
                }

                String modelId = "minecraft:item/texturemixer/" + h.itemId() + "_hand";
                String parent = h.cube() ? "minecraft:block/cube_all" : "minecraft:item/generated";
                String key = h.cube() ? "all" : "layer0";
                Path model = mc.resolve("models").resolve("item").resolve("texturemixer")
                        .resolve(h.itemId() + "_hand.json");
                Files.createDirectories(model.getParent());
                Files.writeString(model, "{\"parent\":\"" + parent + "\",\"textures\":{\"" + key + "\":\"" + modelId + "\"}}");

                Path items = mc.resolve("items").resolve(h.itemId() + ".json");
                Files.createDirectories(items.getParent());
                Files.writeString(items, "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + modelId + "\"}}");
                return 1;
            }
            return 0;
        }

        List<Path> files;
        try (Stream<Path> s = Files.walk(tex)) {
            files = s.filter(Files::isRegularFile).toList();
        }
        int n = 0;
        for (Category.Rule r : v.rules()) {
            for (Path p : files) {
                String rel = rel(tex, p);
                if (!rel.startsWith(r.src())) continue;
                Path target = outTex.resolve(r.dst() + rel.substring(r.src().length()));
                Files.createDirectories(target.getParent());
                Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                n++;
            }
        }
        return n;
    }

    private static Path textures(Path root) {
        return root.resolve("assets").resolve("minecraft").resolve("textures");
    }

    private static String rel(Path base, Path p) {
        return base.relativize(p).toString().replace('\\', '/');
    }

    private static <T> T withRoot(Path pack, RootAction<T> action) throws IOException {
        if (Files.isDirectory(pack)) return action.run(pack);
        try (FileSystem fs = FileSystems.newFileSystem(pack)) {
            return action.run(fs.getPath("/"));
        }
    }

    private static void deleteOutput(Path out) throws IOException {
        if (!Files.exists(out) || !out.getFileName().toString().equals(OUTPUT_NAME)) return;
        List<Path> all;
        try (Stream<Path> s = Files.walk(out)) {
            all = s.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path p : all) Files.deleteIfExists(p);
    }
}
