# Préparation officielle 0.9.0

Romain a autorisé le 8 novembre 2026 la reprise des fonctions de Test dans l'officielle, puis la publication uniquement après validation complète. Cet accord couvre les préparations et la publication finale ; ne pas redemander une autorisation générale déjà donnée. Tout blocage réel doit être expliqué et la version actuelle reste disponible jusqu'à la réussite.

## État vérifié

- Source de départ : Test 24, commit 0e7d2b6b39c2bdc8b461765efacbbe049d0636a1. Les nouveautés et les comptes sont promus au paquet officiel, tout en conservant le paquet Test et son propre flux de mise à jour.
- Projet officiel `swu-collection-official` créé sur Spark, sans Analytics, Gemini, Developer Program ou option payante.
- Application Android `fr.swucollection.app` enregistrée. Le fichier de configuration est chiffré vers l'identité de signature permanente ; aucune clé privée ni configuration client en clair dans Git.
- Connexion e-mail/mot de passe activée. Les modèles d'e-mail sont en français.
- Firestore Standard `(default)` créé à Paris (`europe-west9`), d'abord en refus intégral puis avec les règles officielles vérifiées. Les 24 506 caractères de l'éditeur ont été comparés au fichier testé avant publication ; la console confirme la version active du 8 novembre à 20 h 49 (Paris).
- Inscription autonome après e-mail confirmé : collection `members` dans l'officielle, `testMembers` dans Test. Un compte désactivé ne peut pas se réactiver.
- Les 40 tests de sécurité officiels et 9 tests de la page de confirmation ont réussi. Les premières compilation, tests de code Android et lint ont réussi. Une nouvelle passe sur l'APK optimisé signé, les parcours natifs et la vraie mise à jour est requise pour le dernier état des sources.
- La conservation est vérifiée au niveau SQLite 11 → 12 (cartes, identifiants, quantités, variantes, langues, listes et decks). Un contrôle sur le véritable APK officiel publié est ajouté : fixtures exclusivement dans un émulateur, puis mise à jour signée sans désinstallation entre ancienne et nouvelle versions.

## Blocages et points non encore validés

- Personnalisation des e-mails : le nom d'expéditeur **SWU Collection** a été enregistré et vérifié le 8 novembre, séparément, pour les trois modèles utiles (validation d'adresse, mot de passe oublié, modification d'adresse). L'objet de validation personnalisé, essayé seul, reste refusé par Firebase (« Il n'est actuellement pas possible de mettre à jour les modèles d'e-mail de ce projet »). Le lien reste le gestionnaire Firebase standard, pas une page inexistante. Une demande d'assistance détaillée est préparée dans le formulaire officiel, non envoyée, en attente de l'accord de Romain pour contacter Google.
- L'application Web de confirmation est enregistrée et associée au site Hosting officiel, mais les fichiers de la page ne sont pas déployés. Aucun changement du lien d'action tant que la page n'est pas disponible et validée.
- La connexion de Firebase CLI a été bloquée précédemment par la politique d'URL du navigateur après l'accord de Romain. Ne pas contourner ce refus en rejouant l'autorisation par un chemin indirect. La session CLI en attente a été arrêtée, aucune connexion CLI utilisable n'a été confirmée.
- Aucun parcours avec de vrais comptes officiels, aucune réception réelle de mail officiel et aucune validation sur les deux téléphones de Romain n'est encore confirmée. Les tests de sécurité utilisent exclusivement les émulateurs locaux.
- Aucune mise à jour officielle n'est publiée par le workflow de validation de candidate. Le dernier état signé et tous les contrôles doivent être validés avant toute fusion/publication.

## Dernier essai réel avant ouverture publique

Utiliser la candidate officielle signée, sur l'installation officielle existante, sans désinstaller l'application. Comparer d'abord les quantités et les listes avant/après. Créer un compte avec une adresse contrôlée par Romain, recevoir le mail en français, confirmer son lien puis actualiser le statut dans l'application. L'accès doit s'activer sans intervention dans Firebase. Enregistrer le profil et vérifier une sauvegarde cloud ; sur le second téléphone, se connecter au même compte et contrôler la sauvegarde avant toute restauration explicitement choisie. Les essais de messagerie déjà réalisés sur Test ne constituent pas une preuve de ce parcours avec le nouveau projet officiel.

Ne pas annoncer cet essai réussi sur la seule base des tests d'émulateur ou du HTTP 200 du gestionnaire de confirmation. La publication reste en attente si la réception réelle du mail ou l'activation du compte n'est pas confirmée.

## Commandes utiles, depuis un environnement de déploiement autorisé

Les règles déjà publiées correspondent à `social/official/firestore.rules`. Les index ne nécessitent aucune configuration complémentaire (`indexes: []`). Pour un déploiement contrôlé : `firebase deploy --only firestore --config social/official/firebase.json --project swu-collection-official`.

Pour la page : `firebase deploy --only hosting --config account-email/firebase.json --project swu-collection-official`. Lire `account-email/README.md` pour la validation et le réglage des modèles, actuellement bloqué côté Firebase.

Ne pas migrer les comptes Test ni leurs données : Romain a confirmé qu'il acceptait de repartir de zéro pour ces deux comptes. Conserver impérativement les collections déjà présentes dans l'officielle. Le compte reste facultatif et une connexion ne remplace jamais automatiquement la collection locale par une copie distante.
