package fr.accueil.natif;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Paysages dessinés du thème Thinking, repris de la version web (makeScene) : ciel, soleil ou lune,
 * nuages plats, oiseaux, sept tranches de collines et de montagnes, chemin en perspective, bosquets
 * (sapins, feuillus, pins parasols, arbres nus), fleurs. Quatre moments (aube, jour, crépuscule, nuit)
 * et quatre saisons. Même générateur pseudo-aléatoire, mêmes tirages, dans le même ordre : le paysage
 * est identique à celui de la version web. Dessiné une fois dans une image de 1920 × 1080.
 */
final class Landscape {

    static final int W = 1920, H = 1080;
    static final int VERSION = 5; // à augmenter quand le dessin change : les images en cache sont refaites
    static final String[] TIMES = {"aube", "jour", "crépuscule", "nuit"};

    /* ===== Palettes du moment ===== */

    static final class Palette {
        final String name;
        final boolean night;
        final int[] sky, hills;
        final int sun, halo, tree, path, cloud;

        Palette(String name, boolean night, String[] sky, String sun, String halo, String[] hills, String tree, String path, String cloud) {
            this.name = name;
            this.night = night;
            this.sky = cols(sky);
            this.sun = c(sun);
            this.halo = c(halo);
            this.hills = cols(hills);
            this.tree = c(tree);
            this.path = c(path);
            this.cloud = c(cloud);
        }
    }

    static final Palette[] PALETTES = {
            new Palette("aube", false, new String[]{"#f8d9b6", "#fcefd8", "#eef1dc"}, "#fff6e2", "#fff1cf",
                    new String[]{"#d8dfbd", "#b7cf98", "#8db577", "#5f9161", "#3f6f4c"}, "#3a6a49", "#f3dcae", "#fffaf0"),
            new Palette("jour", false, new String[]{"#b9dff0", "#ddf1f3", "#f3f8ec"}, "#fffdf0", "#ffffff",
                    new String[]{"#cfe4c4", "#a9d394", "#7fbb6c", "#4f9a5c", "#2f7449"}, "#2c6a46", "#efe3bf", "#ffffff"),
            new Palette("crépuscule", false, new String[]{"#f2a98f", "#f9cfb0", "#fbe6cd"}, "#ffe0b5", "#ffd2a0",
                    new String[]{"#dcb6a9", "#bb9a9e", "#8f8399", "#636982", "#414a64"}, "#38405a", "#f6c9a4", "#fde3d2"),
            new Palette("nuit", true, new String[]{"#22314f", "#34476c", "#4b5f83"}, "#f4f0de", "#9fb2d6",
                    new String[]{"#41587a", "#35496a", "#2a3c58", "#203048", "#16243a"}, "#121e2f", "#5b6f92", "#5f7298"),
    };

    /** Part de l'éclairage du moment appliquée par-dessus les couleurs de la saison. */
    private static final double[] TIME_W = {.4, 0, .55, .78};

    /* ===== Saisons ===== */

    static final class Season {
        final String id;
        final int[] hills, blossom, flowers, canopy;
        final int tree, path, skyTint, bare;
        final boolean snow;
        final int flowerCount;
        final double mix0, mix1;

        Season(String id, String[] hills, String tree, String path, String skyTint, String bare, boolean snow,
               String[] blossom, String[] flowers, String[] canopy, int flowerCount, double mix0, double mix1) {
            this.id = id;
            this.hills = cols(hills);
            this.tree = c(tree);
            this.path = c(path);
            this.skyTint = skyTint == null ? 0 : c(skyTint);
            this.bare = bare == null ? 0 : c(bare);
            this.snow = snow;
            this.blossom = blossom == null ? null : cols(blossom);
            this.flowers = flowers == null ? null : cols(flowers);
            this.canopy = canopy == null ? null : cols(canopy);
            this.flowerCount = flowerCount;
            this.mix0 = mix0;
            this.mix1 = mix1;
        }
    }

    static final Season[] SEASONS = {
            new Season("printemps", new String[]{"#d9eab9", "#b8dc92", "#8fca72", "#62ab64", "#3f8b52"}, "#2f7a48", "#efe4c2",
                    null, null, false, new String[]{"#f7c9da", "#ffffff", "#f2a7c3", "#fbe3ea"},
                    new String[]{"#f7c9da", "#ffe48a", "#ffffff", "#cbb0f2", "#f2a7c3"}, null, 70, .35, .85),
            new Season("ete", new String[]{"#e4e2ad", "#cfd585", "#a5c266", "#6f9d50", "#4a7a3f"}, "#36683c", "#f2dfaa",
                    null, null, false, null, new String[]{"#fff1a6", "#cbb0f2", "#ffffff"}, null, 35, .3, .65),
            new Season("automne", new String[]{"#e0dcae", "#cdc58b", "#ab9f66", "#83814e", "#5c623c"}, "#3a6248", "#ead2a5",
                    null, null, false, null, null, new String[]{"#d9893b", "#c8642f", "#e2b04a", "#b5552a", "#a8953f", "#cf7b35"}, 0, .38, .85),
            new Season("hiver", new String[]{"#f1f4f7", "#e5ebf0", "#d7e0e8", "#c6d2dc", "#b3c2cf"}, "#2d4c45", "#e9eef2",
                    "#e3eaf0", "#5d4f48", true, null, null, null, 0, .45, .85),
    };

