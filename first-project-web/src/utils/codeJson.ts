export type JsonEncoding = 'json' | 'escaped' | 'literal'
export type JsonSource = { valid: true; text: string; encoding: JsonEncoding } | { valid: false; error: string }
export interface EmbeddedJson { value: string; start: number; end: number }
export type JsonPreflight = 'empty' | 'candidate' | 'incomplete' | 'invalid'

/**
 * Cheap, allocation-free shape check used before JSON.parse. It deliberately
 * does not try to repair input: its only job is to keep clearly incomplete or
 * noisy text away from the parser while the user is typing.
 */
export function preflightJson(input: string): JsonPreflight {
  const text = input.trim()
  if (!text) return 'empty'

  const first = text[0]!
  if (first !== '{' && first !== '[' && first !== '"') {
    if (/^(?:true|false|null|-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?)$/.test(text)) return 'candidate'
    if (/^(?:t(?:r(?:u(?:e)?)?)?|f(?:a(?:l(?:s(?:e)?)?)?)?|n(?:u(?:l(?:l)?)?)?|-?(?:\d+)?(?:\.\d*)?(?:[eE][+-]?\d*)?)$/.test(text)) return 'incomplete'
    return 'invalid'
  }

  // Multiline escaped JSON uses \" for its structural quotes. Bracket
  // balancing is intentionally conservative here; parseJsonSource remains
  // the source of truth and is called at most once before manual validation.
  if ((first === '{' || first === '[') && /\\"/.test(text)) {
    const expected = first === '{' ? '}' : ']'
    return text.at(-1) === expected ? 'candidate' : 'incomplete'
  }

  const stack: string[] = []
  let quoted = false
  let escaped = false
  let closedAt = -1
  for (let index = 0; index < text.length; index++) {
    const char = text[index]!
    if (quoted) {
      if (escaped) escaped = false
      else if (char === '\\') escaped = true
      else if (char === '"') {
        quoted = false
        if (first === '"') closedAt = index
      }
      continue
    }
    if (char === '"') { quoted = true; continue }
    if (char === '{') stack.push('}')
    else if (char === '[') stack.push(']')
    else if (char === '}' || char === ']') {
      if (stack.pop() !== char) return 'invalid'
      if (!stack.length) closedAt = index
    }
    if (closedAt >= 0 && text.slice(index + 1).trim()) return 'invalid'
  }
  if (quoted || stack.length) return 'incomplete'
  return closedAt === text.length - 1 ? 'candidate' : 'invalid'
}

export function tryParseJson(value: string): unknown | null {
  try { return JSON.parse(value) } catch { return null }
}

/** Decode exactly one layer; unknown escapes must never silently lose a slash. */
function decodeEscapedLines(text: string) {
  return text.split(/\r?\n/).map((line) => JSON.parse('"' + line + '"') as string).join('\n')
}

