package fr.accueil.natif;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Une ligne de la liste Minimal : le nom de l'appli en gris léger ; sélectionnée, elle devient
 * « — Nom » en gras, à la couleur du texte, et glisse un peu vers la droite.
 * En mode édition : pastille jaune si l'appli est épinglée, œil barré et texte atténué si elle est masquée.
 */
class ListRow extends TextView implements DockItem {

    private final String name;
    private final float vw;
    private int ink, inkSoft;
    private boolean selected, editing, pinned, hiddenApp;
    private ValueAnimator colorAnim;

    ListRow(Context ctx, String name, float vw, int ink, int inkSoft) {
        super(ctx);
        this.name = name;
        this.vw = vw;
        this.ink = ink;
        this.inkSoft = inkSoft;
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 2.5f * vw);
        setLetterSpacing(-.01f);
        setIncludeFontPadding(false);
        setSingleLine(true);
        setGravity(Gravity.CENTER_VERTICAL);
        setCompoundDrawablePadding(Math.round(.8f * vw));
        setTextColor(inkSoft);
        render();
    }

    /** Couleurs du thème (Minimal clair ou sombre). */
    void setInk(int ink, int inkSoft) {
        this.ink = ink;
        this.inkSoft = inkSoft;
        if (colorAnim != null) colorAnim.cancel();
        setTextColor(selected ? ink : inkSoft);
        render();
    }

    private void render() {
        setText(selected ? "— " + name : name);
        setTypeface(SceneView.font(selected ? 600 : 300));
        setAlpha(hiddenApp ? .45f : 1f);
        Drawable end = null;
        int s = Math.round(1.8f * vw);
        if (editing && hiddenApp) {
            end = getContext().getDrawable(R.drawable.ic_eye_off).mutate();
            end.setTint(ink);
            end.setBounds(0, 0, s, s);
        } else if (editing && pinned) {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(0xFFFFCF3A);
            Drawable pin = getContext().getDrawable(R.drawable.ic_pin);
            LayerDrawable l = new LayerDrawable(new Drawable[]{dot, pin});
            int p = Math.round(.35f * vw);
            l.setLayerInset(1, p, p, p, p);
            end = l;
            end.setBounds(0, 0, s, s);
        }
        setCompoundDrawablesRelative(null, null, end, null);
    }

    @Override
    public void setSelected(boolean on, boolean animate, boolean withShine) {
        selected = on;
        render();
        int to = on ? ink : inkSoft;
        if (colorAnim != null) colorAnim.cancel();
        if (animate) {
            colorAnim = ValueAnimator.ofObject(new ArgbEvaluator(), getCurrentTextColor(), to).setDuration(200);
            colorAnim.addUpdateListener(a -> setTextColor((int) a.getAnimatedValue()));
            colorAnim.start();
            animate().translationX(on ? 1.4f * vw : 0).setDuration(250).setInterpolator(MainActivity.EASE);
        } else {
            setTextColor(to);
            setTranslationX(on ? 1.4f * vw : 0);
        }
    }

    @Override
    public void setFlags(boolean pinned, boolean hidden) {
        this.pinned = pinned;
        this.hiddenApp = hidden;
        render();
    }

    @Override
    public void setEditing(boolean on) {
        editing = on;
        render();
    }
}
