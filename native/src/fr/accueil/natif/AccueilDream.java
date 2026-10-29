package fr.accueil.natif;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.service.dreams.DreamService;

/**
 * Économiseur d'écran : le décor de l'accueil dans le thème choisi, sans dock ni boutons
 * (Modern, Ambient : heure descendue ; Bullit, Minimal : grande horloge centrée). Une touche le ferme.
 */
public class AccueilDream extends DreamService {

    private SceneView scene;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(size);
        Store store = new Store(this);
        Theme theme = Theme.of(store.theme);
        scene = new SceneView(this, size.x, size.y, theme, store);
        scene.setSaver();
        scene.showNow();
        if (theme == Theme.AMBIENT) { // couleurs de la première appli épinglée, comme le dock
            String pkg = store.pins.isEmpty() ? "" : store.pins.get(0);
            scene.ambient().show(pkg, AmbientView.palette(banner(pkg)), true);
        }
        setContentView(scene);
    }

    private Bitmap banner(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            Drawable d = pm.getApplicationBanner(pkg);
            if (d == null) return null;
            Bitmap b = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, 160, 90);
            d.draw(new Canvas(b));
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        scene.start();
    }

    @Override
    public void onDreamingStopped() {
        scene.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (scene != null) scene.release();
        scene = null;
        super.onDetachedFromWindow();
    }
}
