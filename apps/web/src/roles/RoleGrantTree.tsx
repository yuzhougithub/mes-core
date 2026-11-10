import { Tree } from 'antd'
import type { DataNode } from 'antd/es/tree'
import type { MenuNode } from '../api/menus.ts'
import { menuTypeLabel } from '../menus/tree.ts'
import { grantTree, sanitizeGrant, type GrantTreeNode } from './grant.ts'

export function RoleGrantTree({
  menus,
  value,
  onChange,
}: {
  menus: readonly MenuNode[]
  value: readonly string[]
  onChange: (ids: string[]) => void
}) {
  const described = grantTree(menus)
  if (described.length === 0) return <div>暂无菜单</div>
  return (
    <Tree
      className="mes-grant-tree"
      checkable
      checkStrictly
      selectable={false}
      defaultExpandAll
      checkedKeys={{ checked: [...value], halfChecked: [] }}
      treeData={toData(described)}
      onCheck={(checked) => {
        const keys = Array.isArray(checked) ? checked : checked.checked
        onChange(sanitizeGrant(keys.map(String), menus))
      }}
    />
  )
}

function toData(nodes: readonly GrantTreeNode[]): DataNode[] {
  return nodes.map((node) => ({
    key: node.id,
    disableCheckbox: !node.checkable,
    title: (
      <span>
        <span className="mes-perm-code">{menuTypeLabel(node.type)}</span>
        <span>{node.name}</span>
        {node.permCode ? <span className="mes-perm-code">{node.permCode}</span> : null}
      </span>
    ),
    children: node.children.length > 0 ? toData(node.children) : undefined,
  }))
}
