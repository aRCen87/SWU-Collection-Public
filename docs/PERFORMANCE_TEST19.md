# Performance et simplicité — prochaine version Test

Branche de travail : `preview/performance-test19`, issue de Test 18.
Cette branche n'est pas une version distribuée. Aucun numéro de version ni
annonce de mise à jour n'a été modifié.

## Changements

- Mémoriser la recherche de collection, les totaux, les doubles, le tri et les
  groupes jusqu'au changement de leurs données respectives.
- Normaliser les noms anglais à la construction de l'index, et les mots d'une
  recherche naturelle une seule fois par recherche.
- Réutiliser les expressions régulières de normalisation et l'index du catalogue
  dans les lignes de collection, au lieu de rechercher chaque référence dans
  toute la liste.
- Donner la priorité à Scanner dans la collection, nommer l'ajout manuel,
  compter les filtres actifs et permettre d'effacer les recherches en un geste.
- Masquer le choix Visuel/Liste quand le regroupement le rend inopérant.
- Nommer directement la destination sur la confirmation du scanner.
- Afficher aussi la mise à jour Test dans l'aperçu des paramètres.

Les parcours de decks et d'échanges conservent leurs étapes de vérification.
Aucune migration, modification des quantités, des règles de reconnaissance,
des autorisations du compte ou des règles de restauration n'est introduite.

## Validation

- 50 tests Python réussis localement ; syntaxe du scénario Android vérifiée.
- Nouveaux tests de fidélité des résultats, noms/alias/références, ordre,
  langues/variantes, reconstruction de l'index et collection de 10 000 lignes.
- Nouveaux tests du nombre de filtres actifs.
- Validation Android automatisée :
  https://github.com/aRCen87/SWU-Collection-Public/actions/runs/42421812719
  réussie : testDebugUnitTest, lintDebug, assembleDebug et vérification des détails
  du catalogue dans l’APK.
- La compilation locale est interrompue par l'environnement ; ne pas la compter
  comme une validation réussie.

Restent nécessaires avant distribution : essais visuels sur petit écran et
pliant, mesure sur appareil du démarrage, du
défilement et du scan, puis parcours privé de signature et d'annonce prévu dans
AGENTS.md. Aucun pourcentage de gain de vitesse ou d'autonomie n'est revendiqué.
