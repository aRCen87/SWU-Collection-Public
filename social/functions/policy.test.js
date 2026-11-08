import {test} from 'node:test';import assert from 'node:assert/strict';import {eventNotice,mayAlert} from './policy.js';
test('events target the recipient, suppress unchanged updates and include friendship acceptance',()=>{
 const pending={members:['a','b'],requester:'a',status:'pending',updatedAt:1};
 assert.equal(eventNotice('friend',null,pending,'ab').actor,'a');assert.equal(eventNotice('friend',pending,{...pending,status:'accepted'},'ab').actor,'b');
 assert.equal(eventNotice('friend',pending,pending,'ab'),null);assert.equal(eventNotice('trade',pending,pending,'ab'),null);
 assert.equal(eventNotice('trade',pending,{...pending,status:'cancelled'},'ab').actor,'a');
});
test('muted, hidden or disabled threads cannot alert; a genuinely newer message can reappear',()=>{
 assert.equal(mayAlert({messages:false},{},'Messages',10,100),false);
 assert.equal(mayAlert({}, {mutedUntil:200},'Messages',10,100),false);
 assert.equal(mayAlert({}, {hiddenBefore:10},'Messages',10,100),false);
 assert.equal(mayAlert({}, {hiddenBefore:10},'Messages',11,100),true);
});
