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
  - `MainAssistantScreen.kt` — écran principal (topbar avec accès historique/paramètres, `ConversationList`, zone d'état, `MicButton`)
  - `ConversationList.kt` — liste défilante des messages (`viewModel.messages`), bulles utilisateur/assistant
  - `HistoryScreen.kt` / `PastRequest.kt` — écran d'historique des demandes (données d'exemple pour l'instant)
  - `SettingsScreen.kt` / `AppPreferences.kt` — écran de paramètres (choix de langue, persisté via `SharedPreferences`)
  - `AssistantViewModel.kt` — expose `assistantState: StateFlow<AssistantState>` (statut), `messages: StateFlow<List<ConversationMessage>>` et `currentInput: StateFlow<String>` séparément ; contient `processNationalityRequest(...)` (pipeline émettant les étapes via `ActionInProgress`, délais de 1500ms entre étapes) et `startListening()` (stub, appelé par `MicButton`)
  - `AssistantState.kt` — sealed class du statut courant (`Idle`, `Thinking`, `ActionInProgress(action: ActionType)`, `Result(message)`, `Error`)
  - `Orchestrator.kt` — interface du pipeline IA (`runOcr`, `extractFields`, `verify`) + `StubOrchestrator` (implémentation temporaire, à remplacer par le vrai pipeline)
  - `ConversationMessage.kt` — modèle d'un message de conversation
  - `PermissionHandler.kt` — gestion des permissions runtime (`rememberPermissionState`)
  - `ThinkingIndicator.kt` — indicateur "Je réfléchis…" affiché pour `AssistantState.Thinking`
  - `ActionInProgressIndicator.kt` — badge arrondi affichant une `ActionType` (icône + label, transition fondu entre étapes)
  - `AssistantStatusArea.kt` — bascule animée entre les indicateurs selon `AssistantState`
  - `ActionType.kt` — sealed class extensible listant les étapes du pipeline IA (scan, extraction, vérification, paiement, traduction, alerte, `Custom`) avec label + icône ; branché à `AssistantState.ActionInProgress`
  - `MicButton.kt` — bouton micro branché (Julien) : reconnaissance vocale réelle (`SpeechRecognizer`), permission `RECORD_AUDIO` via `rememberPermissionState`, animation de pulsation pendant l'écoute, envoie le texte reconnu via `viewModel.onInputChange` + `sendMessage()`
  - `ScanDocumentButton.kt` — même principe pour `CAMERA`, demandée à l'appui sur "Scanner ma pièce", appelle `viewModel.startDocumentScan()`
  - `SosButton.kt` — même principe pour `ACCESS_FINE_LOCATION`, demandée uniquement à l'appui sur le bouton SOS, appelle `viewModel.sendSosAlert()`

## Permissions déclarées

- `RECORD_AUDIO`
- `CAMERA`
- `ACCESS_FINE_LOCATION`

## Dépendances notables

- `androidx.compose.material:material-icons-extended:1.6.0` — nécessaire pour les icônes hors du set de base (ex. `Icons.Default.Autorenew`)
- `androidx.compose.animation:animation:1.6.0` — nécessaire pour `AnimatedContent` (transition entre statuts)
