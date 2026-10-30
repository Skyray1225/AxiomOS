package fr.accueil.natif;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;

import java.util.Calendar;

/**
 * Filtre anti lumière bleue sur tout l'écran, par-dessus toutes les applis (la box n'a pas de
 * « lumière nocturne » système). Un voile ambré, transparent aux touches de la télécommande, posé
 * par un service au premier plan. Mode : désactivé, activé, ou automatique (du coucher au lever
 * du soleil, comme la nuit du thème Thinking), avec un fondu à chaque changement.
 * Nécessite l'autorisation « afficher par-dessus les autres applis » (appops SYSTEM_ALERT_WINDOW).
 */
public class BlueFilter extends Service {

    static final String OFF = "off", ON = "on", AUTO = "auto";
    // ambre sombre : réchauffe les blancs sans trop éclaircir les noirs ; opacité selon l'intensité
    private static final int AMBER = 0xB36A1A;
    private static final float[] LEVELS = {.14f, .22f, .32f};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private View veil;
    private WindowManager wm;

    /** Démarre, met à jour ou arrête le filtre selon les réglages. */
    static void sync(Context ctx) {
        Store s = new Store(ctx);
        Intent i = new Intent(ctx, BlueFilter.class);
        if (OFF.equals(s.blueMode)) ctx.stopService(i);
        else if (Settings.canDrawOverlays(ctx)) ctx.startForegroundService(i);
    }

    static boolean allowed(Context ctx) {
        return Settings.canDrawOverlays(ctx);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("filtre", "Filtre lumière bleue", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, "filtre")
                .setContentTitle("Filtre lumière bleue")
                .setSmallIcon(R.drawable.ic_wx_moon)
                .build();
        startForeground(1, n);
        wm = getSystemService(WindowManager.class);
        veil = new View(this);
        veil.setBackgroundColor(0xFF000000 | AMBER);
        veil.setAlpha(0);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        try {
            wm.addView(veil, lp);
        } catch (Exception e) { // autorisation retirée entre-temps
            stopSelf();
            return;
        }
        tick.run();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ui.removeCallbacks(tick);
        tick.run(); // réglages changés : appliqués aussitôt
        return START_STICKY;
    }

    /** Chaque minute : opacité voulue selon le mode, l'intensité et l'heure. */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (veil == null) return;
            Store s = new Store(BlueFilter.this);
            boolean on = ON.equals(s.blueMode) || (AUTO.equals(s.blueMode) && SceneView.isNight(Calendar.getInstance()));
            float target = on ? LEVELS[Math.max(0, Math.min(2, s.blueLevel))] : 0;
            if (Math.abs(veil.getAlpha() - target) > .001f) veil.animate().alpha(target).setDuration(on ? 4000 : 2000);
            ui.postDelayed(this, 60000);
        }
    };

    @Override
    public void onDestroy() {
        ui.removeCallbacks(tick);
        if (veil != null) {
            try {
                wm.removeView(veil);
            } catch (Exception ignored) {
            }
            veil = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
