# Passation : écran d'accueil AxiomOS sur la box Xiaomi (état au 28/10/2026)

À lire en entier avant de toucher à quoi que ce soit. Ce document résume une longue session de travail.
Objectif de la prochaine session : **étudier, puis éventuellement réaliser, le passage de l'écran d'accueil en appli Android native** (Kotlin). En attendant, la version web actuelle fonctionne et est installée.

---

## 1. Matériel et accès

| Élément | Valeur |
|---|---|
| Box | Xiaomi TV Box S (2e gén.), modèle **MiTV-AFKR0**, Google TV / **Android TV 11**, puce Amlogic, 2 Go de RAM, sortie 1920×1080 |
| Télé | Samsung, branchée en HDMI, avec une barre de son |
| Adresse de la box | **192.168.1.39**, ADB en réseau sur le **port 5555** (débogage USB activé dans les options développeur ; cet ordinateur est autorisé) |
| Ordinateur | Linux, IP 192.168.1.41 (Ethernet) / .65 (Wi-Fi). `adb` installé par apt |
| Langue de l'utilisateur | **Français**. Il pilote souvent depuis son téléphone (contrôle à distance de Claude Code) et regarde la télé pendant les tests |
| Météo | Perpignan (lat 42.6986, lon 2.8956), API Open-Meteo sans clé |

**Attention, mise en veille** : le bouton d'alimentation de la télécommande **éteint complètement la box** (redémarrage au rallumage). En veille, le Wi-Fi est coupé et **ADB est perdu** ; il revient au rallumage (`adb connect 192.168.1.39:5555`).

## 2. Outils de compilation (déjà installés, hors du projet)

- SDK Android : `~/Android/Sdk` (cmdline-tools, `platforms;android-30`, `build-tools;34.0.0` **et 35.0.0** ; on utilise **35**, le d8 de la 34 plante sur les classes internes). Licences SDK acceptées avec l'accord de l'utilisateur.
- JDK : `~/Android/jdk` (Temurin 21, téléchargé : le système n'avait qu'un JRE).
- **Pas de Gradle, pas de Kotlin.** La compilation se fait avec `launcher/android/build.sh` : aapt2, puis javac (`-source 8`), d8, zipalign et apksigner.
- **Pour le natif, il faudra Gradle et le compilateur Kotlin** (plusieurs centaines de Mo) : demander l'accord de l'utilisateur avant de les télécharger.

## 3. Fichiers du projet

Tout est dans le dossier de cette session, qui sera **supprimé avec elle** : le copier dans un dossier permanent avant de repartir.

```
PASSATION.md                     ce document
cast_youtube.py                  lance une vidéo YouTube sur la box via Cast (sans dépendance), test du tout début
.claude/launch.json              serveur d'aperçu « maquette » : python -m http.server 8765 --directory launcher
launcher/index.html              TOUTE l'interface (≈ 2 250 lignes : HTML, CSS, JS dans un seul fichier)
launcher/fonts/outfit.woff2      police Outfit embarquée (fonctionne hors ligne au démarrage)
launcher/photos/1..8.jpg         8 photos de paysage (Picsum/Unsplash, libres), 1920×1080, thème Modern
launcher/tools/cdp.py            client minimal du protocole devtools : évalue du JS dans la WebView de la box
launcher/tools/layers.py         liste les couches graphiques composées de la WebView (diagnostic)
launcher/android/AndroidManifest.xml
launcher/android/src/fr/accueil/tv/MainActivity.java   (≈ 380 lignes)
launcher/android/src/fr/accueil/tv/AccueilDream.java   économiseur d'écran (DreamService)
launcher/android/res/drawable/banner.xml
launcher/android/build.sh        compile Accueil.apk (copie index.html, fonts/, photos/ dans les assets)
launcher/android/debug.keystore  ⚠️ CLÉ DE SIGNATURE : indispensable pour mettre à jour l'appli sans la désinstaller
launcher/android/Accueil.apk     dernière version compilée et installée
```

⚠️ **`debug.keystore` est critique.** Sans elle, impossible de mettre à jour `fr.accueil.tv` : il faudrait la désinstaller, ce qui effacerait tous les réglages de l'utilisateur. Mot de passe : `android`, alias : `accueil`.

## 4. Architecture actuelle (version web)

- **Paquet `fr.accueil.tv`**, activité `MainActivity` : une **WebView plein écran** qui charge `file:///android_asset/index.html`.
- **Pont JS ↔ Java `window.Launcher`**, avec les méthodes :
  - `apps()` : JSON des applis LEANBACK, avec leur bannière en WebP base64 320×180, triées par la liste PRIORITY puis par ordre alphabétique, Paramètres en dernier ;
  - `launch(pkg, cls)`, `openSettings()` ;
  - `bannerHi(pkg, cls)` : bannière en 960×540, utilisée par la vitrine d'Ambient ;
  - `forcedWeather()`, `forcedSeason()` ;
  - `introAtBoot()`, `ready()` ;
  - `saveSnapshot(base64)`, `snapshotAsset(path)`.
