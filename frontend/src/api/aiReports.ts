import { http, unwrap } from '@/api/http'
import type { AiReportDetailView, AiReportView, PageResponse } from '@/types/gsmv'

const AI_REPORT_TIMEOUT = 90000

export function generateAiReport(payload: { reportType: string; days: number }) {
  return unwrap<AiReportDetailView>(
    http.post('/v1/ai/reports/generate', payload, { timeout: AI_REPORT_TIMEOUT }),
  )
}

export function fetchAiReports(params: { page: number; size: number }) {
  return unwrap<PageResponse<AiReportView>>(http.get('/v1/ai/reports', { params }))
}

export function fetchAiReportDetail(id: number) {
  return unwrap<AiReportDetailView>(http.get(`/v1/ai/reports/${id}`))
}

export async function downloadAiReportPdf(id: number) {
  const response = await http.get(`/v1/ai/reports/${id}/export/pdf`, {
    responseType: 'blob',
  })
  return {
    blob: response.data as Blob,
    fileName: readFileName(response.headers['content-disposition']) || `gsmv-ai-report-${id}.pdf`,
  }
}

function readFileName(contentDisposition?: string) {
  if (!contentDisposition) {
    return ''
  }
  const plainMatch = contentDisposition.match(/filename="?([^"]+)"?/i)
  return plainMatch?.[1] || ''
}
