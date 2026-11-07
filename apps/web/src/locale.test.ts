import { describe, expect, it } from 'vitest'
import type { Locale } from 'antd/es/locale'
import { unwrapLocale, zhCN } from './locale.ts'

describe('unwrapLocale', () => {
  it('unwraps a Vite CJS double default and keeps a real locale', () => {
    const wrapped = { default: zhCN }
    expect(unwrapLocale(wrapped).locale).toBe('zh-cn')
    expect(unwrapLocale(wrapped).Pagination?.items_per_page).toBe('条/页')
    expect(unwrapLocale(zhCN as Locale).Pagination?.items_per_page).toBe('条/页')
  })
})
