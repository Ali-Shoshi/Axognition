exports.solveCheckpoint=async(page,chapter,afterAnswer=async()=>{})=>{
 for(let index=0;index<chapter.questions.length;index++) {
  await page.evaluate(index=>showQuestion(index),index);
  const q=chapter.questions[index];
  if(q.type==='choice') {
   const button=page.locator('#answers button').nth(q.answer);
   if(await button.isEnabled())await button.click();
  }else{
   const input=page.locator('#answers input');
   if(await input.isEnabled()){await input.fill(String(q.answer));await input.press('Enter');}
  }
  await afterAnswer(index);
  await page.locator('#continue').click();
 }
};
