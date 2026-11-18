# SWU Collection — À faire plus tard

Demandes du 18 novembre 2026. Mise en œuvre de toute la liste autorisée le 18 novembre 2026, suivie d’un contrôle complet puis d’une mise à jour de l’application test uniquement. Bilan historique Test 32 : version 0.9.4-test.32 (code 93) validée et disponible en privé le 18 novembre 2026. Annonce de mise à jour Test publiée et vérifiée. Sources validées : 8d5912dd80308c78dd1ba450ab43aec0032c1c32, branche preview/visual-refresh-test32-layout-fix. La version officielle reste inchangée.

## Deck builder plus intelligent
- Afficher clairement la courbe de coût, les aspects, les unités terrestres et spatiales et les cartes manquantes par rapport à la collection.
- Permettre de toucher une indication pour afficher les cartes concernées.
- Ajouter dans les filtres une recherche par mots-clés, combinable avec les autres filtres.

## Présentation cohérente dans toute l’application
- Toucher une carte ouvre le zoom.
- Balayer permet de passer à la carte suivante.
- Garder les boutons d’ajout au même endroit dans les différentes vues.
- Donner un retour visuel immédiat après chaque ajout pour éviter les doubles appuis.
- Conserver le style bleu et or et une interface simple et lisible.

## Boutons de l’accueil
- Harmoniser les boutons de l’accueil.
- Retravailler les images des deux derniers boutons, moins jolies que celles des autres, pour obtenir la même qualité visuelle et un style cohérent avec les autres boutons.

## Partage d’un deck en image
- Générer une image soignée du deck avec le leader, la base, les visuels des cartes et leurs quantités.
- Permettre de partager facilement cette image dans une conversation ou un groupe.

## Vue adaptée au Fold déplié
- Proposer une vue en deux panneaux : collection à gauche, carte sélectionnée ou deck à droite.
- Permettre de consulter les cartes et construire un deck sans changer constamment d’écran.
- Adapter automatiquement la présentation lors du pliage et du dépliage, en conservant le contexte de navigation.

## Amélioration graphique globale
- Préserver l’identité bleu nuit et or, donner davantage de place aux cartes et alléger les éléments qui les entourent.
- Clarifier la hiérarchie visuelle : titre bien visible, action principale évidente et options secondaires plus discrètes.
- Réserver l’or aux boutons importants, aux sélections et aux éléments remarquables ; affiner les contours ailleurs pour aérer les écrans.
- Conserver le décor spatial immersif à l’accueil ; employer un fond plus sobre derrière les cartes pour faciliter la lecture.
- Placer les quantités dans une petite pastille au même endroit sur chaque carte ; distinguer clairement les quantités possédées de celles présentes dans le deck.
- Présenter chaque deck avec une vignette soignée montrant leader et base, son nom, ses aspects et son nombre de cartes ; afficher les détails à l’ouverture.
- Présenter les filtres sous forme de boutons compacts avec icône et libellé, un état sélectionné très net et un compteur de filtres actifs visible.
- Garder la police du logo pour les titres décoratifs et utiliser une police simple et très lisible pour les menus et informations.
- Prévoir des fondus courts, un léger effet à l’ajout d’une carte et un zoom fluide, sans retarder les actions ; coordonner ces effets avec le retour visuel d’ajout déjà prévu.
- Distinguer visuellement les trois expériences : accueil immersif avec illustrations et animations ; collection comme un classeur visuel où les cartes dominent ; Mes decks comme un espace de construction clair aux informations regroupées.
- Commencer par une maquette de la collection et une maquette de Mes decks pour évaluer le rendu avant de modifier toute l’application.

