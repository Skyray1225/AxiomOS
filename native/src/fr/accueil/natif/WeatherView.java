package fr.accueil.natif;

import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.Random;

/**
 * Météo dans le paysage du thème Thinking, comme la version web : voile qui assombrit le ciel,
 * nuages, brume, pluie ou neige et éclairs. Chaque calque apparaît en fondu (6 s). Pluie et neige
 * s'animent par épisodes (à l'arrivée sur l'accueil, à chaque touche, puis 30 s toutes les 4 minutes),
 * à 30 images par seconde, et seulement si les animations sont activées.
 */
class WeatherView extends FrameLayout {

    static final String CLEAR = "clear", PARTLY = "partly", OVERCAST = "overcast", FOG = "fog",
            RAIN = "rain", SNOW = "snow", STORM = "storm";

    private static final int FADE_MS = 6000;
    private final Veil veil;
    private final ImageView clouds;
    private final Fog fog;
    private final Precip precip;
    private final View flash;
    private String kind = CLEAR;
    private double level;
    private boolean night, anim = true, running;
    private final Random rnd = new Random();

    WeatherView(Context ctx) {
        super(ctx);
        LayoutParams full = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        veil = new Veil(ctx);
        clouds = new ImageView(ctx);
        clouds.setScaleType(ImageView.ScaleType.FIT_XY);
        fog = new Fog(ctx);
        precip = new Precip(ctx);
        flash = new View(ctx);
        flash.setBackgroundColor(0xE6EBF2FF); // rgba(235, 242, 255, .9)
        addView(veil, new LayoutParams(full));
        addView(clouds, new LayoutParams(full));
        addView(fog, new LayoutParams(full));
        addView(precip, new LayoutParams(full)); // hauteur réduite à 70 % dans onLayout
        addView(flash, new LayoutParams(full));
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).setAlpha(0);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        // au-dessus du dock seulement, comme la version web
        precip.layout(0, 0, r - l, Math.round((b - t) * .7f));
    }

    void setCloudBitmap(Bitmap b) {
        clouds.setImageBitmap(b);
    }

    /** Code météo WMO -> ambiance du paysage. */
    static Object[] kindOf(int c) {
        if (c <= 1) return new Object[]{CLEAR, 0.0};
        if (c == 2) return new Object[]{PARTLY, 0.0};
        if (c == 3) return new Object[]{OVERCAST, 0.0};
        if (c == 45 || c == 48) return new Object[]{FOG, 0.0};
        if (c >= 51 && c <= 57) return new Object[]{RAIN, .45};
        if (c >= 61 && c <= 67) return new Object[]{RAIN, c >= 65 ? 1.3 : c >= 63 ? .9 : .55};
        if (c >= 80 && c <= 82) return new Object[]{RAIN, c == 82 ? 1.3 : c == 81 ? .9 : .6};
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return new Object[]{SNOW, c >= 75 || c == 86 ? 1.2 : .7};
        if (c >= 95) return new Object[]{STORM, 1.4};
        return new Object[]{PARTLY, 0.0};
    }

    String kind() { return kind; }

    void setWeather(int code, boolean animate) {
        Object[] k = kindOf(code);
        String nk = (String) k[0];
        double nl = (Double) k[1];
        boolean same = nk.equals(kind) && nl == level;
        kind = nk;
        level = nl;
        fadeTo(veil, veilAlpha(), animate);
        fadeTo(clouds, cloudAlpha(), animate);
        fadeTo(fog, FOG.equals(kind) ? 1 : 0, animate);
        if (!same || !running) syncMotion(true);
    }

    float veilAlpha() {
        switch (kind) {
            case OVERCAST: return .55f;
            case RAIN: case SNOW: return .8f;
            case STORM: return 1;
            case FOG: return .35f;
            default: return 0;
        }
    }

    float cloudAlpha() {
        return PARTLY.equals(kind) ? .55f : OVERCAST.equals(kind) || RAIN.equals(kind) || STORM.equals(kind) || SNOW.equals(kind) ? 1 : 0;
    }

    private static void fadeTo(View v, float a, boolean animate) {
        v.animate().cancel();
        if (animate) v.animate().alpha(a).setDuration(FADE_MS);
        else v.setAlpha(a);
    }

    void setNight(boolean n) {
        if (n == night && veil.ready) return;
        night = n;
        veil.update();
        fog.invalidate();
        // nuit : nuages assombris et désaturés (brightness .55, saturate .6)
        if (n) {
            ColorMatrix m = new ColorMatrix();
            m.setSaturation(.6f);
            ColorMatrix b = new ColorMatrix();
            b.setScale(.55f, .55f, .55f, 1);
            m.postConcat(b);
            clouds.setColorFilter(new ColorMatrixColorFilter(m));
        } else clouds.clearColorFilter();
        precip.night = n;
    }

    void setAnimations(boolean on) {
        anim = on;
        syncMotion(false);
    }

    /** Écran visible : pluie, neige et éclairs peuvent s'animer. */
    void start() {
        running = true;
        syncMotion(false);
    }

    void stop() {
        running = false;
        precip.halt();
        removeCallbacks(lightning);
        flash.animate().cancel();
        flash.setAlpha(0);
    }

    /** Épisode d'animation de `ms` millisecondes. */
    void burst(long ms) {
        precip.until = Math.max(precip.until, SystemClock.uptimeMillis() + ms);
        if (running && anim) precip.go(false);
    }

    private void syncMotion(boolean changed) {
        boolean moving = anim && running;
        String pk = STORM.equals(kind) ? RAIN : kind;
        boolean wantP = moving && (RAIN.equals(pk) || SNOW.equals(pk));
        precip.setAlpha(wantP ? 1 : 0);
        if (wantP) {
            int n = (int) Math.round((SNOW.equals(pk) ? 110 : 130) * level);
            if (changed || precip.n != n || !pk.equals(precip.kind)) precip.configure(pk, n);
            precip.go(true);
        } else precip.halt();
        removeCallbacks(lightning);
        if (STORM.equals(kind) && moving) postDelayed(lightning, 3000);
    }

    private final Runnable lightning = new Runnable() {
        @Override
        public void run() {
            // 0 % : 0 ; 5 % : .55 ; 12 % : .05 ; 18 % : .4 ; 100 % : 0, sur 1,1 s
            PropertyValuesHolder a = PropertyValuesHolder.ofKeyframe(View.ALPHA,
                    Keyframe.ofFloat(0, 0), Keyframe.ofFloat(.05f, .55f), Keyframe.ofFloat(.12f, .05f),
                    Keyframe.ofFloat(.18f, .4f), Keyframe.ofFloat(1, 0));
            ObjectAnimator.ofPropertyValuesHolder(flash, a).setDuration(1100).start();
            postDelayed(this, 7000 + rnd.nextInt(14000));
        }
    };

    /** Calques immobiles (voile, nuages, brume) dessinés par-dessus le fond du dock, à leur opacité finale. */
    void drawStatic(Canvas c, int w, int h) {
        float va = veilAlpha(), ca = cloudAlpha();
        if (va > 0) {
            Paint p = veil.paintFor(h);
            p.setAlpha(Math.round(va * 255));
            c.drawRect(0, 0, w, h, p);
        }
        if (ca > 0 && clouds.getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
            Bitmap b = ((android.graphics.drawable.BitmapDrawable) clouds.getDrawable()).getBitmap();
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            p.setAlpha(Math.round(ca * 255));
            p.setColorFilter(clouds.getColorFilter());
            c.drawBitmap(b, null, new android.graphics.Rect(0, 0, w, h), p);
        }
        if (FOG.equals(kind)) fog.paint(c, w, h);
    }

    /* ===== Voile : le ciel s'assombrit ===== */

    private class Veil extends View {
        final Paint p = new Paint();
        boolean ready;

        Veil(Context ctx) {
            super(ctx);
        }

        void update() {
            ready = true;
            if (getHeight() > 0) p.setShader(shader(getHeight()));
            invalidate();
        }

        Shader shader(int h) {
            return night
                    ? new LinearGradient(0, 0, 0, h, new int[]{0x8C080C16, 0x4D141A28, 0x0D141A28, 0x0D141A28}, new float[]{0, .5f, .85f, 1}, Shader.TileMode.CLAMP)
                    : new LinearGradient(0, 0, 0, h, new int[]{0x80606C7A, 0x47828C96, 0x0F969EA5, 0x0F969EA5}, new float[]{0, .45f, .8f, 1}, Shader.TileMode.CLAMP);
        }

        Paint paintFor(int h) {
            Paint q = new Paint();
            q.setShader(shader(h));
            return q;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            p.setShader(shader(h));
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawRect(0, 0, getWidth(), getHeight(), p);
        }
    }

    /* ===== Brume : bande claire en bas, deux nappes plus denses ===== */

    private class Fog extends View {
        Fog(Context ctx) {
            super(ctx);
        }

        @Override
        protected void onDraw(Canvas c) {
            paint(c, getWidth(), getHeight());
        }

        void paint(Canvas c, int w, int h) {
            float k = night ? .45f : 1; // la nuit, brightness(.45)
            int base = dim(0xECEFF1, k);
            Paint p = new Paint();
            p.setShader(new LinearGradient(0, 0, 0, h, new int[]{base, base | 0x8C000000, base | 0xBF000000, base | 0x73000000},
                    new float[]{.3f, .58f, .78f, 1}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            // ::before : inset 35 % -30 % 10 % -30 %, deux ellipses radiales
            float bl = -.3f * w, bt = .35f * h, bw = 1.6f * w, bh = .55f * h;
            int white = dim(0xFFFFFF, k);
            nappe(c, p, bl + .3f * bw, bt + .5f * bh, .3f * bw, .22f * bh, white | 0x8C000000);
            nappe(c, p, bl + .72f * bw, bt + .6f * bh, .28f * bw, .18f * bh, white | 0x80000000);
        }

        private void nappe(Canvas c, Paint p, float cx, float cy, float rx, float ry, int color) {
            RadialGradient g = new RadialGradient(0, 0, 1, new int[]{color, color & 0x00FFFFFF}, new float[]{0, .7f}, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setScale(rx, ry);
            m.postTranslate(cx, cy);
            g.setLocalMatrix(m);
            p.setShader(g);
            c.drawRect(cx - rx, cy - ry, cx + rx, cy + ry, p);
        }

        private int dim(int rgb, float k) {
            return Math.round((rgb >> 16 & 255) * k) << 16 | Math.round((rgb >> 8 & 255) * k) << 8 | Math.round((rgb & 255) * k);
        }
    }

    /* ===== Pluie et neige (coordonnées de la version web : demi-résolution, 70 % de la hauteur) ===== */

    static class Precip extends View {
        // 33 ms : 30 i/s, ~25 % de CPU pendant un épisode (0 = chaque image de l'écran, ~47 % ; 66 ms, ~13 %)
        static int frameMs = 33;
        private static final int BUCKETS = 8;
        String kind = RAIN;
        int n;
        boolean night;
        long until;
        private float[] x, y, a, b, ph; // pluie : longueur, vitesse ; neige : rayon, vitesse
        private boolean[] dead;
        private int alive;
        private boolean looping;
        private long last;
        private final Random rnd = new Random();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[][] lines = new float[BUCKETS][];
        private final int[] counts = new int[BUCKETS];

        Precip(Context ctx) {
            super(ctx);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (x != null) for (int i = 0; i < n; i++) if (!dead[i]) spawn(i, true); // configuré avant la mise en page
        }

        private float W() { return getWidth() / 2f; }

        private float H() { return getHeight() / 2f; }

        void configure(String k, int count) {
            kind = k;
            n = count;
            x = new float[n];
            y = new float[n];
            a = new float[n];
            b = new float[n];
            ph = new float[n];
            dead = new boolean[n];
            for (int i = 0; i < BUCKETS; i++) lines[i] = new float[n * 4];
            for (int i = 0; i < n; i++) spawn(i, true);
            alive = n;
        }

        private void spawn(int i, boolean initial) {
            float w = Math.max(1, W()), h = Math.max(1, H());
            dead[i] = false;
            if (SNOW.equals(kind)) {
                x[i] = rnd.nextFloat() * w;
                y[i] = initial ? rnd.nextFloat() * h : -5;
                a[i] = .8f + rnd.nextFloat() * 1.8f;
                b[i] = 18 + rnd.nextFloat() * 22;
                ph[i] = rnd.nextFloat() * 6.28f;
            } else {
                x[i] = rnd.nextFloat() * w * 1.2f;
                y[i] = initial ? rnd.nextFloat() * h : -20;
                a[i] = 9 + rnd.nextFloat() * 12;
                b[i] = 380 + rnd.nextFloat() * 220;
            }
        }

        /** Relance l'animation ; `refill` : les gouttes éteintes reviennent. */
        void go(boolean refill) {
            if (x == null) return;
            if (alive == 0 || refill) {
                for (int i = 0; i < n; i++) if (dead[i]) spawn(i, false);
                alive = n;
            }
            if (!looping) {
                looping = true;
                last = SystemClock.uptimeMillis();
                next(frame);
            }
        }

        private void next(Runnable r) {
            if (frameMs <= 0) postOnAnimation(r);
            else postDelayed(r, frameMs);
        }

        void halt() {
            looping = false;
            removeCallbacks(frame);
            alive = 0;
            if (dead != null) java.util.Arrays.fill(dead, true);
            invalidate();
        }

        private final Runnable frame = new Runnable() {
            @Override
            public void run() {
                if (!looping) return;
                long t = SystemClock.uptimeMillis();
                float dt = Math.min(.15f, (t - last) / 1000f);
                last = t;
                boolean active = t < until;
                float h = H();
                boolean snow = SNOW.equals(kind);
                for (int i = 0; i < n; i++) {
                    if (dead[i]) continue;
                    if (snow) {
                        y[i] += b[i] * dt;
                        ph[i] += dt;
                        x[i] += Math.sin(ph[i]) * 12 * dt;
                        if (y[i] > h + 5) { if (active) spawn(i, false); else { dead[i] = true; alive--; } }
                    } else {
                        y[i] += b[i] * dt;
                        x[i] -= b[i] * .18f * dt;
                        if (y[i] > h) { if (active) spawn(i, false); else { dead[i] = true; alive--; } }
                    }
                }
                invalidate();
                if (alive > 0) next(this);
                else looping = false; // fin de l'épisode : plus rien ne bouge
            }
        };

        @Override
        protected void onDraw(Canvas c) {
            if (x == null || alive == 0) return;
            float h = H();
            c.save();
            c.scale(2, 2);
            boolean snow = SNOW.equals(kind);
            // fondu juste avant le bas de la zone (sans coupure nette au-dessus du dock)
            if (snow) {
                int base = night ? 0xD9E1E8F5 : 0xEBFFFFFF;
                for (int i = 0; i < n; i++) {
                    if (dead[i]) continue;
                    paint.setColor(base);
                    paint.setAlpha(Math.round((base >>> 24) * fade(y[i], h)));
                    c.drawCircle(x[i], y[i], a[i], paint);
                }
            } else {
                int base = night ? 0x59BECDEB : 0x73FFFFFF;
                paint.setStrokeWidth(1);
                java.util.Arrays.fill(counts, 0);
                for (int i = 0; i < n; i++) {
                    if (dead[i]) continue;
                    int k = Math.min(BUCKETS - 1, (int) ((1 - fade(y[i], h)) * BUCKETS));
                    float[] L = lines[k];
                    int j = counts[k];
                    L[j] = x[i];
                    L[j + 1] = y[i];
                    L[j + 2] = x[i] + a[i] * .18f;
                    L[j + 3] = y[i] - a[i];
                    counts[k] = j + 4;
                }
                for (int k = 0; k < BUCKETS; k++) {
                    if (counts[k] == 0) continue;
                    paint.setColor(base);
                    paint.setAlpha(Math.round((base >>> 24) * (1 - k / (float) BUCKETS)));
                    c.drawLines(lines[k], 0, counts[k], paint);
                }
            }
            c.restore();
        }

        private static float fade(float y, float h) {
            float f = (y - h * .72f) / (h * .28f);
            return f <= 0 ? 1 : Math.max(0, 1 - f);
        }
    }
}
