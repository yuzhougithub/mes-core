import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { MenuNode } from '../api/menus.ts'
import { RoleGrantTree } from './RoleGrantTree.tsx'

afterEach(() => {
  cleanup()
})

function node(partial: Pick<MenuNode, 'id' | 'type' | 'name'> & Partial<MenuNode>): MenuNode {
  return {
    parentId: null,
    permCode: null,
    path: null,
    component: null,
    icon: null,
    sortNo: 0,
    visible: true,
    enabled: true,
    lockVersion: 0,
    children: [],
    ...partial,
  }
}

const MENUS: MenuNode[] = [
  node({
    id: 'dir-md',
    type: 'DIR',
    name: '主数据',
    children: [
      node({
        id: 'menu-material',
        parentId: 'dir-md',
        type: 'MENU',
        name: '物料',
        permCode: 'md:material:list',
        path: '/materials',
        component: 'md/material',
        children: [
          node({
            id: 'btn-create',
            parentId: 'menu-material',
            type: 'BUTTON',
            name: '新建',
            permCode: 'md:material:create',
          }),
          node({
            id: 'btn-delete',
            parentId: 'menu-material',
            type: 'BUTTON',
            name: '删除',
            permCode: 'md:material:delete',
          }),
        ],
      }),
    ],
  }),
]

function checkbox(name: string): HTMLElement {
  const title = screen.getByText(name)
  const row = title.closest('.ant-tree-treenode')
  const box = row?.querySelector('.ant-tree-checkbox')
  if (!box) throw new Error(`missing checkbox for ${name}`)
  return box as HTMLElement
}

describe('RoleGrantTree', () => {
  it('checks menus and buttons independently', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<RoleGrantTree menus={MENUS} value={['menu-material']} onChange={onChange} />)

    expect(screen.getByText('物料')).toBeTruthy()
    expect(screen.getByText('新建')).toBeTruthy()
    expect(screen.getByText('md:material:create')).toBeTruthy()
    expect(checkbox('主数据').className).toContain('ant-tree-checkbox-disabled')
    expect(checkbox('物料').className).toContain('ant-tree-checkbox-checked')
    expect(checkbox('新建').className).not.toContain('ant-tree-checkbox-checked')

    await user.click(checkbox('新建'))
    expect(onChange).toHaveBeenCalledWith(['menu-material', 'btn-create'])
  })
})
