import { App } from 'antd'
import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { formatProblem, isStale, toApiError, type ApiError } from '../api/problem.ts'
import type { Plant } from '../api/types.ts'
import { reloadMe, switchPlant } from '../auth/store.ts'
import { useAuth } from '../auth/useAuth.ts'
import { PlantContext, type PlantContextValue } from './plant-context.ts'

function toPlant(option: { id: string; code: string | null; name: string | null }): Plant {
  return {
    id: option.id,
    code: option.code ?? '',
    name: option.name ?? '',
    timezone: 'Asia/Shanghai',
    enabled: true,
    lockVersion: 0,
  }
}

export function PlantProvider({ children }: { children: ReactNode }) {
  const { message } = App.useApp()
  const auth = useAuth()
  const [error, setError] = useState<ApiError | null>(null)
  const [switching, setSwitching] = useState(false)
  const plants = useMemo(() => (auth.me?.plants ?? []).map(toPlant), [auth.me])

  const selectPlant = useCallback((id: string) => {
    setSwitching(true)
    setError(null)
    void switchPlant(id)
      .catch((err: unknown) => {
        if (isStale(err)) return
        const api = toApiError(err)
        setError(api)
        message.error(formatProblem(api.problem))
      })
      .finally(() => setSwitching(false))
  }, [message])

  const reloadPlants = useCallback(() => {
    setError(null)
    void reloadMe().catch((err: unknown) => {
      if (!isStale(err)) setError(toApiError(err))
    })
  }, [])

  const value = useMemo<PlantContextValue>(() => ({
    plants,
    plantsTotal: plants.length,
    plantsTruncated: false,
    loading: switching,
    error,
    selectPlant,
    reloadPlants,
  }), [plants, switching, error, selectPlant, reloadPlants])

  return <PlantContext.Provider value={value}>{children}</PlantContext.Provider>
}
