import { http, unwrap } from '@/api/http'
import type { MediaFile } from '@/types/gsmv'

export function fetchMedia(businessType: string, businessId: number) {
  return unwrap<MediaFile[]>(http.get('/v1/media', { params: { businessType, businessId } }))
}

export function uploadMedia(businessType: string, businessId: number, file: File) {
  const formData = new FormData()
  formData.append('businessType', businessType)
  formData.append('businessId', String(businessId))
  formData.append('file', file)
  return unwrap<MediaFile>(http.post('/v1/media/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }))
}
