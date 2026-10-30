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
 * Kids : petit effet autour de l'appli qu'on vient de choisir, tiré au hasard à chaque fois :
 * éclaboussure de peinture, confettis, étoiles ou bulles, dans la couleur de son cadre et des
 * couleurs vives. Dure moins d'une seconde ; rien n'est redessiné ensuite.
 */
class SparkleView extends View {

    private static final int STARS = 0, PAINT = 1, CONFETTI = 2, BUBBLES = 3;
    private static final int[] FUN = {0xFFFF6B8B, 0xFFFFC93C, 0xFF4CC9A6, 0xFF4D9DFF, 0xFFA77BFF, 0xFFFF8A5B};

    private static final class Bit {
        float x, y, vx, vy, rot, spin, size;
        int color;
        Path blob; // éclaboussure : contour irrégulier
    }

    private final List<Bit> bits = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path star = new Path();
    private final Random rnd = new Random();
    private final float vw;
    private ValueAnimator anim;
    private float t;
    private int kind;

    SparkleView(Context ctx, float vw) {
        super(ctx);
        this.vw = vw;
        for (int k = 0; k < 10; k++) { // étoile à 5 branches, de rayon 1
            double a = -Math.PI / 2 + k * Math.PI / 5, r = k % 2 == 0 ? 1 : .45;
            if (k == 0) star.moveTo((float) (Math.cos(a) * r), (float) (Math.sin(a) * r));
            else star.lineTo((float) (Math.cos(a) * r), (float) (Math.sin(a) * r));
        }
        star.close();
    }

