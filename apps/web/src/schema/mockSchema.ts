import type { FormSchema } from './types.ts'

/** Demo schema covering all eight V1 field types. */
export const mockSchema: FormSchema = {
  schemaVersion: 1,
  fields: [
    {
      key: 'part_name',
      type: 'string',
      title: '零件名称',
      required: true,
      validation: { minLength: 2, maxLength: 64 },
      layout: { order: 10, group: '基础信息', span: 12 },
    },
    {
      key: 'qty',
      type: 'number',
      title: '数量',
      required: true,
      validation: { min: 1, max: 9999 },
      layout: { order: 20, group: '基础信息', span: 12 },
    },
    {
      key: 'due_at',
      type: 'datetime',
      title: '要求完成时间',
      required: true,
      layout: { order: 30, group: '基础信息', span: 12 },
    },
    {
      key: 'is_critical',
      type: 'boolean',
      title: '关键件',
      layout: { order: 40, group: '基础信息', span: 12 },
    },
    {
      key: 'surface_finish',
      type: 'enum',
      title: '表面处理',
      required: true,
      options: [
        { value: 'anodize', label: '阳极氧化' },
        { value: 'passivate', label: '钝化' },
        { value: 'paint', label: '喷涂' },
      ],
      layout: { order: 10, group: '质量', span: 12 },
    },
    {
      key: 'inspect_items',
      type: 'multi_enum',
      title: '检验项目',
      required: true,
      options: [
        { value: 'dim', label: '尺寸' },
        { value: 'appearance', label: '外观' },
        { value: 'hardness', label: '硬度' },
      ],
      validation: { minItems: 1, maxItems: 3 },
      layout: { order: 20, group: '质量', span: 12 },
    },
    {
      key: 'photo',
      type: 'image',
      title: '外观照片',
      layout: { order: 10, group: '附件', span: 12 },
    },
    {
      key: 'drawing',
      type: 'file',
      title: '图纸附件',
      layout: { order: 20, group: '附件', span: 12 },
    },
  ],
}
