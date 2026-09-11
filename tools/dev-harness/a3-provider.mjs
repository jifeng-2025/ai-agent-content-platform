import http from 'node:http';
import {readFileSync,writeFileSync,existsSync} from 'node:fs';
const file='/work/provider-state.json';const state=existsSync(file)?JSON.parse(readFileSync(file)): {jobs:{},hold:'',timeline:[]};
const held=[];function save(){writeFileSync(file,JSON.stringify(state,null,2))}
function output(payload){
 if(payload.startsWith('OPERATION: REVIEW'))return JSON.stringify({schemaVersion:1,decision:'PASS',issues:[]});
 const x=JSON.parse(payload);
 if(x.placeholderId)return JSON.stringify({success:true,url:process.env.A3_IMAGE_ORIGIN+'/a2-image.svg',position:x.position,placeholderId:x.placeholderId,method:'PEXELS'});
 if(x.content)return JSON.stringify([{position:1,type:'section',sectionTitle:'开始行动',keywords:'runtime-image',imageSource:'PEXELS'}]);
 if(!x.mainTitle)return JSON.stringify([{mainTitle:x.topic,subTitle:'隔离恢复测试'}]);
 if(!x.outline)return JSON.stringify({sections:[{section:1,title:'开始行动',points:['选择任务']}]});
 return '## 开始行动\n\n选择一个任务，记录自己的感受。\n\n保留这一段，不要重写。';
}
const server=http.createServer(async(req,res)=>{
 try{
 let text='';for await(const c of req)text+=c;const body=text?JSON.parse(text):{};
 const send=v=>{res.setHeader('Content-Type','application/json');res.end(JSON.stringify(v));};
 if(req.url==='/control'){state.hold=body.hold||'';state.timeline.push({at:new Date().toISOString(),control:body});save();if(!state.hold)for(const f of held.splice(0))f();return send({ok:true});}
 if(req.url==='/state')return send(state);
 if(req.method==='POST'&&req.url==='/jobs'){
  let job=state.jobs[body.id];if(!job)job=state.jobs[body.id]={status:'SUCCEEDED',jobId:'mock-'+body.id,result:output(body.payload),actualCostMicros:7,submitCount:0,queryCount:0,payload:body.payload};
  job.submitCount++;state.timeline.push({at:new Date().toISOString(),event:'external-success-before-response',id:body.id});save();
  if(state.hold&&body.payload.includes(state.hold)){held.push(()=>send(job));return;}return send(job);
 }
 if(req.method==='GET'&&req.url.startsWith('/jobs/')){const job=state.jobs[req.url.slice(6)];if(job){job.queryCount++;save();return send(job)}return send({status:'NOT_FOUND'});}
 res.statusCode=404;send({error:'not found'});
 }catch(e){res.statusCode=500;res.end(JSON.stringify({error:e.message}));}
});server.listen(3333,'0.0.0.0');
