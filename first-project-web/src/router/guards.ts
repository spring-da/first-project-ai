import type { Pinia } from 'pinia'
import type { Router } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useAdminStore } from '../stores/admin'
import { useNotificationStore } from '../stores/notifications'

const workspacePages = new Set(['dashboard', 'projects', 'knowledge', 'shared-pool', 'tools', 'profile'])

export function installAuthGuards(router: Router, pinia: Pinia) {
  const auth = useAuthStore(pinia)
  let validatedUserId: string | null = null
  let navigation = 0
  router.beforeEach(async (to, from) => {
    navigation++
    const currentUserId = auth.session?.user.id ?? null
    if (!currentUserId) validatedUserId = null
    if (currentUserId && validatedUserId !== currentUserId) {
      if (await auth.refreshUser()) validatedUserId = auth.session?.user.id ?? null
    }
    if (to.meta.requiresAuth && !auth.isAuthenticated) return { name: 'login', query: { redirect: to.fullPath } }
    if (auth.isAuthenticated && auth.mustChangePassword && to.name !== 'change-password') return { name: 'change-password' }
    if (to.meta.adminOnly && !auth.isAdmin) return { name: 'dashboard' }
    if (to.meta.guestOnly && auth.isAuthenticated) return { name: 'dashboard' }

    if (workspacePages.has(String(to.name))) {
      const target = to.query.workspace ?? (auth.isAdmin && workspacePages.has(String(from.name)) ? from.query.workspace : undefined)
      if (target && !auth.isAdmin) return { path: to.path, query: { ...to.query, workspace: undefined }, hash: to.hash }
      if (target && !to.query.workspace) return { path: to.path, query: { ...to.query, workspace: target }, hash: to.hash }
    }
  })

  // Commit context only after component leave guards accept navigation (including unsaved edits).
  router.beforeResolve(async (to) => {
    const attempt = navigation
    const target = workspacePages.has(String(to.name)) && typeof to.query.workspace === 'string'
      ? to.query.workspace : null
    if (target === (auth.workspaceMember?.id ?? null)) return
    if (!target) { auth.setWorkspaceMember(null); return }
    try {
      const actorId = auth.session?.user.id
      const member = await useAdminStore(pinia).loadWorkspaceAccount(target)
      if (attempt !== navigation || !auth.isAdmin || auth.session?.user.id !== actorId) return false
      auth.setWorkspaceMember(member)
    } catch (cause) {
      if (attempt !== navigation) return false
      useNotificationStore(pinia).notify(cause instanceof Error ? cause.message : '成员工作区加载失败', { type: 'error' })
      return { name: auth.isAuthenticated ? 'admin-accounts' : 'login' }
    }
  })
}
