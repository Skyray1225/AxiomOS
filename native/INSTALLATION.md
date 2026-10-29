# Installer l'accueil natif AxiomOS sur un autre téléviseur — consignes pour Claude

> Ce document s'adresse à une session Claude qui va aider l'utilisateur à installer l'appli
> **AxiomOS natif** (`fr.accueil.natif`) sur un autre téléviseur ou boîtier Android TV.
> Suis les étapes dans l'ordre. **Avant toute modification du téléviseur, explique ce que tu
> vas faire et attends l'accord explicite de l'utilisateur** (voir « Règles de sécurité »).
> Réponds en français.

---

## 1. Ce qu'est l'appli

- Écran d'accueil pour Android TV, écrit en Java natif (pas de WebView), sans Gradle.
- Dépôt privé : `https://github.com/Skyray1225/AxiomOS`, **branche `native_app`**, dossier `native/`.
  - `native/build.sh` : compile, aligne et signe l'APK → `native/AccueilNatif.apk`.
  - `native/src/fr/accueil/natif/*.java` : le code. `native/AndroidManifest.xml` : le manifeste.
  - Les photos du thème Modern viennent de `launcher/photos/` (copiées par `build.sh`).
  - Clé de signature : `launcher/android/debug.keystore` (mot de passe `android`, alias `accueil`).
    Elle est dans le dépôt privé : ne la copie nulle part ailleurs et ne l'affiche pas.
- Paquet `fr.accueil.natif`, Android **8.0 minimum (API 26)**, cible API 30.
- Une seule permission : `INTERNET` (météo Open-Meteo, sans clé ni compte).
- Contient aussi, **inactifs tant qu'on ne les active pas** :
  - un économiseur d'écran (`fr.accueil.natif/.AccueilDream`) ;
  - un service d'accessibilité « Gardien Kids » (`fr.accueil.natif/.KidsGuard`), qui bloque les
    applis non autorisées en mode Kids.
- Thèmes : Thinking (paysage dessiné : saisons, moments de la journée, météo, effets de saison,
  décor d'Halloween), Modern, Bullit, Ambient, Minimal, Kids (code parent).
- **Lieu codé en dur : Perpignan** (`LAT`, `LON` dans `SceneView.java`) pour la météo et les heures
  de lever et de coucher du soleil. Si le téléviseur est ailleurs, propose de changer ces deux valeurs
  avant de compiler.

## 2. Règles de sécurité (à respecter tout du long)

1. **Accord avant chaque changement système** : installation, choix de l'accueil par défaut,
   économiseur d'écran, service d'accessibilité, réglages Android, redémarrage. Annonce la commande
   exacte, puis attends un « oui ».
2. **Ne jamais désinstaller ni désactiver** l'accueil d'origine ou une appli système
   (`pm uninstall`, `pm disable`, `pm hide` interdits). L'appli s'installe **à côté**, et on peut
   toujours revenir en arrière.
3. **Noter l'état initial avant de le modifier** (étape 4) pour pouvoir tout rétablir.
4. **Ne pas déranger** : ne rien installer ni relancer pendant que quelqu'un regarde la télé ;
   vérifie d'abord l'appli au premier plan.
5. **ADB par le réseau = porte ouverte** : seulement sur le réseau local de la maison, jamais exposé
   à Internet ; à la fin, désactive le débogage (étape 10).
6. N'installe que l'APK **compilé depuis ce dépôt**, et vérifie sa signature (étape 5).
7. Pas de root, pas de contournement de sécurité, aucun mot de passe ni compte à saisir à la place
   de l'utilisateur.
8. Essais sans rien enregistrer : utilise `--es themeEssai <thème>` (étape 7) plutôt que de changer
   les réglages de l'utilisateur.

## 3. Vérifier que le téléviseur est compatible

- **Seuls les téléviseurs sous Android TV ou Google TV conviennent** (Sony, Philips, TCL,
  Xiaomi, Chromecast avec Google TV, boîtiers Android TV…). Les Samsung (Tizen), LG (webOS) et autres
  systèmes non Android **ne peuvent pas** l'installer : dis-le à l'utilisateur et arrête-toi.
- Sur l'ordinateur : outils Android installés (`adb`, SDK avec `build-tools/35.0.0` et
  `platforms/android-30`, un JDK). `build.sh` les cherche dans `~/Android/Sdk` et `~/Android/jdk`,
  ou via `ANDROID_HOME` et `JAVA_HOME`.
