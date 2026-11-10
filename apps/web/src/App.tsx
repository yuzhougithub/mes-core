import '@ant-design/v5-patch-for-react-19'
import { App as AntdApp, ConfigProvider } from 'antd'
import { AuthGate } from './auth/AuthGate.tsx'
import { zhCN } from './locale.ts'
import { BrandProvider } from './brand/BrandProvider.tsx'

export default function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      button={{ autoInsertSpace: false }}
      theme={{
        token: {
          colorPrimary: '#1677ff',
          borderRadius: 8,
          colorBgLayout: '#f5f7fa',
        },
      }}
    >
      <AntdApp>
        <BrandProvider>
          <AuthGate />
        </BrandProvider>
      </AntdApp>
    </ConfigProvider>
  )
}
