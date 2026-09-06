<script setup lang="ts">
import {
  Archive,
  BellRing,
  ChevronDown,
  ChevronUp,
  ImagePlus,
  LoaderCircle,
  MessageCircleReply,
  MessagesSquare,
  Send,
  ShieldCheck,
  Trash2,
  X,
} from 'lucide-vue-next'
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref, watch } from 'vue'
import AuthenticatedImage from '../components/AuthenticatedImage.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import { useAuthStore } from '../stores/auth'
import { useCommunicationStore, type ReplyState } from '../stores/communication'
import { useConfirmationStore } from '../stores/confirmation'
import { useNotificationStore } from '../stores/notifications'
import type { CommunityMessage, SystemAnnouncement } from '../types'
import { IMAGE_TYPES, MAX_IMAGE_BYTES } from '../utils/markdownEditing'

const auth = useAuthStore()
const communication = useCommunicationStore()
const confirmation = useConfirmationStore()
const notifications = useNotificationStore()
const canManageAnnouncements = computed(() => auth.isAdmin && !auth.workspaceMember)
const announcements = computed(() => communication.announcements)
const messages = computed(() => communication.messages)
const messageContent = ref('')
const messageFile = ref<File | null>(null)
const messagePreview = ref('')
const messageFileInput = ref<HTMLInputElement | null>(null)
const replyTarget = ref<string | null>(null)
const replyContent = ref('')
const replyFile = ref<File | null>(null)
const replyPreview = ref('')
const replyFileInput = ref<HTMLInputElement | null>(null)
const expandedReplies = reactive(new Set<string>())
const announcementTitle = ref('')
const announcementContent = ref('')
const feedSentinel = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

const currentIdentity = computed(() => auth.session?.user)

async function initialize() {
  const results = await Promise.allSettled([
    communication.loadAnnouncements(canManageAnnouncements.value),
    communication.loadMessages(true),
  ])
  results.forEach((result) => {
    if (result.status === 'rejected') notifyError(result.reason, '消息中心加载失败')
  })
  await nextTick()
  observeSentinel()
}

function observeSentinel() {
  observer?.disconnect()
  if (!feedSentinel.value) return
  observer = new IntersectionObserver((entries) => {
    if (entries.some((entry) => entry.isIntersecting)) {
      communication.loadMessages().catch((error) => notifyError(error, '更多消息加载失败'))
    }
  }, { rootMargin: '240px 0px' })
  observer.observe(feedSentinel.value)
}

function selectMessageImage(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0] ?? null
  setImage(file, 'message')
}

function selectReplyImage(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0] ?? null
  setImage(file, 'reply')
}

function setImage(file: File | null, target: 'message' | 'reply') {
  if (file && (!IMAGE_TYPES.includes(file.type) || file.size > MAX_IMAGE_BYTES)) {
    notifications.notify('仅支持 20 MB 以内的 PNG、JPG、GIF 或 WebP 图片。', { type: 'warning' })
    file = null
  }
  if (target === 'message') {
    revokePreview(messagePreview)
    messageFile.value = file
    messagePreview.value = file ? URL.createObjectURL(file) : ''
    if (!file && messageFileInput.value) messageFileInput.value.value = ''
  } else {
    revokePreview(replyPreview)
    replyFile.value = file
    replyPreview.value = file ? URL.createObjectURL(file) : ''
    if (!file && replyFileInput.value) replyFileInput.value.value = ''
  }
}

async function submitMessage() {
  if (!messageContent.value.trim() && !messageFile.value) return
  try {
    await communication.createMessage(messageContent.value.trim(), messageFile.value)
    messageContent.value = ''
    setImage(null, 'message')
    notifications.notify('意见已发布，感谢你的分享。', { type: 'success' })
  } catch (error) {
    notifyError(error, '意见发布失败')
  }
}

async function submitReply(messageId: string) {
  if (!replyContent.value.trim() && !replyFile.value) return
  try {
    await communication.createReply(messageId, replyContent.value.trim(), replyFile.value)
    replyContent.value = ''
    setImage(null, 'reply')
    replyTarget.value = null
    expandedReplies.add(messageId)
    notifications.notify('回复已发布。', { type: 'success' })
  } catch (error) {
    notifyError(error, '回复发布失败')
  }
}

