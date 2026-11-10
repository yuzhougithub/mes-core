import { createContext } from 'react'
import { BRAND_DEFAULTS, type BrandConfig } from './config.ts'

export const BrandContext = createContext<BrandConfig>(BRAND_DEFAULTS)
