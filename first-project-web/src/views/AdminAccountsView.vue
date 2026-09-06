<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Archive,
  Ban,
  BellRing,
  CheckCircle2,
  Clock3,
  Copy,
  Eye,
  FilePenLine,
  History,
  KeyRound,
  Link2,
  LoaderCircle,
  MailPlus,
  MessagesSquare,
  RefreshCw,
  Search,
  ShieldCheck,
  Trash2,
  UserCheck,
  UserRoundX,
  UsersRound,
} from 'lucide-vue-next'
import PageHeader from '../components/PageHeader.vue'
import AppModal from '../components/AppModal.vue'
import UserAvatar from '../components/UserAvatar.vue'
import { useAdminStore } from '../stores/admin'
import { useConfirmationStore } from '../stores/confirmation'
import { useNotificationStore } from '../stores/notifications'
import { useAuthStore } from '../stores/auth'
import type { AdminAccount } from '../types'

const admin = useAdminStore()
const auth = useAuthStore()
const router = useRouter()
const confirmation = useConfirmationStore()
const notifications = useNotificationStore()
const inviteEmail = ref('')
const search = ref('')
const filter = ref<'all' | 'registered' | 'pending' | 'disabled'>('all')
const auditExpanded = ref(false)
const oneTimeSecret = ref<{
  kind: '邀请链接' | '临时密码'
  subject: string
  value: string
  expiresAt: string
} | null>(null)

const registeredCount = computed(() => admin.accounts.filter((item) => item.registered).length)
const pendingCount = computed(() => admin.accounts.filter((item) => !item.registered).length)
const disabledCount = computed(() => admin.accounts.filter((item) => item.registered && !item.enabled).length)
const visibleAccounts = computed(() => {
  const term = search.value.trim().toLowerCase()
  return admin.accounts.filter((account) => {
    const matchesFilter = filter.value === 'all'
      || (filter.value === 'registered' && account.registered)
      || (filter.value === 'pending' && !account.registered)
      || (filter.value === 'disabled' && account.registered && !account.enabled)
    return matchesFilter && (!term || account.email.toLowerCase().includes(term)
      || account.displayName?.toLowerCase().includes(term))
  })
})
const visibleAuditEvents = computed(() => auditExpanded.value ? admin.auditEvents : admin.auditEvents.slice(0, 8))

const auditActionLabels = {
  INVITATION_CREATED: '创建注册邀请',
  INVITATION_ROTATED: '更新邀请链接',
  INVITATION_REVOKED: '撤销注册资格',
  ACCOUNT_ENABLED: '启用成员账户',
  ACCOUNT_DISABLED: '禁用成员账户',
  ACCOUNT_PASSWORD_RESET: '重置成员密码',
  ACCOUNT_DELETED: '永久删除成员',
  MEMBER_WORKSPACE_WRITE: '维护成员数据',
  ANNOUNCEMENT_PUBLISHED: '发布系统公告',
  ANNOUNCEMENT_ARCHIVED: '撤回系统公告',
  COMMUNITY_MESSAGE_MODERATED: '管理意见消息',
} as const

const auditActionIcons: Record<keyof typeof auditActionLabels, typeof History> = {
  INVITATION_CREATED: MailPlus,
  INVITATION_ROTATED: Link2,
  INVITATION_REVOKED: Trash2,
  ACCOUNT_ENABLED: UserCheck,
  ACCOUNT_DISABLED: Ban,
  ACCOUNT_PASSWORD_RESET: KeyRound,
  ACCOUNT_DELETED: Trash2,
  MEMBER_WORKSPACE_WRITE: FilePenLine,
  ANNOUNCEMENT_PUBLISHED: BellRing,
  ANNOUNCEMENT_ARCHIVED: Archive,
  COMMUNITY_MESSAGE_MODERATED: MessagesSquare,
}

