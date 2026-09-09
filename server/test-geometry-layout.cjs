// Run with Playwright installed (or exposed through NODE_PATH).
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const root = path.join(__dirname, 'src/main/resources/lessons');
const language = process.env.GEOMETRY_TEST_LANGUAGE === 'sq' ? 'sq' : 'en';
const data = JSON.parse(fs.readFileSync(path.join(root, language === 'sq' ? 'geometry.sq.json' : 'geometry.json'), 'utf8'));
const output = path.join(__dirname, 'build/geometry-layout' + (language === 'sq' ? '-sq' : ''));
const server = http.createServer((req, res) => {
  const name = path.basename(req.url.split('?')[0]);
  if (!/^geometry(?:\.sq)?\.(html|css|js|json)$/.test(name)) { res.writeHead(404).end(); return; }
  res.setHeader('Content-Type', {html:'text/html', css:'text/css', js:'text/javascript', json:'application/json'}[name.split('.').pop()]);
  res.end(fs.readFileSync(path.join(root, name)));
});
(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch({headless:true, channel:'chrome'});
  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));
  await page.addInitScript(() => {
    window.GeometryVoice = {speak:()=>true, stop:()=>{}};
  });
  fs.mkdirSync(output, {recursive:true});
  const failures = [];
  let checks = 0;
  async function audit(label, diagram = false, allowScroll = false) {
    const issues = await page.evaluate(({diagram,allowScroll}) => {
      const issues = [];
      const rect = el => el.getBoundingClientRect();
      const inside = (a,b) => a.left >= b.left-1 && a.top >= b.top-1 && a.right <= b.right+1 && a.bottom <= b.bottom+1;
      const viewport = {left:0, top:0, right:innerWidth, bottom:innerHeight};
      if (document.documentElement.scrollWidth > innerWidth+1) issues.push('document overflows horizontally');
      if (document.documentElement.scrollHeight > innerHeight+1) issues.push('document overflows vertically');
      for (const selector of ['header', 'footer', '.stage']) {
        if (!inside(rect(document.querySelector(selector)),viewport)) issues.push(selector+' outside viewport');
      }
      const main = rect(document.querySelector('main'));
      if (Math.abs(main.height-innerHeight)>1) issues.push('lesson does not fill viewport height');
      for (const el of document.querySelectorAll('header, footer, .bubble, .intro, .question-card, #answers form')) {
        if (el.getClientRects().length && el.scrollWidth > el.clientWidth+1) issues.push(el.className || el.id || el.tagName+' horizontal overflow');
      }
      if (diagram) {
        const stage = document.getElementById('stage');
        const svg = document.getElementById('scene');
        if (!svg.firstElementChild || Number(getComputedStyle(svg.firstElementChild).opacity)<.99) issues.push('paused diagram invisible');
        const scene = rect(svg), caption = rect(document.querySelector('.narrator')), heading = rect(document.querySelector('.stage-heading'));
        const speed = rect(document.querySelector('.voice-speed'));
        const mode = rect(document.getElementById('mode'));
        if (!inside(speed, heading)) issues.push('voice speed clipped by heading');
        if (speed.left < mode.right-1 && speed.right > mode.left+1 && speed.top < mode.bottom-1 && speed.bottom > mode.top+1) issues.push('voice speed overlaps mode label');
        if (scene.left < caption.right-1 && scene.right > caption.left+1 && scene.top < caption.bottom-1 && scene.bottom > caption.top+1) issues.push('diagram overlaps caption');
        if (scene.top < heading.bottom-1) issues.push('diagram overlaps heading');
        // Check actual rendered labels and animated pieces, including animation extremes.
        for (const el of svg.querySelectorAll('text, .cut-piece, .split-piece, .twin, .runner, .orbit')) {
          if (!inside(rect(el), scene)) issues.push('SVG clipped: '+(el.textContent || el.getAttribute('class')));
        }
        if (!inside(rect(document.getElementById('caption')),caption)) issues.push('caption clipped');
        // On tablets/desktop every part must be visible at once. Very short
        // windows retain an accessible scrolling stage instead of hiding text.
        if (!allowScroll && innerWidth>=600 && innerHeight>=600) {
          if (stage.scrollHeight>stage.clientHeight+1) issues.push('stage needs scrolling on a full-size screen');
          if (!inside(scene,rect(stage)) || !inside(caption,rect(stage))) issues.push('lesson content outside stage');
        }
      }
      return issues;
    }, {diagram,allowScroll});
    checks++;
    if (issues.length) failures.push({label, issues});
  }
  try {
    const sizes = [[360,640],[412,820],[600,900],[800,1100],[823,1223],[1316,730],[1100,700],[1280,720],[1440,900],[1920,1080],[820,360],[640,300]];
    for (const [width,height] of sizes) {
      await page.setViewportSize({width,height});
      await page.goto(`http://127.0.0.1:${server.address().port}/geometry.html?lang=${language}`);
      await page.waitForFunction(() => typeof lesson !== 'undefined' && lesson);
      await audit(`${width}x${height} welcome`);
      await page.locator('#start').click();
      await page.evaluate(() => setPlaying(false));
      for(let ch=0;ch<data.chapters.length;ch++) {
        for(let cue=0;cue<4;cue++) {
          await page.evaluate(([ch,cue]) => move(ch,cue,false), [ch,cue]);
          // Pause at both extremes rather than letting time-dependent clipping escape checks.
          for (const time of [0, 5200]) {
            await page.evaluate(time => document.getAnimations().forEach(a => {a.pause(); a.currentTime=time;}),time);
            await audit(`${width}x${height} chapter ${ch+1} cue ${cue+1} at ${time}`,true);
          }
        }
        await page.evaluate(() => showQuestion());
        await audit(`${width}x${height} question ${ch+1}`);
        const q = data.chapters[ch].question;
        if(q.type === 'choice') await page.locator('#answers button').nth(q.answer).click();
        else {
          await page.locator('#answers input').fill(String(q.answer));
          await page.locator('#answers button').click();
        }
        await audit(`${width}x${height} feedback ${ch+1}`);
        await page.locator('#continue').click();
      }
      await audit(`${width}x${height} complete`);
      await page.locator('#review').click();
      await audit(`${width}x${height} chapters`);
      await page.locator('#closeContents').click();
      await page.evaluate(() => move(2,3,false));
      for (let side=2;side<=8;side++) {
        await page.locator('#side').fill(String(side));
        await page.locator('#side').dispatchEvent('input');
        await page.evaluate(() => document.getAnimations().forEach(a=>{a.pause();a.currentTime=5200;}));
        await audit(`${width}x${height} square side ${side}`,true);
      }
      await page.evaluate(() => move(5,2,false));
      await page.evaluate(() => document.getAnimations().forEach(a=>{a.pause();a.currentTime=5200;}));
      await page.screenshot({path:path.join(output,`${width}x${height}.png`)});
    }
    await page.setViewportSize({width:823,height:1223});
    await page.evaluate(() => { move(3,3,false); showQuestion(); });
    await page.locator('#answers input').fill('30');
    await page.setViewportSize({width:1316,height:730});
    assert.equal(await page.locator('#answers input').inputValue(),'30','Rotation preserves a typed answer');
    assert.equal(await page.evaluate(()=>chapter),3,'Rotation preserves chapter');
    await audit('rotated question');
    await page.locator('#answers button').click();
    await audit('rotated feedback');
    await page.evaluate(() => move(6,2,false));
    await page.addStyleTag({content:'body {font-size:24px} .bubble p {font-size:36px}'});
    await audit('enlarged text',true,true);
    await page.locator('#caption').scrollIntoViewIfNeeded();
    assert.equal(await page.evaluate(()=>{
      const caption=document.getElementById('caption').getBoundingClientRect();
      const stage=document.getElementById('stage').getBoundingClientRect();
      return caption.bottom<=stage.bottom+1;
    }),true,'Enlarged captions remain reachable by scrolling');
    assert.deepEqual(errors, [], 'Browser errors');
    fs.writeFileSync(path.join(output,'results.json'), JSON.stringify({checks,failures},null,2));
    assert.equal(failures.length,0,JSON.stringify(failures.slice(0,15),null,2)+`\n${failures.length} failed checks`);
    console.log(`PASS: ${checks} layout checks across ${sizes.length} portrait/landscape viewports, 40 scenes, animation extremes, all questions, feedback, completion and chapters.`);
  } finally { await browser.close(); server.close(); }
})().catch(e=>{console.error(e);server.close();process.exitCode=1;});
