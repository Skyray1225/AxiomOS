package fr.accueil.natif;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.PathInterpolator;

/**
 * Une tuile du dock : la bannière de l'appli, le contour blanc (fondu) et la lueur (un passage).
 * En mode édition : tuile sélectionnée qui se dandine, autres tuiles atténuées, pastilles « épinglée »
 * et « masquée ». Zoom, déplacement et rotation passent par les propriétés de la vue :
 * le compositeur les applique sans redessiner l'image.
 */
class TileView extends View implements DockItem {

    /** Dimensions propres au thème (en vw) et zoom de la tuile sélectionnée. */
    static final class Style {
        final float radius, ring, focusScale, editScale, editDy, elevation;
        final boolean kids; // Kids : bordure blanche, tuile inclinée qui rebondit, pas de contour ni de lueur

        Style(float radius, float ring, float focusScale, float editScale, float editDy, float elevation, boolean kids) {
            this.radius = radius;
            this.ring = ring;
            this.focusScale = focusScale;
            this.editScale = editScale;
            this.editDy = editDy;
            this.elevation = elevation;
            this.kids = kids;
        }
    }

    static final Style CAROUSEL = new Style(1.4f, .18f, 1.04f, 1.08f, -1.2f, .5f, false);
    static final Style GRID = new Style(1.1f, .22f, 1.06f, 1.08f, 0, .8f, false);
    static final Style KIDS = new Style(2.2f, 0, 1.08f, 1.08f, 0, 1.2f, true);
    private static final android.animation.TimeInterpolator BOUNCE = new PathInterpolator(.3f, 1.6f, .5f, 1f);

    private static final float SKEW = (float) Math.tan(Math.toRadians(-18));
    private static final ColorMatrixColorFilter GREY, DIM;

    static {
        ColorMatrix g = new ColorMatrix();
        g.setSaturation(0);
        GREY = new ColorMatrixColorFilter(g);
        ColorMatrix d = new ColorMatrix();
        d.setSaturation(.6f);
        ColorMatrix b = new ColorMatrix();
        b.setScale(.9f, .9f, .9f, 1);
        d.postConcat(b);
        DIM = new ColorMatrixColorFilter(d);
    }

    private final Bitmap banner;
    private final float radius, vw;
    private final Style style;
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shinePaint = new Paint();
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Drawable pinIcon, eyeIcon;

    private float ring = 0f;   // opacité du contour, 0..1
    private float shine = -1f; // avancée de la lueur, 0..1 ; -1 = cachée
    private ValueAnimator ringAnim, shineAnim;
    private ObjectAnimator wiggle;
    private boolean selected, editing, pinned, hiddenApp;
    private float tiltRot, tiltDy; // Kids : inclinaison propre à chaque appli
    private int kidState;          // Kids, choix des applis : 0 rien, 1 autorisée, 2 non autorisée
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int frame = 0xFFFF6B6B; // Kids : couleur du cadre propre à l'appli
    private int deco;               // Kids : 0 cadre simple, 1 fleurs, 2 papier déchiré, 3 néon, 4 coutures
    private long seed;              // Kids : tirage propre à l'appli (fleurs, déchirure)
    private android.graphics.Path torn, round;

