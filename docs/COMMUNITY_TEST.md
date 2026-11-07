# Communauté — application Test uniquement

Le projet Firebase est `swu-collection-test`, avec l’application Android `fr.swucollection.app.test`, l’authentification e-mail/mot de passe et Firestore Standard. Aucun abonnement Blaze, Cloud Storage ni fonction payante n’est utilisé.

La configuration client fournie par Romain est chiffrée avec la clé publique du certificat Android. Le job privé de distribution déchiffre uniquement dans `app/src/preview/res/values/community_config.xml`, fichier ignoré par Git. La variante officielle contient une configuration vide et l’accès Communauté est masqué hors du paquet Test.

Depuis la demande de Romain du 6 novembre 2026, les comptes Test s’activent automatiquement après confirmation d’e-mail. La première activation crée uniquement leur propre document `testMembers/{uid}` avec `enabled: true`, `selfRegistered: true` et `joinedAt` fourni par le serveur. Un document existant, notamment `enabled: false`, ne peut pas être modifié ou supprimé par un client. Les comptes existants restent compatibles. La liste des membres n’est jamais consultable. Les règles sont appliquées au projet Test seulement.

Profils : pseudo, bio, avatar prédéfini ou photo réduite à 96×96, stockée dans le profil Firestore pour éviter tout stockage payant. Visibilité et affichage des badges désactivés par défaut. Aucun e-mail, collection, quantité ni preuve privée de badge dans les profils.

Messages : conversations privées à deux membres approuvés, 50 derniers messages, identité de l’expéditeur contrôlée côté serveur, blocage dans les deux sens. Une copie JSON indépendante du deck peut être envoyée. Le destinataire la consulte puis l’ouvre dans l’éditeur de decks pour vérification avant enregistrement ; aucune carte n’est ajoutée à la collection.

Les mots de passe sont gérés par Firebase Authentication et ne sont jamais conservés par un stockage maison. Les écoutes de profils et conversations sont retirées à la déconnexion ; le cache social persistant est désactivé. Une déconnexion ne touche pas aux données locales de collection ou de decks.

État actuel Test 12 : les notifications de messages et d’amis sont vérifiées par WorkManager environ toutes les 15 minutes, sous réserve des restrictions Android. Aucun relais FCM ni push instantané n’est activé. La sauvegarde privée du compte est automatique après vérification d’e-mail, avec une pause avant tout conflit ; la restauration sur un autre téléphone reste confirmée. Voir la section Test 12.

Validation : `cd social && npm ci && npm test` utilise exclusivement le projet d’émulation `demo-swu-community-test`, sans accès aux vraies données. Les scénarios vérifient notamment un véritable parcours Auth émulé : inscription, confirmation de l’e-mail, activation autonome puis déconnexion. Aucun compte réel ni e-mail externe n’est créé par ces tests. Tests Android, lint, compilation et coexistence via le workflow de distribution chiffrée. La publication officielle reste soumise à l’accord explicite de Romain.

## Mises à jour privées

`docs/preview-update.json` annonce uniquement un APK Test validé et enregistré en privé. Son URL doit rester une page Library `chatgpt.com` du destinataire ; aucun APK clair ni clé d’accès ne doit être publié dans GitHub. Le contrôle utilise le code Android croissant, conserve un cache validé et ne notifie qu’une fois chaque code. La notification mène à Mises à jour. Avant l’ouverture du téléchargement privé, une copie de récupération locale est créée ; le destinataire se connecte à son propre compte ChatGPT et installe l’APK par-dessus Test.

Après les contrôles réussis et l’enregistrement privé du nouvel APK, mettre à jour cette annonce avec le vrai code, la version, l’ID Library, le SHA-256 et la taille vérifiés. Une modification de cette seule annonce ne relance pas la compilation. Ne jamais annoncer un fichier non validé ou encore indisponible. L’officielle reste sur son propre flux ; aucun changement n’est publié dans sa distribution. Le premier APK ajoutant les notifications doit être installé manuellement. Android peut retarder les vérifications en arrière-plan.

## Amis et profils — Test 0.8.19-test.4