## Finitions graphiques et confort visuel
- Harmoniser les fenêtres, menus, filtres, confirmations et options : mêmes angles, espacements et styles de boutons.
- Afficher pendant le chargement des emplacements aux proportions des cartes pour éviter les décalages de grille.
- Créer des pages vides avec une illustration discrète et une action claire, par exemple « Créer mon premier deck » ou « Scanner mes premières cartes ».
- Associer symboles et couleurs pour identifier les aspects et les sélections sans dépendre uniquement de la couleur.
- Garder les actions fréquentes près du pouce et afficher les options d’une carte sans masquer son illustration.
- Soigner le cadrage des images des boutons et vignettes : pas d’illustrations étirées ni de personnages coupés.
- Uniformiser les marges, espacements, alignements, hauteurs des boutons et tailles des icônes dans toute l’application.

## Navigation et interactions graphiques
- Réduire progressivement le grand bandeau au défilement pour laisser place à un titre compact et afficher davantage de cartes.
- Permettre de pincer à deux doigts pour agrandir ou réduire les miniatures de la grille.
- Ajouter des séparateurs par extension avec son logo et la progression de collection au-dessus de chaque groupe.
- Afficher un aperçu immédiat de la future barre du bas pendant la personnalisation de ses raccourcis, avant validation de leur ordre.
- Afficher des confirmations discrètes bleu nuit et or avec miniature et possibilité d’annuler, par exemple « Carte ajoutée — Annuler » ; intégrer cette présentation au retour visuel d’ajout déjà prévu.

## Consultation des cartes, accessibilité et sélection
- Proposer une vue plein écran immersive pendant le zoom d’une carte : masquer les commandes et les faire réapparaître au toucher.
- Ajouter un bouton de retournement des leaders recto verso, avec une animation courte pour consulter les deux faces.
- Adapter les menus aux grandes polices du téléphone sans couper les libellés.
- Rendre les statistiques plus visuelles avec des barres de progression et graphiques simples, touchables pour afficher les cartes correspondantes.
- Rendre la sélection multiple bien visible : coche et contour net sur chaque carte sélectionnée, nombre sélectionné affiché et actions regroupées en bas.

## Présentation du scanner et des autres écrans
- Scanner : cadre de détection discret, consigne lisible et miniature de la dernière carte reconnue avec sa quantité ; soigner particulièrement cet écran central pour le rendre plus élégant et facile à comprendre.
- Messagerie : présenter les cartes partagées sous forme de vignettes soignées et cliquables, avec leur nom et leur variante.
- Badges : organiser une galerie par catégories, avec une progression visible pour les badges restant à débloquer.
- Compteur de PV : prévoir deux zones symétriques, des chiffres très lisibles et des commandes utilisables par chaque joueur depuis son côté de la table.
- Erreurs et absence de réseau : intégrer un message clair dans la page avec un bouton « Réessayer » pour comprendre immédiatement la situation.


## Fiche carte, exports et paramètres
- Afficher un aperçu du PDF de proxies ou de l’image du deck avant enregistrement ou partage.
- Afficher des repères de finition (« Foil », « Hyperspace », etc.) toujours au même endroit sans masquer la carte.
- Organiser la fiche carte avec une grande image puis les informations regroupées : collection, cote, variantes et actions.
- Regrouper les paramètres en rubriques avec icônes : compte, affichage, collection et mises à jour.
- Proposer une option pour réduire les animations et effets lumineux tout en conservant le design bleu et or.
- Après réalisation, contrôler la cohérence et le fonctionnement de tous les points de la liste, puis publier une mise à jour sur l’application test.


