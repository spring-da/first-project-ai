/** A point in the graph's local coordinate space. */
export interface EdgePoint {
  x: number
  y: number
}

export interface OrthogonalSegment {
  /** Index of the segment's first point in the source point list. */
  index: number
  start: EdgePoint
  end: EdgePoint
  axis: 'x' | 'y'
  midpoint: EdgePoint
  length: number
}

export interface OrthogonalMoveBounds {
  minX?: number
  maxX?: number
  minY?: number
  maxY?: number
}

export interface OrthogonalMoveOptions {
  /** Minimum distance to keep between a moved segment and its neighbours. */
  minGap?: number
  /** Snap the moved coordinate to this grid size. `grid` is accepted as an alias. */
  gridSize?: number
  grid?: number
  bounds?: OrthogonalMoveBounds
}

export interface OrthogonalInsertOptions {
  /** Perpendicular dogleg offset and minimum length of each new leg. */
  minGap?: number
}

export interface EdgeDragSnapshot<TRouter = unknown> {
  vertices: EdgePoint[]
  router: TRouter
}

const DEFAULT_MIN_GAP = 8
const DEFAULT_MIN_LENGTH = 1

function finiteNumber(value: unknown, fallback = 0): number {
  const number = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(number) ? number : fallback
}

function clonePoint(point: EdgePoint, fallback?: EdgePoint): EdgePoint {
  return {
    x: finiteNumber(point?.x, fallback?.x ?? 0),
    y: finiteNumber(point?.y, fallback?.y ?? 0),
  }
}

function clonePoints(points: readonly EdgePoint[]): EdgePoint[] {
  const result: EdgePoint[] = []
  for (const point of points ?? []) result.push(clonePoint(point, result[result.length - 1]))
  return result
}

function samePoint(a: EdgePoint, b: EdgePoint): boolean {
  return a.x === b.x && a.y === b.y
}

function areCollinear(a: EdgePoint, b: EdgePoint, c: EdgePoint): boolean {
  return (a.x === b.x && b.x === c.x) || (a.y === b.y && b.y === c.y)
}

function segmentAxis(start: EdgePoint, end: EdgePoint): 'x' | 'y' {
  if (start.y === end.y) return 'x'
  if (start.x === end.x) return 'y'
  // A malformed/preview path can contain a diagonal segment. Pick its
  // dominant axis so editing remains deterministic and DOM-free.
  return Math.abs(end.x - start.x) >= Math.abs(end.y - start.y) ? 'x' : 'y'
}

function segmentLength(start: EdgePoint, end: EdgePoint, axis: 'x' | 'y'): number {
  return axis === 'x' ? Math.abs(end.x - start.x) : Math.abs(end.y - start.y)
}

/**
 * Clone a route and remove points that cannot affect an orthogonal path.
 * Endpoints are always retained. `minGap` is accepted here as part of the
 * shared editor contract; short turns are intentionally preserved rather
 * than collapsed into a diagonal segment.
 */
export function normalizeOrthogonalPoints(points: readonly EdgePoint[], minGap = DEFAULT_MIN_GAP): EdgePoint[] {
  const gap = Math.max(0, finiteNumber(minGap, DEFAULT_MIN_GAP))
  void gap
  const result: EdgePoint[] = []
  for (const point of clonePoints(points)) {
    if (result.length && samePoint(result[result.length - 1], point)) continue
    result.push(point)
  }

  let changed = true
  while (changed && result.length > 2) {
    changed = false
    for (let index = 1; index < result.length - 1; index += 1) {
      const previous = result[index - 1]
      const current = result[index]
      const next = result[index + 1]
      if (areCollinear(previous, current, next)) {
        result.splice(index, 1)
        changed = true
        break
      }
    }
  }
  return result
}

/**
 * Build the compact route users should edit. X6's Manhattan router can expose
 * several tiny rounding points that are useful for rendering but painful to
 * drag; this helper keeps only the supplied endpoints/vertices and adds a
 * shallow dogleg for a direct edge so a segment always has a stable handle.
 */
