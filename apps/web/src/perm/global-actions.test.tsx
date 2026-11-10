import { cleanup, render, screen } from '@testing-library/react'
import { App as AntdApp, ConfigProvider } from 'antd'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { resetPlantSession } from '../api/plant-session.ts'
import { resetAuthSession, seedAuthSession } from '../auth/store.ts'
import { zhCN } from '../locale.ts'
import { MenuPage } from '../menus/MenuPage.tsx'
import { RolePage } from '../roles/RolePage.tsx'

const PLANT = '00000000-0000-4000-8000-0000000000a1'
const WRITE = /^(plus )?新增$|^编辑$|^移动$|^停用$|^启用$|^删除$|^授权$/

afterEach(() => {
  cleanup()
  resetAuthSession()
  resetPlantSession()
  vi.unstubAllGlobals()
})

function seedFactoryAdmin(superAdmin: boolean) {
  seedAuthSession({
    phase: 'ready',
    token: 'token',
    notice: null,
    me: {
      user: {
        id: 'user-1',
        username: 'admin',
        status: 'ACTIVE',
        superAdmin,
        mustChangePassword: false,
        permVersion: 1,
      },
      person: { id: 'person-1', code: 'A001', name: '管理员' },
      plant: { id: PLANT, code: 'P1', name: '一厂' },
      plants: [{ id: PLANT, code: 'P1', name: '一厂', isDefault: true }],
      roles: ['ADMIN'],
      permissions: [
        'sys:menu:list', 'sys:menu:create', 'sys:menu:update', 'sys:menu:delete',
        'sys:role:list', 'sys:role:create', 'sys:role:update', 'sys:role:delete', 'sys:role:grant',
      ],
      menus: [],
    },
  })
}

function installApi() {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input)
    if (url.startsWith('/api/v1/menus/tree')) {
      return Response.json([{
        id: 'dir-sys',
        parentId: null,
        type: 'DIR',
        name: '系统管理',
        permCode: null,
        path: null,
        component: null,
        icon: null,
        sortNo: 10,
        visible: true,
        enabled: true,
        lockVersion: 0,
        children: [],
      }])
    }
    if (url.startsWith('/api/v1/roles')) {
      return Response.json({
        items: [{
          id: 'role-admin',
          code: 'ADMIN',
          name: '管理员',
          remark: null,
          dataScope: 'PLANT',
          sortNo: 10,
          enabled: true,
          builtin: true,
          lockVersion: 1,
          updatedAt: '2026-11-09T08:30:00Z',
        }],
        page: 1,
        size: 10,
        total: 1,
      })
    }
    return Response.json({ title: 'missing', status: 404, detail: url, code: 'NOT_FOUND', errors: [] }, { status: 404 })
  }))
}

function renderPages() {
  return render(
    <ConfigProvider locale={zhCN} button={{ autoInsertSpace: false }}>
      <AntdApp>
        <MenuPage />
        <RolePage />
      </AntdApp>
    </ConfigProvider>,
  )
}

describe('global menu and role actions', () => {
  it('hides create, update, delete, move, and grant from a factory ADMIN', async () => {
    seedFactoryAdmin(false)
    installApi()
    renderPages()
    expect(await screen.findByText('系统管理')).toBeTruthy()
    expect(await screen.findByText('管理员')).toBeTruthy()
    const writes = screen.getAllByRole('button').filter((button) => WRITE.test(button.textContent?.trim() ?? ''))
    expect(writes).toEqual([])
  })

  it('shows those actions to a super admin who holds the codes', async () => {
    seedFactoryAdmin(true)
    installApi()
    renderPages()
    expect(await screen.findByRole('button', { name: '移动' })).toBeTruthy()
    expect(screen.getAllByRole('button', { name: /新增/ }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('button', { name: '编辑' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('button', { name: '停用' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('button', { name: '删除' }).length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: '授权' })).toBeTruthy()
  })
})
