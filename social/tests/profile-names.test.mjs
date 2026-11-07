import {readFileSync} from 'node:fs';
import {before,after,beforeEach,test} from 'node:test';
import {initializeTestEnvironment,assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {doc,setDoc,getDoc,getDocs,collection,updateDoc,deleteDoc,serverTimestamp} from 'firebase/firestore';
let env;
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-swu-community-test',firestore:{rules:readFileSync('firestore.rules','utf8')}})});
after(async()=>env.cleanup());
const db=id=>env.authenticatedContext(id,{email_verified:true}).firestore();
const name=(extra={})=>({firstName:'Romain',shareFirstName:false,updatedAt:serverTimestamp(),...extra});
beforeEach(async()=>{
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async c=>{
  for(const id of ['alice','bob','carol']) await setDoc(doc(c.firestore(),'testMembers',id),{enabled:true});
  await setDoc(doc(c.firestore(),'friendships','alice__bob'),{members:['alice','bob'],requester:'alice',status:'accepted'});
 });
 await setDoc(doc(db('alice'),'profileNames','alice'),name());
});
test('name private by default even from friends; owner edits or erases',async()=>{
 await assertSucceeds(getDoc(doc(db('alice'),'profileNames','alice')));
 await assertFails(getDoc(doc(db('bob'),'profileNames','alice')));
 await assertFails(getDoc(doc(db('carol'),'profileNames','alice')));
 await assertSucceeds(setDoc(doc(db('alice'),'profileNames','alice'),name({firstName:''})));
});
test('sharing allows only accepted friends and revokes on hiding or unfriending',async()=>{
 await setDoc(doc(db('alice'),'profileNames','alice'),name({shareFirstName:true}));
 await assertSucceeds(getDoc(doc(db('bob'),'profileNames','alice')));
 await assertFails(getDoc(doc(db('carol'),'profileNames','alice')));
 await assertFails(getDocs(collection(db('bob'),'profileNames')));
 await setDoc(doc(db('alice'),'profileNames','alice'),name());
 await assertFails(getDoc(doc(db('bob'),'profileNames','alice')));
 await setDoc(doc(db('alice'),'profileNames','alice'),name({shareFirstName:true}));
 await env.withSecurityRulesDisabled(async c=>deleteDoc(doc(c.firestore(),'friendships','alice__bob')));
 await assertFails(getDoc(doc(db('bob'),'profileNames','alice')));
});
test('blocks in both directions and disabled owners deny reading',async()=>{
 await setDoc(doc(db('alice'),'profileNames','alice'),name({shareFirstName:true}));
 await setDoc(doc(db('bob'),'blocks','bob','users','alice'),{createdAt:serverTimestamp()});
 await assertFails(getDoc(doc(db('bob'),'profileNames','alice')));
 await deleteDoc(doc(db('bob'),'blocks','bob','users','alice'));
 await setDoc(doc(db('alice'),'blocks','alice','users','bob'),{createdAt:serverTimestamp()});
 await assertFails(getDoc(doc(db('bob'),'profileNames','alice')));
 await deleteDoc(doc(db('alice'),'blocks','alice','users','bob'));
 await env.withSecurityRulesDisabled(async c=>updateDoc(doc(c.firestore(),'testMembers','alice'),{enabled:false}));
 await assertFails(getDoc(doc(db('bob'),'profileNames','alice')));
});
test('no impersonation, extra fields, oversized names or anonymous reads',async()=>{
 await assertFails(setDoc(doc(db('bob'),'profileNames','alice'),name({shareFirstName:true})));
 await assertFails(setDoc(doc(db('alice'),'profileNames','alice'),name({firstName:'x'.repeat(41)})));
 await assertFails(setDoc(doc(db('alice'),'profileNames','alice'),name({email:'private@example.test'})));
 await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),'profileNames','alice')));
});
