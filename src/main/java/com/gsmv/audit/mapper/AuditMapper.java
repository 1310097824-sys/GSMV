package com.gsmv.audit.mapper;

import com.gsmv.audit.dto.AuditLogView;
import com.gsmv.audit.model.AuditLog;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditMapper {

    @Insert("""
            INSERT INTO audit_log (
              user_id, module, action, entity_type, entity_id,
              request_id, ip, user_agent, success, detail_json
            ) VALUES (
              #{userId}, #{module}, #{action}, #{entityType}, #{entityId},
              #{requestId}, #{ip}, #{userAgent}, #{success}, #{detailJson}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AuditLog auditLog);

    @Select("""
            <script>
            SELECT
              a.id,
              a.user_id,
              u.username,
              u.display_name,
              a.module,
              a.action,
              a.entity_type,
              a.entity_id,
              a.request_id,
              a.success,
              a.detail_json,
              a.created_at
            FROM audit_log a
            LEFT JOIN sys_user u ON u.id = a.user_id
            <where>
              <if test='userId != null'>
                AND a.user_id = #{userId}
              </if>
              <if test='module != null and module != ""'>
                AND a.module = #{module}
              </if>
              <if test='success != null'>
                AND a.success = #{success}
              </if>
            </where>
            ORDER BY a.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AuditLogView> findPage(
            @Param("userId") Long userId,
            @Param("module") String module,
            @Param("success") Integer success,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM audit_log a
            <where>
              <if test='userId != null'>
                AND a.user_id = #{userId}
              </if>
              <if test='module != null and module != ""'>
                AND a.module = #{module}
              </if>
              <if test='success != null'>
                AND a.success = #{success}
              </if>
            </where>
            </script>
            """)
    long count(
            @Param("userId") Long userId,
            @Param("module") String module,
            @Param("success") Integer success
    );
}
