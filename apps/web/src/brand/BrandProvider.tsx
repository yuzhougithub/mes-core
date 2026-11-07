import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { BRAND_DEFAULTS, loadBrandConfig, type BrandConfig } from './config.ts'

const BrandContext = createContext<BrandConfig>(BRAND_DEFAULTS)

function envBrand(): { logo?: string; name?: string; subtitle?: string } {
  return {
    logo: import.meta.env.VITE_APP_LOGO,
    name: import.meta.env.VITE_APP_NAME,
    subtitle: import.meta.env.VITE_APP_SUBTITLE,
  }
}

export function BrandProvider({ children }: { children: ReactNode }) {
  const [brand, setBrand] = useState<BrandConfig>(() => BRAND_DEFAULTS)

  useEffect(() => {
    let cancelled = false
    loadBrandConfig(fetch, envBrand()).then((next) => {
      if (!cancelled) setBrand(next)
    })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    document.title = brand.name
  }, [brand.name])

  return <BrandContext.Provider value={brand}>{children}</BrandContext.Provider>
}

export function useBrand(): BrandConfig {
  return useContext(BrandContext)
}
