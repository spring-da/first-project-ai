<script setup lang="ts">
import { BookOpen, CornerDownLeft, X } from 'lucide-vue-next'
const emit = defineEmits<{ close: []; insert: [text: string] }>()
const examples = [
  { title: '标题', hint: '# 后面记得加一个空格，# 越多，标题层级越深。', code: '# 一级标题\n\n## 二级标题\n\n### 三级标题' },
  { title: '强调文字', hint: '用成对的符号包住文字。', code: '**粗体文字**\n\n*斜体文字*\n\n~~删除线~~' },
  { title: '列表与待办', hint: '每一项单独一行，空格也属于语法。', code: '- 第一项\n- 第二项\n\n1. 第一步\n2. 第二步\n\n- [ ] 待完成\n- [x] 已完成' },
  { title: '引用', hint: '适合引用一段话或强调一条备注。', code: '> 记录下值得记住的一句话。' },
  { title: '链接', hint: '方括号里是文字，圆括号里是完整网址。', code: '[链接文字](https://example.com)' },
  { title: '图片', hint: '复制截图后在正文按 Ctrl+V（Mac 为 ⌘V），或点击工具栏的图片按钮。上传后自动插入语法。', code: '![图片说明](https://example.com/image.png)', insertable: false },
  { title: '行内代码与代码块', hint: '短代码用一对反引号，多行代码用三枚反引号包起来。', code: '使用 `const` 声明变量。\n\n```javascript\nconst message = "Hello"\nconsole.log(message)\n```' },
  { title: '表格', hint: '第二行的 --- 是表头分隔线，每一列用 | 分开。', code: '| 名称 | 说明 |\n| --- | --- |\n| 笔记 | 记录想法 |\n| 资料 | 整理知识 |' },
  { title: '分隔线与段落', hint: '段落之间留一个空行；三个短横线会变成分隔线。', code: '上一段内容。\n\n---\n\n下一段内容。' },
]
</script>

<template>
  <aside id="markdown-guide" class="markdown-guide" aria-label="Markdown 语法手册" @keydown.esc.stop="emit('close')">
    <header><span class="guide-icon"><BookOpen :size="18" /></span><div><strong>语法手册</strong><small>边看边写，随时上手</small></div><button type="button" aria-label="关闭语法手册" @click="emit('close')"><X :size="17" /></button></header>
    <div class="guide-scroll" tabindex="0" aria-label="语法示例">
      <p class="guide-tip">在正文中尝试下面的写法，也可以点击“插入示例”。<kbd>Ctrl S</kbd> / <kbd>⌘ S</kbd> 保存当前笔记，新文件请先命名。</p>
      <section v-for="(example, index) in examples" :key="example.title">
        <h3><span>{{ String(index + 1).padStart(2, '0') }}</span>{{ example.title }}</h3>
        <p>{{ example.hint }}</p><pre><code>{{ example.code }}</code></pre>
        <button v-if="example.insertable !== false" type="button" :aria-label="`插入${example.title}示例`" @click="emit('insert', example.code)"><CornerDownLeft :size="13" />插入示例</button>
      </section>
      <p class="guide-note">本编辑器支持以上常用语法，暂不支持原始 HTML、公式及 Mermaid 图表。上传图片仅当前账号可查看。</p>
    </div>
  </aside>
</template>

<style scoped>
.markdown-guide { width: 300px; max-width: 85vw; min-width: 0; min-height: 0; flex-shrink: 0; display: flex; flex-direction: column; border-left: 1px solid var(--border); background: var(--panel); }
header { display: flex; align-items: center; flex-shrink: 0; gap: 10px; padding: 16px; border-bottom: 1px solid var(--border); }
.guide-icon { display: grid; place-items: center; width: 34px; height: 34px; color: var(--accent); border-radius: 10px; background: var(--accent-soft); }
header div { flex: 1; }
header strong, header small { display: block; }
header strong { font-size: 14px; }
header small { margin-top: 4px; color: var(--muted); font-size: 11px; }
header button { display: grid; place-items: center; width: 28px; height: 28px; border: 0; border-radius: 7px; color: var(--muted); background: transparent; cursor: pointer; }
header button:hover { color: var(--text); background: var(--surface-raised); }
.guide-scroll { flex: 1; min-height: 0; overflow: auto; padding: 16px; overscroll-behavior: auto; }
.guide-tip { margin: 0 0 20px; padding: 12px; color: var(--subtle); border-radius: 10px; background: var(--accent-soft); font-size: 12px; line-height: 1.8; }
kbd { padding: 1px 4px; font-size: 10px; }
section + section { margin-top: 25px; }
h3 { display: flex; align-items: center; gap: 9px; margin: 0; font-size: 13px; }
h3 span { color: var(--accent); font: 11px ui-monospace, monospace; }
section p, .guide-note { margin: 9px 0; color: var(--muted); font-size: 12px; line-height: 1.7; }
pre { margin: 10px 0 8px; padding: 12px; overflow-x: auto; border: 1px solid var(--border); border-radius: 9px; background: var(--code-bg); }
pre code { color: var(--code-text); background: transparent; font: 11px/1.75 Consolas, monospace; white-space: pre-wrap; overflow-wrap: anywhere; }
section button { display: flex; align-items: center; gap: 5px; margin-left: auto; padding: 4px 6px; color: var(--accent); border: 0; border-radius: 5px; background: transparent; cursor: pointer; font-size: 11px; }
section button:hover { background: var(--accent-soft); }
.guide-note { margin-top: 24px; padding-top: 14px; border-top: 1px solid var(--border); }
@media (max-width: 720px) { .markdown-guide { position: absolute; z-index: 5; inset: 0 0 0 auto; box-shadow: -8px 0 24px rgba(0,0,0,.12); } }
</style>
