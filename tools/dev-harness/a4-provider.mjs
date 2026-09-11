import http from 'node:http';import {readFileSync,writeFileSync,existsSync} from 'node:fs';
const file='/work/a4-provider-state.json',state=existsSync(file)?JSON.parse(readFileSync(file)):{jobs:{},scenario:'PASS',revisions:0,imageAttempts:0};
const save=()=>writeFileSync(file,JSON.stringify(state,null,2));
function output(payload){
 if(payload.startsWith('OPERATION:')){
  const data=JSON.parse(payload.split('DATA:\n')[1]||payload.slice(payload.indexOf('DATA:')+5));
  if(payload.startsWith('OPERATION: REVISION')){state.revisions++;return JSON.stringify({replacements:[{sectionId:'p2',text:state.scenario==='LIMIT'?'修订尝试'+state.revisions+'：仍需调整表达。':'先选择一个简单任务，记录操作步骤，用日常语言解释。'}]});}
  const edited=data.currentDraft.some(p=>p.text.includes('人工修改完成'));
  const needs=!edited&&((state.scenario==='REVISE'&&state.revisions===0)||state.scenario==='LIMIT');
  const manual=!edited&&state.scenario==='MANUAL';
  return JSON.stringify({schemaVersion:1,decision:manual?'NEEDS_REVIEW':needs?'REVISE':'PASS',issues:manual||needs?[{type:manual?'EVIDENCE_REQUIRED':state.revisions===1?'STRUCTURE':'AUDIENCE',severity:'ERROR',sectionId:'p2',reason:manual?'缺少外部证据，需要人工判断':'当前表达不适合新手，需要调整',suggestedAction:'只修改本段，保留其他段落'}]:[]});
 }
 const x=JSON.parse(payload);
 if(x.placeholderId){state.imageAttempts++;if(state.scenario==='IMAGE_FAIL'&&state.imageAttempts===1)return JSON.stringify({success:false,error:'固定图片服务失败',placeholderId:x.placeholderId});return JSON.stringify({success:true,url:state.origin+'/a4-image.svg',position:x.position,placeholderId:x.placeholderId,method:state.scenario==='DEGRADED'?'PLACEHOLDER':(state.imageSource||'PEXELS')});}
 if(x.content){state.imageSource=x.enabledImageMethods?.includes('NANO_BANANA')&&!x.enabledImageMethods?.includes('PEXELS')?'NANO_BANANA':'PEXELS';return JSON.stringify([{position:1,type:'section',sectionTitle:'开始行动',keywords:'fixed',imageSource:state.imageSource}]);}
 if(!x.mainTitle)return JSON.stringify([{mainTitle:x.topic,subTitle:'A4 固定响应演示'}]);
 if(!x.outline)return JSON.stringify({sections:[{section:1,title:'开始行动',points:['选择任务','记录步骤']}]});
 return '## 开始行动\n\n使用复杂参数矩阵完成任务优化。\n\n保留这一段，不要重写。';
}
http.createServer(async(req,res)=>{try{let text='';for await(const b of req)text+=b;let body=text?JSON.parse(text):{};const send=x=>{res.setHeader('Content-Type','application/json');res.end(JSON.stringify(x));};if(req.url==='/control'){state.origin=body.origin||state.origin;state.scenario=body.scenario||'PASS';state.revisions=0;state.imageAttempts=0;save();return send({ok:true});}if(req.url==='/state')return send(state);if(req.method==='GET'&&req.url.startsWith('/jobs/')){const j=state.jobs[req.url.slice(6)];if(j){j.queryCount++;save();}return send(j||{status:'NOT_FOUND'});}if(req.url==='/jobs'){let j=state.jobs[body.id];if(!j)j=state.jobs[body.id]={status:'SUCCEEDED',jobId:'a4-'+body.id,result:output(body.payload),actualCostMicros:0,submitCount:0,queryCount:0,payload:body.payload};j.submitCount++;save();return send(j);}res.statusCode=404;send({error:'not found'});}catch(e){res.statusCode=500;res.end(JSON.stringify({error:e.message}));}}).listen(3333,'0.0.0.0');
