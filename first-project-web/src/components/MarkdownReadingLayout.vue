<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { ArrowUp, ChevronDown, ListTree } from 'lucide-vue-next'
import { extractMarkdownHeadings } from '../utils/markdown'

const props = withDefaults(defineProps<{ source: string; enabled?: boolean }>(), { enabled: true })
const contentRoot = ref<HTMLElement | null>(null)
const navigationId = useId()
const expanded = ref(false)
const activeId = ref('')
const headings = computed(() => props.enabled ? extractMarkdownHeadings(props.source) : [])
const baseLevel = computed(() => Math.min(...headings.value.map(heading => heading.level)))
let elements: HTMLElement[] = []
let frame = 0
let resizeObserver: ResizeObserver | undefined

function updateActiveHeading() {
  frame = 0
  if (!elements.length) { activeId.value = ''; return }
  const offset = window.matchMedia('(max-width: 1199px)').matches ? 152 : 112
  let current = elements[0]!
  for (const element of elements) {
    if (element.getBoundingClientRect().top > offset) break
    current = element
  }
  // Short final sections cannot always reach the top of the viewport.
  const bottom = contentRoot.value?.getBoundingClientRect().bottom ?? Infinity
  if (window.scrollY > 0 && bottom <= window.innerHeight && elements.at(-1)!.getBoundingClientRect().top < window.innerHeight) {
    current = elements.at(-1)!
  }
  activeId.value = current.id
}

function scheduleUpdate() {
  if (!frame) frame = window.requestAnimationFrame(updateActiveHeading)
}

function collectHeadings() {
  const ids = new Set(headings.value.map(heading => heading.id))
  elements = Array.from(contentRoot.value?.querySelectorAll<HTMLElement>('.markdown-body :is(h1,h2,h3,h4,h5,h6)[id]') ?? [])
    .filter(element => ids.has(element.id))
  scheduleUpdate()
}

async function navigate(id?: string) {
  expanded.value = false
  // Wait for the mobile outline to collapse before measuring the destination.
  await nextTick()
  const target = id ? elements.find(element => element.id === id) : contentRoot.value
  if (!target) return
  target.setAttribute('tabindex', '-1')
  target.focus({ preventScroll: true })
  target.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' })
  if (id) activeId.value = id
}

watch(() => [props.source, props.enabled], () => {
  expanded.value = false
  collectHeadings()
}, { flush: 'post' })
onMounted(() => {
  collectHeadings()
  window.addEventListener('scroll', scheduleUpdate, { passive: true, capture: true })
  window.addEventListener('resize', scheduleUpdate, { passive: true })
  resizeObserver = new ResizeObserver(scheduleUpdate)
  if (contentRoot.value) resizeObserver.observe(contentRoot.value)
})
onBeforeUnmount(() => {
  window.removeEventListener('scroll', scheduleUpdate, true)
  window.removeEventListener('resize', scheduleUpdate)
  window.cancelAnimationFrame(frame)
  resizeObserver?.disconnect()
})
</script>

<template>
  <div class="reading-layout" :class="{ 'reading-layout--with-outline': enabled }">
    <aside v-if="enabled" class="reading-outline" aria-label="当前文章标题导航">
      <div class="reading-outline__heading"><ListTree :size="17" aria-hidden="true" /><h2>本文目录</h2><span>{{ headings.length }}</span></div>
      <button class="reading-outline__toggle" type="button" :aria-expanded="expanded" :aria-controls="navigationId" @click="expanded = !expanded">
        <ListTree :size="17" aria-hidden="true" /><span>本文目录</span><small>{{ headings.length }} 个标题</small><ChevronDown :size="16" :class="{ expanded }" aria-hidden="true" />
      </button>
      <div :id="navigationId" class="reading-outline__body" :class="{ 'is-expanded': expanded }">
        <nav v-if="headings.length" aria-label="文章标题导航">
          <ol>
            <li v-for="heading in headings" :key="heading.id">
              <button type="button" :class="{ active: activeId === heading.id }" :aria-current="activeId === heading.id ? 'location' : undefined" :style="{ '--heading-depth': Math.min(heading.level - baseLevel, 3) }" @click="navigate(heading.id)">{{ heading.text }}</button>
            </li>
          </ol>
        </nav>
        <p v-else class="reading-outline__empty">这篇文章暂无标题，直接阅读正文即可。</p>
        <button class="reading-outline__top" type="button" @click="navigate()"><ArrowUp :size="15" aria-hidden="true" />回到文章顶部</button>
      </div>
    </aside>
    <div ref="contentRoot" class="reading-layout__content"><slot /></div>
  </div>
