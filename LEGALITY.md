# Statuts Premier automatiques

Le workflow privé `legality.yml` contrôle les sources officielles toutes les six heures (planification GitHub, retards possibles). Il publie uniquement `legality.json` dans le dépôt public de distribution. Aucun code source, jeton ou donnée de collection ne part dans ce flux.

Sources :

- Chapitre officiel Rules via `https://admin.starwarsunlimited.com/api/game-info-chapters/2?locale=en&populate=deep,4` : section Premier de Suspended Cards, pour les suspensions et leurs levées.
- Guide d’événements lié depuis `https://starwarsunlimited.com/organized-play` : liste des produits autorisés en Premier, pour la rotation. Intro Battle: Hoth est inclus.

Le guide PDF peut contenir une liste de suspensions différente ; le flux utilise exclusivement la section dédiée du site pour les suspensions. Les deux sources ont des rôles distincts.

Le générateur rejette les listes ambiguës, les limites manquantes, les références absentes du catalogue et les produits inconnus non résolus. L’échec ne publie rien et laisse le dernier flux en place. Les noms de produits futurs sont résolus via la base officielle lorsque possible ; toute modification de structure non reconnue nécessite une maintenance du générateur, pas une réinstallation de l’application.

Le téléphone vérifie le flux à l’ouverture (au maximum toutes les six heures hors bouton manuel) et via WorkManager toutes les six heures avec réseau. Android peut retarder les tâches. Après un échec, les tentatives automatiques sont espacées de quinze minutes au minimum. Les règles téléchargées sont validées et enregistrées avec AtomicFile, indépendamment de la collection. La dernière règle valide et une copie embarquée restent disponibles hors connexion. Un ancien flux ne remplace pas un plus récent. Une liste de suspensions explicitement vide lève les suspensions précédentes. Une erreur de téléchargement ou de structure ne vide jamais cette liste.

Les badges et les paramètres affichent la date du contrôle officiel et le résultat de synchronisation. Un contrôle officiel de plus de 48 heures est signalé après récupération. Les rééditions sont reconnues par nom complet, sous-titre inclus, et type, à partir du catalogue local et des identités de cartes Premier contenues dans le flux. Le téléphone peut donc reconnaître une nouvelle réédition même avec un catalogue plus ancien. Le générateur exige que chaque produit autorisé soit présent dans le catalogue serveur. Les variantes d’une carte suspendue sont reconnues par identité et référence. Aucun changement du schéma, des quantités, de la signature ou de l’identifiant Android.
