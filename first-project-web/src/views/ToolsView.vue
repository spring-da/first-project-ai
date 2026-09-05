<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { Braces, CalendarClock, Check, Clock3, Copy, Play, RefreshCw, TimerReset } from 'lucide-vue-next'
import JsonSnippetEditor from '../components/JsonSnippetEditor.vue'
import { useNotificationStore } from '../stores/notifications'
import { nextCronRuns, parseCronExpression } from '../utils/cron'

type ToolTab = 'cron' | 'json' | 'timestamp'
type CronMode = 'interval' | 'daily' | 'weekly' | 'monthly' | 'custom'

const notifications = useNotificationStore()
const activeTool = ref<ToolTab>('cron')
const cronMode = ref<CronMode>('interval')
const cronExpression = ref('0 0/15 * * * ?')
const intervalMinutes = ref(15)
const cronHour = ref(9)
const cronMinute = ref(0)
const cronDay = ref(1)
const cronWeekday = ref(2)
const copiedKey = ref('')
const now = ref(new Date())
const jsonSource = ref(`{
  "service": "DevNest",
  "enabled": true,
  "features": ["knowledge", "tools"]
}`)
const timestampUnit = ref<'seconds' | 'milliseconds'>('milliseconds')
const timestampInput = ref(String(Date.now()))
const dateInput = ref(toLocalInput(new Date()))
let clockTimer: number | undefined

const toolTabs: Array<{ id: ToolTab; label: string; description: string; icon: typeof CalendarClock }> = [
  { id: 'cron', label: 'CRON 解析器', description: '配置并预览任务触发时间', icon: CalendarClock },
  { id: 'json', label: 'JSON 工具', description: '格式化、转义和结构化编辑', icon: Braces },
  { id: 'timestamp', label: '时间戳转换', description: '秒、毫秒与日期互转', icon: Clock3 },
]

const parsedCron = computed(() => {
  try { return { value: parseCronExpression(cronExpression.value), error: '' } }
  catch (cause) { return { value: null, error: cause instanceof Error ? cause.message : '表达式无法解析' } }
})
const upcomingRuns = computed(() => {
  if (!parsedCron.value.value) return []
  try { return nextCronRuns(cronExpression.value, now.value, 8) }
  catch { return [] }
})
const convertedTimestamp = computed(() => {
  const value = Number(timestampInput.value.trim())
  if (!Number.isFinite(value)) return null
  const date = new Date(timestampUnit.value === 'seconds' ? value * 1000 : value)
  return Number.isNaN(date.getTime()) ? null : date
})
const convertedDate = computed(() => {
  const date = new Date(dateInput.value)
  return Number.isNaN(date.getTime()) ? null : date
})

watch([cronMode, intervalMinutes, cronHour, cronMinute, cronDay, cronWeekday], () => {
  if (cronMode.value === 'interval') cronExpression.value = `0 0/${intervalMinutes.value} * * * ?`
  else if (cronMode.value === 'daily') cronExpression.value = `0 ${cronMinute.value} ${cronHour.value} * * ?`
  else if (cronMode.value === 'weekly') cronExpression.value = `0 ${cronMinute.value} ${cronHour.value} ? * ${cronWeekday.value}`
  else if (cronMode.value === 'monthly') cronExpression.value = `0 ${cronMinute.value} ${cronHour.value} ${cronDay.value} * ?`
})

function toLocalInput(date: Date) {
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 16)
}

function refreshNow() {
  now.value = new Date()
}

function startClock() {
  refreshNow()
  if (clockTimer === undefined) clockTimer = window.setInterval(refreshNow, 30_000)
}

function stopClock() {
  if (clockTimer !== undefined) window.clearInterval(clockTimer)
  clockTimer = undefined
}

function setNowTimestamp() {
  const value = Date.now()
  timestampInput.value = String(timestampUnit.value === 'seconds' ? Math.floor(value / 1000) : value)
}

function setNowDate() {
  dateInput.value = toLocalInput(new Date())
}

function editCronExpression() {
  cronMode.value = 'custom'
}

