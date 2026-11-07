import '@ant-design/v5-patch-for-react-19'
import { App as AntdApp, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { PlantProvider } from './plant/PlantProvider.tsx'
import { AdminShell } from './shell/AdminShell.tsx'

export default function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      button={{ autoInsertSpace: false }}
      theme={{
        token: {
          colorPrimary: '#1677ff',
          borderRadius: 8,
          colorBgLayout: '#f5f5f5',
        },
      }}
    >
      <AntdApp>
        <PlantProvider>
          <AdminShell />
        </PlantProvider>
      </AntdApp>
    </ConfigProvider>
  )
}
