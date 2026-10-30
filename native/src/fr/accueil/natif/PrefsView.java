package fr.accueil.natif;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Fenêtre « Préférences » : 6 cartes de thème avec aperçu, la ligne « Apparence de Minimal »
 * (en Minimal seulement), puis l'interrupteur « Animations du décor ».
 * ← → choisir, OK appliquer, ↑ ↓ naviguer, Retour fermer. Sombre, ou claire en Minimal clair.
 */
class PrefsView extends FrameLayout {

    interface Host {
        void applyTheme(String id);
        void setAnimations(boolean on);
        void setLifeFx(boolean on);
        void setHalloween(boolean on);
        void setNoel(boolean on);
        void setPaques(boolean on);
        void closePrefs();
        String minMode();
        void cycleMinMode(int dir);
        String blueMode();
        int blueLevel();
        void cycleBlueMode(int dir);
        void cycleBlueLevel(int dir);
        int brightness();
        void stepBrightness(int dir);
        Typeface font(int weight);
    }

    static final String[] THEMES = {"dessin", "photo", "bullit", "ambient", "minimal", "kids"};
    static final String[] NAMES = {"Thinking", "Modern", "Bullit", "Ambient", "Minimal", "Kids"};
    private static final String[][] MIN_MODES = {{"auto", "Auto"}, {"light", "Clair"}, {"dark", "Sombre"}};

    private final Host host;
    private final float vw;
    private final ThumbView[] thumbs = new ThumbView[6];
    private final LinearLayout[] cards = new LinearLayout[6];
    private final TextView[] checks = new TextView[6];
    private final List<TextView> texts = new ArrayList<>(); // recolorés en version claire
    private final LinearLayout sheet, animRow, darkRow, lifeRow, halRow, noelRow, paqRow;
    private final TextView darkValue, blueValue, levelValue;
    private final LinearLayout blueRow, levelRow, lightRow;
    private final SliderView lightSlider;
    private final TextView lightValue;
    private final SwitchView animSwitch, lifeSwitch, halSwitch, noelSwitch, paqSwitch;
    private int row = 0, card = 1;
    private final android.widget.ScrollView scroll;
    private String theme = "photo";
    private boolean light;

