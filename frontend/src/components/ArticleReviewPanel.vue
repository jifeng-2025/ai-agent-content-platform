<template>
  <section class="review-panel" aria-label="评审与人工处理">
    <div class="review-heading"><h2>评审与人工处理</h2><a-button size="small" :loading="loading" @click="refresh">刷新状态</a-button></div>
    <a-alert v-if="loadError" type="error" :message="loadError" show-icon />
    <template v-if="view">
      <p v-if="!view.enabled" class="muted">评审面板未启用，原文章仍可查看与导出。</p>
      <p v-else-if="!trace" class="muted">这篇文章暂无评审记录。旧文章正文保留，不推断它已通过评审。</p>
      <template v-else>
        <div class="status-row"><a-tag :color="trace.status === 'NEEDS_REVIEW' ? 'warning' : 'blue'">{{ stateLabel(trace.status) }}</a-tag><a-tag>{{ taskLabel(view.articleStatus) }}</a-tag><span>全局版本 v{{ trace.currentVersion }} · 第 {{ (trace.round || 0) + 1 }} 轮 · 本轮自动修订 {{ trace.currentVersion - (trace.roundStartVersion || 0) }}/2</span></div>
        <p class="muted" role="note">评审检查表达与质量风险，不代表事实已经验证。人工接受会保留原评审问题。</p>
        <p v-if="busy" role="status">{{ view.phase === 'IMAGE_GENERATING' ? '正在配图与合成，正文不会重新生成。' : stateLabel(view.phase) }} · {{ streamFailed ? '连接已断开，使用轮询更新' : '状态自动更新中' }}</p>
        <a-alert v-if="trace.stopReason" type="warning" :message="'停止原因：' + stopLabel(trace.stopReason)" show-icon class="block" />
        <a-alert v-if="view.errorMessage" type="error" :message="view.errorMessage" class="block" />
        <ul v-if="trace.review?.issues?.length" class="issues"><li v-for="(issue,i) in trace.review.issues" :key="i"><strong>{{ issue.sectionId }} · {{ issueLabel(issue.type) }}</strong><a-tag>{{ issue.severity === 'ERROR' ? '需处理' : '提示' }}</a-tag><p>{{ issue.reason }}</p><p class="muted">建议：{{ issue.suggestedAction }}</p></li></ul>
        <p v-else-if="trace.status === 'PASS'">文本评审通过，图文是否完成请查看任务状态。</p>
        <div v-if="trace.humanDecisions?.length" class="human-history"><strong>人工决定（原评审保留）</strong><p v-for="(decision,i) in trace.humanDecisions" :key="i">v{{ decision.version }} · {{ new Date(decision.decidedAt).toLocaleString() }} · {{ decision.note || '已确认风险并接受稿件' }}</p></div>
        <div class="actions" v-if="view.allowedActions.length">
          <a-button v-if="can('ACCEPT')" type="primary" :disabled="submitting" @click="openAccept">人工接受并继续配图</a-button>
          <a-button v-if="can('EDIT_REVIEW')" :disabled="submitting" @click="openEditor">编辑后重新评审</a-button>
          <a-button v-if="can('CONTINUE_IMAGES')" :loading="submitting" @click="submit('CONTINUE_IMAGES')">继续配图与合成</a-button>
        </div>
        <div class="version-tools"><label for="review-version">查看版本</label><select id="review-version" v-model.number="selectedVersion"><option v-for="v in trace.versions" :key="v.version" :value="v.version">v{{ v.version }}{{ v.version === trace.currentVersion ? '（当前）' : '' }}</option></select><a-switch v-model:checked="showDiff" checked-children="差异" un-checked-children="正文" :disabled="selectedVersion === 0" /></div>
        <p class="muted">{{ selected?.review ? '该版评审：' + stateLabel(selected.review.decision) : '该版本尚无评审结果' }}</p>
        <div v-if="showDiff && previous" class="diff" aria-label="版本差异"><div v-for="(line,i) in diff" :key="i" :class="line.kind"><span aria-hidden="true">{{ line.kind === 'added' ? '+ ' : line.kind === 'removed' ? '− ' : '  ' }}</span>{{ line.text || ' ' }}</div></div>
        <pre v-else class="draft" aria-label="版本正文">{{ selected?.content }}</pre>
        <div v-if="view.media?.slots.length" class="media-section"><h3>配图与单张重试</h3><div class="image-grid"><article v-for="slot in view.media.slots" :key="slot.id" class="image-slot"><img v-if="slot.result?.url" :src="slot.result.url" :alt="slot.requirement.sectionTitle || slot.id" /><div v-else class="missing-image">图片暂缺</div><strong>{{ slot.id }} · {{ slot.requirement.sectionTitle || '配图' }}</strong><p><a-tag :color="slot.status === 'SUCCESS' ? 'green' : 'warning'">{{ imageLabel(slot.status) }}</a-tag>尝试 {{ slot.attempts }} 次</p><p class="muted">请求来源 {{ slot.requirement.imageSource }} · 实际来源 {{ slot.result?.method || '无' }}</p><p v-if="slot.result && slot.result.method !== slot.requirement.imageSource" class="image-error">当前图片使用占位或降级来源，未保证图文语义匹配。</p><p v-if="slot.error" class="image-error">{{ slot.error }}</p><a-button v-if="can('RETRY_IMAGE')" :disabled="submitting" @click="submit('RETRY_IMAGE', slot.id)">重试 {{ slot.id }}</a-button></article></div></div>
      </template>
    </template>
    <a-modal v-model:open="acceptOpen" title="人工接受当前稿件" ok-text="确认接受并继续配图" :confirm-loading="submitting" :ok-button-props="{ disabled: !acknowledged }" @ok="submit('ACCEPT')"><p>接受 v{{ trace?.currentVersion }}。原评审及问题会保留；此操作只继续配图和合成，不重写正文。</p><a-checkbox v-model:checked="acknowledged">我理解事实尚未核查，并接受当前稿件的风险</a-checkbox><a-textarea v-model:value="note" placeholder="决定备注（可选）" :maxlength="500" class="block" /></a-modal>
    <a-modal v-model:open="editorOpen" title="编辑正文并开启新一轮评审" width="900px" ok-text="保存新版本并评审" :confirm-loading="submitting" @ok="submit('EDIT_REVIEW')"><p>将保留全部历史；新一轮最多自动修订两次。请保留确认的大纲标题。</p><a-textarea v-model:value="editedContent" aria-label="编辑正文" :rows="16" :maxlength="64000" show-count /></a-modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { fetchIntervention, submitIntervention, InterventionError, type InterventionView } from '@/api/interventions'
