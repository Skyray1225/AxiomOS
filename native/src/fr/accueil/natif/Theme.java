package fr.accueil.natif;

/**
 * Ce qui distingue les thèmes portés en natif (mesures en % de la largeur « vw » ou de la hauteur « vh »
 * de l'écran, reprises de la version web). Le dock prend trois formes : carrousel de 5 tuiles
 * (Modern, Ambient), grille de 6 colonnes (Bullit) ou liste de noms (Minimal).
 */
final class Theme {

    static final int CAROUSEL = 0, GRID = 1, LIST = 2; // Kids : grille de 3 colonnes

    final String id;
    final int dock;          // forme du dock
    final int columns;       // grille : colonnes ; liste : 1
    final boolean idle;      // veille après 1 min (pas dans les thèmes en grille ou en liste)
    final boolean greet;     // salutation au-dessus de l'heure
    final boolean weather;   // météo à côté de la date
    final boolean title;     // nom de l'appli au-dessus du dock
    final boolean shadow;    // ombre portée sous les textes (sur une photo ou un dégradé)
    final float topLeftVw, topTopVh, clockVw, clockSpacing, subVw, subSpacing, subTopVh, subLeftVw;
    // réglages propres à Kids (valeurs par défaut pour les autres thèmes)
    boolean center;          // bloc de l'heure centré en haut
    int clockWeight = 200, subWeight = 500;
    boolean subCaps = true;  // date en capitales

    private Theme(String id, int dock, int columns, boolean idle, boolean greet, boolean weather, boolean title, boolean shadow,
                  float topLeftVw, float topTopVh, float clockVw, float clockSpacing,
                  float subVw, float subSpacing, float subTopVh, float subLeftVw) {
        this.id = id;
        this.dock = dock;
        this.columns = columns;
        this.idle = idle;
        this.greet = greet;
        this.weather = weather;
        this.title = title;
        this.shadow = shadow;
        this.topLeftVw = topLeftVw;
        this.topTopVh = topTopVh;
        this.clockVw = clockVw;
        this.clockSpacing = clockSpacing;
        this.subVw = subVw;
        this.subSpacing = subSpacing;
        this.subTopVh = subTopVh;
        this.subLeftVw = subLeftVw;
    }

    static final Theme MODERN = new Theme("photo", CAROUSEL, 5, true, true, true, true, true,
            4.5f, 7, 6.6f, -.02f, 1.05f, .22f, .6f, .2f);
    static final Theme AMBIENT = new Theme("ambient", CAROUSEL, 5, true, true, true, true, true,
            4.5f, 7, 6.6f, -.02f, 1.05f, .22f, .6f, .2f);
    static final Theme BULLIT = new Theme("bullit", GRID, 6, false, false, false, false, false,
            3.4f, 5, 4.6f, -.02f, 1f, .2f, .4f, .15f);
    static final Theme MINIMAL = new Theme("minimal", LIST, 1, false, false, true, false, false,
            6, 14, 15, -.035f, 1.1f, .25f, 1.5f, .6f);

    static final Theme KIDS = new Theme("kids", GRID, 3, false, true, false, false, false,
            0, 5, 4.2f, -.02f, 1.2f, 0, .3f, 0);

    static {
        KIDS.center = true;
        KIDS.clockWeight = 600;
        KIDS.subWeight = 600;
        KIDS.subCaps = false;
    }

    static final Theme[] ALL = {MODERN, BULLIT, AMBIENT, MINIMAL, KIDS};

    /** Thème enregistré, ou Modern s'il n'est pas (encore) porté en natif. */
    static Theme of(String id) {
        for (Theme t : ALL) if (t.id.equals(id)) return t;
        return MODERN;
    }

    static boolean available(String id) {
        for (Theme t : ALL) if (t.id.equals(id)) return true;
        return false;
    }
}
