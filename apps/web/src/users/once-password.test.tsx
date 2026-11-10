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
const SECRET = 'Qw7!temp-pass'

afterEach(() => {
  cleanup()
  resetAuthSession()
  resetPlantSession()
  vi.unstubAllGlobals()
})

describe('one-time password', () => {
  it('shows a generated password once, with copy, and hides it after close', async () => {
    const user = userEvent.setup()
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    })
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
        permissions: [],
        menus: [],
      },
    })
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.startsWith('/api/v1/users') && (init?.method ?? 'GET') === 'GET') {
        return Response.json({
          items: [{
            id: USER,
            username: 'editor',
            status: 'ACTIVE',
            superAdmin: false,
            mustChangePassword: true,
            personId: '00000000-0000-4000-8000-0000000000d3',
            personCode: 'E001',
            personName: '编辑',
            defaultPlantId: PLANT,
            lockVersion: 3,
            updatedAt: '2026-11-08T01:02:03Z',
            mobile: '13812345678',
            email: 'zhang@x.com',
          }],
          page: 1,
          size: 10,
          total: 1,
        })
      }
      if (url === `/api/v1/users/${USER}/reset-password`) {
        const body = JSON.parse(String(init?.body ?? '{}')) as { newPassword?: string }
        expect(body.newPassword).toBeUndefined()
        return Response.json({ password: SECRET, mustChangePassword: true })
      }
      return Response.json({ title: 'missing', status: 404, detail: url, code: 'NOT_FOUND', errors: [] }, { status: 404 })
    }))

    render(
      <ConfigProvider locale={zhCN} button={{ autoInsertSpace: false }}>
        <AntdApp>
          <UserListPage />
        </AntdApp>
      </ConfigProvider>,
    )

    expect(await screen.findByText('138****5678')).toBeTruthy()
    expect(screen.getByText('z***@x.com')).toBeTruthy()
    expect(screen.queryByText('13812345678')).toBeNull()
    expect(screen.queryByText('zhang@x.com')).toBeNull()

    await user.click(screen.getByRole('button', { name: '重置密码' }))
    await user.click(await screen.findByRole('button', { name: '确认重置' }))
    const field = await screen.findByLabelText('临时口令')
    expect((field as HTMLInputElement).value).toBe(SECRET)
    await user.click(screen.getByRole('button', { name: '复制' }))
    expect(writeText).toHaveBeenCalledWith(SECRET)
    await user.click(screen.getByRole('button', { name: '知道了' }))
    expect(screen.queryByLabelText('临时口令')).toBeNull()
    expect(document.body.textContent ?? '').not.toContain(SECRET)
  })
})
