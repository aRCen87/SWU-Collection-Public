# Cotes Cardmarket — brouillon du 5 novembre 2026

Ces modifications ne sont ni testées, ni intégrées à main, ni publiées. La version stable reste 0.8.10. Le scanner et ses données de reconnaissance restent inchangés. Aucune donnée de collection ni aucun schéma de base de données n'est modifié.

## Correspondances préparées

La table v3 contient 6 318 références sur les 8 453 du catalogue : 2 549 standards existantes, 711 numéros foil distincts et 3 058 références premium supplémentaires. Il reste 2 135 références sans lien. Une référence reliée n'implique pas qu'une tendance positive soit disponible pour chaque finition dans le guide.

Les numéros foil sont associés à la famille officielle de leur carte normale, dans la même extension, puis utilisent exclusivement la tendance foil. Les identifiants de collection restent inchangés. Les références premium utilisent l'extension Extras Cardmarket, le nom anglais complet et l'identité metacard de la carte standard ; les familles comportant plusieurs traitements premium sont exclues. Les prix et l'ordre des identifiants ne servent jamais à reconnaître une variante. Les associations restantes nécessitent une vérification précise sur Cardmarket, surtout pour les promos et les familles comprenant showcase/prestige/serialized.

Chaque finition conserve sa sélection de produits et son champ trend ou trend-foil. Un prix normal ne remplace jamais une tendance premium ou foil absente. Une sélection de plusieurs produits affiche une fourchette explicite ; les bases avec plusieurs versos sont signalées. Les accents et noms français sont contrôlés avant affichage.

Le catalogue et les budgets demandent la finition par défaut de la référence exacte. La valeur de collection utilise toujours la finition enregistrée pour la carte possédée. Aucun remplacement silencieux d'une finition incompatible n'est autorisé.

## Actualisation préparée

Le guide officiel Cardmarket fourni a pour date le 5 novembre 2026. Les tendances EUR concernent toutes les langues et sont hors livraison. L'application affiche les fichiers embarqués ou le cache le plus récent avant de tenter une actualisation. Une erreur réseau ou d'écriture ne supprime pas les données disponibles.

Le workflow market-data préparé actualisera quotidiennement les correspondances depuis les exports publics Cardmarket, les familles et finitions officielles déjà présentes dans les données du projet. Il écrira uniquement les deux fichiers de données dans une branche market-data, sans reconstruire ni publier d'APK. L'application pourra consulter cette table et la conserver en cache pendant la session, avec une tentative au maximum par jour hors actualisation manuelle. Cette alimentation ne sera activée qu'après validation et intégration à main ; elle n'a pas été lancée à ce stade.

Les cas vérifiés manuellement peuvent être ajoutés à .github/cardmarket-overrides.json avec référence, finition, produits exacts, champ, extension, nom anglais et URL Cardmarket documentant le traitement. Le fichier est volontairement vide pour le moment.

## Préparation et contrôles différés

`python3 .github/scripts/extend_market_mapping.py --refresh` régénère la table depuis les liens standards existants. `python3 .github/scripts/build_market_map.py --refresh` reconstruit aussi ces liens standards depuis les identités bilingues et l'API HMW. Les deux chemins conservent le format par finition.

Des cas Kotlin sont préparés pour les numéros foil distincts, les prix premium séparés et la structure de la table. Les contrôles Python de rapprochement, les tests Kotlin, lint, compilation, vérifications de sauvegarde et essais sur téléphone restent à lancer après le prochain point avec Romain. La création de la table est une préparation des données et ne constitue pas une validation applicative.
