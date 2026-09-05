import { onScopeDispose, shallowRef } from 'vue'
import { defineStore } from 'pinia'

export interface ConfirmationOptions {
  title: string
  message: string
  detail?: string
  confirmText?: string
  cancelText?: string
  tone?: 'danger' | 'warning' | 'info'
  icon?: 'delete' | 'logout' | 'leave' | 'info'
}

export const useConfirmationStore = defineStore('confirmation', () => {
  const current = shallowRef<ConfirmationOptions | null>(null)
  let resolve: ((accepted: boolean) => void) | undefined

  function ask(options: ConfirmationOptions): Promise<boolean> {
    // A second click must not overwrite the first request or run its action twice.
    if (current.value) return Promise.resolve(false)
    current.value = { confirmText: '确认', cancelText: '取消', tone: 'info', icon: 'info', ...options }
    return new Promise<boolean>((done) => { resolve = done })
  }

  function answer(accepted: boolean) {
    const done = resolve
    resolve = undefined
    current.value = null
    done?.(accepted)
  }

  const cancel = () => answer(false)
  onScopeDispose(cancel)
  return { current, ask, answer, cancel }
})
