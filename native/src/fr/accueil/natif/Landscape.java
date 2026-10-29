package fr.accueil.natif;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Paysages du thème Thinking : une illustration par saison (papier découpé : montagnes, collines,
 * chemin, bosquets), dans assets/paysages. Le moment de la journée la transforme : de jour l'image
 * telle quelle avec le soleil ; à l'aube, au crépuscule et la nuit, le ciel est découpé (le long
 * de la ligne des montagnes) et redessiné (dégradé, soleil bas, lune, étoiles), et le paysage teinté.
 * Halloween a sa propre illustration ; Noël (sur l'hiver) ajoute ses décors à des endroits choisis
 * sur l'illustration. Le tout est composé une fois dans une image de 1920 × 1080, gardée en cache.
 */
final class Landscape {

    static final int W = 1920, H = 1080;
    static final int VERSION = 18; // à augmenter quand le dessin change : les images en cache sont refaites
    static final String[] TIMES = {"aube", "jour", "crépuscule", "nuit"};
    static final String[] SEASON_IDS = {"printemps", "ete", "automne", "hiver", "halloween", "noel"};

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

    /** Saison reconnue (sinon null). */
    static String season(String id) {
        if (id != null) for (String s : SEASON_IDS) if (s.equals(id)) return s;
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

    /** Illustration de la saison (Halloween sur l'automne, Noël sur l'hiver). */
    private static String image(String seasonId) {
        return "noel".equals(seasonId) ? "hiver" : seasonId; // Halloween a sa propre illustration
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

    /* ===== Arbres de l'illustration (feuilles qui tombent, lucioles) ===== */

    /** Arbre de l'illustration : pied, hauteur (couronne vers y − 0,64 h), plan (6 devant, 4 bosquets). */
    static final class Tree {
        final float x, y, h;
        final int layer;
        final boolean autumn;  // feuillage d'automne
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

    // x, pied, centre de la couronne, plan, couleur d'automne (positions relevées sur les illustrations)
    private static final double[][] TREES = {
            {85, 880, 700, 6, 0xE2702E}, {175, 880, 700, 6, 0xA9652E}, {230, 900, 770, 6, 0xF0A830},
            {440, 700, 615, 4, 0xE8742E}, {490, 700, 570, 4, 0xB06A2C}, {530, 700, 630, 4, 0xEBA634},
            {1225, 700, 640, 4, 0xD8452E}, {1280, 690, 610, 4, 0xD9892E}, {1335, 690, 600, 4, 0xD4472E},
            {1390, 690, 620, 4, 0xF0B030}, {1440, 690, 570, 4, 0xCC3F2A}, {1495, 690, 590, 4, 0xD9792E},
            {1615, 880, 760, 6, 0x8E5A2E},
    };

    static List<Tree> trees(int idx, String seasonId) {
        boolean autumn = "automne".equals(image(seasonId));
        List<Tree> list = new ArrayList<>();
        for (double[] t : TREES)
            list.add(new Tree(t[0], t[1], (t[1] - t[2]) / .64, (int) t[3], autumn, 0xFF000000 | (int) t[4]));
        return list;
    }

    /* ===== Composition ===== */

    private final Canvas cv;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap base; // illustration, pour lire la couleur du sol

    private Landscape(Canvas cv) {
        this.cv = cv;
        stroke.setStyle(Paint.Style.STROKE);
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

    private static Bitmap asset(Context ctx, String name) {
        try (InputStream in = ctx.getAssets().open("paysages/" + name + ".jpg")) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inMutable = true;
            return BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) {
            return null;
        }
    }

    /** Couleur du sol de l'illustration en (x, y). */
    private int ground(double x, double y) {
        int px = (int) Math.max(0, Math.min(W - 1, x)), py = (int) Math.max(0, Math.min(H - 1, y));
        return base.getPixel(px, py) | 0xFF000000;
    }

    /** Paysage `idx` (0 aube, 1 jour, 2 crépuscule, 3 nuit) de la saison, à la taille demandée. */
    static Bitmap render(Context ctx, int idx, String seasonId, int width, int height) {
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float s = Math.max(width / (float) W, height / (float) H);
        c.translate((width - W * s) / 2, (height - H * s) / 2);
        c.scale(s, s);
        Landscape l = new Landscape(c);
        l.base = asset(ctx, image(seasonId));
        if (l.base == null) { c.drawColor(0xFFE9EFE4); return out; }
        l.compose(ctx, idx, seasonId);
        l.base.recycle();
        return out;
    }

    private void compose(Context ctx, int idx, String seasonId) {
        Palette p0 = PALETTES[idx];
        int[] sky = skyline(ctx, base);
        Path skyPath = new Path(); // le ciel, au-dessus de la ligne des montagnes
        skyPath.moveTo(0, 0);
        for (int x = 0; x < W; x += 4) skyPath.lineTo(x, sky[x]);
        skyPath.lineTo(W, sky[W - 1]);
        skyPath.lineTo(W, 0);
        skyPath.close();
        double sx = 330, sy = new double[]{430, 250, 450, 250}[idx];
        Paint img = new Paint(Paint.FILTER_BITMAP_FLAG);
        if ("halloween".equals(seasonId)) { // illustration complète, déjà au crépuscule : seulement teintée
            if (idx != 1 && idx != 2) img.setColorFilter(new ColorMatrixColorFilter(grade(idx)));
            cv.drawBitmap(base, 0, 0, img);
            if (idx == 3) halloweenGlow();
            return;
        }
        if (idx == 1) { // jour : l'illustration telle quelle, le soleil derrière les montagnes
            cv.drawBitmap(base, 0, 0, img);
            cv.save();
            cv.clipPath(skyPath);
            sun(p0, sx, sy, false);
            cv.restore();
        } else {
            // ciel redessiné : dégradé du moment, étoiles, soleil bas ou lune
            fill.setShader(new LinearGradient(0, 0, 0, 600, p0.sky, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, W, H, fill);
            if (p0.night) {
                Random rnd = new Random(1234);
                for (int i = 0; i < 160; i++) circle(rnd.nextFloat() * W, rnd.nextFloat() * 560, rnd.nextFloat() * 1.7 + .4, fill(WHITE, rnd.nextFloat() * .6 + .25));
            }
            sun(p0, sx, sy, p0.night);
            // paysage découpé le long des montagnes, teinté par la lumière du moment
            Bitmap cut = base.copy(Bitmap.Config.ARGB_8888, true);
            cut.setHasAlpha(true); // issue d'un JPEG : sans cela, le ciel vidé effacerait le dégradé
            int[] row = new int[W];
            for (int y = 0; y < H; y++) {
                boolean any = false;
                for (int x = 0; x < W; x++) if (y < sky[x] + 1) { any = true; break; }
                if (!any) continue;
                cut.getPixels(row, 0, W, 0, y, W, 1);
                for (int x = 0; x < W; x++) {
                    if (y < sky[x] - 1) row[x] = 0;
                    else if (y < sky[x] + 1) row[x] = (row[x] & 0x00FFFFFF) | ((y - sky[x] + 2) * 85) << 24; // bord adouci
                }
                cut.setPixels(row, 0, W, 0, y, W, 1);
            }
            img.setColorFilter(new ColorMatrixColorFilter(grade(idx)));
            cv.drawBitmap(cut, 0, 0, img);
            cut.recycle();
            if (idx != 3) { // reflet chaud de l'aube ou du crépuscule sur le paysage
                Paint warm = new Paint();
                warm.setShader(new LinearGradient(0, 350, 0, H, alpha(idx == 0 ? c("#ffc9a6") : c("#e09070"), idx == 0 ? .1 : .12),
                        alpha(idx == 0 ? c("#ffc9a6") : c("#6a4a6a"), .04), Shader.TileMode.CLAMP));
                cv.save();
                Path land = new Path(skyPath);
                land.setFillType(Path.FillType.INVERSE_WINDING);
                cv.clipPath(land);
                cv.drawRect(0, 0, W, H, warm);
                cv.restore();
            }
        }
        if (!"noel".equals(seasonId)) return;
        boolean lit = idx >= 2;
        // décors de Noël dans un calque, teintés par la lumière du moment (sinon trop vifs la nuit)
        int lay = cv.saveLayer(0, 0, W, H, null);
        christmas(p0, lit);
        double tint = new double[]{.12, 0, .18, .42}[idx];
        if (tint > 0) {
            Paint tp = new Paint();
            tp.setColor(alpha(p0.night ? c("#1d2a44") : p0.sky[0], tint));
            tp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
            cv.drawRect(0, 0, W, H, tp);
        }
        cv.restoreToCount(lay);
    }

    /** Teinte du paysage selon le moment : aube dorée, crépuscule rosé, nuit bleutée et sombre. */
    private static ColorMatrix grade(int idx) {
        ColorMatrix m = new ColorMatrix();
        if (idx == 0) m.set(new float[]{1.03f, 0, 0, 0, 8, 0, .95f, 0, 0, 2, 0, 0, .86f, 0, 0, 0, 0, 0, 1, 0});
        else if (idx == 2) m.set(new float[]{.98f, 0, 0, 0, 4, 0, .86f, 0, 0, 0, 0, 0, .84f, 0, 4, 0, 0, 0, 1, 0});
        else if (idx == 3) {
            m.setSaturation(.55f);
            ColorMatrix d = new ColorMatrix(new float[]{.34f, 0, 0, 0, 2, 0, .4f, 0, 0, 4, 0, 0, .6f, 0, 12, 0, 0, 0, 1, 0});
            m.postConcat(d);
        }
        return m;
    }

    /** Soleil (halo, cercles concentriques, disque) ou lune en croissant. */
    private void sun(Palette p0, double sx, double sy, boolean night) {
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
    }

    /* ===== Ligne des montagnes ===== */

    private static int[] SKY0;

    /**
     * Hauteur du ciel, colonne par colonne. Repérée une fois sur l'illustration d'été (ciel bleu vif,
     * montagnes grises : contraste net), puis ajustée sur chaque illustration en cherchant, à quelques
     * pixels près, le bord le plus marqué.
     */
    private static int[] skyline(Context ctx, Bitmap img) {
        synchronized (Landscape.class) {
            if (SKY0 == null) {
                Bitmap ete = asset(ctx, "ete");
                SKY0 = new int[W];
                int[] col = new int[H];
                for (int x = 0; x < W; x++) {
                    ete.getPixels(col, 0, 1, x, 0, 1, H);
                    int y = 0;
                    while (y < H - 1) {
                        int p = col[y], r = p >> 16 & 255, b = p & 255;
                        if (b - r < 60) break; // plus de ciel bleu
                        y++;
                    }
                    SKY0[x] = y;
                }
                ete.recycle();
            }
        }
        int[] sky = new int[W], col = new int[H];
        for (int x = 0; x < W; x++) {
            img.getPixels(col, 0, 1, x, 0, 1, H);
            int y0 = SKY0[x], best = y0;
            double bestD = -1;
            for (int y = Math.max(2, y0 - 10); y <= Math.min(H - 3, y0 + 10); y++) {
                double dd = dist(col[y - 2], col[y + 2]);
                if (dd > bestD + 6) { bestD = dd; best = y; }
            }
            sky[x] = best;
        }
        // lissage léger : pas de dents isolées
        int[] sm = sky.clone();
        for (int x = 2; x < W - 2; x++) {
            int[] w5 = {sky[x - 2], sky[x - 1], sky[x], sky[x + 1], sky[x + 2]};
            Arrays.sort(w5);
            sm[x] = w5[2];
        }
        return sm;
    }

    private static double dist(int a, int b) {
        return Math.abs((a >> 16 & 255) - (b >> 16 & 255)) + Math.abs((a >> 8 & 255) - (b >> 8 & 255)) + Math.abs((a & 255) - (b & 255));
    }

    /** Nuit d'Halloween : les citrouilles et lanternes de l'illustration se rallument par-dessus la teinte de nuit. */
    private void halloweenGlow() {
        double[][] pk = {{709, 769, 22}, {750, 755, 24}, {920, 764, 20}, {978, 731, 14}, {1011, 818, 34}, {699, 900, 50},
                {624, 939, 36}, {1014, 919, 40}, {1084, 939, 30}, {1715, 610, 26}, {1880, 639, 24}, {1792, 697, 24},
                {1560, 789, 22}, {1251, 653, 12}, {1406, 629, 12}, {1454, 605, 12}, {426, 644, 12}};
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        for (double[] p : pk) {
            g.setShader(new RadialGradient((float) p[0], (float) p[1], (float) (p[2] * 2.6), new int[]{0xCCFFB040, 0x66FF8A20, 0x00FF8A20},
                    new float[]{0, .35f, 1}, Shader.TileMode.CLAMP));
            cv.drawCircle((float) p[0], (float) p[1], (float) (p[2] * 2.6), g);
        }
    }

    /* ===== Noël (sur l'illustration d'hiver) ===== */

    private void christmas(Palette p0, boolean lit) {
        int[][] gifts = {{0xFFD23B3B, 0xFFF2C14E}, {0xFF2F8A4A, 0xFFD23B3B}, {0xFF3A6CCF, 0xFFF4F4F4},
                {0xFFE0B040, 0xFFC0302F}, {0xFF8E44AD, 0xFFF2C14E}, {0xFFF4F4F4, 0xFFD23B3B}};
        Rng cr = new Rng(2512);
        // maison en pain d'épices au second plan, à côté du bosquet de gauche
        gingerbread(640, 714, ground(640, 718), p0, lit);
        // au-dessus du dock : grand sapin et cadeaux à droite du chemin, bonhomme, sucres d'orge
        christmasTree(1215, 776, 150, lit);
        giftPile(1215, 780, 3, 26, gifts, 0, cr);
        snowman(400, 776, 70);
        candyCane(1075, 772, 50, false);
        candyCane(1095, 776, 40, true);
        candyCane(545, 778, 52, true);
        giftPile(1400, 778, 3, 26, gifts, 3, cr);
        giftPile(480, 780, 2, 24, gifts, 1, cr);
        // premier plan, visible en veille
        christmasTree(500, 945, 230, lit);
        giftPile(500, 952, 4, 40, gifts, 2, cr);
        snowman(1300, 972, 130);
        candyCane(300, 948, 150, true);
        candyCane(340, 954, 115, false);
        candyCane(1520, 968, 160, false);
        candyCane(1480, 972, 120, true);
        giftPile(1120, 966, 3, 44, gifts, 4, cr);
        giftPile(1440, 975, 2, 46, gifts, 5, cr);
    }

    /**
     * Maison en pain d'épices : murs en pain d'épices bordés de glaçage, toit en tuiles de bonbons
     * sous la neige avec coulures, guirlande lumineuse, couronne sur la porte, sucres d'orge, sucettes,
     * cœur et pastilles de menthe sur la façade, fenêtres allumées le soir, bonhomme en pain d'épices.
     */
    private void gingerbread(double x, double y, int col, Palette p0, boolean lit) {
        double s = 92, haze = .1;
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

    private void groundShadow(double x, double y, double h, int dark) {
        ellipse(x + h * .06, y, h * .2, h * .035, fill(dark, .28));
    }

    private static final int[] BAUBLES = {0xFFD8343A, 0xFFF2C14E, 0xFF3F7FD8, 0xFFE8E8F0, 0xFFC0399A};

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
