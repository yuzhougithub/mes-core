import { describe, expect, it, vi } from 'vitest'
import { BRAND_DEFAULTS, loadBrandConfig, resolveBrand, sanitizeLogo } from './config.ts'

describe('resolveBrand', () => {
  it('uses built-in defaults when config and env are empty', () => {
    expect(resolveBrand(undefined, {})).toEqual(BRAND_DEFAULTS)
    expect(BRAND_DEFAULTS.name).toBe('MES 制造执行系统')
    expect(BRAND_DEFAULTS.subtitle).toBe('')
  })

  it('prefers config.json fields, then env, and keeps an explicit empty subtitle', () => {
    expect(resolveBrand(
      { name: '华东精工 MES', logo: '/brand/logo.svg', subtitle: '' },
      { name: '环境名', logo: '/env.svg', subtitle: '环境副标题' },
    )).toEqual({
      name: '华东精工 MES',
      logo: '/brand/logo.svg',
      subtitle: '',
    })
    expect(resolveBrand(
      { logo: '/brand/logo.svg' },
      { name: '环境名', subtitle: '环境副标题' },
    )).toEqual({
      name: '环境名',
      logo: '/brand/logo.svg',
      subtitle: '环境副标题',
    })
  })

  it('treats markup as text and rejects scriptable logo urls', () => {
    const brand = resolveBrand({
      name: '<img src=x onerror=alert(1)>',
      logo: 'javascript:alert(1)',
      subtitle: '<b>车间</b>',
    })
    expect(brand.name).toBe('<img src=x onerror=alert(1)>')
    expect(brand.subtitle).toBe('<b>车间</b>')
    expect(brand.logo).toBe(BRAND_DEFAULTS.logo)
    expect(sanitizeLogo('javascript:alert(1)')).toBeUndefined()
    expect(sanitizeLogo('//evil.example/logo.svg')).toBeUndefined()
    expect(sanitizeLogo('https://cdn.example/logo.svg')).toBe('https://cdn.example/logo.svg')
  })
})

describe('loadBrandConfig', () => {
  it('falls back when config.json is missing or invalid', async () => {
    const missing = vi.fn(async () => new Response('nope', { status: 404 }))
    await expect(loadBrandConfig(missing as unknown as typeof fetch, {})).resolves.toEqual(BRAND_DEFAULTS)

    const broken = vi.fn(async () => {
      throw new Error('network')
    })
    await expect(loadBrandConfig(broken as unknown as typeof fetch, {
      name: '环境名',
    })).resolves.toMatchObject({ name: '环境名', logo: BRAND_DEFAULTS.logo })
  })
})
