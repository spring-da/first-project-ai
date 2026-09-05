<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Eye, EyeOff, KeyRound, LogOut, ShieldCheck } from 'lucide-vue-next'
import AppLogo from '../components/AppLogo.vue'
import WorkspaceUtilities from '../components/WorkspaceUtilities.vue'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore } from '../stores/notifications'
import { useWorkspaceStore } from '../stores/workspace'

const auth = useAuthStore()
const notifications = useNotificationStore()
const router = useRouter()
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const showPasswords = ref(false)
const forced = computed(() => auth.mustChangePassword)
const validationError = computed(() => {
  if (newPassword.value && newPassword.value.length < 8) return '新密码至少需要 8 位字符'
  if (confirmPassword.value && newPassword.value !== confirmPassword.value) return '两次输入的新密码不一致'
  return ''
})

async function submit() {
  if (validationError.value || !currentPassword.value || !newPassword.value) return
  const wasForced = forced.value
  if (await auth.changePassword(currentPassword.value, newPassword.value)) {
    notifications.notify('密码已更新，之前签发的登录令牌已失效。', { type: 'success' })
    router.replace({ name: wasForced ? 'dashboard' : 'profile' })
  }
}

function logout() {
  auth.clear()
  useWorkspaceStore().clear()
  router.replace({ name: 'login' })
}
</script>

<template>
  <main class="password-page">
    <WorkspaceUtilities class="password-utilities" />
    <section class="password-card">
      <AppLogo />
      <div class="password-icon"><KeyRound :size="25" /></div>
      <p class="eyebrow">ACCOUNT SECURITY</p>
      <h1>{{ forced ? '请先设置新密码' : '修改登录密码' }}</h1>
      <p class="description">{{ forced ? '管理员刚刚重置了你的密码。完成修改前，工作区与其他接口将保持锁定。' : '定期更新密码可以降低账号泄露风险；修改后其他设备需要重新登录。' }}</p>
      <div class="security-note"><ShieldCheck :size="17" /><span>新密码至少 8 位，且不能与当前密码相同。</span></div>

      <form @submit.prevent="submit">
        <label class="form-field"><span>当前密码</span><input v-model="currentPassword" :type="showPasswords ? 'text' : 'password'" required autocomplete="current-password" /></label>
        <label class="form-field"><span>新密码</span><input v-model="newPassword" :type="showPasswords ? 'text' : 'password'" required minlength="8" maxlength="72" autocomplete="new-password" /></label>
        <label class="form-field"><span>确认新密码</span><input v-model="confirmPassword" :type="showPasswords ? 'text' : 'password'" required minlength="8" maxlength="72" autocomplete="new-password" /></label>
        <button class="show-password" type="button" @click="showPasswords = !showPasswords"><EyeOff v-if="showPasswords" :size="16" /><Eye v-else :size="16" />{{ showPasswords ? '隐藏密码' : '显示密码' }}</button>
        <Transition name="reveal"><p v-if="validationError" class="validation-error" role="alert">{{ validationError }}</p></Transition>
        <button class="button button-primary submit" type="submit" :disabled="auth.busy || Boolean(validationError)"><span>{{ auth.busy ? '正在更新…' : forced ? '更新密码并进入工作台' : '确认修改密码' }}</span><ArrowRight :size="17" /></button>
      </form>
      <button v-if="forced" class="logout" type="button" @click="logout"><LogOut :size="15" />退出当前账户</button>
      <button v-else class="logout" type="button" @click="router.replace({ name: 'profile' })"><ArrowLeft :size="15" />返回个人设置</button>
    </section>
  </main>
</template>

<style scoped>
.password-page { min-height: 100svh; display: grid; place-items: center; padding: 48px 20px; background: radial-gradient(circle at 50% 10%, var(--accent-soft), transparent 34%), var(--bg); }
.password-utilities { position: fixed; top: 20px; right: 22px; }
.password-card { width: min(500px, 100%); padding: 34px; border: 1px solid var(--border); border-radius: 20px; background: var(--panel); box-shadow: var(--shadow-lg); animation: password-card-in var(--motion-slow) var(--ease-emphasized) both; }
.password-card :deep(.app-logo) { margin-bottom: 34px; }
.password-icon { width: 50px; height: 50px; display: grid; place-items: center; margin-bottom: 20px; color: var(--accent); border-radius: 14px; background: var(--accent-bg); }
h1 { margin: 0; font-size: 27px; letter-spacing: -.7px; }
.description { margin: 12px 0 0; color: var(--muted); font-size: var(--font-sm); line-height: 1.7; }
.security-note { display: flex; gap: 9px; margin: 22px 0 7px; padding: 12px 13px; color: var(--subtle); border: 1px solid var(--accent-border); border-radius: 10px; background: var(--accent-bg); font-size: var(--font-xs); line-height: 1.5; }
.security-note svg { flex-shrink: 0; color: var(--accent); }
.form-field { margin-top: 16px; }
.show-password, .logout { display: flex; align-items: center; gap: 7px; margin-top: 13px; padding: 5px 0; color: var(--muted); border: 0; background: transparent; cursor: pointer; font-size: var(--font-xs); }
.validation-error { margin: 10px 0 0; color: var(--danger); font-size: var(--font-xs); }
.submit { width: 100%; justify-content: space-between; margin-top: 22px; }
.logout { margin: 18px auto 0; }
@keyframes password-card-in { from { opacity: 0; transform: translateY(14px) scale(.985); } }
@media (max-width: 560px) { .password-page { place-items: start stretch; padding: 78px 14px 28px; } .password-card { padding: 25px 20px; border-radius: 16px; } }
</style>
