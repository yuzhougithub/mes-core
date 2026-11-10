import { useAuth } from '../auth/useAuth.ts'

/** True only when `/auth/me` issued this exact permission code. */
export function usePerm(code: string): boolean {
  const auth = useAuth()
  return auth.me?.permissions.includes(code) === true
}
