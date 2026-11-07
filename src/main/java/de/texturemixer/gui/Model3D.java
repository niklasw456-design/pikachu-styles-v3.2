package de.texturemixer.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometrie der drehbaren Spielerfigur (ohne Minecraft-Klassen, nur Mathematik).
 *
 * Die Figur besteht aus Quadern (Kopf, Körper, Arme, Beine, Rüstungs- und Skin-Ebenen, Elytra-Flügel).
 * Die Kamera schaut waagerecht auf die Figur, die sich um die senkrechte Achse dreht. Dann bleiben alle
 * senkrechten Kanten senkrecht, und jede sichtbare Seitenfläche ist ein Rechteck, dessen Breite sich
 * mit dem Drehwinkel ändert. Jede Fläche lässt sich deshalb mit einem einzigen skalierten
 * Texturausschnitt zeichnen (kein echtes 3D nötig).
 *
 * Koordinaten in Skin-Pixeln: x nach rechts im Bild (bei Winkel 0 = linke Körperseite des Spielers),
 * y nach unten (Kopfoberkante = 0), z zum Betrachter (bei Winkel 0 schaut die Figur den Betrachter an).
 */
final class Model3D {
    static final int SKIN = 0;
    /** Rüstungs-Textur Schicht 1 (Helm, Brustplatte, Stiefel) bzw. Elytra-Flügel. */
    static final int OUTER = 1;
    /** Rüstungs-Textur Schicht 2 (Hose). */
    static final int LEGS = 2;

    private static final float EPS = 0.02f;

    /**
     * Eine sichtbare Fläche. x0/x1 = Bild-X, y0/y1 = Höhe (beides in Skin-Pixeln, Figurmitte x = 0),
     * u/v/w/h = Ausschnitt im 64er-Raster der Textur, flip = waagerecht gespiegelt,
     * shade = Helligkeit 0..1, depth = Abstand (größer = näher am Betrachter).
     */
    record Quad(int tex, float x0, float x1, float y0, float y1,
                int u, int v, int w, int h, boolean flip, float shade, float depth) {}

    private record Box(int tex, int u0, int v0, int w, int h, int d,
                       float cx, float top, float cz, float infl, boolean mirror) {}

    private Model3D() {}

    private static Box box(int tex, int u0, int v0, int w, int h, int d,
                           float cx, float top, float cz, float infl, boolean mirror) {
        return new Box(tex, u0, v0, w, h, d, cx, top, cz, infl, mirror);
    }

    /** Alle Quader von hinten nach vorn: Skin, Skin-Ebene 2, Hose, Rüstung bzw. Flügel. */
    private static List<Box> boxes(boolean elytra) {
        List<Box> b = new ArrayList<>();

        // Skin (Kopf, Körper, Arme, Beine) und zweite Skin-Ebene
        b.add(box(SKIN, 0, 0, 8, 8, 8, 0, 0, 0, 0f, false));
        b.add(box(SKIN, 16, 16, 8, 12, 4, 0, 8, 0, 0f, false));
        b.add(box(SKIN, 40, 16, 4, 12, 4, -6, 8, 0, 0f, false));   // rechter Arm
        b.add(box(SKIN, 32, 48, 4, 12, 4, 6, 8, 0, 0f, false));    // linker Arm
        b.add(box(SKIN, 0, 16, 4, 12, 4, -2, 20, 0, 0f, false));   // rechtes Bein
        b.add(box(SKIN, 16, 48, 4, 12, 4, 2, 20, 0, 0f, false));   // linkes Bein

        b.add(box(SKIN, 32, 0, 8, 8, 8, 0, 0, 0, 0.5f, false));    // Hut
        b.add(box(SKIN, 16, 32, 8, 12, 4, 0, 8, 0, 0.25f, false)); // Jacke
        b.add(box(SKIN, 40, 32, 4, 12, 4, -6, 8, 0, 0.25f, false)); // Ärmel rechts
        b.add(box(SKIN, 48, 48, 4, 12, 4, 6, 8, 0, 0.25f, false));  // Ärmel links
        b.add(box(SKIN, 0, 32, 4, 12, 4, -2, 20, 0, 0.25f, false)); // Hosenbein rechts
        b.add(box(SKIN, 0, 48, 4, 12, 4, 2, 20, 0, 0.25f, false));  // Hosenbein links

        if (elytra) {
            // Flügel hängen hinter dem Rücken (z = -4 .. -2), je 10 x 20 x 2, der rechte ist gespiegelt
            b.add(box(OUTER, 22, 0, 10, 20, 2, 6, 8, -3, 0f, false));
            b.add(box(OUTER, 22, 0, 10, 20, 2, -6, 8, -3, 0f, true));
        } else {
            // Hose (Schicht 2, Aufblähung 0.5): Bund und beide Beine, linkes Bein gespiegelt
            b.add(box(LEGS, 16, 16, 8, 12, 4, 0, 8, 0, 0.5f, false));
            b.add(box(LEGS, 0, 16, 4, 12, 4, -2, 20, 0, 0.5f, false));
            b.add(box(LEGS, 0, 16, 4, 12, 4, 2, 20, 0, 0.5f, true));
            // Helm, Brustplatte (mit Ärmeln) und Stiefel (Schicht 1, Aufblähung 1.0)
            b.add(box(OUTER, 0, 16, 4, 12, 4, -2, 20, 0, 1f, false));
            b.add(box(OUTER, 0, 16, 4, 12, 4, 2, 20, 0, 1f, true));
            b.add(box(OUTER, 16, 16, 8, 12, 4, 0, 8, 0, 1f, false));
            b.add(box(OUTER, 40, 16, 4, 12, 4, -6, 8, 0, 1f, false));
            b.add(box(OUTER, 40, 16, 4, 12, 4, 6, 8, 0, 1f, true));
            b.add(box(OUTER, 0, 0, 8, 8, 8, 0, 0, 0, 1f, false));
        }
        return b;
    }

