# SWU Collection — revue visuelle et simplicité

Travail demandé le 19 novembre 2026, à partir de l'officielle 0.9.6 (sources `3d592af9094e13aa02442484d48fd1fed23a9844`). Branche de préparation : `simplify/official-visual-review-20261119`. Ce document distingue l'inspection du code, les rendus de composants et l'utilisation de l'application publiée.

## Publication suspendue à la demande de Romain

Le 19 novembre 2026, Romain demande de ne pas rendre la mise à jour disponible tout de suite. Continuer les corrections et les vérifications dans une branche de préparation ; conserver la demande d'intégration en brouillon. Ne publier ni APK ni annonce de mise à jour, et ne pas intégrer à main sans un nouvel accord explicite.

## Principes retenus

- Conserver l'accueil restauré, ses illustrations, le bleu et l'or et les logos d'extensions mis en valeur.
- Une action principale par contexte ; actions secondaires dans le menu existant lorsque cela ne masque pas une étape nécessaire.
- Retirer les répétitions de commandes ou de chiffres sur un même écran.
- Garder les raccourcis personnalisables, les fonctions et les données ; un autre point d'entrée n'est pas une deuxième copie à supprimer.
- Préserver les confirmations de retrait, d'abandon, d'échange et de restauration.
- Vérifier les petits écrans, le paysage et les grandes polices ; ne pas réduire les cibles tactiles pour gagner de la place.

## Parcours examinés dans les sources

| Parcours | Décision / changement |
| --- | --- |
| Accueil et recherche d'accueil | Présentation et illustrations conservées. Aucune nouvelle salutation ou nouveau bloc personnel. |
| En-tête et menu | Retirer le bouton Accueil répété dans l'en-tête. Garder Retour et le menu. Ajouter Accueil au menu pour les personnes qui ont retiré ce raccourci de la barre personnalisable. Réduire les espaces décoratifs du menu. |
| Barre basse | Réserver sa hauteur mesurée dans le contenu et placer les confirmations d'ajout au-dessus. Ne plus supposer qu'elle fait toujours 82 dp. |
| Collection possédée | Conserver les vues Visuel, Liste et Miniatures, le regroupement et les actions au toucher/appui long. |
| Catalogue et extensions | Une seule quantité par carte en vue visuelle. Éviter la double progression par rareté lorsque le catalogue affiche déjà son panneau d'extension. |
| Filtres et cartes manquantes | Interrupteur stable « Griser les manquantes » dans Filtres et affichage ; état visible, préférence conservée. Expliquer que la vue Possédées ne contient pas de manquantes. Les reflets de finition ne doivent pas recolorer une image grisée. Désactiver le gris rétablit les illustrations même si le mode silhouettes a été utilisé. |
| Fiche carte et variantes | Préserver la grande image, le choix de langue/finition, les quantités et les onglets Collection/Variantes/Cotes. |
| Zoom, retournement et balayage | Garder les commandes de lecture indépendantes des commandes d'ajout. |
| Scanner unitaire, rafale et deck | Garder destination, validation/correction et fin du lot. Ne pas transformer une reconnaissance ambiguë en ajout automatique. |
| Bibliothèque de decks | Conserver Créer un deck dans la barre et retirer son doublon de l'état vide. Préparer les cartes rejoint le menu du deck ; Modifier reste visible. |
| Construction de deck | Nom affiché une fois en vue simple. Retirer le dock leader/base répété lorsque les emplacements et les compteurs sont déjà visibles dans la vue du deck. Le conserver pendant la consultation du catalogue sur téléphone ; sur Fold, le panneau du deck fournit déjà ces accès. Les onglets Leader/Base/Deck/Réserve répétés au-dessus du catalogue sont retirés de la vue en deux panneaux. Actions secondaires sous ⋮. Deck et Réserve peuvent revenir à la ligne. |
| Consultation du deck | Garder l'aperçu complet, le zoom, les zones principal/réserve et la modification explicite. |
| Mes listes | Un seul Ajouter des cartes, y compris en mode modification. Regrouper cet ajout principal avec Modifier/Terminer ; conserver Renommer et Supprimer dans le mode de modification. |
| Proxies | Retirer le deuxième titre et le retour déjà fournis par l'écran principal. Conserver un retour visible dans les parcours intégrés à un deck. Regrouper PDF, quantité et vidage en bas. Afficher le nombre de filtres actifs. |
| Import, exports et préparation | Garder le choix de format et les aperçus. Ne pas fusionner export de deck et sauvegarde de collection. |
| Tournois et Méta | Garder les onglets et la copie dans Mes decks. Les sources de prix et de méta ne sont pas modifiées. |
| Profil et confidentialité | Regrouper notifications, présence et accusés de lecture dans une rubrique dépliable. Ne changer aucune préférence lors de son ouverture/fermeture. |
| Membres, amis et invitations | Garder recherche, ajout et statut des demandes dans leurs parcours actuels. |
| Discussions et messages partagés | Garder l'envoi au-dessus du clavier et les aperçus des cartes/decks. Aucun changement de protocole ou de stockage. |
| Échanges et prêts | Conserver Je donne/Je reçois, acceptation et remise ; ne pas retirer une confirmation de mutation du stock. |
| Badges | Harmoniser les titres et la recherche sur le mot Badges, en conservant identifiants et progression. |
| Statistiques | Retirer le titre « Statistiques de collection » répété sous l'en-tête. Conserver valeurs, explications et progression par extension. |
| Compteur de PV | Garder les commandes de jeu, options et retour propres à son espace plein écran. |
| Paramètres et apparence | Une liste groupée avec icônes, descriptions secondaires et séparateurs remplace les cinq grands cadres séparés. |
| Sauvegardes et restauration | Conserver compte/fichier/CSV selon leurs usages ; les avertissements de remplacement et de fusion restent nécessaires. |
| Hors connexion | Garder le choix deck/extension, la progression et l'effacement explicite des images. |
| Historique et annulation | Garder la confirmation et la distinction entre historique et stock actuel. |
| Mises à jour et À propos | Ne pas confondre ce chantier visuel avec l'adaptation du canal Google Play ou une certification de confidentialité. |

