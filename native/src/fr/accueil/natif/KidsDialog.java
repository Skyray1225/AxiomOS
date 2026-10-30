package fr.accueil.natif;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Espace parent du thème Kids. Trois écrans dans une même carte :
 *  - code parent : une suite de 4 flèches (vérification, ou choix puis confirmation) ;
 *  - accueil : quatre grandes cases avec leur état (applis autorisées, horaires, code, quitter) ;
 *  - horaires : plages autorisées en semaine et le week-end, frise de la journée pour chacune.
 * Flèches pour se déplacer, OK pour ouvrir ou valider, Retour pour revenir ou fermer.
 */
class KidsDialog extends FrameLayout {

    interface Host {
        String kidsCode();
        void saveKidsCode(String code);
        void kidsMenu(int item); // 0 applis autorisées, 2 changer le code, 3 quitter Kids
        void showToast(String msg);
        int kidsAppCount();
        String kidsQuitTarget();             // nom du thème retrouvé en quittant Kids
        boolean kidsLimit();
        int[] kidsHours();                   // {semaine début, semaine fin, week-end début, week-end fin} en minutes
        void setKidsLimit(boolean on);
        void setKidsHours(int[] hours);
    }

    private static final int INK = 0xFF24456E, SOFT = 0xA624456E, ACCENT = 0xFFFF7A59;
    private static final int[] TILE_COLORS = {0xFF4DABF7, 0xFF51CF66, 0xFFB197FC, 0xFFFF8787};
    private final Host host;
    private final float vw;
    private final LinearLayout card;

    private String mode; // "check", "set1", "set2", "menu" ou "hours"
    private final StringBuilder seq = new StringBuilder();
    private int count, sel;
    private String first, error;
    private Runnable onDone;