</template>

<style scoped>
.reading-layout { min-width: 0; }
.reading-layout--with-outline { display: grid; grid-template-columns: minmax(0, 1fr) 208px; align-items: start; gap: clamp(24px, 2.5vw, 40px); }
.reading-layout__content { min-width: 0; grid-column: 1; grid-row: 1; scroll-margin-top: 100px; }
.reading-layout__content :deep(.markdown-body :is(h1,h2,h3,h4,h5,h6)) { scroll-margin-top: 112px; }
.reading-outline { position: sticky; top: 100px; grid-column: 2; grid-row: 1; min-width: 0; padding-left: 20px; border-left: 1px solid var(--border); }
.reading-outline__heading { display: flex; align-items: center; gap: 8px; padding: 5px 0 16px; }
.reading-outline__heading svg { color: var(--muted); }
.reading-outline__heading h2 { margin: 0; font-size: 14px; font-weight: 650; }
.reading-outline__heading > span { margin-left: auto; color: var(--muted); font-size: 12px; }
.reading-outline__toggle { display: none; }
.reading-outline nav { max-height: calc(100dvh - 230px); overflow-y: auto; overscroll-behavior: contain; padding: 3px; margin: -3px; scrollbar-width: thin; }
.reading-outline ol { margin: 0; padding: 0; list-style: none; }
.reading-outline li + li { margin-top: 3px; }
.reading-outline nav button { width: 100%; min-height: 38px; padding: 8px 10px 8px calc(10px + var(--heading-depth) * 12px); border: 0; border-left: 2px solid transparent; border-radius: 0 6px 6px 0; color: var(--subtle); background: transparent; font-size: 13px; line-height: 1.6; text-align: left; overflow-wrap: anywhere; cursor: pointer; transition: color var(--motion-fast), background var(--motion-fast); }
.reading-outline nav button:hover { color: var(--accent); background: var(--surface-raised); }
.reading-outline nav button.active { color: var(--accent); border-left-color: var(--accent); background: var(--accent-bg); font-weight: 650; }
.reading-outline__empty { margin: 0; color: var(--muted); font-size: 13px; line-height: 1.8; }
.reading-outline__top { display: flex; align-items: center; gap: 7px; min-height: 44px; width: 100%; margin-top: 16px; padding: 10px 0 0; color: var(--muted); border: 0; border-top: 1px solid var(--border); background: transparent; font-size: 12px; cursor: pointer; }
.reading-outline__top:hover { color: var(--accent); }
@media (max-width: 1199px) {
  .reading-layout--with-outline { display: block; }
  .reading-layout__content { grid-column: 1; grid-row: 2; scroll-margin-top: 150px; }
  .reading-layout__content :deep(.markdown-body :is(h1,h2,h3,h4,h5,h6)) { scroll-margin-top: 150px; }
  .reading-outline { z-index: 3; top: 84px; margin-bottom: 24px; padding: 0; border: 1px solid var(--border); border-radius: var(--radius-md); background: var(--panel); box-shadow: var(--shadow-sm); }
  .reading-outline__heading { display: none; }
  .reading-outline__toggle { display: flex; align-items: center; gap: 8px; width: 100%; min-height: 46px; padding: 10px 14px; border: 0; border-radius: inherit; color: var(--text); background: transparent; font-size: 14px; cursor: pointer; }
  .reading-outline__toggle > span { font-weight: 650; }
  .reading-outline__toggle small { margin-left: auto; color: var(--muted); font-size: 12px; }
  .reading-outline__toggle .expanded { transform: rotate(180deg); }
  .reading-outline__body { display: none; padding: 0 12px 12px; }
  .reading-outline__body.is-expanded { display: block; }
  .reading-outline nav { max-height: min(40dvh, 340px); }
  .reading-outline nav button { min-height: 44px; }
}
@media (prefers-reduced-motion: reduce) { .reading-outline nav button { transition: none; } }
</style>
