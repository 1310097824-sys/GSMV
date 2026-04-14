package com.gsmv.versioning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsmv.common.exception.NotFoundException;
import com.gsmv.versioning.dto.EntityVersionRow;
import com.gsmv.versioning.dto.EntityVersionView;
import com.gsmv.versioning.dto.VersionFieldChangeView;
import com.gsmv.versioning.mapper.EntityVersionMapper;
import com.gsmv.versioning.model.EntityVersionRecord;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EntityVersionService {

    public static final String ENTITY_TYPE_SPECIES = "SPECIES";
    public static final String ENTITY_TYPE_OBSERVATION = "OBSERVATION";

    private final EntityVersionMapper entityVersionMapper;
    private final ObjectMapper objectMapper;

    public EntityVersionService(EntityVersionMapper entityVersionMapper, ObjectMapper objectMapper) {
        this.entityVersionMapper = entityVersionMapper;
        this.objectMapper = objectMapper;
    }

    public void recordVersion(
            String entityType,
            Long entityId,
            String action,
            Object snapshot,
            List<VersionFieldChangeView> changes,
            Long changedBy,
            Long rollbackSourceVersionId
    ) {
        EntityVersionRecord record = new EntityVersionRecord();
        record.setEntityType(entityType);
        record.setEntityId(entityId);
        record.setVersionNo(entityVersionMapper.findNextVersionNo(entityType, entityId));
        record.setAction(action);
        record.setSnapshotJson(writeJson(snapshot));
        record.setDiffJson(writeJson(changes == null ? List.of() : changes));
        record.setChangedBy(changedBy);
        record.setRollbackSourceVersionId(rollbackSourceVersionId);
        entityVersionMapper.insert(record);
    }

    public List<EntityVersionView> listVersions(String entityType, Long entityId) {
        return entityVersionMapper.findByEntity(entityType, entityId).stream()
                .map(this::toView)
                .toList();
    }

    public <T> T readSnapshot(String entityType, Long entityId, Long versionId, Class<T> snapshotType) {
        EntityVersionRow row = requireVersionRow(entityType, entityId, versionId);
        return readJson(row.snapshotJson(), snapshotType);
    }

    public EntityVersionView requireVersion(String entityType, Long entityId, Long versionId) {
        return toView(requireVersionRow(entityType, entityId, versionId));
    }

    private EntityVersionRow requireVersionRow(String entityType, Long entityId, Long versionId) {
        EntityVersionRow row = entityVersionMapper.findVersion(entityType, entityId, versionId);
        if (row == null) {
            throw new NotFoundException("版本记录不存在");
        }
        return row;
    }

    private EntityVersionView toView(EntityVersionRow row) {
        return new EntityVersionView(
                row.id(),
                row.versionNo(),
                row.action(),
                row.changedBy(),
                row.changedByName(),
                row.rollbackSourceVersionId(),
                row.rollbackSourceVersionNo(),
                row.createdAt(),
                readFieldChanges(row.diffJson())
        );
    }

    private List<VersionFieldChangeView> readFieldChanges(String diffJson) {
        JavaType listType = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, VersionFieldChangeView.class);
        return readJson(diffJson, listType);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("版本数据序列化失败", ex);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("版本数据解析失败", ex);
        }
    }

    private <T> T readJson(String json, JavaType type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("版本数据解析失败", ex);
        }
    }
}
