const DEFAULT_MAX_BYTES = 64 * 1024 * 1024
const DEFAULT_MAX_ENTRIES = 64

export class MarkdownImageCache {
  private readonly entries = new Map<string, { blob: Blob; expiresAt: number }>()
  private readonly maxBytes: number
  private readonly maxEntries: number
  private totalBytes = 0

  constructor(maxBytes = DEFAULT_MAX_BYTES, maxEntries = DEFAULT_MAX_ENTRIES) {
    this.maxBytes = maxBytes
    this.maxEntries = maxEntries
  }

  get(key: string) {
    const entry = this.entries.get(key)
    if (!entry) return undefined
    if (entry.expiresAt <= Date.now()) {
      this.entries.delete(key)
      this.totalBytes -= entry.blob.size
      return undefined
    }
    // Map insertion order doubles as a small LRU queue.
    this.entries.delete(key)
    this.entries.set(key, entry)
    return entry.blob
  }

  set(key: string, blob: Blob, ttlMs = 60 * 60 * 1000) {
    if (!key || blob.size <= 0 || blob.size > this.maxBytes) return
    const previous = this.entries.get(key)
    if (previous) {
      this.totalBytes -= previous.blob.size
      this.entries.delete(key)
    }
    this.entries.set(key, { blob, expiresAt: Date.now() + ttlMs })
    this.totalBytes += blob.size
    while (this.totalBytes > this.maxBytes || this.entries.size > this.maxEntries) {
      const oldest = this.entries.entries().next().value
      if (!oldest) break
      this.entries.delete(oldest[0])
      this.totalBytes -= oldest[1].blob.size
    }
  }

  clear() {
    this.entries.clear()
    this.totalBytes = 0
  }

  delete(key: string) {
    const entry = this.entries.get(key)
    if (!entry) return
    this.entries.delete(key)
    this.totalBytes -= entry.blob.size
  }

  get size() { return this.entries.size }
  get bytes() { return this.totalBytes }
}

export const markdownImageCache = new MarkdownImageCache()
