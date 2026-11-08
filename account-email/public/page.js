import { parseAction, validateConfig, processAction, savePassword, errorMessage, returnIntent } from './actions.mjs';

// Remove single-use codes from history before loading assets from any remote service.
const search = location.search;
history.replaceState(null, '', location.pathname);
const $ = id => document.getElementById(id);
function render(title, message, symbol = '✦') {
  $('title').textContent = title;
  $('message').textContent = message;
  $('symbol').textContent = symbol;
}

async function main() {
  try {
    const action = parseAction(search);
    const response = await fetch('/__/firebase/init.json', { cache: 'no-store', credentials: 'omit', referrerPolicy: 'no-referrer' });
    if (!response.ok) throw new Error('configuration');
    const config = validateConfig(await response.json(), location.hostname);
    $('return-app').href = returnIntent(config.projectId);
    $('return-app').hidden = false;
    $('return-hint').hidden = false;
    const [{ initializeApp }, sdk] = await Promise.all([
      import('https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js'),
      import('https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js')
    ]);
    const auth = sdk.initializeAuth(initializeApp(config), { persistence: sdk.inMemoryPersistence });
    auth.languageCode = 'fr';
    const state = await processAction(sdk, auth, action);
    if (state === 'verified') {
      render('Adresse confirmée', 'Bienvenue dans SWU Collection ! Revenez dans l’application pour activer automatiquement votre compte et retrouver votre collection.', '✓');
    } else if (state === 'recovered') {
      render('Adresse rétablie', 'Votre ancienne adresse e-mail a été rétablie. Revenez dans l’application pour vous connecter et vérifier votre compte.', '✓');
    } else {
      render('Un nouveau départ', 'Choisissez un nouveau mot de passe pour retrouver votre compte SWU Collection.');
      $('password-form').hidden = false;
      $('password-form').addEventListener('submit', async event => {
        event.preventDefault();
        $('save-password').disabled = true;
        $('password-error').textContent = '';
        try {
          await savePassword(sdk, auth, action.code, $('password').value, $('confirmation').value);
          $('password-form').reset();
          $('password-form').hidden = true;
          render('Mot de passe enregistré', 'Vous pouvez maintenant vous connecter dans SWU Collection avec votre nouveau mot de passe.', '✓');
        } catch (error) {
          $('password-error').textContent = errorMessage(error);
        } finally { $('save-password').disabled = false; }
      });
    }
  } catch (error) {
    render('Lien non confirmé', errorMessage(error), '!');
  }
}
main();
