import zhCNImport from 'antd/locale/zh_CN'
import type { Locale } from 'antd/es/locale'

type MaybeWrapped = Locale | { default: Locale }

/**
 * Vite's CJS interop sometimes exposes this locale as `{ default: locale }`.
 * ConfigProvider then misses `Pagination` and falls back to English.
 */
export function unwrapLocale(value: MaybeWrapped): Locale {
  const record = value as Locale & { default?: Locale }
  if (record.default && typeof record.locale !== 'string') return record.default
  return value as Locale
}

export const zhCN = unwrapLocale(zhCNImport as MaybeWrapped)
