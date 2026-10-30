package fr.accueil.natif;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Kids : petite gerbe d'étoiles autour de l'appli qu'on vient de choisir. Les étoiles partent du
 * bord de la tuile, s'éloignent en tournant et s'effacent en 0,7 s ; rien n'est redessiné ensuite.
 */
class SparkleView extends View {

    private static final class Star {
        float x, y, vx, vy, rot, spin, size;
        int color;
    }

    private final List<Star> stars = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final Random rnd = new Random();
    private final float vw;
    private ValueAnimator anim;
    private float t;

    SparkleView(Context ctx, float vw) {
        super(ctx);
        this.vw = vw;
        for (int k = 0; k < 10; k++) { // étoile à 5 branches, de rayon 1
            double a = -Math.PI / 2 + k * Math.PI / 5, r = k % 2 == 0 ? 1 : .45;
            if (k == 0) shape.moveTo((float) (Math.cos(a) * r), (float) (Math.sin(a) * r));
            else shape.lineTo((float) (Math.cos(a) * r), (float) (Math.sin(a) * r));
        }
        shape.close();
    }

    /** Gerbe autour du rectangle (coordonnées de cette vue), dans la couleur du cadre et en jaune. */
    void burst(float l, float tp, float r, float b, int color) {
        stars.clear();
        float cx = (l + r) / 2, cy = (tp + b) / 2;
        for (int k = 0; k < 12; k++) {
            Star s = new Star();
            double a = k * Math.PI * 2 / 12 + rnd.nextFloat() * .4;
            float ex = (float) Math.cos(a), ey = (float) Math.sin(a);
            s.x = cx + ex * (r - l) / 2 * .95f;
            s.y = cy + ey * (b - tp) / 2 * .95f;
            float speed = (2.5f + rnd.nextFloat() * 3) * vw;
            s.vx = ex * speed;
            s.vy = ey * speed;
            s.rot = rnd.nextFloat() * 72;
            s.spin = (rnd.nextFloat() - .5f) * 300;
            s.size = (.45f + rnd.nextFloat() * .5f) * vw;
            s.color = k % 3 == 0 ? 0xFFFFD43B : k % 3 == 1 ? color : 0xFFFFFFFF;
            stars.add(s);
        }
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0, 1).setDuration(700);
        anim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
        anim.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                stars.clear();
                invalidate();
            }
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (stars.isEmpty()) return;
        float alpha = t < .6f ? 1 : 1 - (t - .6f) / .4f;
        for (Star s : stars) {
            c.save();
            c.translate(s.x + s.vx * t, s.y + s.vy * t);
            c.rotate(s.rot + s.spin * t);
            float sc = s.size * (t < .15f ? t / .15f : 1 - (t - .15f) * .4f);
            c.scale(sc, sc);
            paint.setColor(s.color);
            paint.setAlpha(Math.round(255 * alpha));
            c.drawPath(shape, paint);
            c.restore();
        }
    }
}
