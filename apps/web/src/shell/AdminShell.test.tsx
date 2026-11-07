import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../App.tsx'
import { resetPlantSession } from '../api/plant-session.ts'

const PLANT_A = '00000000-0000-4000-8000-00000000000a'
const PLANT_B = '00000000-0000-4000-8000-00000000000b'
const UOM = '00000000-0000-4000-8000-0000000000c1'

afterEach(() => {
  cleanup()
  resetPlantSession()
  sessionStorage.clear()
  vi.unstubAllGlobals()
})

function plant(id: string, code: string, name: string) {
  return {
    id,
    code,
    name,
    timezone: 'Asia/Shanghai',
    enabled: true,
    lockVersion: 0,
  }
}

function material(code: string) {
  return {
    id: code === 'AAA-CODE' ? '00000000-0000-4000-8000-0000000000a1' : '00000000-0000-4000-8000-0000000000b1',
    scope: 'PLANT',
    plantId: code === 'AAA-CODE' ? PLANT_A : PLANT_B,
    code,
    name: code === 'AAA-CODE' ? '一厂外壳' : '二厂外壳',
    spec: '6061',
    materialType: 'SEMI',
    uomId: UOM,
    status: 'DRAFT',
    lockVersion: 0,
    plantExt: null,
    updatedAt: '2026-11-07T01:02:03Z',
  }
}

function page(items: unknown[]) {
  return { items, page: 1, size: 20, total: items.length }
}

describe('admin shell and material list', () => {
  it('shows the shell, disables unsupported actions, and queries only supported filters', async () => {
    const user = userEvent.setup()
    const calls: { url: string; plant: string | null }[] = []
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const plantHeader = new Headers(init?.headers).get('X-Plant-Id')
      calls.push({ url, plant: plantHeader })
      if (url.startsWith('/api/v1/plants')) {
        return Response.json(page([plant(PLANT_A, 'P1', '一厂'), plant(PLANT_B, 'P2', '二厂')]))
      }
      if (url.startsWith('/api/v1/uoms')) {
        return Response.json(page([{ id: UOM, code: 'PCS', name: '件', decimals: 0, enabled: true, lockVersion: 0 }]))
      }
      if (url.startsWith('/api/v1/materials')) {
        return Response.json(page([material('AAA-CODE')]))
      }
      return Response.json({ title: 'missing', status: 404, detail: url, code: 'NOT_FOUND', errors: [] }, { status: 404 })
    }))

    render(<App />)
    expect(await screen.findByText('AAA-CODE')).toBeTruthy()
    expect(screen.getByText('MES 制造执行系统')).toBeTruthy()
    expect(screen.getByRole('tab', { name: '物料管理' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: '工作台' })).toBeTruthy()
    expect(screen.getByText('物料管理 / 物料主数据')).toBeTruthy()
    expect(screen.getByText('共 1 条')).toBeTruthy()
    expect(screen.getByText('件（PCS）')).toBeTruthy()
    expect((screen.getByTestId('btn-import') as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByTestId('btn-export') as HTMLButtonElement).disabled).toBe(true)
    expect(document.querySelector('[data-testid="filter-category"] .ant-select-disabled')).toBeTruthy()
    expect(document.querySelector('[data-testid="filter-created-range"] .ant-picker-disabled')).toBeTruthy()
    expect(calls.some((call) => call.url.startsWith('/api/v1/plants') && call.plant === null)).toBe(true)
    expect(calls.some((call) => call.url.startsWith('/api/v1/materials') && call.plant === PLANT_A)).toBe(true)

    await user.type(screen.getByRole('textbox', { name: '关键词' }), '外壳')
    await user.click(screen.getByRole('button', { name: /查询/ }))
    await waitFor(() => {
      expect(calls.some((call) => call.url.includes('q=') && call.url.includes('page=1') && call.plant === PLANT_A)).toBe(true)
    })
    expect(calls.every((call) => !call.url.includes('categoryId') && !call.url.includes('createdFrom'))).toBe(true)
  })

  it('ignores a late response from the previous factory', async () => {
    const user = userEvent.setup()
    const lateA: { resolve: ((response: Response) => void) | null } = { resolve: null }
    vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const plantHeader = new Headers(init?.headers).get('X-Plant-Id')
      if (url.startsWith('/api/v1/plants')) {
        return Promise.resolve(Response.json(page([plant(PLANT_A, 'P1', '一厂'), plant(PLANT_B, 'P2', '二厂')])))
      }
      if (url.startsWith('/api/v1/uoms')) {
        return Promise.resolve(Response.json(page([])))
      }
      if (url.startsWith('/api/v1/materials') && plantHeader === PLANT_A) {
        return new Promise<Response>((resolve) => {
          lateA.resolve = resolve
        })
      }
      if (url.startsWith('/api/v1/materials') && plantHeader === PLANT_B) {
        return Promise.resolve(Response.json(page([material('BBB-CODE')])))
      }
      return Promise.resolve(Response.json({}))
    }))

    render(<App />)
    expect(await screen.findByRole('combobox', { name: '当前工厂' })).toBeTruthy()
    await user.click(screen.getByRole('combobox', { name: '当前工厂' }))
    await user.click(await screen.findByText('二厂（P2）'))
    expect(await screen.findByText('BBB-CODE')).toBeTruthy()
    lateA.resolve?.(Response.json(page([material('AAA-CODE')])))
    await new Promise((resolve) => setTimeout(resolve, 30))
    expect(screen.queryByText('AAA-CODE')).toBeNull()
    expect(screen.getByText('BBB-CODE')).toBeTruthy()
  })
})
