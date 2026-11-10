export type PageResult<T> = {
  items: T[]
  page: number
  size: number
  total: number
}

export type Plant = {
  id: string
  code: string
  name: string
  timezone: string
  enabled: boolean
  remark?: string | null
  lockVersion: number
  createdAt?: string
  updatedAt?: string
}

export type MaterialStatus = 'DRAFT' | 'RELEASED' | 'OBSOLETE'
export type MaterialScope = 'SHARED' | 'PLANT'

export type PlantExtEmbedded = {
  enabled: boolean
  values: Record<string, unknown>
  lockVersion: number
}

export type Material = {
  id: string
  scope: MaterialScope
  plantId?: string | null
  code: string
  name: string
  spec?: string | null
  materialType: string
  categoryId?: string | null
  uomId: string
  drawingNo?: string | null
  status: MaterialStatus
  releasedAt?: string | null
  remark?: string | null
  lockVersion: number
  plantExt?: PlantExtEmbedded | null
  createdAt?: string
  updatedAt?: string
}

export type MaterialCreateBody = {
  scope: MaterialScope
  code: string
  name: string
  spec: string | null
  materialType: string
  uomId: string
  drawingNo: string | null
  remark: string | null
}

export type MaterialUpdateBody = {
  name: string
  spec: string | null
  materialType: string
  categoryId: string | null
  uomId: string
  drawingNo: string | null
  remark: string | null
  lockVersion: number
}

export type PlantExtPutBody = {
  enabled: boolean
  values: Record<string, unknown>
  lockVersion: number
}

export type PlantExtView = {
  materialId: string
  plantId: string
  enabled: boolean
  values: Record<string, unknown>
  lockVersion: number
}

export type Uom = {
  id: string
  code: string
  name: string
  decimals: number
  enabled: boolean
  lockVersion: number
}

export type SchemaView = {
  entityType: string
  plantId: string
  schema: unknown
  lockVersion: number
  updatedAt?: string | null
}
