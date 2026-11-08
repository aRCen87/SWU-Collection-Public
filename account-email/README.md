# E-mails de compte SWU Collection

Page Firebase Hosting réutilisable pour Test et, après configuration de son projet distinct, l'officielle. Aucun service d'envoi supplémentaire, fonction Cloud ou abonnement payant.

## État

Le projet officiel `swu-collection-official` existe désormais sur Spark. Le 8 novembre 2026, le nom d'expéditeur **SWU Collection** a été enregistré et vérifié séparément dans les trois modèles : validation d'adresse, réinitialisation du mot de passe et modification d'adresse. Modifier l'objet de validation seul reste refusé par Firebase ; un échec sur cet objet ne signifie donc pas que tous les champs sont bloqués. La page personnalisée n'est pas encore déployée et le lien d'action standard reste actif. Romain a refusé de contacter l'assistance ; ne pas envoyer la demande préparée. Il accepte le nom d'expéditeur enregistré et l'objet français standard pour la sortie officielle. Ne pas annoncer un objet personnalisé, une nouvelle page active ou la réception réelle d'un e-mail sans vérification.

## Déploiement et activation

1. Sélectionner le projet Firebase cible explicitement. Pour l'officielle, créer/configurer d'abord son projet séparé et son authentification ; ne pas réutiliser les comptes Test.
2. Vérifier que le site Hosting par défaut est libre ou examiner sa version active avant tout déploiement, pour ne pas remplacer un autre site.
3. Depuis `account-email`, avec une session Firebase autorisée : `firebase deploy --only hosting --project ID_DU_PROJET`. Ne pas activer Blaze. Le répertoire déployé est uniquement `public/` ; aucun APK, compte, jeton ou clé privée.
4. Dans Authentication → Modèles, sélectionner le français et régler : nom de l'expéditeur **SWU Collection** ; objet de vérification **Confirme ton inscription à SWU Collection**. Utiliser **SWU Collection Test** pour un essai sur le projet Test. Le corps du mail de vérification standard reste celui de Firebase ; aucun logo ni HTML complet n'est promis dans ce mail.
5. Après vérification HTTPS du site, définir l'URL d'action personnalisée sur la racine du domaine Hosting par défaut du projet. Cette URL s'applique aussi à la récupération d'adresse et au mot de passe oublié : la page gère les trois actions.
6. Tester une vraie inscription autorisée, la réception du mail français, un lien valide, un lien réutilisé/expiré, un mot de passe oublié et le retour Android. Ne pas envoyer de mail à un autre joueur sans demande explicite. La réouverture du paquet Android dépend du navigateur ; le texte de secours demande de rouvrir l'appli manuellement si nécessaire.
7. Vérifier dans l'application l'activation après rechargement du statut e-mail. La page ne crée ni profil ni permission Firestore et ne touche à aucune collection. L'activation automatique reste celle de l'application après connexion/actualisation de l'identité.

La configuration publique vient exclusivement du point Hosting réservé `/__/firebase/init.json`. `apiKey` et `continueUrl` fournis par l'URL ne sont jamais utilisés. Aucun lien externe de redirection n'est accepté. Le code est retiré de l'historique, aucun stockage persistant ou outil de suivi n'est utilisé, et aucune erreur ne révèle l'adresse e-mail.

## Vérification locale

`node --test account-email/tests/*.test.mjs`

Les tests injectent les fonctions Firebase pour vérifier l'application réelle du code avant succès, les erreurs et la séparation des opérations. Ils n'envoient aucun e-mail et ne prouvent pas le déploiement ni la configuration des modèles.
