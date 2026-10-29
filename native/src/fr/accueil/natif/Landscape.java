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
 * Halloween et Noël ont leur propre illustration ; la nuit, leurs lumières (citrouilles, guirlandes,
 * lanternes, étoile du sapin) se rallument par-dessus. Le tout est composé une fois dans une image de 1920 × 1080, gardée en cache.
 */
final class Landscape {

    static final int W = 1920, H = 1080;
    static final int VERSION = 26; // à augmenter quand le dessin change : les images en cache sont refaites
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
        return seasonId; // une illustration par saison, plus Halloween et Noël
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
        boolean special = "halloween".equals(seasonId) || "noel".equals(seasonId);
        int[] px = new int[W * H];
        base.getPixels(px, 0, W, 0, 0, W, H);
        int[] sky = skyMask(ctx, px); // opacité du ciel, 0 … 255
        Bitmap emission = special && idx >= 2 ? emission(px, prior(ctx)) : null; // sources de lumière de l'illustration
        double sx = 330, sy = new double[]{430, 250, 450, 250}[idx];
        Paint img = new Paint(Paint.FILTER_BITMAP_FLAG);
        if (idx == 1) { // jour : l'illustration telle quelle, le soleil derrière les montagnes
            cv.drawBitmap(base, 0, 0, img);
            if (special) return; // ciel du crépuscule (Halloween) ou couvert (Noël) : pas de soleil
            int lay = cv.saveLayer(0, 0, W, H, null);
            sun(p0, sx, sy, false);
            int[] a = new int[W * H];
            for (int i = 0; i < a.length; i++) a[i] = sky[i] << 24;
            Paint in = new Paint();
            in.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            Bitmap mk = Bitmap.createBitmap(a, W, H, Bitmap.Config.ARGB_8888);
            cv.drawBitmap(mk, 0, 0, in);
            mk.recycle();
            cv.restoreToCount(lay);
            return;
        }
        // ciel redessiné : dégradé du moment, étoiles, soleil bas ou lune
        fill.setShader(new LinearGradient(0, 0, 0, 600, p0.sky, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, W, H, fill);
        if (p0.night) {
            Random rnd = new Random(1234);
            for (int i = 0; i < 160; i++) circle(rnd.nextFloat() * W, rnd.nextFloat() * 560, rnd.nextFloat() * 1.7 + .4, fill(WHITE, rnd.nextFloat() * .6 + .25));
        }
        sun(p0, sx, sy, p0.night);
        // paysage sans son ciel (les arbres, l'étoile du sapin, les chauves-souris restent), teinté
        for (int i = 0; i < px.length; i++) px[i] = (px[i] & 0x00FFFFFF) | (255 - sky[i]) << 24;
        Bitmap cut = Bitmap.createBitmap(px, W, H, Bitmap.Config.ARGB_8888);
        img.setColorFilter(new ColorMatrixColorFilter(grade(idx)));
        cv.drawBitmap(cut, 0, 0, img);
        cut.recycle();
        if (idx != 3) { // reflet chaud de l'aube ou du crépuscule
            Paint warm = new Paint();
            warm.setShader(new LinearGradient(0, 350, 0, H, alpha(idx == 0 ? c("#ffc9a6") : c("#e09070"), idx == 0 ? .1 : .12),
                    alpha(idx == 0 ? c("#ffc9a6") : c("#6a4a6a"), .04), Shader.TileMode.CLAMP));
            warm.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
            cv.drawRect(0, 350, W, H, warm);
        }
        if (emission != null) {
            lightUp(emission, idx == 3 ? 1 : .5);
            emission.recycle();
        }
    }

    /* ===== Ciel de l'illustration ===== */

    private static int[] SKY0;

