package com.gsmv.versioning.mapper;

import com.gsmv.versioning.dto.EntityVersionRow;
import com.gsmv.versioning.model.EntityVersionRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EntityVersionMapper {

    @Select("""
            SELECT COALESCE(MAX(version_no), 0) + 1
            FROM entity_version
            WHERE entity_type = #{entityType}
              AND entity_id = #{entityId}
            """)
    int findNextVersionNo(@Param("entityType") String entityType, @Param("entityId") Long entityId);

    @Insert("""
            INSERT INTO entity_version (
              entity_type, entity_id, version_no, action, snapshot_json, diff_json,
              changed_by, rollback_source_version_id
            ) VALUES (
              #{entityType}, #{entityId}, #{versionNo}, #{action}, #{snapshotJson}, #{diffJson},
              #{changedBy}, #{rollbackSourceVersionId}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(EntityVersionRecord record);

    @Select("""
            SELECT
              ev.id,
              ev.entity_type,
              ev.entity_id,
              ev.version_no,
              ev.action,
              ev.snapshot_json,
              ev.diff_json,
              ev.changed_by,
              COALESCE(u.display_name, u.username) AS changed_by_name,
              ev.rollback_source_version_id,
              src.version_no AS rollback_source_version_no,
              ev.created_at
            FROM entity_version ev
            LEFT JOIN sys_user u ON u.id = ev.changed_by
            LEFT JOIN entity_version src ON src.id = ev.rollback_source_version_id
            WHERE ev.entity_type = #{entityType}
              AND ev.entity_id = #{entityId}
            ORDER BY ev.version_no DESC, ev.id DESC
            """)
    List<EntityVersionRow> findByEntity(@Param("entityType") String entityType, @Param("entityId") Long entityId);

    @Select("""
            SELECT
              ev.id,
              ev.entity_type,
              ev.entity_id,
              ev.version_no,
              ev.action,
              ev.snapshot_json,
              ev.diff_json,
              ev.changed_by,
              COALESCE(u.display_name, u.username) AS changed_by_name,
              ev.rollback_source_version_id,
              src.version_no AS rollback_source_version_no,
              ev.created_at
            FROM entity_version ev
            LEFT JOIN sys_user u ON u.id = ev.changed_by
            LEFT JOIN entity_version src ON src.id = ev.rollback_source_version_id
            WHERE ev.entity_type = #{entityType}
              AND ev.entity_id = #{entityId}
              AND ev.id = #{versionId}
            """)
    EntityVersionRow findVersion(
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId,
            @Param("versionId") Long versionId
    );
}
