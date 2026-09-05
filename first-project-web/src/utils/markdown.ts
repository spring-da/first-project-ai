const BLOCK_START = /^(?:\s*$| {0,3}#{1,6}\s+| {0,3}(?:[-*_]\s*){3,}$| {0,3}>\s?| {0,3}(?:[-+*]|\d+\.)\s+| {0,3}```)/

export interface MarkdownHeading {
  level: number
  text: string
  id: string
}

export interface MarkdownHeadingTreeNode extends MarkdownHeading {
  children: MarkdownHeadingTreeNode[]
}

export function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function safeUrl(value: string) {
  const trimmed = value.trim()
  if (!trimmed) return null
  if (/^(?:https?:|mailto:)/i.test(trimmed)) return trimmed
  if (/^(?:#|\/|\.\/|\.\.\/)/.test(trimmed) && !trimmed.startsWith('//')) return trimmed
  return null
}

function inlineMarkdown(source: string) {
  const tokens: string[] = []
  const stash = (html: string) => {
    const index = tokens.push(html) - 1
    return `\u0000MD${index}\u0000`
  }

  let value = source.replace(/\u0000/g, '')
  value = value.replace(/`([^`\n]+)`/g, (_, code: string) => stash(`<code>${escapeHtml(code)}</code>`))
  value = value.replace(/!\[([^\]\n]*)\]\(([^)\s]+)(?:\s+["'][^"']*["'])?\)/g, (_, alt: string, url: string) => {
    const imageId = url.match(/^\/api\/v1\/markdown-images\/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})$/i)?.[1]
    if (imageId) return stash(`<span class="markdown-private-image" data-image-id="${imageId}"><img alt="${escapeHtml(alt)}" hidden /><button class="markdown-image-status" type="button" disabled>正在加载图片…</button></span>`)
    const href = safeUrl(url)
    return href
      ? stash(`<img src="${escapeHtml(href)}" alt="${escapeHtml(alt)}" loading="lazy" />`)
      : escapeHtml(alt)
  })
  value = value.replace(/\[([^\]\n]+)\]\(([^)\s]+)(?:\s+["'][^"']*["'])?\)/g, (_, label: string, url: string) => {
    const href = safeUrl(url)
    return href
      ? stash(`<a href="${escapeHtml(href)}" target="_blank" rel="noopener noreferrer">${escapeHtml(label)}</a>`)
      : escapeHtml(label)
  })

  value = escapeHtml(value)
    .replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
    .replace(/__([^_\n]+)__/g, '<strong>$1</strong>')
    .replace(/~~([^~\n]+)~~/g, '<del>$1</del>')
    .replace(/(^|[^*])\*([^*\n]+)\*(?!\*)/g, '$1<em>$2</em>')
    .replace(/(^|[^_])_([^_\n]+)_(?!_)/g, '$1<em>$2</em>')

  return value.replace(/\u0000MD(\d+)\u0000/g, (_, index: string) => tokens[Number(index)] ?? '')
}

function isTableDivider(value: string) {
  const cells = value.trim().replace(/^\||\|$/g, '').split('|')
  return cells.length > 1 && cells.every((cell) => /^\s*:?-{3,}:?\s*$/.test(cell))
}

function tableCells(value: string) {
  return value.trim().replace(/^\||\|$/g, '').split('|').map((cell) => cell.trim())
}

function headingText(source: string) {
  return source
    .replace(/!\[([^\]\n]*)\]\([^)]+\)/g, '$1')
    .replace(/\[([^\]\n]+)\]\([^)]+\)/g, '$1')
    .replace(/`([^`\n]+)`/g, '$1')
    .replace(/[*_~]/g, '')
    .replace(/\\([\\`*_[\]#])/g, '$1')
    .trim()
}

function headingId(text: string, occurrences: Map<string, number>, fallbackIndex: number) {
  const slug = text
    .normalize('NFKC')
    .toLocaleLowerCase()
    .replace(/[^\p{Letter}\p{Number}]+/gu, '-')
    .replace(/^-+|-+$/g, '') || `section-${fallbackIndex + 1}`
  const occurrence = (occurrences.get(slug) ?? 0) + 1
  occurrences.set(slug, occurrence)
  return `markdown-heading-${slug}${occurrence > 1 ? `-${occurrence}` : ''}`
}

/** Returns the same safe, unique heading anchors used by renderMarkdown. */
export function extractMarkdownHeadings(source: string): MarkdownHeading[] {
  const headings: MarkdownHeading[] = []
  const occurrences = new Map<string, number>()
  let fenced = false

  for (const line of source.replace(/\r\n?/g, '\n').split('\n')) {
    if (/^ {0,3}```/.test(line)) {
      fenced = !fenced
      continue
    }
    if (fenced) continue
    const match = line.match(/^ {0,3}(#{1,6})\s+(.+?)\s*#*\s*$/)
    if (!match) continue
    const text = headingText(match[2] ?? '')
    headings.push({
      level: match[1]?.length ?? 1,
      text: text || '未命名标题',
      id: headingId(text, occurrences, headings.length),
    })
  }
  return headings
}

/** Converts the document-order heading list into a semantic outline tree. */
export function buildMarkdownHeadingTree(headings: readonly MarkdownHeading[]): MarkdownHeadingTreeNode[] {
  const roots: MarkdownHeadingTreeNode[] = []
  const parents: MarkdownHeadingTreeNode[] = []

  for (const heading of headings) {
    const node: MarkdownHeadingTreeNode = { ...heading, children: [] }
    while (parents.length && parents[parents.length - 1]!.level >= node.level) parents.pop()
    const parent = parents[parents.length - 1]
    if (parent) parent.children.push(node)
    else roots.push(node)
    parents.push(node)
  }

  return roots
}

/**
 * Small, dependency-free Markdown renderer for the editor preview.
 * Raw HTML is always escaped and links/images only accept an allow-list of protocols.
 */
export function renderMarkdown(source: string) {
  const lines = source.replace(/\r\n?/g, '\n').split('\n')
  const output: string[] = []
  const headings = extractMarkdownHeadings(source)
  let headingIndex = 0
  let index = 0

  while (index < lines.length) {
    const line = lines[index] ?? ''
    if (!line.trim()) {
      index += 1
      continue
    }

    const fence = line.match(/^ {0,3}```\s*([^\s`]*)/)
    if (fence) {
      const language = (fence[1] ?? '').replace(/[^a-z0-9_+-]/gi, '').toLowerCase()
      const code: string[] = []
      index += 1
      while (index < lines.length && !/^ {0,3}```\s*$/.test(lines[index] ?? '')) {
        code.push(lines[index] ?? '')
        index += 1
      }
      if (index < lines.length) index += 1
      output.push(`<pre><code${language ? ` class="language-${language}"` : ''}>${escapeHtml(code.join('\n'))}</code></pre>`)
      continue
    }

    const heading = line.match(/^ {0,3}(#{1,6})\s+(.+?)\s*#*\s*$/)
    if (heading) {
      const level = heading[1]?.length ?? 1
      const id = headings[headingIndex]?.id ?? `markdown-heading-section-${headingIndex + 1}`
      headingIndex += 1
      output.push(`<h${level} id="${escapeHtml(id)}">${inlineMarkdown(heading[2] ?? '')}</h${level}>`)
      index += 1
      continue
    }

    if (/^ {0,3}(?:[-*_]\s*){3,}$/.test(line)) {
      output.push('<hr />')
      index += 1
      continue
    }

    if (index + 1 < lines.length && line.includes('|') && isTableDivider(lines[index + 1] ?? '')) {
      const headings = tableCells(line)
      const rows: string[][] = []
      index += 2
      while (index < lines.length && (lines[index] ?? '').includes('|') && (lines[index] ?? '').trim()) {
        rows.push(tableCells(lines[index] ?? ''))
        index += 1
      }
      output.push(`<div class="markdown-table-wrap"><table><thead><tr>${headings.map((cell) => `<th>${inlineMarkdown(cell)}</th>`).join('')}</tr></thead><tbody>${rows.map((row) => `<tr>${headings.map((_, cellIndex) => `<td>${inlineMarkdown(row[cellIndex] ?? '')}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`)
      continue
    }

    if (/^ {0,3}>/.test(line)) {
      const quote: string[] = []
      while (index < lines.length && /^ {0,3}>/.test(lines[index] ?? '')) {
        quote.push((lines[index] ?? '').replace(/^ {0,3}>\s?/, ''))
        index += 1
      }
      output.push(`<blockquote>${quote.map(inlineMarkdown).join('<br />')}</blockquote>`)
      continue
    }

    const listMatch = line.match(/^ {0,3}([-+*]|\d+\.)\s+(.+)/)
    if (listMatch) {
      const ordered = /\d+\./.test(listMatch[1] ?? '')
      const items: string[] = []
      const itemPattern = ordered ? /^ {0,3}\d+\.\s+(.+)/ : /^ {0,3}[-+*]\s+(.+)/
      while (index < lines.length) {
        const match = (lines[index] ?? '').match(itemPattern)
        if (!match) break
        const body = match[1] ?? ''
        const task = body.match(/^\[([ xX])\]\s+(.+)/)
        items.push(task
          ? `<li class="task-item"><input type="checkbox" disabled${task[1]?.toLowerCase() === 'x' ? ' checked' : ''} />${inlineMarkdown(task[2] ?? '')}</li>`
          : `<li>${inlineMarkdown(body)}</li>`)
        index += 1
      }
      const tag = ordered ? 'ol' : 'ul'
      output.push(`<${tag}>${items.join('')}</${tag}>`)
      continue
    }

    const paragraph: string[] = [line.trim()]
    index += 1
    while (index < lines.length && !BLOCK_START.test(lines[index] ?? '')) {
      if (index + 1 < lines.length && (lines[index] ?? '').includes('|') && isTableDivider(lines[index + 1] ?? '')) break
      paragraph.push((lines[index] ?? '').trim())
      index += 1
    }
    output.push(`<p>${paragraph.map(inlineMarkdown).join('<br />')}</p>`)
  }

  return output.join('\n')
}

export function markdownExcerpt(source: string, length = 150) {
  const text = source
    .replace(/^ {0,3}```[^\n]*\n?/gm, '')
    .replace(/[`*_#>~\[\]()-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  return text.length > length ? `${text.slice(0, length).trim()}…` : text
}
