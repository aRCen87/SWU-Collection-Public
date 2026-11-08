# Relais privé des notifications Test

Le client Test23 peut enregistrer son jeton FCM privé et recevoir les alertes de messages, demandes/acceptations d’amis et changements d’échange. Ces fonctions vérifient comptes actifs, destinataire, amitié, blocages, réglages, sourdine et lectures. Les données envoyées ne contiennent ni texte du message ni pseudo ni avatar. Les événements et appareils dédupliquent les alertes ; les jetons invalides sont retirés.

Le 8 novembre 2026, le projet swu-collection-test est sur Spark. Aucune fonction n’est donc déployée et aucune facturation n’est activée. L’application conserve les notifications périodiques et les alertes lorsque son processus observe les événements. Ne pas présenter ces dernières comme des notifications push disponibles appli fermée.

Après accord explicite du propriétaire sur la facturation, connecter le projet Test à Blaze, installer les dépendances avec npm ci dans functions, puis déployer uniquement les trois fonctions de cette source vers swu-collection-test. Ne pas déployer vers le projet officiel. Vérifier une notification sur deux téléphones avec deux comptes de test consentants avant d’annoncer le relais actif.

Les tests policy.test.js utilisent exclusivement des données fictives. Les règles Firestore supplémentaires sont testées dans demo-swu-community-test. Les événements pushEvents et les jetons ne sont jamais lisibles par d’autres joueurs.
