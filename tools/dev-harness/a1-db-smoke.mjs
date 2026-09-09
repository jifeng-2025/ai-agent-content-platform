import { spawnSync } from 'node:child_process'
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
const root=resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const stamp=new Date().toISOString().replace(/[:.]/g,'-')
const name=`a1-migration-${stamp.toLowerCase()}`
const out=resolve(root,'artifacts/a1/runs',stamp)
mkdirSync(out,{recursive:true})
const logs=[]
function docker(args,input) {
  const r=spawnSync('docker',args,{input,encoding:'utf8',windowsHide:true,timeout:120000})
  logs.push({command:['docker',...args],exitCode:r.status,stdout:r.stdout,stderr:r.stderr})
  if(r.status!==0) throw new Error('Isolated MySQL command failed')
  return r.stdout
}
let status='FAIL'
try {
  // Resolve an already installed image; no pull, no existing container or volume access.
  const image=docker(['image','inspect','mysql:8.0','--format','{{.Id}}']).trim()
  docker(['run','-d','--name',name,'--network=none','--memory=1g','--cpus=2','--tmpfs','/var/lib/mysql',
    '-e','MYSQL_ROOT_PASSWORD=a1-test-only',image])
  let ready=false
  for(let attempt=0;attempt<60;attempt++) {
    const r=spawnSync('docker',['exec',name,'mysql','-uroot','-pa1-test-only','-e','SELECT 1'],{encoding:'utf8',windowsHide:true,timeout:3000})
    if(r.status===0){ready=true;break}
    await new Promise(r=>setTimeout(r,1000))
  }
  if(!ready)throw new Error('Isolated MySQL startup timeout')
  const ddl=readFileSync(resolve(root,'sql/add_article_review.sql'),'utf8')
  const sql=`CREATE DATABASE a1_isolated; USE a1_isolated;
CREATE TABLE article (taskId VARCHAR(64) PRIMARY KEY, content TEXT);
INSERT INTO article VALUES ('untouched','original synthetic row');
${ddl}
${ddl}
INSERT INTO article_review (taskId,reviewJson) VALUES ('fixed','{"schemaVersion":1,"currentVersion":0}');
INSERT INTO article_review (taskId,reviewJson) VALUES ('fixed','{"schemaVersion":1,"currentVersion":1}')
ON DUPLICATE KEY UPDATE reviewJson=VALUES(reviewJson),updatedTime=CURRENT_TIMESTAMP;
START TRANSACTION;
UPDATE article_review SET reviewJson='rollback';
ROLLBACK;
SELECT reviewJson FROM article_review WHERE taskId='fixed';
SELECT content FROM article WHERE taskId='untouched';
SELECT COUNT(*) FROM article_review;
`
  const result=docker(['exec','-i',name,'mysql','-uroot','-pa1-test-only','--batch','--skip-column-names'],sql)
  if(result.trim()!==['{"schemaVersion":1,"currentVersion":1}','original synthetic row','1'].join('\n'))throw new Error('Migration/upsert/rollback/protection assertion failed')
  status='PASS'
} catch(e){logs.push({error:e.message})}
finally {
  // Only the exact newly-created named container is removed; its data lived in tmpfs.
  const r=spawnSync('docker',['rm','-f',name],{encoding:'utf8',windowsHide:true,timeout:30000})
  logs.push({cleanupExitCode:r.status});if(r.status!==0)status='FAIL'
  writeFileSync(resolve(out,'migration-smoke.json'),JSON.stringify({status,scope:'isolated schema smoke; not Spring transaction integration',logs},null,2))
  console.log(`Migration ${status}: ${out}`)
  process.exitCode=status==='PASS'?0:1
}
