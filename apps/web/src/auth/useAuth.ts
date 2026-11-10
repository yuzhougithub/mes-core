import { useSyncExternalStore } from 'react'
import { getAuthSnapshot, subscribeAuth } from './store.ts'

export function useAuth() {
  return useSyncExternalStore(subscribeAuth, getAuthSnapshot, getAuthSnapshot)
}
