import { defineStore } from 'pinia'
import { ref } from 'vue'
import { apiRequest, jsonBody } from '../services/api'
import type { AdminAccount, AdminAuditEvent, AdminWorkspaceAccount, AdminWorkspaceSnapshot, InvitationSecret, MarkdownDocument, TemporaryPasswordSecret } from '../types'

export const useAdminStore = defineStore('admin', () => {
  const accounts = ref<AdminAccount[]>([])
  const loading = ref(false)
  const auditEvents = ref<AdminAuditEvent[]>([])
  const auditLoading = ref(false)
  const auditError = ref('')
  const mutating = ref(false)
  const error = ref('')

  async function load() {
    loading.value = true
    error.value = ''
    try {
      accounts.value = await apiRequest<AdminAccount[]>('/admin/accounts')
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '人员名单加载失败'
      throw cause
    } finally {
      loading.value = false
    }
  }

  async function loadAudit() {
    auditLoading.value = true
    auditError.value = ''
    try {
      auditEvents.value = await apiRequest<AdminAuditEvent[]>('/admin/audit-events?limit=40')
    } catch (cause) {
      auditError.value = cause instanceof Error ? cause.message : '操作记录加载失败'
      throw cause
    } finally {
      auditLoading.value = false
    }
  }

  async function mutate<T>(operation: () => Promise<T>) {
    mutating.value = true
    error.value = ''
    try {
      return await operation()
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '操作失败，请稍后重试'
      throw cause
    } finally {
      mutating.value = false
    }
  }

  async function invite(email: string) {
    const result = await mutate(() => apiRequest<InvitationSecret>('/admin/invitations', {
      method: 'POST',
      body: jsonBody({ email }),
    }))
    accounts.value = [...accounts.value, result.account]
    return result
  }

  async function rotateInvitation(invitationId: string) {
    const result = await mutate(() => apiRequest<InvitationSecret>(
      `/admin/invitations/${invitationId}/rotate-token`,
      { method: 'POST' },
    ))
    accounts.value = accounts.value.map((account) => account.invitationId === invitationId
      ? result.account
      : account)
    return result
  }

  async function revokeInvitation(invitationId: string) {
    await mutate(() => apiRequest<void>(`/admin/invitations/${invitationId}`, { method: 'DELETE' }))
    accounts.value = accounts.value.filter((account) => account.invitationId !== invitationId)
  }

  async function setEnabled(userId: string, enabled: boolean) {
    const updated = await mutate(() => apiRequest<AdminAccount>(`/admin/accounts/${userId}/status`, {
      method: 'PATCH',
      body: jsonBody({ enabled }),
    }))
    accounts.value = accounts.value.map((account) => account.userId === userId ? updated : account)
  }

  async function resetPassword(userId: string) {
    const result = await mutate(() => apiRequest<TemporaryPasswordSecret>(
      `/admin/accounts/${userId}/reset-password`,
      { method: 'POST' },
    ))
    accounts.value = accounts.value.map((account) => account.userId === userId
      ? { ...account, mustChangePassword: true }
      : account)
    return result
  }

  async function deleteAccount(userId: string) {
    await mutate(() => apiRequest<void>(`/admin/accounts/${userId}`, { method: 'DELETE' }))
    accounts.value = accounts.value.filter((account) => account.userId !== userId)
  }

  function loadWorkspaceAccount(userId: string) {
    return apiRequest<AdminWorkspaceAccount>(`/admin/accounts/${encodeURIComponent(userId)}/workspace/account`)
  }

  function loadWorkspace(userId: string) {
    return apiRequest<AdminWorkspaceSnapshot>(`/admin/accounts/${userId}/workspace`, { timeoutMs: 30_000 })
  }

  function loadWorkspaceMarkdown(userId: string, documentId: string) {
    return apiRequest<MarkdownDocument>(
      `/admin/accounts/${userId}/workspace/markdown-documents/${documentId}`,
      { timeoutMs: 30_000 },
    )
  }

  return {
    accounts,
    auditEvents,
    loading,
    auditLoading,
    auditError,
    mutating,
    error,
    load,
    loadAudit,
    invite,
    rotateInvitation,
    revokeInvitation,
    setEnabled,
    resetPassword,
    deleteAccount,
    loadWorkspace,
    loadWorkspaceAccount,
    loadWorkspaceMarkdown,
  }
})
