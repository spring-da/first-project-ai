(() => {
  let theme
  try {
    const saved = localStorage.getItem('devnest_theme_v1')
    theme = saved === 'dark' || saved === 'light'
      ? saved
      : matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  } catch (_) {
    theme = matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  }
  document.documentElement.dataset.theme = theme
  document.documentElement.style.colorScheme = theme
  document.querySelector('meta[name="theme-color"]').content = theme === 'dark' ? '#111318' : '#f6f8fb'
})()
