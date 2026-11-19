# SWU Collection — Audit initial Google Play
Date : 19 novembre 2026. Sources examinées : main, commit 3d592af9094e13aa02442484d48fd1fed23a9844.
Configuration source : version 0.9.6, code 101. Ce numéro ne prouve pas que cette version est effectivement distribuée.

## Résultat
La base Android existe déjà. La préparation Play nécessite une adaptation de distribution et la validation des parcours de données et de communauté.
Audit statique ciblé uniquement : aucune compilation, aucun AAB, aucune signature de binaire, aucun test sur appareil, aucune validation de console Google Play ni déploiement dans ce travail. L'application officielle et les services actifs ne sont pas modifiés.

## Constats et actions
| Priorité | Sujet | Preuve examinée | Action / critère de clôture |
|---|---|---|---|
| P0 | Mise à jour externe | AndroidManifest.xml déclare REQUEST_INSTALL_PACKAGES ; AppUpdates.kt télécharge et installe les APK | Préparer une variante Play qui retire cette permission et désactive téléchargement, installation et notifications de mise à jour APK. Conserver les mises à jour de catalogue. Vérifier le manifeste fusionné et les parcours du binaire final. |
| P0 | Identité et signature | applicationId fr.swucollection.app ; signingConfig utilise les variables de signature et alias swu-release | Conserver identifiant et clé permanente. Comparer certificat du dernier APK réellement publié et certificat du binaire préparé ; préparer Play App Signing avec la clé existante si le parcours retenu le permet. Ne pas accepter une nouvelle clé de signature sans évaluer la continuité des installations. |
| P0 | Suppression de compte | Aucun parcours complet de suppression trouvé dans CommunityService.kt et CommunityUi.kt inspectés | Confirmer sur l'ensemble du projet ; fournir un parcours dans l'appli et une ressource web de demande. Définir suppression Auth, profil, sauvegardes et données sociales, gestion des messages partagés et réauthentification. Tester avec des comptes de test seulement. Une simple suppression Auth est insuffisante. |
| P0 | Confidentialité et déclarations | LEGAL.md décrit surtout l'état du 1er novembre, antérieur aux comptes officiels ; dépendances Auth et Firestore présentes | Inventorier données et destinataires réellement utilisés (compte, profil, membres, présence, messages, échanges, sauvegardes, OCR et services externes). Rédiger une politique publique accessible dans l'appli et préparer le formulaire Sécurité des données. Responsable, contact privé, durées et demandes de droits restent à préciser. |
| P0 | Contenus tiers | LEGAL.md indique que les autorisations de contenus Star Wars et de redistribution de prix restent à clarifier | Retrouver les autorisations ou adapter les usages. La mention non officielle ne suffit pas à résoudre les droits. Ce travail ne certifie pas la situation juridique. |
| P1 | Communauté | Blocage de joueur présent dans CommunityService.kt et CommunityUi.kt ; signalement non trouvé dans ces fichiers | Vérifier les autres fichiers, les conditions acceptées avant contribution et les usages publics/privés. Préparer signalement et traitement effectif selon les fonctions exposées ; tester blocage et accès aux données côté règles. |
| P1 | Notifications en arrière-plan | Service RealtimeMessagingService déclaré foregroundServiceType=specialUse | Examiner justification et déclaration Play, comportement de démarrage et arrêt utilisateur. Évaluer une architecture de notification adaptée si cette déclaration ne convient pas. Ne pas conclure à une acceptation Google sur le seul manifeste. |
| P1 | AAB et validation | Workflow android.yml produit assembleRelease et des APK ; aucun bundleRelease dans ce workflow | Ajouter une compilation Play AAB, vérification de signature, analyse du contenu et essais des APK dérivés. Ne pas téléverser de clé ni de configuration privée sur des artefacts publics. |
| P1 | Conservation des données | AGENTS.md impose identité, clé et base swu_collection.db inchangées ; tests de mise à jour prévus dans android.yml | Tester la transition depuis le dernier APK officiel réellement publié vers un APK dérivé du bundle Play, sans désinstallation : cartes, quantités, variantes, decks, listes, préférences et compte. |
| P2 | Fiche et accès à l'examen | Non audités dans Play Console | Préparer titre, description, icône, captures, contact, classement du contenu, audience, accès des examinateurs et informations sur les permissions. |
| P2 | Test fermé | Aucune console Play inspectée | Vérifier le type et l'ancienneté du compte ; pour un nouveau compte personnel, prévoir 12 testeurs inscrits sans interruption pendant 14 jours, puis demande d'accès production. |

## Points favorables confirmés dans les sources
- compileSdk et targetSdk 36 ; minSdk 26.
- Variante Test distincte par suffixe .test.
- Configuration release R8 et réduction des ressources.
- HTTP en clair interdit dans le manifeste.
- Caméra facultative au niveau des fonctionnalités du matériel.
Ces constats ne valent pas validation du binaire final ou conformité globale.

## Ordre de réalisation proposé
1. Établir la provenance exacte du dernier APK officiel, son certificat et la clé disponible sans exposer de secret.
2. Implémenter la variante Play et la compilation AAB, puis vérifier l'absence d'installation d'APK dans ses parcours.
3. Compléter suppression de compte, confidentialité et dispositifs de communauté.
4. Valider les droits, la déclaration du service de notifications et l'inventaire de données.
5. Exécuter les contrôles Android requis et le test de conservation avec un paquet dérivé du bundle.
6. Préparer la fiche puis utiliser une piste de test Play avant toute publication publique.

## Décisions / informations encore nécessaires
- Compte Play existant ou à créer, avec son type ; aucune dépense effectuée.
- Adresse privée de contact à rendre publique et identité d'éditeur à utiliser.
- Justificatifs ou autorisations concernant les contenus tiers.
- Organisation réelle de la modération et du traitement des demandes de suppression.
Aucune clé, mot de passe ou jeton ne doit être transmis dans une conversation ou ajouté au dépôt.

## Références officielles consultées
- Installation de paquets : https://support.google.com/googleplay/android-developer/answer/12085295
- Permissions sensibles : https://support.google.com/googleplay/android-developer/answer/16558241
- Suppression de compte : https://support.google.com/googleplay/android-developer/answer/13327111
- Contenus générés par les utilisateurs : https://support.google.com/googleplay/android-developer/answer/9876937
- Exigences de niveau API : https://support.google.com/googleplay/android-developer/answer/11926878
- Tests des comptes personnels : https://support.google.com/googleplay/android-developer/answer/14151465
