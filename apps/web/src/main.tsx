import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import '@ant-design/v5-patch-for-react-19'
import 'antd/dist/reset.css'
import './index.css'
import App from './App.tsx'

dayjs.locale('zh-cn')

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
