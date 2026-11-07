## Test 0.8.19-test.12

- Correctifs pliants : fiche conservée lors du passage en paysage ; fenêtre et aperçu caméra rétablis après changement de format, en conservant la session de scan. Tests Android, sécurité, lint, compilation et essais émulateur réussis ; essai du vrai Z Fold à réaliser sur le téléphone.

- Fiche unique par carte avec miniatures des versions, langues et quantités ; regroupement activé une fois par défaut, réimpressions reconnues incluses, sans fusionner les stocks.
- Sauvegarde automatique privée du compte vérifié, modifications regroupées et copie vérifiée sur le serveur. Pause en cas de sauvegarde distante différente ou de changement de compte ; restauration et choix de conserver ce téléphone confirmés.
- Alertes de messages privés et demandes d’amis par vérification Android périodique (15 minutes minimum, retards possibles), réglages séparés et test local. Les textes de messages restent masqués sur l’écran verrouillé.
- Échanges : miniatures, recherches de chaque joueur, doublons partagés et correspondances détaillées ; brouillon modifiable avant envoi.
- Validation des changements de fenêtre avec recherche/clavier ouvert, fiche et carte suivante/précédente conservées, scanner redimensionné. Liste de vérification du vrai téléphone dans Paramètres → Vérifier mon écran pliant.

## Test 0.8.19-test.11

- Correction de l’ouverture des mises à jour privées : page du fichier dans le navigateur normal, sans appel direct au téléchargement qui provoquait le message « Cross-site file URL requests are not allowed ».
- Les indications précisent de se connecter à son compte ChatGPT puis de télécharger depuis la page privée. Copie de récupération conservée avant l’ouverture.
- Conserve le bandeau scintillant, les transitions entre cartes et l’activation automatique des comptes vérifiés.

## Test 0.8.19-test.10

- Transition courte avec glissement et fondu entre les cartes, dans le sens de navigation, sans refermer la fiche agrandie.
- Scintillement indépendant des lumières de la ville et pulsation des éclairages du Faucon Millenium, selon l’aperçu approuvé.
- Animation limitée à l’accueil Test, suspendue en arrière-plan et désactivée avec les animations/reflets.
- Inclut l’activation automatique des comptes après confirmation de l’e-mail ; les comptes désactivés restent bloqués.

## Test 0.8.19-test.9

- Inscription autonome : après confirmation de l’e-mail, le compte est activé automatiquement. Aucune approbation manuelle de chaque nouveau joueur n’est nécessaire.
- Un compte désactivé par l’administrateur ne peut pas se réactiver lui-même. Les sauvegardes restent privées et les blocages et paramètres de messagerie sont conservés.

## Test 0.8.19-test.8

- Grand écran : collection et fiche côte à côte ; catalogue et sélection du deck visibles simultanément sur les fenêtres larges (720 dp pour la collection).
- Compte : sauvegardes privées manuelles, vérification d’intégrité, aperçu avant restauration et copie de récupération locale. Une connexion reprend les données présentes sans restauration automatique.
- Amis : partage volontaire des seuls doublons proposés et recherches, suggestions d’échanges puis proposition modifiable à confirmer.
- Accueil : ordre des raccourcis personnalisable, extension épinglée et conservation des decks épinglés dans la sauvegarde du compte.
- Scanner : correction rapide d’un exemplaire après scan, en rafale, dans le récapitulatif ou dans le brouillon d’un deck, sans quitter la caméra.

## Test 0.8.19-test.7

- Bandeau : proportions conservées même si la fenêtre impose une hauteur supplémentaire ; les animations suivent la même échelle.
- Validation des pliants : centrage contrôlé dans la fenêtre réellement disponible, après les marges système, plutôt que dans la résolution demandée à l’émulateur. Boutons de 48 dp vérifiés dans cette même zone.

## Test 0.8.19-test.6

- Accueil : nouveau bandeau validé avec planète, ville galactique et Faucon Millenium. Avatar, menu et courrier restent de vrais boutons ; aucun ancien menu ne dépasse derrière l’avatar.
- Écrans pliants : accueil centré avec largeur maîtrisée, illustrations des boutons recadrées sans étirement, pages de collection et decks adaptées à l’espace disponible, menu latéral et navigation moins larges sur écran déplié. Redimensionnement contrôlé en formats fermé, déplié et paysage.