    KidsDialog(Context ctx, Host host, float vw) {
        super(ctx);
        this.host = host;
        this.vw = vw;
        setBackgroundColor(0x8C14233C);
        setVisibility(GONE);
        card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = px(2.6f);
        card.setPadding(p, px(2.2f), p, px(1.8f));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFDFBF6);
        bg.setCornerRadius(2.2f * vw);
        card.setBackground(bg);
        card.setElevation(3 * vw);
        addView(card, new LayoutParams(px(50), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    /* ===== Code parent ===== */

    /** Saisie du code : "check" (vérifier), "set1" (choisir), "set2" (confirmer). */
    void openCode(String m, Runnable done) {
        mode = m;
        onDone = done;
        seq.setLength(0);
        count = 0;
        error = null;
        renderCode();
        show();
    }

    private void renderCode() {
        card.removeAllViews();
        boolean check = "check".equals(mode);
        header(check ? "Espace parent" : "set1".equals(mode) ? "Choisis un code parent" : "Confirme le code",
                check ? "Utilisez les flèches directionnelles pour saisir votre code parental"
                        : "set1".equals(mode) ? "Une suite de 4 flèches. Elle protège la sortie du mode Kids et les réglages."
                        : "Tape à nouveau les 4 mêmes flèches");
        // quatre cases : une flèche tapée s'affiche en point plein (le code reste secret)
        LinearLayout slots = new LinearLayout(getContext());
        slots.setGravity(Gravity.CENTER);
        for (int i = 0; i < 4; i++) {
            View v = new View(getContext());
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(1.1f * vw);
            g.setColor(i < count ? INK : 0xFFF1EEE6);
            g.setStroke(px(.18f), i == count ? ACCENT : 0x3324456E);
            v.setBackground(g);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(px(4.6f), px(4.6f));
            if (i > 0) l.leftMargin = px(1.2f);
            slots.addView(v, l);
        }
        card.addView(slots, lp(0, px(2), 0, px(1.4f)));
        // les touches utilisables, en rappel
        TextView keys = label("↑   ↓   ←   →", 1.6f, 500, 0x6624456E);
        keys.setGravity(Gravity.CENTER);
        card.addView(keys, lp(0, 0, 0, px(.8f)));
        TextView msg = label(error != null ? error : " ", 1.05f, 600, ACCENT);
        msg.setGravity(Gravity.CENTER);
        card.addView(msg, lp(0, 0, 0, px(.6f)));
        footer("Retour pour annuler");
    }

    /* ===== Accueil de l'espace parent ===== */

    void openMenu() {
        mode = "menu";
        sel = 0;
        renderMenu();
        show();
    }

    private void renderMenu() {
        card.removeAllViews();
        header("Espace parent", "Réglages du mode Kids");
        String[][] items = {
                {"Applis autorisées", host.kidsAppCount() + (host.kidsAppCount() > 1 ? " applis" : " appli")},
                {"Horaires", host.kidsLimit() ? hoursSummary() : "Pas de limite"},
                {"Code parent", "Modifier le code"},
                {"Quitter le mode Kids", "Retour à " + host.kidsQuitTarget()}};
        for (int r = 0; r < 2; r++) {
            LinearLayout line = new LinearLayout(getContext());
            for (int c = 0; c < 2; c++) {
                int i = r * 2 + c;
                line.addView(tile(i, items[i][0], items[i][1], i == sel), weighted(c > 0 ? px(1.2f) : 0));
            }
            card.addView(line, lp(0, r > 0 ? px(1.2f) : px(1.6f), 0, 0));
        }
        footer("Flèches pour choisir · OK pour ouvrir · Retour pour fermer");
    }

    private String hoursSummary() {
        int[] h = host.kidsHours();
        return "Semaine " + hm(h[0]) + "–" + hm(h[1]) + " · week-end " + hm(h[2]) + "–" + hm(h[3]);
    }

    /** Grande case : pastille de couleur avec son icône, titre, état. */
    private LinearLayout tile(int i, String title, String state, boolean selected) {
        LinearLayout t = new LinearLayout(getContext());
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(px(1.4f), px(1.3f), px(1.4f), px(1.3f));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(1.5f * vw);
        // sélection : fond blanc et contour de sa couleur, sans agrandir ni soulever la case
        // (sa rangée la couperait) ; non choisie : même épaisseur de contour, transparent
        g.setColor(selected ? 0xFFFFFFFF : 0xFFF4F1EA);
        g.setStroke(px(.3f), selected ? TILE_COLORS[i] : 0x00FFFFFF);
        t.setBackground(g);
        t.addView(new Icon(getContext(), i, TILE_COLORS[i]), new LinearLayout.LayoutParams(px(3.6f), px(3.6f)));
        t.addView(label(title, 1.3f, 600, INK), lp(0, px(.9f), 0, 0));
        TextView st = label(state, .95f, 500, SOFT);
        st.setMaxLines(2);
        t.addView(st, lp(0, px(.3f), 0, 0));
        return t;
    }

    /* ===== Horaires ===== */

    private static final String[] HOUR_ROWS = {"limit", "w0", "w1", "e0", "e1"};

    private void openHours() {
        mode = "hours";
        sel = 0;
        renderHours();
    }

    private void renderHours() {
        card.removeAllViews();
        header("Horaires", "En dehors de ces plages, les applis se reposent");
        int[] h = host.kidsHours();
        boolean on = host.kidsLimit();
        card.addView(row(0, "Limiter les horaires", on ? "Oui" : "Non", true), lp(0, px(1.4f), 0, 0));
        group("En semaine · lundi au vendredi", h[0], h[1], on);
        card.addView(row(1, "Début", hm(h[0]), on), lp(0, px(.3f), 0, 0));
        card.addView(row(2, "Fin", hm(h[1]), on), lp(0, px(.3f), 0, 0));
        group("Le week-end · samedi et dimanche", h[2], h[3], on);
        card.addView(row(3, "Début", hm(h[2]), on), lp(0, px(.3f), 0, 0));
        card.addView(row(4, "Fin", hm(h[3]), on), lp(0, px(.3f), 0, 0));
        footer("↑ ↓ choisir · ← → régler (par demi-heure) · Retour : espace parent");
    }

    /** Titre d'un groupe et frise de la journée (0 h → 24 h) avec la plage autorisée. */
    private void group(String title, int start, int end, boolean on) {
        card.addView(label(title, 1f, 600, on ? INK : 0x5924456E), lp(px(1.2f), px(1.4f), 0, px(.4f)));
        card.addView(new Timeline(getContext(), start, end, on), lp(px(1.2f), 0, px(1.2f), px(.2f)));
        ((LinearLayout.LayoutParams) card.getChildAt(card.getChildCount() - 1).getLayoutParams()).height = px(1.4f);
        card.getChildAt(card.getChildCount() - 1).getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
    }

    private LinearLayout row(int i, String title, String value, boolean enabled) {
        boolean s = i == sel;
        LinearLayout r = new LinearLayout(getContext());
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(1.2f), px(.75f), px(1.2f), px(.75f));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(vw);
        g.setColor(s ? INK : 0);
        r.setBackground(g);
        int fg = s ? 0xFFFFFFFF : enabled ? INK : 0x5924456E;
        r.addView(label(title, 1.2f, 500, fg), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        r.addView(label("‹", 1.2f, 400, (fg & 0x00FFFFFF) | 0x80000000));
        TextView v = label(value, 1.25f, 600, fg);
        v.setMinWidth(px(4.6f));
        v.setGravity(Gravity.CENTER);
        r.addView(v);
        r.addView(label("›", 1.2f, 400, (fg & 0x00FFFFFF) | 0x80000000));
        return r;
    }

    private void adjustHour(int k, int d) {
        int[] h = host.kidsHours().clone();
        h[k] = Math.max(0, Math.min(24 * 60, h[k] + d * 30));
        // début avant la fin, d'au moins une demi-heure
        if (k % 2 == 0) h[k] = Math.min(h[k], h[k + 1] - 30);
        else h[k] = Math.max(h[k], h[k - 1] + 30);
        host.setKidsHours(h);
    }

    static String hm(int min) {
        return String.format(Locale.FRANCE, "%02d:%02d", min / 60, min % 60);
    }

    /* ===== Touches ===== */

    /** Touche reçue pendant que la fenêtre est ouverte (appui, sans répétition). */
    void onKey(int k) {
        boolean back = k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE;
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if ("hours".equals(mode)) {
            if (back) { openMenu(); return; }
            if (k == KeyEvent.KEYCODE_DPAD_UP) sel = Math.max(0, sel - 1);
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN) sel = Math.min(host.kidsLimit() ? 4 : 0, sel + 1);
            else if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT || ok) {
                int d = k == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                if (sel == 0) host.setKidsLimit(!host.kidsLimit());
                else if (!ok) adjustHour(sel - 1, d);
            }
            renderHours();
            return;
        }
        if (back) { close(); return; }
        if ("menu".equals(mode)) {
            if (k == KeyEvent.KEYCODE_DPAD_LEFT && sel % 2 == 1) sel--;
            else if (k == KeyEvent.KEYCODE_DPAD_RIGHT && sel % 2 == 0) sel++;
            else if (k == KeyEvent.KEYCODE_DPAD_UP && sel >= 2) sel -= 2;
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN && sel < 2) sel += 2;
            else if (ok) {
                if (sel == 1) { openHours(); return; }
                int item = sel;
                close();
                host.kidsMenu(item);
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
        error = null;
        renderCode();
        if (count < 4) return;
        String code = seq.toString();
        if ("check".equals(mode)) {
            if (code.equals(host.kidsCode())) done();
            else fail("Ce n'est pas le bon code, recommence");
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
            error = "Les deux codes ne correspondent pas, recommence";
            renderCode();
        }
    }

    private void done() {
        Runnable d = onDone;
        close();
        if (d != null) d.run();
    }

    /** Mauvais code : la carte tremble, puis les cases se vident. */
    private void fail(String msg) {
        ObjectAnimator.ofFloat(card, View.TRANSLATION_X, 0, -vw, vw, -vw, 0).setDuration(400).start();
        postDelayed(() -> {
            seq.setLength(0);
            count = 0;
            error = msg;
            renderCode();
        }, 350);
    }

    /* ===== Ouverture, fermeture ===== */

    private void show() {
        animate().cancel(); // une fermeture en cours (code → accueil) ne doit pas masquer la nouvelle fenêtre
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

    /* ===== Éléments ===== */

    private void header(String title, String sub) {
        LinearLayout h = new LinearLayout(getContext());
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.addView(new Icon(getContext(), 4, ACCENT), new LinearLayout.LayoutParams(px(3.2f), px(3.2f)));
        LinearLayout t = new LinearLayout(getContext());
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(label(title, 1.9f, 700, INK));
        t.addView(label(sub, 1f, 500, SOFT), lp(0, px(.25f), 0, 0));
        h.addView(t, lp(px(1.2f), 0, 0, 0));
        card.addView(h);
    }

    private void footer(String text) {
        TextView f = label(text, .9f, 500, 0x8024456E);
        f.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.topMargin = px(1.6f);
        card.addView(f, l);
    }

    private TextView label(String s, float sizeVw, int weight, int color) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeVw * vw);
        t.setTypeface(SceneView.font(weight));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    private int px(float v) {
        return Math.round(v * vw);
    }

    private static LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(l, t, r, b);
        return p;
    }

