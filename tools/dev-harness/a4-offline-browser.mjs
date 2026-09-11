import {createHash,randomUUID} from 'node:crypto';
import {spawn,spawnSync} from 'node:child_process';
import {readFileSync,writeFileSync,mkdirSync,existsSync} from 'node:fs';
import {resolve} from 'node:path';import {pathToFileURL} from 'node:url';
const info=JSON.parse(readFileSync(process.argv[2],'utf8'));
const stamp=new Date().toISOString().replace(/[:.]/g,'-');
let account='';
const fixturePrefix='browser-'+stamp.toLowerCase();
const out=resolve(info.out,'browser-'+stamp);mkdirSync(out,{recursive:true});
const profile=resolve(info.work,'chrome-profile-'+stamp);mkdirSync(profile,{recursive:true});
const chrome=spawn('C:/Program Files/Google/Chrome/Application/chrome.exe',['--headless=new','--remote-debugging-port=0','--remote-debugging-address=127.0.0.1','--user-data-dir='+profile,'--no-first-run','--no-default-browser-check','--disable-extensions','about:blank'],{windowsHide:true,stdio:'ignore'});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
let ws,sessionId,seq=0;let faultInjected=false;const pending=new Map(),events=[],network=[],errors=[],checks=[],cases=[];
function send(method,params={},session=sessionId){return new Promise((ok,no)=>{const id=++seq;const timer=setTimeout(()=>{pending.delete(id);no(Error('CDP timeout '+method))},30000);pending.set(id,{ok,no,timer});ws.send(JSON.stringify({id,method,params,...(session?{sessionId:session}:{})}));})}
async function evaluate(expression){expression=expression.replaceAll('a4browser',account);const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value}
async function wait(expression,label,timeout=30000){const end=Date.now()+timeout;while(Date.now()<end){if(await evaluate(expression)){checks.push(label);return;}await sleep(150)}throw Error('UI timeout '+label+' '+await evaluate('document.body.innerText.slice(-2000)'))}
const textHas=s=>`document.body.innerText.includes(${JSON.stringify(s)})`;
async function clickText(s){const clicked=await evaluate(`(()=>{const x=[...document.querySelectorAll('button')].find(x=>x.innerText.replace(/\\s/g,'').includes(${JSON.stringify(s.replace(/\s/g,''))}));if(!x||x.disabled)return false;x.click();return true})()`);if(!clicked)throw Error('Button missing '+s)}
async function input(selector,value){await evaluate(`(()=>{const root=document.querySelector(${JSON.stringify(selector)});const x=root?.matches('input,textarea')?root:root?.querySelector('input,textarea');if(!x)throw Error('input missing');x.value=${JSON.stringify(value)};x.dispatchEvent(new Event('input',{bubbles:true}));x.dispatchEvent(new Event('change',{bubbles:true}))})()`)}
async function screenshot(name){if(await evaluate('location.pathname.startsWith("/article/")')){const snapshot=await evaluate('(async()=>{const p=location.pathname;return {article:await (await fetch("/api"+p)).json(),intervention:await (await fetch("/api"+p+"/interventions")).json()}})()');cases.push({name,...snapshot});}
await sleep(350);const p=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});writeFileSync(resolve(out,name+'.png'),Buffer.from(p.data,'base64'))}
async function navigate(path){await send('Page.navigate',{url:info.frontend+path});await wait('document.readyState === "complete"','loaded '+path,120000)}
try{
 const saved=JSON.parse(readFileSync(process.argv[3],'utf8'));const offline=pathToFileURL(resolve(saved.unpack,'index.html')).href;
 for(let i=0;i<120&&!existsSync(resolve(profile,'DevToolsActivePort'));i++)await sleep(250);
 const [port,path]=readFileSync(resolve(profile,'DevToolsActivePort'),'utf8').trim().split(/\r?\n/);
 ws=new WebSocket('ws://127.0.0.1:'+port+path);await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j});
 ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);if(p){clearTimeout(p.timer);pending.delete(m.id);m.error?p.no(Error(JSON.stringify(m.error))):p.ok(m.result)}return}events.push(m);if(m.method==='Fetch.requestPaused'){faultInjected=true;void send('Fetch.failRequest',{requestId:m.params.requestId,errorReason:'ConnectionClosed'}).then(()=>send('Fetch.disable'));return;}if(m.method==='Runtime.exceptionThrown')errors.push(m.params);if(m.method==='Runtime.consoleAPICalled'&&['error','warning'].includes(m.params.type))errors.push({type:m.params.type,args:m.params.args.map(x=>x.value||x.description)});if(m.method==='Network.responseReceived')network.push({url:m.params.response.url,status:m.params.response.status,type:m.params.type});if(m.method==='Network.loadingFailed')network.push({requestId:m.params.requestId,error:m.params.errorText,canceled:m.params.canceled})};
 const target=await send('Target.createTarget',{url:'about:blank'},null);sessionId=(await send('Target.attachToTarget',{targetId:target.targetId,flatten:true},null)).sessionId;
 await send('Page.enable');await send('Runtime.enable');await send('Network.enable');await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});



 await send('Network.setBlockedURLs',{urls:['http://*','https://*']});await send('Page.navigate',{url:offline});await wait('document.readyState === "complete"','offline HTML loaded');await wait('document.querySelectorAll("img").length>0 && [...document.querySelectorAll("img")].every(x=>x.naturalWidth>0)','extracted local images render without network');await screenshot('12-offline-desktop');await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});await screenshot('13-offline-mobile');if(errors.length||network.some(x=>x.error&&!x.canceled))throw Error('Offline console/network error');writeFileSync(resolve(out,'result.json'),JSON.stringify({status:'PASS',checks,errors,offline:true,scope:'Actual extracted ZIP opened as file URL with HTTP(S) blocked'},null,2));console.log('OFFLINE ZIP BROWSER PASS');

}catch(e){await screenshot('failure').catch(()=>{});writeFileSync(resolve(out,'result.json'),JSON.stringify({status:'FAIL',error:e.stack,checks,errors},null,2));console.error(e);process.exitCode=1}
finally{writeFileSync(resolve(out,'console.json'),JSON.stringify(errors,null,2));writeFileSync(resolve(out,'network.json'),JSON.stringify(network,null,2));writeFileSync(resolve(out,'snapshots.json'),JSON.stringify(cases,null,2));try{await send('Browser.close',{},null)}catch{}ws?.close();chrome.kill();console.log(out)}
