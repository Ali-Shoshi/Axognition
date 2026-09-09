const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
const root=path.join(__dirname,'src/main/resources/lessons');
const output=path.join(__dirname,'build/geometry-controls');
const server=http.createServer((req,res)=>{
  const file=path.basename(req.url.split('?')[0]);
  if(!/^geometry\.(html|css|js|json)$/.test(file))return res.writeHead(404).end();
  res.setHeader('Content-Type',{html:'text/html',css:'text/css',js:'text/javascript',json:'application/json'}[file.split('.').pop()]);
  res.end(fs.readFileSync(path.join(root,file)));
});
(async()=>{
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({channel:'chrome',headless:true});
 const page=await browser.newPage({viewport:{width:1316,height:730}});
 await page.addInitScript(()=>{
  window.testTime=0; Object.defineProperty(performance,'now',{value:()=>window.testTime});
  window.bridgeEvents=[];
  window.GeometryVoice={
   speak:(text,id)=>{bridgeEvents.push(['speak',text,id]);return true},stop:()=>{},
   setRate:rate=>bridgeEvents.push(['rate',rate]),hideKeyboard:()=>bridgeEvents.push(['keyboard']),
   setCompleted:complete=>{bridgeEvents.push(['complete',complete]);return true},
   finish:()=>{bridgeEvents.push(['finish']);return true}
  };
 });
 const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 const advance=async ms=>page.evaluate(ms=>{testTime+=ms;updateNext()},ms);
 fs.mkdirSync(output,{recursive:true});
 try{
  await page.goto(`http://127.0.0.1:${server.address().port}/geometry.html?theme=dark`);
  await page.waitForFunction(()=>typeof lesson!=='undefined'&&lesson);
  await page.locator('#start').click();
  assert.equal(await page.locator('#next').isDisabled(),true);
  await page.evaluate(()=>$('next').onclick());
  assert.equal(await page.evaluate(()=>cue),0);
  await advance(3500);
  assert.equal(await page.locator('#next').evaluate(e=>Number(e.style.getPropertyValue('--next-fill'))),.5);
  await page.screenshot({path:path.join(output,'dark-countdown-landscape.png')});
  await page.setViewportSize({width:823,height:1223});
  await page.evaluate(()=>window.geometrySetTheme(false));
  await page.screenshot({path:path.join(output,'light-countdown-portrait.png')});
  for(const speed of ['0.5','0.7','1','1.25','1.5','1.75','2']){
   await page.locator('#voiceSpeedButton').click();
   await page.locator(`#voiceSpeedMenu [data-speed="${speed}"]`).click();
   assert.equal(await page.evaluate(()=>bridgeEvents.filter(e=>e[0]==='rate').at(-1)[1]),Number(speed));
   assert.equal(await page.evaluate(()=>cue),0);
   assert.equal(await page.locator('#next').isDisabled(),true,'Speed does not bypass the wait');
  }
  await advance(3499);assert.equal(await page.locator('#next').isDisabled(),true);
  await advance(1);assert.equal(await page.locator('#next').isEnabled(),true);
  await page.locator('#next').click();
  assert.equal(await page.evaluate(()=>cue),1);
  assert.equal(await page.locator('#next').isDisabled(),true,'New slide starts another wait');
  await page.evaluate(()=>setPlaying(false));
  await advance(7000);assert.equal(await page.locator('#next').isEnabled(),true,'Wait also runs with narration paused');
  await page.reload();await page.waitForFunction(()=>typeof lesson!=='undefined'&&lesson);
  assert.equal(await page.locator('#voiceSpeedValue').textContent(),'2×','Speed survives reload');
  await page.locator('#start').click();
  await page.setViewportSize({width:1316,height:730});
  await page.locator('#voiceSpeedButton').click();
  const menuShape=await page.evaluate(()=>{
   const button=$('voiceSpeedButton').getBoundingClientRect(),menu=$('voiceSpeedMenu').getBoundingClientRect(),style=getComputedStyle($('voiceSpeedMenu')),stage=$('stage').getBoundingClientRect();
   const visibleAboveContent=[...$('voiceSpeedMenu').querySelectorAll('button')].every(option=>{
    const r=option.getBoundingClientRect(),top=document.elementFromPoint(r.left+r.width/2,r.top+r.height/2);
    return r.top>=stage.top&&r.bottom<=stage.bottom&&top?.closest('#voiceSpeedMenu');
   });
   return {rightDifference:Math.abs(button.right-menu.right),widthDifference:Math.abs(button.width-menu.width),radius:style.borderRadius,animation:style.animationName,transform:style.transform,colorsDiffer:getComputedStyle($('voiceSpeedButton')).backgroundColor!==style.backgroundColor,visibleAboveContent,label:document.querySelector('.voice-speed-label').textContent};
  });
  assert(menuShape.rightDifference<1,'Menu is anchored to the right edge of its button');
  assert(menuShape.widthDifference<1,'Menu is the same width as its button');
  assert.equal(menuShape.label,'Voice Speed');
  assert.equal(menuShape.colorsDiffer,true,'Menu has a different surface color from its button');
  assert.equal(menuShape.visibleAboveContent,true,'Every speed stays visible above lesson content');
  assert.equal(menuShape.radius,'0px','Menu has square corners');
  assert.equal(menuShape.animation,'none','Menu has no slide-in animation');
  assert.equal(menuShape.transform,'none','Menu does not travel in from the side');
  await page.screenshot({path:path.join(output,'custom-speed-menu-portrait.png')});
  await page.keyboard.press('Escape');
  assert.equal(await page.locator('#voiceSpeedMenu').isHidden(),true,'Escape closes speed menu');
  assert.equal(await page.locator('#next').isDisabled(),true,'Reload starts a new wait');
  await page.evaluate(()=>{move(3,3,false);showQuestion()});
  await page.locator('#answers input').fill('30');
  await page.locator('#answers input').press('Enter');
  assert.equal(await page.locator('#answers input').evaluate(e=>document.activeElement===e),false);
  assert.equal(await page.evaluate(()=>bridgeEvents.some(e=>e[0]==='keyboard')),true);
  assert.equal(await page.locator('#continue').isVisible(),true);
  await page.evaluate(()=>{move(9,3,false);showQuestion()});
  await page.locator('#answers button').nth(await page.evaluate(()=>current().question.answer)).click();
  await page.locator('#continue').click();
  assert.equal(await page.locator('#finish').isDisabled(),true,'Skipping checkpoints cannot mark completion');
  await page.evaluate(()=>{started=true;$('welcome').hidden=true});
  // Complete all checkpoints through their answer controls.
  for(let ch=0;ch<10;ch++){
   await page.evaluate(ch=>{move(ch,3,false);showQuestion()},ch);
   const q=await page.evaluate(()=>current().question);
   if(q.type==='choice')await page.locator('#answers button').nth(q.answer).click();
   else{await page.locator('#answers input').fill(String(q.answer));await page.locator('#answers input').press('Enter')}
   await page.locator('#continue').click();
  }
  assert.equal(await page.locator('#finish').isEnabled(),true);
  assert.equal(await page.locator('#progress').evaluate(e=>e.value===e.max),true,'Completed lecture shows full progress');
  assert.equal(await page.evaluate(()=>Boolean(JSON.parse(localStorage.getItem(STORE)).completedAt)),true);
  await page.screenshot({path:path.join(output,'completed-portrait.png')});
  await page.locator('#finish').click();
  assert.equal(await page.evaluate(()=>bridgeEvents.at(-1)[0]),'finish');
  assert.equal(await page.evaluate(()=>playing),false);
  await page.reload();await page.waitForFunction(()=>typeof lesson!=='undefined'&&lesson);
  assert.equal(await page.locator('#start').textContent(),'Review lecture ▶');
  await page.locator('#contentsButton').click();
  page.once('dialog',dialog=>dialog.accept());
  await page.locator('#restart').click();
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem(STORE)).completedAt),null);
  assert.equal(await page.evaluate(()=>bridgeEvents.filter(e=>e[0]==='complete').at(-1)[1]),false);
  assert.deepEqual(errors,[]);
  console.log('PASS: timed Next gating/fill/reset, pause/rotation/theme/rate invariants, seven native rates, reload persistence, Enter dismissal, incomplete Finish gating, completion, Finish exit and restart.');
 }finally{await browser.close();server.close()}
})().catch(e=>{console.error(e);server.close();process.exitCode=1});
