# Cardio - Application Android Santé & Fitness

Application Android moderne conçue pour visualiser vos données de santé (Fitbit & Health Connect) avec des graphiques interactifs et une expérience utilisateur fluide.

## 📱 Fonctionnalités Principales

### 📊 Tableau de Bord (Dashboard)
- **Multi-Sources**: Supporte **Fitbit** et **Health Connect** (Google Fit).
- **Rythme Cardiaque**:
    - Fréquence cardiaque en temps réel (si disponible).
    - Graphique interactif de la journée (Intraday).
    - Fréquence au repos (RHR).
- **Variabilité Cardiaque (HRV)**:
    - Suivi quotidien du RMSSD.
    - Graphique d'évolution si plusieurs mesures disponibles.
- **Sommeil**:
    - Analyse des phases : Profond, Léger, REM, Éveillé.
    - Score de sommeil et efficacité.
- **Activité & Pas**:
    - Compteur de pas quotidien avec jauge visuelle.
    - Résumé des activités sportives et calories brûlées.

### 📈 Tendances (Trends)
Suivez l'évolution de votre santé sur **7, 15 ou 30 jours** :
- **Fréquence Cardiaque au Repos (RHR)** : Comparaison Jour vs Nuit.
- **HRV** : Analyse de la récupération et du stress.
- **Chargement Intelligent** : Ne télécharge que les données manquantes pour une rapidité optimale.

### 🚀 Performance & Technique
- **Smart Caching** : Toutes les données (HR, Pas, Sommeil, Intraday) sont stockées localement.
- **Mode Hors-Ligne** : Consultez vos données même sans connexion internet.
- **Mise à jour incrémentale** : L'app détecte les "trous" dans l'historique et ne télécharge que le nécessaire.
- **Interface Moderne** : 100% Jetpack Compose avec thème Material 3 (Dark Mode par défaut).

## 🛠️ Stack Technique

- **Langage**: Kotlin
- **UI**: Jetpack Compose, Material 3
- **Architecture**: MVVM, Clean Architecture
- **Injection de Dépendances**: Hilt
- **Réseau**: Retrofit, OkHttp (Authentification OAuth 2.0 avec PKCE)
- **Base de Données**: Room (SQLite) avec DAOs personnalisés
- **Asynchronisme**: Coroutines, Flow
- **Graphiques**: Canvas API personnalisé (pas de lib tierce lourde)

## 📋 Prérequis & Configuration

### 1. Fitbit API
Si vous utilisez la source Fitbit :
- Créez une app sur [dev.fitbit.com](https://dev.fitbit.com).
- Type : **Personal** (pour avoir accès aux données Intraday).
- Callback URL : `cardioapp://fitbit-auth`.
- Scopes requis : `activity`, `heartrate`, `sleep`, `profile`.

### 2. Health Connect
Si vous utilisez Health Connect (bêta) :
- Assurez-vous d'avoir l'application Google Fit ou une autre source compatible installée.
- Accordez les permissions de lecture dans les paramètres Android.

## 🚀 Installation & Compilation

### Cloner et Ouvrir
Le projet est un projet Android Studio standard.
```bash
git clone <url-du-repo>
```

### Modes de Compilation
Via Android Studio ou en ligne de commande :

#### Debug
```bash
./gradlew installDebug
```
L'APK sera installé directement sur votre appareil connecté.

#### Release
```bash
./gradlew assembleRelease
```
L'APK signé sera dans `app/build/outputs/apk/release/`.

## 🏗️ Structure du Projet

```
app/src/main/java/com/cardio/fitbit/
├── auth/          # Gestion OAuth Fitbit & PKCE
├── data/
│   ├── api/       # Enpoints Retrofit
│   ├── local/     # Base de données Room (CacheEntities, DAOs)
│   ├── models/    # Data Classes (HeartRate, Steps, Sleep...)
│   ├── provider/  # Abstraction (FitbitProvider, HealthConnectProvider)
│   └── repository/# HealthRepository (Single Source of Truth)
├── di/            # Modules Hilt (AppModule, DatabaseModule)
├── ui/
│   ├── components/# Widgets UI (Charts, Cards, Headers)
│   ├── screens/   # Dashboard, Trends, Login, Welcome
│   └── theme/     # Couleurs, Typographie, Shapes
└── utils/         # Extensions et Formatteurs de dates
```

## 🔒 Sécurité & Vie Privée

- **Local First** : Vos données restent sur votre téléphone.
- **Chiffrement** : Les tokens d'accès sont stockés dans `EncryptedSharedPreferences`.
- **Contrôle Total** : Vous choisissez quelle source de données utiliser.

## 📄 Licence
Projet personnel éducatif.
