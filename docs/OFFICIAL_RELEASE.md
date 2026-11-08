# Préparation officielle 0.9.0

Romain a autorisé le 8 novembre 2026 la reprise des fonctions de Test dans l'officielle, puis la publication uniquement après validation complète. Cet accord couvre les préparations et la publication finale ; ne pas redemander une autorisation générale déjà donnée. Tout blocage réel doit être expliqué et la version actuelle reste disponible jusqu'à la réussite.

## État vérifié

La validation complète [42522045027](https://github.com/aRCen87/SWU-Collection-Public/actions/runs/42522045027) a réussi le 8 novembre à 21 h 20 UTC, pour les sources `b845f84a566f8181ec4adc41b0611fa492b6163e`. Le précédent arrêt était celui du scénario devant une demande Android de notifications ; le grant a été rétabli après réinstallation de la fixture. Le contrôle de mise à jour signé est désormais réussi.

- Source de départ : Test 24, commit 0e7d2b6b39c2bdc8b461765efacbbe049d0636a1. Les nouveautés et les comptes sont promus au paquet officiel, tout en conservant le paquet Test et son propre flux de mise à jour.
- Projet officiel `swu-collection-official` créé sur Spark, sans Analytics, Gemini, Developer Program ou option payante.
- Application Android `fr.swucollection.app` enregistrée. Le fichier de configuration est chiffré vers l'identité de signature permanente ; aucune clé privée ni configuration client en clair dans Git.
- Connexion e-mail/mot de passe activée. Les modèles d'e-mail sont en français.
- Firestore Standard `(default)` créé à Paris (`europe-west9`), d'abord en refus intégral puis avec les règles officielles vérifiées. Les 24 506 caractères de l'éditeur ont été comparés au fichier testé avant publication ; la console confirme la version active du 8 novembre à 20 h 49 (Paris).
- Inscription autonome après e-mail confirmé : collection `members` dans l'officielle, `testMembers` dans Test. Un compte désactivé ne peut pas se réactiver.
- Les 40 tests de sécurité officiels, 9 tests de la page et 54 contrôles de préparation ont réussi, ainsi que les tests Android, lint et compilations debug/release optimisée signée.
- Les parcours natifs ont réussi : animations et mouvement réduit, collection et prix, miniatures à 3/7/6 colonnes, recherche et filtres, gestion de collection, clavier et fiche conservés pendant les changements d'écran, caméra en redimensionnement, compteur solo/deux joueurs et résultats, ouverture des comptes.
- La conservation est vérifiée au niveau SQLite 11 → 12 (cartes, identifiants, quantités, variantes, langues, listes et decks). Le vrai APK officiel publié 0.8.19 a également été mis à jour vers la candidate 0.9.0 sans désinstallation entre les versions : identifiants, cartes FR/EN, quantités, variantes, listes et decks identiques après mise à jour, UID conservé et schéma 12.
- Précontrôles du serveur en direct : configuration publique Firebase reconnue pour l'application Android (HTTP 200), accès anonyme à un document de membres refusé par Firestore (HTTP 403, permissions insuffisantes). Aucun compte réel créé ni mail envoyé par ces contrôles.
- APK privé `SWU-Collection-Official-Candidate-0.9.0.apk` récupéré, déchiffré et vérifié, puis enregistré pour l'essai réel. Paquet `fr.swucollection.app`, version 0.9.0, code 84, 63 056 047 octets, SHA-256 `472310755af4660383511f9b9ea36ba76d026c7d5efe61da17d3f9806304b040`, certificat permanent `54912449ae2e4ecd6e88243d3e11fb0bb2c1d2e7da77af2038752ee50e7b549b`. Empreinte des sources applicatives : `58945395f74bee47e117b2f19c7c1831dde50118f173007c52de2287ff8965fe`.

## Blocages et points non encore validés

- Personnalisation des e-mails : le nom d'expéditeur **SWU Collection** a été enregistré et vérifié le 8 novembre, séparément, pour les trois modèles utiles (validation d'adresse, mot de passe oublié, modification d'adresse). L'objet de validation personnalisé, essayé seul, reste refusé par Firebase (« Il n'est actuellement pas possible de mettre à jour les modèles d'e-mail de ce projet »). Le lien reste le gestionnaire Firebase standard, pas une page inexistante. Romain a refusé le contact avec l'assistance : ne pas envoyer la demande. Il accepte l'objet français standard et le nom d'expéditeur enregistré ; cette personnalisation n'est plus un préalable à la publication.
- L'application Web de confirmation est enregistrée et associée au site Hosting officiel, mais les fichiers de la page ne sont pas déployés. Aucun changement du lien d'action tant que la page n'est pas disponible et validée.
- La connexion de Firebase CLI a été bloquée précédemment par la politique d'URL du navigateur après l'accord de Romain. Ne pas contourner ce refus en rejouant l'autorisation par un chemin indirect. La session CLI en attente a été arrêtée, aucune connexion CLI utilisable n'a été confirmée.
- Aucun parcours avec de vrais comptes officiels, aucune réception réelle de mail officiel et aucune validation sur les deux téléphones de Romain n'est encore confirmée. Les tests de sécurité utilisent exclusivement les émulateurs locaux.
- Aucune mise à jour officielle n'est publiée par le workflow de validation de candidate. Les contrôles automatiques ont réussi ; l'essai réel de compte, mail et sauvegarde reste requis avant fusion et publication. La dernière version publique vérifiée reste 0.8.19.

## Dernier essai réel avant ouverture publique

Utiliser la candidate officielle signée, sur l'installation officielle existante, sans désinstaller l'application. Comparer d'abord les quantités et les listes avant/après. Créer un compte avec une adresse contrôlée par Romain, recevoir le mail en français, confirmer son lien puis actualiser le statut dans l'application. L'accès doit s'activer sans intervention dans Firebase. Enregistrer le profil et vérifier une sauvegarde cloud ; sur le second téléphone, se connecter au même compte et contrôler la sauvegarde avant toute restauration explicitement choisie. Les essais de messagerie déjà réalisés sur Test ne constituent pas une preuve de ce parcours avec le nouveau projet officiel.

Ne pas annoncer cet essai réussi sur la seule base des tests d'émulateur ou du HTTP 200 du gestionnaire de confirmation. La publication reste en attente si la réception réelle du mail ou l'activation du compte n'est pas confirmée. Le 8 novembre, Romain redemande le contrôle complet et confirme que la mise à jour pourra être disponible après réussite ; ne pas lui redemander une autorisation générale de publication.

## Commandes utiles, depuis un environnement de déploiement autorisé

Les règles déjà publiées correspondent à `social/official/firestore.rules`. Les index ne nécessitent aucune configuration complémentaire (`indexes: []`). Pour un déploiement contrôlé : `firebase deploy --only firestore --config social/official/firebase.json --project swu-collection-official`.

Pour la page : `firebase deploy --only hosting --config account-email/firebase.json --project swu-collection-official`. Lire `account-email/README.md` pour la validation et le réglage des modèles, actuellement bloqué côté Firebase.

Ne pas migrer les comptes Test ni leurs données : Romain a confirmé qu'il acceptait de repartir de zéro pour ces deux comptes. Conserver impérativement les collections déjà présentes dans l'officielle. Le compte reste facultatif et une connexion ne remplace jamais automatiquement la collection locale par une copie distante.
