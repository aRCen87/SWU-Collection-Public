# Communauté — application Test uniquement

Le projet Firebase est `swu-collection-test`, avec l’application Android `fr.swucollection.app.test`, l’authentification e-mail/mot de passe et Firestore Standard. Aucun abonnement Blaze, Cloud Storage ni fonction payante n’est utilisé.

La configuration client fournie par Romain est chiffrée avec la clé publique du certificat Android. Le job privé de distribution déchiffre uniquement dans `app/src/preview/res/values/community_config.xml`, fichier ignoré par Git. La variante officielle contient une configuration vide et l’accès Communauté est masqué hors du paquet Test.

Les comptes nécessitent confirmation d’e-mail ET une autorisation administrative `testMembers/{uid}` avec le booléen `enabled: true`. Ne pas ouvrir la liste à la communauté pendant les essais de Romain. Les clients ne peuvent ni se donner cette autorisation, ni consulter la liste des membres autorisés. Les règles validées sont dans `social/firestore.rules` ; les installer sur le projet Test seulement.

Profils : pseudo, bio, avatar prédéfini ou photo réduite à 96×96, stockée dans le profil Firestore pour éviter tout stockage payant. Visibilité et affichage des badges désactivés par défaut. Aucun e-mail, collection, quantité ni preuve privée de badge dans les profils.

Messages : conversations privées à deux membres approuvés, 50 derniers messages, identité de l’expéditeur contrôlée côté serveur, blocage dans les deux sens. Une copie JSON indépendante du deck peut être envoyée. Le destinataire la consulte puis l’ouvre dans l’éditeur de decks pour vérification avant enregistrement ; aucune carte n’est ajoutée à la collection.

Les mots de passe sont gérés par Firebase Authentication et ne sont jamais conservés par un stockage maison. Les écoutes de profils et conversations sont retirées à la déconnexion ; le cache social persistant est désactivé. Une déconnexion ne touche pas aux données locales de collection ou de decks.

Notifications téléphone fermé : non activées par cette première version. Le relais Cloudflare/FCM reste à développer et à configurer séparément. La synchronisation des collections n’est pas encore activée : ne jamais la présenter comme une sauvegarde cloud existante.

Validation : `cd social && npm ci && npm test` utilise exclusivement le projet d’émulation `demo-swu-community-test`, sans accès aux vraies données. Huit scénarios vérifient notamment un véritable parcours Auth émulé : inscription, confirmation de l’e-mail, autorisation administrative puis déconnexion. Aucun compte réel ni e-mail externe n’est créé par ces tests. Tests Android, lint, compilation et coexistence via le workflow de distribution chiffrée. La publication officielle reste soumise à l’accord explicite de Romain.