import { reviewDiff } from '@/utils/reviewDiff'
const props = defineProps<{ taskId: string }>()
const emit = defineEmits<{ updated: [view: InterventionView] }>()
const view = ref<InterventionView>(), loading = ref(false), submitting = ref(false), loadError = ref('')
const trace = computed(() => view.value?.reviewTrace)
const busy = computed(() => view.value?.articleStatus === 'PROCESSING')
const selectedVersion = ref(0), showDiff = ref(false)
const selected = computed(() => trace.value?.versions.find(v => v.version === selectedVersion.value))
const previous = computed(() => trace.value?.versions.find(v => v.version === selectedVersion.value - 1))
const diff = computed(() => reviewDiff(previous.value?.content || '', selected.value?.content || ''))
const acceptOpen = ref(false), editorOpen = ref(false), acknowledged = ref(false), note = ref(''), editedContent = ref('')
let acceptBase: { version: number; revision: number } | undefined
let editBase: { version: number; revision: number } | undefined
let timer: ReturnType<typeof setTimeout> | undefined, source: EventSource | undefined, controller: AbortController | undefined
let disposed = false, generation = 0
const streamFailed = ref(false)
const can = (action: string) => view.value?.allowedActions.includes(action)
function cleanup() { clearTimeout(timer); controller?.abort(); source?.close(); source = undefined }
function schedule(ms: number) { clearTimeout(timer); if (!disposed) timer = setTimeout(refresh, ms) }
async function refresh() {
  if (disposed || !props.taskId) return
  const id = ++generation
  controller?.abort(); controller = new AbortController(); loading.value = true
  try {
    const next = await fetchIntervention(props.taskId, controller.signal)
    if (disposed || id !== generation) return
    const wasCurrent = selectedVersion.value === trace.value?.currentVersion || !trace.value
    view.value = next; if (wasCurrent) selectedVersion.value = next.reviewTrace?.currentVersion || 0
    loadError.value = ''; emit('updated', next)
    if (next.articleStatus === 'PROCESSING' && next.enabled) {
      if (!source && !streamFailed.value) {
        source = new EventSource('/api/article/progress/' + encodeURIComponent(props.taskId))
        source.onmessage = event => { try { const data = JSON.parse(event.data); if (['ALL_COMPLETE','NEEDS_REVIEW','IMAGES_FAILED','ERROR'].includes(data.type)) { source?.close(); source = undefined }; schedule(150) } catch { schedule(1000) } }
        source.onerror = () => { source?.close(); source = undefined; streamFailed.value = true; schedule(1000) }
      }
      schedule(2000)
    } else { clearTimeout(timer); source?.close(); source = undefined }
  } catch (error) {
    if (id !== generation || controller.signal.aborted || disposed) return
    loadError.value = (error as Error).message || '状态读取失败，请刷新'
    if (!(error instanceof InterventionError && [40100,40101,40400].includes(error.code))) schedule(5000)
  } finally { if (id === generation) loading.value = false }
}
function openAccept() { acceptBase = { version: trace.value!.currentVersion, revision: view.value!.revision }; acknowledged.value = false; acceptOpen.value = true }
function openEditor() { editedContent.value = trace.value?.versions[trace.value.versions.length - 1]?.content || ''; editBase = { version: trace.value!.currentVersion, revision: view.value!.revision }; editorOpen.value = true }
async function submit(action: string, imageId?: string) {
  if (!view.value || !trace.value || submitting.value) return
  submitting.value = true
  const base = action === 'EDIT_REVIEW' ? editBase! : action === 'ACCEPT' ? acceptBase! : { version: trace.value.currentVersion, revision: view.value.revision }
  try {
    await submitIntervention(props.taskId, { requestId: crypto.randomUUID(), action, expectedVersion: base.version, expectedRevision: base.revision, content: action === 'EDIT_REVIEW' ? editedContent.value : undefined, imageId, note: note.value, acknowledgeRisks: acknowledged.value })
    acceptOpen.value = false; editorOpen.value = false; acknowledged.value = false; streamFailed.value = false
    message.success('操作已提交，状态将自动更新')
  } catch (error) { message.error((error as Error).message || '提交状态不确定，请刷新确认，勿重复创建任务') }
  finally { submitting.value = false; await refresh() }
}
const stateLabel = (s: string) => (({ REVIEWING: '评审中', REVISING: '局部修订中', NEEDS_REVIEW: '待人工处理', PASS: '文本评审通过', REVISE: '需要修订', HUMAN_ACCEPTED: '人工接受' } as Record<string, string>)[s] || s)
const taskLabel = (s: string) => (({ PROCESSING: '图文任务处理中', COMPLETED: '图文任务完成', IMAGES_FAILED: '配图待重试', NEEDS_REVIEW: '图文任务已暂停', FAILED: '任务失败，草稿保留' } as Record<string, string>)[s] || s)
const stopLabel = (s: string) => (({ HUMAN_REQUIRED: '需要人工判断或补充证据', REVISION_LIMIT: '本轮已达两次修订上限', REPEATED_ISSUES: '问题重复，未见改善', NO_PROGRESS: '修订没有实质变化', REVIEW_FAILURE: '评审模型或输出异常', REVISION_FAILURE: '修订模型或补丁异常' } as Record<string, string>)[s] || s)
const issueLabel = (s: string) => (({ AUDIENCE: '受众匹配', STRUCTURE: '结构', LENGTH: '篇幅', UNSUPPORTED_NUMBER: '数字缺少依据', UNSUPPORTED_ATTRIBUTION: '机构归因缺少依据', EVIDENCE_REQUIRED: '需要外部证据', OUTPUT_ERROR: '输出格式错误', MODEL_ERROR: '模型异常', MODEL_TIMEOUT: '模型超时', NO_PROGRESS: '没有改善' } as Record<string, string>)[s] || s)
const imageLabel = (s: string) => (({ SUCCESS: '图片可用', FAILED: '本次失败', DEGRADED: '降级 / 占位', PENDING: '等待处理' } as Record<string, string>)[s] || s)
watch(() => props.taskId, () => { cleanup(); generation++; view.value = undefined; streamFailed.value = false; void refresh() }, { immediate: true })
onBeforeUnmount(() => { disposed = true; generation++; cleanup() })
</script>

