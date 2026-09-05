<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { Check, ChevronDown, Edit3, Folder, FolderOpen, FolderPlus, Library, MoreHorizontal, Search, Settings2, Trash2, X } from 'lucide-vue-next'
import type { KnowledgeDomain } from '../types'
import { fitDirectoryTabs } from '../utils/directoryTabs'

const props = defineProps<{ domains: KnowledgeDomain[]; modelValue: string; counts: Record<string, number> }>()
const emit = defineEmits<{ 'update:modelValue': [value: string]; create: []; edit: [domain: KnowledgeDomain]; remove: [domain: KnowledgeDomain] }>()
const items = computed(() => [
  { id: 'ALL', name: '全部知识', description: '查看所有内容' },
  ...props.domains,
  { id: 'UNASSIGNED', name: '未分类', description: '尚未归入目录的内容' },
])
const tray = ref<HTMLElement | null>(null)
const measure = ref<HTMLElement | null>(null)
const moreButton = ref<HTMLButtonElement | null>(null)
const panel = ref<HTMLElement | null>(null)
const menu = ref<HTMLElement | null>(null)
const searchInput = ref<HTMLInputElement | null>(null)
const visibleIds = ref<string[]>(['ALL'])
const visible = computed(() => items.value.filter((item) => visibleIds.value.includes(item.id)))
const hiddenCount = computed(() => items.value.length - visible.value.length)
const showAll = ref(false)
const query = ref('')
const filtered = computed(() => items.value.filter((item) => `${item.name} ${item.description}`.toLocaleLowerCase().includes(query.value.trim().toLocaleLowerCase())))
const context = ref<{ domain: KnowledgeDomain; x: number; y: number } | null>(null)
const panelPosition = ref({ top: 0, left: 0, width: 360, maxHeight: 460 })
let observer: ResizeObserver | undefined
let contextTrigger: HTMLElement | null = null

