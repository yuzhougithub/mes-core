import type { ReactNode } from 'react'
import { usePerm } from './usePerm.ts'

/** Renders children only when `/auth/me` allows `code`. `global` also requires a super admin. */
export function Perm({
  code,
  global = false,
  children,
}: {
  code: string
  global?: boolean
  children: ReactNode
}) {
  if (!usePerm(code, global)) return null
  return children
}
