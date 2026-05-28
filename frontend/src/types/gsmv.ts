export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  traceId: string
  timestamp: string
}

export interface PageResponse<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface AuthProfile {
  id: number
  username: string
  displayName: string
  avatarUrl: string
  roles: string[]
  authorities: string[]
}

export interface LoginResult {
  accessToken: string
  expiresInSeconds: number
  user: AuthProfile
}

export interface RoleOption {
  id: number
  code: string
  name: string
  description: string
}

export interface UserView {
  id: number
  username: string
  displayName: string
  email?: string
  phone?: string
  bio?: string
  avatarUrl: string
  status: number
  approvalStatus: string
  approvalRemark?: string
  reviewedAt?: string
  lastLoginAt?: string
  createdAt: string
  roles: RoleOption[]
}

export interface UserProfileView {
  id: number
  username: string
  displayName: string
  email?: string
  phone?: string
  bio?: string
  avatarUrl: string
  status: number
  approvalStatus: string
  lastLoginAt?: string
  createdAt: string
  roles: RoleOption[]
}

export interface TaxonOption {
  id: number
  parentId?: number
  rank: string
  scientificName: string
  chineseName?: string
}

export interface SpeciesView {
  id: number
  taxonId: number
  rank: string
  scientificName: string
  chineseName?: string
  phylumName?: string
  className?: string
  orderName?: string
  familyName?: string
  genusName?: string
  classificationPath?: string
  protectionLevel?: string
  iucnStatus?: string
  geoRangeText?: string
  status: number
  createdAt: string
  updatedAt: string
}

export interface SpeciesImageView {
  id: number
  url: string
  originalFilename: string
}

export interface SpeciesDetailView extends SpeciesView {
  description?: string
  morphology?: string
  habit?: string
  habitat?: string
  distribution?: string
  distributionLat?: number
  distributionLng?: number
  videoUrl?: string
  referenceText?: string
  images: SpeciesImageView[]
}

export interface Ecosystem {
  id: number
  name: string
  type?: string
  description?: string
  createdAt?: string
}

export interface ObservationEnvironment {
  waterTemperature?: number | null
  salinity?: number | null
  ph?: number | null
  dissolvedOxygen?: number | null
  transparency?: number | null
  depthMeters?: number | null
  weather?: string
  seaState?: string
}

export interface ObservationSpeciesInput {
  speciesId: number | null
  countEstimated?: number | null
  behavior?: string
  comment?: string
}

export interface ObservationSpeciesView {
  speciesId: number
  scientificName: string
  chineseName?: string
  countEstimated?: number
  behavior?: string
  comment?: string
}

export interface ObservationView {
  id: number
  ecosystemId: number
  ecosystemName: string
  observerUserId: number
  observerName: string
  observedAt: string
  locationLat: number
  locationLng: number
  locationName?: string
  envJson?: string
  note?: string
  createdAt: string
}

export interface ObservationDetailView extends ObservationView {
  speciesItems: ObservationSpeciesView[]
}

export interface VersionFieldChangeView {
  fieldKey: string
  fieldLabel: string
  oldValue?: string
  newValue?: string
}

export interface EntityVersionView {
  id: number
  versionNo: number
  action: string
  changedBy?: number
  changedByName?: string
  rollbackSourceVersionId?: number
  rollbackSourceVersionNo?: number
  createdAt: string
  changes: VersionFieldChangeView[]
}

export interface AuditLogView {
  id: number
  userId?: number
  username?: string
  displayName?: string
  module: string
  action: string
  entityType?: string
  entityId?: number
  requestId?: string
  success: number
  detailJson?: string
  createdAt: string
}

export interface DashboardSummary {
  totalSpecies: number
  totalObservations: number
  totalEcosystems: number
  totalUsers: number
  recentObservationCount: number
}

export interface NameValuePoint {
  name: string
  value: number
}

export interface SpeciesDistributionPoint {
  speciesId: number
  scientificName: string
  chineseName?: string
  locationLat: number
  locationLng: number
  geoRangeText?: string
  protectionLevel?: string
  iucnStatus?: string
}

