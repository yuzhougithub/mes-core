import { describe, expect, it } from 'vitest'
import { extPayload, planMaterialSave } from './save-plan.ts'
import { materialSort, pageSummary, resolvePage, toMaterialParams } from './query.ts'
import type { FieldDef } from '../schema/types.ts'

const rohs: FieldDef = { key: 'rohs', type: 'boolean', title: 'RoHS', required: true }
const drawing: FieldDef = { key: 'drawing', type: 'file', title: '图纸', required: true }
const note: FieldDef = { key: 'note', type: 'string', title: '备注' }

describe('toMaterialParams', () => {
  it('sends only the filters the materials API implements', () => {
    const params = toMaterialParams({
      page: 2,
      size: 50,
      q: ' 外壳 ',
      status: 'RELEASED',
      sort: 'updatedAt,desc',
    })
    expect(params).toEqual({
      page: 2,
      size: 50,
      q: '外壳',
      status: 'RELEASED',
      sort: 'updatedAt,desc',
    })
    expect(params).not.toHaveProperty('categoryId')
    expect(params).not.toHaveProperty('createdFrom')
    expect(params).not.toHaveProperty('createdTo')
    expect(params).not.toHaveProperty('enabledInPlant')
  })

  it('omits a blank keyword', () => {
    expect(toMaterialParams({ page: 1, size: 20, q: '  ', status: '', sort: 'code,asc' }).q).toBeUndefined()
  })
})

describe('pageSummary', () => {
  it('formats the pager summary with the selection count', () => {
    expect(pageSummary(1248, 2)).toBe('共 1248 条记录，已选 2 项')
  })
})

describe('resolvePage', () => {
  it('steps back when the last row of the last page is gone', () => {
    expect(resolvePage(2, 10, 10)).toBe(1)
    expect(resolvePage(3, 10, 11)).toBe(2)
  })

  it('keeps a valid page after a sort that does not shrink the list', () => {
    expect(resolvePage(2, 10, 25)).toBe(2)
    expect(resolvePage(1, 10, 0)).toBe(1)
  })
})

describe('materialSort', () => {
  it('rejects fields outside the API whitelist', () => {
    expect(materialSort('spec', 'ascend')).toBe('code,asc')
    expect(materialSort('updatedAt', 'descend')).toBe('updatedAt,desc')
  })
})

describe('extPayload', () => {
  it('keeps required boolean false and drops empty optionals', () => {
    expect(extPayload([rohs, note], { rohs: false, note: '' })).toEqual({ rohs: false })
  })
})

describe('planMaterialSave', () => {
  const header = {
    scope: 'PLANT' as const,
    code: 'MAT-1',
    name: '外壳',
    spec: '',
    materialType: 'SEMI',
    uomId: 'uom-1',
    drawingNo: ' ',
    remark: '',
  }

  it('does not send categoryId on create and still sends boolean false', () => {
    const plan = planMaterialSave({
      mode: 'create',
      header,
      categoryId: null,
      lockVersion: 0,
      fields: [rohs],
      extValues: { rohs: false },
      extEnabled: true,
      extLockVersion: 0,
    })
    expect(plan.kind).toBe('create')
    if (plan.kind !== 'create') return
    expect(plan.header).not.toHaveProperty('categoryId')
    expect(plan.header.spec).toBeNull()
    expect(plan.header.drawingNo).toBeNull()
    expect(plan.ext).toEqual({ enabled: true, values: { rohs: false }, lockVersion: 0 })
  })

  it('blocks create when a required file cannot be uploaded', () => {
    const plan = planMaterialSave({
      mode: 'create',
      header,
      categoryId: null,
      lockVersion: 0,
      fields: [drawing],
      extValues: {},
      extEnabled: true,
      extLockVersion: 0,
    })
    expect(plan.kind).toBe('blocked')
    if (plan.kind !== 'blocked') return
    expect(plan.reason).toContain('即将上线')
  })

  it('keeps categoryId when updating a draft and skips the header after release', () => {
    const draft = planMaterialSave({
      mode: 'edit',
      status: 'DRAFT',
      header,
      categoryId: 'cat-1',
      lockVersion: 3,
      fields: [],
      extValues: {},
      extEnabled: true,
      extLockVersion: 1,
    })
    expect(draft.kind).toBe('update')
    if (draft.kind !== 'update') return
    expect(draft.header?.categoryId).toBe('cat-1')
    expect(draft.header?.lockVersion).toBe(3)
    expect(draft.header).not.toHaveProperty('code')
    expect(draft.ext).toBeNull()

    const released = planMaterialSave({
      mode: 'edit',
      status: 'RELEASED',
      header,
      categoryId: 'cat-1',
      lockVersion: 3,
      fields: [rohs],
      extValues: { rohs: false },
      extEnabled: false,
      extLockVersion: 4,
    })
    expect(released.kind).toBe('update')
    if (released.kind !== 'update') return
    expect(released.header).toBeNull()
    expect(released.ext).toEqual({ enabled: false, values: { rohs: false }, lockVersion: 4 })
  })

  it('blocks obsolete materials', () => {
    const plan = planMaterialSave({
      mode: 'edit',
      status: 'OBSOLETE',
      header,
      categoryId: null,
      lockVersion: 1,
      fields: [rohs],
      extValues: { rohs: true },
      extEnabled: true,
      extLockVersion: 1,
    })
    expect(plan.kind).toBe('blocked')
  })
})