## Test 0.8.19-test.5

- Accueil : avatar seul en haut à droite, menu en dessous et courrier sous le menu. Suppression du bandeau de profil supplémentaire.
- Correction du bandeau des mises à jour : les nouvelles versions Test sont maintenant affichées en haut de l’application.
- Le clic sur la notification ou le bandeau Test lance directement la demande de téléchargement privé, après sauvegarde, sans devoir appuyer sur un second bouton dans Mises à jour.
- Le fichier reste réservé au compte ChatGPT du destinataire ; une authentification dans le navigateur peut être demandée. Android demande toujours la confirmation d’installation.

## Test 0.8.19-test.4

- Avatar et pseudo accessibles depuis l’accueil, avec pastille pour les messages et demandes.
- Amis : recherche parmi les joueurs visibles, demandes à accepter/refuser, retrait et code QR.
- Galerie de 16 portraits recadrés depuis les illustrations des cartes.
- Confidentialité des messages : joueurs du test, amis uniquement ou personne.
- Decks publiés volontairement sur le profil, aperçu avec miniatures et copie dans Mes decks.
- Propositions d’échange entre amis avec quantités, langues et variantes, acceptation/refus/annulation et accès à la messagerie.
- Les collections locales ne sont jamais modifiées par une demande d’ami, un partage ou une acceptation d’échange.

## Version privée 0.8.19-test.3 (code 62)

- Notifications de mise à jour propres à l’application Test, à l’ouverture et périodiquement en arrière-plan selon les autorisations Android.
- La notification ouvre Mises à jour ; le bouton mène au téléchargement privé dans le compte ChatGPT du destinataire. Une copie de récupération est créée avant son ouverture.
- Les annonces Test sont séparées du flux officiel. Une même version n’est signalée qu’une fois ; une version déjà installée ou antérieure n’est pas proposée.
- Ce mécanisme nécessite une première installation de cette version Test. Le téléchargement privé s’ouvre dans le navigateur, et non dans l’installateur intégré de l’application officielle.

## Version privée 0.8.19-test.2 (code 61)

- Comptes de test par e-mail et mot de passe, confirmation de l’adresse et accès réservé aux comptes autorisés.
- Profil avec pseudo, bio et avatar modifiable : photo réduite ou avatar prédéfini. Affichage du profil et des badges au choix du joueur.
- Conversations privées, blocage de joueurs et partage d’une copie de deck à consulter avant enregistrement.
- Collection et decks locaux conservés. Aucune synchronisation cloud des collections ni notification de messagerie téléphone fermé dans cette version.
- Application Test séparée de l’application officielle ; cette version ne modifie pas sa distribution.

## Version 0.8.19

- Bandeau d’accueil : une lumière dorée suit le contour de chacune des lettres S, W et U, pendant le passage du reflet métallique, selon l’aperçu validé.
- Le bandeau d’origine, ses illustrations et les animations existantes sont conservés. Le nouvel effet utilise le même rythme que le reflet, respecte la désactivation des animations et s’arrête lorsque l’accueil est inactif.
- Cette version reprend aussi les améliorations 0.8.18 des fiches regroupées, des brouillons de decks et des rafales du scanner décrites ci-dessous.

## Version 0.8.18

- Fiche carte : versions, finitions et réimpressions réunies avec miniature, cote et quantités par langue. Choisir une version conserve la navigation précédente/suivante de la liste d’origine.
- Les finitions proposées correspondent à la référence exacte. Les anciens exemplaires enregistrés restent visibles ; une finition incompatible ou une langue inconnue ne peut plus être ajoutée depuis la fiche.
- Créer un deck depuis une carte conserve cette carte, qu’il s’agisse d’une unité, d’un événement, d’une amélioration, d’un leader ou d’une base. Cette action reste disponible même avec des decks existants ; chaque création a son propre brouillon.
- Lots du scanner : contrôle de compatibilité de la finition avant validation et correction possible des anciens lots incompatibles. Les scans vers un deck refusent un dépassement de 9 999 exemplaires sans perdre les ajouts en attente.
- Comptage des versions dans la collection corrigé : deux langues d’une même référence et finition ne comptent plus comme deux versions. Aucune migration de base ni modification des quantités existantes.

