import { installAuthGuards } from './router/guards'
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import App from './App.vue'
import { router } from './router'
import { useAuthStore } from './stores/auth'
import { useThemeStore } from './stores/theme'
import { useWorkspaceStore } from './stores/workspace'
import { SESSION_KEY } from './services/api'
import { installWheelScrollChaining } from './utils/scrollChaining'

const app = createApp(App)
const pinia = createPinia()

const stopWheelScrollChaining = installWheelScrollChaining()
if (import.meta.hot) import.meta.hot.dispose(stopWheelScrollChaining)

app.use(pinia)
app.use(router)

const auth = useAuthStore(pinia)
useThemeStore(pinia).initialize()
installAuthGuards(router, pinia)

function returnToLogin() {
  auth.clear()
  useWorkspaceStore(pinia).clear()
  void router.push({ name: 'login' })
}

window.addEventListener('devnest:unauthorized', returnToLogin)
const syncLogoutAcrossTabs = (event: StorageEvent) => {
  if (event.key === SESSION_KEY && event.newValue === null && auth.isAuthenticated) returnToLogin()
}
window.addEventListener('storage', syncLogoutAcrossTabs)
if (import.meta.hot) import.meta.hot.dispose(() => window.removeEventListener('storage', syncLogoutAcrossTabs))

window.addEventListener('devnest:password-change-required', () => {
  auth.setWorkspaceMember(null)
  auth.markPasswordChangeRequired()
  useWorkspaceStore(pinia).clear()
  router.push({ name: 'change-password' })
})

app.mount('#app')
