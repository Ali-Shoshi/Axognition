const fs=require('node:fs'),path=require('node:path');
const catalog=JSON.parse(fs.readFileSync('app/src/main/assets/i18n/sq.json','utf8'));
function files(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?files(path.join(dir,e.name)):[path.join(dir,e.name)]);}
for(const file of files('app/src/main/java/com/example/axognition').filter(f=>f.endsWith('.kt')&&!/FractionsLesson|Language.kt/.test(f))){
 const s=fs.readFileSync(file,'utf8');const missing=[];
 for(const m of s.matchAll(/"((?:\\.|[^"\\])*)"/g)){
  let str=m[1];try{str=JSON.parse(m[0]);}catch{}
  if(catalog[str]||!/[A-Za-z]{3}/.test(str)||str.includes('$')||str.includes('://')||str.includes('\\')||str.includes('window.')||str.includes('BuildConfig')||str.includes('com.example'))continue;
  missing.push(str);
 }
 if(missing.length)console.log(path.basename(file)+': '+JSON.stringify([...new Set(missing)]));
}
