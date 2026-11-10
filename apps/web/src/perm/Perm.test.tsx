import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { resetAuthSession, seedAuthSession } from '../auth/store.ts'
import type { MeResponse } from '../auth/types.ts'
import { Perm } from './Perm.tsx'

afterEach(() => {
  cleanup()
  resetAuthSession()
})

function ready(patch: Partial<Pick<MeResponse, 'permissions' | 'roles'>> & { superAdmin?: boolean }) {
  seedAuthSession({
    phase: 'ready',
    token: 'token',
    notice: null,
    me: {
      user: {
        id: 'user-1',
        username: 'admin',
        status: 'ACTIVE',
        superAdmin: patch.superAdmin === true,
        mustChangePassword: false,
        permVersion: 1,
      },
      person: null,
      plant: { id: 'plant-1', code: 'P1', name: '一厂' },
      plants: [],
      roles: patch.roles ?? ['ADMIN'],
      permissions: patch.permissions ?? [],
      menus: [],
    },
  })
}

describe('Perm', () => {
  it('renders the button only when /auth/me issued that exact code', () => {
    ready({ permissions: ['sys:menu:create'], roles: [], superAdmin: false })
    render(<Perm code="sys:menu:create"><button type="button">新增</button></Perm>)
    expect(screen.getByRole('button', { name: '新增' })).toBeTruthy()
  })

  it('does not infer permissions from the admin role or the super-admin flag', () => {
    ready({ permissions: [], roles: ['ADMIN'], superAdmin: true })
    render(<Perm code="sys:menu:create"><button type="button">新增</button></Perm>)
    expect(screen.queryByRole('button', { name: '新增' })).toBeNull()
  })

  it('does not treat a different code as a match', () => {
    ready({ permissions: ['sys:menu:update'], roles: [], superAdmin: false })
    render(<Perm code="sys:menu:create"><button type="button">新增</button></Perm>)
    expect(screen.queryByRole('button', { name: '新增' })).toBeNull()
  })

  it('hides a global write from a factory admin who was issued the code', () => {
    ready({ permissions: ['sys:menu:create'], roles: ['ADMIN'], superAdmin: false })
    render(<Perm code="sys:menu:create" global><button type="button">新增</button></Perm>)
    expect(screen.queryByRole('button', { name: '新增' })).toBeNull()
  })

  it('shows a global write only when the code is present and the user is a super admin', () => {
    ready({ permissions: ['sys:menu:create'], roles: [], superAdmin: true })
    const { unmount } = render(<Perm code="sys:menu:create" global><button type="button">新增</button></Perm>)
    expect(screen.getByRole('button', { name: '新增' })).toBeTruthy()
    unmount()
    ready({ permissions: [], roles: [], superAdmin: true })
    render(<Perm code="sys:menu:create" global><button type="button">新增</button></Perm>)
    expect(screen.queryByRole('button', { name: '新增' })).toBeNull()
  })
})