## Suivi des contrôles
- 63 tests Python réussis, dont la conservation des références HMWP et P26-26 à P26-31 dans l’index des capacités.
- Schéma de base de données et version officielle inchangés.
- Tests unitaires Debug et Test, tests de sécurité, lint et compilations réussis ; run GitHub Actions 44264847407 terminé avec succès.
- Essais d’interface réussis sur téléphone simulé, Fold déplié et paysage : messagerie et clavier, construction de deck, lecture et ajout explicite, défilement, miniatures (3 / 7 / 6 colonnes), conservation des quantités, animations et réduction des mouvements.
- Paquet fr.swucollection.app.test, version 0.9.4-test.32, code 93, certificat permanent et empreinte SHA-256 vérifiés après déchiffrement privé.
- Fichier : SWU-Collection-Test-32.apk ; 64 820 018 octets ; SHA-256 6bca121342eaba8572857a622c3a515f3c3df20f31b1d942d78eb70cefdf940a.
- Annonce privée publiée dans le canal Test ; fichier disponible à l’adresse https://chatgpt.com/library?file_id=libfile_7101652ae28c8191b5c54031d947e153&account_id=personal.
- Contrôles réalisés sur émulateur et inspection des rendus ; essais physiques sur le Z Fold de Romain à réaliser lors de son utilisation.
- À surveiller : le test de rotation du scanner à 180° a échoué dans deux passes précédentes, puis réussi dans la passe complète finale. Le diagnostic des références proposées a été conservé ; aucune correction fonctionnelle du scanner n’est revendiquée.

## Compléments Test 33 — 18 novembre 2026
Statut : réalisation et contrôles réussis ; mise à disposition dans le canal Test. Version 0.9.4-test.33, code 94. Sources : bcf76a346a70a941b32ebf081ba39970b1825849, branche preview/finishes-test33.

- Logos d’extensions intégrés aux séparateurs et disponibles hors connexion ; code exact conservé pour les promotions, logo SWU de secours pour les séries sans logo dédié.
- Quantité affichée à côté de la dernière carte du scanner, calculée selon le contexte de collection, de rafale ou de deck.
- Retournement animé des leaders dans la visionneuse plein écran ; respect du réglage de réduction des animations.
- Police à 200 % : libellés de filtres sur plusieurs lignes, commandes du scanner accessibles, paramètres et cinq raccourcis lisibles avec défilement lorsque nécessaire.
- Rotation du scanner : échantillonnage déterministe avant comparaison, contrôles répétés à 0°, 90°, 180° et 270°. Le problème intermittent signalé dans le bilan Test 32 est couvert par cette correction et ces tests ; confirmation en conditions réelles encore nécessaire.

Validation : run GitHub Actions 44282341704 entièrement réussi ; 63 tests Python, tests unitaires Debug et Test, lint, compilation, conservation des données et essais d’interface. Les 5 contrôles d’accessibilité ont réussi dans chacun des trois formats simulés (téléphone, Fold déplié et paysage), soit 15 essais avec police à 200 %. Captures finales inspectées. Aucun essai physique sur le Z Fold de Romain n’est revendiqué.

Paquet vérifié : fr.swucollection.app.test, certificat permanent conforme, 65070218 octets, SHA-256 cddab71b93947da2e5f67ef2af80ff638f59b1267c4912639990fad0a40911af. La version officielle et le schéma de données restent inchangés. Ce complément clôt les cinq points précisés après Test 32 ; il ne transforme pas la liste historique ci-dessus en garantie d’absence de tout défaut.

Fichier privé Test 33 : https://chatgpt.com/library?file_id=libfile_f3eed7d10a6c81919be0369b01d499f8&account_id=personal. Annonce publiée et relue dans le canal Test après enregistrement du paquet vérifié.


## Clôture Test 34 — 18 novembre 2026

Statut : travaux des Tests 32 et 33 repris, dernières corrections terminées, validation complète réussie et mise à jour publiée dans le canal Test. Version 0.9.4-test.34, code Android 95. Sources : 7536b9b76d5be6da7a8b7a6f14d856036d2171b6, branche preview/visual-refresh-test32-layout-fix.

