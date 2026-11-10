import { useEffect } from 'react'
import { PlantProvider } from '../plant/PlantProvider.tsx'
import { AdminShell } from '../shell/AdminShell.tsx'
import { BootstrapPage } from '../bootstrap/BootstrapPage.tsx'
import { ChangePasswordPage } from './ChangePasswordPage.tsx'
import { LoginPage } from './LoginPage.tsx'
import { getAuthSnapshot, restoreSession } from './store.ts'
import { useAuth } from './useAuth.ts'

export function AuthGate() {
  const auth = useAuth()

  useEffect(() => {
    if (getAuthSnapshot().phase !== 'booting') return
    let cancel = false
    void restoreSession(() => cancel)
    return () => {
      cancel = true
    }
  }, [])

  if (auth.phase === 'booting') {
    return (
      <div className="mes-auth-page">
        <main className="mes-auth-main">
          <div className="mes-auth-card">正在恢复登录状态</div>
        </main>
      </div>
    )
  }
  if (auth.phase === 'anonymous') return <LoginPage />
  if (auth.phase === 'must-change') return <ChangePasswordPage />
  return (
    <PlantProvider>
      {auth.phase === 'no-plant' ? <BootstrapPage /> : <AdminShell />}
    </PlantProvider>
  )
}
