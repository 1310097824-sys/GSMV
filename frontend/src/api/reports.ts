import { http, unwrap } from '@/api/http'
import type {
  DashboardSummary,
  EcosystemAnalyticsPoint,
  NameValuePoint,
  ObservationMapPoint,
  SpeciesDistributionPoint,
} from '@/types/gsmv'

export function fetchDashboardSummary() {
  return unwrap<DashboardSummary>(http.get('/v1/reports/summary'))
}

export function fetchProtectionLevelDistribution() {
  return unwrap<NameValuePoint[]>(http.get('/v1/reports/protection-level'))
}

export function fetchIucnStatusDistribution() {
  return unwrap<NameValuePoint[]>(http.get('/v1/reports/iucn-status'))
}

export function fetchSpeciesPhylumDistribution() {
  return unwrap<NameValuePoint[]>(http.get('/v1/reports/taxonomy/phylum'))
}

export function fetchSpeciesClassDistribution() {
  return unwrap<NameValuePoint[]>(http.get('/v1/reports/taxonomy/class'))
}

export function fetchObservationTrend(days = 30) {
  return unwrap<NameValuePoint[]>(http.get('/v1/reports/observation-trend', { params: { days } }))
}

export function fetchObservationActivity(days = 30) {
  return unwrap<NameValuePoint[]>(http.get('/v1/reports/observation-activity', { params: { days } }))
}

export function fetchEcosystemAnalytics() {
  return unwrap<EcosystemAnalyticsPoint[]>(http.get('/v1/reports/ecosystem-analytics'))
}

export function fetchSpeciesDistributionPoints() {
  return unwrap<SpeciesDistributionPoint[]>(http.get('/v1/reports/species-distribution'))
}

export function fetchObservationMapPoints() {
  return unwrap<ObservationMapPoint[]>(http.get('/v1/reports/observation-map'))
}

export async function downloadReportExport(format: 'excel' | 'pdf', days = 30) {
  const response = await http.get(`/v1/reports/export/${format}`, {
    params: { days },
    responseType: 'blob',
  })

  return {
    blob: response.data as Blob,
    fileName: readFileName(response.headers['content-disposition']) || `gsmv-report.${format === 'excel' ? 'xlsx' : 'pdf'}`,
  }
}

function readFileName(contentDisposition?: string) {
  if (!contentDisposition) {
    return ''
  }

  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1])
  }

  const plainMatch = contentDisposition.match(/filename="?([^"]+)"?/i)
  return plainMatch?.[1] || ''
}