    PrefsView(Context ctx, Host host, float vw, boolean animOn, boolean lifeOn, boolean halOn, boolean noelOn, boolean paqOn) {
        super(ctx);
        this.host = host;
        this.vw = vw;
        setBackgroundColor(0x73080C12);

        // la carte ; si son contenu dépasse l'écran, il défile pour garder la rangée choisie visible
        sheet = new LinearLayout(ctx);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(px(2.6f), px(1.6f), px(2.6f), px(1.2f));
        sheet.setElevation(3 * vw);
        LayoutParams blp = new LayoutParams(px(58), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        blp.topMargin = blp.bottomMargin = px(1.6f);
        addView(sheet, blp);
        scroll = new android.widget.ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setFocusable(false);
        sheet.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false); // la carte sélectionnée se soulève
        box.setPadding(0, px(.6f), 0, px(.5f));
        scroll.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("Préférences", 2f, 500, 1f);
        box.addView(title, lp(0, 0, 0, px(1.6f)));
        box.addView(section("Thème"), lp(0, 0, 0, px(.9f)));

        // grille 3 × 2
        int inner = px(58) - 2 * px(2.6f), gapX = px(1.6f), gapY = px(1.4f);
        int cw = (inner - 2 * gapX) / 3, ch = Math.round(cw * 9 / 16f);
        for (int r = 0; r < 2; r++) {
            LinearLayout line = new LinearLayout(ctx);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setClipChildren(false);
            for (int c = 0; c < 3; c++) {
                final int i = r * 3 + c;
                LinearLayout k = new LinearLayout(ctx);
                k.setOrientation(LinearLayout.VERTICAL);
                k.setClipChildren(false);
                thumbs[i] = new ThumbView(ctx, THEMES[i], vw);
                k.addView(thumbs[i], new LinearLayout.LayoutParams(cw, ch));
                LinearLayout name = new LinearLayout(ctx);
                name.setOrientation(LinearLayout.HORIZONTAL);
                name.setGravity(Gravity.CENTER_VERTICAL);
                name.addView(text(NAMES[i], 1.15f, 500, 1f), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                if (Theme.available(THEMES[i])) { // coche sur le thème actif
                    TextView check = new TextView(ctx);
                    check.setText("✓");
                    check.setTextSize(TypedValue.COMPLEX_UNIT_PX, .9f * vw);
                    check.setTypeface(host.font(600));
                    check.setIncludeFontPadding(false);
                    check.setGravity(Gravity.CENTER);
                    checks[i] = check;
                    name.addView(check, new LinearLayout.LayoutParams(px(1.5f), px(1.5f)));
                } else {
                    name.addView(text("bientôt", .9f, 500, .55f));
                }
                LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                nlp.topMargin = px(.8f);
                k.addView(name, nlp);
                cards[i] = k;
                LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(cw, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (c > 0) klp.leftMargin = gapX;
                line.addView(k, klp);
            }
            LinearLayout.LayoutParams llp = lp(0, r > 0 ? gapY : 0, 0, r == 1 ? px(1.8f) : 0);
            llp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            box.addView(line, llp);
        }

        box.addView(section("Affichage"), lp(0, 0, 0, px(.9f)));
        darkRow = row("Apparence de Minimal", "Auto suit le jour et la nuit");
        darkValue = text("", 1.25f, 500, 1f);
        LinearLayout val = new LinearLayout(ctx);
        val.setGravity(Gravity.CENTER_VERTICAL);
        TextView lt = text("‹", 1.25f, 400, .5f), gt = text("›", 1.25f, 400, .5f);
        val.addView(lt);
        LinearLayout.LayoutParams vl = lp(px(.9f), 0, px(.9f), 0);
        val.addView(darkValue, vl);
        val.addView(gt);
        darkRow.addView(val);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.bottomMargin = px(.5f);
        box.addView(darkRow, dlp);

        lightRow = row("Luminosité", "← → pour baisser ou remonter ; 100 % : image normale de la télé");
        lightSlider = new SliderView(ctx, vw);
        lightRow.addView(lightSlider, new LinearLayout.LayoutParams(px(12), px(1.9f)));
        lightValue = text("", 1.25f, 500, 1f);
        lightValue.setMinWidth(px(4.2f));
        lightValue.setGravity(Gravity.END);
        lightRow.addView(lightValue, lp(px(1), 0, 0, 0));
        box.addView(lightRow, dlp2());
        blueRow = row("Filtre lumière bleue", "Sur tout l'écran, applis comprises ; Auto : du coucher au lever du soleil");
        blueValue = valueBox(blueRow);
        box.addView(blueRow, dlp2());
        levelRow = row("Intensité du filtre", "Teinte ambrée plus ou moins marquée");
        levelValue = valueBox(levelRow);
        box.addView(levelRow, dlp2());

        animRow = row("Animations du décor", "Lueur sur l'appli sélectionnée");
        animSwitch = new SwitchView(ctx, vw, animOn);
        animRow.addView(animSwitch, new LinearLayout.LayoutParams(px(3.4f), px(1.9f)));
        box.addView(animRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        lifeRow = row("Effets de saison", "Feuilles, papillons, lucioles, étoiles filantes");
        lifeSwitch = new SwitchView(ctx, vw, lifeOn);
        lifeRow.addView(lifeSwitch, new LinearLayout.LayoutParams(px(3.4f), px(1.9f)));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = px(.5f);
        box.addView(lifeRow, flp);

        halRow = row("Décor d'Halloween", "Du 15 octobre au 1er novembre : citrouilles, guirlandes, chauves-souris");
        halSwitch = new SwitchView(ctx, vw, halOn);
        halRow.addView(halSwitch, new LinearLayout.LayoutParams(px(3.4f), px(1.9f)));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = px(.5f);
        box.addView(halRow, hlp);

        noelRow = row("Décor de Noël", "Du 1er au 25 décembre : sapins illuminés, lanternes, traîneau");
        noelSwitch = new SwitchView(ctx, vw, noelOn);
        noelRow.addView(noelSwitch, new LinearLayout.LayoutParams(px(3.4f), px(1.9f)));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = px(.5f);
        box.addView(noelRow, nlp);

        paqRow = row("Décor de Pâques", "Les 15 jours avant Pâques : œufs, paniers, lapins");
        paqSwitch = new SwitchView(ctx, vw, paqOn);
        paqRow.addView(paqSwitch, new LinearLayout.LayoutParams(px(3.4f), px(1.9f)));
        box.addView(paqRow, dlp2());

        TextView hint = text("← → choisir · OK appliquer · ↑ ↓ naviguer · Retour fermer", .95f, 400, .55f);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = px(1.4f);
        box.addView(hint, hl);
        setLight(false);
    }

    /** Une ligne réglable : libellé, explication en petit, valeur à droite. */
    private LinearLayout row(String label, String small) {
        LinearLayout r = new LinearLayout(getContext());
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(1.3f), px(1), px(1.3f), px(1));
        LinearLayout labels = new LinearLayout(getContext());
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(label, 1.25f, 400, 1f));
        labels.addView(text(small, .9f, 400, .55f), lp(0, px(.2f), 0, 0));
        r.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return r;
    }

    /** Fait défiler la carte pour que la rangée choisie soit entièrement visible. */
    private void keepVisible(String id, boolean animate) {
        final View v = "dark".equals(id) ? darkRow : "light".equals(id) ? lightRow : "blue".equals(id) ? blueRow
                : "level".equals(id) ? levelRow : "anim".equals(id) ? animRow : "life".equals(id) ? lifeRow
                : "halloween".equals(id) ? halRow : "noel".equals(id) ? noelRow : "paques".equals(id) ? paqRow : null;
        scroll.post(() -> {
            int h = scroll.getHeight(), y = scroll.getScrollY(), m = px(1.5f), target = y;
            if (v == null) target = 0; // rangée des thèmes : en haut
            else if (v.getBottom() + m > y + h) target = v.getBottom() + m - h;
            else if (v.getTop() - m < y) target = v.getTop() - m;
            if (target == y) return;
            if (animate) scroll.smoothScrollTo(0, target);
            else scroll.scrollTo(0, target);
        });
    }

    /** Valeur à choisir avec ‹ › à droite d'une rangée. */
    private TextView valueBox(LinearLayout r) {
        TextView v = text("", 1.25f, 500, 1f);
        LinearLayout val = new LinearLayout(getContext());
        val.setGravity(Gravity.CENTER_VERTICAL);
        val.addView(text("‹", 1.25f, 400, .5f));
        val.addView(v, lp(px(.9f), 0, px(.9f), 0));
        val.addView(text("›", 1.25f, 400, .5f));
        r.addView(val);
        return v;
    }

    private LinearLayout.LayoutParams dlp2() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = px(.5f);
        return p;
    }

