import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App as AntdApp, ConfigProvider } from 'antd'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { resetPlantSession } from '../api/plant-session.ts'
import { resetAuthSession, seedAuthSession } from '../auth/store.ts'
import { zhCN } from '../locale.ts'
import { UserEditor } from './UserEditor.tsx'

const PLANT = '00000000-0000-4000-8000-0000000000a1'
const SELF = '00000000-0000-4000-8000-0000000000d0'
const OTHER = '00000000-0000-4000-8000-0000000000d1'

afterEach(() => {
  cleanup()
  resetAuthSession()
  resetPlantSession()
  vi.unstubAllGlobals()
})

function seed(userId: string, permissions: string[]) {
  seedAuthSession({
    phase: 'ready',
    token: 'token',
    notice: null,
    me: {
      user: {
        id: userId,
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
      permissions,
      menus: [],
    },
  })
}

function detail(id: string) {
  return {
    id,
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
      jobTitle: '班长',
      roleIds: [] as string[],
      lockVersion: 1,
    }],
  }
}

function install(id: string) {
  const calls: string[] = []
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    const method = init?.method ?? 'GET'
    calls.push(`${method} ${url}`)
    if (url.startsWith('/api/v1/roles')) {
      return Response.json({ items: [], page: 1, size: 200, total: 0 })
    }
    if (url === `/api/v1/users/${id}` && method === 'GET') return Response.json(detail(id))
    if (url === `/api/v1/users/${id}` && method === 'PUT') {
      return Response.json({ ...detail(id), lockVersion: 5, name: '改名' })
    }
    if (url === `/api/v1/users/${id}/plants`) return Response.json(detail(id))
    return Response.json({ title: 'missing', status: 404, detail: url, code: 'NOT_FOUND', errors: [] }, { status: 404 })
  }))
  return calls
}

function renderEditor(userId: string) {
  return render(
    <ConfigProvider locale={zhCN} button={{ autoInsertSpace: false }}>
      <AntdApp>
        <UserEditor
          open
          mode="edit"
          userId={userId}
          superAdmin={false}
          plants={[{ id: PLANT, code: 'P1', name: '一厂' }]}
          onClose={() => {}}
          onSaved={() => {}}
        />
      </AntdApp>
    </ConfigProvider>,
  )
}

describe('user plant save', () => {
  it('skips plant replacement when the assignment did not change', async () => {
    const user = userEvent.setup()
    seed(SELF, ['sys:user:update', 'sys:user:grant'])
    const calls = install(OTHER)
    renderEditor(OTHER)
    const name = await screen.findByDisplayValue('编辑')
    await user.clear(name)
    await user.type(name, '改名')
    await user.click(screen.getByRole('button', { name: '保存' }))
    expect(await screen.findByText('已保存')).toBeTruthy()
    expect(calls.some((call) => call.startsWith('PUT') && call.includes('/plants'))).toBe(false)
    expect(calls.some((call) => call === `PUT /api/v1/users/${OTHER}`)).toBe(true)
  })

  it('keeps assignments read-only when editing yourself or without grant', async () => {
    const user = userEvent.setup()
    seed(SELF, ['sys:user:update', 'sys:user:grant'])
    const selfCalls = install(SELF)
    const view = renderEditor(SELF)
    const plant = await screen.findByRole('checkbox', { name: '一厂（P1）' })
    expect((plant as HTMLInputElement).disabled).toBe(true)
    expect((screen.getByRole('combobox', { name: '一厂角色' }) as HTMLInputElement).disabled).toBe(true)
    await user.click(screen.getByRole('button', { name: '保存' }))
    expect(await screen.findByText('已保存')).toBeTruthy()
    expect(selfCalls.some((call) => call.includes('/plants'))).toBe(false)
    view.unmount()

    seed(SELF, ['sys:user:update'])
    const otherCalls = install(OTHER)
    renderEditor(OTHER)
    expect((await screen.findByRole('checkbox', { name: '一厂（P1）' }) as HTMLInputElement).disabled).toBe(true)
    await user.click(screen.getByRole('button', { name: '保存' }))
    expect(await screen.findByText('已保存')).toBeTruthy()
    expect(otherCalls.some((call) => call.includes('/plants'))).toBe(false)
  })
})
