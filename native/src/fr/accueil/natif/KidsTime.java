package fr.accueil.natif;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import java.util.Calendar;

/**
 * Horaires du mode Kids : plages autorisées en semaine et le week-end. En dehors, l'accueil affiche
 * l'écran de pause et ne lance plus d'appli ; à l'heure de fin, une alarme ramène l'accueil au premier
 * plan (possible grâce à l'autorisation « afficher par-dessus les autres applis »).
 */
public class KidsTime extends BroadcastReceiver {

    /** L'heure actuelle est-elle dans la plage autorisée du jour ? */
    static boolean allowed(Store s, Calendar c) {
        if (!s.kidsLimit) return true;
        int[] h = s.kidsHours;
        int dow = c.get(Calendar.DAY_OF_WEEK);
        boolean weekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY;
        int start = weekend ? h[2] : h[0], end = weekend ? h[3] : h[1];
        int m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        return m >= start && m < end;
    }

    /** Heure de reprise (aujourd'hui ou un jour suivant), pour l'écran de pause. */
    static String nextStart(Store s, Calendar now) {
        Calendar c = (Calendar) now.clone();
        for (int d = 0; d < 8; d++) {
            int dow = c.get(Calendar.DAY_OF_WEEK);
            boolean weekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY;
            int start = weekend ? s.kidsHours[2] : s.kidsHours[0];
            int m = d == 0 ? now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE) : -1;
            if (start > m) return (d == 0 ? "à " : d == 1 ? "demain à " : "") + KidsDialog.hm(start);
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return "";
    }

    /** Prochaine fin de plage autorisée, en millisecondes (ou -1). */
    private static long nextEnd(Store s) {
        Calendar c = Calendar.getInstance();
        for (int d = 0; d < 8; d++) {
            int dow = c.get(Calendar.DAY_OF_WEEK);
            boolean weekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY;
            int end = weekend ? s.kidsHours[3] : s.kidsHours[1];
            Calendar e = (Calendar) c.clone();
            e.set(Calendar.HOUR_OF_DAY, 0);
            e.set(Calendar.MINUTE, 0);
            e.set(Calendar.SECOND, 0);
            e.set(Calendar.MILLISECOND, 0);
            e.add(Calendar.MINUTE, end);
            if (e.getTimeInMillis() > System.currentTimeMillis() + 1000) return e.getTimeInMillis();
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return -1;
    }

    /** Programme (ou annule) l'alarme de fin de plage selon le thème et les réglages. */
    static void schedule(Context ctx) {
        Store s = new Store(ctx);
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 7, new Intent(ctx, KidsTime.class), PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        if (!"kids".equals(s.theme) || !s.kidsLimit) return;
        long at = nextEnd(s);
        if (at > 0) am.setExactAndAllowWhileIdle(AlarmManager.RTC, at, pi);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Store s = new Store(ctx);
        if ("kids".equals(s.theme) && !allowed(s, Calendar.getInstance()) && Settings.canDrawOverlays(ctx)) {
            // fin de la plage : l'appli en cours laisse la place à l'écran de pause de l'accueil
            ctx.startActivity(new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        }
        schedule(ctx);
    }
}
