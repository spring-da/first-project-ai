/**
 * Balances plain text between two side-by-side preview panes without dropping
 * any source characters. Newlines are preferred; long single lines fall back
 * to a nearby readable separator.
 */
export function splitPreviewContent(value: string): [string, string] {
  if (!value) return ['', '']

  const lines = value.split('\n')
  if (lines.length > 1) {
    const weights = lines.map((line) => Math.max(1, Math.ceil(line.length / 88)))
    const target = weights.reduce((sum, weight) => sum + weight, 0) / 2
    let consumed = 0
    let splitAt = 1
    let closestDistance = Number.POSITIVE_INFINITY

    for (let index = 1; index < lines.length; index += 1) {
      consumed += weights[index - 1]!
      const distance = Math.abs(target - consumed)
      if (distance < closestDistance) {
        closestDistance = distance
        splitAt = index
      }
    }
    return [lines.slice(0, splitAt).join('\n'), lines.slice(splitAt).join('\n')]
  }

  if (value.length < 120) return [value, '']
  const middle = Math.floor(value.length / 2)
  const candidates = [...value.matchAll(/[\s,;，。！？、]/g)]
    .map((match) => (match.index ?? 0) + match[0].length)
    .filter((index) => index >= value.length * .25 && index <= value.length * .75)
  const splitAt = candidates.reduce(
    (closest, index) => Math.abs(index - middle) < Math.abs(closest - middle) ? index : closest,
    middle,
  )
  return [value.slice(0, splitAt), value.slice(splitAt)]
}
