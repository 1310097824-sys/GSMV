package com.gsmv.ai.rag;

import com.gsmv.ai.rag.dto.RagDtos;
import com.gsmv.common.ApiResponse;
import com.gsmv.common.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ai/rag")
public class RagKnowledgeController {

    private final RagKnowledgeService ragKnowledgeService;

    public RagKnowledgeController(RagKnowledgeService ragKnowledgeService) {
        this.ragKnowledgeService = ragKnowledgeService;
    }

    @GetMapping("/documents")
    @PreAuthorize("hasAuthority('RAG_READ')")
    public ApiResponse<PageResponse<RagDtos.RagDocumentView>> listDocuments(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(ragKnowledgeService.listDocuments(keyword, sourceType, status, page, size));
    }

    @PostMapping("/documents/upload")
    @PreAuthorize("hasAuthority('RAG_MANAGE')")
    public ApiResponse<RagDtos.RagDocumentDetailView> uploadDocument(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(ragKnowledgeService.uploadDocument(file));
    }

    @PostMapping("/ingest/files")
    @PreAuthorize("hasAuthority('RAG_MANAGE')")
    public ApiResponse<RagDtos.RagIngestJobView> ingestFiles(@RequestParam("files") List<MultipartFile> files) {
        return ApiResponse.success(ragKnowledgeService.ingestFiles(files));
    }

    @PostMapping("/ingest/folder")
    @PreAuthorize("hasAuthority('RAG_MANAGE')")
    public ApiResponse<RagDtos.RagIngestJobView> ingestFolder(@Valid @RequestBody RagDtos.FolderIngestRequest request) {
        return ApiResponse.success(ragKnowledgeService.ingestFolder(request));
    }

    @PostMapping("/ingest/external")
    @PreAuthorize("hasAuthority('RAG_MANAGE')")
    public ApiResponse<RagDtos.RagIngestJobView> ingestExternal(@Valid @RequestBody RagDtos.ExternalIngestRequest request) {
        return ApiResponse.success(ragKnowledgeService.ingestExternal(request));
    }

    @GetMapping("/documents/{id}")
    @PreAuthorize("hasAuthority('RAG_READ')")
    public ApiResponse<RagDtos.RagDocumentDetailView> getDocument(@PathVariable Long id) {
        return ApiResponse.success(ragKnowledgeService.getDocument(id));
    }

    @DeleteMapping("/documents/{id}")
    @PreAuthorize("hasAuthority('RAG_MANAGE')")
    public ApiResponse<Void> deleteDocument(@PathVariable Long id) {
        ragKnowledgeService.deleteDocument(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/rebuild")
    @PreAuthorize("hasAuthority('RAG_MANAGE')")
    public ApiResponse<RagDtos.RagIndexJobView> rebuild() {
        return ApiResponse.success(ragKnowledgeService.rebuildAll());
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasAuthority('RAG_READ')")
    public ApiResponse<PageResponse<RagDtos.RagIndexJobView>> listJobs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(ragKnowledgeService.listJobs(page, size));
    }

    @GetMapping("/ingest/jobs")
    @PreAuthorize("hasAuthority('RAG_READ')")
    public ApiResponse<PageResponse<RagDtos.RagIngestJobView>> listIngestJobs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(ragKnowledgeService.listIngestJobs(page, size));
    }

    @GetMapping("/ingest/jobs/{id}/items")
    @PreAuthorize("hasAuthority('RAG_READ')")
    public ApiResponse<List<RagDtos.RagIngestItemView>> listIngestItems(@PathVariable Long id) {
        return ApiResponse.success(ragKnowledgeService.listIngestItems(id));
    }

    @PostMapping("/ingest/jobs/{id}/retry")
    @PreAuthorize("hasAuthority('RAG_MANAGE')")
    public ApiResponse<RagDtos.RagIngestJobView> retryIngestJob(@PathVariable Long id) {
        return ApiResponse.success(ragKnowledgeService.retryIngestJob(id));
    }

    @GetMapping("/sources")
    @PreAuthorize("hasAuthority('RAG_READ')")
    public ApiResponse<List<RagDtos.RagSourceView>> listSources() {
        return ApiResponse.success(ragKnowledgeService.listSources());
    }

    @GetMapping("/qdrant/status")
    @PreAuthorize("hasAuthority('RAG_READ')")
    public ApiResponse<RagDtos.QdrantStatusView> qdrantStatus() {
        return ApiResponse.success(ragKnowledgeService.qdrantStatus());
    }

    @PostMapping("/qdrant/rebuild")
    @PreAuthorize("hasAuthority('RAG_MANAGE')")
    public ApiResponse<RagDtos.QdrantStatusView> rebuildQdrant() {
        return ApiResponse.success(ragKnowledgeService.rebuildQdrant());
    }

    @PostMapping("/search-test")
    @PreAuthorize("hasAuthority('RAG_READ')")
    public ApiResponse<List<RagDtos.RagSearchResultView>> searchTest(@Valid @RequestBody RagDtos.SearchTestRequest request) {
        int limit = request.limit() == null ? 8 : request.limit();
        return ApiResponse.success(ragKnowledgeService.searchForView(request.query(), limit));
    }
}
