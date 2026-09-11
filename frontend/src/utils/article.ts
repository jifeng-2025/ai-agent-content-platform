/**
 * 文章相关工具函数
 */
import { STATUS_TEXT_MAP, STATUS_TAG_COLOR_MAP, STATUS_COLOR_MAP } from '@/constants/article'

/**
 * 获取状态文本
 * @param status 状态值
 */
export const getStatusText = (status: string): string => {
  return STATUS_TEXT_MAP[status] || status
}

/**
 * 获取状态标签颜色（用于 Ant Design Tag）
 * @param status 状态值
 */
export const getStatusTagColor = (status: string): string => {
  return STATUS_TAG_COLOR_MAP[status] || 'default'
}

/**
 * 获取状态颜色（用于自定义样式）
 * @param status 状态值
 */
export const getStatusColor = (status: string): string => {
  return STATUS_COLOR_MAP[status] || '#999'
}

/** All entry points use the authenticated persisted export. */
export async function downloadArticle(taskId: string, title: string, kind: 'md' | 'zip' | 'html' = 'zip') {
  const response = await fetch('/api/article/' + encodeURIComponent(taskId) + '/export.' + kind, { credentials: 'same-origin' })
  const mime = { md: 'text/markdown', zip: 'application/zip', html: 'text/html' }[kind]
  if (!response.ok || !response.headers.get('content-type')?.includes(mime)) throw new Error('导出失败，请检查登录状态和图片是否存在')
  const url = URL.createObjectURL(await response.blob()), link = document.createElement('a')
  link.href = url; link.download = (title || 'article').replace(/[\\/:*?"<>|]/g, '_') + '.' + kind
  link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
}
