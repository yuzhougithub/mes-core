import { useContext } from 'react'
import type { BrandConfig } from './config.ts'
import { BrandContext } from './brand-context.ts'

export function useBrand(): BrandConfig {
  return useContext(BrandContext)
}
