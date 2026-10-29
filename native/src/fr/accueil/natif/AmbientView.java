package fr.accueil.natif;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fond du thème Ambient : un halo des couleurs dominantes de l'appli sélectionnée, en fondu de 0,35 s
 * entre deux calques, assombri en haut et en bas. Les couleurs sont tirées de la bannière comme dans
 * la version web (histogramme des teintes pondéré par la saturation).
 */
class AmbientView extends FrameLayout {

    private final Layer[] layers = new Layer[2];
    private int on = 0;
    private String current;

    AmbientView(Context ctx) {
        super(ctx);
        setBackgroundColor(0xFF0B0C10);
        for (int i = 0; i < 2; i++) {
            layers[i] = new Layer(ctx);
            layers[i].setAlpha(0);
            addView(layers[i], new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        addView(new Shade(ctx), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    /** Couleurs de l'appli `key` ; fondu sauf au premier affichage. */
    void show(String key, float[][] pal, boolean instant) {
        if (key.equals(current)) return;
        if (current == null) instant = true; // premier affichage : sans fondu
        current = key;
        Layer cur = layers[on], next = layers[on ^ 1];
        next.setPalette(pal);
        next.animate().cancel();
        cur.animate().cancel();
        if (instant) {
            next.setAlpha(1);
            cur.setAlpha(0);
        } else {
            next.animate().alpha(1).setDuration(350);
            cur.animate().alpha(0).setDuration(350);
        }
        on ^= 1;
    }

    /* ===== Couleurs dominantes d'une bannière ===== */

    /** Renvoie {c1, c2, c3} en HSL (teinte en degrés, saturation et luminosité entre 0 et 1). */
    static float[][] palette(Bitmap banner) {
        List<float[]> hsl = new ArrayList<>();
        if (banner != null) {
            Bitmap small = Bitmap.createScaledBitmap(banner, 40, 22, true);
            int[] px = new int[40 * 22];
            small.getPixels(px, 0, 40, 0, 0, 40, 22);
            if (small != banner) small.recycle();
            for (int p : px) hsl.add(toHsl((p >> 16) & 255, (p >> 8) & 255, p & 255));
        }
        float[][] bins = new float[18][4]; // poids, teinte, saturation, luminosité (pondérées)
        for (float[] c : hsl) {
            float h = c[0], s = c[1], l = c[2];
            if (s < .18f || l < .08f || l > .92f) continue; // gris
            float[] b = bins[(int) (h / 20) % 18];
            float w = s * (1 - Math.abs(l - .5f));
            b[0] += w;
            b[1] += h * w;
            b[2] += s * w;
            b[3] += l * w;
        }
        List<float[]> top = new ArrayList<>();
        for (float[] b : bins) if (b[0] > 0) top.add(new float[]{b[0], b[1] / b[0], b[2] / b[0], b[3] / b[0]});
        Collections.sort(top, (x, y) -> Float.compare(y[0], x[0]));
        // couleurs vives et assez sombres pour un fond ; à défaut, des gris bleutés
        float[] c1 = top.isEmpty() ? new float[]{0, 220, .12f, .4f} : top.get(0);
        float[] c2 = top.size() > 1 && Math.abs(top.get(1)[1] - c1[1]) > 25 ? top.get(1)
                : new float[]{0, (c1[1] + 35) % 360, c1[2], c1[3]};
        boolean any = !top.isEmpty();
        return new float[][]{tone(c1, .42f, any), tone(c2, .3f, any), tone(c1, .16f, any)};
    }

    private static float[] tone(float[] c, float l, boolean vivid) {
        float s = vivid ? Math.max(.5f, Math.min(.85f, c[2])) : c[2];
        return new float[]{c[1], s, l};
    }

    private static float[] toHsl(int r8, int g8, int b8) {
        float r = r8 / 255f, g = g8 / 255f, b = b8 / 255f;
        float mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b)), l = (mx + mn) / 2, d = mx - mn;
        if (d == 0) return new float[]{0, 0, l};
        float s = d / (1 - Math.abs(2 * l - 1));
        float h = mx == r ? ((g - b) / d) % 6 : mx == g ? (b - r) / d + 2 : (r - g) / d + 4;
        h *= 60;
        if (h < 0) h += 360;
        return new float[]{h, s, l};
    }

    /** Couleur ARGB depuis HSL et une opacité. */
    static int hsl(float h, float s, float l, float a) {
        float c = (1 - Math.abs(2 * l - 1)) * s, x = c * (1 - Math.abs((h / 60) % 2 - 1)), m = l - c / 2;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; } else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; } else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; } else { r = c; g = 0; b = x; }
        return Math.round(a * 255) << 24 | Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
    }

    /** Un calque : dégradé de fond et trois halos elliptiques (dessinés du dessous vers le dessus). */
    private static class Layer extends View {
        private final Paint base = new Paint(), p1 = new Paint(), p2 = new Paint(), p3 = new Paint();
        private float[][] pal;

        Layer(Context ctx) {
            super(ctx);
        }

        void setPalette(float[][] pal) {
            this.pal = pal;
            build(getWidth(), getHeight());
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            build(w, h);
        }

        private void build(int w, int h) {
            if (pal == null || w == 0) return;
            float[] c1 = pal[0], c2 = pal[1], c3 = pal[2];
            // le calque web déborde de 10 % de chaque côté : les positions sont relatives à cette zone
            float W = w * 1.2f, H = h * 1.2f, ox = -.1f * w, oy = -.1f * h;
            // linear-gradient(160deg, c3, #07080b)
            double a = Math.toRadians(160 - 90);
            float dx = (float) Math.cos(a) * W / 2, dy = (float) Math.sin(a) * H / 2;
            base.setShader(new LinearGradient(w / 2f - dx, h / 2f - dy, w / 2f + dx, h / 2f + dy,
                    hsl(c3[0], c3[1], c3[2], 1), 0xFF07080B, Shader.TileMode.CLAMP));
            p3.setShader(ellipse(ox + .6f * W, oy + H, .5f * W, .5f * H, hsl(c2[0], c2[1], .22f, .8f)));
            p2.setShader(ellipse(ox + .85f * W, oy + .2f * H, .6f * W, .65f * H, hsl(c2[0], c2[1], c2[2], .9f)));
            p1.setShader(ellipse(ox + .15f * W, oy + .85f * H, .65f * W, .75f * H, hsl(c1[0], c1[1], c1[2], .95f)));
        }

        /** radial-gradient(ellipse rx ry at cx cy, couleur, transparent 70%) */
        private static Shader ellipse(float cx, float cy, float rx, float ry, int color) {
            RadialGradient g = new RadialGradient(0, 0, 1, new int[]{color, color & 0x00FFFFFF},
                    new float[]{0, .7f}, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setScale(rx, ry);
            m.postTranslate(cx, cy);
            g.setLocalMatrix(m);
            return g;
        }

        @Override
        protected void onDraw(Canvas c) {
            if (pal == null) return;
            int w = getWidth(), h = getHeight();
            c.drawRect(0, 0, w, h, base);
            c.drawRect(0, 0, w, h, p3);
            c.drawRect(0, 0, w, h, p2);
            c.drawRect(0, 0, w, h, p1);
        }
    }

    /** Assombrit le haut et le bas : linear-gradient(180deg, rgba(0,0,0,.25), transparent 40%, rgba(0,0,0,.35)). */
    private static class Shade extends View {
        private final Paint p = new Paint();

        Shade(Context ctx) {
            super(ctx);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            p.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x40000000, 0, 0x59000000}, new float[]{0, .4f, 1}, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawRect(0, 0, getWidth(), getHeight(), p);
        }
    }
}
