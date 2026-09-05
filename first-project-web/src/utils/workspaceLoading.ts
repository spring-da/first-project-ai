export function settleWorkspaceModules<T>(operations: Array<() => Promise<T>>) {
  return Promise.allSettled(operations.map((operation) => operation()))
}
