import { useAuth } from '../auth/useAuth.ts'

/**
 * True only when `/auth/me` issued this exact permission code.
 * `global` writes also require `me.user.superAdmin`, even if the code is present.
 */
export function usePerm(code: string, global = false): boolean {
  const auth = useAuth()
  if (auth.me?.permissions.includes(code) !== true) return false
  if (global && auth.me.user.superAdmin !== true) return false
  return true
}