async function toggleReplies(message: CommunityMessage) {
  if (expandedReplies.has(message.id)) {
    expandedReplies.delete(message.id)
    return
  }
  expandedReplies.add(message.id)
  if (!communication.replies[message.id]?.loaded) {
    try {
      await communication.loadReplies(message.id, true)
    } catch (error) {
      expandedReplies.delete(message.id)
      notifyError(error, '回复加载失败')
    }
  }
}

async function loadMoreReplies(messageId: string) {
  try {
    await communication.loadReplies(messageId)
  } catch (error) {
    notifyError(error, '更多回复加载失败')
  }
}

function openReply(message: CommunityMessage) {
  const nextTarget = replyTarget.value === message.id ? null : message.id
  if (replyTarget.value !== nextTarget) {
    replyContent.value = ''
    setImage(null, 'reply')
  }
  replyTarget.value = nextTarget
  if (replyTarget.value) {
    expandedReplies.add(message.id)
    if (!communication.replies[message.id]?.loaded) {
      communication.loadReplies(message.id, true).catch((error) => notifyError(error, '回复加载失败'))
    }
  } else setImage(null, 'reply')
}

async function removeMessage(message: CommunityMessage) {
  const accepted = await confirmation.ask({
    title: message.parentId ? '删除这条回复？' : '删除这条消息？',
    message: message.content || '这是一条图片消息。',
    detail: message.parentId ? '删除后无法恢复。' : '主题下的全部回复也会同时删除且无法恢复。',
    confirmText: '确认删除', tone: 'danger', icon: 'delete',
  })
  if (!accepted) return
  try {
    await communication.deleteMessage(message)
    notifications.notify('消息已删除。', { type: 'success' })
  } catch (error) {
    notifyError(error, '消息删除失败')
  }
}

async function publishAnnouncement() {
  if (!announcementTitle.value.trim() || !announcementContent.value.trim()) return
  try {
    await communication.publishAnnouncement(
      announcementTitle.value.trim(), announcementContent.value.trim(),
    )
    announcementTitle.value = ''
    announcementContent.value = ''
    notifications.notify('系统公告已发布，成员下次进入系统时会收到提示。', { type: 'success' })
  } catch (error) {
    notifyError(error, '公告发布失败')
  }
}

