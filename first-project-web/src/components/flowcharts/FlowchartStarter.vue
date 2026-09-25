<script setup lang="ts">
import { ArrowRight, FilePlus2, LayoutTemplate, Upload } from 'lucide-vue-next'

type TemplateKind = 'sequence' | 'decision' | 'architecture'
interface TemplateDescriptor { kind: TemplateKind; title: string; description: string; preview: string }

defineProps<{ templates: TemplateDescriptor[]; importError?: string }>()
const emit = defineEmits<{ 'start-blank': []; 'start-template': [kind: TemplateKind]; import: []; close: [] }>()
</script>

<template>
  <section class="flowchart-starter" aria-label="新建流程图起始面板">
    <div class="flowchart-starter__heading">
      <div>
        <p>流程图工作台</p>
        <h1>从哪里开始？</h1>
        <span>选择空白画布、常用模板，或继续编辑已有的流程图源文件。</span>
      </div>
      <button type="button" class="flowchart-starter__close" @click="emit('close')">取消</button>
    </div>

    <div class="flowchart-starter__primary-actions">
      <button type="button" class="flowchart-starter__action flowchart-starter__action--primary" @click="emit('start-blank')">
        <FilePlus2 :size="22" aria-hidden="true" />
        <span><strong>从空白画布开始</strong><small>自由绘制你的流程</small></span>
        <ArrowRight :size="18" aria-hidden="true" />
      </button>
      <button type="button" class="flowchart-starter__action" @click="emit('import')">
        <Upload :size="22" aria-hidden="true" />
        <span><strong>导入流程图</strong><small>打开 .flowchart.json 源文件</small></span>
        <ArrowRight :size="18" aria-hidden="true" />
      </button>
    </div>
    <p v-if="importError" class="flowchart-starter__import-error" role="alert" aria-live="assertive" aria-atomic="true">导入失败：{{ importError }}。请选择有效的 .flowchart.json 文件后重试。</p>

    <div class="flowchart-starter__templates" aria-labelledby="flowchart-starter-templates">
      <div><h2 id="flowchart-starter-templates">从模板开始</h2><p>选择一个结构，再按需要调整内容。</p></div>
      <div class="flowchart-starter__template-grid">
        <button v-for="template in templates" :key="template.kind" type="button" class="flowchart-starter__template" @click="emit('start-template', template.kind)">
          <span class="flowchart-starter__template-icon"><LayoutTemplate :size="18" aria-hidden="true" /></span>
          <span class="flowchart-starter__template-preview">{{ template.preview }}</span>
          <strong>{{ template.title }}</strong>
          <small>{{ template.description }}</small>
          <span class="flowchart-starter__template-link">使用此模板 <ArrowRight :size="14" aria-hidden="true" /></span>
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.flowchart-starter { display: grid; gap: 28px; width: min(100%, 920px); margin: auto; padding: 32px; color: var(--flow-text, var(--text)); }
.flowchart-starter__heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; }.flowchart-starter__heading p { margin: 0 0 8px; color: var(--flow-accent, var(--accent)); font-size: 13px; font-weight: 700; }.flowchart-starter__heading h1 { margin: 0; font-size: clamp(24px, 4vw, 32px); line-height: 1.2; }.flowchart-starter__heading span { display: block; max-width: 620px; margin-top: 10px; color: var(--flow-muted, var(--muted)); line-height: 1.6; }.flowchart-starter__close { min-width: 44px; min-height: 44px; padding: 0 12px; border: 1px solid var(--flow-border, var(--border)); border-radius: 7px; background: var(--flow-surface, var(--panel)); color: var(--flow-muted, var(--muted)); cursor: pointer; font: inherit; }.flowchart-starter__close:hover { border-color: var(--flow-accent, var(--accent)); color: var(--flow-text, var(--text)); }.flowchart-starter__close:focus-visible,.flowchart-starter__action:focus-visible,.flowchart-starter__template:focus-visible { outline: 3px solid color-mix(in srgb, var(--flow-accent, var(--accent)) 45%, transparent); outline-offset: 3px; }
.flowchart-starter__primary-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }.flowchart-starter__action { display: flex; align-items: center; min-height: 92px; gap: 14px; padding: 18px; border: 1px solid var(--flow-border, var(--border)); border-radius: 12px; background: var(--flow-surface, var(--panel)); color: var(--flow-text, var(--text)); cursor: pointer; text-align: left; transition: border-color .15s ease, box-shadow .15s ease, transform .15s ease; }.flowchart-starter__action span { display: grid; flex: 1; gap: 4px; }.flowchart-starter__action strong { font-size: 15px; }.flowchart-starter__action small,.flowchart-starter__templates p,.flowchart-starter__template small { color: var(--flow-muted, var(--muted)); line-height: 1.5; }.flowchart-starter__action:hover,.flowchart-starter__template:hover { border-color: var(--flow-accent, var(--accent)); box-shadow: 0 8px 18px color-mix(in srgb, var(--flow-accent, var(--accent)) 13%, transparent); transform: translateY(-1px); }.flowchart-starter__action--primary { border-color: var(--flow-accent, var(--accent)); background: var(--flow-accent-soft, var(--panel)); color: var(--flow-accent, var(--accent)); }.flowchart-starter__action--primary span { color: var(--flow-text, var(--text)); }
.flowchart-starter__import-error { margin: -12px 0 0; padding: 10px 12px; border: 1px solid color-mix(in srgb, var(--danger) 55%, var(--flow-border, var(--border))); border-radius: 8px; background: color-mix(in srgb, var(--danger) 8%, var(--flow-surface, var(--panel))); color: var(--danger); font-size: 13px; line-height: 1.5; }
.flowchart-starter__templates > div:first-child { display: grid; gap: 4px; }.flowchart-starter__templates h2,.flowchart-starter__templates p { margin: 0; }.flowchart-starter__templates h2 { font-size: 17px; }.flowchart-starter__template-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; margin-top: 14px; }.flowchart-starter__template { display: grid; gap: 9px; min-width: 0; padding: 16px; border: 1px solid var(--flow-border, var(--border)); border-radius: 12px; background: var(--flow-surface, var(--panel)); color: var(--flow-text, var(--text)); cursor: pointer; text-align: left; transition: border-color .15s ease, box-shadow .15s ease, transform .15s ease; }.flowchart-starter__template-icon { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 8px; background: var(--flow-accent-soft, var(--panel)); color: var(--flow-accent, var(--accent)); }.flowchart-starter__template-preview { min-height: 34px; overflow: hidden; color: var(--flow-muted, var(--muted)); font: 600 11px/1.5 ui-monospace, SFMono-Regular, Menlo, monospace; white-space: pre-line; }.flowchart-starter__template strong { font-size: 14px; }.flowchart-starter__template-link { display: inline-flex; align-items: center; gap: 4px; margin-top: 2px; color: var(--flow-accent, var(--accent)); font-size: 12px; font-weight: 600; }
@media (max-width: 767px) { .flowchart-starter { padding: 22px 16px; gap: 22px; }.flowchart-starter__heading { gap: 12px; }.flowchart-starter__primary-actions,.flowchart-starter__template-grid { grid-template-columns: 1fr; } }
@media (prefers-reduced-motion: reduce) { .flowchart-starter__action,.flowchart-starter__template { transition: none; } }
</style>