export function ensureEditableOrthogonalRoute(points: readonly EdgePoint[], dogleg = 32, addDogleg = true): EdgePoint[] {
  const source = clonePoints(points)
  if (source.length < 2) return source
  const orthogonal: EdgePoint[] = [source[0]!]
  for (const point of source.slice(1)) {
    const previous = orthogonal[orthogonal.length - 1]!
    const current = clonePoint(point)
    if (previous.x !== current.x && previous.y !== current.y) {
      if (Math.abs(current.x - previous.x) >= Math.abs(current.y - previous.y)) orthogonal.push({ x: current.x, y: previous.y })
      else orthogonal.push({ x: previous.x, y: current.y })
    }
    orthogonal.push(current)
  }
  let route = normalizeOrthogonalPoints(orthogonal, 0)
  if (route.length === 2 && addDogleg) {
    const start = route[0]!
    const end = route[1]!
    const offset = Math.max(16, Math.abs(finiteNumber(dogleg, 32)))
    if (start.y === end.y) {
      const y = start.y + offset
      route = [start, { x: start.x, y }, { x: end.x, y }, end]
    } else if (start.x === end.x) {
      const x = start.x + offset
      route = [start, { x, y: start.y }, { x, y: end.y }, end]
    }
  }
  return route
}

/** Describe usable route segments without mutating or normalizing the input. */
export function describeOrthogonalSegments(points: readonly EdgePoint[], minLength = DEFAULT_MIN_LENGTH): OrthogonalSegment[] {
  const route = clonePoints(points)
  const threshold = Math.max(0, finiteNumber(minLength, DEFAULT_MIN_LENGTH))
  const segments: OrthogonalSegment[] = []
  for (let index = 0; index < route.length - 1; index += 1) {
    const start = route[index]
    const end = route[index + 1]
    const axis = segmentAxis(start, end)
    const length = segmentLength(start, end, axis)
    if (length < threshold) continue
    segments.push({
      index,
      start: { ...start },
      end: { ...end },
      axis,
      midpoint: { x: (start.x + end.x) / 2, y: (start.y + end.y) / 2 },
      length,
    })
  }
  return segments
}

function movementAmount(delta: EdgePoint | number, axis: 'x' | 'y'): number {
  if (typeof delta === 'number') return finiteNumber(delta)
  // A segment moves on its perpendicular axis: horizontal => y, vertical => x.
  return finiteNumber(axis === 'x' ? delta?.y : delta?.x, 0)
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.max(minimum, Math.min(maximum, value))
}

function movedCoordinateBounds(axis: 'x' | 'y', bounds?: OrthogonalMoveBounds): [number, number] {
  const minimum = axis === 'x' ? finiteNumber(bounds?.minY, Number.NEGATIVE_INFINITY) : finiteNumber(bounds?.minX, Number.NEGATIVE_INFINITY)
  const maximum = axis === 'x' ? finiteNumber(bounds?.maxY, Number.POSITIVE_INFINITY) : finiteNumber(bounds?.maxX, Number.POSITIVE_INFINITY)
  return minimum <= maximum ? [minimum, maximum] : [maximum, minimum]
}

function applyNeighbourGap(target: number, base: number, neighbours: number[], gap: number): number {
  let result = target
  for (const neighbour of neighbours) {
    if (Math.abs(result - neighbour) >= gap) continue
    // Prefer the side indicated by the drag direction. If there was no drag,
    // keep the segment on the side it occupied before the drag.
    const direction = result > base || (result === base && base >= neighbour) ? 1 : -1
    result = neighbour + direction * gap
  }
  return result
}