## Version 0.8.17

- Boutons d’accueil : léger enfoncement pendant l’appui et retour souple au relâchement, inspirés de l’aperçu validé.
- Un éclat doré parcourt brièvement leur bordure au toucher. Les visuels, titres et destinations restent conservés, ainsi que le reflet lié à l’inclinaison du téléphone.
- Les effets respectent la désactivation des animations Android, ne tournent pas en continu et ne retardent pas l’ouverture des pages. Un appui annulé en faisant défiler la liste arrête l’éclat sans ouvrir de page.

## Version 0.8.16

- Scanner : une langue momentanément illisible ne fait plus recommencer la confirmation d’une même référence. Deux lectures réelles de la langue restent nécessaires ; une langue contradictoire, une autre référence ou une interruption annule la confirmation partielle.
- Lecture du bas de carte : lorsqu’une référence est repérée dans une zone agrandie, sa seconde vérification est rapprochée. La recherche sans résultat conserve son espacement pour limiter la charge caméra.
- Rafales : atteindre le maximum de 9 999 exemplaires n’efface plus la ligne lors d’un nouvel appui sur +. Le lot enregistré reste intact en cas de dépassement.
- Conservation des données : ajout d’un contrôle de migration directe du tout premier schéma vers le schéma actuel, avec références, finitions, quantités et accents conservés, puis export et restauration.
- Miniatures du récapitulatif, séparation des langues et finitions et correspondances Cardmarket existantes conservées. Audit des sources de cotes : 6 318 références reliées sur 8 453 ; les correspondances ambiguës restent explicitement indisponibles.

## Version 0.8.15

- Collection, versions regroupées : un seul en-tête par numéro et finition, puis une ligne par langue avec cote et boutons −/+.
- Les langues partagent l’affichage d’une version, mais conservent leurs quantités et leur visuel propres. Les finitions et références différentes restent séparées.

## Version 0.8.14

- Scanner simplifié : destination collection par défaut et nom du deck affiché explicitement lors des scans vers un deck existant ou nouveau.
- Modes « Une par une » et « Rafale » visibles sous la destination. En mode individuel, la reconnaissance attend Valider ou Annuler sous la caméra ; annuler relance la lecture sans ajout.
- Miniature, référence, langue, finition et cote Cardmarket de la carte reconnue sous la caméra. Cote issue du cache disponible, avec indication lorsqu’elle manque ou que la finition reste à confirmer.
- Récapitulatif illustré des rafales collection avant validation ; les scans vers un deck restent vérifiables dans son brouillon avant enregistrement. Option d’ajout simultané à la collection conservée.
- Accueil : logo Star Wars Unlimited doré sur les dos de cartes et boosters des boutons Scanner, Extensions et Mes decks, avec les illustrations bleu nuit et or conservées.

## Version 0.8.13

- Accueil : nouvelles illustrations réalistes pour Badges et Tournois & Méta, assorties aux autres boutons. Médailles métalliques, trophée et cartes sur fond spatial bleu nuit et or ; titres natifs et disposition conservés.

## Version 0.8.12

- Tournois & Méta s’affiche directement dans l’application : sélection de listes Premier récentes avec leader, base, miniatures, événement et classement final lorsque la source le fournit.
- Actions Copier et Enregistrer visibles sur chaque deck ; export complet avec réserve, copie indépendante dans Mes decks et comparaison avec la collection. Un deck déjà enregistré est signalé.
- Classement par présence parmi les listes complètes reconnues des 60 derniers jours, sans assimiler cette présence à un taux de victoire. Source, période, date et couverture visibles.
- Sélection SWU API issue des exports publics documentés, sans clé dans l’APK, actualisée quotidiennement. Dernière sélection disponible conservée hors connexion ; import manuel toujours accessible dans Mes decks.

## Version 0.8.11

