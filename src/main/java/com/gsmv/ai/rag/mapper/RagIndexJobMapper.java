package com.gsmv.ai.rag.mapper;

import com.gsmv.ai.rag.model.RagIndexJob;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RagIndexJobMapper {

    @Insert("""
            INSERT INTO rag_index_job (
              job_type, status, target_source_type, target_source_id, total_documents,
              total_chunks, success_count, failed_count, error_message, created_by
            ) VALUES (
              #{jobType}, #{status}, #{targetSourceType}, #{targetSourceId}, #{totalDocuments},
              #{totalChunks}, #{successCount}, #{failedCount}, #{errorMessage}, #{createdBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(RagIndexJob job);

    @Update("""
            UPDATE rag_index_job
            SET status = #{status},
                total_documents = #{totalDocuments},
                total_chunks = #{totalChunks},
                success_count = #{successCount},
                failed_count = #{failedCount},
                error_message = #{errorMessage},
                finished_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    void finish(RagIndexJob job);

    @Select("""
            SELECT *
            FROM rag_index_job
            ORDER BY created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<RagIndexJob> findPage(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM rag_index_job")
    long count();

    @Select("SELECT * FROM rag_index_job WHERE id = #{id}")
    RagIndexJob findById(Long id);
}
