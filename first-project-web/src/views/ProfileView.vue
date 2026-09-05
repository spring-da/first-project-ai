<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { BookOpen, CheckCircle2, Cloud, Code2, Edit3, FolderKanban, KeyRound, LogOut, Mail, Moon, RefreshCw, ShieldCheck, ShieldOff, Sun } from 'lucide-vue-next'
import AppModal from '../components/AppModal.vue'
import PageHeader from '../components/PageHeader.vue'
import WorkspaceModuleState from '../components/WorkspaceModuleState.vue'
import { useAuthStore } from '../stores/auth'
import { useThemeStore } from '../stores/theme'
import { useWorkspaceStore } from '../stores/workspace'
import { useConfirmationStore } from '../stores/confirmation'
import { useNotificationStore } from '../stores/notifications'
import type { ProfileDraft } from '../types'

const router = useRouter()
const auth = useAuthStore()
const theme = useThemeStore()
const workspace = useWorkspaceStore()
const confirmation = useConfirmationStore()
const notifications = useNotificationStore()
const showEditor = ref(false)
const draft = reactive<ProfileDraft>({ name: '', role: '', bio: '', avatarUrl: null })
const displayName = computed(() => workspace.profile?.name || auth.workspaceUser?.displayName || '开发者')
const initial = computed(() => displayName.value.charAt(0).toUpperCase() || 'D')
const syncTime = computed(() => workspace.lastSyncedAt
  ? new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(workspace.lastSyncedAt)
  : '尚未同步')

function openEditor() {
  Object.assign(draft, {
    name: workspace.profile?.name || auth.workspaceUser?.displayName || '',
    role: workspace.profile?.role || 'Independent Developer',
    bio: workspace.profile?.bio || '持续构建，持续学习。',
    avatarUrl: workspace.profile?.avatarUrl || null,
  })
  showEditor.value = true
}

async function saveProfile() {
  await workspace.saveProfile({ ...draft, avatarUrl: draft.avatarUrl?.trim() || null })
  showEditor.value = false
}

async function logout() {
  if (auth.workspaceMember) { await router.push({ name: 'admin-accounts' }); return }
  if (!await confirmation.ask({
    title: '退出当前账户？',
    message: '退出后，你需要重新登录才能访问个人工作空间。',
    detail: '已保存到云端的内容不会受到影响。本机草稿会保留在当前浏览器中。',
    confirmText: '退出登录', cancelText: '继续使用', icon: 'logout',
  })) return
  auth.clear()
  workspace.clear()
  router.replace('/login')
}

async function logoutAll() {
  if (auth.workspaceMember || !await confirmation.ask({
    title: '退出所有登录设备？',
    message: '你的全部登录令牌会立即失效，包括当前浏览器和其他设备。',
    detail: '本机尚未提交到云端的草稿仍会保留。完成后需要重新登录。',
    confirmText: '全部退出', cancelText: '取消', tone: 'warning', icon: 'logout',
  })) return
  if (!await auth.logoutAll()) {
    notifications.notify(auth.error || '登录会话注销失败，请稍后重试。', { type: 'error' })
    return
  }
  workspace.clear()
  await router.replace('/login')
  notifications.notify('所有设备的登录会话均已注销。', { type: 'success' })
}
</script>