    static Season season(String id) {
        for (Season s : SEASONS) if (s.id.equals(id)) return s;
        return null;
    }

    /** Saison selon la date (21 mars, 21 juin, 22 septembre, 21 décembre). */
    static String seasonByDate(int month1, int day) {
        int md = month1 * 100 + day;
        if (md >= 1221 || md < 320) return "hiver";
        if (md < 621) return "printemps";
        if (md < 922) return "ete";
        return "automne";
    }

    /* ===== Couleurs ===== */

    static int c(String hex) {
        return 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
    }

    private static int[] cols(String[] h) {
        int[] r = new int[h.length];
        for (int i = 0; i < h.length; i++) r[i] = c(h[i]);
        return r;
    }

    private static int ch(double v) {
        return (int) Math.floor(Math.max(0, Math.min(255, v)) + .5);
    }

    static int mix(int a, int b, double t) {
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return 0xFF000000 | ch(ar + (br - ar) * t) << 16 | ch(ag + (bg - ag) * t) << 8 | ch(ab + (bb - ab) * t);
    }

    private static int ramp(int[] cols, double t) {
        double x = t * (cols.length - 1);
        int i = Math.min(cols.length - 2, (int) Math.floor(x));
        return mix(cols[i], cols[i + 1], x - i);
    }

    private static int alpha(int color, double a) {
        return (color & 0x00FFFFFF) | ch(a * 255) << 24;
    }

    private static final int BLACK = 0xFF000000, WHITE = 0xFFFFFFFF;

    /** Générateur de la version web : seed = seed × 16807 mod (2³¹ − 1). */
    static final class Rng {
        private long s;

        Rng(long seed) {
            s = seed;
        }

        double r() {
            s = (s * 16807) % 2147483647L;
            return s / 2147483647.0;
        }
    }

    /* ===== Relief ===== */

    private static double hillY(double x, double base, double amp, double f, double ph) {
        return base + amp * (Math.sin(x * f + ph) * .6 + Math.sin(x * f * 2.3 + ph * 1.7) * .25 + Math.sin(x * f * 5.1 + ph) * .08);
    }

    /** Crête de montagne : sommets pointus (1 − |sin|) et petites irrégularités. */
    private static double mtnY(double x, Layer L, double ph) {
        double peaks = 1 - Math.abs(Math.sin(x * L.f + ph));
        double small = 1 - Math.abs(Math.sin(x * L.f * 2.7 + ph * 1.9));
        return L.base - L.amp * (peaks * .75 + small * .25) + Math.sin(x * .02 + ph) * 4;
    }

    private static final class Layer {
        final double base, amp, f;
        final boolean mtn;

        Layer(double base, double amp, double f, boolean mtn) {
            this.base = base;
            this.amp = amp;
            this.f = f;
            this.mtn = mtn;
        }
    }

    private static final Layer[] LAYERS = {
            new Layer(520, 170, .0029, true),
            new Layer(610, 42, .0036, false),
            new Layer(668, 48, .0043, false),
            new Layer(738, 50, .0051, false),
            new Layer(812, 48, .0046, false),
            new Layer(896, 44, .0040, false),
            new Layer(992, 38, .0034, false),
    };
    private static final int[] TREES = {0, 0, 30, 22, 15, 9, 6};
    private static final double[] SCALE = {0, 0, .38, .58, .85, 1.15, 1.6};

    /* ===== Chemin en perspective : axe sinueux, de plus en plus large vers l'avant ===== */

    private static final double PATH_TOP = 700, PATH_BOTTOM = 1080;

    private static double[] pathAt(double y) {
        double k = (y - PATH_TOP) / (PATH_BOTTOM - PATH_TOP);
        return new double[]{960 + Math.sin(k * 5.2 + .4) * (30 + k * 120), 5 + k * k * 150 + k * 25};
    }

    private interface Half {
        double of(double k, double w);
    }

    /** Bande qui suit le chemin : décalage (en fraction de la largeur) et demi-largeur selon la profondeur k. */
    private static Path pathStrip(double off, Half half) {
        List<float[]> left = new ArrayList<>(), right = new ArrayList<>();
        for (double y = PATH_TOP; y <= PATH_BOTTOM + 10; y += 8) {
            double k = (y - PATH_TOP) / (PATH_BOTTOM - PATH_TOP);
            double[] p = pathAt(y);
            double cc = p[0] + p[1] * off, h = half.of(k, p[1]);
            left.add(new float[]{(float) (cc - h), (float) y});
            right.add(0, new float[]{(float) (cc + h), (float) y});
        }
        Path path = new Path();
        path.moveTo(left.get(0)[0], left.get(0)[1]);
        for (int i = 1; i < left.size(); i++) path.lineTo(left.get(i)[0], left.get(i)[1]);
        for (float[] q : right) path.lineTo(q[0], q[1]);
        path.close();
        return path;
    }

    private static Path P_EDGE, P_INNER, P_LIGHT, P_RUT_L, P_RUT_R;