- Nouvel espace « Tournois & Méta » : accès aux résultats compétitifs, aux classements, aux listes publiées et aux analyses à jour sur SWUBase, dans le navigateur. La sélection de saison, de région, de niveau et de période reste sur la source.
- Import JSON/fichier ou collage d’une liste exportée : aperçu illustré, leader, base, deck principal, réserve et cartes manquantes calculées avec la collection. Copie JSON et enregistrement indépendant dans Mes decks ; aucun ajout de cartes à la collection.
- Depuis cet espace, comparaison des decks enregistrés avec la collection et filtre « Toutes les cartes possédées ». Quantités cumulées par famille reconnue, variantes, langues et rééditions comprises ; références inconnues signalées.
- Les cartes manquantes apparaissent également dans la bibliothèque Mes decks. Budget et analyse accessibles depuis l’aperçu importé ; avertissements de légalité conservés pour les anciennes listes de tournoi.
- Accueil bleu et or : illustration dédiée de trophée et cartes pour Tournois & Méta, et trois médailles pour Badges, qui remplace le portrait de personnages sur ce bouton. Accès au nouvel espace dans le menu.
- Cotes Cardmarket : table par finition couvrant 6 318 références au lieu de 2 549, dont 711 numéros foil distincts et 3 058 références premium supplémentaires. Certaines tendances restent absentes du guide et 2 135 références restent sans correspondance fiable.
- Actualisation des correspondances Cardmarket en plus du guide de prix, avec cache local conservé en cas d’échec réseau. Mise à jour quotidienne des fichiers de données dans une branche séparée, sans nouvelle installation de l’application.

## Version 0.8.10

- Scanner : lecture séparée du titre et du bas de carte en cas de référence illisible, en conservant les détails de l’image caméra. Recherche automatique des formats portrait et paysage.
- Comparaison visuelle affinée sur les candidats les plus proches : grille plus détaillée et comparaison des contours pour mieux départager des illustrations similaires.
- Croisement du titre, de l’image, du numéro et de la langue imprimée. Les titres français et anglais peuvent aider à départager des références proches, sans imposer une langue ou une finition à partir du visuel.
- Deux lectures cohérentes restent nécessaires ; les contradictions, pauses et doubles lectures restent protégées. Les contrôles supplémentaires sont espacés et réservés aux lectures incertaines.

## Version 0.8.9

- Reconnaissance croisée image/texte : deux lectures cohérentes de la référence et de la langue sont exigées ; un changement de langue, une lecture contradictoire ou une pause annule la lecture partielle.
- Mise au point automatique régulière sur la carte, avec mise au point au toucher conservée.
- Lecture en série par défaut et analyse plus rapprochée, avec protection contre les doubles lectures conservée.
- Bouton « Terminer » visible pour passer au récapitulatif du lot ou retrouver son deck avant enregistrement.
- Destination visible dans le scanner : collection par défaut, nouveau deck, deck enregistré ou brouillon.
- Depuis le scanner de deck, retour possible au scan collection ; les ajouts collection du deck restent optionnels.
- Les cartes déjà scannées restent dans leur destination initiale lors d’un changement de destination.
- Aide à la reconnaissance par l’image, sur le téléphone (8 453 références indexées) : lorsque la référence reste illisible, croisement de l’illustration et des fragments de référence lisibles. L’illustration seule ne détermine jamais la langue ou la finition. Les formats portrait et paysage sont recherchés automatiquement, sans sélection du type de carte.
- Images manquantes : nouvelle recherche des faces françaises officielles ; affichage d’un visuel disponible dans une autre langue avec une mention explicite lorsque la face choisie n’est pas publiée, sans modifier la langue enregistrée. Les versos des leaders bénéficient du même traitement.
- Lecture des numéros fragmentés en plusieurs chiffres ; conservation des références contradictoires pour éviter de choisir une mauvaise carte. Une pause ou une saisie manuelle ne peut plus compléter une ancienne lecture ou déclencher immédiatement un double ajout.

- Langue reconnue par le code imprimé, ou par plusieurs mots distinctifs du texte lorsque le code est illisible, sans utiliser la langue du téléphone ou du visuel.
- En série, une langue ou finition incertaine reste en attente de vérification dans le récapitulatif après « Terminer », sans interrompre chaque scan et sans être ajoutée avec une valeur inventée. Même protection pour les ajouts collection optionnels depuis un deck.

## Version 0.8.8

