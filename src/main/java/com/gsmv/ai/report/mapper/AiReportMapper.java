package com.gsmv.ai.report.mapper;

import com.gsmv.ai.report.model.AiReport;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiReportMapper {

    @Insert("""
            INSERT INTO ai_research_report (
              report_type, days, title, summary, highlights_json, risks_json,
              recommendations_json, evidence_json, created_by
            ) VALUES (
              #{reportType}, #{days}, #{title}, #{summary}, #{highlightsJson}, #{risksJson},
              #{recommendationsJson}, #{evidenceJson}, #{createdBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AiReport report);

    @Select("""
            SELECT r.*, COALESCE(u.display_name, u.username) AS creator_name
            FROM ai_research_report r
            JOIN sys_user u ON u.id = r.created_by
            ORDER BY r.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<AiReport> findPage(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM ai_research_report")
    long count();

    @Select("""
            SELECT r.*, COALESCE(u.display_name, u.username) AS creator_name
            FROM ai_research_report r
            JOIN sys_user u ON u.id = r.created_by
            WHERE r.id = #{id}
            """)
    AiReport findById(Long id);
}
