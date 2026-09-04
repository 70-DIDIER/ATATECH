# ATATECH Android

Projet Android (Kotlin + Jetpack Compose).

## Prérequis

- Java (JDK) installé — vérifier : `java -version`
- Kotlin (kotlinc) installé — vérifier : `kotlinc -version`
- SDK Android présent dans `%LOCALAPPDATA%\Android\Sdk`

> Si `kotlinc` ou `adb` ne sont pas reconnus dans PowerShell alors qu'ils sont installés : ferme **complètement** VS Code (toutes les fenêtres) et rouvre-le. Le PATH n'est rechargé qu'au redémarrage de l'application.

## Commandes courantes

Toutes les commandes `gradlew` se lancent depuis le dossier `atatech_android` :

```powershell
cd "C:\Users\HP\OneDrive\Documents\Dossier penuel\penuel\concours foire estudiantine\ATATECH\atatech_android"
```

### Compiler (vérification rapide, sans installer)

```powershell
.\gradlew.bat compileDebugKotlin
```

### Construire l'APK debug

```powershell
.\gradlew.bat assembleDebug
```

APK généré dans `app\build\outputs\apk\debug\app-debug.apk`.

### Voir les appareils/émulateurs connectés

```powershell
adb devices
```

(si `adb` n'est pas reconnu : `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices`)

### Installer l'app sur un appareil/émulateur connecté

```powershell
.\gradlew.bat installDebug
```

### Lancer l'app sur l'appareil

```powershell
adb shell am start -n com.atatech.app/.MainActivity
```

(si `adb` n'est pas reconnu : `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.atatech.app/.MainActivity`)

### Cycle complet après une modification de code

```powershell
.\gradlew.bat installDebug
adb shell am start -n com.atatech.app/.MainActivity
```

## Structure

- `app/src/main/java/com/atatech/app/`
  - `MainActivity.kt` — point d'entrée, `NavHost` avec 3 routes (`main`, `history`, `settings`)
  - `MainAssistantScreen.kt` — écran principal, **branché sur l'API Démarches réelle** : ouvre une session au lancement (`DemarcheViewModel.startSession`), affiche `DemarcheMessageList` + `DemarcheInputArea`
  - `HistoryScreen.kt` / `PastRequest.kt` — écran d'historique des demandes (données d'exemple pour l'instant)
  - `SettingsScreen.kt` / `AppPreferences.kt` — écran de paramètres (choix de langue + connexion serveur, voir plus bas)
  - `PermissionHandler.kt` — gestion des permissions runtime (`rememberPermissionState`)
  - `ThinkingIndicator.kt` — indicateur "Je réfléchis…"

**Ancien pipeline factice, toujours dans le code mais plus branché à l'écran principal** (`AssistantViewModel`, `ConversationList`, `MessageInputBar`, `BackgroundListeningToggle`, `AssistantStatusArea`, `ActionType`/`ActionInProgressIndicator`, `Orchestrator`/`StubOrchestrator`, `MicButton`, `ScanDocumentButton`, `SosButton`) — conversation libre + note vocale + statut de pipeline simulé. Conservé si besoin de le rebrancher ailleurs, mais l'écran principal utilise maintenant `DemarcheViewModel`.

### Intégration API « Nye Gbe — Démarches » (voir `API_DEMARCHES.md`)

- `DemarchesModels.kt` — data classes du contrat JSON (`DemarchesResponse`, `EtatDemarche`, `DemarcheMessage`, `Attend`, `ChoixOption`, `LigneMessage`, requêtes)
- `DemarchesApi.kt` — interface Retrofit (`ping`, `session`, `message`, `photo` multipart)
- `ApiClient.kt` — construit un `Retrofit`/`OkHttp` à partir des préférences courantes ; ajoute l'en-tête `X-Api-Cle` sur toutes les routes sauf `/api/v1/ping`
- `ApiPreferences.kt` — persiste l'URL de base et la clé API via `SharedPreferences`
- `SettingsScreen.kt` — champs URL de base / clé API + bouton "Tester la connexion" (appelle `/ping`)
- `DemarcheViewModel.kt` — état du parcours : `messages`, `attend` (pilote l'écran), `isFini`, `isLoading`, `errorMessage` ; mode **« état »** (pas de `session_id`, l'objet `etat` reçu est renvoyé à chaque tour — l'app survit à un redémarrage du backend)
- `DemarcheMessageList.kt` — affiche les messages (éwé en gros, français en dessous ; format carte pour `carte == "fiche"`) et joue automatiquement `audio_url` (`MediaPlayer`)
- `DemarcheInputArea.kt` — bascule l'input selon `attend.type` : `choix` → boutons, `photo` → appareil photo (`FileProvider` + `ActivityResultContracts.TakePicture`), `code_secret` → champ masqué (jamais journalisé), `texte` → saisie libre, `rien` → "Parcours terminé"

**Pas encore fait / limites connues :**
- Testé uniquement en local (compilation + UI) — **aucun backend n'a répondu pendant les tests**, voir note réseau ci-dessous
- Le micro n'est pas branché à ce flux (la doc §8 précise qu'il n'est pas nécessaire pour le scénario scripté ; s'il est ajouté plus tard, il faudra enregistrer en PCM 16 bits/16kHz via `AudioRecord`, pas `MediaRecorder`/M4A)
- Pas de `/api/v1/demarches/services` consommé (catalogue des services, non utilisé par le flux scripté)

**Note réseau (04/09/2026)** : le port 5000 sur la machine de dev est occupé par un tout autre serveur (le site vitrine "Nye Gbe me"), pas l'API Démarches — `lancer_api.bat` n'a pas été trouvé sur cette machine. Il faut que quelqu'un lance le vrai backend et communique l'IP:port réellement affiché.

## Permissions déclarées

- `INTERNET`
- `RECORD_AUDIO`
- `CAMERA`
- `ACCESS_FINE_LOCATION`

## Dépendances notables

- `androidx.compose.material:material-icons-extended:1.6.0` — nécessaire pour les icônes hors du set de base (ex. `Icons.Default.Autorenew`)
- `androidx.compose.animation:animation:1.6.0` — nécessaire pour `AnimatedContent` (transition entre statuts)
- `com.squareup.retrofit2:retrofit` + `converter-moshi`, `com.squareup.moshi:moshi-kotlin` (réflexion, pas de kapt), `com.squareup.okhttp3:okhttp` + `logging-interceptor` — client HTTP pour l'API Démarches
- `android:usesCleartextTraffic="true"` dans le manifeste — l'API tourne en HTTP simple (voir `API_DEMARCHES.md` §1)