    /** Rangées du bas : « apparence » (en Minimal seulement), animations, effets de saison (en Thinking seulement). */
    private String[] rows() {
        java.util.List<String> r = new java.util.ArrayList<>();
        r.add("themes");
        if ("minimal".equals(theme)) r.add("dark");
        r.add("light");
        r.add("blue");
        if (!BlueFilter.OFF.equals(host.blueMode())) r.add("level");
        r.add("anim");
        if ("dessin".equals(theme)) {
            r.add("life");
            if (halShown) r.add("halloween");
            if (noelShown) r.add("noel");
            if (paqShown) r.add("paques");
        }
        return r.toArray(new String[0]);
    }

    /** Ouverture : sélection sur le thème actuel, miniature Modern sur la photo du moment. */
    private boolean halShown, noelShown, paqShown;

    /** Rangées des décors de fête : en Thinking, et seulement pendant leur période. */
    void setFestive(boolean halloween, boolean noel, boolean paques) {
        halShown = halloween && "dessin".equals(theme);
        noelShown = noel && "dessin".equals(theme);
        paqShown = paques && "dessin".equals(theme);
    }

    void open(String currentTheme, Bitmap photoMini, Bitmap landscape, String clock, boolean halloweenTime, boolean noelTime, boolean paquesTime) {
        theme = currentTheme;
        setFestive(halloweenTime, noelTime, paquesTime);
        row = 0;
        for (int i = 0; i < 6; i++) if (THEMES[i].equals(theme)) card = i;
        for (ThumbView t : thumbs) t.set(clock, "photo".equals(t.theme) ? photoMini : "dessin".equals(t.theme) ? landscape : null);
        darkRow.setVisibility("minimal".equals(theme) ? VISIBLE : GONE);
        lifeRow.setVisibility("dessin".equals(theme) ? VISIBLE : GONE);
        halRow.setVisibility(halShown ? VISIBLE : GONE);
        noelRow.setVisibility(noelShown ? VISIBLE : GONE);
        paqRow.setVisibility(paqShown ? VISIBLE : GONE);
        render(false);
    }

