import { UserOutlined } from '@ant-design/icons'
import { Avatar, Button, Dropdown, Modal } from 'antd'
import { useState } from 'react'
import { ChangePasswordForm } from '../auth/ChangePasswordPage.tsx'
import { logout } from '../auth/store.ts'
import { useAuth } from '../auth/useAuth.ts'

export function UserAccount() {
  const auth = useAuth()
  const [open, setOpen] = useState(false)
  const name = auth.me?.person?.name || auth.me?.user.username || '当前用户'
  return (
    <>
      <Dropdown
        menu={{
          items: [
            { key: 'password', label: '修改密码' },
            { key: 'logout', label: '退出登录' },
          ],
          onClick: ({ key }) => {
            if (key === 'password') setOpen(true)
            if (key === 'logout') void logout()
          },
        }}
      >
        <Button type="text" className="mes-user-btn" aria-label="当前用户">
          <Avatar size="small" icon={<UserOutlined />} />
          <span>{name}</span>
        </Button>
      </Dropdown>
      <Modal
        open={open}
        title="修改密码"
        footer={null}
        destroyOnHidden
        onCancel={() => setOpen(false)}
      >
        <ChangePasswordForm onDone={() => setOpen(false)} />
      </Modal>
    </>
  )
}
