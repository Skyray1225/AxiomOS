package fr.accueil.natif;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Le décor commun à l'accueil et à l'économiseur d'écran, selon le thème :
 * fond (photo et vignettage la nuit, dégradé Bullit, halo Ambient, aplat Minimal clair ou sombre),
 * salutation, heure, date et météo. En veille, le bloc de l'heure descend (Modern, Ambient) ;
 * en économiseur, Bullit et Minimal centrent une grande horloge.
 */
class SceneView extends FrameLayout {

    interface PhotoListener {
        void onPhoto(Bitmap photo);
    }

    interface InkListener {
        void onInk(int ink, int inkSoft, boolean dark);
    }

    private static final int PHOTO_COUNT = 8;
    static final double LAT = 42.6986, LON = 2.8956; // Perpignan
    private static final SparseArray<Typeface> FONTS = new SparseArray<>();
    private static final int WEATHER_MS = 15 * 60 * 1000;

    final Theme theme;
    final float vw, vh;
    private final Store store;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ImageView[] photos;
    private VignetteView vignette;
    private AmbientView ambient;
    private ImageView[] lands;        // Thinking : deux paysages, en fondu l'un sur l'autre
    private WeatherView wx;           // Thinking : météo dans le paysage
    private LifeView life;            // Thinking : effets de saison
    private Bitmap landBmp;           // paysage affiché
    private int landIdx = -1, landOn = 0;
    private String landSeason;
    String forcedSeason;              // test : --es saison hiver
    int forcedCode = -1;              // test : --ei meteo 63
    private ValueAnimator inkAnim;
    private static boolean cacheCleaned;
    private final LinearLayout top;
    private final TextView greet, clock, date, temp;
    private View dot;
    private ImageView wxIcon;
    private LinearLayout wxBox;
    private int photoOn = 0, photoIdx = -1;
    private Boolean night;
    private boolean dark; // Minimal en sombre
    private int ink = Color.WHITE, inkSoft;
    private int wxCode = -1;
    private PhotoListener photoListener;
    private InkListener inkListener;
    float forcedHour = Float.NaN; // test : --ef heure 21.5