    private static synchronized void paths() {
        if (P_EDGE != null) return;
        P_EDGE = pathStrip(0, (k, w) -> w);
        P_INNER = pathStrip(0, (k, w) -> w * .82);
        P_LIGHT = pathStrip(-.2, (k, w) -> w * .22);
        P_RUT_L = pathStrip(-.42, (k, w) -> .4 + k * 3.2);
        P_RUT_R = pathStrip(.42, (k, w) -> .4 + k * 3.2);
    }

    /* ===== Dessin ===== */

    private final Canvas cv;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rng rng;
    /** Arbre dessiné, pour les effets de saison (feuilles qui tombent, lucioles). */
    static final class Tree {
        final float x, y, h;   // pied de l'arbre et hauteur, en pixels de l'image 1920 × 1080
        final int layer;       // tranche de paysage (2 au fond … 6 devant)
        final boolean autumn;  // feuillage d'automne (orange)
        final int color;

        Tree(double x, double y, double h, int layer, boolean autumn, int color) {
            this.x = (float) x;
            this.y = (float) y;
            this.h = (float) h;
            this.layer = layer;
            this.autumn = autumn;
            this.color = color;
        }
    }

    final List<Tree> trees = new ArrayList<>();

    private Landscape(Canvas cv, long seed) {
        this.cv = cv;
        rng = new Rng(seed);
        stroke.setStyle(Paint.Style.STROKE);
    }

    private double r() {
        return rng.r();
    }

    private Paint fill(int color) {
        fill.setShader(null);
        fill.setColor(color);
        return fill;
    }

    private Paint fill(int color, double opacity) {
        fill.setShader(null);
        fill.setColor(alpha(color, opacity));
        return fill;
    }

    private void circle(double x, double y, double rad, Paint p) {
        cv.drawCircle((float) x, (float) y, (float) rad, p);
    }

    private void ellipse(double cx, double cy, double rx, double ry, Paint p) {
        cv.drawOval((float) (cx - rx), (float) (cy - ry), (float) (cx + rx), (float) (cy + ry), p);
    }

    /** Fond du paysage `idx` (0 aube, 1 jour, 2 crépuscule, 3 nuit) pour la saison donnée, à la taille demandée. */
    static Bitmap render(int idx, String seasonId, int width, int height) {
        paths();
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        // preserveAspectRatio « slice » : mise à l'échelle pour couvrir, centrée
        float s = Math.max(width / (float) W, height / (float) H);
        c.translate((width - W * s) / 2, (height - H * s) / 2);
        c.scale(s, s);
        new Landscape(c, 1234 + idx * 977L).draw(PALETTES[idx], TIME_W[idx], season(seasonId));
        grain(c);
        return out;
    }

    /** Position des arbres du paysage, sans le dessiner vraiment (même tirage, sur une image minuscule). */
    static List<Tree> trees(int idx, String seasonId) {
        paths();
        Bitmap tiny = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(tiny);
        c.scale(2f / W, 2f / W);
        Landscape l = new Landscape(c, 1234 + idx * 977L);
        l.draw(PALETTES[idx], TIME_W[idx], season(seasonId));
        tiny.recycle();
        return l.trees;
    }