const resourceLabels: Record<string, string> = {
  tasks: '任务', projects: '项目', domains: '知识目录', 'knowledge-items': '知识条目',
  'markdown-documents': 'Markdown 文章', 'markdown-images': '文章图片', snippets: '代码片段',
  logs: '开发日志', profile: '个人资料', invitations: '注册邀请', accounts: '账户',
  announcements: '系统公告', 'community-messages': '意见消息',
}


function formatDate(value: string | null) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

async function load(showSuccess = false) {
  const [accountsResult, auditResult] = await Promise.allSettled([admin.load(), admin.loadAudit()])
  if (accountsResult.status === 'rejected') notifications.notify(admin.error, { type: 'error' })
  if (auditResult.status === 'rejected') notifications.notify(admin.auditError, { type: 'warning' })
  if (showSuccess && accountsResult.status === 'fulfilled' && auditResult.status === 'fulfilled') {
    notifications.notify('人员名单和操作记录已刷新。', { type: 'success' })
  }
}

function refreshAudit() {
  void admin.loadAudit().catch(() => undefined)
}

async function refreshAuditFromButton() {
  try {
    await admin.loadAudit()
    notifications.notify('管理员操作记录已刷新。', { type: 'success' })
  } catch {
    notifications.notify(admin.auditError, { type: 'error' })
  }
}

function auditTitle(action: keyof typeof auditActionLabels, resourceType: string, method: string) {
  if (action !== 'MEMBER_WORKSPACE_WRITE') return auditActionLabels[action]
  const operation = method === 'POST' ? '新增/执行' : method === 'DELETE' ? '删除' : '修改'
  return `${operation}${resourceLabels[resourceType] || '工作区数据'}`
}

async function invite() {
  if (!inviteEmail.value.trim()) return
  try {
    const result = await admin.invite(inviteEmail.value)
    inviteEmail.value = ''
    oneTimeSecret.value = {
      kind: '邀请链接',
      subject: result.account.email,
      value: invitationLink(result.invitationToken),
      expiresAt: result.expiresAt,
    }
    notifications.notify(`已生成 ${result.account.email} 的一次性邀请链接。`, { type: 'success' })
    refreshAudit()
  } catch {
    notifications.notify(admin.error, { type: 'error' })
  }
}

function invitationLink(token: string) {
  const url = new URL('/login', window.location.origin)
  url.hash = new URLSearchParams({ invitation: token }).toString()
  return url.toString()
}

async function copyOneTimeSecret() {
  if (!oneTimeSecret.value) return
  try {
    if (window.isSecureContext && navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(oneTimeSecret.value.value)
    } else {
      const field = document.createElement('textarea')
      field.value = oneTimeSecret.value.value
      field.style.position = 'fixed'
      field.style.opacity = '0'
      document.body.appendChild(field)
      field.select()
      document.execCommand('copy')
      field.remove()
    }
    notifications.notify(`${oneTimeSecret.value.kind}已复制。`, { type: 'success' })
  } catch {
    notifications.notify('自动复制失败，请手动选择并复制。', { type: 'warning' })
  }
}

function selectSecret(event: FocusEvent) {
  if (event.target instanceof HTMLTextAreaElement) event.target.select()
}

async function rotateInvitation(account: AdminAccount) {
  if (!account.invitationId || !await confirmation.ask({
    title: account.invitationExpired ? '生成新的邀请链接？' : '替换当前邀请链接？',
    message: `${account.email} 将获得一个新的单次注册链接。`,
    detail: '之前生成的链接会立即失效，新链接只显示一次。',
    confirmText: '生成新链接',
    tone: 'warning',
    icon: 'info',
  })) return
  try {
    const result = await admin.rotateInvitation(account.invitationId)
    oneTimeSecret.value = {
      kind: '邀请链接',
      subject: result.account.email,
      value: invitationLink(result.invitationToken),
      expiresAt: result.expiresAt,
    }
    notifications.notify('新的邀请链接已生成，旧链接已失效。', { type: 'success' })
    refreshAudit()
  } catch {
    notifications.notify(admin.error, { type: 'error' })
  }
}

