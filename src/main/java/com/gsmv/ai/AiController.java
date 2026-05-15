package com.gsmv.ai;

import com.gsmv.ai.dto.AssistantAiDtos;
import com.gsmv.ai.dto.ObservationAiDtos;
import com.gsmv.ai.dto.SpeciesAiDtos;
import com.gsmv.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final SpeciesAiService speciesAiService;
    private final ObservationAiService observationAiService;
    private final AssistantAiService assistantAiService;

    public AiController(
            SpeciesAiService speciesAiService,
            ObservationAiService observationAiService,
            AssistantAiService assistantAiService
    ) {
        this.speciesAiService = speciesAiService;
        this.observationAiService = observationAiService;
        this.assistantAiService = assistantAiService;
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
}
