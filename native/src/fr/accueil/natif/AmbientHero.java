package fr.accueil.natif;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.widget.ImageView;

/**
 * Vitrine du thème Ambient : la bannière de l'appli sélectionnée en grand, sur une carte inclinée
 * en 3D (rotateY -16°, rotateX 3°), avec une lueur de sa couleur, une ombre et un reflet discret.
 * Carte, reflet, lueur et ombre sont dessinés une fois dans une image entourée d'une marge
 * transparente : inclinée, l'image garde des bords lissés (une vue découpée serait crénelée).
 */
class AmbientHero extends ImageView {

    final int margin, cw, ch;
    private final float vw;
    private Runnable pending;

    AmbientHero(Context ctx, float vw, int cardW, int cardH) {
        super(ctx);
        this.vw = vw;
        cw = cardW;
        ch = cardH;
        margin = Math.round(5 * vw);
        setScaleType(ScaleType.FIT_XY);
        setAlpha(0f);
        // perspective(1400px) : distance de la caméra, en « pixels de profondeur » × densité
        setCameraDistance(1400 * getResources().getDisplayMetrics().density * 1.6f);
        setRotationY(-16);
        setRotationX(3);
        setPivotX(margin + cw / 2f);
        setPivotY(margin + ch / 2f);
    }

    int imageWidth() { return cw + 2 * margin; }

    int imageHeight() { return margin + ch + Math.round(1.2f * vw) + Math.round(.25f * ch) + margin; }

    /** Nouvelle vitrine (null : pas de vitrine). */
    void show(final Bitmap composed, boolean instant) {
        if (pending != null) removeCallbacks(pending);
        pending = () -> {
            setImageBitmap(composed);
            animate().alpha(composed == null ? 0f : 1f).rotationY(-16).translationX(0).setDuration(300).setInterpolator(MainActivity.EASE);
        };
        animate().cancel();
        if (instant) {
            setImageBitmap(composed);
            setAlpha(composed == null ? 0f : 1f); // en float : setAlpha(int) d'ImageView règle l'image, pas la vue
            setRotationY(-16);
            setTranslationX(0);
            return;
        }
        animate().alpha(0).rotationY(-20).translationX(2 * vw).setDuration(120).setInterpolator(MainActivity.EASE);
        postDelayed(pending, 120);
    }

    /**
     * Compose la vitrine d'une appli : lueur de sa couleur, ombre, carte arrondie et reflet
     * (copie retournée à 14 %, effacée au-delà de 25 % de sa hauteur), comme dans la version web.
     */
    Bitmap compose(Bitmap banner, int glow) {
        int W = imageWidth(), H = imageHeight(), m = margin, gap = Math.round(1.2f * vw);
        float r = 1.6f * vw;
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        RectF card = new RectF(m, m, m + cw, m + ch);

        // lueur (0 0 7vw couleur) puis ombre (0 3vw 6vw noir .55), floues
        Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        halo.setMaskFilter(new BlurMaskFilter(3.5f * vw, BlurMaskFilter.Blur.NORMAL));
        halo.setColor((glow & 0x00FFFFFF) | 0x8C000000);
        c.drawRoundRect(card, r, r, halo);
        halo.setMaskFilter(new BlurMaskFilter(3 * vw, BlurMaskFilter.Blur.NORMAL));
        halo.setColor(0x73000000);
        RectF shadow = new RectF(card);
        shadow.offset(0, 1.5f * vw);
        c.drawRoundRect(shadow, r, r, halo);

        // carte : la bannière dans un rectangle arrondi lissé, liseré clair
        Paint img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        BitmapShader sh = new BitmapShader(banner, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix mx = new Matrix();
        mx.setScale(cw / (float) banner.getWidth(), ch / (float) banner.getHeight());
        mx.postTranslate(m, m);
        sh.setLocalMatrix(mx);
        img.setShader(sh);
        c.drawRoundRect(card, r, r, img);
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1.5f);
        edge.setColor(0x1FFFFFFF);
        c.drawRoundRect(card, r, r, edge);

        // reflet : copie retournée sous la carte, effacée vers le bas
        float top = m + ch + gap, fade = .25f * ch;
        int layer = c.saveLayerAlpha(new RectF(m, top, m + cw, top + fade), Math.round(.14f * 255));
        Matrix flip = new Matrix();
        flip.setScale(cw / (float) banner.getWidth(), -ch / (float) banner.getHeight());
        flip.postTranslate(m, top + ch);
        BitmapShader fs = new BitmapShader(banner, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        fs.setLocalMatrix(flip);
        Paint refl = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        refl.setShader(fs);
        c.drawRoundRect(new RectF(m, top, m + cw, top + ch), r, r, refl);
        Paint mask = new Paint();
        mask.setShader(new LinearGradient(0, top, 0, top + fade, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawRect(m, top, m + cw, top + fade, mask);
        c.restoreToCount(layer);
        return out;
    }
}
