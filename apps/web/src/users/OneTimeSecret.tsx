import { App, Button, Input, Modal } from 'antd'
import { copyText } from '../auth/copy.ts'

export function OneTimeSecret({
  password,
  onClose,
}: {
  password: string | null
  onClose: () => void
}) {
  const { message } = App.useApp()

  async function copy() {
    if (!password) return
    const ok = await copyText(password)
    if (ok) message.success('已复制')
    else message.error('复制失败，请手动选择口令')
  }

  if (!password) return null

  return (
    <Modal
      open
      title="临时口令"
      destroyOnHidden
      onCancel={onClose}
      footer={[
        <Button key="copy" onClick={() => { void copy() }}>复制</Button>,
        <Button key="close" type="primary" onClick={onClose}>知道了</Button>,
      ]}
    >
      <p className="mes-secret-lead">请立即复制。关闭后不能再查看。</p>
      <Input readOnly aria-label="临时口令" value={password} />
    </Modal>
  )
}
