import { describe, expect, it } from 'vitest'
import { plantStoppedMeta, statusMeta } from './constants.ts'

describe('material status labels', () => {
  it('maps lifecycle status without calling obsolete 停用', () => {
    expect(statusMeta('DRAFT')).toEqual({ label: '草稿', tone: 'draft' })
    expect(statusMeta('RELEASED')).toEqual({ label: '已发布', tone: 'released' })
    expect(statusMeta('OBSOLETE')).toEqual({ label: '已作废', tone: 'obsolete' })
    expect(statusMeta('OBSOLETE').label).not.toBe('停用')
  })

  it('uses 停用 only for a plant extension that is disabled', () => {
    expect(plantStoppedMeta(true)).toBeNull()
    expect(plantStoppedMeta(false)).toEqual({ label: '停用', tone: 'stopped' })
  })
})
