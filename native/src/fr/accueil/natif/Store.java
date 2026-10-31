package fr.accueil.natif;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Réglages de l'utilisateur et tri du dock, comme dans la version web.
 * Les clés et le format (JSON) sont ceux du localStorage de l'accueil web, pour pouvoir migrer tel quel.
 */
class Store {

    private static final double HALF_LIFE = 14 * 864e5; // une ouverture vieille de 2 semaines compte pour moitié

    private final SharedPreferences sp;
    final List<String> pins = new ArrayList<>();    // favoris épinglés, dans l'ordre
    final List<String> order = new ArrayList<>();   // ancien rangement manuel : sert à départager
    final Set<String> hidden = new LinkedHashSet<>();
    private final Map<String, double[]> usage = new HashMap<>(); // pkg -> {score, horodatage}
    boolean animations;
    boolean lifeFx;    // Thinking : effets de saison (feuilles, papillons, lucioles, étoiles filantes)
    boolean halloween; // Thinking : décor d'Halloween pendant sa période (activé sauf si l'utilisateur l'a coupé)
    boolean noel;      // Thinking : décor de Noël pendant sa période (idem)
    boolean paques;    // Thinking : décor de Pâques pendant sa période (idem)
    boolean perso;     // Thinking : personnage en haut à gauche
    String blueMode;   // filtre anti lumière bleue : off, on, auto (la nuit)
    int blueLevel;     // intensité du filtre : 0 légère, 1 moyenne, 2 forte
    int brightness;    // luminosité de l'écran, en % (30 … 100 ; 100 = image d'origine)
    String theme, minMode; // thème ; apparence de Minimal : auto, light, dark
    // Kids : applis autorisées, code parent (4 flèches, ex. "U,U,L,R"), thème à retrouver en quittant Kids
    static final String[] KIDS_DEFAULT = {"com.google.android.youtube.tv", "com.disney.disneyplus", "com.vectorunit.purple.googleplay"};
    final Set<String> kidsApps = new LinkedHashSet<>();
    String kidsCode, themePrev;
    boolean kidsLimit;                                  // Kids : horaires limités
    int[] kidsHours = {8 * 60, 20 * 60, 9 * 60, 21 * 60}; // semaine début/fin, week-end début/fin (minutes)
    private boolean pinsLoaded;

    Store(Context ctx) {
        sp = ctx.getSharedPreferences("reglages", Context.MODE_PRIVATE);
        readList("pins", pins);
        pinsLoaded = sp.contains("pins");
        readList("order", order);
        List<String> h = new ArrayList<>();
        readList("hidden", h);
        hidden.addAll(h);
        animations = !"false".equals(sp.getString("animations", "true"));
        lifeFx = !"false".equals(sp.getString("effets", "true"));
        halloween = !"false".equals(sp.getString("halloween2", "true")); // nouvelle clé : activé par défaut
        noel = !"false".equals(sp.getString("noel2", "true"));
        paques = !"false".equals(sp.getString("paques", "true"));
        perso = !"false".equals(sp.getString("personnage", "true"));
        blueMode = sp.getString("filtre", "off");
        blueLevel = Integer.parseInt(sp.getString("filtreForce", "1"));
        brightness = Integer.parseInt(sp.getString("luminosite", "100"));
        theme = readString("theme", "photo");
        minMode = readString("minMode", "auto");
        kidsCode = readString("kidsCode", null);
        kidsLimit = "true".equals(sp.getString("kidsLimite", "false"));
        try {
            org.json.JSONArray kh = new org.json.JSONArray(sp.getString("kidsHoraires", "[480,1200,540,1260]"));
            for (int i = 0; i < 4; i++) kidsHours[i] = kh.getInt(i);
        } catch (Exception ignored) {
        }
        themePrev = readString("themePrev", "photo");
        if (sp.contains("kidsApps")) {
            List<String> k = new ArrayList<>();
            readList("kidsApps", k);
            kidsApps.addAll(k);
        } else {
            Collections.addAll(kidsApps, KIDS_DEFAULT);
        }
        try {
            JSONObject u = new JSONObject(sp.getString("usage", "{}"));
            for (java.util.Iterator<String> it = u.keys(); it.hasNext(); ) {
                String p = it.next();
                JSONObject o = u.getJSONObject(p);
                usage.put(p, new double[]{o.getDouble("s"), o.getDouble("t")});
            }
        } catch (Exception ignored) {
        }
    }

    /** Chaîne enregistrée au format JSON ("photo"), comme dans le localStorage web. */
    private String readString(String key, String def) {
        try {
            Object v = new org.json.JSONTokener(sp.getString(key, "null")).nextValue();
            return v instanceof String ? (String) v : def;
        } catch (Exception e) {
            return def;
        }
    }

