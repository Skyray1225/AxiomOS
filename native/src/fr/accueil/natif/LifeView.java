package fr.accueil.natif;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Vie du paysage du thème Thinking, selon la saison : feuilles qui tombent des arbres orange en automne,
 * papillons au printemps, lucioles dans les bosquets les soirs et les nuits d'été, étoiles filantes
 * la nuit par ciel dégagé. Rien par temps de pluie ou de neige. Comme la pluie, tout s'anime par épisodes
 * (arrivée sur l'accueil, touche, puis 30 s toutes les 4 minutes), à 30 images par seconde ; les dernières
 * feuilles finissent leur chute, puis plus rien n'est redessiné. Coordonnées : l'image 1920 × 1080 du paysage.
 */
class LifeView extends View {

    private static final int FRAME_MS = 33;
    private static final int[] LEAF_COLORS = {0xFFD9893B, 0xFFC8642F, 0xFFE0B04A, 0xFFA8823C, 0xFFB5552A};
    private static final int[] BUTTERFLY_COLORS = {0xFFFFF6D8, 0xFFFFD36B, 0xFFF5A86B, 0xFFCFE6FF};

    private String season = "";
    private int moment = 1;               // 0 aube, 1 jour, 2 crépuscule, 3 nuit
    private String weather = WeatherView.CLEAR;
    private List<Landscape.Tree> trees = new ArrayList<>(), orange = new ArrayList<>(), groves = new ArrayList<>();
    private boolean enabled = true, running, looping;
    private long until, last;
    private float spawnLeaf, spawnStar = 3;
    private final Random rnd = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private Bitmap glow;

    private static final class Leaf {
        float x, y, ground, vy, sway, ph, spin, rot, size, alpha = 1;
        int color;
    }

    private static final class Fly {
        Landscape.Tree home;
        float x, y, vx, vy, ph, size, freq;
        boolean dead;
    }

    /**
     * Papillon : vol en zigzag. Le cap change sans cesse (virage tiré au hasard toutes les 0,3 à 1,2 s),
     * battements rapides qui le font remonter par à-coups, entrecoupés de courts planés où il redescend.
     */
    private static final class Butterfly {
        float x, y, dir, heading, turn, turnLeft, speed, flap, glide, flapLeft, vy, size;
        int color;
    }

    private static final class Star {
        float x, y, vx, vy, life, len;
    }

    private final List<Leaf> leaves = new ArrayList<>();
    private final List<Fly> flies = new ArrayList<>();
    private final List<Butterfly> butterflies = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();

    /** Fantôme d'Halloween : flotte en ondulant, dans la même bande que les papillons. */
    private static final class Ghost {
        float x, y, baseY, dir, speed, ph, size, alpha;
    }

    private final List<Ghost> ghosts = new ArrayList<>();

    /** Traîneau du père Noël : passe au loin, en haut du ciel, toutes les 2 à 3 minutes. */
    private static final class Sleigh {
        float x, y, dir, speed, ph;
    }

    private Sleigh sleigh;
    private final Runnable sleighTick = new Runnable() {
        @Override
        public void run() {
            if (!"noel".equals(season) || !enabled || !running) return;
            Sleigh sl = new Sleigh();
            sl.dir = rnd.nextFloat() < .5f ? 1 : -1;
            sl.x = sl.dir > 0 ? -160 : Landscape.W + 160;
            sl.y = 45 + rnd.nextFloat() * 35; // au-dessus de l'heure
            sl.speed = 150 + rnd.nextFloat() * 30;
            sleigh = sl;
            burst((long) ((Landscape.W + 320) / sl.speed * 1000) + 500); // anime le temps de la traversée
            postDelayed(this, 120000 + rnd.nextInt(60000));
        }
    };

    private void scheduleSleigh(long firstMs) {
        removeCallbacks(sleighTick);
        if ("noel".equals(season) && enabled && running) postDelayed(sleighTick, firstMs);
    }
    private final android.graphics.Path ghostPath = new android.graphics.Path();

    LifeView(Context ctx) {
        super(ctx);
    }

    /** Paysage affiché : saison, moment de la journée et arbres (positions dans l'image). */
    void setScene(String seasonId, int idx, List<Landscape.Tree> t) {
        boolean changed = !seasonId.equals(season) || idx != moment;
        season = seasonId;
        moment = idx;
        trees = t;
        orange = new ArrayList<>();
        groves = new ArrayList<>();
        for (Landscape.Tree tr : t) {
            if (tr.autumn && tr.layer >= 3) orange.add(tr); // arbres assez proches pour voir tomber les feuilles
            if (tr.layer >= 3) groves.add(tr);
        }
        if (changed) clearAll();
        if (changed) scheduleSleigh(20000 + rnd.nextInt(20000)); // le premier passage peu après l'arrivée
        sync();
    }

    void setWeather(String kind) {
        weather = kind;
        sync();
    }

    /** Effets de saison activés (Préférences) et animations du décor activées. */
    void setActive(boolean on) {
        enabled = on;
        if (!on) clearAll();
        scheduleSleigh(20000);
        sync();
    }

    void start() {
        running = true;
        scheduleSleigh(20000 + rnd.nextInt(20000));
        sync();
    }

    void stop() {
        running = false;
        looping = false;
        removeCallbacks(frame);
        removeCallbacks(sleighTick);
    }

    void burst(long ms) {
        until = Math.max(until, SystemClock.uptimeMillis() + ms);
        sync();
    }

    private boolean wet() {
        return WeatherView.RAIN.equals(weather) || WeatherView.SNOW.equals(weather) || WeatherView.STORM.equals(weather);
    }

    private boolean clearSky() {
        return WeatherView.CLEAR.equals(weather) || WeatherView.PARTLY.equals(weather);
    }

    private boolean leavesWanted() { return autumn() && moment != 3 && !wet() && !orange.isEmpty(); }

    private boolean autumn() { return "automne".equals(season) || "halloween".equals(season); }

    private boolean ghostsWanted() { return "halloween".equals(season) && !wet(); }

    private boolean butterfliesWanted() { return ("printemps".equals(season) || "paques".equals(season)) && moment != 3 && !wet(); }

    private boolean fliesWanted() {
        return "ete".equals(season) && moment >= 2 && !wet() && !WeatherView.FOG.equals(weather) && !groves.isEmpty();
    }

    private boolean starsWanted() { return moment == 3 && clearSky(); }

    private boolean active() { return SystemClock.uptimeMillis() < until; }

    private void clearAll() {
        leaves.clear();
        flies.clear();
        butterflies.clear();
        stars.clear();
        ghosts.clear();
        sleigh = null;
        invalidate();
    }

    private void sync() {
        if (!enabled || !running) {
            clearAll();
            return;
        }
        if (looping || !active()) return;
        if (!leavesWanted() && !butterfliesWanted() && !fliesWanted() && !starsWanted() && !ghostsWanted() && sleigh == null) return;
        looping = true;
        last = SystemClock.uptimeMillis();
        postDelayed(frame, FRAME_MS);
    }

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!looping) return;
            long t = SystemClock.uptimeMillis();
            float dt = Math.min(.15f, (t - last) / 1000f);
            last = t;
            step(dt, active());
            invalidate();
            if (active() || !leaves.isEmpty() || !flies.isEmpty() || !butterflies.isEmpty() || !stars.isEmpty() || !ghosts.isEmpty() || sleigh != null)
                postDelayed(this, FRAME_MS);
            else looping = false; // fin de l'épisode : plus rien ne bouge
        }
    };

    /* ===== Mouvements ===== */

    private void step(float dt, boolean active) {
        // feuilles qui se détachent des arbres orange
        if (leavesWanted() && active && leaves.size() < 16) {
            spawnLeaf -= dt;
            if (spawnLeaf <= 0) {
                leaves.add(newLeaf());
                spawnLeaf = .25f + rnd.nextFloat() * .5f;
            }
        } else if (!leavesWanted()) leaves.clear();
        for (Iterator<Leaf> it = leaves.iterator(); it.hasNext(); ) {
            Leaf l = it.next();
            l.ph += dt * 1.3f;
            l.y += l.vy * dt;
            l.x += Math.cos(l.ph) * l.sway * dt; // balancement, sans dériver : elle tombe au pied de son arbre
            l.rot += l.spin * dt;
            if (l.vy > 0 && l.y >= l.ground) { // posée au sol, à plat
                l.y = l.ground;
                l.vy = 0;
                l.spin = 0;
                l.sway = 0;
            }
            if (l.vy == 0) { // puis s'efface en fondu
                l.alpha -= dt * .7f;
                if (l.alpha <= 0) it.remove();
            }
        }

        // papillons au printemps
        if (butterfliesWanted()) {
            while (active && butterflies.size() < 3) butterflies.add(newButterfly());
            for (Iterator<Butterfly> it = butterflies.iterator(); it.hasNext(); ) {
                Butterfly b = it.next();
                flutter(b, dt);
                if (b.x < -60 || b.x > Landscape.W + 60) {
                    if (active) { Butterfly n = newButterfly(); b.x = n.x; b.y = n.y; b.dir = n.dir; b.heading = n.heading; b.color = n.color; }
                    else it.remove();
                }
            }
        } else butterflies.clear();

        // traîneau du père Noël
        if (sleigh != null) {
            sleigh.x += sleigh.dir * sleigh.speed * dt;
            sleigh.ph += dt;
            if (sleigh.x < -200 || sleigh.x > Landscape.W + 200) sleigh = null;
        }

        // fantômes d'Halloween
        if (ghostsWanted()) {
            while (active && ghosts.size() < 3) ghosts.add(newGhost(ghosts.isEmpty()));
            for (Iterator<Ghost> it = ghosts.iterator(); it.hasNext(); ) {
                Ghost g = it.next();
                g.ph += dt;
                g.x += g.dir * g.speed * dt * (1 + .35f * (float) Math.sin(g.ph * .9f)); // avance par vagues
                g.y = g.baseY + (float) Math.sin(g.ph * 1.6f) * 12 + (float) Math.sin(g.ph * .5f) * 18;
                g.alpha = Math.min(1, g.alpha + dt * .8f); // apparaît en fondu
                if (g.x < -80 || g.x > Landscape.W + 80) {
                    if (active) { Ghost n = newGhost(false); g.x = n.x; g.baseY = n.baseY; g.dir = n.dir; g.speed = n.speed; g.size = n.size; g.alpha = 0; }
                    else it.remove();
                }
            }
        } else ghosts.clear();

        // lucioles dans les bosquets
        if (fliesWanted()) {
            while (active && flies.size() < 8) flies.add(newFly());
            for (Iterator<Fly> it = flies.iterator(); it.hasNext(); ) {
                Fly f = it.next();
                f.ph += dt;
                f.vx = clamp(f.vx + (rnd.nextFloat() - .5f) * 12 * dt, 10);
                f.vy = clamp(f.vy + (rnd.nextFloat() - .5f) * 8 * dt, 6);
                f.x += f.vx * dt;
                f.y += f.vy * dt;
                Landscape.Tree h = f.home; // reste autour de son arbre
                if (Math.abs(f.x - h.x) > h.h * .9f) f.vx = -Math.signum(f.x - h.x) * Math.abs(f.vx);
                if (f.y > h.y + h.h * .05f || f.y < h.y - h.h * .85f) f.vy = -Math.signum(f.y - (h.y - h.h * .4f)) * Math.abs(f.vy);
                if (!active && blink(f) < .02f) it.remove(); // s'éteint au creux de son clignotement
            }
        } else flies.clear();

        // étoiles filantes, de temps en temps la nuit
        if (starsWanted() && active) {
            spawnStar -= dt;
            if (spawnStar <= 0) {
                stars.add(newStar());
                spawnStar = 6 + rnd.nextFloat() * 10;
            }
        } else if (!starsWanted()) stars.clear();
        for (Iterator<Star> it = stars.iterator(); it.hasNext(); ) {
            Star st = it.next();
            st.life += dt;
            st.x += st.vx * dt;
            st.y += st.vy * dt;
            if (st.life >= .8f) it.remove();
        }
    }

    private static float clamp(float v, float m) {
        return Math.max(-m, Math.min(m, v));
    }

    private Leaf newLeaf() {
        // les grands arbres (devant) perdent plus de feuilles
        float total = 0;
        for (Landscape.Tree t : orange) total += t.h;
        float pick = rnd.nextFloat() * total;
        Landscape.Tree tr = orange.get(0);
        for (Landscape.Tree t : orange) { pick -= t.h; if (pick <= 0) { tr = t; break; } }
        float R = tr.h * .3f, k = .6f + tr.h / 120f * .6f; // les feuilles des arbres lointains restent visibles
        Leaf l = new Leaf();
        // elles se détachent du bas de la couronne : sur le feuillage, de la même couleur, on ne les verrait pas
        l.x = tr.x + (rnd.nextFloat() - .5f) * 1.5f * R;
        l.y = tr.y - tr.h * .64f + R * (.35f + rnd.nextFloat() * .45f);
        l.ground = tr.y - tr.h * .01f + rnd.nextFloat() * tr.h * .05f; // au pied de l'arbre
        l.vy = (24 + rnd.nextFloat() * 18) * k;
        l.sway = (8 + rnd.nextFloat() * 10) * k;
        l.ph = rnd.nextFloat() * 6.28f;
        l.spin = (rnd.nextFloat() - .5f) * 3;
        l.rot = rnd.nextFloat() * 6.28f;
        l.size = (5 + rnd.nextFloat() * 3) * k;
        l.color = rnd.nextFloat() < .5f ? tr.color : LEAF_COLORS[rnd.nextInt(LEAF_COLORS.length)];
        return l;
    }

    private Butterfly newButterfly() {
        Butterfly b = new Butterfly();
        b.dir = rnd.nextFloat() < .5f ? 1 : -1;
        b.x = b.dir > 0 ? -40 : Landscape.W + 40;
        b.y = 630 + rnd.nextFloat() * 120; // au-dessus des prés, entre l'horizon et le dock
        b.heading = b.dir > 0 ? 0 : (float) Math.PI;
        b.speed = 45 + rnd.nextFloat() * 25;
        b.size = 6.5f + rnd.nextFloat() * 2.5f;
        b.flap = rnd.nextFloat() * 6.28f;
        b.flapLeft = .5f + rnd.nextFloat();
        b.color = BUTTERFLY_COLORS[rnd.nextInt(BUTTERFLY_COLORS.length)];
        return b;
    }

    private void flutter(Butterfly b, float dt) {
        // nouveau virage de temps en temps ; tendance à garder sa direction et à rester au-dessus des prés
        b.turnLeft -= dt;
        if (b.turnLeft <= 0) {
            b.turnLeft = .3f + rnd.nextFloat() * .9f;
            float base = b.dir > 0 ? 0 : (float) Math.PI;
            float back = (float) Math.atan2(Math.sin(base - b.heading), Math.cos(base - b.heading)) * .8f;
            float band = b.y < 620 ? .9f : b.y > 740 ? -.9f : 0; // y croît vers le bas : remonte ou redescend
            b.turn = back + (rnd.nextFloat() - .5f) * 5f + band * b.dir;
        }
        b.heading += b.turn * dt;
        // battements (≈ 9 par seconde) puis plané
        if (b.glide > 0) {
            b.glide -= dt;
            if (b.glide <= 0) b.flapLeft = .6f + rnd.nextFloat() * 1.4f;
        } else {
            float before = b.flap;
            b.flap += dt * 2 * (float) Math.PI * 9;
            // chaque battement vers le bas donne une petite poussée vers le haut
            if (Math.floor(before / (2 * Math.PI)) != Math.floor(b.flap / (2 * Math.PI))) b.vy -= 9 + rnd.nextFloat() * 5;
            b.flapLeft -= dt;
            if (b.flapLeft <= 0) b.glide = .25f + rnd.nextFloat() * .5f;
        }
        b.vy += 100 * dt;          // pesanteur : compensée en moyenne par les battements, il redescend en planant
        b.vy *= (float) Math.pow(.3, dt); // freinage de l'air
        b.vy += (680 - b.y) * .9f * dt;    // rappel doux vers les prés (pas derrière le dock ni dans le ciel)
        float sp = b.glide > 0 ? b.speed * .7f : b.speed;
        b.x += Math.cos(b.heading) * sp * dt;
        b.y += (Math.sin(b.heading) * sp * .45f + b.vy) * dt;
    }

    /** Ouverture des ailes, de 0 (refermées, vues par la tranche) à 1 (à plat). */
    private static float wingOpen(Butterfly b) {
        if (b.glide > 0) return .85f;
        return .12f + .88f * Math.abs((float) Math.cos(b.flap / 2));
    }

    private Ghost newGhost(boolean anywhere) {
        Ghost g = new Ghost();
        g.dir = rnd.nextFloat() < .5f ? 1 : -1;
        g.x = anywhere ? 200 + rnd.nextFloat() * 1520 : (g.dir > 0 ? -60 : Landscape.W + 60);
        g.baseY = 640 + rnd.nextFloat() * 90;
        g.speed = 35 + rnd.nextFloat() * 30;
        g.ph = rnd.nextFloat() * 6.28f;
        g.size = 13 + rnd.nextFloat() * 6;
        return g;
    }

    private Fly newFly() {
        Fly f = new Fly();
        f.home = groves.get(rnd.nextInt(groves.size()));
        Landscape.Tree h = f.home;
        f.x = h.x + (rnd.nextFloat() - .5f) * h.h * 1.4f;
        f.y = h.y - rnd.nextFloat() * h.h * .8f;
        f.vx = (rnd.nextFloat() - .5f) * 10;
        f.vy = (rnd.nextFloat() - .5f) * 6;
        f.ph = rnd.nextFloat() * 6.28f;
        f.freq = .5f + rnd.nextFloat() * .6f; // chacune son rythme
        f.size = 6 + h.layer * 2.2f; // plus grosses devant
        return f;
    }

    private Star newStar() {
        Star s = new Star();
        float dir = rnd.nextFloat() < .5f ? 1 : -1;
        // sur les côtés du ciel, pas derrière l'heure
        s.x = Landscape.W * (rnd.nextFloat() < .5f ? .05f + rnd.nextFloat() * .25f : .7f + rnd.nextFloat() * .25f);
        s.y = 40 + rnd.nextFloat() * 200;
        s.vx = dir * 1050;
        s.vy = 420;
        s.len = 90 + rnd.nextFloat() * 70;
        return s;
    }

    private static float blink(Fly f) {
        // brefs éclats séparés par de longues pauses éteintes
        float a = (float) Math.max(0, Math.sin(f.ph * f.freq * 2));
        return a * a * a * a;
    }

    /* ===== Dessin ===== */

    private Bitmap glowSprite() {
        if (glow != null) return glow;
        int n = 64;
        glow = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(n / 2f, n / 2f, n / 2f, new int[]{0xFFFFF6C8, 0xE6FFEC96, 0x40FFEC96, 0x00FFEC96},
                new float[]{0, .12f, .45f, 1}, Shader.TileMode.CLAMP));
        new Canvas(glow).drawCircle(n / 2f, n / 2f, n / 2f, p);
        return glow;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (leaves.isEmpty() && flies.isEmpty() && butterflies.isEmpty() && stars.isEmpty() && ghosts.isEmpty() && sleigh == null) return;
        float s = Math.max(getWidth() / (float) Landscape.W, getHeight() / (float) Landscape.H);
        c.save();
        c.translate((getWidth() - Landscape.W * s) / 2, (getHeight() - Landscape.H * s) / 2);
        c.scale(s, s);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);

        for (Star st : stars) {
            float a = (float) Math.sin(Math.min(1, st.life / .8f) * Math.PI);
            float n = (float) Math.hypot(st.vx, st.vy), tx = st.x - st.vx / n * st.len, ty = st.y - st.vy / n * st.len;
            paint.setShader(new LinearGradient(st.x, st.y, tx, ty, ((int) (a * 242) << 24) | 0xFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            paint.setStrokeWidth(2.4f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(st.x, st.y, tx, ty, paint);
            paint.setShader(null);
        }

        for (Leaf l : leaves) drawLeaf(c, l);

        for (Butterfly b : butterflies) {
            float open = wingOpen(b), sz = b.size;
            float tilt = (float) Math.toDegrees(Math.sin(b.heading)) * .35f; // penché dans le sens du vol
            c.save();
            c.translate(b.x, b.y);
            c.rotate(tilt);
            paint.setColor(b.color);
            paint.setAlpha(240);
            // ailes antérieures (grandes, vers le haut) et postérieures (petites, vers le bas), de chaque côté ;
            // en battant, elles se referment en perspective (largeur réduite)
            for (int side = -1; side <= 1; side += 2) {
                c.save();
                c.scale(side * open, 1);
                drawWing(c, sz * .55f, -sz * .32f, sz * .62f, sz * .42f, -28);
                drawWing(c, sz * .42f, sz * .28f, sz * .42f, sz * .3f, 25);
                c.restore();
            }
            paint.setColor(0xFF4B3B30);
            paint.setAlpha(255);
            oval.set(-sz * .09f, -sz * .5f, sz * .09f, sz * .55f);
            c.drawOval(oval, paint);
            c.restore();
        }

        for (Ghost gh : ghosts) drawGhost(c, gh);
        if (sleigh != null) drawSleigh(c, sleigh);

        Bitmap g = flies.isEmpty() ? null : glowSprite();
        for (Fly f : flies) {
            float a = blink(f);
            if (a < .02f) continue;
            paint.setAlpha(Math.round(a * 255));
            float r = f.size;
            oval.set(f.x - r, f.y - r, f.x + r, f.y + r);
            c.drawBitmap(g, null, oval, paint);
        }
        paint.setAlpha(255);
        c.restore();
    }

    private final android.graphics.Path leafShape = leafPath();

    /** Feuille de longueur 2 (de -1 à 1) : base arrondie, pointe effilée. */
    private static android.graphics.Path leafPath() {
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(-1, 0);
        p.cubicTo(-.75f, -.62f, .3f, -.62f, 1, 0);
        p.cubicTo(.3f, .62f, -.75f, .62f, -1, 0);
        p.close();
        return p;
    }

    /**
     * Feuille qui tombe en se retournant : on voit tantôt sa face claire, tantôt son revers plus sombre,
     * avec sa queue et sa nervure ; posée au sol, elle est à plat et s'efface.
     */
    private void drawLeaf(Canvas c, Leaf l) {
        float turn = (float) Math.cos(l.ph * 1.7f);
        float squash = l.vy == 0 ? .55f : .35f + .65f * Math.abs(turn);
        int face = turn >= 0 || l.vy == 0 ? Landscape.mix(l.color, 0xFFFFFFFF, .12) : Landscape.mix(l.color, 0xFF000000, .22);
        int a = Math.round(255 * Math.max(0, Math.min(1, l.alpha)));
        c.save();
        c.translate(l.x, l.y);
        c.rotate((float) Math.toDegrees(l.rot));
        c.scale(l.size, l.size * squash);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(face);
        paint.setAlpha(a);
        c.drawPath(leafShape, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Landscape.mix(l.color, 0xFF000000, .4));
        paint.setAlpha(a);
        paint.setStrokeWidth(.16f);
        c.drawLine(-1.35f, 0, -1, 0, paint); // queue
        paint.setStrokeWidth(.09f);
        c.drawLine(-1, 0, .8f, 0, paint);     // nervure
        paint.setStyle(Paint.Style.FILL);
        c.restore();
    }

    /** Drap blanc arrondi en haut, bas ondulé qui flotte, yeux et bouche ronde ; légère lueur la nuit. */
    private void drawGhost(Canvas c, Ghost g) {
        float r = g.size, w = g.ph * 5;
        int a = Math.round(255 * g.alpha);
        c.save();
        c.translate(g.x, g.y);
        c.rotate(g.dir * (6 + (float) Math.sin(g.ph * 1.6f) * 4)); // penché vers l'avant
        if (g.dir < 0) c.scale(-1, 1);
        if (moment >= 2) { // lueur pâle le soir et la nuit
            paint.setShader(new RadialGradient(0, 0, r * 2.6f, 0x40DDE8FF, 0x00DDE8FF, Shader.TileMode.CLAMP));
            paint.setAlpha(a);
            c.drawCircle(0, 0, r * 2.6f, paint);
            paint.setShader(null);
        }
        android.graphics.Path p = ghostPath;
        p.rewind();
        p.moveTo(-r, 0);
        p.arcTo(new RectF(-r, -r, r, r), 180, 180);
        p.lineTo(r, r * 1.1f);
        // bas en quatre festons qui ondulent, la traîne vers l'arrière
        int n = 4;
        for (int k = 0; k < n; k++) {
            float x0 = r - 2 * r * k / n, x1 = r - 2 * r * (k + 1) / n;
            float wave = (float) Math.sin(w + k * 1.4f) * r * .18f;
            float yEnd = r * (1.1f + .12f * (k + 1)) + wave;
            p.quadTo((x0 + x1) / 2 - r * .1f, r * 1.55f + k * r * .1f + wave, x1, yEnd);
        }
        p.quadTo(-r * 1.15f, r * .6f, -r, 0);
        p.close();
        paint.setColor(moment == 3 ? 0xFFE6ECFA : 0xFFFAFAFA);
        paint.setAlpha(Math.round(a * .9f));
        c.drawPath(p, paint);
        paint.setColor(0xFF2A2633);
        paint.setAlpha(a);
        oval.set(r * .05f, -r * .35f, r * .32f, r * .05f);
        c.drawOval(oval, paint);
        oval.set(r * .45f, -r * .35f, r * .72f, r * .05f);
        c.drawOval(oval, paint);
        oval.set(r * .28f, r * .25f, r * .5f, r * .55f);
        c.drawOval(oval, paint);
        c.restore();
    }

    /**
     * Traîneau vu de loin : quatre rennes au galop (le premier au nez rouge), rênes, traîneau rouge,
     * père Noël et sa hotte, traînée d'étincelles dorées. Ombre chinoise douce le jour, éclairée la nuit.
     */
    private void drawSleigh(Canvas c, Sleigh sl) {
        boolean night = moment == 3;
        float bob = (float) Math.sin(sl.ph * 2.2f) * 4;
        c.save();
        c.translate(sl.x, sl.y + bob);
        c.scale(sl.dir, 1);
        c.rotate(-3);
        // étincelles derrière
        for (int k = 0; k < 10; k++) {
            float tx = -14 - k * 9, ty = -6 + (float) Math.sin(sl.ph * 6 + k) * 3 + k * .8f;
            float tw = (float) Math.abs(Math.sin(sl.ph * 9 + k * 1.7f));
            paint.setColor(0xFFFFE08A);
            paint.setAlpha(Math.round(220 * (1 - k / 10f) * tw));
            c.drawCircle(tx, ty, 1.6f - k * .08f, paint);
        }
        int fur = night ? 0xFF8C6A4E : 0xFF6B4A2E, red = 0xFFC0272D, dark = night ? 0xFF3A2A20 : 0xFF2E2420;
        // traîneau : patin recourbé et caisse rouge
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(1.6f);
        paint.setColor(0xFFE2B547);
        paint.setAlpha(255);
        android.graphics.Path run = new android.graphics.Path();
        run.moveTo(-4, 0);
        run.lineTo(26, 0);
        run.quadTo(33, 0, 32, -6);
        c.drawPath(run, paint);
        paint.setStyle(Paint.Style.FILL);
        android.graphics.Path body = new android.graphics.Path();
        body.moveTo(0, -3);
        body.lineTo(25, -3);
        body.quadTo(30, -6, 29, -12);
        body.lineTo(4, -12);
        body.quadTo(-1, -12, 0, -3);
        body.close();
        paint.setColor(red);
        c.drawPath(body, paint);
        // hotte et père Noël
        paint.setColor(0xFF7A5232);
        c.drawOval(new RectF(1, -21, 13, -10), paint);
        paint.setColor(red);
        c.drawOval(new RectF(12, -21, 22, -10), paint);
        paint.setColor(0xFFF2D2B6);
        c.drawCircle(18, -23, 3, paint);
        paint.setColor(0xFFFFFFFF);
        c.drawOval(new RectF(16.5f, -23, 22, -18.5f), paint); // barbe
        paint.setColor(red);
        android.graphics.Path hat = new android.graphics.Path();
        hat.moveTo(14.5f, -24);
        hat.lineTo(21, -25);
        hat.lineTo(14, -30);
        hat.close();
        c.drawPath(hat, paint);
        paint.setColor(0xFFFFFFFF);
        c.drawCircle(13.6f, -30, 1.3f, paint);
        // rênes
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(.8f);
        paint.setColor(dark);
        c.drawLine(22, -15, 44, -13, paint);
        c.drawLine(44, -13, 104, -13, paint);
        paint.setStyle(Paint.Style.FILL);
        // rennes
        for (int k = 0; k < 4; k++) {
            float cx = 46 + k * 19, gal = (float) Math.sin(sl.ph * 10 + k * .9f);
            float cy = -12 + gal * 1.2f;
            paint.setColor(fur);
            c.drawOval(new RectF(cx - 7, cy - 3.2f, cx + 6, cy + 3.2f), paint);
            c.drawOval(new RectF(cx + 4, cy - 9, cx + 9, cy - 4), paint); // tête
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.4f);
            c.drawLine(cx + 4, cy - 1, cx + 6, cy - 6, paint); // cou
            paint.setStrokeWidth(1.1f);
            // pattes : avant et arrière en opposition (galop)
            c.drawLine(cx + 4, cy + 2, cx + 4 + gal * 4 + 3, cy + 8, paint);
            c.drawLine(cx + 2, cy + 2, cx + 2 + gal * 3 + 2, cy + 8, paint);
            c.drawLine(cx - 5, cy + 2, cx - 5 - gal * 4 - 3, cy + 8, paint);
            c.drawLine(cx - 3, cy + 2, cx - 3 - gal * 3 - 2, cy + 8, paint);
            paint.setColor(dark);
            paint.setStrokeWidth(.8f);
            c.drawLine(cx + 6, cy - 9, cx + 4, cy - 13, paint); // bois
            c.drawLine(cx + 5, cy - 11, cx + 3, cy - 12, paint);
            c.drawLine(cx + 7.5f, cy - 9, cx + 8.5f, cy - 13, paint);
            paint.setStyle(Paint.Style.FILL);
            if (k == 3) { // nez rouge de Rudolph, qui brille
                paint.setShader(new RadialGradient(cx + 9.5f, cy - 6, 6, 0x99FF3A3A, 0x00FF3A3A, Shader.TileMode.CLAMP));
                c.drawCircle(cx + 9.5f, cy - 6, 6, paint);
                paint.setShader(null);
                paint.setColor(0xFFFF3A3A);
                c.drawCircle(cx + 9.5f, cy - 6, 1.4f, paint);
            }
        }
        c.restore();
    }

    private void drawWing(Canvas c, float cx, float cy, float rx, float ry, float deg) {
        c.save();
        c.translate(cx, cy);
        c.rotate(deg);
        oval.set(-rx, -ry, rx, ry);
        c.drawOval(oval, paint);
        c.restore();
    }
}
