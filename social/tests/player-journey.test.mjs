import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { gzipSync, gunzipSync } from 'node:zlib';
import { createHash } from 'node:crypto';
import { initializeApp, deleteApp } from 'firebase/app';
import { getAuth, connectAuthEmulator, createUserWithEmailAndPassword, sendEmailVerification,
  applyActionCode, reload, getIdToken, signOut } from 'firebase/auth';
import { getFirestore, connectFirestoreEmulator, doc, setDoc, getDoc, updateDoc, deleteDoc,
  serverTimestamp, runTransaction, writeBatch, terminate } from 'firebase/firestore';
import { initializeTestEnvironment, assertFails } from '@firebase/rules-unit-testing';

test('two verified players create profiles, become friends, share exact prints and accept a private exchange', async () => {
  const projectId = 'demo-swu-community-test';
  const env = await initializeTestEnvironment({ projectId, firestore: { rules: readFileSync('firestore.rules', 'utf8') } });
  const clients = [];
  async function player(name) {
    const app = initializeApp({ projectId, apiKey: 'demo-api-key', appId: 'demo-player-journey' }, name + Date.now());
    const auth = getAuth(app); connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
    const store = getFirestore(app); connectFirestoreEmulator(store, '127.0.0.1', 8080);
    const client = { app, auth, store }; clients.push(client);
    const email = `${name.toLowerCase()}-${Date.now()}@example.test`;
    const user = (await createUserWithEmailAndPassword(auth, email, 'Emulator-only-strong-password-18!')).user;
    await sendEmailVerification(user);
    const response = await fetch(`http://127.0.0.1:9099/emulator/v1/projects/${projectId}/oobCodes`);
    assert.equal(response.ok, true);
    const code = (await response.json()).oobCodes.find(c => c.email === email && c.requestType === 'VERIFY_EMAIL');
    assert.ok(code); await applyActionCode(auth, code.oobCode); await reload(user); await getIdToken(user, true);
    const member = doc(store, 'testMembers', user.uid);
    await runTransaction(store, async tx => { assert.equal((await tx.get(member)).exists(), false);
      tx.set(member, { enabled: true, selfRegistered: true, joinedAt: serverTimestamp() }); });
    await setDoc(doc(store, 'profiles', user.uid), { name, bio: '', avatar: 'preset:robot', discoverable: false,
      shareBadges: false, badgeIds: [], updatedAt: serverTimestamp() });
    return { ...client, uid: user.uid };
  }
  try {
    const alice = await player('Alice'); const bob = await player('Bob');
    const members = [alice.uid, bob.uid].sort(); const pair = members.join('__');
    const profile = { format: 'SWU_TRADE_PROFILE', version: 3, acceptVariants: false,
      offers: [{ set: 'SOR', number: '031', variant: 'Normale', name: 'Carte B', family: 'b', quantity: 3, acceptVariants: false, language: 'EN' }],
      needs: [{ set: 'SOR', number: '030', variant: 'Normale', name: 'Carte A', family: 'a', quantity: 1, acceptVariants: false, language: 'FR' }] };
    const raw = JSON.stringify(profile); const hash = createHash('sha256').update(raw).digest('hex');
    await setDoc(doc(bob.store, 'tradeInventories', bob.uid), { encoded: gzipSync(raw).toString('base64'), hash, updatedAt: serverTimestamp() });
    await assertFails(getDoc(doc(alice.store, 'tradeInventories', bob.uid)));
    await setDoc(doc(alice.store, 'friendships', pair), { members, requester: alice.uid, status: 'pending',
      requesterName: 'Alice', requesterAvatar: 'preset:robot', updatedAt: serverTimestamp() });
    await assertFails(updateDoc(doc(alice.store, 'friendships', pair), { status: 'accepted', updatedAt: serverTimestamp() }));
    await updateDoc(doc(bob.store, 'friendships', pair), { status: 'accepted', updatedAt: serverTimestamp() });
    const shared = (await getDoc(doc(alice.store, 'tradeInventories', bob.uid))).data();
    const decoded = gunzipSync(Buffer.from(shared.encoded, 'base64')).toString('utf8');
    assert.equal(createHash('sha256').update(decoded).digest('hex'), shared.hash);
    assert.deepEqual(JSON.parse(decoded), profile);
    const offer = JSON.stringify([{ card: 'SOR-030', quantity: 1, language: 'FR', finish: 'Classique' }]);
    const wanted = JSON.stringify([{ card: 'SOR-031', quantity: 1, language: 'EN', finish: 'Classique' }]);
    const id = 'journey-' + Date.now();
    await setDoc(doc(alice.store, 'trades', id), { members, requester: alice.uid, status: 'pending', offer, wanted,
      createdAt: serverTimestamp(), updatedAt: serverTimestamp() });
    const incoming = (await getDoc(doc(bob.store, 'trades', id))).data();
    assert.equal(incoming.offer, offer); assert.equal(incoming.wanted, wanted);
    await assertFails(updateDoc(doc(bob.store, 'trades', id), { offer: '[]', status: 'accepted', updatedAt: serverTimestamp() }));
    await updateDoc(doc(bob.store, 'trades', id), { status: 'accepted', updatedAt: serverTimestamp() });
    assert.equal((await getDoc(doc(alice.store, 'trades', id))).data().status, 'accepted');
    await assertFails(updateDoc(doc(alice.store, 'trades', id), { status: 'pending', updatedAt: serverTimestamp() }));
    await updateDoc(doc(bob.store, 'profiles', bob.uid), { messagePolicy: 'friends', updatedAt: serverTimestamp() });
    await setDoc(doc(alice.store, 'conversations', pair), { members, updatedAt: serverTimestamp() });
    async function send(client, messageId, text) {
      const batch = writeBatch(client.store);
      batch.set(doc(client.store, 'conversations', pair, 'messages', messageId), { senderId: client.uid,
        kind: 'text', text, deckName: '', deckJson: '', createdAt: serverTimestamp() });
      batch.update(doc(client.store, 'conversations', pair), { lastSenderId: client.uid, updatedAt: serverTimestamp() });
      return batch.commit();
    }
    await send(alice, 'first', 'Bonjour Bob');
    assert.equal((await getDoc(doc(bob.store, 'conversations', pair, 'messages', 'first'))).data().text, 'Bonjour Bob');
    assert.equal((await getDoc(doc(bob.store, 'conversations', pair))).data().lastSenderId, alice.uid);
    await setDoc(doc(bob.store, 'reads', bob.uid, 'threads', pair), { seenAt: serverTimestamp() });
    assert.ok((await getDoc(doc(bob.store, 'reads', bob.uid, 'threads', pair))).data().seenAt);
    await send(bob, 'reply', 'Bonjour Alice');
    assert.equal((await getDoc(doc(alice.store, 'conversations', pair, 'messages', 'reply'))).data().senderId, bob.uid);
    await deleteDoc(doc(bob.store, 'tradeInventories', bob.uid));
    assert.equal((await getDoc(doc(alice.store, 'tradeInventories', bob.uid))).exists(), false);
    await deleteDoc(doc(alice.store, 'friendships', pair));
    await assertFails(send(alice, 'after-removal', 'Refusé par la préférence amis uniquement'));
    assert.equal((await getDoc(doc(bob.store, 'conversations', pair, 'messages', 'first'))).data().text, 'Bonjour Bob');
    await signOut(alice.auth);
    await assertFails(getDoc(doc(alice.store, 'trades', id)));
  } finally {
    for (const client of clients) { await terminate(client.store); await deleteApp(client.app); }
    await env.cleanup();
  }
});
