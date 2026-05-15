package com.gsmv.ai.review;

import com.gsmv.ai.review.dto.AiReviewTicketDtos;
import com.gsmv.common.ApiResponse;
import com.gsmv.common.PageResponse;
import com.gsmv.media.MediaFileService;
import com.gsmv.media.model.MediaFile;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ai/review-tickets")
public class AiReviewTicketController {

    private final AiReviewTicketService ticketService;
    private final MediaFileService mediaFileService;

    public AiReviewTicketController(AiReviewTicketService ticketService, MediaFileService mediaFileService) {
        this.ticketService = ticketService;
        this.mediaFileService = mediaFileService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<AiReviewTicketDtos.ReviewTicketDetailView> createTicket(
            @RequestPart("payload") @Valid AiReviewTicketDtos.CreateReviewTicketRequest request,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(ticketService.createTicket(request, file));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<AiReviewTicketDtos.ReviewTicketView>> listTickets(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(ticketService.listTickets(keyword, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<AiReviewTicketDtos.ReviewTicketDetailView> getTicket(@PathVariable Long id) {
        return ApiResponse.success(ticketService.getTicket(id));
    }

    @PostMapping("/{id}/start-review")
    @PreAuthorize("hasAuthority('AI_REVIEW_WRITE')")
    public ApiResponse<AiReviewTicketDtos.ReviewTicketDetailView> startReview(@PathVariable Long id) {
        return ApiResponse.success(ticketService.startReview(id));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('AI_REVIEW_WRITE')")
    public ApiResponse<AiReviewTicketDtos.ReviewTicketDetailView> resolveTicket(
            @PathVariable Long id,
            @RequestBody @Valid AiReviewTicketDtos.ResolveReviewTicketRequest request
    ) {
        return ApiResponse.success(ticketService.resolveTicket(id, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('AI_REVIEW_WRITE')")
    public ApiResponse<AiReviewTicketDtos.ReviewTicketDetailView> rejectTicket(
            @PathVariable Long id,
            @RequestBody @Valid AiReviewTicketDtos.RejectReviewTicketRequest request
    ) {
        return ApiResponse.success(ticketService.rejectTicket(id, request));
    }

    @PostMapping("/{id}/resubmit")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<AiReviewTicketDtos.ReviewTicketDetailView> resubmitTicket(
            @PathVariable Long id,
            @RequestBody AiReviewTicketDtos.ResubmitReviewTicketRequest request
    ) {
        return ApiResponse.success(ticketService.resubmitTicket(id, request));
    }

    @PostMapping("/{id}/link-species")
    @PreAuthorize("hasAuthority('AI_REVIEW_WRITE')")
    public ApiResponse<AiReviewTicketDtos.ReviewTicketDetailView> linkSpecies(
            @PathVariable Long id,
            @RequestBody @Valid AiReviewTicketDtos.LinkSpeciesRequest request
    ) {
        return ApiResponse.success(ticketService.linkSpecies(id, request));
    }

    @GetMapping("/images/{mediaId}")
    public ResponseEntity<byte[]> image(@PathVariable Long mediaId) {
        MediaFile mediaFile = ticketService.getReviewImage(mediaId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaFile.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + mediaFile.getStoredFilename() + "\"")
                .cacheControl(CacheControl.noCache())
                .body(mediaFileService.readBytes(mediaFile));
    }
}
