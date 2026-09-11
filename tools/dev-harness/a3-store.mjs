import {spawnSync} from 'node:child_process';
import {mkdirSync,readFileSync,writeFileSync,copyFileSync,existsSync,readdirSync} from 'node:fs';
import {resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..'),stamp=new Date().toISOString().replace(/[:.]/g,'-'),prefix='a3-store-'+stamp.toLowerCase();
const out=resolve(root,'artifacts/a3/runs',stamp+'-store'),work=resolve(root,'tools/dev-harness/.work',prefix);mkdirSync(out,{recursive:true});mkdirSync(work,{recursive:true});
const log=[];let status='FAIL';
function cmd(args,input){const r=spawnSync('docker',args,{cwd:root,input,encoding:'utf8',windowsHide:true,timeout:600000,maxBuffer:16*1024*1024});log.push({at:new Date().toISOString(),args,exit:r.status,stdout:r.stdout,stderr:r.stderr});if(r.status!==0)throw Error(args.slice(0,3).join(' ')+': '+r.stderr);return r.stdout.trim()}
const wait=ms=>new Promise(r=>setTimeout(r,ms));
const maven='maven@sha256:65353f527c86cb23187c8233475713e15067e8d36220d18863c379680698fe85';
const listing=spawnSync('git',['ls-files','--cached','--others','--exclude-standard','-z','--','src','pom.xml'],{cwd:root,encoding:'utf8',windowsHide:true});if(listing.status!==0)throw Error('git listing');
const manifest=[];for(const p of new Set(listing.stdout.split('\0').filter(Boolean))){if(/application-local|(^|\/)\.env/.test(p)||!existsSync(resolve(root,p)))continue;const dest=resolve(work,p);mkdirSync(dirname(dest),{recursive:true});copyFileSync(resolve(root,p),dest);manifest.push({path:p,sha256:createHash('sha256').update(readFileSync(dest)).digest('hex')});}writeFileSync(resolve(out,'source-manifest.json'),JSON.stringify(manifest,null,2));copyFileSync(resolve(root,'tools/dev-harness/maven-settings.xml'),resolve(work,'settings.xml'));
function start(stage){return cmd(['run','-d','--name',prefix+'-'+stage,'--network',prefix+'-net','--memory=3g','--cpus=2','--mount',`type=bind,source=${work},target=/work`,'--mount',`type=bind,source=${resolve(root,'tools/dev-harness/.cache/m2')},target=/root/.m2`,'-w','/work','-e','A3_STORE_ISOLATED=true','-e','A3_PROCESS_STAGE='+stage,'-e',`A3_DB_URL=jdbc:mysql://${prefix}-mysql:3306/ai_passage_creator`,maven,'mvn','-s','/work/settings.xml','-o','-B','-Dtest=A3StoreIT','test']);}
try{
 cmd(['network','create','--internal',prefix+'-net']);cmd(['run','-d','--name',prefix+'-mysql','--network',prefix+'-net','--tmpfs','/var/lib/mysql','--memory=1g','-e','MYSQL_ROOT_PASSWORD=a3-test-only','mysql:8.0']);
 let ready=false;for(let i=0;i<90;i++){const r=spawnSync('docker',['exec',prefix+'-mysql','mysql','-uroot','-pa3-test-only','-e','SELECT 1'],{encoding:'utf8',windowsHide:true,timeout:3000});if(r.status===0){ready=true;break}await wait(1000)}if(!ready)throw Error('MySQL startup timeout');
 const sql=['create_table.sql','update_quota.sql','add_vip_payment.sql','add_phase_fields.sql','add_article_style.sql','add_article_review.sql','add_article_intervention.sql','add_article_runtime.sql','add_local_image_storage.sql','add_image_providers.sql'].map(f=>readFileSync(resolve(root,'sql',f),'utf8')).join('\n');cmd(['exec','-i',prefix+'-mysql','mysql','-uroot','-pa3-test-only'],sql);
 start('prepare');console.log('A3 store started: '+out);
 let barrier=false;for(let i=0;i<540;i++){if(existsSync(resolve(work,'target/a3-kill-ready'))){barrier=true;break}if(cmd(['inspect',prefix+'-prepare','--format','{{.State.Running}}'])==='false')break;await wait(1000)}
 writeFileSync(resolve(out,'prepare.txt'),cmd(['logs',prefix+'-prepare']));if(!barrier)throw Error('Checkpoint barrier not reached');
 cmd(['kill','--signal=KILL',prefix+'-prepare']);log.push({at:new Date().toISOString(),event:'SIGKILL after committed checkpoint, before completion'});
 await wait(16000);start('recover');const exit=cmd(['wait',prefix+'-recover']);writeFileSync(resolve(out,'recover.txt'),cmd(['logs',prefix+'-recover']));if(exit!=='0')throw Error('Recovery test failed: '+exit);
 for(const f of readdirSync(resolve(work,'target/surefire-reports')).filter(f=>f.includes('A3StoreIT')))copyFileSync(resolve(work,'target/surefire-reports',f),resolve(out,f));
 const report=readFileSync(resolve(out,'com.yupi.template.runtime.A3StoreIT.txt'),'utf8');if(!/Tests run: 1, Failures: 0, Errors: 0, Skipped: 0/.test(report))throw Error('No successful recovery test executed');status='PASS';
}catch(e){log.push({error:e.stack});console.error(e.message)}finally{
 for(const role of ['prepare','recover','mysql'])spawnSync('docker',['rm','-f',prefix+'-'+role],{windowsHide:true});spawnSync('docker',['network','rm',prefix+'-net'],{windowsHide:true});writeFileSync(resolve(out,'result.json'),JSON.stringify({status,scope:'Real MySQL and actual killed/restarted Java processes; RuntimeStore integration only, not full HTTP flow',log},null,2));console.log(status+': '+out);process.exitCode=status==='PASS'?0:1;
}
