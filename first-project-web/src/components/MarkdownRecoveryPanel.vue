<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { FileText, History, LoaderCircle, RotateCcw, Trash2 } from 'lucide-vue-next'
import { useWorkspaceStore } from '../stores/workspace'
import { useNotificationStore } from '../stores/notifications'
import MarkdownContent from './MarkdownContent.vue'
import type { MarkdownDocument, MarkdownRevision, MarkdownRevisionDetail } from '../types'
import AppModal from './AppModal.vue'
import EmptyState from './EmptyState.vue'

const props = defineProps<{ mode: 'trash' | 'history'; document?: MarkdownDocument }>()
const emit = defineEmits<{ close: []; restored: [document: MarkdownDocument] }>()
const workspace = useWorkspaceStore()
const notifications = useNotificationStore()
const trash = ref<MarkdownDocument[]>([])
const revisions = ref<MarkdownRevision[]>([])
const selected = ref<MarkdownRevisionDetail | null>(null)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const page = ref(0)
const hasMore = ref(false)
const pendingPurge = ref<MarkdownDocument | null>(null)
const confirmingRestore = ref(false)
const confirming = computed(() => !!pendingPurge.value || confirmingRestore.value)
const actionNames: Record<MarkdownRevision['action'], string> = {
  CREATED: '首次保存', BASELINE: '升级前快照', UPDATED: '编辑保存', IMPORTED: '导入文章', RESTORED: '版本恢复', RECOVERED: '回收站恢复',
}
const time = (value: string) => new Date(value).toLocaleString('zh-CN')
const fail = (cause: unknown, reported = false) => {
  error.value = cause instanceof Error ? cause.message : '操作失败，请稍后重试。'
  if (!reported) notifications.notify(error.value, { type: 'error' })
}

async function load(more = false) {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    if (props.mode === 'trash') trash.value = await workspace.loadMarkdownTrash()
    else if (props.document) {
      const nextPage = more ? page.value + 1 : 0
      const items = await workspace.loadMarkdownHistory(props.document.id, nextPage)
      revisions.value = more ? [...revisions.value, ...items] : items
      page.value = nextPage
      hasMore.value = items.length === 20
    }
  } catch (cause) { fail(cause) }
  finally { loading.value = false }
}

async function selectRevision(revision: MarkdownRevision) {
  if (!props.document || busy.value || confirming.value) return
  busy.value = true
  error.value = ''
  selected.value = null
  try { selected.value = await workspace.loadMarkdownRevision(props.document.id, revision.id) }
  catch (cause) { fail(cause) }
  finally { busy.value = false }
}

async function restoreTrash(document: MarkdownDocument) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await workspace.restoreMarkdownDocument(document.id)
    trash.value = trash.value.filter((item) => item.id !== document.id)
    notifications.notify(`“${document.title}”已恢复到文章列表。`, { type: 'success' })
  } catch (cause) { fail(cause, true) }
  finally { busy.value = false }
}

async function purge(document: MarkdownDocument) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await workspace.purgeMarkdownDocument(document.id)
    trash.value = trash.value.filter((item) => item.id !== document.id)
    notifications.notify('文章及其历史版本已彻底删除。', { type: 'success' })
    pendingPurge.value = null
  } catch (cause) { fail(cause, true) }
  finally { busy.value = false }
}

async function restoreVersion() {
  if (!props.document || !selected.value || busy.value || props.document.version === undefined) return
  busy.value = true
  error.value = ''
  try {
    const document = await workspace.restoreMarkdownRevision(props.document.id, selected.value.id, props.document.version)
    emit('restored', document)
  } catch (cause) { fail(cause, true) }
  finally { busy.value = false }
}

onMounted(() => load())
</script>

