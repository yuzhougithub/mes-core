import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { listPlants } from '../api/resources.ts'
import { toApiError, type ApiError } from '../api/problem.ts'
import { setActivePlant } from '../api/plant-session.ts'
import type { Plant } from '../api/types.ts'
import { PlantContext, type PlantContextValue } from './plant-context.ts'

const STORAGE_KEY = 'mes.plantId'

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

export function PlantProvider({ children }: { children: ReactNode }) {
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
    let cancelled = false
    listPlants()
      .then((page) => {
        if (cancelled) return
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
        if (cancelled) return
        setError(toApiError(err))
        setPlants([])
        setPlantsTotal(0)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

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
