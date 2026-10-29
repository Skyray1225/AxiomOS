package fr.accueil.natif;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Vitrine du thème Ambient : la bannière de l'appli sélectionnée en grand, sur une carte inclinée
 * en 3D (rotateY -16°, rotateX 3°), avec un reflet fixe dessous et une lueur de sa couleur.
 * Au changement d'appli, la carte pivote un peu en s'effaçant, puis revient avec la nouvelle bannière.
 */
class AmbientHero extends FrameLayout {

    private final ImageView card, refl;
    private final float vw;
    private final int cw, ch;
    private Runnable pending;

    AmbientHero(Context ctx, float vw, int cardW, int cardH) {
        super(ctx);
        this.vw = vw;
        cw = cardW;
        ch = cardH;
        setClipChildren(false);
        setAlpha(0);
        final float r = 1.6f * vw;
        card = new ImageView(ctx);
        card.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
            }
        });
        card.setClipToOutline(true);
        card.setElevation(3 * vw);
        refl = new ImageView(ctx);
        refl.setScaleType(ImageView.ScaleType.FIT_XY);
        refl.setAlpha(.16f);
        LayoutParams rl = new LayoutParams(cw, ch);
        rl.topMargin = ch + Math.round(1.2f * vw);
        addView(refl, rl);
        addView(card, new LayoutParams(cw, ch));
        // perspective(1400px) : distance de la caméra, en « pixels de profondeur » × densité
        setCameraDistance(1400 * getResources().getDisplayMetrics().density * 1.6f);
        setRotationY(-16);
        setRotationX(3);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        setPivotX(cw / 2f);
        setPivotY(ch / 2f);
    }

    /** Nouvelle bannière (null : pas de vitrine) et couleur de la lueur. */
    void show(final Bitmap banner, final int glow, boolean instant) {
        if (pending != null) removeCallbacks(pending);
        pending = () -> {
            card.setImageBitmap(banner);
            refl.setImageBitmap(banner == null ? null : reflection(banner));
            card.setOutlineSpotShadowColor(glow);
            card.setOutlineAmbientShadowColor(glow);
            animate().alpha(banner == null ? 0 : 1).rotationY(-16).translationX(0).setDuration(300).setInterpolator(MainActivity.EASE);
        };
        animate().cancel();
        if (instant) {
            pending.run();
            animate().cancel();
            setAlpha(banner == null ? 0 : 1);
            setRotationY(-16);
            setTranslationX(0);
            return;
        }
        animate().alpha(0).rotationY(-20).translationX(2 * vw).setDuration(120).setInterpolator(MainActivity.EASE);
        postDelayed(pending, 120);
    }

    /** Copie retournée, opaque contre la carte et effacée au-delà de 30 % de sa hauteur. */
    private static Bitmap reflection(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Matrix flip = new Matrix();
        flip.setScale(1, -1);
        flip.postTranslate(0, h);
        c.drawBitmap(src, flip, new Paint(Paint.FILTER_BITMAP_FLAG));
        Paint mask = new Paint();
        mask.setShader(new LinearGradient(0, 0, 0, .3f * h, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawRect(0, 0, w, h, mask);
        return out;
    }
}
