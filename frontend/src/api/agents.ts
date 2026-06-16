import { http, unwrap } from '@/api/http'
import type { AiAgentRunReplayView, AiAgentRunView, PageResponse } from '@/types/gsmv'

export function fetchAgentRuns(params: {
  workflowType?: string
  status?: string
  verificationStatus?: string
  keyword?: string
  page: number
  size: number
}) {
  return unwrap<PageResponse<AiAgentRunView>>(http.get('/v1/agents/runs', { params }))
}

export function fetchAgentRunDetail(id: number) {
  return unwrap<AiAgentRunView>(http.get(`/v1/agents/runs/${id}`))
}

export function fetchAgentRunReplay(id: number) {
  return unwrap<AiAgentRunReplayView>(http.get(`/v1/agents/runs/${id}/replay`))
}

export function runKnowledgeGovernance(payload: { prompt?: string; documentId?: number }) {
  return unwrap<AiAgentRunView>(http.post('/v1/agents/runs/knowledge-governance', payload))
}
