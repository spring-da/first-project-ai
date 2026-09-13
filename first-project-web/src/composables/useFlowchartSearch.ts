import { onScopeDispose, ref, shallowRef, watch } from 'vue'
import type { Ref } from 'vue'
import { flowchartApi } from '../services/flowcharts'
import { useAuthStore } from '../stores/auth'
import type { FlowchartSummary } from '../types/flowcharts'

export function useFlowchartSearch(query: Readonly<Ref<string>>) {
  const auth = useAuthStore(), results = shallowRef<FlowchartSummary[]>([]), loading = ref(false), error = ref('')
  let request: AbortController | undefined, timer: ReturnType<typeof setTimeout> | undefined, epoch = 0
  function cancel() { epoch++; request?.abort(); clearTimeout(timer) }
  watch([query, () => auth.workspaceKey], ([q]) => {
    cancel(); results.value = []; error.value = ''; loading.value = !!q.trim()
    if (!q.trim()) return
    const current = epoch
    timer = setTimeout(async () => {
      request = new AbortController()
      try { const found = await flowchartApi.list(q.trim(), request.signal); if (current === epoch) results.value = found }
      catch (cause) { if (current === epoch) error.value = (cause as Error).message }
      finally { if (current === epoch) loading.value = false }
    }, 250)
  }, { immediate: true, flush: 'sync' })
  onScopeDispose(cancel)
  return { results, loading, error }
}
