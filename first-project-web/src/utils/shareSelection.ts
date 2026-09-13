import type { ShareReference, ShareSelection } from '../types/sharing.ts'

export function prepareShareSelection(items: ShareSelection[]): ShareReference[] {
  const unique = new Map(items.map(item => [`${item.type}:${item.id}`, { type: item.type, id: item.id }]))
  if (!unique.size) throw new Error('请先选择要分享的内容。')
  if (unique.size > 100) throw new Error('每次最多分享 100 项内容，请减少选择后重试。')
  return [...unique.values()]
}

export function shareExpiration(days: number, now = Date.now()) {
  if (!Number.isInteger(days) || days < 1 || days > 365) throw new Error('有效期请输入 1–365 天的整数。')
  return new Date(now + days * 86_400_000).toISOString()
}
