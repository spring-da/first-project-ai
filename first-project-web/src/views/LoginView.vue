<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, BookOpen, Cloud, Eye, EyeOff, FolderKanban, ShieldCheck } from 'lucide-vue-next'
import AppLogo from '../components/AppLogo.vue'
import WorkspaceUtilities from '../components/WorkspaceUtilities.vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const invitationToken = computed(() => {
  const hash = route.hash.startsWith('#') ? route.hash.slice(1) : route.hash
  return new URLSearchParams(hash).get('invitation')?.trim() ?? ''
})
const mode = ref<'login' | 'register'>(invitationToken.value ? 'register' : 'login')
const email = ref('')
const password = ref('')
const displayName = ref('')
const nameAvailability = ref<'idle' | 'checking' | 'available' | 'unavailable'>('idle')
let availabilityTimer: number | null = null
let availabilityVersion = 0
const showPassword = ref(false)
const title = computed(() => mode.value === 'login' ? '欢迎回到工作台' : '创建你的 DevNest')

watch(invitationToken, (token) => {
  if (token) mode.value = 'register'
})

watch([displayName, mode], ([name, currentMode]) => {
  if (availabilityTimer !== null) window.clearTimeout(availabilityTimer)
  const version = ++availabilityVersion
  nameAvailability.value = 'idle'
  if (currentMode !== 'register' || !name.trim()) return
  nameAvailability.value = 'checking'
  availabilityTimer = window.setTimeout(async () => {
    try {
      const available = await auth.displayNameAvailable(name.trim())
      if (version === availabilityVersion) nameAvailability.value = available ? 'available' : 'unavailable'
    } catch {
      if (version === availabilityVersion) nameAvailability.value = 'idle'
    }
  }, 350)
})

onBeforeUnmount(() => {
  if (availabilityTimer !== null) window.clearTimeout(availabilityTimer)
})

async function submit() {
  if (mode.value === 'register' && !invitationToken.value) {
    auth.error = '注册必须使用管理员生成的一次性邀请链接。'
    return
  }
  if (mode.value === 'register' && nameAvailability.value === 'unavailable') {
    auth.error = '该昵称已被占用，请换一个昵称。'
    return
  }
  const ok = mode.value === 'login'
    ? await auth.login(email.value, password.value)
    : await auth.register(invitationToken.value, password.value, displayName.value)
  if (ok) router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : '/')
}

function switchMode(next: 'login' | 'register') {
  mode.value = next
  auth.error = ''
}
</script>

<template>
  <main class="auth-page">
    <WorkspaceUtilities class="auth-utilities" />
    <section class="auth-story">
      <AppLogo />
      <div class="auth-story__copy">
        <p class="eyebrow">YOUR PRIVATE DEVELOPER DESK</p>
        <h1>让每一次构建<br />都有迹可循。</h1>
        <p>项目、任务、代码片段与开发日志，在浏览器和移动端保持一致。</p>
      </div>
      <div class="feature-list">
        <div><FolderKanban :size="18" /><span><strong>项目雷达</strong><small>掌握进度与下一步行动</small></span></div>
        <div><BookOpen :size="18" /><span><strong>个人知识库</strong><small>沉淀可复用的代码与经验</small></span></div>
        <div><Cloud :size="18" /><span><strong>多端云同步</strong><small>与 Android App 共享服务端数据</small></span></div>
      </div>
      <p class="privacy-note"><ShieldCheck :size="15" />你的工作区由 JWT 身份认证保护</p>
    </section>

    <section class="auth-form-side">
      <form class="auth-card" @submit.prevent="submit">
        <div class="mode-tabs" role="tablist">
          <button type="button" :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</button>
          <button type="button" :class="{ active: mode === 'register' }" @click="switchMode('register')">注册</button>
        </div>
        <Transition name="auth-mode" mode="out-in">
          <div :key="mode" class="auth-mode-content">
            <div class="auth-heading"><p class="eyebrow">DEVNEST ACCOUNT</p><h2>{{ title }}</h2><p>{{ mode === 'login' ? '使用和 Android App 相同的账户登录。' : invitationToken ? '一次性邀请已识别，请设置你的账户信息。' : '注册需要管理员生成的一次性邀请链接。' }}</p></div>
            <label v-if="mode === 'register'" class="field"><span>显示名称</span><input v-model.trim="displayName" type="text" maxlength="80" required autocomplete="name" placeholder="你希望怎样被称呼" /><small v-if="nameAvailability !== 'idle'" class="name-availability" :class="nameAvailability">{{ nameAvailability === 'checking' ? '正在检查昵称…' : nameAvailability === 'available' ? '这个昵称可以使用' : '该昵称已被占用，请换一个昵称' }}</small></label>
            <label v-if="mode === 'login'" class="field"><span>邮箱</span><input v-model.trim="email" type="email" required autocomplete="email" placeholder="name@example.com" /></label>
            <p v-else class="invitation-state" :class="{ missing: !invitationToken }"><ShieldCheck :size="16" />{{ invitationToken ? '此链接只能成功注册一次，提交后立即失效。' : '请向管理员获取新的邀请链接后再继续。' }}</p>
          </div>
        </Transition>
        <label class="field">
          <span>密码</span>
          <span class="password-field">
            <input v-model="password" :type="showPassword ? 'text' : 'password'" :minlength="mode === 'register' ? 8 : undefined" required :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" placeholder="至少 8 位字符" />
            <button type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="17" /><Eye v-else :size="17" /></button>
          </span>
        </label>

        <button class="submit-button" type="submit" :disabled="auth.busy || (mode === 'register' && (!invitationToken || nameAvailability === 'checking' || nameAvailability === 'unavailable'))"><span>{{ auth.busy ? '正在连接…' : mode === 'login' ? '进入工作台' : '创建账户' }}</span><ArrowRight :size="18" /></button>
        <p class="auth-footer">继续即表示你将在此浏览器安全保存登录会话。</p>
      </form>
    </section>
  </main>
