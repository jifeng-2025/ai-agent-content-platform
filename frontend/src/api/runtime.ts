export interface RuntimeView {
 enabled: boolean; runId?: string; stateVersion: number; lastEventId: number; status: string; phase: string
 callsUsed: number; maxCalls: number; imageRetriesUsed: number; maxImageRetries: number
 remainingMs: number; reservedCostMicros: number; maxEstimatedCostMicros: number; actualCostMicros: number | null
 operations?: { requestId: string; action: string; status: string; phase: string; fence: number }[]
 mayStillCharge?: boolean; errorMessage?: string
}
async function request<T>(taskId: string, body?: object, signal?: AbortSignal): Promise<T> {
 const response = await fetch('/api/article/' + encodeURIComponent(taskId) + '/runtime', { method: body ? 'POST' : 'GET', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined, signal })
 const data = await response.json(); if (!response.ok || data.code !== 0) throw new Error(data.message || 'Runtime 请求失败')
 return data.data as T
}
export const fetchRuntime = (id: string, signal?: AbortSignal) => request<RuntimeView>(id, undefined, signal)
export const submitRuntime = (id: string, action: string, expectedStateVersion: number, acknowledgePossibleCharge = false) => request(id, { requestId: crypto.randomUUID(), action, expectedStateVersion, acknowledgePossibleCharge })
export const runtimeLabel = (status: string) => ({ RECOVERING: '恢复中', CANCELLED: '已取消，草稿保留', BUDGET_EXHAUSTED: '预算已耗尽', EXTERNAL_UNCERTAIN: '外部结果不确定，等待人工确认', TIMED_OUT: '执行期限已耗尽', PROCESSING: '可靠执行中', COMPLETED: '图文任务完成', NEEDS_REVIEW: '等待人工评审', FAILED: '执行失败，草稿保留', IMAGES_FAILED: '配图待重试' } as Record<string, string>)[status] || status
