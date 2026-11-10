import { describe, expect, it } from 'vitest'
import { normalizeMenus } from '../auth/menus.ts'
import type { MeMenu } from '../auth/types.ts'
import { firstLeaf, navFromMenus } from './nav-model.ts'

function menu(partial: Partial<MeMenu> & Pick<MeMenu, 'id' | 'name' | 'type'>): MeMenu {
  return {
    parentId: null,
    permCode: null,
    path: null,
    component: null,
    icon: null,
    sortNo: 0,
    visible: true,
    children: [],
    ...partial,
  }
}

describe('dynamic menus', () => {
  it('follows the /auth/me tree and leaves button nodes out of the sidebar', () => {
    const modules = navFromMenus([
      menu({
        id: 'sys',
        name: '系统管理',
        type: 'DIR',
        children: [
          menu({
            id: 'menus',
            parentId: 'sys',
            name: '菜单',
            type: 'MENU',
            path: '/menus',
            component: 'sys/menu',
            children: [
              menu({
                id: 'create',
                parentId: 'menus',
                name: '新建',
                type: 'BUTTON',
                permCode: 'sys:menu:create',
              }),
            ],
          }),
          menu({
            id: 'nested',
            parentId: 'sys',
            name: '配置',
            type: 'DIR',
            children: [
              menu({
                id: 'fields',
                parentId: 'nested',
                name: '自定义字段',
                type: 'MENU',
                path: '/custom-field-schemas',
                component: 'cfg/field-schema',
              }),
            ],
          }),
        ],
      }),
      menu({
        id: 'md',
        name: '主数据',
        type: 'DIR',
        children: [
          menu({
            id: 'materials',
            parentId: 'md',
            name: '物料',
            type: 'MENU',
            path: '/materials',
            component: 'md/material',
          }),
        ],
      }),
    ])

    expect(modules.map((item) => item.label)).toEqual(['系统管理', '主数据'])
    expect(modules[0]?.nodes.map((item) => item.kind === 'dir' ? item.label : item.label)).toEqual(['菜单', '配置'])
    const menuLeaf = modules[0]?.nodes[0]
    expect(menuLeaf?.kind).toBe('leaf')
    if (menuLeaf?.kind === 'leaf') {
      expect(menuLeaf.component).toBe('sys/menu')
      expect(menuLeaf.breadcrumb).toEqual(['系统管理', '菜单'])
    }
    const nested = modules[0]?.nodes[1]
    expect(nested?.kind).toBe('dir')
    if (nested?.kind === 'dir') {
      expect(firstLeaf(nested.children)?.label).toBe('自定义字段')
      expect(firstLeaf(nested.children)?.breadcrumb).toEqual(['系统管理', '配置', '自定义字段'])
    }
    const labels = JSON.stringify(modules)
    expect(labels).not.toContain('新建')
    expect(labels).not.toContain('工作台')
  })

  it('drops malformed nodes and does not invent a menu', () => {
    expect(normalizeMenus([
      { id: 'ok', type: 'MENU', name: '用户', path: '/users', component: 'sys/user' },
      { id: '', type: 'MENU', name: '空' },
      { type: 'DIR', name: '缺 id' },
      'bad',
    ])).toEqual([
      menu({ id: 'ok', name: '用户', type: 'MENU', path: '/users', component: 'sys/user' }),
    ])
    expect(navFromMenus([])).toEqual([])
  })
})
