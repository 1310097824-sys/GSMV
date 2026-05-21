package com.gsmv.ai.rag.mapper;

import com.gsmv.ai.rag.model.RagIngestItem;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RagIngestItemMapper {

    @Insert("""
            INSERT INTO rag_ingest_item (
              job_id, source_type, source_code, external_id, source_url, local_path,
              media_id, rag_document_id, title, status, error_message, metadata_json
            ) VALUES (
              #{jobId}, #{sourceType}, #{sourceCode}, #{externalId}, #{sourceUrl}, #{localPath},
              #{mediaId}, #{ragDocumentId}, #{title}, #{status}, #{errorMessage}, #{metadataJson}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(RagIngestItem item);

    @Select("""
            SELECT *
            FROM rag_ingest_item
            WHERE job_id = #{jobId}
            ORDER BY id ASC
            """)
    List<RagIngestItem> findByJobId(Long jobId);

    @Select("""
            SELECT *
            FROM rag_ingest_item
            WHERE id = #{id}
            """)
    RagIngestItem findById(Long id);

    @Update("""
            UPDATE rag_ingest_item
            SET status = #{status},
                error_message = #{errorMessage},
                media_id = #{mediaId},
                rag_document_id = #{ragDocumentId},
                metadata_json = #{metadataJson},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    void updateStatus(RagIngestItem item);

    @Select("""
            SELECT *
            FROM rag_ingest_item
            WHERE job_id = #{jobId}
              AND status = 'FAILED'
            ORDER BY id ASC
            """)
    List<RagIngestItem> findFailedByJobId(Long jobId);

    @Select("""
            SELECT COUNT(*)
            FROM rag_ingest_item
            WHERE source_code = #{sourceCode}
              AND external_id = #{externalId}
              AND status = 'SUCCESS'
            """)
    long countSuccessfulExternal(@Param("sourceCode") String sourceCode, @Param("externalId") String externalId);
}