async function copyText(key: string, value: string) {
  try {
    if (window.isSecureContext && navigator.clipboard?.writeText) await navigator.clipboard.writeText(value)
    else {
      const textarea = document.createElement('textarea')
      textarea.value = value
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      if (!document.execCommand('copy')) throw new Error('copy failed')
      textarea.remove()
    }
    copiedKey.value = key
    window.setTimeout(() => { if (copiedKey.value === key) copiedKey.value = '' }, 1400)
  } catch {
    notifications.notify('复制失败，请手动选择内容。', { type: 'warning' })
  }
}

function formatRun(date: Date) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', weekday: 'short',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(date)
}

function relativeRun(date: Date) {
  const seconds = Math.max(0, Math.round((date.getTime() - now.value.getTime()) / 1000))
  if (seconds < 60) return `${seconds} 秒后`
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分钟后`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} 小时后`
  return `${Math.floor(seconds / 86400)} 天后`
}

onMounted(startClock)
onActivated(startClock)
onDeactivated(stopClock)
onBeforeUnmount(stopClock)
</script>

<template>
  <div class="tools-page">
    <div class="tools-shell">
      <nav class="tool-tabs" aria-label="系统工具">
        <button v-for="tool in toolTabs" :key="tool.id" type="button" :class="{ active: activeTool === tool.id }" :aria-label="tool.label" @click="activeTool = tool.id">
          <span><component :is="tool.icon" :size="19" /></span><div><strong>{{ tool.label }}</strong><small>{{ tool.description }}</small></div>
        </button>
      </nav>

      <section v-if="activeTool === 'cron'" class="tool-workspace cron-workspace">
        <div class="cron-expression" :class="{ invalid: parsedCron.error }">
          <label><span>CRON 表达式</span><input v-model="cronExpression" spellcheck="false" aria-label="CRON 表达式" @input="editCronExpression" /></label>
          <button type="button" :disabled="Boolean(parsedCron.error)" @click="copyText('cron', parsedCron.value?.expression ?? cronExpression)"><Check v-if="copiedKey === 'cron'" :size="16" /><Copy v-else :size="16" />{{ copiedKey === 'cron' ? '已复制' : '复制表达式' }}</button>
          <p v-if="parsedCron.error">{{ parsedCron.error }}</p><p v-else><Check :size="14" />{{ parsedCron.value?.summary }}</p>
        </div>
        <div class="cron-grid">
          <section class="builder-card">
            <header><span><TimerReset :size="17" />快速配置</span><small>修改后实时生成</small></header>
            <div class="mode-grid">
              <button v-for="mode in ([['interval', '按分钟'], ['daily', '每天'], ['weekly', '每周'], ['monthly', '每月']] as const)" :key="mode[0]" type="button" :class="{ active: cronMode === mode[0] }" @click="cronMode = mode[0]">{{ mode[1] }}</button>
            </div>
            <div v-if="cronMode === 'interval'" class="builder-fields"><label><span>每隔</span><select v-model.number="intervalMinutes"><option v-for="value in [1, 2, 5, 10, 15, 20, 30]" :key="value" :value="value">{{ value }} 分钟</option></select></label></div>
            <div v-else-if="cronMode !== 'custom'" class="builder-fields">
              <label v-if="cronMode === 'monthly'"><span>日期</span><select v-model.number="cronDay"><option v-for="value in 28" :key="value" :value="value">{{ value }} 日</option></select></label>
              <label v-if="cronMode === 'weekly'"><span>星期</span><select v-model.number="cronWeekday"><option v-for="(label, index) in ['周日','周一','周二','周三','周四','周五','周六']" :key="label" :value="index + 1">{{ label }}</option></select></label>
              <label><span>小时</span><select v-model.number="cronHour"><option v-for="value in 24" :key="value - 1" :value="value - 1">{{ String(value - 1).padStart(2, '0') }} 时</option></select></label>
              <label><span>分钟</span><select v-model.number="cronMinute"><option v-for="value in 60" :key="value - 1" :value="value - 1">{{ String(value - 1).padStart(2, '0') }} 分</option></select></label>
            </div>
            <div v-else class="custom-note"><Play :size="17" /><div><strong>自由表达式模式</strong><p>当前表达式由你直接编辑。选择上方任一快捷配置即可重新生成。</p></div></div>
            <div class="field-breakdown" v-if="parsedCron.value"><span v-for="(field, key) in parsedCron.value.fields" :key="key"><small>{{ { second: '秒', minute: '分', hour: '时', day: '日', month: '月', weekday: '周' }[key] }}</small><strong>{{ field.source }}</strong></span></div>
          </section>
          <section class="runs-card">
            <header><span><CalendarClock :size="17" />未来触发时间</span><button type="button" @click="refreshNow"><RefreshCw :size="14" />刷新</button></header>
            <div v-if="upcomingRuns.length" class="run-list"><div v-for="(run, index) in upcomingRuns" :key="run.toISOString()"><span>{{ index + 1 }}</span><strong>{{ formatRun(run) }}</strong><small>{{ relativeRun(run) }}</small></div></div>
            <p v-else class="tool-empty">修正表达式后，这里会显示未来 8 次触发时间。</p>
          </section>
        </div>
      </section>

      <section v-else-if="activeTool === 'json'" class="tool-workspace json-workspace">
        <div class="json-editor-host"><JsonSnippetEditor v-model="jsonSource" /></div>
      </section>

      <section v-else class="tool-workspace timestamp-workspace">
        <div class="timestamp-grid">
          <section class="timestamp-card">
            <header><span>时间戳 → 日期</span><div class="timestamp-actions"><button type="button" @click="setNowTimestamp"><Clock3 :size="14" />现在</button><div class="unit-toggle"><button type="button" :class="{ active: timestampUnit === 'seconds' }" @click="timestampUnit = 'seconds'; setNowTimestamp()">秒</button><button type="button" :class="{ active: timestampUnit === 'milliseconds' }" @click="timestampUnit = 'milliseconds'; setNowTimestamp()">毫秒</button></div></div></header>
            <label class="large-input"><span>Unix 时间戳</span><input v-model="timestampInput" inputmode="numeric" spellcheck="false" /></label>
            <div v-if="convertedTimestamp" class="time-results"><div><span>本地时间</span><strong>{{ formatRun(convertedTimestamp) }}</strong></div><div><span>ISO 8601</span><strong>{{ convertedTimestamp.toISOString() }}</strong><button type="button" @click="copyText('iso', convertedTimestamp.toISOString())"><Check v-if="copiedKey === 'iso'" :size="15" /><Copy v-else :size="15" /></button></div></div>
            <p v-else class="input-error">请输入有效的秒或毫秒时间戳。</p>
          </section>
          <section class="timestamp-card">
            <header><span>日期 → 时间戳</span><button type="button" @click="setNowDate">现在</button></header>
            <label class="large-input"><span>本地日期时间</span><input v-model="dateInput" type="datetime-local" step="1" /></label>
            <div v-if="convertedDate" class="time-results"><div><span>秒</span><strong>{{ Math.floor(convertedDate.getTime() / 1000) }}</strong><button type="button" @click="copyText('seconds', String(Math.floor(convertedDate.getTime() / 1000)))"><Check v-if="copiedKey === 'seconds'" :size="15" /><Copy v-else :size="15" /></button></div><div><span>毫秒</span><strong>{{ convertedDate.getTime() }}</strong><button type="button" @click="copyText('milliseconds', String(convertedDate.getTime()))"><Check v-if="copiedKey === 'milliseconds'" :size="15" /><Copy v-else :size="15" /></button></div></div>
          </section>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.tools-page { width: 100%; min-height: 0; flex: 1; display: flex; padding: 12px; }
