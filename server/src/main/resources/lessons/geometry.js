'use strict';
const $=id=>document.getElementById(id);
// Shared playback behavior; subject-specific models are supplied by each lesson.
const presentation=window.lessonPresentation || {};
const LEGACY_STORE=presentation.storageKey || 'axognition-geometry-v1';
const STORE=window.GeometryVoice?.progressKey?.() || LEGACY_STORE;
let lesson;
const lessonLanguage = window.geometryInitialLanguage === 'sq' ? 'sq' : 'en';
const g = text => lesson?.ui?.[text] ?? text;
function localizeLessonInterface() {
  if (!lesson.ui || !document.createTreeWalker) return;
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  let node;
  while ((node = walker.nextNode())) {
    if (node.parentElement.closest('script, style')) continue;
    const text = node.textContent.trim();
    if (lesson.ui[text]) node.textContent = node.textContent.replace(text, lesson.ui[text]);
  }
  document.querySelectorAll('[aria-label], [title]').forEach(element => {
    for (const attribute of ['aria-label', 'title']) {
      const text = element.getAttribute(attribute);
      if (text && lesson.ui[text]) element.setAttribute(attribute, lesson.ui[text]);
    }
  });
  document.title = g(presentation.title || 'Around & inside · Axognition');
}
let chapter=0, cue=0, seconds=0, playing=false, started=false;
let muted=false, speaking=false, token=0, voiceTimer, lastFrame=0, savedAt=0;
let answers={};
let questionAnswers={}, questionIndex=0, inCheckpoint=false;
let completedSlides={}, generation=0, importedToServer=false;
let sessionId=null, eventSequence=0, exitSent=false, visitId=null;
let visitStarted=0, activeStarted=null, activeElapsed=0, lastAttemptAt=0;
let activityOutbox=[];
try { const pending=JSON.parse(localStorage.getItem(STORE+':outbox')||'[]');if(Array.isArray(pending))activityOutbox=pending; } catch (_) {}
const progressRequests=new Map();
function activityId() {
  if(window.crypto?.randomUUID)return window.crypto.randomUUID();
  const bytes=new Uint8Array(16);
  if(window.crypto?.getRandomValues)window.crypto.getRandomValues(bytes);
  else for(let i=0;i<16;i++)bytes[i]=Math.floor(Math.random()*256);
  bytes[6]=(bytes[6]&15)|64;bytes[8]=(bytes[8]&63)|128;
  const hex=Array.from(bytes,b=>b.toString(16).padStart(2,'0')).join('');
  return hex.slice(0,8)+'-'+hex.slice(8,12)+'-'+hex.slice(12,16)+'-'+hex.slice(16,20)+'-'+hex.slice(20);
}
function setActivityVisible(value) {
  const now=performance.now();
  if(activeStarted!==null)activeElapsed+=Math.max(0,now-activeStarted);
  activeStarted=value?now:null;
}
function beginVisit() {
  visitId=activityId();visitStarted=performance.now();lastAttemptAt=visitStarted;
  activeElapsed=0;activeStarted=document.hidden?null:visitStarted;
}
function activityTiming() {
  const now=performance.now(),elapsed=visitId?Math.max(0,now-visitStarted):0;
  return {elapsedMs:Math.round(elapsed),activeMs:Math.round(Math.min(elapsed,activeElapsed+(activeStarted===null?0:Math.max(0,now-activeStarted)))),
    sinceAttemptMs:Math.round(visitId?Math.max(0,now-lastAttemptAt):0)};
}
function drainActivity() {
  if(!window.GeometryVoice?.trackEvent)return;
  while(activityOutbox.length) {
    try { if(!GeometryVoice.trackEvent(JSON.stringify(activityOutbox[0])))break; }
    catch (_) { break; }
    activityOutbox.shift();
  }
  try { localStorage.setItem(STORE+':outbox',JSON.stringify(activityOutbox)); }
  catch (_) { $('notice').textContent=g('Activity could not be saved. Keep this lesson open and try again.'); }
}
function trackActivity(type,extra={}) {
  if(!lesson||!sessionId||!window.GeometryVoice?.trackEvent)return;
  const event={id:activityId(),sessionId,type,occurredAt:new Date().toISOString(),generation,
    lessonVersion:lesson.version,sequence:eventSequence++,chapter,cue,
    question:inCheckpoint?questionIndex:null,checkpointRevision:current().checkpointRevision||1,
    seconds,visitId,...activityTiming(),...extra};
  // Persist before handing it to native storage; retries keep the exact same UUID.
  activityOutbox.push(JSON.parse(JSON.stringify(event)));
  try {localStorage.setItem(STORE+':outbox',JSON.stringify(activityOutbox));}catch(_){}
  drainActivity();
}
function beginSession(reason) {
  sessionId=activityId();eventSequence=0;exitSent=false;visitId=null;
  trackActivity('lecture_started',{value:reason});
  if(!importedToServer && window.GeometryVoice?.trackEvent) {
    trackActivity('legacy_import',{importedAnswers:questionAnswers,importedSlides:Object.keys(completedSlides)});
    importedToServer=true;
  }
}
function finishSlide(type) {
  const key=chapter+':'+cue,revisit=Boolean(completedSlides[key]);
  trackActivity(type,{revisit});
  completedSlides[key]=true;
}
function applyServerProgress(state,restorePosition=false) {
  if(!lesson||!state||state.lessonVersion!==lesson.version||state.generation<generation)return;
  if(state.generation>generation || (restorePosition && importedToServer)) {
    generation=state.generation;completedSlides={};questionAnswers={};answers={};completedAt=null;
  }
  for(const key of state.completedSlides||[])completedSlides[key]=true;
  lesson.chapters.forEach((c,i)=>{
    if(state.checkpointRevisions?.[i] !== (c.checkpointRevision||1))return;
    questionAnswers[i]=c.questions.map((_,j)=>questionAnswers[i]?.[j]===true||state.questionAnswers?.[i]?.[j]===true);
    if(questionAnswers[i].every(Boolean))answers[i]=true;
  });
  if(allCheckpointsComplete())completedAt=state.completedAt||completedAt||new Date().toISOString();
  if(restorePosition && state.cursorAt) {
    chapter=Math.max(0,Math.min(lesson.chapters.length-1,state.chapter));
    cue=Math.max(0,Math.min(current().cues.length-1,state.cue));seconds=Math.max(0,Math.min(40,state.seconds||0));
    questionIndex=state.questionIndex===1?1:0;inCheckpoint=Boolean(state.inCheckpoint);
  }
  if(state.generation>0)importedToServer=true;
  save();updateNext();updateProgress();
}
window.lessonProgressLoaded=(id,payload)=>{
  let response;try{response=JSON.parse(payload);}catch(_){return;}
  const resolve=progressRequests.get(id);
  if(resolve){progressRequests.delete(id);resolve(response);}
  else if(response.state&&!response.pending&&!activityOutbox.length) {
    applyServerProgress(response.state,false);
    if(started&&inCheckpoint)showQuestion(questionIndex);
  }
  if(response.error||response.pending||activityOutbox.length)$('notice').textContent=g('Saved on this device. Waiting to sync with the server.');
  else if(response.state)$('notice').textContent=g('Progress synced with the server.');
};
function loadServerProgress() {
  if(!window.GeometryVoice?.loadProgress)return Promise.resolve(null);
  const id=activityId();
  return new Promise(resolve=>{
    progressRequests.set(id,resolve);
    setTimeout(()=>{if(progressRequests.delete(id))resolve(null);},3500);
    try {GeometryVoice.loadProgress(id);}catch(_){progressRequests.delete(id);resolve(null);}
  });
}
window.geometryExit=()=>{
  if(started&&!exitSent){trackActivity('lecture_exit');exitSent=true;}
  setActivityVisible(false);setPlaying(false);save();drainActivity();
};
let customSide=5;
const NEXT_WAIT_MS = 10000;
const VOICE_SPEEDS = [0.5, 0.7, 1, 1.25, 1.5, 1.75, 2];
let nextUnlockAt = 0, voiceSpeed = 1, lastSpeechText = '', completedAt = null;
function updateNext() {
  const remaining = completedSlides[chapter+':'+cue]||allCheckpointsComplete()?0:Math.max(0, nextUnlockAt - performance.now());
  const inScene = started && $('welcome').hidden && $('questionPanel').hidden && $('finished').hidden;
  const waiting = inScene && remaining > 0;
  $('next').disabled = !inScene || waiting || Boolean($('contents').open);
  $('next').classList.toggle('next-waiting', waiting);
  $('next').style.setProperty('--next-fill', String(1 - remaining / NEXT_WAIT_MS));
  $('nextLabel').textContent = waiting ? g('Next · ') + Math.ceil(remaining / 1000) + 's' : g('Next →');
  $('next').setAttribute('aria-label', waiting ? g('Next scene available in ') + Math.ceil(remaining / 1000) + g(' seconds') : g('Next scene'));
}
function allCheckpointsComplete() {
  return Boolean(lesson) && lesson.chapters.every((_, index) => answers[index] === true);
}
function recordCompletion() {
  if (!allCheckpointsComplete()) return false;
  completedAt ||= new Date().toISOString();
  const saved = save();
  if (window.GeometryVoice?.setCompleted) {
    if (!GeometryVoice.setCompleted(true)) {
      $('notice').textContent = g('Could not save completion. Please try Finish again.');
      return false;
    }
    return true;
  }
  return saved;
}
let appTheme = window.geometryInitialTheme ?? null;
const systemTheme = window.matchMedia?.('(prefers-color-scheme: dark)');
const darkAccents = {
  '#5265ec':'#a9b6ff', '#008e80':'#65d8c7', '#c06924':'#ffc080',
  '#b45087':'#f3a6d0', '#247dc0':'#89caff', '#a36a20':'#edc17f', '#7961ca':'#c4b2ff'
};
function applyLessonTheme() {
  const dark = appTheme ?? systemTheme?.matches ?? false;
  document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
  // Chapter hues remain recognizable, with brighter lines on dark surfaces.
  const accent = lesson?.chapters[chapter].accent || '#5265ec';
  document.documentElement.style.setProperty('--accent', dark ? darkAccents[accent] || '#a9b6ff' : accent);
  document.querySelector?.('meta[name="theme-color"]')?.setAttribute('content', dark ? '#101722' : '#f5f7fb');
}
window.geometrySetTheme = dark => {
  appTheme = Boolean(dark);
  applyLessonTheme();
};
systemTheme?.addEventListener('change', () => { if (appTheme === null) applyLessonTheme(); });
applyLessonTheme();
// Use the space actually available to the WebView, including after rotation or
// keyboard resizing. Device orientation can disagree with the content viewport.
let portraitLayout = false;
function applyLessonLayout() {
  // Some Android WebViews report a zero CSS viewport height even though the
  // visible window has a valid size. Use its measured pixels for sizing and
  // breakpoints, and refresh them after the native container finishes resizing.
  const width = window.innerWidth, height = window.innerHeight;
  if (width > 0 && height > 0) {
    portraitLayout = height >= width;
    document.documentElement.style.setProperty('--lesson-height', height + 'px');
    document.documentElement.classList?.toggle('short-lesson', height <= 500 && width > 600);
  }
  document.documentElement.classList?.toggle('portrait-lesson', portraitLayout);
}
window.geometrySetPortrait = value => {
  portraitLayout = Boolean(value);
  applyLessonLayout();
};
window.addEventListener('resize', applyLessonLayout);
window.visualViewport?.addEventListener('resize', applyLessonLayout);
applyLessonLayout();

