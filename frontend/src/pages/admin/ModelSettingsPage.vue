<template>
  <div class="model-settings">
    <h1>模型设置</h1>
    <a-alert type="info" show-icon message="文字与图片分别配置；默认图片为演示占位，不是免费 AI 生图。" description="Key 仅提交到后端加密保存，不会回显。格式检查不调用模型；实际测试可能收费，每日每管理员最多 5 次。" />
    <a-alert v-if="error" type="error" show-icon :message="error" class="gap" />
    <a-space wrap class="gap"><a-button @click="create('TEXT')">新增文字模型</a-button><a-button @click="create('IMAGE')">新增图片模型</a-button><a-button :loading="busy" @click="load">刷新</a-button></a-space>
    <div class="cards">
      <a-card v-for="row in items" :key="row.spec.id" :title="row.spec.name">
        <a-space wrap><a-tag>{{ row.spec.kind === 'TEXT' ? '文字' : '图片' }}</a-tag><a-tag>{{ row.spec.protocol }}</a-tag><a-tag v-if="row.isDefault" color="blue">默认</a-tag><a-tag>{{ row.enabled ? '启用' : '停用' }}</a-tag></a-space>
        <p class="break">{{ row.spec.model }} · v{{ row.spec.version }}</p><p class="break">{{ row.spec.endpoint || '本地演示/占位' }}</p>
        <p>Key：{{ row.configured ? '已配置（不回显）' : '未配置 / demo 无需' }}</p>
        <a-space wrap><a-button :disabled="busy" @click="edit(row)">编辑</a-button><a-button :disabled="busy || !row.enabled || row.spec.protocol === 'demo'" @click="probe(row)">实际测试（可能收费）</a-button></a-space>
      </a-card>
    </div>
    <a-card v-if="editing" :title="form.id ? '编辑配置：空 Key 保留原值' : '新增模型配置'" class="gap">
      <a-form layout="vertical" @submit.prevent="save">
        <div class="form-grid">
          <a-form-item label="显示名称"><a-input v-model:value="form.name" :maxlength="80" /></a-form-item>
          <a-form-item label="协议"><a-select v-model:value="form.protocol" :disabled="!!form.id" :options="protocols" @change="preset" /></a-form-item>
          <a-form-item v-if="form.protocol !== 'demo'" label="Base URL / 完整接口地址"><a-input v-model:value="form.endpoint" :disabled="!!form.id" autocomplete="off" /><small>保存时去除已识别的完整接口后缀；地址和协议固定后请新建配置切换。</small></a-form-item>
          <a-form-item v-if="form.protocol !== 'demo'" label="模型 ID"><a-input v-model:value="form.model" autocomplete="off" placeholder="填写自己账户已开通的模型或接入点 ID" /></a-form-item>
          <a-form-item v-if="form.protocol !== 'demo'" label="API Key（只写）"><a-input-password v-model:value="apiKey" autocomplete="new-password" :visibility-toggle="false" placeholder="新 Key；编辑时留空保留" /><a-checkbox v-if="form.id" v-model:checked="clearKey">明确清除旧 Key（须同时停用配置）</a-checkbox></a-form-item>
          <a-form-item label="超时（秒）"><a-input-number v-model:value="form.timeoutSeconds" :min="5" :max="180" /></a-form-item>
          <a-form-item label="最大输出 token（适用文字 / Gemini）"><a-input-number v-model:value="form.maxOutputTokens" :min="1" :max="8192" /></a-form-item>
          <a-form-item v-if="form.kind === 'IMAGE'" label="图片尺寸"><a-select v-model:value="form.imageSize" :options="['1K','2K','4K'].map(value => ({value,label:value}))" /><small>模型是否支持此规格以供应商文档为准；豆包默认 2K。</small></a-form-item>
        </div>
        <a-space wrap><a-checkbox v-model:checked="enabled">启用</a-checkbox><a-checkbox v-model:checked="makeDefault">设为默认{{ form.kind === 'TEXT' ? '文字' : '图片' }}</a-checkbox></a-space>
        <p>文字预设：DeepSeek 官方或火山方舟均选择兼容聊天协议；豆包生图必须选择图片协议，不能使用聊天接口。</p>
        <a-space wrap><a-button :disabled="busy" @click="validate">格式检查（不收费）</a-button><a-button type="primary" html-type="submit" :loading="busy">保存</a-button><a-button @click="close">取消</a-button></a-space>
      </a-form>
    </a-card>
    <a-card v-if="result" title="操作结果（脱敏）" class="gap"><pre>{{ result }}</pre></a-card>
    <p class="gap">停用阻止新任务选择；在途任务仍固定原配置。轮换 Key 应为同一供应商、同一账户权限；清除后未完成调用会报错，不自动换供应商。备份须同时保留数据库、图片和主密钥卷。</p>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { Modal } from 'ant-design-vue'
