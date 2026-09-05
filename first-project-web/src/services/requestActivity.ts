import { computed, ref } from 'vue'

const pendingRequests = ref(0)

export const requestPendingCount = computed(() => pendingRequests.value)
export const requestActive = computed(() => pendingRequests.value > 0)

/** Tracks every API request and returns an idempotent release callback. */
export function beginRequest() {
  pendingRequests.value++
  let released = false
  return () => {
    if (released) return
    released = true
    pendingRequests.value = Math.max(0, pendingRequests.value - 1)
  }
}
