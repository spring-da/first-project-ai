import { getLanguage, type CodeLanguage } from './codeLanguages.ts'

// Dependency-free syntax highlighter. It always HTML-escapes the source first-class:
// every emitted segment is escaped before wrapping, so untrusted code never injects HTML.

const BOOLEANS = new Set(['true', 'false'])
const CONSTANTS = new Set(['null', 'nil', 'none', 'undefined', 'nan', 'infinity'])
export const MAX_HIGHLIGHT_CHARACTERS = 40_000

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function token(className: string, text: string) {
  return `<span class="tok-${className}">${escapeHtml(text)}</span>`
}

/** Keep visible escapes while highlighting keys and values in multiline escaped JSON. */
export function highlightEscapedJson(code: string): string {
  if (code.length > MAX_HIGHLIGHT_CHARACTERS) return escapeHtml(code.replace(/\r\n?/g, '\n'))
  let result = ''
  let offset = 0
  let plainStart = 0
  while (offset < code.length) {
    if (code[offset] !== '\\' || code[offset + 1] !== '"') { offset++; continue }
    result += highlightCode(code.slice(plainStart, offset), 'JSON')
    const start = offset
    offset += 2
    while (offset < code.length && code[offset] !== '\n') {
      if (code[offset] !== '\\') { offset++; continue }
      const slashStart = offset
      while (code[offset] === '\\') offset++
      if (code[offset] === '"' && (offset - slashStart) % 4 === 1) { offset++; break }
      if (code[offset] === '"') offset++
    }
    result += token(code.slice(offset).trimStart().startsWith(':') ? 'property' : 'string', code.slice(start, offset))
    plainStart = offset
  }
  return result + highlightCode(code.slice(plainStart), 'JSON')
}

function readQuoted(code: string, start: number, quote: string) {
  let i = start + 1
  const n = code.length
  while (i < n) {
    if (code[i] === '\\') {
      i += 2
      continue
    }
    if (code[i] === quote) {
      i += 1
      break
    }
    i += 1
  }
  return { text: code.slice(start, i), end: i }
}

function readNumber(code: string, start: number) {
  let i = start
  const n = code.length
  if (code[i] === '-') i += 1
  if (code[i] === '0' && /[xXbBoO]/.test(code[i + 1] ?? '')) {
    i += 2
    while (i < n && /[0-9a-fA-F_]/.test(code[i] ?? '')) i += 1
    return { text: code.slice(start, i), end: i }
  }
  while (i < n && /[0-9_]/.test(code[i] ?? '')) i += 1
  if (code[i] === '.' && /[0-9]/.test(code[i + 1] ?? '')) {
    i += 1
    while (i < n && /[0-9_]/.test(code[i] ?? '')) i += 1
  }
  if (/[eE]/.test(code[i] ?? '') && /[+\-0-9]/.test(code[i + 1] ?? '')) {
    i += 1
    if (code[i] === '+' || code[i] === '-') i += 1
    while (i < n && /[0-9_]/.test(code[i] ?? '')) i += 1
  }
  if (code[i] === 'n') i += 1
  // Malformed data must still advance the scanner. A zero-width token would
  // otherwise loop forever and eventually exhaust the browser's memory.
  const end = Math.max(start + 1, i)
  return { text: code.slice(start, end), end }
}

function isIdentifierStart(ch: string | undefined) {
  return !!ch && /[A-Za-z_$]/.test(ch)
}

function isIdentifierChar(ch: string | undefined) {
  return !!ch && /[\w$]/.test(ch)
}

function classifyWord(word: string, lang: CodeLanguage, nextChar: string | undefined) {
  const lower = word.toLocaleLowerCase()
  if (lang.keywords.includes(word) || lang.keywords.includes(lower)) {
    if (BOOLEANS.has(lower)) return token('boolean', word)
    if (CONSTANTS.has(lower)) return token('constant', word)
    return token('keyword', word)
  }
  if (/^[A-Z]/.test(word)) return token('type', word)
  if (nextChar === '(') return token('fn', word)
  return escapeHtml(word)
}

