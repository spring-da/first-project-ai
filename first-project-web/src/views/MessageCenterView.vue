<script setup lang="ts">
import {
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
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, reactive, ref, watch } from 'vue'
import AuthenticatedImage from '../components/AuthenticatedImage.vue'
import EmptyState from '../components/EmptyState.vue'
import LinkedText from '../components/LinkedText.vue'
import PageHeader from '../components/PageHeader.vue'
import UserAvatar from '../components/UserAvatar.vue'
import { useAuthStore } from '../stores/auth'
import { useCommunicationStore, type ReplyState } from '../stores/communication'
import { useConfirmationStore } from '../stores/confirmation'
import { useNotificationStore } from '../stores/notifications'
import type { CommunityMessage } from '../types'
import { imageSelectionError, optimizeImageFile } from '../utils/imageOptimization'

const auth = useAuthStore()
const communication = useCommunicationStore()
const confirmation = useConfirmationStore()
const notifications = useNotificationStore()
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
const messageOptimizing = ref(false)
const replyOptimizing = ref(false)
const sendingMessage = ref(false)
const sendingReply = ref(false)
const expandedReplies = reactive(new Set<string>())
const feedSentinel = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null
let messageImageVersion = 0
let replyImageVersion = 0

const currentIdentity = computed(() => auth.session?.user)

async function initialize() {
  try {
    await communication.loadMessages(true)
  } catch (error) {
    notifyError(error, '意见交流加载失败')
  }
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

async function selectMessageImage(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0] ?? null
  await prepareImage(file, 'message')
}

async function selectReplyImage(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0] ?? null
  await prepareImage(file, 'reply')
}

function pasteImage(event: ClipboardEvent, target: 'message' | 'reply') {
  const file = Array.from(event.clipboardData?.items ?? [])
    .find((item) => item.kind === 'file' && item.type.startsWith('image/'))?.getAsFile() ?? null
  if (!file) return
  event.preventDefault()
  void prepareImage(file, target)
}

async function prepareImage(file: File | null, target: 'message' | 'reply') {
  if (!file) {
    clearImage(target)
    return
  }
  const validationError = imageSelectionError(file)
  if (validationError) {
    notifications.notify(validationError, { type: 'warning' })
    clearImage(target)
    return
  }
  const version = target === 'message' ? ++messageImageVersion : ++replyImageVersion
  if (target === 'message') messageOptimizing.value = true
  else replyOptimizing.value = true
  try {
    const result = await optimizeImageFile(file)
    const currentVersion = target === 'message' ? messageImageVersion : replyImageVersion
    if (version !== currentVersion) return
    applyImage(result.file, target)
    if (result.optimized) {
      const savedPercent = Math.max(1, Math.round((1 - result.file.size / file.size) * 100))
      notifications.notify(`图片已优化，上传体积减少约 ${savedPercent}%`, { type: 'success' })
    }
  } catch (error) {
    const currentVersion = target === 'message' ? messageImageVersion : replyImageVersion
    if (version === currentVersion) {
      clearImage(target)
      notifications.notify(error instanceof Error ? error.message : '图片处理失败，请重新选择。', { type: 'warning' })
    }
  } finally {
    const currentVersion = target === 'message' ? messageImageVersion : replyImageVersion
    if (version === currentVersion) {
      if (target === 'message') messageOptimizing.value = false
      else replyOptimizing.value = false
    }
  }
}