    /** Effet autour du rectangle (coordonnées de cette vue), dans la couleur du cadre. */
    void burst(float l, float tp, float r, float b, int color) {
        bits.clear();
        kind = rnd.nextInt(4);
        float cx = (l + r) / 2, cy = (tp + b) / 2, hw = (r - l) / 2, hh = (b - tp) / 2;
        int n = kind == CONFETTI ? 26 : kind == PAINT ? 14 : kind == BUBBLES ? 12 : 12;
        for (int k = 0; k < n; k++) {
            Bit s = new Bit();
            double a = k * Math.PI * 2 / n + rnd.nextFloat() * .5;
            float ex = (float) Math.cos(a), ey = (float) Math.sin(a);
            s.x = cx + ex * hw * .95f;
            s.y = cy + ey * hh * .95f;
            s.rot = rnd.nextFloat() * 360;
            s.spin = (rnd.nextFloat() - .5f) * 400;
            s.color = rnd.nextInt(3) == 0 ? color : FUN[rnd.nextInt(FUN.length)];
            switch (kind) {
                case PAINT: { // grosses taches collées au bord, plus des gouttelettes projetées
                    boolean drop = k >= 5;
                    float speed = (drop ? 3 + rnd.nextFloat() * 3 : .6f) * vw;
                    s.vx = ex * speed;
                    s.vy = ey * speed;
                    s.size = drop ? (.25f + rnd.nextFloat() * .35f) * vw : (1.4f + rnd.nextFloat() * 1.2f) * vw;
                    s.blob = blob(drop);
                    s.color = k < 5 ? color : s.color;
                    break;
                }
                case CONFETTI: { // petits rectangles projetés vers le haut, qui retombent
                    float speed = (3 + rnd.nextFloat() * 4) * vw;
                    s.vx = ex * speed;
                    s.vy = ey * speed - 3 * vw;
                    s.size = (.35f + rnd.nextFloat() * .3f) * vw;
                    break;
                }
                case BUBBLES: { // bulles qui montent en se balançant
                    s.x = l + rnd.nextFloat() * (r - l);
                    s.y = b - rnd.nextFloat() * hh * .4f;
                    s.vx = (rnd.nextFloat() - .5f) * 2 * vw;
                    s.vy = -(4 + rnd.nextFloat() * 4) * vw;
                    s.size = (.4f + rnd.nextFloat() * .7f) * vw;
                    break;
                }
                default: { // étoiles
                    float speed = (2.5f + rnd.nextFloat() * 3) * vw;
                    s.vx = ex * speed;
                    s.vy = ey * speed;
                    s.size = (.45f + rnd.nextFloat() * .5f) * vw;
                    s.color = k % 3 == 0 ? 0xFFFFD43B : k % 3 == 1 ? color : 0xFFFFFFFF;
                }
            }
            bits.add(s);
        }
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0, 1).setDuration(kind == CONFETTI || kind == BUBBLES ? 950 : 750);
        anim.setInterpolator(new android.view.animation.LinearInterpolator());
        anim.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                bits.clear();
                invalidate();
            }
        });
        anim.start();
    }

    /** Tache de peinture de rayon 1 : contour bosselé, quelques coulures pour les grosses taches. */
    private Path blob(boolean drop) {
        Path p = new Path();
        int n = drop ? 8 : 14;
        for (int k = 0; k <= n; k++) {
            double a = k * Math.PI * 2 / n;
            float rr = drop ? .85f + rnd.nextFloat() * .3f : .7f + rnd.nextFloat() * .5f;
            float x = (float) Math.cos(a) * rr, y = (float) Math.sin(a) * rr;
            if (k == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.close();
        if (!drop) for (int k = 0; k < 3; k++) { // éclats autour
            double a = rnd.nextFloat() * Math.PI * 2;
            float d = 1.25f + rnd.nextFloat() * .5f;
            p.addCircle((float) Math.cos(a) * d, (float) Math.sin(a) * d, .12f + rnd.nextFloat() * .12f, Path.Direction.CW);
        }
        return p;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (bits.isEmpty()) return;
        float fade = t < .6f ? 1 : 1 - (t - .6f) / .4f;
        float ease = 1 - (1 - t) * (1 - t); // départ rapide, arrivée douce
        for (Bit s : bits) {
            c.save();
            switch (kind) {
                case PAINT: {
                    float grow = Math.min(1, t / .18f); // la tache s'écrase vite, puis sèche et s'efface
                    c.translate(s.x + s.vx * ease, s.y + s.vy * ease);
                    c.rotate(s.rot);
                    c.scale(s.size * grow, s.size * grow);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(s.color);
                    paint.setAlpha(Math.round(230 * fade));
                    c.drawPath(s.blob, paint);
                    break;
                }
                case CONFETTI: {
                    float g = 9 * vw; // pesanteur
                    c.translate(s.x + s.vx * t, s.y + s.vy * t + g * t * t / 2);
                    c.rotate(s.rot + s.spin * t);
                    c.scale(1, Math.abs((float) Math.cos(t * 9 + s.rot))); // le confetti tourne sur lui-même
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(s.color);
                    paint.setAlpha(Math.round(255 * fade));
                    c.drawRect(-s.size, -s.size * .55f, s.size, s.size * .55f, paint);
                    break;
                }
                case BUBBLES: {
                    float x = s.x + s.vx * t + (float) Math.sin(t * 10 + s.rot) * .5f * vw, y = s.y + s.vy * ease;
                    float pop = t > .85f ? 1 + (t - .85f) * 4 : 1; // éclate à la fin
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(.15f * vw);
                    paint.setColor(s.color);
                    paint.setAlpha(Math.round(220 * fade));
                    c.drawCircle(x, y, s.size * pop, paint);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(0xFFFFFFFF);
                    paint.setAlpha(Math.round(180 * fade));
                    c.drawCircle(x - s.size * .35f, y - s.size * .35f, s.size * .22f, paint);
                    break;
                }
                default: {
                    c.translate(s.x + s.vx * ease, s.y + s.vy * ease);
                    c.rotate(s.rot + s.spin * t);
                    float sc = s.size * (t < .15f ? t / .15f : 1 - (t - .15f) * .4f);
                    c.scale(sc, sc);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(s.color);
                    paint.setAlpha(Math.round(255 * fade));
                    c.drawPath(star, paint);
                }
            }
            c.restore();
        }
    }
}
