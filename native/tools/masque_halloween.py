#!/usr/bin/env python3
"""
Masque des lumières de l'illustration d'Halloween (paysages/halloween.jpg, 1920 × 1080).

Sortie : paysages/halloween_masque.png, en couleurs :
  - rouge : sources de lumière (visages allumés des citrouilles au sol, ampoules des guirlandes) ;
    la nuit, elles gardent leur éclat d'origine et diffusent un halo ;
  - vert  : zones éclairées par ces sources (sol et herbe autour des citrouilles, feuillage autour
    des ampoules) ; la nuit, elles gardent une partie de leur luminosité, qui décroît avec la distance ;
  - noir  : tout le reste, qui suit la teinte sombre de la nuit (citrouilles pendues, troncs, etc.).

Repérage automatique (luminosité, chaleur, petites taches vives), puis exclusions à la main.
Usage : python3 tools/masque_halloween.py   (depuis le dossier native/)
"""
from PIL import Image, ImageDraw, ImageFilter
from collections import deque

W, H = 1920, 1080
src = Image.open("paysages/halloween.jpg").convert("RGB")
px = src.load()
smooth_img = src.filter(ImageFilter.BoxBlur(1))
sp = smooth_img.load()

# Zones à laisser éteintes (x0, y0, x1, y1) : citrouilles pendues, horizon clair
EXCLUDE = [
    (1680, 570, 1760, 660),   # citrouille pendue, grand arbre (haut)
    (1840, 600, 1920, 690),   # citrouille pendue, grand arbre (droite)
    (1755, 660, 1835, 745),   # citrouille pendue, grand arbre (bas)
    (1495, 755, 1575, 830),   # citrouille pendue, petit arbre de droite
    (1235, 635, 1270, 675),   # petites citrouilles pendues du bosquet de droite
    (1395, 615, 1425, 650),
    (1440, 590, 1470, 625),
    (410, 628, 445, 662),     # petite citrouille pendue du bosquet de gauche
]


def excluded(x, y):
    return any(x0 <= x < x1 and y0 <= y < y1 for x0, y0, x1, y1 in EXCLUDE)


def smoothstep(a, b, v):
    t = max(0.0, min(1.0, (v - a) / (b - a)))
    return t * t * (3 - 2 * t)


# moyenne locale de la valeur (15 × 15) par image intégrale
val = [[0] * W for _ in range(H)]
for y in range(H):
    for x in range(W):
        r, g, b = sp[x, y]
        val[y][x] = max(r, g, b)
integ = [[0] * (W + 1) for _ in range(H + 1)]
for y in range(H):
    row = 0
    for x in range(W):
        row += val[y][x]
        integ[y + 1][x + 1] = integ[y][x + 1] + row


def local_mean(x, y, r=8):
    x0, x1, y0, y1 = max(0, x - r), min(W, x + r + 1), max(0, y - r), min(H, y + r + 1)
    s = integ[y1][x1] - integ[y0][x1] - integ[y1][x0] + integ[y0][x0]
    return s / ((x1 - x0) * (y1 - y0))


warm = [[0.0] * W for _ in range(H)]
spot = [[0.0] * W for _ in range(H)]
for y in range(H):
    for x in range(W):
        if excluded(x, y):
            continue
        r, g, b = px[x, y]
        lum = .3 * r + .59 * g + .11 * b
        if y > 560:  # sous l'horizon clair
            warm[y][x] = smoothstep(203, 216, lum) * smoothstep(20, 70, r - b) * smoothstep(-20, 20, g - b - 25)
        rs, gs, bs = sp[x, y]
        v = val[y][x]
        mn = min(rs, gs, bs)
        sat = (v - mn) / v if v else 0
        spot[y][x] = smoothstep(30, 70, v - local_mean(x, y)) * smoothstep(150, 215, v) * smoothstep(.25, .5, sat)


def blobs(a, min_area, max_area, max_aspect):
    """Taches d'un seul tenant (a > 0,3) gardées selon leur taille et leur forme."""
    seen = [[False] * W for _ in range(H)]
    kept = []
    for y0 in range(H):
        for x0 in range(W):
            if a[y0][x0] <= .3 or seen[y0][x0]:
                continue
            q = deque([(x0, y0)])
            seen[y0][x0] = True
            pts = []
            while q:
                x, y = q.popleft()
                pts.append((x, y))
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < W and 0 <= yy < H and not seen[yy][xx] and a[yy][xx] > .3:
                        seen[yy][xx] = True
                        q.append((xx, yy))
            xs = [p[0] for p in pts]
            ys = [p[1] for p in pts]
            bw, bh = max(xs) - min(xs) + 1, max(ys) - min(ys) + 1
            if min_area <= len(pts) <= max_area and max(bw, bh) / min(bw, bh) <= max_aspect:
                kept.append(pts)
    return kept


