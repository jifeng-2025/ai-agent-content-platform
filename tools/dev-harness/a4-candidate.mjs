import {spawnSync} from 'node:child_process';
import {mkdirSync,readFileSync,writeFileSync,copyFileSync,existsSync} from 'node:fs';
import {resolve,dirname} from 'node:path';import {fileURLToPath} from 'node:url';import {createHash} from 'node:crypto';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..'),base=resolve(root,'artifacts/a4');mkdirSync(base,{recursive:true});
const hash=p=>createHash('sha256').update(readFileSync(p)).digest('hex');
const files=spawnSync('git',['ls-files','--cached','--others','--exclude-standard','-z','--','src','frontend','sql','pom.xml','Dockerfile','.dockerignore','docker-compose.yml'],{cwd:root,encoding:'utf8',windowsHide:true});if(files.status)throw Error('Source enumeration failed');
const manifest=[...new Set(files.stdout.split('\0').filter(Boolean))].filter(p=>existsSync(resolve(root,p))&&!/application-local|(^|\/)\.env|(^|\/)env\.ts$|node_modules|\/dist\//.test(p)).sort().map(path=>({path,sha256:hash(resolve(root,path))}));
const mode=process.argv[2];if(mode==='freeze'){
 const stamp=new Date().toISOString().replace(/[:.]/g,'-'),snapshot=resolve(root,'tools/dev-harness/.work/a4-candidate-'+stamp);for(const row of manifest){const dest=resolve(snapshot,row.path);mkdirSync(dirname(dest),{recursive:true});copyFileSync(resolve(root,row.path),dest)}
 writeFileSync(resolve(base,'candidate.json'),JSON.stringify({createdAt:new Date().toISOString(),snapshot,sourceId:hashBuffer(JSON.stringify(manifest)),configurationDifference:"frontend/src/config/env.ts uses relative /api; isolated profiles and test-only HTTP provider, no user credentials",manifest},null,2));console.log(snapshot);
}else if(mode==='verify'){
 const old=JSON.parse(readFileSync(resolve(base,'candidate.json')));const changed=manifest.filter(r=>old.manifest.find(x=>x.path===r.path)?.sha256!==r.sha256);const removed=old.manifest.filter(r=>!manifest.some(x=>x.path===r.path));if(changed.length||removed.length)throw Error('Candidate changed: '+JSON.stringify({changed,removed}));console.log('PASS same candidate '+old.sourceId);
}else throw Error('freeze | verify');
function hashBuffer(x){return createHash('sha256').update(x).digest('hex')}
