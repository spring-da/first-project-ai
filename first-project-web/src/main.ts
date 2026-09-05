import { installAuthGuards } from './router/guards'
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import App from './App.vue'
import { router } from './router'
import { useAuthStore } from './stores/auth'
import { useThemeStore } from './stores/theme'
import { useWorkspaceStore } from './stores/workspace'
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

window.addEventListener('devnest:unauthorized', () => {
  auth.clear()
  useWorkspaceStore(pinia).clear()
  router.push({ name: 'login' })
})

window.addEventListener('devnest:password-change-required', () => {
  auth.setWorkspaceMember(null)
  auth.markPasswordChangeRequired()
  useWorkspaceStore(pinia).clear()
  router.push({ name: 'change-password' })
})

app.mount('#app')
