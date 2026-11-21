import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import * as vue from 'vue'
import ts from 'typescript'
import { compileScript, parse } from '@vue/compiler-sfc'
import { extractMarkdownHeadings } from '../src/utils/markdown.ts'

const filename = new URL('../src/components/MarkdownReadingLayout.vue', import.meta.url)
const { descriptor } = parse(readFileSync(filename, 'utf8'))
const code = ts.transpileModule(compileScript(descriptor, { id: 'reading-layout' }).content, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText

function mount(source = '## 开始\n\n### 注意事项\n\n### 注意事项') {
  const mounted: Array<() => void> = [], cleanup: Array<() => void> = []
  const events = new Map<string, () => void>()
  const frames = new Map<number, () => void>()
  const props = vue.reactive({ source, enabled: true })
  let counter = 0, disconnected = false
  const viewport = { narrow: false, reducedMotion: false }
  const elements = extractMarkdownHeadings(source).map((heading, index) => ({
    id: heading.id, top: 100 + index * 300, focused: false, behavior: '',
    getBoundingClientRect() { return { top: this.top } },
    setAttribute() {}, focus() { this.focused = true },
    scrollIntoView(options: ScrollIntoViewOptions) { this.behavior = options.behavior ?? '' },
  }))
  const exports: Record<string, any> = {}
  const browser = {
    scrollY: 0, innerHeight: 800,
    matchMedia: (query: string) => ({ matches: query.includes('reduced-motion') ? viewport.reducedMotion : viewport.narrow }),
    addEventListener: (name: string, fn: () => void) => events.set(name, fn),
    removeEventListener: (name: string) => events.delete(name),
    requestAnimationFrame: (fn: () => void) => { frames.set(++counter, fn); return counter },
    cancelAnimationFrame: (id: number) => frames.delete(id),
  }
  vm.runInNewContext(code, {
    exports, window: browser,
    ResizeObserver: class { observe() {} disconnect() { disconnected = true } },
    require: (name: string) => {
      if (name === 'vue') return { ...vue, useId: () => 'outline-test', onMounted: (fn: () => void) => mounted.push(fn), onBeforeUnmount: (fn: () => void) => cleanup.push(fn) }
      if (name === '../utils/markdown') return { extractMarkdownHeadings }
      return {}
    },
  })
  const scope = vue.effectScope()
  const state = scope.run(() => exports.default.setup(props, { expose() {} }))!
  state.contentRoot.value = { querySelectorAll: () => elements, getBoundingClientRect: () => ({ bottom: 2000 }) }
  mounted.forEach(fn => fn())
  function flushFrame() { const pending = [...frames.values()]; frames.clear(); pending.forEach(fn => fn()) }
  return { state, props, elements, browser, viewport, events, frames, flushFrame,
    disconnected: () => disconnected, unmount: () => { cleanup.forEach(fn => fn()); scope.stop() },
  }
}

test('outline tracks viewport position and throttles scroll work to one animation frame', () => {
  const app = mount()
  try {
    app.flushFrame()
    assert.equal(app.state.activeId.value, app.elements[0]!.id)
    app.elements[0]!.top = -300
    app.elements[1]!.top = 100
    app.events.get('scroll')!(); app.events.get('scroll')!()
    assert.equal(app.frames.size, 1)
    app.flushFrame()
    assert.equal(app.state.activeId.value, app.elements[1]!.id)
  } finally { app.unmount() }
  assert.equal(app.events.size, 0)
  assert.equal(app.frames.size, 0)
  assert.equal(app.disconnected(), true)
})

test('navigation targets duplicate heading anchors and honors reduced motion', async () => {
  const app = mount()
  try {
    app.viewport.reducedMotion = true
    app.state.expanded.value = true
    await app.state.navigate(app.elements[2]!.id)
    assert.equal(app.state.expanded.value, false)
    assert.equal(app.elements[2]!.focused, true)
    assert.equal(app.elements[1]!.focused, false)
    assert.equal(app.elements[2]!.behavior, 'auto')
    assert.equal(app.state.activeId.value, app.elements[2]!.id)
  } finally { app.unmount() }
})

test('changing articles or disabling the outline clears stale anchors and selection', async () => {
  const app = mount()
  try {
    app.flushFrame()
    app.state.expanded.value = true
    app.props.source = '没有标题的文章\n\n```md\n# 示例代码\n```'
    await vue.nextTick(); app.flushFrame()
    assert.equal(app.state.headings.value.length, 0)
    assert.equal(app.state.activeId.value, '')
    assert.equal(app.state.expanded.value, false)
    app.props.source = '## 开始'
    app.props.enabled = false
    await vue.nextTick(); app.flushFrame()
    assert.equal(app.state.headings.value.length, 0)
  } finally { app.unmount() }
})
