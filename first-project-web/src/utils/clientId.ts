let sequence = 0

// Local UI/draft identifiers only. Never use the non-crypto fallback for tokens,
// passwords or authentication. randomUUID is absent on ordinary HTTP origins.
export function createClientId(): string {
  const cryptoApi = globalThis.crypto
  try {
    if (typeof cryptoApi?.randomUUID === 'function') return cryptoApi.randomUUID()
  } catch { /* Some embedded browsers expose the method but reject its use. */ }

  try {
    if (typeof cryptoApi?.getRandomValues === 'function') {
      const bytes = cryptoApi.getRandomValues(new Uint8Array(16))
      bytes[6] = (bytes[6]! & 0x0f) | 0x40
      bytes[8] = (bytes[8]! & 0x3f) | 0x80
      const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
      return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
    }
  } catch { /* UI feedback must still work without Web Crypto. */ }

  sequence += 1
  return `local-${Date.now().toString(36)}-${sequence.toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`
}
