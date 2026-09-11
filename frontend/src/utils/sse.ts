/**
 * SSE 工具函数
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */

export interface SSEMessage {
  type: string
  data?: any
  [key: string]: any
}

export interface SSEOptions {
  onMessage: (message: SSEMessage) => void
  onError?: (error: Event) => void
  onComplete?: () => void
}

/**
 * 建立 SSE 连接
 */
export const connectSSE = (taskId: string, options: SSEOptions): EventSource => {
  const { onMessage, onError, onComplete } = options

  const key = 'article-runtime-cursor:' + taskId
  let cursor = 0
  try { cursor = Number(sessionStorage.getItem(key) || 0) } catch { /* storage may be unavailable */ }
  const eventSource = new EventSource('/api/article/progress/' + encodeURIComponent(taskId) + (cursor > 0 ? '?cursor=' + cursor : ''))

  eventSource.onmessage = (event) => {
    try {
      const message: SSEMessage = JSON.parse(event.data)
      if (typeof message.resetCursor === 'number') cursor = message.resetCursor
      else if (typeof message.seq === 'number') { if (message.seq <= cursor) return; cursor = message.seq }
      onMessage(message)
      try { if (cursor > 0) sessionStorage.setItem(key, String(cursor)) } catch { /* GET remains available */ }
      
      // 检查是否完成
      if (message.terminal === true || ['CANCELLED','TIMED_OUT','BUDGET_EXHAUSTED','EXTERNAL_UNCERTAIN'].includes(message.type) || message.type === 'IMAGES_FAILED' || message.type === 'NEEDS_REVIEW' || message.type === 'ALL_COMPLETE' || message.type === 'ERROR') {
        eventSource.close()
        onComplete?.()
      }
    } catch (error) {
      console.error('SSE 消息解析失败:', error)
    }
  }

  eventSource.onerror = (error) => {
    console.error('SSE 连接错误:', error)
    onError?.(error)
    eventSource.close()
  }

  return eventSource
}

/**
 * 关闭 SSE 连接
 */
export const closeSSE = (eventSource: EventSource | null) => {
  if (eventSource) {
    eventSource.close()
  }
}
