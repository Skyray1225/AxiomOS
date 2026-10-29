package fr.accueil.natif;

import android.graphics.Point;
import android.service.dreams.DreamService;

/**
 * Économiseur d'écran : le décor de l'accueil en veille (photo, vignettage la nuit, heure descendue),
 * sans dock ni boutons. Une touche le ferme.
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
        scene = new SceneView(this, size.x, size.y);
        scene.setIdle(true, false);
        scene.showNow();
        setContentView(scene);
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
