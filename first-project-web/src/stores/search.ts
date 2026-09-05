import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export type SearchScope = 'page' | 'global'

// The topbar is the only search input. List views consume pageQuery so global
// searches never leave an unrelated filter behind in a cached page.
export const useSearchStore = defineStore('search', () => {
  const query = ref('')
  const scope = ref<SearchScope>('global')
  const pageQuery = computed(() => scope.value === 'page' ? query.value : '')

  function clear() {
    query.value = ''
  }

  function reset(nextScope: SearchScope) {
    clear()
    scope.value = nextScope
  }

  return { query, scope, pageQuery, clear, reset }
})
