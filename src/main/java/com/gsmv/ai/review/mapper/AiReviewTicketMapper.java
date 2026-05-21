package com.gsmv.ai.review.mapper;

import com.gsmv.ai.review.model.AiReviewTicket;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiReviewTicketMapper {

    @Insert("""
            INSERT INTO ai_review_ticket (
              source_type, status, submitted_by, likely_chinese_name, likely_scientific_name,
              confidence, needs_human_review, reasoning, candidate_json, related_species_json,
              initial_recognition_json, rag_evidence_json, review_evidence_json, submit_note
            ) VALUES (
              #{sourceType}, #{status}, #{submittedBy}, #{likelyChineseName}, #{likelyScientificName},
              #{confidence}, #{needsHumanReview}, #{reasoning}, #{candidateJson}, #{relatedSpeciesJson},
              #{initialRecognitionJson}, #{ragEvidenceJson}, #{reviewEvidenceJson}, #{submitNote}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AiReviewTicket ticket);

    @Update("""
            UPDATE ai_review_ticket
            SET image_media_id = #{imageMediaId},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    void updateImageMediaId(@Param("id") Long id, @Param("imageMediaId") Long imageMediaId);

    @Update("""
            UPDATE ai_review_ticket
            SET status = 'IN_REVIEW',
                reviewer_user_id = #{reviewerUserId},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    void markInReview(@Param("id") Long id, @Param("reviewerUserId") Long reviewerUserId);

    @Update("""
            UPDATE ai_review_ticket
            SET status = 'RESOLVED',
                resolution_code = #{resolutionCode},
                reviewer_user_id = #{reviewerUserId},
                final_species_id = #{finalSpeciesId},
                final_chinese_name = #{finalChineseName},
                final_scientific_name = #{finalScientificName},
                review_note = #{reviewNote},
                reviewed_at = #{reviewedAt},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    void resolve(
            @Param("id") Long id,
            @Param("resolutionCode") String resolutionCode,
            @Param("reviewerUserId") Long reviewerUserId,
            @Param("finalSpeciesId") Long finalSpeciesId,
            @Param("finalChineseName") String finalChineseName,
            @Param("finalScientificName") String finalScientificName,
            @Param("reviewNote") String reviewNote,
            @Param("reviewedAt") LocalDateTime reviewedAt
    );

    @Update("""
            UPDATE ai_review_ticket
            SET status = 'REJECTED',
                resolution_code = 'REJECTED',
                reviewer_user_id = #{reviewerUserId},
                review_note = #{reviewNote},
                reviewed_at = #{reviewedAt},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    void reject(
            @Param("id") Long id,
            @Param("reviewerUserId") Long reviewerUserId,
            @Param("reviewNote") String reviewNote,
            @Param("reviewedAt") LocalDateTime reviewedAt
    );

    @Update("""
            UPDATE ai_review_ticket
            SET status = 'PENDING',
                resolution_code = NULL,
                reviewer_user_id = NULL,
                review_note = NULL,
                reviewed_at = NULL,
                submit_note = #{submitNote},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    void resubmit(@Param("id") Long id, @Param("submitNote") String submitNote);

    @Select("""
            <script>
            SELECT
              t.*,
              COALESCE(su.display_name, su.username) AS submitted_by_name,
              COALESCE(ru.display_name, ru.username) AS reviewer_name
            FROM ai_review_ticket t
            JOIN sys_user su ON su.id = t.submitted_by
            LEFT JOIN sys_user ru ON ru.id = t.reviewer_user_id
            <where>
              <if test='keyword != null and keyword != ""'>
                AND (
                  CAST(t.id AS CHAR) LIKE CONCAT('%', #{keyword}, '%')
                  OR t.submit_note LIKE CONCAT('%', #{keyword}, '%')
                  OR t.likely_chinese_name LIKE CONCAT('%', #{keyword}, '%')
                  OR t.likely_scientific_name LIKE CONCAT('%', #{keyword}, '%')
                  OR su.username LIKE CONCAT('%', #{keyword}, '%')
                  OR su.display_name LIKE CONCAT('%', #{keyword}, '%')
                  OR ru.username LIKE CONCAT('%', #{keyword}, '%')
                  OR ru.display_name LIKE CONCAT('%', #{keyword}, '%')
                )
              </if>
              <if test='status != null and status != ""'>
                AND t.status = #{status}
              </if>
              <if test='submittedBy != null'>
                AND t.submitted_by = #{submittedBy}
              </if>
            </where>
            ORDER BY FIELD(t.status, 'PENDING', 'IN_REVIEW', 'REJECTED', 'RESOLVED'), t.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AiReviewTicket> findPage(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("submittedBy") Long submittedBy,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM ai_review_ticket t
            JOIN sys_user su ON su.id = t.submitted_by
            LEFT JOIN sys_user ru ON ru.id = t.reviewer_user_id
            <where>
              <if test='keyword != null and keyword != ""'>
                AND (
                  CAST(t.id AS CHAR) LIKE CONCAT('%', #{keyword}, '%')
                  OR t.submit_note LIKE CONCAT('%', #{keyword}, '%')
                  OR t.likely_chinese_name LIKE CONCAT('%', #{keyword}, '%')
                  OR t.likely_scientific_name LIKE CONCAT('%', #{keyword}, '%')
                  OR su.username LIKE CONCAT('%', #{keyword}, '%')
                  OR su.display_name LIKE CONCAT('%', #{keyword}, '%')
                  OR ru.username LIKE CONCAT('%', #{keyword}, '%')
                  OR ru.display_name LIKE CONCAT('%', #{keyword}, '%')
                )
              </if>
              <if test='status != null and status != ""'>
                AND t.status = #{status}
              </if>
              <if test='submittedBy != null'>
                AND t.submitted_by = #{submittedBy}
              </if>
            </where>
            </script>
            """)
    long count(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("submittedBy") Long submittedBy
    );

    @Select("""
            SELECT
              t.*,
              COALESCE(su.display_name, su.username) AS submitted_by_name,
              COALESCE(ru.display_name, ru.username) AS reviewer_name
            FROM ai_review_ticket t
            JOIN sys_user su ON su.id = t.submitted_by
            LEFT JOIN sys_user ru ON ru.id = t.reviewer_user_id
            WHERE t.id = #{id}
            """)
    AiReviewTicket findById(Long id);
}
