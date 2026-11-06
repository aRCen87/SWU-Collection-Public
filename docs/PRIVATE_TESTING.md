# SWU Collection Test

La version officielle reste disponible dans SWU-Collection-Releases. La copie de test est une variante Android séparée (`fr.swucollection.app.test`), nommée SWU Collection Test et marquée d’un T sur son icône. Installation simultanée possible ; base SQLite, préférences, fichiers et autorité FileProvider propres à chaque paquet. Aucun accès automatique aux données de l’officielle : utiliser son export puis importer volontairement une copie dans Test. Les données réelles ne sont jamais effacées ou fusionnées automatiquement.

Les contrôles et installations de mises à jour publiques sont désactivés dans Test. Les futurs comptes/services cloud restent à développer ; cette première copie conserve les fonctionnalités actuelles, sans connecter de projet Firebase ou Cloudflare. Les futurs projets de test devront être distincts et leurs accès limités au compte de Romain avant connexion de l’application.

Les APK de test sont compilés et conservés comme artefacts dans le dépôt privé aRCen87/SWU-Collection. Ne pas les publier dans le dépôt de versions public. La distribution privée limite les téléchargements aux personnes autorisées sur ce dépôt ; elle ne constitue pas un verrou matériel empêchant de recopier un APK déjà téléchargé.

La publication officielle exige désormais un lancement manuel du workflow avec confirmation explicite, après accord de Romain. La variante officielle conserve son identifiant et sa signature permanente. Les APK existants et leurs fichiers de métadonnées ne sont pas modifiés. La branche backup/official-before-private-tests conserve les sources de référence ; elle ne contient aucune collection personnelle.