async function revoke(account: AdminAccount) {
  if (!account.invitationId || !await confirmation.ask({
    title: '撤销注册资格？',
    message: `${account.email} 将无法再注册 DevNest。`,
    confirmText: '撤销资格',
    tone: 'warning',
    icon: 'delete',
  })) return
  try {
    await admin.revokeInvitation(account.invitationId)
    notifications.notify(`已撤销 ${account.email} 的注册资格。`, { type: 'success' })
    refreshAudit()
  } catch {
    notifications.notify(admin.error, { type: 'error' })
  }
}

async function toggleEnabled(account: AdminAccount) {
  if (!account.userId) return
  const nextEnabled = !account.enabled
  if (!await confirmation.ask({
    title: nextEnabled ? '启用这个账户？' : '禁用这个账户？',
    message: nextEnabled
      ? `${account.email} 将恢复登录和访问工作区的权限。`
      : `${account.email} 的现有登录会话会在下一次请求时立即失效。`,
    confirmText: nextEnabled ? '确认启用' : '确认禁用',
    tone: nextEnabled ? 'info' : 'warning',
    icon: 'info',
  })) return
  try {
    await admin.setEnabled(account.userId, nextEnabled)
    notifications.notify(`账户已${nextEnabled ? '启用' : '禁用'}。`, { type: 'success' })
    refreshAudit()
  } catch {
    notifications.notify(admin.error, { type: 'error' })
  }
}

async function resetPassword(account: AdminAccount) {
  if (!account.userId || !await confirmation.ask({
    title: '重置该用户的密码？',
    message: `系统将为 ${account.email} 生成一个高强度随机临时密码。`,
    detail: '旧登录会话立即失效；临时密码 30 分钟后过期，且用户登录后必须马上改密。',
    confirmText: '确认重置',
    tone: 'warning',
    icon: 'info',
  })) return
  try {
    const result = await admin.resetPassword(account.userId)
    oneTimeSecret.value = {
      kind: '临时密码',
      subject: account.email,
      value: result.temporaryPassword,
      expiresAt: result.expiresAt,
    }
    notifications.notify('随机临时密码已生成，请立即通过安全渠道告知用户。', { type: 'success' })
    refreshAudit()
  } catch {
    notifications.notify(admin.error, { type: 'error' })
  }
}

async function deleteAccount(account: AdminAccount) {
  if (!account.userId || !await confirmation.ask({
    title: '永久删除这个账户？',
    message: `${account.email} 的账户和全部工作区数据将被永久删除。`,
    detail: '此操作不可撤销。删除后，该邮箱也不会自动获得再次注册的资格。',
    confirmText: '永久删除',
    tone: 'danger',
    icon: 'delete',
  })) return
  try {
    await admin.deleteAccount(account.userId)
    notifications.notify(`已永久删除 ${account.email}。`, { type: 'success' })
    refreshAudit()
  } catch {
    notifications.notify(admin.error, { type: 'error' })
  }
}

function openWorkspace(account: AdminAccount) {
  if (account.userId) void router.push({ name: 'admin-member-workspace', params: { userId: account.userId } })
}

onMounted(() => load())
</script>

