# Constructeur de decks et échanges de fichiers

## Fonctionnement
Mes listes contient Mes decks et les listes de favoris. Le constructeur actuel cible Premier : un leader, une base, deck principal et réserve. Les brouillons incomplets sont enregistrables. Les contraintes particulières imprimées sur les cartes restent à vérifier ; le récapitulatif distingue les points généraux contrôlés et les informations inconnues.

Les quantités de collection restent indépendantes des decks. Les variantes et réimpressions identifiées par CardDetails partagent le stock et la limite de copies. Le récapitulatif indique les manquantes, leader et base compris, sans compter deux fois le stock pour le principal et la réserve.

## Sauvegarde
Les decks résident dans une table additive de swu_collection.db, schéma 9. La sauvegarde globale version 5 inclut decks, listes, collection et objectifs. La restauration est validée avant écriture et utilise une transaction commune. Les sauvegardes antérieures restent acceptées ; l’absence de decks dans une ancienne sauvegarde conserve les decks actuels. La fusion conserve les decks existants portant le même identifiant.

Les anciens decks de développement dans swu_decks.db sont importés une seule fois. Ce fichier reste présent pour récupération. Une suppression ou restauration ne réimporte pas ensuite les decks anciens.

## Formats
JSON : metadata.name/author, leader/base avec id/count, deck et sideboard avec id/count. Identifiants externes SET_001 ; les deux séparateurs sont acceptés en entrée. Les quantités sont entières. Un second leader est rejeté explicitement plutôt qu’ignoré.

Texte : sections Leaders, Base, Deck et Sideboard ; lignes quantité | nom anglais | sous-titre. Les noms français complets et les références explicites sont aussi acceptés à l’import dans l’application. Les noms ambigus demandent une référence précise. Le texte destiné au convertisseur SWUBase utilise les noms anglais disponibles.

## Références de compatibilité
Structure comparée au code public des exports et imports SWUBase :
- https://github.com/the-medo/swu-collection/blob/main/server/lib/decks/deckExport.ts
- https://github.com/the-medo/swu-collection/blob/main/server/lib/decks/deckBuilders.ts
- https://github.com/the-medo/swu-collection/blob/main/frontend/src/components/app/tools/DeckFormatConverter/DeckFormatConverter.tsx
- Convertisseur public : https://swubase.com/tools/deck-format-converter

Référence Karabast pour le parcours d’import par deck SWUDB : https://github.com/SWU-Petranaki/SWUOnline/blob/main/JoinGameInput.php

L’application produit et lit des fichiers. Elle ne publie pas automatiquement de deck dans un compte externe et ne génère pas de lien de deck hébergé. La conformité des structures est testée localement dans les tests JVM ; aucun import authentifié de compte externe n’est revendiqué.

## Vue illustrée et destinations (0.6.2)
Le constructeur propose Cartes et Liste, avec vignettes, quantités, stock et manquantes. Les images ouvrent le visualiseur existant et les leaders peuvent être retournés. Les decks de la bibliothèque montrent le leader et la base.

Melee : les sections et séparateurs suivent les exemples du formulaire officiel : https://help.melee.gg/docs/how-to-create-a-decklist-with-melees-decklist-builder/. Le bouton copie les noms anglais vérifiés puis ouvre https://melee.gg/decklist/form ; l’utilisateur colle dans Text Editor, convertit et vérifie avant soumission. Les noms inconnus sont refusés explicitement. Le formulaire authentifié n’a pas été soumis lors de la validation.

Karabast : https://karabast.net/ accepte actuellement le JSON directement dans son champ « Deck link / OR paste deck JSON directly ». Le format est reconnu dans le formulaire public. Le bouton de l’appli copie le JSON puis ouvre cette page ; aucune partie n’est lancée et aucun compte n’est modifié. Le site ne permet pas de conserver un deck JSON comme un lien de deck enregistré : le fichier JSON peut être conservé depuis l’appli et recollé lors d’une prochaine utilisation.

