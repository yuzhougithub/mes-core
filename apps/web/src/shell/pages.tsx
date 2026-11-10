import { MaterialListPage } from '../materials/MaterialListPage.tsx'
import { MenuPage } from '../menus/MenuPage.tsx'
import { RolePage } from '../roles/RolePage.tsx'
import { UserListPage } from '../users/UserListPage.tsx'
import { Placeholder } from './Placeholder.tsx'

const PAGES = {
  'md/material': MaterialListPage,
  'sys/user': UserListPage,
  'sys/menu': MenuPage,
  'sys/role': RolePage,
} as const

export function PageOutlet({ component }: { component: string | null }) {
  if (!component) return <Placeholder message="页面不存在" />
  const Page = PAGES[component as keyof typeof PAGES]
  if (!Page) return <Placeholder message="页面不存在" />
  return <Page />
}
