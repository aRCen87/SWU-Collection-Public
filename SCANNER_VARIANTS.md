# Reconnaissance des impressions

La reconnaissance lit l’extension et le numéro imprimés ; elle conserve ce numéro dans la collection et les lots. Elle n’effectue pas de conversion vers le numéro de la version classique.

La source du traitement est l’identifiant de l’illustration du catalogue officiel déjà embarqué. Le préfixe numérique est vérifié contre l’extension ET le numéro reconnus. Les identifiants correspondent aux champs `serialCode` et `variantTypes.variantId` de l’API officielle `https://admin.starwarsunlimited.com/api/card-list`.

Correspondances vérifiées : 01 Standard, 02 Hyperspace, 03 Showcase, 31 Standard Foil, 32 Hyperspace Foil, 06 Standard Prestige, 33 Foil Prestige, 34 Serialized Prestige. Les anciens fichiers SOR utilisent des marqueurs HYP/SHO explicites.

Exemple officiel JTL Gold Leader : 054 Standard ; 316 Hyperspace ; 552 Standard Foil ; 788 Hyperspace Foil ; 1021 Standard Prestige ; 1057 Foil Prestige ; 1093 Serialized Prestige. Ces références figurent dans les tests contre le catalogue embarqué.

Les finitions des premiers sets peuvent partager un numéro. Le visuel téléchargé n’est pas une preuve de la finition physique : ces cas restent ambigus et demandent confirmation. Une illustration inconnue, un identifiant ne correspondant pas à la référence ou un traitement futur non reconnu ne sont jamais devinés depuis une plage de numéros.

Le réglage de secours ne s’applique qu’aux choix compatibles d’une impression ambiguë. Il ne remplace jamais une variante déterminée par la référence. En série, les références reconnues dont la langue ou la finition reste indécidable entrent dans le lot en attente de vérification après « Terminer ». La validation en collection est bloquée tant que ces choix ne sont pas résolus. Les copies d’une même référence peuvent être réparties entre plusieurs langues et finitions.

Depuis 0.8.9, si la référence reste illisible ou inconnue, une comparaison visuelle locale utilise un index compact des illustrations officielles. Les formats portrait et paysage sont recherchés automatiquement, avec compensation du recadrage caméra, de sa rotation et de petits écarts de taille. Deux résultats visuels cohérents sont nécessaires. Une correspondance visuelle ne devient une référence exacte automatique qu’avec un fragment de pied de carte contenant la langue et le numéro, sans référence contradictoire. Les images seules ne prouvent jamais la langue ou la finition. Les images caméra ne quittent pas le téléphone.

La langue du pied de carte est prioritaire ; à défaut, au moins trois mots de règles distinctifs et une marge suffisante sur les autres langues peuvent l’identifier. Un nom propre, la langue du téléphone ou la langue du visuel de référence ne suffisent pas. Les images seules qui restent ambiguës ne déclenchent pas de fenêtre à chaque passage. La pause et la relance invalident les traitements encore en cours et la référence retenue est bloquée contre un double ajout immédiat. Aucune analyse spéculative de brillance n’est utilisée.

Les visuels français absents ou attribués à une mauvaise langue par la source sont contrôlés à nouveau dans les données de l’éditeur. Lorsqu’aucun visuel exact dans la langue choisie n’est disponible, l’affichage utilise une autre langue en l’indiquant sur l’image ; la langue et la finition enregistrées ne changent pas. L’index visuel est régénéré avec les mises à jour du catalogue.

Depuis 0.8.16, une lecture de la même référence dont la langue est momentanément illisible est neutre pour la confirmation de langue. Deux lectures effectives de la même langue restent nécessaires et celle-ci doit être lisible dans la dernière image validée. Une langue contradictoire repart de zéro. La confirmation régionale d’un pied de carte déjà repéré peut être tentée après 600 ms au lieu de 1 800 ms ; les recherches sans candidat restent espacées de 1 800 ms. Ces intervalles ne garantissent pas un temps de reconnaissance sur le téléphone.
