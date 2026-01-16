# Configuration du Déploiement Play Store

Pour que le déploiement automatique fonctionne via GitHub Actions, vous devez configurer plusieurs "Secrets" dans votre dépôt GitHub.

Allez dans **Settings** > **Secrets and variables** > **Actions** > **New repository secret**.

## Comment obtenir le fichier JSON (Service Account) ?

Comme le menu "Accès à l'API" semble invisible sur votre compte, nous allons utiliser la méthode moderne (Cloud Console + Invitation).

### Étape 1 : Créer le compte sur Google Cloud
1.  Allez directement sur **Google Cloud Console** : <https://console.cloud.google.com/>
2.  Créez un **Nouveau Projet** (en haut à gauche).
3.  Dans la barre de recherche en haut, tapez `Google Play Android Developer API` et cliquez sur le résultat.
    *   Cliquez sur **ACTIVER** (Enable).
4.  Allez dans le menu **IAM et administration** > **Comptes de service** (Service Accounts).
5.  Cliquez sur **+ CRÉER UN COMPTE DE SERVICE**.
    *   Nom : `github-deploy` (par exemple).
    *   Cliquez sur **Créer et continuer**.
    *   **Rôle** : Choisissez `Utilisateur du compte de service` (Service Account User).
    *   Cliquez sur **Terminé**.
6.  **IMPORTANT :** Copiez l'adresse email du compte de service qui vient d'être créé (elle ressemble à `github-deploy@votre-projet.iam.gserviceaccount.com`). Vous en aurez besoin à l'étape 3.

### Étape 2 : Créer la clé JSON
1.  Toujours dans la liste des comptes de service, cliquez sur les 3 petits points à droite de votre ligne > **Gérer les clés**.
2.  Cliquez sur **Ajouter une clé** > **Créer une nouvelle clé**.
3.  Choisissez **JSON** et validez.
4.  **Le fichier se télécharge.** C'est ce fichier qu'il faudra mettre dans le secret GitHub `SERVICE_ACCOUNT_JSON`.

### Étape 3 : Donner les droits dans la Play Console
1.  Retournez sur la **Google Play Console** : <https://play.google.com/console>
2.  Allez dans **Utilisateurs et autorisations** (Users and permissions).
3.  Cliquez sur **Inviter de nouveaux utilisateurs**.
4.  Dans "Adresse e-mail", collez l'email du compte de service copié à l'étape 1 (`...@...iam.gserviceaccount.com`).
5.  Dans les autorisations :
    *   Onglet **Autorisations de l'appli** : Ajoutez votre application `CardioLens`.
    *   Cochez **Admin** (ou a minima : *Publier sur les tracks de production*, *Gérer les releases*).
6.  Cliquez sur **Inviter l'utilisateur**.

---

## Liste des Secrets à Ajouter

### 1. `SERVICE_ACCOUNT_JSON`
C'est le contenu du fichier JSON que vous avez téléchargé depuis la Google Cloud Platform.
- Ouvrez le fichier JSON avec un éditeur de texte.
- Copiez tout le contenu.
- Collez-le dans la valeur du secret.

### 2. `KEYSTORE_BASE64`
C'est votre fichier `release-key.jks` encodé en base64 pour pouvoir être stocké comme texte.

**Commande pour obtenir la valeur :**
Exécutez cette commande dans votre terminal (à la racine du projet) :
```bash
base64 -w 0 app/release-key.jks
```
Copiez toute la longue chaîne de caractères qui s'affiche et collez-la dans la valeur du secret.

### 3. `KEYSTORE_PASSWORD`
Le mot de passe de votre keystore.
- Valeur actuelle (par défaut) : `cardio2024`

### 4. `KEY_ALIAS`
L'alias de votre clé.
- Valeur actuelle (par défaut) : `cardio-alias`

### 5. `KEY_PASSWORD`
Le mot de passe de la clé.
- Valeur actuelle (par défaut) : `cardio2024`

---

> [!IMPORTANT]
> Une fois ces 5 secrets ajoutés, le workflow `Play Store Deployment` pourra s'exécuter correctement lors de la création d'une Release GitHub ou manuellement via l'onglet "Actions".
