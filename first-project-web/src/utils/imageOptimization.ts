import { IMAGE_TYPES, MAX_IMAGE_BYTES } from './markdownEditing'

export interface ImageOptimizationResult {
  file: File
  optimized: boolean
}

const MAX_SOURCE_BYTES = 40 * 1024 * 1024
const MIN_OPTIMIZATION_BYTES = 350 * 1024

export function imageSelectionError(file: File, maxBytes = MAX_IMAGE_BYTES) {
  if (!IMAGE_TYPES.includes(file.type)) return '仅支持 PNG、JPG、GIF 或 WebP 图片。'
  if (file.size > Math.max(maxBytes, MAX_SOURCE_BYTES)) return '图片文件过大，请选择 40 MB 以内的图片。'
  return ''
}

export async function optimizeImageFile(
  file: File,
  options: { maxDimension?: number; maxBytes?: number; quality?: number } = {},
): Promise<ImageOptimizationResult> {
  const maxDimension = options.maxDimension ?? 1920
  const maxBytes = options.maxBytes ?? MAX_IMAGE_BYTES
  const quality = options.quality ?? 0.84
  const error = imageSelectionError(file, maxBytes)
  if (error) throw new Error(error)
  if (file.type === 'image/gif' || file.size < MIN_OPTIMIZATION_BYTES
      || typeof createImageBitmap !== 'function' || typeof document === 'undefined') {
    if (file.size > maxBytes) throw new Error(`图片优化后仍不能超过 ${Math.floor(maxBytes / 1024 / 1024)} MB。`)
    return { file, optimized: false }
  }

  const bitmap = await createImageBitmap(file)
  try {
    const scale = Math.min(1, maxDimension / Math.max(bitmap.width, bitmap.height))
    const width = Math.max(1, Math.round(bitmap.width * scale))
    const height = Math.max(1, Math.round(bitmap.height * scale))
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d')
    if (!context) return { file, optimized: false }
    context.drawImage(bitmap, 0, 0, width, height)
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/webp', quality))
    if (!blob || blob.size === 0 || (blob.size >= file.size && file.size <= maxBytes)) {
      if (file.size > maxBytes) throw new Error(`图片优化后仍不能超过 ${Math.floor(maxBytes / 1024 / 1024)} MB。`)
      return { file, optimized: false }
    }
    if (blob.size > maxBytes) throw new Error(`图片优化后仍不能超过 ${Math.floor(maxBytes / 1024 / 1024)} MB。`)
    const baseName = file.name.replace(/\.[^.]+$/u, '') || 'image'
    return { file: new File([blob], `${baseName}.webp`, { type: 'image/webp' }), optimized: true }
  } finally {
    bitmap.close()
  }
}