function applyImage(file: File | null, target: 'message' | 'reply') {
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

function clearImage(target: 'message' | 'reply') {
  if (target === 'message') {
    messageImageVersion++
    messageOptimizing.value = false
  } else {
    replyImageVersion++
    replyOptimizing.value = false
  }
  applyImage(null, target)
}

async function submitMessage() {
  if (!messageContent.value.trim() && !messageFile.value) return
  if (messageOptimizing.value) return
  sendingMessage.value = true
  try {
    await communication.createMessage(messageContent.value.trim(), messageFile.value)
    messageContent.value = ''
    clearImage('message')
    notifications.notify('意见已发布，感谢你的分享。', { type: 'success' })
  } catch (error) {
    notifyError(error, '意见发布失败')
  } finally {
    sendingMessage.value = false
  }
}

async function submitReply(messageId: string) {
  if (!replyContent.value.trim() && !replyFile.value) return
  if (replyOptimizing.value) return
  sendingReply.value = true
  try {
    await communication.createReply(messageId, replyContent.value.trim(), replyFile.value)
    replyContent.value = ''
    clearImage('reply')
    replyTarget.value = null
    expandedReplies.add(messageId)
    notifications.notify('回复已发布。', { type: 'success' })
  } catch (error) {
    notifyError(error, '回复发布失败')
  } finally {
    sendingReply.value = false
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
    clearImage('reply')
  }
  replyTarget.value = nextTarget
  if (replyTarget.value) {
    expandedReplies.add(message.id)
    if (!communication.replies[message.id]?.loaded) {
      communication.loadReplies(message.id, true).catch((error) => notifyError(error, '回复加载失败'))
    }
  } else clearImage('reply')
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
onActivated(initialize)
onDeactivated(() => observer?.disconnect())
onBeforeUnmount(() => {
  observer?.disconnect()
  messageImageVersion++
  replyImageVersion++
  revokePreview(messagePreview)
  revokePreview(replyPreview)
})
</script>

<template>
  <div class="page-content message-center-page">
    <PageHeader
      eyebrow="COMMUNITY FEEDBACK"
      title="意见交流"
      description="分享使用感受、问题和建议，也可以回复其他成员的消息。"
    />

    <section class="community-section">
      <header class="community-heading"><div><p class="eyebrow">COMMUNITY FEEDBACK</p><h2>意见交流</h2><p>说说你的想法、遇到的问题，或者回复其他成员的建议。</p></div><span><MessagesSquare :size="16" />公开给所有已登录成员</span></header>

      <form class="message-composer" @submit.prevent="submitMessage">
        <UserAvatar class="composer-avatar" :name="currentIdentity?.displayName || '开发者'" :seed="currentIdentity?.id" />
        <div class="composer-main">
          <textarea v-model="messageContent" maxlength="2000" rows="3" placeholder="分享建议、问题或使用感受；可直接粘贴截图…" aria-label="意见内容" @paste="pasteImage($event, 'message')" @keydown="onComposerKeydown($event, submitMessage)" />
          <div v-if="messagePreview" class="image-preview"><img :src="messagePreview" alt="待发送图片预览" /><button type="button" aria-label="移除图片" @click="clearImage('message')"><X :size="15" /></button></div>
          <footer><div><input ref="messageFileInput" class="sr-only" type="file" accept="image/png,image/jpeg,image/gif,image/webp" @change="selectMessageImage" /><button class="attachment-button" type="button" :disabled="messageOptimizing" @click="messageFileInput?.click()"><LoaderCircle v-if="messageOptimizing" class="spin" :size="17" /><ImagePlus v-else :size="17" />{{ messageOptimizing ? '正在优化图片…' : '添加或粘贴图片' }}</button><span>{{ messageContent.length }} / 2000</span></div><button class="button button-primary" type="submit" :disabled="communication.mutating || messageOptimizing || (!messageContent.trim() && !messageFile)"><LoaderCircle v-if="sendingMessage" class="spin" :size="16" /><Send v-else :size="16" />{{ sendingMessage ? '正在上传…' : '发布意见' }}</button></footer>
        </div>
      </form>

      <div v-if="communication.messageLoading && !messages.length" class="loading-state feed-loading"><LoaderCircle class="spin" :size="22" />正在加载大家的消息…</div>
      <EmptyState v-else-if="!messages.length" title="还没有成员发言" description="发布第一条意见，开始一次有价值的交流。" />
      <TransitionGroup v-else name="list" tag="div" class="message-feed">
        <article v-for="message in messages" :key="message.id" class="message-card">
          <header class="message-meta"><UserAvatar class="message-avatar" :name="message.authorName" :seed="message.authorId" :url="message.authorAvatarUrl" /><div><strong>{{ message.authorName }}</strong><span v-if="message.authorRole === 'ADMIN'"><ShieldCheck :size="12" />管理员</span><time :datetime="message.createdAt">{{ formatDate(message.createdAt) }}</time></div><button v-if="message.viewerCanDelete" class="icon-button danger" type="button" :aria-label="`删除 ${message.authorName} 的消息`" @click="removeMessage(message)"><Trash2 :size="15" /></button></header>
          <p v-if="message.content" class="message-body"><LinkedText :text="message.content" /></p>
          <AuthenticatedImage v-if="message.imageUrl" class="message-image" :url="message.imageUrl" :alt="`${message.authorName} 上传的图片`" />
          <div class="message-actions"><button type="button" @click="openReply(message)"><MessageCircleReply :size="15" />回复</button><button v-if="message.replyCount" type="button" :aria-expanded="expandedReplies.has(message.id)" @click="toggleReplies(message)"><ChevronUp v-if="expandedReplies.has(message.id)" :size="15" /><ChevronDown v-else :size="15" />{{ expandedReplies.has(message.id) ? '收起回复' : `查看 ${message.replyCount} 条回复` }}</button></div>

          <form v-if="replyTarget === message.id" class="reply-composer" @submit.prevent="submitReply(message.id)">
            <textarea v-model="replyContent" autofocus maxlength="2000" rows="2" :placeholder="`回复 ${message.authorName}；可直接粘贴截图…`" @paste="pasteImage($event, 'reply')" @keydown="onComposerKeydown($event, () => submitReply(message.id))" />
            <div v-if="replyPreview" class="image-preview compact"><img :src="replyPreview" alt="待发送回复图片预览" /><button type="button" aria-label="移除图片" @click="clearImage('reply')"><X :size="15" /></button></div>
            <footer><input ref="replyFileInput" class="sr-only" type="file" accept="image/png,image/jpeg,image/gif,image/webp" @change="selectReplyImage" /><button class="attachment-button" type="button" :disabled="replyOptimizing" @click="replyFileInput?.click()"><LoaderCircle v-if="replyOptimizing" class="spin" :size="16" /><ImagePlus v-else :size="16" />{{ replyOptimizing ? '正在优化…' : '图片' }}</button><span></span><button class="button button-primary" type="submit" :disabled="communication.mutating || replyOptimizing || (!replyContent.trim() && !replyFile)"><LoaderCircle v-if="sendingReply" class="spin" :size="15" /><Send v-else :size="15" />{{ sendingReply ? '正在上传…' : '发送回复' }}</button></footer>
          </form>

          <div v-if="expandedReplies.has(message.id)" class="reply-panel">
            <div v-if="replyState(message.id).loading && !replyState(message.id).items.length" class="reply-loading"><LoaderCircle class="spin" :size="17" />正在加载回复…</div>
            <article v-for="reply in replyState(message.id).items" :key="reply.id" class="reply-item">
              <UserAvatar class="reply-avatar" :name="reply.authorName" :seed="reply.authorId" :url="reply.authorAvatarUrl" />
              <div><header><strong>{{ reply.authorName }}</strong><span v-if="reply.authorRole === 'ADMIN'"><ShieldCheck :size="11" />管理员</span><time :datetime="reply.createdAt">{{ formatDate(reply.createdAt) }}</time><button v-if="reply.viewerCanDelete" type="button" aria-label="删除回复" @click="removeMessage(reply)"><Trash2 :size="14" /></button></header><p v-if="reply.content"><LinkedText :text="reply.content" /></p><AuthenticatedImage v-if="reply.imageUrl" class="reply-image" :url="reply.imageUrl" :alt="`${reply.authorName} 上传的回复图片`" /></div>
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
.community-section { overflow: hidden; border: 1px solid var(--border); border-radius: 16px; background: var(--panel); }
.community-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 20px 22px; border-bottom: 1px solid var(--border); }
.community-heading h2 { margin: 0; font-size: var(--font-lg); }
.community-heading p { margin: 7px 0 0; color: var(--muted); font-size: var(--font-xs); line-height: 1.6; }
.community-heading > span { color: var(--muted); font-size: var(--font-xs); }
.community-heading > span { display: inline-flex; align-items: center; gap: 6px; }
.icon-button { width: 32px; height: 32px; display: grid; place-items: center; padding: 0; color: var(--muted); border: 1px solid transparent; border-radius: 8px; background: transparent; cursor: pointer; }
.icon-button:hover { color: var(--accent); border-color: var(--border); background: var(--surface-raised); }
.icon-button.danger:hover { color: var(--danger); border-color: color-mix(in srgb, var(--danger) 25%, var(--border)); }
.message-composer { display: grid; grid-template-columns: 42px minmax(0, 1fr); gap: 12px; margin: 18px 20px 8px; padding: 17px; border: 1px solid var(--border); border-radius: 14px; background: var(--surface-raised); }
.composer-avatar { width: 42px; height: 42px; border-radius: 12px; }
.composer-main { min-width: 0; }
.composer-main > textarea, .reply-composer textarea { width: 100%; resize: vertical; padding: 3px 2px 12px; color: var(--text); border: 0; outline: 0; background: transparent; line-height: 1.65; }
.composer-main > footer, .reply-composer footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-top: 10px; border-top: 1px solid var(--border); }
.composer-main > footer > div { display: flex; align-items: center; gap: 12px; }
.composer-main footer span { color: var(--muted); font-size: var(--font-2xs); }
.attachment-button, .message-actions button, .load-replies { display: inline-flex; align-items: center; gap: 6px; padding: 6px 8px; color: var(--muted); border: 0; border-radius: 7px; background: transparent; cursor: pointer; font-size: var(--font-xs); }
.attachment-button:hover, .message-actions button:hover, .load-replies:hover { color: var(--accent); background: var(--accent-bg); }
.attachment-button:disabled { opacity: .62; cursor: wait; }
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
.message-meta > div > span, .reply-item header span { display: inline-flex; align-items: center; gap: 3px; padding: 3px 6px; color: var(--accent); border-radius: 99px; background: var(--accent-bg); font-size: 10px; }
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