- **Image de chargement** : une `ImageView` posée par-dessus la WebView affiche `files/paysage.jpg` (dernier fond affiché) jusqu'à ce que la page appelle `Launcher.ready()`, avec une sécurité de 6 s. Ça évite l'écran blanc au démarrage à froid.
- **Intro AxiomOS** : jouée à chaque branchement HDMI détecté (`ACTION_HDMI_AUDIO_PLUG`, délai de 2 s), à chaque `SCREEN_ON` (2,5 s), et au démarrage de la box (0,5 s, dans les 10 min suivant l'allumage, une fois par démarrage via SharedPreferences `accueil/introBoot`). Une touche la passe.
- **Économiseur d'écran** `AccueilDream` : la même page avec `?veille=1`, sans dock ni boutons, après 10 min d'inactivité.
- **Inspection du moteur web activée** (`setWebContentsDebuggingEnabled(true)`), pour `tools/cdp.py`. On peut la retirer une fois les réglages terminés.
- **Réglages de l'utilisateur dans le `localStorage` de la WebView**, clés JSON :
  - `theme` (`dessin`|`photo`|`bullit`|`ambient`|`minimal`|`kids`) ;
  - `animations` (bool) ;
  - `pins` : favoris épinglés, dans l'ordre ;
  - `usage` : `{pkg:{s,t}}`, score à demi-vie de 14 jours ;
  - `order` : ancien rangement manuel, sert à départager ;
  - `hidden` : applis masquées ;
  - `kidsCode` : suite de 4 flèches, ex. `U,U,L,R` ;
  - `kidsApps`, `themePrev`, `minMode` (`auto`|`light`|`dark`) ;
  - `snapKey` : interne.

  **À migrer vers la version native** : les lire via `tools/cdp.py` avant la bascule.

### Tester sans la télécommande

- **Aperçu dans le navigateur** : `http://localhost:8765/?heure=19.5&meteo=63&saison=hiver&intro=1&veille=1`. Tous les paramètres sont optionnels ; sans `window.Launcher`, la page affiche des applis fictives.
- **Sur la box** :
  - `adb shell am start -n fr.accueil.tv/.MainActivity --ei meteo 63 --es saison hiver --ez intro true` (`--ei meteo -1` et `--es saison auto` remettent le réel) ;
  - piloter la page : `adb forward tcp:9333 localabstract:$(adb shell cat /proc/net/unix | grep -o "webview_devtools_remote_[0-9]*" | tail -1)` puis `python3 launcher/tools/cdp.py "theme"`.
- **Compiler, installer et relancer** (⚠️ toujours relancer l'accueil juste après l'installation, sinon Android affiche la dernière appli ouverte : c'est arrivé avec TF1+) :
  ```bash
  cd launcher/android && ./build.sh && adb -s 192.168.1.39:5555 install -r Accueil.apk && adb -s 192.168.1.39:5555 shell am start -n fr.accueil.tv/.MainActivity
  ```

## 5. Fonctions en place

- **Dock** : carrousel de 5 tuiles avec les vraies bannières. Contour lumineux préparé, qui apparaît en fondu (pas d'ombre animée, trop coûteuse). Lueur en **un seul passage de 0,6 s**. Animations de navigation d'environ 0,25 s.
- **Tri** : favoris épinglés d'abord (au départ : YouTube, Netflix, TF1+, Twitch, CANAL+), puis le reste par fréquence d'usage. Mis à jour au retour sur l'accueil, jamais pendant la navigation.
- **Mode édition** (appui long sur OK) : ← → déplace et épingle, ↑ épingle ou désépingle, ↓ masque, OK ou Retour termine.
- **Barre du haut** (↑ depuis le dock) : « Préférences » et « Paramètres » (ouvre les Paramètres Android).
- **Modal Préférences** : 6 cartes de thème avec aperçu, la ligne « Apparence de Minimal » (seulement en Minimal) et l'interrupteur « Animations du décor ».
- **6 thèmes** :
  - **Thinking** (`dessin`) : paysage dessiné en SVG, en couches façon papier découpé, avec 4 saisons × 4 moments (aube, jour, crépuscule, nuit) calculés d'après le lever et le coucher du soleil à Perpignan. Météo animée. Paysage en **2 plans**, avec une petite couche pour les animaux.
  - **Modern** (`photo`) : une photo qui change toutes les 2 h. **Sans météo** : pleine luminosité le jour, vignettage la nuit. Salutation, heure et date en haut à gauche, nom de l'appli au-dessus du dock.
  - **Bullit** : grille de 6 colonnes en plein écran.
  - **Ambient** : style **vitrine**, avec un dégradé tiré des couleurs dominantes de la bannière, et la bannière en haute définition sur une carte inclinée en 3D, avec un reflet fixe. Pas de flou sur le dock.
  - **Minimal** : horloge géante et liste de texte. Apparence Auto, Clair ou Sombre.
  - **Kids** : tuiles inclinées, applis autorisées seulement (défaut : YouTube, Disney+, Beach Buggy Racing). Code parent par flèches, espace parent par appui long sur OK.
- **Veille de l'accueil** après 1 min d'inactivité (dock masqué), sauf dans les thèmes en grille.
- **Météo** (Thinking seulement) : nuages, voile et brume fixes. **Pluie, neige et orage animés par épisodes** : 45 s à chaque touche ou retour sur l'accueil, puis 30 s toutes les 4 min, à 15 images/s, limités à la zone au-dessus du dock.
- **Retirés à la demande de l'utilisateur** : animaux, feuilles, pétales, flocons décoratifs, papillons, lucioles, oiseaux, étoiles filantes. Le code est désactivé par `DECOR_LIFE = false` mais toujours présent. **La parallaxe a été supprimée.**

## 6. Modifications faites sur la box (toutes réversibles)

| Action | Retour arrière |
|---|---|
| `fr.accueil.tv` installé et défini comme accueil (`cmd package set-home-activity --user 0 fr.accueil.tv/.MainActivity`) | `pm uninstall fr.accueil.tv` après avoir réactivé le lanceur Google |
| Lanceur Google TV désactivé : `com.google.android.apps.tv.launcherx` | `pm enable com.google.android.apps.tv.launcherx` |
| Écran de secours désactivé : `com.google.android.tungsten.setupwraith` (sinon écran noir sur Accueil) | `pm enable com.google.android.tungsten.setupwraith` |
| Télémétrie Xiaomi désactivée : `com.miui.tv.analytics` | `pm enable …` |
| Désactivés : `com.mitv.tvhome.mitvplus` (Xiaomi TV+), `com.mitv.tvhome.atv` (PatchWall), `com.google.android.videos` (Play Films) | `pm enable …` |
| Économiseur : `settings put secure screensaver_components fr.accueil.tv/.AccueilDream` | `… com.google.android.apps.tv.dreamx/.service.Backdrop` |
| Gardien Kids (service d'accessibilité `KidsGuard`) : `settings put secure enabled_accessibility_services fr.accueil.tv/fr.accueil.tv.KidsGuard` puis `settings put secure accessibility_enabled 1` | `settings put secure enabled_accessibility_services '""'` puis `settings put secure accessibility_enabled 0` |

Préfixer chaque commande par `adb -s 192.168.1.39:5555 shell`.

**Non fait** : DNS anti-pubs (refusé par l'utilisateur), animations système à 0,5× (non retenu). L'utilisateur devait supprimer lui-même son identifiant publicitaire Google dans Paramètres → Confidentialité → Annonces ; je n'ai pas vérifié qu'il l'a fait.

### Gardien Kids
`KidsGuard.java` (service d'accessibilité, même processus que l'accueil). Le JS appelle `Launcher.kidsGuard(actif, pkgsJson)` à chaque changement de thème ou de liste Kids. L'état est stocké dans les SharedPreferences `kidsguard`. Seulement en thème Kids :
- touches en liste blanche (flèches, OK, Retour, Accueil, volume, lecture) ; tout le reste est avalé ;
- si une activité d'une appli lançable non autorisée (ou Réglages, Assistant, Play Store) passe au premier plan, retour à l'accueil.
En cas d'erreur, il laisse tout passer. Un badge « Protection Kids inactive » s'affiche si le service ne tourne pas (`Launcher.kidsGuardRunning()`). Les touches simulées par `adb shell input` ne passent pas par le filtre : il faut tester avec la vraie télécommande. Coût processeur mesuré : nul.

## 7. Mesures de performance (processeur de l'accueil, 100 % = 1 cœur sur 4)

| Situation | Coût |
|---|---|
| Repos, tous thèmes | **0 à 3 %** (au départ jusqu'à 115 % : animations infinies, clignotement de l'horloge… corrigé) |
| Un appui sur une flèche (Modern) | 74 % puis 36 %, retour à ~0 en 2 s (avant : 137/113 %, puis un second pic à 62 % ; −70 %) |
| Touche maintenue (un déplacement toutes les 0,3 s) | **≈ 150 à 185 % dans tous les thèmes** : c'est la limite de la WebView, chaque image recompose tout l'écran en 1080p |
| Pluie ou neige, pendant un épisode (Thinking) | ≈ 32 à 35 %, soit environ 4 à 5 % en moyenne un jour de pluie |
| Autres thèmes sous la pluie | 1 % (la boucle de pluie ne tourne plus quand la météo est cachée) |
| Intro AxiomOS (logo « AxiomOS » seul, couleurs qui circulent dans « OS », ~4,2 s) | ≈ 110 à 175 % pendant l'intro, une fois par allumage (l'ancienne version avec triangle était à ≈ 167 % pendant 5 s) |

**Hors de notre code** : `android.hardware.audio.service-droidlogic` consomme **≈ 23 % en permanence**, même sans son. Les pistes (sons système, format surround en PCM) ont été proposées mais **pas testées** : il faut l'accord de l'utilisateur.

**Mémoire** : accueil ≈ 220 Mo, plus le moteur web ≈ 150 Mo ; environ 760 Mo libres sur la box.

**Pièges de mesure rencontrés** :
- une boucle `requestAnimationFrame` de comptage force elle-même le dessin ;
- `gfxinfo` ne voit pas toujours les images de la WebView ;
- l'accueil en veille fausse les mesures de navigation ;
- mesurer juste après un changement de thème inclut la génération de l'image de chargement.

Méthode fiable : `top -b -n 2 -d 4`, en additionnant `fr.accueil.tv` et `sandboxed_process`, après stabilisation.

## 8. Préférences et règles de l'utilisateur (apprises en cours de route)

- **Demander l'accord avant toute modification système** (désactiver un paquet, réglage Android, téléchargement d'outils, redémarrage, mise en veille de la box).
- **Ne jamais réinstaller l'accueil pendant qu'il regarde la télé** ; toujours relancer l'accueil juste après une installation. **Ne pas changer son thème** pendant les tests, ou le remettre ensuite (actuellement : **Modern**).
- Goûts exprimés :
  - pas de cyprès ;
  - pas de contour orange ;
  - pas de cadre qui glisse d'une appli à l'autre ;
  - flou du logo d'Ambient jugé « pas esthétique », d'où la vitrine ;
  - animaux et particules retirés ;
  - **la météo est prioritaire** ;
  - Modern sans météo ;
  - noms des thèmes : Thinking, Modern, Bullit, Ambient, Minimal, Kids.
- Il apprécie les mesures chiffrées avant/après et les captures prises sur la box (`adb exec-out screencap -p`).
- Le mode Kids **n'est pas un verrou** : les boutons Netflix, Prime Video et Xiaomi TV+ de la télécommande, ainsi que l'Assistant, contournent l'écran d'accueil. YouTube Kids n'existe pas pour cette box ; on passe par un profil enfant dans YouTube.

## 9. Le projet natif : où on en est

**Pourquoi** : la navigation coûte cher parce que la WebView recompose tout l'écran à chaque image. Une appli native ne redessinerait que ce qui change. Estimation, non mesurée : 10 à 30 % par appui au lieu d'environ 74 %, 150 à 250 Mo de mémoire en moins, démarrage instantané.

**Plan sûr proposé et accepté dans le principe** :
1. Créer une **appli native séparée**, avec un autre paquet, par ex. `fr.accueil.native`. **L'accueil web reste installé et reste l'accueil par défaut** pendant tout le développement.
2. La tester comme une appli normale (`am start`), sans la définir comme accueil.
3. **Commencer par l'hybride**, recommandé : dock, grille et navigation en natif, fond affiché comme une simple image (on génère déjà `files/paysage.jpg`). Mesurer le gain réel avant d'aller plus loin.
4. Migrer les réglages (`localStorage` → SharedPreferences) via `tools/cdp.py`.
5. Basculer en une commande (`set-home-activity`), avec un retour arrière en une commande.

**Contraintes connues** :
- **pas de flou en direct sur Android 11** (RenderEffect arrive avec Android 12) : simuler le verre avec un fond pré-flouté ;
- les paysages SVG de Thinking sont les plus longs à porter (on peut commencer par des images pré-rendues) ;
- les itérations sont plus lentes qu'en web, faute d'aperçu dans le navigateur ;
- ADB se coupe en veille.

**Réutilisable tel quel** : la logique Java de `MainActivity` (liste des applis, bannières, lancement, HDMI et `SCREEN_ON`, image de chargement, intro au démarrage) et `AccueilDream`.

## 10. Autres pistes restées ouvertes

- Tester les réglages audio pour réduire les 23 % du service audio (avec accord).
- Si on reste en web : ne pas relancer la lueur ni le titre pendant une touche maintenue, et ne les jouer qu'à l'arrêt sur une appli.
- Retirer l'inspection du moteur web quand on aura fini.
- Nettoyer le code mort : `DECOR_LIFE`, animaux, particules, styles de parallaxe résiduels, `ambientStyle`.