La version ajoute `friendships` (demande bilatérale puis acceptation par le destinataire), `reads/{uid}/threads` (compteurs privés), `trades` (propositions entre amis acceptés) et `profiles/{uid}/decks` (copies publiées volontairement). Une mise à jour des règles `social/firestore.rules` sur **swu-collection-test uniquement** est nécessaire avant d’utiliser cette version. Les anciennes propriétés de profil restent acceptées ; les paramètres ajoutés ont des valeurs par défaut compatibles. Aucun compte de test n’est autorisé automatiquement.

Le QR ami contient seulement l’identifiant du joueur, sans e-mail ni collection. Son scan remplit le code ami ; l’utilisateur confirme l’envoi et le destinataire accepte. Les profils non listés restent privés jusqu’à l’acceptation. Les listes sont bornées : 100 relations/conversations/propositions et 20 decks affichés par profil pour ces essais privés.

La galerie propose Luke, Vador, Leia, Yoda, Han, Chewbacca, Ahsoka, Obi-Wan, R2-D2, C-3PO, Boba Fett, le Mandalorien, Maul, Palpatine, Rey et Sabine. Le portrait est extrait de l’illustration puis réduit et stocké comme les photos, sans lien distant ajouté au profil.

La pastille d’accueil et les compteurs des onglets indiquent les conversations non lues, demandes d’amis et propositions reçues en attente pendant l’utilisation. Aucun nouveau relais push téléphone fermé n’est annoncé. Accepter un échange ne transfère aucune carte automatiquement ; les joueurs organisent la remise dans la messagerie puis ajustent leurs collections après l’échange.

## Accueil compact et téléchargement — Test 0.8.19-test.5

Les raccourcis sont intégrés au visuel d’accueil : avatar, menu puis courrier dans la colonne de droite, avec des cibles tactiles de 48 dp. Le bandeau de profil est retiré. Le menu dessiné dans l’image est masqué uniquement pour cette disposition Test ; les vrais boutons restent accessibles avec un lecteur d’écran.

Le bandeau des mises à jour tient compte du flux privé. Son clic et celui de la notification demandent directement le fichier Library via le navigateur compatible Custom Tabs. La connexion privée reste gérée par ChatGPT dans le navigateur, sans mot de passe, cookie ni jeton intégré dans l’application. L’autorisation des notifications et les restrictions Android restent applicables. Le déclenchement depuis une notification utilise un jeton local à usage unique afin qu’une application externe ne puisse pas lancer arbitrairement le téléchargement. Une sauvegarde est créée avant l’ouverture ; l’installation reste confirmée par Android.

## Bandeau et écrans pliants — Test 0.8.19-test.6

Le nouveau décor d’accueil est une ressource indépendante sans commandes imprimées : planète, ville et Faucon Millenium, dans le style de l’aperçu approuvé. L’officielle conserve son ancien visuel. Les trois raccourcis Test gardent leurs cibles tactiles de 48 dp.

La colonne d’accueil est centrée et plafonnée à 600 dp ; les pages de collection, extensions, decks et badges disposent de 1 000 dp, les autres formulaires de 760 dp. La navigation est plafonnée à 640 dp et le menu à 400 dp. Ces plafonds suivent les contraintes de la fenêtre, y compris le multi-fenêtrage ; ils ne supposent pas un modèle de téléphone. Les illustrations des boutons utilisent une échelle uniforme et un recadrage, sans déformation. Les images de cartes gardent leur rendu existant et le scanner possède déjà sa disposition adaptative avec reconnexion caméra au changement de taille.

Les vérifications d’émulateur couvrent plusieurs dimensions et leur changement pendant la navigation. Elles simulent les surfaces d’un pliant ; la charnière physique et le comportement spécifique du constructeur restent à essayer sur un vrai Z Fold.

## Compte, échanges et personnalisation — Test 0.8.19-test.8