    void saveTheme() { put("theme", JSONObject.quote(theme)); }

    void saveMinMode() { put("minMode", JSONObject.quote(minMode)); }

    void saveKids() {
        put("kidsApps", new JSONArray(new ArrayList<>(kidsApps)).toString());
        put("kidsCode", kidsCode == null ? "null" : JSONObject.quote(kidsCode));
        put("themePrev", JSONObject.quote(themePrev));
        put("kidsLimite", kidsLimit ? "true" : "false");
        put("kidsHoraires", "[" + kidsHours[0] + "," + kidsHours[1] + "," + kidsHours[2] + "," + kidsHours[3] + "]");
    }

    private void readList(String key, List<String> out) {
        try {
            JSONArray a = new JSONArray(sp.getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) {
        }
    }

    private void put(String key, String json) {
        sp.edit().putString(key, json).apply();
    }

    void savePins() { put("pins", new JSONArray(pins).toString()); }

    /** Réaffiche une fois toutes les applis masquées (le masquage n'existe plus en Minimal). */
    void unhideAllOnce() {
        if (sp.getBoolean("hiddenReset1", false)) return;
        hidden.clear();
        saveHidden();
        sp.edit().putBoolean("hiddenReset1", true).apply();
    }

    void saveHidden() { put("hidden", new JSONArray(new ArrayList<>(hidden)).toString()); }

    void saveAnimations() { put("animations", animations ? "true" : "false"); }

    void saveHalloween() { put("halloween2", halloween ? "true" : "false"); }

    void saveBlue() {
        put("filtre", blueMode);
        put("filtreForce", String.valueOf(blueLevel));
        put("luminosite", String.valueOf(brightness));
    }

    void savePerso() { put("personnage", perso ? "true" : "false"); }

    void savePaques() { put("paques", paques ? "true" : "false"); }

    void saveNoel() { put("noel2", noel ? "true" : "false"); }

    void saveLifeFx() { put("effets", lifeFx ? "true" : "false"); }

    double usageScore(String pkg) {
        double[] u = usage.get(pkg);
        return u == null ? 0 : u[0] * Math.pow(.5, (System.currentTimeMillis() - u[1]) / HALF_LIFE);
    }

    void recordLaunch(String pkg) {
        usage.put(pkg, new double[]{usageScore(pkg) + 1, System.currentTimeMillis()});
        try {
            JSONObject u = new JSONObject();
            for (Map.Entry<String, double[]> e : usage.entrySet()) {
                u.put(e.getKey(), new JSONObject().put("s", e.getValue()[0]).put("t", (long) e.getValue()[1]));
            }
            put("usage", u.toString());
        } catch (Exception ignored) {
        }
    }

    /** Ancien rangement manuel, puis ordre du système. */
    private List<MainActivity.App> baseOrder(List<MainActivity.App> apps) {
        Map<String, MainActivity.App> byPkg = new HashMap<>();
        for (MainActivity.App a : apps) byPkg.put(a.pkg, a);
        List<MainActivity.App> list = new ArrayList<>();
        for (String p : order) if (byPkg.containsKey(p) && !list.contains(byPkg.get(p))) list.add(byPkg.get(p));
        for (MainActivity.App a : apps) if (!list.contains(a)) list.add(a);
        return list;
    }

    /** Favoris épinglés d'abord (dans l'ordre choisi), puis le reste par fréquence d'usage. */
    List<MainActivity.App> ordered(List<MainActivity.App> apps) {
        final List<MainActivity.App> base = baseOrder(apps);
        if (!pinsLoaded && !apps.isEmpty()) { // première fois : on épingle les 5 premières applis
            for (MainActivity.App a : base) if (!hidden.contains(a.pkg) && pins.size() < 5) pins.add(a.pkg);
            savePins();
            pinsLoaded = true;
        }
        Map<String, MainActivity.App> byPkg = new HashMap<>();
        for (MainActivity.App a : base) byPkg.put(a.pkg, a);
        List<MainActivity.App> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String p : pins) if (byPkg.containsKey(p) && seen.add(p)) out.add(byPkg.get(p));
        List<MainActivity.App> rest = new ArrayList<>();
        for (MainActivity.App a : base) if (!seen.contains(a.pkg)) rest.add(a);
        final Map<String, Double> score = new HashMap<>();
        for (MainActivity.App a : rest) score.put(a.pkg, usageScore(a.pkg));
        Collections.sort(rest, (a, b) -> {
            int r = Double.compare(score.get(b.pkg), score.get(a.pkg));
            return r != 0 ? r : Integer.compare(base.indexOf(a), base.indexOf(b));
        });
        out.addAll(rest);
        return out;
    }
}
