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
        setClipToOutline(true);
        setElevation(style.elevation * vw);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        bounds.set(0, 0, w, h);
        setPivotX(w / 2f);
        setPivotY(h / 2f);
        float sw = .4f * w;
        shinePaint.setShader(new LinearGradient(-sw / 2, 0, sw / 2, 0,
                new int[]{0x00FFFFFF, 0x8CFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        bmpPaint.setColorFilter(hiddenApp || kidState == 2 ? GREY : editing && !selected ? DIM : null);
        if (banner != null) c.drawBitmap(banner, null, bounds, bmpPaint);
        else c.drawColor(0xFF2A2D33);
        if (style.kids) { // bordure blanche ; sélectionnée : jaune, entourée d'orange
            borderPaint.setStyle(Paint.Style.STROKE);
            if (selected) {
                borderPaint.setColor(0xFFFFCF3A);
                borderPaint.setStrokeWidth(1.5f * vw);
                c.drawRoundRect(bounds, radius, radius, borderPaint);
                borderPaint.setColor(0xFFFF9F1C);
                borderPaint.setStrokeWidth(.8f * vw);
            } else {
                borderPaint.setColor(0xFFFFFFFF);
                borderPaint.setStrokeWidth(.7f * vw);
            }
            c.drawRoundRect(bounds, radius, radius, borderPaint);
            if (kidState == 1) { // ✓ vert : appli autorisée
                float d = 2 * vw, x = getWidth() - .6f * vw - d, y = .6f * vw;
                badgePaint.setColor(0xFF2FB36B);
                c.drawCircle(x + d / 2, y + d / 2, d / 2, badgePaint);
                badgePaint.setColor(0xFFFFFFFF);
                badgePaint.setTextSize(1.2f * vw);
                badgePaint.setFakeBoldText(true);
                badgePaint.setTextAlign(Paint.Align.CENTER);
                c.drawText("✓", x + d / 2, y + d / 2 + .42f * vw, badgePaint);
            }
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

    private void badge(Canvas c, float x, float y, float s, int color, Drawable icon, float pad) {
        badgePaint.setColor(color);
        c.drawCircle(x + s / 2, y + s / 2, s / 2, badgePaint);
        icon.setBounds(Math.round(x + pad), Math.round(y + pad), Math.round(x + s - pad), Math.round(y + s - pad));
        icon.draw(c);
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
            float scale = selected ? style.focusScale : 1f, dy = tiltDy - (selected ? .6f * vw : 0), rot = selected ? tiltRot * .2f : tiltRot;
            setTranslationZ(selected ? vw : 0);
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