<template>
  <div class="page-content admin-page">
    <PageHeader eyebrow="ADMINISTRATION" title="人员管理" description="控制谁可以注册、登录和继续使用 DevNest。">
      <button class="button button-secondary" type="button" :disabled="admin.loading" @click="load(true)"><RefreshCw :size="16" :class="{ spin: admin.loading }" />刷新名单</button>
    </PageHeader>

    <section class="admin-overview">
      <div><span class="metric-icon"><UsersRound :size="19" /></span><strong>{{ registeredCount }}</strong><small>已注册账户</small></div>
      <div><span class="metric-icon"><MailPlus :size="19" /></span><strong>{{ pendingCount }}</strong><small>待注册邮箱</small></div>
      <div><span class="metric-icon danger"><UserRoundX :size="19" /></span><strong>{{ disabledCount }}</strong><small>已禁用账户</small></div>
      <p><ShieldCheck :size="17" />注册必须持有管理员生成的单次邀请链接，链接过期或使用后立即失效。</p>
    </section>

    <section class="invite-panel">
      <div><p class="eyebrow">REGISTRATION ACCESS</p><h2>生成注册邀请</h2><p>添加邮箱后复制一次性链接，并通过可信渠道发送给对方。</p></div>
      <form @submit.prevent="invite"><label><span class="sr-only">邮箱地址</span><input v-model.trim="inviteEmail" type="email" maxlength="190" required autocomplete="off" placeholder="friend@example.com" /></label><button class="button button-primary" type="submit" :disabled="admin.mutating"><MailPlus :size="16" />添加邮箱</button></form>
    </section>

    <section class="accounts-panel">
      <header>
        <div><p class="eyebrow">ACCESS DIRECTORY</p><h2>账户与注册名单</h2></div>
        <div class="account-tools">
          <label class="account-search"><Search :size="16" /><span class="sr-only">搜索人员</span><input v-model="search" type="search" placeholder="搜索邮箱或名称" /></label>
          <div class="filters" role="group" aria-label="筛选人员">
            <button v-for="option in ([['all', '全部'], ['registered', '已注册'], ['pending', '待注册'], ['disabled', '已禁用']] as const)" :key="option[0]" type="button" :class="{ active: filter === option[0] }" :aria-pressed="filter === option[0]" @click="filter = option[0]">{{ option[1] }}</button>
          </div>
        </div>
      </header>

      <div v-if="admin.loading && !admin.accounts.length" class="account-state"><LoaderCircle class="spin" :size="22" />正在加载人员名单…</div>
      <div v-else-if="!visibleAccounts.length" class="account-state">没有符合条件的人员记录。</div>
      <template v-else>
        <div class="account-list-heading" aria-hidden="true"><span></span><span>成员</span><span>状态</span><span>可用操作</span></div>
        <TransitionGroup name="list" tag="div" class="account-list">
        <article v-for="account in visibleAccounts" :key="account.userId || account.invitationId || account.email" class="account-row" :class="{ disabled: account.registered && !account.enabled }">
          <UserAvatar class="account-avatar" :name="account.displayName || account.email" :seed="account.userId || account.email" />
          <div class="account-identity"><strong>{{ account.displayName || '等待注册' }}</strong><span>{{ account.email }}</span><small>{{ account.registered ? `注册于 ${formatDate(account.registeredAt)}` : account.invitationExpired ? `邀请已过期 · 创建于 ${formatDate(account.invitedAt)}` : `有效至 ${formatDate(account.invitationExpiresAt)}` }}</small></div>
          <div class="account-status">
            <span v-if="account.role === 'ADMIN'" class="status admin"><ShieldCheck :size="13" />管理员</span>
            <span v-else-if="account.invitationExpired" class="status disabled"><Clock3 :size="13" />邀请已过期</span>
            <span v-else-if="!account.registered" class="status pending"><MailPlus :size="13" />待注册</span>
            <span v-else-if="!account.enabled" class="status disabled"><Ban :size="13" />已禁用</span>
            <span v-else class="status enabled"><CheckCircle2 :size="13" />使用中</span>
            <span v-if="account.mustChangePassword" class="status password"><KeyRound :size="13" />待改密码</span>
          </div>
          <div class="account-actions">
            <button v-if="account.registered && account.userId" class="button button-ghost" type="button" @click="openWorkspace(account)"><Eye :size="15" />模拟登录 / 管理数据</button>
            <span v-if="account.userId === auth.session?.user.id" class="current-account">当前账户</span>
            <template v-else-if="account.registered && account.role !== 'ADMIN'">
              <button class="button button-ghost" type="button" :disabled="admin.mutating" @click="toggleEnabled(account)"><UserCheck v-if="!account.enabled" :size="15" /><Ban v-else :size="15" />{{ account.enabled ? '禁用' : '启用' }}</button>
              <button class="button button-ghost" type="button" :disabled="admin.mutating" @click="resetPassword(account)"><KeyRound :size="15" />重置密码</button>
              <button class="icon-action danger" type="button" :disabled="admin.mutating" aria-label="永久删除账户" title="永久删除账户" @click="deleteAccount(account)"><Trash2 :size="16" /></button>
            </template>
            <template v-else-if="!account.registered">
              <button class="button button-ghost" type="button" :disabled="admin.mutating" @click="rotateInvitation(account)"><Link2 :size="15" />{{ account.invitationExpired ? '生成新链接' : '替换链接' }}</button>
              <button class="button button-ghost danger-text" type="button" :disabled="admin.mutating" @click="revoke(account)"><Trash2 :size="15" />撤销资格</button>
            </template>
          </div>
        </article>
        </TransitionGroup>
      </template>
    </section>

    <section class="audit-panel">
      <header>
        <div><p class="eyebrow">ADMIN AUDIT TRAIL</p><h2>管理员操作记录</h2><span>保留账号管理与代管成员数据的结果，不保存敏感正文或一次性密钥。</span></div>
        <button class="button button-ghost" type="button" :disabled="admin.auditLoading" @click="refreshAuditFromButton"><RefreshCw :size="15" :class="{ spin: admin.auditLoading }" />刷新记录</button>
      </header>
      <div v-if="admin.auditLoading && !admin.auditEvents.length" class="audit-state"><LoaderCircle class="spin" :size="20" />正在读取操作记录…</div>
      <div v-else-if="admin.auditError && !admin.auditEvents.length" class="audit-state">{{ admin.auditError }}</div>
      <div v-else-if="!admin.auditEvents.length" class="audit-state"><History :size="19" />尚无管理员操作记录。</div>
      <div v-else class="audit-list">
        <article v-for="event in visibleAuditEvents" :key="event.id" :title="event.requestPath">
          <span class="audit-result" :class="{ failed: !event.success }" :aria-label="auditTitle(event.action, event.resourceType, event.httpMethod)">
            <component :is="auditActionIcons[event.action]" :size="18" />
          </span>
          <div><strong>{{ auditTitle(event.action, event.resourceType, event.httpMethod) }}</strong><span>{{ event.actorEmail }} → {{ event.targetLabel || event.targetId || '系统' }}</span></div>
          <small>{{ event.success ? '成功' : `失败 · HTTP ${event.responseStatus}` }}</small>
          <time :datetime="event.createdAt">{{ formatDate(event.createdAt) }}</time>
        </article>
        <button v-if="admin.auditEvents.length > 8" class="audit-more" type="button" @click="auditExpanded = !auditExpanded">{{ auditExpanded ? '收起记录' : `查看全部 ${admin.auditEvents.length} 条记录` }}</button>
      </div>
    </section>

    <AppModal
      v-if="oneTimeSecret"
      :title="`${oneTimeSecret.kind}已生成`"
      description="该敏感信息仅在本次弹窗中显示，关闭后无法再次读取。"
      @close="oneTimeSecret = null"
    >
      <div class="secret-summary"><ShieldCheck :size="18" /><div><strong>{{ oneTimeSecret.subject }}</strong><span>有效至 {{ formatDate(oneTimeSecret.expiresAt) }}</span></div></div>
      <label class="secret-field"><span>{{ oneTimeSecret.kind }}</span><textarea :value="oneTimeSecret.value" readonly rows="3" @focus="selectSecret" /></label>
      <p class="secret-warning"><Clock3 :size="16" />请立即复制并通过可信渠道发送；不要截图保存在公共相册或群聊中。</p>
      <div class="secret-actions"><button class="button button-primary" type="button" @click="copyOneTimeSecret"><Copy :size="16" />复制{{ oneTimeSecret.kind }}</button><button class="button button-secondary" type="button" @click="oneTimeSecret = null">我已妥善保存</button></div>
    </AppModal>

  </div>
