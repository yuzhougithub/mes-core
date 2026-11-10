export const NO_PLANT_ID = '00000000-0000-0000-0000-000000000000'

export type AuthPlant = {
  id: string
  code: string
  name: string
}

export type TokenResponse = {
  accessToken: string
  expiresIn: number
  mustChangePassword: boolean
  plant: AuthPlant | null
  roles: string[]
}

export type MeUser = {
  id: string | null
  username: string | null
  status: string | null
  superAdmin: boolean
  mustChangePassword: boolean
  permVersion: number | null
}

export type MePerson = {
  id: string
  code: string | null
  name: string | null
}

export type MePlant = {
  id: string
  code: string | null
  name: string | null
}

export type PlantOption = {
  id: string
  code: string | null
  name: string | null
  isDefault: boolean
}

export type MeResponse = {
  user: MeUser
  person: MePerson | null
  plant: MePlant | null
  plants: PlantOption[]
  roles: string[]
  permissions: string[]
  menus: unknown[]
}

export type AuthPhase = 'booting' | 'anonymous' | 'must-change' | 'no-plant' | 'ready'

export type AuthSnapshot = {
  phase: AuthPhase
  token: string | null
  me: MeResponse | null
  notice: string | null
}
