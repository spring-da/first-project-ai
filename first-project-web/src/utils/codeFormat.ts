import { CODE_LANGUAGES, getLanguage, type CodeLanguage } from './codeLanguages'

// Dependency-free code beautifier. Each formatter is conservative: when it cannot
// confidently parse the input it falls back to whitespace normalization instead of
// corrupting the source.

const BRACE_LANGS = new Set([
  'javascript', 'typescript', 'java', 'c', 'cpp', 'csharp', 'go', 'rust', 'php',
  'dart', 'kotlin', 'swift', 'scala', 'objectivec', 'groovy', 'r', 'perl',
])

export function formatCode(code: string, language: string | null | undefined): string {
  const lang = getLanguage(language)
  if (lang.id === 'json') return formatJson(code)
  if (lang.kind === 'sql') return formatSql(code, lang.id)
  if (BRACE_LANGS.has(lang.id)) return reindentBraces(code, lang)
  return normalizeLines(code)
}

function normalizeLines(code: string) {
  return code
    .replace(/\r\n?/g, '\n')
    .replace(/[ \t]+$/gm, '')
    .replace(/\n{3,}/g, '\n\n')
    .replace(/^\n+/, '')
    .replace(/\s+$/, '')
}

function formatJson(code: string): string {
  const normalized = code.replace(/\r\n?/g, '\n')
  try {
    return JSON.stringify(JSON.parse(normalized), null, 2)
  } catch {
    try {
      return JSON.stringify(JSON.parse(sanitizeJson(normalized)), null, 2)
    } catch {
      return normalizeLines(normalized)
    }
  }
}

function sanitizeJson(code: string): string {
  let out = ''
  let i = 0
  const n = code.length
  let inString = false
  let quote = ''
  let escaped = false
  while (i < n) {
    const ch = code[i]
    if (inString) {
      out += ch
      if (escaped) escaped = false
      else if (ch === '\\') escaped = true
      else if (ch === quote) inString = false
      i += 1
      continue
    }
    if (ch === '"' || ch === "'") {
      inString = true
      quote = ch
      out += ch
      i += 1
      continue
    }
    if (ch === '/' && code[i + 1] === '/') {
      while (i < n && code[i] !== '\n') i += 1
      out += '\n'
      continue
    }
    if (ch === '/' && code[i + 1] === '*') {
      const end = code.indexOf('*/', i + 2)
      i = end === -1 ? n : end + 2
      continue
    }
    out += ch
    i += 1
  }
  return out.replace(/,\s*([}\]])/g, '$1')
}

interface BraceState {
  inBlockComment: boolean
  inString: string | null
}

function countBraces(line: string, lang: CodeLanguage, state: BraceState) {
  let opens = 0
  let closes = 0
  let i = 0
  const n = line.length
  const block = lang.blockComment
  while (i < n) {
    const ch = line[i]
    if (state.inBlockComment) {
      if (block && ch === block[1][0] && line.startsWith(block[1], i)) {
        state.inBlockComment = false
        i += block[1].length
        continue
      }
      i += 1
      continue
    }
    if (state.inString) {
      if (ch === '\\') {
        i += 2
        continue
      }
      if (ch === state.inString) state.inString = null
      i += 1
      continue
    }
    if (block && line.startsWith(block[0], i)) {
      state.inBlockComment = true
      i += block[0].length
      continue
    }
    if (lang.lineComment.some((c) => line.startsWith(c, i))) break
    if (ch === '"' || ch === "'" || ch === '`') {
      state.inString = ch
      i += 1
      continue
    }
    if (ch === '{') opens += 1
    else if (ch === '}') closes += 1
    i += 1
  }
  return { opens, closes }
}

function reindentBraces(code: string, lang: CodeLanguage): string {
  const lines = code.replace(/\r\n?/g, '\n').split('\n')
  const state: BraceState = { inBlockComment: false, inString: null }
  const out: string[] = []
  let indent = 0
  const size = 2
  for (const raw of lines) {
    const continuation = state.inBlockComment || state.inString !== null
    const trimmed = raw.replace(/[ \t]+$/g, '')
    const { opens, closes } = countBraces(trimmed, lang, state)
    if (continuation) {
      // Inside a multi-line string or block comment: preserve verbatim.
      out.push(raw)
      indent = Math.max(0, indent - closes + opens)
      continue
    }
    const leadingClose = /^\s*\}/.test(trimmed)
    const lineIndent = leadingClose ? Math.max(0, indent - 1) : indent
    const text = trimmed.trim()
    out.push(text ? ' '.repeat(lineIndent * size) + text : '')
    indent = Math.max(0, indent - closes + opens)
  }
  return normalizeLines(out.join('\n'))
}