    SceneView(Context ctx, int w, int h, Theme theme, Store store) {
        super(ctx);
        this.theme = theme;
        this.store = store;
        vw = w / 100f;
        vh = h / 100f;
        LayoutParams full = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        switch (theme.id) {
            case "photo":
                setBackgroundColor(0xFF15181C);
                photos = new ImageView[2];
                for (int i = 0; i < 2; i++) {
                    photos[i] = new ImageView(ctx);
                    photos[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
                    photos[i].setAlpha(0f);
                    addView(photos[i], new LayoutParams(full));
                }
                vignette = new VignetteView(ctx);
                vignette.setAlpha(0f);
                addView(vignette, new LayoutParams(full));
                break;
            case "bullit":
                addView(new BullitBackground(ctx), new LayoutParams(full));
                break;
            case "ambient":
                ambient = new AmbientView(ctx);
                addView(ambient, new LayoutParams(full));
                break;
            case "minimal":
                setBackgroundColor(0xFFF3F1EC);
                break;
            case "kids":
                addView(new KidsBackground(ctx), new LayoutParams(full));
                break;
            case "dessin":
                setBackgroundColor(0xFFE9EFE4);
                lands = new ImageView[2];
                for (int i = 0; i < 2; i++) {
                    lands[i] = new ImageView(ctx);
                    lands[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
                    lands[i].setAlpha(0f);
                    addView(lands[i], new LayoutParams(full));
                }
                life = new LifeView(ctx);
                life.setActive(store.animations && store.lifeFx);
                addView(life, new LayoutParams(full));
                wx = new WeatherView(ctx);
                wx.setAnimations(store.animations);
                addView(wx, new LayoutParams(full));
                worker.execute(() -> {
                    Bitmap c = Landscape.weatherClouds(w, h);
                    ui.post(() -> wx.setCloudBitmap(c));
                });
                break;
        }

        // Bloc de l'heure
        // L'ombre floue des textes déborde de leurs limites : sans cela, elle est coupée net
        // et dessine des rectangles gris sur une photo claire.
        setClipChildren(false);
        top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setClipChildren(false);
        greet = text(ctx, 1.05f * vw, 500, .24f, true, theme.shadow);
        clock = text(ctx, theme.clockVw * vw, theme.clockWeight, theme.clockSpacing, false, theme.shadow);
        date = text(ctx, theme.subVw * vw, theme.subWeight, theme.subSpacing, theme.subCaps, theme.shadow);
        temp = text(ctx, theme.subVw * vw, theme.subWeight, theme.subSpacing, theme.subCaps, theme.shadow);
        if (theme == Theme.KIDS) { // « Coucou ! » en orange, gras
            greet.setTextSize(TypedValue.COMPLEX_UNIT_PX, 2.6f * vw);
            greet.setTypeface(font(700));
            greet.setLetterSpacing(.02f);
            greet.setAllCaps(false);
        }
        if (theme.greet) top.addView(greet, margins(theme.center ? 0 : Math.round(.2f * vw), 0, 0, Math.round((theme.center ? .2f : .4f) * vh)));
        if (theme.center) top.setGravity(Gravity.CENTER_HORIZONTAL);
        top.addView(clock, margins(0, 0, 0, 0));
        LinearLayout sub = new LinearLayout(ctx);
        sub.setOrientation(LinearLayout.HORIZONTAL);
        sub.setGravity(Gravity.CENTER_VERTICAL);
        sub.setClipChildren(false);
        sub.addView(date);
        if (theme.weather) {
            float gap = (theme == Theme.MINIMAL || theme == Theme.THINKING ? 1.2f : .8f) * vw;
            dot = new View(ctx);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Math.round(.35f * vw), Math.round(.35f * vw));
            dl.leftMargin = dl.rightMargin = Math.round(gap);
            sub.addView(dot, dl);
            wxBox = new LinearLayout(ctx);
            wxBox.setGravity(Gravity.CENTER_VERTICAL);
            wxBox.setClipChildren(false);
            wxIcon = new ImageView(ctx);
            int is = Math.round((theme == Theme.MINIMAL ? 1.4f : theme == Theme.THINKING ? 2.2f : 1.35f) * vw);
            wxBox.addView(wxIcon, new LinearLayout.LayoutParams(is, is));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tl.leftMargin = Math.round(.6f * vw);
            wxBox.addView(temp, tl);
            sub.addView(wxBox);
            dot.setVisibility(GONE); // jusqu'à la première réponse de la météo
            wxBox.setVisibility(GONE);
        }
        top.addView(sub, margins(Math.round(theme.subLeftVw * vw), Math.round(theme.subTopVh * vh), 0, 0));
        LayoutParams tlp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | (theme.center ? Gravity.CENTER_HORIZONTAL : Gravity.LEFT));
        tlp.leftMargin = Math.round(theme.topLeftVw * vw);
        tlp.topMargin = Math.round(theme.topTopVh * vh);
        addView(top, tlp);
        applyInk(false);
    }

    void setPhotoListener(PhotoListener l) {
        photoListener = l;
    }

    void setInkListener(InkListener l) {
        inkListener = l;
    }

    int ink() { return ink; }

    int inkSoft() { return inkSoft; }

    boolean isDark() { return dark; }

    AmbientView ambient() { return ambient; }

    WeatherView weather() { return wx; }

