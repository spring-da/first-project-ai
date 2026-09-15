export interface StarterEscapeTarget {
  addEventListener: (type: 'keydown', listener: (event: KeyboardEvent) => void) => void
  removeEventListener: (type: 'keydown', listener: (event: KeyboardEvent) => void) => void
}

export function attachStarterEscapeListener(target: StarterEscapeTarget, isStarterActive: () => boolean, close: () => void) {
  const handleKeydown = (event: KeyboardEvent) => {
    if (!isStarterActive() || event.key !== 'Escape') return
    event.preventDefault()
    close()
  }
  target.addEventListener('keydown', handleKeydown)
  return () => target.removeEventListener('keydown', handleKeydown)
}
