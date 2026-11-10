import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App as AntdApp, ConfigProvider } from 'antd'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { resetPlantSession } from '../api/plant-session.ts'
import { zhCN } from '../locale.ts'
import { resetAuthSession, seedAuthSession } from '../auth/store.ts'
import { UserListPage } from './UserListPage.tsx'

const PLANT = '00000000-0000-4000-8000-0000000000a1'
const USER = '00000000-0000-4000-8000-0000000000d1'
const ROLE = '00000000-0000-4000-8000-0000000000e1'

afterEach(() => {
  cleanup()
  resetAuthSession()
  resetPlantSession()
  vi.unstubAllGlobals()
})

describe('user role assignment', () => {
  it('sends the selected role with the plant assignment', async () => {
    const user = userEvent.setup()
    const bodies: unknown[] = []
    seedAuthSession({
      phase: 'ready',
      token: 'token',
      notice: null,
      me: {
        user: {
          id: '00000000-0000-4000-8000-0000000000d0',
          username: 'lead',
          status: 'ACTIVE',
          superAdmin: false,
          mustChangePassword: false,
          permVersion: 1,
        },
        person: { id: '00000000-0000-4000-8000-0000000000d2', code: 'L001', name: '主管' },
        plant: { id: PLANT, code: 'P1', name: '一厂' },
        plants: [{ id: PLANT, code: 'P1', name: '一厂', isDefault: true }],
        roles: ['EDITOR'],
        permissions: ['sys:user:list', 'sys:user:view', 'sys:user:update', 'sys:user:grant'],
        menus: [],
      },
    })
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const method = init?.method ?? 'GET'
      if (url.startsWith('/api/v1/roles')) {
        return Response.json({
          items: [{
            id: ROLE,
            code: 'EDITOR',
            name: '编辑',
            remark: null,
            dataScope: 'PLANT',
            sortNo: 20,
            enabled: true,
            builtin: true,
            lockVersion: 0,
          }],
          page: 1,
          size: 200,
          total: 1,
        })
      }
      if (url === `/api/v1/users/${USER}` && method === 'GET') {
        return Response.json({
          id: USER,
          username: 'editor',
          status: 'ACTIVE',
          superAdmin: false,
          mustChangePassword: false,
          permVersion: 1,
          defaultPlantId: PLANT,
          lockVersion: 4,
          person: {
            id: '00000000-0000-4000-8000-0000000000d3',
            code: 'E001',
            name: '编辑',
            mobile: '13812345678',
            email: 'zhang@x.com',
            enabled: true,
            remark: null,
            lockVersion: 1,
          },
          plants: [{
            plantId: PLANT,
            plantCode: 'P1',
            plantName: '一厂',
            isDefault: true,
            active: true,
            jobTitle: null,
            roleIds: [],
            lockVersion: 1,
          }],
        })
      }
      if (url === `/api/v1/users/${USER}` && method === 'PUT') {
        return Response.json({
          id: USER,
          username: 'editor',
          status: 'ACTIVE',
          superAdmin: false,
          mustChangePassword: false,
          permVersion: 1,
          defaultPlantId: PLANT,
          lockVersion: 5,
          person: {
            id: '00000000-0000-4000-8000-0000000000d3',
            code: 'E001',
            name: '编辑',
            mobile: '13812345678',
            email: 'zhang@x.com',
            enabled: true,
            remark: null,
            lockVersion: 1,
          },
          plants: [],
        })
      }
      if (url === `/api/v1/users/${USER}/plants` && method === 'PUT') {
        bodies.push(JSON.parse(String(init?.body ?? '{}')))
        return Response.json({
          id: USER,
          username: 'editor',
          status: 'ACTIVE',
          superAdmin: false,
          mustChangePassword: false,
          permVersion: 2,
          defaultPlantId: PLANT,
          lockVersion: 6,
          person: {
            id: '00000000-0000-4000-8000-0000000000d3',
            code: 'E001',
            name: '编辑',
            mobile: null,
            email: null,
            enabled: true,
            remark: null,
            lockVersion: 1,
          },
          plants: [],
        })
      }
      if (url.startsWith('/api/v1/users') && method === 'GET') {
        return Response.json({
          items: [{
            id: USER,
            username: 'editor',
            status: 'ACTIVE',
            superAdmin: false,
            mustChangePassword: false,
            personId: '00000000-0000-4000-8000-0000000000d3',
            personCode: 'E001',
            personName: '编辑',
            defaultPlantId: PLANT,
            lockVersion: 4,
            updatedAt: '2026-11-08T01:02:03Z',
            mobile: '13812345678',
            email: 'zhang@x.com',
          }],
          page: 1,
          size: 10,
          total: 1,
        })
      }
      return Response.json({ title: 'missing', status: 404, detail: url, code: 'NOT_FOUND', errors: [] }, { status: 404 })
    }))

    render(
      <ConfigProvider locale={zhCN} button={{ autoInsertSpace: false }}>
        <AntdApp>
          <UserListPage />
        </AntdApp>,
      </ConfigProvider>,
    )

    await user.click(await screen.findByRole('button', { name: '编辑' }))
    const roleSelect = await screen.findByRole('combobox', { name: '一厂角色' })
    await user.click(roleSelect)
    await user.click(await screen.findByText('编辑（EDITOR）'))
    await user.click(screen.getByRole('button', { name: '保存' }))
    await screen.findByText('已保存')
    const saved = bodies[0] as { plants: { roleIds: string[] }[] }
    expect(saved.plants[0]?.roleIds).toEqual([ROLE])
  })
})
