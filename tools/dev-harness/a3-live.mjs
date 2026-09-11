import { spawnSync } from 'node:child_process'
import { mkdirSync, writeFileSync, readFileSync, copyFileSync, readdirSync, existsSync } from 'node:fs'
import { resolve, dirname, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createHash } from 'node:crypto'
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..')
const mode=process.argv[2] || 'start'
const processTest=process.env.A3_PROCESS_ISOLATED === 'true'
function cmd(args,input) {
 const r=spawnSync('docker',args,{cwd:root,input,encoding:'utf8',windowsHide:true,timeout:120000,maxBuffer:8*1024*1024})
 if(r.status!==0)throw new Error('Docker command failed: '+args.slice(0,3).join(' ')+' '+r.stderr)
 return r.stdout.trim()
}
if(mode==='stop') {
 const infoPath=resolve(process.argv[3]||'');if(!infoPath.startsWith(resolve(root,'artifacts/a3/runs')+'/')&&!infoPath.startsWith(resolve(root,'artifacts/a3/runs')+'\\'))throw Error('Not an A2 artifact')
 const info=JSON.parse(readFileSync(infoPath,'utf8'))
 if(info.processTest)throw Error('Use a3-process-clean.mjs for the two-worker fault environment')
 if(!/^a3-live-[\dTZ-]+$/i.test(info.prefix))throw Error('Invalid container prefix')
 if(!resolve(info.work).startsWith(resolve(root,'tools/dev-harness/.work/a3-live-')))throw Error('Invalid workspace')
 writeFileSync(resolve(info.work,'target/a2-browser-stop'),'stop')
 for(let i=0;i<60;i++){if(cmd(['inspect',info.prefix+'-backend','--format','{{.State.Running}}'])==='false')break;await new Promise(r=>setTimeout(r,1000))}
 const logs=spawnSync('docker',['logs',info.prefix+'-backend'],{encoding:'utf8',windowsHide:true,maxBuffer:16*1024*1024});writeFileSync(resolve(info.out,'http-tests.txt'),logs.stdout+logs.stderr)
 info.backendExitCode=Number(cmd(['inspect',info.prefix+'-backend','--format','{{.State.ExitCode}}']))
 const reports=resolve(info.work,'target/surefire-reports');if(existsSync(reports))for(const file of readdirSync(reports).filter(p=>(p.includes('A3HttpIT')||p.includes('A2HttpIT'))&&(p.endsWith('.txt')||p.endsWith('.xml'))))copyFileSync(resolve(reports,file),resolve(info.out,file))
 for(const role of ['frontend','backend','worker2','provider','redis','mysql']) { const exists=spawnSync('docker',['inspect',info.prefix+'-'+role],{windowsHide:true,stdio:'ignore'});if(exists.status===0)cmd(['rm','-f',info.prefix+'-'+role]) }
 cmd(['network','rm',info.prefix+'-net']);info.cleanedUp=true;writeFileSync(infoPath,JSON.stringify(info,null,2));console.log('A3 live stopped; backend exit '+info.backendExitCode);process.exitCode=info.backendExitCode; 
} else if(mode==='start') {
 const stamp=new Date().toISOString().replace(/[:.]/g,'-'),prefix='a3-live-'+stamp
 const work=resolve(root,'tools/dev-harness/.work',prefix),out=resolve(root,'artifacts/a3/runs',stamp+'-live')
 mkdirSync(work,{recursive:true});mkdirSync(out,{recursive:true})
 const listing=spawnSync('git',['ls-files','--cached','--others','--exclude-standard','-z','--','pom.xml','src','frontend'],{cwd:root,encoding:'utf8',windowsHide:true})
 if(listing.status!==0)throw Error('Source listing failed')
 const manifest=[]
 for(const p of new Set(listing.stdout.split('\0').filter(Boolean))) {
  if(/application-local|(^|\/)\.env|(^|\/)env\.ts$|(^|\/)(node_modules|target|dist)\//.test(p)||!existsSync(resolve(root,p)))continue
  const dest=resolve(work,p);if(relative(work,dest).startsWith('..'))throw Error('Unsafe snapshot path')
  mkdirSync(dirname(dest),{recursive:true});copyFileSync(resolve(root,p),dest);manifest.push({path:p,sha256:createHash('sha256').update(readFileSync(dest)).digest('hex')})
 }
 copyFileSync(resolve(root,'tools/dev-harness/maven-settings.xml'),resolve(work,'a0-maven-settings.xml'))
 writeFileSync(resolve(work,'frontend/src/config/env.ts'),"export const API_BASE_URL = '/api'\n")
 writeFileSync(resolve(out,'source-manifest.json'),JSON.stringify(manifest,null,2))
 // Reuse only an installed test dependency tree with an identical lockfile; no user project node_modules.
 const prior=readdirSync(resolve(root,'tools/dev-harness/.work')).filter(p=>/^\d{4}-/.test(p)).sort().reverse().filter(p=>['a0','a1','a2'].some(stage=>{const log=resolve(root,'artifacts',stage,'runs',p,'npm-ci.txt');return existsSync(log)&&/added \d+ packages/.test(readFileSync(log,'utf8'))})).map(p=>resolve(root,'tools/dev-harness/.work',p,'frontend')).find(p=>existsSync(resolve(p,'node_modules/vite/bin/vite.js'))&&existsSync(resolve(p,'package-lock.json'))&&readFileSync(resolve(p,'package-lock.json')).equals(readFileSync(resolve(root,'frontend/package-lock.json'))))
 if(!prior)throw Error('Run a2 baseline first to install isolated frontend dependencies')
 const mysql=cmd(['image','inspect','mysql:8.0','--format','{{.Id}}']),redis=cmd(['image','inspect','redis:7-alpine','--format','{{.Id}}'])
 const maven='maven@sha256:65353f527c86cb23187c8233475713e15067e8d36220d18863c379680698fe85',node='node@sha256:c610fcdfb1d5b4740dd70c284ed3cb16bb857e0f7166196e36a5501df7a3aa32'
 cmd(['network','create','--internal',prefix+'-net'])
 cmd(['run','-d','--name',prefix+'-mysql','--network',prefix+'-net','--tmpfs','/var/lib/mysql','--memory=1g','--cpus=1','-e','MYSQL_ROOT_PASSWORD=a2-test-only',mysql])
 cmd(['run','-d','--name',prefix+'-redis','--network',prefix+'-net','--tmpfs','/data','--memory=256m',redis,'redis-server','--save','','--appendonly','no'])
 let ready=false
 for(let i=0;i<90;i++){const r=spawnSync('docker',['exec',prefix+'-mysql','mysql','-uroot','-pa2-test-only','-e','SELECT 1'],{encoding:'utf8',windowsHide:true,timeout:3000});if(r.status===0){ready=true;break};await new Promise(r=>setTimeout(r,1000))}
 if(!ready)throw Error('Isolated MySQL startup timeout')
 let sql=['create_table.sql','update_quota.sql','add_vip_payment.sql','add_phase_fields.sql','add_article_style.sql'].map(f=>readFileSync(resolve(root,'sql',f),'utf8')).join('\n')
 sql+="\nINSERT INTO article(taskId,userId,topic,content) VALUES ('migration-preserve',1,'synthetic','preserved synthetic data');\n"
 for(let i=0;i<2;i++)sql+=readFileSync(resolve(root,'sql/add_article_review.sql'),'utf8')+'\n'+readFileSync(resolve(root,'sql/add_article_intervention.sql'),'utf8')+'\n'+readFileSync(resolve(root,'sql/add_article_runtime.sql'),'utf8')+'\n'+readFileSync(resolve(root,'sql/add_local_image_storage.sql'),'utf8')+'\n'+readFileSync(resolve(root,'sql/add_image_providers.sql'),'utf8')+'\n'
 cmd(['exec','-i',prefix+'-mysql','mysql','-uroot','-pa2-test-only'],sql)
 writeFileSync(resolve(out,'migration.json'),JSON.stringify({status:'PASS',scope:'fresh isolated MySQL; A1 and A2 migrations applied twice; synthetic preservation asserted in HTTP test',mysqlImage:mysql},null,2))
 mkdirSync(resolve(work,'frontend/public'),{recursive:true});mkdirSync(resolve(work,'frontend/node_modules'),{recursive:true})
 writeFileSync(resolve(work,'frontend/public/a2-image.svg'),'<svg xmlns="http://www.w3.org/2000/svg" width="640" height="360"><rect width="640" height="360" fill="#e5f1e9"/><circle cx="320" cy="135" r="60" fill="#6e9c7c"/><path d="M100 300L220 180L320 290L430 180L540 300Z" fill="#a9cbb4"/><text x="320" y="335" text-anchor="middle" font-size="20" fill="#31523b">A2 fixed image fixture</text></svg>')
 writeFileSync(resolve(work,'a2-vite.config.mjs'),`import { defineConfig } from './frontend/node_modules/vite/dist/node/index.js';\nimport vue from './frontend/node_modules/@vitejs/plugin-vue/dist/index.mjs';\nexport default defineConfig({plugins:[vue()],cacheDir:'/work/vite-cache',resolve:{alias:{'@':'/work/frontend/src'}},server:{host:'0.0.0.0',port:5173,proxy:{'/api':{target:'http://${prefix}-backend:8567',changeOrigin:true}}}});`)
 cmd(['create','--name',prefix+'-frontend','--network','bridge','--memory=2g','--cpus=2','-p','127.0.0.1::5173','--mount',`type=bind,source=${work},target=/work`,'--mount',`type=bind,source=${resolve(prior,'node_modules')},target=/work/frontend/node_modules,readonly`,'-w','/work/frontend',node,'node','node_modules/vite/bin/vite.js','--config','/work/a2-vite.config.mjs','--configLoader','native'])
 cmd(['network','connect',prefix+'-net',prefix+'-frontend']);cmd(['start',prefix+'-frontend'])
 const frontPort=cmd(['port',prefix+'-frontend','5173/tcp']).split(':').at(-1)
 cmd(['run','-d','--name',prefix+'-backend','--network',prefix+'-net','--memory=3g','--cpus=2','--mount',`type=bind,source=${work},target=/work`,'--mount',`type=bind,source=${resolve(root,'tools/dev-harness/.cache/m2')},target=/root/.m2`,'-w','/work',
 ...(processTest?['-e','A3_PROCESS_ISOLATED=true','-e','A3_WORKER_ROLE=backend','-e',`A3_PROVIDER_URL=http://${prefix}-provider:3333`]:[]),'-e','A2_ISOLATED=true','-e','A2_BROWSER_HOLD=true','-e','A2_REVIEW_DELAY_MS=1200','-e',`A2_TEST_DB_URL=jdbc:mysql://${prefix}-mysql:3306/ai_passage_creator?useUnicode=true&characterEncoding=UTF-8`,'-e',`A2_REDIS_HOST=${prefix}-redis`,'-e',`A2_IMAGE_ORIGIN=http://127.0.0.1:${frontPort}`,maven,'mvn','-s','/work/a0-maven-settings.xml','-o','-B','-Darticle.runtime.enabled=true',...(processTest?['-Darticle.runtime.initial-dispatch-delay-ms=600000']:[]),processTest?'-Dtest=A3ProcessIT':'-Dtest=A3HttpIT','test'])
 if(processTest){
  copyFileSync(resolve(root,'tools/dev-harness/a3-provider.mjs'),resolve(work,'a3-provider.mjs'))
  cmd(['run','-d','--name',prefix+'-provider','--network',prefix+'-net','--mount',`type=bind,source=${work},target=/work`,'-e',`A3_IMAGE_ORIGIN=http://127.0.0.1:${frontPort}`,node,'node','/work/a3-provider.mjs'])
 }
 const backendPort=frontPort
 const info={processTest,prefix,work,out,frontend:`http://127.0.0.1:${frontPort}`,backend:`http://127.0.0.1:${backendPort}`,dependencySnapshot:prior,images:{mysql,redis,maven,node},cleanedUp:false}
 writeFileSync(resolve(out,'live.json'),JSON.stringify(info,null,2));console.log('A3 live environment: '+resolve(out,'live.json'));console.log('Wait for '+resolve(work,'target/a2-http-ready.json')+' before browser validation')
} else throw Error('Use start | stop <live.json>')
