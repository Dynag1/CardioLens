# ❤️ CardioLens - Votre Santé, Clarifiée.

**CardioLens** est une application Android moderne et performante conçue pour centraliser, visualiser et analyser vos données de santé (Fitbit & Health Connect). 
Avec une interface **Jetpack Compose** fluide et un moteur de données intelligent, redécouvrez vos métriques vitales sous un nouveau jour.

---

## ✨ Fonctionnalités Clés

### 📊 Tableau de Bord (Dashboard)
Une vue d'ensemble complète de votre journée :
- **Multi-Sources** : Basculez instantanément entre **Fitbit** et **Health Connect** (Google Fit).
- **Rythme Cardiaque** :
    - Données en temps réel et graphique intraday précis.
    - **Analyse RHR Avancée** : Distinction scientifique entre le pouls au repos de jour vs nuit.
- **Métriques Avancées** :
    - **Variabilité Cardiaque (HRV)** : Suivez votre stress et votre récupération (RMSSD).
    - **SpO2** : Saturation en oxygène du sang.
- **Sommeil & Activité** :
    - Analyse détaillée des phases de sommeil.
    - Jauges d'activité visuelles et suivi des pas.

### 🎭 Suivi de l'Humeur
Parce que la santé mentale est indissociable de la santé physique :
- **Journal Quotidien** : Une interface simple ("Comment allez-vous ?") pour noter votre humeur du jour.
- **Corrélation** : Visualisez l'impact de votre sommeil et de votre activité sur votre moral dans l'onglet Tendances.

### 🏃 Entraînements
Une vue complète et intelligente de vos activités sportives :
- **Pull-to-Refresh** : Rafraîchissez vos données d'un simple geste.
- **Filtres Dynamiques** : Filtrage automatique par type d'activité (Marche, Course, Vélo, Natation, Exercice).
- **Tri Multi-Critères** : Organisez vos séances par récence, durée ou intensité.
- **Statistiques Mensuelles** : Visualisez en un coup d'œil vos totaux du mois (activités, durée, distance, calories).
- **Résumés Hebdomadaires** : Cartes interactives avec statistiques détaillées et export PDF.

![Workouts Screen](screenshots/workouts_screen.png)

### 📈 Tendances & Analyse
Ne regardez pas seulement aujourd'hui, comprenez votre évolution :
- Graphiques interactifs sur **7, 15 ou 30 jours**.
- Comparaison des moyennes vs médianes pour éviter les faux positifs.
- Détection automatique des anomalies.

### 💾 Sauvegarde & Sécurité
Vos données vous appartiennent :
- **Sauvegarde Universelle** : Exportez vos données (Humeur, Cache, Préférences) vers **n'importe quel dossier** (Local, Google Drive, Dropbox...).
- **Mode Hors-Ligne** : "Smart Caching" complet. Consultez tout votre historique sans connexion.
- **Confidentialité** : Les tokens sont chiffrés (`EncryptedSharedPreferences`) et aucune donnée ne part vers un serveur tiers inconnu.

### ⚡ Expérience Utilisateur
- **Actualisation Automatique** : Vos données sont fraîches dès l'ouverture de l'application.
- **Dark Mode** natif et respectueux de la batterie.
- **Performance** : Moteur de synchronisation incrémentale (ne télécharge que ce qui manque).

---
Depot F-Droid : https://dynag1.github.io/Dynag1-Fdroid/fdroid/repo/
## 🛠️ Stack Technique

Construit avec les dernières technologies Android pour robustesse et maintenabilité :

- **Langage** : 100% [Kotlin](https://kotlinlang.org/)
- **UI** : [Jetpack Compose](https://developer.android.com/jetpack/compose) + Material Design 3
- **Architecture** : Clean Architecture + MVVM
- **Injection** : [Hilt](https://dagger.dev/hilt/)
- **Données** : 
  - [Room](https://developer.android.com/training/data-storage/room) (SQLite) pour la persistance locale complexe.
  - [Retrofit](https://square.github.io/retrofit/) & OkHttp pour l'API Fitbit.
- **Tâches de fond** : [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) pour les synchronisations périodiques.

---

## 🚀 Installation & Configuration

### Prérequis
- Android 8.0 (Oreo) ou supérieur.

### Configuration API
1. **Fitbit** : Créez une application "Personal" sur [dev.fitbit.com](https://dev.fitbit.com) avec l'URL de callback `cardioapp://fitbit-auth`.
2. **Health Connect** : Installez simplement l'application Google Health Connect (intégrée sur Android 14+).

---

## 📄 Licence
Ce projet est développé dans un but éducatif et personnel.
Code source disponible sur [GitHub](https://github.com/Dynag1/CardioLens).