## Deuxième passe — organisation demandée après les captures

- Sur Fold, les commandes de recherche, filtres et affichage restent dans le panneau du catalogue. Le panneau Composition commence en haut, indépendamment de la recherche.
- Le titre du catalogue indique la destination : Ajouter au deck, Ajouter à la réserve, Choisir le leader ou Choisir la base.
- Scanner devient une action secondaire dans l'en-tête du catalogue. Le menu Affichage et tri regroupe les modes Cartes/Liste et les critères de classement, avec le choix courant indiqué.
- Les paramètres sont classés en Personnalisation, Mes données et Application ; les cinq destinations sont conservées.
- Dans une liste ouverte, un titre compact et une ligne de compteurs remplacent le grand bandeau, pour laisser plus de place aux cartes. Ajouter et Modifier restent directement accessibles.
- La publication demeure suspendue. Les contrôles de disposition sur Fold couvrent la position de la recherche et l'accès au choix Leader/Base ainsi qu'au changement Cartes/Liste.

## État des contrôles

### Fiche carte — simplification autorisée après les captures

- La hauteur de l'image suit le format de la face affichée et la largeur disponible, avec un plafond adapté à l'écran. Les leaders et bases horizontaux ne réservent plus un grand cadre vertical vide. Plein écran, navigation et balayage sont conservés.
- Le toucher retourne toujours le leader ; un seul bouton Deux faces active ou quitte la présentation recto/verso. L'instruction répétée et le bouton Retourner sont retirés.
- La langue et la finition sont regroupées sous le titre, avec leurs choix et des libellés d'accessibilité explicites. Les trois onglets Collection, Variantes et Cotes restent disponibles.
- Un seul compteur affiche le stock de la langue/finition choisie. L'ajout garde sa confirmation explicite ; le total des autres versions n'apparaît que s'il apporte une information supplémentaire.
- L'ajout rapide en haut conserve une cible de 48 dp avec un fond de 36 dp et un symbole de 20 dp.
- L'usage dans les decks est replié par défaut, les listes vides ne sont pas mentionnées. Comparer et Partager sont regroupés dans le menu secondaire. La croix du haut reste le moyen de fermer la fiche ; le second bouton Fermer est retiré.
- Préparation uniquement, sans publication ni modification du schéma de collection.

Contrôles : 17 tests ciblés réussis (langues, session de lecture et vrais écrans), puis le parcours de la fiche relancé après ajout du contrôle FR/EN. Il couvre le choix d'extension, retournement, deux faces, plein écran, comparaison, finition, usages de decks, ajout confirmé, compteur par langue, retrait et navigation précédente/suivante. lintDebug et assembleDebug passent, ainsi que les 67 tests Python et la vérification du catalogue dans le paquet. Le rendu téléphone a été inspecté sur données isolées ; aucun compte réel n'est utilisé et aucune publication n'est effectuée.

