export type NavigationTransition = 'page-forward' | 'page-back' | 'page-crossfade'

export function resolveNavigationTransition(
  paths: readonly string[],
  nextPath: string,
  previousPath?: string,
): NavigationTransition {
  if (!previousPath || nextPath === previousPath) return 'page-crossfade'

  const nextIndex = paths.indexOf(nextPath)
  const previousIndex = paths.indexOf(previousPath)
  if (nextIndex < 0 || previousIndex < 0 || nextIndex === previousIndex) return 'page-crossfade'

  return nextIndex > previousIndex ? 'page-forward' : 'page-back'
}