    /**
     * Ciel de l'illustration : remplissage depuis le haut de l'image, de proche en proche tant que la
     * couleur varie doucement (dégradé du ciel, nuages vaporeux). Arbres, étoile du sapin, chauves-souris
     * ont un bord net : ils restent. Par sécurité, rien en dessous de la ligne des montagnes (repérée
     * une fois sur l'illustration d'été, au ciel bleu vif bien contrasté). Bord adouci d'un pixel.
     */
    private static int[] skyMask(Context ctx, int[] px) {
        int[] prior = prior(ctx);
        byte[] m = new byte[W * H];
        int[] q = new int[W * H];
        int head = 0, tail = 0;
        for (int x = 0; x < W; x++) if (prior[x] > 0) { m[x] = 1; q[tail++] = x; }
        while (head < tail) {
            int i = q[head++], x = i % W, y = i / W;
            for (int k = 0; k < 4; k++) {
                int x2 = x + (k == 0 ? 1 : k == 1 ? -1 : 0), y2 = y + (k == 2 ? 1 : k == 3 ? -1 : 0);
                if (x2 < 0 || x2 >= W || y2 < 0 || y2 >= H) continue;
                int j = y2 * W + x2;
                if (m[j] != 0 || y2 > prior[x2] + 3 || dist(px[i], px[j]) > 10) continue;
                m[j] = 1;
                q[tail++] = j;
            }
        }
        int[] a = new int[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int sum = 0, n = 0;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= W || yy < 0 || yy >= H) continue;
                        sum += m[yy * W + xx];
                        n++;
                    }
                a[y * W + x] = sum * 255 / n;
            }
        return a;
    }

    /** Ligne des montagnes de l'illustration d'été (ciel bleu vif, montagnes grises). */
    private static synchronized int[] prior(Context ctx) {
        if (SKY0 != null) return SKY0;
        Bitmap ete = asset(ctx, "ete");
        SKY0 = new int[W];
        int[] col = new int[H];
        for (int x = 0; x < W; x++) {
            ete.getPixels(col, 0, 1, x, 0, 1, H);
            int y = 0;
            while (y < H - 1) {
                int p = col[y], r = p >> 16 & 255, b = p & 255;
                if (b - r < 60) break;
                y++;
            }
            SKY0[x] = y;
        }
        ete.recycle();
        return SKY0;
    }

    /**
     * Sources de lumière de l'illustration : pixels à la fois très lumineux et chauds (ampoules,
     * étoile du sapin, flammes des lanternes, visages des citrouilles). Les feuillages jaunes,
     * la neige ou les boules dorées n'y entrent pas (pas assez lumineux, ou pas assez chauds).
     * Renvoie ces pixels avec leur couleur d'origine et une opacité selon leur intensité.
     */
    private static Bitmap emission(int[] px, int[] prior) {
        // source légèrement lissée (3 × 3) : le grain et les hachures de texture ne s'allument pas par points
        int[] sm = new int[px.length];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int r = 0, g = 0, b = 0, n = 0;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= W || yy < 0 || yy >= H) continue;
                        int p = px[yy * W + xx];
                        r += p >> 16 & 255; g += p >> 8 & 255; b += p & 255; n++;
                    }
                sm[y * W + x] = (r / n) << 16 | (g / n) << 8 | b / n;
            }
        int[] v = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int p = sm[i];
            v[i] = Math.max(p >> 16 & 255, Math.max(p >> 8 & 255, p & 255));
        }
        int[] sum = new int[(W + 1) * (H + 1)];
        for (int y = 0; y < H; y++)
            for (int x = 0, row = 0; x < W; x++) {
                row += v[y * W + x];
                sum[(y + 1) * (W + 1) + x + 1] = sum[y * (W + 1) + x + 1] + row;
            }
        float[] warmA = new float[px.length], spotA = new float[px.length];
        int R = 8;
        for (int i = 0; i < px.length; i++) {
            int p = sm[i], r = p >> 16 & 255, g = p >> 8 & 255, b = p & 255, x = i % W, y = i / W;
            // lumière chaude et très vive (flammes, visages allumés : luminosité 220 à 245 sur les
            // illustrations ; les citrouilles éteintes ne dépassent pas 200), hors de la bande claire de l'horizon
            int o = px[i], or = o >> 16 & 255, og = o >> 8 & 255, ob = o & 255;
            double lum = .3 * or + .59 * og + .11 * ob, warm = or - ob;
            if (y >= prior[x] + 30 || y < prior[x] - 60) warmA[i] = (float) (smooth(203, 216, lum) * smooth(20, 70, warm) * smooth(-20, 20, og - ob - 25));
            // petite source plus vive que ce qui l'entoure (ampoules, y compris leur cœur presque blanc)
            int x0 = Math.max(0, x - R), x1 = Math.min(W, x + R + 1), y0 = Math.max(0, y - R), y1 = Math.min(H, y + R + 1);
            double mean = (sum[y1 * (W + 1) + x1] - sum[y0 * (W + 1) + x1] - sum[y1 * (W + 1) + x0] + sum[y0 * (W + 1) + x0])
                    / (double) ((x1 - x0) * (y1 - y0));
            int mn = Math.min(r, Math.min(g, b));
            double sat = v[i] == 0 ? 0 : (v[i] - mn) / (double) v[i];
            spotA[i] = (float) (smooth(30, 70, v[i] - mean) * smooth(150, 215, v[i]) * smooth(.25, .5, sat));
        }
        close(spotA, 3); // le cœur presque blanc des ampoules (peu saturé) est rebouché
        // ne garder que des taches : petites et rondes pour les ampoules (pas un bord de feuillage ni
        // une citrouille entière), d'un seul tenant et assez grandes pour les visages allumés
        keepBlobs(spotA, 20, 1100, 2.2);
        keepBlobs(warmA, 60, 40000, 6);
        int[] e = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            double a = Math.max(warmA[i], spotA[i]);
            if (a > .02) e[i] = (int) Math.round(a * 255) << 24 | (px[i] & 0x00FFFFFF);
        }
        // trous éventuels au cœur des taches : bouchés par un léger élargissement
        int[] out = e.clone();
        for (int y = 1; y < H - 1; y++)
            for (int x = 1; x < W - 1; x++) {
                int i = y * W + x, best = e[i] >>> 24;
                for (int k = -1; k <= 1; k += 2) {
                    best = Math.max(best, Math.min(e[i + k] >>> 24, e[i - k] >>> 24));
                    best = Math.max(best, Math.min(e[i + k * W] >>> 24, e[i - k * W] >>> 24));
                }
                if (best > (e[i] >>> 24)) out[i] = best << 24 | (px[i] & 0x00FFFFFF);
            }
        return Bitmap.createBitmap(out, W, H, Bitmap.Config.ARGB_8888);
    }

    /** Fermeture (maximum puis minimum sur un carré de côté 2r + 1) : bouche les petits trous. */
    private static void close(float[] a, int r) {
        float[] t = new float[a.length];
        for (int pass = 0; pass < 2; pass++) {
            boolean max = pass == 0;
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    float m = a[y * W + x];
                    for (int d = -r; d <= r; d++) {
                        int xx = Math.max(0, Math.min(W - 1, x + d));
                        m = max ? Math.max(m, a[y * W + xx]) : Math.min(m, a[y * W + xx]);
                    }
                    t[y * W + x] = m;
                }
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    float m = t[y * W + x];
                    for (int d = -r; d <= r; d++) {
                        int yy = Math.max(0, Math.min(H - 1, y + d));
                        m = max ? Math.max(m, t[yy * W + x]) : Math.min(m, t[yy * W + x]);
                    }
                    a[y * W + x] = m;
                }
        }
    }

    /** Efface les taches (a > 0,3, d'un seul tenant) trop petites, trop grandes ou trop allongées. */
    private static void keepBlobs(float[] a, int minArea, int maxArea, double maxAspect) {
        int[] label = new int[a.length], q = new int[a.length];
        int next = 1;
        for (int s0 = 0; s0 < a.length; s0++) {
            if (a[s0] <= .3f || label[s0] != 0) continue;
            int head = 0, tail = 0, minX = W, maxX = 0, minY = H, maxY = 0;
            q[tail++] = s0;
            label[s0] = next;
            while (head < tail) {
                int i = q[head++], x = i % W, y = i / W;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                for (int k = 0; k < 4; k++) {
                    int x2 = x + (k == 0 ? 1 : k == 1 ? -1 : 0), y2 = y + (k == 2 ? 1 : k == 3 ? -1 : 0);
                    if (x2 < 0 || x2 >= W || y2 < 0 || y2 >= H) continue;
                    int j = y2 * W + x2;
                    if (label[j] != 0 || a[j] <= .3f) continue;
                    label[j] = next;
                    q[tail++] = j;
                }
            }
            int bw = maxX - minX + 1, bh = maxY - minY + 1;
            double aspect = Math.max(bw, bh) / (double) Math.min(bw, bh);
            boolean keep = tail >= minArea && tail <= maxArea && aspect <= maxAspect;
            // les pixels faibles autour d'une tache suivent son sort (halo de la source)
            for (int k = 0; k < tail; k++) if (!keep) a[q[k]] = 0;
            if (!keep) {
                for (int y = Math.max(0, minY - 3); y <= Math.min(H - 1, maxY + 3); y++)
                    for (int x = Math.max(0, minX - 3); x <= Math.min(W - 1, maxX + 3); x++) if (label[y * W + x] == 0) a[y * W + x] = 0;
            }
            next++;
        }
    }

    private static double smooth(double a, double b, double v) {
        double t = Math.max(0, Math.min(1, (v - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /**
     * Les lumières gardent leur éclat d'origine par-dessus la scène assombrie, puis diffusent :
     * un halo serré et vif, et une lueur large et très douce (comme dans l'air humide du soir).
     */
    private void lightUp(Bitmap em, double strength) {
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setAlpha((int) Math.round(255 * Math.min(1, strength * 1.1)));
        cv.drawBitmap(em, 0, 0, p);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        int[][] spreads = {{4, 3}, {16, 3}}; // réduction, passes de flou
        double[] amount = {1.1, .65};
        for (int k = 0; k < spreads.length; k++) {
            int f = spreads[k][0], w = W / f, h = H / f;
            Bitmap small = Bitmap.createScaledBitmap(em, w, h, true);
            int[] a = new int[w * h], t = new int[w * h];
            small.getPixels(a, 0, w, 0, 0, w, h);
            // couleur prémultipliée par l'opacité : le flou ne fait pas baver de noir
            for (int i = 0; i < a.length; i++) {
                int al = a[i] >>> 24;
                a[i] = ((a[i] >> 16 & 255) * al / 255) << 16 | ((a[i] >> 8 & 255) * al / 255) << 8 | (a[i] & 255) * al / 255;
            }
            for (int pass = 0; pass < spreads[k][1]; pass++) {
                box(a, t, w, h, true);
                box(t, a, w, h, false);
            }
            double gain = amount[k] * strength * (k == 0 ? 2.2 : 3.2); // le flou dilue : on compense
            for (int i = 0; i < a.length; i++) {
                int r = Math.min(255, (int) ((a[i] >> 16 & 255) * gain)), g = Math.min(255, (int) ((a[i] >> 8 & 255) * gain)),
                        b = Math.min(255, (int) ((a[i] & 255) * gain * .85));
                a[i] = 0xFF000000 | r << 16 | g << 8 | b;
            }
            small.recycle();
            Bitmap halo = Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888);
            p.setAlpha(255);
            cv.drawBitmap(halo, null, new RectF(0, 0, W, H), p);
            halo.recycle();
        }
    }

    /** Moyenne sur 5 pixels, horizontalement ou verticalement (canaux R, G, B). */
    private static void box(int[] in, int[] out, int w, int h, boolean horizontal) {
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int r = 0, g = 0, b = 0;
                for (int d = -2; d <= 2; d++) {
                    int xx = horizontal ? Math.max(0, Math.min(w - 1, x + d)) : x, yy = horizontal ? y : Math.max(0, Math.min(h - 1, y + d));
                    int p = in[yy * w + xx];
                    r += p >> 16 & 255;
                    g += p >> 8 & 255;
                    b += p & 255;
                }
                out[y * w + x] = (r / 5) << 16 | (g / 5) << 8 | b / 5;
            }
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


    private static double dist(int a, int b) {
        return Math.abs((a >> 16 & 255) - (b >> 16 & 255)) + Math.abs((a >> 8 & 255) - (b >> 8 & 255)) + Math.abs((a & 255) - (b & 255));
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
    /* ===== Nuages de la météo ===== */

    /**
     * Nuages de la météo, dans l'esprit des nuages vaporeux des illustrations : nappes allongées à
     * base plate, bosses basses, bords adoucis, à peine ombrées dessous. Perspective : grands et
     * hauts dans le ciel, petits et étirés près de l'horizon. Blancs semi-transparents, puis teintés
     * selon le moment et posés derrière les montagnes et les arbres (masque du ciel).
     */
    static Bitmap weatherClouds(int width, int height) {
        return weatherClouds(width, height, false);
    }

    /** dense : couverture d'orage, nuages plus nombreux, plus gros, plus sombres dessous. */
    static Bitmap weatherClouds(int width, int height, boolean dense) {
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        float s = Math.max(width / (float) W, height / (float) H);
        cv.translate((width - W * s) / 2, (height - H * s) / 2);
        cv.scale(s, s);
        Random r = new Random(4242);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // x, base, taille (perspective : petits et étirés près de l'horizon)
        double[][] clouds = {{260, 210, 1.15}, {760, 150, 1.0}, {1260, 230, 1.2}, {1700, 140, .95},
                {520, 360, .7}, {1080, 390, .62}, {1520, 380, .66}, {180, 450, .45}, {880, 470, .42}, {1820, 470, .4}};
        if (dense) clouds = new double[][]{{60, 120, 1.4}, {460, 90, 1.5}, {900, 130, 1.45}, {1340, 100, 1.5}, {1780, 120, 1.4},
                {260, 240, 1.2}, {700, 260, 1.25}, {1150, 250, 1.3}, {1600, 260, 1.2}, {40, 340, .9}, {480, 380, .85},
                {920, 360, .9}, {1380, 390, .85}, {1860, 360, .9}, {250, 460, .55}, {720, 470, .5}, {1200, 480, .55}, {1650, 470, .5}};
        for (double[] cl : clouds) {
            double sc = cl[2], w = 380 * sc * (.85 + r.nextDouble() * .4), hgt = w * (.32 - (1 - sc) * .12);
            int n = 14 + r.nextInt(8);
            double[][] puffs = new double[n][];
            for (int k = 0; k < n; k++) { // bouffées : plus grosses et plus hautes au centre, base à peu près plate
                double u = r.nextDouble() * 2 - 1, cx = cl[0] + u * w * .5;
                double rad = hgt * (.28 + (1 - u * u) * .38) * (.7 + r.nextDouble() * .5);
                double cy = cl[1] - rad * (.35 + r.nextDouble() * .5);
                puffs[k] = new double[]{cx, cy, rad};
            }
            float blur = (float) (hgt * .22 + 4);
            // ombre du dessous, gris-bleu, décalée vers le bas
            p.setMaskFilter(new android.graphics.BlurMaskFilter(blur * 1.4f, android.graphics.BlurMaskFilter.Blur.NORMAL));
            p.setColor(alpha(c(dense ? "#7d8796" : "#aebbcc"), (dense ? .7 : .42) * (.6 + Math.min(1, sc) * .4)));
            for (double[] pf : puffs) cv.drawCircle((float) pf[0], (float) (pf[1] + pf[2] * .32), (float) (pf[2] * .95), p);
            // corps blanc, éclairé par le haut
            p.setMaskFilter(new android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL));
            p.setColor(alpha(c("#f4f7fb"), .5 * (.55 + sc * .45)));
            for (double[] pf : puffs) cv.drawCircle((float) pf[0], (float) pf[1], (float) (pf[2] * .82), p);
            p.setMaskFilter(new android.graphics.BlurMaskFilter(blur * .7f, android.graphics.BlurMaskFilter.Blur.NORMAL));
            p.setColor(alpha(WHITE, .45 * (.5 + sc * .5)));
            for (double[] pf : puffs) cv.drawCircle((float) (pf[0] - pf[2] * .12), (float) (pf[1] - pf[2] * .22), (float) (pf[2] * .55), p);
        }
        return out;
    }

    /** Opacité du ciel de l'illustration (masque), pour poser les nuages derrière le paysage. */
    static Bitmap skyAlpha(Context ctx, String seasonId, int width, int height) {
        Bitmap img = asset(ctx, image(seasonId));
        if (img == null) return null;
        int[] px = new int[W * H];
        img.getPixels(px, 0, W, 0, 0, W, H);
        img.recycle();
        int[] a = skyMask(ctx, px);
        for (int i = 0; i < a.length; i++) a[i] <<= 24;
        Bitmap m = Bitmap.createBitmap(a, W, H, Bitmap.Config.ARGB_8888);
        if (width == W && height == H) return m;
        Bitmap sc = Bitmap.createScaledBitmap(m, width, height, true);
        m.recycle();
        return sc;
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
