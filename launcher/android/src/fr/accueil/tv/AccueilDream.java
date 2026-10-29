package fr.accueil.tv;

import android.graphics.Color;
import android.service.dreams.DreamService;
import android.webkit.WebSettings;
import android.webkit.WebView;

/** Économiseur d'écran : le paysage de l'accueil en mode veille. Une touche le ferme. */
public class AccueilDream extends DreamService {

    private WebView web;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(0xE9, 0xEF, 0xE4));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true); // mêmes réglages que l'accueil (animations, rangement)
        web.loadUrl("file:///android_asset/index.html?veille=1");
        setContentView(web);
    }

    @Override
    public void onDetachedFromWindow() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDetachedFromWindow();
    }
}
