package com.gsmv.ai.rag.mapper;

import com.gsmv.ai.rag.model.RagChunk;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RagChunkMapper {

    @Insert("""
            <script>
            INSERT INTO rag_chunk (
              document_id, source_type, source_id, chunk_index, title, summary,
              content, embedding_json, vector_point_id, embedding_model, embedding_dimension,
              embedding_status, embedding_error, character_count, metadata_json, status
            ) VALUES
            <foreach collection='chunks' item='item' separator=','>
              (
                #{item.documentId}, #{item.sourceType}, #{item.sourceId}, #{item.chunkIndex}, #{item.title},
                #{item.summary}, #{item.content}, #{item.embeddingJson}, #{item.vectorPointId},
                #{item.embeddingModel}, #{item.embeddingDimension}, #{item.embeddingStatus},
                #{item.embeddingError}, #{item.characterCount}, #{item.metadataJson}, #{item.status}
              )
            </foreach>
            </script>
            """)
    void insertBatch(@Param("chunks") List<RagChunk> chunks);

    @Insert("""
            INSERT INTO rag_chunk (
              document_id, source_type, source_id, chunk_index, title, summary,
              content, embedding_json, vector_point_id, embedding_model, embedding_dimension,
              embedding_status, embedding_error, character_count, metadata_json, status
            ) VALUES (
              #{documentId}, #{sourceType}, #{sourceId}, #{chunkIndex}, #{title}, #{summary},
              #{content}, #{embeddingJson}, #{vectorPointId}, #{embeddingModel}, #{embeddingDimension},
              #{embeddingStatus}, #{embeddingError}, #{characterCount}, #{metadataJson}, #{status}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(RagChunk chunk);

    @Delete("DELETE FROM rag_chunk WHERE document_id = #{documentId}")
    void deleteByDocumentId(Long documentId);

    @Update("UPDATE rag_chunk SET status = 'DELETED', updated_at = CURRENT_TIMESTAMP(3) WHERE document_id = #{documentId}")
    void markDeletedByDocumentId(Long documentId);

    @Select("""
            SELECT *
            FROM rag_chunk
            WHERE document_id = #{documentId}
              AND status != 'DELETED'
            ORDER BY chunk_index
            """)
    List<RagChunk> findByDocumentId(Long documentId);

    @Select("""
            SELECT *
            FROM rag_chunk
            WHERE status = 'READY'
              AND (embedding_json IS NOT NULL OR vector_point_id IS NOT NULL)
            ORDER BY updated_at DESC
            LIMIT #{limit}
            """)
    List<RagChunk> findSearchPool(int limit);

    @Select("""
            <script>
            SELECT *
            FROM rag_chunk
            WHERE status = 'READY'
              AND id IN
              <foreach collection='ids' item='id' open='(' separator=',' close=')'>
                #{id}
              </foreach>
            </script>
            """)
    List<RagChunk> findByIds(@Param("ids") List<Long> ids);

    @Select("""
            SELECT *
            FROM rag_chunk
            WHERE status = 'READY'
            ORDER BY id ASC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<RagChunk> findReadyPage(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM rag_chunk WHERE status = 'READY'")
    long countReady();

    @Update("""
            UPDATE rag_chunk
            SET embedding_json = #{embeddingJson},
                vector_point_id = #{vectorPointId},
                embedding_model = #{embeddingModel},
                embedding_dimension = #{embeddingDimension},
                embedding_status = #{embeddingStatus},
                embedding_error = #{embeddingError},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    void updateEmbeddingState(
            @Param("id") Long id,
            @Param("embeddingJson") String embeddingJson,
            @Param("vectorPointId") String vectorPointId,
            @Param("embeddingModel") String embeddingModel,
            @Param("embeddingDimension") Integer embeddingDimension,
            @Param("embeddingStatus") String embeddingStatus,
            @Param("embeddingError") String embeddingError
    );
}
