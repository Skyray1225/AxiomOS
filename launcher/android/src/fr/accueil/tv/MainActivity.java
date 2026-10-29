package fr.accueil.tv;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    // Ordre de préférence dans le dock ; les autres applis suivent par ordre alphabétique.
    private static final List<String> PRIORITY = Arrays.asList(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom",
            "com.canal.android.canal",
            "tv.twitch.android.app",
            "fr.tf1.mytf1",
            "fr.m6.m6replay",
            "fr.francetv.pluzz",
            "tv.arte.plus7");
    private static final String SETTINGS = "com.android.tv.settings";

    private WebView web;
    private ImageView splash; // dernier paysage affiché, montré pendant le chargement
    private boolean introPending; // intro AxiomOS à jouer (premier affichage après le démarrage)

    private boolean hdmiConnected = true; // la télé reçoit-elle l'image ?
    private long lastIntro = 0;

    /** Ramène l'accueil au premier plan et joue l'intro (pas deux fois en moins de 15 s). */
    private void introNow(int delayMs) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastIntro < 15000) return;
        lastIntro = now;
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra("intro", true)
                .putExtra("introDelay", delayMs));
    }

    // L'intro se joue quand la télé commence à recevoir l'image (allumage de la télé ou de la box)
    private final BroadcastReceiver hdmiPlug = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean connected = intent.getIntExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, 0) == 1;
            boolean wasConnected = hdmiConnected;
            hdmiConnected = connected;
            if (isInitialStickyBroadcast()) {
                // état au lancement : télé éteinte -> l'intro du démarrage attendra la connexion
                if (!connected && introPending) { introPending = false; lastIntro = 0; }
                return;
            }
            if (connected && !wasConnected) introNow(2000);
        }
    };

    // Sortie de veille sans coupure HDMI
    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (hdmiConnected) introNow(2500);
        }
    };
    private int forcedWeather = -1; // test : am start ... --ei meteo <code WMO>
    private String forcedSeason = ""; // test : am start ... --es saison hiver
    private final Map<String, String> bannerCache = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        forcedWeather = getIntent().getIntExtra("meteo", -1);
        if (getIntent().hasExtra("saison")) forcedSeason = getIntent().getStringExtra("saison");
        introPending = getIntent().getBooleanExtra("intro", false) || bootIntroDue();
        WebView.setWebContentsDebuggingEnabled(true); // inspection depuis l'ordinateur via ADB
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(0xE9, 0xEF, 0xE4));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.addJavascriptInterface(new Bridge(), "Launcher");
        web.loadUrl("file:///android_asset/index.html");

        FrameLayout root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        splash = new ImageView(this);
        splash.setScaleType(ImageView.ScaleType.CENTER_CROP);
        File snap = snapshotFile();
        Bitmap bmp = !introPending && snap.exists() ? BitmapFactory.decodeFile(snap.getPath()) : null;
        if (bmp != null) splash.setImageBitmap(bmp);
        else if (introPending) splash.setBackgroundColor(Color.rgb(0x05, 0x08, 0x0F)); // fond de l'intro
        else splash.setBackgroundColor(Color.rgb(0xE9, 0xEF, 0xE4));
        root.addView(splash, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        root.postDelayed(this::hideSplash, 6000); // sécurité si la page ne signale jamais qu'elle est prête
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
        registerReceiver(hdmiPlug, new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG));
        if (introPending) lastIntro = SystemClock.elapsedRealtime();
        web.requestFocus();
        hideSystemUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        web.requestFocus();
        web.evaluateJavascript("window.refreshApps && refreshApps(); window.wake && wake()", null);
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(screenOn);
            unregisterReceiver(hdmiPlug);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra("meteo")) {
            forcedWeather = intent.getIntExtra("meteo", -1);
            web.evaluateJavascript("window.forceWeather && forceWeather(" + forcedWeather + ")", null);
        }
        if (intent.getBooleanExtra("intro", false)) {
            int delay = intent.getIntExtra("introDelay", 0);
            web.evaluateJavascript("window.playIntro && playIntro(" + delay + ")", null);
        }
        if (intent.hasExtra("saison")) {
            forcedSeason = intent.getStringExtra("saison");
            web.evaluateJavascript("window.forceSeason && forceSeason('" + forcedSeason.replaceAll("[^a-z]", "") + "')", null);
        }
    }

    @Override
    public void onBackPressed() {
        // Écran d'accueil : le bouton retour sert seulement à quitter le mode édition ou les paramètres.
        web.evaluateJavascript("window.onBack && onBack()", null);
    }

    /** Vrai une seule fois par démarrage de la box, dans les 10 minutes qui suivent. */
    private boolean bootIntroDue() {
        long bootMinute = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60000;
        SharedPreferences prefs = getSharedPreferences("accueil", MODE_PRIVATE);
        if (Math.abs(prefs.getLong("introBoot", -10) - bootMinute) <= 1) return false;
        if (SystemClock.elapsedRealtime() > 10 * 60 * 1000) return false;
        prefs.edit().putLong("introBoot", bootMinute).apply();
        return true;
    }

    private File snapshotFile() {
        return new File(getFilesDir(), "paysage.jpg");
    }

    private void hideSplash() {
        final ImageView s = splash;
        if (s == null) return;
        splash = null;
        s.animate().alpha(0f).setDuration(450).withEndAction(() -> {
            ViewGroup parent = (ViewGroup) s.getParent();
            if (parent != null) parent.removeView(s);
        }).start();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    /** Bannière en 960x540 pour l'affichage en grand (thème Ambient, style vitrine). */
    private String bannerHi(String pkg, String cls) {
        try {
            PackageManager pm = getPackageManager();
            android.content.ComponentName cn = new android.content.ComponentName(pkg, cls);
            Drawable d = pm.getActivityBanner(cn);
            if (d == null) d = pm.getApplicationBanner(pkg);
            if (d == null) return null;
            Bitmap bmp = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, 960, 540);
            d.draw(new Canvas(bmp));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.WEBP, 90, out);
            bmp.recycle();
            return "data:image/webp;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private String banner(ResolveInfo ri) {
        String key = ri.activityInfo.packageName + "/" + ri.activityInfo.name;
        String cached = bannerCache.get(key);
        if (cached != null) return cached;

        PackageManager pm = getPackageManager();
        Drawable d = ri.activityInfo.loadBanner(pm);
        if (d == null) d = ri.activityInfo.applicationInfo.loadBanner(pm);
        boolean isIcon = d == null;
        if (isIcon) d = ri.loadIcon(pm);

        int w = 320, h = 180;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        if (isIcon) {
            c.drawColor(Color.WHITE);
            int size = 120;
            d.setBounds((w - size) / 2, (h - size) / 2, (w + size) / 2, (h + size) / 2);
        } else {
            d.setBounds(0, 0, w, h);
        }
        d.draw(c);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.WEBP, 85, out); // bien plus léger que le PNG
        bmp.recycle();
        String uri = "data:image/webp;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        bannerCache.put(key, uri);
        return uri;
    }

    private int rank(ResolveInfo ri) {
        String pkg = ri.activityInfo.packageName;
        if (pkg.equals(SETTINGS)) return Integer.MAX_VALUE;
        int i = PRIORITY.indexOf(pkg);
        return i >= 0 ? i : PRIORITY.size();
    }

    class Bridge {
        @JavascriptInterface
        public String apps() {
            final PackageManager pm = getPackageManager();
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            List<ResolveInfo> list = new ArrayList<>();
            for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                if (!ri.activityInfo.packageName.equals(getPackageName())) list.add(ri);
            }
            Collections.sort(list, new Comparator<ResolveInfo>() {
                @Override
                public int compare(ResolveInfo a, ResolveInfo b) {
                    int r = Integer.compare(rank(a), rank(b));
                    return r != 0 ? r : a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
                }
            });

            JSONArray arr = new JSONArray();
            try {
                for (ResolveInfo ri : list) {
                    JSONObject o = new JSONObject();
                    o.put("name", ri.loadLabel(pm).toString());
                    o.put("pkg", ri.activityInfo.packageName);
                    o.put("cls", ri.activityInfo.name);
                    o.put("banner", banner(ri));
                    arr.put(o);
                }
            } catch (Exception ignored) {
            }
            return arr.toString();
        }

        @JavascriptInterface
        public boolean introAtBoot() {
            boolean due = introPending;
            introPending = false;
            return due;
        }

        @JavascriptInterface
        public String bannerHi(String pkg, String cls) {
            return MainActivity.this.bannerHi(pkg, cls);
        }

        @JavascriptInterface
        public void ready() {
            runOnUiThread(MainActivity.this::hideSplash);
        }

        @JavascriptInterface
        public void saveSnapshot(String base64Jpeg) {
            try {
                byte[] data = Base64.decode(base64Jpeg, Base64.DEFAULT);
                File tmp = new File(getFilesDir(), "paysage.tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(data);
                }
                tmp.renameTo(snapshotFile());
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void snapshotAsset(String path) {
            if (path == null || !path.matches("photos/[0-9]+\\.jpg")) return;
            try (java.io.InputStream in = getAssets().open(path)) {
                File tmp = new File(getFilesDir(), "paysage.tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                tmp.renameTo(snapshotFile());
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public String forcedSeason() {
            return forcedSeason == null ? "" : forcedSeason;
        }

        @JavascriptInterface
        public int forcedWeather() {
            return forcedWeather;
        }

        @JavascriptInterface
        public void openSettings() {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                launch("com.android.tv.settings", "com.android.tv.settings.MainSettings");
            }
        }

        @JavascriptInterface
        public void kidsGuard(boolean on, String pkgsJson) {
            java.util.HashSet<String> set = new java.util.HashSet<>();
            try {
                JSONArray a = new JSONArray(pkgsJson);
                for (int i = 0; i < a.length(); i++) set.add(a.getString(i));
            } catch (Exception e) {
                // liste illisible : aucune appli tierce autorisée
            }
            KidsGuard.set(MainActivity.this, on, set);
        }

        @JavascriptInterface
        public boolean kidsGuardRunning() {
            return KidsGuard.running;
        }

        @JavascriptInterface
        public void launch(String pkg, String cls) {
            Intent i = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setClassName(pkg, cls)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                startActivity(i);
            } catch (Exception e) {
                Intent fallback = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                if (fallback != null) startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }
    }
}
