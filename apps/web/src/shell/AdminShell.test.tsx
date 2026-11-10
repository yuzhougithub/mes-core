import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../App.tsx'
import { resetPlantSession } from '../api/plant-session.ts'
import { resetAuthSession, seedAuthSession } from '../auth/store.ts'
import type { AuthSnapshot, MeMenu, MeResponse } from '../auth/types.ts'

const PLANT_A = '00000000-0000-4000-8000-00000000000a'
const PLANT_B = '00000000-0000-4000-8000-00000000000b'
const UOM = '00000000-0000-4000-8000-0000000000c1'

const USER = '00000000-0000-4000-8000-0000000000aa'

afterEach(() => {
  cleanup()
  resetAuthSession()
  resetPlantSession()
  sessionStorage.clear()
  vi.unstubAllGlobals()
})

function leaf(id: string, parentId: string, name: string, component: string, path: string): MeMenu {
  return {
    id,
    parentId,
    type: 'MENU',
    name,
    permCode: null,
    path,
    component,
    icon: null,
    sortNo: 0,
    visible: true,
    children: [],
  }
}

const SHELL_MENUS: MeMenu[] = [
  {
    id: 'mod-material',
    parentId: null,
    type: 'DIR',
    name: '物料管理',
    permCode: null,
    path: null,
    component: null,
    icon: null,
    sortNo: 10,
    visible: true,
    children: [leaf('page-material', 'mod-material', '物料主数据', 'md/material', '/materials')],
  },
  {
    id: 'mod-work',
    parentId: null,
    type: 'DIR',
    name: '工作台',
    permCode: null,
    path: null,
    component: null,
    icon: null,
    sortNo: 20,
    visible: true,
    children: [leaf('page-overview', 'mod-work', '概览', 'workbench/overview', '/overview')],
  },
]

function sessionMe(plantId: string): MeResponse {
  return {
    user: {
      id: USER,
      username: 'admin',
      status: 'ACTIVE',
      superAdmin: true,
      mustChangePassword: false,
      permVersion: 1,
    },
    person: { id: '00000000-0000-4000-8000-0000000000bb', code: 'A001', name: '管理员' },
    plant: {
      id: plantId,
      code: plantId === PLANT_B ? 'P2' : 'P1',
      name: plantId === PLANT_B ? '二厂' : '一厂',
    },
    plants: [
      { id: PLANT_A, code: 'P1', name: '一厂', isDefault: plantId !== PLANT_B },
      { id: PLANT_B, code: 'P2', name: '二厂', isDefault: plantId === PLANT_B },
    ],
    roles: ['ADMIN'],
    permissions: [
      'md:material:list',
      'md:material:view',
      'md:material:create',
      'md:material:update',
      'md:material:delete',
    ],
    menus: SHELL_MENUS,
  }
}

function seedShell() {
  const session: AuthSnapshot = {
    phase: 'ready',
    token: 'token-a',
    notice: null,
    me: sessionMe(PLANT_A),
  }
  seedAuthSession(session)
}