async function archiveAnnouncement(announcement: SystemAnnouncement) {
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

function replyState(messageId: string): ReplyState {
  return communication.replies[messageId]
    ?? { items: [], nextPage: null, loaded: false, loading: false }
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

function notifyError(error: unknown, fallback: string) {
  notifications.notify(error instanceof Error ? error.message : fallback, { type: 'error' })
}

function revokePreview(target: { value: string }) {
  if (target.value) URL.revokeObjectURL(target.value)
}

function onComposerKeydown(event: KeyboardEvent, action: () => void) {
  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
    event.preventDefault()
    action()
  }
}

watch(feedSentinel, observeSentinel)
onMounted(initialize)
onActivated(observeSentinel)
onDeactivated(() => observer?.disconnect())
onBeforeUnmount(() => {
  observer?.disconnect()
  revokePreview(messagePreview)
  revokePreview(replyPreview)
})
</script>

<template>
  <div class="page-content message-center-page">
    <PageHeader
      eyebrow="ANNOUNCEMENTS & FEEDBACK"
      title="消息中心"
      description="查看系统动态，分享使用感受，也可以回复其他成员的建议。"
    />

    <section v-if="canManageAnnouncements" class="announcement-publisher">
      <div class="publisher-copy">
        <span class="section-icon"><BellRing :size="20" /></span>
        <div><p class="eyebrow">ADMIN BROADCAST</p><h2>发布系统公告</h2><p>发布后，每位尚未阅读的成员进入系统时都会看到公告弹窗。</p></div>
      </div>
      <form @submit.prevent="publishAnnouncement">
        <label class="form-field"><span>公告标题</span><input v-model="announcementTitle" required maxlength="160" placeholder="例如：知识库版本更新说明" /></label>
        <label class="form-field"><span>公告内容</span><textarea v-model="announcementContent" required maxlength="4000" rows="4" placeholder="说明本次变化、影响范围和使用建议…" /></label>
        <div class="publisher-actions"><span>{{ announcementContent.length }} / 4000</span><button class="button button-primary" type="submit" :disabled="communication.mutating"><Send :size="16" />发布公告</button></div>
      </form>
    </section>

    <section class="announcement-board">
      <header><div><p class="eyebrow">SYSTEM UPDATES</p><h2>系统公告</h2></div><span>{{ announcements.length }} 则</span></header>
      <div v-if="communication.announcementLoading && !announcements.length" class="loading-state"><LoaderCircle class="spin" :size="20" />正在读取公告…</div>
      <EmptyState v-else-if="!announcements.length" compact title="暂时没有系统公告" description="后续版本变化和维护信息会在这里同步。" />
      <div v-else class="announcement-list">
        <article v-for="announcement in announcements" :key="announcement.id" :class="{ archived: !announcement.active }">
          <span class="announcement-mark"><BellRing :size="17" /></span>
          <div class="announcement-copy"><div><h3>{{ announcement.title }}</h3><span v-if="!announcement.active">已撤回</span></div><p>{{ announcement.content }}</p><small>{{ announcement.publisherName }} · {{ formatDate(announcement.publishedAt) }}<template v-if="canManageAnnouncements"> · {{ announcement.readCount }} 人已读</template></small></div>
          <button v-if="canManageAnnouncements && announcement.active" class="icon-button danger" type="button" aria-label="撤回公告" title="撤回公告" @click="archiveAnnouncement(announcement)"><Archive :size="16" /></button>
        </article>
      </div>
    </section>

    <section class="community-section">
      <header class="community-heading"><div><p class="eyebrow">COMMUNITY FEEDBACK</p><h2>意见交流</h2><p>说说你的想法、遇到的问题，或者回复其他成员的建议。</p></div><span><MessagesSquare :size="16" />公开给所有已登录成员</span></header>

      <form class="message-composer" @submit.prevent="submitMessage">
        <div class="composer-avatar">{{ currentIdentity?.displayName.charAt(0).toUpperCase() || 'D' }}</div>
        <div class="composer-main">
          <textarea v-model="messageContent" maxlength="2000" rows="3" placeholder="分享建议、问题或使用感受…" aria-label="意见内容" @keydown="onComposerKeydown($event, submitMessage)" />
          <div v-if="messagePreview" class="image-preview"><img :src="messagePreview" alt="待发送图片预览" /><button type="button" aria-label="移除图片" @click="setImage(null, 'message')"><X :size="15" /></button></div>
          <footer><div><input ref="messageFileInput" class="sr-only" type="file" accept="image/png,image/jpeg,image/gif,image/webp" @change="selectMessageImage" /><button class="attachment-button" type="button" @click="messageFileInput?.click()"><ImagePlus :size="17" />添加图片</button><span>{{ messageContent.length }} / 2000</span></div><button class="button button-primary" type="submit" :disabled="communication.mutating || (!messageContent.trim() && !messageFile)"><Send :size="16" />发布意见</button></footer>
        </div>
      </form>

      <div v-if="communication.messageLoading && !messages.length" class="loading-state feed-loading"><LoaderCircle class="spin" :size="22" />正在加载大家的消息…</div>
      <EmptyState v-else-if="!messages.length" title="还没有成员发言" description="发布第一条意见，开始一次有价值的交流。" />
      <TransitionGroup v-else name="list" tag="div" class="message-feed">
        <article v-for="message in messages" :key="message.id" class="message-card">
          <header class="message-meta"><span class="message-avatar">{{ message.authorName.charAt(0).toUpperCase() }}</span><div><strong>{{ message.authorName }}</strong><span v-if="message.authorRole === 'ADMIN'"><ShieldCheck :size="12" />管理员</span><time :datetime="message.createdAt">{{ formatDate(message.createdAt) }}</time></div><button v-if="message.viewerCanDelete" class="icon-button danger" type="button" :aria-label="`删除 ${message.authorName} 的消息`" @click="removeMessage(message)"><Trash2 :size="15" /></button></header>
          <p v-if="message.content" class="message-body">{{ message.content }}</p>
          <AuthenticatedImage v-if="message.imageUrl" class="message-image" :url="message.imageUrl" :alt="`${message.authorName} 上传的图片`" />
          <div class="message-actions"><button type="button" @click="openReply(message)"><MessageCircleReply :size="15" />回复</button><button v-if="message.replyCount" type="button" :aria-expanded="expandedReplies.has(message.id)" @click="toggleReplies(message)"><ChevronUp v-if="expandedReplies.has(message.id)" :size="15" /><ChevronDown v-else :size="15" />{{ expandedReplies.has(message.id) ? '收起回复' : `查看 ${message.replyCount} 条回复` }}</button></div>

          <form v-if="replyTarget === message.id" class="reply-composer" @submit.prevent="submitReply(message.id)">
            <textarea v-model="replyContent" autofocus maxlength="2000" rows="2" :placeholder="`回复 ${message.authorName}…`" @keydown="onComposerKeydown($event, () => submitReply(message.id))" />
            <div v-if="replyPreview" class="image-preview compact"><img :src="replyPreview" alt="待发送回复图片预览" /><button type="button" aria-label="移除图片" @click="setImage(null, 'reply')"><X :size="15" /></button></div>
            <footer><input ref="replyFileInput" class="sr-only" type="file" accept="image/png,image/jpeg,image/gif,image/webp" @change="selectReplyImage" /><button class="attachment-button" type="button" @click="replyFileInput?.click()"><ImagePlus :size="16" />图片</button><span></span><button class="button button-primary" type="submit" :disabled="communication.mutating || (!replyContent.trim() && !replyFile)"><Send :size="15" />发送回复</button></footer>
          </form>

          <div v-if="expandedReplies.has(message.id)" class="reply-panel">
            <div v-if="replyState(message.id).loading && !replyState(message.id).items.length" class="reply-loading"><LoaderCircle class="spin" :size="17" />正在加载回复…</div>
            <article v-for="reply in replyState(message.id).items" :key="reply.id" class="reply-item">
              <span class="reply-avatar">{{ reply.authorName.charAt(0).toUpperCase() }}</span>
              <div><header><strong>{{ reply.authorName }}</strong><span v-if="reply.authorRole === 'ADMIN'"><ShieldCheck :size="11" />管理员</span><time :datetime="reply.createdAt">{{ formatDate(reply.createdAt) }}</time><button v-if="reply.viewerCanDelete" type="button" aria-label="删除回复" @click="removeMessage(reply)"><Trash2 :size="14" /></button></header><p v-if="reply.content">{{ reply.content }}</p><AuthenticatedImage v-if="reply.imageUrl" class="reply-image" :url="reply.imageUrl" :alt="`${reply.authorName} 上传的回复图片`" /></div>
            </article>
            <button v-if="replyState(message.id).nextPage !== null" class="load-replies" type="button" :disabled="replyState(message.id).loading" @click="loadMoreReplies(message.id)"><LoaderCircle v-if="replyState(message.id).loading" class="spin" :size="15" />加载更多回复</button>
          </div>
        </article>
      </TransitionGroup>

      <div ref="feedSentinel" class="feed-sentinel" aria-hidden="true"></div>
      <div v-if="communication.loadingMore" class="loading-more"><LoaderCircle class="spin" :size="18" />正在加载更多消息…</div>
      <p v-else-if="messages.length && !communication.hasMore" class="feed-end">已经看到全部消息了</p>
    </section>
  </div>
</template>

<style scoped>
.message-center-page { display: grid; gap: 16px; }
.message-center-page :deep(.page-header) { margin-bottom: 8px; }
.announcement-publisher, .announcement-board, .community-section { border: 1px solid var(--border); border-radius: 16px; background: var(--panel); }
.announcement-publisher { display: grid; grid-template-columns: minmax(260px, .72fr) minmax(520px, 1.45fr); gap: 34px; padding: 24px; border-color: var(--accent-border); background: var(--surface-accent); }
.publisher-copy { display: flex; align-items: flex-start; gap: 13px; }
.section-icon { width: 40px; height: 40px; display: grid; flex: 0 0 auto; place-items: center; color: var(--accent); border-radius: 11px; background: var(--accent-bg); }
.publisher-copy h2, .announcement-board h2, .community-heading h2 { margin: 0; font-size: var(--font-lg); }
.publisher-copy > div > p:last-child, .community-heading p { margin: 7px 0 0; color: var(--muted); font-size: var(--font-xs); line-height: 1.6; }
.announcement-publisher form { display: grid; gap: 12px; }
.publisher-actions { display: flex; align-items: center; justify-content: flex-end; gap: 14px; }
.publisher-actions > span { color: var(--muted); font-size: var(--font-2xs); }
.announcement-board > header, .community-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 20px 22px; border-bottom: 1px solid var(--border); }
.announcement-board > header > span, .community-heading > span { color: var(--muted); font-size: var(--font-xs); }
.community-heading > span { display: inline-flex; align-items: center; gap: 6px; }
.announcement-list { padding: 5px 15px; }
.announcement-list > article { display: grid; grid-template-columns: 38px minmax(0, 1fr) 34px; align-items: start; gap: 12px; padding: 15px 7px; border-bottom: 1px solid var(--border); }
.announcement-list > article:last-child { border-bottom: 0; }
.announcement-list > article.archived { opacity: .58; }
.announcement-mark { width: 34px; height: 34px; display: grid; place-items: center; color: var(--accent); border-radius: 9px; background: var(--accent-bg); }
.announcement-copy { min-width: 0; }
.announcement-copy > div { display: flex; align-items: center; gap: 8px; }
.announcement-copy h3 { margin: 0; font-size: var(--font-sm); }
.announcement-copy > div > span { padding: 3px 6px; color: var(--muted); border-radius: 99px; background: var(--surface-raised); font-size: 10px; }
.announcement-copy p { margin: 7px 0; color: var(--subtle); font-size: var(--font-xs); line-height: 1.65; white-space: pre-wrap; overflow-wrap: anywhere; }
.announcement-copy small { color: var(--muted); font-size: var(--font-2xs); }
.icon-button { width: 32px; height: 32px; display: grid; place-items: center; padding: 0; color: var(--muted); border: 1px solid transparent; border-radius: 8px; background: transparent; cursor: pointer; }
.icon-button:hover { color: var(--accent); border-color: var(--border); background: var(--surface-raised); }
.icon-button.danger:hover { color: var(--danger); border-color: color-mix(in srgb, var(--danger) 25%, var(--border)); }
.community-section { overflow: hidden; }
.message-composer { display: grid; grid-template-columns: 42px minmax(0, 1fr); gap: 12px; margin: 18px 20px 8px; padding: 17px; border: 1px solid var(--border); border-radius: 14px; background: var(--surface-raised); }
.composer-avatar, .message-avatar, .reply-avatar { display: grid; flex: 0 0 auto; place-items: center; color: var(--accent-contrast); background: var(--accent); font-weight: 750; }
.composer-avatar { width: 42px; height: 42px; border-radius: 12px; }
.composer-main { min-width: 0; }
.composer-main > textarea, .reply-composer textarea { width: 100%; resize: vertical; padding: 3px 2px 12px; color: var(--text); border: 0; outline: 0; background: transparent; line-height: 1.65; }
.composer-main > footer, .reply-composer footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-top: 10px; border-top: 1px solid var(--border); }
.composer-main > footer > div { display: flex; align-items: center; gap: 12px; }
.composer-main footer span { color: var(--muted); font-size: var(--font-2xs); }
.attachment-button, .message-actions button, .load-replies { display: inline-flex; align-items: center; gap: 6px; padding: 6px 8px; color: var(--muted); border: 0; border-radius: 7px; background: transparent; cursor: pointer; font-size: var(--font-xs); }
.attachment-button:hover, .message-actions button:hover, .load-replies:hover { color: var(--accent); background: var(--accent-bg); }
.image-preview { position: relative; width: min(380px, 100%); margin: 6px 0 12px; overflow: hidden; border: 1px solid var(--border); border-radius: 11px; background: var(--surface-sunken); }
.image-preview.compact { width: min(260px, 100%); }
.image-preview img { display: block; width: 100%; max-height: 240px; object-fit: contain; }
.image-preview button { position: absolute; top: 7px; right: 7px; width: 28px; height: 28px; display: grid; place-items: center; padding: 0; color: #fff; border: 0; border-radius: 8px; background: rgba(11,14,20,.76); cursor: pointer; }
.message-feed { padding: 8px 20px 0; }
.message-card { padding: 20px 0; border-bottom: 1px solid var(--border); }
.message-meta { display: flex; align-items: center; gap: 10px; }
.message-avatar { width: 38px; height: 38px; border-radius: 11px; }
.message-meta > div { min-width: 0; display: flex; align-items: center; flex-wrap: wrap; gap: 5px 8px; }
.message-meta strong { font-size: var(--font-sm); }
.message-meta span, .reply-item header span { display: inline-flex; align-items: center; gap: 3px; padding: 3px 6px; color: var(--accent); border-radius: 99px; background: var(--accent-bg); font-size: 10px; }
.message-meta time { flex-basis: 100%; color: var(--muted); font-size: var(--font-2xs); }
.message-meta > .icon-button { margin-left: auto; }
.message-body { margin: 13px 0 0 48px; color: var(--text); font-size: var(--font-sm); line-height: 1.72; white-space: pre-wrap; overflow-wrap: anywhere; }
.message-image { margin: 13px 0 0 48px; width: min(720px, calc(100% - 48px)); }
.message-actions { display: flex; align-items: center; gap: 5px; margin: 10px 0 0 40px; }
.reply-composer, .reply-panel { margin: 10px 0 0 48px; border: 1px solid var(--border); border-radius: 12px; background: var(--surface-sunken); }
.reply-composer { padding: 12px; }
.reply-composer footer { justify-content: flex-start; }
.reply-composer footer > span { flex: 1; }
.reply-panel { overflow: hidden; }
.reply-item { display: grid; grid-template-columns: 31px minmax(0, 1fr); gap: 9px; padding: 13px; border-bottom: 1px solid var(--border); }
.reply-item:last-of-type { border-bottom: 0; }
.reply-avatar { width: 31px; height: 31px; border-radius: 9px; font-size: var(--font-xs); }
.reply-item > div { min-width: 0; }
.reply-item header { display: flex; align-items: center; gap: 7px; }
.reply-item header strong { font-size: var(--font-xs); }
.reply-item header time { color: var(--muted); font-size: 10px; }
.reply-item header button { display: grid; place-items: center; margin-left: auto; padding: 3px; color: var(--muted); border: 0; border-radius: 5px; background: transparent; cursor: pointer; }
.reply-item header button:hover { color: var(--danger); background: color-mix(in srgb, var(--danger) 8%, transparent); }
.reply-item p { margin: 6px 0 0; color: var(--subtle); font-size: var(--font-xs); line-height: 1.65; white-space: pre-wrap; overflow-wrap: anywhere; }
.reply-image { width: min(520px, 100%); margin-top: 9px; }
.load-replies { margin: 7px auto; }
.reply-loading, .loading-state, .loading-more { display: flex; align-items: center; justify-content: center; gap: 8px; color: var(--muted); font-size: var(--font-xs); }
.reply-loading { min-height: 70px; }
.loading-state { min-height: 130px; }
.feed-loading { min-height: 260px; }
.loading-more { padding: 18px; }
.feed-sentinel { height: 1px; }
.feed-end { margin: 0; padding: 18px; color: var(--muted); text-align: center; font-size: var(--font-xs); }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
</style>
