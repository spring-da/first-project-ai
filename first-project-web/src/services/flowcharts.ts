import { apiRequest, jsonBody } from './api.ts'
import type { FlowchartCreate, FlowchartDocument, FlowchartRevision, FlowchartRevisionDetail, FlowchartSummary, FlowchartUpdate } from '../types/flowcharts.ts'
const root = '/flowcharts'
export const flowchartApi = {
  list: (q = '', signal?: AbortSignal) => apiRequest<FlowchartSummary[]>(`${root}${q ? `?q=${encodeURIComponent(q)}` : ''}`, { signal, cache: 'no-store' }),
  get: (id: string, signal?: AbortSignal) => apiRequest<FlowchartDocument>(`${root}/${id}`, { signal, cache: 'no-store' }),
  create: (draft: FlowchartCreate, signal?: AbortSignal) => apiRequest<FlowchartDocument>(root, { method: 'POST', body: jsonBody(draft), signal, timeoutMs: 30000 }),
  update: (id: string, draft: FlowchartUpdate, signal?: AbortSignal) => apiRequest<FlowchartDocument>(`${root}/${id}`, { method: 'PUT', body: jsonBody(draft), signal, timeoutMs: 30000 }),
  trash: () => apiRequest<FlowchartSummary[]>(`${root}/trash`, { cache: 'no-store' }),
  remove: (id: string) => apiRequest<void>(`${root}/${id}`, { method: 'DELETE' }),
  restore: (id: string) => apiRequest<FlowchartDocument>(`${root}/${id}/restore`, { method: 'POST' }),
  permanent: (id: string) => apiRequest<void>(`${root}/${id}/permanent`, { method: 'DELETE' }),
  revisions: (id: string, page = 0) => apiRequest<FlowchartRevision[]>(`${root}/${id}/revisions?page=${page}`, { cache: 'no-store' }),
  revision: (id: string, revisionId: string) => apiRequest<FlowchartRevisionDetail>(`${root}/${id}/revisions/${revisionId}`, { cache: 'no-store' }),
  restoreRevision: (id: string, revisionId: string, expectedVersion: number) => apiRequest<FlowchartDocument>(`${root}/${id}/revisions/${revisionId}/restore`, { method: 'POST', body: jsonBody({ expectedVersion }) }),
}
