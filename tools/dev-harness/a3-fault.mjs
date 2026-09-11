import {spawnSync} from 'node:child_process';
import {readFileSync,writeFileSync,existsSync,unlinkSync,mkdirSync} from 'node:fs';
import {resolve} from 'node:path';
const info=JSON.parse(readFileSync(process.argv[2],'utf8'));if(!info.processTest||!/^a3-live-[\dTZ-]+$/i.test(info.prefix))throw Error('Requires an isolated A3 process environment');
const {prefix,work,out}=info;const prior=process.argv.includes('--resume')?JSON.parse(readFileSync(resolve(out,'fault-result.json'),'utf8')):null;const timeline=prior?.timeline||[],checks=prior?.checks||[];let cookie='';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
function cmd(args,input){const r=spawnSync('docker',args,{input,encoding:'utf8',windowsHide:true,timeout:120000,maxBuffer:16*1024*1024});if(r.status!==0)throw Error(args.slice(0,3).join(' ')+': '+r.stderr);return r.stdout.trim()}
function sql(q){return cmd(['exec','-i',prefix+'-mysql','mysql','-uroot','-pa2-test-only','--default-character-set=utf8mb4','--batch','--skip-column-names','ai_passage_creator'],q)}
function provider(path,body){const code=`const r=await fetch('http://localhost:3333${path}',${JSON.stringify({method:body?'POST':'GET',headers:{'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined})}); console.log(await r.text())`;return JSON.parse(cmd(['exec',prefix+'-provider','node','--input-type=module','-e',code]));}
async function api(method,path,body){timeline.push({at:new Date().toISOString(),method,path});const r=await fetch(info.backend+'/api'+path,{method,headers:{'Content-Type':'application/json',Cookie:cookie},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(15000)});const cookies=r.headers.getSetCookie();if(cookies.length)cookie=cookies.map(c=>c.split(';')[0]).join('; ');const data=await r.json();if(data.code!==0)throw Error(path+' '+JSON.stringify(data));return data.data;}
async function wait(fn,label,ms=180000){const end=Date.now()+ms;while(Date.now()<end){if(await fn()){timeline.push({at:new Date().toISOString(),event:label});return}await sleep(500)}throw Error('Timeout '+label)}
function check(condition,label){if(!condition)throw Error(label);checks.push(label);console.log('PASS '+label)}
function saveLogs(role,suffix){writeFileSync(resolve(out,`process-${role}-${suffix}.txt`),cmd(['logs',prefix+'-'+role]));}
let generation=0;
async function launch(role,restart=false){
 if(restart){saveLogs(role,'before-kill-'+generation);cmd(['kill','--signal=KILL',prefix+'-'+role]);timeline.push({at:new Date().toISOString(),event:'SIGKILL',role,generation});cmd(['rm',prefix+'-'+role]);}
 const ready=resolve(work,'target/a3-process-ready-'+role+'.json');if(existsSync(ready))unlinkSync(ready);
 cmd(['run','-d','--name',prefix+'-'+role,'--network',prefix+'-net','--memory=3g','--cpus=2','--mount',`type=bind,source=${work},target=/work`,'--mount',`type=bind,source=${resolve(work,'../../.cache/m2')},target=/root/.m2`,'-w','/work','-e','A3_PROCESS_ISOLATED=true','-e','A3_WORKER_ROLE='+role,'-e',`A3_PROVIDER_URL=http://${prefix}-provider:3333`,'-e',`A2_TEST_DB_URL=jdbc:mysql://${prefix}-mysql:3306/ai_passage_creator?useUnicode=true&characterEncoding=UTF-8`,'-e',`A2_REDIS_HOST=${prefix}-redis`,info.images.maven,'mvn','-s','/work/a0-maven-settings.xml','-o','-B','-Darticle.runtime.enabled=true','-Dtest=A3ProcessIT','test']);
 generation++;await wait(()=>existsSync(ready),role+' JVM ready after restart',360000);
}
function rows(id){if(!/^[a-f0-9]{32}$/.test(id))throw Error('Unexpected task id');return JSON.parse(sql(`SELECT JSON_OBJECT('status',status,'phase',phase) FROM article WHERE taskId='${id}';`))}
async function create(topic){return api('POST','/article/create',{topic,enabledImageMethods:['PEXELS'],requestId:crypto.randomUUID()})}
try{
 await wait(()=>existsSync(resolve(work,'target/a3-process-ready-backend.json')),'initial JVM ready',540000);
 await wait(async()=>{try{return (await fetch(info.frontend)).ok}catch{return false}},'isolated frontend reachable',120000);
 // The test context may publish its ready marker before lazy Redis/auth initialization. Probe read-only readiness; business requests retain 15s deadlines.
 await wait(async()=>{try{const r=await fetch(info.backend+'/api/user/get/login',{signal:AbortSignal.timeout(5000)});const x=await r.json();return r.ok&&[0,40100].includes(x.code)}catch{return false}},'authentication HTTP readiness',90000);
 if(sql("SELECT COUNT(*) FROM user WHERE userAccount='a3process'") === '0') await api('POST','/user/register',{userAccount:'a3process',userPassword:'a3-local-only',checkPassword:'a3-local-only'});await api('POST','/user/login',{userAccount:'a3process',userPassword:'a3-local-only'});
 let id;
 if(prior){id=sql("SELECT taskId FROM article WHERE topic='QUEUE_RECOVERY' AND status='COMPLETED' ORDER BY id DESC LIMIT 1");timeline.push({at:new Date().toISOString(),event:'resume after explicit mysql UTF-8 fix'});}else{
 id=await create('QUEUE_RECOVERY');
 check(sql(`SELECT status FROM article_operation WHERE taskId='${id}'`) === 'QUEUED','commit before dispatch is durably QUEUED');check(Object.keys(provider('/state').jobs).length===0,'no provider effect before dispatch');
 await launch('backend',true);await wait(()=>rows(id).phase==='TITLE_SELECTING','queued creation recovered');
 const before=provider('/state');const titleId=Object.keys(before.jobs)[0];check(before.jobs[titleId].submitCount===1,'recovered title submitted once');
 await launch('backend',true);await sleep(1500);check(provider('/state').jobs[titleId].submitCount===1,'completed title reused after process restart');
 await api('POST','/article/confirm-title',{taskId:id,selectedMainTitle:'QUEUE_RECOVERY',selectedSubTitle:'隔离恢复测试',userDescription:'面向初学者'});await wait(()=>rows(id).phase==='OUTLINE_EDITING','outline checkpoint saved');
 provider('/control',{hold:'QUEUE_RECOVERY'});await api('POST','/article/confirm-outline',{taskId:id,outline:[{section:1,title:'开始行动',points:['选择任务']}]});
 await wait(()=>Number(sql(`SELECT COUNT(*) FROM article_call WHERE taskId='${id}' AND stepId='BODY' AND status='IN_FLIGHT'`))===1&&Object.values(provider('/state').jobs).some(j=>j.payload.includes('QUEUE_RECOVERY')&&j.payload.includes('outline')),'external body succeeded while local save pending');
 const bodyId=sql(`SELECT callId FROM article_call WHERE taskId='${id}' AND stepId='BODY'`);check(provider('/state').jobs[bodyId].status==='SUCCEEDED','supplier result durable before application receives it');
 await launch('backend',true);provider('/control',{hold:''});await wait(()=>rows(id).status==='COMPLETED','external job queried and full task completed');
 const recovered=provider('/state').jobs[bodyId];check(recovered.submitCount===1&&recovered.queryCount>=1,'external success recovered by query without another submission');
 }
 check(sql(`SELECT content FROM article WHERE taskId='${id}'`).includes('保留这一段'),'final persisted draft retained');
 await launch('worker2');provider('/control',{hold:'DOUBLE_WORKER'});const concurrent=await create('DOUBLE_WORKER');
 await wait(()=>Number(sql(`SELECT COUNT(*) FROM article_call WHERE taskId='${concurrent}'`))===1&&Object.values(provider('/state').jobs).some(j=>j.payload.includes('DOUBLE_WORKER')),'one worker has in-flight external job');
 const owner=sql(`SELECT owner FROM article_operation WHERE taskId='${concurrent}'`);const primary=JSON.parse(readFileSync(resolve(work,'target/a3-process-ready-backend.json'))).owner;const oldRole=owner===primary?'backend':'worker2';
 cmd(['pause',prefix+'-'+oldRole]);timeline.push({at:new Date().toISOString(),event:'pause old worker',oldRole});
 await wait(()=>Number(sql(`SELECT fence FROM article_operation WHERE taskId='${concurrent}'`))>=2,'other worker reclaimed expired lease',45000);
 await wait(()=>rows(concurrent).phase==='TITLE_SELECTING','new fence saved queried result');
 provider('/control',{hold:''});cmd(['unpause',prefix+'-'+oldRole]);await sleep(2500);
 check(rows(concurrent).phase==='TITLE_SELECTING','late old worker cannot replace recovered checkpoint');
 const dual=Object.values(provider('/state').jobs).filter(j=>j.payload.includes('DOUBLE_WORKER'));check(dual.length===1&&dual[0].submitCount===1,'two actual worker processes produce one provider submission');
 provider('/control',{hold:'CANCEL_RUNNING'});const cancelled=await create('CANCEL_RUNNING');await wait(()=>Number(sql(`SELECT COUNT(*) FROM article_call WHERE taskId='${cancelled}'`))===1,'cancel has in-flight call');
 const snapshot=await api('GET','/article/'+cancelled+'/runtime');await api('POST','/article/'+cancelled+'/runtime',{requestId:crypto.randomUUID(),action:'CANCEL',expectedStateVersion:snapshot.stateVersion});provider('/control',{hold:''});await sleep(1500);
 check(rows(cancelled).status==='CANCELLED','cancel terminal survives late HTTP response');check(sql(`SELECT COUNT(*) FROM article_call WHERE taskId='${cancelled}' AND status='SUCCEEDED'`)==='0','late cancelled result not committed');
 const replay=await fetch(info.backend+'/api/article/progress/'+cancelled+'?cursor=0',{headers:{Cookie:cookie},signal:AbortSignal.timeout(10000)}).then(r=>r.text());check(replay.includes('CANCELLED')&&!replay.includes('ALL_COMPLETE'),'SSE replay recovers cancellation terminal');
 writeFileSync(resolve(out,'fault-result.json'),JSON.stringify({status:'PASS',checks,timeline,taskIds:{id,concurrent,cancelled},scope:'actual killed/restarted Spring JVMs + two workers + real MySQL/Redis/HTTP; local query-capable mock supplier'},null,2));console.log('FAULT PASS '+checks.length);
}catch(e){writeFileSync(resolve(out,'fault-result.json'),JSON.stringify({status:'FAIL',error:e.stack,checks,timeline},null,2));console.error(e);process.exitCode=1}
finally{writeFileSync(resolve(out,'provider-state.json'),JSON.stringify(provider('/state'),null,2));for(const role of ['backend','worker2']){const r=spawnSync('docker',['inspect',prefix+'-'+role],{windowsHide:true,stdio:'ignore'});if(r.status===0){spawnSync('docker',['unpause',prefix+'-'+role],{windowsHide:true,stdio:'ignore'});saveLogs(role,'final');}}}