export interface ObservationMapPoint {
  observationId: number
  ecosystemName: string
  observerName: string
  observedAt: string
  locationLat: number
  locationLng: number
  locationName?: string
  speciesCount: number
  note?: string
}

export interface EcosystemAnalyticsPoint {
  ecosystemId: number
  ecosystemName: string
  ecosystemType?: string
  observationCount: number
  speciesCount: number
}

export interface MediaFile {
  id: number
  businessType: string
  businessId: number
  originalFilename: string
  storedFilename: string
  contentType: string
  sizeBytes: number
  storagePath: string
  sha256?: string
  uploadedBy?: number
  uploadedAt: string
}

export interface AiRelatedSpeciesRecord {
  id: number
  chineseName?: string
  scientificName: string
  classificationPath?: string
  protectionLevel?: string
  iucnStatus?: string
}

export interface AiIdentificationCandidate {
  chineseName?: string
  scientificName?: string
  confidence: number
  reason?: string
}

export interface AiIdentifyImageResponse {
  likelyChineseName?: string
  likelyScientificName?: string
  confidence: number
  needsHumanReview: boolean
  confidenceLabel: string
  reasoning?: string
  candidates: AiIdentificationCandidate[]
  relatedSpeciesRecords: AiRelatedSpeciesRecord[]
  ragEvidence: RagEvidenceItem[]
  confidenceAdjustedByRag: boolean
  ragConclusion?: string
  conflictWarnings: string[]
}

export interface AiSpeciesAutocompleteResponse {
  chineseName?: string
  scientificName?: string
  phylumName?: string
  className?: string
  orderName?: string
  familyName?: string
  genusName?: string
  protectionLevel?: string
  iucnStatus?: string
  description?: string
  morphology?: string
  habit?: string
  habitat?: string
  distribution?: string
  geoRangeText?: string
  summary?: string
  confidence: number
  notes: string[]
  relatedSpeciesRecords: AiRelatedSpeciesRecord[]
}

export interface AiPolishTextResponse {
  fieldName: string
  polishedText: string
  summary?: string
  keywords: string[]
}

export interface AiTranslateSpeciesResponse {
  targetLanguage: string
  description?: string
  morphology?: string
  habit?: string
  habitat?: string
  distribution?: string
  geoRangeText?: string
  summary?: string
}

export interface AiObservationSpeciesItem {
  speciesId?: number | null
  scientificName?: string
  chineseName?: string
  countEstimated?: number | null
  behavior?: string
  comment?: string
}

export interface AiObservationAnomaly {
  severity: string
  speciesName: string
  message: string
  suggestion?: string
  distanceKm?: number
}

export interface AiObservationAnalysisResponse {
  summary?: string
  tags: string[]
  reviewNotes: string[]
  anomalies: AiObservationAnomaly[]
  needsReview: boolean
}

export interface AiObservationQualityIssue {
  severity: string
  title: string
  message: string
  suggestion?: string
}

export interface AiObservationQualityResponse {
  observationId: number
  score: number
  grade: string
  summary: string
  strengths: string[]
  issues: AiObservationQualityIssue[]
  needsReview: boolean
}

export interface AiAssistantStructuredQuery {
  intent: string
  locationKeyword?: string
  ecosystemKeyword?: string
  speciesKeyword?: string
  protectionLevel?: string
  iucnStatus?: string
  yearsBack?: number
  recentDays?: number
  includeTrend: boolean
  riskOnly: boolean
  limit?: number
}

export interface AiAssistantEvidenceItem {
  type?: string
  title?: string
  description?: string
  sourceId?: number
  score?: number
  sourcePath?: string
}

export interface AiAssistantMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface AiAssistantHistoryItem extends AiAssistantMessage {
  id: number
  createdAt: string
}

export interface AiAssistantChatResponse {
  answer: string
  structuredQuery: AiAssistantStructuredQuery
  highlights: string[]
  evidence: AiAssistantEvidenceItem[]
  cacheHit: boolean
}

