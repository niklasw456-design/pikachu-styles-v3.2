package de.texturemixer.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.Comparator;
import java.util.List;

/**
 * Zeichnet den eigenen Spieler-Skin als sich langsam drehende Figur, mit der Rüstung bzw. den
 * Elytra-Flügeln aus einem Pack. Die Geometrie steht in {@link Model3D}: jede sichtbare Fläche ist ein
 * skalierter Texturausschnitt (kein Pixel-Zugriff auf den Skin nötig), von hinten nach vorn gezeichnet
 * und je nach Blickwinkel leicht abgedunkelt.
 *
 * Z = GUI-Pixel pro Skin-Pixel. Die Figur ist 32 Skin-Pixel hoch, mit Rüstung kommt oben und unten je
 * 1 Pixel dazu.
 */
final class BodyView {
    static final int Z = 2;
    /** Höhe des Vorschaubereichs in GUI-Pixeln (34 Skin-Pixel). */
    static final int H = 34 * Z;
    /** Eine volle Umdrehung dauert so viele Millisekunden. */
    private static final long PERIOD_MS = 10_000L;

    private static final Identifier STEVE = Identifier.of("minecraft", "textures/entity/player/wide/steve.png");

    private BodyView() {}

    /** Textur des eigenen Skins, ohne Welt (z. B. im Titelbildschirm) Steve. */
    private static Identifier skin() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                return mc.player.getSkin().body().texturePath();
            }
        } catch (Throwable ignored) {
            // fällt auf Steve zurück
        }
        return STEVE;
    }

    /**
     * cx = Mitte der Figur, y = obere Kante des Vorschaubereichs (H GUI-Pixel hoch).
     * elytra = Flügel statt Rüstung.
     */
    static void draw(DrawContext ctx, int cx, int y, PreviewCache.Body body, boolean elytra) {
        double angle = (System.currentTimeMillis() % PERIOD_MS) / (double) PERIOD_MS * Math.PI * 2.0;
        List<Model3D.Quad> quads = Model3D.build(angle, elytra);
        quads.sort(Comparator.comparingDouble(Model3D.Quad::depth));

        Identifier skin = skin();
        int top = y + Z; // 1 Skin-Pixel Platz für die Aufblähung des Helms

        for (Model3D.Quad q : quads) {
            Identifier id;
            int texW;
            int texH;
            int m;
            if (q.tex() == Model3D.SKIN) {
                id = skin;
                texW = 64;
                texH = 64;
                m = 1;
            } else {
                PreviewCache.Tex t = q.tex() == Model3D.OUTER ? body.outer() : body.legs();
                if (t == null) continue;
                id = t.id();
                texW = t.texW();
                texH = t.texH();
                // HD-Packs (128, 256 ...) werden über den Faktor m auf das 64er-Raster umgerechnet
                m = Math.max(1, t.grid() / 64) * t.scale();
            }

            int dx0 = Math.round(cx + q.x0() * Z);
            int dx1 = Math.round(cx + q.x1() * Z);
            int dy0 = Math.round(top + q.y0() * Z);
            int dy1 = Math.round(top + q.y1() * Z);
            int dw = dx1 - dx0;
            int dh = dy1 - dy0;
            if (dw <= 0 || dh <= 0) continue;

            float u = q.u() * m;
            float v = q.v() * m;
            int rw = q.w() * m;
            int rh = q.h() * m;
            if (q.flip()) { // gespiegelt: rechts beginnen und rückwärts lesen
                u += rw;
                rw = -rw;
            }

            int g = Math.round(255f * q.shade());
            int color = 0xFF000000 | (g << 16) | (g << 8) | g;
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, id, dx0, dy0, u, v, dw, dh,
                    rw, rh, texW, texH, color);
        }
    }
}
