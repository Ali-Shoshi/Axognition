// Run with Playwright on NODE_PATH and a local Chrome installation.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
const root=path.join(__dirname,'src/main/resources/lessons');
const server=http.createServer((req,res)=>{
  const file=path.basename(req.url.split('?')[0]);
  if(!/^(geometry|fractions)(\.sq)?\.(html|css|js|json)$/.test(file))return res.writeHead(404).end();
  res.setHeader('Content-Type',{html:'text/html',css:'text/css',js:'text/javascript',json:'application/json'}[file.split('.').pop()]);
  res.end(fs.readFileSync(path.join(root,file)));
});
(async()=>{
  await new Promise(r=>server.listen(0,'127.0.0.1',r));
  const browser=await chromium.launch({channel:'chrome',headless:true});
  const errors=[];
  async function open(id,lang='en',extraQuestion=false){
    const page=await browser.newPage({viewport:{width:1316,height:730}});
    page.on('pageerror',e=>errors.push(String(e)));
    await page.addInitScript(()=>{
      window.testTime=0;window.wallTime=Date.parse('2026-09-20T12:00:00Z');
      const OriginalDate=Date;window.Date=class extends OriginalDate{constructor(...args){super(...(args.length?args:[wallTime]));}static now(){return wallTime;}};
      Object.defineProperty(performance,'now',{value:()=>testTime});
      window.events=[];window.nativeResults=[];window.nativePassed=false;window.closedLesson=false;
      window.GeometryVoice={stop(){},speak(){return true},setRate(){},hideKeyboard(){},
        trackEvent(payload){events.push(JSON.parse(payload));return true;},
        saveResults(payload){const state=JSON.parse(payload);nativeResults=state.lectureResults;nativePassed=state.passed;return true;},
        setCompleted(passed){nativePassed=passed;return true;},
        finish(){if(!nativeResults.length)return false;closedLesson=true;return true;}};
    });
    if(extraQuestion)await page.route(/\/(geometry|fractions)(\.sq)?\.json$/,async route=>{
      const data=JSON.parse(fs.readFileSync(path.join(root,id+(lang==='sq'?'.sq':'')+'.json'),'utf8'));
      data.chapters[0].questions.push({...data.chapters[0].questions[1]});
      await route.fulfill({json:data});
    });
    await page.goto(`http://127.0.0.1:${server.address().port}/${id}.html?lang=${lang}`);
    await page.waitForFunction(()=>typeof progressReady!=='undefined'&&progressReady);
    return page;
  }
  const ready=page=>page.waitForFunction(()=>typeof progressReady!=='undefined'&&progressReady);
  const start=async page=>{
    await page.locator('#start').click();
    assert.equal(await page.evaluate(()=>preparing),true);
    await page.evaluate(()=>{testTime+=14999;tickPreparation();});
    assert.equal(await page.evaluate(()=>started),false);
    await page.evaluate(()=>{testTime++;tickPreparation();setPlaying(false);});
    assert.equal(await page.evaluate(()=>started),true);
  };
  const answer=async(page,correct=true)=>{
    const q=await page.evaluate(()=>current().questions[questionIndex]);
    if(q.type==='choice')await page.locator('#answers > button').nth(correct?q.answer:(q.answer+1)%q.options.length).click();
    else {await page.locator('#answers input').fill(String(q.answer+(correct?0:1)));await page.locator('#answers input').press('Enter');}
    assert.equal(await page.locator('#continue').isVisible(),true);
    assert.equal(await page.locator('#answers button:enabled, #answers input:enabled').count(),0);
  };
  const finish=async(page,correct)=>{
    const chapters=await page.evaluate(()=>lesson.chapters.map(c=>c.questions.length));let index=0;
    for(let ch=0;ch<chapters.length;ch++){
      await page.evaluate(ch=>{move(ch,3,false);showQuestion(0);},ch);
      for(let q=0;q<chapters[ch];q++){await answer(page,index++<correct);await page.locator('#continue').click();}
    }
    assert.equal(await page.locator('#finished').isVisible(),true);
  };
  try{
    for(const id of ['geometry','fractions'])for(const lang of ['en','sq']){
      const page=await open(id,lang);await start(page);
      await finish(page,15);
      assert.equal(await page.evaluate(()=>lastResult().percent),75);
      assert.equal(await page.evaluate(()=>lastResult().passed),false);
      assert.equal(await page.evaluate(()=>cooldownUntil()-Date.now()),3600000);
      if(lang==='en'){
        const snapshot=await page.evaluate(()=>({scoringVersion:1,lessonVersion:lesson.version,generation,
          checkpointRevisions:revisions(),questionAnswers,attemptedAnswers,lectureResults,completedSlides:Object.keys(completedSlides),
          completedAt,passed:false,startCount:1,chapter,cue,questionIndex,inCheckpoint:false}));
        const other=await open(id);
        await other.evaluate(state=>applyServerProgress(state,true),snapshot);
        assert.equal(await other.locator('#start').isDisabled(),true,'A new device restores the cooldown');
        assert.equal(await other.evaluate(()=>nativeResults.length),1,'Unit summary restores from the server');
        assert.equal(await other.evaluate(()=>correctQuestions()),15);
        await other.evaluate(()=>applyServerProgress({scoringVersion:1,lessonVersion:lesson.version,generation:1,
          checkpointRevisions:revisions(),questionAnswers:{},attemptedAnswers:{},lectureResults,completedSlides:[],startCount:1},true));
        assert.equal(await other.evaluate(()=>chapter),0,'A remote retry resets the position');
        assert.equal(await other.evaluate(()=>questionIndex),0);
        await other.close();
      }
      assert.equal(await page.evaluate(()=>events.filter(e=>e.type==='lecture_finished').length),1);
      assert.equal(await page.locator('#review').isHidden(),true);
      await page.locator('#contentsButton').click();
      assert.equal(await page.locator('#chapterList button:disabled').count(),10);
      assert((await page.locator('#chapterList').innerText()).includes('100%'));
      assert.equal(await page.locator('#restart').isDisabled(),true);
      await page.locator('#closeContents').click();
      const at=await page.evaluate(()=>chapter);
      await page.evaluate(()=>{$('previous').onclick();$('play').onclick();move(0,0,true);});
      assert.equal(await page.evaluate(()=>chapter),at);assert.equal(await page.evaluate(()=>playing),false);
      await page.locator('#finish').click();assert.equal(await page.evaluate(()=>closedLesson),true,'Failed attempts can exit Android');
      await page.reload();await ready(page);
      assert.equal(await page.locator('#start').isDisabled(),true);
      assert((await page.locator('#attemptSummary').innerText()).includes('75%'));
      await page.locator('#contentsButton').click();assert.equal(await page.locator('#contents').isVisible(),true,'Stats remain available during cooldown');await page.locator('#closeContents').click();
      await page.evaluate(()=>{wallTime+=3600000;updateStartAvailability();});
      await start(page);
      assert.equal(await page.evaluate(()=>generation),1);
      assert.equal(await page.evaluate(()=>correctQuestions()),0);
      assert.equal(await page.evaluate(()=>Object.values(attemptedAnswers).flat().filter(Boolean).length),0);
      assert.equal(await page.evaluate(()=>events.filter(e=>e.type==='reset'&&e.generation===0).length),1);
      await finish(page,0);
      assert.equal(await page.evaluate(()=>cooldownUntil()-Date.now()),86400000);
      assert.equal(await page.evaluate(()=>lectureResults.length),2);
      await page.evaluate(()=>{wallTime+=86400000;updateStartAvailability();});
      await page.locator('#retry').click();await page.evaluate(()=>{preparationRemaining=0;tickPreparation();setPlaying(false);});
      await finish(page,16);
      assert.equal(await page.evaluate(()=>nativePassed),true);assert.equal(await page.evaluate(()=>lastResult().percent),80);
      assert.equal(await page.evaluate(()=>cooldownUntil()-Date.now()),86400000);
      await page.evaluate(()=>{wallTime+=86400000;updateStartAvailability();});
      await page.locator('#retry').click();await page.evaluate(()=>{preparationRemaining=0;tickPreparation();setPlaying(false);});
      await finish(page,20);
      assert.equal(await page.evaluate(()=>cooldownUntil()),0);assert.equal(await page.locator('#review').isVisible(),true);
      await page.locator('#review').click();await page.locator('#chapterList button').first().click();
      await page.evaluate(()=>showQuestion(0));assert.equal(await page.locator('#answers button:disabled').count(),6);
      assert.equal(await page.evaluate(()=>lectureResults.length),4,'Review does not add an attempt');
      await page.reload();await ready(page);await page.locator('#start').click();
      assert.equal(await page.evaluate(()=>reviewing),true);assert.equal(await page.evaluate(()=>preparing),false);
      // Restart after 100% creates a new attempt instead of an empty review.
      await page.locator('#contentsButton').click();page.once('dialog',d=>d.accept());await page.locator('#restart').click();
      assert.equal(await page.evaluate(()=>preparing),true);assert.equal(await page.evaluate(()=>Boolean(currentResult())),false);
      await page.close();
      console.log(`PASS ${id}/${lang}: 75% fail, 80% pass, wrong-answer locks, 1h/24h cooldowns, reload, retries, stats, Android Finish, perfect review.`);
    }
    for(const id of ['geometry','fractions']){
      const page=await open(id);await start(page);
      await page.locator('#contentsButton').click();await page.locator('#chapterList button').last().click();
      await page.evaluate(()=>{cue=3;showQuestion(0);});
      await answer(page,false);await page.evaluate(()=>$('answers').children[current().questions[0].answer].onclick());
      assert.equal(await page.evaluate(()=>questionAnswers[9][0]),false);
      assert.equal(await page.evaluate(()=>events.filter(e=>e.type==='answer_submitted').length),1);
      await page.locator('#continue').click();await answer(page);await page.locator('#continue').click();
      assert.equal(await page.evaluate(()=>lectureResults.length),0);assert.equal(await page.locator('#finished').isHidden(),true);
      assert.equal(await page.evaluate(()=>chapter),0,'Unfinished earlier chapters must still be completed');
      // A server snapshot carries wrong-answer locks, even on a new device.
      await page.evaluate(()=>{applyServerProgress({scoringVersion:1,lessonVersion:lesson.version,generation:0,startCount:1,checkpointRevisions:revisions(),completedSlides:[],questionAnswers:{0:[false,true]},attemptedAnswers:{0:[true,true]},lectureResults:[]});showQuestion(0);});
      assert.equal(await page.locator('#answers button:disabled').count(),6);
      assert.equal(await page.evaluate(()=>questionAnswers[0][0]),false);
      await page.close();
      const variable=await open(id,'en',true);await start(variable);
      await variable.evaluate(()=>showQuestion(2));await answer(variable,false);await variable.reload();await ready(variable);await start(variable);
      assert.equal(await variable.evaluate(()=>questionIndex),2,'Resume supports a third question');
      assert.equal(await variable.locator('#answers input').isDisabled(),true);
      await variable.close();
      for(const correct of [15,16]){
        const varied=await open(id,'en',true);await start(varied);await finish(varied,correct);
        assert.equal(await varied.evaluate(()=>lastResult().total),21);
        assert.equal(await varied.evaluate(()=>lastResult().passed),correct===16);
        await varied.close();
      }
      console.log(`PASS ${id}: no early result, one submission, remote answer locks, third-question resume, 21-question denominator.`);
    }
    const output=path.join(__dirname,'build/lecture-scoring');fs.mkdirSync(output,{recursive:true});
    for(const id of ['geometry','fractions'])for(const lang of ['en','sq']){
      const page=await open(id,lang);await page.locator('#start').click();
      for(const [width,height] of [[360,640],[820,360],[1316,730]]){
        await page.setViewportSize({width,height});
        const overflow=await page.evaluate(()=>({page:document.documentElement.scrollWidth>innerWidth+1,
          card:document.querySelector('.prepare-card').scrollWidth>document.querySelector('.prepare-card').clientWidth+1}));
        assert.deepEqual(overflow,{page:false,card:false},`${id}/${lang} preparation at ${width}x${height}`);
        await page.screenshot({path:path.join(output,`${id}-${lang}-rules-${width}.png`)});
      }
      await page.close();
    }
    assert.deepEqual(errors,[]);
  }finally{await browser.close();server.close();}
})().catch(e=>{console.error(e);server.close();process.exitCode=1;});
