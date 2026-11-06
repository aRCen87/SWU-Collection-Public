import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { initializeApp, deleteApp } from 'firebase/app';
import { getAuth, connectAuthEmulator, createUserWithEmailAndPassword, sendEmailVerification,
 applyActionCode, reload, getIdToken, signOut } from 'firebase/auth';
import { getFirestore, connectFirestoreEmulator, doc, setDoc, serverTimestamp, terminate, runTransaction, getDoc } from 'firebase/firestore';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';

test('real emulator authentication activates a new account after email verification without administrator approval',async()=>{
 const project='demo-swu-community-test';
 const app=initializeApp({projectId:project,apiKey:'demo-api-key',appId:'demo-community-client'},'auth-integration');
 const auth=getAuth(app);connectAuthEmulator(auth,'http://127.0.0.1:9099',{disableWarnings:true});
 const store=getFirestore(app);connectFirestoreEmulator(store,'127.0.0.1',8080);
 const env=await initializeTestEnvironment({projectId:project,firestore:{rules:readFileSync('firestore.rules','utf8')}});
 try {
  const email='private-test-'+Date.now()+'@example.test';
  const user=(await createUserWithEmailAndPassword(auth,email,'Test-only-strong-password-123!')).user;
  const profile=doc(store,'profiles',user.uid);
  const data=()=>({name:'Test privé',bio:'',avatar:'preset:robot',discoverable:false,shareBadges:false,badgeIds:[],updatedAt:serverTimestamp()});
  await assertFails(setDoc(profile,data()));
  const member=doc(store,'testMembers',user.uid);
  await assertFails(setDoc(member,{enabled:true,selfRegistered:true,joinedAt:serverTimestamp()}));
  await sendEmailVerification(user);
  const response=await fetch('http://127.0.0.1:9099/emulator/v1/projects/'+project+'/oobCodes');
  assert.equal(response.ok,true);
  const code=(await response.json()).oobCodes.find(c=>c.email===email && c.requestType==='VERIFY_EMAIL');
  assert.ok(code,'Verification email must exist only in the local emulator');
  await applyActionCode(auth,code.oobCode);await reload(user);await getIdToken(user,true);
  assert.equal(user.emailVerified,true);
  await assertFails(setDoc(profile,data()));
  await assertSucceeds(runTransaction(store,async tx=>{const existing=await tx.get(member);assert.equal(existing.exists(),false);tx.set(member,{enabled:true,selfRegistered:true,joinedAt:serverTimestamp()});}));
  assert.equal((await getDoc(member)).data().enabled,true);
  await assertSucceeds(setDoc(profile,data()));
  await signOut(auth);
  await assertFails(setDoc(profile,data()));
 } finally {await terminate(store);await env.cleanup();await deleteApp(app);}
});
