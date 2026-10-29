package fr.accueil.natif;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Gardien du mode Kids de l'accueil natif (service d'accessibilité, repris de la version web).
 * Inactif hors du thème Kids. En Kids :
 *  - seules les touches de navigation, de volume et de lecture passent (liste blanche) ;
 *  - si une appli non autorisée passe au premier plan, retour à l'accueil.
 * En cas d'erreur, il laisse tout passer.
 */
public class KidsGuard extends AccessibilityService {
    static final String PREFS = "kidsguard";
    static volatile boolean running = false;

    private static final Set<Integer> KEYS_OK = new HashSet<>(Arrays.asList(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND));

    // Applis système qui ouvrent l'accès au reste de la box, même sans icône d'appli.
    private static final Set<String> ALWAYS_GUARDED = new HashSet<>(Arrays.asList(
            "com.android.tv.settings", "com.android.settings", "com.google.android.katniss",
            "com.android.vending", "com.google.android.tvrecommendations"));

    private long lastBounce = 0;

    static void set(Context c, boolean on, Set<String> allowed) {
        c.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("on", on).putStringSet("allowed", allowed).apply();
    }

    private boolean active() {
        try {
            return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("on", false);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onServiceConnected() {
        running = true;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        running = false;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        try {
            return active() && !KEYS_OK.contains(e.getKeyCode()); // true = touche avalée
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent ev) {
        try {
            if (ev.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || !active()) return;
            CharSequence p = ev.getPackageName(), c = ev.getClassName();
            if (p == null || c == null) return;
            String pkg = p.toString();
            if (pkg.equals(getPackageName())) return;
            Set<String> allowed = getSharedPreferences(PREFS, MODE_PRIVATE).getStringSet("allowed", null);
            if (allowed != null && allowed.contains(pkg)) return;
            PackageManager pm = getPackageManager();
            // Seulement les vrais écrans d'appli (pas les dialogues, claviers, fenêtres système)…
            try {
                pm.getActivityInfo(new ComponentName(pkg, c.toString()), 0);
            } catch (PackageManager.NameNotFoundException nf) {
                return;
            }
            // …d'une appli visible dans la liste des applis, ou d'une appli système sensible.
            if (!ALWAYS_GUARDED.contains(pkg) && pm.getLeanbackLaunchIntentForPackage(pkg) == null
                    && pm.getLaunchIntentForPackage(pkg) == null) return;
            long now = SystemClock.uptimeMillis();
            if (now - lastBounce < 800) return;
            lastBounce = now;
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch (Exception ex) {
            // en cas de doute, ne rien faire
        }
    }

    @Override
    public void onInterrupt() {
    }
}
