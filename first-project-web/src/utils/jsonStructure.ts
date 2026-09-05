import { encodeJsonText, parseJsonSource } from './codeJson.ts'

export type JsonType = 'object' | 'array' | 'string' | 'number' | 'boolean' | 'null'
export interface JsonNode {
  type: JsonType
  start: number
  end: number
  key?: string
  keyStart?: number
  raw: string
  children: JsonNode[]
}
export type JsonEdit =
  | { kind: 'value'; value: string }
  | { kind: 'rename'; name: string }
  | { kind: 'add' }
  | { kind: 'remove' }

/** Source ranges keep edits lossless for unrelated numbers, keys and string escapes. */
export function buildJsonTree(text: string, alreadyValidated = false): JsonNode {
  if (!alreadyValidated) JSON.parse(text)
  let offset = 0
  const whitespace = () => { while (offset < text.length && /\s/.test(text[offset]!)) offset++ }
  const stringEnd = () => {
    offset++
    while (offset < text.length) {
      const char = text[offset++]
      if (char === '\\') offset++
      else if (char === '"') break
    }
  }
  function read(depth: number): JsonNode {
    if (depth > 100) throw new Error('嵌套超过 100 层，请使用代码视图编辑。')
    whitespace()
    const start = offset
    const char = text[offset]
    const children: JsonNode[] = []
    let type: JsonType
    if (char === '{' || char === '[') {
      type = char === '{' ? 'object' : 'array'
      const closing = char === '{' ? '}' : ']'
      offset++
      whitespace()
      while (text[offset] !== closing) {
        let key: string | undefined
        let keyStart: number | undefined
        if (type === 'object') {
          keyStart = offset
          stringEnd()
          key = JSON.parse(text.slice(keyStart, offset)) as string
          whitespace()
          offset++
        }
        const child = read(depth + 1)
        children.push({ ...child, key, keyStart })
        whitespace()
        if (text[offset] !== ',') break
        offset++
        whitespace()
      }
      offset++
    } else if (char === '"') { type = 'string'; stringEnd() }
    else {
      while (offset < text.length && !/[\s,\]}]/.test(text[offset]!)) offset++
      const raw = text.slice(start, offset)
      type = raw === 'null' ? 'null' : raw === 'true' || raw === 'false' ? 'boolean' : 'number'
    }
    // Container substrings can overlap across every nesting level and multiply
    // memory usage. Only leaf nodes need their raw lexeme for field editing.
    return { type, start, end: offset, raw: children.length || type === 'object' || type === 'array' ? '' : text.slice(start, offset), children }
  }
  return read(0)
}

export function countJsonNodes(root: JsonNode, stopAfter = Number.POSITIVE_INFINITY): number {
  let count = 0
  const pending = [root]
  while (pending.length && count <= stopAfter) {
    const node = pending.pop()!
    count++
    pending.push(...node.children)
  }
  return count
}

export function editJsonSource(source: string, path: number[], edit: JsonEdit): string {
  const parsed = parseJsonSource(source)
  if (!parsed.valid) throw new Error(parsed.error)
  let node = buildJsonTree(parsed.text, true)
  let parent: JsonNode | undefined
  for (const index of path) {
    parent = node
    const child = node.children[index]
    if (!child) throw new Error('字段已变化，请重试。')
    node = child
  }
  let start = node.start
  let end = node.end
  let replacement = ''
  if (edit.kind === 'value') {
    JSON.parse(edit.value)
    replacement = edit.value
  } else if (edit.kind === 'rename') {
    if (node.keyStart === undefined || !parent) throw new Error('只能重命名对象字段。')
    if (parent.children.some((child) => child !== node && child.key === edit.name)) throw new Error('已存在同名字段。')
    start = node.keyStart
    end = node.start
    replacement = `${JSON.stringify(edit.name)}: `
  } else if (edit.kind === 'add') {
    if (node.type !== 'object' && node.type !== 'array') throw new Error('只能向对象或数组添加字段。')
    start = end = node.end - 1
    let name = 'field'
    let index = 1
    while (node.children.some((child) => child.key === name)) name = `field${index++}`
    replacement = `${node.children.length ? ',' : ''}${node.type === 'object' ? `${JSON.stringify(name)}: ` : ''}null`
  } else {
    if (!parent) throw new Error('根节点不能删除，请修改根节点类型。')
    const index = path[path.length - 1]!
    const next = parent.children[index + 1]
    const previous = parent.children[index - 1]
    start = node.keyStart ?? node.start
    if (next) end = next.keyStart ?? next.start
    else if (previous) start = previous.end
  }
  return encodeJsonText(parsed.text.slice(0, start) + replacement + parsed.text.slice(end), parsed.encoding)
}
