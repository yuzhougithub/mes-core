import { getMaterial, putPlantExt } from '../api/resources.ts'
import { StalePlantResponse, isAuthRedirect, isStale, toApiError } from '../api/problem.ts'
import { getPlantEpoch, getPlantId } from '../api/plant-session.ts'
import type { MaterialStatus } from '../api/types.ts'

export type BatchFailure = {
  id: string
  code: string
  message: string
}

export type BatchEnableResult = {
  updated: number
  skipped: number
  failures: BatchFailure[]
}

/**
 * There is no batch endpoint. Each id is read, then `PUT .../plant-ext`.
 * A factory switch stops the loop before the next call and does not retarget it.
 */
export async function setMaterialsEnabled(
  ids: string[],
  enabled: boolean,
): Promise<BatchEnableResult> {
  const plantId = getPlantId()
  const epoch = getPlantEpoch()
  if (!plantId) {
    throw toApiError(new Error('请先选择工厂'))
  }
  const result: BatchEnableResult = { updated: 0, skipped: 0, failures: [] }
  for (const id of ids) {
    if (getPlantEpoch() !== epoch || getPlantId() !== plantId) {
      throw new StalePlantResponse()
    }
    try {
      const detail = await getMaterial(id)
      if (getPlantEpoch() !== epoch || getPlantId() !== plantId) throw new StalePlantResponse()
      const status = detail.status as MaterialStatus
      if (status === 'OBSOLETE') {
        result.failures.push({
          id,
          code: 'STATE_CONFLICT',
          message: '物料已作废，本厂扩展只读',
        })
        continue
      }
      const ext = detail.plantExt ?? { enabled: true, values: {}, lockVersion: 0 }
      if (ext.enabled === enabled) {
        result.skipped += 1
        continue
      }
      await putPlantExt(id, {
        enabled,
        values: ext.values ?? {},
        lockVersion: ext.lockVersion ?? 0,
      })
      if (getPlantEpoch() !== epoch || getPlantId() !== plantId) throw new StalePlantResponse()
      result.updated += 1
    } catch (err) {
      if (isStale(err) || isAuthRedirect(err)) throw new StalePlantResponse()
      const problem = toApiError(err).problem
      result.failures.push({
        id,
        code: problem.code,
        message: problem.detail || problem.title,
      })
    }
  }
  return result
}