export function parseJsonSource(input: string): JsonSource {
  const text = input.trim()
  if (!text) return { valid: false, error: '输入 JSON，或创建一个空对象 / 数组。' }
  try {
    const value: unknown = JSON.parse(text)
    // Old snippets may contain a whole JSON object encoded as one string literal.
    if (typeof value === 'string' && /^[\[{]/.test(value.trim())) {
      try { JSON.parse(value); return { valid: true, text: value, encoding: 'literal' } } catch { /* A normal string. */ }
    }
    return { valid: true, text, encoding: 'json' }
  } catch (error) {
    // Escaped editor text always starts with a JSON container/scalar. Do not
    // duplicate and decode arbitrary log lines merely because they contain \".
    if (/^[\[{]/.test(text) && /\\"/.test(text)) {
      try {
        const decoded = decodeEscapedLines(text)
        JSON.parse(decoded)
        return { valid: true, text: decoded, encoding: 'escaped' }
      } catch { /* Show the original parser location. */ }
    }
    return { valid: false, error: error instanceof Error ? error.message : 'JSON 格式不正确' }
  }
}

function balancedJsonEnd(text: string, start: number): number {
  const stack: string[] = []
  let quoted = false
  let escaped = false
  for (let index = start; index < text.length; index++) {
    const char = text[index]!
    if (quoted) {
      if (escaped) escaped = false
      else if (char === '\\') escaped = true
      else if (char === '"') quoted = false
      continue
    }
    if (char === '"') { quoted = true; continue }
    if (char === '{') stack.push('}')
    else if (char === '[') stack.push(']')
    else if (char === '}' || char === ']') {
      if (stack.pop() !== char) return -1
      if (!stack.length) return index + 1
    }
  }
  return -1
}

/** Find the largest valid object/array embedded in a log without altering the source. */
export function extractEmbeddedJson(input: string): EmbeddedJson | null {
  let best: EmbeddedJson | null = null
  let attempts = 0
  for (let start = 0; start < input.length && attempts < 128; start++) {
    const char = input[start]
    if (char !== '{' && char !== '[') continue
    attempts++
    const end = balancedJsonEnd(input, start)
    if (end < 0 || (best && end - start <= best.value.length)) continue
    const value = input.slice(start, end)
    try {
      const parsed: unknown = JSON.parse(value)
      if (parsed !== null && typeof parsed === 'object') best = { value, start, end }
    } catch { /* This bracket pair belongs to the surrounding log text. */ }
  }
  return best
}

/** Format tokens rather than parsed values, preserving large integers and duplicate keys. */
export function prettyJson(text: string): string {
  JSON.parse(text)
  let depth = 0
  let offset = 0
  let previous = ''
  const output: string[] = []
  const newline = () => output.push('\n', '  '.repeat(depth))
  while (offset < text.length) {
    const char = text[offset]!
    if (/\s/.test(char)) { offset++; continue }
    if (char === '"') {
      const start = offset++
      while (offset < text.length) {
        if (text[offset] === '\\') offset += 2
        else if (text[offset++] === '"') break
      }
      output.push(text.slice(start, offset))
      previous = 'value'
    } else if (char === '{' || char === '[') {
      output.push(char)
      depth++
      let next = offset + 1
      while (next < text.length && /\s/.test(text[next]!)) next++
      if (text[next] !== '}' && text[next] !== ']') newline()
      offset++
      previous = char
    } else if (char === '}' || char === ']') {
      depth--
      if (previous !== '{' && previous !== '[') newline()
      output.push(char)
      offset++
      previous = char
    } else if (char === ',') {
      output.push(',')
      offset++
      newline()
      previous = char
    } else if (char === ':') {
      output.push(': ')
      offset++
      previous = char
    } else {
      const start = offset
      while (offset < text.length && !/[\s{}\[\],:]/.test(text[offset]!)) offset++
      output.push(text.slice(start, offset))
      previous = 'value'
    }
  }
  return output.join('')
}

export function encodeJsonText(text: string, encoding: JsonEncoding): string {
  const formatted = prettyJson(text)
  if (encoding === 'json') return formatted
  // Multiline escaped text intentionally keeps physical line breaks, without outer quotes.
  return formatted.split('\n').map((line) => JSON.stringify(line).slice(1, -1)).join('\n')
}

export function formatJsonText(input: string): string {
  const parsed = parseJsonSource(input)
  if (!parsed.valid) throw new Error(parsed.error)
  return encodeJsonText(parsed.text, parsed.encoding)
}

export function unescapeJsonText(input: string): { value: string; changed: boolean } {
  const parsed = parseJsonSource(input)
  if (!parsed.valid) throw new Error(parsed.error)
  return { value: prettyJson(parsed.text), changed: parsed.encoding !== 'json' }
}

export function escapeJsonText(input: string): { value: string; changed: boolean } {
  const parsed = parseJsonSource(input)
  if (!parsed.valid) throw new Error(parsed.error)
  return { value: encodeJsonText(parsed.text, 'escaped'), changed: parsed.encoding === 'json' }
}
