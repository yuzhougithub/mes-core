import { Button, Card, Tooltip } from 'antd'
import type { NavItem } from './nav.ts'

export function Placeholder({ item }: { item: NavItem }) {
  return (
    <Card>
      <p className="mes-placeholder">{item.placeholder}</p>
      <Tooltip title="即将上线">
        <span className="mes-soon">
          <Button disabled>即将上线</Button>
        </span>
      </Tooltip>
    </Card>
  )
}