function calculate() {
  if (!tray.value || !measure.value) return
  const widths = Array.from(measure.value.children).map((element, index) => ({ id: items.value[index]!.id, width: element.getBoundingClientRect().width }))
  visibleIds.value = fitDirectoryTabs(widths, tray.value.clientWidth, props.modelValue)
  if (showAll.value) positionPanel()
}
function positionPanel() {
  if (!moreButton.value) return
  const rect = moreButton.value.getBoundingClientRect()
  const width = Math.min(380, window.innerWidth - 24)
  const below = window.innerHeight - rect.bottom - 20
  const above = rect.top - 20
  const height = Math.min(460, Math.max(below, above))
  panelPosition.value = { width, left: Math.max(12, Math.min(rect.right - width, window.innerWidth - width - 12)), top: below >= 240 || below >= above ? rect.bottom + 6 : Math.max(12, rect.top - height - 6), maxHeight: Math.max(120, height) }
}
function closePanel(restore = false) {
  showAll.value = false
  if (restore) moreButton.value?.focus()
}
async function toggleAll() {
  context.value = null
  if (showAll.value) { closePanel(true); return }
  query.value = ''
  positionPanel()
  showAll.value = true
  await nextTick()
  searchInput.value?.focus()
}
function select(id: string, restore = false) {
  emit('update:modelValue', id)
  closePanel(restore)
}
async function openContext(event: MouseEvent | KeyboardEvent, id: string) {
  const domain = props.domains.find((item) => item.id === id)
  if (!domain) return
  event.preventDefault()
  contextTrigger = event.currentTarget as HTMLElement
  const rect = contextTrigger.getBoundingClientRect()
  const x = event instanceof MouseEvent && event.button === 2 ? event.clientX : rect.left
  const y = event instanceof MouseEvent && event.button === 2 ? event.clientY : rect.bottom
  context.value = { domain, x: Math.max(8, Math.min(x, window.innerWidth - 216)), y: Math.max(8, Math.min(y, window.innerHeight - 166)) }
  await nextTick()
  menu.value?.querySelector<HTMLButtonElement>('button')?.focus()
}
function closeContext(restore = false) {
  context.value = null
  if (restore) contextTrigger?.focus()
}
function manage(action: 'edit' | 'remove') {
  const domain = context.value?.domain
  closeContext()
  closePanel()
  if (domain && action === 'edit') emit('edit', domain)
  else if (domain) emit('remove', domain)
}
function onOutside(event: PointerEvent) {
  const target = event.target as Node
  if (!menu.value?.contains(target)) closeContext()
  if (!panel.value?.contains(target) && !moreButton.value?.contains(target) && !menu.value?.contains(target)) closePanel()
}
function navigate(event: KeyboardEvent) {
  const root = event.currentTarget as HTMLElement
  const buttons = Array.from(root.querySelectorAll<HTMLButtonElement>('button:not(:disabled)'))
  const current = buttons.indexOf(document.activeElement as HTMLButtonElement)
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? buttons.length - 1 : event.key === 'ArrowDown' || event.key === 'ArrowRight' ? (current + 1) % buttons.length : event.key === 'ArrowUp' || event.key === 'ArrowLeft' ? (current - 1 + buttons.length) % buttons.length : -1
  if (next >= 0) { event.preventDefault(); buttons[next]?.focus() }
}
function onEscape(event: KeyboardEvent) {
  if (event.key !== 'Escape') return
  if (context.value) { event.stopPropagation(); closeContext(true) }
  else if (showAll.value) { event.stopPropagation(); closePanel(true) }
}
function onScroll(event: Event) {
  if (event.target instanceof Node && (panel.value?.contains(event.target) || menu.value?.contains(event.target))) return
  closeContext()
  closePanel()
}
function start() {
  if (observer) return
  observer = new ResizeObserver(calculate)
  if (tray.value) observer.observe(tray.value)
  if (measure.value) observer.observe(measure.value)
  document.addEventListener('pointerdown', onOutside)
  document.addEventListener('keydown', onEscape)
  window.addEventListener('scroll', onScroll, true)
  void nextTick(calculate)
}
function stop() {
  observer?.disconnect()
  observer = undefined
  document.removeEventListener('pointerdown', onOutside)
  document.removeEventListener('keydown', onEscape)
  window.removeEventListener('scroll', onScroll, true)
  closePanel()
  closeContext()
}
watch(() => [props.domains, props.counts, props.modelValue], () => { void nextTick(calculate) }, { deep: true })
onMounted(start)
onActivated(start)
onDeactivated(stop)
onBeforeUnmount(stop)
</script>

