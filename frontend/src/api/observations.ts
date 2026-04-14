import { http, unwrap } from '@/api/http'
import type { EntityVersionView, ObservationDetailView, ObservationView, PageResponse } from '@/types/gsmv'

export function fetchObservations(params: {
  ecosystemId?: number
  keyword?: string
  observedFrom?: string
  observedTo?: string
  page: number
  size: number
}) {
  return unwrap<PageResponse<ObservationView>>(http.get('/v1/observations', { params }))
}

export function fetchObservationDetail(id: number) {
  return unwrap<ObservationDetailView>(http.get(`/v1/observations/${id}`))
}

export function fetchObservationVersions(id: number) {
  return unwrap<EntityVersionView[]>(http.get(`/v1/observations/${id}/versions`))
}

export function createObservation(payload: Record<string, unknown>) {
  return unwrap<ObservationDetailView>(http.post('/v1/observations', payload))
}

export function updateObservation(id: number, payload: Record<string, unknown>) {
  return unwrap<ObservationDetailView>(http.put(`/v1/observations/${id}`, payload))
}

export function rollbackObservationVersion(id: number, versionId: number) {
  return unwrap<ObservationDetailView>(http.post(`/v1/observations/${id}/versions/${versionId}/rollback`))
}

export function deleteObservation(id: number) {
  return unwrap<void>(http.delete(`/v1/observations/${id}`))
}
