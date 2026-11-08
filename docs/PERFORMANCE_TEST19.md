# Performance et simplicité — prochaine version Test

Branche de travail : `preview/performance-test19`, issue de Test 18.
Version privée distribuée : 0.8.19-test.19, code Android 78.
APK récupéré, empreinte/identité/certificat permanent vérifiés, copie privée enregistrée et annonce activée après réussite des contrôles.

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
- En paysage court, réduire les espacements et conserver les réglages d’affichage dans Filtres ; le contenu doit rester visible au-dessus de la navigation.
- Limiter le texte indicatif de recherche à une ligne pour garder une hauteur compacte.
- Afficher aussi la mise à jour Test dans l'aperçu des paramètres.

Les parcours de decks et d'échanges conservent leurs étapes de vérification.
Aucune migration, modification des quantités, des règles de reconnaissance,
des autorisations du compte ou des règles de restauration n'est introduite.

## Validation

- 50 tests Python réussis localement ; syntaxe des scénarios Android vérifiée.
- Tests de fidélité des résultats, noms/alias/références, ordre, langues/variantes,
  reconstruction de l’index et collection de 10 000 lignes.
- Tests du nombre de filtres actifs.
- Contrôle Android du parcours recherche/effacement, activation et réinitialisation
  des filtres, accès aux actions et contenu visible au-dessus de la navigation
  en format fermé, déplié et paysage.
- Validation signée finale : https://github.com/aRCen87/SWU-Collection-Public/actions/runs/42425906628
  réussie : confidentialité, tests Android debug et preview, conservation/restauration, lint, compilation optimisée signée et essais sur émulateur. Les trois captures fermé/déplié/paysage ont été vérifiées visuellement ; le contenu reste visible au-dessus de la navigation.

Sources de l’APK : `f8c6dd2fec4e2f0202b2eef641d6902c54b1b8cc`.
SHA-256 : `25c8dd4a214063a51affbda224dbb201c01e0c46573ec1dc07327c553888a5cb`.

La précision optique du scan, la consommation et les temps de démarrage réels
restent à mesurer sur téléphone. Aucun pourcentage de gain de vitesse ou
d’autonomie n’est revendiqué.

## Tournois et méta

Le flux public a été actualisé puis vérifié après comparaison avec les exports
publics de SWU API : tournois du 7 novembre 2026 présents, 3 267 listes compatibles
(+174), 30 groupes de méta et 338 listes représentatives. Toutes leurs références
sont reconnues par le catalogue de l’application. Certains événements récents
n’ont pas encore de liste exploitable ; ils restent visibles sans liste inventée.

Données publiées : commit `56dd2d67d586739ac2f831fe91e6323afdef485c`
de `competitive-data`, génération du 7 novembre à 23 h 20 UTC (8 novembre à
0 h 20 Paris). Actualisation quotidienne du flux ; l’application garde un cache
jusqu’à six heures et le bouton Actualiser force son retéléchargement.
Ces données sont disponibles sans nouvelle installation de l’APK.
