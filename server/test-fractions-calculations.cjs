const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
const {solveCheckpoint}=require('./checkpoint-test-helpers.cjs');
const root=path.join(__dirname,'src/main/resources/lessons'),id='fractions-calculations';
const english=JSON.parse(fs.readFileSync(path.join(root,id+'.json'),'utf8'));
const albanian=JSON.parse(fs.readFileSync(path.join(root,id+'.sq.json'),'utf8'));
// Verify actual calculations using exact fractions, independently of the authored answers.
const gcd=(a,b)=>b===0n?(a<0n?-a:a):gcd(b,a%b);
function fraction(n,d=1n){n=BigInt(n);d=BigInt(d);assert.notEqual(d,0n);const f=gcd(n,d);return [n/f*(d<0n?-1n:1n),d/f*(d<0n?-1n:1n)];}
function calculate(expression){
 const normalized=expression.replaceAll('−','-').replaceAll('×','*').replaceAll('÷','/').replace(/\s/g,'');
 const tokens=normalized.match(/\d+\/\d+|\d+|[()+\-*/]/g)||[];
 assert.equal(tokens.join(''),normalized);let index=0;
 function atom(){if(tokens[index]==='('){index++;const value=sum();assert.equal(tokens[index++],')');return value;}const token=tokens[index++];assert(token);return fraction(...token.split('/'));}
 function product(){let a=atom();while(['*','/'].includes(tokens[index])){const op=tokens[index++],b=atom();a=op==='*'?fraction(a[0]*b[0],a[1]*b[1]):fraction(a[0]*b[1],a[1]*b[0]);}return a;}
 function sum(){let a=product();while(['+','-'].includes(tokens[index])){const op=tokens[index++],b=product();a=fraction(a[0]*b[1]+(op==='+'?1n:-1n)*b[0]*a[1],a[1]*b[1]);}return a;}
 const answer=sum();assert.equal(index,tokens.length);return answer;
}
assert.equal(english.id,id);assert.equal(english.chapters.length,10);
assert.equal(english.chapters.flatMap(c=>c.cues).length,40);
for(const [i,c] of english.chapters.entries()){
 const sq=albanian.chapters[i];assert.notEqual(c.title,sq.title);
 assert.equal(c.cues.length,4);assert.equal(c.questions.length,2);
 assert.equal(c.questions[0].type,'choice');assert.equal(c.questions[0].options.length,6);
 assert.equal(new Set(c.questions[0].options).size,6);assert.equal(c.questions[1].type,'number');
 c.cues.forEach((cue,j)=>{
  assert(cue.text.length>150);assert(sq.cues[j].text.length>150);assert.notEqual(cue.text,sq.cues[j].text);
  assert.deepEqual(cue.model,sq.cues[j].model);assert.equal(cue.seconds,40);
  for(const step of cue.model.steps){const values=step.expression.split('=').map(calculate);for(const value of values)assert.deepEqual(value,values[0],step.expression);assert(albanian.ui[step.note],step.note);}
 });
 c.questions.forEach((q,j)=>{
  const value=calculate(q.calculation.expression);
  if(q.type==='choice')assert.deepEqual(calculate(q.options[q.answer]),value,q.prompt);
  else assert.equal(BigInt(q.answer),value[q.calculation.component==='numerator'?0:1],q.prompt);
  assert.equal(q.answer,sq.questions[j].answer);assert.notEqual(q.prompt,sq.questions[j].prompt);
 });
}
console.log('PASS: exact arithmetic for all worked equalities and 20 checkpoint answers; 40 bilingual scenes.');
const server=http.createServer((req,res)=>{
 const name=path.basename(req.url.split('?')[0]);
 if(!/^(geometry|fractions|fractions-calculations)(\.sq)?\.(html|css|js|json)$/.test(name))return res.writeHead(404).end();
 const file=path.join(root,name);if(!fs.existsSync(file))return res.writeHead(404).end();
 res.setHeader('Content-Type',{html:'text/html',css:'text/css',js:'text/javascript',json:'application/json'}[name.split('.').pop()]);res.end(fs.readFileSync(file));
});
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const browser=await chromium.launch({channel:'chrome',headless:true});
 const page=await browser.newPage({viewport:{width:1316,height:730}}),errors=[];
 page.on('pageerror',error=>errors.push(String(error)));
 await page.addInitScript(()=>{
  window.testTime=0;Object.defineProperty(performance,'now',{value:()=>testTime});
  window.GeometryVoice={progressKey:()=> 'axognition-fractions-calculations-v1:child-test',speak:()=>true,stop(){},setCompleted:()=>true};
 });
 const url=`http://127.0.0.1:${server.address().port}/${id}.html`;
 const load=async(lang='en',theme='light')=>{await page.goto(url+`?lang=${lang}&theme=${theme}`);await page.waitForFunction(()=>typeof lesson!=='undefined'&&lesson&&!$('start').disabled);};
 const output=path.join(__dirname,'build/fractions-calculations-preview');fs.mkdirSync(output,{recursive:true});
 try{
  await load();await page.evaluate(()=>{localStorage.setItem('axognition-fractions-v1:child-test','basic progress');localStorage.setItem('axognition-geometry-v1:child-test','geometry progress');});
  await page.locator('#start').click();assert.equal(await page.locator('#prepare').isVisible(),true);
  await page.evaluate(()=>{preparationRemaining=0;tickPreparation()});
  assert.equal(await page.locator('#caption').textContent(),english.chapters[0].cues[0].text);
  assert.equal(await page.locator('.calc-step').first().evaluate(element=>getComputedStyle(element).animationName),'calc-reveal');
  assert.equal(await page.locator('#next').isDisabled(),true);
  await page.evaluate(()=>{testTime=10000;updateNext()});await page.locator('#next').click();
  assert.equal(await page.evaluate(()=>cue),1);
  await page.locator('#previous').click();assert.equal(await page.locator('#next').isDisabled(),false);
  await page.evaluate(()=>{move(3,2,false);save()});await load('sq','dark');
  assert.equal(await page.evaluate(()=>chapter),3);assert.equal(await page.evaluate(()=>cue),2);
  await page.locator('#start').click();await page.evaluate(()=>{preparationRemaining=0;tickPreparation()});
  assert.equal(await page.locator('#caption').textContent(),albanian.chapters[3].cues[2].text);
  let layouts=0;
  for(const language of ['en','sq'])for(const theme of ['light','dark']){
   await load(language,theme);await page.locator('#start').click();await page.evaluate(()=>{preparationRemaining=0;tickPreparation()});
   for(const viewport of [{width:1316,height:730},{width:823,height:1223},{width:390,height:844}]){
    await page.setViewportSize(viewport);
    for(let ch=0;ch<10;ch++)for(let sc=0;sc<4;sc++){
     await page.evaluate(({ch,sc})=>move(ch,sc,false),{ch,sc});
     const problems=await page.evaluate(()=>{
      const issues=[],svg=$('scene'),s=svg.getBoundingClientRect();
      for(const element of svg.querySelectorAll('text,rect,path')){
       const r=element.getBoundingClientRect();
       if(r.left<s.left-1||r.right>s.right+1||r.top<s.top-1||r.bottom>s.bottom+1)issues.push('Clipped: '+(element.textContent||element.getAttribute('class')));
      }
      if(document.documentElement.scrollWidth>innerWidth+1)issues.push('Horizontal page overflow');
      const steps=[...svg.querySelectorAll('.calc-step')];
      for(let i=1;i<steps.length;i++){
       const previous=steps[i-1].querySelector('.calc-note').getBoundingClientRect();
       const current=steps[i].querySelector('.calc-number').getBoundingClientRect();
       if(current.top<previous.bottom-1)issues.push('Calculation steps overlap');
      }
      if(innerWidth>=600&&innerHeight>=600&&$('stage').scrollHeight>$('stage').clientHeight+1)issues.push('Tablet explanation needs scrolling');
      if(!$('scene').getAttribute('aria-label').includes($('caption').textContent))issues.push('Missing diagram description');
      return issues;
     });
     assert.deepEqual(problems,[],`${language}/${theme}/${viewport.width} chapter ${ch} scene ${sc}`);layouts++;
     if(viewport.width===1316&&sc===3&&[0,5,9].includes(ch))await page.screenshot({path:path.join(output,`${language}-${theme}-chapter-${ch+1}.png`)});
    }
   }
  }
  await page.setViewportSize({width:1316,height:730});
  for(let ch=0;ch<10;ch++){
   await page.evaluate(ch=>{move(ch,3,false);showQuestion()},ch);
   await solveCheckpoint(page,albanian.chapters[ch]);
  }
  assert.equal(await page.locator('#finished').isVisible(),true);
  assert.equal(await page.evaluate(()=>currentResult().correct),20);
  assert.equal(await page.evaluate(()=>currentResult().percent),100);
  await load();await page.locator('#start').click();
  assert.equal(await page.locator('#prepare').isVisible(),false);assert.equal(await page.evaluate(()=>chapter),0);assert.equal(await page.evaluate(()=>cue),0);
  assert.equal(await page.locator('#next').isDisabled(),false);
  await page.evaluate(()=>showQuestion(0));assert.equal(await page.locator('#answers button.correct').count(),1);
  assert.equal(await page.evaluate(()=>localStorage.getItem('axognition-fractions-v1:child-test')),'basic progress');
  assert.equal(await page.evaluate(()=>localStorage.getItem('axognition-geometry-v1:child-test')),'geometry progress');
  assert.deepEqual(errors,[]);
  console.log(`PASS: ${layouts} diagram/layout checks; start/resume, slide wait/revisit, all checkpoints, completion/review and separate progress.`);
 }finally{await browser.close();await new Promise(resolve=>server.close(resolve));}
})().catch(error=>{console.error(error);process.exitCode=1;server.close();});