    TileView(Context ctx, Bitmap banner, float vw, Style style) {
        super(ctx);
        this.banner = banner;
        this.vw = vw;
        this.style = style;
        this.radius = style.radius * vw;
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2 * style.ring * vw); // trait centré sur le bord : la moitié intérieure reste visible
        ringPaint.setColor(0xFFFFFFFF);
        pinIcon = ctx.getDrawable(R.drawable.ic_pin);
        eyeIcon = ctx.getDrawable(R.drawable.ic_eye_off);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        setClipToOutline(!style.kids); // Kids : les décorations dépassent du cadre (fleurs, papier, néon)
        setElevation(style.elevation * vw);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        bounds.set(0, 0, w, h);
        round = new android.graphics.Path();
        round.addRoundRect(bounds, radius, radius, android.graphics.Path.Direction.CW);
        if (style.kids) torn = tornPaper(w, h);
        setPivotX(w / 2f);
        setPivotY(h / 2f);
        float sw = .4f * w;
        shinePaint.setShader(new LinearGradient(-sw / 2, 0, sw / 2, 0,
                new int[]{0x00FFFFFF, 0x8CFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        bmpPaint.setColorFilter(hiddenApp || kidState == 2 ? GREY : editing && !selected ? DIM : null);
        if (style.kids && deco == 2 && torn != null) { // papier déchiré, posé sous la tuile
            badgePaint.setColor(0x33000000);
            c.save();
            c.translate(.25f * vw, .35f * vw);
            c.drawPath(torn, badgePaint);
            c.restore();
            badgePaint.setColor(0xFFFFFCF4);
            c.drawPath(torn, badgePaint);
        }
        if (style.kids) {
            c.save();
            c.clipPath(round); // coins arrondis (la tuile Kids ne coupe pas ses décorations)
        }
        if (banner != null) c.drawBitmap(banner, null, bounds, bmpPaint);
        else c.drawColor(0xFF2A2D33);
        if (style.kids) c.restore();
        if (style.kids && deco != 0) {
            decorate(c);
            if (kidState == 1) checkBadge(c);
            return;
        }
        if (style.kids) { // cadre de couleur propre à l'appli, liseré blanc ; sélectionnée : cadre épais et vif
            borderPaint.setStyle(Paint.Style.STROKE);
            float fw = (selected ? 1.3f : .75f) * vw;
            borderPaint.setColor(selected ? frame : (frame & 0x00FFFFFF) | 0xD9000000);
            borderPaint.setStrokeWidth(2 * fw); // centré sur le bord : la moitié intérieure se voit
            c.drawRoundRect(bounds, radius, radius, borderPaint);
            borderPaint.setColor(0xFFFFFFFF);
            borderPaint.setStrokeWidth(.28f * vw);
            RectF in = new RectF(bounds);
            in.inset(fw, fw);
            float ir = Math.max(0, radius - fw);
            c.drawRoundRect(in, ir, ir, borderPaint);
            if (kidState == 1) checkBadge(c);
            return;
        }
        if (shine >= 0) {
            float w = getWidth(), h = getHeight(), sw = .4f * w;
            c.save();
            c.translate((-1.5f + 4.8f * shine) * sw + sw / 2, h / 2);
            c.skew(SKEW, 0);
            c.drawRect(-sw / 2, -.7f * h, sw / 2, .7f * h, shinePaint);
            c.restore();
        }
        if (ring > 0) {
            ringPaint.setAlpha(Math.round(255 * ring));
            c.drawRoundRect(bounds, radius, radius, ringPaint);
        }
        float s = 1.8f * vw, m = .5f * vw;
        if (editing && pinned) badge(c, m, m, s, 0xFFFFCF3A, pinIcon, .35f * vw);
        if (hiddenApp) badge(c, getWidth() - m - s, m, s, 0x8C141E19, eyeIcon, .3f * vw);
    }

    /** ✓ vert : appli autorisée (choix des applis du mode Kids). */
    private void checkBadge(Canvas c) {
        float d = 2 * vw, x = getWidth() - .6f * vw - d, y = .6f * vw;
        badgePaint.setColor(0xFF2FB36B);
        c.drawCircle(x + d / 2, y + d / 2, d / 2, badgePaint);
        badgePaint.setColor(0xFFFFFFFF);
        badgePaint.setTextSize(1.2f * vw);
        badgePaint.setFakeBoldText(true);
        badgePaint.setTextAlign(Paint.Align.CENTER);
        c.drawText("✓", x + d / 2, y + d / 2 + .42f * vw, badgePaint);
    }

    /** Kids : style de cadre (tiré une fois pour toutes par appli). */
    void setDecoration(int style, long seed) {
        deco = style;
        this.seed = seed;
        if (getWidth() > 0) torn = tornPaper(getWidth(), getHeight());
        invalidate();
    }

    /** Bord de papier déchiré : contour irrégulier qui déborde de la tuile. */
    private android.graphics.Path tornPaper(int w, int h) {
        java.util.Random r = new java.util.Random(seed);
        float m = .9f * vw, step = .55f * vw;
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(-m, -m);
        for (float x = -m; x < w + m; x += step) p.lineTo(x, -m + (r.nextFloat() - .5f) * .7f * vw);
        for (float y = -m; y < h + m; y += step) p.lineTo(w + m + (r.nextFloat() - .5f) * .7f * vw, y);
        for (float x = w + m; x > -m; x -= step) p.lineTo(x, h + m + (r.nextFloat() - .5f) * .7f * vw);
        for (float y = h + m; y > -m; y -= step) p.lineTo(-m + (r.nextFloat() - .5f) * .7f * vw, y);
        p.close();
        return p;
    }

    /** Décorations du cadre : fleurs, papier déchiré (liseré), néon, coutures. Plus vives à la sélection. */
    private void decorate(Canvas c) {
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setPathEffect(null);
        borderPaint.setMaskFilter(null);
        float w = getWidth(), h = getHeight();
        switch (deco) {
            case 1: { // couronne de fleurs aux coins et sur les bords
                borderPaint.setColor(frame);
                borderPaint.setStrokeWidth((selected ? 1.6f : 1f) * vw);
                c.drawRoundRect(bounds, radius, radius, borderPaint);
                java.util.Random r = new java.util.Random(seed);
                int[] petals = {0xFFFFFFFF, 0xFFFFB3C7, 0xFFFFE066, 0xFFB197FC, 0xFF8CE99A};
                float[][] spots = {{0, 0}, {w, 0}, {0, h}, {w, h}, {w * .5f, 0}, {w * .5f, h}, {0, h * .5f}, {w, h * .5f}};
                int n = 5 + r.nextInt(3);
                for (int k = 0; k < n; k++) {
                    float[] sp = spots[k];
                    float fx = sp[0] + (r.nextFloat() - .5f) * vw, fy = sp[1] + (r.nextFloat() - .5f) * vw;
                    flower(c, fx, fy, (.9f + r.nextFloat() * .6f) * vw * (selected ? 1.15f : 1), petals[r.nextInt(petals.length)], r.nextFloat() * 72);
                }
                break;
            }
            case 2: // papier déchiré : liseré coloré fin à l'intérieur
                borderPaint.setColor(frame);
                borderPaint.setStrokeWidth((selected ? .9f : .45f) * vw);
                c.drawRoundRect(bounds, radius, radius, borderPaint);
                break;
            case 3: { // néon : tube lumineux et halo de sa couleur (plus intense à la sélection)
                int glow = selected ? 0xE6000000 : 0x8C000000;
                borderPaint.setColor((frame & 0x00FFFFFF) | glow);
                borderPaint.setStrokeWidth((selected ? 1.6f : 1.1f) * vw);
                borderPaint.setMaskFilter(new android.graphics.BlurMaskFilter((selected ? 1.4f : .9f) * vw, android.graphics.BlurMaskFilter.Blur.NORMAL));
                c.drawRoundRect(bounds, radius, radius, borderPaint);
                borderPaint.setMaskFilter(null);
                borderPaint.setColor(Landscape.mix(frame, 0xFFFFFFFF, .55));
                borderPaint.setStrokeWidth(.32f * vw);
                c.drawRoundRect(bounds, radius, radius, borderPaint);
                break;
            }
            default: { // coutures : cadre de tissu coloré, points de couture blancs
                float fw = (selected ? 1.3f : .85f) * vw;
                borderPaint.setColor(frame);
                borderPaint.setStrokeWidth(2 * fw);
                c.drawRoundRect(bounds, radius, radius, borderPaint);
                borderPaint.setColor(0xF2FFFFFF);
                borderPaint.setStrokeWidth(.22f * vw);
                borderPaint.setStrokeCap(Paint.Cap.ROUND);
                borderPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{.55f * vw, .45f * vw}, 0));
                RectF in = new RectF(bounds);
                in.inset(fw * .5f, fw * .5f);
                c.drawRoundRect(in, radius * .8f, radius * .8f, borderPaint);
                borderPaint.setPathEffect(null);
                borderPaint.setStrokeCap(Paint.Cap.BUTT);
            }
        }
    }

