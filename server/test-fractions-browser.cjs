const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
const root=path.join(__dirname,'src/main/resources/lessons'),output=path.join(__dirname,'build/fractions-preview');
const english=JSON.parse(fs.readFileSync(path.join(root,'fractions.json'),'utf8'));
const albanian=JSON.parse(fs.readFileSync(path.join(root,'fractions.sq.json'),'utf8'));
albanian.chapters.forEach((c,i)=>{
  const en=english.chapters[i];
  c.questions.forEach((q,j)=>assert.equal(q.answer,en.questions[j].answer));
  assert.notEqual(c.title,en.title);
  c.cues.forEach((s,j)=>{assert.deepEqual(s.model,en.cues[j].model);assert.notEqual(s.text,en.cues[j].text);});
});
const server=http.createServer((req,res)=>{
  const file=path.basename(req.url.split('?')[0]);
  if(!/^(geometry|fractions)(\.sq)?\.(html|css|js|json)$/.test(file))return res.writeHead(404).end();
  res.setHeader('Content-Type',{html:'text/html',css:'text/css',js:'text/javascript',json:'application/json'}[file.split('.').pop()]);
  res.end(fs.readFileSync(path.join(root,file)));
});
(async()=>{
  await new Promise(r=>server.listen(0,'127.0.0.1',r));
  const browser=await chromium.launch({channel:'chrome',headless:true});
  const page=await browser.newPage({viewport:{width:1316,height:730}}),errors=[];
  page.on('pageerror',e=>errors.push(String(e)));
  await page.addInitScript(()=>{
    window.testTime=0;Object.defineProperty(performance,'now',{value:()=>testTime});
    window.bridgeEvents=[];
    window.GeometryVoice={progressKey:()=> 'axognition-fractions-v1:student-a',
      speak:(text,id)=>{bridgeEvents.push(['speak',text,id]);return true},stop:()=>bridgeEvents.push(['stop']),
      setRate:rate=>bridgeEvents.push(['rate',rate]),setCompleted:done=>{bridgeEvents.push(['complete',done]);return true},
      hideKeyboard:()=>bridgeEvents.push(['keyboard']),finish:()=>{bridgeEvents.push(['finish']);return true}};
  });
  const url=`http://127.0.0.1:${server.address().port}/fractions.html`;
  const load=async(language='en',theme='light')=>{
    await page.goto(url+`?lang=${language}&theme=${theme}`);
    await page.waitForFunction(()=>typeof lesson!=='undefined'&&lesson&&$('start').disabled===false);
  };
  fs.mkdirSync(output,{recursive:true});
  const failures=[];let checks=0;
  async function audit(name,diagram=false) {
    const issues=await page.evaluate(diagram=>{
      const issues=[],rect=e=>e.getBoundingClientRect();
      const inside=(a,b)=>a.left>=b.left-1&&a.right<=b.right+1&&a.top>=b.top-1&&a.bottom<=b.bottom+1;
      const viewport={left:0,top:0,right:innerWidth,bottom:innerHeight};
      if(document.documentElement.scrollWidth>innerWidth+1)issues.push('horizontal overflow');
      if(document.documentElement.scrollHeight>innerHeight+1)issues.push('vertical overflow');
      for(const sel of ['header','footer','.stage'])if(!inside(rect(document.querySelector(sel)),viewport))issues.push(sel+' outside viewport');
      for(const e of document.querySelectorAll('header,footer,.intro,.bubble,.question-card,#answers form')) {
        if(e.getClientRects().length&&e.scrollWidth>e.clientWidth+1)issues.push((e.id||e.className)+' horizontal overflow');
      }
      if(diagram) {
        const scene=rect($('scene')),caption=rect(document.querySelector('.narrator')),heading=rect(document.querySelector('.stage-heading'));
        if(scene.top<heading.bottom-1)issues.push('diagram overlaps heading');
        if(scene.left<caption.right-1&&scene.right>caption.left+1&&scene.top<caption.bottom-1&&scene.bottom>caption.top+1)issues.push('diagram overlaps caption');
        for(const e of $('scene').querySelectorAll('text,rect,circle,path'))if(!inside(rect(e),scene))issues.push('clipped model: '+(e.textContent||e.getAttribute('class')));
        if(innerWidth>=600&&innerHeight>=600&&$('stage').scrollHeight>$('stage').clientHeight+1)issues.push('tablet stage needs scrolling');
        if(!inside(rect($('caption')),caption))issues.push('caption clipped');
      }
      return issues;
    },diagram);
    checks++;if(issues.length)failures.push({name,issues});
  }
  try {
    await load();
    // Claim old progress once, without mixing it with geometry or another student.
    await page.addInitScript(()=>{
      if(sessionStorage.getItem('seeded'))return;
      sessionStorage.setItem('seeded','true');
      localStorage.clear();localStorage.setItem('axognition-fractions-v1',JSON.stringify({index:2,elapsed:90,answers:{0:true,1:true}}));
      localStorage.setItem('axognition-geometry-v1',JSON.stringify({chapter:7}));
    });
    await load();assert.equal(await page.evaluate(()=>chapter),2);assert.equal(await page.evaluate(()=>cue),2);
    assert.equal(await page.evaluate(()=>questionAnswers[1][0]),true);
    assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('axognition-geometry-v1')).chapter),7);
    await page.locator('#start').click();
    await page.evaluate(()=>{if(preparing){preparationRemaining=0;tickPreparation()}});
    assert.equal(await page.locator('#next').isDisabled(),true);
    await page.evaluate(()=>{testTime=9999;updateNext();$('next').onclick()});assert.equal(await page.evaluate(()=>cue),2);
    await page.evaluate(()=>{geometrySetTheme(true);selectVoiceSpeed(1.5)});
    await page.setViewportSize({width:823,height:1223});assert.equal(await page.locator('#next').isDisabled(),true);
    await page.evaluate(()=>{testTime=10000;updateNext()});await page.locator('#next').click();
    assert.equal(await page.evaluate(()=>cue),3);
    await page.locator('#side').fill('12');await page.locator('#side').dispatchEvent('input');
    assert.equal(await page.locator('#sideValue').textContent(),'1/12');
    assert.equal(await page.locator('.fraction-slot').count(),12);
    await load('sq','dark');assert.equal(await page.evaluate(()=>voiceSpeed),1.5);
    assert.equal(await page.locator('html').getAttribute('lang'),'sq');
    assert.equal(await page.evaluate(()=>questionAnswers[1][0]),true);
    await page.locator('#start').click();
    await page.evaluate(()=>{if(preparing){preparationRemaining=0;tickPreparation()}});
    assert.equal(await page.locator('#caption').textContent(),albanian.chapters[2].cues[3].text);
    // Every question is answered once, and completion is saved before exit.
    await page.evaluate(()=>{answers={};questionAnswers={};attemptedAnswers={};save()});
    for(let ch=0;ch<10;ch++) {
      await page.evaluate(ch=>{move(ch,3,false);showQuestion()},ch);
      for(const q of albanian.chapters[ch].questions) {
      if(q.type==='choice') {
        await page.locator('#answers button').nth(q.answer).click();
      } else {
        await page.locator('#answers input').fill(String(q.answer));
        await page.setViewportSize({width:1316,height:730});
        assert.equal(await page.locator('#answers input').inputValue(),String(q.answer));
        await page.locator('#answers input').press('Enter');
        assert(await page.evaluate(()=>bridgeEvents.some(e=>e[0]==='keyboard')));
      }
      await page.locator('#continue').click();
      }
    }
    assert.equal(await page.locator('#finished').isVisible(),true);
    await page.locator('#finish').click();
    assert(await page.evaluate(()=>bridgeEvents.some(e=>e[0]==='finish')));
    assert(await page.evaluate(()=>bridgeEvents.some(e=>e[0]==='complete'&&e[1]===true)));
    await page.locator('#review').click();page.once('dialog',dialog=>dialog.accept());
    await page.locator('#restart').click();assert.equal(await page.evaluate(()=>Object.keys(answers).length),0);
    assert.equal(await page.evaluate(()=>allCheckpointsComplete()),false);
    for(const language of ['en','sq'])for(const theme of ['light','dark']) {
      for(const [width,height] of [[360,640],[600,900],[823,1223],[1316,730],[820,360]]) {
        await page.setViewportSize({width,height});await load(language,theme);
        await audit(`${language} ${theme} ${width} welcome`);
        await page.locator('#start').click();await page.evaluate(()=>setPlaying(false));
        await page.evaluate(()=>{if(preparing){preparationRemaining=0;tickPreparation()}});
        for(let ch=0;ch<10;ch++) {
          for(let cue=0;cue<4;cue++) {
            await page.evaluate(([ch,cue])=>move(ch,cue,false),[ch,cue]);
            await audit(`${language} ${theme} ${width} ${ch}/${cue}`,true);
          }
          await page.evaluate(()=>showQuestion(0));await audit(`${language} ${theme} ${width} question ${ch}`);
        }
        await page.evaluate(()=>move(4,3,false));
        if(width===1316||width===823)await page.screenshot({path:path.join(output,`${language}-${theme}-${width}.png`)});
      }
    }
    // Animation endpoints and reduced motion retain the same mathematical result.
    await page.setViewportSize({width:1316,height:730});
    await page.waitForFunction(()=>document.documentElement.style.getPropertyValue('--lesson-height')==='730px' && !document.documentElement.classList.contains('portrait-lesson'));
    await page.evaluate(()=>document.fonts.ready);
    for(const [ch,sc] of [[0,3],[3,2],[6,1],[7,1],[8,2]]) {
      await page.evaluate(([ch,sc])=>move(ch,sc,true),[ch,sc]);
      for(const time of [0,1000,5000]) {
        await page.evaluate(t=>document.getAnimations().forEach(a=>{a.pause();a.currentTime=t}),time);
        await audit(`animation ${ch}/${sc} at ${time}`,true);
      }
    }
    await page.emulateMedia({reducedMotion:'reduce'});
    await page.evaluate(()=>move(7,1,true));
    assert.equal(await page.locator('.fraction-away').first().evaluate(e=>getComputedStyle(e).opacity),'0');
    await audit('reduced motion',true);
    await page.evaluate(()=>geometryPause());assert.equal(await page.evaluate(()=>playing),false);
    // New student must not inherit already-claimed legacy answers.
    await page.addInitScript(()=>{window.GeometryVoice.progressKey=()=> 'axognition-fractions-v1:student-b'});
    await load();assert.equal(await page.evaluate(()=>Object.keys(answers).length),0);
    assert.deepEqual(errors,[]);
    fs.writeFileSync(path.join(output,'results.json'),JSON.stringify({checks,failures},null,2));
    assert.equal(failures.length,0,JSON.stringify(failures.slice(0,12),null,2));
    console.log(`PASS: ${checks} browser checks; both languages/themes, responsive scenes, quizzes, voice controls, migration, student isolation, finish and restart.`);
  } finally { await browser.close();server.close(); }
})().catch(e=>{console.error(e);server.close();process.exitCode=1});
