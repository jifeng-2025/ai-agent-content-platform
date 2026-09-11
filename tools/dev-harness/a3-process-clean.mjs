import {spawnSync} from 'node:child_process';import {readFileSync,writeFileSync} from 'node:fs';import {resolve} from 'node:path';
const file=resolve(process.argv[2]),info=JSON.parse(readFileSync(file,'utf8'));if(!info.processTest||!/^a3-live-[\dTZ-]+$/i.test(info.prefix))throw Error('Only isolated A3 process environment allowed');
const rows=[];function cmd(args){const r=spawnSync('docker',args,{encoding:'utf8',windowsHide:true,timeout:120000,maxBuffer:16*1024*1024});if(r.status!==0)throw Error(r.stderr);return r.stdout.trim()}
const snapshot=cmd(['exec',info.prefix+'-mysql','mysql','-uroot','-pa2-test-only','--default-character-set=utf8mb4','--batch','--skip-column-names','ai_passage_creator','-e',"SELECT JSON_OBJECT('taskId',taskId,'requestId',requestId,'status',status,'phase',phase,'fence',fence,'checkpoint',checkpointJson) FROM article_operation WHERE runId IS NOT NULL;"]);writeFileSync(resolve(info.out,'process-checkpoints.jsonl'),snapshot);
writeFileSync(resolve(info.work,'target/a3-process-stop'),'stop');
for(const role of ['backend','worker2']){
 const name=info.prefix+'-'+role;for(let i=0;i<60;i++){if(cmd(['inspect',name,'--format','{{.State.Running}}'])==='false')break;await new Promise(r=>setTimeout(r,1000))}
 writeFileSync(resolve(info.out,`process-${role}-shutdown.txt`),cmd(['logs',name]));rows.push({role,exit:Number(cmd(['inspect',name,'--format','{{.State.ExitCode}}']))});
}
for(const role of ['frontend','backend','worker2','provider','redis','mysql'])cmd(['rm','-f',info.prefix+'-'+role]);cmd(['network','rm',info.prefix+'-net']);info.cleanedUp=true;writeFileSync(file,JSON.stringify(info,null,2));writeFileSync(resolve(info.out,'process-shutdown.json'),JSON.stringify(rows,null,2));console.log('Isolated process environment cleaned; '+JSON.stringify(rows));