<template>
  <div class="page-content">
    <PageHeader eyebrow="DEVELOPER PROFILE" title="我的" description="查看你的开发足迹，并管理多端工作区。">
      <button class="button button-secondary" type="button" @click="openEditor"><Edit3 :size="16" />编辑资料</button>
    </PageHeader>
    <WorkspaceModuleState module="profile" title="个人资料" :has-data="Boolean(workspace.profile)" />

    <section class="profile-hero">
      <div class="profile-avatar">
        <img v-if="workspace.profile?.avatarUrl" :src="workspace.profile.avatarUrl" alt="开发者头像" />
        <span v-else>{{ initial }}</span>
      </div>
      <div class="profile-copy"><p class="eyebrow">SPRINGDA EDITION</p><h2>{{ displayName }}</h2><strong>{{ workspace.profile?.role || 'Independent Developer' }}</strong><p>{{ workspace.profile?.bio || '持续构建，持续学习。' }}</p></div>
      <div class="profile-account"><span><Mail :size="15" />{{ auth.workspaceUser?.email }}</span><span><ShieldCheck :size="15" />账户已通过 JWT 安全认证</span></div>
    </section>

    <section class="profile-grid">
      <article class="profile-panel stats-panel">
        <div class="panel-title"><div><p class="eyebrow">YOUR FOOTPRINT</p><h2>数据足迹</h2></div><span>与 Android App 实时共享</span></div>
        <div class="stats-errors">
          <WorkspaceModuleState module="projects" title="项目统计" :has-data="Boolean(workspace.projects.length)" compact />
          <WorkspaceModuleState module="snippets" title="片段统计" :has-data="Boolean(workspace.snippets.length)" compact />
          <WorkspaceModuleState module="logs" title="日志统计" :has-data="Boolean(workspace.logs.length)" compact />
          <WorkspaceModuleState module="tasks" title="任务统计" :has-data="Boolean(workspace.tasks.length)" compact />
        </div>
        <div class="stat-grid">
          <div><span class="stat-icon"><FolderKanban :size="18" /></span><strong>{{ !workspace.projects.length && (workspace.moduleStates.projects.loading || workspace.moduleStates.projects.error) ? '—' : workspace.projects.length }}</strong><small>项目</small></div>
          <div><span class="stat-icon"><Code2 :size="18" /></span><strong>{{ !workspace.snippets.length && (workspace.moduleStates.snippets.loading || workspace.moduleStates.snippets.error) ? '—' : workspace.snippets.length }}</strong><small>代码片段</small></div>
          <div><span class="stat-icon"><BookOpen :size="18" /></span><strong>{{ !workspace.logs.length && (workspace.moduleStates.logs.loading || workspace.moduleStates.logs.error) ? '—' : workspace.logs.length }}</strong><small>开发日志</small></div>
          <div><span class="stat-icon"><CheckCircle2 :size="18" /></span><strong>{{ !workspace.tasks.length && (workspace.moduleStates.tasks.loading || workspace.moduleStates.tasks.error) ? '—' : workspace.completedTasks }}</strong><small>已完成任务</small></div>
        </div>
      </article>

      <article class="profile-panel sync-panel">
        <div class="panel-title"><div><p class="eyebrow">CLOUD WORKSPACE</p><h2>多端同步</h2></div><Cloud :size="20" /></div>
        <div class="sync-state" :class="{ warning: workspace.hasLoadErrors }"><span></span><div><strong>{{ workspace.hasLoadErrors ? '部分模块需要重试' : '云端连接正常' }}</strong><small>最近同步：{{ syncTime }}</small></div></div>
        <p>浏览器端的修改会直接写入 DevNest 服务端，并自动出现在 Android App 中。</p>
        <button class="button button-secondary full" type="button" :disabled="workspace.loading" @click="workspace.loadAll(true)"><RefreshCw :size="16" :class="{ spin: workspace.loading }" />立即刷新全部数据</button>
      </article>
    </section>

    <section class="appearance-panel">
      <div class="appearance-copy"><span class="setting-icon"><Sun :size="19" /></span><span><strong>外观主题</strong><small>主题偏好只保存在当前设备，不会影响你的工作区数据。</small></span></div>
      <div class="theme-options" aria-label="主题选择">
        <button type="button" :class="{ active: theme.mode === 'light' }" @click="theme.setTheme('light')"><Sun :size="16" />浅色</button>
        <button type="button" :class="{ active: theme.mode === 'dark' }" @click="theme.setTheme('dark')"><Moon :size="16" />深色</button>
      </div>
    </section>

    <section class="settings-panel">
      <div><span class="setting-icon"><ShieldCheck :size="19" /></span><span><strong>账户与隐私</strong><small>登录令牌仅保存在当前浏览器，本项目不会保存你的密码。</small></span></div>
      <div class="account-actions">
        <button v-if="!auth.workspaceMember" class="password-button" type="button" @click="router.push({ name: 'change-password' })"><KeyRound :size="16" />修改密码</button>
        <button v-if="!auth.workspaceMember" class="session-button" type="button" :disabled="auth.busy" @click="logoutAll"><ShieldOff :size="16" />退出全部设备</button>
        <button class="logout-button" type="button" @click="logout"><LogOut :size="16" />{{ auth.workspaceMember ? '结束模拟登录' : '退出登录' }}</button>
      </div>
    </section>

    <AppModal v-if="showEditor" title="编辑开发者资料" description="这些信息会同步到你的所有 DevNest 设备。" @close="showEditor = false">
      <form class="form-grid" @submit.prevent="saveProfile">
        <label class="form-field"><span>显示名称</span><input v-model.trim="draft.name" autofocus required maxlength="80" /></label>
        <label class="form-field"><span>职业角色</span><input v-model.trim="draft.role" required maxlength="120" placeholder="Independent Developer" /></label>
        <label class="form-field"><span>个人签名</span><textarea v-model.trim="draft.bio" required maxlength="500" rows="4" placeholder="用一句话描述你的构建方式。"></textarea></label>
        <label class="form-field"><span>头像链接（可选）</span><input v-model.trim="draft.avatarUrl" type="url" maxlength="500" placeholder="https://example.com/avatar.jpg" /></label>
        <div class="form-actions"><button class="button button-ghost" type="button" @click="showEditor = false">取消</button><button class="button button-primary" type="submit" :disabled="workspace.mutating">保存资料</button></div>
      </form>
    </AppModal>
  </div>
