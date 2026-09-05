const SCROLLABLE_OVERFLOW = /^(?:auto|scroll|overlay)$/

function scrollContainers(target: EventTarget | null, documentRoot: Document) {
  const containers: HTMLElement[] = []
  let element = target instanceof Element ? target : null

  while (element) {
    if (element instanceof HTMLElement) {
      const overflow = window.getComputedStyle(element).overflowY
      if (SCROLLABLE_OVERFLOW.test(overflow)) containers.push(element)
    }
    element = element.parentElement
  }

  const page = documentRoot.scrollingElement
  if (page instanceof HTMLElement && !containers.includes(page)) containers.push(page)
  return containers
}

export type VerticalScrollMetrics = Pick<HTMLElement, 'clientHeight' | 'scrollHeight' | 'scrollTop'>

export function canScrollVertically(element: VerticalScrollMetrics, delta: number) {
  if (element.scrollHeight <= element.clientHeight + 1) return false
  if (delta < 0) return element.scrollTop > 0.5
  return element.scrollTop + element.clientHeight < element.scrollHeight - 0.5
}

export function findScrollDestinationIndex(containers: VerticalScrollMetrics[], delta: number) {
  return containers.findIndex((element) => canScrollVertically(element, delta))
}

function pixelDelta(event: WheelEvent, target: HTMLElement) {
  if (event.deltaMode === WheelEvent.DOM_DELTA_LINE) return event.deltaY * 16
  if (event.deltaMode === WheelEvent.DOM_DELTA_PAGE) return event.deltaY * Math.max(target.clientHeight, 1)
  return event.deltaY
}

/**
 * Keeps nested wheel scrolling predictable: the nearest region scrolls first,
 * then a wheel gesture at its boundary is handed to the next scrollable parent.
 */
export function installWheelScrollChaining(documentRoot: Document = document) {
  const onWheel = (event: WheelEvent) => {
    if (event.defaultPrevented || event.ctrlKey || Math.abs(event.deltaY) < 0.01) return
    const containers = scrollContainers(event.target, documentRoot)
    const nearest = containers[0]
    const destinationIndex = findScrollDestinationIndex(containers, event.deltaY)
    const destination = containers[destinationIndex]
    if (!nearest || !destination || destination === nearest) return

    event.preventDefault()
    destination.scrollBy({ top: pixelDelta(event, destination), behavior: 'auto' })
  }

  documentRoot.addEventListener('wheel', onWheel, { capture: true, passive: false })
  return () => documentRoot.removeEventListener('wheel', onWheel, { capture: true })
}
