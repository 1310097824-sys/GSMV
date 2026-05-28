package com.gsmv.ai;

import com.gsmv.ai.dto.AssistantAiDtos;
import com.gsmv.ai.history.AssistantChatHistoryService;
import com.gsmv.ai.dto.ObservationAiDtos;
import com.gsmv.ai.dto.SpeciesAiDtos;
import com.gsmv.common.ApiResponse;
import com.gsmv.common.exception.BusinessException;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final SpeciesAiService speciesAiService;
    private final ObservationAiService observationAiService;
    private final AssistantAiService assistantAiService;
    private final AssistantChatHistoryService assistantChatHistoryService;

    public AiController(
            SpeciesAiService speciesAiService,
            ObservationAiService observationAiService,
            AssistantAiService assistantAiService,
            AssistantChatHistoryService assistantChatHistoryService
    ) {
        this.speciesAiService = speciesAiService;
        this.observationAiService = observationAiService;
        this.assistantAiService = assistantAiService;
        this.assistantChatHistoryService = assistantChatHistoryService;
    }

    @PostMapping(value = "/species/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SPECIES_READ')")
    public ApiResponse<SpeciesAiDtos.IdentifyImageResponse> identifySpecies(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(speciesAiService.identifyImage(file));
    }

    @PostMapping("/species/autocomplete")
    @PreAuthorize("hasAuthority('SPECIES_WRITE')")
    public ApiResponse<SpeciesAiDtos.AutocompleteResponse> autocompleteSpecies(
            @Valid @RequestBody SpeciesAiDtos.AutocompleteRequest request
    ) {
        return ApiResponse.success(speciesAiService.autocomplete(request));
    }

    @PostMapping("/species/polish")
    @PreAuthorize("hasAuthority('SPECIES_WRITE')")
    public ApiResponse<SpeciesAiDtos.PolishTextResponse> polishSpeciesText(
            @Valid @RequestBody SpeciesAiDtos.PolishTextRequest request
    ) {
        return ApiResponse.success(speciesAiService.polishText(request));
    }

    @PostMapping("/species/translate")
    @PreAuthorize("hasAuthority('SPECIES_READ')")
    public ApiResponse<SpeciesAiDtos.TranslateSpeciesResponse> translateSpecies(
            @Valid @RequestBody SpeciesAiDtos.TranslateSpeciesRequest request
    ) {
        return ApiResponse.success(speciesAiService.translate(request));
    }

    @PostMapping("/observations/analyze")
    @PreAuthorize("hasAuthority('OBS_WRITE')")
    public ApiResponse<ObservationAiDtos.AnalyzeObservationResponse> analyzeObservation(
            @Valid @RequestBody ObservationAiDtos.AnalyzeObservationRequest request
    ) {
        return ApiResponse.success(observationAiService.analyze(request));
    }

    @PostMapping("/observations/{id}/quality-check")
    @PreAuthorize("hasAuthority('OBS_READ')")
    public ApiResponse<ObservationAiDtos.QualityCheckResponse> qualityCheckObservation(@PathVariable Long id) {
        return ApiResponse.success(observationAiService.qualityCheck(id));
    }

    @PostMapping("/assistant/chat")
    public ApiResponse<AssistantAiDtos.ChatResponse> assistantChat(@Valid @RequestBody AssistantAiDtos.ChatRequest request) {
        return ApiResponse.success(assistantAiService.chat(request));
    }

    @GetMapping("/assistant/messages")
    public ApiResponse<AssistantAiDtos.ChatHistoryResponse> assistantMessages() {
        return ApiResponse.success(assistantChatHistoryService.listCurrentUserHistory());
    }

    @DeleteMapping("/assistant/messages")
    public ApiResponse<Void> clearAssistantMessages() {
        assistantChatHistoryService.clearCurrentUserHistory();
        return ApiResponse.success();
    }

    @PostMapping(value = "/assistant/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter assistantChatStream(@Valid @RequestBody AssistantAiDtos.ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        CompletableFuture.runAsync(() -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            try {
                sendEvent(emitter, "status", new AssistantAiDtos.ChatStreamEvent("status", "正在查资料并组织回答...", null));
                AssistantAiDtos.ChatResponse response = assistantAiService.chat(request);
                sendEvent(emitter, "status", new AssistantAiDtos.ChatStreamEvent(
                        "status",
                        response.cacheHit() ? "命中缓存，正在展开回答..." : "想好了，开始回答...",
                        null
                ));
                for (String chunk : splitAnswerForStreaming(response.answer())) {
                    sendEvent(emitter, "delta", new AssistantAiDtos.ChatStreamEvent("delta", chunk, null));
                    sleepQuietly(22);
                }
                sendEvent(emitter, "final", new AssistantAiDtos.ChatStreamEvent("final", "", response));
                emitter.complete();
            } catch (Exception exception) {
                try {
                    sendEvent(emitter, "error", new AssistantAiDtos.ChatStreamEvent("error", readableMessage(exception), null));
                } catch (IOException ignored) {
                    // The client may have disconnected; complete below either way.
                }
                emitter.complete();
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        return emitter;
    }

    private void sendEvent(
            SseEmitter emitter,
            String eventName,
            AssistantAiDtos.ChatStreamEvent event
    ) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(event, MediaType.APPLICATION_JSON));
    }

    private List<String> splitAnswerForStreaming(String answer) {
        List<String> chunks = new ArrayList<>();
        if (answer == null || answer.isBlank()) {
            return chunks;
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < answer.length(); index++) {
            char current = answer.charAt(index);
            builder.append(current);
            boolean sentenceBoundary = "。！？；\n".indexOf(current) >= 0;
            if (builder.length() >= 18 || sentenceBoundary) {
                chunks.add(builder.toString());
                builder.setLength(0);
            }
        }
        if (!builder.isEmpty()) {
            chunks.add(builder.toString());
        }
        return chunks;
    }

    private String readableMessage(Exception exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getMessage();
        }
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "AI 助手生成失败，请稍后再试" : message;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
