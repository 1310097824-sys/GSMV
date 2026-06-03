package com.gsmv.species;

import com.gsmv.ai.AssistantQueryCache;
import com.gsmv.ai.rag.RagKnowledgeService;
import com.gsmv.audit.service.AuditService;
import com.gsmv.common.ErrorCode;
import com.gsmv.common.PageResponse;
import com.gsmv.common.exception.BusinessException;
import com.gsmv.common.exception.NotFoundException;
import com.gsmv.media.MediaFileService;
import com.gsmv.media.model.MediaFile;
import com.gsmv.security.SecurityUtils;
import com.gsmv.versioning.EntityVersionService;
import com.gsmv.versioning.dto.EntityVersionView;
import com.gsmv.versioning.dto.VersionFieldChangeView;
import com.gsmv.species.dto.SpeciesDetailView;
import com.gsmv.species.dto.SpeciesImageView;
import com.gsmv.species.dto.SpeciesRow;
import com.gsmv.species.dto.SpeciesSaveRequest;
import com.gsmv.species.dto.SpeciesVersionSnapshot;
import com.gsmv.species.dto.SpeciesView;
import com.gsmv.species.dto.TaxonOption;
import com.gsmv.species.mapper.SpeciesMapper;
import com.gsmv.species.mapper.TaxonMapper;
import com.gsmv.species.model.Species;
import com.gsmv.species.model.Taxon;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SpeciesService {

    private static final String SPECIES_IMAGE_BUSINESS_TYPE = "SPECIES_IMAGE";
    private static final List<String> RANK_ORDER = List.of("PHYLUM", "CLASS", "ORDER", "FAMILY", "GENUS", "SPECIES");

    private final SpeciesMapper speciesMapper;
    private final TaxonMapper taxonMapper;
    private final AuditService auditService;
    private final MediaFileService mediaFileService;
    private final AssistantQueryCache assistantQueryCache;
    private final EntityVersionService entityVersionService;
    private final RagKnowledgeService ragKnowledgeService;

    public SpeciesService(
            SpeciesMapper speciesMapper,
            TaxonMapper taxonMapper,
            AuditService auditService,
            MediaFileService mediaFileService,
            AssistantQueryCache assistantQueryCache,
            EntityVersionService entityVersionService,
            RagKnowledgeService ragKnowledgeService
    ) {
        this.speciesMapper = speciesMapper;
        this.taxonMapper = taxonMapper;
        this.auditService = auditService;
        this.mediaFileService = mediaFileService;
        this.assistantQueryCache = assistantQueryCache;
        this.entityVersionService = entityVersionService;
        this.ragKnowledgeService = ragKnowledgeService;
    }

    public PageResponse<SpeciesView> listSpecies(
            String keyword,
            Integer status,
            String protectionLevel,
            String iucnStatus,
            String distributionKeyword,
            Long taxonId,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;

        List<Taxon> taxa = taxonMapper.findAll();
        Map<Long, Taxon> taxonMap = toTaxonMap(taxa);
        List<Long> taxonIds = resolveTaxonFilterIds(taxa, taxonId);
        if (taxonId != null && taxonIds.isEmpty()) {
            return new PageResponse<>(List.of(), 0, safePage, safeSize);
        }

        List<SpeciesView> items = speciesMapper.findPage(
                        normalizeNullable(keyword),
                        status,
                        normalizeProtectionLevelFilter(protectionLevel),
                        normalizeIucnStatusFilter(iucnStatus),
                        normalizeNullable(distributionKeyword),
                        taxonIds,
                        safeSize,
                        offset
                ).stream()
                .map(row -> toSummaryView(row, taxonMap))
                .toList();
        long total = speciesMapper.count(
                normalizeNullable(keyword),
                status,
                normalizeProtectionLevelFilter(protectionLevel),
                normalizeIucnStatusFilter(iucnStatus),
                normalizeNullable(distributionKeyword),
                taxonIds
        );
        return new PageResponse<>(items, total, safePage, safeSize);
    }

    public SpeciesDetailView getSpecies(Long id) {
        SpeciesRow row = requireSpeciesRow(id);
        Map<Long, Taxon> taxonMap = toTaxonMap(taxonMapper.findAll());
        return toDetailView(row, taxonMap);
    }

    public List<TaxonOption> listTaxa() {
        return taxonMapper.findAll().stream()
                .map(taxon -> new TaxonOption(
                        taxon.getId(),
                        taxon.getParentId(),
                        taxon.getRank(),
                        taxon.getScientificName(),
                        taxon.getChineseName()
                ))
                .toList();
    }

    public List<EntityVersionView> listVersions(Long id) {
        if (speciesMapper.findById(id) == null) {
            throw new NotFoundException("物种不存在");
        }
        return entityVersionService.listVersions(EntityVersionService.ENTITY_TYPE_SPECIES, id);
    }

    @Transactional
    public SpeciesDetailView createSpecies(SpeciesSaveRequest request) {
        Long currentUserId = SecurityUtils.requireCurrentUser().userId();
        Long taxonId = resolveSpeciesTaxonId(request);
        Species species = toEntity(request, taxonId);
        speciesMapper.insert(species);
        SpeciesDetailView detailView = getSpecies(species.getId());
        entityVersionService.recordVersion(
                EntityVersionService.ENTITY_TYPE_SPECIES,
                species.getId(),
                "CREATE",
                SpeciesVersionSnapshot.fromDetail(detailView),
                buildSpeciesChanges(null, SpeciesVersionSnapshot.fromDetail(detailView)),
                currentUserId,
                null
        );
        assistantQueryCache.invalidateAll();
        ragKnowledgeService.syncSpecies(species.getId());
        auditService.record(currentUserId, "SPECIES", "CREATE", "SPECIES", species.getId(), true,
                "{\"scientificName\":\"" + escapeJson(request.scientificName()) + "\"}");
        return detailView;
    }

    @Transactional
    public SpeciesDetailView updateSpecies(Long id, SpeciesSaveRequest request) {
        if (speciesMapper.findById(id) == null) {
            throw new NotFoundException("物种不存在");
        }
        Long currentUserId = SecurityUtils.requireCurrentUser().userId();
        SpeciesVersionSnapshot beforeSnapshot = SpeciesVersionSnapshot.fromDetail(getSpecies(id));
        Long taxonId = resolveSpeciesTaxonId(request);
        Species species = toEntity(request, taxonId);
        species.setId(id);
        speciesMapper.update(species);
        SpeciesDetailView detailView = getSpecies(id);
        SpeciesVersionSnapshot afterSnapshot = SpeciesVersionSnapshot.fromDetail(detailView);
        entityVersionService.recordVersion(
                EntityVersionService.ENTITY_TYPE_SPECIES,
                id,
                "UPDATE",
                afterSnapshot,
                buildSpeciesChanges(beforeSnapshot, afterSnapshot),
                currentUserId,
                null
        );
        assistantQueryCache.invalidateAll();
        ragKnowledgeService.syncSpecies(id);
        auditService.record(currentUserId, "SPECIES", "UPDATE", "SPECIES", id, true,
                "{\"scientificName\":\"" + escapeJson(request.scientificName()) + "\"}");
        return detailView;
    }

    @Transactional
    public void deleteSpecies(Long id) {
        Long currentUserId = SecurityUtils.requireCurrentUser().userId();
        Species existing = speciesMapper.findById(id);
        if (existing == null) {
            throw new NotFoundException("物种不存在");
        }
        if (speciesMapper.countObservationReferences(id) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该物种已被观测记录引用，无法删除", HttpStatus.CONFLICT);
        }
        SpeciesVersionSnapshot beforeSnapshot = SpeciesVersionSnapshot.fromDetail(getSpecies(id));
        mediaFileService.deleteByBusiness(SPECIES_IMAGE_BUSINESS_TYPE, id);
        speciesMapper.deleteById(id);
        entityVersionService.recordVersion(
                EntityVersionService.ENTITY_TYPE_SPECIES,
                id,
                "DELETE",
                beforeSnapshot,
                buildSpeciesChanges(beforeSnapshot, null),
                currentUserId,
                null
        );
        assistantQueryCache.invalidateAll();
        ragKnowledgeService.markSourceDeleted(RagKnowledgeService.SOURCE_SPECIES, id);
        auditService.record(currentUserId, "SPECIES", "DELETE", "SPECIES", id, true,
                "{\"speciesId\":" + id + "}");
    }

    @Transactional
    public SpeciesImageView uploadImage(Long speciesId, MultipartFile file) {
        if (speciesMapper.findById(speciesId) == null) {
            throw new NotFoundException("物种不存在");
        }
        if (file.isEmpty() || file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请上传图片格式的物种图片", HttpStatus.BAD_REQUEST);
        }
        MediaFile mediaFile = mediaFileService.upload(SPECIES_IMAGE_BUSINESS_TYPE, speciesId, file);
        return toImageView(mediaFile);
    }

    public MediaFile getSpeciesImage(Long mediaId) {
        MediaFile mediaFile = mediaFileService.getRequired(mediaId);
        if (!SPECIES_IMAGE_BUSINESS_TYPE.equalsIgnoreCase(mediaFile.getBusinessType())) {
            throw new NotFoundException("物种图片不存在");
        }
        return mediaFile;
    }

    @Transactional
    public SpeciesDetailView rollbackSpecies(Long id, Long versionId) {
        Long currentUserId = SecurityUtils.requireCurrentUser().userId();
        SpeciesVersionSnapshot targetSnapshot = entityVersionService.readSnapshot(
                EntityVersionService.ENTITY_TYPE_SPECIES,
                id,
                versionId,
                SpeciesVersionSnapshot.class
        );
        Species existing = speciesMapper.findById(id);
        SpeciesVersionSnapshot beforeSnapshot = existing == null ? null : SpeciesVersionSnapshot.fromDetail(getSpecies(id));

        Long taxonId = resolveSpeciesTaxonId(targetSnapshot.toSaveRequest());
        Species species = toEntity(targetSnapshot.toSaveRequest(), taxonId);
        species.setId(id);
        if (existing == null) {
            speciesMapper.insertWithId(species);
        } else {
            speciesMapper.update(species);
        }

        SpeciesDetailView detailView = getSpecies(id);
        SpeciesVersionSnapshot afterSnapshot = SpeciesVersionSnapshot.fromDetail(detailView);
        entityVersionService.recordVersion(
                EntityVersionService.ENTITY_TYPE_SPECIES,
                id,
                "ROLLBACK",
                afterSnapshot,
                buildSpeciesChanges(beforeSnapshot, afterSnapshot),
                currentUserId,
                versionId
        );
        assistantQueryCache.invalidateAll();
        ragKnowledgeService.syncSpecies(id);
        auditService.record(currentUserId, "SPECIES", "ROLLBACK", "SPECIES", id, true,
                "{\"versionId\":" + versionId + "}");
        return detailView;
    }

    private SpeciesRow requireSpeciesRow(Long id) {
        SpeciesRow row = speciesMapper.findRowById(id);
        if (row == null) {
            throw new NotFoundException("物种不存在");
        }
        return row;
    }

    private Species toEntity(SpeciesSaveRequest request, Long taxonId) {
        Species species = new Species();
        species.setTaxonId(taxonId);
        species.setProtectionLevel(normalizeNullable(request.protectionLevel()));
        species.setIucnStatus(normalizeNullable(request.iucnStatus()));
        species.setDescription(normalizeNullable(request.description()));
        species.setMorphology(normalizeNullable(request.morphology()));
        species.setHabit(normalizeNullable(request.habit()));
        species.setHabitat(normalizeNullable(request.habitat()));
        species.setDistribution(normalizeNullable(request.distribution()));
        species.setDistributionLat(normalizeDecimal(request.distributionLat()));
        species.setDistributionLng(normalizeDecimal(request.distributionLng()));
        species.setGeoRangeText(normalizeNullable(request.geoRangeText()));
        species.setVideoUrl(normalizeNullable(request.videoUrl()));
        species.setReferenceText(normalizeNullable(request.referenceText()));
        species.setStatus(request.status());
        return species;
    }

    private Long resolveSpeciesTaxonId(SpeciesSaveRequest request) {
        Long phylumId = findOrCreateTaxon(null, "PHYLUM", request.phylumName(), null);
        Long classId = findOrCreateTaxon(phylumId, "CLASS", request.className(), null);
        Long orderId = findOrCreateTaxon(classId, "ORDER", request.orderName(), null);
        Long familyId = findOrCreateTaxon(orderId, "FAMILY", request.familyName(), null);
        Long genusId = findOrCreateTaxon(familyId, "GENUS", request.genusName(), null);
        return findOrCreateTaxon(genusId, "SPECIES", request.scientificName(), request.chineseName());
    }

    private Long findOrCreateTaxon(Long parentId, String rank, String scientificName, String chineseName) {
        String normalizedScientificName = normalizeRequired(scientificName);
        String normalizedChineseName = normalizeNullable(chineseName);

        Taxon existing = taxonMapper.findByParentAndRankAndScientificName(parentId, rank, normalizedScientificName);
        if (existing != null) {
            if ("SPECIES".equals(rank) && normalizedChineseName != null && !Objects.equals(existing.getChineseName(), normalizedChineseName)) {
                taxonMapper.updateChineseName(existing.getId(), normalizedChineseName);
            }
            return existing.getId();
        }

        Taxon taxon = new Taxon();
        taxon.setParentId(parentId);
        taxon.setRank(rank);
        taxon.setScientificName(normalizedScientificName);
        taxon.setChineseName("SPECIES".equals(rank) ? normalizedChineseName : null);
        taxonMapper.insert(taxon);
        return taxon.getId();
    }

    private SpeciesView toSummaryView(SpeciesRow row, Map<Long, Taxon> taxonMap) {
        TaxonomyLineage lineage = resolveLineage(row.taxonId(), taxonMap);
        return new SpeciesView(
                row.id(),
                row.taxonId(),
                row.rank(),
                row.scientificName(),
                row.chineseName(),
                lineage.phylumName(),
                lineage.className(),
                lineage.orderName(),
                lineage.familyName(),
                lineage.genusName(),
                lineage.classificationPath(),
                row.protectionLevel(),
                row.iucnStatus(),
                row.geoRangeText(),
                row.status(),
                row.createdAt(),
                row.updatedAt()
        );
    }

    private SpeciesDetailView toDetailView(SpeciesRow row, Map<Long, Taxon> taxonMap) {
        TaxonomyLineage lineage = resolveLineage(row.taxonId(), taxonMap);
        List<SpeciesImageView> images = mediaFileService.list(SPECIES_IMAGE_BUSINESS_TYPE, row.id()).stream()
                .map(this::toImageView)
                .toList();
        return new SpeciesDetailView(
                row.id(),
                row.taxonId(),
                row.rank(),
                row.scientificName(),
                row.chineseName(),
                lineage.phylumName(),
                lineage.className(),
                lineage.orderName(),
                lineage.familyName(),
                lineage.genusName(),
                lineage.classificationPath(),
                row.protectionLevel(),
                row.iucnStatus(),
                row.description(),
                row.morphology(),
                row.habit(),
                row.habitat(),
                row.distribution(),
                row.distributionLat(),
                row.distributionLng(),
                row.geoRangeText(),
                row.videoUrl(),
                row.referenceText(),
                row.status(),
                row.createdAt(),
                row.updatedAt(),
                images
        );
    }

    private SpeciesImageView toImageView(MediaFile mediaFile) {
        return new SpeciesImageView(
                mediaFile.getId(),
                "/api/v1/species/images/" + mediaFile.getId(),
                mediaFile.getOriginalFilename()
        );
    }

    private TaxonomyLineage resolveLineage(Long taxonId, Map<Long, Taxon> taxonMap) {
        Map<String, String> namesByRank = new LinkedHashMap<>();
        Long currentId = taxonId;
        while (currentId != null) {
            Taxon taxon = taxonMap.get(currentId);
            if (taxon == null) {
                break;
            }
            namesByRank.put(taxon.getRank(), taxon.getScientificName());
            currentId = taxon.getParentId();
        }

        String classificationPath = RANK_ORDER.stream()
                .map(namesByRank::get)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" / "));

        return new TaxonomyLineage(
                namesByRank.get("PHYLUM"),
                namesByRank.get("CLASS"),
                namesByRank.get("ORDER"),
                namesByRank.get("FAMILY"),
                namesByRank.get("GENUS"),
                classificationPath
        );
    }

    private List<Long> resolveTaxonFilterIds(List<Taxon> taxa, Long rootTaxonId) {
        if (rootTaxonId == null) {
            return null;
        }

        Map<Long, List<Taxon>> childrenMap = taxa.stream()
                .filter(taxon -> taxon.getParentId() != null)
                .collect(Collectors.groupingBy(Taxon::getParentId));

        List<Long> ids = new ArrayList<>();
        ArrayDeque<Long> stack = new ArrayDeque<>();
        stack.push(rootTaxonId);
        while (!stack.isEmpty()) {
            Long currentId = stack.pop();
            ids.add(currentId);
            for (Taxon child : childrenMap.getOrDefault(currentId, List.of())) {
                stack.push(child.getId());
            }
        }
        return ids;
    }

    private Map<Long, Taxon> toTaxonMap(List<Taxon> taxa) {
        return taxa.stream().collect(Collectors.toMap(Taxon::getId, taxon -> taxon, (left, right) -> left));
    }

    private String normalizeRequired(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeProtectionLevelFilter(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return null;
        }
        String compact = normalized
                .replace(" ", "")
                .replace("　", "")
                .replace("１", "1")
                .replace("２", "2")
                .toLowerCase(Locale.ROOT);
        if (compact.contains("一级") || compact.contains("1级") || compact.equals("1") || compact.contains("一类")) {
            return "一级";
        }
        if (compact.contains("二级") || compact.contains("2级") || compact.equals("2") || compact.contains("二类")) {
            return "二级";
        }
        if (compact.contains("重点")) {
            return "重点";
        }
        if (compact.contains("地方")) {
            return "地方";
        }
        return normalized;
    }

    private String normalizeIucnStatusFilter(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "极危", "CRITICAL", "CRITICALLYENDANGERED", "CRITICALLY ENDANGERED" -> "CR";
            case "濒危", "ENDANGERED" -> "EN";
            case "易危", "VULNERABLE" -> "VU";
            case "近危", "NEARTHREATENED", "NEAR THREATENED" -> "NT";
            case "无危", "低危", "LEASTCONCERN", "LEAST CONCERN" -> "LC";
            case "数据缺乏", "数据不足", "DATADEFICIENT", "DATA DEFICIENT" -> "DD";
            case "未评估", "NOTEVALUATED", "NOT EVALUATED" -> "NE";
            case "灭绝", "EXTINCT" -> "EX";
            case "野外灭绝", "EXTINCTINTHEWILD", "EXTINCT IN THE WILD" -> "EW";
            default -> upper;
        };
    }

    private BigDecimal normalizeDecimal(BigDecimal value) {
        return value;
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private List<VersionFieldChangeView> buildSpeciesChanges(
            SpeciesVersionSnapshot before,
            SpeciesVersionSnapshot after
    ) {
        List<VersionFieldChangeView> changes = new ArrayList<>();
        addChange(changes, "chineseName", "中文名", before == null ? null : before.chineseName(), after == null ? null : after.chineseName());
        addChange(changes, "scientificName", "学名", before == null ? null : before.scientificName(), after == null ? null : after.scientificName());
        addChange(changes, "phylumName", "门", before == null ? null : before.phylumName(), after == null ? null : after.phylumName());
        addChange(changes, "className", "纲", before == null ? null : before.className(), after == null ? null : after.className());
        addChange(changes, "orderName", "目", before == null ? null : before.orderName(), after == null ? null : after.orderName());
        addChange(changes, "familyName", "科", before == null ? null : before.familyName(), after == null ? null : after.familyName());
        addChange(changes, "genusName", "属", before == null ? null : before.genusName(), after == null ? null : after.genusName());
        addChange(changes, "protectionLevel", "保护等级", before == null ? null : before.protectionLevel(), after == null ? null : after.protectionLevel());
        addChange(changes, "iucnStatus", "濒危状态", before == null ? null : before.iucnStatus(), after == null ? null : after.iucnStatus());
        addChange(changes, "description", "物种简介", before == null ? null : before.description(), after == null ? null : after.description());
        addChange(changes, "morphology", "形态特征", before == null ? null : before.morphology(), after == null ? null : after.morphology());
        addChange(changes, "habit", "生活习性", before == null ? null : before.habit(), after == null ? null : after.habit());
        addChange(changes, "habitat", "栖息环境", before == null ? null : before.habitat(), after == null ? null : after.habitat());
        addChange(changes, "distribution", "分布区域", before == null ? null : before.distribution(), after == null ? null : after.distribution());
        addChange(changes, "distributionLat", "分布纬度", decimalToString(before == null ? null : before.distributionLat()), decimalToString(after == null ? null : after.distributionLat()));
        addChange(changes, "distributionLng", "分布经度", decimalToString(before == null ? null : before.distributionLng()), decimalToString(after == null ? null : after.distributionLng()));
        addChange(changes, "geoRangeText", "地理范围", before == null ? null : before.geoRangeText(), after == null ? null : after.geoRangeText());
        addChange(changes, "videoUrl", "视频链接", before == null ? null : before.videoUrl(), after == null ? null : after.videoUrl());
        addChange(changes, "referenceText", "参考文献", before == null ? null : before.referenceText(), after == null ? null : after.referenceText());
        addChange(changes, "status", "档案状态", formatStatus(before == null ? null : before.status()), formatStatus(after == null ? null : after.status()));
        return changes;
    }

    private void addChange(List<VersionFieldChangeView> changes, String fieldKey, String fieldLabel, String oldValue, String newValue) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        changes.add(new VersionFieldChangeView(fieldKey, fieldLabel, oldValue, newValue));
    }

    private String decimalToString(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String formatStatus(Integer value) {
        if (value == null) {
            return null;
        }
        return value == 1 ? "启用" : "归档";
    }

    private record TaxonomyLineage(
            String phylumName,
            String className,
            String orderName,
            String familyName,
            String genusName,
            String classificationPath
    ) {
    }
}
