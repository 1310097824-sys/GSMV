import { http, unwrap } from '@/api/http'
import type {
  PageResponse,
  QdrantStatusView,
  RagDocumentDetailView,
  RagDocumentView,
  RagIndexJobView,
  RagIngestItemView,
  RagIngestJobView,
  RagSourceView,
  RagSearchResultView,
} from '@/types/gsmv'

const RAG_TIMEOUT = 90000

export function fetchRagDocuments(params: {
  keyword?: string
  sourceType?: string
  status?: string
  page: number
  size: number
}) {
  return unwrap<PageResponse<RagDocumentView>>(http.get('/v1/ai/rag/documents', { params }))
}

export function fetchRagDocumentDetail(id: number) {
  return unwrap<RagDocumentDetailView>(http.get(`/v1/ai/rag/documents/${id}`))
}

export function uploadRagDocument(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return unwrap<RagDocumentDetailView>(
    http.post('/v1/ai/rag/documents/upload', formData, {
      timeout: RAG_TIMEOUT,
      headers: { 'Content-Type': 'multipart/form-data' },
    }),
  )
}

export function ingestRagFiles(files: File[]) {
  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))
  return unwrap<RagIngestJobView>(
    http.post('/v1/ai/rag/ingest/files', formData, {
      timeout: RAG_TIMEOUT,
      headers: { 'Content-Type': 'multipart/form-data' },
    }),
  )
}

export function ingestRagFolder(payload: { path: string; recursive?: boolean }) {
  return unwrap<RagIngestJobView>(http.post('/v1/ai/rag/ingest/folder', payload, { timeout: RAG_TIMEOUT }))
}

export function ingestRagExternal(payload: { sourceCode: string; query?: string; limit?: number; urls?: string[] }) {
  return unwrap<RagIngestJobView>(http.post('/v1/ai/rag/ingest/external', payload, { timeout: RAG_TIMEOUT }))
}

export function deleteRagDocument(id: number) {
  return unwrap<void>(http.delete(`/v1/ai/rag/documents/${id}`))
}

export function rebuildRagIndex() {
  return unwrap<RagIndexJobView>(http.post('/v1/ai/rag/rebuild', undefined, { timeout: RAG_TIMEOUT }))
}

export function fetchRagJobs(params: { page: number; size: number }) {
  return unwrap<PageResponse<RagIndexJobView>>(http.get('/v1/ai/rag/jobs', { params }))
}

export function fetchRagIngestJobs(params: { page: number; size: number }) {
  return unwrap<PageResponse<RagIngestJobView>>(http.get('/v1/ai/rag/ingest/jobs', { params }))
}

export function fetchRagIngestItems(jobId: number) {
  return unwrap<RagIngestItemView[]>(http.get(`/v1/ai/rag/ingest/jobs/${jobId}/items`))
}

export function retryRagIngestJob(jobId: number) {
  return unwrap<RagIngestJobView>(http.post(`/v1/ai/rag/ingest/jobs/${jobId}/retry`, undefined, { timeout: RAG_TIMEOUT }))
}

export function fetchRagSources() {
  return unwrap<RagSourceView[]>(http.get('/v1/ai/rag/sources'))
}

export function fetchQdrantStatus() {
  return unwrap<QdrantStatusView>(http.get('/v1/ai/rag/qdrant/status'))
}

export function rebuildQdrantIndex() {
  return unwrap<QdrantStatusView>(http.post('/v1/ai/rag/qdrant/rebuild', undefined, { timeout: RAG_TIMEOUT }))
}

export function testRagSearch(payload: { query: string; limit?: number }) {
  return unwrap<RagSearchResultView[]>(
    http.post('/v1/ai/rag/search-test', payload, { timeout: RAG_TIMEOUT }),
  )
}
