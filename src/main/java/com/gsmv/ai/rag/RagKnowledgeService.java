package com.gsmv.ai.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsmv.ai.AiModelGateway;
import com.gsmv.ai.AssistantQueryCache;
import com.gsmv.ai.rag.dto.RagDtos;
import com.gsmv.ai.rag.mapper.RagChunkMapper;
import com.gsmv.ai.rag.mapper.RagDocumentMapper;
import com.gsmv.ai.rag.mapper.RagIndexJobMapper;
import com.gsmv.ai.rag.mapper.RagIngestItemMapper;
import com.gsmv.ai.rag.mapper.RagIngestJobMapper;
import com.gsmv.ai.rag.mapper.RagSourceMapper;
import com.gsmv.ai.rag.model.RagChunk;
import com.gsmv.ai.rag.model.RagDocument;
import com.gsmv.ai.rag.model.RagIndexJob;
import com.gsmv.ai.rag.model.RagIngestItem;
import com.gsmv.ai.rag.model.RagIngestJob;
import com.gsmv.ai.rag.model.RagSource;
import com.gsmv.ai.report.mapper.AiReportMapper;
import com.gsmv.ai.report.model.AiReport;
import com.gsmv.ai.review.mapper.AiReviewTicketMapper;
import com.gsmv.ai.review.model.AiReviewTicket;
import com.gsmv.audit.service.AuditService;
import com.gsmv.common.PageResponse;
import com.gsmv.common.exception.NotFoundException;
import com.gsmv.ecosystem.mapper.EcosystemMapper;
import com.gsmv.ecosystem.model.Ecosystem;
import com.gsmv.media.MediaFileService;
import com.gsmv.media.model.MediaFile;
import com.gsmv.observation.dto.ObservationSpeciesView;
import com.gsmv.observation.dto.ObservationView;
import com.gsmv.observation.mapper.ObservationMapper;
import com.gsmv.security.CurrentUser;
import com.gsmv.security.SecurityUtils;
import com.gsmv.species.dto.SpeciesRow;
import com.gsmv.species.mapper.SpeciesMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.web.client.RestClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RagKnowledgeService {
    public static final String SOURCE_SPECIES = "SPECIES";
    public static final String SOURCE_OBSERVATION = "OBSERVATION";
    public static final String SOURCE_ECOSYSTEM = "ECOSYSTEM";
    public static final String SOURCE_AI_REPORT = "AI_REPORT";
    public static final String SOURCE_AI_REVIEW = "AI_REVIEW_TICKET";
    public static final String SOURCE_UPLOAD = "UPLOAD";
    public static final String SCENARIO_ASSISTANT = "ASSISTANT";
    public static final String SCENARIO_REPORT = "REPORT";
    public static final String SCENARIO_SPECIES_PROFILE = "SPECIES_PROFILE";
    public static final String SCENARIO_OBSERVATION_ANALYSIS = "OBSERVATION_ANALYSIS";
    public static final String SCENARIO_IMAGE_IDENTIFICATION = "IMAGE_IDENTIFICATION";
    public static final String SCENARIO_REVIEW_TICKET = "REVIEW_TICKET";

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";
    private static final String CHUNK_READY = "READY";
    private static final String EMBEDDING_READY = "READY";
    private static final String EMBEDDING_FAILED = "FAILED";
    private static final String RAG_UPLOAD_BUSINESS_TYPE = "RAG_DOCUMENT";
    private static final int SEARCH_POOL_LIMIT = 1000;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".pdf", ".docx", ".txt", ".md");

    private final RagDocumentMapper documentMapper;
    private final RagChunkMapper chunkMapper;
    private final RagIndexJobMapper jobMapper;
    private final RagIngestJobMapper ingestJobMapper;
    private final RagIngestItemMapper ingestItemMapper;
    private final RagSourceMapper sourceMapper;
    private final SpeciesMapper speciesMapper;
    private final ObservationMapper observationMapper;
    private final EcosystemMapper ecosystemMapper;
    private final AiReportMapper aiReportMapper;
    private final AiReviewTicketMapper aiReviewTicketMapper;
    private final MediaFileService mediaFileService;
    private final RagTextExtractor textExtractor;
    private final RagTextChunker textChunker;
    private final AiModelGateway aiModelGateway;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final AssistantQueryCache assistantQueryCache;
    private final RagProperties ragProperties;
    private final QdrantVectorClient qdrantVectorClient;
    private final RestClient.Builder restClientBuilder;

    public RagKnowledgeService(
            RagDocumentMapper documentMapper,
            RagChunkMapper chunkMapper,
            RagIndexJobMapper jobMapper,
            RagIngestJobMapper ingestJobMapper,
            RagIngestItemMapper ingestItemMapper,
            RagSourceMapper sourceMapper,
            SpeciesMapper speciesMapper,
            ObservationMapper observationMapper,
            EcosystemMapper ecosystemMapper,
            AiReportMapper aiReportMapper,
            AiReviewTicketMapper aiReviewTicketMapper,
            MediaFileService mediaFileService,
            RagTextExtractor textExtractor,
            RagTextChunker textChunker,
            AiModelGateway aiModelGateway,
            ObjectMapper objectMapper,
            AuditService auditService,
            AssistantQueryCache assistantQueryCache,
            RagProperties ragProperties,
            QdrantVectorClient qdrantVectorClient,
            RestClient.Builder restClientBuilder
    ) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.jobMapper = jobMapper;
        this.ingestJobMapper = ingestJobMapper;
        this.ingestItemMapper = ingestItemMapper;
        this.sourceMapper = sourceMapper;
        this.speciesMapper = speciesMapper;
        this.observationMapper = observationMapper;
        this.ecosystemMapper = ecosystemMapper;
        this.aiReportMapper = aiReportMapper;
        this.aiReviewTicketMapper = aiReviewTicketMapper;
        this.mediaFileService = mediaFileService;
        this.textExtractor = textExtractor;
        this.textChunker = textChunker;
        this.aiModelGateway = aiModelGateway;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.assistantQueryCache = assistantQueryCache;
        this.ragProperties = ragProperties;
        this.qdrantVectorClient = qdrantVectorClient;
        this.restClientBuilder = restClientBuilder;
    }

    public PageResponse<RagDtos.RagDocumentView> listDocuments(
            String keyword,
            String sourceType,
            String status,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        List<RagDtos.RagDocumentView> items = documentMapper.findPage(
                        normalizeNullable(keyword),
                        normalizeNullable(sourceType),
                        normalizeNullable(status),
                        safeSize,
                        offset
                ).stream()
                .map(this::toDocumentView)
                .toList();
        return new PageResponse<>(items, documentMapper.count(
                normalizeNullable(keyword),
                normalizeNullable(sourceType),
                normalizeNullable(status)
        ), safePage, safeSize);
    }

    public RagDtos.RagDocumentDetailView getDocument(Long id) {
        RagDocument document = requireDocument(id);
        return new RagDtos.RagDocumentDetailView(
                toDocumentView(document),
                chunkMapper.findByDocumentId(id).stream().map(this::toChunkView).toList()
        );
    }

    @Transactional
    public RagDtos.RagDocumentDetailView uploadDocument(MultipartFile file) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        validateUpload(file);
        MediaFile mediaFile = mediaFileService.store(RAG_UPLOAD_BUSINESS_TYPE, 0L, file, currentUser.userId());
        RagDocument document = new RagDocument();
        document.setSourceType(SOURCE_UPLOAD);
        document.setSourceId(mediaFile.getId());
        document.setMediaId(mediaFile.getId());
        document.setTitle(cleanTitle(mediaFile.getOriginalFilename()));
        document.setOriginalFilename(mediaFile.getOriginalFilename());
        document.setContentType(mediaFile.getContentType());
        document.setStatus(STATUS_PENDING);
        document.setChunkCount(0);
        document.setUploadedBy(currentUser.userId());
        document.setMetadataJson(writeJson(Map.of("sha256", safe(mediaFile.getSha256()))));
        documentMapper.insert(document);

        try {
            String text = textExtractor.extract(mediaFileService.readBytes(mediaFile), mediaFile.getOriginalFilename(), mediaFile.getContentType());
            indexDocumentContent(document, document.getTitle(), text, true);
        } catch (RuntimeException ex) {
            markFailed(document, readableError(ex));
        }

        auditService.record(currentUser.userId(), "AI", "UPLOAD_RAG_DOCUMENT", "RAG_DOCUMENT", document.getId(), true,
                "{\"filename\":\"" + escapeJson(mediaFile.getOriginalFilename()) + "\"}");
        assistantQueryCache.invalidateAll();
        return getDocument(document.getId());
    }

    @Transactional
    public void deleteDocument(Long id) {
        RagDocument document = requireDocument(id);
        documentMapper.markDeleted(id);
        chunkMapper.markDeletedByDocumentId(id);
        auditService.record(SecurityUtils.requireCurrentUser().userId(), "AI", "DELETE_RAG_DOCUMENT", "RAG_DOCUMENT", id, true,
                "{\"title\":\"" + escapeJson(document.getTitle()) + "\"}");
        assistantQueryCache.invalidateAll();
    }

    @Transactional
    public RagDtos.RagIndexJobView rebuildAll() {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        RagIndexJob job = newJob("FULL_REBUILD", null, null, currentUser.userId());
        int totalDocs = 0;
        int totalChunks = 0;
        int success = 0;
        int failed = 0;
        String lastError = null;

        for (SpeciesRow row : speciesMapper.findPage(null, 1, null, null, null, null, 5000, 0)) {
            IndexOutcome outcome = indexSystemSource(SOURCE_SPECIES, row.id(), buildSpeciesTitle(row), buildSpeciesText(row), false);
            totalDocs++;
            totalChunks += outcome.chunkCount();
            if (outcome.success()) success++; else { failed++; lastError = outcome.message(); }
        }
        for (ObservationView view : observationMapper.findPage(null, null, null, null, 5000, 0)) {
            IndexOutcome outcome = indexSystemSource(SOURCE_OBSERVATION, view.id(), buildObservationTitle(view), buildObservationText(view), false);
            totalDocs++;
            totalChunks += outcome.chunkCount();
            if (outcome.success()) success++; else { failed++; lastError = outcome.message(); }
        }
        for (Ecosystem ecosystem : ecosystemMapper.findAll()) {
            IndexOutcome outcome = indexSystemSource(SOURCE_ECOSYSTEM, ecosystem.getId(), buildEcosystemTitle(ecosystem), buildEcosystemText(ecosystem), false);
            totalDocs++;
            totalChunks += outcome.chunkCount();
            if (outcome.success()) success++; else { failed++; lastError = outcome.message(); }
        }
        for (AiReport report : aiReportMapper.findPage(5000, 0)) {
            IndexOutcome outcome = indexSystemSource(SOURCE_AI_REPORT, report.getId(), report.getTitle(), buildReportText(report), false);
            totalDocs++;
            totalChunks += outcome.chunkCount();
            if (outcome.success()) success++; else { failed++; lastError = outcome.message(); }
        }
        for (AiReviewTicket ticket : aiReviewTicketMapper.findPage(null, null, null, 5000, 0)) {
            IndexOutcome outcome = indexSystemSource(SOURCE_AI_REVIEW, ticket.getId(), buildReviewTitle(ticket), buildReviewText(ticket), false);
            totalDocs++;
            totalChunks += outcome.chunkCount();
            if (outcome.success()) success++; else { failed++; lastError = outcome.message(); }
        }
        for (RagDocument upload : documentMapper.findUploadedDocuments(5000)) {
            IndexOutcome outcome = reindexUploaded(upload);
            totalDocs++;
            totalChunks += outcome.chunkCount();
            if (outcome.success()) success++; else { failed++; lastError = outcome.message(); }
        }

        job.setStatus(failed > 0 ? "PARTIAL_SUCCESS" : "SUCCESS");
        job.setTotalDocuments(totalDocs);
        job.setTotalChunks(totalChunks);
        job.setSuccessCount(success);
        job.setFailedCount(failed);
        job.setErrorMessage(lastError);
        jobMapper.finish(job);
        auditService.record(currentUser.userId(), "AI", "REBUILD_RAG_INDEX", "RAG", null, failed == 0,
                "{\"success\":" + success + ",\"failed\":" + failed + "}");
        assistantQueryCache.invalidateAll();
        return toJobView(jobMapper.findById(job.getId()));
    }

    public PageResponse<RagDtos.RagIndexJobView> listJobs(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        List<RagDtos.RagIndexJobView> items = jobMapper.findPage(safeSize, offset).stream()
                .map(this::toJobView)
                .toList();
        return new PageResponse<>(items, jobMapper.count(), safePage, safeSize);
    }

    public PageResponse<RagDtos.RagIngestJobView> listIngestJobs(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        List<RagDtos.RagIngestJobView> items = ingestJobMapper.findPage(safeSize, offset).stream()
                .map(this::toIngestJobView)
                .toList();
        return new PageResponse<>(items, ingestJobMapper.count(), safePage, safeSize);
    }

    public List<RagDtos.RagIngestItemView> listIngestItems(Long jobId) {
        return ingestItemMapper.findByJobId(jobId).stream().map(this::toIngestItemView).toList();
    }

    public List<RagDtos.RagSourceView> listSources() {
        return sourceMapper.findAll().stream().map(this::toSourceView).toList();
    }

    public RagDtos.QdrantStatusView qdrantStatus() {
        QdrantVectorClient.QdrantStatus status = qdrantVectorClient.status();
        return new RagDtos.QdrantStatusView(
                status.available(),
                status.status(),
                status.pointsCount(),
                chunkMapper.countReady(),
                status.errorMessage()
        );
    }

    @Transactional
    public RagDtos.QdrantStatusView rebuildQdrant() {
        qdrantVectorClient.ensureCollection();
        long total = chunkMapper.countReady();
        int pageSize = 200;
        for (int offset = 0; offset < total; offset += pageSize) {
            List<QdrantVectorClient.QdrantPoint> points = new ArrayList<>();
            for (RagChunk chunk : chunkMapper.findReadyPage(pageSize, offset)) {
                List<Double> vector = readVector(chunk.getEmbeddingJson());
                if (vector.isEmpty()) {
                    continue;
                }
                points.add(new QdrantVectorClient.QdrantPoint(
                        chunk.getId(),
                        vector,
                        Map.of(
                                "chunkId", chunk.getId(),
                                "documentId", chunk.getDocumentId(),
                                "sourceType", safe(chunk.getSourceType()),
                                "sourceId", chunk.getSourceId() == null ? 0L : chunk.getSourceId(),
                                "title", safe(chunk.getTitle())
                        )
                ));
            }
            qdrantVectorClient.upsert(points);
        }
        assistantQueryCache.invalidateAll();
        return qdrantStatus();
    }

    @Transactional
    public RagDtos.RagIngestJobView ingestFolder(RagDtos.FolderIngestRequest request) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        Path folder = Path.of(request.path()).toAbsolutePath().normalize();
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("Folder does not exist: " + folder);
        }
        boolean recursive = request.recursive() == null || request.recursive();
        List<Path> files = scanSupportedFiles(folder, recursive);
        RagIngestJob job = createIngestJob("FOLDER", "LOCAL_FOLDER", "Folder: " + folder, files.size(), currentUser.userId());
        for (Path file : files) {
            RagIngestItem item = newIngestItem(job, "LOCAL_FOLDER", "LOCAL_FOLDER", null, null, file.toString(), file.getFileName().toString());
            processLocalPathItem(job, item, file, currentUser.userId());
        }
        finishIngestJob(job, null);
        assistantQueryCache.invalidateAll();
        return toIngestJobView(ingestJobMapper.findById(job.getId()));
    }

    @Transactional
    public RagDtos.RagIngestJobView ingestFiles(List<MultipartFile> files) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        List<MultipartFile> safeFiles = files == null ? List.of() : files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        RagIngestJob job = createIngestJob("FILES", "UPLOAD", "Uploaded files", safeFiles.size(), currentUser.userId());
        for (MultipartFile file : safeFiles) {
            RagIngestItem item = newIngestItem(job, SOURCE_UPLOAD, "UPLOAD", null, null, null, file.getOriginalFilename());
            processMultipartItem(job, item, file, currentUser.userId());
        }
        finishIngestJob(job, null);
        assistantQueryCache.invalidateAll();
        return toIngestJobView(ingestJobMapper.findById(job.getId()));
    }

    @Transactional
    public RagDtos.RagIngestJobView ingestExternal(RagDtos.ExternalIngestRequest request) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        String sourceCode = normalizeRequired(request.sourceCode()).toUpperCase(Locale.ROOT);
        int limit = request.limit() == null ? 10 : Math.min(Math.max(request.limit(), 1), 50);
        List<ExternalRecord> records = collectExternalRecords(sourceCode, normalizeNullable(request.query()), limit, request.urls());
        RagIngestJob job = createIngestJob("EXTERNAL", sourceCode, sourceCode + " import", records.size(), currentUser.userId());
        for (ExternalRecord record : records) {
            RagIngestItem item = newIngestItem(job, "EXTERNAL", sourceCode, record.externalId(), record.sourceUrl(), null, record.title());
            processExternalRecordItem(job, item, record);
        }
        finishIngestJob(job, null);
        assistantQueryCache.invalidateAll();
        return toIngestJobView(ingestJobMapper.findById(job.getId()));
    }

    @Transactional
    public RagDtos.RagIngestJobView retryIngestJob(Long jobId) {
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        RagIngestJob original = ingestJobMapper.findById(jobId);
        if (original == null) {
            throw new NotFoundException("RAG ingest job not found");
        }
        List<RagIngestItem> failedItems = ingestItemMapper.findFailedByJobId(jobId);
        RagIngestJob retry = createIngestJob("RETRY", original.getSourceCode(), "Retry job #" + jobId, failedItems.size(), currentUser.userId());
        for (RagIngestItem failed : failedItems) {
            RagIngestItem item = newIngestItem(retry, failed.getSourceType(), failed.getSourceCode(), failed.getExternalId(),
                    failed.getSourceUrl(), failed.getLocalPath(), failed.getTitle());
            if (StringUtils.hasText(failed.getLocalPath())) {
                processLocalPathItem(retry, item, Path.of(failed.getLocalPath()), currentUser.userId());
            } else {
                markIngestItemFailed(retry, item, "Retry needs a local path or external refetch is not available for this item");
            }
        }
        finishIngestJob(retry, null);
        assistantQueryCache.invalidateAll();
        return toIngestJobView(ingestJobMapper.findById(retry.getId()));
    }

    public List<RagDtos.RagSearchResultView> searchForView(String query, int limit) {
        return retrieveForScenario(SCENARIO_ASSISTANT, query, limit).stream().map(this::toSearchView).toList();
    }

    public List<RagSearchHit> retrieve(String query, int limit) {
        return retrieveForScenario(SCENARIO_ASSISTANT, query, limit);
    }

    public List<RagSearchHit> retrieveForScenario(String scenario, String query, int limit) {
        String normalizedQuery = normalizeNullable(query);
        if (!StringUtils.hasText(normalizedQuery)) {
            return List.of();
        }
        int safeLimit = Math.min(Math.max(limit, 1), 12);
        List<Double> queryVector = tryEmbedQuery(normalizedQuery);
        if (queryVector != null) {
            List<RagSearchHit> qdrantHits = retrieveFromQdrant(scenario, normalizedQuery, queryVector, safeLimit);
            if (!qdrantHits.isEmpty()) {
                return qdrantHits;
            }
        }
        return retrieveFromMysql(normalizedQuery, queryVector, safeLimit);
    }

    public List<RagDtos.RagEvidenceItem> retrieveEvidenceForScenario(String scenario, String query, int limit) {
        return retrieveForScenario(scenario, query, limit).stream()
                .map(hit -> toEvidenceItem(hit, scenario))
                .toList();
    }

    private List<RagSearchHit> retrieveFromQdrant(String scenario, String query, List<Double> queryVector, int safeLimit) {
        List<QdrantVectorClient.QdrantSearchHit> vectorHits = qdrantVectorClient.search(queryVector, Math.max(safeLimit * 3, 12), scenarioFilter(scenario));
        if (vectorHits.isEmpty()) {
            return List.of();
        }
        List<Long> chunkIds = vectorHits.stream().map(QdrantVectorClient.QdrantSearchHit::chunkId).distinct().toList();
        if (chunkIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Double> qdrantScores = new HashMap<>();
        for (QdrantVectorClient.QdrantSearchHit hit : vectorHits) {
            qdrantScores.putIfAbsent(hit.chunkId(), hit.score());
        }
        Map<Long, RagChunk> chunkMap = new LinkedHashMap<>();
        for (RagChunk chunk : chunkMapper.findByIds(chunkIds)) {
            chunkMap.put(chunk.getId(), chunk);
        }
        List<RagSearchHit> hits = new ArrayList<>();
        for (Long chunkId : chunkIds) {
            RagChunk chunk = chunkMap.get(chunkId);
            if (chunk == null) {
                continue;
            }
            double cosine = qdrantScores.getOrDefault(chunkId, 0.0d);
            String searchable = chunk.getTitle() + "\n" + safe(chunk.getSummary()) + "\n" + safe(chunk.getContent());
            double keyword = RagVectorUtils.keywordScore(query, searchable);
            double score = 0.75d * cosine + 0.15d * keyword + 0.10d * sourceWeight(chunk.getSourceType());
            hits.add(toHit(chunk, score, cosine, keyword));
        }
        return hits.stream()
                .sorted(Comparator.comparingDouble(RagSearchHit::score).reversed())
                .limit(safeLimit)
                .toList();
    }

    private List<RagSearchHit> retrieveFromMysql(String normalizedQuery, List<Double> queryVector, int safeLimit) {
        List<RagSearchHit> hits = new ArrayList<>();
        for (RagChunk chunk : chunkMapper.findSearchPool(SEARCH_POOL_LIMIT)) {
            List<Double> vector = readVector(chunk.getEmbeddingJson());
            double cosine = queryVector == null ? 0.0d : RagVectorUtils.cosine(queryVector, vector);
            String searchable = chunk.getTitle() + "\n" + safe(chunk.getSummary()) + "\n" + safe(chunk.getContent());
            double keyword = RagVectorUtils.keywordScore(normalizedQuery, searchable);
            if (queryVector == null && keyword <= 0.0d) {
                continue;
            }
            double score = 0.75d * cosine + 0.15d * keyword + 0.10d * sourceWeight(chunk.getSourceType());
            hits.add(toHit(chunk, score, cosine, keyword));
        }
        return hits.stream()
                .sorted(Comparator.comparingDouble(RagSearchHit::score).reversed())
                .limit(safeLimit)
                .toList();
    }

    private Map<String, Object> scenarioFilter(String scenario) {
        if (!StringUtils.hasText(scenario)) {
            return Map.of();
        }
        return Map.of();
    }

    public void syncSpecies(Long id) {
        try {
            SpeciesRow row = speciesMapper.findRowById(id);
            if (row == null) {
                markSourceDeleted(SOURCE_SPECIES, id);
                return;
            }
            indexSystemSource(SOURCE_SPECIES, id, buildSpeciesTitle(row), buildSpeciesText(row), true);
        } catch (RuntimeException ignored) {
            // RAG indexing must not block core data maintenance.
        }
    }

    public void syncObservation(Long id) {
        try {
            ObservationView view = observationMapper.findViewById(id);
            if (view == null) {
                markSourceDeleted(SOURCE_OBSERVATION, id);
                return;
            }
            indexSystemSource(SOURCE_OBSERVATION, id, buildObservationTitle(view), buildObservationText(view), true);
        } catch (RuntimeException ignored) {
        }
    }

    public void syncEcosystem(Long id) {
        try {
            Ecosystem ecosystem = ecosystemMapper.findById(id);
            if (ecosystem == null) {
                markSourceDeleted(SOURCE_ECOSYSTEM, id);
                return;
            }
            indexSystemSource(SOURCE_ECOSYSTEM, id, buildEcosystemTitle(ecosystem), buildEcosystemText(ecosystem), true);
        } catch (RuntimeException ignored) {
        }
    }

    public void syncAiReport(Long id) {
        try {
            AiReport report = aiReportMapper.findById(id);
            if (report == null) {
                markSourceDeleted(SOURCE_AI_REPORT, id);
                return;
            }
            indexSystemSource(SOURCE_AI_REPORT, id, report.getTitle(), buildReportText(report), true);
        } catch (RuntimeException ignored) {
        }
    }

    public void syncAiReviewTicket(Long id) {
        try {
            AiReviewTicket ticket = aiReviewTicketMapper.findById(id);
            if (ticket == null) {
                markSourceDeleted(SOURCE_AI_REVIEW, id);
                return;
            }
            indexSystemSource(SOURCE_AI_REVIEW, id, buildReviewTitle(ticket), buildReviewText(ticket), true);
        } catch (RuntimeException ignored) {
        }
    }

    public void markSourceDeleted(String sourceType, Long sourceId) {
        RagDocument document = documentMapper.findBySource(sourceType, sourceId);
        if (document != null) {
            documentMapper.markDeleted(document.getId());
            chunkMapper.markDeletedByDocumentId(document.getId());
        }
    }

    private RagDocument requireDocument(Long id) {
        RagDocument document = documentMapper.findById(id);
        if (document == null || "DELETED".equalsIgnoreCase(document.getStatus())) {
            throw new NotFoundException("RAG 文档不存在");
        }
        return document;
    }

    private IndexOutcome indexSystemSource(String sourceType, Long sourceId, String title, String text, boolean swallowFailure) {
        RagDocument document = documentMapper.findBySource(sourceType, sourceId);
        if (document == null) {
            document = new RagDocument();
            document.setSourceType(sourceType);
            document.setSourceId(sourceId);
            document.setTitle(firstNonBlank(title, sourceType + "#" + sourceId));
            document.setStatus(STATUS_PENDING);
            document.setChunkCount(0);
            document.setMetadataJson(writeJson(Map.of("sourceType", sourceType, "sourceId", sourceId)));
            documentMapper.insert(document);
        } else {
            document.setTitle(firstNonBlank(title, document.getTitle()));
            document.setStatus(STATUS_PENDING);
            document.setChunkCount(0);
            document.setErrorMessage(null);
            document.setMetadataJson(writeJson(Map.of("sourceType", sourceType, "sourceId", sourceId)));
            documentMapper.update(document);
        }
        try {
            int chunks = indexDocumentContent(document, document.getTitle(), text, false);
            return new IndexOutcome(true, chunks, null);
        } catch (RuntimeException ex) {
            markFailed(document, readableError(ex));
            if (!swallowFailure) {
                return new IndexOutcome(false, 0, readableError(ex));
            }
            return new IndexOutcome(false, 0, readableError(ex));
        }
    }

    private IndexOutcome reindexUploaded(RagDocument document) {
        try {
            if (document.getMediaId() == null) {
                throw new IllegalStateException("上传文档缺少媒体文件");
            }
            MediaFile mediaFile = mediaFileService.getRequired(document.getMediaId());
            String text = textExtractor.extract(mediaFileService.readBytes(mediaFile), mediaFile.getOriginalFilename(), mediaFile.getContentType());
            int chunks = indexDocumentContent(document, document.getTitle(), text, true);
            return new IndexOutcome(true, chunks, null);
        } catch (RuntimeException ex) {
            markFailed(document, readableError(ex));
            return new IndexOutcome(false, 0, readableError(ex));
        }
    }

    private int indexDocumentContent(RagDocument document, String title, String text, boolean upload) {
        List<RagTextChunker.ChunkDraft> drafts = textChunker.chunk(title, text);
        if (drafts.isEmpty()) {
            throw new IllegalArgumentException(upload ? "文档未抽取到可索引文本" : "系统数据内容为空，无法索引");
        }

        chunkMapper.deleteByDocumentId(document.getId());
        List<RagChunk> chunks = new ArrayList<>();
        for (RagTextChunker.ChunkDraft draft : drafts) {
            RagChunk chunk = new RagChunk();
            chunk.setDocumentId(document.getId());
            chunk.setSourceType(document.getSourceType());
            chunk.setSourceId(document.getSourceId());
            chunk.setChunkIndex(draft.index());
            chunk.setTitle(draft.title());
            chunk.setSummary(draft.summary());
            chunk.setContent(draft.content());
            chunk.setEmbeddingJson(null);
            chunk.setEmbeddingModel(embeddingModel());
            chunk.setEmbeddingDimension(embeddingDimension());
            chunk.setEmbeddingStatus(STATUS_PENDING);
            chunk.setCharacterCount(draft.characterCount());
            chunk.setMetadataJson(writeJson(Map.of("documentTitle", draft.title(), "chunkIndex", draft.index())));
            chunk.setStatus(CHUNK_READY);
            chunkMapper.insert(chunk);
            chunks.add(chunk);
        }
        embedAndStoreChunks(chunks);
        documentMapper.updateStatus(document.getId(), STATUS_READY, chunks.size(), null);
        return chunks.size();
    }

    private void embedAndStoreChunks(List<RagChunk> chunks) {
        for (int i = 0; i < chunks.size(); i += 10) {
            List<RagChunk> batch = chunks.subList(i, Math.min(i + 10, chunks.size()));
            List<List<Double>> vectors = aiModelGateway.embedTexts(batch.stream().map(RagChunk::getContent).toList());
            List<QdrantVectorClient.QdrantPoint> points = new ArrayList<>();
            for (int j = 0; j < batch.size(); j++) {
                RagChunk chunk = batch.get(j);
                List<Double> vector = vectors.get(j);
                chunkMapper.updateEmbeddingState(
                        chunk.getId(),
                        writeJson(vector),
                        String.valueOf(chunk.getId()),
                        embeddingModel(),
                        embeddingDimension(),
                        EMBEDDING_READY,
                        null
                );
                points.add(new QdrantVectorClient.QdrantPoint(
                        chunk.getId(),
                        vector,
                        Map.of(
                                "chunkId", chunk.getId(),
                                "documentId", chunk.getDocumentId(),
                                "sourceType", safe(chunk.getSourceType()),
                                "sourceId", chunk.getSourceId() == null ? 0L : chunk.getSourceId(),
                                "title", safe(chunk.getTitle())
                        )
                ));
            }
            qdrantVectorClient.upsert(points);
        }
    }

    private void markFailed(RagDocument document, String message) {
        documentMapper.updateStatus(document.getId(), STATUS_FAILED, 0, truncate(message, 950));
        chunkMapper.deleteByDocumentId(document.getId());
    }

    private RagIngestJob createIngestJob(String jobType, String sourceCode, String title, int totalItems, Long userId) {
        RagIngestJob job = new RagIngestJob();
        job.setJobType(jobType);
        job.setStatus("RUNNING");
        job.setSourceCode(sourceCode);
        job.setTitle(title);
        job.setTotalItems(totalItems);
        job.setProcessedItems(0);
        job.setSuccessCount(0);
        job.setFailedCount(0);
        job.setCreatedBy(userId);
        ingestJobMapper.insert(job);
        return job;
    }

    private RagIngestItem newIngestItem(
            RagIngestJob job,
            String sourceType,
            String sourceCode,
            String externalId,
            String sourceUrl,
            String localPath,
            String title
    ) {
        RagIngestItem item = new RagIngestItem();
        item.setJobId(job.getId());
        item.setSourceType(sourceType);
        item.setSourceCode(sourceCode);
        item.setExternalId(externalId);
        item.setSourceUrl(sourceUrl);
        item.setLocalPath(localPath);
        item.setTitle(firstNonBlank(title, externalId, "RAG item"));
        item.setStatus(STATUS_PENDING);
        ingestItemMapper.insert(item);
        return item;
    }

    private void processLocalPathItem(RagIngestJob job, RagIngestItem item, Path file, Long userId) {
        try {
            validatePathForIngest(file);
            byte[] bytes = Files.readAllBytes(file);
            String filename = file.getFileName().toString();
            String contentType = contentTypeFor(filename);
            MediaFile mediaFile = mediaFileService.storeBytes(RAG_UPLOAD_BUSINESS_TYPE, 0L, filename, contentType, bytes, userId);
            RagDocument document = createDocumentFromMedia(mediaFile, SOURCE_UPLOAD, mediaFile.getId(), userId,
                    Map.of("ingestJobId", job.getId(), "localPath", file.toString(), "sha256", safe(mediaFile.getSha256())));
            String text = textExtractor.extract(bytes, filename, contentType);
            indexDocumentContent(document, document.getTitle(), text, true);
            item.setMediaId(mediaFile.getId());
            item.setRagDocumentId(document.getId());
            markIngestItemSuccess(job, item);
        } catch (RuntimeException | IOException ex) {
            markIngestItemFailed(job, item, readableError(ex));
        }
    }

    private void processMultipartItem(RagIngestJob job, RagIngestItem item, MultipartFile file, Long userId) {
        try {
            validateUpload(file);
            MediaFile mediaFile = mediaFileService.store(RAG_UPLOAD_BUSINESS_TYPE, 0L, file, userId);
            RagDocument document = createDocumentFromMedia(mediaFile, SOURCE_UPLOAD, mediaFile.getId(), userId,
                    Map.of("ingestJobId", job.getId(), "sha256", safe(mediaFile.getSha256())));
            String text = textExtractor.extract(mediaFileService.readBytes(mediaFile), mediaFile.getOriginalFilename(), mediaFile.getContentType());
            indexDocumentContent(document, document.getTitle(), text, true);
            item.setMediaId(mediaFile.getId());
            item.setRagDocumentId(document.getId());
            markIngestItemSuccess(job, item);
        } catch (RuntimeException ex) {
            markIngestItemFailed(job, item, readableError(ex));
        }
    }

    private void processExternalRecordItem(RagIngestJob job, RagIngestItem item, ExternalRecord record) {
        try {
            if (StringUtils.hasText(record.externalId())
                    && ingestItemMapper.countSuccessfulExternal(record.sourceCode(), record.externalId()) > 0) {
                markIngestItemFailed(job, item, "Duplicate external record skipped: " + record.externalId());
                return;
            }
            RagDocument document = new RagDocument();
            document.setSourceType("EXTERNAL_" + truncate(record.sourceCode(), 23));
            document.setSourceId(item.getId());
            document.setTitle(firstNonBlank(record.title(), record.sourceCode() + " record"));
            document.setOriginalFilename(null);
            document.setContentType("text/plain");
            document.setStatus(STATUS_PENDING);
            document.setChunkCount(0);
            document.setUploadedBy(null);
            document.setMetadataJson(writeJson(Map.of(
                    "ingestJobId", job.getId(),
                    "sourceCode", record.sourceCode(),
                    "sourceUrl", safe(record.sourceUrl()),
                    "externalId", safe(record.externalId())
            )));
            documentMapper.insert(document);
            indexDocumentContent(document, document.getTitle(), record.content(), false);
            item.setRagDocumentId(document.getId());
            item.setMetadataJson(writeJson(Map.of("sourceUrl", safe(record.sourceUrl()))));
            markIngestItemSuccess(job, item);
        } catch (RuntimeException ex) {
            markIngestItemFailed(job, item, readableError(ex));
        }
    }

    private RagDocument createDocumentFromMedia(MediaFile mediaFile, String sourceType, Long sourceId, Long userId, Map<String, Object> metadata) {
        RagDocument document = new RagDocument();
        document.setSourceType(sourceType);
        document.setSourceId(sourceId);
        document.setMediaId(mediaFile.getId());
        document.setTitle(cleanTitle(mediaFile.getOriginalFilename()));
        document.setOriginalFilename(mediaFile.getOriginalFilename());
        document.setContentType(mediaFile.getContentType());
        document.setStatus(STATUS_PENDING);
        document.setChunkCount(0);
        document.setUploadedBy(userId);
        document.setMetadataJson(writeJson(metadata));
        documentMapper.insert(document);
        return document;
    }

    private void markIngestItemSuccess(RagIngestJob job, RagIngestItem item) {
        item.setStatus("SUCCESS");
        item.setErrorMessage(null);
        ingestItemMapper.updateStatus(item);
        job.setProcessedItems(job.getProcessedItems() + 1);
        job.setSuccessCount(job.getSuccessCount() + 1);
        ingestJobMapper.updateProgress(job);
    }

    private void markIngestItemFailed(RagIngestJob job, RagIngestItem item, String error) {
        item.setStatus(STATUS_FAILED);
        item.setErrorMessage(truncate(error, 950));
        ingestItemMapper.updateStatus(item);
        job.setProcessedItems(job.getProcessedItems() + 1);
        job.setFailedCount(job.getFailedCount() + 1);
        job.setErrorMessage(item.getErrorMessage());
        ingestJobMapper.updateProgress(job);
    }

    private void finishIngestJob(RagIngestJob job, String errorMessage) {
        String status = job.getFailedCount() == null || job.getFailedCount() == 0 ? "SUCCESS" : "PARTIAL_SUCCESS";
        if (job.getSuccessCount() != null && job.getSuccessCount() == 0 && job.getFailedCount() != null && job.getFailedCount() > 0) {
            status = STATUS_FAILED;
        }
        ingestJobMapper.finish(job.getId(), status, truncate(firstNonBlank(errorMessage, job.getErrorMessage()), 950), LocalDateTime.now());
    }

    private RagIndexJob newJob(String jobType, String sourceType, Long sourceId, Long userId) {
        RagIndexJob job = new RagIndexJob();
        job.setJobType(jobType);
        job.setStatus("RUNNING");
        job.setTargetSourceType(sourceType);
        job.setTargetSourceId(sourceId);
        job.setTotalDocuments(0);
        job.setTotalChunks(0);
        job.setSuccessCount(0);
        job.setFailedCount(0);
        job.setCreatedBy(userId);
        jobMapper.insert(job);
        return job;
    }

    private List<Double> tryEmbedQuery(String query) {
        try {
            return aiModelGateway.embedTexts(List.of(query)).get(0);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private List<Double> readVector(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Double>>() { });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String buildSpeciesTitle(SpeciesRow row) {
        return firstNonBlank(row.chineseName(), row.scientificName(), "物种档案#" + row.id());
    }

    private String buildSpeciesText(SpeciesRow row) {
        return String.join("\n",
                "来源：物种档案",
                "中文名：" + safe(row.chineseName()),
                "学名：" + safe(row.scientificName()),
                "保护等级：" + safe(row.protectionLevel()),
                "IUCN濒危状态：" + safe(row.iucnStatus()),
                "物种简介：" + safe(row.description()),
                "形态特征：" + safe(row.morphology()),
                "生活习性：" + safe(row.habit()),
                "栖息环境：" + safe(row.habitat()),
                "分布区域：" + safe(row.distribution()),
                "地理范围：" + safe(row.geoRangeText()),
                "参考文献：" + safe(row.referenceText())
        );
    }

    private String buildObservationTitle(ObservationView view) {
        return firstNonBlank(view.locationName(), view.ecosystemName(), "观测记录#" + view.id());
    }

    private String buildObservationText(ObservationView view) {
        List<ObservationSpeciesView> speciesItems = observationMapper.findSpeciesViews(view.id());
        String speciesText = speciesItems.stream()
                .map(item -> firstNonBlank(item.chineseName(), item.scientificName(), "物种#" + item.speciesId())
                        + nullableSuffix(" 数量", item.countEstimated())
                        + nullableSuffix(" 行为", item.behavior())
                        + nullableSuffix(" 备注", item.comment()))
                .toList()
                .toString();
        return String.join("\n",
                "来源：观测记录",
                "生态系统：" + safe(view.ecosystemName()),
                "观测人员：" + safe(view.observerName()),
                "观测时间：" + safe(view.observedAt()),
                "地点：" + safe(view.locationName()),
                "坐标：" + safe(view.locationLat()) + "," + safe(view.locationLng()),
                "环境参数：" + safe(view.envJson()),
                "备注：" + safe(view.note()),
                "关联物种：" + speciesText
        );
    }

    private String buildEcosystemTitle(Ecosystem ecosystem) {
        return firstNonBlank(ecosystem.getName(), "生态系统#" + ecosystem.getId());
    }

    private String buildEcosystemText(Ecosystem ecosystem) {
        return String.join("\n",
                "来源：生态系统",
                "名称：" + safe(ecosystem.getName()),
                "类型：" + safe(ecosystem.getType()),
                "描述：" + safe(ecosystem.getDescription())
        );
    }

    private String buildReportText(AiReport report) {
        return String.join("\n",
                "来源：AI科研报告",
                "标题：" + safe(report.getTitle()),
                "类型：" + safe(report.getReportType()),
                "时间范围：近 " + safe(report.getDays()) + " 天",
                "摘要：" + safe(report.getSummary()),
                "重点发现：" + safe(report.getHighlightsJson()),
                "风险提示：" + safe(report.getRisksJson()),
                "建议行动：" + safe(report.getRecommendationsJson()),
                "数据依据：" + safe(report.getEvidenceJson())
        );
    }

    private String buildReviewTitle(AiReviewTicket ticket) {
        return firstNonBlank(ticket.getFinalChineseName(), ticket.getLikelyChineseName(), ticket.getLikelyScientificName(), "AI复核工单#" + ticket.getId());
    }

    private String buildReviewText(AiReviewTicket ticket) {
        return String.join("\n",
                "来源：AI复核工单",
                "工单状态：" + safe(ticket.getStatus()),
                "识别中文名：" + safe(ticket.getLikelyChineseName()),
                "识别学名：" + safe(ticket.getLikelyScientificName()),
                "置信度：" + safe(ticket.getConfidence()),
                "识别理由：" + safe(ticket.getReasoning()),
                "候选列表：" + safe(ticket.getCandidateJson()),
                "初始识别快照：" + safe(ticket.getInitialRecognitionJson()),
                "RAG证据快照：" + safe(ticket.getRagEvidenceJson()),
                "人工复核证据：" + safe(ticket.getReviewEvidenceJson()),
                "人工结论：" + safe(ticket.getResolutionCode()),
                "最终中文名：" + safe(ticket.getFinalChineseName()),
                "最终学名：" + safe(ticket.getFinalScientificName()),
                "复核说明：" + safe(ticket.getReviewNote())
        );
    }

    private RagDtos.RagDocumentView toDocumentView(RagDocument document) {
        return new RagDtos.RagDocumentView(
                document.getId(),
                document.getSourceType(),
                document.getSourceId(),
                document.getMediaId(),
                document.getTitle(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getStatus(),
                document.getChunkCount(),
                document.getErrorMessage(),
                document.getUploadedBy(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    private RagDtos.RagChunkView toChunkView(RagChunk chunk) {
        return new RagDtos.RagChunkView(
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getSourceType(),
                chunk.getSourceId(),
                chunk.getChunkIndex(),
                chunk.getTitle(),
                chunk.getSummary(),
                chunk.getContent(),
                chunk.getVectorPointId(),
                chunk.getEmbeddingStatus(),
                chunk.getEmbeddingError(),
                chunk.getCharacterCount(),
                chunk.getStatus(),
                chunk.getCreatedAt()
        );
    }

    private RagDtos.RagIndexJobView toJobView(RagIndexJob job) {
        return new RagDtos.RagIndexJobView(
                job.getId(),
                job.getJobType(),
                job.getStatus(),
                job.getTargetSourceType(),
                job.getTargetSourceId(),
                job.getTotalDocuments(),
                job.getTotalChunks(),
                job.getSuccessCount(),
                job.getFailedCount(),
                job.getErrorMessage(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getCreatedBy(),
                job.getCreatedAt()
        );
    }

    private RagDtos.RagSearchResultView toSearchView(RagSearchHit hit) {
        return new RagDtos.RagSearchResultView(
                hit.chunkId(),
                hit.documentId(),
                hit.sourceType(),
                hit.sourceId(),
                hit.title(),
                hit.summary(),
                truncate(hit.content(), 520),
                hit.score(),
                hit.cosineScore(),
                hit.keywordScore(),
                hit.sourcePath()
        );
    }

    private RagSearchHit toHit(RagChunk chunk, double score, double cosine, double keyword) {
        return new RagSearchHit(
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getSourceType(),
                chunk.getSourceId(),
                chunk.getTitle(),
                chunk.getSummary(),
                chunk.getContent(),
                score,
                cosine,
                keyword,
                sourcePath(chunk)
        );
    }

    private RagDtos.RagEvidenceItem toEvidenceItem(RagSearchHit hit, String scenario) {
        return new RagDtos.RagEvidenceItem(
                hit.sourceType(),
                hit.sourceId(),
                hit.documentId(),
                hit.chunkId(),
                hit.title(),
                hit.summary(),
                truncate(hit.content(), 360),
                hit.score(),
                hit.sourcePath(),
                sourceName(hit.sourceType()),
                scenario
        );
    }

    private RagDtos.RagIngestJobView toIngestJobView(RagIngestJob job) {
        return new RagDtos.RagIngestJobView(
                job.getId(),
                job.getJobType(),
                job.getStatus(),
                job.getSourceCode(),
                job.getTitle(),
                job.getTotalItems(),
                job.getProcessedItems(),
                job.getSuccessCount(),
                job.getFailedCount(),
                job.getErrorMessage(),
                job.getCreatedBy(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getCreatedAt()
        );
    }

    private RagDtos.RagIngestItemView toIngestItemView(RagIngestItem item) {
        return new RagDtos.RagIngestItemView(
                item.getId(),
                item.getJobId(),
                item.getSourceType(),
                item.getSourceCode(),
                item.getExternalId(),
                item.getSourceUrl(),
                item.getLocalPath(),
                item.getMediaId(),
                item.getRagDocumentId(),
                item.getTitle(),
                item.getStatus(),
                item.getErrorMessage(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private RagDtos.RagSourceView toSourceView(RagSource source) {
        return new RagDtos.RagSourceView(
                source.getId(),
                source.getCode(),
                source.getName(),
                source.getSourceType(),
                source.getBaseUrl(),
                source.getEnabled() != null && source.getEnabled() == 1
        );
    }

    private String sourcePath(RagChunk chunk) {
        if (SOURCE_SPECIES.equals(chunk.getSourceType())) {
            return "/species/" + chunk.getSourceId();
        }
        if (SOURCE_OBSERVATION.equals(chunk.getSourceType())) {
            return "/observations?focus=" + chunk.getSourceId();
        }
        if (SOURCE_ECOSYSTEM.equals(chunk.getSourceType())) {
            return "/ecosystems?focus=" + chunk.getSourceId();
        }
        if (SOURCE_AI_REPORT.equals(chunk.getSourceType())) {
            return "/ai-reports?focus=" + chunk.getSourceId();
        }
        if (SOURCE_AI_REVIEW.equals(chunk.getSourceType())) {
            return "/ai-reviews?focus=" + chunk.getSourceId();
        }
        return "/rag-knowledge?document=" + chunk.getDocumentId();
    }

    private String sourceName(String sourceType) {
        return switch (safe(sourceType)) {
            case SOURCE_SPECIES -> "Species archive";
            case SOURCE_OBSERVATION -> "Observation record";
            case SOURCE_ECOSYSTEM -> "Ecosystem";
            case SOURCE_AI_REPORT -> "AI research report";
            case SOURCE_AI_REVIEW -> "AI review ticket";
            case SOURCE_UPLOAD -> "Uploaded document";
            default -> sourceType != null && sourceType.startsWith("EXTERNAL_") ? sourceType.substring("EXTERNAL_".length()) : "Knowledge base";
        };
    }

    private double sourceWeight(String sourceType) {
        return switch (sourceType) {
            case SOURCE_SPECIES, SOURCE_OBSERVATION, SOURCE_ECOSYSTEM, SOURCE_AI_REPORT -> 1.0d;
            case SOURCE_UPLOAD -> 0.92d;
            case SOURCE_AI_REVIEW -> 0.82d;
            default -> 0.75d;
        };
    }

    private List<Path> scanSupportedFiles(Path folder, boolean recursive) {
        int depth = recursive ? Integer.MAX_VALUE : 1;
        try (Stream<Path> stream = Files.walk(folder, depth)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedPath)
                    .limit(2000)
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Folder scan failed: " + ex.getMessage(), ex);
        }
    }

    private boolean isSupportedPath(Path path) {
        String filename = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.stream().anyMatch(filename::endsWith);
    }

    private void validatePathForIngest(Path file) {
        if (!Files.isRegularFile(file) || !isSupportedPath(file)) {
            throw new IllegalArgumentException("Unsupported file: " + file);
        }
    }

    private String contentTypeFor(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lower.endsWith(".md")) {
            return "text/markdown";
        }
        return "text/plain";
    }

    private List<ExternalRecord> collectExternalRecords(String sourceCode, String query, int limit, List<String> urls) {
        if ("WEB_PDF".equals(sourceCode)) {
            return collectWebDocuments(urls, limit);
        }
        String safeQuery = StringUtils.hasText(query) ? query : "marine biodiversity";
        String encoded = URLEncoder.encode(safeQuery, StandardCharsets.UTF_8);
        return switch (sourceCode) {
            case "OBIS" -> collectExternalJson(sourceCode, "https://api.obis.org/v3/occurrence?scientificname=" + encoded + "&size=" + limit, safeQuery);
            case "GBIF" -> collectExternalJson(sourceCode, "https://api.gbif.org/v1/occurrence/search?scientificName=" + encoded + "&limit=" + limit, safeQuery);
            case "WORMS" -> collectExternalJson(sourceCode, "https://www.marinespecies.org/rest/AphiaRecordsByName/" + encoded + "?like=true&marine_only=true", safeQuery);
            case "IUCN" -> List.of(new ExternalRecord(sourceCode, "IUCN-" + safeQuery, "IUCN query: " + safeQuery,
                    "IUCN Red List import needs an API token. Add official export/PDF via WEB_PDF or configure a collector token later.",
                    "https://api.iucnredlist.org"));
            default -> throw new IllegalArgumentException("Unsupported external source: " + sourceCode);
        };
    }

    private List<ExternalRecord> collectExternalJson(String sourceCode, String url, String query) {
        try {
            String body = restClientBuilder.build()
                    .get()
                    .uri(URI.create(url))
                    .retrieve()
                    .body(String.class);
            String title = sourceCode + " " + query;
            String content = "Source: " + sourceCode + "\nQuery: " + query + "\nURL: " + url + "\nRaw summary:\n" + truncate(body, 24000);
            return List.of(new ExternalRecord(sourceCode, sourceCode + "-" + Integer.toHexString(url.hashCode()), title, content, url));
        } catch (RuntimeException ex) {
            throw new IllegalStateException(sourceCode + " collection failed: " + ex.getMessage(), ex);
        }
    }

    private List<ExternalRecord> collectWebDocuments(List<String> urls, int limit) {
        if (urls == null || urls.isEmpty()) {
            throw new IllegalArgumentException("WEB_PDF needs one or more urls");
        }
        return urls.stream()
                .filter(StringUtils::hasText)
                .limit(limit)
                .map(url -> new ExternalRecord("WEB_PDF", "WEB-" + Integer.toHexString(url.hashCode()), url, fetchWebText(url), url))
                .toList();
    }

    private String fetchWebText(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (SUPPORTED_EXTENSIONS.stream().noneMatch(lower::endsWith)) {
            throw new IllegalArgumentException("Only PDF/DOCX/TXT/MD urls are supported: " + url);
        }
        ensureAllowedDomain(url);
        try {
            byte[] bytes = restClientBuilder.build()
                    .get()
                    .uri(URI.create(url))
                    .retrieve()
                    .body(byte[].class);
            String filename = Path.of(URI.create(url).getPath()).getFileName().toString();
            return textExtractor.extract(bytes == null ? new byte[0] : bytes, filename, contentTypeFor(filename));
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Web document fetch failed: " + ex.getMessage(), ex);
        }
    }

    private void ensureAllowedDomain(String url) {
        List<String> allowedDomains = ragProperties.ingest().allowedDomains();
        if (allowedDomains == null || allowedDomains.isEmpty()) {
            return;
        }
        String host = URI.create(url).getHost();
        boolean allowed = allowedDomains.stream()
                .filter(StringUtils::hasText)
                .anyMatch(domain -> host != null && (host.equalsIgnoreCase(domain) || host.toLowerCase(Locale.ROOT).endsWith("." + domain.toLowerCase(Locale.ROOT))));
        if (!allowed) {
            throw new IllegalArgumentException("URL domain is not in gsmv.rag.ingest.allowed-domains: " + host);
        }
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请先选择需要上传的知识文档");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        boolean supported = filename.endsWith(".pdf") || filename.endsWith(".docx")
                || filename.endsWith(".txt") || filename.endsWith(".md");
        if (!supported) {
            throw new IllegalArgumentException("知识库仅支持 PDF、DOCX、TXT、MD 文件");
        }
    }

    private String cleanTitle(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "上传知识文档";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeRequired(String value) {
        String normalized = normalizeNullable(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("Value is required");
        }
        return normalized;
    }

    private String embeddingModel() {
        return StringUtils.hasText(ragProperties.embedding().model()) ? ragProperties.embedding().model() : "text-embedding-v4";
    }

    private int embeddingDimension() {
        return ragProperties.embedding().dimension() == null ? 1024 : ragProperties.embedding().dimension();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String nullableSuffix(String label, Object value) {
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? "" : "，" + label + "：" + value;
    }

    private String readableError(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getMessage() == null) {
            current = current.getCause();
        }
        return StringUtils.hasText(current.getMessage()) ? current.getMessage() : current.getClass().getSimpleName();
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record IndexOutcome(boolean success, int chunkCount, String message) {
    }

    private record ExternalRecord(String sourceCode, String externalId, String title, String content, String sourceUrl) {
    }
}
