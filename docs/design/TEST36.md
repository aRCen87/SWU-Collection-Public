# Passe graphique Test 36

La passe reprend les demandes du 18 novembre, sans changement de l’identité officielle ni du schéma de collection.

| Zone | Réalisation |
|---|---|
| Accueil | Boutons illustrés en colonne, deux actions principales plus grandes, titres alignés, dégradés et cadrage préservant les proportions ; salutation, sauvegarde réelle, reprise du dernier deck et nouveautés illustrées. Les animations et les icônes de l’en-tête existantes sont conservées. |
| Collection | Commandes fixes avec hauteur limitée sur petit écran, trois densités et aperçu immédiat dans la grille ; étoiles, filtre des favoris, quantités et détails accessibles. Favoris enregistrés comme liste personnelle et inclus dans la sauvegarde existante. |
| Decks | Confirmation de sortie compacte et défilable ; résumé avant enregistrement, état valide vert, avertissements de légalité et de dépassement ; filtres actifs retirables et symboles de rareté. Couvertures leader/base, mode concentration, table, réserve et adaptation Fold existants conservés. |
| Échanges | Panneaux distincts Je donne / Je reçois, côte à côte si la largeur le permet ; miniatures des cartes et quantités disponibles. |
| Profil | Carte de profil encadrée et choix de trois badges à placer en tête, en réutilisant l’ordre des badges existant ; aucun champ serveur ajouté. |
| Scanner et cotes | Six dernières miniatures de la session, sans changer la reconnaissance ; date des cotes en cache lorsqu’elle est fournie. |
| Finitions communes | Repère doré du raccourci actif ; surfaces bleu nuit, pages vides, chargements, zooms, variantes, reflets, médailles et réduction des animations existants conservés. |

## Vérification nécessaire avant publication

- Test de la fenêtre de sortie en paysage avec police à 200 % et vérification de son action Continuer.
- Test de conservation des favoris et des quantités à travers sauvegarde et restauration ; les identifiants SQLite de lignes peuvent être réattribués par la restauration existante.
- Ensemble des tests Android, lint, compilation, sécurité, essais téléphone/Fold/paysage et contrôle des fichiers chiffrés signés.
- Enregistrer le paquet vérifié en privé avant de publier son annonce dans le canal Test.
