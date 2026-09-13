export const WORKSPACE_OWNER_HEADER = 'X-Workspace-Owner'

let ownerId: string | null = null
let lifetime = new AbortController()

export function isWorkspacePath(path: string) {
  return /^\/(tasks|projects|domains|knowledge-items|snippets|flowcharts|profile|markdown-documents|markdown-images|sharing)(?:[/?]|$)/.test(path)
}

export function setRequestWorkspace(nextOwnerId: string | null) {
  if (ownerId === nextOwnerId) return
  lifetime.abort()
  lifetime = new AbortController()
  ownerId = nextOwnerId
}

export function resetWorkspaceRequests() {
  lifetime.abort()
  lifetime = new AbortController()
  ownerId = null
}

export function getRequestWorkspaceOwner() { return ownerId }

// Capture before starting I/O so a response cannot cross workspace boundaries.
export function captureWorkspaceRequest(path: string, authenticated = true) {
  return authenticated && isWorkspacePath(path) ? { ownerId, signal: lifetime.signal } : null
}
