package fr.accueil.natif;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Vignettage de nuit du thème Modern : bords assombris et léger voile bleu nuit. */
class VignetteView extends View {

    private final Paint paint = new Paint();

    VignetteView(Context ctx) {
        super(ctx);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        // radial-gradient(ellipse 75% 70% at 50% 45%, transparent 35%, rgba(0,0,0,.7) 100%)
        RadialGradient g = new RadialGradient(0, 0, 1, new int[]{0, 0, 0xB3000000},
                new float[]{0, .35f, 1}, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(.75f * w, .70f * h);
        m.postTranslate(w / 2f, .45f * h);
        g.setLocalMatrix(m);
        paint.setShader(g);
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0x40050A19);
        c.drawRect(0, 0, getWidth(), getHeight(), paint);
    }
}