Les sauvegardes manuelles utilisent `accountBackups/{uid}/snapshots/{id}/chunks/{index}`. Elles sont compressées et vérifiées après lecture depuis le serveur, privées au propriétaire vérifié et autorisé, immuables et publiées atomiquement avec leur pointeur. Une sauvegarde concurrente sur un autre téléphone bloque le remplacement jusqu’à actualisation. La connexion n’envoie ni ne restaure de données automatiquement. L’écran présente les trois sauvegardes les plus récentes ; les versions plus anciennes sont conservées. La restauration affiche les quantités et demande confirmation, crée une récupération locale puis remplace les données sans additionner les copies. Les réglages d’accueil et épingles sont inclus ; ni mot de passe ni messages ne sont sauvegardés avec la collection.

Le partage d’échange est volontaire : `tradeInventories/{uid}` contient uniquement un profil d’offres et de recherches compressé, sans collection complète ni coordonnées. Lecture uniquement par le propriétaire ou des amis acceptés, autorisés et non bloqués. La suppression de ce document révoque le partage. Les suggestions sont des brouillons modifiables ; aucun message ni proposition n’est envoyé sans validation de l’utilisateur.

Les règles `social/firestore.rules` de cette version ont été déployées sur **swu-collection-test uniquement**, le 6 novembre 2026 à 19 h 25 (Paris), après réussite des tests de sécurité. Les règles de sauvegarde ne rendent aucune collection publique et n’autorisent aucun nouveau compte.

## Inscription autonome — Test 0.8.19-test.9

L’approbation manuelle a été supprimée à la demande de Romain. `CommunityService.allowed()` crée le document du compte vérifié dans une transaction, uniquement s’il n’existe pas. Les règles autorisent cette création initiale avec un schéma strict et l’identifiant du propriétaire ; toute modification ou suppression reste réservée à l’administration. Une désactivation existante ne peut donc être contournée par le client. Aucune sauvegarde ni conversation n’est rendue publique par cette activation. Les règles d’inscription autonome ont été déployées sur **swu-collection-test uniquement**, le 6 novembre 2026 à 21 h 01 (Paris), après réussite des tests de sécurité. La version Test 10 inclut le parcours client ; aucun accès aux sauvegardes ou conversations d’autres comptes n’a été ajouté.

## Bandeau vivant — Test 0.8.19-test.10

Les fenêtres déjà éclairées scintillent à des rythmes indépendants ; les éclairages avant et moteur du Faucon Millenium pulsent doucement. Le décor reste fixe et le même cadrage uniforme est partagé par l’image et ses lumières, y compris sur les écrans pliants. L’animation tourne uniquement sur l’accueil Test au premier plan, respecte le réglage des reflets et les animations système, et réutilise ses halos sans allocation par image.

## Téléchargement privé corrigé — Test 0.8.19-test.11

Le client conserve le lien de page privé complet, y compris le compte personnel, et ouvre un navigateur normal avec ACTION_VIEW. Il ne transforme plus le lien en appel direct à `/api/library/files/.../download`, qui est refusé depuis une autre origine. Le téléchargement est lancé par le joueur depuis la page ChatGPT authentifiée, sans copie de session dans l’application. Les tests de régression vérifient l’URL de page, l’absence de session Custom Tabs et de référent ajouté, ainsi que le refus des URL API directes et d’autres domaines. Les anciennes versions gardant l’ancien routage doivent installer une fois ce correctif depuis un lien fourni dans la conversation ; l’annonce seule ne peut pas changer le code installé.

## Fiches, sauvegarde automatique, alertes, échanges et pliants — Test 0.8.19-test.12

Le regroupement visuel est activé une fois lors de la mise à jour Test, puis le choix de l’utilisateur est conservé. Les familles connues peuvent contenir des réimpressions d’autres extensions. Les références sans lien confirmé restent séparées. Un clic ouvre la fiche unique avec les miniatures, les stocks par langue et finition. Aucun identifiant de stock ni quantité n’est fusionné.

