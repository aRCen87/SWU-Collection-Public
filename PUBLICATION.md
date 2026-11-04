# Copie publique de SWU Collection

Cette copie prépare la version 0.8.7 (code 48), incluant les decks en brouillon visibles et leurs actions Modifier/Supprimer. Elle ne contient pas l'ancien historique. Le dépôt privé original reste inchangé.

## Avant la première publication

1. Dans les réglages GitHub / Emails, activer « Keep my email addresses private » pour les opérations web futures. Le pseudo public est aRCen87.
2. Créer le nouveau dépôt public aRCen87/SWU-Collection-Public. Utiliser une nouvelle base de code, sans importer ni fusionner l'ancien historique.
3. Importer les sources de cette archive. Le bundle fourni conserve un unique commit initial sous pseudo et adresse noreply ; il n'a aucun parent de l'ancien dépôt.
4. Configurer les secrets du nouveau dépôt dans l'interface sécurisée GitHub : ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, RELEASES_TOKEN. Réutiliser exclusivement la clé Android permanente existante et son mot de passe. Un secret d'un ancien dépôt n'est pas automatiquement transmis au nouveau. Ne jamais mettre leurs valeurs dans un fichier du dépôt, une issue, un message ou un artefact.
5. Autoriser explicitement les outils connectés sur le nouveau dépôt si l'installation GitHub est limitée aux dépôts sélectionnés.
6. Exécuter les tests Android, lint, compilation optimisée et parcours émulateur avant toute publication d'APK. Les 33 tests Python ont réussi localement ; cela ne remplace pas la validation Android encore à effectuer.

## Compatibilité

- ApplicationId inchangé : fr.swucollection.app.
- La même clé permanente doit signer les mises à jour pour remplacer l'application installée sans désinstallation.
- Les versions restent distribuées dans aRCen87/SWU-Collection-Releases. Les liens publics existants restent utilisables.
- La référence du catalogue et le garde-fou du publicateur pointent vers aRCen87/SWU-Collection-Public.
- Le workflow n'envoie pas le mapping R8 lorsque le dépôt est public.
- L'ancien dépôt, ses branches, journaux et artefacts privés ne sont pas copiés.

Cette archive est une préparation de sources : aucun nouveau dépôt n'a encore été créé et la version 0.8.7 n'est pas publiée.
