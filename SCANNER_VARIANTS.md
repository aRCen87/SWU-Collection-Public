# Reconnaissance des impressions

La reconnaissance lit l’extension et le numéro imprimés ; elle conserve ce numéro dans la collection et les lots. Elle n’effectue pas de conversion vers le numéro de la version classique.

La source du traitement est l’identifiant de l’illustration du catalogue officiel déjà embarqué. Le préfixe numérique est vérifié contre l’extension ET le numéro reconnus. Les identifiants correspondent aux champs `serialCode` et `variantTypes.variantId` de l’API officielle `https://admin.starwarsunlimited.com/api/card-list`.

Correspondances vérifiées : 01 Standard, 02 Hyperspace, 03 Showcase, 31 Standard Foil, 32 Hyperspace Foil, 06 Standard Prestige, 33 Foil Prestige, 34 Serialized Prestige. Les anciens fichiers SOR utilisent des marqueurs HYP/SHO explicites.

Exemple officiel JTL Gold Leader : 054 Standard ; 316 Hyperspace ; 552 Standard Foil ; 788 Hyperspace Foil ; 1021 Standard Prestige ; 1057 Foil Prestige ; 1093 Serialized Prestige. Ces références figurent dans les tests contre le catalogue embarqué.

Les finitions des premiers sets peuvent partager un numéro. Le visuel téléchargé n’est pas une preuve de la finition physique : ces cas restent ambigus et demandent confirmation. Une illustration inconnue, un identifiant ne correspondant pas à la référence ou un traitement futur non reconnu ne sont jamais devinés depuis une plage de numéros.

Le réglage de secours ne s’applique qu’aux choix compatibles d’une impression ambiguë. Il ne remplace jamais une variante déterminée par la référence. En scan en série, les cas ambigus restent en confirmation avant d’entrer dans le lot. Les images caméra sont traitées par le pipeline OCR existant ; aucune analyse spéculative de brillance n’est utilisée.
