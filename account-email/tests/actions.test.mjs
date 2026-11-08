import test from 'node:test';
import assert from 'node:assert/strict';
import { parseAction, validateConfig, processAction, savePassword, returnIntent, errorMessage } from '../public/actions.mjs';

test('rejects missing, duplicate, unsupported or malformed action parameters', () => {
  for (const query of ['', '?mode=signIn&oobCode=abc', '?mode=verifyEmail', '?mode=verifyEmail&oobCode=%3Cscript%3E', '?mode=verifyEmail&mode=recoverEmail&oobCode=abc', '?mode=verifyEmail&oobCode=abc&oobCode=def']) assert.throws(() => parseAction(query));
});
test('uses only action and code, ignoring redirects and caller API keys', () => {
  assert.deepEqual(parseAction('?mode=verifyEmail&oobCode=abc_123-xyz&apiKey=attacker&continueUrl=https://evil.test'), { mode: 'verifyEmail', code: 'abc_123-xyz' });
});
test('configuration is bound to its own Firebase hosting project', () => {
  const config = { projectId: 'swu-collection-test', apiKey: 'public-key' };
  assert.equal(validateConfig(config, 'swu-collection-test.web.app'), config);
  assert.throws(() => validateConfig(config, 'other.web.app'));
  assert.throws(() => validateConfig({ ...config, apiKey: '' }, 'swu-collection-test.web.app'));
});
test('reports verified only after the one-time code is successfully applied', async () => {
  const calls = []; const auth = {};
  const sdk = { checkActionCode: async (a, c) => { assert.equal(a, auth); calls.push(c); return { operation: 'VERIFY_EMAIL' }; }, applyActionCode: async (a, c) => { assert.equal(a, auth); calls.push(c); } };
  assert.equal(await processAction(sdk, auth, { mode: 'verifyEmail', code: 'once' }), 'verified');
  assert.deepEqual(calls, ['once', 'once']);
  sdk.applyActionCode = async () => { throw Object.assign(new Error(), { code: 'auth/expired-action-code' }); };
  await assert.rejects(processAction(sdk, auth, { mode: 'verifyEmail', code: 'once' }));
});
test('never applies a code for the wrong operation', async () => {
  let applied = false;
  const sdk = { checkActionCode: async () => ({ operation: 'PASSWORD_RESET' }), applyActionCode: async () => { applied = true; } };
  await assert.rejects(processAction(sdk, {}, { mode: 'verifyEmail', code: 'once' }));
  assert.equal(applied, false);
});
test('password reset checks the link without consuming it until password submission', async () => {
  let saved;
  const sdk = { verifyPasswordResetCode: async () => 'private@example.test', confirmPasswordReset: async (...args) => { saved = args; } };
  const auth = {};
  assert.equal(await processAction(sdk, auth, { mode: 'resetPassword', code: 'once' }), 'password-form');
  assert.equal(saved, undefined);
  await assert.rejects(savePassword(sdk, auth, 'once', 'short', 'short'));
  await assert.rejects(savePassword(sdk, auth, 'once', 'long-password', 'different-password'));
  await savePassword(sdk, auth, 'once', 'long-password', 'long-password');
  assert.deepEqual(saved, [auth, 'once', 'long-password']);
});
test('email recovery is handled because Firebase custom action URLs affect every template', async () => {
  const sdk = { checkActionCode: async () => ({ operation: 'RECOVER_EMAIL' }), applyActionCode: async () => {} };
  assert.equal(await processAction(sdk, {}, { mode: 'recoverEmail', code: 'once' }), 'recovered');
});
test('return destinations are fixed and Test cannot open the official app', () => {
  assert.match(returnIntent('swu-collection-test'), /package=fr\.swucollection\.app\.test;/);
  assert.match(returnIntent('swu-collection-production'), /package=fr\.swucollection\.app;/);
});
test('errors never expose raw backend details or user email addresses', () => {
  assert.match(errorMessage({ code: 'auth/expired-action-code' }), /expiré/);
  assert.match(errorMessage({ code: 'auth/network-request-failed' }), /Internet/);
  assert.doesNotMatch(errorMessage(new Error('private@example.test')), /private@example/);
});