<template>
  <div class="directories">
    <div class="directories__label"><FolderOpen :size="18" /><span><strong>知识目录</strong><small>右键目录可管理</small></span></div>
    <div class="directories__navigation">
      <nav ref="tray" class="directories__tabs" aria-label="知识目录" @keydown="navigate">
        <button v-for="item in visible" :key="item.id" class="directory-tab" :class="{ active: modelValue === item.id }" type="button" :title="item.description ? `${item.name} · ${item.description}` : item.name" :aria-current="modelValue === item.id ? 'page' : undefined" @click="select(item.id)" @contextmenu="openContext($event, item.id)" @keydown.shift.f10="openContext($event, item.id)">
          <Library v-if="item.id === 'ALL'" :size="15" /><FolderOpen v-else-if="modelValue === item.id" :size="15" /><Folder v-else :size="15" /><strong>{{ item.name }}</strong><b>{{ counts[item.id] ?? 0 }}</b>
        </button>
      </nav>
      <button ref="moreButton" class="directories__more" :class="{ active: showAll }" type="button" :aria-label="`查看所有目录${hiddenCount ? `，另有 ${hiddenCount} 个目录` : ''}`" aria-haspopup="dialog" :aria-expanded="showAll" @click="toggleAll"><span v-if="hiddenCount">+{{ hiddenCount }}</span><ChevronDown :size="16" /></button>
    </div>
    <button class="directories__create" type="button" @click="emit('create')"><FolderPlus :size="16" /><span>新建目录</span></button>
    <div ref="measure" class="directories__measure" aria-hidden="true"><span v-for="item in items" :key="item.id" class="directory-tab"><Folder :size="15" /><strong>{{ item.name }}</strong><b>{{ counts[item.id] ?? 0 }}</b></span></div>
    <Teleport to="body">
      <div v-if="showAll" ref="panel" class="directory-panel" role="dialog" aria-label="所有知识目录" :style="{ top: `${panelPosition.top}px`, left: `${panelPosition.left}px`, width: `${panelPosition.width}px`, maxHeight: `${panelPosition.maxHeight}px` }" @focusout="!context && $event.relatedTarget && !panel?.contains($event.relatedTarget as Node) && closePanel()">
        <header><span><FolderOpen :size="16" />所有目录 <small>{{ domains.length }}</small></span><button type="button" aria-label="关闭目录列表" @click="closePanel(true)"><X :size="16" /></button></header>
        <label class="directory-panel__search"><Search :size="15" /><input ref="searchInput" v-model="query" aria-label="搜索目录" placeholder="搜索目录名称或说明…" @keydown.down.prevent="panel?.querySelector<HTMLButtonElement>('.directory-panel__item > button')?.focus()" /></label>
        <div class="directory-panel__list" @keydown="navigate">
          <div v-for="item in filtered" :key="item.id" class="directory-panel__item" :class="{ active: modelValue === item.id }" @contextmenu="openContext($event, item.id)">
            <button type="button" @click="select(item.id, true)" @keydown.shift.f10="openContext($event, item.id)"><Folder :size="16" /><span><strong>{{ item.name }}</strong><small>{{ item.description || '暂无说明' }}</small></span><b>{{ counts[item.id] ?? 0 }}</b><Check v-if="modelValue === item.id" :size="14" /></button>
            <button v-if="domains.some(domain => domain.id === item.id)" class="directory-panel__manage" type="button" :aria-label="`管理目录：${item.name}`" @click="openContext($event, item.id)"><MoreHorizontal :size="17" /></button>
          </div>
          <p v-if="!filtered.length">没有匹配的目录</p>
        </div>
        <footer><button type="button" @click="closePanel(); emit('create')"><FolderPlus :size="15" />新建目录</button><span>右键或点 ··· 管理目录</span></footer>
      </div>
      <div v-if="context" ref="menu" class="directory-context" role="menu" :aria-label="`管理目录：${context.domain.name}`" :style="{ left: `${context.x}px`, top: `${context.y}px` }" @keydown="navigate" @keydown.tab="closeContext()">
        <strong><Settings2 :size="13" />{{ context.domain.name }}</strong>
        <button type="button" role="menuitem" @click="manage('edit')"><Edit3 :size="15" />编辑目录</button>
        <button type="button" role="menuitem" class="danger" @click="manage('remove')"><Trash2 :size="15" />删除目录</button>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.directories { display: flex; align-items: center; gap: 12px; min-width: 0; padding: 12px 18px; border-top: 1px solid var(--border); background: color-mix(in srgb, var(--surface-sunken) 45%, var(--panel)); }
