package fr.accueil.natif;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

/**
 * Le dock en verre dépoli. Android 11 n'a pas de flou en direct : on dessine derrière les tuiles
 * la partie de la photo qui se trouve sous le dock, floutée une fois pour toutes à chaque photo.
 */
class DockView extends FrameLayout {

    private final float radius;
    private final Rect screenPos = new Rect(); // position du dock à l'écran
    private final Rect src = new Rect();
    private final RectF bounds = new RectF();
    private final Paint glass = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlight = new Paint();
    private Bitmap backdrop;
    private int screenW;

    DockView(Context ctx, float radius) {
        super(ctx);
        this.radius = radius;
        setWillNotDraw(false);
        setClipChildren(false);
        ColorMatrix sat = new ColorMatrix();
        sat.setSaturation(1.3f);
        glass.setColorFilter(new ColorMatrixColorFilter(sat));
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1);
        edge.setColor(0x24FFFFFF);
        highlight.setColor(0x1FFFFFFF);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), DockView.this.radius);
            }
        });
        setClipToOutline(true);
    }

    void setScreenPosition(int left, int top, int width, int height, int screenWidth) {
        screenPos.set(left, top, left + width, top + height);
        screenW = screenWidth;
        updateSrc();
    }

    /** Nouvelle photo de fond : on en garde une version minuscule et floutée. */
    void setBackdrop(Bitmap photo) {
        Bitmap old = backdrop;
        backdrop = photo == null ? null : blurred(photo);
        if (old != null) old.recycle();
        updateSrc();
        invalidate();
    }

    private void updateSrc() {
        if (backdrop == null || screenW == 0) return;
        float k = backdrop.getWidth() / (float) screenW;
        src.set(Math.round(screenPos.left * k), Math.round(screenPos.top * k),
                Math.round(screenPos.right * k), Math.round(screenPos.bottom * k));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        bounds.set(.5f, .5f, w - .5f, h - .5f);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (backdrop != null) c.drawBitmap(backdrop, src, bounds, glass);
        c.drawColor(0x5C1A1C20);
        c.drawRect(radius, 0, getWidth() - radius, 1, highlight); // liseré clair en haut
        c.drawRoundRect(bounds, radius, radius, edge);
    }

    /** Réduit la photo par moitiés successives jusqu'à ~120 px de large, puis la floute. */
    static Bitmap blurred(Bitmap photo) {
        Bitmap b = photo;
        while (b.getWidth() > 200) {
            Bitmap half = Bitmap.createScaledBitmap(b, b.getWidth() / 2, b.getHeight() / 2, true);
            if (b != photo) b.recycle();
            b = half;
        }
        if (b == photo) b = photo.copy(Bitmap.Config.ARGB_8888, true);
        else if (!b.isMutable()) { Bitmap m = b.copy(Bitmap.Config.ARGB_8888, true); b.recycle(); b = m; }
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h], tmp = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            boxBlur(px, tmp, w, h, true);
            boxBlur(tmp, px, w, h, false);
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
        return b;
    }

    /** Moyenne sur 3 pixels (horizontalement ou verticalement), bords répétés. */
    private static void boxBlur(int[] in, int[] out, int w, int h, boolean horizontal) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = 0, g = 0, bl = 0;
                for (int d = -1; d <= 1; d++) {
                    int xx = horizontal ? Math.max(0, Math.min(w - 1, x + d)) : x;
                    int yy = horizontal ? y : Math.max(0, Math.min(h - 1, y + d));
                    int p = in[yy * w + xx];
                    r += (p >> 16) & 0xFF;
                    g += (p >> 8) & 0xFF;
                    bl += p & 0xFF;
                }
                out[y * w + x] = 0xFF000000 | (r / 3) << 16 | (g / 3) << 8 | (bl / 3);
            }
        }
    }
}
