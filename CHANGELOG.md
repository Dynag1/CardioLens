# Historique des Versions (Changelog)

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
