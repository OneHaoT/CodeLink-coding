import { http } from './request'
import type { Overview, TrendPoint } from './types'

export function getOverview() {
  return http.get<Overview>('/admin/statistics/overview')
}

export function getTrends(days?: number) {
  return http.get<TrendPoint[]>('/admin/statistics/trends', {
    params: days ? { days } : undefined,
  })
}
