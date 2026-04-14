import { http, unwrap } from '@/api/http'
import type { EntityVersionView, PageResponse, SpeciesDetailView, SpeciesImageView, SpeciesView, TaxonOption } from '@/types/gsmv'

export function fetchSpecies(params: {
  keyword?: string
  status?: number
  protectionLevel?: string
  iucnStatus?: string
  distributionKeyword?: string
  taxonId?: number
  page: number
  size: number
}) {
  return unwrap<PageResponse<SpeciesView>>(http.get('/v1/species', { params }))
}

export function fetchSpeciesDetail(id: number) {
  return unwrap<SpeciesDetailView>(http.get(`/v1/species/${id}`))
}

export function fetchSpeciesVersions(id: number) {
  return unwrap<EntityVersionView[]>(http.get(`/v1/species/${id}/versions`))
}

export function fetchTaxa() {
  return unwrap<TaxonOption[]>(http.get('/v1/species/taxa'))
}

export function createSpecies(payload: Record<string, unknown>) {
  return unwrap<SpeciesDetailView>(http.post('/v1/species', payload))
}

export function updateSpecies(id: number, payload: Record<string, unknown>) {
  return unwrap<SpeciesDetailView>(http.put(`/v1/species/${id}`, payload))
}

export function rollbackSpeciesVersion(id: number, versionId: number) {
  return unwrap<SpeciesDetailView>(http.post(`/v1/species/${id}/versions/${versionId}/rollback`))
}

export function deleteSpecies(id: number) {
  return unwrap<void>(http.delete(`/v1/species/${id}`))
}

export async function uploadSpeciesImage(id: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return unwrap<SpeciesImageView>(
    http.post(`/v1/species/${id}/images`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    }),
  )
}