/** Move one segment along its perpendicular axis, returning a fresh route. */
export function moveOrthogonalSegment(
  points: readonly EdgePoint[],
  segmentIndex: number,
  delta: EdgePoint | number,
  options: OrthogonalMoveOptions = {},
): EdgePoint[] {
  const route = clonePoints(points)
  const index = Math.trunc(segmentIndex)
  if (index < 0 || index >= route.length - 1) return route

  const start = route[index]
  const end = route[index + 1]
  const axis = segmentAxis(start, end)
  const base = axis === 'x' ? start.y : start.x
  const amount = movementAmount(delta, axis)
  const rawGrid = options.gridSize ?? options.grid
  const gridSize = rawGrid && rawGrid > 0 ? rawGrid : 0
  let target = base + amount
  if (gridSize) target = Math.round(target / gridSize) * gridSize

  const gap = Math.max(0, finiteNumber(options.minGap, DEFAULT_MIN_GAP))
  const neighbours: number[] = []
  if (index > 0) neighbours.push(axis === 'x' ? route[index - 1].y : route[index - 1].x)
  if (index + 2 < route.length) neighbours.push(axis === 'x' ? route[index + 2].y : route[index + 2].x)
  target = applyNeighbourGap(target, base, neighbours, gap)
  const [minimum, maximum] = movedCoordinateBounds(axis, options.bounds)
  target = clamp(target, minimum, maximum)

  const setCoordinate = (point: EdgePoint) => {
    if (axis === 'x') point.y = target
    else point.x = target
  }
  // A direct edge has no interior point to move. Build a parallel dogleg so
  // the source and target remain fixed while the dragged segment stays axis
  // aligned.
  if (route.length === 2) {
    const source = route[0]!
    const end = route[1]!
    route.splice(1, 0, axis === 'x'
      ? { x: source.x, y: target }
      : { x: target, y: source.y }, axis === 'x'
      ? { x: end.x, y: target }
      : { x: target, y: end.y })
    return route
  }

  // Keep source/target anchors fixed. Moving the first/last segment therefore
  // needs a small dogleg at the anchor instead of turning the segment diagonal.
  if (index === 0) {
    setCoordinate(route[1]!)
    const source = route[0]!
    const bridge = axis === 'x' ? { x: source.x, y: target } : { x: target, y: source.y }
    if (!samePoint(bridge, source) && !samePoint(bridge, route[1]!)) route.splice(1, 0, bridge)
  } else if (index === route.length - 2) {
    setCoordinate(route[index]!)
    const end = route[route.length - 1]!
    const bridge = axis === 'x' ? { x: end.x, y: target } : { x: target, y: end.y }
    if (!samePoint(bridge, end) && !samePoint(bridge, route[index]!)) route.splice(route.length - 1, 0, bridge)
  } else {
    // Interior points on the selected segment move together, preserving both
    // neighbouring turns and keeping the path orthogonal.
    setCoordinate(route[index]!)
    setCoordinate(route[index + 1]!)
  }
  return route
}

/** Move a bend while preserving the orientation of both adjacent legs. */
export function moveOrthogonalVertex(
  points: readonly EdgePoint[],
  vertexIndex: number,
  delta: EdgePoint,
  options: OrthogonalMoveOptions = {},
): EdgePoint[] {
  const route = clonePoints(points)
  const index = Math.trunc(vertexIndex)
  if (index <= 0 || index >= route.length - 1) return route
  const current = route[index]!
  const previous = route[index - 1]!
  const next = route[index + 1]!
  const gridSize = options.gridSize ?? options.grid
  const snap = (value: number) => gridSize && gridSize > 0 ? Math.round(value / gridSize) * gridSize : value
  let x = snap(current.x + finiteNumber(delta?.x, 0))
  let y = snap(current.y + finiteNumber(delta?.y, 0))
  const xBounds = movedCoordinateBounds('y', options.bounds)
  const yBounds = movedCoordinateBounds('x', options.bounds)
  x = clamp(x, xBounds[0], xBounds[1]); y = clamp(y, yBounds[0], yBounds[1])

  const previousHorizontal = previous.y === current.y
  const nextHorizontal = current.y === next.y
  current.x = x; current.y = y
  if (previousHorizontal) previous.y = y
  else previous.x = x
  if (nextHorizontal) next.y = y
  else next.x = x

  // At the first/last bend the endpoint must stay fixed. Add a bridge when
  // moving the bend changes the endpoint-facing axis.
  if (index === 1) {
    const endpoint = route[0]!
    const bridge = previousHorizontal ? { x: endpoint.x, y } : { x, y: endpoint.y }
    if (!samePoint(bridge, endpoint) && !samePoint(bridge, route[1]!)) route.splice(1, 0, bridge)
  } else if (index === route.length - 2) {
    const endpoint = route[route.length - 1]!
    const bridge = nextHorizontal ? { x: endpoint.x, y } : { x, y: endpoint.y }
    if (!samePoint(bridge, endpoint) && !samePoint(bridge, route[route.length - 2]!)) route.splice(route.length - 1, 0, bridge)
  }
  return route
}

