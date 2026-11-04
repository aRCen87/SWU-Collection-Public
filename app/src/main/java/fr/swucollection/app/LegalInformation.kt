package fr.swucollection.app

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity

/** Informational notices. Third-party rights are never claimed by the publisher. */
@Composable
internal fun LegalInformationPanel() {
    val context = LocalContext.current
    val links = LocalUriHandler.current
    @Suppress("DEPRECATION")
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    Text("SWU Collection", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Text("Version $version • Informations du 2 novembre 2026",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Application indépendante pour gérer une collection Star Wars Unlimited. " +
        "Non officielle, non affiliée et non approuvée par Lucasfilm, Disney, Fantasy Flight Games ou Cardmarket.")
    LegalSection("Éditeur et conditions d’utilisation") {
        Text("Éditeur du projet : aR_Cen (Romain C.).")
        LegalLink("Contacter le projet / signaler un problème",
            "https://github.com/aRCen87/SWU-Collection-Releases/issues")
        Text("Ce canal GitHub est public : n’y joins aucune donnée personnelle, sauvegarde de collection ou clé privée.")
        Text("L’utilisation personnelle de l’application est autorisée. Les droits sur les contributions originales " +
            "protégeables du projet sont réservés à leurs titulaires. Leur reprise, modification ou redistribution " +
            "requiert l’autorisation du titulaire concerné, sauf exception légale ou licence applicable. " +
            "Cette réserve ne porte ni sur le concept d’un gestionnaire de collection, ni sur les contenus de tiers, " +
            "ni sur les éléments qui ne sont pas protégeables.")
        Text("Les composants tiers restent soumis à leurs propres licences. Les droits et exceptions prévus par " +
            "la loi, notamment en matière d’interopérabilité, restent applicables.")
        Text("Le catalogue, la reconnaissance et les prix peuvent comporter des erreurs. Vérifie la référence " +
            "avant un ajout et le prix avant une transaction. Les cotes sont indicatives, ne constituent pas une " +
            "offre d’achat et peuvent être anciennes ou indisponibles. Aucune valeur de revente n’est garantie.")
        Text("Exporte régulièrement une sauvegarde depuis Paramètres. Une désinstallation ou la perte du " +
            "téléphone peut supprimer les données locales. Ces informations ne limitent aucun droit impératif de l’utilisateur.")
    }
    LegalSection("Confidentialité et autorisations") {
        Text("Collection et réglages : les cartes, variantes, quantités et préférences sont enregistrées sur le " +
            "téléphone pour assurer le suivi de la collection. Le code de l’application ne les envoie pas à un " +
            "serveur de l’éditeur et ne crée aucun compte utilisateur.")
        Text("Caméra : elle sert à reconnaître le texte des cartes et à scanner les QR codes de comparaison. Les images et " +
            "le texte reconnus sont traités sur l’appareil ; l’application ne les téléverse pas et ne conserve " +
            "pas de photos du flux. L’autorisation peut être retirée dans les réglages Android ; la saisie manuelle reste disponible.")
        Text("Comparaison avec un ami : les QR codes contiennent directement les surplus et manquantes choisis. Chacun scanne les codes de l’autre, sans réseau ni serveur de collection. Ces codes ne sont pas chiffrés : une personne qui les photographie peut lire et conserver ce profil, comme un fichier exporté. Aucun transfert de cartes ni changement de quantité n’est effectué.")
        Text("Composant Google ML Kit : Google indique recevoir des informations techniques sur l’appareil et " +
            "l’application, des identifiants d’installation et des mesures d’utilisation, de performance et d’erreur. " +
            "Ces échanges techniques sont distincts des images et du texte reconnus. ML Kit peut aussi contacter " +
            "Google pour sa maintenance et sa configuration. Consulte les informations de Google ci-dessous.")
        LegalLink("Données techniques ML Kit", "https://developers.google.com/ml-kit/android-data-disclosure")
        LegalLink("Conditions et confidentialité ML Kit", "https://developers.google.com/ml-kit/terms")
        LegalLink("Politique de confidentialité Google", "https://policies.google.com/privacy?hl=fr")
        Text("Internet : GitHub fournit les vérifications de versions, les APK et les mises à jour du catalogue ; " +
            "Cardmarket fournit le guide de prix ; les hébergeurs d’images fournissent les visuels des cartes. " +
            "Ces services reçoivent les données nécessaires aux requêtes, notamment l’adresse IP et la ressource " +
            "demandée ; GitHub reçoit aussi la version dans l’identifiant de l’application. Ouvrir un lien " +
            "Cardmarket transmet au site la recherche affichée. Chaque service applique sa propre politique.")
        LegalLink("Confidentialité GitHub", "https://docs.github.com/fr/site-policy/privacy-policies/github-general-privacy-statement")
        LegalLink("Confidentialité Cardmarket", "https://help.cardmarket.com/fr/privacy-policy")
        Text("Notifications : elles sont créées sur le téléphone pour signaler les nouvelles versions et restent " +
            "désactivables dans Android. L’autorisation d’installation sert à ouvrir l’installateur Android après " +
            "ton action ; aucune mise à jour n’est installée silencieusement.")
        Text("Conservation et sauvegardes : les données restent sur l’appareil jusqu’à leur suppression ou " +
            "l’effacement des données de l’application. Des caches de catalogue, de prix et de versions sont " +
            "actualisés ; une sauvegarde locale précède une mise à jour. Les exports sont enregistrés à " +
            "l’emplacement que tu choisis et peuvent contenir toute ta collection. Android peut aussi sauvegarder " +
            "ou transférer les données selon les réglages du téléphone et du compte. Effacer l’application " +
            "ne supprime pas les exports ou les copies externes.")
        Text("Pour consulter ou récupérer la collection, utilise les écrans et exports de Paramètres. Pour " +
            "supprimer les données locales, utilise les réglages Android après avoir conservé les sauvegardes " +
            "souhaitées. Les demandes concernant les données traitées par un service tiers se font auprès de ce service.")
    }
    LegalSection("Crédits, marques et sources") {
        Text("Star Wars et les contenus associés : © & ™ Lucasfilm Ltd. Fantasy Flight Games et son logo " +
            "sont des marques de Fantasy Flight Publishing, Inc. Les illustrations, textes et autres contenus " +
            "des cartes appartiennent à leurs titulaires respectifs. Aucune propriété sur ces contenus n’est revendiquée.")
        Text("La mention « non officielle » et les crédits ne constituent pas une autorisation de reproduction " +
            "ou de redistribution de ces contenus.")
        LegalLink("Mentions de l’éditeur du jeu", "https://starwarsunlimited.com/fr/legal")
        Text("Catalogue : données structurées et traductions issues notamment de Team-Zura/swu-cards-json, " +
            "qui annonce CC0 1.0 pour ses contributions, complétées par les données de l’éditeur du jeu. " +
            "CC0 ne confère aucun droit sur les marques et œuvres de tiers.")
        LegalLink("Source Team-Zura et licence", "https://github.com/Team-Zura/swu-cards-json")
        Text("Cotes : guide public Cardmarket, en euros, avec date de référence. Cardmarket est un service " +
            "de Sammelkartenmarkt GmbH & Co. KG. Aucun partenariat ni accord de réutilisation spécifique " +
            "n’est revendiqué par cette application.")
        LegalLink("Guide de prix Cardmarket", "https://www.cardmarket.com/fr/StarWarsUnlimited/Data/Price-Guide")
    }
    OutlinedButton(onClick = {
        OssLicensesMenuActivity.setActivityTitle("Licences des composants")
        context.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
    }, modifier = Modifier.fillMaxWidth()) { Text("Licences des composants") }
    Text("Les licences des bibliothèques sont rassemblées automatiquement lors de la compilation. " +
        "Certaines notices proposent un lien vers le texte de licence.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    // No account or private source link is necessary to read the notices.
    TextButton(onClick = { links.openUri("https://www.cnil.fr/fr/comprendre-vos-droits") }) {
        Text("Comprendre ses droits sur les données")
    }
}

@Composable
private fun LegalSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text(if (expanded) "$title −" else "$title +", fontWeight = FontWeight.SemiBold)
        }
        if (expanded) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun LegalLink(label: String, url: String) {
    val links = LocalUriHandler.current
    TextButton(onClick = { links.openUri(url) }) { Text(label) }
}