<style scoped>
.review-panel { margin: 24px 0; padding: 22px; border: 1px solid var(--color-border, #e5e7eb); border-radius: 14px; background: #f8fbf9; overflow-wrap: anywhere; }
.review-heading, .status-row, .actions, .version-tools { display:flex; align-items:center; flex-wrap:wrap; gap:10px; margin-bottom:16px; }
.review-heading { justify-content:space-between; }.review-heading h2 { margin:0; font-size:20px; }.muted { color:#5e6c65; font-size:13px; line-height:1.7; }.block { margin-top:14px; }
.issues { list-style:none; padding:0; }.issues li { background:white; border-left:3px solid #d99a26; padding:14px; margin:10px 0; border-radius:6px; }.issues p { margin:8px 0 0; }.issues strong { margin-right:8px; }.human-history { padding:14px; background:#eaf4ee; border-radius:8px; margin:16px 0; }
.version-tools { margin-top:22px; }.version-tools select { padding:7px 12px; border:1px solid #c9d5ce; border-radius:6px; background:white; }.draft,.diff { white-space:pre-wrap; overflow-wrap:anywhere; overflow:auto; max-height:420px; background:white; padding:16px; border:1px solid #e1e8e3; border-radius:8px; font:14px/1.8 ui-monospace,monospace; }.diff .added { background:#def5e4; color:#17592d; }.diff .removed { background:#ffe4e5; color:#862c33; }.image-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(220px,1fr)); gap:14px; }.image-slot { background:white; border:1px solid #dde6e0; border-radius:10px; padding:14px; }.image-slot img,.missing-image { width:100%; height:150px; object-fit:contain; border-radius:6px; margin-bottom:12px; background:#edf0ee; }.missing-image { display:grid; place-items:center; color:#6d7671; }.image-error { color:#986000; font-size:13px; }.image-slot p { margin:10px 0; }
@media(max-width:600px) { .review-panel { padding:14px; }.review-heading h2 { font-size:18px; }.actions { align-items:stretch; flex-direction:column; }.image-grid { grid-template-columns:1fr; }.draft,.diff { padding:10px; font-size:13px; }.status-row { align-items:flex-start; } }
</style>
