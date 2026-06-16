package com.gsmv.ai.agent.mapper;

import com.gsmv.ai.agent.model.AgentRun;
import com.gsmv.ai.agent.model.AgentStep;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AgentRunMapper {

    @Insert("""
            INSERT INTO ai_agent_run (
              workflow_type, status, subject_type, subject_id, user_id, prompt, started_at
            ) VALUES (
              #{workflowType}, #{status}, #{subjectType}, #{subjectId}, #{userId}, #{prompt}, #{startedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertRun(AgentRun run);

    @Update("""
            UPDATE ai_agent_run
            SET status = #{status},
                summary = #{summary},
                verification_status = #{verificationStatus},
                confidence = #{confidence},
                final_output_json = #{finalOutputJson},
                finished_at = #{finishedAt}
            WHERE id = #{id}
            """)
    void finishRun(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("summary") String summary,
            @Param("verificationStatus") String verificationStatus,
            @Param("confidence") BigDecimal confidence,
            @Param("finalOutputJson") String finalOutputJson,
            @Param("finishedAt") LocalDateTime finishedAt
    );

    @Update("""
            UPDATE ai_agent_run
            SET subject_type = #{subjectType},
                subject_id = #{subjectId}
            WHERE id = #{id}
            """)
    void updateSubject(
            @Param("id") Long id,
            @Param("subjectType") String subjectType,
            @Param("subjectId") Long subjectId
    );

    @Insert("""
            INSERT INTO ai_agent_step (
              run_id, step_order, agent_name, agent_role, status, summary,
              input_json, output_json, evidence_json, error_message, confidence,
              duration_ms, started_at, finished_at
            ) VALUES (
              #{runId}, #{stepOrder}, #{agentName}, #{agentRole}, #{status}, #{summary},
              #{inputJson}, #{outputJson}, #{evidenceJson}, #{errorMessage}, #{confidence},
              #{durationMs}, #{startedAt}, #{finishedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertStep(AgentStep step);

    @Select("""
            <script>
            SELECT r.*, COALESCE(u.display_name, u.username) AS username
            FROM ai_agent_run r
            LEFT JOIN sys_user u ON u.id = r.user_id
            <where>
              <if test='userId != null'>
                AND r.user_id = #{userId}
              </if>
              <if test='workflowType != null and workflowType != ""'>
                AND r.workflow_type = #{workflowType}
              </if>
              <if test='status != null and status != ""'>
                AND r.status = #{status}
              </if>
              <if test='verificationStatus != null and verificationStatus != ""'>
                AND r.verification_status = #{verificationStatus}
              </if>
              <if test='keyword != null and keyword != ""'>
                AND (
                  r.prompt LIKE CONCAT('%', #{keyword}, '%')
                  OR r.summary LIKE CONCAT('%', #{keyword}, '%')
                  OR r.workflow_type LIKE CONCAT('%', #{keyword}, '%')
                  OR r.subject_type LIKE CONCAT('%', #{keyword}, '%')
                  OR CAST(r.subject_id AS CHAR) LIKE CONCAT('%', #{keyword}, '%')
                  OR u.username LIKE CONCAT('%', #{keyword}, '%')
                  OR u.display_name LIKE CONCAT('%', #{keyword}, '%')
                )
              </if>
            </where>
            ORDER BY r.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AgentRun> findPage(
            @Param("userId") Long userId,
            @Param("workflowType") String workflowType,
            @Param("status") String status,
            @Param("verificationStatus") String verificationStatus,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM ai_agent_run r
            LEFT JOIN sys_user u ON u.id = r.user_id
            <where>
              <if test='userId != null'>
                AND r.user_id = #{userId}
              </if>
              <if test='workflowType != null and workflowType != ""'>
                AND r.workflow_type = #{workflowType}
              </if>
              <if test='status != null and status != ""'>
                AND r.status = #{status}
              </if>
              <if test='verificationStatus != null and verificationStatus != ""'>
                AND r.verification_status = #{verificationStatus}
              </if>
              <if test='keyword != null and keyword != ""'>
                AND (
                  r.prompt LIKE CONCAT('%', #{keyword}, '%')
                  OR r.summary LIKE CONCAT('%', #{keyword}, '%')
                  OR r.workflow_type LIKE CONCAT('%', #{keyword}, '%')
                  OR r.subject_type LIKE CONCAT('%', #{keyword}, '%')
                  OR CAST(r.subject_id AS CHAR) LIKE CONCAT('%', #{keyword}, '%')
                  OR u.username LIKE CONCAT('%', #{keyword}, '%')
                  OR u.display_name LIKE CONCAT('%', #{keyword}, '%')
                )
              </if>
            </where>
            </script>
            """)
    long count(
            @Param("userId") Long userId,
            @Param("workflowType") String workflowType,
            @Param("status") String status,
            @Param("verificationStatus") String verificationStatus,
            @Param("keyword") String keyword
    );

    @Select("""
            SELECT r.*, COALESCE(u.display_name, u.username) AS username
            FROM ai_agent_run r
            LEFT JOIN sys_user u ON u.id = r.user_id
            WHERE r.id = #{id}
            """)
    AgentRun findRunById(Long id);

    @Select("""
            SELECT *
            FROM ai_agent_step
            WHERE run_id = #{runId}
            ORDER BY step_order ASC, id ASC
            """)
    List<AgentStep> findStepsByRunId(Long runId);
}
