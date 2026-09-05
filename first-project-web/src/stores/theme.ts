import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

export type ThemeMode = 'dark' | 'light'

const THEME_KEY = 'devnest_theme_v1'
let transitionTimer: ReturnType<typeof setTimeout> | undefined

function preferredTheme(): ThemeMode {
  try {
    const saved = localStorage.getItem(THEME_KEY)
    if (saved === 'dark' || saved === 'light') return saved
  } catch {
    // Respect the system theme when browser storage is unavailable.
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(preferredTheme())
  const isDark = computed(() => mode.value === 'dark')

  function applyTheme(animate = false) {
    if (animate) {
      document.documentElement.classList.add('theme-transition')
      if (transitionTimer) clearTimeout(transitionTimer)
      transitionTimer = setTimeout(() => document.documentElement.classList.remove('theme-transition'), 260)
    }
    document.documentElement.dataset.theme = mode.value
    document.documentElement.style.colorScheme = mode.value
    document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')
      ?.setAttribute('content', mode.value === 'dark' ? '#111318' : '#f6f8fb')
  }

  function setTheme(next: ThemeMode) {
    mode.value = next
    try {
      localStorage.setItem(THEME_KEY, next)
    } catch {
      // Theme switching still works for the current session.
    }
    applyTheme(true)
  }

  function toggleTheme() {
    setTheme(isDark.value ? 'light' : 'dark')
  }

  function initialize() {
    applyTheme()
  }

  return { mode, isDark, setTheme, toggleTheme, initialize }
})
