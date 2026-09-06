<script setup lang="ts">
import { Archive, BellRing, Code2, Eye, LoaderCircle, Send } from 'lucide-vue-next'
import { computed, onActivated } from 'vue'
import { ref } from 'vue'
import EmptyState from '../components/EmptyState.vue'
import MarkdownContent from '../components/MarkdownContent.vue'
import PageHeader from '../components/PageHeader.vue'
import { useAuthStore } from '../stores/auth'
import { useCommunicationStore } from '../stores/communication'
import { useConfirmationStore } from '../stores/confirmation'
import { useNotificationStore } from '../stores/notifications'
import type { SystemAnnouncement } from '../types'

const auth = useAuthStore()
const communication = useCommunicationStore()
const confirmation = useConfirmationStore()
const notifications = useNotificationStore()
const canManage = computed(() => auth.isAdmin && !auth.workspaceMember)
const announcements = computed(() => communication.announcements)
const title = ref('')
const content = ref('')

async function load() {
  try {
    await communication.loadAnnouncements(canManage.value)
  } catch (error) {
    notifyError(error, '系统公告加载失败')
  }
}

async function publish() {
  if (!title.value.trim() || !content.value.trim()) return
  try {
    await communication.publishAnnouncement(title.value.trim(), content.value.trim())
    title.value = ''
    content.value = ''
    notifications.notify('系统公告已发布，成员进入系统时会收到提示。', { type: 'success' })
  } catch (error) {
    notifyError(error, '公告发布失败')
  }
}

