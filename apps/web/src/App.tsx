import { ConfigProvider, Typography } from 'antd'
import zhCN from 'antd/locale/zh_CN'

const { Title, Paragraph } = Typography

export default function App() {
  return (
    <ConfigProvider locale={zhCN}>
      <main style={{ maxWidth: 720, margin: '64px auto', padding: '0 24px' }}>
        <Title level={2}>MES 主数据</Title>
        <Paragraph>
          精密、电子、机加离散制造的私有化多工厂 MES。此页是 PC 端骨架，主数据功能尚未开始。
        </Paragraph>
      </main>
    </ConfigProvider>
  )
}
