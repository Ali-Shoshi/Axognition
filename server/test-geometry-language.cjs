const {solveCheckpoint}=require('./checkpoint-test-helpers.cjs');
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
const root=path.join(__dirname,'src/main/resources/lessons');
const en=JSON.parse(fs.readFileSync(path.join(root,'geometry.json'),'utf8'));
const sq=JSON.parse(fs.readFileSync(path.join(root,'geometry.sq.json'),'utf8'));
assert.equal(sq.chapters.length,en.chapters.length);
sq.chapters.forEach((c,i)=>{
 const original=en.chapters[i];assert.equal(c.shape,original.shape);assert.notEqual(c.title,original.title);
 c.questions.forEach((q,j)=>{const eq=original.questions[j];assert.equal(q.answer,eq.answer);assert.equal(q.type,eq.type);assert.equal(q.unit,eq.unit);assert.notEqual(q.prompt,eq.prompt);assert.notEqual(q.explanation,eq.explanation);});
 c.cues.forEach((cue,j)=>{assert.equal(cue.mode,original.cues[j].mode);assert.equal(cue.seconds,original.cues[j].seconds);assert.notEqual(cue.text,original.cues[j].text);assert(cue.text.length>80);});
});
const catalog=JSON.parse(fs.readFileSync(path.join(__dirname,'../app/src/main/assets/i18n/sq.json'),'utf8'));
for(const [english,albanian] of Object.entries(catalog)){
 assert(albanian.trim(),`Empty translation: ${english}`);
 assert.deepEqual((english.match(/\{\d+}/g)||[]).sort(),(albanian.match(/\{\d+}/g)||[]).sort(),`Placeholders differ: ${english}`);
}
const server=http.createServer((req,res)=>{
 const file=path.basename(req.url.split('?')[0]);
 if(!/^geometry(?:\.sq)?\.(html|css|js|json)$/.test(file))return res.writeHead(404).end();
 res.setHeader('Content-Type',{html:'text/html',css:'text/css',js:'text/javascript',json:'application/json'}[file.split('.').pop()]);
 res.end(fs.readFileSync(path.join(root,file)));
});
(async()=>{
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({channel:'chrome',headless:true});
 const page=await browser.newPage({viewport:{width:1316,height:730}}),errors=[];
 page.on('pageerror',e=>errors.push(String(e)));
 await page.addInitScript(()=>{
  window.spoken=[];
  window.GeometryVoice={progressKey:()=> 'axognition-geometry-v1:language-test',stop:()=>{},speak:(text)=>{spoken.push(text);return true},setRate:()=>{},setCompleted:()=>true};
 });
 const go=async language=>{await page.goto(`http://127.0.0.1:${server.address().port}/geometry.html?lang=${language}&theme=dark`);await page.waitForFunction(()=>typeof lesson!=='undefined'&&lesson&&$('start').disabled===false);};
 try{
  await go('en');await page.locator('#start').click();await page.evaluate(()=>{preparationRemaining=0;tickPreparation()});
  await page.evaluate(()=>{move(1,3,false);showQuestion()});
  await solveCheckpoint(page,en.chapters[1]);
  await page.evaluate(()=>selectVoiceSpeed(1.5));
  await go('sq');
  assert.equal(await page.locator('html').getAttribute('lang'),'sq');
  assert.equal(await page.evaluate(()=>answers[1]),true,'Language switch preserves answers');
  assert.equal(await page.locator('#voiceSpeedValue').textContent(),'1.50×');
  assert.equal(await page.locator('#start').textContent(),sq.ui['Resume exploring ▶']);
  assert.equal(await page.locator('.voice-speed-label').textContent(),sq.ui['Voice Speed']);
  await page.locator('#start').click();await page.evaluate(()=>{if(preparing){preparationRemaining=0;tickPreparation()}});assert.equal(await page.locator('#next').isDisabled(),true);
  assert.equal(await page.evaluate(()=>spoken.at(-1)),sq.chapters[2].cues[0].text,'Native narration gets Albanian text');
  for(let i=0;i<10;i++){
   await page.evaluate(i=>{move(i,3,false);showQuestion()},i);
   await solveCheckpoint(page,sq.chapters[i],async(index)=>{
    assert.equal(await page.locator('#question').textContent(),sq.chapters[i].questions[index].prompt);
    assert.equal(await page.locator('#feedback').textContent(),sq.ui['Exactly. ']+sq.chapters[i].questions[index].explanation);
   });
  }
  assert.equal(await page.locator('#finish').textContent(),sq.ui.Finish);
  assert.equal(await page.locator('#finish').isEnabled(),true);
  await go('en');assert.equal(await page.locator('#start').textContent(),'Review lecture ▶');
  assert.equal(await page.evaluate(()=>Object.keys(answers).length),10,'Completion survives switching back');
  assert.deepEqual(errors,[]);
  // Exercise the standalone browser's actual language argument without native TTS.
  const standalone=await browser.newPage();
  await standalone.addInitScript(()=>{
   window.speechCalls=[];window.SpeechSynthesisUtterance=function(text){this.text=text};
   Object.defineProperty(window,'speechSynthesis',{value:{cancel(){},speak(u){speechCalls.push({lang:u.lang,text:u.text,rate:u.rate})}}});
  });
  await standalone.goto(`http://127.0.0.1:${server.address().port}/geometry.html?lang=sq`);
  await standalone.locator('#start').click();
  await standalone.evaluate(()=>{preparationRemaining=0;tickPreparation()});
  assert.equal(await standalone.evaluate(()=>speechCalls.at(-1).lang),'sq-AL');
  assert.equal(await standalone.evaluate(()=>speechCalls.at(-1).text),sq.chapters[0].cues[0].text);
  console.log(`PASS: ${Object.keys(catalog).length} Albanian app strings, all 40 translated scenes/10 checkpoints, shared progress/completion, saved speed, Albanian native and browser narration.`);
 }finally{await browser.close();server.close();}
})().catch(error=>{console.error(error);server.close();process.exitCode=1;});
