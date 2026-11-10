import { describe, expect, it } from 'vitest'
import { grantTree, isGrantable, sanitizeGrant, type GrantSource } from './grant.ts'

const TREE: GrantSource[] = [
  {
    id: 'dir-md',
    type: 'DIR',
    name: '主数据',
    children: [
      {
        id: 'menu-material',
        type: 'MENU',
        name: '物料',
        permCode: 'md:material:list',
        children: [
          { id: 'btn-create', type: 'BUTTON', name: '新建', permCode: 'md:material:create' },
          { id: 'btn-delete', type: 'BUTTON', name: '删除', permCode: 'md:material:delete' },
        ],
      },
    ],
  },
]

describe('role permission tree', () => {
  it('keeps menus and buttons in one tree and does not grant a directory', () => {
    const nodes = grantTree(TREE)
    expect(nodes[0]?.name).toBe('主数据')
    expect(nodes[0]?.checkable).toBe(false)
    expect(isGrantable('DIR')).toBe(false)
    expect(nodes[0]?.children[0]?.name).toBe('物料')
    expect(nodes[0]?.children[0]?.checkable).toBe(true)
    expect(nodes[0]?.children[0]?.children.map((item) => item.name)).toEqual(['新建', '删除'])
    expect(nodes[0]?.children[0]?.children.every((item) => item.checkable && item.type === 'BUTTON')).toBe(true)
  })

  it('stores only the checked menu and button ids, without inferring the rest', () => {
    expect(sanitizeGrant(['menu-material'], TREE)).toEqual(['menu-material'])
    expect(sanitizeGrant(['btn-create'], TREE)).toEqual(['btn-create'])
    expect(sanitizeGrant(['dir-md', 'menu-material', 'btn-delete', 'missing'], TREE)).toEqual([
      'menu-material',
      'btn-delete',
    ])
    expect(sanitizeGrant([], TREE)).toEqual([])
  })
})
