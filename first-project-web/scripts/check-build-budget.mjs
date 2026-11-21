import { readdir, readFile, stat } from 'node:fs/promises'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const assetsDirectory = new URL('../dist/assets/', import.meta.url)
const assetsPath = fileURLToPath(assetsDirectory)
const indexFile = new URL('../dist/index.html', import.meta.url)
const maxJavaScriptChunkBytes = 500_000

const chunks = (await readdir(assetsDirectory)).filter((name) => name.endsWith('.js'))
const sizes = await Promise.all(chunks.map(async (name) => ({
  name,
  bytes: (await stat(join(assetsPath, name))).size,
})))
const oversized = sizes.filter(({ bytes }) => bytes > maxJavaScriptChunkBytes)
if (oversized.length) {
  const details = oversized.map(({ name, bytes }) => `${name}: ${(bytes / 1000).toFixed(1)} kB`).join(', ')
  throw new Error(`JavaScript 构建预算超限（单块最多 500 kB）：${details}`)
}

const indexHtml = await readFile(indexFile, 'utf8')
if (/rel="modulepreload"[^>]+href="[^"]*\/graph-[^"]+\.js"/.test(indexHtml)) {
  throw new Error('流程图引擎被加入首屏预加载，请恢复为按需加载。')
}

const largest = sizes.sort((left, right) => right.bytes - left.bytes)[0]
console.log(`Build budget passed: largest JS chunk ${largest?.name ?? 'none'} (${((largest?.bytes ?? 0) / 1000).toFixed(1)} kB).`)
