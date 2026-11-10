import { Button, Form, Input } from 'antd'
import { useState } from 'react'
import { apiRequest } from '../api/http.ts'
import { formatProblem, toApiError, type ApiError } from '../api/problem.ts'
import { useBrand } from '../brand/useBrand.ts'
import { passwordProblems } from './password.ts'
import { finishPasswordChange, getPendingUsername, logout } from './store.ts'
import { useAuth } from './useAuth.ts'

function useAuthUsername(): string {
  const auth = useAuth()
  return auth.me?.user.username || getPendingUsername()
}

export function ChangePasswordForm({ onDone }: { onDone?: () => void }) {
  const username = useAuthUsername()
  const [form] = Form.useForm<{ oldPassword: string; newPassword: string; confirm: string }>()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function onFinish(values: { oldPassword: string; newPassword: string; confirm: string }) {
    const problems = passwordProblems(values.newPassword, username)
    if (values.newPassword !== values.confirm) problems.push('两次输入的新密码不一致')
    if (values.newPassword === values.oldPassword) problems.push('新密码不能与当前密码相同')
    if (problems.length) {
      form.setFields([{ name: 'newPassword', errors: problems }])
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      await apiRequest<void>('/api/v1/auth/change-password', {
        method: 'POST',
        body: { oldPassword: values.oldPassword, newPassword: values.newPassword },
        plant: false,
      })
      finishPasswordChange()
      onDone?.()
    } catch (err) {
      const api = toApiError(err)
      applyPasswordErrors(form, api)
      setError(formatProblem(api.problem))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Form form={form} layout="vertical" requiredMark={false} onFinish={(values) => { void onFinish(values) }}>
      {error ? <div className="mes-form-error">{error}</div> : null}
      <Form.Item label="当前密码" name="oldPassword" rules={[{ required: true, message: '请输入当前密码' }]}>
        <Input.Password autoComplete="current-password" />
      </Form.Item>
      <Form.Item
        label="新密码"
        name="newPassword"
        extra="至少 8 位，且包含大小写字母、数字、符号中的至少 3 类，不能包含用户名。"
        rules={[{ required: true, message: '请输入新密码' }]}
      >
        <Input.Password autoComplete="new-password" />
      </Form.Item>
      <Form.Item label="确认新密码" name="confirm" rules={[{ required: true, message: '请再次输入新密码' }]}>
        <Input.Password autoComplete="new-password" />
      </Form.Item>
      <Button type="primary" htmlType="submit" loading={submitting}>
        更新密码
      </Button>
    </Form>
  )
}

function applyPasswordErrors(
  form: ReturnType<typeof Form.useForm<{ oldPassword: string; newPassword: string; confirm: string }>>[0],
  api: ApiError,
) {
  const known = new Set(['oldPassword', 'newPassword', 'password'])
  const fields = api.problem.errors
    .filter((item) => known.has(item.field) && item.message)
    .map((item) => ({
      name: (item.field === 'password' ? 'newPassword' : item.field) as 'oldPassword' | 'newPassword',
      errors: [item.message],
    }))
  if (fields.length) form.setFields(fields)
}

export function ChangePasswordPage() {
  const brand = useBrand()
  return (
    <div className="mes-auth-page">
      <header className="mes-topbar">
        <div className="mes-brand">
          <img className="mes-logo" src={brand.logo} alt="" width={32} height={32} />
          <div className="mes-brand-text">
            <span className="mes-name" title={brand.name}>{brand.name}</span>
          </div>
        </div>
        <div className="mes-tools">
          <Button type="text" onClick={() => { void logout() }}>退出登录</Button>
        </div>
      </header>
      <main className="mes-auth-main">
        <div className="mes-auth-card">
          <div className="mes-auth-heading">请先修改密码</div>
          <p className="mes-auth-lead">当前口令还未更换，更换完成前不能进入其他页面。</p>
          <ChangePasswordForm />
        </div>
      </main>
    </div>
  )
}
