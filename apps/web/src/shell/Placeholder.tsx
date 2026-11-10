import { Button, Card, Tooltip } from 'antd'

export function Placeholder({ message }: { message: string }) {
  return (
    <Card>
      <p className="mes-placeholder">{message}</p>
      <Tooltip title="即将上线">
        <span className="mes-soon">
          <Button disabled>即将上线</Button>
        </span>
      </Tooltip>
    </Card>
  )
}