La sauvegarde automatique réutilise les instantanés privés immuables et la publication atomique. Un contrôle est demandé pendant l’utilisation et à la sortie, avec une tâche périodique connectée en arrière-plan. Les sauvegardes inchangées ne sont pas renvoyées ; les modifications sont regroupées au plus toutes les cinq minutes quand Android autorise l’exécution. Le contenu est relu et vérifié avant d’enregistrer le nouveau pointeur local. Les anciennes sauvegardes sont conservées. Une sauvegarde existante sans baseline reconnue (nouveau téléphone), un pointeur changé par un autre téléphone ou un changement de compte met l’envoi en pause. L’écran permet de restaurer une copie avec aperçu ou de confirmer la conservation des données locales. Aucun remplacement, transfert entre comptes ni cumul de quantités ne se produit automatiquement. Les réglages et baselines de sauvegarde sont locaux et propres à chaque compte ; seules une empreinte et un pointeur sont ajoutés aux préférences.

Les notifications utilisent les métadonnées de conversations, leurs reçus de lecture et les demandes d’amis déjà autorisées, limitées à 100 documents par liste. Les messages rédigés par soi-même, les conversations déjà lues et les événements déjà notifiés sont exclus. Les réglages sont distincts pour messages et amis ; les canaux Android peuvent aussi être désactivés. Les notifications sont génériques, privées sur l’écran verrouillé, et leur ouverture vérifie que le compte destinataire est toujours connecté. La déconnexion annule les tâches et retire les alertes communautaires. Le bouton de test local ne crée ni conversation ni message externe. Ce sont des vérifications périodiques WorkManager (intervalle minimal 15 minutes), pas un relais push ; Doze, absence de réseau, batterie faible ou arrêt forcé peuvent retarder ou empêcher l’exécution jusqu’à la réouverture. Aucun nouvel abonnement ni projet serveur n’est configuré. Les quotas existants Firebase restent applicables.

Les échanges exposent seulement les profils volontairement partagés aux amis acceptés et non bloqués. La consultation affiche les recherches des deux côtés, les doublons de l’ami et les correspondances illustrées, avec quantités/langues/finitions conservées. Une correspondance à sens unique peut initialiser un brouillon à compléter. L’envoi exige toujours deux côtés valides et une action explicite. L’accord ne transfère aucune carte automatiquement.

Les essais d’émulateur ajoutent une recherche avec clavier ouvert pendant le changement de fenêtre, la conservation d’une fiche de carte et de son index, les flèches suivante/précédente après dépliage et paysage, et le redimensionnement CameraX. Les scénarios n’utilisent que le catalogue et un compte local vide, sans utilisateur cloud réel. Paramètres → Vérifier mon écran pliant fournit six vérifications manuelles et un compte rendu local sans collection ni identifiant de compte. La charnière réelle, la caméra physique et le comportement spécifique du Z Fold de Romain restent à vérifier par son propriétaire ; aucune validation sur ce vrai téléphone n’est revendiquée.

La fiche Test se ferme avec son bouton ou le retour Android, sans fermeture par toucher extérieur lors des changements de fenêtre. Dans la collection regroupée, les flèches parcourent les familles de cartes ; le choix de version reste dans la fiche. Le panneau Échanges comporte un accès direct aux échanges avec les amis.


## Compteur, prêts, alertes et image de deck — Test 0.8.19-test.13

Le compteur est accessible depuis l’accueil et le menu, sans compte ni réseau. Les deux joueurs choisissent chacun leur design parmi six styles, leur nom et les PV de leur base (1 à 100). Le mode face à face peut être désactivé. L’initiative, les dégâts/PV, les manches et les jetons restent manuels ; les jetons sont un aide-mémoire et n’appliquent aucune règle de carte. Le chronomètre peut être mis en pause. Les 50 dernières actions peuvent être annulées sans remonter le chronomètre. Le match est conservé après fermeture et les 50 derniers matchs enregistrés sont consultables. KEEP_SCREEN_ON est attaché à la vue du compteur puis son état précédent est restauré à sa fermeture ; aucun verrou de réveil permanent ni permission supplémentaire n’est utilisé.

Les prêts sont privés et conservés avec la référence, la langue, la finition, l’emprunteur, la quantité, les retours partiels et une date de retour facultative. Ils ne modifient aucune quantité possédée. Les nouveaux profils et QR d’échange déduisent les prêts des disponibilités, tout en conservant les quantités possédées pour les objectifs de collection. Les profils déjà partagés sont des copies : il faut les actualiser après un prêt ou un retour. Enregistrer un prêt n’envoie aucun message.