    private void draw(Palette p0, double w, Season S) {
        // couleurs de la saison sous l'éclairage du moment
        int[] hills = new int[5];
        for (int i = 0; i < 5; i++) hills[i] = mix(S.hills[i], p0.hills[i], w);
        int tree = mix(S.tree, p0.tree, w), pathCol = mix(S.path, p0.path, w);
        int[] sky = p0.sky.clone();
        if (S.skyTint != 0) for (int i = 0; i < 3; i++) sky[i] = mix(p0.sky[i], S.skyTint, .28 * (1 - w));
        final double lw = w * .85;
        Integer snowCol = S.snow ? mix(c("#f5f8fb"), p0.hills[0], w * .85) : null;
        boolean night = p0.night;

        // ciel
        fill.setShader(new LinearGradient(0, 0, 0, H, sky, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, W, H, fill);

        // étoiles
        if (night) {
            for (int i = 0; i < 140; i++) {
                double x = Math.round(r() * W), y = Math.round(r() * 560), rr = r() * 1.7 + .4, op = r() * .6 + .25;
                circle(x, y, rr, fill(WHITE, op));
            }
        }

        // soleil ou lune sur un côté, jamais derrière l'heure
        double v = r(), sx = v < .5 ? 260 + v * 500 : 1410 + (v - .5) * 480;
        double sy = new double[]{420, 250, 440, 250}[indexOf(p0)];
        fill.setShader(new RadialGradient((float) sx, (float) sy, 340, alpha(p0.halo, .8), alpha(p0.halo, 0), Shader.TileMode.CLAMP));
        circle(sx, sy, 340, fill);
        double[] rings = {240, 180, 130}, ops = {.07, .11, .17};
        for (int k = 0; k < 3; k++) circle(sx, sy, night ? rings[k] * .7 : rings[k], fill(p0.halo, ops[k]));
        if (night) {
            Path moon = new Path(), bite = new Path();
            moon.addCircle((float) sx, (float) sy, 58, Path.Direction.CW);
            bite.addCircle((float) sx + 26, (float) sy - 18, 50, Path.Direction.CW);
            moon.op(bite, Path.Op.DIFFERENCE);
            cv.drawPath(moon, fill(p0.sun));
        } else circle(sx, sy, 92, fill(p0.sun));

        // nuages plats à deux tons (opacité de groupe)
        int layer = cv.saveLayerAlpha(0, 0, W, H, (int) Math.round((night ? .4 : .95) * 255));
        int shade = mix(p0.cloud, sky[0], .35);
        for (int i = 0; i < 6; i++) {
            double cx = r() * W * 1.05 - 60, cy = 110 + r() * 260, cw = 180 + r() * 220, chh = cw * .16;
            fill(shade);
            cv.drawRoundRect((float) (cx - cw / 2), (float) (cy - chh / 2 + 7), (float) (cx + cw / 2), (float) (cy + chh / 2 + 7), (float) (chh / 2), (float) (chh / 2), fill);
            fill(p0.cloud);
            circle(cx - cw * .16, cy - chh * .45, cw * .16, fill);
            circle(cx + cw * .1, cy - chh * .6, cw * .2, fill);
            cv.drawRoundRect((float) (cx - cw / 2), (float) (cy - chh / 2), (float) (cx + cw / 2), (float) (cy + chh / 2), (float) (chh / 2), (float) (chh / 2), fill);
        }
        cv.restoreToCount(layer);

        // oiseaux, de jour
        if (!night) {
            stroke.setColor(mix(tree, sky[1], .35));
            stroke.setStrokeWidth(2.2f);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            for (int i = 0; i < 4; i++) {
                double bx = sx + 180 + r() * 260, by = sy - 200 + r() * 120, z = .7 + r() * .6;
                Path b = new Path();
                b.moveTo((float) bx, (float) by);
                b.rQuadTo((float) (6 * z), (float) (-6 * z), (float) (12 * z), 0);
                b.rQuadTo((float) (6 * z), (float) (-6 * z), (float) (12 * z), 0);
                cv.drawPath(b, stroke);
            }
        }

        // tranches de paysage, du fond vers l'avant
        for (int i = 0; i < LAYERS.length; i++) {
            final Layer L = LAYERS[i];
            double t = i / (double) (LAYERS.length - 1);
            int col = i == 0 ? mix(hills[0], sky[2], .3) : ramp(hills, t);
            final double ph = r() * 6;
            Path ridge = new Path(), d = new Path();
            float minY = H;
            d.moveTo(-20, H);
            boolean first = true;
            for (int x = -20; x <= W + 20; x += 16) {
                float y = (float) yAt(L, x, ph);
                minY = Math.min(minY, y);
                if (first) ridge.moveTo(x, y);
                else ridge.lineTo(x, y);
                d.lineTo(x, y);
                first = false;
            }
            d.lineTo(W + 20, H);
            d.close();

            // ombre douce portée sur la tranche de derrière (effet papier découpé)
            if (i > 0) {
                int sh = mix(col, BLACK, .55);
                cv.save();
                cv.translate(0, -12);
                cv.drawPath(d, fill(sh, .05));
                cv.restore();
                cv.save();
                cv.translate(0, -5);
                cv.drawPath(d, fill(sh, .08));
                cv.restore();
            }
            fill.setShader(new LinearGradient(0, minY, 0, H, mix(col, WHITE, .1), mix(col, BLACK, .14), Shader.TileMode.CLAMP));
            cv.drawPath(d, fill);
            stroke.setColor(alpha(mix(col, p0.halo, .45), .6));
            stroke.setStrokeWidth((float) (1.5 + t * 2));
            stroke.setStrokeCap(Paint.Cap.BUTT);
            stroke.setStrokeJoin(Paint.Join.MITER);
            cv.drawPath(ridge, stroke);

            // brume entre les plans lointains
            if (i <= 3) {
                float top = (float) (L.base - 50);
                int a = (int) Math.round((.42 - i * .1) * 255);
                int lay = cv.saveLayerAlpha(0, top, W, H, a);
                fill.setShader(new LinearGradient(0, top, 0, H, new int[]{alpha(sky[2], 0), alpha(sky[2], .55), alpha(sky[2], .15)},
                        new float[]{0, .35f, 1}, Shader.TileMode.CLAMP));
                cv.drawRect(0, top, W, H, fill);
                cv.restoreToCount(lay);
            }

            // chemin : redessiné sur chaque tranche, caché derrière la crête suivante (découpe lissée)
            if (i >= 3) {
                int pc = mix(pathCol, col, .3 - t * .2), edge = mix(pc, col, .45), rut = mix(pc, BLACK, .18);
                int lay = cv.saveLayer(0, Math.max(minY, (float) PATH_TOP) - 4, W, H, null);
                cv.drawPath(P_EDGE, fill(edge));
                cv.drawPath(P_INNER, fill(pc));
                cv.drawPath(P_LIGHT, fill(mix(pc, WHITE, .35), .45));
                cv.drawPath(P_RUT_L, fill(rut, .35));
                cv.drawPath(P_RUT_R, fill(rut, .35));
                // cailloux et touffes d'herbe au bord, sur les tranches de devant
                if (i >= 5) {
                    for (int k = 0; k < 14; k++) {
                        double y = Math.max(L.base - 10, PATH_TOP) + r() * (H - L.base);
                        double[] pa = pathAt(Math.min(y, PATH_BOTTOM));
                        double kk = (y - PATH_TOP) / (PATH_BOTTOM - PATH_TOP), sz = 2 + kk * 6;
                        double ex = pa[0] + (r() - .5) * pa[1] * 1.4;
                        ellipse(ex, y, sz, sz * .55, fill(mix(pc, BLACK, .12 + r() * .1)));
                        int side = r() < .5 ? -1 : 1;
                        double gx = pa[0] + side * pa[1] * (.92 + r() * .12), gh = 6 + kk * 16;
                        Path g = new Path();
                        g.moveTo((float) (gx - gh * .3), (float) y);
                        g.lineTo((float) (gx - gh * .15), (float) (y - gh * .8));
                        g.lineTo((float) gx, (float) y);
                        g.lineTo((float) (gx + gh * .1), (float) (y - gh));
                        g.lineTo((float) (gx + gh * .25), (float) y);
                        g.lineTo((float) (gx + gh * .4), (float) (y - gh * .7));
                        g.lineTo((float) (gx + gh * .5), (float) y);
                        g.close();
                        cv.drawPath(g, fill(mix(col, BLACK, .1)));
                    }
                }
                // efface tout ce qui dépasse de la colline (contour inversé : un mode de fusion
                // ne s'applique qu'aux pixels dessinés, la colline elle-même ne masquerait rien)
                Path outside = new Path(d);
                outside.setFillType(Path.FillType.INVERSE_WINDING);
                Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
                mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                cv.drawPath(outside, mask);
                cv.restoreToCount(lay);
            }

            // massifs de fleurs dans les prés visibles au-dessus du dock (tirage à part)
            if (S.flowerCount > 0 && !night && (i == 3 || i == 4)) meadow(L, ph, i, col, S, p0.tree, lw);

            // arbres, regroupés en bosquets
            if (TREES[i] > 0) {
                int baseDark = i >= 5 ? tree : mix(col, BLACK, .22 + t * .1);
                int n = Math.max(2, (int) Math.round(TREES[i] / 3.0));
                double[] groves = new double[n];
                for (int k = 0; k < n; k++) groves[k] = r() * W;
                for (int k = 0; k < TREES[i]; k++) {
                    double g = groves[k % n];
                    double x = k % 4 == 3 ? r() * W : g + (r() + r() - 1) * 140 * (SCALE[i] + .4);
                    double y = yAt(L, x, ph) + 6, h = (70 + r() * 55) * SCALE[i];
                    if (y > PATH_TOP - 20) {
                        double[] pa = pathAt(Math.min(y, PATH_BOTTOM));
                        if (Math.abs(x - pa[0]) < pa[1] + h * .5) continue;
                    }
                    int toward = r() < .5 ? BLACK : p0.halo;
                    int dark = mix(baseDark, toward, r() * .12);
                    int light = mix(dark, p0.halo, .3), mid = mix(dark, p0.halo, .14);
                    double kind = r(), haze = .45 * (1 - t);
                    int before = trees.size();
                    if (kind < S.mix0) pine(x, y, h, dark, light, snowCol);
                    else if (kind < S.mix1) {
                        if (S.bare != 0) bareTree(x, y, h, mix(mix(S.bare, p0.tree, lw), col, haze), snowCol);
                        else if (S.canopy != null) { // feuillage d'automne
                            int cc = mix(mix(S.canopy[(int) Math.floor(r() * S.canopy.length)], p0.tree, lw), col, haze);
                            roundTree(x, y, h, mix(cc, BLACK, .28), mix(cc, p0.halo, .3), cc, null);
                            trees.add(new Tree(x, y, h, i, true, cc));
                        } else {
                            int[] bl = null;
                            if (S.blossom != null) {
                                bl = new int[S.blossom.length];
                                for (int b = 0; b < bl.length; b++) bl[b] = mix(mix(S.blossom[b], p0.tree, lw), col, haze);
                            }
                            roundTree(x, y, h, dark, light, mid, bl);
                        }
                    } else parasol(x, y, h * .95, dark, light, mid, snowCol);
                    if (trees.size() == before) trees.add(new Tree(x, y, h, i, false, mid));
                }
            }

            // fleurs au premier plan, selon la saison
            if (i == LAYERS.length - 1 && !night && S.flowerCount > 0) {
                for (int k = 0; k < S.flowerCount; k++) {
                    double x = r() * W, y = yAt(L, x, ph) + 25 + r() * 60;
                    double[] pa = pathAt(Math.min(y, PATH_BOTTOM));
                    if (Math.abs(x - pa[0]) < pa[1] + 10) continue;
                    double rad = 2 + r() * 2.5;
                    int fc = mix(S.flowers[(int) Math.floor(r() * S.flowers.length)], p0.tree, lw);
                    flower(x, y, rad * 1.3, fc, mix(col, BLACK, .25), x * 7 + y);
                }
            }
        }
    }

    /** Massifs de fleurs sur une tranche : quelques touffes, chacune de plusieurs fleurs de la même couleur. */
    private void meadow(Layer L, double ph, int i, int col, Season S, int tree, double lw) {
        Rng mr = new Rng(9001 + i * 131 + S.flowerCount);
        double sc = SCALE[i];
        int clusters = S.flowerCount >= 70 ? 9 : 5;
        int stem = mix(col, BLACK, .25);
        for (int k = 0; k < clusters; k++) {
            double cx = mr.r() * W;
            int fc = mix(S.flowers[(int) Math.floor(mr.r() * S.flowers.length)], tree, lw);
            int n = 5 + (int) (mr.r() * 6);
            for (int j = 0; j < n; j++) {
                double x = cx + (mr.r() - .5) * 120 * sc;
                double y = yAt(L, x, ph) + 8 + mr.r() * 26 * sc;
                double[] pa = pathAt(Math.min(Math.max(y, PATH_TOP), PATH_BOTTOM));
                if (y > PATH_TOP - 20 && Math.abs(x - pa[0]) < pa[1] + 14) continue;
                flower(x, y, (4.2 + mr.r() * 2.5) * sc, fc, stem, x * 3 + y * 5);
            }
        }
    }

    /**
     * Fleur dessinée : tige et petite feuille, cinq pétales autour d'un cœur jaune (ou orangé pour
     * les fleurs jaunes). `salt` fait varier l'orientation sans toucher au tirage du paysage.
     */
    private void flower(double x, double y, double rad, int color, int stemCol, double salt) {
        double turn = (Math.sin(salt * 12.9898) * 43758.5453) % 1;
        double stemH = rad * 2.6;
        stroke.setColor(stemCol);
        stroke.setStrokeWidth((float) Math.max(.8, rad * .28));
        stroke.setStrokeCap(Paint.Cap.ROUND);
        cv.drawLine((float) x, (float) (y + stemH), (float) x, (float) y, stroke);
        ellipse(x + rad * .55, y + stemH * .6, rad * .55, rad * .22, fill(stemCol)); // feuille
        Paint pf = fill(color);
        for (int k = 0; k < 5; k++) {
            double a = turn * 6.283 + k * 6.283 / 5;
            cv.save();
            cv.translate((float) (x + Math.cos(a) * rad * .62), (float) (y + Math.sin(a) * rad * .62 * .8));
            cv.rotate((float) Math.toDegrees(a));
            cv.drawOval((float) (-rad * .55), (float) (-rad * .36), (float) (rad * .55), (float) (rad * .36), pf);
            cv.restore();
        }
        boolean yellow = (color >> 16 & 255) > 230 && (color >> 8 & 255) > 200 && (color & 255) < 190;
        circle(x, y, rad * .32, fill(yellow ? c("#e8933a") : c("#f6c94a")));
    }

    private static int indexOf(Palette p) {
        for (int i = 0; i < PALETTES.length; i++) if (PALETTES[i] == p) return i;
        return 1;
    }

    private static double yAt(Layer L, double x, double ph) {
        return L.mtn ? mtnY(x, L, ph) : hillY(x, L.base, L.amp, L.f, ph);
    }

    /* ===== Arbres ===== */

    private void groundShadow(double x, double y, double h, int dark) {
        ellipse(x + h * .06, y, h * .2, h * .035, fill(dark, .28));
    }

    private static void q(Path p, double x1, double y1, double x2, double y2) {
        p.quadTo((float) x1, (float) y1, (float) x2, (float) y2);
    }

    private static void m(Path p, double x, double y) {
        p.moveTo((float) x, (float) y);
    }

    private static void l(Path p, double x, double y) {
        p.lineTo((float) x, (float) y);
    }

    /** Sapin : étages aux bords festonnés qui retombent, face gauche éclairée. */
    private void pine(double x, double y, double h, int dark, int light, Integer snow) {
        double lean = (r() - .5) * h * .04;
        groundShadow(x, y, h, dark);
        cv.drawRect((float) (x - h * .025), (float) (y - h * .16), (float) (x + h * .025), (float) y, fill(dark));
        for (int k = 0; k < 4; k++) {
            double base = y - h * (.12 + k * .19), top = base - h * (.4 - k * .03), w = h * (.31 - k * .06) * (.92 + r() * .16);
            double ax = x + lean * (k + 1), drop = w * .16;
            int n = 4;
            Path dk = new Path(), lt = new Path();
            m(dk, ax, top);
            l(dk, x + w, base);
            m(lt, ax, top);
            l(lt, x, base + drop * .2);
            for (int j = 0; j < n; j++) {
                double x0 = x + w - (2 * w * j) / n, x1 = x + w - (2 * w * (j + 1)) / n;
                double qy = base + drop * 1.6, ey = base + (j < n - 1 ? drop * .2 : 0);
                q(dk, (x0 + x1) / 2, qy, x1, ey);
                if (j >= n / 2) q(lt, (x0 + x1) / 2, qy, x1, ey);
            }
            dk.close();
            lt.close();
            cv.drawPath(dk, fill(dark));
            cv.drawPath(lt, fill(light));
            if (snow != null) { // neige posée sur l'étage
                double my = top + (base - top) * .55;
                Path s = new Path();
                m(s, ax, top);
                l(s, x + w * .5, my);
                q(s, x + w * .2, my + w * .12, x, my + w * .04);
                q(s, x - w * .25, my + w * .14, x - w * .5, my);
                s.close();
                cv.drawPath(s, fill(snow));
            }
        }
    }

    /** Arbre feuillu : couronne irrégulière faite de touffes, lumière en haut à gauche. */
    private void roundTree(double x, double y, double h, int dark, int light, int mid, int[] blossom) {
        double R = h * .3, cx = x + (r() - .5) * h * .04, cy = y - h * .64;
        double[][] lumps = new double[8][];
        for (int k = 0; k < 7; k++) {
            double a = (k / 7.0) * 6.283 + r() * .5;
            lumps[k] = new double[]{cx + Math.cos(a) * R * .55, cy + Math.sin(a) * R * .45, R * (.48 + r() * .14), a};
        }
        lumps[7] = new double[]{cx, cy, R * .62, 0};
        groundShadow(x, y, h, dark);
        // tronc qui se divise en deux branches
        Path tr = new Path();
        m(tr, x - h * .03, y);
        l(tr, x - h * .018, y - h * .4);
        l(tr, x - h * .1, cy + R * .1);
        l(tr, x - h * .08, cy + R * .2);
        l(tr, x, y - h * .44);
        l(tr, x + h * .07, cy + R * .25);
        l(tr, x + h * .09, cy + R * .18);
        l(tr, x + h * .018, y - h * .4);
        l(tr, x + h * .03, y);
        tr.close();
        cv.drawPath(tr, fill(dark));
        fill(mid);
        for (double[] lp : lumps) circle(lp[0], lp[1], lp[2], fill);
        fill(dark);
        for (double[] lp : lumps) // ombre côté bas-droite
            if (Math.cos(lp[3] - .8) > .35) circle(lp[0] + lp[2] * .22, lp[1] + lp[2] * .22, lp[2] * .72, fill);
        fill(light);
        for (double[] lp : lumps) // lumière côté haut-gauche
            if (Math.cos(lp[3] - 3.93) > .3) circle(lp[0] - lp[2] * .18, lp[1] - lp[2] * .2, lp[2] * .62, fill);
        if (blossom != null) for (int k = 0; k < 12; k++) { // fleurs du printemps dans la couronne
            double a = r() * 6.283, dd = Math.sqrt(r()) * R * .85;
            double rad = R * (.06 + r() * .05);
            int col = blossom[(int) Math.floor(r() * blossom.length)];
            // une sur trois seulement (les tirages restent les mêmes : le reste du paysage ne bouge pas)
            if (k % 3 == 0) circle(cx + Math.cos(a) * dd, cy + Math.sin(a) * dd * .85, rad, fill(col));
        }
    }

    /** Pin parasol : tronc qui se ramifie, couronne en dôme faite de touffes aplaties. */
    private void parasol(double x, double y, double h, int dark, int light, int mid, Integer snow) {
        double ty = y - h * .78, bend = h * (.05 + r() * .06) * (r() < .5 ? -1 : 1), tx = x + bend;
        groundShadow(x, y, h * 1.3, dark);
        Path tr = new Path();
        m(tr, x - h * .022, y);
        q(tr, x - bend * .3, y - h * .45, tx - h * .012, ty + h * .05);
        l(tr, tx - h * .16, ty);
        l(tr, tx - h * .15, ty - h * .015);
        l(tr, tx, ty + h * .02);
        l(tr, tx + h * .15, ty - h * .02);
        l(tr, tx + h * .16, ty);
        l(tr, tx + h * .012, ty + h * .06);
        q(tr, x - bend * .3 + h * .04, y - h * .45, x + h * .022, y);
        tr.close();
        cv.drawPath(tr, fill(dark));
        double[][] lumps = new double[6][];
        for (int k = 0; k < 6; k++) {
            double u = k / 5.0 - .5;
            double lx = tx + u * h * .62 + (r() - .5) * h * .04, ly = ty - h * .05 - (1 - 4 * u * u) * h * .07;
            double rx = h * (.15 + r() * .04), ry = h * (.07 + r() * .02);
            lumps[k] = new double[]{lx, ly, rx, ry, u};
        }
        fill(mid);
        for (double[] lp : lumps) ellipse(lp[0], lp[1], lp[2], lp[3], fill);
        fill(dark);
        for (double[] lp : lumps) ellipse(lp[0] + lp[2] * .12, lp[1] + lp[3] * .38, lp[2] * .8, lp[3] * .55, fill);
        fill(light);
        for (double[] lp : lumps) if (lp[4] <= .1) ellipse(lp[0] - lp[2] * .15, lp[1] - lp[3] * .3, lp[2] * .6, lp[3] * .5, fill);
        if (snow != null) {
            fill(snow);
            for (double[] lp : lumps) ellipse(lp[0], lp[1] - lp[3] * .5, lp[2] * .78, lp[3] * .42, fill);
        }
    }

    /** Arbre nu de l'hiver : branches qui se divisent. */
    private void bareTree(double x, double y, double h, int col, Integer snow) {
        List<List<double[]>> segs = new ArrayList<>();
        for (int k = 0; k < 4; k++) segs.add(new ArrayList<>());
        branch(segs, x, y, h * .42, -Math.PI / 2 + (r() - .5) * .12, 3);
        groundShadow(x, y, h, col);
        stroke.setColor(col);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        for (int k = 0; k < 4; k++) {
            if (segs.get(k).isEmpty()) continue;
            stroke.setStrokeWidth((float) (h * .05 * Math.pow(.6, k)));
            Path p = new Path();
            for (double[] sg : segs.get(k)) {
                m(p, sg[0], sg[1]);
                l(p, sg[2], sg[3]);
            }
            cv.drawPath(p, stroke);
        }
        if (snow != null) ellipse(x, y, h * .16, h * .03, fill(snow));
    }

    private void branch(List<List<double[]>> segs, double x0, double y0, double len, double ang, int depth) {
        double x1 = x0 + Math.cos(ang) * len, y1 = y0 + Math.sin(ang) * len;
        segs.get(3 - depth).add(new double[]{x0, y0, x1, y1});
        if (depth == 0) return;
        branch(segs, x1, y1, len * .7, ang - .42 - r() * .3, depth - 1);
        branch(segs, x1, y1, len * .7, ang + .38 + r() * .3, depth - 1);
        if (r() < .45) branch(segs, x1, y1, len * .6, ang + (r() - .5) * .3, depth - 1);
    }

    /* ===== Grain du papier et vignettage léger (calque #grain de la version web, opacité .3) ===== */

    private static Bitmap GRAIN;

    private static synchronized Bitmap grainTile() {
        if (GRAIN != null) return GRAIN;
        int n = 220;
        int[] px = new int[n * n];
        Random rnd = new Random(7);
        float[] v = new float[n * n];
        for (int i = 0; i < v.length; i++) v[i] = rnd.nextFloat();
        for (int y = 0; y < n; y++) // léger lissage : bruit fin, pas des pixels isolés
            for (int x = 0; x < n; x++) {
                float s = v[y * n + x] * 2 + v[y * n + (x + 1) % n] + v[((y + 1) % n) * n + x];
                // bruit fractal de la version web : peu contrasté autour de sa moyenne (≈ 8 % d'opacité)
                int a = Math.round((.08f + (s / 4 - .5f) * .05f) * 255);
                px[y * n + x] = a << 24 | 0x80 << 16 | 0x73 << 8 | 0x66; // rgb(.5, .45, .4)
            }
        GRAIN = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
        return GRAIN;
    }

    private static void grain(Canvas c) {
        Paint p = new Paint();
        p.setShader(new android.graphics.BitmapShader(grainTile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        c.drawRect(0, 0, W, H, p);
        // radial-gradient(ellipse 75% 70% at 50% 45%, transparent 60%, rgba(20,30,40,.16)) × .3
        RadialGradient g = new RadialGradient(0, 0, 1, new int[]{0, 0, 0x0C141E28}, new float[]{0, .6f, 1}, Shader.TileMode.CLAMP);
        android.graphics.Matrix mx = new android.graphics.Matrix();
        mx.setScale(.75f * W, .7f * H); // rayons de l'ellipse : 75 % de la largeur, 70 % de la hauteur
        mx.postTranslate(W / 2f, .45f * H);
        g.setLocalMatrix(mx);
        p.setShader(g);
        c.drawRect(0, 0, W, H, p);
    }

    /* ===== Nuages de la météo (calque #clouds, graine 4242) ===== */

    static Bitmap weatherClouds(int width, int height) {
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        float s = Math.max(width / (float) W, height / (float) H);
        cv.translate((width - W * s) / 2, (height - H * s) / 2);
        cv.scale(s, s);
        Rng r = new Rng(4242);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < 9; i++) {
            double cx = r.r() * 2100 - 90, cy = 60 + r.r() * 380, w = 320 + r.r() * 420;
            int a = (int) Math.round((.65 + r.r() * .3) * 255);
            int lay = cv.saveLayerAlpha(null, a);
            p.setColor(0xFFDFE5EA);
            cv.drawOval(new RectF((float) (cx - w / 2), (float) (cy + w * .04 - w / 8), (float) (cx + w / 2), (float) (cy + w * .04 + w / 8)), p);
            p.setColor(WHITE);
            cv.drawOval(new RectF((float) (cx - w / 2.1), (float) (cy - w / 9), (float) (cx + w / 2.1), (float) (cy + w / 9)), p);
            double ex = cx - w * .14, ey = cy - w * .07;
            cv.drawOval(new RectF((float) (ex - w / 4.2), (float) (ey - w / 7.5), (float) (ex + w / 4.2), (float) (ey + w / 7.5)), p);
            ex = cx + w * .12;
            ey = cy - w * .05;
            cv.drawOval(new RectF((float) (ex - w / 4.8), (float) (ey - w / 9), (float) (ex + w / 4.8), (float) (ey + w / 9)), p);
            cv.restoreToCount(lay);
        }
        return out;
    }

    /* ===== Moment de la journée : 0 aube, 1 jour, 2 crépuscule, 3 nuit ===== */

    static int sceneForTime(java.util.Calendar c) {
        int mm = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
        double[] st = SceneView.sunTimes(c);
        if (mm < st[0] - 40 || mm >= st[1] + 40) return 3;
        if (mm < st[0] + 80) return 0;
        if (mm >= st[1] - 80) return 2;
        return 1;
    }
}
