import {readFileSync,writeFileSync,readdirSync,mkdirSync,copyFileSync} from 'node:fs';
import {resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..'),base=resolve(root,'artifacts/a4');
const read=p=>JSON.parse(readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const cfg=read(resolve(base,'acceptance-runs.json')),gate=read(resolve(base,'final-gate.json'));
if(gate.status!=='ENGINEERING_PASS')throw Error('Collect only a completed same-source gate');
const dest=resolve(base,'dual-provider-evidence'),files=[];
// Retain only synthetic test evidence. Actual downloads, credentials, full server logs,
// JARs and build directories stay in ignored run/work locations.
const allowed=/^(result\.json|summary\.json|fault-result\.json|provider-state\.json|process-checkpoints\.jsonl|process-shutdown\.json|migration\.json|demo-provider-evidence\.json|provider-selector-mock\.json|provider-metadata-ui-mock\.json|markdown-export\.json|install-check\.json|console\.json|network\.json|snapshots\.json|commands\.json|.*\.png|(?:mock-)?TEST-.*\.xml|com\.yupi\..*\.txt)$/;
for(const [name,entry] of Object.entries(cfg.runs)){
 const dir=resolve(root,entry.out),target=resolve(dest,name);mkdirSync(target,{recursive:true});
 for(const item of readdirSync(dir,{withFileTypes:true}))if(item.isFile()&&allowed.test(item.name)){
  const source=resolve(dir,item.name),path=resolve(target,item.name);copyFileSync(source,path);
  files.push({path:path.slice(root.length+1).replaceAll('\\','/'),sha256:createHash('sha256').update(readFileSync(path)).digest('hex')});
 }
}
writeFileSync(resolve(base,'evidence-index.json'),JSON.stringify({at:new Date().toISOString(),sourceId:gate.sourceId,scope:'Dedicated synthetic test environments only; cloud boundaries Mock; paid API NOT_RUN',files},null,2)+'\n');
console.log(JSON.stringify({sourceId:gate.sourceId,evidenceFiles:files.length,directory:dest}));
