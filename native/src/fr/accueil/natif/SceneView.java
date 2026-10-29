package fr.accueil.natif;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Le décor du thème Modern, commun à l'accueil et à l'économiseur d'écran : photo de fond
 * (une toutes les 2 h, fondu de 3 s), vignettage la nuit, salutation, heure et date en haut à gauche.
 * En veille, le bloc de l'heure descend de 24 % de la hauteur de l'écran.
 */
class SceneView extends FrameLayout {

    interface PhotoListener {
        void onPhoto(Bitmap photo);
    }

    private static final int PHOTO_COUNT = 8;
    private static final double LAT = 42.6986, LON = 2.8956; // Perpignan
    private static final SparseArray<Typeface> FONTS = new SparseArray<>();

    final float vw, vh;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ImageView[] photos = new ImageView[2];
    private final VignetteView vignette;
    private final LinearLayout top;
    private final TextView greet, clock, date;
    private int photoOn = 0, photoIdx = -1;
    private Boolean night;
    private PhotoListener photoListener;
    float forcedHour = Float.NaN; // test : --ef heure 21.5

    SceneView(Context ctx, int w, int h) {
        super(ctx);
        vw = w / 100f;
        vh = h / 100f;
        setBackgroundColor(0xFF15181C);
        for (int i = 0; i < 2; i++) {
            photos[i] = new ImageView(ctx);
            photos[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
            photos[i].setAlpha(0f);
            addView(photos[i], new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        vignette = new VignetteView(ctx);
        vignette.setAlpha(0f);
        addView(vignette, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.VERTICAL);
        greet = text(ctx, 1.05f * vw, 500, .24f, true);
        greet.setAlpha(.9f);
        clock = text(ctx, 6.6f * vw, 200, -.02f, false);
        date = text(ctx, 1.05f * vw, 500, .22f, true);
        date.setTextColor(0xDBFFFFFF);
        top.addView(greet, margins(Math.round(.2f * vw), 0, 0, Math.round(.4f * vh)));
        top.addView(clock, margins(0, 0, 0, 0));
        top.addView(date, margins(Math.round(.2f * vw), Math.round(.6f * vh), 0, 0));
        LayoutParams tlp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
        tlp.leftMargin = Math.round(4.5f * vw);
        tlp.topMargin = Math.round(7 * vh);
        addView(top, tlp);
    }

    void setPhotoListener(PhotoListener l) {
        photoListener = l;
    }

    /** Premier affichage : photo du moment et nuit, tout de suite (sans fondu). */
    void showNow() {
        showPhoto(photoForNow(), false);
        updateNight(false);
        tick();
    }

    /** Heure, photo et nuit vérifiées à chaque minute, tant que l'écran est visible. */
    void start() {
        minuteTick.run();
    }

    void stop() {
        ui.removeCallbacks(minuteTick);
    }

    void release() {
        stop();
        worker.shutdownNow();
    }

    int photoIndex() {
        return photoIdx;
    }

    /** Veille : le bloc de l'heure descend (1,8 s). */
    void setIdle(boolean idle, boolean animate) {
        float dy = idle ? 24 * vh : 0;
        top.animate().cancel();
        if (animate) top.animate().translationY(dy).setDuration(1800).setInterpolator(MainActivity.EASE);
        else top.setTranslationY(dy);
    }

    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            tick();
            showPhoto(photoForNow(), true);
            updateNight(true);
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
        greet.setText(h < 5 || h >= 18 ? "Bonsoir" : h < 12 ? "Bonjour" : "Bon après-midi");
        date.setText(new SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(n.getTime()));
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
        double rad = Math.PI / 180;
        int m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        double g = 2 * Math.PI / 365 * (c.get(Calendar.DAY_OF_YEAR) - 1);
        double eqt = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g)
                - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
        double decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g)
                + 0.000907 * Math.sin(2 * g) - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
        double ha = Math.acos(Math.cos(90.833 * rad) / (Math.cos(LAT * rad) * Math.cos(decl))
                - Math.tan(LAT * rad) * Math.tan(decl)) / rad;
        double tz = c.getTimeZone().getOffset(c.getTimeInMillis()) / 60000.0;
        double noon = 720 - 4 * LON - eqt + tz;
        return m < noon - 4 * ha - 40 || m >= noon + 4 * ha + 40;
    }

    void updateNight(boolean animate) {
        boolean n = isNight(now());
        if (night != null && night == n) return;
        night = n;
        vignette.animate().cancel();
        if (animate) vignette.animate().alpha(n ? 1 : 0).setDuration(3000);
        else vignette.setAlpha(n ? 1 : 0);
    }

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
        TextView v = new TextView(ctx);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
        v.setTypeface(font(weight));
        v.setLetterSpacing(letterSpacing);
        v.setTextColor(Color.WHITE);
        v.setIncludeFontPadding(false);
        v.setSingleLine(true);
        v.setAllCaps(caps); // après setSingleLine, qui remplacerait la mise en majuscules
        v.setShadowLayer(.007f * ctx.getResources().getDisplayMetrics().widthPixels, 0, 1, 0x59000000); // flou .7vw
        return v;
    }

    private static LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(l, t, r, b);
        return lp;
    }
}
