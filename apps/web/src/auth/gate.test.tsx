import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../App.tsx'
import { resetPlantSession } from '../api/plant-session.ts'
import { resetAuthSession, seedAuthSession } from './store.ts'

const PLANT = '00000000-0000-4000-8000-0000000000a1'

afterEach(() => {
  cleanup()
  resetAuthSession()
  resetPlantSession()
  vi.unstubAllGlobals()
})

describe('forced password change', () => {
  it('stays on the password page and does not open the shell', async () => {
    const user = userEvent.setup()
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url === '/api/v1/auth/refresh') {
        return Response.json({
          title: '未登录',
          status: 401,
          detail: '未登录',
          code: 'UNAUTHENTICATED',
          errors: [],
        }, { status: 401 })
      }
      if (url === '/api/v1/auth/login' && init?.method === 'POST') {
        return Response.json({
          accessToken: 'access-1',
          expiresIn: 1800,
          mustChangePassword: true,
          plant: { id: PLANT, code: 'P1', name: '一厂' },
          roles: ['ADMIN'],
        })
      }
      return Response.json({ title: 'missing', status: 404, detail: url, code: 'NOT_FOUND', errors: [] }, { status: 404 })
    }))

    render(<App />)
    expect(await screen.findByRole('button', { name: '登录' })).toBeTruthy()
    await user.type(screen.getByLabelText('用户名'), 'admin')
    await user.type(screen.getByLabelText('密码'), 'OldPassw0rd!')
    await user.click(screen.getByRole('button', { name: '登录' }))
    expect(await screen.findByRole('button', { name: '更新密码' })).toBeTruthy()
    expect(screen.getByText('请先修改密码')).toBeTruthy()
    expect(screen.queryByRole('tab', { name: '物料管理' })).toBeNull()
    expect(screen.queryByText('物料主数据')).toBeNull()
    expect(screen.queryByText('用户管理')).toBeNull()
  })

  it('enters the shell after login when no password change is required', async () => {
    const user = userEvent.setup()
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url === '/api/v1/auth/refresh') {
        return Response.json({
          title: '未登录',
          status: 401,
          detail: '未登录',
          code: 'UNAUTHENTICATED',
          errors: [],
        }, { status: 401 })
      }
      if (url === '/api/v1/auth/login' && init?.method === 'POST') {
        return Response.json({
          accessToken: 'access-1',
          expiresIn: 1800,
          mustChangePassword: false,
          plant: { id: PLANT, code: 'P1', name: '一厂' },
          roles: ['ADMIN'],
        })
      }
      if (url === '/api/v1/auth/me') {
        return Response.json({
          user: {
            id: '00000000-0000-4000-8000-0000000000c1',
            username: 'admin',
            status: 'ACTIVE',
            superAdmin: true,
            mustChangePassword: false,
            permVersion: 1,
          },
          person: { id: '00000000-0000-4000-8000-0000000000c2', code: 'A001', name: '管理员' },
          plant: { id: PLANT, code: 'P1', name: '一厂' },
          plants: [{ id: PLANT, code: 'P1', name: '一厂', isDefault: true }],
          roles: ['ADMIN'],
          permissions: [],
          menus: [],
        })
      }
      if (url.startsWith('/api/v1/materials') || url.startsWith('/api/v1/uoms')) {
        return Response.json({ items: [], page: 1, size: 20, total: 0 })
      }
      return Response.json({ title: 'missing', status: 404, detail: url, code: 'NOT_FOUND', errors: [] }, { status: 404 })
    }))

    render(<App />)
    expect(await screen.findByRole('button', { name: '登录' })).toBeTruthy()
    await user.type(screen.getByLabelText('用户名'), 'admin')
    await user.type(screen.getByLabelText('密码'), 'ChangeMe-12!')
    await user.click(screen.getByRole('button', { name: '登录' }))
    expect(await screen.findByRole('tab', { name: '物料管理' })).toBeTruthy()
    expect(screen.getByText('管理员')).toBeTruthy()
  })

  it('returns to the login page when the access token is invalid', async () => {
    seedAuthSession({
      phase: 'ready',
      token: 'old-token',
      notice: null,
      me: {
        user: {
          id: '00000000-0000-4000-8000-0000000000c1',
          username: 'admin',
          status: 'ACTIVE',
          superAdmin: true,
          mustChangePassword: false,
          permVersion: 1,
        },
        person: { id: '00000000-0000-4000-8000-0000000000c2', code: 'A001', name: '管理员' },
        plant: { id: PLANT, code: 'P1', name: '一厂' },
        plants: [{ id: PLANT, code: 'P1', name: '一厂', isDefault: true }],
        roles: ['ADMIN'],
        permissions: [],
        menus: [],
      },
    })
    let refreshes = 0
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url === '/api/v1/auth/refresh') {
        refreshes += 1
        return Response.json({
          title: '登录凭证无效',
          status: 401,
          detail: '登录凭证无效',
          code: 'TOKEN_INVALID',
          errors: [],
        }, { status: 401 })
      }
      if (url.startsWith('/api/v1/materials') || url.startsWith('/api/v1/uoms')) {
        return Response.json({
          title: '登录凭证无效',
          status: 401,
          detail: '登录凭证无效',
          code: 'TOKEN_INVALID',
          errors: [],
        }, { status: 401 })
      }
      return Response.json({ title: 'missing', status: 404, detail: url, code: 'NOT_FOUND', errors: [] }, { status: 404 })
    }))

    render(<App />)
    expect(await screen.findByRole('button', { name: '登录' })).toBeTruthy()
    expect(refreshes).toBeGreaterThan(0)
    expect(screen.queryByText('物料主数据')).toBeNull()
  })

  it('uses the username from /auth/me when checking a new password', async () => {
    const user = userEvent.setup()
    const calls: string[] = []
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      calls.push(`${init?.method ?? 'GET'} ${url}`)
      if (url === '/api/v1/auth/refresh') {
        return Response.json({
          title: '未登录',
          status: 401,
          detail: '未登录',
          code: 'UNAUTHENTICATED',
          errors: [],
        }, { status: 401 })
      }
      if (url === '/api/v1/auth/login' && init?.method === 'POST') {
        return Response.json({
          accessToken: 'access-1',
          expiresIn: 1800,
          mustChangePassword: true,
          plant: null,
          roles: [],
        })
      }
      if (url === '/api/v1/auth/me') {
        return Response.json({
          user: {
            id: '00000000-0000-4000-8000-0000000000c1',
            username: 'zhang',
            status: 'ACTIVE',
            superAdmin: false,
            mustChangePassword: true,
            permVersion: 1,
          },
          person: { id: '00000000-0000-4000-8000-0000000000c2', code: 'Z001', name: '张三' },
          plant: { id: PLANT, code: 'P1', name: '一厂' },
          plants: [{ id: PLANT, code: 'P1', name: '一厂', isDefault: true }],
          roles: [],
          permissions: [],
          menus: [],
        })
      }
      return Response.json({ title: 'missing', status: 404, detail: url, code: 'NOT_FOUND', errors: [] }, { status: 404 })
    }))

    render(<App />)
    await user.type(await screen.findByLabelText('用户名'), 'admin')
    await user.type(screen.getByLabelText('密码'), 'OldPassw0rd!')
    await user.click(screen.getByRole('button', { name: '登录' }))
    await user.type(await screen.findByLabelText('当前密码'), 'OldPassw0rd!')
    await user.type(screen.getByLabelText('新密码'), 'Zhang-aa1!')
    await user.type(screen.getByLabelText('确认新密码'), 'Zhang-aa1!')
    await user.click(screen.getByRole('button', { name: '更新密码' }))
    expect(await screen.findByText('密码不能包含用户名')).toBeTruthy()
    expect(calls.some((call) => call.includes('/api/v1/auth/me'))).toBe(true)
    expect(calls.some((call) => call.includes('/api/v1/auth/change-password'))).toBe(false)
  })

  it('returns to the login page when a refresh fails', async () => {
    seedAuthSession({
      phase: 'ready',
      token: 'old-token',
      notice: null,
      me: {
        user: {
          id: '00000000-0000-4000-8000-0000000000c1',
          username: 'admin',
          status: 'ACTIVE',
          superAdmin: true,
          mustChangePassword: false,
          permVersion: 1,
        },
        person: { id: '00000000-0000-4000-8000-0000000000c2', code: 'A001', name: '管理员' },
        plant: { id: PLANT, code: 'P1', name: '一厂' },
        plants: [{ id: PLANT, code: 'P1', name: '一厂', isDefault: true }],
        roles: ['ADMIN'],
        permissions: [],
        menus: [],
      },
    })
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url === '/api/v1/auth/refresh') {
        return Response.json({
          title: '未登录',
          status: 401,
          detail: '未登录',
          code: 'UNAUTHENTICATED',
          errors: [],
        }, { status: 401 })
      }
      if (url.startsWith('/api/v1/materials') || url.startsWith('/api/v1/uoms')) {
        return Response.json({
          title: '登录已过期',
          status: 401,
          detail: '登录已过期',
          code: 'TOKEN_EXPIRED',
          errors: [],
        }, { status: 401 })
      }
      return Response.json({ title: 'missing', status: 404, detail: url, code: 'NOT_FOUND', errors: [] }, { status: 404 })
    }))

    render(<App />)
    expect(await screen.findByRole('button', { name: '登录' })).toBeTruthy()
    expect(screen.queryByText('物料主数据')).toBeNull()
  })
})