const esc=s=>String(s).replace(/[&<>"']/g,c=>({
  '&':'&amp;',
  '<':'&lt;',
  '>':'&gt;',
  '"':'&quot;',
  "'":'&#39;'
}[c]));
const current=()=>lesson.chapters[chapter];
const line=(x1,y1,x2,y2)=>'<line class="measure" x1="'+x1+'" y1="'+y1+'" x2="'+x2+'" y2="'+y2+'"/>';
const label=(x,y,s,cls='')=>'<text class="label '+cls+'" x="'+x+'" y="'+y+'" text-anchor="middle">'+esc(s)+'</text>';
function fillGrid(x,y,cols,rows,unit,clip='') {
  let s='<g'+(clip?' clip-path="url(#'+clip+')"':'')+'>';
  for(let r=0;
  r<rows;
  r++)for(let c=0;
  c<cols;
  c++)s+='<rect class="tile" style="--i:'+(r*cols+c)+'" x="'+(x+c*unit)+'" y="'+(y+r*unit)+'" width="'+unit+'" height="'+unit+'"/>';
  return s+'</g>';
}
function renderShape() {
  if (presentation.render) {
    presentation.render({chapter:current(), cue, value:customSide, scene:$('scene'), label, g});
    return;
  }
  const c=current(), mode=c.cues[cue].mode, area=['area','split','rearrange','compare'].includes(mode), edge=mode==='perimeter'||mode==='compare';
  let shape='', labels='', extras='', tiles='', path='';
  if(c.shape==='rectangle'||c.shape==='square') {
    const a=c.shape==='square'?customSide:c.a,b=c.shape==='square'?customSide:c.b,u=Math.min(50,380/a,220/b),w=a*u,h=b*u,x=500-w/2,y=230-h/2;
    path='M '+x+' '+y+' h '+w+' v '+h+' h '+(-w)+' Z';
    labels=label(500,y+h+32,a+' '+(chapter===9?'m':'cm'))+label(x-45,235,b+' '+(chapter===9?'m':'cm'));
    if(area)tiles=fillGrid(x,y,a,b,u);
    if(c.shape==='square'&&cue===3)labels+=label(500,410,'P = '+(4*a)+' cm     A = '+(a*a)+' cm²');
  }
  else if(c.shape==='triangle') {
    path='M 350 330 L 650 330 L 350 130 Z';
    labels=label(500,370,g('base = 6 cm'))+label(275,230,g('height = 4 cm'));
    extras='<path d="M350 310 h20 v20" fill="none" stroke="#647084"/>';
    if(area)tiles=fillGrid(350,130,6,4,50,'shapeClip');
    if(mode==='split')extras+='<path class="split-piece" d="M350 130 L650 130 L650 330 Z"/>';
    if(edge)labels+=label(585,205,g('sloping side ≈ 7.21 cm'),'sub');
  }
  else if(c.shape==='parallelogram') {
    path='M300 330 L600 330 L700 130 L400 130 Z';
    labels=label(450,370,g('base = 6 cm'))+label(460,235,'h = 4 cm');
    extras=line(400,130,400,330)+'<path d="M400 310 h20 v20" fill="none" stroke="#647084"/>';
    if(area)tiles=fillGrid(300,130,8,4,50,'shapeClip');
    if(mode==='rearrange')extras+='<path class="cut-piece" d="M300 330 L400 130 L400 330 Z"/>';
    if(edge)labels+=label(690,250,g('side ≈ 4.47 cm'),'sub');
  }
  else if(c.shape==='trapezoid') {
    path='M200 330 L600 330 L500 180 L300 180 Z';
    labels=label(400,365,'8 cm')+label(400,160,'4 cm')+label(345,260,'h = 3 cm');
    extras=line(300,180,300,330);
    if(area)tiles=fillGrid(200,180,8,3,50,'shapeClip');
    if(mode==='rearrange')extras+='<path class="twin" d="M500 180 L900 180 L800 330 L600 330 Z"/>'+label(730,390,g('matching copy'),'sub');
  }
  else if(c.shape==='circle') {
    shape='<circle class="shape-fill '+(area?'pulse-fill':'')+'" cx="500" cy="225" r="120"/>';
    if(area) {
      for(let j=0;
      j<12;
      j++) {
        let t=j*Math.PI/6,v=t+Math.PI/6;
        tiles+='<path class="tile" style="--i:'+j+'" d="M500 225 L'+(500+120*Math.cos(t))+' '+(225+120*Math.sin(t))+' A120 120 0 0 1 '+(500+120*Math.cos(v))+' '+(225+120*Math.sin(v))+' Z"/>';
      }
    }
    extras=line(380,225,620,225)+'<circle cx="500" cy="225" r="5" fill="#23334a"/>';
    labels=label(557,205,'r = 3 cm')+label(500,380,'d = 6 cm');
    if(edge)extras+='<circle class="edge" pathLength="1" cx="500" cy="225" r="120"/><g class="orbit"><circle cx="620" cy="225" r="9" fill="#efac47"/></g>';
  }
  else if(c.shape==='compound') {
    path='M300 130 H500 V230 H600 V330 H300 Z';
    labels=label(450,372,'6 m')+label(255,235,'4 m')+label(550,210,'2 m','sub');
    if(area)tiles=fillGrid(300,130,6,4,50,'shapeClip');
    if(mode==='split')extras=line(300,230,600,230)+label(400,190,'4 × 2')+label(450,290,'6 × 2');
    if(mode==='area')extras='<path d="M500 130 H600 V230 H500 Z" fill="none" stroke="#c06924" stroke-dasharray="6 5"/>';
  }
  else if(c.shape==='comparison') {
    path='M100 130 H400 V330 H100 Z M500 180 H900 V280 H500 Z';
    labels=label(250,370,'6 × 4')+label(700,370,'8 × 2');
    if(area)tiles=fillGrid(100,130,6,4,50)+fillGrid(500,180,8,2,50);
  }
  if(c.shape==='parallelogram'&&mode==='rearrange') {
    path='M400 330 L600 330 L700 130 L400 130 Z';
  }
  if(path) {
    shape='<defs><clipPath id="shapeClip"><path d="'+path+'"/></clipPath></defs><path class="shape-fill" d="'+path+'"/>';
    if(edge)extras+='<path class="edge" pathLength="1" d="'+path+'"/><path class="runner" pathLength="1" d="'+path+'"/>';
  }
  const diagram=shape+tiles+extras+labels;
  $('scene').innerHTML='<g class="shape-enter">'+diagram+'</g>';
  // Include measurement labels and the complete travel of moving pieces.
  const bounds = {
    rectangle: [225, 95, 485, 295],
    square: [280, 95, 440, 335],
    triangle: [205, 60, 525, 330],
    parallelogram: [270, 105, 495, 290],
    trapezoid: [170, 80, 810, 335],
    circle: [355, 80, 290, 325],
    compound: [220, 105, 410, 290],
    comparison: [75, 105, 850, 290]
  }[c.shape] || [200, 65, 600, 365];
  if (c.shape === 'rectangle' || c.shape === 'square') {
    const a = c.shape === 'square' ? customSide : c.a;
    const b = c.shape === 'square' ? customSide : c.b;
    const unit = Math.min(50, 380 / a, 220 / b);
    const width = a * unit;
    const height = b * unit;
    const top = 230 - height / 2 - 16;
    bounds.splice(0, 4, 500 - width / 2 - 80, top, width + 100,
      c.shape === 'square' && cue === 3 ? 430 - top : height + 65);
  }
  // The matching trapezoid travels up and right before joining. Keep its whole
  // path in frame, but do not reserve that space in the other three scenes.
  if (c.shape === 'trapezoid' && mode !== 'rearrange') bounds.splice(0,4,170,135,460,255);
  $('scene').setAttribute('viewBox', bounds.join(' '));
  $('scene').setAttribute('aria-label',c.title+'. '+c.cues[cue].headline);
}
function save() {
  if(!lesson)return;
  try {
    localStorage.setItem(STORE,JSON.stringify( {
      version:2,chapter,cue,seconds,answers,questionAnswers,questionIndex,inCheckpoint,voiceSpeed,completedAt,
      finalCheckpointRevision:lesson.chapters[lesson.chapters.length-1].checkpointRevision,
      completedSlides,generation,importedToServer
    }
    ));
    return true;
  }
  catch(e) {
    $('notice').textContent=g('Storage is unavailable; keep this page open to retain your progress.');
    return false;
  }
}
function stopVoice() {
  token++;
  speaking=false;
  clearTimeout(voiceTimer);
  if(window.GeometryVoice)GeometryVoice.stop();
  else if(window.speechSynthesis)speechSynthesis.cancel();
}
window.geometryVoiceEnd=(id,ok)=> {
  if(Number(id)!==token)return;
  speaking=false;
  clearTimeout(voiceTimer);
  $('voiceState').textContent=ok?g('Explore the model'):g('Read along');
  if(!ok)$('notice').textContent=g('Narration is unavailable. Captions remain available; check that an English voice is installed.');
}
;
function speak(text) {
  stopVoice();
  lastSpeechText = text;
  if(muted) {
    $('voiceState').textContent=g('Captions on');
    return;
  }
  const id=token;
  speaking=true;
  $('voiceState').textContent=g('Speaking…');
  voiceTimer=setTimeout(()=>window.geometryVoiceEnd(id,false),90000 / Math.min(voiceSpeed, 1));
  if(window.GeometryVoice) {
    GeometryVoice.setRate?.(voiceSpeed);
    if(!GeometryVoice.speak(text,String(id)))window.geometryVoiceEnd(id,false);
  }
  else if(window.speechSynthesis) {
    const utterance=new SpeechSynthesisUtterance(text);
    utterance.lang=lessonLanguage === 'sq' ? 'sq-AL' : 'en-US';
    utterance.rate=.92 * voiceSpeed;
    utterance.onend=()=>window.geometryVoiceEnd(id,true);
    utterance.onerror=()=>window.geometryVoiceEnd(id,false);
    speechSynthesis.speak(utterance);
  }
  else window.geometryVoiceEnd(id,false);
}
function setPlaying(value) {
  playing=value;
  lastFrame=0;
  $('stage').classList.toggle('paused',!value);
  $('play').textContent=value?'Ⅱ':'▶';
  $('play').setAttribute('aria-label',value?g('Pause'):g('Play'));
  if(!value)stopVoice();
}
function updateProgress() {
  $('progress').value=!$('finished').hidden && allCheckpointsComplete() ? 1600 : chapter*160+cue*40+Math.min(seconds,40);
  $('progressText').textContent=Object.values(answers).filter(Boolean).length+g(' / 10 checkpoints');
  $('sceneCount').textContent=g('Scene ')+(cue+1)+g(' of 4');
  $('previous').disabled=chapter===0&&cue===0;
}
function renderCue(narrate=true) {
  inCheckpoint=false;
  const c=current(), s=c.cues[cue];
  applyLessonTheme();
  $('heading').textContent=c.title;
  $('headline').textContent=s.headline;
  $('caption').textContent=s.text;
  $('mode').textContent=( {
    outline:g('MEET THE SHAPE'),perimeter:g('TRACE THE BOUNDARY'),area:g('COVER THE INSIDE'),split:g('SPLIT & DISCOVER'),rearrange:g('MOVE THE PIECES'),compare:g('MAKE A CONNECTION')
  }
  )[s.mode] || g(presentation.modes?.[s.mode] || 'WATCH & DISCOVER');
  $('chapterCount').textContent=String(chapter+1).padStart(2,'0')+' / 10';
  $('explorer').hidden=presentation.hasExplorer ? !presentation.hasExplorer(c, cue) : c.shape!=='square'||cue!==3;
  $('sideValue').textContent=presentation.explorerValue ? presentation.explorerValue(customSide, g) : customSide+' cm';
  $('questionPanel').hidden=true;
  $('finished').hidden=true;
  nextUnlockAt = performance.now() + NEXT_WAIT_MS;
  updateNext();
  updateProgress();
  renderShape();
  if(started) {beginVisit();trackActivity('slide_entered',{revisit:Boolean(completedSlides[chapter+':'+cue])||allCheckpointsComplete()});}
  if(playing&&narrate)speak(s.text);
}
function move(ch,sc,play=playing) {
  stopVoice();
  chapter=ch;
  cue=sc;
  seconds=0;
  setPlaying(play);
  renderCue();
  save();
}
function start() {
  if(!lesson||$('start').disabled)return;
  beginSession(allCheckpointsComplete()?'review':'start');
  started=true;
  $('welcome').hidden=true;
  if(inCheckpoint) {
    showQuestion(questionIndex);
    return;
  }
  setPlaying(true);
  renderCue();
}
function showQuestion(index) {
  setPlaying(false);
  inCheckpoint=true;
  const questions=current().questions;
  const unanswered=questions.findIndex((_,i)=>questionAnswers[chapter]?.[i]!==true);
  questionIndex=Number.isInteger(index) ? Math.max(0,Math.min(questions.length-1,index)) : Math.max(0,unanswered);
  $('questionPanel').hidden=false;
  updateNext();
  $('answers').replaceChildren();
  $('feedback').textContent='';
  let solved=questionAnswers[chapter]?.[questionIndex]===true;
  $('continue').hidden=!solved;
  $('continue').textContent=questionIndex===0?g('Next question →'):g('Complete checkpoint →');
  $('questionCount').textContent=g('Question ')+(questionIndex+1)+g(' of 2');
  const q=questions[questionIndex];
  beginVisit();trackActivity('question_shown',{question:questionIndex,revisit:solved});
  $('question').textContent=q.prompt;
  $('caption').textContent=q.prompt;
  $('speaker').textContent=g('THINK IT THROUGH');
  function check(value,button) {
    if(solved||!Number.isFinite(value))return;
    const correct=value===q.answer;
    trackActivity('answer_submitted',{question:questionIndex,answer:value});
    lastAttemptAt=performance.now();
    if(button)button.classList.add(correct?'correct':'incorrect');
    const feedback=(correct?g('Exactly. '):g('Let’s check that. '))+q.explanation;
    $('feedback').textContent=feedback;
    $('caption').textContent=feedback;
    speak(feedback);
    if(correct) {
      solved=true;
      questionAnswers[chapter] ||= [];
      questionAnswers[chapter][questionIndex]=true;
      if(questions.every((_,i)=>questionAnswers[chapter][i]===true))answers[chapter]=true;
      document.querySelectorAll('#answers button, #answers input').forEach(element=>element.disabled=true);
      $('continue').hidden=false;
      updateProgress();
      save();
      if (allCheckpointsComplete()) recordCompletion();
    }
  }
  if(q.type==='choice')q.options.forEach((option,i)=> {
    const b=document.createElement('button');
    b.textContent=String.fromCharCode(65+i)+'  '+option;
    b.onclick=()=>check(i,b);
    $('answers').append(b);
  }
  );
  else {
    const row=document.createElement('form'),input=document.createElement('input'),unit=document.createElement('span'),b=document.createElement('button');
    input.type='number';
    input.inputMode='decimal';
    input.setAttribute('enterkeyhint','done');
    input.min='0';
    input.step='any';
    input.required=true;
    input.setAttribute('aria-label',q.unit?g('Your answer in ')+g(q.unit):g('Your answer'));
    unit.textContent=' '+g(q.unit)+' ';
    b.type='submit';
    b.textContent=g('Check answer');
    row.append(input,unit,b);
    row.onsubmit=e=> {
      e.preventDefault();
      if(input.value.trim()!=='') {
        input.blur();
        window.GeometryVoice?.hideKeyboard?.();
        check(Number(input.value),b);
      }
    }
    ;
    $('answers').append(row);
  }
  if(solved) {
    if(q.type==='choice')$('answers').children[q.answer].classList.add('correct');
    else $('answers').children[0].children[0].value=String(q.answer);
    $('feedback').textContent=g('Exactly. ')+q.explanation;
    document.querySelectorAll('#answers button, #answers input').forEach(element=>element.disabled=true);
  }
  speak(q.prompt);
  save();
}
function completeOrContinue() {
  if(!inCheckpoint||questionAnswers[chapter]?.[questionIndex]!==true)return;
  trackActivity('question_next',{question:questionIndex});
  if(questionIndex<current().questions.length-1) {
    showQuestion(questionIndex+1);
    return;
  }
  if(!answers[chapter])return;
  inCheckpoint=false;
  $('speaker').textContent=g('YOUR GUIDE');
  stopVoice();
  if(chapter<lesson.chapters.length-1)move(chapter+1,0,true);
  else {
    $('questionPanel').hidden=true;
    $('finished').hidden=false;
    updateNext();
    updateProgress();
    const count=Object.values(answers).filter(Boolean).length;
    $('score').textContent=count===10?g('All ten checkpoints completed. Well done!'):count+g(' of ten checkpoints completed. Revisit the remaining chapters to finish your journey.');
    $('finish').disabled = !allCheckpointsComplete();
    if (allCheckpointsComplete()) recordCompletion();
    speak($('score').textContent);
    save();
  }
}
function openContents() {
  if(!lesson||$('start').disabled)return;
  if(started)trackActivity('pause',{value:'chapters'});
  setActivityVisible(false);
  setPlaying(false);
  $('chapterList').replaceChildren();
  lesson.chapters.forEach((c,i)=> {
    const b=document.createElement('button');
    b.textContent=String(i+1).padStart(2,'0')+'  '+c.title+(answers[i]?'   ✓':'');
    b.onclick=()=> {
      $('contents').close();
      if(!started)beginSession('chapter');
      trackActivity('chapter_selected',{targetChapter:i,targetCue:0});
      started=true;
      $('welcome').hidden=true;
      $('speaker').textContent=g('YOUR GUIDE');
      move(i,0,true);
    }
    ;
    $('chapterList').append(b);
  }
  );
  if(!$('contents').open)$('contents').showModal();
}
$('start').onclick=start;
$('play').onclick=()=> {
  if(!lesson)return;
  if(!started) {
    start();
    return;
  }
  if(!$('questionPanel').hidden||!$('finished').hidden)return;
  trackActivity(playing?'pause':'resume',{value:'play_button'});
  if(playing)setPlaying(false);
  else {
    setPlaying(true);
    speak(current().cues[cue].text);
  }
}
;
$('next').onclick=()=> {
  updateNext();
  if(!lesson||$('next').disabled)return;
  finishSlide('slide_next');
  if(cue<3)move(chapter,cue+1);
  else showQuestion();
}
;
$('previous').onclick=()=> {
  if(!lesson||!started)return;
  if(cue===0&&chapter===0)return;
  const targetChapter=cue>0?chapter:chapter-1,targetCue=cue>0?cue-1:3;
  trackActivity('slide_back',{targetChapter,targetCue,revisit:Boolean(completedSlides[targetChapter+':'+targetCue])||allCheckpointsComplete()});
  $('speaker').textContent=g('YOUR GUIDE');
  if(cue>0)move(chapter,cue-1);
  else if(chapter>0)move(chapter-1,3);
}
;
$('replay').onclick=()=> {
  if(!lesson||!started)return;
  trackActivity('voice_replay');
  speak($('caption').textContent);
}
;
$('mute').onclick=()=> {
  muted=!muted;
  trackActivity('mute',{value:String(muted)});
  stopVoice();
  $('mute').textContent=muted?g('Sound off'):g('Sound on');
  $('mute').setAttribute('aria-pressed',String(muted));
  if(!muted&&playing)speak($('caption').textContent);
}
;
$('continue').onclick=completeOrContinue;
$('finish').onclick=()=> {
  if (!recordCompletion()) return;
  trackActivity('lecture_finished');
  window.geometryExit();
  setPlaying(false);
  window.GeometryVoice?.hideKeyboard?.();
  if (window.GeometryVoice?.finish) {
    if (!GeometryVoice.finish()) $('notice').textContent=g('Could not finish. Please try again.');
  } else {
    $('finish').textContent=g('Finished ✓');
    $('finish').disabled=true;
    $('score').textContent=g('Lecture finished. Your completion is saved on this device.');
  }
};
function closeVoiceSpeedMenu(returnFocus=false) {
  $('voiceSpeedMenu').hidden=true;
  $('voiceSpeedButton').setAttribute('aria-expanded','false');
  if(returnFocus)$('voiceSpeedButton').focus();
}
function selectVoiceSpeed(selected) {
  if (!VOICE_SPEEDS.includes(selected)) return;
  const resumeSpeech = speaking;
  const text = lastSpeechText;
  voiceSpeed = selected;
  if(started)trackActivity('voice_speed',{value:String(selected)});
  const label = selected === 1.5 ? '1.50×' : selected + '×';
  $('voiceSpeedValue').textContent=label;
  $('voiceSpeedButton').setAttribute('aria-label',g('Voice speed, ')+label.replace('×',g(' times')));
  document.querySelectorAll('#voiceSpeedMenu [data-speed]').forEach(option=>
    option.setAttribute('aria-selected',String(Number(option.dataset.speed)===selected)));
  window.GeometryVoice?.setRate?.(voiceSpeed);
  save();
  if (resumeSpeech) speak(text);
}
$('voiceSpeedButton').onclick=()=> {
  const opening=$('voiceSpeedMenu').hidden;
  $('voiceSpeedMenu').hidden=!opening;
  $('voiceSpeedButton').setAttribute('aria-expanded',String(opening));
  if(opening)$('voiceSpeedMenu').querySelector('[aria-selected="true"]')?.focus();
};
document.querySelectorAll('#voiceSpeedMenu [data-speed]').forEach(option=> {
  option.onclick=()=> { selectVoiceSpeed(Number(option.dataset.speed)); closeVoiceSpeedMenu(true); };
});
document.addEventListener('pointerdown',event=> {
  if(!$('voiceSpeedControl').contains(event.target))closeVoiceSpeedMenu();
});
document.addEventListener('keydown',event=> {
  if(event.key==='Escape'&&!$('voiceSpeedMenu').hidden) { event.preventDefault(); closeVoiceSpeedMenu(true); }
});
$('contentsButton').onclick=openContents;
$('review').onclick=openContents;
$('closeContents').onclick=()=>$('contents').close();
$('contents').addEventListener?.('close',()=>setActivityVisible(!document.hidden));
$('restart').onclick=()=> {
  if(confirm(g('Restart this unit and clear its saved checkpoints?'))) {
    if (window.GeometryVoice?.setCompleted && !GeometryVoice.setCompleted(false)) {
      $('notice').textContent=g('Could not reset completion. Please try again.');
      return;
    }
    completedAt = null;
    if(!sessionId){sessionId=activityId();eventSequence=0;}
    trackActivity('reset');generation++;completedSlides={};
    questionAnswers={};
    questionIndex=0;
    answers= {
    }
    ;
    $('contents').close();
    $('speaker').textContent=g('YOUR GUIDE');
    started=true;
    $('welcome').hidden=true;
    beginSession('restart');
    move(0,0,false);
  }
}
;
$('side').oninput=()=> {
  customSide=Number($('side').value);
  trackActivity('explorer_changed',{value:String(customSide)});
  $('sideValue').textContent=presentation.explorerValue ? presentation.explorerValue(customSide, g) : customSide+' cm';
  renderShape();
}
;
document.addEventListener('visibilitychange',()=> {
  if(started)trackActivity(document.hidden?'hidden':'visible');
  setActivityVisible(!document.hidden);
  if(document.hidden) {
    setPlaying(false);
    save();
  }
}
);
window.addEventListener('pagehide',()=> {
  window.geometryExit();
}
);
window.geometryPause=()=> {
  if(started)trackActivity('hidden',{value:'app_background'});
  setActivityVisible(false);
  setPlaying(false);
  save();
}
;
window.geometryResume=()=> {
  if(started)trackActivity('visible',{value:'app_foreground'});
  setActivityVisible(!document.hidden);
};
function frame(now) {
  updateNext();
  if(playing&&lesson) {
    if(lastFrame)seconds+=Math.min((now-lastFrame)/1000,.25);
    lastFrame=now;
    updateProgress();
    if(seconds>=current().cues[cue].seconds&&!speaking) {
      finishSlide('slide_auto');
      if(cue<3)move(chapter,cue+1,true);
      else showQuestion();
    }
    if(now-savedAt>1000) {
      save();
      savedAt=now;
    }
  }
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
fetch('./'+(presentation.id || 'geometry')+(lessonLanguage === 'sq' ? '.sq' : '')+'.json').then(r=> {
  if(!r.ok)throw Error('HTTP '+r.status);
  return r.json();
}
).then(async data=> {
  lesson=data;
  localizeLessonInterface();
  $('subjectName').textContent=lesson.subject||g('Subject');
  let resumeCheckpoint=false;
  try {
    let stored = localStorage.getItem(STORE);
    // Claim the old device-wide progress only once when upgrading, then keep
    // every student's answers and completion in their own player storage.
    if (!stored && STORE !== LEGACY_STORE && !localStorage.getItem(LEGACY_STORE + ':owner')) {
      stored = localStorage.getItem(LEGACY_STORE);
      if (stored) localStorage.setItem(STORE, stored);
      localStorage.setItem(LEGACY_STORE + ':owner', STORE);
    }
    const parsed=JSON.parse(stored);
    const saved=presentation.migrateSaved ? presentation.migrateSaved(parsed) : parsed;
    if(saved?.version===1 || saved?.version===2) {
      chapter=Math.min(lesson.chapters.length-1,Math.max(0,Math.floor(Number(saved.chapter)||0)));
      cue=Math.min(3,Math.max(0,Math.floor(Number(saved.cue)||0)));
      seconds=Math.min(40,Math.max(0,Number(saved.seconds)||0));
      if (VOICE_SPEEDS.includes(saved.voiceSpeed)) voiceSpeed = saved.voiceSpeed;
      generation=Number.isSafeInteger(saved.generation)&&saved.generation>=0?saved.generation:0;
      importedToServer=saved.importedToServer===true;
      lesson.chapters.forEach((c,i)=>c.cues.forEach((_,j)=>{if(saved.completedSlides?.[i+':'+j]===true)completedSlides[i+':'+j]=true;}));
      for(let i=0;i<lesson.chapters.length;i++) {
        const questions=lesson.chapters[i].questions;
        questionAnswers[i]=questions.map((_,j)=>saved.version===2
          ? saved.questionAnswers?.[i]?.[j]===true
          : saved.answers?.[i]===true && j===lesson.chapters[i].legacyQuestionIndex);
        if(questionAnswers[i].every(Boolean))answers[i]=true;
        if(answers[i]&&!saved.completedSlides)lesson.chapters[i].cues.forEach((_,j)=>completedSlides[i+':'+j]=true);
      }
      if(saved.version===1 && Object.values(saved.answers||{}).some(value=>value===true)) {
        $('notice').textContent=g('Checkpoint updated: complete both questions to finish each chapter.');
      }
      resumeCheckpoint=saved.version===2 && saved.inCheckpoint===true;
      questionIndex=saved.questionIndex===1?1:0;
      const finalChapter=lesson.chapters.length-1;
      const finalRevision=lesson.chapters[finalChapter].checkpointRevision;
      if(finalRevision && saved.finalCheckpointRevision!==finalRevision) {
        questionAnswers[finalChapter]=lesson.chapters[finalChapter].questions.map(()=>false);
        delete answers[finalChapter];
        if(chapter===finalChapter && resumeCheckpoint)questionIndex=0;
        completedAt=null;
        $('notice').textContent=g('The final checkpoint has new questions. Complete both to finish.');
      }
      if (allCheckpointsComplete() && typeof saved.completedAt === 'string') completedAt = saved.completedAt;
      $('start').textContent=g('Resume exploring ▶');
    }
  }
  catch(e) {
  }
  inCheckpoint=resumeCheckpoint;
  drainActivity();
  const remote=await loadServerProgress();
  if(remote?.state&&!remote.pending&&!activityOutbox.length) {
    applyServerProgress(remote.state,true);
    resumeCheckpoint=inCheckpoint;
  }
  renderCue(false);
  inCheckpoint=resumeCheckpoint;
  selectVoiceSpeed(voiceSpeed);
  if (allCheckpointsComplete()) {
    recordCompletion();
    $('start').textContent=g('Review lecture ▶');
  } else if(window.GeometryVoice?.setCompleted) {
    if(!GeometryVoice.setCompleted(false))$('notice').textContent=g('Could not reset completion. Please try again.');
  }
  $('start').disabled=false;
}
).catch(e=> {
  $('heading').textContent=lessonLanguage === 'sq' ? 'Mësimi nuk mund të ngarkohej' : 'The lesson could not load';
  $('notice').textContent=(lessonLanguage === 'sq' ? 'Kontrollo lidhjen me serverin dhe ringarko faqen. ' : 'Check your server connection, then reload. ')+e.message;
  $('start').disabled=true;
}
);