</template>

<style scoped>
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
.admin-page { width: 100%; max-width: none; }
.admin-overview { display: grid; grid-template-columns: repeat(3, minmax(150px, 1fr)) minmax(260px, 1.4fr); gap: 12px; }
.admin-overview > div, .admin-overview > p { min-height: 120px; margin: 0; padding: 18px; border: 1px solid var(--border); border-radius: 14px; background: var(--panel); transition: border-color var(--motion-fast), transform var(--motion-base) var(--ease-emphasized), box-shadow var(--motion-base); }
.admin-overview > div:hover { border-color: var(--border-strong); box-shadow: var(--shadow-sm); transform: translateY(-2px); }
.admin-overview > div strong, .admin-overview > div small { display: block; }
.admin-overview > div strong { margin-top: 13px; font-size: 22px; }
.admin-overview > div small { margin-top: 3px; color: var(--muted); font-size: var(--font-xs); }
.metric-icon { width: 33px; height: 33px; display: grid; place-items: center; color: var(--accent); border-radius: 9px; background: var(--accent-bg); }
.metric-icon.danger { color: var(--danger); background: color-mix(in srgb, var(--danger) 10%, transparent); }
.admin-overview > p { display: flex; align-items: center; gap: 11px; color: var(--muted); font-size: var(--font-sm); line-height: 1.65; background: var(--surface-accent); }
.admin-overview > p svg { flex-shrink: 0; color: var(--accent); }
.invite-panel { display: flex; align-items: center; justify-content: space-between; gap: 30px; margin-top: 14px; padding: 23px; border: 1px solid var(--accent-border); border-radius: 15px; background: var(--surface-accent); }
.invite-panel h2, .accounts-panel h2 { margin: 0; font-size: var(--font-lg); }
.invite-panel > div > p:last-child { margin: 7px 0 0; color: var(--muted); font-size: var(--font-xs); }
.invite-panel form { width: min(760px, 58%); display: grid; grid-template-columns: 1fr auto; gap: 9px; }
.invite-panel input { width: 100%; height: var(--control-height); padding: 0 14px; color: var(--text); border: 1px solid var(--border-strong); border-radius: 10px; outline: none; background: var(--panel); }
.invite-panel input:focus, .account-search:focus-within { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.accounts-panel { margin-top: 14px; border: 1px solid var(--border); border-radius: 15px; overflow: hidden; background: var(--panel); }
.accounts-panel > header { display: flex; align-items: center; justify-content: space-between; gap: 25px; padding: 22px; border-bottom: 1px solid var(--border); }
.account-tools { display: flex; align-items: center; gap: 9px; }
.account-search { width: clamp(220px, 18vw, 360px); display: flex; align-items: center; gap: 7px; padding: 0 10px; color: var(--muted); border: 1px solid var(--border); border-radius: 9px; transition: border-color var(--motion-fast), box-shadow var(--motion-fast), background var(--motion-fast); }
.account-search input { min-width: 0; width: 100%; height: 34px; color: var(--text); border: 0; outline: none; background: transparent; font-size: var(--font-xs); }
.filters { display: flex; gap: 3px; padding: 3px; border: 1px solid var(--border); border-radius: 9px; background: var(--surface-sunken); }
.filters button { padding: 7px 9px; color: var(--muted); border: 0; border-radius: 6px; background: transparent; cursor: pointer; font-size: var(--font-2xs); transition: color var(--motion-fast), background var(--motion-fast), transform var(--motion-fast); }
.filters button:active { transform: scale(.96); }
.filters button.active { color: var(--accent); background: var(--accent-bg); }
.account-list { padding: 5px 14px; }
.account-list-heading { display: grid; grid-template-columns: 43px minmax(240px, 1.2fr) minmax(180px, .65fr) minmax(300px, auto); gap: 14px; padding: 9px 22px; color: var(--muted); border-bottom: 1px solid var(--border); background: color-mix(in srgb, var(--surface-sunken) 72%, transparent); font-size: 10px; font-weight: 700; letter-spacing: .08em; }
.account-list-heading span:nth-child(3) { padding-left: 8px; }
.account-list-heading span:last-child { text-align: right; }
.account-row { display: grid; grid-template-columns: 43px minmax(240px, 1.2fr) minmax(180px, .65fr) minmax(300px, auto); align-items: center; gap: 14px; min-height: 86px; padding: 12px 8px; border-bottom: 1px solid var(--border); transition: background var(--motion-fast), opacity var(--motion-base), transform var(--motion-slow) var(--ease-emphasized); }
.account-row:hover { background: color-mix(in srgb, var(--surface-raised) 62%, transparent); }
.account-row:last-child { border-bottom: 0; }
.account-row.disabled { opacity: .76; }
.account-avatar { width: 39px; height: 39px; border-radius: 11px; }
.account-identity { min-width: 0; }
.account-identity strong, .account-identity span, .account-identity small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.account-identity strong { font-size: var(--font-sm); }
.account-identity span { margin-top: 3px; color: var(--subtle); font-size: var(--font-xs); }
.account-identity small { margin-top: 4px; color: var(--muted); font-size: var(--font-2xs); }
.account-status { min-height: 30px; display: flex; align-items: center; flex-wrap: wrap; gap: 6px; padding-left: 8px; }
.status { min-width: 88px; display: inline-flex; align-items: center; justify-content: center; gap: 5px; padding: 5px 8px; border-radius: 99px; font-size: var(--font-2xs); }
.status.enabled { color: var(--success); background: color-mix(in srgb, var(--success) 10%, transparent); }
.status.pending, .status.password { color: var(--warning); background: color-mix(in srgb, var(--warning) 10%, transparent); }
.status.disabled { color: var(--danger); background: color-mix(in srgb, var(--danger) 10%, transparent); }
.status.admin { color: var(--accent); background: var(--accent-bg); }
.account-actions { display: flex; align-items: center; justify-content: flex-end; gap: 5px; }
.account-actions .button { min-height: 34px; padding: 7px 9px; font-size: var(--font-2xs); }
.icon-action { width: 34px; height: 34px; display: grid; place-items: center; padding: 0; color: var(--muted); border: 1px solid transparent; border-radius: 8px; background: transparent; cursor: pointer; }
.icon-action.danger, .danger-text { color: var(--danger) !important; }
.icon-action:hover { border-color: color-mix(in srgb, var(--danger) 25%, var(--border)); background: color-mix(in srgb, var(--danger) 7%, transparent); }
.current-account { padding: 6px 9px; color: var(--muted); border: 1px solid var(--border); border-radius: 99px; font-size: var(--font-2xs); }
.account-state { min-height: 180px; display: flex; align-items: center; justify-content: center; gap: 9px; color: var(--muted); font-size: var(--font-sm); }
.audit-panel { margin-top: 14px; border: 1px solid var(--border); border-radius: 15px; overflow: hidden; background: var(--panel); }
.audit-panel > header { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 21px 22px; border-bottom: 1px solid var(--border); }
.audit-panel h2 { margin: 0; font-size: var(--font-lg); }
.audit-panel header span { display: block; margin-top: 6px; color: var(--muted); font-size: var(--font-xs); }
.audit-state { min-height: 112px; display: flex; align-items: center; justify-content: center; gap: 8px; color: var(--muted); font-size: var(--font-sm); }
.audit-list { padding: 4px 14px; }
.audit-list article { min-height: 68px; display: grid; grid-template-columns: 48px minmax(260px, 1fr) 110px 155px; align-items: center; gap: 12px; padding: 10px 8px; border-bottom: 1px solid var(--border); }
.audit-result { position: relative; width: 40px; height: 40px; justify-self: center; align-self: center; color: var(--accent); border: 1px solid var(--accent-border); border-radius: 11px; background: linear-gradient(145deg, var(--accent-bg), var(--panel)); box-shadow: inset 0 1px 0 color-mix(in srgb, #fff 45%, transparent); }
.audit-result :deep(svg) { position: absolute; top: 50%; left: 50%; display: block; margin: 0; transform: translate(-50%, -50%); }
.audit-result.failed { color: var(--danger); border-color: color-mix(in srgb, var(--danger) 22%, var(--border)); background: color-mix(in srgb, var(--danger) 8%, var(--panel)); }
.audit-list article div { min-width: 0; }
.audit-list article strong, .audit-list article span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.audit-list article strong { font-size: var(--font-xs); }
.audit-list article div span, .audit-list article small, .audit-list article time { color: var(--muted); font-size: var(--font-2xs); }
.audit-list article div span { margin-top: 4px; }
.audit-list article small { text-align: right; }
.audit-list article time { text-align: right; white-space: nowrap; }
.audit-more { width: 100%; padding: 11px; color: var(--accent); border: 0; background: transparent; cursor: pointer; font-size: var(--font-xs); }
.audit-more:hover { background: var(--accent-bg); }
.secret-summary { display: flex; align-items: center; gap: 11px; padding: 13px; color: var(--accent); border: 1px solid var(--accent-border); border-radius: 11px; background: var(--accent-bg); }
.secret-summary div { min-width: 0; }
.secret-summary strong, .secret-summary span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.secret-summary span { margin-top: 3px; color: var(--muted); font-size: var(--font-xs); }
.secret-field { display: grid; gap: 8px; margin-top: 18px; color: var(--subtle); font-size: var(--font-xs); font-weight: 650; }
.secret-field textarea { width: 100%; resize: none; padding: 13px; color: var(--text); border: 1px solid var(--border-strong); border-radius: 10px; outline: 0; background: var(--surface-sunken); font: 600 var(--font-sm)/1.55 ui-monospace, SFMono-Regular, Consolas, monospace; overflow-wrap: anywhere; }
.secret-field textarea:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.secret-warning { display: flex; align-items: flex-start; gap: 8px; margin: 14px 0 0; color: var(--warning); font-size: var(--font-xs); line-height: 1.55; }
.secret-warning svg { flex-shrink: 0; }
.secret-actions { display: flex; justify-content: flex-end; gap: 9px; margin-top: 22px; }
@media (min-width: 1800px) { .admin-overview { grid-template-columns: repeat(3, minmax(200px, .75fr)) minmax(420px, 1.5fr); gap: 16px; } .invite-panel, .accounts-panel > header { padding-inline: 28px; } .account-list { padding-inline: 20px; } .account-list-heading, .account-row { grid-template-columns: 43px minmax(300px, 1.25fr) minmax(220px, .65fr) minmax(340px, auto); column-gap: 20px; } }
@media (max-width: 1100px) { .admin-overview { grid-template-columns: repeat(3, 1fr); } .admin-overview > p { grid-column: 1 / -1; min-height: auto; } .accounts-panel > header { align-items: stretch; flex-direction: column; } .account-tools { justify-content: space-between; } .account-list-heading { display: none; } .account-row { grid-template-columns: 43px 1fr auto; } .account-status { justify-content: flex-end; } .account-actions { grid-column: 2 / -1; justify-content: flex-start; } }
@media (max-width: 720px) { .admin-overview { grid-template-columns: repeat(3, 1fr); gap: 7px; } .admin-overview > div { min-width: 0; min-height: 105px; padding: 13px 10px; } .admin-overview > div small { font-size: 10px; } .admin-overview > p { grid-column: 1 / -1; padding: 14px; } .invite-panel { align-items: stretch; flex-direction: column; gap: 17px; padding: 19px; } .invite-panel form { width: 100%; grid-template-columns: 1fr; } .account-tools { align-items: stretch; flex-direction: column; } .account-search { width: 100%; } .filters { display: grid; grid-template-columns: repeat(4, 1fr); } .filters button { padding-inline: 3px; } .accounts-panel > header { padding: 18px; } .account-list { padding-inline: 10px; } .account-row { grid-template-columns: 39px minmax(0, 1fr); gap: 10px; padding-block: 15px; } .account-status, .account-actions { grid-column: 2; justify-content: flex-start; } .account-actions { flex-wrap: wrap; } .secret-actions { align-items: stretch; flex-direction: column; } .secret-actions .button { justify-content: center; } }
</style>
