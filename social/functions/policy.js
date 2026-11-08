export function eventNotice(type,before,after,id){
 if(!after)return null;
 if(type==='message')return {actor:after.senderId,tab:'Messages',key:'message:'+id,at:after.createdAt};
 if(type==='friend'){
  if(!before && after.status==='pending')return {actor:after.requester,tab:'Amis',key:'friend:'+id,at:after.updatedAt};
  if(before?.status==='pending' && after.status==='accepted')return {actor:after.members.find(x=>x!==after.requester),tab:'Amis',key:'friend-accepted:'+id,at:after.updatedAt};
 }
 if(type==='trade' && before?.status!==after.status)return {actor:!before || after.status==='cancelled'?after.requester:after.members.find(x=>x!==after.requester),tab:'Échanges',key:'trade:'+id,at:after.updatedAt};
 return null;
}
export function mayAlert(settings,state,tab,version,now){
 const key=tab==='Messages'?'messages':tab==='Amis'?'friends':'trades';
 return settings?.[key]!==false && (tab!=='Messages' || (!(state?.mutedUntil>now) && !(state?.hiddenBefore>=version)));
}