    private static LinearLayout.LayoutParams weighted(int left) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        p.leftMargin = left;
        return p;
    }

    /** Frise de la journée : piste claire, plage autorisée en couleur, repères 6 h, 12 h, 18 h. */
    private class Timeline extends View {
        private final int start, end;
        private final boolean on;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Timeline(Context ctx, int start, int end, boolean on) {
            super(ctx);
            this.start = start;
            this.end = end;
            this.on = on;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = h / 2;
            p.setColor(0xFFECE8DF);
            c.drawRoundRect(0, 0, w, h, r, r, p);
            p.setColor(on ? 0xFF51CF66 : 0x4024456E);
            c.drawRoundRect(w * start / 1440f, 0, w * end / 1440f, h, r, r, p);
            p.setColor(0x4024456E);
            for (int k = 1; k < 4; k++) c.drawRect(w * k / 4f - 1, h * .25f, w * k / 4f + 1, h * .75f, p);
        }
    }

    /** Icônes dessinées : 0 applis, 1 horloge, 2 cadenas, 3 sortie, 4 bouclier (en-tête). */
    private class Icon extends View {
        private final int kind, color;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Icon(Context ctx, int kind, int color) {
            super(ctx);
            this.kind = kind;
            this.color = color;
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = getWidth(), m = s / 2;
            p.setStyle(Paint.Style.FILL);
            p.setColor((color & 0x00FFFFFF) | 0x33000000);
            c.drawCircle(m, m, m, p);
            p.setColor(color);
            float u = s / 24f;
            switch (kind) {
                case 0: // quatre carrés
                    for (int k = 0; k < 4; k++) {
                        float x = (k % 2 == 0 ? 6.5f : 12.5f) * u, y = (k < 2 ? 6.5f : 12.5f) * u;
                        c.drawRoundRect(x, y, x + 5 * u, y + 5 * u, 1.3f * u, 1.3f * u, p);
                    }
                    break;
                case 1: // horloge
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(1.8f * u);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    c.drawCircle(m, m, 6.5f * u, p);
                    c.drawLine(m, m, m, m - 4 * u, p);
                    c.drawLine(m, m, m + 3 * u, m + 1.5f * u, p);
                    break;
                case 2: // cadenas
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(1.8f * u);
                    c.drawArc(new RectF(8.5f * u, 5.5f * u, 15.5f * u, 13 * u), 180, 180, false, p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawRoundRect(7 * u, 10.5f * u, 17 * u, 18.5f * u, 1.5f * u, 1.5f * u, p);
                    break;
                case 3: // sortie : flèche vers la droite hors d'un cadre
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(1.8f * u);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    p.setStrokeJoin(Paint.Join.ROUND);
                    c.drawRoundRect(6.5f * u, 6.5f * u, 13 * u, 17.5f * u, 1.5f * u, 1.5f * u, p);
                    c.drawLine(10.5f * u, 12 * u, 18.5f * u, 12 * u, p);
                    c.drawLine(15.5f * u, 9 * u, 18.5f * u, 12 * u, p);
                    c.drawLine(15.5f * u, 15 * u, 18.5f * u, 12 * u, p);
                    break;
                default: // bouclier
                    android.graphics.Path sh = new android.graphics.Path();
                    sh.moveTo(m, 5 * u);
                    sh.lineTo(18 * u, 7.5f * u);
                    sh.quadTo(18 * u, 16 * u, m, 19.5f * u);
                    sh.quadTo(6 * u, 16 * u, 6 * u, 7.5f * u);
                    sh.close();
                    c.drawPath(sh, p);
            }
        }
    }
}
