package com.gsmv.user.mapper;

import com.gsmv.user.model.SysUser;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser findByUsername(String username);

    @Select("SELECT * FROM sys_user WHERE id = #{id}")
    SysUser findById(Long id);

    @Select("""
            <script>
            SELECT *
            FROM sys_user
            <where>
              <if test='keyword != null and keyword != ""'>
                AND (
                  username LIKE CONCAT('%', #{keyword}, '%')
                  OR display_name LIKE CONCAT('%', #{keyword}, '%')
                  OR email LIKE CONCAT('%', #{keyword}, '%')
                )
              </if>
              <if test='status != null'>
                AND status = #{status}
              </if>
              <if test='approvalStatus != null and approvalStatus != ""'>
                AND approval_status = #{approvalStatus}
              </if>
            </where>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<SysUser> findPage(
            @Param("keyword") String keyword,
            @Param("status") Integer status,
            @Param("approvalStatus") String approvalStatus,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sys_user
            <where>
              <if test='keyword != null and keyword != ""'>
                AND (
                  username LIKE CONCAT('%', #{keyword}, '%')
                  OR display_name LIKE CONCAT('%', #{keyword}, '%')
                  OR email LIKE CONCAT('%', #{keyword}, '%')
                )
              </if>
              <if test='status != null'>
                AND status = #{status}
              </if>
              <if test='approvalStatus != null and approvalStatus != ""'>
                AND approval_status = #{approvalStatus}
              </if>
            </where>
            </script>
            """)
    long count(
            @Param("keyword") String keyword,
            @Param("status") Integer status,
            @Param("approvalStatus") String approvalStatus
    );

    @Insert("""
            INSERT INTO sys_user (
              username, password_hash, display_name, email, phone, bio, status,
              approval_status, approval_remark, reviewed_by, reviewed_at, avatar_media_id
            ) VALUES (
              #{username}, #{passwordHash}, #{displayName}, #{email}, #{phone}, #{bio}, #{status},
              #{approvalStatus}, #{approvalRemark}, #{reviewedBy}, #{reviewedAt}, #{avatarMediaId}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(SysUser user);

    @Update("""
            UPDATE sys_user
            SET display_name = #{displayName},
                email = #{email},
                phone = #{phone},
                bio = #{bio},
                status = #{status},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    void updateByAdmin(SysUser user);

    @Update("""
            UPDATE sys_user
            SET display_name = #{displayName},
                email = #{email},
                phone = #{phone},
                bio = #{bio},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    void updateProfile(SysUser user);

    @Update("""
            UPDATE sys_user
            SET password_hash = #{passwordHash},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    void updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    @Update("""
            UPDATE sys_user
            SET approval_status = #{approvalStatus},
                approval_remark = #{approvalRemark},
                reviewed_by = #{reviewedBy},
                reviewed_at = CURRENT_TIMESTAMP(3),
                status = #{status},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    void updateApproval(
            @Param("id") Long id,
            @Param("approvalStatus") String approvalStatus,
            @Param("approvalRemark") String approvalRemark,
            @Param("reviewedBy") Long reviewedBy,
            @Param("status") Integer status
    );

    @Update("""
            UPDATE sys_user
            SET avatar_media_id = #{avatarMediaId},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    void updateAvatarMediaId(@Param("id") Long id, @Param("avatarMediaId") Long avatarMediaId);

    @Update("""
            UPDATE sys_user
            SET last_login_at = CURRENT_TIMESTAMP(3),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    void updateLastLoginAt(Long id);
}
