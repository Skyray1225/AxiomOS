package fr.accueil.natif;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;

/**
 * Une tuile du dock : la bannière de l'appli, le contour blanc (fondu) et la lueur (un passage).
 * Le zoom passe par les propriétés de la vue : déplacé par le compositeur, sans redessiner l'image.
 */
class TileView extends View {

    private static final float SKEW = (float) Math.tan(Math.toRadians(-18));

    private final Bitmap banner;
    private final float radius;
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shinePaint = new Paint();
    private final RectF bounds = new RectF();

    private float ring = 0f;   // opacité du contour, 0..1
    private float shine = -1f; // avancée de la lueur, 0..1 ; -1 = cachée
    private ValueAnimator ringAnim, shineAnim;

    TileView(Context ctx, Bitmap banner, float radius, float ringWidth, float elevation) {
        super(ctx);
        this.banner = banner;
        this.radius = radius;
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(ringWidth * 2); // le trait est centré sur le bord : la moitié intérieure reste visible
        ringPaint.setColor(0xFFFFFFFF);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), TileView.this.radius);
            }
        });
        setClipToOutline(true);
        setElevation(elevation);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        bounds.set(0, 0, w, h);
        float sw = .4f * w;
        shinePaint.setShader(new LinearGradient(-sw / 2, 0, sw / 2, 0,
                new int[]{0x00FFFFFF, 0x8CFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        if (banner != null) c.drawBitmap(banner, null, bounds, bmpPaint);
        else c.drawColor(0xFF2A2D33);
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
    }

    void setSelected(boolean on, boolean animate) {
        if (ringAnim != null) ringAnim.cancel();
        float scale = on ? 1.04f : 1f;
        if (!animate) {
            ring = on ? 1 : 0;
            setScaleX(scale);
            setScaleY(scale);
            invalidate();
            return;
        }
        ringAnim = ValueAnimator.ofFloat(ring, on ? 1 : 0).setDuration(180);
        ringAnim.addUpdateListener(a -> { ring = (float) a.getAnimatedValue(); invalidate(); });
        ringAnim.start();
        animate().scaleX(scale).scaleY(scale).setDuration(250).setInterpolator(MainActivity.EASE);
        if (on) shineOnce();
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
}
