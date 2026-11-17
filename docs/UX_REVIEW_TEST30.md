# Revue de simplicité — Test 30

Revue du code des destinations et des actions principales le 17 novembre 2026. Les essais automatisés Android complètent cette revue ; ils ne constituent pas une utilisation manuelle de chaque fonction sur un vrai téléphone.

| Parcours inspecté | Décision |
| --- | --- |
| Accueil, recherche, barre basse et menu | Garder les raccourcis utiles vers les mêmes destinations ; regrouper le menu en compte, collection/jeu et outils. Libellé Mon compte. |
| Collection et catalogue d’extensions | Garder la vue unifiée et les liens historiques. Retirer le deuxième choix d’extension dans les filtres. Remplacer les onglets de possession répétés par Uniquement mes doublons. |
| Scanner et lot à vérifier | Garder ouverture directe, validation explicite et reprise du lot ; pas de nouvelle page intermédiaire. |
| Bibliothèque de decks et brouillons | Toucher le deck pour consulter, Modifier pour construire. Retirer Consulter en double ; exporter et épingler disponibles dans le menu du deck. |
| Consultation d’un deck | Nouvelle hiérarchie : progression, leader/base, principal, réserve. Miniatures compactes et quantités superposées ; gestion des quantités au menu de la carte. |
| Ajout de cartes aux decks/listes | Grandes cartes plein écran, lecture et ajout distincts, filtres communs. Les refus sont affichés sans confirmation de réussite. |
| Visionneuse | Une visionneuse commune aux deux parcours, précédente/suivante, zoom, recentrage et retour au catalogue/deck sans modifier les quantités. |
| Listes de cartes | Conserver leur distinction avec les decks : favoris et cartes recherchées, sans imposer des règles Premier. |
| Proxies et export | Choisir des cartes / À imprimer remplace Catalogue / Sélection. Export reste accessible depuis le deck ; dimensions d’impression et confirmations conservées. |
| Profil, membres, messagerie et échanges | Membres remplace Joueurs dans le profil. Vitrines favorites distingue les profils favoris des cartes favorites. Discussions et membres restent dans leur espace existant. |
| Sauvegardes, paramètres, mises à jour, hors connexion | Accès aux sauvegardes du profil dirigé vers la page commune ; sauvegarde du compte et fichier restent distincts selon leur usage. Les images hors connexion restent un outil séparé des sauvegardes de données. |
| Gestion de collection, prêts, historique | Modifier par lot remplace Sélection. Prêts, échanges et annulation gardent leurs fonctions et confirmations distinctes. |
| Badges, statistiques, méta et tournois | Conserver ces fonctions différentes ; les raccourcis de l’accueil ne créent pas de deuxième parcours de données. |

Validation attendue : tests de conservation/restauration, tests unitaires Debug et Preview, lint et compilation ; essais de grille de collection existants ; nouveaux essais de consultation, navigation et zoom, ajout explicite, recherche et refus d’ajout sur téléphone, écran déplié et paysage. Captures de fixture chiffrées pour contrôle privé.