    /** Paysage du moment pour la miniature des Préférences : celui affiché, ou celui en cache (réduit). */
    Bitmap landscape() {
        if (landBmp != null) return landBmp;
        java.io.File f = cacheFile(seasonNow(), Landscape.sceneForTime(now()), Math.round(100 * vw), Math.round(100 * vh));
        if (!f.exists()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 4;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    /** Essais : météo forcée (--ei meteo 63, -1 = réelle). */
    void refreshWeather() {
        if (wx == null) return;
        if (forcedCode >= 0) {
            wxCode = forcedCode;
            wx.setWeather(forcedCode, true);
            life.setWeather(wx.kind());
            showWeatherIcon();
            if (landBmp != null) sendBackdrop();
        }
        weatherTick.run();
    }

    /** Premier affichage : photo du moment, nuit et couleurs, tout de suite (sans fondu). */
    void showNow() {
        if (photos != null) showPhoto(photoForNow(), false);
        updateNight(false);
        if (lands != null) showLandscape(false);
        if (wx != null && forcedCode >= 0) {
            wx.setWeather(forcedCode, false);
            life.setWeather(wx.kind());
        }
        tick();
    }

    /** Heure, photo, nuit et météo mises à jour tant que l'écran est visible. */
    void start() {
        minuteTick.run();
        if (theme.weather) weatherTick.run();
        if (life != null) life.start();
        if (wx != null) {
            wx.start();
            ui.removeCallbacks(burstTick);
            ui.postDelayed(burstTick, BURST_EVERY);
        }
    }

    void stop() {
        ui.removeCallbacks(minuteTick);
        ui.removeCallbacks(weatherTick);
        ui.removeCallbacks(burstTick);
        if (wx != null) wx.stop();
        if (life != null) life.stop();
    }

    /** Thinking : épisode d'animation de la pluie ou de la neige (arrivée sur l'accueil, touche). */
    void burst(long ms) {
        if (wx != null) wx.burst(ms);
        if (life != null) life.burst(ms);
    }

    void setAnimations(boolean on) {
        if (wx != null) wx.setAnimations(on);
        if (life != null) life.setActive(on && store.lifeFx);
    }

    void setLifeFx(boolean on) {
        if (life != null) life.setActive(store.animations && on);
    }

    private static final long BURST_EVERY = 4 * 60 * 1000;
    private final Runnable burstTick = new Runnable() { // 30 s d'animation toutes les 4 minutes
        @Override
        public void run() {
            burst(30000);
            ui.postDelayed(this, BURST_EVERY);
        }
    };

    void release() {
        stop();
        worker.shutdownNow();
    }

    int photoIndex() {
        return Math.max(0, photoIdx >= 0 ? photoIdx : photoForNow());
    }

    /** Kids, choix des applis : la salutation s'efface sous l'aide. */
    void setGreetVisible(boolean v) {
        greet.setVisibility(v ? VISIBLE : INVISIBLE);
    }

    /** Veille (Modern, Ambient) : le bloc de l'heure descend (1,8 s). */
    void setIdle(boolean idle, boolean animate) {
        float dy = idle && theme.idle ? 24 * vh : 0;
        top.animate().cancel();
        if (animate) top.animate().translationY(dy).setDuration(1800).setInterpolator(MainActivity.EASE);
        else top.setTranslationY(dy);
    }

    /** Économiseur d'écran : heure descendue, ou grande horloge centrée (Bullit, Minimal). */
    void setSaver() {
        if (theme.idle) {
            setIdle(true, false);
            return;
        }
        LayoutParams lp = (LayoutParams) top.getLayoutParams();
        lp.gravity = Gravity.CENTER;
        lp.leftMargin = lp.topMargin = 0;
        top.setLayoutParams(lp);
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        if (theme == Theme.BULLIT) clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 11 * vw);
        if (theme == Theme.KIDS) clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 10 * vw);
    }

