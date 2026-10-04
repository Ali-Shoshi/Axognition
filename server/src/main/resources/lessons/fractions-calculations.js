'use strict';
// The shared player owns timing, voice, checkpoints, scoring and saved progress.
window.lessonPresentation = {
  id:'fractions-calculations', title:'Fraction calculations · Axognition', storageKey:'axognition-fractions-calculations-v1',
  modes:{plan:'PLAN THE CALCULATION', convert:'MATCH THE DENOMINATORS', calculate:'WORK THROUGH THE STEPS',
    simplify:'SIMPLIFY & CHECK', multiply:'MULTIPLY THE FRACTIONS', cancel:'CANCEL COMMON FACTORS',
    divide:'MULTIPLY BY THE RECIPROCAL', order:'FOLLOW THE ORDER', brackets:'START INSIDE THE BRACKETS'},
  hasExplorer:()=>false,
  render({chapter,cue,scene,label,g}) {
    const model=chapter.cues[cue].model;
    const esc=value=>String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    const text=(x,y,value,cls='')=>label(x,y,g(value),cls);
    function equation(expression,y,index) {
      const tokens=expression.match(/\d+\/\d+|\d+|[^\s]/g)||[];
      const widths=tokens.map(token=>token.includes('/')?Math.max(...token.split('/').map(n=>n.length))*17+24:Math.max(24,token.length*17));
      const total=widths.reduce((sum,w)=>sum+w+12,0)-12,scale=Math.min(1,740/total);
      let x=-total/2,result='';
      tokens.forEach((token,i)=>{
        const center=x+widths[i]/2;
        if(token.includes('/')) {
          const [n,d]=token.split('/');
          result+='<text class="calc-number" text-anchor="middle" x="'+center+'" y="-11">'+esc(n)+'</text>'+
            '<path class="calc-rule" d="M'+x+' 0 h'+widths[i]+'"/>'+
            '<text class="calc-number" text-anchor="middle" x="'+center+'" y="27">'+esc(d)+'</text>';
        } else result+='<text class="calc-number" text-anchor="middle" x="'+center+'" y="8">'+esc(token)+'</text>';
        x+=widths[i]+12;
      });
      return '<g class="calc-step" style="--step:'+index+'"><g transform="translate(420 '+y+') scale('+scale+')">'+result+'</g>'+text(420,y+49,model.steps[index].note,'calc-note')+'</g>';
    }
    function bar(row,y,index) {
      const wholes=Math.max(1,Math.ceil(row.n/row.d)),unit=560/(wholes*row.d),nSlots=wholes*row.d;
      let result='';
      for(let i=0;i<nSlots;i++) {
        const x=90+i*unit;
        result+='<rect class="fraction-slot" x="'+x+'" y="'+y+'" width="'+unit+'" height="30"/>';
        if(i<row.n) {
          const removed=row.removeFrom!==undefined&&i>=row.removeFrom;
          const group=row.groups?.findIndex((end)=>i<end)??0;
          result+='<rect class="calc-piece '+(removed?'calc-away':'')+'" style="--i:'+i+';--piece:var(--'+(group===1?'control':group===2?'muted':'accent')+')" x="'+(x+1)+'" y="'+(y+1)+'" width="'+Math.max(0,unit-2)+'" height="28"/>';
        }
        if(i>0&&i%row.d===0)result+='<path class="calc-whole" d="M'+x+' '+(y-4)+' v38"/>';
      }
      result+=text(728,y+22,row.caption,'calc-note');
      if(row.group) {
        const w=unit*row.group;
        result+='<path class="calc-group" d="M90 '+(y+36)+' v8 h'+w+' v-8"/>';
        result+=text(90+w/2,y+64,row.groupLabel,'calc-note');
      }
      return '<g class="calc-visual" style="--step:'+index+'">'+result+'</g>';
    }
    function grid(model) {
      const x=210,y=308,w=420,h=108,dx=w/model.cols,dy=h/model.rows;
      let result='<rect class="fraction-slot" x="'+x+'" y="'+y+'" width="'+w+'" height="'+h+'"/>';
      result+='<rect class="calc-factor-a" x="'+x+'" y="'+y+'" width="'+(dx*model.selectedCols)+'" height="'+h+'"/>';
      result+='<rect class="calc-factor-b" x="'+x+'" y="'+y+'" width="'+w+'" height="'+(dy*model.selectedRows)+'"/>';
      for(let r=0;r<model.rows;r++)for(let c=0;c<model.cols;c++) {
        if(r<model.selectedRows&&c<model.selectedCols)result+='<rect class="calc-overlap" x="'+(x+c*dx+2)+'" y="'+(y+r*dy+2)+'" width="'+(dx-4)+'" height="'+(dy-4)+'"/>';
        result+='<rect class="calc-grid-cell" x="'+(x+c*dx)+'" y="'+(y+r*dy)+'" width="'+dx+'" height="'+dy+'"/>';
      }
      return result+text(420,445,model.caption,'calc-note');
    }
    let svg=model.steps.map((step,i)=>equation(step.expression,47+i*94,i)).join('');
    if(model.grid)svg+=grid(model.grid);
    else if(model.bars)model.bars.forEach((row,i)=>{svg+=bar(row,304+i*48,model.steps.length+i);});
    scene.innerHTML='<g>'+svg+'</g>';
    scene.setAttribute('viewBox','0 0 840 470');
    scene.setAttribute('aria-label',chapter.title+'. '+chapter.cues[cue].headline+'. '+chapter.cues[cue].text);
  }
};
