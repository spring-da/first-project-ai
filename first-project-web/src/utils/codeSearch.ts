export interface CodeMatch { start: number; end: number }

function foldCase(value: string): string {
  // Preserve UTF-16 offsets: expanding lowercase mappings (e.g. İ → i + ◌̇)
  // must not shift subsequent matches or select only part of an original character.
  return value.replace(/[\p{Lu}\p{Lt}]/gu, (character) => {
    const lower = character.toLowerCase()
    return lower.length === character.length ? lower : character
  })
}

/** Literal, case-insensitive search. Keep offsets in the original text for textarea selections. */
export function findCodeMatches(source: string, query: string): CodeMatch[] {
  if (!query) return []
  const text = foldCase(source)
  const needle = foldCase(query)
  const matches: CodeMatch[] = []
  let offset = 0
  while ((offset = text.indexOf(needle, offset)) !== -1) {
    matches.push({ start: offset, end: offset + query.length })
    offset += query.length
  }
  return matches
}