- Activer le débogage sur le téléviseur (c'est l'utilisateur qui le fait, à la télécommande) :
  1. Paramètres → Système (ou Préférences de l'appareil) → À propos → appuyer 7 fois sur
     « Build » / « Version de l'OS » → options pour les développeurs activées.
  2. Options pour les développeurs → activer « Débogage USB » / « Débogage ADB » et, selon le modèle,
     « Débogage réseau » ou « Débogage sans fil ».
  3. Noter l'adresse IP du téléviseur (Paramètres → Réseau).
- Connexion depuis l'ordinateur (remplace `IP`) :
  ```bash
  adb connect IP:5555
  ```
  Sur certains Android 11 et plus avec « débogage sans fil », il faut d'abord `adb pair IP:PORT`
  avec le code affiché à l'écran, puis `adb connect` sur le port indiqué.
  **Une fenêtre « Autoriser le débogage ? » apparaît sur la télé : c'est l'utilisateur qui accepte.**
- Contrôles (lecture seule, sans risque) :
  ```bash
  adb -s IP:5555 shell getprop ro.build.version.sdk      # doit être >= 26
  adb -s IP:5555 shell pm list features | grep leanback  # présent sur Android TV
  adb -s IP:5555 shell wm size                           # 16:9 attendu (1920x1080, 3840x2160…)
  ```
  Avec une version inférieure à 26, ne continue pas. Un écran qui n'est pas en 16:9 fonctionne, mais
  préviens que la mise en page peut être imparfaite.

## 4. Noter l'état initial (pour pouvoir revenir en arrière)

```bash
adb -s IP:5555 shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s IP:5555 shell settings get secure screensaver_components
adb -s IP:5555 shell settings get secure enabled_accessibility_services
adb -s IP:5555 shell dumpsys activity activities | grep -m1 mResumedActivity
```
Garde ces valeurs dans la conversation : l'accueil d'origine, l'économiseur d'origine et les services
d'accessibilité déjà actifs. La dernière commande indique ce qui est à l'écran : si c'est une appli
vidéo, attends.

## 5. Compiler et vérifier l'APK

```bash
git clone https://github.com/Skyray1225/AxiomOS.git && cd AxiomOS && git checkout native_app
# (facultatif) adapter LAT et LON dans native/src/fr/accueil/natif/SceneView.java
./native/build.sh
"$HOME/Android/Sdk/build-tools/35.0.0/apksigner" verify --print-certs native/AccueilNatif.apk
```
La vérification doit réussir (« Verified ») et afficher un certificat. N'installe pas un APK qui
ne vient pas de cette compilation.

## 6. Installer à côté de l'accueil existant (avec accord)

```bash
adb -s IP:5555 install -r --no-incremental native/AccueilNatif.apk
```
- `--no-incremental` : l'installation incrémentale peut rester bloquée sur certains téléviseurs.
- Cette étape ne remplace rien : l'accueil d'origine reste l'accueil par défaut. L'appli apparaît
  dans la liste des applis sous le nom « Accueil natif ».

## 7. Essayer sans rien changer

```bash
adb -s IP:5555 shell am start -S -n fr.accueil.natif/.MainActivity
adb -s IP:5555 shell am start -S -n fr.accueil.natif/.MainActivity --es themeEssai dessin
```
- `themeEssai` (`dessin` = Thinking, `photo` = Modern, `bullit`, `ambient`, `minimal`, `kids`) affiche
  un thème **sans l'enregistrer**.
- Pour Thinking, tu peux aussi forcer `--es saison printemps|ete|automne|hiver|halloween`,
  `--ef heure 22` ou `--ei meteo 63` (codes WMO : 1 clair, 3 couvert, 45 brouillard, 63 pluie,
  73 neige, 95 orage).
- Captures pour vérifier : `adb -s IP:5555 exec-out screencap -p > capture.png`.
- Si l'écran de veille est affiché, réveille la télé avec `adb -s IP:5555 shell input keyevent KEYCODE_WAKEUP`.
- Au premier lancement, l'intro « AxiomOS » dure environ 4 s ; une touche la passe.
- Erreurs éventuelles : `adb -s IP:5555 logcat -d | grep -E "AccueilNatif|FATAL"`.

## 8. En faire l'accueil principal (avec accord)

```bash
adb -s IP:5555 shell cmd package set-home-activity fr.accueil.natif/.MainActivity
adb -s IP:5555 shell input keyevent KEYCODE_HOME
```
- Vérifie avec la commande `resolve-activity` de l'étape 4 : elle doit afficher
  `fr.accueil.natif/.MainActivity`.
- Si `set-home-activity` n'existe pas (Android plus ancien), appuie sur Accueil : Android propose
  de choisir l'accueil, et c'est l'utilisateur qui sélectionne « Accueil natif », puis « Toujours ».
- **Google TV et certains constructeurs** peuvent remettre leur accueil d'origine (mise à jour,
  redémarrage) ou ignorer ce choix. Dans ce cas, n'essaie **pas** de désactiver l'accueil
  d'origine : explique la limite, et l'appli reste utilisable depuis la liste des applis.
