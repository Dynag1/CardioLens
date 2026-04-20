# Justification pour Connexion Santé (Health Connect) - CardioLens

Ce document contient les informations nécessaires pour remplir la déclaration dans la Google Play Console et corriger le refus du 7 avril 2026.

## 1. Description du fonctionnement (User Experience)

CardioLens est un tableau de bord de santé global. L'application demande l'accès à Health Connect pour :
1.  **Récupérer les données** (Cœur, Sommeil, Activité) pour les centraliser localement.
2.  **Afficher des graphiques interactifs** (Intraday) permettant à l'utilisateur de voir sa réponse cardiaque heure par heure.
3.  **Calculer des corrélations** entre l'intensité de l'activité physique (Calories/Distance) et la récupération (VRC, SpO2).

## 2. Permissions et Justifications (Excessive Data Access)

| Type de Donnée | Permission | Utilisation Critique |
| :--- | :--- | :--- |
| **Cœur** | `READ_HEART_RATE` | Affichage des graphiques 24h/24 et calcul du pouls au repos (Repos N/J). |
| **VRC** | `READ_HEART_RATE_VARIABILITY` | Utilisé dans l'onglet **Tendances** pour afficher l'évolution de la récupération physique sur 30 jours. |
| **SpO2** | `READ_OXYGEN_SATURATION` | Affichage des niveaux d'oxygène dans l'onglet **Sommeil** pour détecter les baisses nocturnes. |
| **Pas** | `READ_STEPS` | Suivi de l'activité quotidienne. |
| **Calories Actives** | `READ_ACTIVE_CALORIES_BURNED` | Calcul de la dépense énergétique pour chaque session de sport. |
| **Sommeil** | `READ_SLEEP` | Analyse détaillée des phases (Léger, Profond, REM) sur le tableau de bord. |
| **Exercices** | `READ_EXERCISE` | Identification des sessions de sport pour permettre un zoom sur le cardio durant l'effort. |

> [!IMPORTANT]
> **Réduction du périmètre de données (Correction du 20 avril)**
> Nous avons supprimé les permissions `READ_DISTANCE` et `READ_TOTAL_CALORIES_BURNED` car elles étaient jugées "excessives" par Google. L'application utilise maintenant uniquement les calories actives et les pas.

### 2.1 Avertissement Médical (Health Content Policy)
Pour respecter la politique sur les contenus de santé, l'application et sa fiche Play Store incluent désormais un **Avertissement Médical** explicite indiquant que l'app n'est pas un dispositif médical.

## 3. Actions recommandées pour la nouvelle soumission

### A. Mettre à jour les Captures d'Écran (CRITIQUE)
Google rejette pour "Accès excessif" s'il ne voit pas la donnée dans les images du Play Store.
*   **Action** : Ajoutez impérativement une capture d'écran montrant l'onglet **Tendances (VRC)** et une montrant l'onglet **Sommeil (SpO2)**.
*   Si la donnée n'apparaît pas dans la capture, Google considérera que vous n'en avez pas besoin.

### B. Vidéo de Démonstration
La vidéo jointe à la déclaration DOIT montrer :
1.  **L'écran de divulgation** (HealthConnectPermissionsScreen) avant l'apparition de la popup système.
2.  L'utilisateur acceptant les permissions.
3.  **Les données s'affichant réellement** (Cœur, Pas, mais aussi HRV et SpO2 si possible).

### C. Déclaration Data Safety
*   **Collecte** : OUI (votre application lit les données).
*   **Partage** : NON (très important de préciser qu'aucune donnée n'est partagée avec des tiers).
*   **Stockage** : Local uniquement.

---
*Ce document sert de guide interne pour le remplissage des formulaires Google Play.*
