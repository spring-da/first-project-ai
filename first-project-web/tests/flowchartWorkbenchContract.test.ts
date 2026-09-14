import assert from 'node:assert/strict'
import test from 'node:test'
import * as editorState from '../src/utils/flowchartEditorState.ts'

type NodeLibraryControllerFactory = (ownerKey: string, canvas: () => { add: (kind: 'rectangle' | 'diamond' | 'io') => void } | undefined, storage?: Pick<Storage, 'getItem' | 'setItem'>) => {
  key: string
  preferences: { libraryCollapsed: boolean; recentKinds: string[] }
  create: (kind: 'rectangle' | 'diamond' | 'io') => void
  dragStart: (kind: 'rectangle' | 'diamond' | 'io', event: DragEvent) => void
  toggleLibrary: () => void
}

test('node-library workspace actions forward creates and persist drag recents and collapse state', () => {
  const createNodeLibraryController = (editorState as Record<string, unknown>).createNodeLibraryController as NodeLibraryControllerFactory
  assert.equal(typeof createNodeLibraryController, 'function')
  const writes: Array<[string, string]> = []
  const storage = { getItem: () => null, setItem: (key: string, value: string) => writes.push([key, value]) }
  const created: string[] = []
  const controller = createNodeLibraryController('member-42', () => ({ add: kind => created.push(kind) }), storage)

  controller.create('diamond')
  controller.dragStart('io', {} as DragEvent)
  controller.toggleLibrary()

  assert.deepEqual(created, ['diamond'])
  assert.equal(controller.key, 'devnest-flowchart-workbench:member-42')
  assert.deepEqual(controller.preferences, { libraryCollapsed: true, inspectorCollapsed: false, recentKinds: ['io', 'diamond'] })
  assert.deepEqual(JSON.parse(writes.at(-1)![1]), controller.preferences)
})

test('native canvas drops accept only supported node-library payloads', () => {
  const readNativeFlowNodeKind = (editorState as Record<string, unknown>).readNativeFlowNodeKind as ((transfer: Pick<DataTransfer, 'getData'> | null) => string | null)
  assert.equal(typeof readNativeFlowNodeKind, 'function')
  assert.equal(readNativeFlowNodeKind({ getData: type => type === 'application/x-devnest-flow-node' ? 'diamond' : '' }), 'diamond')
  assert.equal(readNativeFlowNodeKind({ getData: () => 'unknown' }), null)
  assert.equal(readNativeFlowNodeKind(null), null)
})

test('node-library actions stay usable when workspace preference storage is unavailable', () => {
  const createNodeLibraryController = (editorState as Record<string, unknown>).createNodeLibraryController as NodeLibraryControllerFactory
  const storage = { getItem: () => { throw new Error('blocked') }, setItem: () => { throw new Error('blocked') } }
  const controller = createNodeLibraryController('member-42', () => undefined, storage)

  controller.dragStart('rectangle', {} as DragEvent)
  controller.toggleLibrary()

  assert.deepEqual(controller.preferences, { libraryCollapsed: true, inspectorCollapsed: false, recentKinds: ['rectangle'] })
})