import request from '@/request'
interface Spec { id: string; version: number; name: string; kind: string; protocol: string; endpoint: string; model: string; timeoutSeconds: number; maxOutputTokens: number; imageSize: string; aspectRatio: string }
interface Row { spec: Spec; configured: boolean; enabled: boolean; isDefault: boolean }
const blank = (kind = 'TEXT'): Spec => ({id:'',version:0,name:'',kind,protocol:kind==='TEXT'?'compatible':'demo',endpoint:kind==='TEXT'?'https://ark.cn-beijing.volces.com/api/v3':'',model:kind==='TEXT'?'':'demo',timeoutSeconds:60,maxOutputTokens:2000,imageSize:'2K',aspectRatio:'1:1'})
const items=ref<Row[]>([]), form=reactive<Spec>(blank()), apiKey=ref(''), clearKey=ref(false), enabled=ref(true), makeDefault=ref(false), editing=ref(false), busy=ref(false), error=ref(''), result=ref('')
let csrf=''
const protocols=computed(()=> (form.kind==='TEXT'?[['compatible','DeepSeek / 火山方舟兼容聊天'],['dashscope','百炼 DashScope 原生']]:[['demo','演示占位（无 Key）'],['doubao','豆包 / Seedream 图片'],['gemini','Gemini 原生图片']]).map(([value,label])=>({value,label})))
async function post(path:string, body:unknown){const r=await request.post('/admin/models/'+path,body,{headers:{'X-Model-CSRF':csrf},timeout:190000});if(r.data.code!==0)throw new Error(r.data.message||'操作失败');return r.data.data}
function fail(e:unknown){error.value=e instanceof Error && !('isAxiosError' in e)?e.message:'请求失败；请检查同源地址、登录状态、迁移和后端脱敏日志。'}
async function load(){try{error.value='';const r=await request.get('/admin/models');if(r.data.code!==0)throw new Error(r.data.message);items.value=r.data.data.items;csrf=r.data.data.csrf}catch(e){fail(e)}}
function create(kind:string){Object.assign(form,blank(kind));apiKey.value='';clearKey.value=false;enabled.value=true;makeDefault.value=false;editing.value=true}
function edit(row:Row){Object.assign(form,row.spec);apiKey.value='';clearKey.value=false;enabled.value=!!row.enabled;makeDefault.value=row.isDefault;editing.value=true}
function close(){apiKey.value='';editing.value=false}
function preset(){form.endpoint=({compatible:'https://ark.cn-beijing.volces.com/api/v3',dashscope:'https://dashscope.aliyuncs.com',doubao:'https://ark.cn-beijing.volces.com/api/v3',gemini:'https://generativelanguage.googleapis.com',demo:''} as Record<string,string>)[form.protocol]||'';form.model=form.protocol==='demo'?'demo':''}
async function validate(){try{const r=await post('validate',{...form});form.endpoint=r.spec.endpoint;result.value=r.message}catch(e){fail(e)}}
async function doSave(){busy.value=true;error.value='';try{await post('save',{spec:{...form},apiKey:apiKey.value,clearKey:clearKey.value,enabled:enabled.value,makeDefault:makeDefault.value});close();await load();result.value='已保存；仅新任务使用新的默认配置，账户实际可用性尚未验证。'}catch(e){fail(e)}finally{apiKey.value='';busy.value=false}}
function save(){if(clearKey.value)Modal.confirm({title:'清除 Key 会阻止在途任务后续模型调用，确认清除？',onOk:doSave});else void doSave()}
function probe(row:Row){Modal.confirm({title:'实际测试可能收费',content:row.spec.kind==='TEXT'?'提交一次最小文字请求，最多 16 输出 token；不自动重试。':'可能生成 1 张收费图片；不自动重试或切换供应商。',async onOk(){busy.value=true;const requestId=crypto.randomUUID();try{const r=await post('test',{id:row.spec.id,version:row.spec.version,requestId,consent:true});result.value=JSON.stringify({testRequestId:requestId,...r},null,2)}catch(e){fail(e);result.value='测试请求 '+requestId+' 的结果未确认。不要盲目再次点击；先核对供应商账单。'}finally{busy.value=false}}})}
onMounted(load)
onBeforeUnmount(()=>{apiKey.value='';csrf=''})
</script>
<style scoped>
.model-settings{max-width:1100px;margin:0 auto;padding:24px}.gap{margin-top:20px}.cards,.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;margin-top:20px}.break,pre{overflow-wrap:anywhere;white-space:pre-wrap}small{color:#777}p{line-height:1.7}@media(max-width:640px){.model-settings{padding:12px}.cards,.form-grid{grid-template-columns:1fr}}
</style>