async function archive(announcement: SystemAnnouncement) {
  if (!await confirmation.ask({
    title: '撤回这则公告？',
    message: announcement.title,
    detail: '撤回后尚未阅读的成员不会再看到弹窗，历史记录仍会保留。',
    confirmText: '撤回公告', tone: 'danger', icon: 'delete',
  })) return
  try {
    await communication.archiveAnnouncement(announcement.id)
    notifications.notify('公告已撤回。', { type: 'success' })
  } catch (error) {
    notifyError(error, '公告撤回失败')
  }
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

function notifyError(error: unknown, fallback: string) {
  notifications.notify(error instanceof Error ? error.message : fallback, { type: 'error' })
}

onActivated(load)
</script>

<template>
  <div class="page-content announcements-page">
    <PageHeader v-if="!canManage" eyebrow="SYSTEM UPDATES" title="系统公告" description="集中查看版本更新、维护安排和系统级通知。" />

    <section v-if="canManage" class="announcement-publisher">
      <header class="publisher-heading">
        <div class="publisher-copy"><span class="section-icon"><BellRing :size="20" /></span><div><p class="eyebrow">ADMIN BROADCAST</p><h2>发布系统公告</h2><p>使用 Markdown 编写更新说明，发布后会在成员登录时完整展示。</p></div></div>
        <span class="markdown-status"><Code2 :size="15" />支持标题、列表、引用、代码、表格与链接</span>
      </header>
      <form @submit.prevent="publish">
        <label class="form-field title-field"><span>公告标题</span><input v-model="title" required maxlength="160" placeholder="例如：知识库版本更新说明" /></label>
        <div class="markdown-composer">
          <label class="markdown-pane editor-pane"><span><Code2 :size="15" />Markdown 内容</span><textarea v-model="content" required maxlength="4000" rows="11" placeholder="# 本次更新&#10;&#10;- 新增功能说明&#10;- 使用注意事项&#10;&#10;详情请查看 [使用文档](https://example.com)" /><small>{{ content.length }} / 4000</small></label>
          <section class="markdown-pane preview-pane" aria-label="公告预览"><header><span><Eye :size="15" />实时预览</span><small>{{ content.trim() ? '成员看到的内容' : '等待输入内容' }}</small></header><div class="preview-content"><MarkdownContent v-if="content.trim()" :source="content" /><div v-else class="preview-empty"><BellRing :size="24" /><strong>公告预览</strong><span>在左侧输入 Markdown，排版效果会显示在这里。</span></div></div></section>
        </div>
        <div class="publisher-actions"><span>发布后，每位尚未阅读的成员进入系统时都会看到公告弹窗。</span><button class="button button-primary" type="submit" :disabled="communication.mutating"><Send :size="16" />发布公告</button></div>
      </form>
    </section>

    <section class="announcement-board">
      <header><div><p class="eyebrow">ANNOUNCEMENT ARCHIVE</p><h2>{{ canManage ? '公告管理' : '全部公告' }}</h2></div><span>{{ announcements.length }} 则</span></header>
      <div v-if="communication.announcementLoading && !announcements.length" class="loading-state"><LoaderCircle class="spin" :size="20" />正在读取公告…</div>
      <EmptyState v-else-if="!announcements.length" compact title="暂时没有系统公告" description="后续版本变化和维护信息会在这里同步。" />
      <div v-else class="announcement-list">
        <article v-for="announcement in announcements" :key="announcement.id" :class="{ archived: !announcement.active }">
          <span class="announcement-mark"><BellRing :size="17" /></span>
          <div class="announcement-copy">
            <div><h3>{{ announcement.title }}</h3><span v-if="!announcement.active">已撤回</span></div>
            <div class="announcement-markdown"><MarkdownContent :source="announcement.content" /></div>
            <small>{{ announcement.publisherName }} · {{ formatDate(announcement.publishedAt) }}<template v-if="canManage"> · {{ announcement.readCount }} 人已读</template></small>
          </div>
          <button v-if="canManage && announcement.active" class="icon-button danger" type="button" aria-label="撤回公告" title="撤回公告" @click="archive(announcement)"><Archive :size="16" /></button>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.announcements-page { display: grid; gap: 16px; }
.announcements-page :deep(.page-header) { margin-bottom: 8px; }
.announcement-publisher, .announcement-board { border: 1px solid var(--border); border-radius: 16px; background: var(--panel); }
.announcement-publisher { overflow: hidden; border-color: var(--accent-border); background: var(--surface-accent); }
.publisher-heading { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 17px 24px; border-bottom: 1px solid var(--accent-border); }
.publisher-copy { display: flex; align-items: flex-start; gap: 13px; }
.section-icon { width: 40px; height: 40px; display: grid; flex: 0 0 auto; place-items: center; color: var(--accent); border-radius: 11px; background: var(--accent-bg); }
.publisher-copy h2, .announcement-board h2 { margin: 0; font-size: var(--font-lg); }
.publisher-copy > div > p:last-child { margin: 7px 0 0; color: var(--muted); font-size: var(--font-xs); line-height: 1.6; }
.markdown-status { display: inline-flex; align-items: center; gap: 7px; flex-shrink: 0; padding: 7px 10px; color: var(--accent); border: 1px solid var(--accent-border); border-radius: 99px; background: var(--panel); font-size: var(--font-2xs); }
.announcement-publisher form { display: grid; gap: 14px; padding: 18px 24px 20px; }
.title-field { max-width: none; }
.markdown-composer { height: clamp(440px, 54vh, 650px); display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(0, .9fr); overflow: hidden; border: 1px solid var(--border-strong); border-radius: 13px; background: var(--panel); box-shadow: 0 8px 28px color-mix(in srgb, var(--accent) 7%, transparent); }
.markdown-pane { min-width: 0; display: flex; flex-direction: column; margin: 0; }
.markdown-pane > span, .preview-pane > header { min-height: 43px; display: flex; align-items: center; gap: 7px; padding: 0 14px; color: var(--subtle); border-bottom: 1px solid var(--border); background: var(--surface-raised); font-size: var(--font-xs); font-weight: 700; }
.editor-pane { position: relative; border-right: 1px solid var(--border); }
.editor-pane textarea { flex: 1; min-height: 0; resize: none; padding: 18px; color: var(--text); border: 0; outline: 0; background: transparent; font: 13px/1.75 "Cascadia Code", Consolas, monospace; tab-size: 2; }
.editor-pane:focus-within { box-shadow: inset 0 0 0 2px var(--accent-soft); }
.editor-pane > small { position: absolute; right: 12px; bottom: 10px; padding: 3px 6px; color: var(--muted); border-radius: 6px; background: color-mix(in srgb, var(--panel) 90%, transparent); font-size: 10px; }
.preview-pane > header { justify-content: space-between; }
.preview-pane > header span { display: inline-flex; align-items: center; gap: 7px; }
.preview-pane > header small { color: var(--muted); font-size: 10px; font-weight: 500; }
.preview-content { flex: 1; min-height: 0; overflow: auto; padding: 20px 22px; }
.preview-empty { min-height: 350px; display: grid; place-content: center; justify-items: center; gap: 8px; color: var(--muted); text-align: center; }
.preview-empty svg { color: var(--accent); }
.preview-empty strong { color: var(--subtle); font-size: var(--font-sm); }
.preview-empty span { font-size: var(--font-xs); }
.publisher-actions { display: flex; align-items: center; justify-content: flex-end; gap: 14px; }
.publisher-actions > span { margin-right: auto; color: var(--muted); font-size: var(--font-2xs); }
.announcement-board > header { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 20px 22px; border-bottom: 1px solid var(--border); }
.announcement-board > header > span { color: var(--muted); font-size: var(--font-xs); }
.announcement-list { display: grid; gap: 12px; padding: 15px; }
.announcement-list > article { position: relative; display: grid; grid-template-columns: 38px minmax(0, 1fr) 34px; align-items: start; gap: 12px; padding: 17px; overflow: hidden; border: 1px solid var(--border); border-radius: 13px; background: var(--surface-raised); }
.announcement-list > article::before { content: ''; position: absolute; inset: 0 auto 0 0; width: 3px; background: var(--accent); }
.announcement-list > article.archived { opacity: .58; }
.announcement-mark { width: 34px; height: 34px; display: grid; place-items: center; color: var(--accent); border-radius: 9px; background: var(--accent-bg); }
.announcement-copy { min-width: 0; }
.announcement-copy > div { display: flex; align-items: center; gap: 8px; }
.announcement-copy h3 { margin: 0; font-size: var(--font-sm); }
.announcement-copy > div > span { padding: 3px 6px; color: var(--muted); border-radius: 99px; background: var(--surface-raised); font-size: 10px; }
.announcement-markdown { margin: 10px 0 12px; color: var(--subtle); font-size: var(--font-xs); line-height: 1.7; }
.announcement-copy small { color: var(--muted); font-size: var(--font-2xs); }
.preview-content :deep(.markdown-body > :first-child), .announcement-markdown :deep(.markdown-body > :first-child) { margin-top: 0; }
.preview-content :deep(.markdown-body > :last-child), .announcement-markdown :deep(.markdown-body > :last-child) { margin-bottom: 0; }
.preview-content :deep(h1), .preview-content :deep(h2), .preview-content :deep(h3), .announcement-markdown :deep(h1), .announcement-markdown :deep(h2), .announcement-markdown :deep(h3) { margin: 1.1em 0 .5em; color: var(--text); line-height: 1.3; }
.preview-content :deep(h1) { font-size: 1.45em; }.preview-content :deep(h2) { font-size: 1.25em; }.preview-content :deep(h3) { font-size: 1.1em; }
.announcement-markdown :deep(h1) { font-size: 1.22em; }.announcement-markdown :deep(h2) { font-size: 1.12em; }.announcement-markdown :deep(h3) { font-size: 1em; }
.preview-content :deep(p), .preview-content :deep(ul), .preview-content :deep(ol), .preview-content :deep(blockquote), .preview-content :deep(pre), .announcement-markdown :deep(p), .announcement-markdown :deep(ul), .announcement-markdown :deep(ol), .announcement-markdown :deep(blockquote), .announcement-markdown :deep(pre) { margin: 0 0 .85em; }
.preview-content :deep(a), .announcement-markdown :deep(a) { color: var(--accent); text-decoration: underline; text-underline-offset: 3px; }
.preview-content :deep(blockquote), .announcement-markdown :deep(blockquote) { padding: .45em .85em; border-left: 3px solid var(--accent); background: var(--surface-sunken); }
.preview-content :deep(pre), .announcement-markdown :deep(pre) { overflow: auto; padding: 12px; border: 1px solid var(--border); border-radius: 9px; background: var(--code-bg); }
.preview-content :deep(code), .announcement-markdown :deep(code) { font-family: "Cascadia Code", Consolas, monospace; }
.preview-content :deep(.markdown-table-wrap), .announcement-markdown :deep(.markdown-table-wrap) { overflow-x: auto; }
.preview-content :deep(table), .announcement-markdown :deep(table) { width: 100%; border-collapse: collapse; }
.preview-content :deep(th), .preview-content :deep(td), .announcement-markdown :deep(th), .announcement-markdown :deep(td) { padding: 7px 9px; text-align: left; border: 1px solid var(--border); }
.icon-button { width: 32px; height: 32px; display: grid; place-items: center; padding: 0; color: var(--muted); border: 1px solid transparent; border-radius: 8px; background: transparent; cursor: pointer; }
.icon-button:hover { color: var(--accent); border-color: var(--border); background: var(--surface-raised); }
.icon-button.danger:hover { color: var(--danger); border-color: color-mix(in srgb, var(--danger) 25%, var(--border)); }
.loading-state { min-height: 150px; display: flex; align-items: center; justify-content: center; gap: 8px; color: var(--muted); font-size: var(--font-xs); }
@media (max-width: 1100px) { .markdown-composer { grid-template-columns: 1fr; }.editor-pane { border-right: 0; border-bottom: 1px solid var(--border); } }
</style>
