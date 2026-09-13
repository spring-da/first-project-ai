import type { FlowchartDraft } from '../types/flowcharts.ts'
import { validateDiagram } from './flowcharts.ts'
export interface LocalFlowchartDraft { id: string; ownerKey: string; documentId: string | null; creationKey: string; baseVersion: number | null; savedAt: string; draft: FlowchartDraft }
export function createFlowchartDraftStorage(factory: IDBFactory = indexedDB) {
  let database: IDBDatabase | null = null
  let opening: Promise<IDBDatabase> | null = null
  function open() {
    return opening ??= new Promise<IDBDatabase>((resolve, reject) => {
      const request = factory.open('devnest-flowchart-drafts-v1', 1)
      request.onupgradeneeded = () => { const store = request.result.createObjectStore('drafts', { keyPath: ['ownerKey', 'id'] }); store.createIndex('owner', 'ownerKey') }
      request.onsuccess = () => { database = request.result; database.onversionchange = () => database?.close(); resolve(database) }
      request.onerror = () => { opening = null; reject(new Error('浏览器无法打开流程图草稿存储。')) }
      request.onblocked = () => reject(new Error('草稿存储升级被其他窗口阻止，请关闭旧窗口后重试。'))
    })
  }
  async function operation<T>(mode: IDBTransactionMode, action: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
    const db = await open()
    return new Promise((resolve, reject) => {
      const transaction = db.transaction('drafts', mode), request = action(transaction.objectStore('drafts'))
      transaction.oncomplete = () => resolve(request.result)
      transaction.onerror = transaction.onabort = () => reject(new Error('本机草稿保存失败，请导出源文件备份后重试。'))
    })
  }
  return {
    async list(ownerKey: string): Promise<LocalFlowchartDraft[]> {
      const records = await operation('readonly', store => store.index('owner').getAll(ownerKey))
      return records.filter((r: LocalFlowchartDraft) => {
        try { return r.ownerKey === ownerKey && typeof r.id === 'string' && typeof r.creationKey === 'string' && Number.isFinite(Date.parse(r.savedAt)) && !!validateDiagram(r.draft.diagram) } catch { return false }
      }).sort((a, b) => b.savedAt.localeCompare(a.savedAt))
    },
    async write(record: LocalFlowchartDraft) {
      if (!record.ownerKey || !record.id) throw new Error('草稿缺少工作区或会话标识。')
      validateDiagram(record.draft.diagram)
      await operation('readwrite', store => store.put(structuredClone(record)))
    },
    async remove(ownerKey: string, id: string) { await operation('readwrite', store => store.delete([ownerKey, id])) },
    close() { database?.close(); database = null; opening = null },
  }
}
