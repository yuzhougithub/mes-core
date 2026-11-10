import type { ReactNode } from 'react'
import { useAuth } from '../auth/useAuth.ts'

/** Renders children only when `/auth/me` issued this exact permission code. */
export function Perm({ code, children }: { code: string; children: ReactNode }) {
  const auth = useAuth()
  const permissions = auth.me?.permissions
  if (!permissions || !permissions.includes(code)) return null
  return children
}
