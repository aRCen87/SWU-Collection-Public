# SWU Collection Playstore

Application séparée : `fr.swucollection.app.playstore`, nom Android « SWU Collection Playstore », version initiale `0.1.0` (code 1). Elle peut coexister avec l’officielle et Test sans toucher à leurs données.

`prepare.py` produit un projet Android autonome depuis les sources courantes sans modifier `app/`. Les transformations vérifient leurs points de départ et s’arrêtent si les sources changent. Les services de compte, Firebase Auth/Firestore, profils, amis, annuaire, messagerie, partage à des amis, échanges connectés et sauvegardes cloud sont supprimés de cette compilation. Les configurations de serveurs et les sources Test sont exclues.

Collection, scanner, variantes/langues, decks, listes, proxies, badges locaux, statistiques, compteur de PV, prêts locaux et sauvegarde/restauration sur fichier restent disponibles. La page Échanges conserve uniquement les outils locaux de collection. Internet reste utilisé pour le catalogue, les cotes, les visuels et les decks méta. Il n’y a pas de connexion à un compte dans l’application.

Les mises à jour applicatives passent par Google Play. Aucun installateur APK, permission d’installation, service de messagerie ou notification de compte n’est inclus.

## Construction

```sh
python3 -m unittest discover -s playstore -p 'test_*.py'
python3 playstore/prepare.py build/playstore-source
gradle -p build/playstore-source --no-daemon testDebugUnitTest lintDebug assembleDebug
gradle -p build/playstore-source --no-daemon testReleaseUnitTest lintRelease assembleRelease bundleRelease
python3 playstore/verify_package.py build/playstore-source/app/build/outputs/apk/release/app-release.apk "$ANDROID_HOME/build-tools/36.0.0/aapt"
```

Le workflow privé utilise la signature existante et conserve l’AAB signé dans un artefact privé. Aucun déploiement officiel et aucun envoi à Google Play n’est effectué automatiquement. La fiche Play Store et ses déclarations de données devront correspondre aux composants réellement inclus, notamment ML Kit.
