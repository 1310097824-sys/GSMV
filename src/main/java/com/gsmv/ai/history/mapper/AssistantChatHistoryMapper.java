package com.gsmv.ai.history.mapper;

import com.gsmv.ai.history.AssistantChatMessage;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AssistantChatHistoryMapper {

    @Insert("""
            INSERT INTO ai_assistant_message (
              user_id, role, content, structured_query_json, highlights_json, evidence_json, cache_hit
            ) VALUES (
              #{userId}, #{role}, #{content}, #{structuredQueryJson}, #{highlightsJson}, #{evidenceJson}, #{cacheHit}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AssistantChatMessage message);

    @Select("""
            SELECT *
            FROM (
              SELECT *
              FROM ai_assistant_message
              WHERE user_id = #{userId}
              ORDER BY created_at DESC, id DESC
              LIMIT #{limit}
            ) recent_messages
            ORDER BY created_at ASC, id ASC
            """)
    List<AssistantChatMessage> listRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Delete("DELETE FROM ai_assistant_message WHERE user_id = #{userId}")
    int deleteByUserId(Long userId);
}
