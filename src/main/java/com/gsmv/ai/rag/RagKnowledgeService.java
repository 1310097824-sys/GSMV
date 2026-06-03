package com.gsmv.ai.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.web.client.RestClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
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
        qdrantVectorClient.deleteByDocumentId(id);
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
        qdrantVectorClient.recreateCollection();
        removeReviewTicketsFromRag();
        assistantQueryCache.invalidateAll();
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
        for (RagDocument upload : documentMapper.findUploadedDocuments(5000)) {
            IndexOutcome outcome = reindexUploaded(upload);
            totalDocs++;
            totalChunks += outcome.chunkCount();
            if (outcome.success()) success++; else { failed++; lastError = outcome.message(); }
        }
        for (RagDocument external : documentMapper.findExternalDocuments(5000)) {
            IndexOutcome outcome = reindexExternal(external);
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
        qdrantVectorClient.recreateCollection();
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
            } else if (isExternalIngestItem(failed) && StringUtils.hasText(failed.getSourceUrl())) {
                processExternalRetryItem(retry, item);
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
            if (!Integer.valueOf(1).equals(row.status())) {
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
        if (id != null) {
            markSourceDeleted(SOURCE_AI_REVIEW, id);
        }
    }

    public void markSourceDeleted(String sourceType, Long sourceId) {
        RagDocument document = documentMapper.findBySource(sourceType, sourceId);
        if (document != null) {
            documentMapper.markDeleted(document.getId());
            chunkMapper.markDeletedByDocumentId(document.getId());
        }
    }

    private void removeReviewTicketsFromRag() {
        for (RagDocument document : documentMapper.findActiveBySourceType(SOURCE_AI_REVIEW, 5000)) {
            qdrantVectorClient.deleteByDocumentId(document.getId());
            documentMapper.markDeleted(document.getId());
            chunkMapper.markDeletedByDocumentId(document.getId());
        }
        for (AiReviewTicket ticket : aiReviewTicketMapper.findPage(null, null, null, 5000, 0)) {
            markSourceDeleted(SOURCE_AI_REVIEW, ticket.getId());
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

    private IndexOutcome reindexExternal(RagDocument document) {
        try {
            List<RagChunk> existingChunks = chunkMapper.findByDocumentId(document.getId());
            if (existingChunks.isEmpty()) {
                throw new IllegalStateException("外部导入文档缺少可重建内容");
            }
            StringBuilder text = new StringBuilder();
            for (RagChunk chunk : existingChunks) {
                if (StringUtils.hasText(chunk.getContent())) {
                    if (!text.isEmpty()) {
                        text.append("\n\n");
                    }
                    text.append(chunk.getContent());
                }
            }
            int chunks = indexDocumentContent(document, document.getTitle(), text.toString(), false);
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
                item.setMetadataJson(writeJson(Map.of(
                        "duplicateExternalId", record.externalId(),
                        "sourceUrl", safe(record.sourceUrl())
                )));
                markIngestItemSuccess(job, item);
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

    private void processExternalRetryItem(RagIngestJob job, RagIngestItem item) {
        try {
            processExternalRecordItem(job, item, refetchExternalRecord(item));
        } catch (RuntimeException ex) {
            markIngestItemFailed(job, item, readableError(ex));
        }
    }

    private ExternalRecord refetchExternalRecord(RagIngestItem item) {
        String sourceCode = firstNonBlank(item.getSourceCode(), item.getSourceType(), "EXTERNAL").toUpperCase(Locale.ROOT);
        String sourceUrl = normalizeRequired(item.getSourceUrl());
        String externalId = firstNonBlank(item.getExternalId(), sourceCode + "-" + Integer.toHexString(sourceUrl.hashCode()));
        String title = firstNonBlank(item.getTitle(), sourceCode + " " + externalId);
        String content;
        if ("WEB_PDF".equals(sourceCode)) {
            content = fetchWebText(sourceUrl);
        } else if ("OBIS".equals(sourceCode) || "GBIF".equals(sourceCode) || "WORMS".equals(sourceCode)) {
            ExternalQuery query = resolveExternalQuery(firstNonBlank(item.getTitle(), item.getExternalId(), sourceCode));
            List<ExternalRecord> records = collectExternalJson(sourceCode, sourceUrl, query, 10);
            if (records.isEmpty()) {
                throw new IllegalStateException(sourceCode + " retry returned no structured records");
            }
            return records.get(0);
        } else {
            String body = restClientBuilder.build()
                    .get()
                    .uri(URI.create(sourceUrl))
                    .retrieve()
                    .body(String.class);
            content = "Source: " + sourceCode + "\nURL: " + sourceUrl + "\nRaw summary:\n" + truncate(body, 24000);
        }
        return new ExternalRecord(sourceCode, externalId, title, content, sourceUrl);
    }

    private boolean isExternalIngestItem(RagIngestItem item) {
        return safe(item.getSourceType()).startsWith("EXTERNAL") || safe(item.getSourceCode()).startsWith("OBIS")
                || "GBIF".equalsIgnoreCase(item.getSourceCode())
                || "WORMS".equalsIgnoreCase(item.getSourceCode())
                || "IUCN".equalsIgnoreCase(item.getSourceCode())
                || "WEB_PDF".equalsIgnoreCase(item.getSourceCode());
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
        return "/rag-knowledge?document=" + chunk.getDocumentId();
    }

    private String sourceName(String sourceType) {
        return switch (safe(sourceType)) {
            case SOURCE_SPECIES -> "Species archive";
            case SOURCE_OBSERVATION -> "Observation record";
            case SOURCE_ECOSYSTEM -> "Ecosystem";
            case SOURCE_AI_REPORT -> "AI research report";
            case SOURCE_UPLOAD -> "Uploaded document";
            default -> sourceType != null && sourceType.startsWith("EXTERNAL_") ? sourceType.substring("EXTERNAL_".length()) : "Knowledge base";
        };
    }

    private double sourceWeight(String sourceType) {
        return switch (sourceType) {
            case SOURCE_SPECIES, SOURCE_OBSERVATION, SOURCE_ECOSYSTEM, SOURCE_AI_REPORT -> 1.0d;
            case SOURCE_UPLOAD -> 0.92d;
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
        ExternalQuery externalQuery = resolveExternalQuery(query);
        String encoded = URLEncoder.encode(externalQuery.lookupQuery(), StandardCharsets.UTF_8);
        return switch (sourceCode) {
            case "OBIS" -> collectExternalJson(sourceCode, "https://api.obis.org/v3/occurrence?scientificname=" + encoded + "&size=" + limit, externalQuery, limit);
            case "GBIF" -> collectExternalJson(sourceCode, "https://api.gbif.org/v1/occurrence/search?scientificName=" + encoded + "&limit=" + limit, externalQuery, limit);
            case "WORMS" -> collectExternalJson(sourceCode, "https://www.marinespecies.org/rest/AphiaRecordsByName/" + encoded + "?like=true&marine_only=true", externalQuery, limit);
            case "IUCN" -> collectIucnRecords(externalQuery, limit);
            default -> throw new IllegalArgumentException("Unsupported external source: " + sourceCode);
        };
    }

    private List<ExternalRecord> collectIucnRecords(ExternalQuery query, int limit) {
        String apiToken = ragProperties.iucn() == null ? "" : ragProperties.iucn().apiToken();
        if (!StringUtils.hasText(apiToken)) {
            throw new IllegalStateException("IUCN_API_TOKEN 未配置，无法导入 IUCN Red List API 数据");
        }

        String scientificName = firstNonBlank(query.scientificName(), query.lookupQuery());
        String[] nameParts = scientificName.trim().split("\\s+");
        if (nameParts.length < 2 || containsCjk(scientificName)) {
            throw new IllegalStateException("IUCN 导入需要物种学名，例如 Sousa chinensis。当前查询：" + query.originalQuery());
        }

        String genusName = nameParts[0];
        String speciesName = nameParts[1];
        JsonNode taxaResponse = readIucnJson(
                "/taxa/scientific_name?genus_name=" + encodeUrl(genusName) + "&species_name=" + encodeUrl(speciesName),
                apiToken
        );
        JsonNode assessments = taxaResponse.path("assessments");
        if (!assessments.isArray() || assessments.isEmpty()) {
            throw new IllegalStateException("IUCN 未找到评估记录：" + scientificName);
        }

        List<JsonNode> selectedAssessments = new ArrayList<>();
        for (JsonNode assessment : assessments) {
            if (assessment.path("latest").asBoolean(false)) {
                selectedAssessments.add(assessment);
            }
        }
        for (JsonNode assessment : assessments) {
            if (selectedAssessments.size() >= Math.max(1, limit)) {
                break;
            }
            if (!assessment.path("latest").asBoolean(false)) {
                selectedAssessments.add(assessment);
            }
        }

        List<ExternalRecord> records = new ArrayList<>();
        for (JsonNode assessmentSummary : selectedAssessments.stream().limit(Math.max(1, limit)).toList()) {
            long assessmentId = assessmentSummary.path("assessment_id").asLong(0L);
            if (assessmentId <= 0L) {
                continue;
            }
            JsonNode assessmentDetail = readIucnJson("/assessment/" + assessmentId, apiToken);
            records.add(new ExternalRecord(
                    "IUCN",
                    "IUCN-" + assessmentId,
                    buildIucnTitle(query, assessmentSummary, assessmentDetail),
                    buildIucnContent(query, assessmentSummary, assessmentDetail),
                    firstNonBlank(textAt(assessmentDetail, "url"), textAt(assessmentSummary, "url"), "https://www.iucnredlist.org")
            ));
        }
        if (records.isEmpty()) {
            throw new IllegalStateException("IUCN 返回了评估列表，但没有可导入的 assessment_id：" + scientificName);
        }
        return records;
    }

    private JsonNode readIucnJson(String pathAndQuery, String apiToken) {
        String baseUrl = firstNonBlank(ragProperties.iucn() == null ? null : ragProperties.iucn().baseUrl(), "https://api.iucnredlist.org/api/v4")
                .replaceAll("/+$", "");
        try {
            String body = restClientBuilder.build()
                    .get()
                    .uri(URI.create(baseUrl + pathAndQuery))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body);
        } catch (RuntimeException | JsonProcessingException ex) {
            throw new IllegalStateException("IUCN collection failed: " + readableError(ex), ex);
        }
    }

    private String buildIucnTitle(ExternalQuery query, JsonNode summary, JsonNode detail) {
        String categoryCode = firstNonBlank(
                textAt(detail, "red_list_category", "code"),
                textAt(summary, "red_list_category_code")
        );
        String year = firstNonBlank(textAt(detail, "year_published"), textAt(summary, "year_published"));
        return "IUCN Red List: " + query.displayName()
                + nullableSuffix("等级", categoryCode)
                + nullableSuffix("年份", year);
    }

    private String buildIucnContent(ExternalQuery query, JsonNode summary, JsonNode detail) {
        String categoryCode = firstNonBlank(textAt(detail, "red_list_category", "code"), textAt(summary, "red_list_category_code"));
        String categoryName = textAt(detail, "red_list_category", "description", "en");
        String populationTrend = textAt(detail, "population_trend", "description", "en");
        String scientificName = firstNonBlank(textAt(detail, "taxon", "scientific_name"), query.scientificName(), query.lookupQuery());
        String commonNames = joinJsonValues(detail.path("taxon").path("common_names"), 8, "name");
        String locations = joinJsonValues(detail.path("locations"), 30, "description", "en");
        String systems = joinJsonValues(detail.path("systems"), 10, "description", "en");
        String habitats = joinJsonValues(detail.path("habitats"), 20, "description", "en");
        String threats = joinJsonValues(detail.path("threats"), 24, "description", "en");
        String conservationActions = joinJsonValues(detail.path("conservation_actions"), 24, "description", "en");
        String references = joinJsonValues(detail.path("references"), 8, "citation_short");
        if (!StringUtils.hasText(references)) {
            references = joinJsonValues(detail.path("references"), 5, "citation");
        }

        return String.join("\n", List.of(
                "Source: IUCN Red List API",
                externalQueryContext(query),
                "Assessment ID: " + textAt(detail, "assessment_id"),
                "SIS taxon ID: " + textAt(detail, "sis_taxon_id"),
                "Scientific name: " + scientificName,
                "Common names: " + firstNonBlank(commonNames, "N/A"),
                "Red List category: " + firstNonBlank(categoryCode, "N/A") + nullableSuffix("说明", categoryName),
                "Criteria: " + firstNonBlank(textAt(detail, "criteria"), "N/A"),
                "Published year: " + firstNonBlank(textAt(detail, "year_published"), textAt(summary, "year_published"), "N/A"),
                "Assessment date: " + firstNonBlank(textAt(detail, "assessment_date"), "N/A"),
                "Latest assessment: " + detail.path("latest").asBoolean(summary.path("latest").asBoolean(false)),
                "Population trend: " + firstNonBlank(populationTrend, "N/A"),
                "Systems: " + firstNonBlank(systems, "N/A"),
                "Countries/locations: " + firstNonBlank(locations, "N/A"),
                "Structured habitats: " + firstNonBlank(habitats, "N/A"),
                "Structured threats: " + firstNonBlank(threats, "N/A"),
                "Conservation actions: " + firstNonBlank(conservationActions, "N/A"),
                "Range: " + truncate(cleanIucnText(textAt(detail, "documentation", "range")), 5000),
                "Population: " + truncate(cleanIucnText(textAt(detail, "documentation", "population")), 5000),
                "Habitat and ecology: " + truncate(cleanIucnText(textAt(detail, "documentation", "habitats")), 5000),
                "Major threats: " + truncate(cleanIucnText(textAt(detail, "documentation", "threats")), 5000),
                "Conservation measures: " + truncate(cleanIucnText(textAt(detail, "documentation", "measures")), 5000),
                "Rationale: " + truncate(cleanIucnText(textAt(detail, "documentation", "rationale")), 5000),
                "Citation: " + firstNonBlank(cleanIucnText(textAt(detail, "citation")), "IUCN Red List of Threatened Species"),
                "References: " + firstNonBlank(references, "N/A"),
                "URL: " + firstNonBlank(textAt(detail, "url"), textAt(summary, "url"), "https://www.iucnredlist.org")
        ));
    }

    private List<ExternalRecord> collectExternalJson(String sourceCode, String url, ExternalQuery query, int limit) {
        String body;
        try {
            body = restClientBuilder.build()
                    .get()
                    .uri(URI.create(url))
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException ex) {
            throw new IllegalStateException(sourceCode + " collection request failed: " + ex.getMessage(), ex);
        }
        if (!StringUtils.hasText(body)) {
            throw new IllegalStateException(sourceCode + " 未返回可解析内容："
                    + query.lookupQuery()
                    + unresolvedQueryHint(query));
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            List<ExternalRecord> records = switch (sourceCode) {
                case "WORMS" -> buildWormsRecords(root, query, limit, url);
                case "GBIF" -> buildGbifRecords(root, query, limit, url);
                case "OBIS" -> buildObisRecords(root, query, limit, url);
                default -> List.of();
            };
            if (records.isEmpty()) {
                throw new IllegalStateException(sourceCode + " 未找到可导入的结构化记录："
                        + query.lookupQuery()
                        + unresolvedQueryHint(query));
            }
            return records;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(sourceCode + " returned non-JSON content", ex);
        }
    }

    private List<ExternalRecord> buildWormsRecords(JsonNode root, ExternalQuery query, int limit, String requestUrl) {
        JsonNode items = arrayAt(root, "value", "results");
        if (!items.isArray() || items.isEmpty()) {
            return List.of();
        }

        List<ExternalRecord> records = new ArrayList<>();
        int max = Math.max(1, limit);
        for (JsonNode item : items) {
            if (records.size() >= max) {
                break;
            }
            String aphiaId = textAt(item, "AphiaID");
            String scientificName = firstNonBlank(textAt(item, "scientificname"), textAt(item, "valid_name"));
            if (!StringUtils.hasText(aphiaId) && !StringUtils.hasText(scientificName)) {
                continue;
            }

            String sourceUrl = firstNonBlank(textAt(item, "url"), requestUrl);
            String titleName = firstNonBlank(query.chineseName(), scientificName, query.displayName());
            String title = "WORMS " + titleName;
            List<String> lines = new ArrayList<>();
            addLine(lines, "Source", "WoRMS REST API");
            addBlock(lines, externalQueryContext(query));
            addLine(lines, "AphiaID", aphiaId);
            addLine(lines, "Scientific name", scientificName);
            addLine(lines, "Authority", textAt(item, "authority"));
            addLine(lines, "Status", textAt(item, "status"));
            addLine(lines, "Valid name", textAt(item, "valid_name"));
            addLine(lines, "Valid AphiaID", textAt(item, "valid_AphiaID"));
            addLine(lines, "Rank", textAt(item, "rank"));
            addLine(lines, "Taxonomy", joinNonBlank(" > ",
                    textAt(item, "kingdom"),
                    textAt(item, "phylum"),
                    textAt(item, "class"),
                    textAt(item, "order"),
                    textAt(item, "family"),
                    textAt(item, "genus")));
            addLine(lines, "Environment flags", wormsEnvironment(item));
            addLine(lines, "Citation", cleanIucnText(textAt(item, "citation")));
            addLine(lines, "URL", sourceUrl);

            records.add(new ExternalRecord(
                    "WORMS",
                    "WORMS-" + firstNonBlank(aphiaId, Integer.toHexString((requestUrl + scientificName).hashCode())),
                    title,
                    String.join("\n", lines),
                    sourceUrl
            ));
        }
        return records;
    }

    private List<ExternalRecord> buildGbifRecords(JsonNode root, ExternalQuery query, int limit, String requestUrl) {
        JsonNode items = arrayAt(root, "results");
        if (!items.isArray() || items.isEmpty()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        addLine(lines, "Source", "GBIF Occurrence API");
        addBlock(lines, externalQueryContext(query));
        addLine(lines, "Matched occurrence count", textAt(root, "count"));
        addLine(lines, "API URL", requestUrl);
        addBlock(lines, "Occurrence samples:");

        int max = Math.max(1, Math.min(limit, 20));
        int index = 0;
        for (JsonNode item : items) {
            if (index >= max) {
                break;
            }
            String sample = occurrenceSample(item, index + 1);
            if (StringUtils.hasText(sample)) {
                lines.add(sample);
                index++;
            }
        }
        if (index == 0) {
            return List.of();
        }

        return List.of(new ExternalRecord(
                "GBIF",
                "GBIF-" + Integer.toHexString((requestUrl + query.lookupQuery()).hashCode()),
                "GBIF " + query.displayName() + " occurrence summary",
                String.join("\n", lines),
                requestUrl
        ));
    }

    private List<ExternalRecord> buildObisRecords(JsonNode root, ExternalQuery query, int limit, String requestUrl) {
        JsonNode items = arrayAt(root, "results", "features");
        if (!items.isArray() || items.isEmpty()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        addLine(lines, "Source", "OBIS Occurrence API");
        addBlock(lines, externalQueryContext(query));
        addLine(lines, "Matched occurrence count", firstNonBlank(textAt(root, "total"), textAt(root, "count"), textAt(root, "total_records")));
        addLine(lines, "API URL", requestUrl);
        addBlock(lines, "Occurrence samples:");

        int max = Math.max(1, Math.min(limit, 20));
        int index = 0;
        for (JsonNode rawItem : items) {
            if (index >= max) {
                break;
            }
            JsonNode item = rawItem.has("properties") ? rawItem.path("properties") : rawItem;
            String sample = occurrenceSample(item, index + 1);
            if (StringUtils.hasText(sample)) {
                lines.add(sample);
                index++;
            }
        }
        if (index == 0) {
            return List.of();
        }

        return List.of(new ExternalRecord(
                "OBIS",
                "OBIS-" + Integer.toHexString((requestUrl + query.lookupQuery()).hashCode()),
                "OBIS " + query.displayName() + " occurrence summary",
                String.join("\n", lines),
                requestUrl
        ));
    }

    private String occurrenceSample(JsonNode item, int index) {
        String scientificName = firstNonBlank(
                textAt(item, "scientificName"),
                textAt(item, "scientificname"),
                textAt(item, "acceptedScientificName"),
                textAt(item, "species")
        );
        String location = joinNonBlank(", ",
                textAt(item, "country"),
                textAt(item, "stateProvince"),
                textAt(item, "locality"));
        String coordinates = joinNonBlank(", ",
                firstNonBlank(textAt(item, "decimalLatitude"), textAt(item, "decimallatitude")),
                firstNonBlank(textAt(item, "decimalLongitude"), textAt(item, "decimallongitude")));
        String date = firstNonBlank(textAt(item, "eventDate"), textAt(item, "date_year"), textAt(item, "yearcollected"));
        String dataset = firstNonBlank(textAt(item, "datasetName"), textAt(item, "dataset_id"), textAt(item, "dataset"));
        String basis = firstNonBlank(textAt(item, "basisOfRecord"), textAt(item, "basisofrecord"));
        String key = firstNonBlank(textAt(item, "key"), textAt(item, "id"), textAt(item, "occurrenceID"), textAt(item, "occurrenceid"));

        List<String> parts = new ArrayList<>();
        addPart(parts, "record", key);
        addPart(parts, "scientificName", scientificName);
        addPart(parts, "location", location);
        addPart(parts, "coordinates", coordinates);
        addPart(parts, "date", date);
        addPart(parts, "dataset", dataset);
        addPart(parts, "basis", basis);
        if (parts.isEmpty()) {
            return "";
        }
        return index + ". " + String.join("; ", parts);
    }

    private ExternalQuery resolveExternalQuery(String query) {
        String originalQuery = StringUtils.hasText(query) ? query.trim() : "marine biodiversity";
        SpeciesRow matchedSpecies = findBestSpeciesMatch(originalQuery);
        if (matchedSpecies != null && StringUtils.hasText(matchedSpecies.scientificName())) {
            String displayName = StringUtils.hasText(matchedSpecies.chineseName())
                    ? matchedSpecies.chineseName() + " / " + matchedSpecies.scientificName()
                    : matchedSpecies.scientificName();
            return new ExternalQuery(
                    originalQuery,
                    matchedSpecies.scientificName().trim(),
                    displayName,
                    matchedSpecies.chineseName(),
                    matchedSpecies.scientificName(),
                    true,
                    "LOCAL_SPECIES",
                    "Resolved by local species archive"
            );
        }
        ExternalQuery aliasResolved = resolveExternalQueryWithAlias(originalQuery);
        if (aliasResolved != null) {
            return aliasResolved;
        }
        if (containsCjk(originalQuery)) {
            ExternalQuery modelResolved = resolveExternalQueryWithModel(originalQuery);
            if (modelResolved != null) {
                return modelResolved;
            }
        }
        return new ExternalQuery(
                originalQuery,
                originalQuery,
                originalQuery,
                null,
                null,
                false,
                "UNRESOLVED",
                containsCjk(originalQuery)
                        ? "No matching local species or reliable model-resolved scientific name was found; the original query is kept only for error reporting."
                        : ""
        );
    }

    private ExternalQuery resolveExternalQueryWithAlias(String originalQuery) {
        String normalized = normalizeNameToken(originalQuery);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        Map<String, CommonNameAlias> aliases = Map.of(
                "三文鱼", new CommonNameAlias("三文鱼", "Salmo salar", "Common Chinese seafood name; resolved to Atlantic salmon for external biodiversity APIs"),
                "大西洋鲑", new CommonNameAlias("大西洋鲑", "Salmo salar", "Chinese common name for Atlantic salmon")
        );
        for (Map.Entry<String, CommonNameAlias> entry : aliases.entrySet()) {
            String aliasKey = normalizeNameToken(entry.getKey());
            if (normalized.equals(aliasKey) || normalized.contains(aliasKey) || aliasKey.contains(normalized)) {
                CommonNameAlias alias = entry.getValue();
                return new ExternalQuery(
                        originalQuery,
                        alias.scientificName(),
                        alias.chineseName() + " / " + alias.scientificName(),
                        alias.chineseName(),
                        alias.scientificName(),
                        true,
                        "COMMON_NAME_ALIAS",
                        alias.note()
                );
            }
        }
        return null;
    }

    private ExternalQuery resolveExternalQueryWithModel(String originalQuery) {
        try {
            JsonNode result = aiModelGateway.deepSeekJson(List.of(
                    AiModelGateway.message("system", """
                            You convert Chinese marine organism common names into scientific names for biodiversity APIs.
                            Return JSON only with fields: chineseName, scientificName, confidence, reason.
                            If the name is ambiguous, choose the most common biological taxon and lower confidence.
                            If no credible Latin binomial can be inferred, return an empty scientificName and confidence 0.
                            """),
                    AiModelGateway.message("user", "Chinese query: " + originalQuery)
            ));
            String scientificName = normalizeScientificNameCandidate(firstNonBlank(
                    textAt(result, "scientificName"),
                    textAt(result, "latinName"),
                    textAt(result, "binomial")
            ));
            double confidence = result.path("confidence").asDouble(0.0d);
            if (!looksLikeScientificName(scientificName) || confidence < 0.45d) {
                return null;
            }
            String chineseName = firstNonBlank(textAt(result, "chineseName"), originalQuery);
            String displayName = chineseName + " / " + scientificName;
            String reason = textAt(result, "reason");
            String note = "Resolved by AI common-name parser; confidence="
                    + String.format(Locale.ROOT, "%.2f", confidence)
                    + (StringUtils.hasText(reason) ? "; reason=" + reason : "");
            return new ExternalQuery(
                    originalQuery,
                    scientificName,
                    displayName,
                    chineseName,
                    scientificName,
                    false,
                    "AI_COMMON_NAME",
                    note
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private SpeciesRow findBestSpeciesMatch(String query) {
        if (!StringUtils.hasText(query)) {
            return null;
        }
        try {
            List<SpeciesRow> matches = speciesMapper.findPage(query.trim(), null, null, null, null, null, 20, 0);
            if (matches == null || matches.isEmpty()) {
                return null;
            }
            String normalizedQuery = normalizeNameToken(query);
            for (SpeciesRow row : matches) {
                if (normalizedQuery.equals(normalizeNameToken(row.chineseName()))
                        || normalizedQuery.equals(normalizeNameToken(row.scientificName()))) {
                    return row;
                }
            }
            for (SpeciesRow row : matches) {
                String chineseName = normalizeNameToken(row.chineseName());
                if (StringUtils.hasText(chineseName) && (chineseName.contains(normalizedQuery) || normalizedQuery.contains(chineseName))) {
                    return row;
                }
            }
            return matches.get(0);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String externalQueryContext(ExternalQuery query) {
        StringBuilder builder = new StringBuilder();
        builder.append("Original query: ").append(query.originalQuery()).append('\n');
        builder.append("Lookup query: ").append(query.lookupQuery()).append('\n');
        if (StringUtils.hasText(query.chineseName())) {
            builder.append("Chinese name: ").append(query.chineseName()).append('\n');
        }
        if (StringUtils.hasText(query.scientificName())) {
            builder.append("Scientific name: ").append(query.scientificName()).append('\n');
        }
        if (StringUtils.hasText(query.resolutionSource())) {
            builder.append("Resolution source: ").append(query.resolutionSource()).append('\n');
        }
        if (StringUtils.hasText(query.resolutionNote())) {
            builder.append("Resolution note: ").append(query.resolutionNote()).append('\n');
        }
        return builder.toString().trim();
    }

    private String unresolvedQueryHint(ExternalQuery query) {
        if (!query.resolved() && containsCjk(query.originalQuery())) {
            return "；中文俗名未解析为学名，请先补充本地物种档案，或直接输入学名后重试";
        }
        return "";
    }

    private JsonNode arrayAt(JsonNode root, String... fieldNames) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return objectMapper.createArrayNode();
        }
        if (root.isArray()) {
            return root;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = root.path(fieldName);
            if (value.isArray()) {
                return value;
            }
        }
        return objectMapper.createArrayNode();
    }

    private void addLine(List<String> lines, String label, String value) {
        if (StringUtils.hasText(value)) {
            lines.add(label + ": " + value.trim());
        }
    }

    private void addBlock(List<String> lines, String value) {
        if (StringUtils.hasText(value)) {
            lines.add(value.trim());
        }
    }

    private void addPart(List<String> parts, String label, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(label + "=" + value.trim());
        }
    }

    private String joinNonBlank(String delimiter, String... values) {
        List<String> nonBlank = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                String trimmed = value.trim();
                if (!nonBlank.contains(trimmed)) {
                    nonBlank.add(trimmed);
                }
            }
        }
        return String.join(delimiter, nonBlank);
    }

    private String wormsEnvironment(JsonNode item) {
        List<String> flags = new ArrayList<>();
        addEnvironmentFlag(flags, item, "isMarine", "marine");
        addEnvironmentFlag(flags, item, "isBrackish", "brackish");
        addEnvironmentFlag(flags, item, "isFreshwater", "freshwater");
        addEnvironmentFlag(flags, item, "isTerrestrial", "terrestrial");
        return String.join("; ", flags);
    }

    private void addEnvironmentFlag(List<String> flags, JsonNode item, String field, String label) {
        JsonNode value = item.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return;
        }
        String raw = value.asText("");
        if (!StringUtils.hasText(raw)) {
            return;
        }
        String normalized = switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes" -> "yes";
            case "0", "false", "no" -> "no";
            default -> raw.trim();
        };
        flags.add(label + "=" + normalized);
    }

    private String normalizeScientificNameCandidate(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String candidate = value.replace('_', ' ').trim();
        Matcher matcher = Pattern.compile("\\b([A-Z][a-zA-Z-]+\\s+[a-z][a-zA-Z-]+)\\b").matcher(candidate);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return candidate;
    }

    private boolean looksLikeScientificName(String value) {
        return StringUtils.hasText(value)
                && !containsCjk(value)
                && Pattern.matches("^[A-Z][a-zA-Z-]+\\s+[a-z][a-zA-Z-]+(?:\\s+.*)?$", value.trim());
    }

    private String encodeUrl(String value) {
        return URLEncoder.encode(safe(value), StandardCharsets.UTF_8);
    }

    private String textAt(JsonNode node, String... path) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode current = node;
        for (String segment : path) {
            current = current.path(segment);
            if (current.isMissingNode() || current.isNull()) {
                return "";
            }
        }
        if (current.isValueNode()) {
            return current.asText("");
        }
        return current.toString();
    }

    private String joinJsonValues(JsonNode array, int limit, String... path) {
        if (array == null || !array.isArray()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : array) {
            String value = textAt(item, path);
            value = cleanIucnText(value);
            if (StringUtils.hasText(value) && !values.contains(value)) {
                values.add(value);
            }
            if (values.size() >= limit) {
                break;
            }
        }
        return String.join("; ", values);
    }

    private String cleanIucnText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String cleaned = HtmlUtils.htmlUnescape(value)
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned;
    }

    private String normalizeNameToken(String value) {
        return safe(value)
                .replace(" ", "")
                .replace("　", "")
                .replace("_", "")
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
    }

    private boolean containsCjk(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= '\u4e00' && ch <= '\u9fff') {
                return true;
            }
        }
        return false;
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
        return StringUtils.hasText(ragProperties.embedding().model()) ? ragProperties.embedding().model() : "bge-m3";
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

    private record ExternalQuery(
            String originalQuery,
            String lookupQuery,
            String displayName,
            String chineseName,
            String scientificName,
            boolean resolved,
            String resolutionSource,
            String resolutionNote
    ) {
    }

    private record CommonNameAlias(String chineseName, String scientificName, String note) {
    }

    private record ExternalRecord(String sourceCode, String externalId, String title, String content, String sourceUrl) {
    }
}
