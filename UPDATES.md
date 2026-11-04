# Mises à jour de SWU Collection

Les notifications Android sont demandées une seule fois et restent réglables dans Paramètres. Chaque nouvelle version stable est notifiée une fois ; une version plus ancienne qu’une version déjà notifiée ne déclenche pas d’alerte. Toucher la notification ouvre directement les paramètres, y compris lorsque l’application redémarre. Une alerte dans l’application reste disponible sans autorisation de notification.

L’application vérifie les versions à son ouverture et à son retour au premier plan. WorkManager vérifie aussi en arrière-plan environ une fois par heure, avec une connexion. Android peut différer ces contrôles en veille ou en économie de batterie. Aucun téléchargement ni remplacement de l’application n’a lieu sans action de l’utilisateur.

Le flux public stable est `aRCen87/SWU-Collection-Releases`. Les brouillons et préversions sont ignorés. Le téléphone vérifie paquet, version croissante, taille, SHA-256 et signature permanente. Il sauvegarde atomiquement la collection et le mode d’affichage des leaders avant l’installateur Android et relit la sauvegarde pour vérifier son écriture. Le nom `swu_collection.db`, le schéma v4 et l’applicationId sont conservés. Aucun chemin d’installation ne désinstalle l’application ou n’efface ses données.

La sauvegarde avant mise à jour peut être exportée dans Paramètres. Sa restauration remplace l’état courant par l’instantané après confirmation, sans additionner les quantités. L’import externe existant conserve sa fusion. Une sauvegarde invalide est refusée avant tout effacement et la restauration utilise une transaction SQLite. La copie locale ne protège pas d’une désinstallation ou de la perte du téléphone ; son export permet d’en conserver une copie externe.

## Publication automatique après finalisation

Une intégration finalisée à main déclenche le workflow Android. Après les tests et la compilation signée, un job de publication distinct récupère l’APK du même run. Il vérifie les empreintes, l’identité, la version et le certificat et crée la version publique en brouillon. La version devient visible seulement après vérification des octets déposés chez GitHub. La même version n’est pas remplacée ; une réexécution des mêmes sources applicatives est sans effet. Toute nouvelle modification applicative doit augmenter les deux numéros de version.

La configuration exige une seule autorisation de publication : un jeton GitHub à accès fin limité au dépôt `SWU-Collection-Releases`, avec Contents: lecture/écriture, enregistré comme secret Actions `RELEASES_TOKEN` dans le dépôt privé `SWU-Collection`. Le jeton n’a pas besoin d’accès au code privé ; le publicateur n’y utilise pas ce jeton. Le téléphone ne reçoit aucun secret. Le code source et la clé de signature restent privés ; seules les versions signées et leurs métadonnées publiques sont distribuées.

Tant que ce secret n’est pas configuré, le job publish échoue explicitement et l’activation n’est pas terminée. Après configuration, relancer uniquement le job de publication échoué permet de publier l’artefact signé déjà validé sans recommencer une modification de code.

La version 0.3.3 déjà installée peut proposer la première mise à jour 0.3.4 depuis Paramètres dès publication. 0.3.4 ajoutera les notifications en arrière-plan et la restauration de sauvegarde ; aucun nouvel APK manuel ne doit être imposé si ce flux est disponible.

## Vérifications

Tests de versions/empreintes, déduplication des notifications, ouverture vers les paramètres, cache sans retour arrière, réouverture SQLite sans perte des cartes/variantes/quantités/accents, restauration exacte et répétable avec réglages, refus d’un instantané invalide sans effacement. Les tests SQLite et notifications utilisent Robolectric ; ils ne remplacent pas une vérification sur le téléphone réel. Le publicateur est aussi vérifié sur un APK réellement signé avec la clé permanente.
