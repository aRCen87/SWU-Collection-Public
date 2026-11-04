# Comparaison hors ligne

Dans Échanges, choisir le périmètre et les versions acceptées, puis afficher ses QR codes. L’autre téléphone choisit « Scanner ceux d’un ami » et reçoit tous les codes ; un compteur indique ceux qui manquent. Les codes bouclent automatiquement toutes les 1,8 secondes et peuvent aussi être choisis manuellement, en pause. Inverser ensuite les rôles. Chaque téléphone calcule les deux directions avec les mêmes profils figés. « Nouvelle comparaison » actualise le profil avec les réglages et la collection courants.

Les deux appareils doivent avoir une version compatible (0.5.0 ou suivante). Un ancien QR de connexion Wi-Fi est refusé avec une invitation à mettre l’autre application à jour. Aucun Wi-Fi, Bluetooth, compte, serveur ou socket n’intervient dans ce transfert ; le lecteur ML Kit est fourni dans l’application. L’export/import JSON reste disponible.

Les QR transportent directement les surplus et manquantes sélectionnés. Ce sont des données compressées, pas chiffrées : le destinataire ou une personne qui photographie les codes peut conserver ce profil. Aucune photo de caméra n’est enregistrée et aucune quantité de collection n’est modifiée.

Le format SWUTRADE2 utilise une table de chaînes partagées et des lignes compactes, GZIP puis Base64 URL. Chaque fragment comprend l’empreinte SHA-256 du profil compressé, le nombre total et son index. Réception dans n’importe quel ordre, déduplication, refus d’un autre profil pendant une réception, vérification de l’empreinte et du schéma avant comparaison. SHA-256 vérifie l’intégrité ; il n’authentifie pas l’identité de l’ami. Limites : 700 caractères de charge par QR, 1 024 fragments, 512 Kio compressés, 4 Mio décompressés et validation du format portable existant. Réduire le périmètre ou utiliser un fichier pour un profil trop volumineux.

Les tests couvrent les deux sens d’un échange, les accents et variantes, une collection de 8 453 références, la reconstruction hors ordre, les doublons, les profils mélangés ou altérés, les limites de décompression et le décodage de véritables images QR. La lisibilité en conditions réelles dépend de la caméra, de la lumière et de l’écran ; le défilement manuel reste disponible.


Depuis la version 0.5.0, les profils portables et optiques utilisent le schéma 2 afin de conserver les variantes acceptées pour chaque besoin. Les anciens profils de schéma 1 restent lisibles. Les objectifs sont communs aux écrans de manquantes et d’échanges ; les versions sont regroupées dans leur extension et les références exactes ne sont jamais modifiées dans la collection.
