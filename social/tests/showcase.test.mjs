import { readFileSync } from 'node:fs';
import { before, after, beforeEach, test } from 'node:test';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, setDoc, getDoc, collection, getDocs, query, where, limit, serverTimestamp, updateDoc, deleteDoc } from 'firebase/firestore';
let env;
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-swu-community-test',firestore:{rules:readFileSync('firestore.rules','utf8')}})});
after(async()=>env.cleanup());
const db=(id)=>env.authenticatedContext(id,{email_verified:true}).firestore();
const profile=(extra={})=>({name:'Joueur',bio:'',avatar:'preset:star',discoverable:true,shareBadges:false,badgeIds:[],updatedAt:serverTimestamp(),city:'Angoulême',cityKey:'angouleme',formats:['Premier'],leaders:['SOR-001'],shareShowcase:true,...extra});
const card=(extra={})=>({set:'SOR',number:'001',variant:'Normale',language:'FR',caption:'Ma carte préférée',updatedAt:serverTimestamp(),...extra});
const favorite=(extra={})=>({owner:'alice',item:'one',createdAt:serverTimestamp(),...extra});
beforeEach(async()=>{
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async c=>{for(const id of ['alice','bob','carol']) await setDoc(doc(c.firestore(),'testMembers',id),{enabled:true})});
 await setDoc(doc(db('alice'),'profiles','alice'),profile());
 await setDoc(doc(db('alice'),'profiles','alice','showcase','one'),card());
});
test('city directory accepts a bounded opt-in query and never an unfiltered enumeration',async()=>{
 await assertSucceeds(getDocs(query(collection(db('bob'),'profiles'),where('discoverable','==',true),where('cityKey','==','angouleme'),limit(50))));
 await assertFails(getDocs(query(collection(db('bob'),'profiles'),where('cityKey','==','angouleme'),limit(50))));
 await assertFails(setDoc(doc(db('bob'),'profiles','alice'),profile()));
 await assertFails(updateDoc(doc(db('alice'),'profiles','alice'),{latitude:45,longitude:0}));
 await assertFails(updateDoc(doc(db('alice'),'profiles','alice'),{formats:['Hacked']}));
});
test('showcase includes only selected print and caption; no stock, email or arbitrary fields',async()=>{
 await assertSucceeds(getDocs(query(collection(db('bob'),'profiles','alice','showcase'),limit(12))));
 await assertFails(getDocs(collection(db('bob'),'profiles','alice','showcase')));
 await assertFails(setDoc(doc(db('bob'),'profiles','alice','showcase','fake'),card()));
 await assertFails(setDoc(doc(db('alice'),'profiles','alice','showcase','stock'),card({quantity:999})));
 await assertFails(setDoc(doc(db('alice'),'profiles','alice','showcase','stock'),card({email:'private@example.test'})));
 await assertFails(setDoc(doc(db('alice'),'profiles','alice','showcase','stock'),card({caption:'x'.repeat(161)})));
});
test('hiding a showcase revokes reads and new favorites while its owner retains control',async()=>{
 await assertSucceeds(updateDoc(doc(db('alice'),'profiles','alice'),{shareShowcase:false,updatedAt:serverTimestamp()}));
 await assertFails(getDoc(doc(db('bob'),'profiles','alice','showcase','one')));
 await assertFails(setDoc(doc(db('bob'),'showcaseFavorites','bob','items','alice__one'),favorite()));
 await assertSucceeds(getDoc(doc(db('alice'),'profiles','alice','showcase','one')));
 await assertSucceeds(deleteDoc(doc(db('alice'),'profiles','alice','showcase','one')));
});
test('favorites are private, cannot impersonate another owner, and do not publish popularity',async()=>{
 await assertSucceeds(setDoc(doc(db('bob'),'showcaseFavorites','bob','items','alice__one'),favorite()));
 await assertSucceeds(getDocs(query(collection(db('bob'),'showcaseFavorites','bob','items'),limit(100))));
 await assertFails(getDoc(doc(db('alice'),'showcaseFavorites','bob','items','alice__one')));
 await assertFails(getDocs(query(collection(db('carol'),'showcaseFavorites','bob','items'),limit(100))));
 await assertFails(setDoc(doc(db('carol'),'showcaseFavorites','bob','items','alice__one'),favorite()));
 await assertFails(setDoc(doc(db('bob'),'showcaseFavorites','bob','items','alice__missing'),favorite({item:'missing'})));
 await assertSucceeds(deleteDoc(doc(db('bob'),'showcaseFavorites','bob','items','alice__one')));
});
test('blocking either way denies showcase reads and new favorites',async()=>{
 await setDoc(doc(db('alice'),'blocks','alice','users','bob'),{createdAt:serverTimestamp()});
 await assertFails(getDoc(doc(db('bob'),'profiles','alice','showcase','one')));
 await assertFails(setDoc(doc(db('bob'),'showcaseFavorites','bob','items','alice__one'),favorite()));
});
test('old profiles remain writable and hidden profiles are not exposed by the showcase',async()=>{
 const old=profile({discoverable:false,shareShowcase:true});delete old.city;delete old.cityKey;delete old.formats;delete old.leaders;
 await assertSucceeds(setDoc(doc(db('alice'),'profiles','alice'),old));
 await assertFails(getDoc(doc(db('bob'),'profiles','alice','showcase','one')));
});
