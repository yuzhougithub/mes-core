import { useContext, useSyncExternalStore } from 'react'
import { getPlantSnapshot, subscribePlantSession } from '../api/plant-session.ts'
import { PlantContext } from './plant-context.ts'

export function usePlant() {
  const context = useContext(PlantContext)
  const session = useSyncExternalStore(subscribePlantSession, getPlantSnapshot, getPlantSnapshot)
  if (!context) {
    throw new Error('usePlant 必须在 PlantProvider 内使用')
  }
  return {
    ...context,
    plantId: session.plantId,
    epoch: session.epoch,
  }
}
