import assert from 'node:assert/strict'
import { test } from 'node:test'
import { completeImageUpload, handleSaveShortcut, imageMarkdown, MAX_IMAGE_BYTES, needsDocumentName, validateImageFile } from '../src/utils/markdownEditing.ts'
import { buildMarkdownHeadingTree, extractMarkdownHeadings, renderMarkdown } from '../src/utils/markdown.ts'

test('new files need an explicit title or filename; existing documents keep their save behavior', () => {
  for (const name of ['', 'untitled.md', ' UNTITLED.MD ']) assert.equal(needsDocumentName('', name, null), true)
  assert.equal(needsDocumentName('我的笔记', 'untitled.md', null), false)
  assert.equal(needsDocumentName('', '学习笔记.md', null), false)
  assert.equal(needsDocumentName('', 'untitled.md', 'existing'), false)
})

test('Ctrl/Cmd+S prevents the browser save dialog and dispatches only one active-editor save', () => {
  let saved = 0
  let prevented = 0
  const event = { key: 's', ctrlKey: true, metaKey: false, altKey: false, shiftKey: false, isComposing: false, repeat: false, preventDefault: () => prevented++, stopPropagation: () => {} }
  handleSaveShortcut(event, true, () => saved++)
  handleSaveShortcut({ ...event, key: 'S', ctrlKey: false, metaKey: true }, true, () => saved++)
  handleSaveShortcut({ ...event, repeat: true }, true, () => saved++)
  assert.equal(saved, 2)
  assert.equal(prevented, 3)
  handleSaveShortcut(event, false, () => saved++)
  for (const changed of [{ altKey: true }, { shiftKey: true }, { isComposing: true }, { key: 'x' }]) handleSaveShortcut({ ...event, ...changed }, true, () => saved++)
  assert.equal(saved, 2)
})

test('image upload constraints reject empty, oversized and active-content files', () => {
  assert.equal(validateImageFile({ name: '截图.png', type: 'image/png', size: MAX_IMAGE_BYTES }), null)
  assert.equal(validateImageFile({ name: 'photo.webp', type: '', size: 20 }), null)
  assert.match(validateImageFile({ name: 'large.jpg', type: 'image/jpeg', size: MAX_IMAGE_BYTES + 1 })!, /20 MB/)
  assert.ok(validateImageFile({ name: 'empty.png', type: 'image/png', size: 0 }))
  assert.ok(validateImageFile({ name: 'script.svg', type: 'image/svg+xml', size: 30 }))
})

test('completing an image upload preserves concurrent typing and removed placeholders', () => {
  const marker = '![上传中](devnest-upload:one)'
  const content = `原有文字\n${marker}\n上传时新写的文字`
  assert.equal(completeImageUpload(content, marker, '![图片](/image)'), '原有文字\n![图片](/image)\n上传时新写的文字')
  assert.equal(completeImageUpload('已手动移除', marker, '![图片](/image)'), '已手动移除')
  assert.equal(completeImageUpload(content, marker, ''), '原有文字\n\n上传时新写的文字')
})

test('private images render through authenticated placeholders, never bearer-token or expiring URLs', () => {
  const url = '/api/v1/markdown-images/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'
  const syntax = imageMarkdown('a[bad]\\name.png', url)
  const html = renderMarkdown(syntax)
  assert.match(html, /data-image-id="aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"/)
  assert.doesNotMatch(html, /src=/)
  assert.doesNotMatch(renderMarkdown('```\n' + syntax + '\n```'), /data-image-id=/)
  assert.throws(() => imageMarkdown('test.png', 'javascript:alert(1)'))
  assert.doesNotMatch(renderMarkdown('![x](javascript:alert(1))'), /<img/)
  assert.match(renderMarkdown('<script>alert(1)</script>'), /&lt;script&gt;/)
})

test('markdown headings produce safe unique anchors for the collapsible outline', () => {
  const source = '# 标题\n## 安装 `SDK`\n```md\n# 代码块不是标题\n```\n## 安装 SDK\n### [接口](https://example.com)'
  assert.deepEqual(extractMarkdownHeadings(source), [
    { level: 1, text: '标题', id: 'markdown-heading-标题' },
    { level: 2, text: '安装 SDK', id: 'markdown-heading-安装-sdk' },
    { level: 2, text: '安装 SDK', id: 'markdown-heading-安装-sdk-2' },
    { level: 3, text: '接口', id: 'markdown-heading-接口' },
  ])
  const html = renderMarkdown(source)
  assert.match(html, /<h1 id="markdown-heading-标题">标题<\/h1>/)
  assert.match(html, /<h2 id="markdown-heading-安装-sdk-2">安装 SDK<\/h2>/)
  assert.doesNotMatch(html, /id="markdown-heading-代码块不是标题"/)
})

test('markdown headings become a nested tree even when heading levels are skipped', () => {
  const headings = extractMarkdownHeadings('# 开始\n### 深入\n#### 细节\n## 回到第二级\n# 结束')
  assert.deepEqual(buildMarkdownHeadingTree(headings), [
    {
      level: 1, text: '开始', id: 'markdown-heading-开始', children: [
        {
          level: 3, text: '深入', id: 'markdown-heading-深入', children: [
            { level: 4, text: '细节', id: 'markdown-heading-细节', children: [] },
          ],
        },
        { level: 2, text: '回到第二级', id: 'markdown-heading-回到第二级', children: [] },
      ],
    },
    { level: 1, text: '结束', id: 'markdown-heading-结束', children: [] },
  ])
})