    /* ===== Heure, nuit, couleurs ===== */

    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            tick();
            if (photos != null) showPhoto(photoForNow(), true);
            updateNight(true);
            if (lands != null) showLandscape(true);
            ui.removeCallbacks(this);
            ui.postDelayed(this, 60000 - System.currentTimeMillis() % 60000 + 50);
        }
    };

    static String clockText() {
        Calendar n = Calendar.getInstance();
        return String.format(Locale.FRANCE, "%02d:%02d", n.get(Calendar.HOUR_OF_DAY), n.get(Calendar.MINUTE));
    }

    private void tick() {
        Calendar n = Calendar.getInstance();
        int h = n.get(Calendar.HOUR_OF_DAY);
        clock.setText(clockText());
        if (theme == Theme.KIDS) greet.setText(h < 5 || h >= 19 ? "Bonne soirée !" : "Coucou !");
        else greet.setText(h < 5 || h >= 18 ? "Bonsoir" : h < 12 ? "Bonjour" : "Bon après-midi");
        String d = new SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(n.getTime());
        date.setText(Character.toUpperCase(d.charAt(0)) + d.substring(1));
    }

    private Calendar now() {
        Calendar c = Calendar.getInstance();
        if (!Float.isNaN(forcedHour)) {
            c.set(Calendar.HOUR_OF_DAY, (int) Math.floor(forcedHour));
            c.set(Calendar.MINUTE, Math.round(forcedHour % 1 * 60));
        }
        return c;
    }

    /** Nuit = du coucher du soleil + 40 min au lever - 40 min, à Perpignan (formule NOAA, comme la version web). */
    static boolean isNight(Calendar c) {
        int m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        double[] st = sunTimes(c);
        return m < st[0] - 40 || m >= st[1] + 40;
    }

    /** Lever et coucher du soleil, en minutes locales (formule NOAA, précise à quelques minutes). */
    static double[] sunTimes(Calendar c) {
        double rad = Math.PI / 180;
        double g = 2 * Math.PI / 365 * (c.get(Calendar.DAY_OF_YEAR) - 1);
        double eqt = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g)
                - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
        double decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g)
                + 0.000907 * Math.sin(2 * g) - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
        double ha = Math.acos(Math.cos(90.833 * rad) / (Math.cos(LAT * rad) * Math.cos(decl))
                - Math.tan(LAT * rad) * Math.tan(decl)) / rad;
        double tz = c.getTimeZone().getOffset(c.getTimeInMillis()) / 60000.0;
        double noon = 720 - 4 * LON - eqt + tz;
        return new double[]{noon - 4 * ha, noon + 4 * ha};
    }

    void updateNight(boolean animate) {
        boolean n = isNight(now());
        if (night == null || night != n) {
            night = n;
            if (vignette != null) {
                vignette.animate().cancel();
                if (animate) vignette.animate().alpha(n ? 1 : 0).setDuration(3000);
                else vignette.setAlpha(n ? 1 : 0);
            }
            if (wxCode >= 0) showWeatherIcon();
            if (wx != null) wx.setNight(n);
            if (theme == Theme.THINKING) {
                applyInk(animate);
                if (landBmp != null) sendBackdrop();
            }
        }
        if (theme == Theme.MINIMAL) applyInk(animate);
    }

    /** Couleurs du texte ; Minimal : clair ou sombre selon le réglage (auto = sombre la nuit). */
    void applyInk(boolean animate) {
        if (theme == Theme.KIDS) { // bleu marine, salutation orange
            ink = 0xFF24456E;
            inkSoft = 0xBF24456E;
        } else if (theme == Theme.THINKING) { // vert sombre le jour, crème la nuit (transition 2,5 s)
            boolean n = Boolean.TRUE.equals(night);
            dark = n;
            int toInk = n ? 0xFFF4F1E8 : 0xFF2C3A33, toSoft = n ? 0xBFF4F1E8 : 0xB32C3A33;
            float sr = 1.56f * vw; // text-shadow 0 2px 30px : blanc .35 le jour, noir .3 la nuit
            int sc = n ? 0x4D000000 : 0x59FFFFFF;
            for (TextView t : new TextView[]{clock, date, temp}) t.setShadowLayer(sr, 0, 2, sc);
            if (inkAnim != null) inkAnim.cancel();
            if (animate && inkSoft != 0) {
                final int fromInk = ink, fromSoft = inkSoft;
                final ArgbEvaluator ev = new ArgbEvaluator();
                inkAnim = ValueAnimator.ofFloat(0, 1).setDuration(2500);
                inkAnim.addUpdateListener(an -> {
                    float f = an.getAnimatedFraction();
                    ink = (int) ev.evaluate(f, fromInk, toInk);
                    inkSoft = (int) ev.evaluate(f, fromSoft, toSoft);
                    paintInk();
                });
                inkAnim.start();
                return;
            }
            ink = toInk;
            inkSoft = toSoft;
        } else if (theme != Theme.MINIMAL) {
            ink = Color.WHITE;
            inkSoft = theme == Theme.BULLIT ? 0x99FFFFFF : 0xDBFFFFFF;
        } else {
            boolean d = "dark".equals(store.minMode) || ("auto".equals(store.minMode) && Boolean.TRUE.equals(night));
            boolean changed = d != dark || inkSoft == 0;
            dark = d;
            ink = d ? 0xFFF2F2F0 : 0xFF151515;
            inkSoft = d ? 0x52F2F2F0 : 0x61151515;
            int bg = d ? 0xFF111214 : 0xFFF3F1EC;
            if (!changed) return;
            if (animate) { // transition de 2 s, comme dans la version web
                int from = getBackground() instanceof android.graphics.drawable.ColorDrawable
                        ? ((android.graphics.drawable.ColorDrawable) getBackground()).getColor() : bg;
                ValueAnimator a = ValueAnimator.ofObject(new ArgbEvaluator(), from, bg).setDuration(2000);
                a.addUpdateListener(an -> setBackgroundColor((int) an.getAnimatedValue()));
                a.start();
            } else setBackgroundColor(bg);
        }
        paintInk();
    }

    private void paintInk() {
        clock.setTextColor(ink);
        // opacité .9 dans la couleur, pas sur la vue : une vue transparente est dessinée
        // dans un calque à sa taille, qui couperait l'ombre du texte
        greet.setTextColor(theme == Theme.KIDS ? 0xFFFF7A59 : (ink & 0x00FFFFFF) | 0xE6000000);
        date.setTextColor(inkSoft);
        temp.setTextColor(inkSoft);
        if (wxIcon != null) tintIcon();
        if (dot != null) dot.setBackground(dotDrawable(theme == Theme.MINIMAL ? inkSoft : (ink & 0x00FFFFFF) | 0x99000000));
        if (inkListener != null) inkListener.onInk(ink, inkSoft, dark);
    }

    private static GradientDrawable dotDrawable(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    /* ===== Météo (Open-Meteo, sans clé), toutes les 15 minutes ===== */

    private final Runnable weatherTick = new Runnable() {
        @Override
        public void run() {
            ui.removeCallbacks(this);
            ui.postDelayed(this, WEATHER_MS);
            worker.execute(() -> {
                try {
                    URL u = new URL("https://api.open-meteo.com/v1/forecast?latitude=" + LAT + "&longitude=" + LON
                            + "&current=temperature_2m,weather_code");
                    HttpURLConnection c = (HttpURLConnection) u.openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    String body;
                    try (Scanner sc = new Scanner(c.getInputStream(), "UTF-8").useDelimiter("\\A")) {
                        body = sc.hasNext() ? sc.next() : "";
                    }
                    JSONObject cur = new JSONObject(body).getJSONObject("current");
                    final double t = cur.getDouble("temperature_2m");
                    final int code = cur.getInt("weather_code");
                    ui.post(() -> showWeather(t, code));
                } catch (Exception ignored) {
                    // pas de réseau : la météo reste masquée ou garde sa dernière valeur
                }
            });
        }
    };

    private void showWeather(double t, int code) {
        if (forcedCode >= 0) code = forcedCode;
        boolean first = wxCode < 0;
        wxCode = code;
        if (wx != null) {
            String before = wx.kind();
            float va = wx.veilAlpha();
            wx.setWeather(code, !first);
            life.setWeather(wx.kind());
            if (landBmp != null && (!before.equals(wx.kind()) || va != wx.veilAlpha())) sendBackdrop();
        }
        temp.setText(Math.round(t) + "°");
        showWeatherIcon();
        dot.setVisibility(VISIBLE);
        wxBox.setVisibility(VISIBLE);
    }

    /** Code météo WMO -> icône (lune par temps clair la nuit), comme la version web. */
    private void showWeatherIcon() {
        int c = wxCode, icon;
        if (c <= 1) icon = Boolean.TRUE.equals(night) ? R.drawable.ic_wx_moon : R.drawable.ic_wx_sun;
        else if (c <= 3) icon = R.drawable.ic_wx_cloud;
        else if (c == 45 || c == 48) icon = R.drawable.ic_wx_fog;
        else if ((c >= 51 && c <= 67) || (c >= 80 && c <= 82)) icon = R.drawable.ic_wx_rain;
        else if ((c >= 71 && c <= 77) || c == 85 || c == 86) icon = R.drawable.ic_wx_snow;
        else if (c >= 95) icon = R.drawable.ic_wx_storm;
        else icon = R.drawable.ic_wx_cloud;
        wxIcon.setImageResource(icon);
        tintIcon();
    }

    /** Icône de la couleur du texte : teinte opaque, transparence appliquée à part (sinon trop claire). */
    private void tintIcon() {
        wxIcon.setColorFilter(inkSoft | 0xFF000000);
        wxIcon.setImageAlpha(inkSoft >>> 24);
    }

    /* ===== Paysage dessiné (Thinking) : saison et moment de la journée ===== */

    String seasonNow() {
        if (Landscape.season(forcedSeason) != null) return forcedSeason;
        Calendar c = now();
        return Landscape.seasonByDate(c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * Paysage de la saison et du moment. Dessiné une fois (environ une seconde) puis gardé en cache :
     * les fois suivantes, il s'affiche aussitôt. Changement de moment : fondu de 8 s, comme la version web.
     */
    void showLandscape(boolean animate) {
        final int idx = Landscape.sceneForTime(now());
        final String sz = seasonNow();
        if (idx == landIdx && sz.equals(landSeason)) return;
        landIdx = idx;
        landSeason = sz;
        final int w = Math.round(100 * vw), h = Math.round(100 * vh);
        if (!animate) {
            Bitmap cached = loadLandscape(sz, idx, w, h);
            if (cached != null) {
                setLandscape(cached, false);
                prefetch(sz, w, h);
                return;
            }
        }
        worker.execute(() -> {
            Bitmap b = loadLandscape(sz, idx, w, h);
            if (b == null) {
                b = Landscape.render(idx, sz, w, h);
                saveLandscape(b, sz, idx, w, h);
            }
            final Bitmap fb = b;
            ui.post(() -> {
                if (idx == landIdx && sz.equals(landSeason)) setLandscape(fb, true);
            });
            prefetch(sz, w, h);
        });
    }

    private void setLandscape(Bitmap b, boolean fade) {
        landBmp = b;
        final String sz = landSeason;
        final int idx = landIdx;
        worker.execute(() -> { // arbres du paysage, pour les feuilles et les lucioles
            final java.util.List<Landscape.Tree> t = Landscape.trees(idx, sz);
            ui.post(() -> {
                if (idx == landIdx && sz.equals(landSeason)) life.setScene(sz, idx, t);
            });
        });
        final ImageView old = lands[landOn];
        boolean first = old.getDrawable() == null;
        landOn = 1 - landOn;
        ImageView next = lands[landOn];
        next.setImageBitmap(b);
        next.animate().cancel();
        old.animate().cancel();
        if (!fade) {
            next.setAlpha(1f);
            old.setAlpha(0f);
            old.setImageDrawable(null);
        } else {
            long ms = first ? 600 : 8000;
            next.setAlpha(0f);
            next.animate().alpha(1).setDuration(ms);
            old.animate().alpha(0).setDuration(ms).withEndAction(() -> old.setImageDrawable(null));
        }
        sendBackdrop();
    }

    /** Les autres moments de la saison, dessinés à l'avance pour un changement sans attente. */
    private void prefetch(final String sz, final int w, final int h) {
        worker.execute(() -> {
            for (int i = 0; i < 4; i++) {
                if (cacheFile(sz, i, w, h).exists()) continue;
                Bitmap b = Landscape.render(i, sz, w, h);
                saveLandscape(b, sz, i, w, h);
                b.recycle();
            }
        });
    }

    private java.io.File cacheFile(String sz, int idx, int w, int h) {
        java.io.File dir = new java.io.File(getContext().getCacheDir(), "paysages");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        if (!cacheCleaned) { // images d'une version précédente du dessin : supprimées
            cacheCleaned = true;
            java.io.File[] old = dir.listFiles();
            if (old != null) for (java.io.File f : old)
                if (!f.getName().startsWith("v" + Landscape.VERSION + "-")) //noinspection ResultOfMethodCallIgnored
                    f.delete();
        }
        return new java.io.File(dir, "v" + Landscape.VERSION + "-" + sz + "-" + idx + "-" + w + "x" + h + ".png");
    }

    private Bitmap loadLandscape(String sz, int idx, int w, int h) {
        java.io.File f = cacheFile(sz, idx, w, h);
        if (!f.exists()) return null;
        return BitmapFactory.decodeFile(f.getPath());
    }

    private void saveLandscape(Bitmap b, String sz, int idx, int w, int h) {
        java.io.File f = cacheFile(sz, idx, w, h), tmp = new java.io.File(f.getPath() + ".tmp");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(tmp)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(f);
    }

    /** Fond du dock en verre : le paysage et les calques de la météo, en petit (flouté ensuite par le dock). */
    private void sendBackdrop() {
        if (photoListener == null || landBmp == null) return;
        int bw = 480, bh = Math.round(480 * vh / vw);
        Bitmap small = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(small);
        c.drawBitmap(landBmp, null, new android.graphics.Rect(0, 0, bw, bh), new Paint(Paint.FILTER_BITMAP_FLAG));
        wx.drawStatic(c, bw, bh);
        photoListener.onPhoto(small);
        small.recycle();
    }

    /* ===== Photo de fond (Modern), renouvelée toutes les 2 heures ===== */

    private static int photoForNow() {
        return (int) ((System.currentTimeMillis() / 7_200_000L) % PHOTO_COUNT);
    }

    Bitmap decodeAsset(String path) {
        try (InputStream in = getContext().getAssets().open(path)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    private void showPhoto(final int idx, boolean animate) {
        if (idx == photoIdx) return;
        photoIdx = idx;
        if (!animate) {
            Bitmap b = decodeAsset("photos/" + (idx + 1) + ".jpg");
            photos[photoOn].setImageBitmap(b);
            photos[photoOn].setAlpha(1f);
            if (photoListener != null) photoListener.onPhoto(b);
            return;
        }
        worker.execute(() -> {
            final Bitmap b = decodeAsset("photos/" + (idx + 1) + ".jpg");
            ui.post(() -> {
                if (b == null || idx != photoIdx) return;
                final ImageView old = photos[photoOn];
                photoOn = 1 - photoOn;
                ImageView next = photos[photoOn];
                next.setImageBitmap(b);
                next.animate().alpha(1).setDuration(3000);
                old.animate().alpha(0).setDuration(3000).withEndAction(() -> old.setImageDrawable(null));
                if (photoListener != null) photoListener.onPhoto(b);
            });
        });
    }

    /** Fond Bullit : radial-gradient(ellipse 90% 70% at 50% 0%, #1c2230, #0b0d12 70%). */
    private static class BullitBackground extends View {
        private final Paint p = new Paint();

        BullitBackground(Context ctx) {
            super(ctx);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            RadialGradient g = new RadialGradient(0, 0, 1, new int[]{0xFF1C2230, 0xFF0B0D12}, new float[]{0, .7f}, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setScale(.9f * w, .7f * h);
            m.postTranslate(w / 2f, 0);
            g.setLocalMatrix(m);
            p.setShader(g);
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawRect(0, 0, getWidth(), getHeight(), p);
        }
    }

    /** Fond Kids : ciel dégradé, soleil en haut à droite, nuage flou en bas à gauche. */
    private static class KidsBackground extends View {
        private final Paint sky = new Paint(), sun = new Paint(Paint.ANTI_ALIAS_FLAG), cloud = new Paint(Paint.ANTI_ALIAS_FLAG);

        KidsBackground(Context ctx) {
            super(ctx);
            cloud.setColor(0x8CFFFFFF);
            setLayerType(LAYER_TYPE_SOFTWARE, null); // flou du nuage (dessiné une fois)
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            float vw = w / 100f;
            sky.setShader(new android.graphics.LinearGradient(0, 0, 0, h, new int[]{0xFFA9DCFF, 0xFFDFF3FF, 0xFFFFF4D6},
                    new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
            float r = 13 * vw, cx = w + 6 * vw - r, cy = -8 * vw + r;
            sun.setShader(new RadialGradient(cx, cy, r, new int[]{0xFFFFF3A8, 0xFFFFD75A, 0x00FFD75A},
                    new float[]{0, .6f / .7f, 1}, Shader.TileMode.CLAMP));
            cloud.setMaskFilter(new android.graphics.BlurMaskFilter(vw, android.graphics.BlurMaskFilter.Blur.NORMAL));
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            float vw = w / 100f;
            c.drawRect(0, 0, w, h, sky);
            float r = 13 * vw;
            c.drawCircle(w + 6 * vw - r, -8 * vw + r, r, sun);
            c.drawOval(-8 * vw, h + 6 * vw - 16 * vw, 32 * vw, h + 6 * vw, cloud);
        }
    }

    /* ===== Outils d'affichage, partagés avec l'accueil ===== */

    /** Roboto, la police système d'Android, au poids demandé (choix de l'utilisateur). */
    static Typeface font(int weight) {
        Typeface t = FONTS.get(weight);
        if (t == null) {
            t = Build.VERSION.SDK_INT >= 28 ? Typeface.create(Typeface.SANS_SERIF, weight, false) : Typeface.SANS_SERIF;
            FONTS.put(weight, t);
        }
        return t;
    }

    static TextView text(Context ctx, float sizePx, int weight, float letterSpacing, boolean caps) {
        return text(ctx, sizePx, weight, letterSpacing, caps, true);
    }

    static TextView text(Context ctx, float sizePx, int weight, float letterSpacing, boolean caps, boolean shadow) {
        TextView v = new TextView(ctx);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
        v.setTypeface(font(weight));
        v.setLetterSpacing(letterSpacing);
        v.setTextColor(Color.WHITE);
        v.setIncludeFontPadding(false);
        v.setSingleLine(true);
        v.setAllCaps(caps); // après setSingleLine, qui remplacerait la mise en majuscules
        if (shadow) v.setShadowLayer(.007f * ctx.getResources().getDisplayMetrics().widthPixels, 0, 1, 0x59000000); // flou .7vw
        return v;
    }

    private static LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(l, t, r, b);
        return lp;
    }
}
