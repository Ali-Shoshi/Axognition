const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
const root=path.join(__dirname,'src/main/resources/lessons');
const server=http.createServer((req,res)=>{
 const name=path.basename(req.url.split('?')[0]);
 if(!/^(geometry|fractions|fractions-calculations)(\.sq)?\.(html|css|js|json)$/.test(name))return res.writeHead(404).end();
 const file=path.join(root,name);if(!fs.existsSync(file))return res.writeHead(404).end();
 res.setHeader('Content-Type',{html:'text/html',css:'text/css',js:'text/javascript',json:'application/json'}[name.split('.').pop()]);
 res.end(fs.readFileSync(file));
});
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const browser=await chromium.launch({channel:'chrome',headless:true});
 const output=path.join(__dirname,'build/checkpoint-pad');fs.mkdirSync(output,{recursive:true});
 let cases=0;
 try {
  for(const id of ['geometry','fractions','fractions-calculations'])for(const lang of ['en','sq'])for(const theme of ['light','dark']) {
   const page=await browser.newPage({viewport:{width:1316,height:730}}),errors=[];
   page.on('pageerror',e=>errors.push(String(e)));
   await page.addInitScript(()=>{window.GeometryVoice={speak:()=>true,stop(){},hideKeyboard(){},setCompleted:()=>true};});
   await page.goto(`http://127.0.0.1:${server.address().port}/${id}.html?lang=${lang}&theme=${theme}`);
   await page.waitForFunction(()=>typeof lesson!=='undefined'&&lesson&&!$('start').disabled);
   await page.locator('#start').click();await page.evaluate(()=>{preparationRemaining=0;tickPreparation();showQuestion(0);});
   const card=await page.locator('#questionPanel > .question-card').boundingBox();
   const pad=await page.locator('.calculation-pad').boundingBox();
   assert(Math.abs(card.width-760)<1,'Question retains its existing 760px width');
   assert(pad.x>=card.x+card.width&&pad.width>350&&pad.height>490,'Pad fills the remaining right side');
   assert.equal(await page.locator('#padPen').getAttribute('aria-pressed'),'true');
   if(lang==='sq')assert.equal(await page.locator('.pad-heading h3').textContent(),'Fletë për llogaritje');
   const ink=()=>page.evaluate(()=>{
    const c=$('calculationCanvas'),data=c.getContext('2d').getImageData(0,0,c.width,c.height).data;
    let count=0;for(let i=3;i<data.length;i+=4)if(data[i])count++;return count;
   });
   const line=async(y,pen=false,button='left')=>{
    const r=await page.locator('#calculationCanvas').boundingBox();
    if(!pen){await page.mouse.move(r.x+50,r.y+y);await page.mouse.down();await page.mouse.move(r.x+230,r.y+y,{steps:12});await page.mouse.up();}
    else {
     const cdp=await page.context().newCDPSession(page),buttons=button==='right'?2:1;
     await cdp.send('Input.dispatchMouseEvent',{type:'mousePressed',x:r.x+50,y:r.y+y,button,buttons,pointerType:'pen'});
     for(let x=65;x<=230;x+=15)await cdp.send('Input.dispatchMouseEvent',{type:'mouseMoved',x:r.x+x,y:r.y+y,button:'none',buttons,pointerType:'pen'});
     await cdp.send('Input.dispatchMouseEvent',{type:'mouseReleased',x:r.x+230,y:r.y+y,button,buttons:0,pointerType:'pen'});
     await cdp.detach();
    }
   };
   const touch=await page.context().newCDPSession(page),r=await page.locator('#calculationCanvas').boundingBox();
   await touch.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:r.x+50,y:r.y+80}]});
   await touch.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:r.x+230,y:r.y+80}]});
   await touch.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await touch.detach();
   assert((await ink())>500,'Finger input draws on the pad');await page.locator('#padClear').click();
   await line(80);const thin=await ink();assert(thin>500,'Pen actually paints canvas pixels');
   await page.locator('#padClear').click();assert.equal(await ink(),0);
   await page.locator('#padThickness').fill('12');assert.equal(await page.locator('#padThicknessValue').textContent(),'12 px');
   await line(80);assert((await ink())>thin*2,'Thickness slider changes the drawn line');
   await page.locator('#padEraser').click();await line(80);assert((await ink())<thin/5,'Eraser actually removes ink');
   await page.locator('#padPen').click();await page.locator('#padClear').click();await line(80,true);
   assert((await ink())>500);await line(80,true,'right');assert((await ink())<thin/5,'Stylus barrel button erases without switching tools');
   assert.equal(await page.locator('#padPen').getAttribute('aria-pressed'),'true');
   await page.locator('#padClear').click();await line(80,true);
   await page.evaluate(()=>window.geometryStylusEraser=true);await line(80,true);await page.evaluate(()=>window.geometryStylusEraser=false);
   assert((await ink())<thin/5,'Android stylus-button fallback erases');
   await page.locator('#padClear').click();await line(80,true);const beforeResize=await ink();
   await page.setViewportSize({width:823,height:1223});await page.waitForTimeout(80);
   const narrowCard=await page.locator('#questionPanel > .question-card').boundingBox();
   const narrowPad=await page.locator('.calculation-pad').boundingBox();
   assert(narrowCard.width>700&&narrowPad.y>=narrowCard.y+narrowCard.height,'Portrait keeps question width and places pad below');
   assert((await ink())>beforeResize/3,'Rotation preserves scratch work');
   await page.setViewportSize({width:390,height:844});await page.waitForTimeout(80);
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true);
   await page.setViewportSize({width:360,height:640});await page.waitForTimeout(80);
   const smallCard=await page.locator('#questionPanel > .question-card').boundingBox();
   const smallPad=await page.locator('.calculation-pad').boundingBox();
   assert(smallPad.y>=smallCard.y+smallCard.height,'Small phone question does not overlap the pad');
   await page.setViewportSize({width:1316,height:730});await page.waitForTimeout(80);
   const q=await page.evaluate(()=>current().questions[0]);
   await page.locator('#answers > button').nth((q.answer+1)%6).click();
   assert.equal(await page.locator('#answers > button.incorrect').count(),1);
   const color=locator=>locator.evaluate(el=>({bg:getComputedStyle(el).backgroundColor,opacity:getComputedStyle(el).opacity}));
   assert.deepEqual(await color(page.locator('#answers > button.incorrect')),{bg:'rgb(214, 0, 0)',opacity:'1'});
   assert.deepEqual(await color(page.locator('#feedback')),{bg:'rgb(214, 0, 0)',opacity:'1'});
   assert((await page.locator('#feedback').textContent()).startsWith(lang==='sq'?'Gabim. ':'Incorrect. '));
   await page.locator('#continue').click();assert.equal(await ink(),0,'Every question has separate scratch work');
   const typed=await page.evaluate(()=>current().questions[1]);
   await page.locator('#answers input').fill(String(typed.answer+1));await page.locator('#answers input').press('Enter');
   assert.equal(await page.locator('#answers input').getAttribute('aria-invalid'),'true');
   assert.deepEqual(await color(page.locator('#answers input')),{bg:'rgb(214, 0, 0)',opacity:'1'});
   assert.equal(await page.locator('#answers input').inputValue(),String(typed.answer+1),'Wrong typed answer remains visible');
   assert.equal(await page.locator('#answers input').isDisabled(),true,'Single-answer rule remains enforced');
   await line(100,true);assert((await ink())>500,'Pad remains usable after submitting an answer');
   await page.screenshot({path:path.join(output,`${id}-${lang}-${theme}-wrong-typed.png`)});
   await page.evaluate(()=>showQuestion(0));assert((await ink())>500,'Returning to a question restores its scratch work');
   await page.locator('#padClear').click();assert.equal(await ink(),0);
   await page.reload();await page.waitForFunction(()=>typeof lesson!=='undefined'&&lesson&&!$('start').disabled);
   await page.locator('#start').click();await page.evaluate(()=>{if(preparing){preparationRemaining=0;tickPreparation();}showQuestion(1);});
   assert.equal(await page.locator('#feedback').getAttribute('class'),'answer-incorrect','Wrong-result feedback survives resume');
   assert.deepEqual(errors,[]);await page.close();cases++;
  }
  console.log(`PASS: ${cases} lecture/language/theme combinations; unchanged question width, responsive pad, drawing, thickness, clear, erasers, resize/revisit, strong wrong-answer feedback and answer locks.`);
 } finally {await browser.close();await new Promise(resolve=>server.close(resolve));}
})().catch(e=>{console.error(e);process.exitCode=1;server.close();});
