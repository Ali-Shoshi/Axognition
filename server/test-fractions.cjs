const fs=require('node:fs'), vm=require('node:vm'), assert=require('node:assert/strict');
const lesson=JSON.parse(fs.readFileSync('server/src/main/resources/lessons/fractions.json','utf8'));
const html=fs.readFileSync('server/src/main/resources/lessons/fractions.html','utf8');
assert.equal(lesson.chapters.reduce((n,c)=>n+c.seconds,0),1800);
assert.equal(lesson.chapters.length,10);
for(const c of lesson.chapters){assert.equal(c.scenes.length,3);assert(c.options[c.answer]);assert(c.numerator<=c.denominator);}
const elements=new Map();
function element(){return {children:[],value:0,textContent:'',append(x){this.children.push(x)},replaceChildren(){this.children=[]},setAttribute(){}};}
let stored={}, tick;
const context=vm.createContext({document:{getElementById(id){if(!elements.has(id))elements.set(id,element());return elements.get(id)},createElement:element,addEventListener(){}},window:{},localStorage:{getItem:k=>stored[k]||null,setItem:(k,v)=>stored[k]=v},fetch:async()=>({ok:true,json:async()=>lesson}),setInterval:f=>{tick=f},confirm:()=>true,console});
vm.runInContext(html.match(/<script>([\s\S]*)<\/script>/)[1],context);
setImmediate(()=>{
 assert.equal(elements.get('chapters').children.length,10);
 assert.equal(elements.get('next').disabled,true);
 elements.get('play').onclick();
 for(let i=0;i<136;i++)tick();
 assert.equal(elements.get('quiz').hidden,false);
 assert.equal(elements.get('play').textContent,'Play');
 assert.equal(elements.get('choices').children.length,3);
 elements.get('choices').children[1].onclick();
 assert.equal(elements.get('next').disabled,true);
 elements.get('choices').children[0].onclick();
 assert.equal(elements.get('next').disabled,false);
 elements.get('next').onclick();
 assert.equal(elements.get('heading').textContent,'2. Reading fractions');
 assert.equal(JSON.parse(stored['axognition-fractions-v1']).index,1);
 elements.get('reset').onclick();
 assert.equal(elements.get('heading').textContent,'1. Fair shares');
 console.log('PASS: 1800-second content, 10 chapters, loading, pause at checkpoint, wrong/correct answers, next, persistence and reset');
});

