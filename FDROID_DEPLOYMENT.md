# Déploiement sur F-Droid

Ce guide détaille la procédure pour soumettre **CardioLens** sur le dépôt principal de F-Droid.

## 1. Prérequis

L'application doit respecter les critères d'inclusion F-Droid :
- **Open Source** (License approuvée, ici Apache 2.0).
- **Pas de librairies propriétaires** (Google Play Services, Firebase, Crashlytics, etc.).
    - *Vérifié* : `build.gradle.kts` ne contient pas de dépendances Google fermées.
- **Build reproductible** : F-Droid compile l'application depuis le code source.
    - *Corrigé* : Le fichier `build.gradle.kts` a été modifié pour ne pas planter si la clé de signature est absente.

## 2. Préparer les Métadonnées

F-Droid utilise un fichier YAML pour configurer le build et les infos de l'app.

### Fichier `com.cardio.fitbit.yml`
Créez ce fichier (contenu fourni ci-dessous) qui définit comment construire l'application.

```yaml
Categories:
  - Sports & Health
License: Apache-2.0
SourceCode: https://github.com/Dynag1/CardioLens
IssueTracker: https://github.com/Dynag1/CardioLens/issues
Changelog: https://github.com/Dynag1/CardioLens/blob/master/CHANGELOG.md

AutoName: CardioLens
Summary: Visualize health data from multiple sources
Description: |-
  CardioLens connects to health data providers to retrieve and visualize your fitness information.

RepoType: git
Repo: https://github.com/Dynag1/CardioLens

Builds:
  - versionName: 1.2.3
    versionCode: 31
    commit: e45b6ff25b947c13b23b68da4e5d6e64820cba42
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version v%v
UpdateCheckMode: Tags
CurrentVersion: 1.2.3
CurrentVersionCode: 31

AntiFeatures:
  - NonFreeNet
```

> **Note** : `AntiFeatures: NonFreeNet` est ajouté car l'application se connecte à l'API Fitbit (service, non-libre).

## 3. Procédure de Soumission (Merge Request)

La soumission se fait via une "Merge Request" (MR) sur le dépôt GitLab de F-Droid.

1.  **Créer un compte** sur [GitLab.com](https://gitlab.com) (si nécessaire).
2.  **Forker** le dépôt `fdroiddata` : [https://gitlab.com/fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata).
3.  **Cloner** votre fork localement :
    ```bash
    git clone https://gitlab.com/VOTRE_USER/fdroiddata.git
    cd fdroiddata
    ```
4.  **Ajouter le fichier de métadonnées** :
    Créez un fichier `metadata/com.cardio.fitbit.yml` et collez-y le contenu YAML ci-dessus.
5.  **Commettre et Pousser** :
    ```bash
    git checkout -b add-cardiolens
    git add metadata/com.cardio.fitbit.yml
    git commit -m "Add CardioLens"
    git push origin add-cardiolens
    ```
6.  **Ouvrir la Merge Request** :
    Allez sur votre fork sur GitLab et cliquez sur "Create Merge Request".

## 4. Maintenance

Une fois acceptée :
- **Mises à jour** : F-Droid détectera automatiquement les nouveaux Tags git (ex: `v1.2.4`) grâce à `UpdateCheckMode: Tags`.
- **Fastlane** : F-Droid récupère automatiquement les descriptions et images depuis votre dossier `fastlane/` dans votre dépôt GitHub.

## 5. Vérification Locale (Optionnel)

Si vous voulez tester le build F-Droid chez vous (nécessite Docker ou les outils `fdroidserver`) :
```bash
fdroid build com.cardio.fitbit
```
