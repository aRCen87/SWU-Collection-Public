# Appli Playstore

Édition distincte : `fr.swucollection.app.playstore`, version initiale 1.0.0 (code 1), nom Android « Appli Playstore ».

La variante `playstore` conserve le scanner, le catalogue, les cotes, les collections, decks, listes, proxies et sauvegardes locales exportables. L’accès Internet reste disponible pour le contenu. Les comptes, amis, messagerie, échanges entre membres, sauvegardes cloud, présence et notifications sociales sont désactivés par constante de compilation et bloqués par la politique des paquets. Les points d’entrée sociaux sont retirés ; le fournisseur Firebase et le service de messagerie sont retirés du manifeste, ainsi que l’installation d’APK et les liens de connexion.

Les données restent dans le stockage privé de cette installation ; aucune migration ou modification des collections de l’officielle n’est réalisée. Les mises à jour applicatives de cette édition passent par Google Play. Le compte développeur, la fiche Play Console et la publication ne font pas partie de cette préparation. Le bundle de validation CI est non signé ; une compilation signée avec la clé de distribution choisie sera nécessaire avant dépôt dans Play Console.

Vérification : `gradle testPlaystoreUnitTest lintPlaystore bundlePlaystore`. Le workflow `Appli Playstore validation` contrôle également le manifeste fusionné. Ne pas présenter l’édition comme publiée tant qu’aucune publication Google Play n’a été faite.
