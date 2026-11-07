export type BrandConfig = {
  logo: string
  name: string
  subtitle: string
}

export const BRAND_DEFAULTS: BrandConfig = {
  logo: '/favicon.svg',
  name: 'MES 制造执行系统',
  subtitle: '',
}

const NAME_MAX = 80
const SUBTITLE_MAX = 80

export type BrandEnv = {
  logo?: string
  name?: string
  subtitle?: string
}

/** Plain text only. Callers must render with React text nodes, never as HTML. */
function plainText(value: unknown, max: number): string | undefined {
  if (typeof value !== 'string') return undefined
  const text = value.replace(/[\u0000-\u001F\u007F]/g, '').trim()
  if (!text) return undefined
  return Array.from(text).slice(0, max).join('')
}

function optionalText(value: unknown, max: number): string | undefined {
  if (value == null) return undefined
  if (typeof value !== 'string') return undefined
  const text = value.replace(/[\u0000-\u001F\u007F]/g, '').trim()
  return Array.from(text).slice(0, max).join('')
}

/**
 * Accept a site-relative path or http(s) URL. Reject scripts and scheme-relative URLs.
 */
export function sanitizeLogo(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined
  const logo = value.trim()
  if (!logo || logo.length > 500 || /[\s<>"']/.test(logo)) return undefined
  if (logo.startsWith('/') && !logo.startsWith('//')) return logo
  try {
    const url = new URL(logo)
    if (url.protocol === 'http:' || url.protocol === 'https:') return url.toString()
  } catch {
    return undefined
  }
  return undefined
}

function readField(source: Record<string, unknown> | undefined, key: string): unknown {
  if (!source || !Object.prototype.hasOwnProperty.call(source, key)) return undefined
  return source[key]
}

/**
 * Per field: config.json, then env, then built-in default.
 * An explicit empty subtitle stays empty. A missing subtitle falls through.
 * Name and logo must be usable; blank or unsafe values fall through.
 */
export function resolveBrand(config: unknown, env: BrandEnv = {}): BrandConfig {
  const record = config && typeof config === 'object' ? (config as Record<string, unknown>) : undefined
  const name = plainText(readField(record, 'name'), NAME_MAX)
    ?? plainText(env.name, NAME_MAX)
    ?? BRAND_DEFAULTS.name
  const logo = sanitizeLogo(readField(record, 'logo'))
    ?? sanitizeLogo(env.logo)
    ?? BRAND_DEFAULTS.logo
  const subtitleFromFile = optionalText(readField(record, 'subtitle'), SUBTITLE_MAX)
  const subtitle = subtitleFromFile !== undefined
    ? subtitleFromFile
    : (optionalText(env.subtitle, SUBTITLE_MAX) ?? BRAND_DEFAULTS.subtitle)
  return { name, logo, subtitle }
}

export async function loadBrandConfig(
  fetchImpl: typeof fetch = fetch,
  env: BrandEnv = {},
): Promise<BrandConfig> {
  try {
    const response = await fetchImpl('/config.json', { cache: 'no-store' })
    if (!response.ok) return resolveBrand(undefined, env)
    const body: unknown = await response.json()
    return resolveBrand(body, env)
  } catch {
    return resolveBrand(undefined, env)
  }
}