export interface AiAssistantHistoryResponse {
  messages: AiAssistantHistoryItem[]
  lastResponse?: AiAssistantChatResponse
}

export interface AiAssistantStreamEvent {
  type: 'status' | 'delta' | 'final' | 'error'
  content?: string
  response?: AiAssistantChatResponse
}

export interface AiReviewTicketView {
  id: number
  sourceType: string
  status: string
  resolutionCode?: string
  submittedBy: number
  submittedByName: string
  reviewerUserId?: number
  reviewerName?: string
  likelyChineseName?: string
  likelyScientificName?: string
  confidence: number
  needsHumanReview: boolean
  imageMediaId?: number
  imageUrl?: string
  reviewedAt?: string
  createdAt: string
  updatedAt: string
}

export interface AiReviewTicketDetailView extends AiReviewTicketView {
  imageMediaId?: number
  reasoning?: string
  candidates: AiIdentificationCandidate[]
  relatedSpeciesRecords: AiRelatedSpeciesRecord[]
  ragEvidence: RagEvidenceItem[]
  initialRecognitionJson?: string
  reviewEvidenceJson?: string
  submitNote?: string
  finalSpeciesId?: number
  finalChineseName?: string
  finalScientificName?: string
  reviewNote?: string
}

export interface AiReportView {
  id: number
  reportType: string
  days: number
  title: string
  summary: string
  createdBy: number
  creatorName: string
  createdAt: string
}

export interface AiReportDetailView extends AiReportView {
  highlights: string[]
  risks: string[]
  recommendations: string[]
  evidence: string[]
}

export interface RagDocumentView {
  id: number
  sourceType: string
  sourceId?: number
  mediaId?: number
  title: string
  originalFilename?: string
  contentType?: string
  status: string
  chunkCount: number
  errorMessage?: string
  uploadedBy?: number
  createdAt: string
  updatedAt: string
}

export interface RagChunkView {
  id: number
  documentId: number
  sourceType: string
  sourceId?: number
  chunkIndex: number
  title: string
  summary?: string
  content: string
  vectorPointId?: string
  embeddingStatus?: string
  embeddingError?: string
  characterCount: number
  status: string
  createdAt: string
}

export interface RagEvidenceItem {
  sourceType: string
  sourceId?: number
  documentId: number
  chunkId: number
  title: string
  summary?: string
  contentSnippet?: string
  score: number
  sourcePath?: string
  sourceName?: string
  scenario?: string
}

export interface RagDocumentDetailView {
  document: RagDocumentView
  chunks: RagChunkView[]
}

export interface RagIndexJobView {
  id: number
  jobType: string
  status: string
  targetSourceType?: string
  targetSourceId?: number
  totalDocuments: number
  totalChunks: number
  successCount: number
  failedCount: number
  errorMessage?: string
  startedAt: string
  finishedAt?: string
  createdBy?: number
  createdAt: string
}

export interface RagSearchResultView {
  chunkId: number
  documentId: number
  sourceType: string
  sourceId?: number
  title: string
  summary?: string
  content: string
  score: number
  cosineScore: number
  keywordScore: number
  sourcePath?: string
}

export interface RagIngestJobView {
  id: number
  jobType: string
  status: string
  sourceCode?: string
  title?: string
  totalItems: number
  processedItems: number
  successCount: number
  failedCount: number
  errorMessage?: string
  createdBy?: number
  startedAt: string
  finishedAt?: string
  createdAt: string
}

export interface RagIngestItemView {
  id: number
  jobId: number
  sourceType: string
  sourceCode?: string
  externalId?: string
  sourceUrl?: string
  localPath?: string
  mediaId?: number
  ragDocumentId?: number
  title?: string
  status: string
  errorMessage?: string
  createdAt: string
  updatedAt: string
}

export interface RagSourceView {
  id: number
  code: string
  name: string
  sourceType: string
  baseUrl?: string
  enabled: boolean
}

export interface QdrantStatusView {
  available: boolean
  status: string
  pointsCount: number
  readyChunks: number
  errorMessage?: string
}
