const fs=require('node:fs'), vm=require('node:vm'), assert=require('node:assert/strict');
const ts=require(require.resolve('typescript',{paths:[process.cwd()]}));const js=ts.transpileModule(fs.readFileSync('src/utils/sse.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText;
const memory=new Map();class Source { constructor(url){this.url=url} closed=false;close(){this.closed=true} }
const exportsObject={};vm.runInNewContext(js,{exports:exportsObject,EventSource:Source,console,sessionStorage:{getItem:k=>memory.get(k),setItem:(k,v)=>memory.set(k,v)}});
let received=0,complete=0;let source=exportsObject.connectSSE('a3',{onMessage:()=>received++,onComplete:()=>complete++});
for(const seq of [1,1,2,2])source.onmessage({data:JSON.stringify({type:'REVIEW_UPDATED',seq})});assert.equal(received,2);
source.close();source=exportsObject.connectSSE('a3',{onMessage:()=>received++,onComplete:()=>complete++});assert.match(source.url,/cursor=2$/);
source.onmessage({data:JSON.stringify({type:'SNAPSHOT_REQUIRED',resetCursor:9})});source.onmessage({data:JSON.stringify({type:'CANCELLED',seq:10})});assert.equal(complete,1);assert.equal(source.closed,true);
for(const type of ['TIMED_OUT','BUDGET_EXHAUSTED','EXTERNAL_UNCERTAIN']){const stream=exportsObject.connectSSE(type,{onMessage:()=>{},onComplete:()=>{}});stream.onmessage({data:JSON.stringify({type,seq:1})});assert.equal(stream.closed,true);}
console.log('A3 SSE: 6 checks passed; real utility with simulated EventSource, NOT browser/network validation');
