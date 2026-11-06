import { readFileSync } from 'node:fs';
import { before, after, beforeEach, test } from 'node:test';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, setDoc, getDoc, collection, getDocs, query, where, limit, serverTimestamp, updateDoc, writeBatch } from 'firebase/firestore';
let env;
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-swu-community-test',firestore:{rules:readFileSync('firestore.rules','utf8')}})});
after(async()=>{await env.cleanup()});
beforeEach(async()=>{
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async c=>{
  for(const uid of ['alice','bob','carol']) await setDoc(doc(c.firestore(),'testMembers',uid),{enabled:true});
 });
});
const db=(uid,verified=true)=>env.authenticatedContext(uid,{email_verified:verified,email:uid+'@example.test'}).firestore();
const profile=(extra={})=>({name:'Joueur',bio:'',avatar:'preset:star',discoverable:true,shareBadges:false,badgeIds:[],updatedAt:serverTimestamp(),...extra});
const message=(sender,extra={})=>({senderId:sender,kind:'text',text:'Bonjour',deckName:'',deckJson:'',createdAt:serverTimestamp(),...extra});
async function thread(){await assertSucceeds(setDoc(doc(db('alice'),'conversations','alice__bob'),{members:['alice','bob'],updatedAt:serverTimestamp()}));}

test('anonymous, unverified and unapproved users cannot read or write profiles',async()=>{
 for(const store of [env.unauthenticatedContext().firestore(),db('alice',false),db('outsider')]){
  await assertFails(setDoc(doc(store,'profiles','alice'),profile()));
  await assertFails(getDocs(query(collection(store,'profiles'),where('discoverable','==',true),limit(50))));
 }
});
test('test membership is administrator-only and not enumerable',async()=>{
 await assertFails(setDoc(doc(db('outsider'),'testMembers','outsider'),{enabled:true}));
 await assertFails(setDoc(doc(db('alice'),'testMembers','bob'),{enabled:true}));
 await assertFails(getDocs(collection(db('alice'),'testMembers')));
 await assertSucceeds(getDoc(doc(db('alice'),'testMembers','alice')));
});
test('profile owner may update avatar and visibility; no email or private badges may leak',async()=>{
 const a=db('alice');await assertSucceeds(setDoc(doc(a,'profiles','alice'),profile({discoverable:false})));
 await assertSucceeds(getDoc(doc(a,'profiles','alice')));
 await assertFails(getDoc(doc(db('bob'),'profiles','alice')));
 await assertFails(setDoc(doc(db('bob'),'profiles','alice'),profile()));
 await assertFails(setDoc(doc(a,'profiles','alice'),profile({email:'private@example.test'})));
 await assertFails(setDoc(doc(a,'profiles','alice'),profile({badgeIds:['hidden']})));
 await assertFails(setDoc(doc(a,'profiles','alice'),profile({avatar:'https://tracking.example/image'})));
 await assertSucceeds(setDoc(doc(a,'profiles','alice'),profile({avatar:'preset:robot',shareBadges:true,badgeIds:['first_scan']})));
 await assertSucceeds(getDoc(doc(db('bob'),'profiles','alice')));
 await assertFails(getDocs(collection(db('bob'),'profiles')));
 await assertSucceeds(getDocs(query(collection(db('bob'),'profiles'),where('discoverable','==',true),limit(50))));
});
test('only participants can read messages and the sender cannot impersonate another person',async()=>{
 await thread();const a=db('alice');const ref=doc(a,'conversations','alice__bob','messages','one');
 await assertSucceeds(setDoc(ref,message('alice')));
 await assertSucceeds(getDoc(doc(db('bob'),'conversations','alice__bob','messages','one')));
 await assertFails(getDoc(doc(db('carol'),'conversations','alice__bob','messages','one')));
 await assertFails(setDoc(doc(a,'conversations','alice__bob','messages','spoof'),message('bob')));
 await assertFails(updateDoc(ref,{text:'Modifié'}));
 await assertFails(updateDoc(doc(a,'conversations','alice__bob'),{members:['alice','carol'],updatedAt:serverTimestamp()}));
});
test('blocked users cannot send in either direction, including in an atomic batch',async()=>{
 await thread();await assertSucceeds(setDoc(doc(db('bob'),'blocks','bob','users','alice'),{createdAt:serverTimestamp()}));
 for(const uid of ['alice','bob']){
  const store=db(uid);const batch=writeBatch(store);
  batch.set(doc(store,'conversations','alice__bob','messages',uid),message(uid));
  batch.update(doc(store,'conversations','alice__bob'),{updatedAt:serverTimestamp()});
  await assertFails(batch.commit());
 }
});
test('shared decks are bounded immutable snapshots; private collections are never exposed',async()=>{
 await thread();const a=db('alice');
 await assertSucceeds(setDoc(doc(a,'conversations','alice__bob','messages','deck'),message('alice',{kind:'deck',text:'Mon deck',deckName:'Deck test',deckJson:'{"leader":"SOR_001","base":"SOR_024","deck":[]}'})));
 await assertFails(setDoc(doc(a,'conversations','alice__bob','messages','huge'),message('alice',{kind:'deck',deckName:'X',deckJson:'x'.repeat(40001)})));
 await assertFails(getDocs(collection(a,'collections')));
 await assertFails(setDoc(doc(a,'collections','alice'),{cards:['private']}));
});
test('bounded participant queries and a valid message batch succeed',async()=>{
 await thread();const a=db('alice');const batch=writeBatch(a);
 batch.set(doc(a,'conversations','alice__bob','messages','batch'),message('alice'));
 batch.update(doc(a,'conversations','alice__bob'),{updatedAt:serverTimestamp()});
 await assertSucceeds(batch.commit());
 await assertSucceeds(getDocs(query(collection(a,'conversations'),where('members','array-contains','alice'),limit(100))));
 await assertFails(getDocs(query(collection(a,'conversations'),limit(100))));
 await assertSucceeds(getDocs(query(collection(db('bob'),'conversations','alice__bob','messages'),limit(50))));
 await assertFails(getDocs(query(collection(db('carol'),'conversations','alice__bob','messages'),limit(50))));
 await assertFails(getDocs(collection(a,'conversations','alice__bob','messages')));
 await assertFails(setDoc(doc(a,'conversations','alice__outsider'),{members:['alice','outsider'],updatedAt:serverTimestamp()}));
});
