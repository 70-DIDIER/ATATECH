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
  - `MainActivity.kt` — point d'entrée, affiche `MainAssistantScreen`
  - `MainAssistantScreen.kt` — écran principal (topbar, zone conversation, zone d'état, zone micro)
  - `AssistantViewModel.kt` — état de l'assistant (`assistantState: StateFlow<AssistantState>`)
  - `AssistantState.kt` — modèle d'état UI
  - `ConversationMessage.kt` — modèle d'un message de conversation
  - `PermissionHandler.kt` — gestion des permissions runtime (`rememberPermissionState`)

## Permissions déclarées

- `RECORD_AUDIO`
- `CAMERA`
- `ACCESS_FINE_LOCATION`




cd "c:\Users\Julien\Desktop\ATATECH\ATATECH" && git status && echo "---branch---" && git branch --show-current

cd "c:\Users\Julien\Desktop\ATATECH\ATATECH" && git add atatech_android/app/build.gradle.kts atatech_android/app/src/main/AndroidManifest.xml atatech_android/app/src/main/java/com/atatech/app/ && git status