/**
 * Insert a visible, editable dogleg at a clicked segment location. The click
 * is projected onto the segment, then a parallel return leg is offset by the
 * minimum gap. Endpoints remain untouched and short segments are left alone
 * when there is not enough room for four valid legs.
 */
export function insertOrthogonalVertex(
  points: readonly EdgePoint[],
  segmentIndex: number,
  point: EdgePoint,
  options: OrthogonalInsertOptions = {},
): EdgePoint[] {
  const route = clonePoints(points)
  const index = Math.trunc(segmentIndex)
  if (index < 0 || index >= route.length - 1) return route
  const start = route[index]
  const end = route[index + 1]
  const axis = segmentAxis(start, end)
  const gap = Math.max(1, finiteNumber(options.minGap, DEFAULT_MIN_GAP))
  const length = segmentLength(start, end, axis)
  if (length < gap * 2) return route
  const candidate = clonePoint(point, start)
  const clicked: EdgePoint = axis === 'x'
    ? { x: clamp(candidate.x, Math.min(start.x, end.x) + gap, Math.max(start.x, end.x) - gap), y: start.y }
    : { x: start.x, y: clamp(candidate.y, Math.min(start.y, end.y) + gap, Math.max(start.y, end.y) - gap) }
  const offset = axis === 'x' ? { x: clicked.x, y: start.y + gap } : { x: start.x + gap, y: clicked.y }
  const returnPoint = axis === 'x' ? { x: end.x, y: offset.y } : { x: offset.x, y: end.y }
  route.splice(index + 1, 0, clicked, offset, returnPoint)
  return route
}

/** Remove an interior point and normalize the resulting route. */
export function removeOrthogonalVertex(points: readonly EdgePoint[], vertexIndex: number, minGap = DEFAULT_MIN_GAP): EdgePoint[] {
  const route = clonePoints(points)
  const index = Math.trunc(vertexIndex)
  if (index <= 0 || index >= route.length - 1) return route
  route.splice(index, 1)
  return normalizeOrthogonalPoints(route, minGap)
}

function cloneValue<T>(value: T): T {
  if (Array.isArray(value)) return value.map(item => cloneValue(item)) as T
  if (value && typeof value === 'object') {
    const result: Record<string, unknown> = {}
    for (const [key, item] of Object.entries(value as Record<string, unknown>)) result[key] = cloneValue(item)
    return result as T
  }
  return value
}

/** Capture mutable edge state before an interactive drag starts. */
export function createEdgeDragSnapshot<TRouter = unknown>(vertices: readonly EdgePoint[], router: TRouter): EdgeDragSnapshot<TRouter> {
  return { vertices: clonePoints(vertices), router: cloneValue(router) }
}

/** Restore a drag snapshot as fresh mutable values suitable for X6 setters. */
export function restoreEdgeDragSnapshot<TRouter>(snapshot: EdgeDragSnapshot<TRouter>): EdgeDragSnapshot<TRouter> {
  return { vertices: clonePoints(snapshot.vertices), router: cloneValue(snapshot.router) }
}
