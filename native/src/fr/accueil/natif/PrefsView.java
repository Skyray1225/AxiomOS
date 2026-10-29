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

/**
 * Fenêtre « Préférences » (thème Modern) : 6 cartes de thème avec aperçu, puis l'interrupteur
 * « Animations du décor ». ← → choisir, OK appliquer, ↑ ↓ naviguer, Retour fermer.
 */
class PrefsView extends FrameLayout {

    interface Host {
        void applyTheme(String id);
        void setAnimations(boolean on);
        void closePrefs();
        Typeface font(int weight);
    }

    static final String[] THEMES = {"dessin", "photo", "bullit", "ambient", "minimal", "kids"};
    static final String[] NAMES = {"Thinking", "Modern", "Bullit", "Ambient", "Minimal", "Kids"};
    static final String AVAILABLE = "photo"; // seul thème porté en natif pour l'instant

    private final Host host;
    private final float vw;
    private final ThumbView[] thumbs = new ThumbView[6];
    private final LinearLayout[] cards = new LinearLayout[6];
    private final LinearLayout sheet, animRow;
    private final SwitchView animSwitch;
    private int row = 0, card = 1; // row 0 = thèmes, 1 = animations
    private String theme = "photo";

    PrefsView(Context ctx, Host host, float vw, boolean animOn) {
        super(ctx);
        this.host = host;
        this.vw = vw;
        setBackgroundColor(0x73080C12);

        LinearLayout box = sheet = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false); // la carte sélectionnée se soulève
        box.setPadding(px(2.6f), px(2.2f), px(2.6f), px(1.7f));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF51A1C20);
        bg.setCornerRadius(1.9f * vw);
        bg.setStroke(1, 0x29FFFFFF);
        box.setBackground(bg);
        box.setElevation(3 * vw);
        LayoutParams blp = new LayoutParams(px(58), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        addView(box, blp);

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
                if (AVAILABLE.equals(THEMES[i])) {
                    TextView check = text("✓", .9f, 600, 1f);
                    check.setTextColor(0xFF1A1C20);
                    check.setGravity(Gravity.CENTER);
                    GradientDrawable cb = new GradientDrawable();
                    cb.setShape(GradientDrawable.OVAL);
                    cb.setColor(0xFFFFFFFF);
                    check.setBackground(cb);
                    check.setTag("check");
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
        animRow = new LinearLayout(ctx);
        animRow.setOrientation(LinearLayout.HORIZONTAL);
        animRow.setGravity(Gravity.CENTER_VERTICAL);
        animRow.setPadding(px(1.3f), px(1), px(1.3f), px(1));
        LinearLayout labels = new LinearLayout(ctx);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text("Animations du décor", 1.25f, 400, 1f));
        labels.addView(text("Lueur sur l'appli sélectionnée", .9f, 400, .55f), lp(0, px(.2f), 0, 0));
        animRow.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        animSwitch = new SwitchView(ctx, vw, animOn);
        animRow.addView(animSwitch, new LinearLayout.LayoutParams(px(3.4f), px(1.9f)));
        box.addView(animRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = text("← → choisir · OK appliquer · ↑ ↓ naviguer · Retour fermer", .95f, 400, .55f);
        hint.setGravity(Gravity.CENTER);
        box.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
            topMargin = px(1.4f);
        }});
    }

    /** Ouverture : sélection sur le thème actuel, miniature Modern sur la photo affichée. */
    void open(String currentTheme, Bitmap photoMini, String clock) {
        theme = currentTheme;
        row = 0;
        for (int i = 0; i < 6; i++) if (THEMES[i].equals(theme)) card = i;
        for (ThumbView t : thumbs) t.set(clock, "photo".equals(t.theme) ? photoMini : null);
        render(false);
    }

    /** Ouverture : le voile apparaît en fondu, la carte monte légèrement en grandissant. */
    void show() {
        animate().cancel();
        sheet.animate().cancel();
        setVisibility(VISIBLE);
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

    boolean onKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (row > 0) row--;
                else if (card >= 3) card -= 3;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (row == 0 && card < 3) card += 3;
                else row = 1;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                int d = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                if (row == 0) card = Math.max(0, Math.min(5, card + d));
                else host.setAnimations(!animSwitch.on);
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (row == 0) host.applyTheme(THEMES[card]);
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
        for (int i = 0; i < 6; i++) {
            boolean sel = row == 0 && i == card;
            thumbs[i].setSel(sel);
            float dy = sel ? -.5f * vw : 0;
            if (animate) cards[i].animate().translationY(dy).setDuration(350).setInterpolator(MainActivity.EASE);
            else cards[i].setTranslationY(dy);
        }
        GradientDrawable rb = new GradientDrawable();
        rb.setCornerRadius(vw);
        if (row == 1) {
            rb.setColor(0x21FFFFFF);
            rb.setStroke(px(.14f), 0xFFFFFFFF);
        } else {
            rb.setColor(0);
        }
        animRow.setBackground(rb);
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
    static class SwitchView extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), knob = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float vw;
        boolean on;
        private float pos;

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
            track.setColor(blend(0x40FFFFFF, 0xFF2F8A5B, pos));
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
