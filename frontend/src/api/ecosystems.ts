import { http, unwrap } from '@/api/http'
import type { Ecosystem, PageResponse } from '@/types/gsmv'

export function fetchEcosystems(params: { keyword?: string; type?: string; page: number; size: number }) {
  return unwrap<PageResponse<Ecosystem>>(http.get('/v1/ecosystems', { params }))
}

export function fetchAllEcosystems() {
  return unwrap<Ecosystem[]>(http.get('/v1/ecosystems/all'))
}

export function createEcosystem(payload: Record<string, unknown>) {
  return unwrap<Ecosystem>(http.post('/v1/ecosystems', payload))
}

export function updateEcosystem(id: number, payload: Record<string, unknown>) {
  return unwrap<Ecosystem>(http.put(`/v1/ecosystems/${id}`, payload))
}

export function deleteEcosystem(id: number) {
  return unwrap<void>(http.delete(`/v1/ecosystems/${id}`))
}