</template>

<style scoped>
.auth-utilities { position: absolute; z-index: 2; top: 20px; right: 22px; }
.auth-page { min-height: 100vh; display: grid; grid-template-columns: minmax(500px, 1.08fr) minmax(500px, .92fr); background: var(--bg); }
.auth-story { position: relative; display: flex; flex-direction: column; padding: 46px 7vw 42px; overflow: hidden; border-right: 1px solid var(--border); background: var(--sidebar-bg); }
.auth-story::after { content: ''; position: absolute; right: -180px; bottom: -220px; width: 520px; height: 520px; border: 1px solid var(--border); border-radius: 50%; box-shadow: 0 0 0 70px var(--accent-soft), 0 0 0 140px color-mix(in srgb,var(--accent) 3%,transparent); }
.auth-story__copy { position: relative; z-index: 1; margin: auto 0 58px; }
.auth-story__copy h1 { margin: 0; font-size: clamp(40px, 4.4vw, 68px); line-height: 1.08; letter-spacing: -2.6px; }
.auth-story__copy > p:last-child { max-width: 520px; margin: 24px 0 0; color: var(--muted); font-size: var(--font-lg); line-height: 1.85; }
.feature-list { position: relative; z-index: 1; display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.feature-list > div { min-height: 114px; padding: 17px; border: 1px solid var(--border); border-radius: 13px; background: var(--panel); transition: border-color var(--motion-fast), background var(--motion-fast), transform var(--motion-base) var(--ease-emphasized); }
.feature-list > div:hover { border-color: var(--accent-border); background: var(--surface-raised); transform: translateY(-2px); }
.feature-list svg { color: var(--accent); }
.feature-list span, .feature-list strong, .feature-list small { display: block; }
.feature-list strong { margin-top: 15px; font-size: var(--font-sm); }
.feature-list small { margin-top: 6px; color: var(--muted); font-size: var(--font-xs); line-height: 1.5; }
.privacy-note { position: relative; z-index: 1; display: flex; align-items: center; gap: 8px; margin: 19px 0 0; color: var(--muted); font-size: var(--font-xs); }
.auth-form-side { display: grid; place-items: center; padding: 55px; }
.auth-card { width: min(450px, 100%); }
.mode-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 4px; padding: 4px; border: 1px solid var(--border); border-radius: 11px; background: var(--surface-sunken); }
.mode-tabs button { padding: 9px; color: var(--muted); border: 0; border-radius: 7px; background: transparent; cursor: pointer; font-size: var(--font-sm); font-weight: 650; transition: color var(--motion-fast), background var(--motion-base), box-shadow var(--motion-base), transform var(--motion-fast); }
.mode-tabs button:active { transform: scale(.98); }
.mode-tabs button.active { color: var(--text); background: var(--panel-strong); box-shadow: 0 4px 14px rgba(0,0,0,.2); }
.auth-heading { margin: 37px 0 30px; }
.auth-heading h2 { margin: 0; font-size: 25px; letter-spacing: -.7px; }
.auth-heading > p:last-child { margin: 9px 0 0; color: var(--muted); font-size: var(--font-sm); }
.invitation-state { display: flex; align-items: center; gap: 8px; margin: 17px 0 0; padding: 11px 12px; color: var(--success); border: 1px solid color-mix(in srgb, var(--success) 28%, var(--border)); border-radius: 10px; background: color-mix(in srgb, var(--success) 8%, transparent); font-size: var(--font-xs); line-height: 1.5; }
.invitation-state.missing { color: var(--warning); border-color: color-mix(in srgb, var(--warning) 30%, var(--border)); background: color-mix(in srgb, var(--warning) 8%, transparent); }
.field { display: grid; gap: 8px; margin-top: 17px; }
.field > span:first-child { color: var(--subtle); font-size: var(--font-sm); font-weight: 650; }
.field input { width: 100%; height: var(--control-height); padding: 0 15px; color: var(--text); border: 1px solid var(--border-strong); border-radius: 11px; outline: 0; background: var(--panel); font-size: var(--font-sm); box-shadow: inset 0 1px 0 rgba(255,255,255,.025); }
.field input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.name-availability { font-size: var(--font-2xs); }
.name-availability.checking { color: var(--muted); }
.name-availability.available { color: var(--success); }
.name-availability.unavailable { color: var(--danger); }
.password-field { position: relative; }
.password-field button { position: absolute; right: 9px; top: 50%; display: grid; place-items: center; padding: 5px; color: var(--muted); border: 0; background: transparent; transform: translateY(-50%); cursor: pointer; }
.password-field input { padding-right: 44px; }
.submit-button { width: 100%; height: var(--control-height); display: flex; align-items: center; justify-content: space-between; margin-top: 26px; padding: 0 17px; color: #ffffff; border: 0; border-radius: 11px; background: var(--brand); cursor: pointer; font-size: var(--font-sm); font-weight: 700; transition: background var(--motion-fast), transform var(--motion-base) var(--ease-emphasized), box-shadow var(--motion-base); }
.submit-button:hover { background: var(--accent-strong); }
.submit-button:hover:not(:disabled) { box-shadow: 0 8px 22px rgba(65,105,225,.22); transform: translateY(-1px); }
.submit-button:active:not(:disabled) { transform: translateY(1px) scale(.99); }
.submit-button svg { transition: transform var(--motion-base) var(--ease-emphasized); }
.submit-button:hover:not(:disabled) svg { transform: translateX(4px); }
.submit-button:disabled { opacity: .65; cursor: wait; }
.auth-mode-enter-active, .auth-mode-leave-active { transition: opacity var(--motion-base) ease, transform var(--motion-base) var(--ease-standard); }
.auth-mode-enter-from { opacity: 0; transform: translateX(10px); }
.auth-mode-leave-to { opacity: 0; transform: translateX(-8px); }
.auth-footer { margin: 14px 0 0; color: var(--muted); text-align: center; font-size: var(--font-2xs); }
@media (max-width: 1180px) { .auth-story { padding-inline: 45px; } .feature-list { grid-template-columns: 1fr; } .feature-list > div { min-height: auto; display: grid; grid-template-columns: 25px 1fr; align-items: center; } .feature-list strong { margin-top: 0; } }
@media (max-width: 720px) {
  .auth-page { display: block; min-height: 100svh; }
  .auth-story { min-height: 285px; padding: 25px 20px 23px; border-right: 0; border-bottom: 1px solid var(--border); }
  .auth-story::after { right: -230px; bottom: -310px; }
  .auth-story__copy { margin: 48px 0 0; }
  .auth-story__copy h1 { font-size: 34px; letter-spacing: -1.6px; }
  .auth-story__copy > p:last-child { margin-top: 15px; font-size: var(--font-md); line-height: 1.65; }
  .feature-list, .privacy-note { display: none; }
  .auth-form-side { padding: 30px 20px calc(46px + env(safe-area-inset-bottom)); }
  .auth-card { width: 100%; }
  .auth-heading { margin: 29px 0 24px; }
}
</style>
