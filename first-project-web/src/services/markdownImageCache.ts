const DEFAULT_MAX_BYTES = 64 * 1024 * 1024
const DEFAULT_MAX_ENTRIES = 64

export class MarkdownImageCache {
  private readonly entries = new Map<string, Blob>()
  private readonly maxBytes: number
  private readonly maxEntries: number
  private totalBytes = 0

  constructor(maxBytes = DEFAULT_MAX_BYTES, maxEntries = DEFAULT_MAX_ENTRIES) {
    this.maxBytes = maxBytes
    this.maxEntries = maxEntries
  }

  get(key: string) {
    const blob = this.entries.get(key)
    if (!blob) return undefined
    // Map insertion order doubles as a small LRU queue.
    this.entries.delete(key)
    this.entries.set(key, blob)
    return blob
  }

  set(key: string, blob: Blob) {
    if (!key || blob.size <= 0 || blob.size > this.maxBytes) return
    const previous = this.entries.get(key)
    if (previous) {
      this.totalBytes -= previous.size
      this.entries.delete(key)
    }
    this.entries.set(key, blob)
    this.totalBytes += blob.size
    while (this.totalBytes > this.maxBytes || this.entries.size > this.maxEntries) {
      const oldest = this.entries.entries().next().value as [string, Blob] | undefined
      if (!oldest) break
      this.entries.delete(oldest[0])
      this.totalBytes -= oldest[1].size
    }
  }

  clear() {
    this.entries.clear()
    this.totalBytes = 0
  }

  get size() { return this.entries.size }
  get bytes() { return this.totalBytes }
}

export const markdownImageCache = new MarkdownImageCache()
