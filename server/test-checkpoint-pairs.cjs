const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
const root=path.join(__dirname,'src/main/resources/lessons'),output=path.join(__dirname,'build/checkpoint-pairs');
const server=http.createServer((req,res)=>{
  const name=path.basename(req.url.split('?')[0]);
  if(!/^(geometry|fractions)(\.sq)?\.(html|css|js|json)$/.test(name))return res.writeHead(404).end();
  res.setHeader('Content-Type',{html:'text/html',css:'text/css',js:'text/javascript',json:'application/json'}[name.split('.').pop()]);
  res.end(fs.readFileSync(path.join(root,name)));
});
(async()=>{
  await new Promise(r=>server.listen(0,'127.0.0.1',r));
  const browser=await chromium.launch({channel:'chrome',headless:true});
  fs.mkdirSync(output,{recursive:true});
  try {
    for(const id of ['geometry','fractions'])for(const language of ['en','sq']) {
      const data=JSON.parse(fs.readFileSync(path.join(root,id+(language==='sq'?'.sq':'')+'.json'),'utf8'));
      for(const c of data.chapters) {
        assert.equal(c.questions.length,2);
        const [choice,typed]=c.questions;
        assert.equal(choice.type,'choice');assert.equal(choice.options.length,6);
        assert.equal(new Set(choice.options).size,6);assert(choice.answer>=0&&choice.answer<6);
        assert.equal(typed.type,'number');assert(Number.isFinite(typed.answer));
        assert(choice.explanation&&typed.explanation);
      }
      const page=await browser.newPage({viewport:{width:1316,height:730}}),errors=[];
      page.on('pageerror',e=>errors.push(String(e)));
      await page.addInitScript(()=>{
        window.events=[];
        window.GeometryVoice={stop(){},speak(){return true},setRate(){},hideKeyboard(){},
          setCompleted:done=>{events.push(done);return true},finish:()=>true};
      });
      const url=`http://127.0.0.1:${server.address().port}/${id}.html?lang=${language}&theme=dark`;
      const ready=()=>page.waitForFunction(()=>typeof lesson!=='undefined'&&lesson&&$('start').disabled===false);
      await page.goto(url);await ready();await page.locator('#start').click();
      for(let chapter=0;chapter<10;chapter++) {
        await page.evaluate(ch=>{move(ch,3,false);showQuestion()},chapter);
        assert.equal(await page.locator('#answers > button').count(),6);
        assert.equal(await page.evaluate(()=>questionIndex),0);
        assert.equal(await page.locator('#continue').isHidden(),true);
        // Calling Continue before a correct answer cannot skip a question.
        await page.evaluate(()=>completeOrContinue());assert.equal(await page.evaluate(()=>questionIndex),0);
        const [choice,typed]=data.chapters[chapter].questions;
        await page.locator('#answers > button').nth((choice.answer+1)%6).click();
        assert.equal(await page.locator('#continue').isHidden(),true);
        if(chapter===0)await page.screenshot({path:path.join(output,`${id}-${language}-six-choices.png`)});
        await page.locator('#answers > button').nth(choice.answer).click();
        assert.equal(await page.evaluate(ch=>answers[ch]===true,chapter),false,'First answer alone is not a checkpoint');
        assert.equal(await page.evaluate(()=>allCheckpointsComplete()),false);
        assert.equal(await page.evaluate(()=>recordCompletion()),false);
        await page.locator('#continue').click();
        assert.equal(await page.evaluate(()=>questionIndex),1);
        assert.equal(await page.locator('#answers > button').count(),0);
        assert.equal(await page.locator('#answers input').count(),1);
        if(chapter===0) {
          // Reload while the second question is unanswered: keep the first result.
          await page.reload();await ready();await page.locator('#start').click();
          assert.equal(await page.evaluate(()=>questionIndex),1);
          assert.equal(await page.locator('#question').textContent(),typed.prompt);
          assert.equal(await page.evaluate(()=>questionAnswers[0][0]),true);
          assert.equal(await page.locator('#continue').isHidden(),true);
          await page.locator('#answers button').click();
          assert.equal(await page.locator('#continue').isHidden(),true,'Empty answers are rejected');
          // No keyboard or orientation change can complete an unanswered question.
          await page.setViewportSize({width:360,height:640});
        }
        await page.locator('#answers input').fill(String(typed.answer+1));
        await page.locator('#answers input').press('Enter');
        assert.equal(await page.locator('#continue').isHidden(),true);
        assert.equal(await page.evaluate(ch=>answers[ch]===true,chapter),false);
        await page.locator('#answers input').fill(String(typed.answer));
        await page.locator('#answers input').press('Enter');
        assert.equal(await page.evaluate(ch=>answers[ch],chapter),true);
        if(chapter===0)await page.screenshot({path:path.join(output,`${id}-${language}-typed-answer.png`)});
        await page.locator('#continue').click();
        await page.setViewportSize({width:1316,height:730});
      }
      assert.equal(await page.locator('#finished').isVisible(),true);
      assert.equal(await page.locator('#finish').isEnabled(),true);
      assert.equal(await page.evaluate(()=>Object.values(questionAnswers).flat().filter(Boolean).length),20);
      assert.equal(await page.evaluate(()=>events.includes(true)),true);
      await page.reload();await ready();
      assert.equal(await page.evaluate(()=>allCheckpointsComplete()),true);
      if(id==='fractions') {
        // A completed older checkpoint must become available for the new problems.
        await page.addInitScript(()=>{
          if(sessionStorage.getItem('old-final-checkpoint-seeded'))return;
          sessionStorage.setItem('old-final-checkpoint-seeded','true');
          const key='axognition-fractions-v1';
          const saved=JSON.parse(localStorage.getItem(key));
          delete saved.finalCheckpointRevision;
          localStorage.setItem(key,JSON.stringify(saved));
        });
        await page.reload();await ready();
        assert.equal(await page.evaluate(()=>answers[8]),true);
        assert.equal(await page.evaluate(()=>answers[9]===true),false);
        assert.equal(await page.evaluate(()=>questionAnswers[9].some(Boolean)),false);
        assert.equal(await page.evaluate(()=>allCheckpointsComplete()),false);
        assert.equal(await page.evaluate(()=>events.at(-1)),false);
        await page.locator('#start').click();
        await page.evaluate(()=>{move(9,3,false);showQuestion()});
        assert.equal(await page.locator('#question').textContent(),data.chapters[9].questions[0].prompt);
        await page.locator('#answers > button').nth(data.chapters[9].questions[0].answer).click();
        await page.locator('#continue').click();
        await page.locator('#answers input').fill(String(data.chapters[9].questions[1].answer));
        await page.locator('#answers input').press('Enter');
        await page.locator('#continue').click();
        assert.equal(await page.evaluate(()=>allCheckpointsComplete()),true);
        assert.equal(await page.evaluate(()=>events.at(-1)),true);
      }
      await page.locator('#contentsButton').click();page.once('dialog',d=>d.accept());
      await page.locator('#restart').click();
      assert.equal(await page.evaluate(()=>Object.keys(questionAnswers).length),0);
      assert.equal(await page.evaluate(()=>events.at(-1)),false);
      assert.deepEqual(errors,[]);await page.close();
    }
    console.log('PASS: both lectures/languages, 80 questions, six unique choices, typed answers, wrong/empty answer gates, partial resume, 20-answer completion, revised final checkpoint and restart.');
  } finally { await browser.close();server.close(); }
})().catch(e=>{console.error(e);server.close();process.exitCode=1});
