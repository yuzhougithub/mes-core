import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../App.tsx'
import { resetPlantSession } from '../api/plant-session.ts'
import { resetAuthSession, seedAuthSession } from '../auth/store.ts'
import type { MeMenu } from '../auth/types.ts'

const PLANT = '00000000-0000-4000-8000-0000000000a1'

afterEach(() => {
  cleanup()
  resetAuthSession()
  resetPlantSession()
  vi.unstubAllGlobals()
})

const MENUS: MeMenu[] = [
  {
    id: 'sys',
    parentId: null,
    type: 'DIR',
    name: '系统管理',
    permCode: null,
    path: null,
    component: null,
    icon: null,
    sortNo: 10,
    visible: true,
    children: [
      {
        id: 'menus',
        parentId: 'sys',
        type: 'MENU',
        name: '菜单',
        permCode: 'sys:menu:list',
        path: '/menus',
        component: 'sys/menu',
        icon: null,
        sortNo: 10,
        visible: true,
        children: [
          {
            id: 'create',
            parentId: 'menus',
            type: 'BUTTON',
            name: '新建',
            permCode: 'sys:menu:create',
            path: null,
            component: null,
            icon: null,
            sortNo: 10,
            visible: true,
            children: [],
          },
        ],
      },
      {
        id: 'dict',
        parentId: 'sys',
        type: 'MENU',
        name: '字典',
        permCode: null,
        path: '/dicts',
        component: 'sys/dict',
        icon: null,
        sortNo: 20,
        visible: true,
        children: [],
      },
    ],
  },
]

describe('dynamic sidebar', () => {
  it('shows only the menus from /auth/me and a missing page for an unknown component', async () => {
    const user = userEvent.setup()
    seedAuthSession({
      phase: 'ready',
      token: 'token',
      notice: null,
      me: {
        user: {
          id: 'user-1',
          username: 'lead',
          status: 'ACTIVE',
          superAdmin: true,
          mustChangePassword: false,
          permVersion: 1,
        },
        person: { id: 'person-1', code: 'A001', name: '主管' },
        plant: { id: PLANT, code: 'P1', name: '一厂' },
        plants: [{ id: PLANT, code: 'P1', name: '一厂', isDefault: true }],
        roles: ['ADMIN'],
        permissions: [],
        menus: MENUS,
      },
    })
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.startsWith('/api/v1/menus/tree')) return Response.json([])
      return Response.json({ title: 'missing', status: 404, detail: url, code: 'NOT_FOUND', errors: [] }, { status: 404 })
    }))

    render(<App />)
    expect(await screen.findByRole('tab', { name: '系统管理' })).toBeTruthy()
    expect(screen.queryByRole('tab', { name: '工作台' })).toBeNull()
    expect(screen.getByRole('menuitem', { name: '菜单' })).toBeTruthy()
    expect(screen.queryByRole('menuitem', { name: '新建' })).toBeNull()
    expect(screen.getByText('系统管理 / 菜单')).toBeTruthy()
    expect(screen.queryByRole('button', { name: '新增' })).toBeNull()

    await user.click(screen.getByRole('menuitem', { name: '字典' }))
    expect(await screen.findByText('页面不存在')).toBeTruthy()
    expect(screen.getByText('系统管理 / 字典')).toBeTruthy()
  })
})
