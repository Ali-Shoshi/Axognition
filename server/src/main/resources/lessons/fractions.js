'use strict';
// Models use the same whole width, so comparisons never change the reference size.
window.lessonPresentation = {
  id:'fractions', title:'Share, see & solve · Axognition', storageKey:'axognition-fractions-v1',
  modes:{whole:'MEET THE WHOLE', partition:'MAKE EQUAL SHARES', select:'COUNT THE PARTS',
    combine:'BRING PARTS TOGETHER', name:'NAME WHAT YOU SEE', compare:'MAKE A CONNECTION',
    explore:'TRY IT YOURSELF', travel:'FOLLOW THE FRACTION', remove:'TAKE PARTS AWAY'},
  hasExplorer:(chapter,cue)=>chapter.cues[cue].model.type==='explore',
  explorerValue:value=>'1/'+value,
  migrateSaved(saved) {
    if (!saved || saved.version === 1 || saved.version === 2) return saved;
    // The earlier player used three 45-second scenes and a 135-second checkpoint.
    // Preserve earned checkpoints and resume near the corresponding explanation.
    return {version:1,chapter:saved.index,cue:Math.min(3,Math.floor((Number(saved.elapsed)||0)/45)),
      seconds:0,answers:saved.answers};
  },
  render({chapter,cue,value,scene,label,g}) {
    const model=chapter.cues[cue].model;
    const text=(x,y,s,cls='')=>label(x,y,g(s),cls);
    const width=640, left=100;
    function strip(row,y) {
      let result='';
      for(let i=0;i<row.d;i++) {
        const x=row.unequal ? left+(i===0?0:width*.7) : left+i*width/row.d;
        const w=row.unequal ? width*(i===0?.7:.3) : width/row.d;
        const removed=row.removedFrom!==undefined && i>=row.removedFrom && i<row.n;
        const added=row.secondaryFrom!==undefined && i>=row.secondaryFrom && i<row.n;
        result+='<rect class="fraction-slot" x="'+x+'" y="'+y+'" width="'+w+'" height="76"/>';
        if(i<row.n) result+='<rect class="fraction-piece '+(removed?'fraction-away':added?'fraction-join':'fraction-count')+'" style="--i:'+i+'" x="'+(x+2)+'" y="'+(y+2)+'" width="'+(w-4)+'" height="72"/>';
        if(removed) result+='<path class="fraction-cross" d="M'+(x+12)+' '+(y+20)+' l'+(w-24)+' 36 M'+(x+w-12)+' '+(y+20)+' l-'+(w-24)+' 36"/>';
      }
      return result+(row.caption?text(420,y+111,row.caption):'');
    }
    let svg='';
    if(model.type==='bars') {
      model.rows.forEach((row,i)=>svg+=strip(row,model.rows.length===1?135:65+i*155));
    } else if(model.type==='explore') {
      svg=strip({d:value,n:1,caption:'1/'+value},125)+text(420,290,'Same whole. One selected part.','sub');
    } else if(model.type==='reading') {
      svg=strip({d:model.d,n:model.n,caption:''},65);
      svg+=text(350,220,model.n)+text(350,272,model.d)+
        '<path class="fraction-rule" d="M326 235 h48"/>'+text(535,220,'Numerator: selected parts','sub')+text(535,272,'Denominator: all equal parts','sub');
    } else if(model.type==='line') {
      const step=width/model.d, distance=step*model.n;
      svg='<path class="fraction-rule" d="M100 190 H740"/>';
      for(let i=0;i<=model.d;i++) {
        const x=left+i*step;
        svg+='<path class="fraction-rule" d="M'+x+' 180 v20"/>'+text(x,235,i===0?'0':i===model.d?'1':i+'/'+model.d);
        if(i<model.d && model.d>1) svg+='<path class="fraction-step" style="--i:'+i+'" d="M'+(x+4)+' 165 Q'+(x+step/2)+' 95 '+(x+step-4)+' 165"/>';
      }
      svg+='<circle class="fraction-marker '+(model.travel?'fraction-travel':'')+'" style="--distance:'+distance+'px;--steps:'+Math.max(1,model.n)+'" cx="'+(left+distance)+'" cy="190" r="11"/>';
      svg+=text(420,305,model.half?'2/4 = 1/2 < 3/4 < 1':model.n?model.n+'/'+model.d:'0 → 1');
    } else if(model.type==='collection') {
      for(let group=0;group<4;group++) {
        const x=145+group*180, selected=group<model.selected;
        if(model.groups) svg+='<rect class="fraction-group" x="'+(x-50)+'" y="65" width="100" height="175" rx="20"/>';
        for(let j=0;j<2;j++) svg+='<circle class="'+(selected?'fraction-piece fraction-count':'fraction-slot')+'" style="--i:'+(group*2+j)+'" cx="'+x+'" cy="'+(110+j*80)+'" r="24"/>';
        if(model.groups) svg+=text(x,273,'1/4','sub');
      }
      svg+=text(420,325,model.formula?'8 ÷ 4 × 3 = 6':model.selected===3?'3/4 of 8 = 6':model.selected===1?'1/4 of 8 = 2':'8 counters');
    }
    scene.innerHTML='<g class="shape-enter">'+svg+'</g>';
    scene.setAttribute('viewBox','45 10 760 360');
    scene.setAttribute('aria-label',chapter.title+'. '+chapter.cues[cue].headline+'. '+chapter.cues[cue].text);
  }
};