function switchBody(init?: RequestInit) {
  const raw = typeof init?.body === 'string' ? init.body : '{}'
  const parsed = JSON.parse(raw) as { plantId?: string }
  const id = parsed.plantId ?? PLANT_A
  return {
    accessToken: `token-${id}`,
    expiresIn: 1800,
    mustChangePassword: false,
    plant: {
      id,
      code: id === PLANT_B ? 'P2' : 'P1',
      name: id === PLANT_B ? '二厂' : '一厂',
    },
    roles: ['ADMIN'],
  }
}

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
    seedShell()
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
    expect(screen.getByText('共 1 条记录，已选 0 项')).toBeTruthy()
    expect(screen.getByText('件（PCS）')).toBeTruthy()
    expect((screen.getByTestId('btn-import') as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByTestId('btn-export') as HTMLButtonElement).disabled).toBe(true)
    expect(document.querySelector('[data-testid="filter-category"] .ant-select-disabled')).toBeTruthy()
    expect(document.querySelector('[data-testid="filter-created-range"] .ant-picker-disabled')).toBeTruthy()
    expect(screen.getByText('一厂（P1）')).toBeTruthy()
    expect(calls.some((call) => call.url.startsWith('/api/v1/plants'))).toBe(false)
    expect(calls.some((call) => call.url.startsWith('/api/v1/materials') && call.plant === PLANT_A)).toBe(true)

    await user.type(screen.getByRole('textbox', { name: '关键词' }), '外壳')
    await user.click(screen.getByRole('button', { name: /查询/ }))
    await waitFor(() => {
      expect(calls.some((call) => call.url.includes('q=') && call.url.includes('page=1') && call.plant === PLANT_A)).toBe(true)
    })
    expect(calls.every((call) => !call.url.includes('categoryId') && !call.url.includes('createdFrom'))).toBe(true)
  })

  it('ignores a late response from the previous factory', async () => {
    seedShell()
    const user = userEvent.setup()
    const lateA: { resolve: ((response: Response) => void) | null } = { resolve: null }
    let plantNow = PLANT_A
    vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const plantHeader = new Headers(init?.headers).get('X-Plant-Id')
      if (url.startsWith('/api/v1/auth/switch-plant')) {
        const body = switchBody(init)
        plantNow = body.plant.id
        return Promise.resolve(Response.json(body))
      }
      if (url.startsWith('/api/v1/auth/me')) {
        return Promise.resolve(Response.json(sessionMe(plantNow)))
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

  it('shows a loading state while the material list reloads', async () => {
    seedShell()
    const user = userEvent.setup()
    let materialLists = 0
    const pendingList: { resolve: ((response: Response) => void) | null } = { resolve: null }
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.startsWith('/api/v1/plants')) {
        return Response.json(page([plant(PLANT_A, 'P1', '一厂')]))
      }
      if (url.startsWith('/api/v1/uoms')) {
        return Response.json(page([{ id: UOM, code: 'PCS', name: '件', decimals: 0, enabled: true, lockVersion: 0 }]))
      }
      if (url.startsWith('/api/v1/materials')) {
        materialLists += 1
        if (materialLists === 1) return Response.json(page([material('AAA-CODE')]))
        return new Promise<Response>((resolve) => {
          pendingList.resolve = resolve
        })
      }
      return Response.json({})
    }))

    render(<App />)
    expect(await screen.findByText('AAA-CODE')).toBeTruthy()
    await user.click(screen.getByRole('columnheader', { name: '编码' }))
    expect(document.querySelector('.ant-spin-spinning')).toBeTruthy()
    pendingList.resolve?.(Response.json(page([material('AAA-CODE')])))
    await waitFor(() => expect(document.querySelector('.ant-spin-spinning')).toBeNull())
    expect(screen.getByText('AAA-CODE')).toBeTruthy()
  })

  it('shows the form after a failed material load is retried', async () => {
    seedShell()
    const user = userEvent.setup()
    const detailId = '00000000-0000-4000-8000-0000000000a1'
    let detailCalls = 0
    const pendingDetail: { resolve: ((response: Response) => void) | null } = { resolve: null }
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.startsWith('/api/v1/plants')) {
        return Response.json(page([plant(PLANT_A, 'P1', '一厂')]))
      }
      if (url.startsWith('/api/v1/uoms')) {
        return Response.json(page([{ id: UOM, code: 'PCS', name: '件', decimals: 0, enabled: true, lockVersion: 0 }]))
      }
      if (url.startsWith('/api/v1/custom-field-schemas/')) {
        return Response.json({
          entityType: 'material',
          plantId: PLANT_A,
          schema: { schemaVersion: 1, fields: [] },
          lockVersion: 0,
        })
      }
      if (url.startsWith(`/api/v1/materials/${detailId}`)) {
        detailCalls += 1
        if (detailCalls === 1) {
          return Response.json(
            { title: '加载失败', status: 500, detail: '暂时失败', code: 'INTERNAL', errors: [] },
            { status: 500 },
          )
        }
        return new Promise<Response>((resolve) => {
          pendingDetail.resolve = resolve
        })
      }
      if (url.startsWith('/api/v1/materials')) {
        return Response.json(page([material('AAA-CODE')]))
      }
      return Response.json({ title: 'missing', status: 404, detail: url, code: 'NOT_FOUND', errors: [] }, { status: 404 })
    }))

    render(<App />)
    expect(await screen.findByText('AAA-CODE')).toBeTruthy()
    await user.click(screen.getByRole('button', { name: '编辑' }))
    expect(await screen.findByRole('button', { name: '重试' })).toBeTruthy()
    expect(screen.queryByRole('textbox', { name: '名称' })).toBeNull()
    await user.click(screen.getByRole('button', { name: '重试' }))
    expect(await screen.findByText('正在加载物料、计量单位和自定义字段…')).toBeTruthy()
    expect(screen.queryByRole('button', { name: '重试' })).toBeNull()
    pendingDetail.resolve?.(Response.json(material('AAA-CODE')))
    expect(await screen.findByDisplayValue('一厂外壳')).toBeTruthy()
    expect(screen.queryByRole('button', { name: '重试' })).toBeNull()
  })
})
