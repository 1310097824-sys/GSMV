package com.gsmv.observation;

import com.gsmv.ai.AssistantQueryCache;
import com.gsmv.ai.rag.RagKnowledgeService;
import com.gsmv.audit.service.AuditService;
import com.gsmv.common.ErrorCode;
import com.gsmv.common.PageResponse;
import com.gsmv.common.exception.BusinessException;
import com.gsmv.common.exception.NotFoundException;
import com.gsmv.ecosystem.mapper.EcosystemMapper;
import com.gsmv.observation.dto.ObservationDetailView;
import com.gsmv.observation.dto.ObservationSaveRequest;
import com.gsmv.observation.dto.ObservationSpeciesInput;
import com.gsmv.observation.dto.ObservationVersionSnapshot;
import com.gsmv.observation.dto.ObservationVersionSpeciesSnapshot;
import com.gsmv.observation.dto.ObservationView;
import com.gsmv.observation.mapper.ObservationMapper;
import com.gsmv.observation.model.Observation;
import com.gsmv.security.CurrentUser;
import com.gsmv.security.SecurityUtils;
import com.gsmv.species.dto.SpeciesRow;
import com.gsmv.species.mapper.SpeciesMapper;
import com.gsmv.versioning.EntityVersionService;
import com.gsmv.versioning.dto.EntityVersionView;
import com.gsmv.versioning.dto.VersionFieldChangeView;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
    private final EntityVersionService entityVersionService;
    private final RagKnowledgeService ragKnowledgeService;

    public ObservationService(
            ObservationMapper observationMapper,
            EcosystemMapper ecosystemMapper,
            SpeciesMapper speciesMapper,
            AuditService auditService,
            AssistantQueryCache assistantQueryCache,
            EntityVersionService entityVersionService,
            RagKnowledgeService ragKnowledgeService
    ) {
        this.observationMapper = observationMapper;
        this.ecosystemMapper = ecosystemMapper;
        this.speciesMapper = speciesMapper;
        this.auditService = auditService;
        this.assistantQueryCache = assistantQueryCache;
        this.entityVersionService = entityVersionService;
        this.ragKnowledgeService = ragKnowledgeService;
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

    public List<EntityVersionView> listVersions(Long id) {
        if (observationMapper.findById(id) == null) {
            throw new NotFoundException("观测记录不存在");
        }
        return entityVersionService.listVersions(EntityVersionService.ENTITY_TYPE_OBSERVATION, id);
    }

    @Transactional
    public ObservationDetailView create(ObservationSaveRequest request) {
        List<ObservationSpeciesInput> normalizedSpeciesItems = validateAndNormalize(request, Set.of());
        CurrentUser currentUser = SecurityUtils.requireCurrentUser();
        Observation observation = toObservation(request, currentUser.userId());
        observationMapper.insert(observation);
        replaceSpeciesItems(observation.getId(), normalizedSpeciesItems);
        ObservationDetailView detailView = getDetail(observation.getId());
        entityVersionService.recordVersion(
                EntityVersionService.ENTITY_TYPE_OBSERVATION,
                observation.getId(),
                "CREATE",
                ObservationVersionSnapshot.fromDetail(detailView),
                buildObservationChanges(null, ObservationVersionSnapshot.fromDetail(detailView)),
                currentUser.userId(),
                null
        );
        assistantQueryCache.invalidateAll();
        ragKnowledgeService.syncObservation(observation.getId());
        auditService.record(currentUser.userId(), "OBSERVATION", "CREATE", "OBSERVATION", observation.getId(), true,
                "{\"ecosystemId\":" + request.ecosystemId() + ",\"speciesCount\":" + normalizedSpeciesItems.size() + "}");
        return detailView;
    }

    @Transactional
    public ObservationDetailView update(Long id, ObservationSaveRequest request) {
        Observation existing = observationMapper.findById(id);
        if (existing == null) {
            throw new NotFoundException("观测记录不存在");
        }
        Set<Long> existingSpeciesIds = existingSpeciesIds(id);
        List<ObservationSpeciesInput> normalizedSpeciesItems = validateAndNormalize(request, existingSpeciesIds);
        ObservationVersionSnapshot beforeSnapshot = ObservationVersionSnapshot.fromDetail(getDetail(id));
        Observation observation = toObservation(request, existing.getObserverUserId());
        observation.setId(id);
        observationMapper.update(observation);
        replaceSpeciesItems(id, normalizedSpeciesItems);
        ObservationDetailView detailView = getDetail(id);
        ObservationVersionSnapshot afterSnapshot = ObservationVersionSnapshot.fromDetail(detailView);
        entityVersionService.recordVersion(
                EntityVersionService.ENTITY_TYPE_OBSERVATION,
                id,
                "UPDATE",
                afterSnapshot,
                buildObservationChanges(beforeSnapshot, afterSnapshot),
                SecurityUtils.requireCurrentUser().userId(),
                null
        );
        assistantQueryCache.invalidateAll();
        ragKnowledgeService.syncObservation(id);
        auditService.record(SecurityUtils.requireCurrentUser().userId(), "OBSERVATION", "UPDATE", "OBSERVATION", id, true,
                "{\"ecosystemId\":" + request.ecosystemId() + ",\"speciesCount\":" + normalizedSpeciesItems.size() + "}");
        return detailView;
    }

    @Transactional
    public void delete(Long id) {
        Observation existing = observationMapper.findById(id);
        if (existing == null) {
            throw new NotFoundException("观测记录不存在");
        }
        Long currentUserId = SecurityUtils.requireCurrentUser().userId();
        ObservationVersionSnapshot beforeSnapshot = ObservationVersionSnapshot.fromDetail(getDetail(id));
        observationMapper.deleteSpeciesByObservationId(id);
        observationMapper.deleteById(id);
        entityVersionService.recordVersion(
                EntityVersionService.ENTITY_TYPE_OBSERVATION,
                id,
                "DELETE",
                beforeSnapshot,
                buildObservationChanges(beforeSnapshot, null),
                currentUserId,
                null
        );
        assistantQueryCache.invalidateAll();
        ragKnowledgeService.markSourceDeleted(RagKnowledgeService.SOURCE_OBSERVATION, id);
        auditService.record(currentUserId, "OBSERVATION", "DELETE", "OBSERVATION", id, true,
                "{\"ecosystemId\":" + existing.getEcosystemId() + "}");
    }

    @Transactional
    public ObservationDetailView rollback(Long id, Long versionId) {
        Long currentUserId = SecurityUtils.requireCurrentUser().userId();
        ObservationVersionSnapshot targetSnapshot = entityVersionService.readSnapshot(
                EntityVersionService.ENTITY_TYPE_OBSERVATION,
                id,
                versionId,
                ObservationVersionSnapshot.class
        );
        Observation existing = observationMapper.findById(id);
        ObservationVersionSnapshot beforeSnapshot = existing == null ? null : ObservationVersionSnapshot.fromDetail(getDetail(id));
        Set<Long> existingSpeciesIds = existing == null ? Set.of() : existingSpeciesIds(id);
        ObservationSaveRequest targetRequest = targetSnapshot.toSaveRequest();
        List<ObservationSpeciesInput> normalizedSpeciesItems = validateAndNormalize(targetRequest, existingSpeciesIds);

        Observation observation = toObservation(targetRequest, targetSnapshot.observerUserId());
        observation.setId(id);
        if (existing == null) {
            observationMapper.insertWithId(observation);
        } else {
            observationMapper.update(observation);
            observationMapper.deleteSpeciesByObservationId(id);
        }
        replaceSpeciesItems(id, normalizedSpeciesItems);

        ObservationDetailView detailView = getDetail(id);
        ObservationVersionSnapshot afterSnapshot = ObservationVersionSnapshot.fromDetail(detailView);
        entityVersionService.recordVersion(
                EntityVersionService.ENTITY_TYPE_OBSERVATION,
                id,
                "ROLLBACK",
                afterSnapshot,
                buildObservationChanges(beforeSnapshot, afterSnapshot),
                currentUserId,
                versionId
        );
        assistantQueryCache.invalidateAll();
        ragKnowledgeService.syncObservation(id);
        auditService.record(currentUserId, "OBSERVATION", "ROLLBACK", "OBSERVATION", id, true,
                "{\"versionId\":" + versionId + "}");
        return detailView;
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

    private Set<Long> existingSpeciesIds(Long observationId) {
        return observationMapper.findSpeciesViews(observationId).stream()
                .map(com.gsmv.observation.dto.ObservationSpeciesView::speciesId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
    }

    private List<ObservationSpeciesInput> validateAndNormalize(ObservationSaveRequest request, Set<Long> existingSpeciesIds) {
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
            SpeciesRow species = speciesMapper.findViewById(item.speciesId());
            if (species == null) {
                throw new NotFoundException("关联物种不存在: " + item.speciesId());
            }
            if (!Objects.equals(species.status(), 1) && !existingSpeciesIds.contains(item.speciesId())) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "归档物种不能新增为观测记录关联项，请先启用该物种或选择其他可用物种",
                        HttpStatus.BAD_REQUEST
                );
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

    private List<VersionFieldChangeView> buildObservationChanges(
            ObservationVersionSnapshot before,
            ObservationVersionSnapshot after
    ) {
        List<VersionFieldChangeView> changes = new ArrayList<>();
        addChange(changes, "ecosystemName", "生态系统", before == null ? null : before.ecosystemName(), after == null ? null : after.ecosystemName());
        addChange(changes, "observerName", "观测人员", before == null ? null : before.observerName(), after == null ? null : after.observerName());
        addChange(changes, "observedAt", "观测时间", formatDateTime(before == null ? null : before.observedAt()), formatDateTime(after == null ? null : after.observedAt()));
        addChange(changes, "locationName", "地点说明", before == null ? null : before.locationName(), after == null ? null : after.locationName());
        addChange(changes, "locationLat", "纬度", decimalToString(before == null ? null : before.locationLat()), decimalToString(after == null ? null : after.locationLat()));
        addChange(changes, "locationLng", "经度", decimalToString(before == null ? null : before.locationLng()), decimalToString(after == null ? null : after.locationLng()));
        addChange(changes, "envJson", "环境参数", summarizeEnvironment(before == null ? null : before.envJson()), summarizeEnvironment(after == null ? null : after.envJson()));
        addChange(changes, "note", "备注", before == null ? null : before.note(), after == null ? null : after.note());
        addChange(changes, "speciesItems", "关联物种", summarizeSpeciesItems(before == null ? null : before.speciesItems()), summarizeSpeciesItems(after == null ? null : after.speciesItems()));
        return changes;
    }

    private void addChange(List<VersionFieldChangeView> changes, String fieldKey, String fieldLabel, String oldValue, String newValue) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        changes.add(new VersionFieldChangeView(fieldKey, fieldLabel, oldValue, newValue));
    }

    private String summarizeSpeciesItems(List<ObservationVersionSpeciesSnapshot> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.stream()
                .map(item -> {
                    StringBuilder builder = new StringBuilder();
                    builder.append(item.chineseName() != null ? item.chineseName() : item.scientificName());
                    if (item.countEstimated() != null) {
                        builder.append(" × ").append(item.countEstimated());
                    }
                    if (item.behavior() != null && !item.behavior().isBlank()) {
                        builder.append("（").append(item.behavior()).append("）");
                    }
                    if (item.comment() != null && !item.comment().isBlank()) {
                        builder.append("：").append(item.comment());
                    }
                    return builder.toString();
                })
                .toList()
                .stream()
                .collect(java.util.stream.Collectors.joining("；"));
    }

    private String summarizeEnvironment(String envJson) {
        return normalizeNullable(envJson);
    }

    private String decimalToString(java.math.BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
