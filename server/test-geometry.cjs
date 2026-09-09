const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const root=path.join(__dirname,'src/main/resources/lessons');
const data=JSON.parse(fs.readFileSync(path.join(root,'geometry.json'),'utf8'));
assert.equal(data.chapters.length,10);assert.equal(data.chapters.flatMap(c=>c.cues).length,40);
assert.equal(data.chapters.flatMap(c=>c.cues).reduce((s,c)=>s+c.seconds,0),1600);
for(const c of data.chapters){assert(c.cues.every(s=>s.text.length>80));if(c.question.type==='choice')assert(c.question.options[c.question.answer]);}
const el=new Map(), storage={};let frame, voiced=[], clock=0;
function element(){return{hidden:true,children:[],textContent:'',value:'',blur(){this.blurred=true},focus(){},querySelector(){return null},classList:{add(){},toggle(){}},style:{setProperty(){}},setAttribute(){},append(...v){this.children.push(...v)},replaceChildren(){this.children=[]},showModal(){this.open=true},close(){this.open=false}}}
const context=vm.createContext({console,document:{documentElement:element(),getElementById(id){if(!el.has(id))el.set(id,element());return el.get(id)},createElement:element,querySelectorAll(){return[]},addEventListener(){}},window:{addEventListener(){},GeometryVoice:{stop(){},speak(t,id){voiced.push([t,id]);return true}}},GeometryVoice:{stop(){},speak(t,id){voiced.push([t,id]);return true}},localStorage:{getItem:k=>storage[k]||null,setItem:(k,v)=>storage[k]=v},fetch:async()=>({ok:true,json:async()=>data}),requestAnimationFrame:f=>frame=f,setTimeout:()=>0,clearTimeout(){},confirm:()=>true});
context.performance={now:()=>clock};
function nextScene(){clock+=7000;el.get('next').onclick()}
vm.runInContext(fs.readFileSync(path.join(root,'geometry.js'),'utf8'),context);
setImmediate(()=>{
 el.get('start').onclick();assert.equal(el.get('caption').textContent,data.chapters[0].cues[0].text);
 // Speech completion from a cancelled utterance cannot release the next cue.
 const firstId=voiced.at(-1)[1];
 el.get('next').onclick();assert.equal(vm.runInContext('cue',context),0);
 clock=6999;el.get('next').onclick();assert.equal(vm.runInContext('cue',context),0);
 clock=7000;el.get('next').onclick();context.window.geometryVoiceEnd(firstId,true);
 assert.equal(vm.runInContext('speaking',context),true);
 // A cue waits for speech to finish even when its target duration elapses.
 vm.runInContext('seconds=40',context);frame(100);assert.equal(vm.runInContext('cue',context),1);
 context.window.geometryVoiceEnd(voiced.at(-1)[1],true);frame(101);assert.equal(vm.runInContext('cue',context),2);
 el.get('play').onclick();assert.equal(vm.runInContext('playing',context),false);
 nextScene();nextScene();
 assert.equal(el.get('questionPanel').hidden,false);
 el.get('answers').children[0].onclick();assert.equal(el.get('continue').hidden,true);
 el.get('answers').children[1].onclick();assert.equal(el.get('continue').hidden,false);
 el.get('continue').onclick();assert.equal(el.get('heading').textContent,'The rectangle');
 // Visit every model and solve every remaining checkpoint through the player.
 for(let ch=1;ch<10;ch++){
  for(let i=0;i<4;i++)nextScene();
  const q=data.chapters[ch].question;
  if(q.type==='choice')el.get('answers').children[q.answer].onclick();
  else {const form=el.get('answers').children[0];form.children[0].value=String(q.answer);form.onsubmit({preventDefault(){}});assert.equal(form.children[0].blurred,true);}
  assert.equal(el.get('continue').hidden,false);el.get('continue').onclick();
 }
 assert.equal(el.get('finished').hidden,false);assert(el.get('score').textContent.includes('All ten'));
 const saved=JSON.parse(storage['axognition-geometry-v1']);assert.equal(Object.keys(saved.answers).length,10);
 assert.equal(typeof saved.completedAt,'string');
 assert.equal(el.get('finish').disabled,false);
 el.get('finish').onclick();assert.equal(el.get('finish').textContent,'Finished ✓');
 assert.equal(vm.runInContext('playing',context),false);
 console.log('PASS: 10 chapters / 40 cues, all models, narration synchronization, pause, wrong answers, numeric questions, all checkpoints, completion and stored progress.');
});
