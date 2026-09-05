export interface CodeEdit { value: string; start: number; end: number }

export const LARGE_CODE_CHARACTER_LIMIT = 40_000
export const LARGE_CODE_LINE_LIMIT = 1_500

export interface CodeMetrics {
  characters: number
  lines: number
  large: boolean
}

/** Avoid split()/match() allocations when this runs for every editor update. */
export function measureCode(value: string): CodeMetrics {
  let lines = 1
  let offset = -1
  while ((offset = value.indexOf('\n', offset + 1)) !== -1) lines++
  return {
    characters: value.length,
    lines,
    large: value.length > LARGE_CODE_CHARACTER_LIMIT || lines > LARGE_CODE_LINE_LIMIT,
  }
}

/** Keep whole-document undo useful without retaining dozens of large source copies. */
export function appendBoundedHistory(history: string[], value: string, maxEntries = 40, maxCharacters = 600_000): string[] {
  if (history.at(-1) === value) return history
  const next = [...history, value]
  let characters = next.reduce((total, entry) => total + entry.length, 0)
  while (next.length > 1 && (next.length > maxEntries || characters > maxCharacters)) {
    characters -= next.shift()!.length
  }
  return next
}

export function codeKeyEdit(value: string, start: number, end: number, key: string, shift = false, tabSize = 2, escaped = false): CodeEdit | null {
  const pad = ' '.repeat(tabSize)
  const replace = (text: string, from = start, to = end, caret = from + text.length): CodeEdit => ({ value: value.slice(0, from) + text + value.slice(to), start: caret, end: caret })
  const lineStart = start === 0 ? 0 : value.lastIndexOf('\n', start - 1) + 1
  const before = value.slice(lineStart, start)
  const indent = before.match(/^[ \t]*/)?.[0] ?? ''
  if (key === 'Tab') {
    if (!shift && start === end) return replace(pad)
    const lastLine = end > start && value[end - 1] === '\n' ? end - 1 : end
    const lastBreak = value.indexOf('\n', lastLine)
    const blockEnd = lastBreak === -1 ? value.length : lastBreak
    const lines = value.slice(lineStart, blockEnd).split('\n')
    let delta = 0
    let firstDelta = 0
    const changed = lines.map((line, index) => {
      const removed = line.startsWith('\t') ? 1 : Math.min(tabSize, line.match(/^ */)?.[0].length ?? 0)
      const diff = shift ? -removed : tabSize
      if (!index) firstDelta = diff
      delta += diff
      return shift ? line.slice(removed) : pad + line
    }).join('\n')
    return { value: value.slice(0, lineStart) + changed + value.slice(blockEnd), start: Math.max(lineStart, start + firstDelta), end: Math.max(lineStart, end + delta) }
  }
  let inString = false
  let slashes = 0
  for (let i = 0; i < start; i++) {
    const char = value[i]
    if (char === '"' && (escaped ? slashes % 4 === 1 : slashes % 2 === 0)) inString = !inString
    slashes = char === '\\' ? slashes + 1 : 0
  }
  const opener = before.trimEnd().slice(-1)
  const pairs: Record<string, string> = { '{': '}', '[': ']', '(': ')' }
  if (key === 'Enter') {
    const extra = !inString && pairs[opener!] ? pad : ''
    const next = value.slice(end).match(/^[ \t]*([\]})])/)?.[1]
    const closing = extra && next === pairs[opener!]
    return replace(`\n${indent}${extra}${closing ? `\n${indent}` : ''}`, start, closing ? end + (value.slice(end).match(/^[ \t]*/)?.[0].length ?? 0) : end, start + 1 + indent.length + extra.length)
  }
  if (!inString && pairs[key]) {
    const selected = value.slice(start, end)
    const next = replace(key + selected + pairs[key])
    return { ...next, start: start + 1, end: end + 1 }
  }
  if (!escaped && key === '"' && !inString && slashes % 2 === 0) {
    const next = replace(`"${value.slice(start, end)}"`)
    return { ...next, start: start + 1, end: end + 1 }
  }
  if (start === end && value[start] === key && ((!inString && ']})'.includes(key)) || (!escaped && key === '"' && inString && slashes % 2 === 0))) return { value, start: start + 1, end: start + 1 }
  if (key === 'Backspace' && start === end && start > 0 && ((!inString && pairs[value[start - 1]!] && pairs[value[start - 1]!] === value[start]) || (!escaped && value[start - 1] === '"' && value[start] === '"' && inString))) return replace('', start - 1, end + 1, start - 1)
  return null
}