function highlightSource(code: string, lang: CodeLanguage): string {
  const n = code.length
  const out: string[] = []
  let i = 0
  while (i < n) {
    const ch = code[i]

    if (lang.blockComment && code.startsWith(lang.blockComment[0], i)) {
      const end = code.indexOf(lang.blockComment[1], i + lang.blockComment[0].length)
      const close = end === -1 ? n : end + lang.blockComment[1].length
      out.push(token('comment', code.slice(i, close)))
      i = close
      continue
    }

    const lineComment = lang.lineComment.find((c) => code.startsWith(c, i))
    if (lineComment) {
      const end = code.indexOf('\n', i)
      out.push(token('comment', code.slice(i, end === -1 ? n : end)))
      i = end === -1 ? n : end
      continue
    }

    if (ch === '"' || ch === "'" || ch === '`') {
      const res = readQuoted(code, i, ch)
      out.push(token('string', res.text))
      i = res.end
      continue
    }

    if (/[0-9]/.test(ch)) {
      const res = readNumber(code, i)
      out.push(token('number', res.text))
      i = res.end
      continue
    }

    if (isIdentifierStart(ch)) {
      let j = i
      while (j < n && isIdentifierChar(code[j])) j += 1
      out.push(classifyWord(code.slice(i, j), lang, code[j]))
      i = j
      continue
    }

    out.push(escapeHtml(ch))
    i += 1
  }
  return out.join('')
}

function isKeyAfter(code: string, from: number) {
  let j = from
  while (j < code.length && (code[j] === ' ' || code[j] === '\t')) j += 1
  return code[j] === ':'
}

function highlightData(code: string, lang: CodeLanguage): string {
  const n = code.length
  const out: string[] = []
  let i = 0
  while (i < n) {
    const ch = code[i]

    if (lang.blockComment && code.startsWith(lang.blockComment[0], i)) {
      const end = code.indexOf(lang.blockComment[1], i + lang.blockComment[0].length)
      const close = end === -1 ? n : end + lang.blockComment[1].length
      out.push(token('comment', code.slice(i, close)))
      i = close
      continue
    }

    const lineComment = lang.lineComment.find((c) => code.startsWith(c, i))
    if (lineComment) {
      const end = code.indexOf('\n', i)
      out.push(token('comment', code.slice(i, end === -1 ? n : end)))
      i = end === -1 ? n : end
      continue
    }

    if (ch === '"' || ch === "'") {
      const res = readQuoted(code, i, ch)
      out.push(token(isKeyAfter(code, res.end) ? 'property' : 'string', res.text))
      i = res.end
      continue
    }

    if (/[0-9]/.test(ch) || (ch === '-' && /[0-9]/.test(code[i + 1] ?? ''))) {
      const res = readNumber(code, i)
      out.push(token('number', res.text))
      i = res.end
      continue
    }

    if (isIdentifierStart(ch)) {
      let j = i
      while (j < n && /[\w-]/.test(code[j] ?? '')) j += 1
      const word = code.slice(i, j)
      const lower = word.toLocaleLowerCase()
      if (BOOLEANS.has(lower)) out.push(token('boolean', word))
      else if (CONSTANTS.has(lower) || word === '~') out.push(token('constant', word))
      else if (isKeyAfter(code, j)) out.push(token('property', word))
      else out.push(escapeHtml(word))
      i = j
      continue
    }

    out.push(escapeHtml(ch))
    i += 1
  }
  return out.join('')
}

