# Cardio - Application Android Fitbit

Application Android moderne qui se connecte à Fitbit pour récupérer et afficher vos données de santé dans des graphiques interactifs.

## 📱 Fonctionnalités

- ✅ **Authentification OAuth 2.0** avec Fitbit (PKCE)
- ❤️ **Rythme cardiaque**: Visualisation du rythme cardiaque au repos et des zones de fréquence
- 😴 **Sommeil**: Analyse détaillée des phases de sommeil (profond, léger, REM, éveillé)
- 👟 **Pas**: Suivi quotidien et statistiques hebdomadaires
- 🏃 **Activités**: Résumé des exercices et calories brûlées
- 🎨 **Interface moderne**: Design sombre avec Material 3 et Jetpack Compose
- 💾 **Stockage sécurisé**: Tokens OAuth chiffrés avec EncryptedSharedPreferences

## 🛠️ Technologies utilisées

- **Kotlin** - Langage de programmation
- **Jetpack Compose** - UI moderne et déclarative
- **Material 3** - Design system
- **Hilt** - Injection de dépendances
- **Retrofit** - Client HTTP pour l'API Fitbit
- **Room** - Base de données locale (prévu pour le cache)
- **Coroutines** - Programmation asynchrone
- **Chrome Custom Tabs** - Authentification OAuth

## 📋 Prérequis

1. **Compte développeur Fitbit**
   - Créez un compte sur [dev.fitbit.com](https://dev.fitbit.com)
   - Créez une nouvelle application

2. **Configuration de l'application Fitbit**
   - **OAuth 2.0 Application Type**: Client ou Personal
   - **Callback URL**: `cardioapp://fitbit-auth`
   - **Scopes**: activity, heartrate, sleep, profile

3. **Android Studio**
   - Version: Arctic Fox ou supérieure
   - SDK minimum: API 26 (Android 8.0)
   - SDK cible: API 34 (Android 14)

## 🚀 Installation

### 1. Cloner le projet

Le projet est déjà créé dans: `/home/hemge/Clood/021 - Programmation/Android/Cardio`

### 2. Configurer les credentials Fitbit

Éditez le fichier `app/src/main/res/values/strings.xml` et remplacez les placeholders:

```xml
<string name="fitbit_client_id">VOTRE_CLIENT_ID</string>
<string name="fitbit_client_secret">VOTRE_CLIENT_SECRET</string>
```

### 3. Synchroniser le projet

Ouvrez le projet dans Android Studio et laissez Gradle synchroniser les dépendances.

### 4. Compiler l'application

#### Mode Debug (pour tester)
```bash
cd /home/hemge/Clood/021\ -\ Programmation/Android/Cardio
./gradlew assembleDebug
```

L'APK sera généré dans: `app/build/outputs/apk/debug/app-debug.apk`

#### Mode Release (pour production)
```bash
./gradlew assembleRelease
```

L'APK sera généré dans: `app/build/outputs/apk/release/app-release.apk`

### 5. Installer sur un appareil

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 📱 Utilisation

1. **Lancer l'application**
2. **Cliquer sur "Se connecter avec Fitbit"**
3. **S'authentifier** avec vos identifiants Fitbit dans le navigateur
4. **Autoriser l'accès** aux données demandées
5. **Profiter** de vos données de santé visualisées !

## 🏗️ Architecture

```
app/
├── src/main/java/com/cardio/fitbit/
│   ├── auth/                    # Authentification OAuth
│   │   └── FitbitAuthManager.kt
│   ├── data/
│   │   ├── api/                 # Client API Retrofit
│   │   ├── models/              # Modèles de données
│   │   └── repository/          # Repository pattern
│   ├── ui/
│   │   ├── components/          # Composants UI réutilisables
│   │   ├── screens/             # Écrans de l'application
│   │   ├── theme/               # Thème Material 3
│   │   └── navigation/          # Navigation Compose
│   └── utils/                   # Utilitaires
└── res/                         # Ressources (strings, themes, etc.)
```

## 🔐 Sécurité

- Les tokens OAuth sont stockés de manière chiffrée avec `EncryptedSharedPreferences`
- Utilisation de PKCE (Proof Key for Code Exchange) pour l'OAuth
- Rafraîchissement automatique des tokens expirés
- Pas de stockage de credentials en clair

## 🐛 Dépannage

### Erreur "Client ID not found"
Vérifiez que vous avez bien configuré les credentials dans `strings.xml`

### Erreur d'authentification
- Vérifiez que le Callback URL dans l'app Fitbit correspond exactement à `cardioapp://fitbit-auth`
- Assurez-vous que les scopes sont correctement configurés

### Pas de données affichées
- Vérifiez que votre compte Fitbit contient des données
- Vérifiez la connexion internet
- Consultez les logs avec `adb logcat`

## 📄 Licence

Ce projet est un exemple éducatif. Consultez les conditions d'utilisation de l'API Fitbit.

## 🤝 Contribution

Projet personnel - Pas de contributions externes pour le moment.

## 📞 Support

Pour toute question concernant l'API Fitbit, consultez la [documentation officielle](https://dev.fitbit.com/build/reference/web-api/).
