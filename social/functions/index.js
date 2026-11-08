import {initializeApp} from 'firebase-admin/app';
import {getFirestore,FieldValue} from 'firebase-admin/firestore';
import {getMessaging} from 'firebase-admin/messaging';
import {onDocumentCreated,onDocumentWritten} from 'firebase-functions/v2/firestore';
import {eventNotice,mayAlert} from './policy.js';
initializeApp();const db=getFirestore();const options={region:'europe-west1',maxInstances:1,retry:true};
async function relay(notice,members,eventId){
 if(!notice || !Array.isArray(members) || members.length!==2 || !members.includes(notice.actor))return;
 const recipient=members.find(x=>x!==notice.actor);const sorted=[...members].sort();
 const [a,b,blockA,blockB,friend,settings,state]=await Promise.all([
  db.doc('testMembers/'+notice.actor).get(),db.doc('testMembers/'+recipient).get(),db.doc(`blocks/${notice.actor}/users/${recipient}`).get(),db.doc(`blocks/${recipient}/users/${notice.actor}`).get(),
  db.doc('friendships/'+sorted.join('__')).get(),db.doc('messengerSettings/'+recipient).get(),db.doc(`messenger/${recipient}/threads/${notice.key.replace('message:','')}`).get()]);
 if(!a.get('enabled') || !b.get('enabled') || blockA.exists || blockB.exists)return;
 if(notice.tab!=='Amis' && friend.get('status')!=='accepted')return;
 const version=notice.at?.toMillis?.()??0;if(!version || !mayAlert(settings.data(),state.data(),notice.tab,version,Date.now()))return;
 if(notice.tab==='Messages'){
  const read=await db.doc(`reads/${recipient}/threads/${notice.key.slice(8)}`).get();if((read.get('seenAt')?.toMillis?.()??0)>=version)return;
 }
 // A lease prevents concurrent duplicate triggers. Data-only Android messages dedupe again on device.
 const eventKey=(await import('node:crypto')).createHash('sha256').update(eventId+recipient).digest('hex');const marker=db.doc('pushEvents/'+eventKey);
 const acquired=await db.runTransaction(async tx=>{const d=await tx.get(marker);if(d.get('done'))return 'done';if((d.get('leasedAt')?.toMillis?.()??0)>Date.now()-60000)return 'busy';tx.set(marker,{leasedAt:FieldValue.serverTimestamp()});return 'acquired';});if(acquired==='done')return;if(acquired==='busy')throw new Error('Notification lease busy');
 const devices=await db.collection(`pushDevices/${recipient}/devices`).limit(20).get();
 if(devices.empty){await marker.set({done:true});return;}
 const response=await getMessaging().sendEachForMulticast({tokens:devices.docs.map(x=>x.get('token')),data:{owner:recipient,tab:notice.tab,key:notice.key,version:String(version)},android:{priority:'high',ttl:24*3600*1000,collapseKey:notice.key.slice(0,64)}});
 let retry=false;
 for(let i=0;i<response.responses.length;i++){
  const result=response.responses[i];if(!result.success){if(['messaging/registration-token-not-registered','messaging/invalid-registration-token'].includes(result.error?.code))await devices.docs[i].ref.delete();else retry=true;}
 }
 await marker.set({done:!retry,leasedAt:null});if(retry)throw new Error('Temporary notification delivery failure');
}
export const messageAlert=onDocumentCreated({...options,document:'conversations/{thread}/messages/{message}'},async event=>{
 const message=event.data?.data();const thread=await db.doc('conversations/'+event.params.thread).get();await relay(eventNotice('message',null,message,event.params.thread),thread.get('members'),event.id);
});
export const friendAlert=onDocumentWritten({...options,document:'friendships/{pair}'},async event=>{const before=event.data?.before.data(),after=event.data?.after.data();await relay(eventNotice('friend',before,after,event.params.pair),after?.members,event.id)});
export const tradeAlert=onDocumentWritten({...options,document:'trades/{trade}'},async event=>{const before=event.data?.before.data(),after=event.data?.after.data();await relay(eventNotice('trade',before,after,event.params.trade),after?.members,event.id)});
