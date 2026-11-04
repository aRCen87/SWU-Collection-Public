# Cotes Cardmarket

Les libellés français et leurs accents sont conservés exactement tels qu'ils figurent dans le catalogue. Le rapprochement utilise l'extension, le numéro de collection, le type et l'identité bilingue ; il ne rapproche jamais des lignes par leur position. La normalisation de recherche conserve les accents (NFC), mais harmonise la casse et les guillemets. Une dernière vérification du nom français protège l'affichage si le catalogue a changé depuis la création de la table.

La table v2 couvre les 2 549 références standard des onze extensions incluses : SOR 252, SHD 262, TWI 257, JTL 262, LOF 264, IBH 104, SEC 264, LAW 264, TS26 84, ASH 264 et HMW 272. Les données HMW proviennent des quatre pages anglaises de l'API officielle de l'éditeur, associées aux mêmes numéros et types du catalogue français. Les jetons qui réutilisent un numéro ne peuvent plus remplacer une carte.

Les prix sont exclusivement les tendances EUR du guide officiel Cardmarket, toutes langues et hors livraison. La tendance normale et la tendance foil sont lues séparément, y compris lorsqu'elles sont publiées sous différents identifiants de produit. Les moyennes de vente propres à chaque finition permettent d'écarter un identifiant dont la tendance résiduelle correspond à une autre finition. Lorsque plusieurs impressions vérifiées de la même carte restent possibles, l'application affiche une fourchette et l'indique. Une base dont le verso-jeton n'est pas identifié affiche aussi une fourchette explicite.

L'application affiche d'abord la dernière cote enregistrée ou l'instantané livré avec l'APK, avec la date du guide, puis tente une actualisation à la première consultation de la session. Le guide est réutilisé pendant 24 heures ; Actualiser force une nouvelle tentative. Un échec de réseau ou de sauvegarde du cache n'efface pas les prix déjà disponibles. Les noms, fichiers et requêtes sont traités explicitement en UTF-8.

## Limites annoncées dans l'interface

Une correspondance vérifiée de la référence normale n'autorise pas à appliquer son prix à une impression premium, à un numéro foil distinct, à une promo ou à un showcase. Ces références restent signalées comme non reliées ; le bouton Voir Cardmarket reste accessible. Les 5 904 autres références du catalogue ne sont pas présentées comme cotées.

Le guide public peut ne pas publier certaines tendances, même quand le site Cardmarket affiche des offres ou une cote. L'absence dans le guide n'est jamais présentée comme une absence de carte ou de cote sur le site. Dans l'instantané du 31 octobre 2026, les 272 cartes normales HMW ont une tendance exploitable ; le guide de leur édition normale ne fournit pas de tendance foil exploitable. L'application ne substitue pas une cote d'hyperspace, de prestige ou d'une autre impression pour combler ce manque.

## Reconstruction et validation

Depuis la racine du dépôt : `python3 .github/scripts/build_market_map.py --refresh`. Le script met à jour la table et l'instantané avec les exports officiels de produits et de prix Cardmarket, les identités bilingues de Team-Zura/swu-cards-json et les identités HMW de l'API de l'éditeur. Il refuse une pagination HMW incomplète et les associations ambiguës. Inspecter la couverture affichée avant publication. Le dossier temporaire `.market-cache` ne doit pas être commité.

Les six tests Python couvrent les collisions de numéros, l'extension, les impressions, les accents et le refus d'identités ambiguës. Les tests Kotlin contrôlent les régressions Krennic/Chewbacca, les 272 prix normaux HMW, les noms français du catalogue, le choix normal/foil, les bases à plusieurs versos, le leader double face Palpatine/Sidious et la validation des dates et des montants. Aucun de ces tests ne prétend simuler un téléphone réel.
