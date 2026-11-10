import { createContext } from 'react'
import type { ApiError } from '../api/problem.ts'
import type { Plant } from '../api/types.ts'

export type PlantContextValue = {
  plants: Plant[]
  plantsTotal: number
  plantsTruncated: boolean
  loading: boolean
  error: ApiError | null
  selectPlant: (id: string) => void
  reloadPlants: () => void
}

export const PlantContext = createContext<PlantContextValue | null>(null)
