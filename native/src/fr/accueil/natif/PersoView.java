package fr.accueil.natif;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * Thinking : personnage détouré en haut à gauche de l'écran, avec une ombre douce portée sur le
 * paysage ; le bas du buste est fondu (images/personnage.png). La nuit, il prend un peu la teinte
 * bleutée de la scène pour ne pas paraître collé dessus.
 */
class PersoView extends View {

    private final Bitmap img;
    private Bitmap shadow;
    private final int[] off = new int[2];
    private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), sh = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();

    PersoView(Context ctx, Bitmap img) {
        super(ctx);
        this.img = img;
        sh.setColor(0x59000000);
    }

    void setNight(boolean night) {
        if (night) {
            ColorMatrix m = new ColorMatrix(new float[]{.62f, 0, 0, 0, 4, 0, .66f, 0, 0, 6, 0, 0, .82f, 0, 16, 0, 0, 0, 1, 0});
            p.setColorFilter(new ColorMatrixColorFilter(m));
        } else p.setColorFilter(null);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float pad = w * .04f;
        dst.set(pad, pad * .5f, w - pad, h - pad * 1.5f);
        if (shadow != null) shadow.recycle();
        // ombre : silhouette floutée, calculée une fois à la taille affichée
        Bitmap scaled = Bitmap.createScaledBitmap(img, Math.max(1, Math.round(dst.width())), Math.max(1, Math.round(dst.height())), true);
        Paint blur = new Paint();
        blur.setMaskFilter(new BlurMaskFilter(Math.max(2, w * .025f), BlurMaskFilter.Blur.NORMAL));
        shadow = scaled.extractAlpha(blur, off);
        scaled.recycle();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (shadow != null) c.drawBitmap(shadow, dst.left + off[0] + getWidth() * .012f, dst.top + off[1] + getWidth() * .02f, sh);
        c.drawBitmap(img, null, dst, p);
    }
}
