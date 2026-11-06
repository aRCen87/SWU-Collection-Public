import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
import {before,after,beforeEach,test} from 'node:test';
import {initializeTestEnvironment,assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {doc,setDoc,getDoc,deleteDoc,collection,getDocs,query,limit,serverTimestamp,writeBatch,runTransaction,where} from 'firebase/firestore';
let env;
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-swu-community-test',firestore:{rules:readFileSync('firestore.rules','utf8')}})});
after(async()=>{await env.cleanup()});
beforeEach(async()=>{await env.clearFirestore();await env.withSecurityRulesDisabled(async c=>{
 for(const id of ['alice','bob','carol']) await setDoc(doc(c.firestore(),'testMembers',id),{enabled:true});
 await setDoc(doc(c.firestore(),'friendships','alice__bob'),{members:['alice','bob'],requester:'alice',status:'accepted'});
})});
const db=(id,verified=true)=>env.authenticatedContext(id,{email_verified:verified}).firestore();
const id='00000000-0000-0000-0000-000000000001';
async function save(store,uid='alice',parts=1){const b=writeBatch(store);const path=`accountBackups/${uid}`;
 b.set(doc(store,path),{head:id,updatedAt:serverTimestamp()});
 b.set(doc(store,`${path}/snapshots/${id}`),{parts,bytes:500,hash:'a'.repeat(64),createdAt:serverTimestamp()});
 for(let i=0;i<parts;i++) b.set(doc(store,`${path}/snapshots/${id}/chunks/${i}`),{index:i,data:'encoded'});
 return b.commit();}
test('owner may atomically save all 32 chunks and restore a private backup',async()=>{
 await assertSucceeds(getDoc(doc(db('alice'),'accountBackups/alice')));
 await assertSucceeds(save(db('alice'),'alice',32));
 await assertSucceeds(getDoc(doc(db('alice'),`accountBackups/alice/snapshots/${id}/chunks/31`)));
 await assertSucceeds(getDocs(query(collection(db('alice'),'accountBackups/alice/snapshots'),limit(3))));
 await assertFails(getDocs(collection(db('alice'),'accountBackups/alice/snapshots')));
 await assertFails(setDoc(doc(db('alice'),`accountBackups/alice/snapshots/${id}/chunks/0`),{index:0,data:'overwrite'}));
});
test('friends, strangers and unverified accounts cannot access private backups',async()=>{
 await assertSucceeds(save(db('alice')));
 for(const store of [db('bob'),db('carol'),db('outsider'),db('alice',false),env.unauthenticatedContext().firestore()]) {
  await assertFails(getDoc(doc(store,'accountBackups/alice')));
  await assertFails(getDoc(doc(store,`accountBackups/alice/snapshots/${id}/chunks/0`)));
  await assertFails(save(store,'alice'));
 }
 await assertFails(getDocs(collection(db('alice'),'accountBackups')));
});
test('invalid head pointers and oversized chunks are rejected',async()=>{
 await assertFails(setDoc(doc(db('alice'),'accountBackups/alice'),{head:id,updatedAt:serverTimestamp()}));
 const store=db('alice');const b=writeBatch(store);
 b.set(doc(store,`accountBackups/alice/snapshots/${id}`),{parts:1,bytes:10,hash:'a'.repeat(64),createdAt:serverTimestamp()});
 b.set(doc(store,`accountBackups/alice/snapshots/${id}/chunks/0`),{index:0,data:'x'.repeat(200001)});
 await assertFails(b.commit());
});
const inventory=()=>({encoded:'encoded',hash:'a'.repeat(64),updatedAt:serverTimestamp()});
test('exchange inventory is opt-in, friends-only, bounded and revocable',async()=>{
 await assertSucceeds(setDoc(doc(db('alice'),'tradeInventories/alice'),inventory()));
 await assertSucceeds(getDoc(doc(db('bob'),'tradeInventories/alice')));
 await assertFails(getDoc(doc(db('carol'),'tradeInventories/alice')));
 await assertFails(getDocs(query(collection(db('bob'),'tradeInventories'),limit(10))));
 await assertFails(setDoc(doc(db('bob'),'tradeInventories/alice'),inventory()));
 await assertFails(setDoc(doc(db('alice'),'tradeInventories/alice'),{...inventory(),collection:'private'}));
 await assertSucceeds(setDoc(doc(db('alice'),'blocks/alice/users/bob'),{createdAt:serverTimestamp()}));
 await assertFails(getDoc(doc(db('bob'),'tradeInventories/alice')));
 await assertSucceeds(deleteDoc(doc(db('alice'),'tradeInventories/alice')));
});

test('two phones cannot replace a newer backup using the same stale baseline',async()=>{
 const store=db('alice'); await save(store);
 const first='00000000-0000-0000-0000-000000000002';
 const second='00000000-0000-0000-0000-000000000003';
 const root=doc(store,'accountBackups/alice');
 async function fromPhone(next){return runTransaction(store,async tx=>{
  const current=await tx.get(root);if(current.data().head!==id)throw new Error('stale baseline');
  const snapshot=doc(store,`accountBackups/alice/snapshots/${next}`);
  tx.set(snapshot,{parts:1,bytes:500,hash:'b'.repeat(64),createdAt:serverTimestamp()});
  tx.set(doc(store,`accountBackups/alice/snapshots/${next}/chunks/0`),{index:0,data:'new-copy'});
  tx.set(root,{head:next,updatedAt:serverTimestamp()});
 });}
 const outcomes=await Promise.allSettled([fromPhone(first),fromPhone(second)]);
 assert.equal(outcomes.filter(x=>x.status==='fulfilled').length,1);
 assert.equal(outcomes.filter(x=>x.status==='rejected').length,1);
 const head=(await getDoc(root)).data().head;assert.ok([first,second].includes(head));
 assert.equal((await getDoc(doc(store,`accountBackups/alice/snapshots/${id}/chunks/0`))).data().data,'encoded');
 assert.equal((await getDoc(doc(store,`accountBackups/alice/snapshots/${head}/chunks/0`))).data().data,'new-copy');
});
test('background notification queries are limited to the current account metadata',async()=>{
 await env.withSecurityRulesDisabled(async c=>{
  for(const [name,members,sender]of [['alice__bob',['alice','bob'],'bob'],['bob__carol',['bob','carol'],'carol']]){
   await setDoc(doc(c.firestore(),'conversations',name),{members,lastSenderId:sender,updatedAt:serverTimestamp()});
  }
  await setDoc(doc(c.firestore(),'reads/alice/threads/alice__bob'),{seenAt:serverTimestamp()});
 });
 const heads=await assertSucceeds(getDocs(query(collection(db('alice'),'conversations'),where('members','array-contains','alice'),limit(100))));
 assert.deepEqual(heads.docs.map(x=>x.id),['alice__bob']);
 await assertSucceeds(getDocs(query(collection(db('alice'),'reads/alice/threads'),limit(100))));
 await assertSucceeds(getDocs(query(collection(db('alice'),'friendships'),where('members','array-contains','alice'),limit(100))));
 await assertFails(getDocs(query(collection(db('bob'),'reads/alice/threads'),limit(100))));
 await assertFails(getDocs(query(collection(env.unauthenticatedContext().firestore(),'conversations'),where('members','array-contains','alice'),limit(100))));
});