</template>

<style scoped>
.profile-hero { display: grid; grid-template-columns: 88px minmax(0, 1fr) auto; align-items: center; gap: 23px; padding: 29px; border: 1px solid var(--accent-border); border-radius: 16px; background: var(--surface-accent); }
.profile-avatar { width: 88px; height: 88px; display: grid; place-items: center; overflow: hidden; color: var(--accent-contrast); border: 3px solid var(--accent-border); border-radius: 22px; background: var(--accent); font-size: 30px; font-weight: 850; }
.profile-avatar img { width: 100%; height: 100%; object-fit: cover; }
.profile-copy h2 { margin: 0; font-size: 24px; letter-spacing: -.6px; }
.profile-copy > strong { display: block; margin-top: 7px; color: var(--accent); font-size: var(--font-sm); }
.profile-copy > p:last-child { max-width: 600px; margin: 10px 0 0; color: var(--muted); font-size: var(--font-sm); line-height: 1.65; }
.profile-account { display: grid; gap: 9px; padding-left: 27px; border-left: 1px solid var(--border); }
.profile-account span { display: flex; align-items: center; gap: 8px; color: var(--muted); font-size: var(--font-xs); }
.profile-account svg { color: var(--accent); }
.profile-grid { display: grid; grid-template-columns: 1.45fr .75fr; gap: 15px; margin-top: 15px; }
.profile-panel { padding: 24px; border: 1px solid var(--border); border-radius: 15px; background: var(--panel); transition: border-color var(--motion-fast), transform var(--motion-base) var(--ease-emphasized), box-shadow var(--motion-base); }
.profile-panel:hover { border-color: var(--border-strong); box-shadow: var(--shadow-sm); transform: translateY(-2px); }
.panel-title { display: flex; align-items: center; justify-content: space-between; }
.panel-title h2 { margin: 0; font-size: var(--font-lg); }
.panel-title > span { color: var(--muted); font-size: var(--font-2xs); }
.panel-title > svg { color: var(--accent); }
.stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 9px; margin-top: 22px; }
.stats-errors:empty { display: none; }
.stat-grid > div { min-height: 118px; padding: 15px; border: 1px solid var(--border); border-radius: 11px; background: var(--surface-raised); }
.stat-icon { width: 31px; height: 31px; display: grid; place-items: center; color: var(--accent); border-radius: 8px; background: var(--accent-bg); }
.stat-grid strong, .stat-grid small { display: block; }
.stat-grid strong { margin-top: 13px; font-size: 19px; }
.stat-grid small { margin-top: 3px; color: var(--muted); font-size: var(--font-2xs); }
.sync-state { display: flex; align-items: center; gap: 12px; margin-top: 24px; padding: 13px; border: 1px solid color-mix(in srgb,var(--success) 25%,var(--border)); border-radius: 10px; background: rgba(111,207,151,.04); }
.sync-state > span { width: 8px; height: 8px; border-radius: 50%; background: var(--success); box-shadow: 0 0 0 5px rgba(111,207,151,.08); }
.sync-state.warning { border-color: color-mix(in srgb,var(--warning) 28%,var(--border)); }
.sync-state.warning > span { background: var(--warning); box-shadow: 0 0 0 5px color-mix(in srgb,var(--warning) 10%,transparent); }
.sync-state strong, .sync-state small { display: block; }
.sync-state strong { font-size: var(--font-sm); }
.sync-state small { margin-top: 4px; color: var(--muted); font-size: var(--font-2xs); }
.sync-panel > p { margin: 17px 0; color: var(--muted); font-size: var(--font-xs); line-height: 1.75; }
.button.full { width: 100%; }
.settings-panel { display: flex; align-items: center; justify-content: space-between; margin-top: 15px; padding: 18px 21px; border: 1px solid var(--border); border-radius: 13px; background: var(--panel); }
.appearance-panel { display: flex; align-items: center; justify-content: space-between; margin-top: 15px; padding: 18px 21px; border: 1px solid var(--border); border-radius: 13px; background: var(--panel); }
.appearance-copy { display: flex; align-items: center; gap: 12px; }
.appearance-copy strong, .appearance-copy small { display: block; }
.appearance-copy strong { font-size: var(--font-sm); }
.appearance-copy small { margin-top: 5px; color: var(--muted); font-size: var(--font-2xs); }
.theme-options { display: grid; grid-template-columns: 1fr 1fr; gap: 5px; padding: 4px; border: 1px solid var(--border); border-radius: 10px; background: var(--surface-sunken); }
.theme-options button { display: flex; align-items: center; gap: 7px; padding: 8px 12px; color: var(--muted); border: 1px solid transparent; border-radius: 7px; background: transparent; cursor: pointer; font-size: var(--font-xs); transition: color var(--motion-fast), border-color var(--motion-fast), background var(--motion-base), transform var(--motion-fast); }
.theme-options button:active { transform: scale(.97); }
.theme-options button.active { color: var(--accent); border-color: var(--accent-border); background: var(--accent-bg); }
.settings-panel > div { display: flex; align-items: center; gap: 12px; }
.setting-icon { width: 37px; height: 37px; display: grid; place-items: center; color: var(--accent); border-radius: 9px; background: var(--accent-bg); }
.settings-panel strong, .settings-panel small { display: block; }
.settings-panel strong { font-size: var(--font-sm); }
.settings-panel small { margin-top: 5px; color: var(--muted); font-size: var(--font-2xs); }
.account-actions { display: flex; align-items: center; gap: 8px; }
.password-button, .session-button, .logout-button { display: flex; align-items: center; gap: 7px; padding: 8px 11px; border-radius: 8px; cursor: pointer; font-size: var(--font-xs); }
.password-button { color: var(--accent); border: 1px solid var(--accent-border); background: var(--accent-bg); }
.session-button { color: var(--warning); border: 1px solid color-mix(in srgb, var(--warning) 24%, var(--border)); background: color-mix(in srgb, var(--warning) 7%, transparent); }
.session-button:disabled { opacity: .55; cursor: wait; }
.logout-button { color: var(--danger); border: 1px solid rgba(229,140,140,.2); background: rgba(229,140,140,.05); }
@media (max-width: 1220px) { .profile-account { display: none; } .profile-grid { grid-template-columns: 1fr; } }
@media (max-width: 720px) {
  .profile-hero { grid-template-columns: 72px 1fr; gap: 17px; padding: 20px; }
  .profile-avatar { width: 72px; height: 72px; border-radius: 18px; font-size: 24px; }
  .profile-copy h2 { font-size: 20px; }
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
  .settings-panel { align-items: stretch; flex-direction: column; gap: 18px; }
  .appearance-panel { align-items: stretch; flex-direction: column; gap: 18px; }
  .theme-options button { justify-content: center; }
  .logout-button { justify-content: center; }
  .account-actions { display: grid; grid-template-columns: 1fr 1fr; }
  .password-button { justify-content: center; }
}
</style>
