import assert from 'node:assert/strict'
import test from 'node:test'
import { linkifyText } from '../src/utils/linkify.ts'

test('plain text URLs and www links become safe external links', () => {
  assert.deepEqual(linkifyText('文档 https://example.com/a?q=1，备用 www.example.org/test。'), [
    { type: 'text', text: '文档 ' },
    { type: 'link', text: 'https://example.com/a?q=1', href: 'https://example.com/a?q=1' },
    { type: 'text', text: '，备用 ' },
    { type: 'link', text: 'www.example.org/test', href: 'https://www.example.org/test' },
    { type: 'text', text: '。' },
  ])
})

test('non-web protocols and ordinary text stay inert', () => {
  assert.deepEqual(linkifyText('javascript:alert(1) 邮箱 a@example.com'), [
    { type: 'text', text: 'javascript:alert(1) 邮箱 a@example.com' },
  ])
})
