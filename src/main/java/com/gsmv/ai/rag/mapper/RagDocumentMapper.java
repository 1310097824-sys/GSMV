package com.gsmv.ai.rag.mapper;

import com.gsmv.ai.rag.model.RagDocument;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RagDocumentMapper {

    @Insert("""
            INSERT INTO rag_document (
              source_type, source_id, media_id, title, original_filename, content_type,
              status, chunk_count, error_message, metadata_json, uploaded_by
            ) VALUES (
              #{sourceType}, #{sourceId}, #{mediaId}, #{title}, #{originalFilename}, #{contentType},
              #{status}, #{chunkCount}, #{errorMessage}, #{metadataJson}, #{uploadedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(RagDocument document);

    @Update("""
            UPDATE rag_document
            SET media_id = #{mediaId},
                title = #{title},
                original_filename = #{originalFilename},
                content_type = #{contentType},
                status = #{status},
                chunk_count = #{chunkCount},
                error_message = #{errorMessage},
                metadata_json = #{metadataJson},
                uploaded_by = #{uploadedBy},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    void update(RagDocument document);

    @Update("""
            UPDATE rag_document
            SET status = #{status},
                chunk_count = #{chunkCount},
                error_message = #{errorMessage},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    void updateStatus(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("chunkCount") int chunkCount,
            @Param("errorMessage") String errorMessage
    );

    @Update("""
            UPDATE rag_document
            SET status = 'DELETED',
                chunk_count = 0,
                error_message = NULL,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    void markDeleted(Long id);

    @Select("SELECT * FROM rag_document WHERE id = #{id}")
    RagDocument findById(Long id);

    @Select("""
            SELECT *
            FROM rag_document
            WHERE source_type = #{sourceType}
              AND source_id = #{sourceId}
            """)
    RagDocument findBySource(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);

    @Select("""
            <script>
            SELECT *
            FROM rag_document
            <where>
              status != 'DELETED'
              <if test='keyword != null and keyword != ""'>
                AND (
                  title LIKE CONCAT('%', #{keyword}, '%')
                  OR original_filename LIKE CONCAT('%', #{keyword}, '%')
                  OR error_message LIKE CONCAT('%', #{keyword}, '%')
                )
              </if>
              <if test='sourceType != null and sourceType != ""'>
                AND source_type = #{sourceType}
              </if>
              <if test='status != null and status != ""'>
                AND status = #{status}
              </if>
            </where>
            ORDER BY updated_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<RagDocument> findPage(
            @Param("keyword") String keyword,
            @Param("sourceType") String sourceType,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM rag_document
            <where>
              status != 'DELETED'
              <if test='keyword != null and keyword != ""'>
                AND (
                  title LIKE CONCAT('%', #{keyword}, '%')
                  OR original_filename LIKE CONCAT('%', #{keyword}, '%')
                  OR error_message LIKE CONCAT('%', #{keyword}, '%')
                )
              </if>
              <if test='sourceType != null and sourceType != ""'>
                AND source_type = #{sourceType}
              </if>
              <if test='status != null and status != ""'>
                AND status = #{status}
              </if>
            </where>
            </script>
            """)
    long count(
            @Param("keyword") String keyword,
            @Param("sourceType") String sourceType,
            @Param("status") String status
    );

    @Select("""
            SELECT *
            FROM rag_document
            WHERE source_type = 'UPLOAD'
              AND status != 'DELETED'
            ORDER BY updated_at DESC
            LIMIT #{limit}
            """)
    List<RagDocument> findUploadedDocuments(int limit);

    @Select("""
            SELECT *
            FROM rag_document
            WHERE source_type LIKE 'EXTERNAL\\_%'
              AND status != 'DELETED'
            ORDER BY updated_at DESC
            LIMIT #{limit}
            """)
    List<RagDocument> findExternalDocuments(int limit);

    @Select("""
            SELECT *
            FROM rag_document
            WHERE source_type = #{sourceType}
              AND status != 'DELETED'
            ORDER BY updated_at DESC
            LIMIT #{limit}
            """)
    List<RagDocument> findActiveBySourceType(@Param("sourceType") String sourceType, @Param("limit") int limit);
}
