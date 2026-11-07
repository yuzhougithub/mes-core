import { createContext, useCallback, useContext, useEffect, useMemo, useState, useSyncExternalStore } from 'react'
import { listPlants } from '../api/resources.ts'
import { ApiError, toApiError } from '../api/problem.ts'
import {
  getPlantSnapshot,
  setActivePlant,
  subscribePlantSession,
} from '../api/plant-session.ts'
import type { Plant } from '../api/types.ts'

const STORAGE_KEY = 'mes.plantId'

type PlantContextValue = {
  plants: Plant[]
  plantsTotal: number
  plantsTruncated: boolean
  loading: boolean
  error: ApiError | null
  selectPlant: (id: string) => void
  reloadPlants: () => void
}

const PlantContext = createContext<PlantContextValue | null>(null)

function readSavedPlant(): string | null {
  try {
    return sessionStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

function writeSavedPlant(id: string) {
  try {
    sessionStorage.setItem(STORAGE_KEY, id)
  } catch {
    /* private mode */
  }
}

export function PlantProvider({ children }: { children: React.ReactNode }) {
  const [plants, setPlants] = useState<Plant[]>([])
  const [plantsTotal, setPlantsTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError | null>(null)

  const reloadPlants = useCallback(() => {
    setLoading(true)
    setError(null)
    listPlants()
      .then((page) => {
        setPlants(page.items)
        setPlantsTotal(page.total)
        const saved = readSavedPlant()
        const pick = page.items.find((plant) => plant.id === saved)?.id ?? page.items[0]?.id ?? null
        if (pick) {
          writeSavedPlant(pick)
          setActivePlant(pick)
        } else {
          setActivePlant(null)
        }
      })
      .catch((err: unknown) => {
        setError(toApiError(err))
        setPlants([])
        setPlantsTotal(0)
      })
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    reloadPlants()
  }, [reloadPlants])

  const selectPlant = useCallback((id: string) => {
    writeSavedPlant(id)
    setActivePlant(id)
  }, [])

  const value = useMemo<PlantContextValue>(() => ({
    plants,
    plantsTotal,
    plantsTruncated: plantsTotal > plants.length,
    loading,
    error,
    selectPlant,
    reloadPlants,
  }), [plants, plantsTotal, loading, error, selectPlant, reloadPlants])

  return <PlantContext.Provider value={value}>{children}</PlantContext.Provider>
}

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
