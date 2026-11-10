import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { Alert, Button, Form, Input } from 'antd'
import { useState } from 'react'
import { formatProblem, toApiError } from '../api/problem.ts'
import { useBrand } from '../brand/useBrand.ts'
import { login } from './store.ts'
import { useAuth } from './useAuth.ts'

export function LoginPage() {
  const brand = useBrand()
  const { notice } = useAuth()
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function onFinish(values: { username: string; password: string }) {
    setSubmitting(true)
    setError(null)
    try {
      await login(values.username, values.password)
    } catch (err) {
      setError(formatProblem(toApiError(err).problem))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mes-auth-page">
      <header className="mes-topbar">
        <div className="mes-brand">
          <img className="mes-logo" src={brand.logo} alt="" width={32} height={32} />
          <div className="mes-brand-text">
            <span className="mes-name" title={brand.name}>{brand.name}</span>
            {brand.subtitle ? <span className="mes-brand-sub" title={brand.subtitle}>{brand.subtitle}</span> : null}
          </div>
        </div>
      </header>
      <main className="mes-auth-main">
        <div className="mes-auth-card">
          <div className="mes-auth-heading">登录</div>
          {notice ? <Alert type="success" showIcon message={notice} style={{ marginBottom: 16 }} /> : null}
          {error ? <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} /> : null}
          <Form layout="vertical" requiredMark={false} onFinish={(values) => { void onFinish(values) }}>
            <Form.Item label="用户名" name="username" rules={[{ required: true, message: '请输入用户名' }]}>
              <Input prefix={<UserOutlined />} autoComplete="username" placeholder="用户名" />
            </Form.Item>
            <Form.Item label="密码" name="password" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password prefix={<LockOutlined />} autoComplete="current-password" placeholder="密码" />
            </Form.Item>
            <Button type="primary" htmlType="submit" block loading={submitting}>
              登录
            </Button>
          </Form>
        </div>
      </main>
    </div>
  )
}
