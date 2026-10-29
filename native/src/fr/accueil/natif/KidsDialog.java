package fr.accueil.natif;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Fenêtres de l'espace parent (thème Kids), comme dans la version web :
 *  - code parent : une suite de 4 flèches, saisie à la télécommande (vérification, ou choix puis confirmation) ;
 *  - menu parent : choisir les applis autorisées, changer le code, quitter le mode Kids.
 * Retour ferme la fenêtre.
 */
class KidsDialog extends FrameLayout {

    interface Host {
        String kidsCode();
        void saveKidsCode(String code);
        void kidsMenu(int row); // 0 applis autorisées, 1 changer le code, 2 quitter Kids
        void showToast(String msg);
    }

    private static final int INK = 0xFF24456E;
    private final Host host;
    private final float vw;
    private final LinearLayout card, dots, rows;
    private final TextView title, text, foot;
    private final View[] dot = new View[4];
    private final TextView[] row = new TextView[3];

    private String mode;          // "check", "set1", "set2" ou "menu"
    private final StringBuilder seq = new StringBuilder();
    private int count, menuRow;
    private String first;         // code saisi une première fois (set1)
    private Runnable onDone;

    KidsDialog(Context ctx, Host host, float vw) {
        super(ctx);
        this.host = host;
        this.vw = vw;
        setBackgroundColor(0x7314233C);
        setVisibility(GONE);
        card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = px(2.4f);
        card.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(2 * vw);
        card.setBackground(bg);
        card.setElevation(3 * vw);
        addView(card, new LayoutParams(px(34), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        title = text(1.9f, 600, 1f);
        card.addView(title, lp(0, 0, 0, px(.6f)));
        text = text(1.1f, 400, .7f);
        text.setGravity(Gravity.CENTER);
        card.addView(text, lp(0, 0, 0, px(1.6f)));

        dots = new LinearLayout(ctx);
        for (int i = 0; i < 4; i++) {
            dot[i] = new View(ctx);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px(1.6f), px(1.6f));
            if (i > 0) dl.leftMargin = px(1.2f);
            dots.addView(dot[i], dl);
        }
        card.addView(dots, lp(0, 0, 0, px(1.2f)));
        foot = text(.95f, 400, .7f);
        foot.setText("Retour pour annuler");
        card.addView(foot);

        rows = new LinearLayout(ctx);
        rows.setOrientation(LinearLayout.VERTICAL);
        String[] labels = {"Choisir les applis autorisées", "Changer le code", "Quitter le mode Kids"};
        for (int i = 0; i < 3; i++) {
            row[i] = text(1.3f, 400, 1f);
            row[i].setText(labels[i]);
            row[i].setPadding(px(1.2f), px(1), px(1.2f), px(1));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rl.topMargin = px(.5f);
            rows.addView(row[i], rl);
        }
        card.addView(rows, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    /** Saisie du code : "check" (vérifier), "set1" (choisir), "set2" (confirmer). */
    void openCode(String m, Runnable done) {
        mode = m;
        onDone = done;
        seq.setLength(0);
        count = 0;
        title.setText("check".equals(m) ? "Code parent" : "set1".equals(m) ? "Choisis un code parent" : "Confirme le code");
        text.setText("check".equals(m) ? "Saisis le code avec les flèches de la télécommande"
                : "Une suite de 4 flèches, par exemple ↑ ↑ ← →. Elle protège la sortie du mode Kids.");
        dots.setVisibility(VISIBLE);
        foot.setVisibility(VISIBLE);
        rows.setVisibility(GONE);
        renderDots();
        show();
    }

    void openMenu() {
        mode = "menu";
        menuRow = 0;
        title.setText("Espace parent");
        text.setText("↑ ↓ choisir · OK valider · Retour fermer");
        dots.setVisibility(GONE);
        foot.setVisibility(GONE);
        rows.setVisibility(VISIBLE);
        renderMenu();
        show();
    }

    private void show() {
        animate().cancel(); // une fermeture en cours (code → menu) ne doit pas masquer la nouvelle fenêtre
        if (isOpen()) {
            setAlpha(1);
            return;
        }
        setVisibility(VISIBLE);
        setAlpha(0);
        card.setScaleX(.96f);
        card.setScaleY(.96f);
        card.setTranslationY(1.5f * vw);
        animate().alpha(1).setDuration(220);
        card.animate().scaleX(1).scaleY(1).translationY(0).setDuration(350).setInterpolator(MainActivity.EASE);
    }

    void close() {
        mode = null;
        animate().alpha(0).setDuration(160).withEndAction(() -> setVisibility(GONE));
    }

    /** Touche reçue pendant que la fenêtre est ouverte (appui, sans répétition). */
    void onKey(int k) {
        if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) {
            close();
            return;
        }
        if ("menu".equals(mode)) {
            if (k == KeyEvent.KEYCODE_DPAD_UP) menuRow = Math.max(0, menuRow - 1);
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN) menuRow = Math.min(2, menuRow + 1);
            else if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                int r = menuRow;
                close();
                host.kidsMenu(r);
                return;
            }
            renderMenu();
            return;
        }
        String sym = k == KeyEvent.KEYCODE_DPAD_UP ? "U" : k == KeyEvent.KEYCODE_DPAD_DOWN ? "D"
                : k == KeyEvent.KEYCODE_DPAD_LEFT ? "L" : k == KeyEvent.KEYCODE_DPAD_RIGHT ? "R" : null;
        if (sym == null || mode == null) return;
        if (count > 0) seq.append(',');
        seq.append(sym);
        count++;
        renderDots();
        if (count < 4) return;
        String code = seq.toString();
        if ("check".equals(mode)) {
            if (code.equals(host.kidsCode())) done();
            else fail();
        } else if ("set1".equals(mode)) {
            Runnable d = onDone;
            openCode("set2", d);
            first = code;
        } else if (code.equals(first)) {
            host.saveKidsCode(code);
            host.showToast("Code parent enregistré");
            done();
        } else {
            Runnable d = onDone;
            openCode("set1", d);
            text.setText("Les deux codes ne correspondent pas, recommence.");
        }
    }

    private void done() {
        Runnable d = onDone;
        close();
        if (d != null) d.run();
    }

    /** Mauvais code : la carte tremble et les points se vident. */
    private void fail() {
        ObjectAnimator a = ObjectAnimator.ofFloat(card, View.TRANSLATION_X, 0, -vw, vw, -vw, 0).setDuration(400);
        a.start();
        postDelayed(() -> {
            seq.setLength(0);
            count = 0;
            renderDots();
        }, 350);
    }

    private void renderDots() {
        for (int i = 0; i < 4; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setStroke(px(.2f), INK);
            g.setColor(i < count ? INK : 0);
            dot[i].setBackground(g);
        }
    }

    private void renderMenu() {
        for (int i = 0; i < 3; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(vw);
            g.setColor(i == menuRow ? INK : 0);
            row[i].setBackground(g);
            row[i].setTextColor(i == menuRow ? 0xFFFFFFFF : INK);
        }
    }

    private int px(float v) {
        return Math.round(v * vw);
    }

    private TextView text(float sizeVw, int weight, float alpha) {
        TextView t = new TextView(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeVw * vw);
        t.setTypeface(SceneView.font(weight));
        t.setTextColor(INK);
        t.setAlpha(alpha);
        t.setIncludeFontPadding(false);
        return t;
    }

    private static LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(l, t, r, b);
        return p;
    }
}
