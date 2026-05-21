import { http, unwrap } from '@/api/http'
import type { AiIdentifyImageResponse, AiReviewTicketDetailView, AiReviewTicketView, PageResponse } from '@/types/gsmv'

export async function createAiReviewTicket(payload: {
  likelyChineseName?: string
  likelyScientificName?: string
  confidence: number
  needsHumanReview: boolean
  reasoning?: string
  candidates: AiIdentifyImageResponse['candidates']
  relatedSpeciesRecords: AiIdentifyImageResponse['relatedSpeciesRecords']
  ragEvidence?: AiIdentifyImageResponse['ragEvidence']
  ragConclusion?: string
  conflictWarnings?: string[]
  submitNote?: string
}, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append(
    'payload',
    new Blob([JSON.stringify(payload)], {
      type: 'application/json',
    }),
  )
  return unwrap<AiReviewTicketDetailView>(
    http.post('/v1/ai/review-tickets', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    }),
  )
}

export function fetchAiReviewTickets(params: {
  keyword?: string
  status?: string
  page: number
  size: number
}) {
  return unwrap<PageResponse<AiReviewTicketView>>(http.get('/v1/ai/review-tickets', { params }))
}

export function fetchAiReviewTicketDetail(id: number) {
  return unwrap<AiReviewTicketDetailView>(http.get(`/v1/ai/review-tickets/${id}`))
}

export function startAiReviewTicket(id: number) {
  return unwrap<AiReviewTicketDetailView>(http.post(`/v1/ai/review-tickets/${id}/start-review`))
}

export function resolveAiReviewTicket(id: number, payload: {
  resolutionCode: string
  finalSpeciesId?: number
  finalChineseName?: string
  finalScientificName?: string
  reviewNote: string
}) {
  return unwrap<AiReviewTicketDetailView>(http.post(`/v1/ai/review-tickets/${id}/resolve`, payload))
}

export function rejectAiReviewTicket(id: number, payload: { reviewNote: string }) {
  return unwrap<AiReviewTicketDetailView>(http.post(`/v1/ai/review-tickets/${id}/reject`, payload))
}

export function resubmitAiReviewTicket(id: number, payload: { submitNote?: string }) {
  return unwrap<AiReviewTicketDetailView>(http.post(`/v1/ai/review-tickets/${id}/resubmit`, payload))
}

export function linkAiReviewTicketSpecies(id: number, payload: { finalSpeciesId: number; reviewNote: string }) {
  return unwrap<AiReviewTicketDetailView>(http.post(`/v1/ai/review-tickets/${id}/link-species`, payload))
}

export async function fetchAiReviewImageBlob(mediaId: number) {
  const response = await http.get(`/v1/ai/review-tickets/images/${mediaId}`, {
    responseType: 'blob',
  })
  return response.data as Blob
}
