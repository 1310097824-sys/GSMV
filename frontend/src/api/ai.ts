import { http, unwrap } from '@/api/http'
import type {
  AiAssistantChatResponse,
  AiAssistantMessage,
  AiIdentifyImageResponse,
  AiObservationAnalysisResponse,
  AiObservationQualityResponse,
  AiObservationSpeciesItem,
  AiPolishTextResponse,
  AiSpeciesAutocompleteResponse,
  AiTranslateSpeciesResponse,
  ObservationEnvironment,
} from '@/types/gsmv'

const AI_REQUEST_TIMEOUT = 90000

export async function identifySpeciesByImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return unwrap<AiIdentifyImageResponse>(
    http.post('/v1/ai/species/identify', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      timeout: AI_REQUEST_TIMEOUT,
    }),
  )
}

export function autocompleteSpeciesProfile(payload: {
  chineseName?: string
  scientificName?: string
  description?: string
  morphology?: string
  habit?: string
  habitat?: string
  distribution?: string
  geoRangeText?: string
}) {
  return unwrap<AiSpeciesAutocompleteResponse>(
    http.post('/v1/ai/species/autocomplete', payload, { timeout: AI_REQUEST_TIMEOUT }),
  )
}

export function polishSpeciesText(payload: {
  fieldName: string
  text: string
}) {
  return unwrap<AiPolishTextResponse>(http.post('/v1/ai/species/polish', payload, { timeout: AI_REQUEST_TIMEOUT }))
}

export function translateSpeciesProfile(payload: {
  chineseName?: string
  scientificName?: string
  description?: string
  morphology?: string
  habit?: string
  habitat?: string
  distribution?: string
  geoRangeText?: string
  targetLanguage: string
}) {
  return unwrap<AiTranslateSpeciesResponse>(
    http.post('/v1/ai/species/translate', payload, { timeout: AI_REQUEST_TIMEOUT }),
  )
}

export function analyzeObservationWithAi(payload: {
  ecosystemId?: number
  ecosystemName: string
  observedAt: string
  locationLat: number
  locationLng: number
  locationName?: string
  note?: string
  environment: ObservationEnvironment
  speciesItems: AiObservationSpeciesItem[]
}) {
  return unwrap<AiObservationAnalysisResponse>(
    http.post('/v1/ai/observations/analyze', payload, { timeout: AI_REQUEST_TIMEOUT }),
  )
}

export function qualityCheckObservationWithAi(id: number) {
  return unwrap<AiObservationQualityResponse>(
    http.post(`/v1/ai/observations/${id}/quality-check`, undefined, { timeout: AI_REQUEST_TIMEOUT }),
  )
}

export function askAiAssistant(payload: {
  message: string
  history?: AiAssistantMessage[]
}) {
  return unwrap<AiAssistantChatResponse>(http.post('/v1/ai/assistant/chat', payload, { timeout: 60000 }))
}
