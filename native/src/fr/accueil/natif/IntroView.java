package fr.accueil.natif;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Intro AxiomOS : « Axiom » fin et gris clair, « OS » en gras dont les couleurs circulent en boucle,
 * sur fond noir. Apparition en 1,1 s, sortie en fondu au bout de 4,2 s (ou à la première touche).
 * Seul le mot « OS » est redessiné pendant l'animation des couleurs.
 */
class IntroView extends FrameLayout {

    private static final int[] COLORS = {0xFF1AA0FF, 0xFF8A3CFF, 0xFFD81F8F, 0xFFFF4D5E, 0xFFFF8A2B, 0xFFFFC21E, 0xFFFF6A3D, 0xFF1AA0FF};
    private static final float[] STOPS = {0, .17f, .33f, .45f, .58f, .70f, .85f, 1};

    private final LinearLayout stage;
    private final OsText os;
    private final Runnable onEnd;
    private ValueAnimator flow;
    private boolean playing, ending;
    private final Runnable start = this::startAnimations, autoEnd = () -> end(false);

    IntroView(Context ctx, float vw, Runnable onEnd) {
        super(ctx);
        this.onEnd = onEnd;
        setBackgroundColor(0xFF000000);
        setVisibility(GONE);
        stage = new LinearLayout(ctx);
        stage.setOrientation(LinearLayout.HORIZONTAL);
        stage.setGravity(Gravity.BOTTOM);
        TextView ax = SceneView.text(ctx, 11 * vw, 200, .02f, false);
        ax.setText("Axiom");
        ax.setTextColor(0xFFDCDCDC);
        ax.setShadowLayer(0, 0, 0, 0);
        stage.addView(ax);
        os = new OsText(ctx, vw);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.leftMargin = Math.round(.04f * 11 * vw);
        stage.addView(os, olp);
        addView(stage, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    boolean isPlaying() {
        return playing;
    }

    /** Joue l'intro ; pendant `delayMs`, écran noir (la télé n'affiche peut-être pas encore l'image). */
    void play(int delayMs) {
        removeCallbacks(start);
        removeCallbacks(autoEnd);
        animate().cancel();
        stage.animate().cancel();
        playing = true;
        ending = false;
        setAlpha(1);
        setVisibility(VISIBLE);
        stage.setAlpha(0);
        stage.setScaleX(.94f);
        stage.setScaleY(.94f);
        postDelayed(start, delayMs);
        postDelayed(autoEnd, delayMs + 4200);
    }

    private void startAnimations() {
        stage.animate().alpha(1).scaleX(1).scaleY(1).setStartDelay(150).setDuration(1100)
                .setInterpolator(new PathInterpolator(.2f, .8f, .2f, 1f));
        if (flow != null) flow.cancel();
        flow = ValueAnimator.ofFloat(0, 1).setDuration(2600);
        flow.setRepeatCount(ValueAnimator.INFINITE);
        flow.setInterpolator(new LinearInterpolator());
        flow.addUpdateListener(a -> os.setPhase((float) a.getAnimatedValue()));
        flow.start();
    }

    /** Sortie : le logo grossit en s'effaçant, puis le fond noir s'efface. */
    void end(boolean fast) {
        if (!playing || ending) return;
        ending = true;
        removeCallbacks(start);
        removeCallbacks(autoEnd);
        stage.animate().cancel();
        stage.animate().scaleX(1.07f).scaleY(1.07f).alpha(0).setStartDelay(0).setDuration(fast ? 500 : 900)
                .setInterpolator(new PathInterpolator(.3f, 0, .2f, 1));
        animate().alpha(0).setStartDelay(250).setDuration(fast ? 650 : 900).withEndAction(() -> {
            if (flow != null) flow.cancel();
            flow = null;
            setVisibility(GONE);
            playing = false;
            onEnd.run();
        });
    }

    /** « OS » en dégradé qui se raccorde sur lui-même et défile de droite à gauche. */
    private static class OsText extends TextView {
        private final Matrix m = new Matrix();
        private LinearGradient shader;
        private float period;

        OsText(Context ctx, float vw) {
            super(ctx);
            setText("OS");
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 11 * vw);
            setTypeface(SceneView.font(700));
            setLetterSpacing(-.01f);
            setIncludeFontPadding(false);
            setSingleLine(true);
            setTextColor(0xFFFFFFFF);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            period = 2f * w; // dégradé deux fois plus large que le mot, comme background-size: 200%
            shader = new LinearGradient(0, 0, period, 0, COLORS, STOPS, Shader.TileMode.REPEAT);
            getPaint().setShader(shader);
            setPhase(0);
        }

        void setPhase(float p) {
            if (shader == null) return;
            m.setTranslate(-p * period, 0);
            shader.setLocalMatrix(m);
            invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (flow != null) flow.cancel();
        super.onDetachedFromWindow();
    }
}