### Collection — validation étape par étape sur téléphone standard

Romain autorise l'allègement de la collection après examen de ses deux captures :

- Possédées, Toutes les cartes et Manquantes sont accessibles par le seul menu Afficher. Favoris reste directement visible à côté en portrait.
- Le choix d'extension et son objectif sont accessibles dans Filtres et affichage ; les raccourcis d'extensions du catalogue sont conservés.
- Taille et Silhouettes sont regroupés dans Affichage, dans le dialogue des filtres. Leurs préférences restent appliquées lors du retour aux cartes.
- Après examen du rendu, Romain préfère la taille antérieure du logo : sa zone retrouve 76 dp et dépasse de 20 dp au-dessus du cadre. Ce débordement est réservé dans l'élément pour éviter toute coupe. Le nom et l'accès Raretés partagent une seule ligne afin de compenser l'encombrement ; pourcentage et nombre possédé/total restent à droite. Le détail par rareté reste dépliable et la barre de progression répétée est retirée.
- Les cartes manquantes n'affichent plus ×0, y compris en miniatures. Le nom précède une ligne compacte avec quantité ou état, étoile et petit + ; les cibles tactiles restent confortables, et la ligne peut se replier sur écran étroit.
- Deux colonnes en vue Visuel équilibrée sur téléphone. Les choix explicites Grandes cartes/Compact et le pincement gardent leur effet ; les miniatures gardent leur propre densité.
- Aucun stock, format de sauvegarde ou schéma de données n'est modifié. L'accueil validé reste inchangé, et aucune publication n'est autorisée.

Contrôles de cette passe : 12 tests d'interface réussis, puis les 5 tests UnifiedCollectionUiTest relancés avec succès après le dernier ajustement de densité. lintDebug et assembleDebug réussissent. Les 67 tests Python passent et les trois scripts de navigation mis à jour sont vérifiés syntaxiquement ; leurs parcours sur émulateur ne sont pas revendiqués comme exécutés ici. Les illustrations du catalogue en fixtures restent des images de démonstration.

Après l'ajustement du logo débordant, les 5 tests UnifiedCollectionUiTest et la capture de la vraie page via CollectionPhonePreviewTest passent (6 contrôles ciblés). Le test vérifie que le logo dépasse le cadre et que les raretés restent dépliables. Le rendu téléphone a été inspecté ; aucune version n'est publiée.

### Validation de l'accueil, téléphone standard prioritaire

Romain souhaite conserver l'accueil de sa capture du 19 novembre tel quel. Seuls les trois boutons à droite sont légèrement réduits : fond de 42 dp au lieu de 48 dp, icônes de 22 dp au lieu de 24 dp, avec des cibles tactiles inchangées de 48 dp. Le bandeau, l'avatar, la recherche, les raccourcis et la navigation ne changent pas. Les prochains ajustements se valident écran par écran, d'abord en portrait sur téléphone standard ; le Fold reste une adaptation secondaire. La publication demeure suspendue.

Les modifications ne changent ni identifiant, ni signature, ni schéma SQLite, ni format de sauvegarde, ni règles Firebase.

- 67 tests Python réussis lors de cette passe.
- Deuxième passe : les 7 tests de SimplicityReviewTest passent (paramètres, grandes polices, liste, gris des manquantes, rendus téléphone/Fold/paysage). lintDebug et assembleDebug passent ; les rendus Fold et paramètres ont été inspectés.
- Sur le commit précédent `604d705525c9b22d046bc2db7f0ad4990afe9c02`, la suite complète Debug et la vérification du catalogue ont passé en CI. Le contrôle Release n'est pas validé : lintAnalyzeRelease a échoué par manque de mémoire Java pendant l'analyse de MainActivity.kt. Aucune publication n'est autorisée sur cette base.
- Rendus : fixtures jetables, hors connexion, des composants de production. Les noms, cartes et quantités de démonstration ne sont pas des données d'utilisateur. Les illustrations de fixture servent à contrôler les cadres et les proportions, pas à certifier chaque correspondance du catalogue.
- Aucun essai physique sur le Z Fold de Romain, aucune connexion à un vrai compte et aucun échange réel ne sont revendiqués.
- Aucun changement de l'officielle distribuée n'est annoncé sans publication vérifiée.
