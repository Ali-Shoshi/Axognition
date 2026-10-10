const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const assert = require('node:assert/strict');
const {chromium} = require('playwright');
const root = path.join(__dirname, 'src/main/resources/lessons');
const server = http.createServer((req, res) => {
  const file = path.basename(req.url.split('?')[0]);
  if (!/^[a-z.-]+\.(html|css|js|json)$/.test(file) || !fs.existsSync(path.join(root, file))) return res.writeHead(404).end();
  res.setHeader('Content-Type', {html:'text/html', css:'text/css', js:'text/javascript', json:'application/json'}[file.split('.').pop()]);
  res.end(fs.readFileSync(path.join(root, file)));
});
(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch({channel:'chrome', headless:true});
  try {
    for (const name of ['geometry', 'fractions', 'fractions-calculations']) {
      const page = await browser.newPage();
      const errors = [];
      page.on('pageerror', error => errors.push(String(error)));
      await page.addInitScript(() => {
        window.prefetched = [];
        window.GeometryVoice = {speak:()=>true, stop:()=>{}, prefetch:text=>prefetched.push(text)};
      });
      await page.goto(`http://127.0.0.1:${server.address().port}/${name}.html`);
      await page.waitForFunction(() => typeof lesson !== 'undefined' && lesson && progressReady);
      await page.evaluate(() => {started=true;setPlaying(true);renderCue();});
      assert(await page.evaluate(() => prefetched.includes(current().cues[cue+1].text)), 'Next slide was not prepared');
      for (const viewport of [{width:1316,height:730}, {width:823,height:1223}]) {
        await page.setViewportSize(viewport);
        const result = await page.evaluate(() => {
          const bounds = () => ['stage','caption'].map(id => {
            const r = document.getElementById(id).getBoundingClientRect();
            return [r.x,r.y,r.width,r.height];
          });
          const before = bounds();
          $('notice').textContent = 'Saved on this device. Waiting to sync with the server.';
          $('voiceState').textContent = 'Preparing narration…';
          const during = bounds();
          $('notice').textContent = '';
          $('voiceState').textContent = 'Speaking…';
          return {before,during,after:bounds(),hidden:['notice','voiceState'].every(id=>!$(id).getClientRects().length)};
        });
        assert(result.hidden, `${name}: status text remains visible`);
        assert.deepEqual(result.before,result.during, `${name}: status text moved the lesson`);
        assert.deepEqual(result.before,result.after, `${name}: removing status text moved the lesson`);
      }
      assert.deepEqual(errors, []);
      await page.close();
    }
    console.log('PASS: all three lectures keep stable layout across status changes in portrait and landscape');
  } finally {await browser.close();server.close();}
})().catch(error => {console.error(error);server.close();process.exitCode=1;});
