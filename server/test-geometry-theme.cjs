// Run with Playwright on NODE_PATH and Chrome installed.
const fs = require('node:fs'), path = require('node:path'), http = require('node:http');
const assert = require('node:assert/strict');
const {chromium} = require('playwright');
const root = path.join(__dirname,'src/main/resources/lessons');
const language = process.env.GEOMETRY_TEST_LANGUAGE === 'sq' ? 'sq' : 'en';
const output = path.join(__dirname,'build/geometry-theme' + (language === 'sq' ? '-sq' : ''));
const data = JSON.parse(fs.readFileSync(path.join(root,'geometry.json'),'utf8'));
const server = http.createServer((req,res) => {
  const file = path.basename(req.url.split('?')[0]);
  if (!/^geometry(?:\.sq)?\.(html|css|js|json)$/.test(file)) return res.writeHead(404).end();
  res.setHeader('Content-Type', {html:'text/html',css:'text/css',js:'text/javascript',json:'application/json'}[file.split('.').pop()]);
  res.end(fs.readFileSync(path.join(root,file)));
});
(async () => {
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const browser = await chromium.launch({channel:'chrome',headless:true});
  const page = await browser.newPage();
  await page.addInitScript(()=>{window.GeometryVoice={speak:()=>true,stop:()=>{}}});
  const failures = [], errors = [];
  page.on('pageerror',e=>errors.push(String(e)));
  fs.mkdirSync(output,{recursive:true});
  let checks=0;
  async function check(label) {
    checks++;
    const issues = await page.evaluate(() => {
      const issues=[];
      const rgb = value => (value.match(/[\d.]+/g)||[]).slice(0,3).map(Number);
      const luminance = color => rgb(color).map(v=>{v/=255;return v<=.04045?v/12.92:((v+.055)/1.055)**2.4}).reduce((sum,v,i)=>sum+v*[.2126,.7152,.0722][i],0);
      const ratio = (a,b) => {a=luminance(a);b=luminance(b);return (Math.max(a,b)+.05)/(Math.min(a,b)+.05)};
      const css=getComputedStyle(document.documentElement);
      const variable=name=>css.getPropertyValue(name).trim();
      // Resolve hex tokens through CSS so the same luminance calculation works
      // for chapter colours, CSS variables and computed element colours.
      const probe=document.createElement('span');document.body.append(probe);
      const resolve=color=>{probe.style.color=color;return getComputedStyle(probe).color};
      for (const [fg,bg,min] of [
        ['--ink','--paper',4.5],['--muted','--paper',4.5],['--ink','--surface',4.5],
        ['--muted','--surface',4.5],['--accent-text','--surface',4.5],
        ['--on-control','--control',4.5],['--on-control','--control-hover',4.5],
        ['--correct-text','--correct-bg',4.5],['--correct-text','--surface',4.5],
        ['--warning','--paper',4.5],['--accent','--stage-base',3],
        ['--ink','--label-halo',4.5],['--muted','--label-halo',4.5],
        ['--ink','--line',4.5],['--ink','--hover',4.5],['--muted','--hover',4.5]
      ]) {
        const value=ratio(resolve(variable(fg)),resolve(variable(bg)));
        if(value<min)issues.push(`${fg} on ${bg}: ${value.toFixed(2)} < ${min}`);
      }
      for (const selector of ['#contentsButton','#next','#play','.primary','#answers button','#answers input','dialog','#voiceSpeedButton','.voice-speed-menu button']) {
        for(const e of document.querySelectorAll(selector)) {
          if(!e.getClientRects().length||e.disabled)continue;
          const s=getComputedStyle(e),r=e.getBoundingClientRect();
          if (s.backgroundColor!=='rgba(0, 0, 0, 0)' && ratio(s.color,s.backgroundColor)<4.5) issues.push(selector+' text contrast');
          if(selector==='#next'||selector==='#contentsButton') {
            if(r.height<48 || r.width<44)issues.push(selector+' touch target');
            if(ratio(s.backgroundColor,resolve(variable('--paper')))<3)issues.push(selector+' does not stand out');
          }
        }
      }
      const next = document.getElementById('next');
      if (next.classList.contains('next-waiting')) {
        const s = getComputedStyle(next), fill = getComputedStyle(next, '::before');
        if (ratio(s.color,s.backgroundColor)<4.5 || ratio(s.color,fill.backgroundColor)<4.5) issues.push('countdown text contrast over fill');
      }
      probe.remove();
      if(document.documentElement.scrollWidth>innerWidth+1||document.documentElement.scrollHeight>innerHeight+1)issues.push('page overflow');
      return issues;
    });
    if(issues.length)failures.push({label,issues});
  }
  try {
    for(const theme of ['light','dark']) {
      // The app preference must win even if Android/browser uses the opposite.
      await page.emulateMedia({colorScheme:theme==='dark'?'light':'dark'});
      for(const [width,height] of [[360,640],[823,1223],[1316,730],[820,360]]) {
        await page.setViewportSize({width,height});
        await page.goto(`http://127.0.0.1:${server.address().port}/geometry.html?theme=${theme}&lang=${language}`);
        await page.waitForFunction(()=>typeof lesson!=='undefined'&&lesson);
        assert.equal(await page.locator('html').getAttribute('data-theme'),theme);
        await check(`${theme} ${width} welcome`);
        await page.locator('#start').click();
        await page.locator('#next').focus();
        const interactionStyle = await page.locator('#next').evaluate(button => {
          const style = getComputedStyle(button);
          return {
            outline: style.outlineStyle,
            tapHighlight: style.webkitTapHighlightColor,
            shadow: style.boxShadow,
            radius: style.borderRadius
          };
        });
        assert.equal(interactionStyle.outline, 'none', `${theme} focus uses no square outline`);
        assert.equal(interactionStyle.tapHighlight, 'rgba(0, 0, 0, 0)', `${theme} tap highlight is transparent`);
        assert.notEqual(interactionStyle.shadow, 'none', `${theme} focus ring follows rounded button`);
        assert.notEqual(interactionStyle.radius, '0px', `${theme} button remains rounded`);
        for(let ch=0;ch<data.chapters.length;ch++) {
          for(let sc=0;sc<4;sc++) {
            await page.evaluate(([ch,sc])=>move(ch,sc,false),[ch,sc]);
            await check(`${theme} ${width} chapter ${ch+1} scene ${sc+1}`);
          }
          await page.evaluate(()=>showQuestion());
          await check(`${theme} ${width} question ${ch+1}`);
          const q=data.chapters[ch].question;
          if(q.type==='choice')await page.locator('#answers button').nth(q.answer).click();
          else {await page.locator('#answers input').fill(String(q.answer));await page.locator('#answers button').click()}
          await check(`${theme} ${width} feedback ${ch+1}`);
        }
        await page.locator('#contentsButton').click();
        await check(`${theme} ${width} chapter picker`);
        await page.locator('#closeContents').click();
        await page.evaluate(()=>move(2,3,false));
        await page.screenshot({path:path.join(output,`${theme}-${width}x${height}.png`)});
      }
    }
    await page.evaluate(()=>{move(3,3,false);showQuestion()});
    await page.locator('#answers input').fill('30');
    await page.evaluate(()=>window.geometrySetTheme(false));
    assert.equal(await page.locator('#answers input').inputValue(),'30','Theme changes retain typed answers');
    assert.equal(await page.evaluate(()=>chapter),3);
    assert.equal(await page.locator('html').getAttribute('data-theme'),'light');
    await page.emulateMedia({colorScheme:'dark'});
    assert.equal(await page.locator('html').getAttribute('data-theme'),'light','System changes do not override app theme');
    await page.goto(`http://127.0.0.1:${server.address().port}/geometry.html`);
    assert.equal(await page.locator('html').getAttribute('data-theme'),'dark','Standalone browser follows system');
    await page.emulateMedia({colorScheme:'light'});
    await page.waitForFunction(()=>document.documentElement.getAttribute('data-theme')==='light');
    assert.deepEqual(errors,[]);
    fs.writeFileSync(path.join(output,'results.json'),JSON.stringify({checks,failures},null,2));
    assert.equal(failures.length,0,JSON.stringify(failures.slice(0,8),null,2)+`\n${failures.length} failures`);
    console.log(`PASS: ${checks} theme/contrast checks, 40 scenes, all checkpoints, 4 sizes, app theme precedence and live theme changes.`);
  } finally {await browser.close();server.close()}
})().catch(e=>{console.error(e);server.close();process.exitCode=1});
