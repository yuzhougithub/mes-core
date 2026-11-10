import type { MaterialStatus } from '../api/types.ts'

/**
 * Lifecycle values enforced by `mat_material.status`.
 * This is not the per-plant `enabled` flag.
 */
export const MATERIAL_STATUSES: { value: MaterialStatus; label: string }[] = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'RELEASED', label: '已发布' },
  { value: 'OBSOLETE', label: '已作废' },
]

/**
 * Seeded by V2__master_data.sql into `material_type`.
 * `GET /api/v1/dict-types/{code}/items` is not implemented, so the screen cannot
 * discover items added later. Saves are still checked by the API.
 */
export const MATERIAL_TYPES: { value: string; label: string }[] = [
  { value: 'RAW', label: '原材料' },
  { value: 'SEMI', label: '半成品' },
  { value: 'FINISHED', label: '成品' },
  { value: 'AUX', label: '辅料' },
]

export function materialTypeLabel(value: string): string {
  return MATERIAL_TYPES.find((item) => item.value === value)?.label ?? value
}

export function statusMeta(status: string): { label: string; tone: 'draft' | 'released' | 'obsolete' } {
  switch (status) {
    case 'DRAFT':
      return { label: '草稿', tone: 'draft' }
    case 'RELEASED':
      return { label: '已发布', tone: 'released' }
    case 'OBSOLETE':
      return { label: '已作废', tone: 'obsolete' }
    default:
      return { label: status, tone: 'obsolete' }
  }
}

/** Plant extension `enabled`. This is not the lifecycle status above. */
export function plantStoppedMeta(enabled: boolean): { label: string; tone: 'stopped' } | null {
  if (enabled) return null
  return { label: '停用', tone: 'stopped' }
}