    private void flower(Canvas c, float x, float y, float r, int petal, float rot) {
        c.save();
        c.translate(x, y);
        c.rotate(rot);
        badgePaint.setColor(0x33000000);
        c.drawCircle(r * .12f, r * .18f, r * .95f, badgePaint);
        badgePaint.setColor(petal);
        for (int k = 0; k < 5; k++) {
            double a = k * Math.PI * 2 / 5;
            c.drawCircle((float) Math.cos(a) * r * .55f, (float) Math.sin(a) * r * .55f, r * .45f, badgePaint);
        }
        badgePaint.setColor(0xFFFFC93C);
        c.drawCircle(0, 0, r * .32f, badgePaint);
        c.restore();
    }

    private void badge(Canvas c, float x, float y, float s, int color, Drawable icon, float pad) {
        badgePaint.setColor(color);
        c.drawCircle(x + s / 2, y + s / 2, s / 2, badgePaint);
        icon.setBounds(Math.round(x + pad), Math.round(y + pad), Math.round(x + s - pad), Math.round(y + s - pad));
        icon.draw(c);
    }

    /** Kids : couleur du cadre, et lueur de la même couleur autour de la tuile sélectionnée. */
    void setFrameColor(int color) {
        frame = color;
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            setOutlineSpotShadowColor(color);
            setOutlineAmbientShadowColor(color);
        }
        invalidate();
    }

    int frameColor() {
        return frame;
    }

    /** Kids : inclinaison (degrés) et décalage vertical (px) propres à l'appli. */
    void setTilt(float rot, float dy) {
        tiltRot = rot;
        tiltDy = dy;
        updateTransform(false);
    }

    /** Kids, choix des applis autorisées : 0 normal, 1 autorisée (✓), 2 non autorisée (grisée). */
    void setKidState(int state) {
        kidState = state;
        setAlpha(state == 2 ? .45f : 1f);
        invalidate();
    }

    /** Appli épinglée / masquée (pastilles et grisé). */
    @Override
    public void setFlags(boolean pinned, boolean hiddenApp) {
        this.pinned = pinned;
        this.hiddenApp = hiddenApp;
        setAlpha(hiddenApp ? .55f : 1f);
        invalidate();
    }

    @Override
    public void setEditing(boolean on) {
        editing = on;
        updateTransform(false);
        invalidate();
    }

    @Override
    public void setSelected(boolean on, boolean animate, boolean withShine) {
        selected = on;
        if (ringAnim != null) ringAnim.cancel();
        if (style.kids) {
            invalidate();
        } else if (!animate) {
            ring = on ? 1 : 0;
            invalidate();
        } else {
            ringAnim = ValueAnimator.ofFloat(ring, on ? 1 : 0).setDuration(180);
            ringAnim.addUpdateListener(a -> { ring = (float) a.getAnimatedValue(); invalidate(); });
            ringAnim.start();
            if (on && withShine && !editing) shineOnce();
        }
        updateTransform(animate);
    }

    /** Zoom de la tuile sélectionnée ; en édition, elle se soulève et se dandine. */
    private void updateTransform(boolean animate) {
        if (style.kids) { // se redresse un peu, monte et grossit avec un petit rebond
            float scale = selected ? 1.1f : 1f, dy = tiltDy - (selected ? .8f * vw : 0), rot = selected ? 0 : tiltRot;
            setTranslationZ(selected ? 2.4f * vw : 0); // ombre colorée plus large : la tuile « brille »
            animate().cancel();
            if (animate && selected) {
                setScaleX(.96f);
                setScaleY(.96f);
                animate().scaleX(scale).scaleY(scale).translationY(dy).rotation(rot).setDuration(500).setInterpolator(BOUNCE);
            } else if (animate) {
                animate().scaleX(scale).scaleY(scale).translationY(dy).rotation(rot).setDuration(250).setInterpolator(MainActivity.EASE);
            } else {
                setScaleX(scale);
                setScaleY(scale);
                setTranslationY(dy);
                setRotation(rot);
            }
            return;
        }
        boolean wig = editing && selected;
        float scale = wig ? style.editScale : selected ? style.focusScale : 1f, dy = wig ? style.editDy * vw : 0;
        setTranslationZ(selected ? vw : 0); // la tuile sélectionnée passe devant ses voisines
        if (wig && wiggle == null) {
            wiggle = ObjectAnimator.ofFloat(this, View.ROTATION, -1.2f, 1.2f).setDuration(500);
            wiggle.setRepeatCount(ValueAnimator.INFINITE);
            wiggle.setRepeatMode(ValueAnimator.REVERSE);
            wiggle.setInterpolator(new AccelerateDecelerateInterpolator());
            wiggle.start();
        } else if (!wig && wiggle != null) {
            wiggle.cancel();
            wiggle = null;
            setRotation(0);
        }
        animate().cancel();
        if (animate) {
            animate().scaleX(scale).scaleY(scale).translationY(dy).setDuration(250).setInterpolator(MainActivity.EASE);
        } else {
            setScaleX(scale);
            setScaleY(scale);
            setTranslationY(dy);
        }
    }

    private void shineOnce() {
        if (shineAnim != null) shineAnim.cancel();
        shineAnim = ValueAnimator.ofFloat(0, 1).setDuration(600);
        shineAnim.setStartDelay(100);
        shineAnim.setInterpolator(new PathInterpolator(0f, 0f, .58f, 1f)); // ease-out
        shineAnim.addUpdateListener(a -> {
            shine = a.getAnimatedFraction() >= 1 ? -1 : (float) a.getAnimatedValue();
            invalidate();
        });
        shineAnim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (wiggle != null) wiggle.cancel();
        if (shineAnim != null) shineAnim.cancel();
        if (ringAnim != null) ringAnim.cancel();
        super.onDetachedFromWindow();
    }
}
