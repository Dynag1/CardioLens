# Historique des Versions (Changelog)

## Version 36 (v1.2.7) - 2026-01-29
### Corrections
- **Health Connect** : Amélioration de la récupération des données de sommeil et du rythme cardiaque pour le calcul du RHR (élargissement de la plage de recherche).


## Version 34 (v1.2.6) - 2026-01-26
### Améliorations de l'Interface (UI)
- **Thème Sombre** : Correction de l'arrière-plan blanc qui rendait l'application illisible en mode sombre.
- **Pages Tendances & Sauvegarde** : Ajustement complet des couleurs pour le mode sombre (Menu, Graphiques, Arrière-plans).
- **Barre de Statut** : Les icônes de la barre de statut s'adaptent désormais correctement (Noires sur fond blanc pour le thème clair, Blanches sur fond noir pour le thème sombre) pour garantir une visibilité parfaite.
- **Paramètres Santé** : Les paramètres de seuils cardiaques, notifications et date de naissance ont été déplacés dans un nouveau menu dédié "Paramètres Santé" (accessible sous "Tendances") pour une meilleure organisation.

## Version 33 (v1.2.5) - 2026-01-24
### Améliorations Graphiques
- **Visualisation des Pas** : Regroupement des pas par blocs de 10 minutes pour une meilleure lisibilité.
- **Indicateur d'Intensité** : Les barres de pas utilisent un dégradé de couleur (Violet) indiquant l'intensité de l'activité.
- **Infobulle Intelligente** : Le clic sur un bloc de pas affiche le total cumulé pour les 10 minutes.

## Version 32 (v1.2.4) - 2026-01-24
### Corrections
- **Calcul du Pouls Nuit (Night RHR)** : Correction logique pour exclure strictement le sommeil commençant tard le soir même (après 22h) du calcul du jour courant. Prend désormais correctement en compte uniquement le sommeil se terminant le matin.

## Version 25 (v1.1.25) - 2026-01-19
### Corrections
- **Fitbit RHR** : Correction du calcul du pouls au repos (Jour/Nuit). Les données sont maintenant récupérées avec plus de précision et la tolérance aux mouvements a été ajustée pour ne pas bloquer le calcul lors de simples pas.

## Version 24 (v1.1.24) - 2026-01-19
### Interface
- **Détails d'Entraînement** : Les graphiques sont désormais masqués par défaut et ne se chargent qu'au clic pour une meilleure fluidité.
- **Optimisation** : Chargement différé des données détaillées pour économiser de la batterie et améliorer le défilement.

## Version 23 (v1.1.23) - 2026-01-19
### Nouvelles Fonctionnalités
- **Suivi des Symptômes** : Ajout quotidien de symptômes (Malade, Fièvre, etc.) via le Dashboard.
- **Sauvegarde Complète** : Inclusion des symptômes et de l'âge dans les sauvegardes.

### Corrections
- **Optimisations** : Réduction drastique des lags (mémoïsation et mise à jour intelligente des graphiques).
- **Traductions** : Ajout des traductions françaises manquantes.

## Version 22 (v1.1.22) - 2026-01-19
### Modifié
- **Graphique Principal** : Résolution forcée à 1 minute pour une lisibilité optimale (barres pleines).
- **Interface** : Suppression des popups de résumé d'entraînement à l'ouverture des détails.

## Version 21 (v1.1.21) - 2026-01-19
### Nouvelles Fonctionnalités
- **Haute Précision Cardiaque** : Support des données à la seconde pour les détails d'activité.
- **Statistiques de Récupération** : Affichage automatique de la chute de cardio (1min/2min) après l'effort.
- **Graphique Principal Optimisé** : Agrégation intelligente à la minute pour une lisibilité parfaite sur le tableau de bord.
- **Vérification des Permissions** : Bouton pour s'assurer que toutes les données (Distance, Pas) sont accessibles.