- Scanner : lecture progressive du catalogue visuel de 20 Mo, sans copie complète du JSON en mémoire ; correction de la cause reproduite de reconnaissances vides sous pression mémoire. Les erreurs graves de mémoire ne sont plus assimilées à un catalogue vide. Formats d’index v1/v2, rotations et intégrité de l’image caméra contrôlés, avec 48 reconnaissances répétées.
- Consultation hors connexion : bouton de reprise d’image déplacé dans un coin pour conserver l’ouverture de la carte par un toucher central.
- Toutes les améliorations Test 33 sont conservées : logos d’extensions, quantité du dernier scan, retournement des leaders, grandes polices et échantillonnage déterministe.
- Validation finale GitHub Actions 44291165790 entièrement réussie : sécurité, 63 tests Python, tests unitaires Debug et Test, lint, compilations et signature. Bilan local : 452 tests Android, zéro échec ni erreur.
- Essais d’interface : vrai clavier ouvert, bouton Envoyer au-dessus du clavier et maintien du clavier après envoi dans les trois formats simulés ; 2 contrôles graphiques, 9 essais de decks et 15 essais d’accessibilité à 200 %. Démarrage et coexistence Test/officielle contrôlés. Les 29 rendus privés sont vérifiés et un échantillon couvrant clavier, decks et accessibilité a été inspecté visuellement.
- Paquet fr.swucollection.app.test, version 0.9.4-test.34, code 95 ; certificat permanent conforme ; 65070222 octets ; SHA-256 c07654a63083b9345a87190a38cdda3f1c081fa37a0bba5964440024126f1c1b.
- Fichier privé enregistré puis annonce Test publiée et relue anonymement sur le flux de mise à jour. Fichier : https://chatgpt.com/library?file_id=libfile_a90043ef5100819188b2a24176e41552&account_id=personal. Commit de l’annonce : 68f711503f825f20db7f67297139f70a52e9f107.
- Version officielle, schéma et données de collection inchangés. Aucun essai physique sur le Z Fold de Romain n’est revendiqué ; la reconnaissance sur de vraies cartes reste à confirmer à l’usage.


## Prochaine passe graphique — ajouts demandés le 18 novembre 2026

À réaliser après Test 34. Ces demandes précisent les orientations précédentes ; elles ne sont pas encore intégrées à l’application.

- Accueil plus aéré : mieux distinguer Collection et Scanner et harmoniser le cadrage et la luminosité des illustrations.
- Collection façon classeur premium : cartes dominantes, fond bleu nuit discret, cadres allégés, groupes mieux espacés.
- Couvertures de decks reconnaissables : leader et base, nom visible, symboles d’aspects et nombre de cartes ; détails à l’ouverture.
- Barre compacte réunissant recherche, filtres et choix d’affichage pour alléger les blocs de commandes.
- Or réservé aux actions importantes et sélections ; bleu clair et blanc pour les informations secondaires avec un contraste lisible.
- Animations courtes : carte rejoignant le compteur après ajout réussi, transitions fluides et reflet doré occasionnel ; respecter la réduction des mouvements.
- Icônes harmonisées : même épaisseur, dimensions et style dans toutes les rubriques.
- Priorité aux maquettes Collection et Mes decks, à présenter avant modification de l’application.
- Fiche carte à onglets Collection, Variantes et Cotes sous une grande image pour limiter la longueur de page.
- Variantes en éventail : versions légèrement superposées, déployées au toucher.
- Repère de défilement par extension pour rejoindre rapidement un set dans une grande collection.
- Mode table pour les decks : regroupement visuel par coût, unités terrestres, unités spatiales et événements.
- Comparaison de deux cartes côte à côte, adaptée notamment au Fold déplié.

- Vue des cartes manquantes en silhouettes pour garder l’apparence d’un classeur à compléter.
- Finitions visibles dans le zoom : léger reflet pour les foils, désactivable, sans gêner la lecture.
- Couvertures personnalisables des decks : choix de l’illustration et de son cadrage.
- Mode concentration pour construire un deck : masquer temporairement les éléments secondaires pour privilégier cartes et quantités.
- Fiche d’extension immersive : illustration du set, logo, progression et accès direct aux cartes.
- Barre de construction fixe en bas : leader, base et total toujours visibles.
- Transition depuis la miniature vers le zoom pour garder ses repères.
- Progression par rareté dans chaque extension.
- Comparaison avant/après une modification de deck : ajouts et retraits distingués, réserve et identités comprises.

Réalisation de toute cette passe et publication Test autorisées par Romain le 18 novembre 2026.
