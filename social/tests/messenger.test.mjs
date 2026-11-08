import {readFileSync} from 'node:fs';
import {before,after,beforeEach,test} from 'node:test';
import {initializeTestEnvironment,assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {doc,setDoc,getDoc,getDocs,collection,serverTimestamp} from 'firebase/firestore';
let env;
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-swu-community-test',firestore:{rules:readFileSync('firestore.rules','utf8')}})});
after(async()=>env.cleanup());
const db=id=>env.authenticatedContext(id,{email_verified:true}).firestore();
beforeEach(async()=>{await env.clearFirestore();await env.withSecurityRulesDisabled(async c=>{
 for(const id of ['alice','bob','carol'])await setDoc(doc(c.firestore(),'testMembers',id),{enabled:true});
 await setDoc(doc(c.firestore(),'friendships','alice__bob'),{members:['alice','bob'],status:'accepted',requester:'alice'});
 await setDoc(doc(c.firestore(),'conversations','alice__bob'),{members:['alice','bob'],updatedAt:serverTimestamp()});
});});
test('presence is owner-written, friends-only, blocked in both directions and revocable',async()=>{
 const ref=doc(db('alice'),'presence','alice');const p={visible:true,online:true,updatedAt:serverTimestamp()};
 await assertSucceeds(setDoc(ref,p));await assertSucceeds(getDoc(doc(db('bob'),'presence','alice')));
 await assertFails(getDoc(doc(db('carol'),'presence','alice')));await assertFails(getDocs(collection(db('bob'),'presence')));
 await assertFails(setDoc(doc(db('bob'),'presence','alice'),p));
 await setDoc(ref,{...p,visible:false});await assertFails(getDoc(doc(db('bob'),'presence','alice')));
 await setDoc(ref,p);await setDoc(doc(db('bob'),'blocks','bob','users','alice'),{createdAt:serverTimestamp()});
 await assertFails(getDoc(doc(db('bob'),'presence','alice')));
});
test('conversation hiding, mute and pins are private and never mutate the other participant',async()=>{
 const ref=doc(db('alice'),'messenger','alice','threads','alice__bob');const state={hiddenBefore:100,pinned:true,mutedUntil:123,hiddenMessages:['m1']};
 await assertSucceeds(setDoc(ref,state));await assertFails(getDoc(doc(db('bob'),'messenger','alice','threads','alice__bob')));
 await assertFails(setDoc(doc(db('bob'),'messenger','alice','threads','alice__bob'),state));
 await assertFails(setDoc(doc(db('carol'),'messenger','carol','threads','alice__bob'),state));
 await assertFails(setDoc(ref,{...state,mutedUntil:-1}));await assertSucceeds(getDoc(doc(db('bob'),'conversations','alice__bob')));
});
test('typing and receipts cannot be forged and read opt-out revokes historical receipts',async()=>{
 const ref=doc(db('alice'),'conversations','alice__bob','signals','alice');
 const signal={deliveredAt:serverTimestamp(),seenAt:serverTimestamp(),typingAt:serverTimestamp(),typing:true};
 await assertSucceeds(setDoc(ref,signal));await assertSucceeds(getDoc(doc(db('bob'),'conversations','alice__bob','signals','alice')));
 await assertFails(setDoc(doc(db('bob'),'conversations','alice__bob','signals','alice'),signal));
 await assertFails(getDoc(doc(db('carol'),'conversations','alice__bob','signals','alice')));
 await setDoc(doc(db('alice'),'messengerSettings','alice'),{sharePresence:true,shareRead:false});
 await assertFails(getDoc(doc(db('bob'),'conversations','alice__bob','signals','alice')));
 await assertFails(setDoc(ref,signal));await assertSucceeds(setDoc(ref,{...signal,seenAt:null}));
});
test('card/reply attachments are bounded and strangers and blocked players cannot send',async()=>{
 const msg={senderId:'alice',kind:'card',text:'Regarde',deckName:'',deckJson:'',cardJson:'{"set":"SOR","number":"5","language":"FR","variant":"Foil"}',replyId:'previous',replyText:'Oui',createdAt:serverTimestamp()};
 await assertSucceeds(setDoc(doc(db('alice'),'conversations','alice__bob','messages','card'),msg));
 await assertFails(setDoc(doc(db('alice'),'conversations','alice__bob','messages','large'),{...msg,cardJson:'x'.repeat(301)}));
 await assertFails(setDoc(doc(db('alice'),'conversations','alice__carol'),{members:['alice','carol'],updatedAt:serverTimestamp()}));
 await assertFails(setDoc(doc(db('bob'),'conversations','alice__bob','messages','spoof'),msg));
 await setDoc(doc(db('bob'),'blocks','bob','users','alice'),{createdAt:serverTimestamp()});
 await assertFails(setDoc(doc(db('alice'),'conversations','alice__bob','messages','blocked'),msg));
});
test('push tokens are private and Test-only',async()=>{
 const token={token:'test-token-for-emulator-only',package:'fr.swucollection.app.test',updatedAt:serverTimestamp()};const id='a'.repeat(64);
 await assertSucceeds(setDoc(doc(db('alice'),'pushDevices','alice','devices',id),token));
 await assertFails(getDoc(doc(db('bob'),'pushDevices','alice','devices',id)));
 await assertFails(setDoc(doc(db('bob'),'pushDevices','alice','devices',id),token));
 await assertFails(setDoc(doc(db('alice'),'pushDevices','alice','devices',id),{...token,package:'fr.swucollection.app'}));
});
test('hiding presence in settings revokes an old online document immediately',async()=>{
 await setDoc(doc(db('alice'),'presence','alice'),{visible:true,online:true,updatedAt:serverTimestamp()});
 await setDoc(doc(db('alice'),'messengerSettings','alice'),{sharePresence:false,shareRead:true});
 await assertFails(getDoc(doc(db('bob'),'presence','alice')));
 await assertFails(setDoc(doc(db('alice'),'presence','alice'),{visible:true,online:true,updatedAt:serverTimestamp()}));
});
