import { resetWorkspaceRequests, setRequestWorkspace } from '../services/workspaceContext'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { apiRequest, jsonBody, readStoredSession, writeStoredSession, SESSION_KEY } from '../services/api'
import type { AdminWorkspaceAccount, AuthResponse, AuthSession, AuthUser } from '../types'

export const useAuthStore = defineStore('auth', () => {
  const session = ref<AuthSession | null>(readStoredSession())
  const workspaceMember = ref<AdminWorkspaceAccount | null>(null)
  const workspaceUser = computed(() => workspaceMember.value ?? session.value?.user)
  const workspaceKey = computed(() => workspaceMember.value
    ? `admin:${session.value?.user.id}:member:${workspaceMember.value.id}` : session.value?.user.id ?? null)
  function setWorkspaceMember(member: AdminWorkspaceAccount | null) {
    setRequestWorkspace(member?.id ?? null)
    workspaceMember.value = member
  }
  const busy = ref(false)
  const error = ref('')
  const isAuthenticated = computed(() => session.value !== null)
  const isAdmin = computed(() => session.value?.user.role === 'ADMIN')
  const mustChangePassword = computed(() => session.value?.user.mustChangePassword === true)

  function saveSession(response: AuthResponse) {
    const value: AuthSession = {
      ...response,
      expiresAt: Date.now() + response.expiresInSeconds * 1000,
    }
    writeStoredSession(value)
    session.value = value
  }

  async function authenticate(
    mode: 'login' | 'register',
    payload: { email: string; password: string } | { invitationToken: string; password: string; displayName: string },
  ) {
    busy.value = true
    error.value = ''
    try {
      const response = await apiRequest<AuthResponse>(`/auth/${mode}`, {
        method: 'POST',
        body: jsonBody(payload),
        authenticated: false,
      })
      saveSession(response)
      return true
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '登录失败，请稍后重试'
      return false
    } finally {
      busy.value = false
    }
  }

  const login = (email: string, password: string) => authenticate('login', { email, password })
  const register = (invitationToken: string, password: string, displayName: string) =>
    authenticate('register', { invitationToken, password, displayName })

  async function displayNameAvailable(displayName: string) {
    const result = await apiRequest<{ available: boolean }>(
      `/auth/display-name-availability?displayName=${encodeURIComponent(displayName)}`,
      { authenticated: false },
    )
    return result.available
  }

  async function refreshUser() {
    if (!session.value) return false
    const currentSession = session.value
    try {
      const user = await apiRequest<AuthUser>('/auth/me')
      if (session.value !== currentSession) return false
      updateUser(user)
      return true
    } catch {
      return false
    }
  }

  async function changePassword(currentPassword: string, newPassword: string) {
    busy.value = true
    error.value = ''
    try {
      const response = await apiRequest<AuthResponse>('/auth/password', {
        method: 'PUT',
        body: jsonBody({ currentPassword, newPassword }),
      })
      saveSession(response)
      return true
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '密码修改失败，请稍后重试'
      return false
    } finally {
      busy.value = false
    }
  }

  async function logoutAll() {
    busy.value = true
    error.value = ''
    try {
      await apiRequest<void>('/auth/logout-all', { method: 'POST' })
      clear()
      return true
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '登录会话注销失败，请稍后重试'
      return false
    } finally {
      busy.value = false
    }
  }

  function updateUser(user: AuthUser) {
    if (!session.value) return
    const value = { ...session.value, user }
    writeStoredSession(value)
    session.value = value
  }

  function markPasswordChangeRequired() {
    if (!session.value || session.value.user.mustChangePassword) return
    updateUser({ ...session.value.user, mustChangePassword: true })
  }

  function clear() {
    resetWorkspaceRequests()
    workspaceMember.value = null
    try { localStorage.removeItem(SESSION_KEY) } catch { /* Logout must work without storage. */ }
    session.value = null
    error.value = ''
  }

  return {
    session,
    workspaceMember,
    workspaceUser,
    workspaceKey,
    setWorkspaceMember,
    busy,
    error,
    isAuthenticated,
    isAdmin,
    mustChangePassword,
    login,
    register,
    displayNameAvailable,
    refreshUser,
    updateUser,
    changePassword,
    logoutAll,
    markPasswordChangeRequired,
    clear,
  }
})
