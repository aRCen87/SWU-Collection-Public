# Revue des parcours — Test 18

Périmètre : accueil et navigation, collection et variantes, scan unitaire/rafale/deck, extensions et objectifs, listes/decks/import/export/proxies, échanges/prêts, comptes/profils/messagerie, compteur, badges/statistiques, sauvegardes et mises à jour. Revue du code et des routes, tests de données et des accès, puis contrôles Android et essais d’émulateur dans la chaîne de validation. La reconnaissance optique de cartes physiques et le comportement exact du téléphone restent à essayer sur l’appareil.

## Simplifications appliquées

- Messagerie dans une destination unique, ouverte depuis l’enveloppe ou une notification ; Messages et Amis ne doublonnent plus dans les onglets du compte. Les actions Message et Ajouter un ami des autres parcours aboutissent au même espace.
- Grand bouton et entrée de menu du compteur retirés ; seul le cœur près de la recherche sert de raccourci. Choix 1 ou 2 joueurs dans le compteur, sans dupliquer les réglages.
- Titres Communauté/Échanges répétés sous l’en-tête retirés.
- Bouton « Voir le lot » des options du scanner retiré : « Vérifier la liste » et « Terminer » restent accessibles dans le parcours principal.
- Entrée de gestion de collection renommée « Vérifier et gérer ma collection » pour annoncer les corrections, les lots et les disponibilités.
- Description Apparence mise à jour pour inclure la personnalisation de l’accueil.
- Anciennes mentions d’un onglet Amis du compte remplacées par l’ouverture de Messagerie → Amis.

## Éléments conservés volontairement

- Les raccourcis d’accueil, la barre de navigation et le menu ne créent pas plusieurs copies des données ; ils servent de points d’entrée selon le contexte.
- Sauvegarde du compte et export de fichier répondent à deux usages : changement de téléphone avec le compte, et copie indépendante sans connexion. Le CSV reste rangé parmi les options secondaires.
- Les variantes et langues de collection ne sont pas des doublons à supprimer. Les corrections regroupent seulement les références/finitions/langues identiques après confirmation.
- Les listes personnelles, decks et cartes favorites de vitrines sont des objets différents ; aucune liste existante n’est supprimée.
- La suppression d’un ami ne supprime ni son compte ni l’historique des conversations.
- Les données héritées de QR sont compatibles avec les anciennes sauvegardes ; aucune interface de QR n’est réintroduite.

## Vérifications ciblées

- Scan ambigu : références illustrées distinctes ; pas d’ajout par simple ressemblance.
- Correction : quantités, regroupement, annulation, prêts et contrôle des lignes devenues obsolètes.
- Échange : deux comptes émulés, amitié confirmée, offres/recherches, proposition, acceptation, messages/réponse/lecture et retrait d’ami. Remise locale atomique, sauvegarde et protection contre une seconde application.
- Sauvegarde : état courant comparé à la copie vérifiée ; séparation des comptes et restauration explicite.
- Compteur : modes 1/2, conservation des deux états, manche solo annulable, ancien format, restauration et validation des valeurs.
- Navigation : accueil/messagerie/retour, cœur près de la recherche et changement de dimensions de fenêtre ; guide en trois étapes conservant son étape lors des redimensionnements.

La publication d’un APK et de son annonce reste conditionnée au succès de la chaîne Android et à la vérification du fichier signé.
