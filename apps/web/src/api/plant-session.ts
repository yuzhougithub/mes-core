/**
 * Current factory for the whole tab. Switching aborts in-flight plant-scoped
 * requests so a slow response cannot be painted onto another factory.
 */

export type PlantSnapshot = {
  plantId: string | null
  epoch: number
}

const listeners = new Set<() => void>()
const inflight = new Set<AbortController>()

let plantId: string | null = null
let epoch = 0
let snapshot: PlantSnapshot = { plantId, epoch }

export function getPlantId(): string | null {
  return plantId
}

export function getPlantEpoch(): number {
  return epoch
}

export function getPlantSnapshot(): PlantSnapshot {
  return snapshot
}

export function subscribePlantSession(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function setActivePlant(next: string | null): void {
  if (next === plantId) return
  plantId = next
  epoch += 1
  for (const controller of inflight) controller.abort()
  inflight.clear()
  snapshot = { plantId, epoch }
  for (const listener of listeners) listener()
}

/** Test-only reset. */
export function resetPlantSession(): void {
  plantId = null
  epoch = 0
  for (const controller of inflight) controller.abort()
  inflight.clear()
  snapshot = { plantId, epoch }
  for (const listener of listeners) listener()
}

export function beginPlantRequest(epochAtStart: number): AbortController {
  const controller = new AbortController()
  if (epochAtStart !== epoch) {
    controller.abort()
    return controller
  }
  inflight.add(controller)
  return controller
}

export function endPlantRequest(controller: AbortController): void {
  inflight.delete(controller)
}
