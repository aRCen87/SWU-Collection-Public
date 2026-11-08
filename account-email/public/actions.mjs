export function parseAction(search) {
  const p = new URLSearchParams(search);
  const mode = p.get('mode');
  if (!['verifyEmail', 'resetPassword', 'recoverEmail'].includes(mode) || p.getAll('mode').length !== 1 || p.getAll('oobCode').length !== 1) throw new Error('invalid-link');
  const code = p.get('oobCode');
  if (!code || code.length > 2048 || !/^[A-Za-z0-9_-]+$/.test(code)) throw new Error('invalid-link');
  // Never trust apiKey, continueUrl, email or redirect targets supplied in the link.
  return { mode, code };
}

export function validateConfig(config, hostname) {
  if (!config || !/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/.test(config.projectId || '') || typeof config.apiKey !== 'string' || !config.apiKey) throw new Error('configuration');
  if (![`${config.projectId}.web.app`, `${config.projectId}.firebaseapp.com`].includes(hostname)) throw new Error('configuration');
  return config;
}

export function returnIntent(projectId) {
  const pkg = projectId === 'swu-collection-test' ? 'fr.swucollection.app.test' : 'fr.swucollection.app';
  return `intent://account#Intent;scheme=swucollection;package=${pkg};end`;
}

export async function processAction(sdk, auth, action) {
  if (action.mode === 'resetPassword') {
    await sdk.verifyPasswordResetCode(auth, action.code);
    return 'password-form';
  }
  const info = await sdk.checkActionCode(auth, action.code);
  const expected = action.mode === 'verifyEmail' ? 'VERIFY_EMAIL' : 'RECOVER_EMAIL';
  if (info.operation !== expected) throw new Error('invalid-link');
  await sdk.applyActionCode(auth, action.code);
  return action.mode === 'verifyEmail' ? 'verified' : 'recovered';
}

export async function savePassword(sdk, auth, code, password, confirmation) {
  if (password.length < 12 || password.length > 128) throw new Error('password-length');
  if (password !== confirmation) throw new Error('password-mismatch');
  await sdk.confirmPasswordReset(auth, code, password);
}

export function errorMessage(error) {
  const code = error?.code || error?.message;
  if (['auth/expired-action-code', 'auth/invalid-action-code', 'invalid-link'].includes(code)) return 'Ce lien est expiré, a déjà été utilisé ou n’est pas valide. Ouvrez l’application pour demander un nouvel e-mail.';
  if (code === 'auth/network-request-failed') return 'La connexion a été interrompue. Vérifiez votre accès à Internet puis rouvrez le lien du mail.';
  if (code === 'password-length') return 'Choisissez un mot de passe de 12 à 128 caractères.';
  if (code === 'password-mismatch') return 'Les deux mots de passe ne correspondent pas.';
  if (['auth/weak-password', 'auth/password-does-not-meet-requirements'].includes(code)) return 'Ce mot de passe ne respecte pas les exigences du compte. Choisissez-en un plus robuste.';
  return 'La demande n’a pas pu être confirmée. Réessayez depuis le lien du mail ou demandez un nouvel e-mail dans l’application.';
}