interface SqlToken {
  text: string
  type: 'word' | 'string' | 'number' | 'comment' | 'punct'
}

// Every SQL keyword from every dialect, uppercased — used to decide which words
// become uppercase and which clause keywords drive line breaks.
const SQL_WORDS = new Set<string>()
for (const language of CODE_LANGUAGES) {
  if (language.kind === 'sql') {
    for (const word of language.keywords) SQL_WORDS.add(word.toUpperCase())
  }
}

const CLAUSE_BREAK = new Set([
  'SELECT', 'FROM', 'WHERE', 'GROUP', 'ORDER', 'HAVING', 'LIMIT', 'OFFSET', 'FETCH',
  'UNION', 'INTERSECT', 'EXCEPT', 'INSERT', 'UPDATE', 'DELETE', 'SET', 'VALUES',
  'CREATE', 'ALTER', 'DROP', 'TRUNCATE', 'WITH', 'RETURNING',
])

const JOIN_MODIFIERS = new Set(['LEFT', 'RIGHT', 'INNER', 'FULL', 'CROSS'])

const SQL_FUNCTIONS = new Set([
  'COUNT', 'SUM', 'AVG', 'MIN', 'MAX', 'ABS', 'ROUND', 'CEIL', 'CEILING', 'FLOOR',
  'POWER', 'SQRT', 'LENGTH', 'LEN', 'CHAR_LENGTH', 'UPPER', 'LOWER', 'TRIM', 'LTRIM',
  'RTRIM', 'SUBSTRING', 'SUBSTR', 'LEFT', 'RIGHT', 'REPLACE', 'CONCAT', 'CONCAT_WS',
  'INSTR', 'CHARINDEX', 'LOCATE', 'POSITION', 'CAST', 'CONVERT', 'COALESCE', 'NULLIF',
  'IFNULL', 'NVL', 'NVL2', 'ISNULL', 'CURRENT_DATE', 'CURRENT_TIMESTAMP', 'CURRENT_TIME',
  'NOW', 'SYSDATE', 'GETDATE', 'DATEADD', 'DATEDIFF', 'DATEPART', 'EXTRACT', 'TO_DATE',
  'TO_CHAR', 'TO_NUMBER', 'DATE_FORMAT', 'STR_TO_DATE', 'GROUP_CONCAT', 'STRING_AGG',
  'LISTAGG', 'ARRAY_AGG', 'JSON_AGG', 'JSONB_AGG', 'ROW_NUMBER', 'RANK', 'DENSE_RANK',
  'NTILE', 'LAG', 'LEAD', 'FIRST_VALUE', 'LAST_VALUE', 'NTH_VALUE', 'CUME_DIST',
  'PERCENT_RANK', 'RATIO_TO_REPORT', 'GREATEST', 'LEAST', 'SIGN', 'MOD', 'TRUNC',
  'RAND', 'RANDOM', 'MD5', 'SHA1', 'UNIX_TIMESTAMP', 'FROM_UNIXTIME', 'DAY', 'MONTH',
  'YEAR', 'HOUR', 'MINUTE', 'SECOND', 'DATE', 'TIME', 'TIMESTAMP', 'UNICODE', 'ASCII',
  'CHR', 'CHAR', 'DECODE', 'CASE', 'IF', 'IIF', 'EXPLODE', 'COLLECT_SET', 'STACK',
  // SQL type names also attach their parentheses without a space (VARCHAR(50))
  'VARCHAR', 'NVARCHAR', 'NCHAR', 'INT', 'INTEGER', 'BIGINT', 'SMALLINT', 'TINYINT',
  'MEDIUMINT', 'DECIMAL', 'NUMERIC', 'REAL', 'DOUBLE', 'FLOAT', 'BOOLEAN', 'BIT',
  'BINARY', 'VARBINARY', 'BLOB', 'CLOB', 'TEXT', 'DATETIME', 'DATETIME2', 'JSON',
  'JSONB', 'UUID', 'ARRAY', 'SERIAL', 'BIGSERIAL', 'INTERVAL', 'ENUM', 'XML', 'MONEY',
  'SMALLMONEY', 'UNIQUEIDENTIFIER', 'IMAGE', 'NTEXT', 'ROWID', 'NUMBER', 'RAW', 'LONG',
  'NCLOB', 'BFILE', 'TIMESTAMPTZ', 'GEOMETRY', 'POINT', 'YEAR',
])

