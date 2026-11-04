# Droits et protection de SWU Collection

État au 1er novembre 2026. Ce document décrit les protections mises en œuvre et les démarches qui restent distinctes du code. Il ne certifie pas une conformité juridique globale.

## Protections applicatives

- L’historique du dépôt original reste privé. Cette copie du code prépare une diffusion publique sans cet historique. Les APK publics ne doivent contenir aucun secret, aucune clé privée et aucune collection personnelle.
- La compilation release active R8 et la réduction des ressources. Cela complique l’analyse du code, sans interdire techniquement la copie de l’APK. Les catalogues et images restent extractibles.
- Les noms et constructeurs des deux workers sont préservés pour permettre à WorkManager de reprendre les tâches déjà enregistrées avant une mise à jour. L’applicationId, les noms des préférences, le nom de la base et son schéma ne changent pas.
- Le fichier de correspondance R8 reste dans l’artefact du dépôt privé ; le publicateur ne publie que l’APK et les métadonnées prévues.
- Les téléchargements de mises à jour sont déjà vérifiés par taille, SHA-256, identité du paquet, version croissante et certificat de signature. L’installateur Android effectue aussi ses contrôles. La clé permanente est conservée dans les secrets de compilation. Ne jamais remplacer cette clé pour une mise à jour.
- Le manifeste refuse les connexions HTTP en clair. Les communications prévues utilisent HTTPS.
- Les licences de dépendances sont générées par le plugin officiel Google OSS à chaque compilation et consultables dans À propos. Vérifier les notices particulières lorsqu’une dépendance est ajoutée ; la génération ne vaut pas audit de tous les droits.

## Périmètre des droits revendiqués

Les mentions réservent uniquement les droits réellement détenus sur les contributions originales protégeables du projet. Les idées, fonctionnalités générales, éléments générés automatiquement sans apport humain protégeable et œuvres de tiers ne sont pas revendiqués. Ne pas présenter l’ensemble du code ou des logos générés avec une IA comme nécessairement couvert par un droit exclusif. Les composants tiers gardent leurs licences et les exceptions légales restent applicables.

Le dépôt original privé et ses commits conservent un historique. Aucun dépôt e-Soleau, de marque ou de brevet n’a été effectué dans ce travail. Une preuve horodatée ne crée ni originalité, ni droit sur les contenus de tiers.

## Contenus du jeu et données de prix

Les mentions officielles de Star Wars Unlimited réservent les droits sur les illustrations, textes et marques et exigent une autorisation pour les réutilisations concernées. Aucune autorisation Lucasfilm/FFG n’a été trouvée dans le projet. Les crédits, la gratuité, l’utilisation de liens vers un CDN et la mention « non officielle » ne remplacent pas cette autorisation. Clarifier ce périmètre avant une diffusion à d’autres utilisateurs ou une commercialisation, ou adapter les contenus utilisés.

Team-Zura annonce CC0 1.0 pour son dépôt de données structurées ; cela ne peut concéder des droits qu’il ne possède pas sur les illustrations, textes ou marques du jeu. Les contributions et la source sont citées.

Cardmarket annonce publiquement le téléchargement de ses catalogues et guides de prix. Cette mise à disposition ne suffit pas à conclure à une licence illimitée de redistribution dans un APK. La réutilisation et la redistribution de l’instantané fourni dans le projet restent à clarifier auprès du titulaire avant une diffusion élargie. Aucun partenariat n’est déclaré.

## Confidentialité

Les mentions décrivent la collection locale, les caches, les exports, les sauvegardes Android autorisées par le manifeste, les requêtes GitHub/Cardmarket/hébergeurs d’images, et les métriques Google ML Kit. Elles n’affirment pas qu’aucune donnée ne quitte le téléphone : Google documente la transmission de données techniques distinctes des images et résultats OCR.

Le responsable du projet est nommé. Le lien de contact existant pointe vers les issues du dépôt public de versions, dont l’activation a été vérifiée. Ne pas y demander de données privées. Avant une diffusion élargie, prévoir un contact privé utilisable pour les droits et retraits, compléter les coordonnées réglementaires selon le statut de l’éditeur et préciser les bases légales, durées, transferts et modalités d’exercice des droits pour les traitements effectivement retenus. Les réglages de consentement éventuellement nécessaires aux métriques du SDK doivent être évalués avant cette diffusion ; aucune exemption n’est présumée.

## Références consultées

- INPI, droit d’auteur : https://www.inpi.fr/ressources/propriete-intellectuelle/droit-dauteur
- CNIL, sécurité des applications mobiles : https://www.cnil.fr/fr/securite-applications-mobiles-conception-et-developpement
- Jeu : https://starwarsunlimited.com/fr/legal et https://starwarsunlimited.com/fr/terms-of-use
- Données communautaires : https://github.com/Team-Zura/swu-cards-json
- Guide Cardmarket : https://news.cardmarket.com/en/Magic/were-making-the-price-guide-and-product-catalogue-available-for-download
- ML Kit : https://developers.google.com/ml-kit/terms et https://developers.google.com/ml-kit/android-data-disclosure
- Licences : https://developers.google.com/android/guides/opensource
