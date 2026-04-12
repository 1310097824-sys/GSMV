package com.gsmv.observation;

import com.gsmv.ai.AssistantQueryCache;
import com.gsmv.audit.service.AuditService;
import com.gsmv.common.ErrorCode;
import com.gsmv.common.PageResponse;
import com.gsmv.common.exception.BusinessException;
import com.gsmv.common.exception.NotFoundException;
import com.gsmv.ecosystem.mapper.EcosystemMapper;
import com.gsmv.observation.dto.ObservationDetailView;
import com.gsmv.observation.dto.ObservationSaveRequest;
import com.gsmv.observation.dto.ObservationSpeciesInput;
import com.gsmv.observation.dto.ObservationView;
import com.gsmv.observation.mapper.ObservationMapper;
import com.gsmv.observation.model.Observation;
import com.gsmv.security.CurrentUser;
import com.gsmv.security.SecurityUtils;
import com.gsmv.species.mapper.SpeciesMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObservationService {

    private final ObservationMapper observationMapper;
    private final EcosystemMapper ecosystemMapper;
    private final SpeciesMapper speciesMapper;
    private final AuditService auditService;
    private final AssistantQueryCache assistantQueryCache;

    public ObservationService(
            ObservationMapper observationMapper,
            EcosystemMapper ecosystemMapper,
            SpeciesMapper speciesMapper,
            AuditService auditService,
            AssistantQueryCache assistantQueryCache
    ) {
        this.observationMapper = observationMapper;
        this.ecosystemMapper = ecosystemMapper;
        this.speciesMapper = speciesMapper;
        this.auditService = auditService;
        this.assistantQueryCache = assistantQueryCache;
    }

    public PageResponse<ObservationView> list(
            Long ecosystemId,
            String keyword,
            LocalDateTime observedFrom,
            LocalDateTime observedTo,
            int page,
            int size
    ) {
        validateDateRange(observedFrom, observedTo);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        String normalizedKeyword = normalizeNullable(keyword);
        List<ObservationView> items = observationMapper.findPage(
                ecosystemId,
                normalizedKeyword,
                observedFrom,
                observedTo,
                safeSize,
                offset
        );
        long total = observationMapper.count(ecosystemId, normalizedKeyword, observedFrom, observedTo);
        return new PageResponse<>(items, total, safePage, safeSize);
    }

    public ObservationDetailView getDetail(Long id) {
        ObservationView observation = observationMapper.findViewById(id);
        if (observation == null) {
            throw new NotFoundException("观测记录不存在");
        }
        return new ObservationDetailView(
                observation.id(),
                observation.ecosystemId(),
                observation.ecosystemName(),
                observation.observerUserId(),
                observation.observerName(),
                observation.observedAt(),
                observation.locationLat(),
                observation.locationLng(),
                observation.locationName(),
                observation.envJson(),
                observation.note(),
                observation.createdAt(),
                observationMapper.findSpeciesViews(id)
        );
    }

    @Transactional
    public ObservationDetailView create(ObservationSaveRequest request) {
        List<ObservationSpeciesInput> normalizedSpeciesItems = validateAndNormalize(request);
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        Observation observation = toObservation(request, currentUser.userId());
        observationMapper.insert(observation);
        replaceSpeciesItems(observation.getId(), normalizedSpeciesItems);
        assistantQueryCache.invalidateAll();
        auditService.record(currentUser.userId(), "OBSERVATION", "CREATE", "OBSERVATION", observation.getId(), true,
                "{\"ecosystemId\":" + request.ecosystemId() + ",\"speciesCount\":" + normalizedSpeciesItems.size() + "}");
        return getDetail(observation.getId());
    }

    @Transactional
    public ObservationDetailView update(Long id, ObservationSaveRequest request) {
        Observation existing = observationMapper.findById(id);
        if (existing == null) {
            throw new NotFoundException("观测记录不存在");
        }
        List<ObservationSpeciesInput> normalizedSpeciesItems = validateAndNormalize(request);
        Observation observation = toObservation(request, existing.getObserverUserId());
        observation.setId(id);
        observationMapper.update(observation);
        replaceSpeciesItems(id, normalizedSpeciesItems);
        assistantQueryCache.invalidateAll();
        auditService.record(SecurityUtils.requireCurrentUser().userId(), "OBSERVATION", "UPDATE", "OBSERVATION", id, true,
                "{\"ecosystemId\":" + request.ecosystemId() + ",\"speciesCount\":" + normalizedSpeciesItems.size() + "}");
        return getDetail(id);
    }

    @Transactional
    public void delete(Long id) {
        Observation existing = observationMapper.findById(id);
        if (existing == null) {
            throw new NotFoundException("观测记录不存在");
        }
        observationMapper.deleteSpeciesByObservationId(id);
        observationMapper.deleteById(id);
        assistantQueryCache.invalidateAll();
        auditService.record(SecurityUtils.requireCurrentUser().userId(), "OBSERVATION", "DELETE", "OBSERVATION", id, true,
                "{\"ecosystemId\":" + existing.getEcosystemId() + "}");
    }

    private Observation toObservation(ObservationSaveRequest request, Long observerUserId) {
        Observation observation = new Observation();
        observation.setEcosystemId(request.ecosystemId());
        observation.setObserverUserId(observerUserId);
        observation.setObservedAt(request.observedAt());
        observation.setLocationLat(request.locationLat());
        observation.setLocationLng(request.locationLng());
        observation.setLocationName(normalizeNullable(request.locationName()));
        observation.setEnvJson(normalizeNullable(request.envJson()));
        observation.setNote(normalizeNullable(request.note()));
        return observation;
    }

    private void replaceSpeciesItems(Long observationId, List<ObservationSpeciesInput> speciesItems) {
        observationMapper.deleteSpeciesByObservationId(observationId);
        if (!speciesItems.isEmpty()) {
            observationMapper.insertSpeciesBatch(observationId, speciesItems);
        }
    }

    private List<ObservationSpeciesInput> validateAndNormalize(ObservationSaveRequest request) {
        if (ecosystemMapper.findById(request.ecosystemId()) == null) {
            throw new NotFoundException("生态系统不存在");
        }

        List<ObservationSpeciesInput> inputItems = request.speciesItems() == null ? List.of() : request.speciesItems();
        List<ObservationSpeciesInput> normalizedItems = new ArrayList<>();
        Set<Long> seenSpeciesIds = new HashSet<>();

        for (ObservationSpeciesInput item : inputItems) {
            if (item == null) {
                continue;
            }

            String behavior = normalizeNullable(item.behavior());
            String comment = normalizeNullable(item.comment());
            boolean hasContent = item.speciesId() != null || item.countEstimated() != null || behavior != null || comment != null;
            if (!hasContent) {
                continue;
            }

            if (item.speciesId() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请先为每条关联记录选择物种", HttpStatus.BAD_REQUEST);
            }
            if (!seenSpeciesIds.add(item.speciesId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "同一条观测记录中不能重复关联相同物种", HttpStatus.BAD_REQUEST);
            }
            if (speciesMapper.findViewById(item.speciesId()) == null) {
                throw new NotFoundException("关联物种不存在: " + item.speciesId());
            }

            normalizedItems.add(new ObservationSpeciesInput(item.speciesId(), item.countEstimated(), behavior, comment));
        }

        return normalizedItems;
    }

    private void validateDateRange(LocalDateTime observedFrom, LocalDateTime observedTo) {
        if (observedFrom != null && observedTo != null && observedFrom.isAfter(observedTo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "开始时间不能晚于结束时间", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
