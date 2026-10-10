const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
const root=path.join(__dirname,'src/main/resources/lessons');
const output=path.join(__dirname,'build/lecture-highlights');
const server=http.createServer((req,res)=>{
  const file=path.basename(req.url.split('?')[0]);
  if(!/^[a-z.-]+\.(html|css|js|json)$/.test(file)||!fs.existsSync(path.join(root,file)))return res.writeHead(404).end();
  res.setHeader('Content-Type',{html:'text/html',css:'text/css',js:'text/javascript',json:'application/json'}[file.split('.').pop()]);
  res.end(fs.readFileSync(path.join(root,file)));
});
(async()=>{
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const browser=await chromium.launch({channel:'chrome',headless:true});
  fs.mkdirSync(output,{recursive:true});
  try {
    for(const name of ['geometry','fractions','fractions-calculations'])for(const language of ['en','sq']) {
      const page=await browser.newPage();
      const errors=[];page.on('pageerror',error=>errors.push(String(error)));
      await page.addInitScript(()=>{window.GeometryVoice={speak:()=>true,stop:()=>{},prefetch:()=>{},setRate:()=>{}}});
      await page.goto(`http://127.0.0.1:${server.address().port}/${name}.html?lang=${language}`);
      await page.waitForFunction(()=>typeof lesson!=='undefined'&&lesson&&progressReady);
      await page.evaluate(()=>{started=true;$('welcome').hidden=true;setPlaying(true);renderCue()});
      for(const dark of [false,true])for(const portrait of [false,true]) {
        await page.setViewportSize(portrait?{width:823,height:1223}:{width:1316,height:730});
        const result=await page.evaluate(({dark,portrait})=>{
          geometrySetTheme(dark);geometrySetPortrait(portrait);
          const rects=()=>[...$('caption').querySelectorAll('.caption-word')].map(element=>{
            const r=element.getBoundingClientRect();return [r.x,r.y,r.width,r.height];
          });
          const before=rects(),text=$('caption').textContent;
          const target=captionWords[Math.min(5,captionWords.length-1)];
          geometryVoiceRange(token,target.start,target.end);
          return {before,after:rects(),text,expected:current().cues[cue].text,
            active:[...$('caption').querySelectorAll('.is-current')].map(element=>element.textContent),
            target:target.element.textContent,background:getComputedStyle(target.element).backgroundColor,
            color:getComputedStyle(target.element).color};
        },{dark,portrait});
        assert.equal(result.text,result.expected,'Word spans changed the caption text');
        assert.deepEqual(result.active,[result.target],'The reported word is not highlighted');
        assert.deepEqual(result.before,result.after,'Word highlighting moved the caption');
        assert.notEqual(result.background,'rgba(0, 0, 0, 0)');
        assert.notEqual(result.background,result.color);
        if(name==='geometry'&&language==='en')await page.screenshot({path:path.join(output,`${dark?'dark':'light'}-${portrait?'portrait':'landscape'}.png`)});
      }
      const lifecycle=await page.evaluate(()=>{
        const active=()=>$('caption').querySelectorAll('.is-current').length;
        const highlight=()=>geometryVoiceRange(token,captionWords[0].start,captionWords[0].end);
        const stale=token;
        setPlaying(false);const paused=active();
        geometryVoiceRange(stale,0,4);const staleAfterPause=active();
        setPlaying(true);$('replay').onclick();highlight();const replay=active();
        geometryVoiceEnd(token,true);const ended=active();
        geometryVoiceRange(token,0,4);const lateAfterEnd=active();
        $('replay').onclick();highlight();$('mute').onclick();const muted=active();
        $('mute').onclick();highlight();selectVoiceSpeed(2);const speedChanged=active();
        highlight();const old=token;move(0,1,true);const moved=active();
        geometryVoiceRange(old,0,4);const staleAfterMove=active();
        highlight();const newSlide=active();
        return {paused,staleAfterPause,replay,ended,lateAfterEnd,muted,speedChanged,moved,staleAfterMove,newSlide};
      });
      assert.deepEqual(lifecycle,{paused:0,staleAfterPause:0,replay:1,ended:0,lateAfterEnd:0,muted:0,speedChanged:0,moved:0,staleAfterMove:0,newSlide:1});
      assert.deepEqual(errors,[]);
      await page.close();
    }
    console.log('PASS: caption highlighting, stable layout, pause, replay, mute, speed changes and stale callbacks in all lectures and both languages');
  } finally {await browser.close();server.close();}
})().catch(error=>{console.error(error);server.close();process.exitCode=1;});