Les données anglaises sont désormais ancrées sur l’image d’impression ou le titre et sous-titre français du catalogue. Les numéros promotionnels réutilisés au sein d’une extension ne peuvent plus écraser une identité classique.

## Validation
Tests de conservation du stock et des listes lors de l’édition des decks, restauration globale, annulation complète en cas d’erreur SQLite, migration depuis le schéma 8 et depuis le stockage de développement, ambiguïtés de noms, accents, JSON et texte. Le smoke test de l’APK optimisé ouvre le constructeur, enregistre un deck et le retrouve après arrêt et relancement de l’application.

## Parcours et filtres (0.6.3)
Organisation inspirée du constructeur officiel https://starwarsunlimited.com/fr/deck-builder : nom à la création, choix explicite du leader et de la base, catalogue et sélection, deck et réserve. Les identités d’amorçage internes ne sont pas choisies pour l’utilisateur : sauvegarde et export restent désactivés jusqu’au choix explicite des deux cartes. Les brouillons dont le principal est incomplet restent enregistrables ensuite.
Recherche sans accents, filtres de catalogue par affinité, type, coût, extension, arène, trait, rareté et stock par famille ; tri par nom, coût ou numéro. Les filtres sont appliqués avant le regroupement des impressions. Le bouton de déplacement transfère un exemplaire entre principal et réserve sans changer le stock, sans dépasser dix cartes en réserve. Effacer demande confirmation et ne modifie le deck sauvegardé qu’après Enregistrer. Le nom reste modifiable et l’état d’édition est conservé pendant un aperçu et une rotation.

## PDF et proxies
PDF illustré accessible depuis Exporter, avec images et quantités des quatre zones. Proxies accessible depuis Mes listes ou Exporter : sélection par carte et quantité mémorisée localement, sans toucher aux decks ni au stock. Export natif PDF A4 (neuf cartes par page), format physique 63 × 88 mm par défaut, option 63,5 × 88,9 mm, repères de découpe et mention PROXIES. Les visuels horizontaux sont tournés dans les emplacements de carte. Les faces arrière optionnelles sont imprimées comme des cartes séparées, sans revendication de recto verso aligné. Impression à 100 % / taille réelle.
Le PDF est préparé dans un fichier temporaire avant le choix d’emplacement Android ; une image non disponible ou une erreur ne produit pas silencieusement une carte blanche. Les images téléchargées par Coil sont mises en cache. Un document contient jusqu’à 360 cartes/faces et le plan de page vérifie cette limite avant allocation. Les dimensions, pagination, quantités, faces et quatre zones sont testées.

## Assistance de construction (0.7.0)
Analyse pondérée des quantités du principal ou de la réserve, coûts inconnus et références absentes explicités. Les affinités répétées sont prises en compte par multiplicité dans le filtre facultatif ; les cartes sans donnée connue restent accessibles dans le catalogue complet. Ce repère ne remplace pas les règles particulières des cartes. Le tri chronologique des extensions reste prioritaire.
Le budget utilise les tendances normales exactes de chaque impression sélectionnée, sans choisir arbitrairement une version moins chère. Fourchettes, nombres de copies non cotées, date la plus ancienne du guide utilisé et cache sont visibles. Les manquantes consomment une seule fois le stock par famille (leader/base, principal puis réserve) ; cette allocation préremplit les proxies et reste modifiable.
Les brouillons atomiques locaux sont séparés des decks validés, avec identifiant de deck et empreinte textuelle de la version enregistrée. Une version enregistrée différente ne reçoit pas un ancien brouillon. La reprise demande confirmation, Annuler garde jusqu’à trente états pendant l’édition, Enregistrer efface le brouillon après réussite. Les brouillons sont propres à ce téléphone et ne changent pas le format de sauvegarde globale de la collection.
L’aperçu ouvre le fichier PDF réellement préparé via PdfRenderer sur un thread de travail, une page à la fois. Les dimensions du fichier ne changent pas avec le zoom d’écran ; l’enregistrement se fait après aperçu dans le sélecteur Android habituel.