function findMatchingParen(tokens: SqlToken[], open: number): number {
  let depth = 0
  for (let i = open; i < tokens.length; i += 1) {
    const p = tokens[i].text
    if (p === '(') depth += 1
    else if (p === ')') {
      depth -= 1
      if (depth === 0) return i
    }
  }
  return -1
}

function parenIsBlock(tokens: SqlToken[], open: number): boolean {
  const close = findMatchingParen(tokens, open)
  if (close === -1) return false
  for (let i = open + 1; i < close; i += 1) {
    const t = tokens[i]
    if (t.type === 'word' && CLAUSE_BREAK.has(t.text.toUpperCase())) return true
  }
  return false
}

function listWraps(tokens: SqlToken[], start: number): boolean {
  let paren = 0
  for (let i = start; i < tokens.length; i += 1) {
    const t = tokens[i]
    if (t.type === 'word') {
      if (paren === 0 && CLAUSE_BREAK.has(t.text.toUpperCase())) return false
      continue
    }
    if (t.type !== 'punct') continue
    if (t.text === '(') paren += 1
    else if (t.text === ')') paren = Math.max(0, paren - 1)
    else if (t.text === ',' && paren === 0) return true
  }
  return false
}

function sqlTokens(sql: string): SqlToken[] {
  const tokens: SqlToken[] = []
  let i = 0
  const n = sql.length
  while (i < n) {
    const ch = sql[i]
    if (/\s/.test(ch)) {
      i += 1
      continue
    }
    if (ch === '-' && sql[i + 1] === '-') {
      let j = sql.indexOf('\n', i)
      if (j === -1) j = n
      tokens.push({ text: sql.slice(i, j), type: 'comment' })
      i = j
      continue
    }
    if (ch === '#') {
      let j = sql.indexOf('\n', i)
      if (j === -1) j = n
      tokens.push({ text: sql.slice(i, j), type: 'comment' })
      i = j
      continue
    }
    if (ch === '/' && sql[i + 1] === '*') {
      const end = sql.indexOf('*/', i + 2)
      const j = end === -1 ? n : end + 2
      tokens.push({ text: sql.slice(i, j), type: 'comment' })
      i = j
      continue
    }
    if (ch === "'" || ch === '"' || ch === '`') {
      let j = i + 1
      while (j < n) {
        if (sql[j] === ch && sql[j + 1] === ch) {
          j += 2
          continue
        }
        if (sql[j] === ch) {
          j += 1
          break
        }
        j += 1
      }
      tokens.push({ text: sql.slice(i, j), type: 'string' })
      i = j
      continue
    }
    if (/[0-9]/.test(ch)) {
      let j = i
      while (j < n && /[0-9a-fA-F_.]/.test(sql[j] ?? '')) j += 1
      tokens.push({ text: sql.slice(i, j), type: 'number' })
      i = j
      continue
    }
    if (/[A-Za-z_]/.test(ch)) {
      let j = i
      while (j < n && /[\w$]/.test(sql[j] ?? '')) j += 1
      tokens.push({ text: sql.slice(i, j), type: 'word' })
      i = j
      continue
    }
    const two = sql.slice(i, i + 2)
    if (two === '<=' || two === '>=' || two === '<>' || two === '!=' || two === '||' || two === '&&') {
      tokens.push({ text: two, type: 'punct' })
      i += 2
      continue
    }
    tokens.push({ text: ch, type: 'punct' })
    i += 1
  }
  return tokens
}