    /** Berechnet alle sichtbaren Flächen für den Drehwinkel (Bogenmaß), unsortiert. */
    static List<Quad> build(double angle, boolean elytra) {
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        List<Quad> out = new ArrayList<>();
        List<Box> boxes = boxes(elytra);

        for (int i = 0; i < boxes.size(); i++) {
            Box b = boxes.get(i);
            float hx = b.w() / 2f + b.infl();
            float hz = b.d() / 2f + b.infl();
            float y0 = b.top() - b.infl();
            float y1 = b.top() + b.h() + b.infl();
            float bias = i * 1e-3f;

            // Ausschnitte im Strip: rechts | vorne | links | hinten
            int u0 = b.u0();
            int v = b.v0() + b.d();
            boolean m = b.mirror();

            if (c > EPS) { // Vorderseite
                float z = b.cz() + hz;
                add(out, b, (b.cx() - hx) * c + z * s, (b.cx() + hx) * c + z * s, y0, y1,
                        u0 + b.d(), v, b.w(), m, c, -b.cx() * s + z * c + bias);
            }
            if (c < -EPS) { // Rückseite
                float z = b.cz() - hz;
                add(out, b, (b.cx() - hx) * c + z * s, (b.cx() + hx) * c + z * s, y0, y1,
                        u0 + 2 * b.d() + b.w(), v, b.w(), m, -c, -b.cx() * s + z * c + bias);
            }
            if (s < -EPS) { // Seite bei +x (linke Körperseite des Spielers)
                float x = b.cx() + hx;
                int u = m ? u0 : u0 + b.d() + b.w();
                add(out, b, x * c + (b.cz() - hz) * s, x * c + (b.cz() + hz) * s, y0, y1,
                        u, v, b.d(), m, -s, -x * s + b.cz() * c + bias);
            }
            if (s > EPS) { // Seite bei -x (rechte Körperseite des Spielers)
                float x = b.cx() - hx;
                int u = m ? u0 + b.d() + b.w() : u0;
                add(out, b, x * c + (b.cz() - hz) * s, x * c + (b.cz() + hz) * s, y0, y1,
                        u, v, b.d(), m, s, -x * s + b.cz() * c + bias);
            }
        }
        return out;
    }

    private static void add(List<Quad> out, Box b, float xa, float xb, float y0, float y1,
                            int u, int v, int w, boolean flip, float facing, float depth) {
        float x0 = Math.min(xa, xb);
        float x1 = Math.max(xa, xb);
        float shade = 0.6f + 0.4f * Math.min(1f, Math.max(0f, facing));
        out.add(new Quad(b.tex(), x0, x1, y0, y1, u, v, w, b.h(), flip, shade, depth));
    }
}