faces = blobs(warm, 60, 40000, 6)
bulbs = blobs(spot, 20, 1100, 2.2)
print(len(faces), "visages,", len(bulbs), "ampoules")

# guirlandes des arbres du fond : petites ampoules jaunes ou violettes sur un feuillage orange,
# peu contrastées ; dans ces zones, une ampoule est un point nettement plus jaune (canal vert)
# ou plus violet (canal bleu) que le feuillage autour
GARLAND_ZONES = [
    (5, 645, 165, 745),      # grand arbre orange de gauche
    (405, 585, 560, 640),    # bosquet de gauche
    (1185, 548, 1530, 635),  # bosquet de droite
]
far = [[0.0] * W for _ in range(H)]
for x0, y0, x1, y1 in GARLAND_ZONES:
    for y in range(y0, y1):
        for x in range(x0, x1):
            if excluded(x, y):
                continue
            r, g, b = px[x, y]
            n = mg = mb = 0
            for yy in range(max(0, y - 4), min(H, y + 5)):
                for xx in range(max(0, x - 4), min(W, x + 5)):
                    rr, gg, bb = px[xx, yy]
                    mg += gg; mb += bb; n += 1
            mg /= n; mb /= n
            yellow = smoothstep(20, 40, g - mg) * smoothstep(205, 240, r)
            purple = smoothstep(30, 60, b - mb) * smoothstep(110, 160, b)
            far[y][x] = max(yellow, purple)
far_bulbs = [b for b in blobs(far, 3, 120, 2)  # ampoules : taches compactes, presque rondes
             if len(b) / ((max(p[0] for p in b) - min(p[0] for p in b) + 1) * (max(p[1] for p in b) - min(p[1] for p in b) + 1)) > .45]
print(len(far_bulbs), "ampoules des arbres du fond")

source = Image.new("L", (W, H), 0)
sdraw = source.load()
for group, arr in ((faces, warm), (bulbs, spot), (far_bulbs, far)):
    for pts in group:
        for x, y in pts:
            sdraw[x, y] = max(sdraw[x, y], int(255 * min(1, arr[y][x] * 1.4)))
# cœur des ampoules rebouché, bords adoucis
source = source.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.MinFilter(5)).filter(ImageFilter.GaussianBlur(1))

# lumière reçue autour des sources : flaque ovale au sol pour les citrouilles, halo rond pour les ampoules
ambient = Image.new("L", (W, H), 0)
ad = ImageDraw.Draw(ambient)
for pts in faces:
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    cx, cy = sum(xs) / len(xs), sum(ys) / len(ys)
    size = max(max(xs) - min(xs), max(ys) - min(ys)) + 10
    rx, ry = size * 2.6, size * 1.5
    for k in range(20, 0, -1):  # disques concentriques : décroissance douce
        f = k / 20
        ad.ellipse([cx - rx * f, cy + size * .25 - ry * f, cx + rx * f, cy + size * .25 + ry * f], fill=int(150 * (1 - f) ** 1.6 + 8))
for pts in bulbs:
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    cx, cy = sum(xs) / len(xs), sum(ys) / len(ys)
    r = 26
    for k in range(10, 0, -1):
        f = k / 10
        ad.ellipse([cx - r * f, cy - r * f, cx + r * f, cy + r * f], fill=int(70 * (1 - f) ** 1.5 + 4))
for pts in far_bulbs:
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    cx, cy = sum(xs) / len(xs), sum(ys) / len(ys)
    for k in range(8, 0, -1):
        f = k / 8
        ad.ellipse([cx - 14 * f, cy - 14 * f, cx + 14 * f, cy + 14 * f], fill=int(60 * (1 - f) ** 1.5 + 3))
ambient = ambient.filter(ImageFilter.GaussianBlur(8))
for x0, y0, x1, y1 in EXCLUDE:  # les citrouilles pendues restent sombres
    ad2 = ImageDraw.Draw(ambient)
    ad2.rectangle([x0, y0, x1, y1], fill=0)
ambient = ambient.filter(ImageFilter.GaussianBlur(3))

Image.merge("RGB", (source, ambient, Image.new("L", (W, H), 0))).save("paysages/halloween_masque.png", optimize=True)
print("paysages/halloween_masque.png")