Les alertes de recherche sont facultatives et désactivées par défaut dans les notifications du compte. Elles comparent les besoins locaux aux seules listes volontairement partagées par des amis acceptés (requêtes bornées à 100 amis). Les recherches ne sont pas publiées. Les correspondances inchangées ne sont pas notifiées plusieurs fois ; les listes privées, retirées ou bloquées n’émettent pas d’alerte. Elles utilisent la vérification périodique Android existante (15 minutes minimum, retards possibles), sans nouveau service serveur ni push instantané. Les quotas Firebase existants restent applicables.

L’export de deck en PNG comporte le leader, la base, le deck principal et la réserve, avec quantités et références. Les visuels manquants sont signalés ; l’image peut être prévisualisée, enregistrée puis partagée par le sélecteur Android. Aucun envoi externe n’est automatique.

La migration SQLite 11 → 12 ajoute uniquement la table app_tools. La sauvegarde JSON 8 inclut les prêts, la partie et les matchs. Une restauration avec remplacement les remplace également ; une sauvegarde ancienne n’en contient pas. Une fusion sans remplacement conserve les outils déjà présents et ne cumule pas leurs quantités. Les copies de récupération incluent ces outils. Les tests vérifient la conservation des cartes, les retours, les variantes/langues, l’intégrité avant restauration, l’annulation des manches et dégâts, l’indépendance des designs et la restauration du maintien d’écran. Les essais sur émulateur couvrent dégâts, annulation, choix de design, formats fermé/déplié/paysage et réouverture du compteur. Les six vérifications physiques du SM-F966B ont été confirmées par Romain sur Test 12 ; l’écran temporaire de vérification a donc été retiré.


## Profils, vitrines et collection — Test 14

La ville est saisie volontairement, sans GPS ni adresse, avec normalisation des accents et des séparateurs pour la recherche. Le répertoire interroge les profils `discoverable=true` de cette ville, avec un maximum explicite de 50 résultats, puis filtre le pseudo et le format. Formats et trois leaders favoris sont facultatifs. Les anciens profils restent compatibles.

Les documents `profiles/{uid}/showcase/{id}` ne contiennent que la référence choisie, langue, finition, légende et date de mise à jour. Aucune quantité ni collection complète n’est envoyée. Le propriétaire contrôle les cartes et l’affichage de sa vitrine. Sa visibilité suit le profil et `shareShowcase`, avec vérification des comptes actifs et des blocages dans les deux sens. Les favoris `showcaseFavorites/{uid}/items/{id}` sont strictement privés ; ni compteur de popularité ni notification externe n’est créé. Masquer une vitrine révoque les lectures et les nouveaux favoris ; une ancienne entrée privée de favori n’accorde aucun accès supplémentaire.

Les objectifs précis sont conservés dans `tools.printGoals`, validés puis inclus dans les sauvegardes existantes. Ils ne modifient aucun stock. Les corrections par lot sont atomiques, refusent les finitions incompatibles et les exemplaires prêtés, conservent les quantités totales et se consignent dans l’historique. Les besoins des decks sont indicatifs : un exemplaire peut servir à plusieurs decks et n’est pas automatiquement réservé.

En rafale, une suggestion visuelle incertaine reste une ligne non validable dans le lot local, avec `needsIdentity=true`. Le joueur doit confirmer carte, langue et finition. Les corrections et leur retrait du groupe d’attente sont enregistrés ensemble dans les préférences. Les scans confirmés restent soumis au numéro, au catalogue et au contrôle de répétition existants. Les conseils de lumière et de contraste sont indicatifs et n’affectent ni les seuils de reconnaissance ni la décision de finition.

Les règles Firestore de Test 14 ont été publiées sur `swu-collection-test` le 7 novembre 2026 à 10 h 03 (Paris), après réussite des 27 tests de sécurité. Les essais de sécurité utilisent uniquement `demo-swu-community-test`, sans compte réel ni message envoyé.
