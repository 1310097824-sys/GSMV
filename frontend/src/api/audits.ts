import { http, unwrap } from '@/api/http'
import type { AuditLogView, PageResponse } from '@/types/gsmv'

export function fetchAudits(params: { module?: string; success?: number; page: number; size: number }) {
  return unwrap<PageResponse<AuditLogView>>(http.get('/v1/audits', { params }))
}

export function fetchMyActivities(params: { module?: string; success?: number; page: number; size: number }) {
  return unwrap<PageResponse<AuditLogView>>(http.get('/v1/audits/me', { params }))
}