### Corrections
- **Affichage Graphique** : Correction de la transparence et de la finesse des barres sur les données haute fréquence.
- **Déploiement** : Correction du nom de version sur le Play Store.

## Version 17 (En cours)
### Fichiers locaux et Cloud
- **Nouveau système de sauvegarde** : Sauvegardez vos données dans n'importe quel dossier (Google Drive, Dropbox, Stockage local) sans configuration complexe.
- **Sauvegardes automatiques** quotidiennes vers le dossier choisi.
- Export manuel direct.

### Améliorations et Corrections
- **Actualisation automatique** : Vos données sont à jour dès l'ouverture de l'application.
- **Correction critique** : Les données d'humeur sont désormais conservées lors des mises à jour de l'application.
- **Correction** : Les entrées d'humeur sont correctement incluses dans les fichiers de sauvegarde.
- Simplification de l'interface "Sauvegarde / Restauration".

## Version 16
### Amélioration du calcul du pouls au repos (RHR)
- **Nuit** : Prise en compte du sommeil complet (y compris si endormissement avant minuit).
- **Jour** : Nouvelle méthode scientifique (Moyenne des périodes stables de 20 min) pour une mesure plus précise.

## Version 15
### Suivi d'humeur amélioré
- Les icônes d'humeur sont maintenant plus discrètes.
- Visualisez votre historique d'humeur directement dans l'onglet "Tendances".
- Corrections mineures et améliorations de performances.

## Version 14
### Nouveautés
- **Suivi de l'humeur** : Notez votre humeur chaque jour avec des émojis !
- **Synchronisation** : L'heure de la dernière synchronisation est affichée en haut.
- **Améliorations** : Calcul de la fréquence cardiaque au repos plus stable et scientifique.
- Corrections de bugs et améliorations de l'interface.

## Version 13
- **Améliorations** : Application du calcul scientifique du RHR (Médiane jour, Moyenne nuit) à l'écran Tendances.
- **Correctif** : Les tendances historiques du RHR correspondent maintenant à la précision améliorée du tableau de bord.

## Version 12
- **Améliorations** : Calcul de la "Fréquence Cardiaque au Repos" plus scientifique.
  - Nuit : Utilise la moyenne de la fréquence cardiaque pendant le sommeil.
  - Jour : Utilise la médiane des périodes de repos (moins sensible aux valeurs aberrantes/siestes).
- **Amélioration** : Compatibilité F-Droid (Licence ajoutée).

## Version 11
- **Correctif** : Erreur "Portée invalide" lors de la connexion en supprimant les permissions non supportées.
- **Correctif** : La valeur de VFC (HRV) "Aujourd'hui" dans Tendances correspond maintenant au tableau de bord (calcul de moyenne).
- **Nouveau** : Ajout des tendances historiques de la Variabilité de la Fréquence Cardiaque (VFC).
- **Amélioration** : Les couleurs du graphique de fréquence cardiaque sont plus dynamiques.

## Version 10
- **Correctif** : Couleurs dynamiques du graphique de fréquence cardiaque (Plus vives pour les fréquences plus basses).
- **Amélioration** : Suppression des logs de débogage pour de meilleures performances.

## Version 7
- **Amélioration** : Écran de bienvenue simplifié V2 (Nouveau design avec un bouton de connexion unique).
- **Correctif** : Graphiques d'activité Fitbit (Correction du problème de format de temps).
- **Amélioration** : Calcul du RHR nocturne amélioré pour Fitbit (Supporte les données de sommeil avant minuit).
- **Amélioration** : Mise à jour du logo de l'application sur l'écran de bienvenue.

## Version 6
- **Correctif** : Affichage du graphique de fréquence cardiaque après des interruptions de données.
- **Amélioration** : Meilleure interface pour les zones de sommeil et d'activité (coins arrondis et bordures pleines).
- **Amélioration** : Réactivation de la sélection Health Connect sur l'écran de bienvenue.
- **Correctif** : Pagination des données de fréquence cardiaque intraday.