    /** Ouverture : le voile apparaît en fondu, la carte monte légèrement en grandissant. */
    void show(boolean animate) {
        animate().cancel();
        sheet.animate().cancel();
        setVisibility(VISIBLE);
        if (!animate) {
            setAlpha(1);
            sheet.setAlpha(1);
            sheet.setScaleX(1);
            sheet.setScaleY(1);
            sheet.setTranslationY(0);
            return;
        }
        setAlpha(0);
        sheet.setAlpha(0);
        sheet.setScaleX(.96f);
        sheet.setScaleY(.96f);
        sheet.setTranslationY(1.5f * vw);
        animate().alpha(1).setStartDelay(0).setDuration(220);
        sheet.animate().alpha(1).scaleX(1).scaleY(1).translationY(0).setDuration(380).setInterpolator(MainActivity.EASE);
    }

    /** Fermeture, plus rapide : la carte redescend un peu en s'effaçant, puis le voile disparaît. */
    void hide() {
        sheet.animate().cancel();
        sheet.animate().alpha(0).scaleX(.97f).scaleY(.97f).translationY(.8f * vw).setDuration(180)
                .setInterpolator(new AccelerateInterpolator());
        animate().cancel();
        animate().alpha(0).setStartDelay(60).setDuration(180).withEndAction(() -> setVisibility(GONE));
    }

    void setAnimations(boolean on) {
        animSwitch.set(on);
    }

    void setLifeFx(boolean on) {
        lifeSwitch.set(on);
    }

    void setHalloween(boolean on) {
        halSwitch.set(on);
    }

    void setNoel(boolean on) {
        noelSwitch.set(on);
    }

    void setPaques(boolean on) {
        paqSwitch.set(on);
    }

    /** Version claire (Minimal clair) ou sombre (tous les autres cas), comme dans la version web. */
    void setLight(boolean l) {
        light = l;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(l ? 0xF7FAFAF8 : 0xF51A1C20);
        bg.setCornerRadius(1.9f * vw);
        bg.setStroke(1, l ? 0xB3FFFFFF : 0x29FFFFFF);
        sheet.setBackground(bg);
        int fg = l ? 0xFF1E2A24 : 0xFFFFFFFF;
        for (TextView t : texts) t.setTextColor(fg);
        for (TextView c : checks) {
            if (c == null) continue;
            GradientDrawable cb = new GradientDrawable();
            cb.setShape(GradientDrawable.OVAL);
            cb.setColor(l ? 0xFF1E2A24 : 0xFFFFFFFF);
            c.setBackground(cb);
            c.setTextColor(l ? 0xFFFFFFFF : 0xFF1A1C20);
        }
        animSwitch.setOffColor(l ? 0x401E2A24 : 0x40FFFFFF);
        lifeSwitch.setOffColor(l ? 0x401E2A24 : 0x40FFFFFF);
        halSwitch.setOffColor(l ? 0x401E2A24 : 0x40FFFFFF);
        noelSwitch.setOffColor(l ? 0x401E2A24 : 0x40FFFFFF);
        paqSwitch.setOffColor(l ? 0x401E2A24 : 0x40FFFFFF);
        for (ThumbView t : thumbs) t.setRingColor(l ? 0xE61E2A24 : 0xFFFFFFFF);
        render(false);
    }