<template>
  <AppModal :title="mode === 'trash' ? '回收站' : '文章历史版本'" :description="mode === 'trash' ? '已删除的文章会保留在这里，不会自动清空。' : `${document?.title ?? ''} · 每次保存保留快照，恢复旧版不会删除其他版本。`" wide @close="!busy && emit('close')">
    <Transition name="reveal"><section v-if="confirming" class="recovery-confirm" role="alertdialog" :aria-label="pendingPurge ? '确认彻底删除' : '确认恢复历史版本'">
      <strong>{{ pendingPurge ? `彻底删除“${pendingPurge.title}”？` : '将此历史版本恢复为当前文章？' }}</strong>
      <p>{{ pendingPurge ? '文章及所有历史版本都会永久删除，无法恢复。' : '恢复前的云端版本仍会保留在历史记录中。' }}</p>
      <div class="recovery-actions"><button class="button button-secondary" type="button" :disabled="busy" @click="pendingPurge = null; confirmingRestore = false">取消</button><button class="button button-primary" type="button" :disabled="busy" @click="pendingPurge ? purge(pendingPurge) : restoreVersion()">{{ pendingPurge ? '确认彻底删除' : '确认恢复' }}</button></div>
    </section></Transition>
    <div class="recovery-toolbar"><button type="button" class="button button-ghost" :disabled="busy || loading || confirming" @click="load()"><RotateCcw :size="14" />刷新列表</button></div>
    <p v-if="loading || busy" class="recovery-loading" role="status"><LoaderCircle :size="17" class="spin" />处理中…</p>
    <template v-if="mode === 'trash'">
      <TransitionGroup name="list" tag="div">
      <div v-for="item in trash" :key="item.id" class="recovery-row">
        <FileText :size="19" />
        <div class="recovery-info"><strong>{{ item.title }}</strong><small>{{ item.fileName }} · {{ time(item.deletedAt ?? item.updatedAt) }} 删除</small></div>
        <div class="recovery-actions"><button class="button button-secondary" type="button" :aria-label="`恢复文章 ${item.title}`" :disabled="busy || confirming" @click="restoreTrash(item)"><RotateCcw :size="15" />恢复</button><button class="button button-ghost danger" type="button" :aria-label="`彻底删除 ${item.title}`" :disabled="busy || confirming" @click="pendingPurge = item"><Trash2 :size="15" />彻底删除</button></div>
      </div>
      </TransitionGroup>
      <EmptyState v-if="!loading && !error && !trash.length" title="回收站是空的" description="删除的文章可以在这里找回。" />
    </template>
    <div v-else class="history-layout">
      <div class="history-list" aria-label="版本列表">
        <TransitionGroup name="list" tag="div">
        <button v-for="revision in revisions" :key="revision.id" class="history-item" :class="{ active: selected?.id === revision.id }" type="button" :disabled="busy || confirming" @click="selectRevision(revision)">
          <span><History :size="15" />版本 {{ revision.documentVersion + 1 }} <b v-if="revision.documentVersion === document?.version">当前</b></span>
          <strong>{{ actionNames[revision.action] }}</strong><small>{{ time(revision.createdAt) }}</small>
        </button>
        </TransitionGroup>
        <button v-if="hasMore" type="button" class="button button-ghost" :disabled="loading || busy" @click="load(true)">加载更多版本</button>
        <p v-if="!loading && !error && !revisions.length" class="recovery-hint">还没有历史版本，保存文章后会自动记录。</p>
      </div>
      <section class="history-preview">
        <template v-if="selected">
          <header><div><strong>{{ selected.title }}</strong><small>{{ selected.fileName }}</small></div><button type="button" class="button button-primary" :disabled="busy || confirming || selected.documentVersion === document?.version || document?.version === undefined" @click="confirmingRestore = true"><RotateCcw :size="15" />恢复此版本</button></header>
          <MarkdownContent :source="selected?.content ?? ''" tabindex="0" aria-label="历史版本预览" />
        </template>
        <div v-else class="history-placeholder"><History :size="28" /><p>选择一个版本，查看当时的内容</p></div>
      </section>
    </div>
  </AppModal>
</template>

<style scoped>
.recovery-row { display: flex; align-items: center; gap: 14px; padding: 18px 0; border-bottom: 1px solid var(--border); }
.recovery-row > svg { flex-shrink: 0; color: var(--muted); }
.recovery-info { flex: 1; min-width: 0; }
.recovery-info strong, .recovery-info small { display: block; overflow-wrap: anywhere; }
.recovery-info small, .history-item small, .recovery-hint { margin-top: 6px; color: var(--muted); font-size: var(--font-xs); }
.recovery-actions { display: flex; gap: 6px; }
.recovery-confirm { margin-bottom: 20px; padding: 18px; border: 1px solid var(--border-strong); border-radius: 10px; background: var(--surface-sunken); }
.recovery-confirm p { color: var(--muted); line-height: 1.6; }
.danger { color: var(--danger); }
.recovery-toolbar { display: flex; justify-content: flex-end; margin-bottom: 8px; }
.recovery-loading { display: flex; align-items: center; gap: 8px; color: var(--muted); }
.history-layout { display: grid; grid-template-columns: 190px minmax(0, 1fr); gap: 20px; }
.history-list { max-height: 55vh; overflow: auto; }
.history-item { display: flex; flex-direction: column; gap: 8px; width: 100%; padding: 14px; margin-bottom: 8px; text-align: left; border: 1px solid var(--border); border-radius: 10px; background: var(--panel); color: var(--text); cursor: pointer; }
.history-item.active { border-color: var(--brand); background: var(--brand-soft); }
.history-item span { display: flex; align-items: center; gap: 6px; font-size: var(--font-xs); }
.history-item b { color: var(--brand); margin-left: auto; }
.history-item strong { font-size: var(--font-sm); }
.history-preview { min-width: 0; border: 1px solid var(--border); border-radius: 10px; overflow: hidden; }
.history-preview header { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 15px; border-bottom: 1px solid var(--border); }
.history-preview header > div { min-width: 0; overflow-wrap: anywhere; }
.history-preview header small { display: block; color: var(--muted); margin-top: 5px; }
.history-preview header button { flex-shrink: 0; }
.history-preview .markdown-body { padding: 20px; max-height: 48vh; overflow: auto; overscroll-behavior: auto; }
.history-placeholder { display: grid; justify-items: center; align-content: center; min-height: 260px; color: var(--muted); padding: 20px; text-align: center; }
@media (max-width: 720px) {
  .recovery-row { flex-wrap: wrap; }
  .recovery-actions { width: 100%; justify-content: flex-end; }
  .history-layout { grid-template-columns: minmax(0, 1fr); }
  .history-list { max-height: 180px; }
  .history-preview header { flex-wrap: wrap; }
}
</style>