- Scanner : cadre horizontal « Leader / base », recherche dans les quatre orientations et demande d’une image plus détaillée lorsque la caméra le permet.
- Lecture de la référence reconstruite à partir de la position des mots du bas de carte, même lorsque l’OCR sépare l’extension, la langue et le numéro en plusieurs blocs. Les références contradictoires restent refusées.
- Le numéro exact reste conservé pour les hyperspace, showcase et autres variantes. Les finitions qui partagent un numéro, notamment normale/foil sur les premiers sets, restent à confirmer.

## Version 0.8.7

- Les decks commencés apparaissent dans Mes listes avec leur nom, leurs quantités et la mention Brouillon, même avant le choix du leader et de la base. Plusieurs créations peuvent être conservées et reprises indépendamment, y compris l’ancien brouillon local.
- Actions Modifier et Supprimer pour les brouillons ; accès Modifier explicite pour les decks enregistrés. La suppression demande confirmation et conserve les cartes de la collection. Les scans en attente restent rattachés au bon brouillon jusqu’à leur validation.

## Version 0.8.6

- Collection plus lisible sur petit écran : titre sur une largeur utile, langue et quantités sous les informations de la carte ; filtres de langue et d’extension séparés.

- Illustrations des badges recadrées à l’intérieur de leur zone dessinée, sans règles imprimées. Export direct des decks vers Melee/Karabast, scan de deck avec ajout facultatif à la collection et navigation simplifiée.

- Scan en français, anglais, allemand, espagnol et italien : lecture stable du code de langue au pied de la carte, choix explicite si la langue reste incertaine et contrôle compact pour la corriger avant l’ajout.
- Collection séparée par langue et finition, avec recto et verso correspondants. Les anciens exemplaires dont la langue n’était pas enregistrée restent « À préciser » ; leur quantité est conservée. Répartition et correction de langue avec confirmation et annulation dans l’historique.
- Langue conservée dans les lots, brouillons de scan de deck, sauvegardes, CSV et échanges QR. Les objectifs et les limites de copies des decks cumulent les langues ; les surplus proposés à l’échange gardent leur langue exacte. Lecture des anciennes sauvegardes et profils préservée.
- Images officielles associées à l’identité de la carte et à sa finition, sans fabriquer de traduction ni remplacer un visuel manquant par une autre langue. Téléchargement explicite des images hors connexion dans les langues disponibles.
- Contrôle des variantes : confirmation des numéros partagés entre normale et foil, hyperspace et hyperspace foil, ainsi que des finitions sérialisées standard, or rose et or. Les numéros de collection exacts restent conservés.

## Version 0.8.5

- Navigation clarifiée après revue des écrans : onglet Sélections pour les favoris et cartes recherchées, proxies présentés comme une action, export directement sur un deck enregistré. Les anciens formats de vérification des listes restent disponibles dans les options.
- Accès Mises à jour dans le bas du menu, entre À propos et Paramètres ; le raccourci d’un lot en attente indique désormais « Vérifier mon lot ».

- Les variantes scannées sont converties vers une référence classique vérifiée lors de l’export et leurs quantités regroupées par zone. Les numéros et finitions de la collection restent exacts ; un lien de variante inconnu bloque l’export avec une explication.
- Export des decks simplifié : deux boutons directs « Copier et ouvrir Melee » et « Copier et ouvrir Karabast ». Instructions adaptées et copie/fichier disponibles ; PDF et proxies regroupés dans les autres exports.
- Option « Ajouter aussi à ma collection », désactivée par défaut : finitions ambiguës à confirmer, récapitulatif avant enregistrement, annulation des ajouts et conservation dans le brouillon. Deck et collection sont enregistrés ensemble ; un lot déjà validé ne peut pas doubler les quantités.
- Scanner dans un deck : ajout carte après carte dans le deck principal ou la réserve, reconnaissance du leader et de la base, confirmation avant remplacement et annulation du dernier ajout. Les limites de 3 exemplaires entre variantes et réserve et de 10 cartes en réserve sont contrôlées. Les ajouts restent dans le brouillon jusqu’à l’enregistrement du deck.

- Illustrations des badges recadrées à l’intérieur de leur zone dessinée : les noms, règles et statistiques imprimés sur les cartes ne se retrouvent plus dans les pins. Cadrage adapté aux unités, améliorations, bases et leaders, y compris les versions Showcase utilisées.
- Même correction dans la galerie, les fiches, la révélation et le bouton Hauts faits de l’accueil ; cadres et progression conservés.