function highlightCss(code: string, lang: CodeLanguage): string {
  const n = code.length
  const out: string[] = []
  let i = 0
  while (i < n) {
    const ch = code[i]

    if (lang.blockComment && code.startsWith(lang.blockComment[0], i)) {
      const end = code.indexOf(lang.blockComment[1], i + lang.blockComment[0].length)
      const close = end === -1 ? n : end + lang.blockComment[1].length
      out.push(token('comment', code.slice(i, close)))
      i = close
      continue
    }

    const lineComment = lang.lineComment.find((c) => code.startsWith(c, i))
    if (lineComment) {
      const end = code.indexOf('\n', i)
      out.push(token('comment', code.slice(i, end === -1 ? n : end)))
      i = end === -1 ? n : end
      continue
    }

    if (ch === '"' || ch === "'") {
      const res = readQuoted(code, i, ch)
      out.push(token('string', res.text))
      i = res.end
      continue
    }

    if (ch === '@') {
      let j = i + 1
      while (j < n && /[\w-]/.test(code[j] ?? '')) j += 1
      out.push(token('keyword', code.slice(i, j)))
      i = j
      continue
    }

    if (ch === '#' && /[0-9a-fA-F]/.test(code[i + 1] ?? '')) {
      let j = i + 1
      while (j < n && /[0-9a-fA-F]/.test(code[j] ?? '')) j += 1
      out.push(token('number', code.slice(i, j)))
      i = j
      continue
    }

    if (/[0-9]/.test(ch)) {
      const res = readNumber(code, i)
      out.push(token('number', res.text))
      i = res.end
      continue
    }

    if (isIdentifierStart(ch)) {
      let j = i
      while (j < n && /[\w-]/.test(code[j] ?? '')) j += 1
      const word = code.slice(i, j)
      out.push(token(isKeyAfter(code, j) ? 'property' : 'tag', word))
      i = j
      continue
    }

    out.push(escapeHtml(ch))
    i += 1
  }
  return out.join('')
}

function highlightMarkup(code: string, lang: CodeLanguage): string {
  const n = code.length
  const out: string[] = []
  let i = 0
  while (i < n) {
    if (lang.blockComment && code.startsWith(lang.blockComment[0], i)) {
      const end = code.indexOf(lang.blockComment[1], i + lang.blockComment[0].length)
      const close = end === -1 ? n : end + lang.blockComment[1].length
      out.push(token('comment', code.slice(i, close)))
      i = close
      continue
    }

    const ch = code[i]
    if (ch === '&') {
      const end = code.indexOf(';', i)
      if (end !== -1 && end - i <= 10) {
        out.push(token('constant', code.slice(i, end + 1)))
        i = end + 1
        continue
      }
      out.push(escapeHtml(ch))
      i += 1
      continue
    }

    if (ch === '<') {
      const res = readTag(code, i)
      out.push(res.html)
      i = res.end
      continue
    }

    out.push(escapeHtml(ch))
    i += 1
  }
  return out.join('')
}

function readTag(code: string, start: number): { html: string; end: number } {
  const n = code.length
  let i = start + 1
  const parts: string[] = ['<']
  if (code[i] === '/') {
    parts.push('/')
    i += 1
  }

  const nameStart = i
  while (i < n && /[A-Za-z0-9:_-]/.test(code[i] ?? '')) i += 1
  if (i > nameStart) parts.push(token('tag', code.slice(nameStart, i)))

  while (i < n && code[i] !== '>') {
    const ch = code[i]
    if (ch === '"' || ch === "'") {
      const res = readQuoted(code, i, ch)
      parts.push(token('string', res.text))
      i = res.end
      continue
    }
    if (/[A-Za-z_:@]/.test(ch)) {
      let j = i
      while (j < n && /[A-Za-z0-9_:@.-]/.test(code[j] ?? '')) j += 1
      parts.push(token('attr', code.slice(i, j)))
      i = j
      continue
    }
    parts.push(escapeHtml(ch))
    i += 1
  }
  if (code[i] === '>') {
    parts.push(escapeHtml('>'))
    i += 1
  }
  return { html: parts.join(''), end: i }
}

export function highlightCode(code: string, language: string | null | undefined): string {
  const lang = getLanguage(language)
  const source = code.replace(/\r\n?/g, '\n')
  if (!source) return ''
  // Large sources stay as one escaped text node. Thousands of token spans are
  // expensive to patch and can exhaust the browser renderer during a paste.
  if (source.length > MAX_HIGHLIGHT_CHARACTERS) return escapeHtml(source)
  switch (lang.kind) {
    case 'markup':
      return highlightMarkup(source, lang)
    case 'css':
      return highlightCss(source, lang)
    case 'json':
      return highlightData(source, lang)
    case 'text':
      return escapeHtml(source)
    case 'sql':
    default:
      return highlightSource(source, lang)
  }
}
