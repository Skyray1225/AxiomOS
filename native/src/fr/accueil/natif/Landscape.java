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
    static final int VERSION = 16; // à augmenter quand le dessin change : les images en cache sont refaites
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
            new Season("halloween", new String[]{"#e0dcae", "#cdc58b", "#ab9f66", "#83814e", "#5c623c"}, "#3a6248", "#ead2a5",
                    null, null, false, null, null, new String[]{"#d9893b", "#c8642f", "#e2b04a", "#b5552a", "#a8953f", "#cf7b35"}, 0, .38, .85),
            new Season("hiver", new String[]{"#f1f4f7", "#e5ebf0", "#d7e0e8", "#c6d2dc", "#b3c2cf"}, "#2d4c45", "#e9eef2",
                    "#e3eaf0", "#5d4f48", true, null, null, null, 0, .45, .85),
            new Season("noel", new String[]{"#f1f4f7", "#e5ebf0", "#d7e0e8", "#c6d2dc", "#b3c2cf"}, "#2d4c45", "#e9eef2",
                    "#e3eaf0", "#5d4f48", true, null, null, null, 0, .45, .85),
    };

    /** Décor de Noël : l'hiver, sucres d'orge, cadeaux, maison en pain d'épices, sapins décorés. */
    static boolean noel(Season s) {
        return s != null && "noel".equals(s.id);
    }

    /** Décor d'Halloween : couleurs de l'automne, citrouilles, tombes, hutte de sorcière. */
    static boolean halloween(Season s) {
        return s != null && "halloween".equals(s.id);
    }

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
    private final double[] phases = new double[7]; // phase de la crête de chaque tranche
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
            phases[i] = ph;
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
            boolean lit = night || indexOf(p0) == 2; // citrouilles et fenêtre allumées au crépuscule et la nuit

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
                    // Noël : certains sapins des tranches proches portent guirlande et boules (choix sans tirage)
                    boolean deco = noel(S) && i >= 3 && frac(Math.sin(x * 12.9898 + y * 78.233) * 43758.5453) < .7;
                    if (kind < S.mix0) pine(x, y, h, dark, light, snowCol, deco, lit, i >= 5);
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

            // Halloween : hutte de sorcière au second plan, citrouilles près du chemin
            if (halloween(S)) {
                if (i == 2) hut(L, ph, col, p0, lit, .45 * (1 - t));
                if (i == 3 || i == 4) pumpkins(L, ph, i, col, lit);
                if (i == 4) graves(LAYERS[3], phases[3], L, ph, col, p0, lw, lit); // le cimetière, bien visible
            }

            // Noël : maison en pain d'épices au second plan, sucres d'orge et cadeaux bien visibles
            if (noel(S) && (i == 2 || i == 4 || i == 6)) {
                // dessinés dans un calque puis teintés par la lumière du moment (sinon trop vifs la nuit)
                int lay = cv.saveLayer(0, 0, W, H, null);
                if (i == 2) gingerbread(L, ph, col, p0, lit, .45 * (1 - t));
                else if (i == 4) christmas(LAYERS[3], phases[3], L, ph, col, lit);
                else christmasFront(LAYERS[5], phases[5], L, ph, lit);
                double tint = new double[]{.12, 0, .16, .4}[indexOf(p0)];
                if (tint > 0) {
                    Paint tp = new Paint();
                    tp.setColor(alpha(p0.night ? p0.hills[2] : p0.sky[0], tint));
                    tp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
                    cv.drawRect(0, 0, W, H, tp);
                }
                cv.restoreToCount(lay);
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

    /* ===== Halloween ===== */

    /** Citrouilles sculptées posées sur la tranche, de part et d'autre du chemin et sur les côtés. */
    private void pumpkins(Layer L, double ph, int i, int col, boolean lit) {
        double sc = SCALE[i];
        double[][] spots = i == 3
                ? new double[][]{{-1, .5}, {1, .2}, {0, 0}, {0, 0}}
                : new double[][]{{-1, .2}, {1, .6}, {0, 0}, {0, 0}, {0, 0}};
        Rng pr = new Rng(666 + i);
        for (double[] sp : spots) {
            double x, y;
            if (sp[0] != 0) { // au bord du chemin
                double yy = yAt(L, 960, ph) + 14 + sp[1] * 20 * sc;
                double[] pa = pathAt(Math.min(Math.max(yy, PATH_TOP), PATH_BOTTOM));
                x = pa[0] + sp[0] * (pa[1] + 26 * sc);
                y = yAt(L, x, ph) + 10 + sp[1] * 20 * sc;
            } else { // ailleurs dans le pré
                x = 120 + pr.r() * 1680;
                y = yAt(L, x, ph) + 8 + pr.r() * 16 * sc;
                double[] pa = pathAt(Math.min(Math.max(y, PATH_TOP), PATH_BOTTOM));
                if (Math.abs(x - pa[0]) < pa[1] + 40) x += 260;
            }
            // formes variées : rondes ou aplaties, plus ou moins côtelées
            pumpkin(x, y, (17 + pr.r() * 7) * sc * 1.6, .62 + pr.r() * .4, pr.r() < .5 ? 2 : 3, col, lit, pr.r() < .5);
        }
    }

    /** Citrouille : côtes orange, tige, visage sculpté (yeux en triangle, bouche dentée), lueur la nuit. */
    private void pumpkin(double x, double y, double r, double round, int ribs, int col, boolean lit, boolean grin) {
        double h = r * round * 1.05; // round : .62 aplatie … 1 bien ronde
        if (lit) { // lueur orangée autour de la citrouille
            fill.setShader(new RadialGradient((float) x, (float) (y - h * .5), (float) (r * 3.2), 0x66FF9A2E, 0x00FF9A2E, Shader.TileMode.CLAMP));
            circle(x, y - h * .5, r * 3.2, fill);
        }
        ellipse(x + r * .1, y, r * 1.15, r * .22, fill(mix(col, BLACK, .35), .35)); // ombre au sol
        int base = c("#e2761f"), dark = c("#b4521a"), light = c("#f39a3c");
        if (!lit) { base = mix(base, col, .12); dark = mix(dark, col, .12); light = mix(light, col, .12); }
        double cy = y - h * .52;
        // côtes : de l'extérieur (plus sombres) vers le centre
        for (int k = ribs; k >= 1; k--) {
            double off = r * .5 * k / ribs * (ribs == 3 ? 1.05 : 1), rw = r * (ribs == 3 ? .42 : .55);
            int cc = k == ribs ? dark : mix(dark, base, .55);
            ellipse(x - off, cy, rw, h * .5, fill(cc));
            ellipse(x + off, cy, rw, h * .5, fill(cc));
        }
        ellipse(x, cy, r * (ribs == 3 ? .36 : .5), h * .52, fill(base));
        ellipse(x - r * .3, cy - h * .12, r * .18, h * .28, fill(light, .55)); // reflet
        // tige
        Path st = new Path();
        m(st, x - r * .07, cy - h * .45);
        q(st, x - r * .05, cy - h * .78, x + r * .14, cy - h * .86);
        l(st, x + r * .18, cy - h * .74);
        q(st, x + r * .06, cy - h * .7, x + r * .08, cy - h * .45);
        st.close();
        cv.drawPath(st, fill(c("#5b6b2e")));
        // visage
        int face = lit ? c("#ffd75a") : c("#4a2410");
        Path f = new Path();
        m(f, x - r * .42, cy - h * .02); l(f, x - r * .27, cy - h * .3); l(f, x - r * .12, cy - h * .02); f.close();
        m(f, x + r * .12, cy - h * .02); l(f, x + r * .27, cy - h * .3); l(f, x + r * .42, cy - h * .02); f.close();
        m(f, x - r * .07, cy + h * .06); l(f, x, cy - h * .08); l(f, x + r * .07, cy + h * .06); f.close();
        // bouche : arc denté
        m(f, x - r * .5, cy + h * .14);
        q(f, x, cy + h * (grin ? .62 : .48), x + r * .5, cy + h * .14);
        l(f, x + r * .3, cy + h * .22);
        l(f, x + r * .2, cy + h * .13);
        l(f, x + r * .08, cy + h * .24);
        l(f, x - r * .06, cy + h * .14);
        l(f, x - r * .18, cy + h * .25);
        l(f, x - r * .3, cy + h * .15);
        f.close();
        cv.drawPath(f, fill(face));
    }

    /**
     * Petit cimetière au premier plan (tranche juste au-dessus du dock), là où la crête est la plus haute
     * pour être bien visible : stèles arrondies et croix un peu penchées, deux bougies allumées.
     */
    private void graves(Layer L, double ph, Layer front, double phFront, int col, Palette p0, double lw, boolean lit) {
        Rng gr = new Rng(1313);
        double sc = SCALE[4];
        // posé sur la crête au-dessus du dock, là où la colline de devant ne le cache pas
        // (et loin du chemin et de la hutte) : les tombes et les bougies restent entières
        double best = 300, bestGap = -1e9;
        for (double x0 = 140; x0 <= 1560; x0 += 20) {
            if (x0 + 230 > 800 && x0 < 1130) continue;   // chemin
            if (x0 + 230 > 1340 && x0 < 1620) continue;  // hutte
            double gap = 1e9;
            for (int k = 0; k <= 5; k++) {
                double x = x0 + k * 46, b = yAt(L, x, ph) + 10;
                gap = Math.min(gap, Math.min(yAt(front, x, phFront) - b, 778 - b));
            }
            if (gap > bestGap) { bestGap = gap; best = x0; }
        }
        int stone = mix(mix(c("#a3a7ae"), p0.tree, lw * .7), col, .1);
        int shade = mix(stone, BLACK, .3), light = mix(stone, WHITE, .3), mound = mix(col, BLACK, .2);
        int n = 5;
        double[] tx = new double[n], ty = new double[n], th = new double[n];
        for (int k = 0; k < n; k++) {
            double x = best + k * 52 + (gr.r() - .5) * 16, y = yAt(L, x, ph) + 6 + gr.r() * 8;
            double h = (50 + gr.r() * 22) * sc * 1.25, w = h * .6;
            tx[k] = x; ty[k] = y; th[k] = h;
            double tilt = (gr.r() - .5) * 14;
            ellipse(x, y, w * .95, h * .1, fill(mound));
            cv.save();
            cv.rotate((float) tilt, (float) x, (float) y);
            if (k % 3 == 1) { // croix
                double bw = w * .26;
                cv.drawRect((float) (x - bw / 2), (float) (y - h), (float) (x + bw / 2), (float) y, fill(stone));
                cv.drawRect((float) (x - w * .45), (float) (y - h * .78), (float) (x + w * .45), (float) (y - h * .78 + bw), fill(stone));
                cv.drawRect((float) (x + bw * .1), (float) (y - h), (float) (x + bw / 2), (float) y, fill(shade));
                cv.drawRect((float) (x + bw * .1), (float) (y - h * .78), (float) (x + w * .45), (float) (y - h * .78 + bw), fill(shade));
            } else { // stèle arrondie
                RectF top = new RectF((float) (x - w / 2), (float) (y - h), (float) (x + w / 2), (float) (y - h + w));
                Path p = new Path();
                m(p, x - w / 2, y);
                l(p, x - w / 2, y - h + w / 2);
                p.arcTo(top, 180, 180);
                l(p, x + w / 2, y);
                p.close();
                cv.drawPath(p, fill(stone));
                Path sd = new Path(); // face à l'ombre
                m(sd, x + w * .22, y);
                l(sd, x + w * .22, y - h + w * .1);
                sd.arcTo(top, 300, 60);
                l(sd, x + w / 2, y);
                sd.close();
                cv.drawPath(sd, fill(shade));
                cv.drawRect((float) (x - w * .3), (float) (y - h * .6), (float) (x + w * .12), (float) (y - h * .6 + h * .05), fill(shade, .75));
                cv.drawRect((float) (x - w * .3), (float) (y - h * .47), (float) (x + w * .04), (float) (y - h * .47 + h * .05), fill(shade, .75));
                cv.drawRect((float) (x - w * .46), (float) (y - h + w * .5), (float) (x - w * .38), (float) (y - h * .1), fill(light, .5));
            }
            cv.restore();
        }
        // deux bougies au pied des tombes
        candle(tx[0] + th[0] * .45, ty[0] + 2, th[0] * .32, lit);
        candle(tx[3] - th[3] * .48, ty[3] + 3, th[3] * .26, lit);
    }

    /** Bougie : cire crème, mèche, flamme en goutte et halo (plus fort le soir et la nuit). */
    private void candle(double x, double y, double h, boolean lit) {
        double w = h * .38;
        fill.setShader(new RadialGradient((float) x, (float) (y - h * 1.2), (float) (h * (lit ? 3 : 1.6)),
                lit ? 0x80FFC45A : 0x40FFD27A, 0x00FFC45A, Shader.TileMode.CLAMP));
        circle(x, y - h * 1.2, h * (lit ? 3 : 1.6), fill);
        cv.drawRect((float) (x - w / 2), (float) (y - h), (float) (x + w / 2), (float) y, fill(c("#efe6cf")));
        cv.drawRect((float) (x + w * .1), (float) (y - h), (float) (x + w / 2), (float) y, fill(c("#cfc3a6")));
        ellipse(x, y - h, w / 2, w * .18, fill(c("#f7f0de")));
        stroke.setColor(c("#3a2a1a"));
        stroke.setStrokeWidth((float) Math.max(1, w * .12));
        cv.drawLine((float) x, (float) (y - h), (float) x, (float) (y - h - w * .3), stroke);
        Path f = new Path(); // flamme
        m(f, x, y - h - w * 1.6);
        q(f, x + w * .45, y - h - w * .7, x, y - h - w * .25);
        q(f, x - w * .45, y - h - w * .7, x, y - h - w * 1.6);
        f.close();
        cv.drawPath(f, fill(c("#ffb43a")));
        ellipse(x, y - h - w * .55, w * .14, w * .26, fill(c("#fff4c2")));
    }

    /** Hutte de sorcière : murs de planches de travers, toit pointu tordu, cheminée qui fume, fenêtre allumée. */
    private void hut(Layer L, double ph, int col, Palette p0, boolean lit, double haze) {
        double x = 1470, y = yAt(L, x, ph) + 6, s = 78;
        int wood = mix(mix(c("#6b4a33"), p0.tree, .35), col, haze), woodD = mix(wood, BLACK, .3);
        int roof = mix(mix(c("#4b3a52"), p0.tree, .3), col, haze), roofL = mix(roof, p0.halo, .2);
        ellipse(x + s * .05, y, s * .62, s * .07, fill(mix(col, BLACK, .3), .35));
        // cheminée et fumée
        cv.drawRect((float) (x + s * .14), (float) (y - s * 1.22), (float) (x + s * .27), (float) (y - s * .8), fill(woodD));
        int smoke = mix(p0.sky[1], WHITE, .3);
        double[][] puffs = {{.22, 1.34, .08}, {.27, 1.46, .1}, {.35, 1.6, .12}, {.46, 1.74, .13}};
        for (double[] pf : puffs) circle(x + s * pf[0], y - s * pf[1], s * pf[2], fill(smoke, .45));
        // murs penchés
        Path w = new Path();
        m(w, x - s * .4, y); l(w, x - s * .36, y - s * .55); l(w, x + s * .38, y - s * .6); l(w, x + s * .42, y); w.close();
        cv.drawPath(w, fill(wood));
        stroke.setColor(woodD);
        stroke.setStrokeWidth((float) (s * .02));
        stroke.setStrokeCap(Paint.Cap.ROUND);
        for (int k = 1; k < 4; k++) {
            double yy = y - s * .14 * k;
            cv.drawLine((float) (x - s * .39 + k * .01 * s), (float) yy, (float) (x + s * .41 - k * .01 * s), (float) (yy - s * .02 * k), stroke);
        }
        // porte arrondie et fenêtre
        Path d = new Path();
        m(d, x - s * .22, y); l(d, x - s * .22, y - s * .3);
        d.arcTo(new RectF((float) (x - s * .22), (float) (y - s * .4), (float) (x - s * .04), (float) (y - s * .22)), 180, 180);
        l(d, x - s * .04, y); d.close();
        cv.drawPath(d, fill(mix(woodD, BLACK, .3)));
        if (lit) {
            fill.setShader(new RadialGradient((float) (x + s * .2), (float) (y - s * .32), (float) (s * .45), 0x66FFC24A, 0x00FFC24A, Shader.TileMode.CLAMP));
            circle(x + s * .2, y - s * .32, s * .45, fill);
        }
        cv.drawRect((float) (x + s * .1), (float) (y - s * .42), (float) (x + s * .3), (float) (y - s * .24), fill(lit ? c("#ffcf5a") : mix(woodD, BLACK, .35)));
        stroke.setColor(woodD);
        cv.drawLine((float) (x + s * .2), (float) (y - s * .42), (float) (x + s * .2), (float) (y - s * .24), stroke);
        cv.drawLine((float) (x + s * .1), (float) (y - s * .33), (float) (x + s * .3), (float) (y - s * .33), stroke);
        // toit pointu, la pointe tordue
        Path r = new Path();
        m(r, x - s * .58, y - s * .5);
        q(r, x - s * .1, y - s * .78, x - s * .02, y - s * 1.25);
        q(r, x + s * .05, y - s * 1.5, x + s * .3, y - s * 1.52);
        q(r, x + s * .12, y - s * 1.36, x + s * .14, y - s * 1.1);
        q(r, x + s * .26, y - s * .78, x + s * .6, y - s * .56);
        q(r, x, y - s * .64, x - s * .58, y - s * .5);
        r.close();
        cv.drawPath(r, fill(roof));
        Path rl = new Path(); // côté éclairé
        m(rl, x - s * .58, y - s * .5);
        q(rl, x - s * .1, y - s * .78, x - s * .02, y - s * 1.25);
        q(rl, x - s * .02, y - s * .8, x - s * .1, y - s * .58);
        rl.close();
        cv.drawPath(rl, fill(roofL));
        // bord de chaume
        stroke.setColor(mix(roof, BLACK, .25));
        stroke.setStrokeWidth((float) (s * .025));
        for (int k = 0; k < 9; k++) {
            double xx = x - s * .52 + k * s * .13;
            cv.drawLine((float) xx, (float) (y - s * .55), (float) (xx - s * .03), (float) (y - s * .47), stroke);
        }
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
    private static double frac(double v) {
        return v - Math.floor(v);
    }

    private void pine(double x, double y, double h, int dark, int light, Integer snow) {
        pine(x, y, h, dark, light, snow, false, false, false);
    }

    private void pine(double x, double y, double h, int dark, int light, Integer snow, boolean deco, boolean lit, boolean star) {
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
            if (deco) garland(x, top, base, w, h, k, lit);
        }
        if (deco && star) { // étoile dorée à la pointe des grands sapins
            double sy = y - h * (.12 + 3 * .19) - h * (.4 - 3 * .03), sr = h * .045;
            if (lit) {
                fill.setShader(new RadialGradient((float) x, (float) sy, (float) (sr * 4), 0x80FFE08A, 0x00FFE08A, Shader.TileMode.CLAMP));
                circle(x, sy, sr * 4, fill);
            }
            Path st = new Path();
            for (int k = 0; k < 10; k++) {
                double a = -Math.PI / 2 + k * Math.PI / 5, rr = k % 2 == 0 ? sr : sr * .45;
                if (k == 0) m(st, x + Math.cos(a) * rr, sy + Math.sin(a) * rr);
                else l(st, x + Math.cos(a) * rr, sy + Math.sin(a) * rr);
            }
            st.close();
            cv.drawPath(st, fill(c("#ffd24a")));
        }
    }

    private static final int[] BAUBLES = {0xFFD8343A, 0xFFF2C14E, 0xFF3F7FD8, 0xFFE8E8F0, 0xFFC0399A};

    /** Guirlande dorée en arc sur un étage du sapin, avec des boules (allumées la nuit). */
    private void garland(double x, double top, double base, double w, double h, int k, boolean lit) {
        double f = .72, yg = top + (base - top) * f, half = w * f * .92;
        Path g = new Path();
        m(g, x - half, yg - h * .01);
        q(g, x, yg + w * .32, x + half, yg - h * .03);
        stroke.setColor(lit ? c("#ffd772") : c("#e3b341"));
        stroke.setStrokeWidth((float) Math.max(1, h * .014));
        stroke.setStrokeCap(Paint.Cap.ROUND);
        cv.drawPath(g, stroke);
        double br = Math.max(1.4, h * .022);
        for (int j = 0; j < 3; j++) {
            double u = .2 + j * .3, bx = x - half + 2 * half * u;
            double by = (1 - u) * (1 - u) * (yg - h * .01) + 2 * (1 - u) * u * (yg + w * .32) + u * u * (yg - h * .03) + br * .8;
            int col = BAUBLES[Math.floorMod(k * 3 + j + (int) Math.floor(x * 7), BAUBLES.length)]; // x peut être négatif (bord gauche)
            if (lit) {
                fill.setShader(new RadialGradient((float) bx, (float) by, (float) (br * 3.2), (col & 0x00FFFFFF) | 0x80000000, col & 0x00FFFFFF, Shader.TileMode.CLAMP));
                circle(bx, by, br * 3.2, fill);
            }
            circle(bx, by, br, fill(lit ? mix(col, WHITE, .25) : col));
            circle(bx - br * .3, by - br * .3, br * .35, fill(WHITE, .7));
        }
    }

    /* ===== Noël ===== */

    /**
     * Noël au premier plan, sur la crête au-dessus du dock, là où la colline de devant ne cache rien
     * (comme le cimetière d'Halloween) : grand sapin décoré et ses cadeaux près du chemin, sucres d'orge
     * à l'entrée du chemin, bonshommes de neige, piles de cadeaux et sucres d'orge un peu partout.
     */
    private void christmas(Layer L, double ph, Layer front, double phFront, int col, boolean lit) {
        Rng cr = new Rng(2512);
        // emplacements visibles en entier, espacés, loin du chemin et de la maison
        List<Double> spots = new ArrayList<>();
        for (double x = 110; x <= 1810; x += 6) {
            if (Math.abs(x - 960) < 150 || (x > 1330 && x < 1640)) continue;
            if (!visible(L, ph, front, phFront, x)) continue;
            boolean close = false;
            for (double o : spots) if (Math.abs(o - x) < 105) close = true;
            if (!close) spots.add(x);
        }
        double sc = 1.15;
        // sucres d'orge de part et d'autre du chemin, là où il passe sur cette crête
        double py = yAt(L, 960, ph) + 10;
        double[] pa = pathAt(Math.min(Math.max(py, PATH_TOP), PATH_BOTTOM));
        for (int side = -1; side <= 1; side += 2) {
            double x = pa[0] + side * (pa[1] + 26);
            if (visible(L, ph, front, phFront, x)) candyCane(x, yAt(L, x, ph) + 10, 62 * sc, side > 0);
        }
        // le grand sapin : l'emplacement le plus proche du chemin, côté gauche si possible
        int tree = -1;
        for (int k = 0; k < spots.size(); k++)
            if (spots.get(k) < 960 && (tree < 0 || spots.get(k) > spots.get(tree))) tree = k;
        int[][] gifts = {{0xFFD23B3B, 0xFFF2C14E}, {0xFF2F8A4A, 0xFFD23B3B}, {0xFF3A6CCF, 0xFFF4F4F4},
                {0xFFE0B040, 0xFFC0302F}, {0xFF8E44AD, 0xFFF2C14E}, {0xFFF4F4F4, 0xFFD23B3B}};
        int g = 0, snowmen = 0;
        for (int k = 0; k < spots.size(); k++) {
            double x = spots.get(k), y = yAt(L, x, ph) + 10;
            if (k == tree) {
                christmasTree(x, y, 165, lit);
                for (int j = 0; j < 4; j++) { // cadeaux au pied
                    int[] gc = gifts[g++ % gifts.length];
                    double bw = (22 + cr.r() * 12) * sc;
                    gift(x - 52 + j * 34 + (j % 2) * 4, y + 4 + (j % 2) * 3, bw, bw * (.7 + cr.r() * .4), gc[0], gc[1]);
                }
                continue;
            }
            int kind = (k + 1) % 4;
            if (kind == 0 && snowmen < 2) {
                snowman(x, y, (58 + cr.r() * 10) * sc);
                snowmen++;
                candyCane(x + 30 * sc, y + 2, 40 * sc, false);
            } else if (kind == 1) { // deux sucres d'orge croisés
                candyCane(x - 8, y, (56 + cr.r() * 10) * sc, true);
                candyCane(x + 12, y + 3, (44 + cr.r() * 8) * sc, false);
            } else { // une pile de cadeaux
                int n = 2 + (int) (cr.r() * 3);
                double cx = x - (n - 1) * 15 * sc;
                for (int j = 0; j < n; j++) {
                    int[] gc = gifts[g++ % gifts.length];
                    double bw = (22 + cr.r() * 16) * sc, bh = bw * (.65 + cr.r() * .5);
                    gift(cx + j * 30 * sc, y + (j % 2) * 3, bw, bh, gc[0], gc[1]);
                }
                if (n >= 3) { // un petit cadeau posé sur les autres
                    int[] gc = gifts[g++ % gifts.length];
                    gift(cx + 15 * sc, y - 26 * sc, 18 * sc, 14 * sc, gc[0], gc[1]);
                }
            }
        }
    }

    /**
     * Premier plan de Noël (visible surtout en veille, quand le dock disparaît) : sur l'avant-dernière
     * tranche, grand sapin, bonshommes de neige, sucres d'orge et cadeaux ; tout devant, de chaque côté
     * du chemin, de très grands sucres d'orge et des piles de cadeaux.
     */
    private void christmasFront(Layer L5, double ph5, Layer L6, double ph6, boolean lit) {
        Rng cr = new Rng(2412);
        int[][] gifts = {{0xFFD23B3B, 0xFFF2C14E}, {0xFF2F8A4A, 0xFFD23B3B}, {0xFF3A6CCF, 0xFFF4F4F4},
                {0xFFE0B040, 0xFFC0302F}, {0xFF8E44AD, 0xFFF2C14E}, {0xFFF4F4F4, 0xFFD23B3B}};
        int g = 0;
        // tranche 5 : emplacements entiers (non cachés par la tranche 6), loin du chemin
        List<Double> spots = new ArrayList<>();
        for (double x = 150; x <= 1770; x += 8) {
            double y = yAt(L5, x, ph5) + 10;
            double[] pa = pathAt(Math.min(Math.max(y, PATH_TOP), PATH_BOTTOM));
            if (Math.abs(x - pa[0]) < pa[1] + 90) continue;
            boolean ok = true;
            for (int d = -40; d <= 40; d += 40) {
                double b = yAt(L5, x + d, ph5) + 10;
                if (yAt(L6, x + d, ph6) - b < 4) ok = false;
            }
            if (!ok) continue;
            boolean close = false;
            for (double o : spots) if (Math.abs(o - x) < 230) close = true;
            if (!close) spots.add(x);
        }
        int[] plan = {0, 1, 2, 3, 1, 2, 3, 1};  // 0 grand sapin, 1 cadeaux, 2 bonhomme, 3 sucres d'orge
        int snowmen = 0;
        for (int k = 0; k < spots.size(); k++) {
            double x = spots.get(k), y = yAt(L5, x, ph5) + 12;
            int kind = plan[k % plan.length];
            if (kind == 2 && snowmen >= 2) kind = 3;
            switch (kind) {
                case 0:
                    christmasTree(x, y, 250, lit);
                    for (int j = 0; j < 4; j++) {
                        int[] gc = gifts[g++ % gifts.length];
                        double bw = 34 + cr.r() * 18;
                        gift(x - 80 + j * 52 + (j % 2) * 6, y + 5 + (j % 2) * 4, bw, bw * (.7 + cr.r() * .4), gc[0], gc[1]);
                    }
                    break;
                case 1:
                    giftPile(x, y, 3 + (int) (cr.r() * 2), 38, gifts, g, cr);
                    g += 5;
                    break;
                case 2:
                    snowman(x, y, 125 + cr.r() * 20);
                    snowmen++;
                    break;
                default:
                    candyCane(x - 14, y, 100 + cr.r() * 16, true);
                    candyCane(x + 20, y + 4, 78 + cr.r() * 12, false);
            }
        }
        // tout devant : de part et d'autre du chemin, et aux deux bords de l'écran
        double[] xs = {130, 470, 1450, 1790};
        for (int k = 0; k < xs.length; k++) {
            double x = xs[k], y = yAt(L6, x, ph6) + 30;
            double[] pa = pathAt(Math.min(Math.max(y, PATH_TOP), PATH_BOTTOM));
            if (Math.abs(x - pa[0]) < pa[1] + 110) x += x < pa[0] ? -120 : 120;
            if (k % 2 == 0) {
                candyCane(x - 20, y, 175, true);
                candyCane(x + 34, y + 6, 135, false);
                giftPile(x + 110 * (x < 960 ? 1 : -1), y + 8, 2, 52, gifts, g, cr);
            } else {
                giftPile(x, y + 6, 4, 54, gifts, g, cr);
                candyCane(x + 95 * (x < 960 ? 1 : -1), y + 4, 150, x > 960);
            }
            g += 5;
        }
    }

    /** Pile de cadeaux : n boîtes côte à côte, plus une petite posée dessus. */
    private void giftPile(double x, double y, int n, double size, int[][] gifts, int g, Rng cr) {
        double step = size * .95, cx = x - (n - 1) * step / 2;
        for (int j = 0; j < n; j++) {
            int[] gc = gifts[(g + j) % gifts.length];
            double bw = size * (.8 + cr.r() * .45), bh = bw * (.65 + cr.r() * .5);
            gift(cx + j * step, y + (j % 2) * 4, bw, bh, gc[0], gc[1]);
        }
        if (n >= 3) {
            int[] gc = gifts[(g + n) % gifts.length];
            gift(x + size * .1, y - size * .78, size * .6, size * .48, gc[0], gc[1]);
        }
    }

    /** Un objet posé en x sur la crête L est-il entièrement visible (pas caché par la colline de devant, au-dessus du dock) ? */
    private static boolean visible(Layer L, double ph, Layer front, double phFront, double x) {
        for (int d = -25; d <= 25; d += 25) {
            double b = yAt(L, x + d, ph) + 10;
            if (b > 777 || yAt(front, x + d, phFront) - b < 4) return false;
        }
        return true;
    }

    /** Grand sapin de Noël : étages festonnés, guirlandes, boules, étoile ; lumières qui brillent le soir. */
    private void christmasTree(double x, double y, double h, boolean lit) {
        int dark = c("#1f5a3c"), light = c("#2f7a50");
        Integer snow = c("#f5f8fb");
        groundShadow(x, y, h, BLACK);
        cv.drawRect((float) (x - h * .03), (float) (y - h * .14), (float) (x + h * .03), (float) y, fill(c("#5a3a22")));
        for (int k = 0; k < 4; k++) {
            double base = y - h * (.1 + k * .19), top = base - h * (.4 - k * .03), w = h * (.33 - k * .065);
            Path dk = new Path(), lt = new Path();
            m(dk, x, top); l(dk, x + w, base);
            m(lt, x, top); l(lt, x, base + w * .03);
            int n = 4;
            double drop = w * .16;
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
            double my = top + (base - top) * .5;
            Path sn = new Path();
            m(sn, x, top); l(sn, x + w * .45, my);
            q(sn, x + w * .2, my + w * .1, x, my + w * .04);
            q(sn, x - w * .25, my + w * .12, x - w * .45, my);
            sn.close();
            cv.drawPath(sn, fill(snow, .9));
            garland(x, top, base, w, h, k, lit);
        }
        double sy = y - h * (.1 + 3 * .19) - h * (.4 - 3 * .03), sr = h * .055;
        fill.setShader(new RadialGradient((float) x, (float) sy, (float) (sr * (lit ? 5 : 3)), lit ? 0x99FFE08A : 0x55FFE08A, 0x00FFE08A, Shader.TileMode.CLAMP));
        circle(x, sy, sr * (lit ? 5 : 3), fill);
        Path st = new Path();
        for (int k = 0; k < 10; k++) {
            double a = -Math.PI / 2 + k * Math.PI / 5, rr = k % 2 == 0 ? sr : sr * .45;
            if (k == 0) m(st, x + Math.cos(a) * rr, sy + Math.sin(a) * rr);
            else l(st, x + Math.cos(a) * rr, sy + Math.sin(a) * rr);
        }
        st.close();
        cv.drawPath(st, fill(c("#ffd24a")));
    }

    /** Bonhomme de neige : trois boules, chapeau, écharpe rouge, nez carotte, bras en branches. */
    private void snowman(double x, double y, double h) {
        ellipse(x + h * .06, y, h * .32, h * .05, fill(BLACK, .16));
        double r1 = h * .25, r2 = h * .18, r3 = h * .13;
        double c1 = y - r1, c2 = c1 - r1 - r2 * .7, c3 = c2 - r2 - r3 * .75;
        int snow = c("#f7f9fc"), shade = c("#d6dfea");
        for (double[] b : new double[][]{{c1, r1}, {c2, r2}, {c3, r3}}) {
            circle(x, b[0], b[1], fill(snow));
            circle(x + b[1] * .25, b[0] + b[1] * .15, b[1] * .8, fill(shade, .55));
            circle(x - b[1] * .1, b[0] - b[1] * .05, b[1] * .78, fill(snow));
        }
        stroke.setColor(c("#5a3a22"));
        stroke.setStrokeWidth((float) (h * .025));
        stroke.setStrokeCap(Paint.Cap.ROUND);
        cv.drawLine((float) (x - r2 * .8), (float) c2, (float) (x - r2 * 2), (float) (c2 - r2 * .9), stroke);
        cv.drawLine((float) (x - r2 * 1.6), (float) (c2 - r2 * .6), (float) (x - r2 * 1.9), (float) (c2 - r2 * 1.3), stroke);
        cv.drawLine((float) (x + r2 * .8), (float) c2, (float) (x + r2 * 2), (float) (c2 - r2 * .5), stroke);
        for (int k = 0; k < 3; k++) circle(x, c2 - r2 * .45 + k * r2 * .4, h * .018, fill(c("#2a2a30")));
        circle(x - r3 * .35, c3 - r3 * .2, h * .016, fill(c("#2a2a30")));
        circle(x + r3 * .25, c3 - r3 * .2, h * .016, fill(c("#2a2a30")));
        Path nose = new Path();
        m(nose, x, c3 - r3 * .05); l(nose, x + r3 * .9, c3 + r3 * .08); l(nose, x, c3 + r3 * .18); nose.close();
        cv.drawPath(nose, fill(c("#f08a24")));
        cv.drawRect((float) (x - r2 * .95), (float) (c3 + r3 * .7), (float) (x + r2 * .95), (float) (c3 + r3 * 1.0), fill(c("#d23b3b")));
        cv.drawRect((float) (x + r2 * .3), (float) (c3 + r3 * .8), (float) (x + r2 * .6), (float) (c3 + r3 * 1.8), fill(c("#b52f30")));
        cv.drawRect((float) (x - r3 * 1.05), (float) (c3 - r3 * .85), (float) (x + r3 * 1.05), (float) (c3 - r3 * .7), fill(c("#26262c")));
        cv.drawRect((float) (x - r3 * .7), (float) (c3 - r3 * 1.9), (float) (x + r3 * .7), (float) (c3 - r3 * .8), fill(c("#26262c")));
        cv.drawRect((float) (x - r3 * .7), (float) (c3 - r3 * 1.05), (float) (x + r3 * .7), (float) (c3 - r3 * .88), fill(c("#d23b3b")));
    }

    /** Sucette : bâton blanc, disque en spirale de deux couleurs. */
    private void lollipop(double x, double y, double h, int color) {
        stroke.setColor(c("#f4efe6"));
        stroke.setStrokeWidth((float) (h * .07));
        stroke.setStrokeCap(Paint.Cap.ROUND);
        cv.drawLine((float) x, (float) y, (float) x, (float) (y - h * .7), stroke);
        double r = h * .3, cy = y - h * .7 - r * .8;
        circle(x, cy, r, fill(WHITE));
        Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
        sp.setStyle(Paint.Style.STROKE);
        sp.setStrokeWidth((float) (r * .28));
        sp.setColor(color);
        Path spiral = new Path();
        for (int k = 0; k <= 40; k++) {
            double a = k * .45, rr = r * .85 * k / 40.0;
            double px = x + Math.cos(a) * rr, py2 = cy + Math.sin(a) * rr;
            if (k == 0) m(spiral, px, py2);
            else l(spiral, px, py2);
        }
        cv.drawPath(spiral, sp);
    }

    /** Sucre d'orge : canne blanche à rayures rouges, crosse vers la gauche ou la droite. */
    private void candyCane(double x, double y, double h, boolean left) {
        double r = h * .2, s = left ? -1 : 1;
        Path p = new Path();
        m(p, x, y);
        l(p, x, y - h + r);
        RectF arc = new RectF((float) (x + (s < 0 ? -2 * r : 0)), (float) (y - h), (float) (x + (s < 0 ? 0 : 2 * r)), (float) (y - h + 2 * r));
        p.arcTo(arc, s < 0 ? 0 : 180, s < 0 ? -180 : 180);
        l(p, x + 2 * r * s, y - h + r * 1.6);
        ellipse(x + r * .3, y, h * .16, h * .04, fill(BLACK, .18));
        Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
        st.setStyle(Paint.Style.STROKE);
        st.setStrokeCap(Paint.Cap.ROUND);
        st.setStrokeWidth((float) (h * .12));
        st.setColor(c("#f7f3ee"));
        cv.drawPath(p, st);
        st.setStrokeCap(Paint.Cap.BUTT);
        st.setColor(c("#d22a33"));
        st.setPathEffect(new android.graphics.DashPathEffect(new float[]{(float) (h * .07), (float) (h * .08)}, 0));
        cv.drawPath(p, st);
        st.setPathEffect(null);
        st.setStrokeWidth((float) (h * .03)); // reflet
        st.setColor(0x66FFFFFF);
        cv.save();
        cv.translate((float) (-h * .025), 0);
        cv.drawPath(p, st);
        cv.restore();
    }

    /** Cadeau : boîte, couvercle un peu plus large, rubans croisés, nœud, une pointe de neige. */
    private void gift(double x, double y, double w, double h, int box, int ribbon) {
        ellipse(x + w * .1, y, w * .65, h * .1, fill(BLACK, .18));
        double l = x - w / 2, t = y - h;
        cv.drawRect((float) l, (float) (t + h * .18), (float) (l + w), (float) y, fill(box));
        cv.drawRect((float) (l + w * .62), (float) (t + h * .18), (float) (l + w), (float) y, fill(mix(box, BLACK, .18))); // côté à l'ombre
        cv.drawRect((float) (l - w * .05), (float) t, (float) (l + w * 1.05), (float) (t + h * .22), fill(mix(box, WHITE, .1)));
        cv.drawRect((float) (x - w * .08), (float) t, (float) (x + w * .08), (float) y, fill(ribbon));
        cv.drawRect((float) l, (float) (t + h * .5), (float) (l + w), (float) (t + h * .6), fill(ribbon));
        ellipse(x - w * .17, t - h * .06, w * .17, h * .1, fill(ribbon));
        ellipse(x + w * .17, t - h * .06, w * .17, h * .1, fill(ribbon));
        circle(x, t - h * .03, w * .07, fill(mix(ribbon, BLACK, .15)));
        ellipse(x - w * .25, t + h * .02, w * .28, h * .05, fill(c("#f5f8fb"))); // neige sur le couvercle
    }

    /**
     * Maison en pain d'épices : murs en pain d'épices bordés de glaçage, toit en tuiles de bonbons
     * sous la neige avec coulures, guirlande lumineuse, couronne sur la porte, sucres d'orge, sucettes,
     * cœur et pastilles de menthe sur la façade, fenêtres allumées le soir, bonhomme en pain d'épices.
     */
    private void gingerbread(Layer L, double ph, int col, Palette p0, boolean lit, double haze) {
        double x = 1485, y = yAt(L, x, ph) + 6, s = 104;
        double hz = haze * .6;
        int dough = mix(mix(c("#b5713a"), p0.tree, .2), col, hz), doughD = mix(dough, BLACK, .25);
        int choco = mix(mix(c("#5b3420"), p0.tree, .2), col, hz);
        int icing = c("#fdfaf5");
        ellipse(x + s * .05, y + s * .01, s * .78, s * .08, fill(c("#f5f8fb")));
        // cheminée et fumée
        cv.drawRect((float) (x + s * .2), (float) (y - s * 1.12), (float) (x + s * .34), (float) (y - s * .76), fill(doughD));
        cv.drawRect((float) (x + s * .18), (float) (y - s * 1.17), (float) (x + s * .36), (float) (y - s * 1.1), fill(icing));
        int smoke = mix(p0.sky[1], WHITE, .3);
        double[][] puffs = {{.29, 1.27, .07}, {.34, 1.39, .09}, {.43, 1.53, .11}, {.55, 1.67, .12}};
        for (double[] pf : puffs) circle(x + s * pf[0], y - s * pf[1], s * pf[2], fill(smoke, .45));
        // murs et pignon
        Path w = new Path();
        m(w, x - s * .44, y); l(w, x - s * .44, y - s * .5); l(w, x, y - s * .88); l(w, x + s * .44, y - s * .5); l(w, x + s * .44, y); w.close();
        cv.drawPath(w, fill(dough));
        cv.drawRect((float) (x + s * .2), (float) (y - s * .5), (float) (x + s * .44), (float) y, fill(doughD, .4));
        stroke.setColor(icing);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeWidth((float) (s * .03));
        // glaçage : angles des murs et festons en bas
        cv.drawLine((float) (x - s * .44), (float) y, (float) (x - s * .44), (float) (y - s * .5), stroke);
        cv.drawLine((float) (x + s * .44), (float) y, (float) (x + s * .44), (float) (y - s * .5), stroke);
        Path z = new Path();
        m(z, x - s * .44, y - s * .08);
        for (int k = 0; k < 8; k++) {
            double x0 = x - s * .44 + k * s * .11;
            q(z, x0 + s * .055, y + s * .005, x0 + s * .11, y - s * .08);
        }
        cv.drawPath(z, stroke);
        // cœur au pignon, pastilles de menthe
        double hy = y - s * .64;
        Path heart = new Path();
        m(heart, x, hy + s * .08);
        q(heart, x - s * .12, hy - s * .02, x - s * .06, hy - s * .07);
        q(heart, x, hy - s * .1, x, hy - s * .03);
        q(heart, x, hy - s * .1, x + s * .06, hy - s * .07);
        q(heart, x + s * .12, hy - s * .02, x, hy + s * .08);
        heart.close();
        cv.drawPath(heart, fill(c("#d8343a")));
        cv.drawPath(heart, stroke);
        for (int k = -1; k <= 1; k += 2) mint(x + k * s * .3, y - s * .62 + s * .04, s * .055);
        // porte arrondie : encadrement en sucre d'orge, couronne
        Path d = new Path();
        m(d, x - s * .11, y); l(d, x - s * .11, y - s * .27);
        d.arcTo(new RectF((float) (x - s * .11), (float) (y - s * .38), (float) (x + s * .11), (float) (y - s * .16)), 180, 180);
        l(d, x + s * .11, y); d.close();
        cv.drawPath(d, fill(choco));
        Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth((float) (s * .035));
        frame.setColor(icing);
        cv.drawPath(d, frame);
        frame.setColor(c("#d22a33"));
        frame.setPathEffect(new android.graphics.DashPathEffect(new float[]{(float) (s * .025), (float) (s * .025)}, 0));
        cv.drawPath(d, frame);
        double wy = y - s * .22;
        Paint wr = new Paint(Paint.ANTI_ALIAS_FLAG);
        wr.setStyle(Paint.Style.STROKE);
        wr.setStrokeWidth((float) (s * .035));
        wr.setColor(c("#2f7a42"));
        cv.drawCircle((float) x, (float) wy, (float) (s * .055), wr);
        for (int k = 0; k < 6; k++) circle(x + Math.cos(k * 1.05) * s * .055, wy + Math.sin(k * 1.05) * s * .055, s * .011, fill(c("#e8343a")));
        circle(x, wy + s * .06, s * .018, fill(c("#e8343a")));
        candyCane(x - s * .19, y + 1, s * .42, true);
        candyCane(x + s * .19, y + 1, s * .42, false);
        // fenêtres : glaçage, volets, lumière le soir
        for (int k = -1; k <= 1; k += 2) {
            double wx = x + k * s * .3, wyy = y - s * .3;
            if (lit) {
                fill.setShader(new RadialGradient((float) wx, (float) wyy, (float) (s * .25), 0x77FFC24A, 0x00FFC24A, Shader.TileMode.CLAMP));
                circle(wx, wyy, s * .25, fill);
            }
            cv.drawRect((float) (wx - s * .075), (float) (wyy - s * .08), (float) (wx + s * .075), (float) (wyy + s * .08), fill(lit ? c("#ffcf5a") : c("#5a3418")));
            cv.drawRect((float) (wx - s * .12), (float) (wyy - s * .08), (float) (wx - s * .08), (float) (wyy + s * .08), fill(c("#3f9a5a")));
            cv.drawRect((float) (wx + s * .08), (float) (wyy - s * .08), (float) (wx + s * .12), (float) (wyy + s * .08), fill(c("#3f9a5a")));
            stroke.setStrokeWidth((float) (s * .02));
            cv.drawLine((float) wx, (float) (wyy - s * .08), (float) wx, (float) (wyy + s * .08), stroke);
            cv.drawLine((float) (wx - s * .075), (float) wyy, (float) (wx + s * .075), (float) wyy, stroke);
            stroke.setStrokeWidth((float) (s * .03));
            cv.drawLine((float) (wx - s * .13), (float) (wyy + s * .1), (float) (wx + s * .13), (float) (wyy + s * .1), stroke);
        }
        // toit : chocolat, tuiles de bonbons, neige épaisse en haut, coulures de glaçage sur les bords
        Path r = new Path();
        m(r, x - s * .6, y - s * .42); l(r, x, y - s * 1.0); l(r, x + s * .6, y - s * .42);
        l(r, x + s * .52, y - s * .36); l(r, x, y - s * .88); l(r, x - s * .52, y - s * .36); r.close();
        cv.drawPath(r, fill(choco));
        int[] tiles = {0xFFF6B3C8, 0xFFB8E3C2, 0xFFFFF1A6, 0xFFB9D7F5};
        for (int row = 0; row < 3; row++) {
            double f = .3 + row * .22;
            for (int side = -1; side <= 1; side += 2) {
                for (int k = 0; k < 4 - row / 2; k++) {
                    double u = f + (k - 1.5) * .0 ;
                    double along = .18 + k * .2;
                    double tx = x + side * s * .56 * along, ty = y - s * 1.0 + (s * .58) * along + (row - 1) * s * .055;
                    if (ty > y - s * .4) continue;
                    circle(tx, ty, s * .03, fill(tiles[(row + k + (side > 0 ? 1 : 0)) % tiles.length]));
                }
            }
        }
        Path snow = new Path();
        m(snow, x - s * .62, y - s * .43); l(snow, x, y - s * 1.03); l(snow, x + s * .62, y - s * .43);
        l(snow, x + s * .56, y - s * .43); l(snow, x, y - s * .95); l(snow, x - s * .56, y - s * .43); snow.close();
        cv.drawPath(snow, fill(icing));
        Path drip = new Path();
        for (int k = 0; k < 7; k++) {
            double u = .08 + k * .135, d2 = s * (.05 + (k % 3) * .025);
            double ex = x - s * .6 + u * s * .6, ey = y - s * .43 - u * s * .58;
            m(drip, ex - s * .025, ey); q(drip, ex, ey + d2 * 1.6, ex + s * .025, ey); drip.close();
            double fx = x + s * .6 - u * s * .6;
            m(drip, fx - s * .025, ey); q(drip, fx, ey + d2 * 1.6, fx + s * .025, ey); drip.close();
        }
        cv.drawPath(drip, fill(icing));
        // gros bonbons sur le faîte
        int[] gum = {0xFFD8343A, 0xFF2F9A4A, 0xFFF2C14E, 0xFF3F7FD8};
        for (int k = 0; k < 4; k++) {
            double u = .25 + k * .17;
            circle(x - s * .6 + u * s * .6, y - s * .45 - u * s * .58 - s * .03, s * .03, fill(gum[k]));
            circle(x + s * .6 - u * s * .6, y - s * .45 - u * s * .58 - s * .03, s * .03, fill(gum[(k + 2) % 4]));
        }
        circle(x, y - s * 1.04, s * .045, fill(c("#d8343a")));
        // guirlande lumineuse le long du bord du toit
        int[] bulbs = {0xFFFF5A5A, 0xFFFFD45A, 0xFF5AD27A, 0xFF5AA8FF};
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 7; k++) {
                double u = (k + .5) / 7;
                double bx = x + side * s * .6 * (1 - u) , by = y - s * .43 - (u) * s * .58 + s * .05 + Math.sin(k * 1.3) * s * .008;
                int bc = bulbs[(k + (side > 0 ? 2 : 0)) % 4];
                if (lit) {
                    fill.setShader(new RadialGradient((float) bx, (float) by, (float) (s * .05), (bc & 0x00FFFFFF) | 0x99000000, bc & 0x00FFFFFF, Shader.TileMode.CLAMP));
                    circle(bx, by, s * .05, fill);
                }
                circle(bx, by, s * .013, fill(lit ? mix(bc, WHITE, .3) : mix(bc, BLACK, .2)));
            }
        }
        // sucettes et bonhomme en pain d'épices devant la maison
        lollipop(x - s * .52, y + 2, s * .38, c("#d8343a"));
        lollipop(x + s * .52, y + 2, s * .34, c("#3f9a5a"));
        gingerMan(x + s * .72, y + 2, s * .3, dough);
    }

    /** Pastille de menthe : disque blanc à quartiers rouges. */
    private void mint(double x, double y, double r) {
        circle(x, y, r, fill(WHITE));
        for (int k = 0; k < 6; k += 2) {
            Path p = new Path();
            m(p, x, y);
            p.arcTo(new RectF((float) (x - r), (float) (y - r), (float) (x + r), (float) (y + r)), k * 60, 60);
            p.close();
            cv.drawPath(p, fill(c("#d8343a")));
        }
    }

    /** Bonhomme en pain d'épices, glaçage blanc et boutons rouges. */
    private void gingerMan(double x, double y, double h, int dough) {
        double r = h * .17;
        Path b = new Path();
        b.addCircle((float) x, (float) (y - h + r), (float) r, Path.Direction.CW);
        b.addRoundRect(new RectF((float) (x - h * .17), (float) (y - h + r * 1.7), (float) (x + h * .17), (float) (y - h * .32)), (float) (h * .08), (float) (h * .08), Path.Direction.CW);
        b.addRoundRect(new RectF((float) (x - h * .38), (float) (y - h + r * 1.9), (float) (x + h * .38), (float) (y - h + r * 2.9)), (float) (h * .06), (float) (h * .06), Path.Direction.CW);
        b.addRoundRect(new RectF((float) (x - h * .19), (float) (y - h * .4), (float) (x - h * .03), (float) y), (float) (h * .07), (float) (h * .07), Path.Direction.CW);
        b.addRoundRect(new RectF((float) (x + h * .03), (float) (y - h * .4), (float) (x + h * .19), (float) y), (float) (h * .07), (float) (h * .07), Path.Direction.CW);
        cv.drawPath(b, fill(dough));
        circle(x - r * .35, y - h + r * .85, h * .025, fill(WHITE));
        circle(x + r * .35, y - h + r * .85, h * .025, fill(WHITE));
        for (int k = 0; k < 2; k++) circle(x, y - h * .62 + k * h * .12, h * .03, fill(c("#d8343a")));
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
