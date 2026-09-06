export interface TextSegment {
  type: 'text' | 'link'
  text: string
  href?: string
}

// CJK prose commonly places a URL directly before full-width punctuation. Stop
// there so the following sentence is never included in the link target.
const URL_PATTERN = /(?:https?:\/\/|www\.)[^\s<>"'，。！？；：、（）【】《》]+/giu
const TRAILING_PUNCTUATION = /[.,!?;:，。！？；：)\]}>）》】]+$/u

export function linkifyText(value: string): TextSegment[] {
  if (!value) return []
  const segments: TextSegment[] = []
  let cursor = 0

  for (const match of value.matchAll(URL_PATTERN)) {
    const index = match.index ?? 0
    if (index > cursor) segments.push({ type: 'text', text: value.slice(cursor, index) })
    const candidate = match[0]
    const trailing = candidate.match(TRAILING_PUNCTUATION)?.[0] ?? ''
    const text = trailing ? candidate.slice(0, -trailing.length) : candidate
    if (text) {
      segments.push({
        type: 'link',
        text,
        href: text.toLowerCase().startsWith('www.') ? `https://${text}` : text,
      })
    }
    if (trailing) segments.push({ type: 'text', text: trailing })
    cursor = index + candidate.length
  }

  if (cursor < value.length) segments.push({ type: 'text', text: value.slice(cursor) })
  return segments.length ? segments : [{ type: 'text', text: value }]
}