.directories__label { display: flex; align-items: center; gap: 8px; flex-shrink: 0; color: var(--accent); }
.directories__label span { display: grid; gap: 3px; }
.directories__label strong { color: var(--text); font-size: 11px; }
.directories__label small { color: var(--muted); font-size: 9px; }
.directories__navigation { display: flex; gap: 6px; flex: 1; min-width: 0; }
.directories__tabs { display: flex; flex: 1; align-items: center; gap: 6px; min-width: 0; overflow: hidden; padding-block: 3px; }
.directory-tab { display: inline-flex; flex: 0 0 auto; align-items: center; gap: 7px; max-width: min(220px, 100%); height: 38px; padding: 0 10px; color: var(--subtle); border: 1px solid var(--border); border-radius: 9px; background: var(--panel); font: inherit; cursor: pointer; }
.directory-tab svg { flex-shrink: 0; color: var(--muted); }
.directory-tab strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 11px; font-weight: 650; }
.directory-tab b { display: grid; flex-shrink: 0; place-items: center; min-width: 22px; height: 21px; padding: 0 5px; border-radius: 6px; background: var(--surface-sunken); color: var(--muted); font-size: 10px; }
.directory-tab:hover { border-color: var(--accent-border); color: var(--accent); }
.directory-tab.active { border-color: var(--accent-border); color: var(--accent); background: var(--accent-bg); box-shadow: inset 0 -2px var(--accent); }
.directory-tab.active svg { color: var(--accent); }
.directory-tab.active b { color: #fff; background: var(--accent); }
.directories__more, .directories__create { display: inline-flex; align-items: center; justify-content: center; gap: 6px; height: 38px; align-self: center; padding: 0 10px; border: 1px solid var(--border); border-radius: 9px; color: var(--subtle); background: var(--panel); font-size: 11px; cursor: pointer; flex-shrink: 0; }
.directories__more:hover, .directories__more.active { color: var(--accent); background: var(--accent-bg); border-color: var(--accent-border); }
.directories__create { border-color: var(--accent-border); color: var(--accent); background: var(--accent-bg); font-weight: 650; }
.directories__measure { position: fixed; top: 0; left: 0; height: 0; max-width: 100vw; display: flex; overflow: hidden; visibility: hidden; pointer-events: none; }
.directories__measure .directory-tab { max-width: 220px; }
.directory-panel, .directory-context { position: fixed; z-index: 110; padding: 8px; color: var(--text); border: 1px solid var(--border-strong); border-radius: 13px; background: var(--panel); box-shadow: 0 18px 60px rgba(0,0,0,.22); }
.directory-panel { display: flex; flex-direction: column; gap: 8px; }
.directory-panel header { display: flex; align-items: center; justify-content: space-between; padding: 3px 5px; font-size: 12px; font-weight: 700; }
.directory-panel header > span { display: flex; align-items: center; gap: 7px; }
.directory-panel header small { color: var(--muted); }
.directory-panel button, .directory-context button { display: flex; align-items: center; gap: 8px; padding: 7px; border: 0; border-radius: 7px; background: transparent; color: var(--subtle); cursor: pointer; font-size: 12px; text-align: left; }
.directory-panel button:hover, .directory-context button:hover { background: var(--accent-bg); color: var(--accent); }
.directory-panel__search { display: flex; align-items: center; gap: 8px; padding: 8px 10px; border: 1px solid var(--border); border-radius: 8px; color: var(--muted); background: var(--surface-sunken); }
.directory-panel__search input { width: 100%; min-width: 0; padding: 0; border: 0; outline: 0; color: var(--text); background: transparent; font-size: 12px; }
.directory-panel__search:focus-within { outline: 2px solid var(--accent-border); }
.directory-panel__list { min-height: 0; overflow: auto; }
.directory-panel__item { display: flex; align-items: center; border-radius: 8px; }
.directory-panel__item.active { background: var(--accent-bg); }
.directory-panel__item > button:first-child { flex: 1; min-width: 0; min-height: 48px; }
.directory-panel__item > button > span { flex: 1; min-width: 0; display: grid; gap: 4px; }
.directory-panel__item strong, .directory-panel__item small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.directory-panel__item small { color: var(--muted); font-size: 10px; }
.directory-panel__item svg { flex-shrink: 0; }
.directory-panel__item b { color: var(--muted); font-size: 11px; }
.directory-panel__manage { flex-shrink: 0; }
.directory-panel__list > p { padding: 16px; text-align: center; color: var(--muted); font-size: 12px; }
.directory-panel footer { display: flex; align-items: center; justify-content: space-between; gap: 6px; padding-top: 5px; border-top: 1px solid var(--border); }
.directory-panel footer span { color: var(--muted); font-size: 10px; }
.directory-context { z-index: 111; width: 208px; }
.directory-context > strong { display: flex; align-items: center; gap: 6px; padding: 8px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; color: var(--muted); font-size: 11px; }
.directory-context button { width: 100%; min-height: 35px; }
.directory-context button.danger { color: var(--danger); }
@media (max-width: 980px) {
  .directories { flex-wrap: wrap; gap: 6px; }
  .directories__navigation { order: 3; flex-basis: 100%; }
  .directories__create { margin-left: auto; }
}
@media (max-width: 600px) {
  .directories { padding: 8px 10px; }
  .directories__label small { display: none; }
  .directories__label strong { font-size: 12px; }
}
</style>