## Version 0.8.4

- Nouvel espace Hauts faits assorti à l’accueil bleu nuit et or : galerie de pins épiques illustrés avec les vraies cartes, recherche, familles, progression et fiches des conditions avec cartes acceptées.
- 36 histoires et personnages des films et séries, 28 objectifs de collection et d’utilisation, et 7 sagas regroupant plusieurs chapitres. Les histoires qui attendent des cartes restent en réserve. Les objectifs classiques sont figés pour préserver les récompenses lors des prochaines extensions.
- Révélation sur fond noir, mise en relief dorée et explication du badge gagné. Plusieurs gains sont regroupés ; aucune interruption du scanner ou d’un lot en attente. Réglage Android de réduction des animations respecté.
- Badges conservés après un échange, inclus avec les compteurs dans les sauvegardes et restaurés sans recompter les mêmes actions. Collection et decks existants pris en compte ; aucun ancien scan ou échange inventé.
- Les scans comptent après validation, les exports après copie ou enregistrement, les proxies après enregistrement du PDF et les sauvegardes après relecture. Confirmation explicite d’un échange effectué depuis les propositions QR.

- Futures extensions : les variantes dont la famille n’est pas encore vérifiée ne gonflent pas les objectifs. Leurs scans sont conservés en attente et crédités une seule fois après identification. Calcul de progression indexé hors du fil d’interface.
- Identité des dix premiers leaders de Mondes Natals corrigée depuis la source officielle ; les jetons partageant leurs numéros ne peuvent plus les remplacer.

## Version 0.8.3

- Accueil : 30 citations françaises des films sous le bandeau, avec personnage et film. Changement toutes les 20 secondes avec un fondu discret, sans déplacer les boutons ; rotation suspendue hors de l’accueil et en arrière-plan.

- Constructeur : les filtres propres aux cartes du deck (type, coût, arène) sont retirés au passage vers le leader ou la base. Les filtres utiles restent disponibles. Suppression du choix de tri redondant ; chronologie des extensions conservée.
- Recherche dans les decks et les proxies : références reconnues avec tiret, espace ou underscore ; accents et apostrophes harmonisés.
- Import : fenêtre défilable pour les petits écrans et le clavier, messages de références et de quantités en français, erreurs JSON présentées sans détails techniques.
- Atelier Proxies : grille adaptée aux grands caractères et commandes pouvant revenir à la ligne. Préparation du deck défilable et passage aux proxies dans une même fenêtre ; état conservé lors des changements de configuration Android.
- Formats d’export, dimensions des proxies, collection, sauvegardes et accueil conservés.

## Version 0.8.2

- Accueil bleu nuit et or harmonisé : six boutons en colonne, illustrations adaptées à leur fonction et contenues dans les cadres, titres natifs lisibles et sensibles à la taille de police. Accès direct aux échanges ; listes, decks épinglés et suivi de collection conservés.
- Bandeau d’origine et logo conservés : étoile filante plus visible à l’ouverture, arrivée de droite et fixation sous le logo, puis disparition de la traînée. Reflet lumineux régulier limité aux pixels dorés du logo, sans recoloration métallique.
- Suppression des vaisseaux et des variations d’ambiance du bandeau. Animations suspendues hors de l’accueil, derrière le scanner et en arrière-plan ; réglage Android de réduction des animations respecté.

## Version 0.8.1

- Bibliothèque de decks allégée : épinglage et préparation restent visibles ; duplication et suppression sont regroupées dans le menu du deck. Les actions de collection et de création peuvent revenir à la ligne avec de grands caractères.
- Scanner adapté aux écrans étroits, au paysage et au changement de taille d’un pliable : aperçu et commandes côte à côte lorsque la hauteur est limitée, commandes défilables et reconnexion de la caméra lors d’un changement d’orientation ou de dimensions. La protection contre les doubles lectures reste active pendant cette reconnexion.
- Lecture renforcée des références : ponctuation du pied de carte et erreurs OCR courantes des codes d’extension, uniquement devant une langue explicite. Numéros propres aux variantes conservés ; finitions partageant une référence toujours confirmées.
- Sauvegardes exportées relues et comparées avant confirmation, noms datés pour distinguer les copies. Fichiers tronqués, encodages invalides et quantités non entières refusés. Fusion entièrement annulée si une quantité dépasse la limite prise en charge. Vérifications de conservation et restauration de collection, variantes, listes et decks.

