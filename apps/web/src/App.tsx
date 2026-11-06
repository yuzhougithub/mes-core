import { Card, ConfigProvider, Typography, message } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { SchemaForm } from './schema/SchemaForm.tsx'
import { mockSchema } from './schema/mockSchema.ts'
import type { SchemaValues } from './schema/types.ts'

const { Title, Paragraph } = Typography

export default function App() {
  const handleFinish = (values: SchemaValues) => {
    console.log('SchemaForm submit values:', values)
    void message.success('已提交，详见控制台输出')
  }

  return (
    <ConfigProvider locale={zhCN}>
      <main style={{ maxWidth: 960, margin: '48px auto', padding: '0 24px' }}>
        <Title level={2}>动态表单 Schema 渲染器演示</Title>
        <Paragraph type="secondary">
          本页用于验证自研薄 Schema 渲染器（Ant Design Form）。字段定义来自
          mockSchema，覆盖 string / number / datetime / enum / multi_enum /
          boolean / image / file。正式主数据列表与接口接入待架构师规范进仓后实现。
        </Paragraph>
        <Card>
          <SchemaForm schema={mockSchema} onFinish={handleFinish} />
        </Card>
      </main>
    </ConfigProvider>
  )
}
