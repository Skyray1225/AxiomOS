package fr.accueil.natif;

/** Un élément du dock : tuile (carrousel, grille) ou ligne de texte (liste Minimal). */
interface DockItem {
    void setSelected(boolean on, boolean animate, boolean withShine);

    void setFlags(boolean pinned, boolean hidden);

    void setEditing(boolean on);
}
