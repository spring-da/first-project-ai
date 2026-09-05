/** Keep the selected directory visible when a sidebar or viewport consumes space. */
export function fitDirectoryTabs(items: { id: string; width: number }[], available: number, active: string): string[] {
  const selected = items.find((item) => item.id === active) ?? items[0]
  if (!selected || available <= 0) return []
  const result = new Set([selected.id])
  let used = Math.min(selected.width, available)
  for (const item of items) {
    if (result.has(item.id)) continue
    if (used + 6 + item.width > available) break
    result.add(item.id)
    used += 6 + item.width
  }
  return items.filter((item) => result.has(item.id)).map((item) => item.id)
}
