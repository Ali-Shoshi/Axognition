const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
const root=path.join(__dirname,'src/main/resources/lessons');
const server=http.createServer((req,res)=>{
  const name=path.basename(req.url.split('?')[0]);
  if(!/^(geometry|fractions)(\.sq)?\.(html|css|js|json)$/.test(name))return res.writeHead(404).end();
  res.setHeader('Content-Type',{html:'text/html',css:'text/css',js:'text/javascript',json:'application/json'}[name.split('.').pop()]);
  res.end(fs.readFileSync(path.join(root,name)));
});
(async()=>{
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try {
  for(const id of ['geometry','fractions']) {
   const page=await browser.newPage({viewport:{width:1316,height:730}}),errors=[];
   page.on('pageerror',e=>errors.push(String(e)));
   await page.addInitScript(()=>{
    window.testTime=0;Object.defineProperty(performance,'now',{value:()=>testTime});
    window.sent=[];window.acceptEvents=true;
    window.GeometryVoice={stop(){},speak(){return true},setRate(){},hideKeyboard(){},setCompleted(){return true},
      trackEvent(payload){if(!acceptEvents)return false;sent.push(JSON.parse(payload));return true}};
   });
   await page.goto(`http://127.0.0.1:${server.address().port}/${id}.html`);
   await page.waitForFunction(()=>typeof lesson!=='undefined'&&lesson&&!$('start').disabled);
   await page.locator('#start').click();
   await page.evaluate(()=>{preparationRemaining=0;tickPreparation()});
   const advance=ms=>page.evaluate(ms=>{testTime+=ms;updateNext()},ms);
   assert.equal(await page.locator('#next').isDisabled(),true);
   await advance(10000);await page.locator('#next').click();
   await advance(10000);await page.locator('#next').click();
   assert.equal(await page.evaluate(()=>cue),2);
   await page.locator('#previous').click();assert.equal(await page.locator('#next').isEnabled(),true);
   await page.locator('#previous').click();assert.equal(await page.locator('#next').isEnabled(),true);
   await page.locator('#next').click();await page.locator('#next').click();
   assert.equal(await page.evaluate(()=>cue),2);assert.equal(await page.locator('#next').isDisabled(),true,'Unfinished slide still waits');
   let events=await page.evaluate(()=>sent);
   assert.equal(events.filter(e=>e.type==='lecture_started').length,1);
   assert.equal(events.filter(e=>e.type==='slide_back').length,2);
   assert(events.filter(e=>e.type==='slide_back').every(e=>e.revisit));
   assert.equal(events.find(e=>e.type==='slide_next').elapsedMs,10000);
   assert(events.some(e=>e.type==='slide_next'&&e.revisit&&e.elapsedMs===0));
   await page.evaluate(()=>showQuestion(0));
   const answer=await page.evaluate(()=>current().questions[0].answer);
   await advance(2300);await page.locator('#answers > button').nth((answer+1)%6).click();
   await advance(1100);assert.equal(await page.locator('#answers > button').nth((answer+2)%6).isDisabled(),true);
   await advance(900);assert.equal(await page.locator('#answers > button').nth(answer).isDisabled(),true);
   events=(await page.evaluate(()=>sent)).filter(e=>e.type==='answer_submitted');
   assert.deepEqual(events.map(e=>e.elapsedMs),[2300]);
   assert.deepEqual(events.map(e=>e.sinceAttemptMs),[2300]);
   assert.equal(new Set(events.map(e=>e.visitId)).size,1);
   await page.locator('#continue').click();
   await advance(1000);await page.evaluate(()=>window.geometryPause());
   await advance(4000);await page.evaluate(()=>window.geometryResume());
   await advance(1500);
   const number=await page.evaluate(()=>current().questions[1].answer);
   await page.locator('#answers input').fill(String(number));await page.locator('#answers input').press('Enter');
   const numeric=await page.evaluate(()=>sent.filter(e=>e.type==='answer_submitted').at(-1));
   assert.equal(numeric.elapsedMs,6500);assert.equal(numeric.activeMs,2500);
   // A failed native handoff survives refresh with the same event UUID and payload.
   await page.evaluate(()=>{acceptEvents=false;trackActivity('voice_speed',{value:'1.25'});save()});
   const pending=await page.evaluate(()=>JSON.parse(localStorage.getItem(STORE+':outbox')));
   assert.equal(pending.length,1);
   await page.evaluate(()=>lessonProgressLoaded('late-response',JSON.stringify({state:{lessonVersion:lesson.version,generation:99},pending:false})));
   assert.equal(await page.evaluate(()=>generation),0,'A remote response cannot replace work still waiting for native storage');
   await page.reload();await page.waitForFunction(()=>typeof lesson!=='undefined'&&lesson&&!$('start').disabled);
   assert.deepEqual(await page.evaluate(()=>sent[0]),pending[0]);
   assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem(STORE+':outbox'))),[]);
   await page.locator('#start').click();
   await page.evaluate(()=>{if(preparing){preparationRemaining=0;tickPreparation()}});
   await page.evaluate(()=>move(0,0,false));assert.equal(await page.locator('#next').isEnabled(),true,'Finished slide survives reload');
   // Server progress on another device grants review and prefills both question types.
   await page.evaluate(()=>{
    const state={scoringVersion:1,lessonVersion:lesson.version,generation:0,checkpointRevisions:lesson.chapters.map(c=>c.checkpointRevision||1),
     completedSlides:[],questionAnswers:Object.fromEntries(lesson.chapters.map((c,i)=>[i,c.questions.map(()=>true)])),completedAt:new Date().toISOString()};
    state.attemptedAnswers=state.questionAnswers;
    state.lectureResults=[{generation:0,lessonVersion:lesson.version,checkpointRevisions:state.checkpointRevisions,correct:totalQuestions(),total:totalQuestions(),percent:100,passed:true,completedAt:state.completedAt,retryAt:null,units:[]}];
    applyServerProgress(state);beginLesson(true);move(9,0,false);
   });
   assert.equal(await page.locator('#next').isEnabled(),true,'Completed lecture skips every slide, even previously unvisited slides');
   await page.evaluate(()=>showQuestion(0));assert.equal(await page.locator('#answers > button.correct').count(),1);
   assert.equal(await page.locator('#answers > button:disabled').count(),6);
   await page.locator('#continue').click();
   assert.equal(await page.locator('#answers input').inputValue(),String(await page.evaluate(()=>current().questions[1].answer)));
   assert.equal(await page.locator('#answers input').isDisabled(),true);
   // A remote reset removes local completion and the review bypass.
   await page.evaluate(()=>{
    applyServerProgress({scoringVersion:1,lessonVersion:lesson.version,generation:1,checkpointRevisions:lesson.chapters.map(c=>c.checkpointRevision||1),
     completedSlides:[],questionAnswers:{},completedAt:null});move(0,0,false);
   });
   assert.equal(await page.locator('#next').isDisabled(),true);
   assert.equal(await page.evaluate(()=>allCheckpointsComplete()),false);
   await page.evaluate(()=>{geometryExit();geometryExit()});
   assert.equal(await page.evaluate(()=>sent.filter(e=>e.type==='lecture_exit'&&e.sessionId===sessionId).length),1);
   assert.deepEqual(errors,[]);await page.close();
  }
  console.log('PASS: both lectures: revisit gating, completion review/prefills, per-attempt and active timing, Back events, reload-safe UUID outbox, remote reset and single exit.');
 }finally{await browser.close();server.close()}
})().catch(e=>{console.error(e);server.close();process.exitCode=1});