## Version 0.8.0

- Recherche globale sans distinction d’accents : cartes françaises et noms anglais connus, decks par nom ou cartes utilisées, extensions ; quantités possédées et decks utilisateurs visibles. Filtres de cartes conservés.
- Jusqu’à trois decks épinglés sur l’accueil, avec leader et cartes manquantes. Épinglage géré depuis Mes decks ; préférence locale indépendante des quantités.
- Fiche carte : stock cumulé, versions et réimpressions, decks utilisateurs et ajout direct à un deck enregistré. Limites de copies et de réserve contrôlées ; confirmation des changements de leader/base et protection des brouillons existants.
- Hors connexion dans les paramètres : téléchargement explicite des images recto/verso d’un deck ou d’une extension, progression, interruption, reprise des images restantes et libération séparée. Les images conservées sont utilisées aussi pour les PDF et restent hors des sauvegardes de collection.
- Préparer mon deck depuis la bibliothèque ou le constructeur : manquantes illustrées, liste d’achat texte et proxies préremplis, avec aperçu PDF existant.
- Confort mobile : actions pouvant revenir à la ligne, grille adaptée aux grands caractères, onglets sur deux rangées si nécessaire, fiche carte adaptée à la hauteur disponible et conservation de l’éditeur lors des changements de taille/orientation. Style bleu nuit et or et chronologie des extensions conservés.

## Version 0.7.0

- Analyse visuelle du principal ou de la réserve : courbe des coûts, types, unités terrestres/spatiales et affinités, pondérés par les quantités. Données inconnues signalées.
- Estimation du deck complet et des exemplaires manquants avec les cotes Cardmarket existantes en versions normales ; fourchettes, cotes absentes et date du guide visibles.
- Proxies des cartes manquantes : sélection préremplie à partir du stock partagé entre variantes et entre deck/réserve, ajustable avant impression.
- Brouillon automatique local séparé du deck enregistré, reprise après interruption et bouton Annuler pour les modifications récentes. Enregistrer valide le deck et efface son brouillon ; quitter conserve la création locale. Aucun changement de schéma de collection ou de sauvegarde globale.
- Affinités du leader et de la base : repères sur les cartes et filtre facultatif, sans modifier le classement par extension récente et numéro. Catalogue complet toujours accessible.
- Aperçu du PDF réel avant enregistrement, navigation entre pages et zoom. Dimensions des proxies, repères de découpe et exports Melee/Karabast/SWUDB/SWUBase conservés.

## Version 0.6.4

- Cartes du constructeur et de l’atelier Proxies classées par extension, de la plus récente à la plus ancienne, puis par numéro de carte. Même chronologie que la page Extensions.
- Sélecteur d’extension présenté dans cet ordre ; le tri alphabétique n’est plus proposé. Les tris par coût restent disponibles, avec départage chronologique.
- Collection, quantités, decks et exports conservés.

## Version 0.6.3

- Édition concentrée sur le deck : retour avec confirmation des modifications non enregistrées, navigation principale rétablie après fermeture du constructeur, davantage de hauteur pour les cartes.

- Export PDF illustré du deck, leader et base compris, avec quantités et réserve.
- Atelier Proxies depuis Mes listes ou depuis l’export d’un deck : choix des cartes et quantités, sélection mémorisée, PDF A4 de neuf cartes par page aux dimensions 63 × 88 mm avec repères de découpe ; format 63,5 × 88,9 mm et faces arrière séparées en option. Impression à taille réelle / 100 %.

- Parcours de création inspiré du constructeur officiel : nom du deck, choix explicite du leader puis de la base, catalogue et sélection, deck et réserve. Aucun leader ou base n’est imposé lors d’une nouvelle création.
- Recherche accessible pendant le défilement, sans distinction d’accents, filtres par affinité, type, coût, extension, arène, trait, rareté et possession ; tri par nom, coût et numéro. Remise à zéro depuis une vue vide.
- Grille illustrée adaptée au format horizontal des leaders et bases ; quantités et manquantes toujours visibles. Style bleu nuit et or conservé.
- Sauvegarde et export disponibles dès le choix du leader et de la base ; les brouillons sans 50 cartes restent enregistrables. Collection et sauvegardes existantes inchangées.