// DataGrip-style "wrap everything" formatter: 4-space indent, uppercase keywords,
// SELECT / FROM / WHERE / JOIN clauses each on their own line, AND/OR indented.
function formatSql(sql: string, dialect: string): string {
  const tokens = sqlTokens(sql)
  const out: string[] = []
  let cur = ''
  let curIndent = 0
  let baseIndent = 0
  const parenKinds: ('inline' | 'block')[] = []
  const parenIndents: number[] = []
  const cases: number[] = []
  let prevUpper = ''
  let inOn = false
  let pendingTableList = false

  const flush = () => {
    const text = cur.trimEnd()
    if (text) out.push(text)
    cur = ''
  }
  const nl = (level: number) => {
    flush()
    cur = ' '.repeat(level)
    curIndent = level
  }
  const contentLevel = () => baseIndent + 4
  const inInlineParen = () => parenKinds.length > 0 && parenKinds[parenKinds.length - 1] === 'inline'
  const resetStatement = () => {
    parenKinds.length = 0
    parenIndents.length = 0
    baseIndent = 0
    cases.length = 0
    inOn = false
    prevUpper = ''
    pendingTableList = false
  }

  for (let i = 0; i < tokens.length; i += 1) {
    const t = tokens[i]

    if (t.type === 'comment') {
      if (cur.trim()) {
        if (!/\s$/.test(cur)) cur += ' '
        cur += t.text
      } else {
        flush()
        out.push(' '.repeat(curIndent) + t.text)
        cur = ' '.repeat(curIndent)
      }
      prevUpper = ''
      continue
    }

    if (t.type === 'string' || t.type === 'number') {
      if (cur && !/\s$/.test(cur) && !cur.endsWith('(') && !cur.endsWith('[') && !cur.endsWith('.')) cur += ' '
      cur += t.text
      prevUpper = ''
      continue
    }

    if (t.type === 'word') {
      const u = t.text.toUpperCase()
      const isKeyword = SQL_WORDS.has(u)
      const text = isKeyword ? u : t.text

      if (isKeyword && u === 'GO' && dialect === 'sqlserver') {
        cur += 'GO'
        flush()
        resetStatement()
        continue
      }

      // CASE expression family
      if (isKeyword && u === 'CASE') {
        if (cur && !/\s$/.test(cur)) cur += ' '
        cur += 'CASE'
        cases.push(curIndent)
        prevUpper = u
        continue
      }
      if (isKeyword && (u === 'WHEN' || u === 'ELSE')) {
        nl((cases[cases.length - 1] ?? curIndent) + 4)
        cur += u
        prevUpper = u
        continue
      }
      if (isKeyword && u === 'THEN') {
        if (cur && !/\s$/.test(cur)) cur += ' '
        cur += 'THEN'
        prevUpper = u
        continue
      }
      if (isKeyword && u === 'END') {
        const level = cases.pop() ?? curIndent
        nl(level)
        cur += 'END'
        prevUpper = u
        continue
      }

      // AND / OR predicates
      if (isKeyword && (u === 'AND' || u === 'OR')) {
        if (inOn || inInlineParen()) {
          if (cur && !/\s$/.test(cur)) cur += ' '
          cur += u
        } else {
          nl(Math.max(0, contentLevel() - 2))
          cur += u
        }
        prevUpper = u
        continue
      }

      // JOIN modifiers only break when followed by JOIN (LEFT(col) stays a call)
      if (isKeyword && JOIN_MODIFIERS.has(u)) {
        const next = tokens[i + 1]
        if (next?.type === 'word' && next.text.toUpperCase() === 'JOIN') {
          nl(contentLevel() + 4)
          cur += u
        } else if (cur && !/\s$/.test(cur) && !cur.endsWith('.') && !cur.endsWith('(') && !cur.endsWith('[')) {
          cur += ' '
          cur += text
        } else {
          cur += text
        }
        prevUpper = u
        continue
      }
      if (isKeyword && u === 'JOIN') {
        if (JOIN_MODIFIERS.has(prevUpper) && tokens[i - 1]?.type === 'word') {
          if (cur && !/\s$/.test(cur)) cur += ' '
          cur += 'JOIN'
        } else {
          nl(contentLevel() + 4)
          cur += 'JOIN'
        }
        inOn = false
        prevUpper = u
        continue
      }
      if (isKeyword && u === 'APPLY') {
        nl(contentLevel() + 4)
        cur += 'APPLY'
        inOn = false
        prevUpper = u
        continue
      }
      // ON stays on the JOIN line; its AND/OR/commas do not wrap
      if (isKeyword && u === 'ON') {
        if (cur && !/\s$/.test(cur)) cur += ' '
        cur += 'ON'
        inOn = true
        prevUpper = u
        continue
      }

      // Major clause keywords
      if (isKeyword && CLAUSE_BREAK.has(u)) {
        if (u === 'FROM' && prevUpper === 'DELETE') {
          if (cur && !/\s$/.test(cur)) cur += ' '
          cur += 'FROM'
          prevUpper = u
          continue
        }
        nl(baseIndent)
        cur += u
        prevUpper = u
        inOn = false
        if (u === 'SELECT' && listWraps(tokens, i + 1)) nl(contentLevel())
        else if (u === 'FROM' || u === 'WHERE' || u === 'HAVING') nl(contentLevel())
        else if (u === 'SET' || u === 'VALUES') nl(contentLevel())
        else if (u === 'RETURNING') nl(contentLevel())
        continue
      }

      // GROUP BY / ORDER BY
      if (isKeyword && u === 'BY' && (prevUpper === 'GROUP' || prevUpper === 'ORDER')) {
        cur += ' BY'
        nl(contentLevel())
        prevUpper = u
        continue
      }

      // CREATE TABLE / ALTER TABLE column lists wrap like a block
      if (isKeyword && u === 'TABLE' && (prevUpper === 'CREATE' || prevUpper === 'ALTER')) pendingTableList = true

      // plain word
      if (cur && !/\s$/.test(cur) && !cur.endsWith('(') && !cur.endsWith('[') && !cur.endsWith('.')) cur += ' '
      cur += text
      prevUpper = u
      continue
    }

    const p = t.text
    if (p === ',') {
      cur += ','
      if (!inOn && !inInlineParen()) nl(contentLevel())
      prevUpper = ''
      continue
    }
    if (p === '(') {
      const block = parenIsBlock(tokens, i) || pendingTableList
      const prevWordIsFn = prevUpper !== '' && SQL_FUNCTIONS.has(prevUpper)
      if (cur && !/\s$/.test(cur) && !prevWordIsFn && !cur.endsWith('.') && !cur.endsWith('[')) cur += ' '
      cur += '('
      parenKinds.push(block ? 'block' : 'inline')
      if (block) {
        parenIndents.push(curIndent)
        baseIndent = curIndent + 4
        nl(baseIndent)
      }
      pendingTableList = false
      prevUpper = ''
      continue
    }
    if (p === ')') {
      const kind = parenKinds.pop()
      if (kind === 'block') {
        const level = parenIndents.pop() ?? curIndent
        baseIndent = Math.max(0, level - 4)
        nl(level)
      }
      cur = cur.replace(/ $/, '')
      cur += ')'
      prevUpper = ''
      continue
    }
    if (p === '[') {
      if (cur && !/\s$/.test(cur) && !cur.endsWith('.') && !cur.endsWith('[')) cur += ' '
      cur += '['
      prevUpper = ''
      continue
    }
    if (p === ']') {
      cur += ']'
      prevUpper = ''
      continue
    }
    if (p === ';') {
      cur += ';'
      flush()
      resetStatement()
      continue
    }
    if (['=', '!=', '<>', '<=', '>=', '<', '>', '+', '-', '*', '/', '||'].includes(p)) {
      if (p === '*' && (inInlineParen() || cur.endsWith('.'))) {
        cur += '*'
        prevUpper = ''
        continue
      }
      if (cur && !/\s$/.test(cur)) cur += ' '
      cur += p
      if (!cur.endsWith(' ')) cur += ' '
      prevUpper = ''
      continue
    }
    if (p === '.') {
      cur = cur.replace(/ $/, '')
      cur += '.'
      prevUpper = ''
      continue
    }
    cur += p
    prevUpper = ''
  }
  flush()
  return out.join('\n').trimEnd()
}
