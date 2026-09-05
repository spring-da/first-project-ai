export const MAX_IMAGE_BYTES = 20 * 1024 * 1024
export const MAX_IMAGE_BATCH = 5
export const IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/gif', 'image/webp']

export function isDefaultFileName(name: string) {
  return !name.trim() || /^untitled(?:\.md|\.markdown|\.mdown)?$/i.test(name.trim())
}

export function needsDocumentName(title: string, fileName: string, editingId: string | null) {
  return !editingId && !title.trim() && isDefaultFileName(fileName)
}

export function handleSaveShortcut(event: Pick<KeyboardEvent, 'key' | 'ctrlKey' | 'metaKey' | 'altKey' | 'shiftKey' | 'isComposing' | 'repeat' | 'preventDefault' | 'stopPropagation'>, active: boolean, save: () => void) {
  if (!active || event.isComposing || !(event.ctrlKey || event.metaKey) || event.altKey || event.shiftKey || event.key.toLowerCase() !== 's') return false
  event.preventDefault()
  event.stopPropagation()
  if (!event.repeat) save()
  return true
}

export function validateImageFile(file: Pick<File, 'type' | 'size' | 'name'>) {
  if (!file.size) return '图片是空文件，请重新选择。'
  if (file.size > MAX_IMAGE_BYTES) return '单张图片不能超过 20 MB。'
  if (!IMAGE_TYPES.includes(file.type) && !(file.type === '' && /\.(png|jpe?g|gif|webp)$/i.test(file.name))) return '仅支持 PNG、JPG、GIF、WebP 图片，不支持 SVG。'
  return null
}

export const MANAGED_IMAGE_URL = /^\/api\/v1\/markdown-images\/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})$/i

export function imageMarkdown(fileName: string, url: string) {
  if (!MANAGED_IMAGE_URL.test(url)) throw new Error('图片接口返回了无效的图片地址。')
  const alt = fileName.replace(/\.[^.]+$/, '').replace(/[\[\]\\!\r\n]/g, ' ').trim().slice(0, 100) || '图片'
  return `![${alt}](${url})`
}

// Replace only our exact pending marker; typing elsewhere is never overwritten.
export function completeImageUpload(content: string, marker: string, markdown: string) {
  return content.includes(marker) ? content.replace(marker, markdown) : content
}
