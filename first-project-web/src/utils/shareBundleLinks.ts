const TOKEN_PATTERN = /^[A-Za-z0-9_-]{43}$/
const STORAGE_PREFIX = 'devnest.share-bundle-tokens:'

function storageKey(ownerKey: string) {
  return `${STORAGE_PREFIX}${ownerKey}`
}

function readTokens(ownerKey: string) {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(storageKey(ownerKey)) ?? '{}')
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    return Object.fromEntries(Object.entries(parsed).filter(([, token]) => typeof token === 'string' && TOKEN_PATTERN.test(token)))
  } catch {
    return {}
  }
}

export function rememberShareBundleToken(ownerKey: string | null, bundleId: string, token: string) {
  if (!ownerKey || !bundleId || !TOKEN_PATTERN.test(token)) return
  try {
    const tokens = readTokens(ownerKey)
    tokens[bundleId] = token
    localStorage.setItem(storageKey(ownerKey), JSON.stringify(tokens))
  } catch {
    // Copying remains available in the creation dialog if storage is unavailable.
  }
}

export function getShareBundleToken(ownerKey: string | null, bundleId: string) {
  if (!ownerKey || !bundleId) return ''
  return readTokens(ownerKey)[bundleId] ?? ''
}

export function forgetShareBundleToken(ownerKey: string | null, bundleId: string) {
  if (!ownerKey || !bundleId) return
  try {
    const tokens = readTokens(ownerKey)
    if (!(bundleId in tokens)) return
    delete tokens[bundleId]
    localStorage.setItem(storageKey(ownerKey), JSON.stringify(tokens))
  } catch {
    // A stale local token is harmless when storage cannot be updated.
  }
}