- Une fois accueil par défaut, l'intro joue une fois par démarrage et au rallumage de la télé.

## 9. Options (chacune avec accord séparé)

- **Économiseur d'écran** (le décor de l'accueil pendant la veille) :
  ```bash
  adb -s IP:5555 shell settings put secure screensaver_components fr.accueil.natif/.AccueilDream
  ```
  Pour tester : `adb -s IP:5555 shell am start -n com.android.systemui/.Somnambulator` (une touche le ferme).
- **Gardien Kids** (seulement si l'utilisateur veut le mode Kids **et** le demande) : c'est un
  service d'accessibilité qui voit l'appli au premier plan. Préviens de ce que cela implique. Pour
  l'activer, ajoute-le **à la liste existante** notée à l'étape 4, séparé par `:`, au lieu de
  l'écraser :
  ```bash
  adb -s IP:5555 shell settings put secure enabled_accessibility_services "<liste existante>:fr.accueil.natif/fr.accueil.natif.KidsGuard"
  adb -s IP:5555 shell settings put secure accessibility_enabled 1
  ```
  (Si la liste était `null` ou vide, mets seulement `fr.accueil.natif/fr.accueil.natif.KidsGuard`.)
- Les réglages de l'utilisateur (thème, favoris, Halloween, effets de saison, animations) se font
  ensuite **à la télécommande** : flèche haut → Préférences. Ne les modifie pas toi-même.

## 10. Terminer en sécurité

1. Vérifie que l'accueil s'affiche correctement à la touche Accueil, puis après un redémarrage
   **si l'utilisateur accepte de redémarrer**.
2. Déconnecte-toi : `adb disconnect IP:5555`.
3. Recommande à l'utilisateur de **désactiver le débogage ADB / réseau** dans les options pour
   les développeurs (voire les options pour les développeurs elles-mêmes) : sinon n'importe quel
   appareil du réseau peut demander l'accès.

## 11. Revenir en arrière

Utilise les valeurs notées à l'étape 4 :
```bash
adb -s IP:5555 shell cmd package set-home-activity <accueil d'origine>
adb -s IP:5555 shell settings put secure screensaver_components <économiseur d'origine>
adb -s IP:5555 shell settings put secure enabled_accessibility_services "<liste d'origine>"
adb -s IP:5555 uninstall fr.accueil.natif      # seulement si l'utilisateur veut supprimer l'appli
```
Si `screensaver_components` ou `enabled_accessibility_services` valaient `null`, utilise
`settings delete secure <clé>` au lieu de `put`. Désinstaller l'appli efface aussi ses réglages
(thème, favoris).

## 12. Mises à jour

Recompile (étape 5) puis réinstalle avec la même commande `install -r --no-incremental` : les
réglages sont conservés, car la clé de signature est la même. **Ne réinstalle jamais pendant que
quelqu'un regarde la télé**, et relance ensuite l'accueil (touche Accueil, ou
`am start -n fr.accueil.natif/.MainActivity`).