.tools-shell { width: 100%; min-height: 0; display: flex; flex: 1; flex-direction: column; overflow: hidden; border: 1px solid var(--border); border-radius: 14px; background: var(--panel); box-shadow: var(--shadow-sm); }
.tool-tabs { flex: 0 0 auto; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; padding: 10px; border-bottom: 1px solid var(--border); background: color-mix(in srgb, var(--surface-sunken) 72%, var(--panel)); }
.tool-tabs button { position: relative; min-width: 0; width: 100%; display: flex; align-items: center; gap: 10px; padding: 8px 10px; color: var(--muted); text-align: left; border: 1px solid transparent; border-radius: 11px; background: transparent; cursor: pointer; }
.tool-tabs button:hover { color: var(--text); background: var(--panel); }
.tool-tabs button.active { color: var(--accent); border-color: var(--accent-border); background: var(--accent-bg); box-shadow: var(--shadow-sm); }
.tool-tabs button > span { width: 32px; height: 32px; display: grid; flex: 0 0 32px; place-items: center; border-radius: 9px; background: var(--panel); }
.tool-tabs button div { min-width: 0; display: grid; gap: 3px; }
.tool-tabs strong { color: var(--text); font-size: var(--font-xs); }
.tool-tabs small { overflow: hidden; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.tool-workspace { min-width: 0; min-height: 0; flex: 1; overflow: auto; padding: 14px; }
.cron-expression { display: grid; grid-template-columns: minmax(0,1fr) auto; gap: 9px 12px; margin: 0 0 14px; padding: 15px; border: 1px solid var(--accent-border); border-radius: 13px; background: var(--accent-bg); }
.cron-expression label { min-width: 0; display: grid; gap: 7px; }
.cron-expression label > span { color: var(--muted); font-size: var(--font-2xs); font-weight: 700; }
.cron-expression input { width: 100%; padding: 11px 12px; color: var(--text); border: 1px solid var(--border-strong); border-radius: 9px; outline: 0; background: var(--panel); font: 700 15px/1.3 "Cascadia Code", Consolas, monospace; }
.cron-expression input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.cron-expression > button, .now-button { align-self: end; height: 41px; display: inline-flex; align-items: center; gap: 6px; padding: 0 13px; color: #fff; border: 0; border-radius: 9px; background: var(--accent); cursor: pointer; font-weight: 700; }
.cron-expression > button:disabled { opacity: .45; cursor: not-allowed; }
.cron-expression > p { grid-column: 1/-1; display: flex; align-items: center; gap: 5px; margin: 0; color: var(--success); font-size: var(--font-xs); }
.cron-expression.invalid { border-color: color-mix(in srgb, var(--danger) 55%, var(--border)); background: color-mix(in srgb, var(--danger) 7%, var(--panel)); }
.cron-expression.invalid > p { color: var(--danger); }
.cron-grid, .timestamp-grid { display: grid; grid-template-columns: minmax(0, .92fr) minmax(380px, 1.08fr); gap: 16px; }
.builder-card, .runs-card, .timestamp-card { padding: 18px; border: 1px solid var(--border); border-radius: 14px; background: var(--surface-sunken); }
.builder-card > header, .runs-card > header, .timestamp-card > header { min-height: 32px; display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.builder-card > header > span, .runs-card > header > span, .timestamp-card > header > span { display: flex; align-items: center; gap: 7px; color: var(--text); font-weight: 750; }
.builder-card > header small { color: var(--muted); font-size: 10px; }
.runs-card > header button, .timestamp-card > header > button { display: flex; align-items: center; gap: 5px; padding: 6px 8px; color: var(--muted); border: 1px solid var(--border); border-radius: 7px; background: var(--panel); cursor: pointer; }
.mode-grid { display: grid; grid-template-columns: repeat(4,1fr); gap: 6px; margin: 17px 0; }
.mode-grid button, .unit-toggle button { padding: 8px; color: var(--muted); border: 1px solid var(--border); background: var(--panel); cursor: pointer; }
.mode-grid button { border-radius: 8px; }
.mode-grid button.active, .unit-toggle button.active { color: var(--accent); border-color: var(--accent-border); background: var(--accent-bg); font-weight: 700; }
.builder-fields { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 10px; }
.builder-fields label { display: grid; gap: 6px; }
.builder-fields span, .large-input > span { color: var(--muted); font-size: var(--font-2xs); }
.builder-fields select { width: 100%; padding: 9px; color: var(--text); border: 1px solid var(--border); border-radius: 8px; background: var(--panel); }
.custom-note { display: flex; gap: 10px; min-height: 72px; align-items: center; padding: 13px; color: var(--accent); border: 1px dashed var(--accent-border); border-radius: 9px; background: var(--accent-bg); }
.custom-note p { margin: 4px 0 0; color: var(--muted); font-size: var(--font-2xs); }
.field-breakdown { display: grid; grid-template-columns: repeat(6,1fr); gap: 5px; margin-top: 17px; }
.field-breakdown span { min-width: 0; padding: 8px 4px; text-align: center; border: 1px solid var(--border); border-radius: 7px; background: var(--panel); }
.field-breakdown small, .field-breakdown strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.field-breakdown small { color: var(--muted); font-size: 9px; }.field-breakdown strong { margin-top: 3px; font: 700 11px/1.2 monospace; }
.run-list { display: grid; margin-top: 13px; }
.run-list > div { display: grid; grid-template-columns: 26px minmax(0,1fr) auto; align-items: center; gap: 9px; min-height: 43px; border-bottom: 1px solid var(--border); }
.run-list > div:last-child { border-bottom: 0; }.run-list > div > span { width: 22px; height: 22px; display: grid; place-items: center; color: var(--accent); border-radius: 6px; background: var(--accent-bg); font-size: 10px; font-weight: 750; }
.run-list strong { font-size: var(--font-xs); font-weight: 650; }.run-list small { color: var(--muted); font-size: 10px; }
.tool-empty { margin: 18px 0 0; padding: 30px; color: var(--muted); text-align: center; border: 1px dashed var(--border); border-radius: 10px; }
.json-workspace { display: flex; flex-direction: column; overflow: hidden; padding: 0; }.json-editor-host { min-height: 0; flex: 1; display: flex; overflow: hidden; }
.timestamp-workspace { min-height: 0; }.timestamp-grid { height: 100%; align-content: start; }.timestamp-card { min-height: 330px; background: var(--panel); }
.timestamp-actions { display: flex; align-items: center; gap: 8px; }.timestamp-actions > button { display: inline-flex; align-items: center; gap: 5px; padding: 6px 8px; color: var(--muted); border: 1px solid var(--border); border-radius: 7px; background: var(--panel); cursor: pointer; }
.unit-toggle { display: flex; }.unit-toggle button { padding: 5px 10px; }.unit-toggle button:first-child { border-radius: 7px 0 0 7px; }.unit-toggle button:last-child { margin-left: -1px; border-radius: 0 7px 7px 0; }
.large-input { display: grid; gap: 7px; margin-top: 25px; }.large-input input { width: 100%; padding: 13px; color: var(--text); border: 1px solid var(--border-strong); border-radius: 9px; outline: 0; background: var(--surface-sunken); font: 700 16px/1.2 "Cascadia Code", Consolas, monospace; }.large-input input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.time-results { display: grid; gap: 9px; margin-top: 18px; }.time-results > div { min-width: 0; display: grid; grid-template-columns: 82px minmax(0,1fr) 28px; align-items: center; gap: 9px; padding: 11px; border: 1px solid var(--border); border-radius: 9px; background: var(--surface-sunken); }.time-results > div > span { color: var(--muted); font-size: var(--font-2xs); }.time-results strong { overflow: hidden; font: 650 12px/1.4 "Cascadia Code", Consolas, monospace; text-overflow: ellipsis; white-space: nowrap; }.time-results button { width: 28px; height: 28px; display: grid; place-items: center; color: var(--muted); border: 0; border-radius: 7px; background: transparent; cursor: pointer; }.time-results button:hover { color: var(--accent); background: var(--accent-bg); }
.input-error { color: var(--danger); font-size: var(--font-xs); }
@media (max-width: 1100px) { .cron-grid, .timestamp-grid { grid-template-columns: 1fr; } }
@media (max-width: 720px) { .tools-page { padding: 0; }.tool-tabs { gap: 4px; padding: 6px; }.tool-tabs button { justify-content: center; padding: 7px; }.tool-tabs button div { display: none; }.cron-expression { grid-template-columns: 1fr; }.cron-expression > button { justify-content: center; }.builder-fields { grid-template-columns: 1fr; } }
</style>
