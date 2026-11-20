"""Build a standalone account-free app from the pinned application sources.

The official app is never edited. Exact transformations fail closed on source drift.
"""
from pathlib import Path
import re
import shutil
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'fr.swucollection.app.playstore'
REMOVED = set('''AccountBackups AutomaticAccountBackup CharacterPortrait CommunityBackendPolicy
CommunityLiveConnection CommunityNotifications CommunityService CommunitySocial CommunitySocialUi
CommunityTradeUi CommunityUi ConfirmedTradeUi ConfirmedTrades FriendTradeSuggestions MemberDirectory
MessengerChatUi MessengerPresenceUi MessengerService MessengerUi PlayerShowcase PlayerShowcaseUi
RealtimeMessagingService SearchCardAlerts ShareCardUi AppUpdates UpdateNotifications PreviewUpdates PrivateUpdateBrowser SimpleTradeStock'''.split())


def once(text, old, new=''):
    if text.count(old) != 1:
        raise ValueError(f'Source changed: expected one occurrence of {old[:100]!r}, found {text.count(old)}')
    return text.replace(old, new, 1)


def between(text, start, end, replacement=''):
    a = text.index(start)
    b = text.index(end, a + len(start))
    return text[:a] + replacement + text[b:]


def prepare(destination):
    out = Path(destination).resolve()
    if out == ROOT or ROOT in out.parents and out.parts[len(ROOT.parts):len(ROOT.parts)+1] != ('build',):
        raise ValueError('Use a disposable directory outside the sources, or beneath build/.')
    if out.exists():
        raise ValueError('Destination already exists; choose a fresh directory.')
    out.mkdir(parents=True)
    for name in ('settings.gradle.kts', 'build.gradle.kts', 'gradle.properties'):
        shutil.copy2(ROOT / name, out / name)
    shutil.copytree(ROOT / 'app', out / 'app', ignore=shutil.ignore_patterns('build', '*.apk', '*.aab', '*.jks', '*.keystore'))
    src = out / 'app/src/main/java/fr/swucollection/app'
    removed_symbols = set()
    for name in REMOVED:
        file = src / (name + '.kt')
        data = file.read_text()
        removed_symbols.update(re.findall(r'\b(?:class|object)\s+([A-Z][A-Za-z0-9_]*)', data))
        removed_symbols.update(re.findall(r'^(?:internal |private |suspend |@Composable )*fun\s+(?:[A-Za-z0-9_]+\.)?([A-Za-z_][A-Za-z0-9_]*)', data, flags=re.M))
        file.unlink()
    # Retain the backup-format validator for imported historical offline backups.
    (src / 'TradeReceiptValidation.kt').write_text('''package fr.swucollection.app
import org.json.JSONObject
internal fun validateTradeReceipts(j: JSONObject) {
    require(j.length() <= 10000)
    j.keys().forEach { key -> require(key.matches(Regex("[a-f0-9]{64}")) && j.getLong(key) >= 0) }
}
''')
    removed_symbols.discard('validateTradeReceipts')
    # No social backend resources or alternate app identities enter this project.
    for name in ('preview', 'release', 'androidTest'):
        shutil.rmtree(out / 'app/src' / name, ignore_errors=True)
    (out / 'app/src/main/res/values/community_config.xml').unlink()
    gradle = (out / 'app/build.gradle.kts').read_text()
    gradle = once(gradle, 'applicationId = "fr.swucollection.app"', f'applicationId = "{PACKAGE}"')
    gradle = re.sub(r'versionCode = \d+', 'versionCode = 1', gradle, count=1)
    gradle = re.sub(r'versionName = "[^"]+"', 'versionName = "0.1.0"', gradle, count=1)
    gradle = between(gradle, '        create("preview") {', '    buildFeatures', '    }\n')
    gradle = between(gradle, '// Test keeps its independent Android update sequence.', 'kotlin {')
    gradle = between(gradle, '    signingConfigs {', '    buildTypes {')
    gradle = once(gradle, 'signingConfig = signingConfigs.getByName("release")', 'signingConfig = null')
    gradle = '\n'.join(line for line in gradle.splitlines() if 'firebase' not in line and 'androidTestImplementation' not in line)
    (out / 'app/build.gradle.kts').write_text(gradle + '\n')
    # Keep only scanner/network permissions; no account links, notifications or messaging service.
    android = '{http://schemas.android.com/apk/res/android}'
    manifest_path = out / 'app/src/main/AndroidManifest.xml'
    ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
    ET.register_namespace('tools', 'http://schemas.android.com/tools')
    manifest = ET.parse(manifest_path)
    root = manifest.getroot()
    for permission in list(root.findall('uses-permission')):
        if permission.get(android + 'name') not in {'android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE', 'android.permission.CAMERA'}:
            root.remove(permission)
    app = root.find('application')
    app.set(android + 'label', 'SWU Collection Playstore')
    for service in list(app.findall('service')):
        app.remove(service)
    for activity in app.findall('activity'):
        for intent in list(activity.findall('intent-filter')):
            if intent.find('data') is not None:
                activity.remove(intent)
    manifest.write(manifest_path, encoding='utf-8', xml_declaration=True)

    p = src / 'MainActivity.kt'
    s = p.read_text()
    s = between(s, '    private val communityRequest', '    override fun onStop()')
    s = once(s, '        CommunityLiveConnection.activityVisible=false\n')
    s = once(s, '        if (AppChannel.supportsExperience(packageName)) AutomaticBackupWorker.enqueue(applicationContext)\n')
    s = s.replace('        handleCommunityIntent(intent)\n', '')
    s = s.replace('        if (intent.action == UpdateNotifications.ACTION_OPEN_UPDATES) updateNavigationRequest.intValue++\n', '')
    s = re.sub(r'        if \(PreviewUpdateNotifications.consumeDownloadRequest\(this, intent\)\) \{\n.*?\n        }\n', '', s, flags=re.S)
    s = once(s, '        CommunityLiveConnection.activityVisible=true\n')
    s = once(s, '        CommunityLiveConnection.refresh(this)\n')
    s = between(s, '        if (AppChannel.supportsExperience(packageName)) {\n            CommunityBackground', '        val database =')
    s = once(s, '            UpdateCheckWorker.schedule(this)\n')
    s = once(s, '                        communityNavigationRequest = communityRequest.value,\n')
    s = once(s, '    foregroundRequest: Int = 0,\n    communityNavigationRequest: Pair<Int, String>? = null', '    foregroundRequest: Int = 0')
    s = once(s, '    CommunityPresenceHost()\n')
    for name in ('communityInitialTab', 'messengerInitialTab', 'messengerConversation', 'sharingPlayerCard'):
        s, count = re.subn(r'^    var ' + name + r'\b.*\n', '', s, flags=re.M)
        assert count == 1, name
    s = between(s, '    LaunchedEffect(communityNavigationRequest)', '\n\n    fun openPreview')
    s = once(s, '''                            OutlinedButton(onClick = {
                                sharingPlayerCard = SharedPlayerCard(card.setCode, card.number, previewLanguage, previewVariant)
                            }) { Text("Partager à des amis") }
''')
    s = between(s, '    val appUpdates =', '    LaunchedEffect(Unit) {\n        withContext(Dispatchers.IO)')
    s = between(s, '    val updateBanner:', '    AdaptiveWorkspace')
    s = re.sub(r'^.*updateBanner\(\).*\n', '', s, flags=re.M)
    s = between(s, '                if (privateHome) CommunityHomeControls(', '                else IconButton', '''                if (privateHome) PlaystoreHomeControls(
                    onMenu = { navigation.navigateTo("Paramètres") },
                    onCounter = { navigation.navigateTo("Compteur") },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)))
''')
    s = once(s, '                        "Communauté" -> "Mon compte"\n')
    s = between(s, '            if (selectedSection == "Échanges") {', '            if (selectedSection == "Historique")', '''            if (selectedSection == "Échanges") {
                ExchangePanel(sets, catalogBySet, cards, Modifier.weight(1f), database, cardDetails)
            }
''')
    s = between(s, '            if (selectedSection in listOf("Communauté", "Messagerie")', '            if (selectedSection == "Hauts faits")')
    s = once(s, 'updateAvailable = appUpdates.availableVersion', 'updateAvailable = null')
    s = re.sub(r'^.*AccountBackupPanel\(.*\n', '', s, flags=re.M)
    s = between(s, '                AppUpdatePanel(appUpdates,', '                Card(Modifier.fillMaxWidth())', '''                PlaystoreUpdatePanel()
''')
    s = once(s, '                    HomeBackupStatus(database) { navigation.navigateTo("Sauvegardes") }\n')
    s = once(s, '    sharingPlayerCard?.let { shared -> CardShareDialog(shared,allCatalog) { sharingPlayerCard=null } }\n')
    s = between(s, '                        val communityEntries =', '                        val entries =', '''                        val communityEntries = listOf(Triple(Icons.Default.Handshake, "Mes prêts de cartes", "Prêts"))
''')
    s = once(s, '                                section == "Communauté" && communityEntries.isNotEmpty() -> "Compte et partage"\n')
    s = between(s, '@Composable\nprivate fun AppUpdatePanel(', '\n\n\n\n', '')
    s = s.replace('else if(selectedSection=="Messagerie")Modifier.padding(messengerContentPadding()) ', '')
    # Remove residual messaging-only navigation and keyboard behavior.
    s = s.replace('val messengerKeyboardOpen=selectedSection=="Messagerie" && WindowInsets.ime.getBottom(LocalDensity.current)>0', 'val messengerKeyboardOpen = false')
    s = s.replace(' || selectedSection == "Messagerie"', '').replace(' && selectedSection != "Messagerie"', '').replace(', "Messagerie"', '')
    p.write_text(s)
    channel = src / 'AppChannel.kt'
    data = channel.read_text().replace('packageName == OFFICIAL_PACKAGE || packageName == TEST_PACKAGE', 'packageName == OFFICIAL_PACKAGE || packageName == TEST_PACKAGE || packageName == "' + PACKAGE + '"')
    data = data.replace('fun supportsUpdateChecks(packageName: String): Boolean = allowsOfficialUpdates(packageName) || packageName == TEST_PACKAGE', 'fun supportsUpdateChecks(packageName: String): Boolean = false')
    data = data.replace('fun allowsOfficialUpdates(packageName: String): Boolean = packageName == OFFICIAL_PACKAGE', 'fun allowsOfficialUpdates(packageName: String): Boolean = false')
    channel.write_text(data)
    settings = src / 'SettingsOverview.kt'
    settings.write_text(settings.read_text().replace('            Triple("Mon compte", "Profil et confidentialité", "Communauté"),\n', '').replace('                        "Communauté" -> Icons.Default.AccountCircle\n', ''))
    shortcuts = src / 'BottomShortcutUi.kt'
    shortcuts.write_text('\n'.join(line for line in shortcuts.read_text().splitlines() if not any('"' + key + '" to' in line for key in ('Messagerie', 'Communauté'))) + '\n')
    navigation = src / 'AppNavigation.kt'
    text = re.sub(r'^internal fun communityDestination.*\n', '', navigation.read_text(), flags=re.M)
    text = once(text, '        if (section == "Accueil") {', '        if (section in setOf("Communauté", "Messagerie", "Mon compte", "Amis", "Membres")) return\n        if (section == "Accueil") {')
    navigation.write_text(text)
    loans = src / 'CardLoansUi.kt'
    loans.write_text(loans.read_text().replace('TradePreviewRow(', 'LocalLoanPreviewRow('))
    legal = src / 'LegalInformation.kt'
    text = '\n'.join(line for line in legal.read_text().splitlines() if 'Échanges entre amis dans l’application Test' not in line)
    text = text.replace('GitHub fournit les vérifications de versions, les APK et les mises à jour du catalogue', 'Google Play fournit les mises à jour de l’application ; GitHub fournit les mises à jour du catalogue')
    legal.write_text(text + '\n')
    update_settings = src / 'UpdateSettings.kt'
    update_settings.write_text(between(update_settings.read_text(), '@Composable\ninternal fun UpdateNotificationPermissionRequest()', '@Composable\ninternal fun UpdateSafetyPanel'))
    recovery = src / 'StartupRecovery.kt'
    text = once(recovery.read_text(), '    val updates = remember { AppUpdates(context, database) }\n')
    text = between(text, '        OutlinedButton(onClick = { scope.launch { updates.check()', '        message?.let { Text(it) }', '        PlaystoreUpdatePanel()\n        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())\n')
    recovery.write_text(text)
    first_steps = src / 'FirstSteps.kt'
    first_steps.write_text(once(first_steps.read_text(), ' Avec un compte connecté, la sauvegarde privée indique sa date et les changements en attente.', ' Sauvegarde ta collection sur fichier depuis Paramètres → Sauvegarder et restaurer.'))
    text = legal.read_text()
    text = between(text, '        Text("Notifications :', '        Text("Conservation et sauvegardes :')
    text = text.replace('de prix et de versions', 'de prix').replace('une sauvegarde locale précède une mise à jour.', 'les sauvegardes sur fichier sont disponibles dans Paramètres.')
    legal.write_text(text)
    shutil.copy2(ROOT / 'playstore/PlaystoreUi.kt', src / 'PlaystoreUi.kt')
    # Remove tests for deleted features, keeping collection, migrations and backups.
    # Match declarations rather than filenames so a retained test cannot use a deleted API.
    symbol_pattern = re.compile(r'\b(' + '|'.join(map(re.escape, sorted(removed_symbols))) + r')\b')
    review = out / 'app/src/testDebug/java/fr/swucollection/app/SimplicityReviewTest.kt'
    text = review.read_text().replace('CommunityHomeControls({}, {}, {})', 'PlaystoreHomeControls({}, {})')
    text = between(text, '                    "members" -> MemberDirectory(', '                    "settings" ->')
    text = between(text, '                    "profile" -> PlayerHeading(', '                    "complete" ->')
    text = text.replace(', "profile", "members", "chat", "trade"', '')
    text = once(text, 'listOf("Mon compte" to "Communauté", "Hors connexion"', 'listOf("Hors connexion"')
    review.write_text(text)
    reader = out / 'app/src/testDebug/java/fr/swucollection/app/CollectionPhonePreviewTest.kt'
    reader.write_text(once(reader.read_text(), 'ui.onNodeWithText("Partager à des amis").performScrollTo().assertIsDisplayed()', 'ui.onNodeWithText("Partager à des amis").assertDoesNotExist()'))
    identity = out / 'app/src/test/java/fr/swucollection/app/AppChannelTest.kt'
    identity.write_text(once(identity.read_text(), 'assertTrue(AppChannel.allowsOfficialUpdates("fr.swucollection.app"))', 'assertFalse(AppChannel.allowsOfficialUpdates("fr.swucollection.app"))'))
    for test in (out / 'app/src').rglob('*.kt'):
        if not any(part.startswith('test') for part in test.relative_to(out / 'app/src').parts):
            continue
        if symbol_pattern.search(test.read_text()) or 'communityDestination' in test.read_text():
            test.unlink()
    shutil.copy2(ROOT / 'playstore/PlaystoreIsolationTest.kt', out / 'app/src/test/java/fr/swucollection/app/PlaystoreIsolationTest.kt')
    audit(out)
    return out


def audit(out):
    out = Path(out)
    src = out / 'app/src/main'
    for file in src.rglob('*.kt'):
        text = file.read_text()
        for forbidden in ('com.google.firebase', 'CommunityService', 'CommunityPanel', 'AccountBackupPanel', 'AutomaticBackupWorker', 'RealtimeMessagingService', 'SharedPlayerCard', 'PlayerConversation', 'AppUpdates', 'UpdateCheckWorker', 'CardShareDialog'):
            assert forbidden not in text, (file, forbidden)
    manifest = (src / 'AndroidManifest.xml').read_text()
    for forbidden in ('REQUEST_INSTALL_PACKAGES', 'FOREGROUND_SERVICE', 'POST_NOTIFICATIONS', 'swucollection', 'RealtimeMessagingService'):
        assert forbidden not in manifest, forbidden
    assert 'firebase' not in (out / 'app/build.gradle.kts').read_text()
    assert PACKAGE in (out / 'app/build.gradle.kts').read_text()
    assert not list(src.rglob('community_config.xml'))


if __name__ == '__main__':
    print(prepare(sys.argv[1]))