    boolean onKey(int keyCode) {
        String[] rows = rows();
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (row > 0) row--;
                else if (card >= 3) card -= 3;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (row == 0 && card < 3) card += 3;
                else row = Math.min(rows.length - 1, row + 1);
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                int d = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                if (row == 0) card = Math.max(0, Math.min(5, card + d));
                else if ("dark".equals(rows[row])) host.cycleMinMode(d);
                else if ("blue".equals(rows[row])) host.cycleBlueMode(d);
                else if ("light".equals(rows[row])) host.stepBrightness(d);
                else if ("level".equals(rows[row])) host.cycleBlueLevel(d);
                else if ("life".equals(rows[row])) host.setLifeFx(!lifeSwitch.on);
                else if ("halloween".equals(rows[row])) host.setHalloween(!halSwitch.on);
                else if ("noel".equals(rows[row])) host.setNoel(!noelSwitch.on);
                else if ("paques".equals(rows[row])) host.setPaques(!paqSwitch.on);
                else host.setAnimations(!animSwitch.on);
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (row == 0) host.applyTheme(THEMES[card]);
                else if ("dark".equals(rows[row])) host.cycleMinMode(1);
                else if ("blue".equals(rows[row])) host.cycleBlueMode(1);
                else if ("level".equals(rows[row])) host.cycleBlueLevel(1);
                else if ("life".equals(rows[row])) host.setLifeFx(!lifeSwitch.on);
                else if ("halloween".equals(rows[row])) host.setHalloween(!halSwitch.on);
                else if ("noel".equals(rows[row])) host.setNoel(!noelSwitch.on);
                else if ("paques".equals(rows[row])) host.setPaques(!paqSwitch.on);
                else host.setAnimations(!animSwitch.on);
                break;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                host.closePrefs();
                return true;
            default:
                return false;
        }
        render(true);
        return true;
    }

    private void render(boolean animate) {
        String[] rows = rows();
        for (int i = 0; i < 6; i++) {
            boolean sel = row == 0 && i == card;
            thumbs[i].setSel(sel);
            float dy = sel ? -.5f * vw : 0;
            if (animate) cards[i].animate().translationY(dy).setDuration(350).setInterpolator(MainActivity.EASE);
            else cards[i].setTranslationY(dy);
            if (checks[i] != null) checks[i].setVisibility(THEMES[i].equals(theme) ? VISIBLE : INVISIBLE);
        }
        String mode = host.minMode();
        for (String[] m : MIN_MODES) if (m[0].equals(mode)) darkValue.setText(m[1]);
        darkRow.setBackground(rowBg("dark".equals(rows[row])));
        String bm = host.blueMode();
        blueValue.setText(BlueFilter.ON.equals(bm) ? "Activé" : BlueFilter.AUTO.equals(bm) ? "Auto" : "Désactivé");
        levelValue.setText(new String[]{"Légère", "Moyenne", "Forte"}[Math.max(0, Math.min(2, host.blueLevel()))]);
        levelRow.setVisibility(BlueFilter.OFF.equals(bm) ? GONE : VISIBLE);
        blueRow.setBackground(rowBg("blue".equals(rows[row])));
        lightRow.setBackground(rowBg("light".equals(rows[row])));
        lightSlider.set((host.brightness() - 30) / 70f);
        lightValue.setText(host.brightness() + " %");
        levelRow.setBackground(rowBg("level".equals(rows[row])));
        animRow.setBackground(rowBg("anim".equals(rows[row])));
        lifeRow.setBackground(rowBg("life".equals(rows[row])));
        halRow.setBackground(rowBg("halloween".equals(rows[row])));
        noelRow.setBackground(rowBg("noel".equals(rows[row])));
        paqRow.setBackground(rowBg("paques".equals(rows[row])));
        keepVisible(rows[row], animate);
    }

    private GradientDrawable rowBg(boolean sel) {
        GradientDrawable rb = new GradientDrawable();
        rb.setCornerRadius(vw);
        if (!sel) rb.setColor(0);
        else if (light) {
            rb.setColor(0xFFFFFFFF);
            rb.setStroke(px(.14f), 0xD91E2A24);
        } else {
            rb.setColor(0x21FFFFFF);
            rb.setStroke(px(.14f), 0xFFFFFFFF);
        }
        return rb;
    }

    /* ===== Outils ===== */

    private int px(float v) {
        return Math.round(v * vw);
    }

    private TextView text(String s, float sizeVw, int weight, float alpha) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeVw * vw);
        t.setTypeface(host.font(weight));
        t.setTextColor(0xFFFFFFFF);
        t.setAlpha(alpha);
        t.setIncludeFontPadding(false);
        texts.add(t);
        return t;
    }

    private TextView section(String s) {
        TextView t = text(s, .9f, 600, .55f);
        t.setLetterSpacing(.18f);
        t.setAllCaps(true);
        return t;
    }

    private static LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(l, t, r, b);
        return p;
    }

    /** Interrupteur vert / gris, pastille qui glisse. */
    /** Curseur : piste, partie remplie, pastille. */
    static class SliderView extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), knob = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float vw;
        private float pos = 1;

        SliderView(Context ctx, float vw) {
            super(ctx);
            this.vw = vw;
            knob.setColor(0xFFFFFFFF);
            knob.setShadowLayer(.3f * vw, 0, 2, 0x40000000);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        void set(float p) {
            pos = Math.max(0, Math.min(1, p));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = .65f * vw, th = .45f * vw, x0 = r, x1 = w - r, cy = h / 2;
            track.setColor(0x40FFFFFF);
            c.drawRoundRect(x0, cy - th / 2, x1, cy + th / 2, th / 2, th / 2, track);
            float x = x0 + (x1 - x0) * pos;
            track.setColor(0xFF2F8A5B);
            c.drawRoundRect(x0, cy - th / 2, x, cy + th / 2, th / 2, th / 2, track);
            c.drawCircle(x, cy, r, knob);
        }
    }

    static class SwitchView extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), knob = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float vw;
        boolean on;
        private float pos;
        private int offColor = 0x40FFFFFF;

        void setOffColor(int c) {
            offColor = c;
            invalidate();
        }

        SwitchView(Context ctx, float vw, boolean on) {
            super(ctx);
            this.vw = vw;
            this.on = on;
            pos = on ? 1 : 0;
            knob.setColor(0xFFFFFFFF);
            knob.setShadowLayer(.3f * vw, 0, 2, 0x40000000);
            setLayerType(LAYER_TYPE_SOFTWARE, null); // ombre de la pastille
        }

        void set(boolean v) {
            if (v == on) return;
            on = v;
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(pos, v ? 1 : 0).setDuration(300);
            a.setInterpolator(MainActivity.EASE);
            a.addUpdateListener(an -> { pos = (float) an.getAnimatedValue(); invalidate(); });
            a.start();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            track.setColor(blend(offColor, 0xFF2F8A5B, pos));
            c.drawRoundRect(0, 0, w, h, h / 2, h / 2, track);
            float r = .75f * vw, x = .2f * vw + r + pos * 1.5f * vw;
            c.drawCircle(x, h / 2, r, knob);
        }

        private static int blend(int a, int b, float t) {
            int r = 0;
            for (int s = 0; s < 32; s += 8) {
                int ca = (a >>> s) & 0xFF, cb = (b >>> s) & 0xFF;
                r |= Math.round(ca + (cb - ca) * t) << s;
            }
            return r;
        }
    }

    /** Aperçu d'un thème : esquisse de sa mise en page (photo réelle pour Modern). */
    static class ThumbView extends View {
        final String theme;
        private final float vw, radius;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private Bitmap photo;
        private String clock = "";
        private boolean sel;

        ThumbView(Context ctx, String theme, float vw) {
            super(ctx);
            this.theme = theme;
            this.vw = vw;
            radius = 1.1f * vw;
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(.4f * vw);
            ring.setColor(0xFFFFFFFF);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
                }
            });
            setClipToOutline(true);
            setElevation(.6f * vw);
        }

        void set(String clock, Bitmap photo) {
            this.clock = clock;
            this.photo = photo;
            invalidate();
        }

        void setRingColor(int c) {
            ring.setColor(c);
            invalidate();
        }

        void setSel(boolean s) {
            if (s == sel) return;
            sel = s;
            setElevation((s ? 1.6f : .6f) * vw);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setShader(null);
            p.setAlpha(255);
            switch (theme) {
                case "photo":
                    if (photo != null) {
                        float s = Math.max(w / photo.getWidth(), h / photo.getHeight());
                        float bw = w / s, bh = h / s;
                        int l = Math.round((photo.getWidth() - bw) / 2), t = Math.round((photo.getHeight() - bh) / 2);
                        c.drawBitmap(photo, new Rect(l, t, l + Math.round(bw), t + Math.round(bh)), new RectF(0, 0, w, h), p);
                    } else c.drawColor(0xFF3A4A5C);
                    p.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x73000000, 0x0D000000, 0x80000000},
                            new float[]{0, .4f, 1}, Shader.TileMode.CLAMP));
                    c.drawRect(0, 0, w, h, p);
                    p.setShader(null);
                    pill(c, .065f * w, .09f * h, .16f * w, .03f * h, 0xE6FFFFFF);
                    pill(c, .65f * w, .07f * h, .13f * w, .06f * h, 0x59FFFFFF);
                    pill(c, w - .06f * w - .13f * w, .07f * h, .13f * w, .06f * h, 0x59FFFFFF);
                    pill(c, .09f * w, h - .29f * h - .045f * h, .2f * w, .045f * h, 0xE6FFFFFF);
                    clockText(c, .06f * w, .15f * h, 1.9f * vw, 0xFFFFFFFF, false);
                    dock(c, w, h, 0x7314161A, true);
                    break;
                case "dessin":
                    if (photo != null) { // le paysage de la saison et du moment
                        c.drawBitmap(photo, null, new RectF(0, 0, w, h), p);
                        clockText(c, w / 2, .14f * h, 2.2f * vw, 0xFF2C3A33, true);
                        dock(c, w, h, 0x59FFFFFF, true);
                        break;
                    }
                    p.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFFB9DFF0, 0xFFDDF1F3, 0xFFF3F8EC}, null, Shader.TileMode.CLAMP));
                    c.drawRect(0, 0, w, h, p);
                    p.setShader(null);
                    hill(c, w, h, .62f, 0xFF9DBB8E);
                    hill(c, w, h, .74f, 0xFF6F9A6B);
                    clockText(c, w / 2, .14f * h, 2.2f * vw, 0xFF2C3A33, true);
                    dock(c, w, h, 0x59FFFFFF, true);
                    break;
                case "bullit":
                    p.setShader(new RadialGradient(w / 2, 0, .9f * w, 0xFF1C2230, 0xFF0B0D12, Shader.TileMode.CLAMP));
                    c.drawRect(0, 0, w, h, p);
                    p.setShader(null);
                    clockText(c, .05f * w, .07f * h, 1.4f * vw, 0xFFFFFFFF, false);
                    float gw = .9f * w, cell = gw / 6;
                    for (int i = 0; i < 18; i++) {
                        float x = .05f * w + (i % 6) * cell, y = .3f * h + (i / 6) * cell * .62f;
                        tile(c, x + cell * .08f, y, cell * .84f, cell * .5f, i % 5 == 1 ? 0xFFE50914 : i % 7 == 2 ? 0xFF1E40D8 : 0x2EFFFFFF);
                    }
                    break;
                case "ambient":
                    c.drawColor(0xFF0B0C10);
                    p.setShader(new RadialGradient(.25f * w, .85f * h, .7f * w, 0xCCE50914, 0x00E50914, Shader.TileMode.CLAMP));
                    c.drawRect(0, 0, w, h, p);
                    p.setShader(null);
                    clockText(c, .06f * w, .15f * h, 1.9f * vw, 0xFFFFFFFF, false);
                    dock(c, w, h, 0x59FFFFFF, false);
                    break;
                case "minimal":
                    c.drawColor(0xFFF3F1EC);
                    clockText(c, .07f * w, .2f * h, 3.4f * vw, 0xFF1E1F22, false);
                    for (int i = 0; i < 5; i++) pill(c, .6f * w, (.22f + i * .12f) * h, (i == 0 ? .3f : .24f) * w, .05f * h, i == 0 ? 0xFF1E1F22 : 0x401E1F22);
                    break;
                case "kids":
                    p.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFFA9DCFF, 0xFFDFF3FF, 0xFFFFF4D6},
                            new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
                    c.drawRect(0, 0, w, h, p);
                    p.setShader(null);
                    p.setColor(0xFFFF7A59);
                    p.setTextSize(1.3f * vw);
                    p.setFakeBoldText(true);
                    p.setTextAlign(Paint.Align.CENTER);
                    c.drawText("Coucou !", w / 2, .28f * h, p);
                    p.setFakeBoldText(false);
                    p.setTextAlign(Paint.Align.LEFT);
                    int[] kc = {0xFFFF0033, 0xFF113CCF, 0xFF2FB36B};
                    for (int i = 0; i < 3; i++) {
                        c.save();
                        float x = .14f * w + i * .26f * w;
                        c.rotate(i == 1 ? 3 : -3, x + .11f * w, .6f * h);
                        tile(c, x, .45f * h, .22f * w, .3f * h, kc[i]);
                        c.restore();
                    }
                    break;
            }
            if (sel) c.drawRoundRect(0, 0, w, h, radius, radius, ring);
        }

        private void clockText(Canvas c, float x, float y, float size, int color, boolean center) {
            p.setShader(null);
            p.setColor(color);
            p.setTextSize(size);
            p.setTextAlign(center ? Paint.Align.CENTER : Paint.Align.LEFT);
            p.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
            c.drawText(clock, x, y + size * .8f, p);
            p.setTypeface(null);
            p.setTextAlign(Paint.Align.LEFT);
        }

        private void pill(Canvas c, float x, float y, float w, float h, int color) {
            p.setShader(null);
            p.setColor(color);
            c.drawRoundRect(x, y, x + w, y + h, h / 2, h / 2, p);
        }

        private void tile(Canvas c, float x, float y, float w, float h, int color) {
            p.setShader(null);
            p.setColor(color);
            c.drawRoundRect(x, y, x + w, y + h, .3f * vw, .3f * vw, p);
        }

        private void hill(Canvas c, float w, float h, float base, int color) {
            Path path = new Path();
            path.moveTo(0, base * h);
            path.quadTo(.3f * w, (base - .14f) * h, .55f * w, base * h);
            path.quadTo(.8f * w, (base + .1f) * h, w, (base - .06f) * h);
            path.lineTo(w, h);
            path.lineTo(0, h);
            path.close();
            p.setShader(null);
            p.setColor(color);
            c.drawPath(path, p);
        }

        /** Dock esquissé : 4 tuiles (blanche, Netflix, bleue, violette), la première sélectionnée. */
        private void dock(Canvas c, float w, float h, int bg, boolean ringFirst) {
            float l = .08f * w, rr = w - .08f * w, b = h - .07f * h, t = b - .18f * h, pad = .024f * w;
            p.setShader(null);
            p.setColor(bg);
            c.drawRoundRect(l, t, rr, b, .6f * vw, .6f * vw, p);
            int[] cols = {0xD9FFFFFF, 0xFFE50914, 0xFF1E40D8, 0xFF8A4FF0};
            float gap = .03f * w, tw = (rr - l - 2 * pad - 3 * gap) / 4;
            for (int i = 0; i < 4; i++) {
                float x = l + pad + i * (tw + gap);
                tile(c, x, t + pad, tw, b - t - 2 * pad, cols[i]);
                if (i == 0 && ringFirst) {
                    Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
                    s.setStyle(Paint.Style.STROKE);
                    s.setStrokeWidth(.12f * vw);
                    s.setColor(0xFFFFFFFF);
                    c.drawRoundRect(x, t + pad, x + tw, b - pad, .3f * vw, .3f * vw, s);
                }
            }
        }
    }
}
