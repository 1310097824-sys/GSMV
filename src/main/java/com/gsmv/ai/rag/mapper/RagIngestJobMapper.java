package com.gsmv.ai.rag.mapper;

import com.gsmv.ai.rag.model.RagIngestJob;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RagIngestJobMapper {

    @Insert("""
            INSERT INTO rag_ingest_job (
              job_type, status, source_code, title, total_items, processed_items,
              success_count, failed_count, error_message, created_by
            ) VALUES (
              #{jobType}, #{status}, #{sourceCode}, #{title}, #{totalItems}, #{processedItems},
              #{successCount}, #{failedCount}, #{errorMessage}, #{createdBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(RagIngestJob job);

    @Select("SELECT * FROM rag_ingest_job WHERE id = #{id}")
    RagIngestJob findById(Long id);

    @Select("""
            SELECT *
            FROM rag_ingest_job
            ORDER BY created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<RagIngestJob> findPage(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM rag_ingest_job")
    long count();

    @Update("""
            UPDATE rag_ingest_job
            SET total_items = #{totalItems},
                processed_items = #{processedItems},
                success_count = #{successCount},
                failed_count = #{failedCount},
                status = #{status},
                error_message = #{errorMessage},
                finished_at = #{finishedAt}
            WHERE id = #{id}
            """)
    void updateProgress(RagIngestJob job);

    @Update("""
            UPDATE rag_ingest_job
            SET status = #{status},
                error_message = #{errorMessage},
                finished_at = #{finishedAt}
            WHERE id = #{id}
            """)
    void finish(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage,
            @Param("finishedAt") LocalDateTime finishedAt
    );
}
