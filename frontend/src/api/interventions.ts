import request from '@/request'
export interface ReviewIssue { type: string; severity: string; sectionId: string; reason: string; suggestedAction: string }
export interface ReviewResult { schemaVersion: number; decision: string; issues: ReviewIssue[] }
export interface DraftVersion { version: number; content: string; review?: ReviewResult }
export interface ReviewTrace { schemaVersion: number; status: string; currentVersion: number; maxRevisions: number; round?: number; roundStartVersion?: number; stopReason?: string; versions: DraftVersion[]; review?: ReviewResult; humanDecisions?: { version: number; userId: number; decidedAt: string; note: string }[] }
export interface ImageSlot { id: string; status: string; attempts: number; error?: string; requirement: { imageSource: string; sectionTitle?: string }; result?: { url: string; method: string; metadata?: { provider: string; model: string; requestId?: string; jobId?: string; usage?: Record<string, number> } } }
export interface InterventionView { enabled: boolean; taskId: string; articleStatus: string; phase: string; revision: number; reviewTrace?: ReviewTrace; media?: { slots: ImageSlot[] }; allowedActions: string[]; errorMessage?: string }
export interface InterventionRequest { requestId: string; action: string; expectedVersion: number; expectedRevision: number; content?: string; imageId?: string; note?: string; acknowledgeRisks?: boolean }
interface Envelope<T> { code: number; data: T; message: string }
export class InterventionError extends Error { constructor(public code: number, message: string) { super(message) } }
export async function fetchIntervention(taskId: string, signal?: AbortSignal) {
  const response = await request.get<Envelope<InterventionView>>('/article/' + encodeURIComponent(taskId) + '/interventions', { signal })
  if (response.data.code !== 0) throw new InterventionError(response.data.code, response.data.message)
  return response.data.data
}
export async function submitIntervention(taskId: string, body: InterventionRequest) {
  const response = await request.post<Envelope<{ requestId: string; status: string; replayed: boolean }>>('/article/' + encodeURIComponent(taskId) + '/interventions', body)
  if (response.data.code !== 0) throw new InterventionError(response.data.code, response.data.message)
  return response.data.data
}
