import type { FlowchartCreate, FlowchartDocument, FlowchartDraft, FlowchartUpdate, FlowSaveMode } from '../types/flowcharts.ts'
import { copyDraft, flowchartFingerprint } from './flowcharts.ts'

interface Transport {
  create: (draft: FlowchartCreate) => Promise<FlowchartDocument>
  update: (id: string, draft: FlowchartUpdate) => Promise<FlowchartDocument>
  changed?: () => void
  saved?: (document: FlowchartDocument) => void
}
// This session owns only one editor. No response is allowed to replace newer input.
export class FlowchartSession {
  draft: FlowchartDraft
  document: FlowchartDocument | null
  readonly creationKey: string
  status: 'idle' | 'saving' | 'saved' | 'error' | 'conflict' = 'idle'
  error = ''
  private transport: Transport
  private baseline: string
  private running: Promise<void> | null = null
  private queued: FlowSaveMode | null = null
  private disposed = false
  constructor(draft: FlowchartDraft, document: FlowchartDocument | null, creationKey: string, transport: Transport) {
    this.draft = copyDraft(draft); this.document = document; this.creationKey = creationKey; this.transport = transport
    this.baseline = document ? flowchartFingerprint(copyDraft(document)) : ''
  }
  get dirty() { return flowchartFingerprint(this.draft) !== this.baseline }
  edit(draft: FlowchartDraft) { if (this.disposed) return; this.draft = copyDraft(draft); this.transport.changed?.() }
  save(mode: FlowSaveMode): Promise<void> {
    if (this.disposed) return Promise.resolve()
    if (this.status === 'conflict') return Promise.reject(new Error(this.error))
    if (this.running) { if (this.queued !== 'MANUAL') this.queued = mode; return this.running }
    this.running = this.run(mode).finally(() => { this.running = null })
    return this.running
  }
  private async run(initial: FlowSaveMode) {
    let mode: FlowSaveMode | null = initial
    while (mode && !this.disposed) {
      this.queued = null
      if (!this.dirty && mode === 'AUTO') return
      const submitted = copyDraft(this.draft)
      if (!submitted.title.trim()) { this.status = 'error'; this.error = '请输入流程图标题。'; this.transport.changed?.(); throw new Error(this.error) }
      this.status = 'saving'; this.error = ''; this.transport.changed?.()
      try {
        const saved = this.document
          ? await this.transport.update(this.document.id, { ...submitted, expectedVersion: this.document.version, saveMode: mode })
          : await this.transport.create({ ...submitted, creationKey: this.creationKey })
        if (this.disposed) return
        this.document = saved
        // A retried POST may return an earlier successful creation. Keep the local
        // graph and metadata; only accept the server's documented title trimming.
        if (this.draft.title === submitted.title && saved.title === submitted.title.trim()) this.draft.title = saved.title
        this.baseline = flowchartFingerprint(copyDraft(saved)); this.status = 'saved'
        this.transport.saved?.(saved)
      } catch (error) {
        if (this.disposed) return
        this.status = (error as { status?: number }).status === 409 ? 'conflict' : 'error'
        this.error = error instanceof Error ? error.message : '保存失败，请重试。'; this.queued = null
        throw error
      } finally { if (!this.disposed) this.transport.changed?.() }
      mode = this.queued
    }
  }
  dispose() { this.disposed = true; this.queued = null }
}
