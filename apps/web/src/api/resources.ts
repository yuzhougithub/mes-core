import { apiRequest } from './http.ts'
import type {
  Material,
  MaterialCreateBody,
  MaterialUpdateBody,
  PageResult,
  Plant,
  PlantExtPutBody,
  PlantExtView,
  SchemaView,
  Uom,
} from './types.ts'

export function listPlants(q?: string) {
  return apiRequest<PageResult<Plant>>('/api/v1/plants', {
    plant: false,
    query: { page: 1, size: 200, enabled: true, sort: 'code,asc', q },
  })
}

export function listUoms(query: { q?: string; enabled?: boolean; size?: number }) {
  return apiRequest<PageResult<Uom>>('/api/v1/uoms', {
    query: {
      page: 1,
      size: query.size ?? 200,
      enabled: query.enabled,
      sort: 'code,asc',
      q: query.q,
    },
  })
}

export function getUom(id: string) {
  return apiRequest<Uom>(`/api/v1/uoms/${id}`)
}

export function listMaterials(query: Record<string, string | number | undefined>) {
  return apiRequest<PageResult<Material>>('/api/v1/materials', { query })
}

export function getMaterial(id: string) {
  return apiRequest<Material>(`/api/v1/materials/${id}`)
}

export function createMaterial(body: MaterialCreateBody) {
  return apiRequest<Material>('/api/v1/materials', { method: 'POST', body })
}

export function updateMaterial(id: string, body: MaterialUpdateBody) {
  return apiRequest<Material>(`/api/v1/materials/${id}`, { method: 'PUT', body })
}

export function deleteMaterial(id: string, lockVersion: number) {
  return apiRequest<void>(`/api/v1/materials/${id}`, {
    method: 'DELETE',
    query: { lockVersion },
  })
}

export function putPlantExt(id: string, body: PlantExtPutBody) {
  return apiRequest<PlantExtView>(`/api/v1/materials/${id}/plant-ext`, {
    method: 'PUT',
    body,
  })
}

export function getMaterialSchema() {
  return apiRequest<SchemaView>('/api/v1/custom-field-schemas/material')
}