## Version 0.6.2

- Constructeur illustré : vue en cartes ou liste avec vignettes, images du leader et de la base, aperçu agrandi avec navigation et retournement des leaders. Cartes possédées, manquantes et quantités restent visibles.
- Bibliothèque de decks illustrée par le leader et la base. Le catalogue est ouvert immédiatement lors de la création d’un deck vide.
- Exports dédiés Melee et Karabast : copie au format texte Melee ou JSON Karabast, puis ouverture du site pour coller le deck. Le fichier peut aussi être enregistré. La soumission et le lancement d’une partie restent à effectuer sur le site.
- Correction de 103 correspondances de noms anglais et familles écrasées par des références promotionnelles. Export Melee avec séparation correcte du titre et du sous-titre ; blocage explicite si un nom anglais est indisponible.
- Publication fiabilisée en utilisant directement l’identifiant du brouillon créé. Collection, listes et decks conservés ; aucun changement du schéma de sauvegarde.

## Version 0.6.1

- Onglets Deck, Réserve, Leader et Base de même largeur pour conserver des libellés lisibles sur les petits écrans, dans la palette bleu nuit et or.
- Vérification sur l’APK optimisé de l’affichage sur une seule ligne des quatre onglets du constructeur.

## Version 0.6.0

- Constructeur Premier dans Mes listes → Mes decks : leader, base, deck principal et réserve ; recherche, quantités, renommage, duplication et suppression avec confirmation.
- Vérification des règles générales : 50 cartes minimum dans le principal, 10 maximum en réserve, 3 exemplaires toutes versions reconnues confondues sur les deux zones, types de cartes, rotation et suspensions connues. Les contraintes particulières imprimées sur certaines cartes restent à vérifier.
- Détail des cartes manquantes avec stock cumulé par famille de variantes et réimpressions reconnues, leader et base compris. L’édition d’un deck ne modifie pas les quantités de collection.
- Import JSON et texte par fichier ou collage avec aperçu ; exports JSON SWUDB/SWUBase et texte structuré pour le convertisseur SWUBase. Les noms ambigus, références inconnues et decks à deux leaders sont signalés.
- Sauvegarde globale incluant les decks ; restauration commune des cartes, listes et decks dans une transaction. Une ancienne sauvegarde sans decks conserve les decks actuels. Fusion sans écraser un deck déjà présent.
- Migration additive conservant les données et récupération des decks des versions de développement. Contrôle sur émulateur de l’ouverture, de l’enregistrement et de la réouverture d’un deck dans l’APK optimisé.

## Version 0.5.0

- Navigation fixe en cinq onglets, menu simplifié et accès direct au scanner, dans la charte spatiale bleu nuit et or existante. Logo, bandeau animé et illustrations conservés.
- Accueil avec résumé de collection, valeur cotée, lot de scan à reprendre et progression d’une extension à continuer.
- Filtres actifs visibles et supprimables individuellement ; remise à zéro depuis les résultats vides.
- Objectif commun par extension pour les manquantes et les échanges : un exemplaire ou playset, avec choix de compter les variantes. Ces réglages sont sauvegardés et restaurés avec la collection.
- Comparaison QR guidée avec étapes, réception détaillée, invitation à inverser les rôles et résultats illustrés « Je donne / Je reçois ». Les deux téléphones doivent utiliser la version 0.5.0 ou suivante. Lecture des anciens profils toujours possible.
- Affichage facultatif regroupant les versions d’une carte, sans fusionner leurs références ou quantités. Confirmation de scan allégée avec quantité déjà possédée.
- Écran de récupération pour les erreurs de démarrage détectées, avec sauvegarde, mises à jour et mode essentiel lorsque possible. Contrôle d’ouverture et de réouverture du véritable APK optimisé sur émulateur avant publication.
- Migration conservant cartes, variantes, quantités, listes, historique et objectifs ; sauvegardes des anciennes versions toujours lisibles.


