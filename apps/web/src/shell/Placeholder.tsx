import { Button, Card, Typography } from 'antd'
import type { NavItem } from './nav.ts'

export function Placeholder({ item }: { item: NavItem }) {
  return (
    <Card>
      <Typography.Paragraph style={{ marginBottom: 8 }}>
        {item.placeholder}
      </Typography.Paragraph>
      <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
        此页没有可用操作，也不会展示模拟数据。
      </Typography.Paragraph>
      <Button type="primary" style={{ marginTop: 16 }} disabled>
        功能未实现
      </Button>
    </Card>
  )
}
